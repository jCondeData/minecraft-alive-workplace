package io.github.jcondedata.aliveworkplace.craft;

import com.google.common.collect.ImmutableMap;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Requests;
import io.github.jcondedata.aliveworkplace.work.Walker;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * A Crafter's shift (ROADMAP 37.14). They look for what the village's workers are short of: first a builder's missing
 * materials, then the Storehouse's stock orders and a Wonder's goods (as the Carpenter does), then what any other worker
 * nearby is waiting for (a miner's ladders, a farmer's composter). They plan the whole chain with the crafting table's
 * and the stonecutter's recipes, and a furniture mod's bench's while it stands in the workshop ({@link CrafterBenches}):
 * for a barrel, logs into planks, planks into slabs, then the barrel. Everything the chain needs is checked before a
 * thing is taken, so a raw material that's short is said ("short of 2 Oak Log to make 1 Barrel") and nothing is
 * wasted. The makings come from the asker's chests and the village's stores only (never a chest outside it); each step
 * is made in turn at the workbench, and what's made goes in the chests the asker takes from. The job is saved on the
 * villager ({@link CrafterJob}), so a chain half made carries on after a restart; what doesn't fit the bag, or a job
 * that falls through, ends in the chests it came from. A big order is made a bagful at a time until there's enough.
 */
public class CrafterShift extends Behavior<Villager> {
	private static final float SPEED = 0.55f;
	private static final double REACH = 3.0;
	private static final int LOOK_EVERY = 100;
	/** Base ticks one step takes, per craft (faster with levels). */
	public static final int STEP_TICKS = 6;

	private final Walker walker = new Walker(SPEED);
	private final Finder finder = new Finder();
	/** The job as chosen, with the other builders' chests it may only take their spare from (not saved: see fetch). */
	@Nullable
	private CrafterWork.Job chosen;
	private int timer = -1;
	private int lookTimer;

	public CrafterShift() {
		super(ImmutableMap.of(
			MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 1200);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return Crafters.ENABLED && !villager.isSleeping() && !villager.isBaby() && Builders.benchPos(villager).isPresent();
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return checkExtraStartConditions(level, villager);
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		walker.reset();
		lookTimer = 0;
		timer = -1;
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		BlockPos station = Builders.benchPos(villager).orElse(null);
		if (station == null) {
			return;
		}
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(villager);
		CrafterJob job = ModAttachments.CRAFTER_JOB.get(villager);
		if (job == null) {
			chosen = null;
			if (!bag.isEmpty()) {
				putAway(level, villager, SupplyContainers.find(level, station, null), station, bag);
				return;
			}
			if (--lookTimer > 0) {
				return;
			}
			lookTimer = LOOK_EVERY;
			job = choose(level, villager, station);
			if (job == null) {
				Component lack = Crafters.lacking(villager);
				status(villager, lack != null ? lack : Component.translatable("message.aliveworkplace.village_crafter.state.idle"));
				return;
			}
			ModAttachments.CRAFTER_JOB.set(villager, job);
			walker.reset();
		}
		switch (job.phase()) {
			case FETCHING -> fetch(level, villager, job, bag);
			case CRAFTING -> craft(level, villager, station, job, bag);
			default -> deliver(level, villager, station, job, bag);
		}
	}

	/** The first thing the workers nearby are short of that can be made from what the village holds, as a job. */
	@Nullable
	private CrafterJob choose(ServerLevel level, Villager villager, BlockPos station) {
		finder.start(level, station);
		CrafterWork.Job found = finder.choose(level, villager, station);
		if (found != null) {
			chosen = found;
			Crafters.setLacking(villager, null);
			// saved: only the chests it may take anything from (after a restart another builder's spare isn't known)
			List<BlockPos> sources = found.sources().stream().filter(p -> !found.guarded().containsKey(p)).toList();
			return CrafterJob.of(found.deliverTo(), found.area(), found.plan(), sources, found.claim());
		}
		CrafterJob asked = chooseRequest(level, villager, station);
		if (asked != null) {
			Crafters.setLacking(villager, null);
			return asked;
		}
		if (finder.lack != null) {
			Crafters.setLacking(villager, finder.lack);
		}
		return null;
	}

	/**
	 * What another worker is waiting for (37.14, "then other workers' needs"): a miner's ladders, a lumberjack's axe, made
	 * from our own chests and the Storehouse's, for the chests by their workstation.
	 */
	@Nullable
	private CrafterJob chooseRequest(ServerLevel level, Villager villager, BlockPos station) {
		long now = level.getGameTime();
		List<BlockPos> sources = new ArrayList<>(SupplyContainers.find(level, station, null));
		level.getPoiManager().findAll(h -> h.is(io.github.jcondedata.aliveworkplace.registry.ModVillagers.STOREHOUSE_POI), p -> true, station,
				io.github.jcondedata.aliveworkplace.store.StockOrders.RANGE, net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY)
			.forEach(storehouse -> SupplyContainers.find(level, storehouse.immutable(), null).forEach(chest -> {
				if (!sources.contains(chest)) {
					sources.add(chest);
				}
			}));
		for (Requests.Request request : Requests.near(level, station, ModAttachments.BUILDER_EMPLOYER.get(villager))) {
			if (request.forBlock() || request.worker() == null || request.worker() == villager || Builders.isBuilder(request.worker())) {
				continue; // builders are asked after through their sites, above
			}
			Item item = request.item() != null ? request.item() : request.icon().getItem();
			if (!request.accepts().test(new ItemStack(item))) {
				continue;
			}
			String claim = "asked|" + request.worker().getUUID() + "|" + BuiltInRegistries.ITEM.getKey(item);
			if (CrafterWork.claimed(claim, now)) {
				continue;
			}
			List<BlockPos> theirs = SupplyContainers.find(level, request.station(), null);
			List<BlockPos> from = sources.stream().filter(p -> !theirs.contains(p)).toList();
			Crafting.Plan plan = finder.planFor(level, item, Math.max(1, request.count()), SupplyContainers.contents(level, from));
			if (plan == null) {
				continue;
			}
			CrafterWork.claim(claim, now);
			return CrafterJob.of(request.station(), null, plan, from, claim);
		}
		return null;
	}

	/** Walks round the job's chests for its makings; if they're gone, everything taken goes back and the job is dropped. */
	private void fetch(ServerLevel level, Villager villager, CrafterJob job, BuilderBag bag) {
		Map<Item, Integer> toFetch = new LinkedHashMap<>();
		job.takes().forEach((item, n) -> {
			int left = n - bag.count(item);
			if (left > 0) {
				toFetch.put(item, left);
			}
		});
		status(villager, Component.translatable("message.aliveworkplace.village_crafter.state.fetching", job.count(), job.target().getDescription()));
		if (toFetch.isEmpty()) {
			ModAttachments.CRAFTER_JOB.set(villager, job.withPhase(CrafterJob.Phase.CRAFTING));
			timer = -1;
			walker.reset();
			return;
		}
		Map<CrafterWork.Guard, Map<Item, Integer>> reserved = new HashMap<>();
		BlockPos chest = null;
		for (BlockPos source : chosen != null ? chosen.sources() : job.sources()) {
			if (SupplyContainers.firstMatching(level, List.of(source), s -> s.getComponentsPatch().isEmpty() && toFetch.containsKey(s.getItem())
				&& allowance(level, source, reserved, s.getItem()) > 0) != null) {
				chest = source;
				break;
			}
		}
		if (chest == null) {
			// someone took the makings meanwhile: what was fetched goes back where it came from, and nothing is used
			List<BlockPos> back = chosen != null ? chosen.sources() : job.sources();
			for (ItemStack stack : bag.takeAll()) {
				ItemStack rest = back.isEmpty() ? stack : SupplyContainers.insert(level, back, stack);
				if (!rest.isEmpty()) {
					bag.add(rest); // no room: put away in our own chests next
				}
			}
			Crafters.setLacking(villager, lackLine(toFetch, job.count(), job.target()));
			drop(villager, job);
			return;
		}
		if (!walker.walkTo(level, villager, chest, REACH)) {
			return;
		}
		for (Map.Entry<Item, Integer> e : toFetch.entrySet()) {
			int allowed = (int) Math.min(e.getValue(), allowance(level, chest, reserved, e.getKey()));
			int got = allowed <= 0 ? 0 : SupplyContainers.extract(level, List.of(chest), e.getKey(), allowed);
			if (got > 0) {
				int over = bag.addAll(e.getKey(), got);
				if (over > 0) {
					SupplyContainers.insert(level, List.of(chest), new ItemStack(e.getKey(), over));
				}
			}
		}
		villager.swing(InteractionHand.MAIN_HAND);
		level.playSound(null, chest, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.4f, 1.1f);
		walker.reset();
	}

	/** How many of {@code item} may be taken from {@code chest}: all, or from another builder's only their spare. */
	private long allowance(ServerLevel level, BlockPos chest, Map<CrafterWork.Guard, Map<Item, Integer>> reserved, Item item) {
		CrafterWork.Guard guard = chosen == null ? null : chosen.guarded().get(chest);
		if (guard == null) {
			return Long.MAX_VALUE;
		}
		Map<Item, Integer> keep = reserved.computeIfAbsent(guard, g -> Builders.reservedAt(level, g.station(), g.except()));
		return Builders.spare(level, guard.chests(), item, keep);
	}

	/** At the workbench: makes the chain's next step, then the next, each in its own time. */
	private void craft(ServerLevel level, Villager villager, BlockPos station, CrafterJob job, BuilderBag bag) {
		if (job.step() >= job.steps().size()) {
			ModAttachments.CRAFTER_JOB.set(villager, job.withPhase(CrafterJob.Phase.DELIVERING));
			walker.reset();
			return;
		}
		Crafting.Step step = job.steps().get(job.step());
		status(villager, Component.translatable("message.aliveworkplace.village_crafter.state.crafting", step.out().getCount() * step.times(),
			step.out().getHoverName(), job.step() + 1, job.steps().size()));
		if (!walker.walkTo(level, villager, station, 2.5)) {
			return;
		}
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(station));
		if (timer < 0) {
			timer = Math.max(20, BuilderLevels.delay(STEP_TICKS * step.times(), villager));
		}
		if (timer % 10 == 0) {
			villager.swing(InteractionHand.MAIN_HAND);
			boolean cut = step.in().size() == 1 && Crafting.recipesFor(level, Crafting.Kind.STONECUTTING, step.out().getItem()).stream()
				.anyMatch(h -> h.value().getType() == RecipeType.STONECUTTING);
			level.playSound(null, station, cut ? SoundEvents.UI_STONECUTTER_TAKE_RESULT : SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.5f,
				0.9f + level.random.nextFloat() * 0.2f);
		}
		if (--timer > 0) {
			return;
		}
		timer = -1;
		List<ItemStack> extra = makeStep(bag, step);
		if (extra == null) {
			// the makings aren't in the bag (a bag lost to a crash): nothing more can be made, what there is goes to the asker
			ModAttachments.CRAFTER_JOB.set(villager, job.withPhase(CrafterJob.Phase.DELIVERING));
			return;
		}
		extra.forEach(stack -> Block.popResource(level, station.above(), stack));
		BuilderLevels.addXp(level, villager, Math.max(1, step.times() / 4), null);
		ModAttachments.CRAFTER_JOB.set(villager, job.nextStep());
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

	/** Takes everything in the bag to the chests the asker takes from. */
	private void deliver(ServerLevel level, Villager villager, BlockPos station, CrafterJob job, BuilderBag bag) {
		status(villager, Component.translatable("message.aliveworkplace.village_crafter.state.delivering", job.count(), job.target().getDescription()));
		List<BlockPos> chests = SupplyContainers.find(level, job.deliverTo(), job.areaOrNull());
		if (chests.isEmpty()) {
			drop(villager, job); // the asker's chests are gone: the bag goes to our own
			return;
		}
		if (!walker.walkTo(level, villager, chests.get(0), REACH)) {
			return;
		}
		int made = 0;
		for (ItemStack stack : bag.takeAll()) {
			if (stack.is(job.target())) {
				made += stack.getCount();
			}
			ItemStack rest = SupplyContainers.insert(level, chests, stack);
			if (!rest.isEmpty()) {
				Block.popResource(level, chests.get(0).above(), rest);
			}
		}
		villager.swing(InteractionHand.MAIN_HAND);
		level.playSound(null, chests.get(0), SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.4f, 1.1f);
		ModAttachments.ITEMS_CRAFTED.set(villager, ModAttachments.ITEMS_CRAFTED.getOrElse(villager, 0) + made);
		drop(villager, job);
		lookTimer = 20; // straight on to the next (a big order is made a bagful at a time)
	}

	/** The job is over: the request is let go, and anything still in the bag is put away. */
	private void drop(Villager villager, CrafterJob job) {
		if (!job.claim().isEmpty()) {
			CrafterWork.release(job.claim());
		}
		ModAttachments.CRAFTER_JOB.remove(villager);
		chosen = null;
		timer = -1;
		walker.reset();
	}

	/** Puts the bag in {@code chests} (our own, by the workbench), or drops it by the workbench if there are none. */
	private void putAway(ServerLevel level, Villager villager, List<BlockPos> chests, BlockPos station, BuilderBag bag) {
		status(villager, Component.translatable("message.aliveworkplace.village_crafter.state.tidying"));
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

	/** "short of 2 Oak Log, 1 Stick to make 4 Barrel". */
	static Component lackLine(Map<Item, Integer> lack, int count, Item target) {
		MutableComponent list = Component.empty();
		boolean first = true;
		for (Map.Entry<Item, Integer> e : lack.entrySet()) {
			if (!first) {
				list.append(", ");
			}
			first = false;
			list.append(Component.translatable("message.aliveworkplace.village_crafter.lack_item", e.getValue(), e.getKey().getDescription()));
		}
		return Component.translatable("message.aliveworkplace.village_crafter.lacks", list, count, target.getDescription());
	}

	private static void status(Villager villager, Component line) {
		Component title = Component.translatable("message.aliveworkplace.village_crafter.title", ModAttachments.ITEMS_CRAFTED.getOrElse(villager, 0));
		WorkerStatus.set(villager, title, -1f, line.copy().withStyle(ChatFormatting.GRAY));
	}

	/**
	 * The Carpenter's way of finding what the builders and the Storehouse want ({@link CrafterWork#choose}), planned as a
	 * Crafter plans: whole chains, a bagful at a time; it notes what's short of the first thing it couldn't make.
	 */
	static final class Finder extends CrafterWork {
		private Predicate<RecipeType<?>> types = t -> true;
		@Nullable
		Component lack;

		Finder() {
			super(Crafting.Kind.CRAFTER, "village_crafter", "village_crafter", true);
		}

		void start(ServerLevel level, BlockPos station) {
			types = CrafterBenches.usableAt(level, station);
			lack = null;
		}

		@Nullable
		@Override
		protected Crafting.Plan planFor(ServerLevel level, Item item, int count, Map<Item, Long> usable) {
			for (int n = Math.min(count, 64); n >= 1; n /= 2) {
				Crafting.Plan plan = Crafting.chain(level, item, n, usable, types);
				if (plan == null && Crafting.planningSpent(level)) {
					return null; // the tick's planning is used up: asked again next time
				}
				if (fits(plan)) {
					return plan;
				}
			}
			if (lack == null && !Crafting.planningSpent(level)) {
				Map<Item, Integer> short_ = Crafting.lacking(level, item, Math.max(1, count), usable, types);
				if (!short_.isEmpty()) {
					lack = lackLine(short_, Math.max(1, count), item);
				}
			}
			return null;
		}
	}
}
