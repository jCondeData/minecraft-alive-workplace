package io.github.jcondedata.aliveworkplace.farm;

import com.google.common.collect.ImmutableMap;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Walker;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LevelEvent;
import io.github.jcondedata.aliveworkplace.orchard.Fruit;
import org.jetbrains.annotations.Nullable;

/**
 * A farmer's shift on the field they were given: harvest what's ripe (and plant the same crop right
 * back), sow empty farmland with seeds from the chests near the composter, till bare dirt with a hoe
 * from those chests, cut sugar cane down to its last block, pick pumpkins and melons, and carry the
 * harvest to the chests. When everything is tended the farmer goes back to the vanilla routine until
 * something ripens.
 */
public class FieldWork extends Behavior<Villager> {
	static final double REACH = 3.0;
	private static final float SPEED = 0.55f;
	private static final int SCAN_EVERY = 100;
	/** Seeds a farmer keeps in the bag for replanting; the rest goes to the chests. */
	static final int KEEP_SEEDS = 32;
	private static final int HARVESTS_PER_XP = 10;

	enum Phase { TENDING, NEEDS_SEEDS, DEPOSITING, RESTING }

	enum Kind { HARVEST, PLANT, TILL, FERTILIZE }

	/** Bone meal a farmer keeps in the bag; the rest stays in the chests. */
	static final int KEEP_BONE_MEAL = 16;

	record Task(Kind kind, BlockPos pos) {
	}

	/** Farmers with nothing to do right now: their vanilla routine may run (see {@link Fields#vanillaMayRun}). */
	private static final Set<Villager> RESTING = Collections.newSetFromMap(new WeakHashMap<>());

	private final Walker walker = new Walker(SPEED);
	private List<Task> tasks = new ArrayList<>();
	/** Tasks that can't be done until the next scan (no seed fits, nowhere to stand). */
	private final Set<BlockPos> skipped = new HashSet<>();
	@Nullable
	private Task current;
	private int scanTimer;
	private int progress;
	private boolean depositDue;
	private boolean chestsHaveSeeds;
	private boolean chestsHaveHoe;
	private boolean chestsHaveBoneMeal;

	public FieldWork() {
		super(ImmutableMap.of(
			MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 1200);
	}

	static boolean isResting(Villager villager) {
		return RESTING.contains(villager);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		if (!villager.hasAttached(ModAttachments.FARM_FIELD) && (villager.tickCount + villager.getId()) % Fields.ADOPT_EVERY == 0) {
			Fields.adoptOwnFarm(level, villager);
		}
		return canWork(villager);
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return canWork(villager);
	}

	private static boolean canWork(Villager villager) {
		return !villager.isSleeping() && villager.hasAttached(ModAttachments.FARM_FIELD) && Builders.benchPos(villager).isPresent();
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		walker.reset();
		current = null;
		scanTimer = 0;
		villager.setDropChance(EquipmentSlot.MAINHAND, 0f);
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		RESTING.remove(villager);
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		FieldJob job = villager.getAttached(ModAttachments.FARM_FIELD);
		BlockPos station = Builders.benchPos(villager).orElse(null);
		if (job == null || station == null) {
			return;
		}
		BoundingBox field = job.box();
		BuilderBag bag = villager.getAttachedOrCreate(ModAttachments.BUILDER_BAG);

		// 1. Harvest goes to the chests when the bag fills up, and after every round of the field.
		if (depositDue || bag.freeSlots() < 3) {
			rest(villager, false);
			status(villager, field, Phase.DEPOSITING);
			if (walker.walkTo(level, villager, containerNear(level, station), 3.0)) {
				deposit(level, station, bag);
				depositDue = false;
			}
			return;
		}

		// 2. Look over the field now and then.
		if (--scanTimer <= 0) {
			scanTimer = SCAN_EVERY;
			tasks = scan(level, field);
			skipped.clear();
			List<BlockPos> supplies = SupplyContainers.find(level, station, null);
			chestsHaveSeeds = SupplyContainers.firstMatching(level, supplies, FieldWork::isSeed) != null
				|| io.github.jcondedata.aliveworkplace.work.Village.find(level, villager, station, null, FieldWork::isSeed) != null;
			chestsHaveHoe = SupplyContainers.firstMatching(level, supplies, FieldWork::isHoe) != null
				|| io.github.jcondedata.aliveworkplace.work.Village.find(level, villager, station, null, FieldWork::isHoe) != null;
			chestsHaveBoneMeal = SupplyContainers.firstMatching(level, supplies, s -> s.is(Items.BONE_MEAL)) != null;
		}

		// 3. Pick the nearest job that can be done with what we carry (or can fetch).
		boolean hasSeeds = hasSeeds(bag);
		boolean hasHoe = isHoe(villager.getItemBySlot(EquipmentSlot.MAINHAND));
		boolean hasBoneMeal = bag.has(Items.BONE_MEAL, 1);
		if (current == null || !stillNeeded(level, field, current)) {
			current = null;
			progress = 0;
			boolean wantsSeeds = false;
			boolean wantsHoe = false;
			boolean wantsBoneMeal = false;
			Task best = null;
			double bestDistance = Double.MAX_VALUE;
			// Bone meal comes last: only once there's nothing to harvest, sow or till.
			for (boolean fertilizing : new boolean[]{false, true}) {
				for (Task t : tasks) {
					if (skipped.contains(t.pos()) || (t.kind() == Kind.FERTILIZE) != fertilizing) {
						continue;
					}
					boolean doable = switch (t.kind()) {
						case HARVEST -> true;
						case PLANT -> hasSeeds;
						case TILL -> hasSeeds && hasHoe;
						case FERTILIZE -> hasBoneMeal;
					};
					if (!doable) {
						wantsSeeds |= (t.kind() == Kind.PLANT || t.kind() == Kind.TILL) && !hasSeeds;
						wantsHoe |= t.kind() == Kind.TILL && !hasHoe;
						wantsBoneMeal |= t.kind() == Kind.FERTILIZE;
						continue;
					}
					double d = t.pos().distSqr(villager.blockPosition());
					if (d < bestDistance) {
						bestDistance = d;
						best = t;
					}
				}
				if (best != null) {
					break;
				}
			}
			if (best == null && wantsBoneMeal && !wantsSeeds && chestsHaveBoneMeal) {
				rest(villager, false);
				status(villager, field, Phase.TENDING);
				fetchBoneMeal(level, villager, station, bag);
				return;
			}
			if (best == null) {
				// Nothing we can do with what we carry: fetch seeds or a hoe if the chests have them.
				if (wantsSeeds && chestsHaveSeeds) {
					rest(villager, false);
					status(villager, field, Phase.TENDING);
					fetch(level, villager, station, bag, true);
					return;
				}
				if (wantsHoe && hasSeeds && chestsHaveHoe) {
					rest(villager, false);
					status(villager, field, Phase.TENDING);
					fetch(level, villager, station, bag, false);
					return;
				}
				if (hasHarvest(bag)) {
					depositDue = true;
					return;
				}
				rest(villager, true);
				status(villager, field, wantsSeeds ? Phase.NEEDS_SEEDS : Phase.RESTING);
				return;
			}
			tasks.remove(best);
			current = best;
		}

		// 4. Go there and do it.
		rest(villager, false);
		status(villager, field, Phase.TENDING);
		Task task = current;
		if (!walker.reach(level, villager, task.pos(), REACH)) {
			if (walker.noSpot()) {
				skipped.add(task.pos());
				current = null;
			}
			return;
		}
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(task.pos()));
		int time = BuilderLevels.delay(task.kind() == Kind.PLANT || task.kind() == Kind.FERTILIZE ? 8 : 12, villager);
		if (progress++ == 0) {
			villager.swing(InteractionHand.MAIN_HAND);
		}
		if (progress < time) {
			return;
		}
		progress = 0;
		current = null;
		switch (task.kind()) {
			case HARVEST -> harvest(level, villager, bag, task.pos());
			case PLANT -> {
				if (!plant(level, bag, task.pos())) {
					skipped.add(task.pos());
				}
			}
			case TILL -> till(level, villager, bag, task.pos());
			case FERTILIZE -> fertilize(level, bag, task.pos());
		}
	}

	private static void rest(Villager villager, boolean resting) {
		if (resting) {
			RESTING.add(villager);
		} else {
			RESTING.remove(villager);
		}
	}

	// --- what needs doing -----------------------------------------------------------------------

	/** Everything to do on the field, in no particular order. */
	static List<Task> scan(ServerLevel level, BoundingBox field) {
		List<Task> out = new ArrayList<>();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int x = field.minX(); x <= field.maxX(); x++) {
			for (int z = field.minZ(); z <= field.maxZ(); z++) {
				for (int y = field.maxY() + 3; y >= field.minY() - 1; y--) {
					p.set(x, y, z);
					Kind kind = classify(level, field, p);
					if (kind != null) {
						out.add(new Task(kind, kind == Kind.PLANT ? p.above().immutable() : p.immutable()));
					}
				}
			}
		}
		return out;
	}

	/** What (if anything) to do with the block at {@code pos}. PLANT means: sow the spot above it. */
	@Nullable
	static Kind classify(ServerLevel level, BoundingBox field, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) {
			return null;
		}
		if (isRipe(level, pos, state)) {
			return Kind.HARVEST;
		}
		if (isGrowing(state) && ((BonemealableBlock) state.getBlock()).isValidBonemealTarget(level, pos, state)) {
			return Kind.FERTILIZE;
		}
		boolean ground = pos.getY() >= field.minY() - 1 && pos.getY() <= field.maxY();
		if (!ground) {
			return null;
		}
		BlockState above = level.getBlockState(pos.above());
		if ((state.getBlock() instanceof FarmBlock || state.is(Blocks.SOUL_SAND)) && above.isAir()) {
			return Kind.PLANT;
		}
		if ((state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK)) && (above.isAir() || isWeed(above))) {
			return Kind.TILL;
		}
		return null;
	}

	private static boolean isRipe(ServerLevel level, BlockPos pos, BlockState state) {
		Block block = state.getBlock();
		if (block instanceof CropBlock crop) {
			return crop.isMaxAge(state);
		}
		if (block instanceof NetherWartBlock) {
			return state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
		}
		if (state.is(Blocks.PUMPKIN) || state.is(Blocks.MELON)) {
			// Only fruit growing off a stem: a pumpkin someone put down as decoration stays.
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockState n = level.getBlockState(pos.relative(d));
				if (n.getBlock() instanceof AttachedStemBlock && n.getValue(AttachedStemBlock.FACING) == d.getOpposite()) {
					return true;
				}
			}
			return false;
		}
		if (state.is(Blocks.SUGAR_CANE)) {
			// The second block of a cane: cut from here up, leave the bottom one to grow back.
			return level.getBlockState(pos.below()).is(Blocks.SUGAR_CANE) && !level.getBlockState(pos.below(2)).is(Blocks.SUGAR_CANE);
		}
		// Sweet berries, cocoa pods, glow berries (and apricorns and berries with Cobblemon): picked, not cut.
		return Fruit.isRipe(state);
	}

	/** Something planted that bone meal would bring on: crops, stems, cocoa, berry bushes (not grass: it'd sprout flowers). */
	private static boolean isGrowing(BlockState state) {
		Block block = state.getBlock();
		return block instanceof BonemealableBlock && (block instanceof CropBlock || block instanceof StemBlock
			|| block instanceof CocoaBlock || block instanceof SweetBerryBushBlock);
	}

	private static void fertilize(ServerLevel level, BuilderBag bag, BlockPos pos) {
		if (!bag.has(Items.BONE_MEAL, 1)) {
			return;
		}
		if (BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), level, pos)) {
			bag.remove(Items.BONE_MEAL, 1);
			level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, pos, 15);
		}
	}

	private static boolean isWeed(BlockState state) {
		return state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN);
	}

	private static boolean stillNeeded(ServerLevel level, BoundingBox field, Task task) {
		BlockPos at = task.kind() == Kind.PLANT ? task.pos().below() : task.pos();
		return classify(level, field, at) == task.kind();
	}

	// --- doing it ---------------------------------------------------------------------------------

	private static void harvest(ServerLevel level, Villager villager, BuilderBag bag, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof CropBlock) && Fruit.isRipe(state)) {
			for (ItemStack fruit : Fruit.pick(level, pos, villager)) {
				ItemStack rest = bag.add(fruit);
				if (!rest.isEmpty()) {
					Block.popResource(level, pos, rest);
				}
			}
		} else if (state.is(Blocks.SUGAR_CANE)) {
			BlockPos top = pos;
			while (level.getBlockState(top.above()).is(Blocks.SUGAR_CANE)) {
				top = top.above();
			}
			for (BlockPos p = top; p.getY() >= pos.getY(); p = p.below()) {
				take(level, villager, bag, p);
			}
		} else {
			boolean replant = state.getBlock() instanceof CropBlock || state.getBlock() instanceof NetherWartBlock;
			Item seed = replant ? state.getBlock().getCloneItemStack(level, pos, state).getItem() : null;
			take(level, villager, bag, pos);
			// The same crop goes straight back in.
			if (seed != null && bag.has(seed, 1) && seed instanceof BlockItem bi) {
				BlockState sown = bi.getBlock().defaultBlockState();
				if (level.getBlockState(pos).isAir() && sown.canSurvive(level, pos)) {
					level.setBlockAndUpdate(pos, sown);
					bag.remove(seed, 1);
				}
			}
		}
		level.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 0.8f, 1f);
		FieldJob job = villager.getAttached(ModAttachments.FARM_FIELD);
		if (job != null && job.adopted()) {
			keepFood(villager, bag); // a village's own farmer feeds the village; a player's field all goes to the chests
		}
		int harvested = villager.getAttachedOrElse(ModAttachments.FARM_HARVESTED, 0) + 1;
		villager.setAttached(ModAttachments.FARM_HARVESTED, harvested);
		if (harvested % HARVESTS_PER_XP == 0) {
			BuilderLevels.addXp(level, villager, 1, null);
		}
	}

	/** Food points (bread 4, carrot, potato, beetroot 1) a farmer keeps on them to share with the village. */
	static final int FOOD_KEPT = 36;

	/**
	 * Villagers breed and feed each other with the food they carry, and vanilla farmers are where it comes from: some of
	 * the harvest stays on the farmer (their own inventory, which vanilla shares out; wheat as bread), the rest goes to
	 * the chests.
	 */
	static void keepFood(Villager villager, BuilderBag bag) {
		net.minecraft.world.SimpleContainer inventory = villager.getInventory();
		int points = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			points += Villager.FOOD_POINTS.getOrDefault(stack.getItem(), 0) * stack.getCount();
		}
		for (Item food : Villager.FOOD_POINTS.keySet()) {
			int each = Villager.FOOD_POINTS.get(food);
			while (points < FOOD_KEPT && bag.has(food, 1)) {
				int count = Math.min(bag.count(food), (FOOD_KEPT - points + each - 1) / each);
				ItemStack rest = inventory.addItem(new ItemStack(food, count));
				int moved = count - rest.getCount();
				if (moved <= 0) {
					return; // their pockets are full
				}
				bag.remove(food, moved);
				points += moved * each;
			}
		}
		// Wheat is baked into bread, three to a loaf, as vanilla farmers do at their composter.
		while (points < FOOD_KEPT && bag.has(Items.WHEAT, 3)) {
			if (!inventory.addItem(new ItemStack(Items.BREAD)).isEmpty()) {
				return;
			}
			bag.remove(Items.WHEAT, 3);
			points += Villager.FOOD_POINTS.get(Items.BREAD);
		}
	}

	private static void take(ServerLevel level, Villager villager, BuilderBag bag, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), villager, villager.getItemBySlot(EquipmentSlot.MAINHAND));
		level.destroyBlock(pos, false, villager);
		for (ItemStack drop : drops) {
			ItemStack rest = bag.add(drop);
			if (!rest.isEmpty()) {
				Block.popResource(level, pos, rest);
			}
		}
	}

	/** Sows {@code spot} with the crop growing next to it, or else the seed we carry most of. */
	static boolean plant(ServerLevel level, BuilderBag bag, BlockPos spot) {
		if (!level.getBlockState(spot).isAir()) {
			return false;
		}
		Item seed = seedFor(level, bag, spot);
		if (seed == null) {
			return false;
		}
		level.setBlockAndUpdate(spot, ((BlockItem) seed).getBlock().defaultBlockState());
		bag.remove(seed, 1);
		level.playSound(null, spot, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 0.8f, 1f);
		return true;
	}

	@Nullable
	private static Item seedFor(ServerLevel level, BuilderBag bag, BlockPos spot) {
		for (int r = 1; r <= 2; r++) {
			for (BlockPos n : BlockPos.betweenClosed(spot.offset(-r, 0, -r), spot.offset(r, 0, r))) {
				BlockState state = level.getBlockState(n);
				if (state.is(BlockTags.CROPS) || state.getBlock() instanceof NetherWartBlock) {
					Item seed = state.getBlock().getCloneItemStack(level, n, state).getItem();
					if (bag.has(seed, 1) && canPlant(level, seed, spot)) {
						return seed;
					}
				}
			}
		}
		Item best = null;
		int most = 0;
		for (ItemStack stack : bag.stacks()) {
			if (isSeed(stack) && canPlant(level, stack.getItem(), spot)) {
				int count = bag.count(stack.getItem());
				if (count > most) {
					most = count;
					best = stack.getItem();
				}
			}
		}
		return best;
	}

	private static boolean canPlant(ServerLevel level, Item seed, BlockPos spot) {
		return seed instanceof BlockItem bi && bi.getBlock().defaultBlockState().canSurvive(level, spot);
	}

	private static void till(ServerLevel level, Villager villager, BuilderBag bag, BlockPos pos) {
		BlockPos above = pos.above();
		BlockState weed = level.getBlockState(above);
		if (isWeed(weed)) {
			take(level, villager, bag, above);
		}
		if (!level.getBlockState(above).isAir()) {
			return;
		}
		level.setBlockAndUpdate(pos, Blocks.FARMLAND.defaultBlockState());
		level.playSound(null, pos, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 0.8f, 1f);
		ItemStack hoe = villager.getItemBySlot(EquipmentSlot.MAINHAND);
		if (!hoe.isEmpty()) {
			hoe.hurtAndBreak(1, villager, EquipmentSlot.MAINHAND);
		}
		// Sow it right away so the fresh farmland doesn't dry back to dirt.
		plant(level, bag, above);
	}

	// --- errands ----------------------------------------------------------------------------------

	public static boolean isSeed(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof BlockItem bi
			&& (bi.getBlock().defaultBlockState().is(BlockTags.CROPS) || bi.getBlock() instanceof NetherWartBlock);
	}

	static boolean isHoe(ItemStack stack) {
		return !stack.isEmpty() && stack.is(ItemTags.HOES);
	}

	private static boolean hasSeeds(BuilderBag bag) {
		for (ItemStack stack : bag.stacks()) {
			if (isSeed(stack)) {
				return true;
			}
		}
		return false;
	}

	/** Anything in the bag worth carrying to the chests (not just the seeds we keep). */
	private static boolean hasHarvest(BuilderBag bag) {
		for (ItemStack stack : bag.stacks()) {
			if (!stack.isEmpty() && !stack.is(Items.BONE_MEAL) && (!isSeed(stack) || bag.count(stack.getItem()) > KEEP_SEEDS)) {
				return true;
			}
		}
		return false;
	}

	/** Walks to the chests and takes some bone meal. */
	private void fetchBoneMeal(ServerLevel level, Villager villager, BlockPos station, BuilderBag bag) {
		List<BlockPos> supplies = SupplyContainers.find(level, station, null);
		BlockPos chest = SupplyContainers.firstWith(level, supplies, Items.BONE_MEAL);
		if (chest == null) {
			chestsHaveBoneMeal = false;
			return;
		}
		if (walker.walkTo(level, villager, chest, 3.0)) {
			bag.addAll(Items.BONE_MEAL, SupplyContainers.extract(level, supplies, Items.BONE_MEAL, KEEP_BONE_MEAL));
			level.playSound(null, chest, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.4f, 1.1f);
		}
	}

	/** Walks to the chests and takes seeds (the kind there is most of) or a hoe. */
	private void fetch(ServerLevel level, Villager villager, BlockPos station, BuilderBag bag, boolean seeds) {
		List<BlockPos> supplies = SupplyContainers.find(level, station, null);
		BlockPos chest = SupplyContainers.firstMatching(level, supplies, seeds ? FieldWork::isSeed : FieldWork::isHoe);
		if (chest == null) {
			// Another worker in the village may have some.
			io.github.jcondedata.aliveworkplace.work.Village.Find elsewhere = io.github.jcondedata.aliveworkplace.work.Village.find(level, villager, station, null, seeds ? FieldWork::isSeed : FieldWork::isHoe);
			if (elsewhere != null) {
				supplies = elsewhere.stash().chests();
				chest = elsewhere.chest();
			}
		}
		if (chest == null) {
			if (seeds) {
				chestsHaveSeeds = false;
			} else {
				chestsHaveHoe = false;
			}
			return;
		}
		if (!walker.walkTo(level, villager, chest, 3.0)) {
			return;
		}
		ItemStack got = SupplyContainers.takeOne(level, supplies, seeds ? FieldWork::isSeed : FieldWork::isHoe);
		if (got.isEmpty()) {
			return;
		}
		if (seeds) {
			int more = SupplyContainers.extract(level, supplies, got.getItem(), KEEP_SEEDS - 1);
			bag.addAll(got.getItem(), 1 + more);
		} else {
			ItemStack old = villager.getItemBySlot(EquipmentSlot.MAINHAND);
			if (!old.isEmpty()) {
				bag.add(old);
			}
			villager.setItemSlot(EquipmentSlot.MAINHAND, got);
		}
		level.playSound(null, chest, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.4f, 1.1f);
	}

	private static void deposit(ServerLevel level, BlockPos station, BuilderBag bag) {
		List<BlockPos> supplies = SupplyContainers.find(level, station, null);
		List<ItemStack> all = new ArrayList<>(bag.takeAll());
		all.sort(Comparator.comparingInt(s -> -s.getCount())); // keep the fullest seed stacks
		for (ItemStack stack : all) {
			if (isSeed(stack) && bag.count(stack.getItem()) < KEEP_SEEDS) {
				int keep = Math.min(stack.getCount(), KEEP_SEEDS - bag.count(stack.getItem()));
				bag.add(stack.split(keep));
			} else if (stack.is(Items.BONE_MEAL) && bag.count(Items.BONE_MEAL) < KEEP_BONE_MEAL) {
				bag.add(stack.split(Math.min(stack.getCount(), KEEP_BONE_MEAL - bag.count(Items.BONE_MEAL))));
			}
			if (!stack.isEmpty()) {
				ItemStack rest = SupplyContainers.insert(level, supplies, stack);
				if (!rest.isEmpty()) {
					Block.popResource(level, station.above(), rest);
				}
			}
		}
		level.playSound(null, station, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.4f, 1.1f);
	}

	private static BlockPos containerNear(ServerLevel level, BlockPos station) {
		List<BlockPos> supplies = SupplyContainers.find(level, station, null);
		return supplies.isEmpty() ? station : supplies.get(0);
	}

	private static void status(Villager villager, BoundingBox field, Phase phase) {
		int harvested = villager.getAttachedOrElse(ModAttachments.FARM_HARVESTED, 0);
		Component title = Component.translatable("message.aliveworkplace.field.title", field.getXSpan(), field.getZSpan(), harvested);
		Component line = Component.translatable("message.aliveworkplace.field.state." + phase.name().toLowerCase())
			.withStyle(phase == Phase.NEEDS_SEEDS ? ChatFormatting.YELLOW : ChatFormatting.GRAY);
		WorkerStatus.set(villager, title, -1f, line);
	}
}
