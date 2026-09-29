package io.github.jcondedata.aliveworkplace.flower;

import com.google.common.collect.ImmutableMap;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Partners;
import io.github.jcondedata.aliveworkplace.work.Walker;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.TallFlowerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * The Florist's shift at the Flower Stand. The garden round it ({@link #GARDEN} blocks) grows flowers: bone meal from
 * the chests on the grass there brings up the flowers of the biome (and grass, which is weeded out), bone meal on a tall
 * flower (sunflower, lilac, rose bush, peony) gives another. The small flowers are picked into the chests — for the dyer
 * and the builders nearby, while there are fewer than {@link #KEEP_FLOWERS} there — and empty flower pots within
 * {@link #POT_RANGE} blocks of the stand get a flower each.
 */
public class FloristWork extends Behavior<Villager> {
	/** The garden round the Flower Stand. */
	public static int GARDEN = 5;
	/** How far from the stand empty flower pots are filled. */
	public static int POT_RANGE = 24;
	/** Flowers in the chests before the florist stops growing more. */
	static final int KEEP_FLOWERS = 64;
	private static final int LOOK_EVERY = 60;
	private static final int POT_SCAN_EVERY = 600;
	private static final float SPEED = 0.55f;
	private static final double REACH = 3.0;

	enum Task { NONE, DEPOSIT, PICK, GROW, POT }

	private final Walker walker = new Walker(SPEED);
	private Task task = Task.NONE;
	@Nullable
	private BlockPos target;
	@Nullable
	private ItemEntity drop;
	private int lookTimer;
	private int timer = -1;
	private int stuck;
	private String idle = "none";
	private final List<BlockPos> emptyPots = new ArrayList<>();
	private long potsScanned = -1_000_000L;

	public FloristWork() {
		super(ImmutableMap.of(
			MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 1200);
	}

	public static boolean isFlorist(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == ModVillagers.FLORIST;
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return !villager.isSleeping() && isFlorist(villager) && Builders.benchPos(villager).isPresent();
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
		task = Task.NONE;
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		BlockPos station = Builders.benchPos(villager).orElse(null);
		if (station == null) {
			return;
		}
		List<BlockPos> own = SupplyContainers.find(level, station, null);
		if (own.isEmpty()) {
			status(villager, "no_chest");
			return;
		}
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(villager);
		if (task == Task.NONE) {
			if (--lookTimer > 0) {
				status(villager, idle);
				return;
			}
			lookTimer = LOOK_EVERY;
			choose(level, villager, station, own, bag, gameTime);
			walker.reset();
			timer = -1;
			stuck = 0;
			if (task == Task.NONE) {
				return;
			}
		}
		status(villager, task.name().toLowerCase());
		switch (task) {
			case DEPOSIT -> deposit(level, villager, own, bag);
			case PICK -> pick(level, villager, bag);
			case GROW -> grow(level, villager, own, bag);
			case POT -> pot(level, villager, own, bag);
			default -> done();
		}
	}

	private void done() {
		task = Task.NONE;
		target = null;
		drop = null;
		lookTimer = 5;
		walker.reset();
	}

	private void choose(ServerLevel level, Villager villager, BlockPos station, List<BlockPos> own, BuilderBag bag, long now) {
		idle = "none";
		int carried = BuilderBag.SLOTS - bag.freeSlots();
		// Empty flower pots round the village get a flower (from the bag or the chests).
		if (now - potsScanned > POT_SCAN_EVERY) {
			potsScanned = now;
			scanPots(level, station);
		}
		emptyPots.removeIf(p -> !level.getBlockState(p).is(Blocks.FLOWER_POT));
		if (!emptyPots.isEmpty() && has(level, own, bag, FloristWork::isPottable)) {
			task = Task.POT;
			target = emptyPots.stream().min(Comparator.comparingDouble(p -> p.distSqr(villager.blockPosition()))).orElseThrow();
			return;
		}
		// What's dropped or standing in the garden: picked (the weeds too).
		AABB garden = new AABB(station).inflate(GARDEN, 2, GARDEN);
		drop = level.getEntitiesOfClass(ItemEntity.class, garden, e -> e.isAlive() && isFlower(e.getItem())).stream()
			.min(Comparator.comparingDouble(villager::distanceToSqr)).orElse(null);
		if (drop != null || (target = pickable(level, station, villager)) != null) {
			task = Task.PICK;
			return;
		}
		if (carried > 0) {
			task = Task.DEPOSIT;
			return;
		}
		// More flowers while the chests are short of them.
		if (SupplyContainers.contents(level, own).entrySet().stream().filter(e -> new ItemStack(e.getKey()).is(ItemTags.FLOWERS))
			.mapToLong(Map.Entry::getValue).sum() >= KEEP_FLOWERS) {
			return;
		}
		if (!has(level, own, bag, s -> s.is(Items.BONE_MEAL))) {
			idle = "no_bone_meal";
			return;
		}
		target = growable(level, station, villager);
		if (target != null) {
			task = Task.GROW;
		}
	}

	private static boolean has(ServerLevel level, List<BlockPos> own, BuilderBag bag, Predicate<ItemStack> test) {
		return bag.stacks().stream().anyMatch(test) || SupplyContainers.firstMatching(level, own, test) != null;
	}

	static boolean isFlower(ItemStack stack) {
		return stack.is(ItemTags.FLOWERS);
	}

	static boolean isPottable(ItemStack stack) {
		return stack.is(ItemTags.SMALL_FLOWERS) && potted(stack) != null;
	}

	private static Map<Block, Block> potted;

	/** The flower pot with {@code stack} in it (a Potted Poppy for a poppy); null if it doesn't go in a pot. */
	@Nullable
	static Block potted(ItemStack stack) {
		if (potted == null) {
			Map<Block, Block> map = new HashMap<>();
			for (Block block : BuiltInRegistries.BLOCK) {
				if (block instanceof FlowerPotBlock pot && block != Blocks.FLOWER_POT) {
					map.put(pot.getPotted(), block);
				}
			}
			potted = map;
		}
		return stack.getItem() instanceof BlockItem bi ? potted.get(bi.getBlock()) : null;
	}

	private void scanPots(ServerLevel level, BlockPos station) {
		emptyPots.clear();
		for (BlockPos p : BlockPos.betweenClosed(station.offset(-POT_RANGE, -6, -POT_RANGE), station.offset(POT_RANGE, 6, POT_RANGE))) {
			if (level.getBlockState(p).is(Blocks.FLOWER_POT)) {
				emptyPots.add(p.immutable());
			}
		}
	}

	/** A small flower, or a weed, standing in the garden (not the tall flowers: they're grown from). */
	@Nullable
	private static BlockPos pickable(ServerLevel level, BlockPos station, Villager villager) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(station.offset(-GARDEN, -2, -GARDEN), station.offset(GARDEN, 2, GARDEN))) {
			BlockState state = level.getBlockState(p);
			if (state.is(BlockTags.SMALL_FLOWERS) && !state.is(Blocks.WITHER_ROSE) || state.is(Blocks.SHORT_GRASS) || state.is(Blocks.FERN)) {
				double d = p.distSqr(villager.blockPosition());
				if (d < bestDistance) {
					bestDistance = d;
					best = p.immutable();
				}
			}
		}
		return best;
	}

	/** A tall flower to bring on, or else bare grass in the garden. */
	@Nullable
	private static BlockPos growable(ServerLevel level, BlockPos station, Villager villager) {
		BlockPos grass = null;
		double grassDistance = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(station.offset(-GARDEN, -2, -GARDEN), station.offset(GARDEN, 2, GARDEN))) {
			BlockState state = level.getBlockState(p);
			if (state.getBlock() instanceof TallFlowerBlock) {
				return p.immutable();
			}
			if (state.is(Blocks.GRASS_BLOCK) && level.getBlockState(p.above()).isAir()) {
				double d = p.distSqr(villager.blockPosition()) + level.random.nextInt(9);
				if (d < grassDistance) {
					grassDistance = d;
					grass = p.immutable();
				}
			}
		}
		return grass;
	}

	private void pick(ServerLevel level, Villager villager, BuilderBag bag) {
		if (drop != null) {
			if (!drop.isAlive()) {
				done();
				return;
			}
			if (!walker.walkTo(level, villager, drop.blockPosition(), 1.8)) {
				if (++stuck > 400) {
					done();
				}
				return;
			}
			ItemStack rest = bag.add(drop.getItem().copy());
			flowersGrown(level, villager, drop.getItem().getCount() - rest.getCount());
			if (rest.isEmpty()) {
				drop.discard();
			} else {
				drop.setItem(rest);
			}
			level.playSound(null, drop.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.4f, 1.4f);
			done();
			return;
		}
		BlockState state = level.getBlockState(target);
		if (state.isAir() || !reach(level, villager, target)) {
			if (state.isAir()) {
				done();
			}
			return;
		}
		boolean flower = state.is(BlockTags.SMALL_FLOWERS);
		for (ItemStack got : Block.getDrops(state, level, target, null, villager, ItemStack.EMPTY)) {
			if (flower || isFlower(got)) {
				ItemStack rest = bag.add(got);
				if (!rest.isEmpty()) {
					Block.popResource(level, target, rest);
				}
			}
		}
		level.destroyBlock(target, false, villager);
		villager.swing(InteractionHand.MAIN_HAND);
		if (flower) {
			flowersGrown(level, villager, 1);
		}
		done();
	}

	private static void flowersGrown(ServerLevel level, Villager villager, int count) {
		if (count <= 0) {
			return;
		}
		int grown = ModAttachments.FLOWERS_GROWN.getOrElse(villager, 0);
		ModAttachments.FLOWERS_GROWN.set(villager, grown + count);
		if ((grown + count) / 8 > grown / 8) {
			BuilderLevels.addXp(level, villager, 1, null);
		}
	}

	private void grow(ServerLevel level, Villager villager, List<BlockPos> own, BuilderBag bag) {
		if (!bag.has(Items.BONE_MEAL, 1)) {
			BlockPos chest = SupplyContainers.firstWith(level, own, Items.BONE_MEAL);
			if (chest == null) {
				done();
				return;
			}
			if (walker.walkTo(level, villager, chest, REACH)) {
				bag.addAll(Items.BONE_MEAL, SupplyContainers.extract(level, own, Items.BONE_MEAL, 16));
				walker.reset();
			}
			return;
		}
		if (!reach(level, villager, target)) {
			return;
		}
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target));
		if (timer < 0) {
			timer = Math.max(8, (int) (BuilderLevels.delay(20, villager) * Partners.factor(villager)));
		}
		if (--timer > 0) {
			return;
		}
		if (BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), level, target)) {
			bag.remove(Items.BONE_MEAL, 1);
			level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, target, 15);
			villager.swing(InteractionHand.MAIN_HAND);
		}
		done();
	}

	private void pot(ServerLevel level, Villager villager, List<BlockPos> own, BuilderBag bag) {
		if (bag.stacks().stream().noneMatch(FloristWork::isPottable)) {
			BlockPos chest = SupplyContainers.firstMatching(level, own, FloristWork::isPottable);
			if (chest == null) {
				done();
				return;
			}
			if (walker.walkTo(level, villager, chest, REACH)) {
				// A few of the kind there's most of, for this pot and the next ones.
				ItemStack first = SupplyContainers.takeOne(level, List.of(chest), FloristWork::isPottable);
				if (!first.isEmpty()) {
					bag.add(first);
					bag.addAll(first.getItem(), SupplyContainers.extract(level, own, first.getItem(), Math.min(emptyPots.size(), 8) - 1));
				}
				walker.reset();
			}
			return;
		}
		if (!level.getBlockState(target).is(Blocks.FLOWER_POT)) {
			done();
			return;
		}
		if (!reach(level, villager, target)) {
			return;
		}
		ItemStack flower = bag.takeFirst(FloristWork::isPottable);
		Block pottedBlock = flower.isEmpty() ? null : potted(flower);
		if (pottedBlock != null) {
			level.setBlockAndUpdate(target, pottedBlock.defaultBlockState());
			level.playSound(null, target, SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 0.8f, 1.2f);
			villager.swing(InteractionHand.MAIN_HAND);
			flower.shrink(1);
			BuilderLevels.addXp(level, villager, 1, null);
		}
		if (!flower.isEmpty()) {
			bag.add(flower);
		}
		emptyPots.remove(target);
		done();
	}

	private void deposit(ServerLevel level, Villager villager, List<BlockPos> own, BuilderBag bag) {
		if (!walker.walkTo(level, villager, own.get(0), REACH)) {
			return;
		}
		for (ItemStack stack : bag.takeAll()) {
			ItemStack rest = SupplyContainers.insert(level, own, stack);
			if (!rest.isEmpty()) {
				Block.popResource(level, own.get(0).above(), rest);
			}
		}
		level.playSound(null, own.get(0), SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.4f, 1f);
		done();
	}

	private boolean reach(ServerLevel level, Villager villager, BlockPos pos) {
		if (walker.reach(level, villager, pos, REACH)) {
			return true;
		}
		if (walker.noSpot() || ++stuck > 600) {
			if (task == Task.POT) {
				emptyPots.remove(pos); // can't get to it
			}
			done();
		}
		return false;
	}

	private static void status(Villager villager, String state) {
		Component title = Component.translatable("message.aliveworkplace.florist.title", ModAttachments.FLOWERS_GROWN.getOrElse(villager, 0));
		boolean warn = state.equals("no_chest") || state.equals("no_bone_meal");
		WorkerStatus.set(villager, title, -1f, Component.translatable("message.aliveworkplace.florist.state." + state)
			.withStyle(warn ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
	}
}
