package io.github.jcondedata.aliveworkplace.craft;

import com.google.common.collect.ImmutableMap;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.MaterialFamilies;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Village;
import io.github.jcondedata.aliveworkplace.work.Walker;
import io.github.jcondedata.aliveworkplace.work.Words;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * A crafter's shift (the Carpenter at the crafting table's recipes, the Mason at the stonecutter's): find a builder
 * nearby waiting for something that can be made from what they can get at (their chests and their village's), fetch
 * the ingredients, make it at the workstation and take it to the builder's chests. Only what the rest of that build
 * doesn't need is used (planks for stairs, but not the planks the walls still want). The job itself isn't saved: a
 * crafter interrupted with things in their bag puts them in the chests by their workstation.
 *
 * <p>With Milestone 37 open ({@link #chains}, ROADMAP 37.14a, which moved the retired Crafter's way of working here) a
 * job that crafts for builders plans whole chains of its own kind ({@link Crafting#CHAIN_DEPTH} steps down: a Carpenter's
 * barrel from logs), checks every step before taking anything and notes what's short for the builder's shortage line
 * ({@link JobShortages}), makes a big order a bagful at a time, makes each step in turn at the workstation, and keeps the
 * job on the villager ({@link CrafterJob}, the {@code crafter_job} attachment) so a chain half made carries on after a
 * restart.
 */
public class CrafterWork extends Behavior<Villager> {
	/** Base ticks one craft takes (faster with levels and Pokémon partners). */
	public static final int CRAFT_TICKS = 8;

	/** Ticks {@code crafts} crafts take this villager: the level's share at their pace, Craftsmanship included (ROADMAP 30.2). */
	public static int craftTicks(Villager villager, int crafts) {
		return Math.max(20, BuilderLevels.delay(CRAFT_TICKS * crafts, villager));
	}
	/** A job's ingredients have to fit in this many stacks (the rest of the bag is for what comes out). */
	static final int MAX_STACKS_IN = 12;
	private static final float SPEED = 0.55f;
	private static final double REACH = 3.0;
	private static final int LOOK_EVERY = 100;
	/** A request another crafter took is left to them this long. */
	private static final long CLAIM_TICKS = 2400;

	private enum Phase { IDLE, FETCHING, CRAFTING, DELIVERING, TIDYING }

	/**
	 * Making {@code plan} with ingredients from {@code sources}, for the chests by {@code deliverTo} (outside {@code area}:
	 * a builder's building). {@code claim} keeps other crafters off the same request ("" for none). {@code guarded} maps
	 * the chests of another builder to what keeps that builder's builds supplied: from those only the spare is taken.
	 */
	protected record Job(BlockPos deliverTo, @Nullable BoundingBox area, Crafting.Plan plan, String claim, List<BlockPos> sources,
		Map<BlockPos, Guard> guarded) {
		protected Job(BlockPos deliverTo, @Nullable BoundingBox area, Crafting.Plan plan, String claim, List<BlockPos> sources) {
			this(deliverTo, area, plan, claim, sources, Map.of());
		}
	}

	/**
	 * Another builder's stash, whose builds (at {@code station}, other than {@code except}) keep what they still need
	 * (B33: a mason cutting for one builder took the andesite another builder's build was waiting to use).
	 */
	protected record Guard(BlockPos station, List<BlockPos> chests, @Nullable BuildSite except) {
	}

	private static final Set<Villager> BUSY = Collections.newSetFromMap(new WeakHashMap<>());
	private static final Map<String, Long> CLAIMS = new HashMap<>();

	protected final Crafting.Kind kind;
	/** Whose title the line above the head shows: "carpenter", "mason", "chef". */
	private final String who;
	/** The status lines used ("crafter": making things for the builders; "chef": cooking). */
	private final String lines;
	/** Whether the line above the head shows while there's nothing to do (vanilla masons go about their day instead). */
	private final boolean alwaysShowStatus;
	private final Walker walker = new Walker(SPEED);
	@Nullable
	private Job job;
	/** The next step of a chain being made step by step (37.14a), and whether {@link #job} is made that way. */
	private int step;
	private boolean stepwise;
	private Map<Item, Integer> toFetch = new LinkedHashMap<>();
	private Phase phase = Phase.IDLE;
	private int timer;
	private int lookTimer;
	/** The last look found nothing to do (so the line above the head says so). */
	private boolean foundNothing = true;

	public CrafterWork(Crafting.Kind kind, boolean alwaysShowStatus) {
		this(kind, kind == Crafting.Kind.STONECUTTING ? "mason" : "carpenter", "crafter", alwaysShowStatus);
	}

	protected CrafterWork(Crafting.Kind kind, String who, String lines, boolean alwaysShowStatus) {
		super(ImmutableMap.of(
			MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 1200);
		this.kind = kind;
		this.who = who;
		this.lines = lines;
		this.alwaysShowStatus = alwaysShowStatus;
	}

	/** Whether this crafter is on a job (a vanilla mason's own routine waits till they're done). */
	public static boolean isBusy(Villager villager) {
		synchronized (BUSY) {
			return BUSY.contains(villager);
		}
	}

	/** A vanilla mason's routine runs while they aren't making anything for a builder. */
	public static boolean vanillaMayRun(Villager villager) {
		return !isBusy(villager);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return !villager.isSleeping() && !villager.isBaby() && Builders.benchPos(villager).isPresent();
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return checkExtraStartConditions(level, villager);
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		walker.reset();
		lookTimer = 0;
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		// The job waits for tomorrow's shift in memory; if it's gone by then, the bag goes to our own chests.
		setBusy(villager, false);
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		BlockPos station = Builders.benchPos(villager).orElse(null);
		if (station == null) {
			return;
		}
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(villager);
		if (job != null && chains() && ModAttachments.CRAFTER_JOB.get(villager) == null) {
			job = null; // let go while the workstation was gone (B353): what was in the bag went back already
			phase = Phase.IDLE;
		}
		if (job == null && chains()) {
			restore(level, villager, bag);
		}
		if (job == null) {
			if (!bag.isEmpty()) {
				tidy(level, villager, station, bag);
				return;
			}
			setBusy(villager, false);
			if (foundNothing) {
				status(villager, Phase.IDLE); // (not while between two jobs)
			}
			if (--lookTimer > 0) {
				return;
			}
			lookTimer = LOOK_EVERY;
			job = choose(level, villager, station);
			foundNothing = job == null;
			if (job == null) {
				status(villager, Phase.IDLE);
				return;
			}
			toFetch = new LinkedHashMap<>(job.plan().takes());
			phase = Phase.FETCHING;
			step = 0;
			stepwise = chains() && stepwise(job.plan());
			save(villager);
			walker.reset();
		}
		setBusy(villager, true);
		switch (phase) {
			case FETCHING -> fetch(level, villager, bag);
			case CRAFTING -> craft(level, villager, station, bag);
			default -> deliver(level, villager, station, bag);
		}
	}

	/** Walks round the job's chests, collecting the ingredients. */
	private void fetch(ServerLevel level, Villager villager, BuilderBag bag) {
		status(villager, Phase.FETCHING);
		if (toFetch.isEmpty()) {
			phase = Phase.CRAFTING;
			timer = -1;
			save(villager);
			return;
		}
		Map<Guard, Map<Item, Integer>> reserved = new HashMap<>();
		BlockPos chest = null;
		for (BlockPos source : job.sources()) {
			Guard guard = job.guarded().get(source);
			if (SupplyContainers.firstMatching(level, List.of(source), s -> fetchable(level, s) && toFetch.containsKey(s.getItem())
				&& allowance(level, guard, reserved, s.getItem()) > 0) != null) {
				chest = source;
				break;
			}
		}
		if (chest == null) {
			phase = Phase.DELIVERING; // someone took them: bring back what we have
			save(villager);
			walker.reset();
			return;
		}
		if (!walker.walkTo(level, villager, chest, REACH)) {
			return;
		}
		for (Map.Entry<Item, Integer> e : new ArrayList<>(toFetch.entrySet())) {
			int allowed = (int) Math.min(e.getValue(), allowance(level, job.guarded().get(chest), reserved, e.getKey()));
			int got = allowed <= 0 ? 0 : takeFrom(level, chest, e.getKey(), allowed);
			if (got > 0) {
				int over = bag.addAll(e.getKey(), got);
				if (over > 0) {
					SupplyContainers.insert(level, List.of(chest), new ItemStack(e.getKey(), over));
				}
				int left = e.getValue() - (got - over);
				if (left <= 0) {
					toFetch.remove(e.getKey());
				} else {
					toFetch.put(e.getKey(), left);
				}
			}
		}
		villager.swing(InteractionHand.MAIN_HAND);
		level.playSound(null, chest, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.4f, 1.1f);
		walker.reset();
	}

	/** How many of {@code item} may be taken from a chest: all of it, or from another builder's only what they can spare. */
	private static long allowance(ServerLevel level, @Nullable Guard guard, Map<Guard, Map<Item, Integer>> reserved, Item item) {
		if (guard == null) {
			return Long.MAX_VALUE;
		}
		Map<Item, Integer> keep = reserved.computeIfAbsent(guard, g -> Builders.reservedAt(level, g.station(), g.except()));
		return Builders.spare(level, guard.chests(), item, keep);
	}

	/** At the workstation: turns the ingredients in the bag into what the plan makes. */
	private void craft(ServerLevel level, Villager villager, BlockPos station, BuilderBag bag) {
		status(villager, Phase.CRAFTING);
		if (!walker.walkTo(level, villager, station, 2.5)) {
			return;
		}
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(station));
		if (stepwise) {
			craftStep(level, villager, station, bag);
			return;
		}
		int crafts = job.plan().steps().stream().mapToInt(Crafting.Step::times).sum();
		if (timer < 0) {
			timer = ticksFor(villager, job.plan(), crafts);
			cue(level, villager, station);
		}
		if (timer % 10 == 0) {
			villager.swing(InteractionHand.MAIN_HAND);
			workEffects(level, station);
		}
		if (--timer > 0) {
			return;
		}
		finishCraft(level, villager, station, bag, crafts);
	}

	/** A Fighting, Rock or Steel partner holds the board or stone while it's worked (28.4); a trade's partner does its show (28.5). */
	private void cue(ServerLevel level, Villager villager, BlockPos station) {
		Item worked = job.plan().takes().keySet().stream().findFirst().orElse(job.plan().target()); // the board, the stone
		List<BlockPos> to = SupplyContainers.find(level, job.deliverTo(), job.area());
		io.github.jcondedata.aliveworkplace.work.PartnerShows.cue(villager, partnerCue(), station, new ItemStack(worked),
			to.isEmpty() ? null : new io.github.jcondedata.aliveworkplace.work.PartnerShows.Delivery(new ItemStack(job.plan().target()), to.get(0)));
	}

	/**
	 * A chain made step by step (37.14a): the next step's makings out of the bag and its making in, in its own time, the
	 * step saved after each; when the last is made, fuel the plan took (a kiln's coal) is burnt and it's taken to the asker.
	 */
	private void craftStep(ServerLevel level, Villager villager, BlockPos station, BuilderBag bag) {
		List<Crafting.Step> steps = job.plan().steps();
		if (step >= steps.size()) {
			burnFuel(bag, job.plan());
			int crafts = steps.stream().mapToInt(Crafting.Step::times).sum();
			ModAttachments.ITEMS_CRAFTED.set(villager, ModAttachments.ITEMS_CRAFTED.getOrElse(villager, 0) + job.plan().count());
			BuilderLevels.addXp(level, villager, Math.max(1, crafts / 2), null);
			phase = Phase.DELIVERING;
			save(villager);
			walker.reset();
			return;
		}
		Crafting.Step next = steps.get(step);
		if (timer < 0) {
			timer = ticksFor(villager, job.plan(), next.times());
			if (step == 0) {
				cue(level, villager, station);
			}
		}
		if (timer % 10 == 0) {
			villager.swing(InteractionHand.MAIN_HAND);
			workEffects(level, station);
		}
		if (--timer > 0) {
			return;
		}
		timer = -1;
		List<ItemStack> extra = makeStep(bag, next);
		if (extra == null) {
			// the makings aren't in the bag (a bag lost to a crash): nothing more can be made, what there is goes to the asker
			phase = Phase.DELIVERING;
			save(villager);
			walker.reset();
			return;
		}
		extra.forEach(stack -> Block.popResource(level, station.above(), stack));
		step++;
		save(villager);
	}

	/**
	 * Takes one step's makings out of the bag and puts what it makes in; returns what didn't fit, or null (nothing changed)
	 * if the makings aren't all there.
	 */
	@Nullable
	static List<ItemStack> makeStep(BuilderBag bag, Crafting.Step step) {
		for (Map.Entry<Item, Integer> e : step.in().entrySet()) {
			if (bag.count(e.getKey()) < e.getValue() * step.times()) {
				return null;
			}
		}
		List<ItemStack> extra = new ArrayList<>();
		step.in().forEach((item, n) -> {
			bag.remove(item, n * step.times());
			Item rest = item.getCraftingRemainingItem();
			if (rest != null) {
				int over = bag.addAll(rest, n * step.times());
				if (over > 0) {
					extra.add(new ItemStack(rest, over));
				}
			}
		});
		int over = bag.addAll(step.out().getItem(), step.out().getCount() * step.times());
		if (over > 0) {
			extra.add(new ItemStack(step.out().getItem(), over));
		}
		return extra;
	}

	/** What a plan takes that none of its steps uses (the coal a kiln or a blast furnace burns) is used up at the end. */
	private static void burnFuel(BuilderBag bag, Crafting.Plan plan) {
		plan.takes().forEach((item, n) -> {
			if (plan.steps().stream().noneMatch(s -> s.in().containsKey(item))) {
				bag.remove(item, Math.min(n, bag.count(item)));
			}
		});
	}

	/**
	 * Whether {@code plan}'s steps, made in turn from what it takes, come to what it makes of the target (most plans; one
	 * whose last change isn't a step is made all at once as before).
	 */
	static boolean stepwise(Crafting.Plan plan) {
		if (plan.steps().isEmpty()) {
			return false;
		}
		Map<Item, Long> have = new HashMap<>();
		plan.takes().forEach((item, n) -> have.merge(item, (long) n, Long::sum));
		for (Crafting.Step s : plan.steps()) {
			for (Map.Entry<Item, Integer> e : s.in().entrySet()) {
				long need = (long) e.getValue() * s.times();
				if (have.getOrDefault(e.getKey(), 0L) < need) {
					return false;
				}
				have.merge(e.getKey(), -need, Long::sum);
			}
			have.merge(s.out().getItem(), (long) s.out().getCount() * s.times(), Long::sum);
		}
		return have.getOrDefault(plan.target(), 0L) >= plan.count();
	}

	/** Whether this job plans whole chains and keeps them saved (37.14a): with Milestone 37 open, for the jobs that craft for builders. */
	protected boolean chains() {
		return chainsForBuilders() && io.github.jcondedata.aliveworkplace.Expansions.isOpen("M37");
	}

	/** Whether this trade crafts for builders the chain way (37.14a); a cook's, a luxury maker's, a weaponsmith's or a fletcher's don't. */
	protected boolean chainsForBuilders() {
		return true;
	}

	/** The job in hand, saved on the villager (37.14a), or the saved one taken away when there's none. */
	private void save(Villager villager) {
		if (!chains()) {
			return;
		}
		if (job == null) {
			ModAttachments.CRAFTER_JOB.remove(villager);
			return;
		}
		// only the chests it may take anything from (another builder's spare isn't known after a restart)
		List<BlockPos> sources = job.sources().stream().filter(p -> !job.guarded().containsKey(p)).toList();
		CrafterJob saved = CrafterJob.of(job.deliverTo(), job.area(), job.plan(), sources, job.claim());
		CrafterJob.Phase at = switch (phase) {
			case CRAFTING -> CrafterJob.Phase.CRAFTING;
			case DELIVERING -> CrafterJob.Phase.DELIVERING;
			default -> CrafterJob.Phase.FETCHING;
		};
		saved = saved.withPhase(at);
		for (int i = 0; i < step; i++) {
			saved = saved.nextStep();
		}
		ModAttachments.CRAFTER_JOB.set(villager, saved);
	}

	/** Picks up the job saved on the villager (after a restart or a reload), where it was. */
	private void restore(ServerLevel level, Villager villager, BuilderBag bag) {
		CrafterJob saved = ModAttachments.CRAFTER_JOB.get(villager);
		if (saved == null) {
			return;
		}
		Map<Item, Integer> makes = new LinkedHashMap<>();
		makes.put(saved.target(), saved.count());
		Crafting.Plan plan = new Crafting.Plan(saved.target(), saved.count(), saved.steps(), saved.takes(), makes);
		job = new Job(saved.deliverTo(), saved.areaOrNull(), plan, saved.claim(), saved.sources());
		if (!saved.claim().isEmpty()) {
			claim(saved.claim(), level.getGameTime());
		}
		step = saved.step();
		stepwise = stepwise(plan);
		phase = switch (saved.phase()) {
			case CRAFTING -> Phase.CRAFTING;
			case DELIVERING -> Phase.DELIVERING;
			default -> Phase.FETCHING;
		};
		toFetch = new LinkedHashMap<>();
		saved.takes().forEach((item, n) -> {
			int left = n - bag.count(item);
			if (left > 0) {
				toFetch.put(item, left);
			}
		});
		timer = -1;
		foundNothing = false;
		walker.reset();
	}

	/** The sound and particles of the work at the station, every half second while crafting (a trade may have its own). */
	protected void workEffects(ServerLevel level, BlockPos station) {
		level.playSound(null, station, kind == Crafting.Kind.STONECUTTING ? SoundEvents.UI_STONECUTTER_TAKE_RESULT
			: kind == Crafting.Kind.KITCHEN ? SoundEvents.CAMPFIRE_CRACKLE : kind == Crafting.Kind.WORKSHOP ? SoundEvents.CHAIN_HIT : SoundEvents.WOOD_HIT,
			SoundSource.BLOCKS, 0.5f, 0.9f + level.random.nextFloat() * 0.2f);
		if (kind == Crafting.Kind.WORKSHOP) {
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMALL_FLAME, station.getX() + 0.5, station.getY() + 1.05,
				station.getZ() + 0.5, 2, 0.15, 0.02, 0.15, 0.005);
		}
		if (kind == Crafting.Kind.KITCHEN) {
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE, station.getX() + 0.5, station.getY() + 1.1,
				station.getZ() + 0.5, 1, 0.1, 0.05, 0.1, 0.01);
		}
	}

	private void finishCraft(ServerLevel level, Villager villager, BlockPos station, BuilderBag bag, int crafts) {
		List<ItemStack> extra = apply(bag, job.plan());
		if (extra != null) {
			extra.forEach(stack -> Block.popResource(level, station.above(), stack));
			ModAttachments.ITEMS_CRAFTED.set(villager, ModAttachments.ITEMS_CRAFTED.getOrElse(villager, 0) + job.plan().count());
			BuilderLevels.addXp(level, villager, Math.max(1, crafts / 2), null);
		}
		phase = Phase.DELIVERING;
		save(villager);
		walker.reset();
	}

	/**
	 * Takes the plan's ingredients out of the bag and puts what it makes in; returns what didn't fit, or null (and nothing
	 * changed) if some ingredients are missing.
	 */
	@Nullable
	static List<ItemStack> apply(BuilderBag bag, Crafting.Plan plan) {
		for (Map.Entry<Item, Integer> e : plan.takes().entrySet()) {
			if (bag.count(e.getKey()) < e.getValue()) {
				return null;
			}
		}
		plan.takes().forEach(bag::remove);
		List<ItemStack> extra = new ArrayList<>();
		plan.makes().forEach((item, n) -> {
			int over = bag.addAll(item, n);
			if (over > 0) {
				extra.add(new ItemStack(item, over));
			}
		});
		// Buckets and bottles left over from ingredients (milk, honey...) come back too.
		plan.takes().forEach((item, n) -> {
			Item rest = item.getCraftingRemainingItem();
			if (rest != null) {
				int over = bag.addAll(rest, n);
				if (over > 0) {
					extra.add(new ItemStack(rest, over));
				}
			}
		});
		return extra;
	}

	/** The partner-show cue for this trade's crafting (ROADMAP 28.3): {@code craft} unless a trade has its own. */
	protected String partnerCue() {
		return "craft";
	}

	/** Takes everything in the bag to the job's chests (or our own, if there are none any more). */
	private void deliver(ServerLevel level, Villager villager, BlockPos station, BuilderBag bag) {
		status(villager, Phase.DELIVERING);
		List<BlockPos> chests = SupplyContainers.find(level, job.deliverTo(), job.area());
		if (chests.isEmpty()) {
			job = null;
			save(villager);
			return; // tidy() takes it home
		}
		if (!walker.walkTo(level, villager, chests.get(0), REACH)) {
			return;
		}
		int made = 0;
		for (ItemStack stack : bag.takeAll()) {
			if (stack.is(job.plan().target())) {
				made += stack.getCount();
			}
			ItemStack rest = SupplyContainers.insert(level, chests, finished(level, stack));
			if (!rest.isEmpty()) {
				Block.popResource(level, chests.get(0).above(), rest);
			}
		}
		villager.swing(InteractionHand.MAIN_HAND);
		level.playSound(null, chests.get(0), SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.4f, 1.1f);
		delivered(level, villager, job, made);
		if (!job.claim().isEmpty()) {
			synchronized (CLAIMS) {
				CLAIMS.remove(job.claim()); // the builder checks again, and asks for more if it wasn't enough
			}
		}
		job = null;
		phase = Phase.IDLE;
		save(villager);
		lookTimer = 20;
		walker.reset();
	}

	/** {@code job} was carried to its chests with {@code made} of what it makes (a trade may count it off a request: 38.8a). */
	protected void delivered(ServerLevel level, Villager villager, Job job, int made) {
	}

	/** Puts what's left in the bag (from a job that fell through) in the chests by our workstation. */
	private void tidy(ServerLevel level, Villager villager, BlockPos station, BuilderBag bag) {
		status(villager, Phase.TIDYING);
		List<BlockPos> chests = SupplyContainers.find(level, station, null);
		BlockPos target = chests.isEmpty() ? station : chests.get(0);
		if (!walker.walkTo(level, villager, target, REACH)) {
			return;
		}
		for (ItemStack stack : bag.takeAll()) {
			ItemStack rest = chests.isEmpty() ? stack : SupplyContainers.insert(level, chests, stack);
			if (!rest.isEmpty()) {
				Block.popResource(level, station.above(), rest);
			}
		}
		walker.reset();
	}

	/** The first builder near our workstation waiting for something we can make from what they can get at. */
	@Nullable
	protected Job choose(ServerLevel level, Villager villager, BlockPos station) {
		int radius = Math.max(0, Village.RADIUS);
		if (radius == 0) {
			return chooseOrder(level, villager, station);
		}
		long now = level.getGameTime();
		synchronized (CLAIMS) {
			CLAIMS.values().removeIf(until -> until < now);
		}
		List<Villager> builders = level.getEntitiesOfClass(Villager.class, new AABB(station).inflate(radius + 16),
			v -> v.isAlive() && Builders.isBuilder(v) && Builders.benchPos(v).map(p -> p.closerThan(station, radius)).orElse(false));
		builders.sort((a, b) -> Double.compare(a.distanceToSqr(villager), b.distanceToSqr(villager)));
		for (Villager builder : builders) {
			BuildSite site = Builders.activeSite(level, builder);
			// B636: a builder short of something is served while he builds on, not only once he stands waiting
			if (site == null || site.isDone() || site.isDeconstruction() || site.missing().isEmpty()) {
				continue;
			}
			BuildPlan plan = site.plan(level);
			BlockPos builderStation = Builders.benchPos(builder).orElse(null);
			if (plan == null || builderStation == null) {
				continue;
			}
			BoundingBox area = plan.supplyExclude(); // B261: none for a repair
			// The builder's chests and village-mates' first; another builder's last, and only what its builds can spare (B33).
			List<BlockPos> sources = new ArrayList<>(SupplyContainers.find(level, builderStation, area));
			List<BlockPos> theirs = new ArrayList<>();
			Map<BlockPos, Guard> guarded = new HashMap<>();
			Map<Item, Long> stock = new HashMap<>();
			for (Village.Stash stash : Village.stashes(level, builder, builderStation, area)) {
				if (stash.job() != ModVillagers.BUILDER) {
					sources.addAll(stash.chests());
					continue;
				}
				Guard guard = new Guard(stash.station(), stash.chests(), site);
				Map<Item, Integer> keep = Builders.reservedAt(level, stash.station(), site);
				Map<Item, Long> held = SupplyContainers.contents(level, stash.chests());
				Map<Item, Long> heldByFamily = new HashMap<>();
				held.forEach((item, n) -> heldByFamily.merge(MaterialFamilies.key(item), n, Long::sum));
				held.forEach((item, n) -> {
					Item key = MaterialFamilies.key(item);
					long spare = Math.min(n, heldByFamily.get(key) - keep.getOrDefault(key, 0));
					if (spare > 0) {
						stock.merge(item, spare, Long::sum);
					}
				});
				for (BlockPos chest : stash.chests()) {
					guarded.put(chest, guard);
				}
				theirs.addAll(stash.chests());
			}
			SupplyContainers.contents(level, sources).forEach((item, n) -> stock.merge(item, n, Long::sum));
			sources.addAll(theirs);
			Map<Item, Integer> need = Builders.remainingNeed(level, site, plan);
			Map<Item, Long> usable = new HashMap<>();
			stock.forEach((item, n) -> {
				long spare = n - need.getOrDefault(MaterialFamilies.key(item), 0);
				if (spare > 0) {
					usable.put(item, spare);
				}
			});
			List<Map.Entry<Item, Integer>> missing = new ArrayList<>(site.missing().entrySet());
			missing.sort(Map.Entry.<Item, Integer>comparingByValue().reversed());
			for (Map.Entry<Item, Integer> want : missing) {
				if (!wants(want.getKey())) {
					continue;
				}
				String claim = builder.getUUID() + "|" + BuiltInRegistries.ITEM.getKey(want.getKey());
				synchronized (CLAIMS) {
					if (CLAIMS.containsKey(claim)) {
						if (chains()) {
							JobShortages.covered(site.id(), want.getKey(), now); // another of the village's jobs is on it
						}
						continue;
					}
				}
				Crafting.Plan made = planFor(level, want.getKey(), want.getValue(), usable);
				if (!fits(made)) {
					if (chains() && !Crafting.planningSpent(level)) {
						Map<Item, Integer> lack = JobShortages.positive(lackFor(level, want.getKey(), want.getValue(), usable));
						if (!lack.isEmpty()) {
							JobShortages.lacks(site.id(), want.getKey(), JobShortages.lackLine(lack, want.getValue(), want.getKey()), now);
						}
					}
					continue;
				}
				if (chains()) {
					JobShortages.covered(site.id(), want.getKey(), now);
				}
				synchronized (CLAIMS) {
					CLAIMS.put(claim, now + CLAIM_TICKS);
				}
				return new Job(builderStation, area, made, claim, sources, guarded);
			}
		}
		return chooseOrder(level, villager, station);
	}

	/**
	 * A stock order to fill (see {@link io.github.jcondedata.aliveworkplace.store.StockOrders}): the first thing a storehouse
	 * near our workstation is short of that we can make from what's in its store, without dipping into what's kept for
	 * its other orders. Only storehouses our employer shares with (an unhired crafter serves any).
	 */
	@Nullable
	protected Job chooseOrder(ServerLevel level, Villager villager, BlockPos station) {
		long now = level.getGameTime();
		io.github.jcondedata.aliveworkplace.build.Employer boss = ModAttachments.BUILDER_EMPLOYER.get(villager);
		List<BlockPos> storehouses = level.getPoiManager().findAll(h -> h.is(io.github.jcondedata.aliveworkplace.registry.ModVillagers.STOREHOUSE_POI),
				p -> true, station, io.github.jcondedata.aliveworkplace.store.StockOrders.RANGE, net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY)
			.map(BlockPos::immutable).sorted(java.util.Comparator.comparingDouble(p -> p.distSqr(station))).toList();
		Job wonder = chooseWonderOrder(level, station, storehouses, now);
		if (wonder != null) {
			return wonder; // 35.7: a Wonder's goods first
		}
		for (BlockPos storehouse : storehouses) {
			Map<Item, Integer> orders = io.github.jcondedata.aliveworkplace.store.StockOrders.of(level, storehouse);
			if (orders.isEmpty() || boss != null && !Village.sameSide(level, boss, io.github.jcondedata.aliveworkplace.store.Porters.owner(level, storehouse))) {
				continue;
			}
			List<BlockPos> store = SupplyContainers.find(level, storehouse, null);
			if (store.isEmpty()) {
				continue;
			}
			Map<Item, Long> stock = held(level, store);
			Map<Item, Long> usable = new HashMap<>(usableIn(level, store));
			orders.forEach((item, keep) -> usable.computeIfPresent(item, (k, n) -> n > keep ? n - keep : null));
			for (Map.Entry<Item, Integer> order : orders.entrySet()) {
				long have = stock.getOrDefault(order.getKey(), 0L);
				if (have >= order.getValue() || !wants(order.getKey())) {
					continue;
				}
				String claim = "stock|" + storehouse.asLong() + "|" + BuiltInRegistries.ITEM.getKey(order.getKey());
				if (claimed(claim, now)) {
					continue;
				}
				Crafting.Plan plan = planFor(level, order.getKey(), (int) Math.min(64, order.getValue() - have), usable);
				if (!fits(plan)) {
					continue;
				}
				claim(claim, now);
				return new Job(storehouse, null, plan, claim, store);
			}
		}
		return null;
	}

	/**
	 * A Wonder of the village short of goods this crafter makes (ROADMAP 35.7): a stock order for its stockpile, the chests
	 * by its Foundation Stone, made from what's in our own chests and the storehouses near (never what their orders keep).
	 */
	@Nullable
	private Job chooseWonderOrder(ServerLevel level, BlockPos station, List<BlockPos> storehouses, long now) {
		List<io.github.jcondedata.aliveworkplace.wonder.WonderGoods.Order> orders = io.github.jcondedata.aliveworkplace.wonder.WonderGoods.orders(level, station);
		if (orders.isEmpty()) {
			return null;
		}
		List<BlockPos> sources = new ArrayList<>(SupplyContainers.find(level, station, null));
		Map<Item, Integer> keep = new HashMap<>();
		for (BlockPos storehouse : storehouses) {
			for (BlockPos chest : SupplyContainers.find(level, storehouse, null)) {
				if (!sources.contains(chest)) {
					sources.add(chest);
				}
			}
			io.github.jcondedata.aliveworkplace.store.StockOrders.of(level, storehouse).forEach((item, n) -> keep.merge(item, n, Integer::sum));
		}
		for (io.github.jcondedata.aliveworkplace.wonder.WonderGoods.Order order : orders) {
			if (!wants(order.item())) {
				continue;
			}
			List<BlockPos> stockpile = SupplyContainers.find(level, order.stone(), null);
			List<BlockPos> from = sources.stream().filter(p -> !stockpile.contains(p)).toList();
			if (from.isEmpty()) {
				continue;
			}
			String claim = "wonder|" + order.stone().asLong() + "|" + order.line();
			if (claimed(claim, now)) {
				continue;
			}
			Map<Item, Long> usable = new HashMap<>(usableIn(level, from));
			keep.forEach((item, n) -> usable.computeIfPresent(item, (k, have) -> have > n ? have - n : null));
			// as many as there are makings for, up to what's short
			for (int count = Math.min(64, order.count()); count >= 1; count /= 2) {
				Crafting.Plan plan = planFor(level, order.item(), count, usable);
				if (fits(plan)) {
					claim(claim, now);
					return new Job(order.stone(), null, plan, claim, from);
				}
			}
		}
		return null;
	}

	/** Whether a stack in a chest may be fetched as an ingredient (plain items only, unless a trade says otherwise). */
	protected boolean fetchable(ServerLevel level, ItemStack stack) {
		return stack.getComponentsPatch().isEmpty();
	}

	/** Takes up to {@code max} of {@code item} out of {@code chest} as an ingredient; returns how many were taken. */
	protected int takeFrom(ServerLevel level, BlockPos chest, Item item, int max) {
		return SupplyContainers.extract(level, List.of(chest), item, max);
	}

	/** How long making {@code plan} ({@code crafts} crafts) takes this villager. */
	protected int ticksFor(Villager villager, Crafting.Plan plan, int crafts) {
		return craftTicks(villager, crafts);
	}

	/** A stack from the bag as it goes into the job's chests (a luxury maker stamps the day an aging good was made). */
	protected ItemStack finished(ServerLevel level, ItemStack stack) {
		return stack;
	}

	/** How many of each thing {@code chests} hold, for a stock order's count. */
	protected Map<Item, Long> held(ServerLevel level, List<BlockPos> chests) {
		return SupplyContainers.contents(level, chests);
	}

	/** How many of each thing in {@code chests} may go into a plan. */
	protected Map<Item, Long> usableIn(ServerLevel level, List<BlockPos> chests) {
		return SupplyContainers.contents(level, chests);
	}

	/** Whether this crafter makes {@code item} for builders (everything its recipes make, unless a job says otherwise). */
	protected boolean wants(Item item) {
		return true;
	}

	/** How to make {@code count} of {@code item} from {@code usable} (the recipes of this crafter's kind), or null. */
	@Nullable
	protected Crafting.Plan planFor(ServerLevel level, Item item, int count, Map<Item, Long> usable) {
		if (!chains()) {
			return Crafting.plan(level, kind, item, count, usable);
		}
		return chainPlan(level, kind, item, count, usable);
	}

	/** A whole chain of {@code kind} for as many as there are makings for, a bagful at a time (all, half... down to one). */
	@Nullable
	protected static Crafting.Plan chainPlan(ServerLevel level, Crafting.Kind kind, Item item, int count, Map<Item, Long> usable) {
		Crafting.Plan last = null;
		for (int n = Math.min(Math.max(1, count), Crafting.MAX_CRAFTS); n >= 1; n /= 2) {
			Crafting.Plan plan = Crafting.chain(level, kind, item, n, usable);
			if (plan == null && Crafting.planningSpent(level)) {
				return null; // the tick's planning is used up: asked again next time
			}
			if (fits(plan)) {
				return plan;
			}
			last = plan == null ? last : plan;
		}
		return last;
	}

	/**
	 * The raw materials short of making {@code count} of {@code item} from {@code usable} with this job's recipes (37.14a),
	 * for the builder's shortage line; empty if nothing of this job's makes it.
	 */
	protected Map<Item, Integer> lackFor(ServerLevel level, Item item, int count, Map<Item, Long> usable) {
		if (Crafting.recipesFor(level, kind, item).isEmpty()) {
			return Map.of();
		}
		return Crafting.lacking(level, kind, item, Math.min(Math.max(1, count), Crafting.MAX_CRAFTS), usable);
	}

	/** Whether another crafter is on {@code claim} (a request they took); forgets claims that ran out. */
	static boolean claimed(String claim, long now) {
		synchronized (CLAIMS) {
			CLAIMS.values().removeIf(until -> until < now);
			return CLAIMS.containsKey(claim);
		}
	}

	/**
	 * Every villager's AI step (mixin/VillagerMixin, B353): a crafter whose workstation has been gone for {@link #BENCHLESS_TICKS}
	 * with a chain job saved lets the job go (its claim too) and the bag goes to the asker's chests, or at their feet when
	 * those are gone or full. A bench put back (or moved) within that time, or a chunk reload, carries on with the job.
	 */
	public static void benchlessTick(Villager villager) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return;
		}
		CrafterJob saved = ModAttachments.CRAFTER_JOB.get(villager);
		if (saved == null || Builders.benchPos(villager).isPresent()) {
			synchronized (BENCHLESS) {
				BENCHLESS.remove(villager);
			}
			return;
		}
		long now = level.getGameTime();
		long since;
		synchronized (BENCHLESS) {
			since = BENCHLESS.computeIfAbsent(villager, v -> now);
		}
		if (now - since < BENCHLESS_TICKS) {
			return;
		}
		synchronized (BENCHLESS) {
			BENCHLESS.remove(villager);
		}
		if (!saved.claim().isEmpty()) {
			release(saved.claim());
		}
		ModAttachments.CRAFTER_JOB.remove(villager);
		BuilderBag bag = ModAttachments.BUILDER_BAG.get(villager);
		if (bag == null || bag.isEmpty()) {
			return;
		}
		List<BlockPos> chests = level.isLoaded(saved.deliverTo()) ? SupplyContainers.find(level, saved.deliverTo(), saved.areaOrNull()) : List.of();
		for (ItemStack stack : bag.takeAll()) {
			ItemStack rest = chests.isEmpty() ? stack : SupplyContainers.insert(level, chests, stack);
			if (!rest.isEmpty()) {
				Block.popResource(level, villager.blockPosition(), rest);
			}
		}
	}

	/** How long a crafter's saved job outlasts its workstation (B353): time to put a bench back or move it. */
	static final long BENCHLESS_TICKS = 600;
	private static final Map<Villager, Long> BENCHLESS = new WeakHashMap<>();

	/** Lets {@code claim} go (the job is done or fell through): the asker is looked at again (the Crafter, 37.14). */
	static void release(String claim) {
		synchronized (CLAIMS) {
			CLAIMS.remove(claim);
		}
	}

	/** Takes {@code claim}, so other crafters leave that request alone for a while. */
	static void claim(String claim, long now) {
		synchronized (CLAIMS) {
			CLAIMS.put(claim, now + CLAIM_TICKS);
		}
	}

	/**
	 * {@code plan} (of {@code kind}) with a coal or charcoal for every {@code perFuel} things it fires; null if there's too
	 * little fuel in {@code usable}. A plan that fires nothing comes back as it is.
	 */
	@Nullable
	protected static Crafting.Plan withFuel(ServerLevel level, Crafting.Plan plan, Map<Item, Long> usable, Crafting.Kind kind, int perFuel) {
		int fired = 0;
		for (Crafting.Step step : plan.steps()) {
			if (Crafting.isFired(level, kind, step)) {
				fired += step.times();
			}
		}
		if (fired == 0) {
			return plan;
		}
		int fuel = (fired + perFuel - 1) / perFuel;
		Map<Item, Integer> takes = new LinkedHashMap<>(plan.takes());
		return addFuel(takes, usable, fuel) ? new Crafting.Plan(plan.target(), plan.count(), plan.steps(), takes, plan.makes()) : null;
	}

	/** The fuels a kiln or a forge burns, in the order they're taken. */
	protected static final List<Item> FUELS = List.of(net.minecraft.world.item.Items.COAL, net.minecraft.world.item.Items.CHARCOAL);

	/**
	 * Adds {@code fuel} coal and charcoal to {@code takes}, coal first, the two kinds mixed when neither alone is enough
	 * (B396), from what {@code usable} has beyond what {@code takes} already takes; false (and {@code takes} unchanged) if
	 * both together are too few.
	 */
	protected static boolean addFuel(Map<Item, Integer> takes, Map<Item, Long> usable, int fuel) {
		if (fuel <= 0) {
			return true;
		}
		if (fuelSpare(takes, usable) < fuel) {
			return false;
		}
		int left = fuel;
		for (Item coal : FUELS) {
			long spare = Math.max(0, usable.getOrDefault(coal, 0L) - takes.getOrDefault(coal, 0));
			int got = (int) Math.min(left, spare);
			if (got > 0) {
				takes.merge(coal, got, Integer::sum);
				left -= got;
			}
		}
		return true;
	}

	/** Coal and charcoal together in {@code usable} beyond what {@code takes} already takes. */
	protected static long fuelSpare(Map<Item, Integer> takes, Map<Item, Long> usable) {
		long spare = 0;
		for (Item coal : FUELS) {
			spare += Math.max(0, usable.getOrDefault(coal, 0L) - takes.getOrDefault(coal, 0));
		}
		return spare;
	}

	/**
	 * The coal short of firing what {@code plan} (of {@code kind}, a {@link Crafting#shortfall} plan) fires, a coal or
	 * charcoal for every {@code perFuel}, the two mixed (B396); 0 if it fires nothing or there's enough. A forge's or a
	 * kiln's fuel is a raw material of its chain, so the builder's shortage line can name it (B352, B408).
	 */
	protected static int fuelShort(ServerLevel level, @Nullable Crafting.Plan plan, Crafting.Kind kind, Map<Item, Long> usable, int perFuel) {
		if (plan == null) {
			return 0;
		}
		int fired = 0;
		for (Crafting.Step step : plan.steps()) {
			if (Crafting.isFired(level, kind, step)) {
				fired += step.times();
			}
		}
		int fuel = (fired + perFuel - 1) / perFuel;
		return (int) Math.max(0, fuel - fuelSpare(Map.of(), usable));
	}

	/**
	 * What's short of making {@code count} of {@code item} with {@code kind}'s recipes, as {@link Crafting#lacking}, with
	 * the coal its firing needs counted too ({@link #fuelShort}): ore that's all there but can't be fired names the coal.
	 */
	protected static Map<Item, Integer> lackingWithFuel(ServerLevel level, Crafting.Kind kind, Item item, int count, Map<Item, Long> usable, int perFuel) {
		Crafting.Plan plan = Crafting.shortfall(level, kind, item, count, usable);
		Map<Item, Integer> lack = new LinkedHashMap<>(Crafting.lacks(plan));
		int coal = fuelShort(level, plan, kind, usable, perFuel);
		if (coal > 0) {
			lack.merge(net.minecraft.world.item.Items.COAL, coal, Integer::sum);
		}
		return lack;
	}

	/** Whether {@code plan} (of {@code kind}) fires anything. */
	protected static boolean fires(ServerLevel level, Crafting.Plan plan, Crafting.Kind kind) {
		return plan.steps().stream().anyMatch(step -> Crafting.isFired(level, kind, step));
	}

	/** Whether a plan is worth doing and fits in the bag. */
	protected static boolean fits(@Nullable Crafting.Plan plan) {
		return plan != null && plan.count() > 0 && stacks(plan.takes()) <= MAX_STACKS_IN && stacks(plan.makes()) <= BuilderBag.SLOTS - MAX_STACKS_IN;
	}

	private static int stacks(Map<Item, Integer> items) {
		int n = 0;
		for (Map.Entry<Item, Integer> e : items.entrySet()) {
			int max = Math.max(1, e.getKey().getDefaultMaxStackSize());
			n += (e.getValue() + max - 1) / max;
		}
		return n;
	}

	private static void setBusy(Villager villager, boolean busy) {
		synchronized (BUSY) {
			if (busy) {
				BUSY.add(villager);
			} else {
				BUSY.remove(villager);
			}
		}
	}

	/** The line shown while there's nothing to make, in place of the usual one (null: the usual). */
	@Nullable
	protected Component idleLine(Villager villager) {
		return null;
	}

	private void status(Villager villager, Phase phase) {
		if (phase == Phase.IDLE && !alwaysShowStatus) {
			return;
		}
		Component title = Words.countedFirst("message.aliveworkplace." + who + ".title", ModAttachments.ITEMS_CRAFTED.getOrElse(villager, 0));
		Component idle = phase == Phase.IDLE && job == null ? idleLine(villager) : null;
		Component line = idle != null ? idle : job != null && phase != Phase.TIDYING
			? (phase == Phase.FETCHING // "fetching what 1 Amethyst Ring takes"
				? io.github.jcondedata.aliveworkplace.work.Words.counted("message.aliveworkplace." + lines + ".state.fetching", job.plan().count(), job.plan().count(),
					job.plan().target().getDescription())
				: Component.translatable("message.aliveworkplace." + lines + ".state." + phase.name().toLowerCase(), job.plan().count(),
					job.plan().target().getDescription()))
			: Component.translatable("message.aliveworkplace." + lines + ".state." + phase.name().toLowerCase());
		if (phase == Phase.CRAFTING && stepwise && job != null && job.plan().steps().size() > 1) {
			// 37.14a: "making 3 Barrel (step 2 of 3)"
			line = Component.translatable("message.aliveworkplace.job_chain.step", line, Math.min(step + 1, job.plan().steps().size()), job.plan().steps().size());
		}
		WorkerStatus.set(villager, title, -1f, line.copy().withStyle(ChatFormatting.GRAY));
	}
}
