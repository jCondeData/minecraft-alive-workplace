package io.github.jcondedata.aliveworkplace.wood;

import com.google.common.collect.ImmutableMap;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.mine.QuarrySite;
import io.github.jcondedata.aliveworkplace.mine.QuarrySiteManager;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Walker;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * The lumberjack's shift: find a grown tree near the Chopping Block, chop it down with an axe from
 * the chests (the whole tree comes down, leaves and all, like a timber mod), plant a sapling where it
 * stood, and bring the logs back to the chests. Only natural trees (see {@link Trees}) are cut, and
 * never inside a build site or a quarry.
 */
public class LumberjackWork extends Behavior<Villager> {
	/** How far from the Chopping Block the lumberjack looks for trees. */
	public static int RADIUS = 16;
	static final double REACH = 4.0;
	private static final float SPEED = 0.6f;
	private static final int SEARCH_EVERY = 60;
	private static final int KEEP_SAPLINGS = 16;
	/** Bone meal kept in the bag for the saplings. */
	private static final int KEEP_BONE_MEAL = 16;
	/** Bone meal given to one sapling before giving up on it (a lone dark oak sapling never grows). */
	private static final int MAX_FEEDS = 16;
	private static final int FEED_EVERY = 10;

	private enum Phase { LOOKING, CHOPPING, NEEDS_AXE, DEPOSITING, PLANTING, FERTILIZING }

	private final Walker walker = new Walker(SPEED);
	@Nullable
	private BlockPos tree;
	private int searchTimer;
	private int chopProgress;
	private int chopTotal;
	private boolean depositDue;
	/** Stumps still waiting for a sapling (the leaves dropped none): filled from the chests after the next drop-off. */
	private final java.util.Map<BlockPos, Block> unplanted = new java.util.LinkedHashMap<>();
	/** Saplings this lumberjack planted where a tree came down (the farm's are found by looking): bone meal goes to these. */
	private final Set<BlockPos> replanted = new java.util.HashSet<>();
	/** Bone meal given to each sapling so far. */
	private final java.util.Map<BlockPos, Integer> fed = new java.util.HashMap<>();
	@Nullable
	private BlockPos feeding;
	private int feedTimer;

	public LumberjackWork() {
		super(ImmutableMap.of(
			MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 1200);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return !villager.isSleeping() && Builders.benchPos(villager).isPresent();
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return !villager.isSleeping() && Builders.benchPos(villager).isPresent();
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		walker.reset();
		tree = null;
		searchTimer = 0;
		villager.setDropChance(EquipmentSlot.MAINHAND, 0f);
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		if (tree != null) {
			level.destroyBlockProgress(villager.getId(), tree, -1);
		}
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		BlockPos block = Builders.benchPos(villager).orElse(null);
		if (block == null) {
			return;
		}
		BuilderBag bag = villager.getAttachedOrCreate(ModAttachments.BUILDER_BAG);
		ItemStack axe = villager.getItemBySlot(EquipmentSlot.MAINHAND);

		// 1. Drop off logs after each tree (and whenever the bag fills up).
		if (depositDue || bag.freeSlots() < 3) {
			status(villager, Phase.DEPOSITING);
			if (walker.walkTo(level, villager, containerNear(level, block), 3.0)) {
				deposit(level, villager, block, bag);
				takeSaplings(level, block, bag);
				takeBoneMeal(level, block, bag, TreeFarms.farm(villager), false);
				depositDue = false;
			}
			return;
		}

		// 2. Plant the stumps the leaves gave no sapling for.
		if (!unplanted.isEmpty()) {
			var next = unplanted.entrySet().iterator().next();
			BlockPos spot = next.getKey();
			Block sapling = next.getValue();
			if (!bag.has(sapling.asItem(), 1) || !Trees.canPlant(level, spot, sapling)) {
				unplanted.remove(spot);
				return;
			}
			status(villager, Phase.CHOPPING);
			if (walker.reach(level, villager, spot, REACH)) {
				level.setBlockAndUpdate(spot, Trees.plantState(level, spot, sapling));
				bag.remove(sapling.asItem(), 1);
				level.playSound(null, spot, SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 0.8f, 1f);
				unplanted.remove(spot);
				replanted.add(spot);
			} else if (walker.noSpot()) {
				unplanted.remove(spot);
			}
			return;
		}

		// 2b. Keep the tree farm planted (saplings from the bag, topped up from the chests).
		BoundingBox farm = TreeFarms.farm(villager);
		if (farm != null && tree == null && plantFarm(level, villager, block, bag, farm)) {
			return;
		}

		// 3. An axe in hand.
		if (!isAxe(axe)) {
			fetchAxe(level, villager, block);
			return;
		}

		// 4. A tree to cut (and while there's none, bone meal for the young ones).
		if (tree == null || Trees.treeAt(level, tree).isEmpty()) {
			tree = null;
			chopAt = null;
			chopProgress = 0;
			if (feeding != null && feed(level, villager, block, bag)) {
				return;
			}
			if (--searchTimer > 0) {
				status(villager, Phase.LOOKING);
				return;
			}
			searchTimer = SEARCH_EVERY;
			tree = findTree(level, block, villager.blockPosition(), farm, unreachable);
			if (tree == null) {
				feeding = findSapling(level, villager.blockPosition(), farm);
				walker.reset();
				status(villager, feeding != null ? Phase.FERTILIZING : Phase.LOOKING);
				return;
			}
		}

		// 5. Walk up to the trunk and chop (from wherever a log of it can be reached: a mangrove's trunk stands high on its roots).
		status(villager, Phase.CHOPPING);
		if (chopAt == null || !Trees.isLog(level.getBlockState(chopAt))) {
			chopAt = reachableLog(level, tree, villager.blockPosition());
			if (chopAt == null) {
				unreachable.add(tree); // can't get at it: look for another
				tree = null;
				return;
			}
			walker.reset();
		}
		if (!walker.reach(level, villager, chopAt, REACH)) {
			if (walker.noSpot()) {
				unreachable.add(tree);
				tree = null;
				chopAt = null;
			}
			return;
		}
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(chopAt));
		Optional<Trees.Tree> found = Trees.treeAt(level, tree);
		if (found.isEmpty()) {
			tree = null;
			return;
		}
		Trees.Tree t = found.get();
		if (chopProgress == 0) {
			BlockState log = level.getBlockState(tree);
			float perLog = Math.max(2f, log.getDestroySpeed(level, tree) * 30f / Math.max(1f, axe.getDestroySpeed(log)));
			int total = (int) Math.ceil(perLog * Math.min(t.logs().size(), 24)); // big trees don't take forever
			chopTotal = Math.max(10, BuilderLevels.delay(total, villager));
		}
		chopProgress++;
		if (chopProgress % 5 == 0) {
			villager.swing(InteractionHand.MAIN_HAND);
			level.playSound(null, tree, SoundEvents.AXE_STRIP, SoundSource.NEUTRAL, 0.5f, 0.9f + level.random.nextFloat() * 0.2f);
			level.destroyBlockProgress(villager.getId(), tree, Math.min(9, chopProgress * 10 / chopTotal));
		}
		if (chopProgress < chopTotal) {
			return;
		}
		level.destroyBlockProgress(villager.getId(), tree, -1);
		fell(level, villager, bag, axe, t, unplanted, replanted);
		tree = null;
		chopAt = null;
		unreachable.clear(); // the ground has changed: worth another look
		chopProgress = 0;
		searchTimer = 0;
		depositDue = true;
	}

	/** Trees that couldn't be got at (lowest log), skipped until the next one comes down. */
	private final Set<BlockPos> unreachable = new java.util.HashSet<>();

	/** The log being chopped at: the lowest of the tree that someone can stand within reach of. */
	@Nullable
	private BlockPos chopAt;

	@Nullable
	private static BlockPos reachableLog(ServerLevel level, BlockPos tree, BlockPos from) {
		Optional<Trees.Tree> t = Trees.treeAt(level, tree);
		if (t.isEmpty()) {
			return null;
		}
		int checked = 0;
		for (BlockPos log : t.get().logs()) { // lowest first
			if (Walker.standingSpot(level, log, from, REACH) != null) {
				return log;
			}
			if (++checked >= 24) {
				break;
			}
		}
		return null;
	}

	// --- felling -------------------------------------------------------------------------------

	private static void fell(ServerLevel level, Villager villager, BuilderBag bag, ItemStack axe, Trees.Tree t, java.util.Map<BlockPos, Block> unplanted,
							 Set<BlockPos> replanted) {
		Block sapling = Trees.saplingFor(level, t);
		for (BlockPos leaf : t.leaves()) {
			take(level, villager, bag, axe, leaf, false);
		}
		for (int i = t.logs().size() - 1; i >= 0; i--) {
			take(level, villager, bag, axe, t.logs().get(i), true);
			if (axe.isEmpty()) {
				break; // worn out mid-tree: the rest waits for the next axe
			}
		}
		// A new tree where the old one stood.
		if (sapling != null) {
			if (sapling == net.minecraft.world.level.block.Blocks.AZALEA && !bag.has(sapling.asItem(), 1) && bag.has(Items.FLOWERING_AZALEA, 1)) {
				sapling = net.minecraft.world.level.block.Blocks.FLOWERING_AZALEA; // grows the same tree
			}
			Item seed = sapling.asItem();
			for (BlockPos spot : Trees.replantSpots(level, t, sapling)) {
				if (bag.has(seed, 1)) {
					level.setBlockAndUpdate(spot, Trees.plantState(level, spot, sapling));
					bag.remove(seed, 1);
					replanted.add(spot.immutable());
				} else {
					unplanted.put(spot.immutable(), sapling);
				}
			}
		}
		int trees = villager.getAttachedOrElse(ModAttachments.TREES_FELLED, 0) + 1;
		villager.setAttached(ModAttachments.TREES_FELLED, trees);
		BuilderLevels.addXp(level, villager, 2, null);
	}

	private static void take(ServerLevel level, Villager villager, BuilderBag bag, ItemStack axe, BlockPos pos, boolean log) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) {
			return;
		}
		List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), villager, axe);
		level.destroyBlock(pos, false, villager);
		for (ItemStack drop : drops) {
			ItemStack rest = bag.add(drop);
			if (!rest.isEmpty()) {
				Block.popResource(level, villager.blockPosition(), rest);
			}
		}
		if (log && !axe.isEmpty()) {
			axe.hurtAndBreak(1, villager, EquipmentSlot.MAINHAND);
		}
	}

	/** Nearest natural tree to the villager within {@link #RADIUS} of the Chopping Block (outside builds and quarries). */
	@Nullable
	private static BlockPos findTree(ServerLevel level, BlockPos block, BlockPos from, @Nullable BoundingBox farm, Set<BlockPos> unreachable) {
		List<BoundingBox> keepOut = new java.util.ArrayList<>();
		for (BuildSite site : BuildSiteManager.get(level).all()) {
			io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary.get(level, site.structure())
				.map(b -> io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline.bounds(site.placement(), b.size()))
				.ifPresent(keepOut::add);
		}
		for (QuarrySite quarry : QuarrySiteManager.get(level).all()) {
			keepOut.add(quarry.box());
		}
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		Set<BlockPos> checked = new java.util.HashSet<>();
		Iterable<BlockPos> around = BlockPos.betweenClosed(block.offset(-RADIUS, -6, -RADIUS), block.offset(RADIUS, 10, RADIUS));
		if (farm != null) {
			// The tree farm too, even where it's further out than the lumberjack would look on their own.
			around = com.google.common.collect.Iterables.concat(around,
				BlockPos.betweenClosed(farm.minX(), farm.minY() - 2, farm.minZ(), farm.maxX(), farm.maxY() + 4, farm.maxZ()));
		}
		for (BlockPos p : around) {
			BlockState state = level.getBlockState(p);
			if (!Trees.isLog(state) || !Trees.isGround(level.getBlockState(p.below()))) {
				continue; // only trunks standing on the ground
			}
			BlockPos trunk = p.immutable();
			if (checked.contains(trunk) || unreachable.contains(trunk) || keepOut.stream().anyMatch(b -> b.isInside(trunk))) {
				continue;
			}
			double d = trunk.distSqr(from);
			if (d >= bestDistance) {
				continue;
			}
			Optional<Trees.Tree> t = Trees.treeAt(level, trunk);
			if (t.isPresent() && unreachable.contains(t.get().lowest())) {
				checked.addAll(t.get().base());
				continue;
			}
			if (t.isPresent()) {
				checked.addAll(t.get().base());
				best = t.get().lowest();
				bestDistance = d;
			} else {
				checked.add(trunk);
			}
		}
		return best;
	}

	// --- bone meal -----------------------------------------------------------------------------

	/**
	 * The nearest sapling to feed: on the tree farm, or one this lumberjack replanted. Saplings that had
	 * {@link #MAX_FEEDS} bone meal without growing are left alone.
	 */
	@Nullable
	private BlockPos findSapling(ServerLevel level, BlockPos from, @Nullable BoundingBox farm) {
		replanted.removeIf(p -> !isFeedable(level, p));
		fed.keySet().removeIf(p -> !level.getBlockState(p).is(net.minecraft.tags.BlockTags.SAPLINGS));
		List<BlockPos> candidates = new java.util.ArrayList<>(replanted);
		if (farm != null) {
			for (BlockPos p : BlockPos.betweenClosed(farm.minX(), farm.minY() - 2, farm.minZ(), farm.maxX(), farm.maxY() + 2, farm.maxZ())) {
				if (isFeedable(level, p)) {
					candidates.add(p.immutable());
				}
			}
		}
		BlockPos best = null;
		for (BlockPos p : candidates) {
			if (fed.getOrDefault(p, 0) < MAX_FEEDS && (best == null || p.distSqr(from) < best.distSqr(from))) {
				best = p;
			}
		}
		return best;
	}

	private static boolean isFeedable(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.is(net.minecraft.tags.BlockTags.SAPLINGS) && state.getBlock() instanceof net.minecraft.world.level.block.BonemealableBlock b
			&& b.isValidBonemealTarget(level, pos, state);
	}

	/**
	 * Walks to the sapling being fed and gives it bone meal now and then until it grows (fetching bone meal from the
	 * chests first). False once done with it, or when there's no bone meal.
	 */
	private boolean feed(ServerLevel level, Villager villager, BlockPos block, BuilderBag bag) {
		BlockPos pos = feeding;
		if (pos == null || !isFeedable(level, pos) || fed.getOrDefault(pos, 0) >= MAX_FEEDS) {
			feeding = null;
			return false;
		}
		if (!bag.has(Items.BONE_MEAL, 1)) {
			List<BlockPos> supplies = SupplyContainers.find(level, block, null);
			BlockPos chest = SupplyContainers.firstMatching(level, supplies, st -> st.is(Items.BONE_MEAL));
			if (chest == null) {
				feeding = null;
				return false;
			}
			status(villager, Phase.FERTILIZING);
			if (walker.walkTo(level, villager, chest, 3.0)) {
				takeBoneMeal(level, block, bag, null, true);
			}
			return true;
		}
		status(villager, Phase.FERTILIZING);
		if (!walker.reach(level, villager, pos, REACH)) {
			if (walker.noSpot()) {
				fed.put(pos, MAX_FEEDS);
				feeding = null;
			}
			return true;
		}
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(pos));
		if (--feedTimer > 0) {
			return true;
		}
		feedTimer = FEED_EVERY;
		if (net.minecraft.world.item.BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), level, pos)) {
			villager.swing(InteractionHand.MAIN_HAND);
			level.levelEvent(net.minecraft.world.level.block.LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, pos, 15);
			bag.remove(Items.BONE_MEAL, 1);
			fed.merge(pos, 1, Integer::sum);
		}
		if (!level.getBlockState(pos).is(net.minecraft.tags.BlockTags.SAPLINGS)) {
			// It grew: the next look round finds the tree.
			fed.remove(pos);
			replanted.remove(pos);
			feeding = null;
			searchTimer = 0;
		}
		return true;
	}

	/** Bone meal from the chests, when there are saplings to feed (or {@code now}, on the way to one). */
	private void takeBoneMeal(ServerLevel level, BlockPos block, BuilderBag bag, @Nullable BoundingBox farm, boolean now) {
		if (!now && farm == null && replanted.isEmpty() || bag.count(Items.BONE_MEAL) >= KEEP_BONE_MEAL / 2) {
			return;
		}
		List<BlockPos> supplies = SupplyContainers.find(level, block, null);
		int got = SupplyContainers.extract(level, supplies, Items.BONE_MEAL, KEEP_BONE_MEAL - bag.count(Items.BONE_MEAL));
		if (got > 0) {
			bag.addAll(Items.BONE_MEAL, got);
		}
	}

	// --- the tree farm --------------------------------------------------------------------------

	@Nullable
	private BlockPos plantSpot;
	private int plantSearchTimer;

	/**
	 * Plants the next empty spot of the farm. Returns false when there's nothing to plant (the farm is full, or there
	 * are no saplings in the bag or the chests), so the lumberjack gets on with felling.
	 */
	private boolean plantFarm(ServerLevel level, Villager villager, BlockPos block, BuilderBag bag, BoundingBox farm) {
		Block sapling = TreeFarms.saplingToPlant(bag);
		if (sapling == null) {
			// Nothing in the bag: fetch some from the chests, if there are any and the farm has room.
			if (--plantSearchTimer > 0) {
				return false;
			}
			plantSearchTimer = SEARCH_EVERY;
			List<BlockPos> supplies = SupplyContainers.find(level, block, null);
			BlockPos chest = SupplyContainers.firstMatching(level, supplies, s -> s.is(ItemTags.SAPLINGS));
			if (chest == null) {
				return false;
			}
			ItemStack sample = SupplyContainers.peekMatching(level, chest, s -> s.is(ItemTags.SAPLINGS)).stream().findFirst().orElse(ItemStack.EMPTY);
			if (sample.isEmpty() || !(sample.getItem() instanceof net.minecraft.world.item.BlockItem item)
				|| TreeFarms.nextSpot(level, farm, item.getBlock(), villager.blockPosition()).isEmpty()) {
				return false;
			}
			status(villager, Phase.PLANTING);
			plantSearchTimer = 0;
			if (walker.walkTo(level, villager, chest, 3.0)) {
				bag.addAll(sample.getItem(), SupplyContainers.extract(level, supplies, sample.getItem(), KEEP_SAPLINGS));
			}
			return true;
		}
		if (plantSpot == null) {
			if (--plantSearchTimer > 0) {
				return false;
			}
			plantSearchTimer = SEARCH_EVERY;
			plantSpot = TreeFarms.nextSpot(level, farm, sapling, villager.blockPosition()).orElse(null);
			if (plantSpot == null) {
				return false; // the farm is full
			}
			walker.reset();
		}
		status(villager, Phase.PLANTING);
		if (walker.reach(level, villager, plantSpot, REACH)) {
			TreeFarms.plant(level, villager, bag, sapling, plantSpot);
			plantSpot = null;
			plantSearchTimer = 0;
		} else if (walker.noSpot()) {
			plantSpot = null;
		}
		return true;
	}

	// --- errands -------------------------------------------------------------------------------

	static boolean isAxe(ItemStack stack) {
		return !stack.isEmpty() && stack.is(ItemTags.AXES);
	}

	private void fetchAxe(ServerLevel level, Villager villager, BlockPos block) {
		List<BlockPos> supplies = SupplyContainers.find(level, block, null);
		BlockPos chest = SupplyContainers.firstMatching(level, supplies, LumberjackWork::isAxe);
		if (chest == null) {
			// Another worker in the village may have a spare.
			io.github.jcondedata.aliveworkplace.work.Village.Find elsewhere = io.github.jcondedata.aliveworkplace.work.Village.find(level, villager, block, null, LumberjackWork::isAxe);
			if (elsewhere != null) {
				supplies = elsewhere.stash().chests();
				chest = elsewhere.chest();
			}
		}
		if (chest == null) {
			status(villager, Phase.NEEDS_AXE);
			walker.walkTo(level, villager, block, 3.0);
			return;
		}
		if (!walker.walkTo(level, villager, chest, 3.0)) {
			return;
		}
		ItemStack axe = SupplyContainers.takeOne(level, supplies, LumberjackWork::isAxe);
		if (!axe.isEmpty()) {
			villager.setItemSlot(EquipmentSlot.MAINHAND, axe);
			level.playSound(null, chest, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.4f, 1.1f);
		}
	}

	private static void deposit(ServerLevel level, Villager villager, BlockPos block, BuilderBag bag) {
		List<BlockPos> supplies = SupplyContainers.find(level, block, null);
		for (ItemStack stack : bag.takeAll()) {
			// Keep a few saplings for replanting (and the bone meal); everything else goes in the chests.
			int keepUpTo = stack.is(ItemTags.SAPLINGS) ? KEEP_SAPLINGS : stack.is(Items.BONE_MEAL) ? KEEP_BONE_MEAL : 0;
			if (bag.count(stack.getItem()) < keepUpTo) {
				int keep = Math.min(stack.getCount(), keepUpTo - bag.count(stack.getItem()));
				bag.add(stack.split(keep));
			}
			if (!stack.isEmpty()) {
				ItemStack rest = SupplyContainers.insert(level, supplies, stack);
				if (!rest.isEmpty()) {
					Block.popResource(level, block.above(), rest);
				}
			}
		}
		level.playSound(null, villager.blockPosition(), SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.4f, 1.1f);
	}

	/** Saplings for the stumps still waiting for one, from the chests. */
	private void takeSaplings(ServerLevel level, BlockPos block, BuilderBag bag) {
		if (unplanted.isEmpty()) {
			return;
		}
		List<BlockPos> supplies = SupplyContainers.find(level, block, null);
		java.util.Map<Item, Integer> needed = new java.util.HashMap<>();
		for (Block sapling : unplanted.values()) {
			needed.merge(sapling.asItem(), 1, Integer::sum);
		}
		needed.forEach((item, count) -> {
			int missing = count - bag.count(item);
			if (missing > 0) {
				bag.addAll(item, SupplyContainers.extract(level, supplies, item, missing));
			}
		});
	}

	private static BlockPos containerNear(ServerLevel level, BlockPos block) {
		List<BlockPos> supplies = SupplyContainers.find(level, block, null);
		return supplies.isEmpty() ? block : supplies.get(0);
	}

	private static void status(Villager villager, Phase phase) {
		int trees = villager.getAttachedOrElse(ModAttachments.TREES_FELLED, 0);
		Component title = Component.translatable("message.aliveworkplace.lumberjack.title", trees);
		Component line = Component.translatable("message.aliveworkplace.lumberjack.state." + phase.name().toLowerCase())
			.withStyle(phase == Phase.NEEDS_AXE ? ChatFormatting.YELLOW : ChatFormatting.GRAY);
		WorkerStatus.set(villager, title, -1f, line);
	}
}
