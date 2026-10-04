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
 */
public class CrafterWork extends Behavior<Villager> {
	/** Base ticks one craft takes (faster with levels and Pokémon partners). */
	public static final int CRAFT_TICKS = 8;
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
			return;
		}
		Map<Guard, Map<Item, Integer>> reserved = new HashMap<>();
		BlockPos chest = null;
		for (BlockPos source : job.sources()) {
			Guard guard = job.guarded().get(source);
			if (SupplyContainers.firstMatching(level, List.of(source), s -> s.getComponentsPatch().isEmpty() && toFetch.containsKey(s.getItem())
				&& allowance(level, guard, reserved, s.getItem()) > 0) != null) {
				chest = source;
				break;
			}
		}
		if (chest == null) {
			phase = Phase.DELIVERING; // someone took them: bring back what we have
			walker.reset();
			return;
		}
		if (!walker.walkTo(level, villager, chest, REACH)) {
			return;
		}
		for (Map.Entry<Item, Integer> e : new ArrayList<>(toFetch.entrySet())) {
			int allowed = (int) Math.min(e.getValue(), allowance(level, job.guarded().get(chest), reserved, e.getKey()));
			int got = allowed <= 0 ? 0 : SupplyContainers.extract(level, List.of(chest), e.getKey(), allowed);
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
		int crafts = job.plan().steps().stream().mapToInt(Crafting.Step::times).sum();
		if (timer < 0) {
			timer = Math.max(20, Math.round(BuilderLevels.delay(CRAFT_TICKS * crafts, villager) * (1f - 0.15f
				* io.github.jcondedata.aliveworkplace.research.Research.level(villager, io.github.jcondedata.aliveworkplace.research.Research.Topic.CRAFTSMANSHIP))));
			// A Fighting, Rock or Steel partner holds the board or stone at the table while it's worked (28.4).
			Item worked = job.plan().takes().keySet().stream().findFirst().orElse(job.plan().target()); // the board, the stone
			io.github.jcondedata.aliveworkplace.work.PartnerShows.cue(villager, "craft", station, new ItemStack(worked));
		}
		if (timer % 10 == 0) {
			villager.swing(InteractionHand.MAIN_HAND);
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
		if (--timer > 0) {
			return;
		}
		List<ItemStack> extra = apply(bag, job.plan());
		if (extra != null) {
			extra.forEach(stack -> Block.popResource(level, station.above(), stack));
			ModAttachments.ITEMS_CRAFTED.set(villager, ModAttachments.ITEMS_CRAFTED.getOrElse(villager, 0) + job.plan().count());
			BuilderLevels.addXp(level, villager, Math.max(1, crafts / 2), null);
		}
		phase = Phase.DELIVERING;
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

	/** Takes everything in the bag to the job's chests (or our own, if there are none any more). */
	private void deliver(ServerLevel level, Villager villager, BlockPos station, BuilderBag bag) {
		status(villager, Phase.DELIVERING);
		List<BlockPos> chests = SupplyContainers.find(level, job.deliverTo(), job.area());
		if (chests.isEmpty()) {
			job = null;
			return; // tidy() takes it home
		}
		if (!walker.walkTo(level, villager, chests.get(0), REACH)) {
			return;
		}
		for (ItemStack stack : bag.takeAll()) {
			ItemStack rest = SupplyContainers.insert(level, chests, stack);
			if (!rest.isEmpty()) {
				Block.popResource(level, chests.get(0).above(), rest);
			}
		}
		villager.swing(InteractionHand.MAIN_HAND);
		level.playSound(null, chests.get(0), SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.4f, 1.1f);
		if (!job.claim().isEmpty()) {
			synchronized (CLAIMS) {
				CLAIMS.remove(job.claim()); // the builder checks again, and asks for more if it wasn't enough
			}
		}
		job = null;
		phase = Phase.IDLE;
		lookTimer = 20;
		walker.reset();
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
			if (site == null || site.status() != BuildSite.Status.WAITING_FOR_MATERIALS || site.missing().isEmpty()) {
				continue;
			}
			BuildPlan plan = site.plan(level);
			BlockPos builderStation = Builders.benchPos(builder).orElse(null);
			if (plan == null || builderStation == null) {
				continue;
			}
			BoundingBox area = plan.bounds();
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
						continue;
					}
				}
				Crafting.Plan made = planFor(level, want.getKey(), want.getValue(), usable);
				if (!fits(made)) {
					continue;
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
		for (BlockPos storehouse : storehouses) {
			Map<Item, Integer> orders = io.github.jcondedata.aliveworkplace.store.StockOrders.of(level, storehouse);
			if (orders.isEmpty() || boss != null && !Village.sameSide(level, boss, io.github.jcondedata.aliveworkplace.store.Porters.owner(level, storehouse))) {
				continue;
			}
			List<BlockPos> store = SupplyContainers.find(level, storehouse, null);
			if (store.isEmpty()) {
				continue;
			}
			Map<Item, Long> stock = SupplyContainers.contents(level, store);
			Map<Item, Long> usable = new HashMap<>(stock);
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

	/** Whether this crafter makes {@code item} for builders (everything its recipes make, unless a job says otherwise). */
	protected boolean wants(Item item) {
		return true;
	}

	/** How to make {@code count} of {@code item} from {@code usable} (the recipes of this crafter's kind), or null. */
	@Nullable
	protected Crafting.Plan planFor(ServerLevel level, Item item, int count, Map<Item, Long> usable) {
		return Crafting.plan(level, kind, item, count, usable);
	}

	/** Whether another crafter is on {@code claim} (a request they took); forgets claims that ran out. */
	static boolean claimed(String claim, long now) {
		synchronized (CLAIMS) {
			CLAIMS.values().removeIf(until -> until < now);
			return CLAIMS.containsKey(claim);
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
		for (Item coal : List.of(net.minecraft.world.item.Items.COAL, net.minecraft.world.item.Items.CHARCOAL)) {
			if (usable.getOrDefault(coal, 0L) - plan.takes().getOrDefault(coal, 0) >= fuel) {
				Map<Item, Integer> takes = new LinkedHashMap<>(plan.takes());
				takes.merge(coal, fuel, Integer::sum);
				return new Crafting.Plan(plan.target(), plan.count(), plan.steps(), takes, plan.makes());
			}
		}
		return null;
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

	private void status(Villager villager, Phase phase) {
		if (phase == Phase.IDLE && !alwaysShowStatus) {
			return;
		}
		Component title = Component.translatable("message.aliveworkplace." + who + ".title", ModAttachments.ITEMS_CRAFTED.getOrElse(villager, 0));
		Component line = job != null && phase != Phase.TIDYING
			? Component.translatable("message.aliveworkplace." + lines + ".state." + phase.name().toLowerCase(), job.plan().count(),
				job.plan().target().getDescription())
			: Component.translatable("message.aliveworkplace." + lines + ".state." + phase.name().toLowerCase());
		WorkerStatus.set(villager, title, -1f, line.copy().withStyle(ChatFormatting.GRAY));
	}
}
