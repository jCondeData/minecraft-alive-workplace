package io.github.jcondedata.aliveworkplace.bee;

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
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * The Beekeeper's shift at the Apiary: the beehives and bee nests within {@link #RADIUS} blocks are harvested when full
 * — honey bottles with the glass bottles in the chests, honeycomb with shears (3 a hive; the bees stay calm with a
 * campfire under the hive, and fly out otherwise) — kept in flowers (planted from the chests round any hive with fewer
 * than {@link #FLOWERS_PER_HIVE} near it) and filled with bees (two fed flowers to breed while there are fewer than
 * {@link #BEES_PER_HIVE} a hive). What's harvested goes in the chests by the Apiary.
 */
public class BeekeeperWork extends Behavior<Villager> {
	public static int RADIUS = 16;
	/** Flowers wanted within 4 blocks of every hive. */
	static final int FLOWERS_PER_HIVE = 4;
	/** Bees wanted for every hive (a hive holds 3). */
	static final int BEES_PER_HIVE = 3;
	/** Honey bottles kept in the chests before the beekeeper takes honeycomb instead. */
	static final int KEEP_HONEY = 16;
	private static final int LOOK_EVERY = 60;
	private static final float SPEED = 0.55f;
	private static final double REACH = 3.0;

	enum Task { NONE, DEPOSIT, HARVEST, PLANT, BREED }

	private final Walker walker = new Walker(SPEED);
	private Task task = Task.NONE;
	@Nullable
	private BlockPos target;
	@Nullable
	private Bee bee;
	@Nullable
	private Bee mate;
	private boolean bottle;
	private int lookTimer;
	private int timer;
	private int stuck;
	/** What's shown while there's nothing to do. */
	private String idle = "none";

	public BeekeeperWork() {
		super(ImmutableMap.of(
			MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 1200);
	}

	public static boolean isBeekeeper(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == ModVillagers.BEEKEEPER;
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return !villager.isSleeping() && isBeekeeper(villager) && Builders.benchPos(villager).isPresent();
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
				status(villager, idle, RADIUS);
				return;
			}
			lookTimer = LOOK_EVERY;
			choose(level, villager, station, own, bag);
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
			case HARVEST -> harvest(level, villager, own, bag);
			case PLANT -> plant(level, villager, own, bag);
			case BREED -> breed(level, villager, own, bag);
			default -> done();
		}
	}

	private void done() {
		task = Task.NONE;
		target = null;
		bee = null;
		mate = null;
		lookTimer = 10;
		walker.reset();
	}

	/** The hives around the Apiary. */
	private static List<BlockPos> hives(ServerLevel level, BlockPos station) {
		return level.getPoiManager().findAll(h -> h.is(PoiTypes.BEEHIVE) || h.is(PoiTypes.BEE_NEST), p -> true, station, RADIUS,
			PoiManager.Occupancy.ANY).map(BlockPos::immutable).sorted(Comparator.comparingDouble(p -> p.distSqr(station))).toList();
	}

	/** What to do next: put the harvest away, harvest a full hive, plant flowers, breed bees. */
	private void choose(ServerLevel level, Villager villager, BlockPos station, List<BlockPos> own, BuilderBag bag) {
		if (bag.stacks().stream().anyMatch(s -> !s.isEmpty() && !isTool(s) && !isFlower(s))) {
			task = Task.DEPOSIT;
			return;
		}
		List<BlockPos> hives = hives(level, station);
		idle = hives.isEmpty() ? "no_hives" : "none";
		if (hives.isEmpty()) {
			return;
		}
		boolean bottles = has(level, own, bag, s -> s.is(Items.GLASS_BOTTLE));
		boolean shears = has(level, own, bag, s -> s.is(Items.SHEARS));
		if (bottles || shears) {
			for (BlockPos hive : hives) {
				BlockState state = level.getBlockState(hive);
				if (state.hasProperty(BeehiveBlock.HONEY_LEVEL) && state.getValue(BeehiveBlock.HONEY_LEVEL) >= BeehiveBlock.MAX_HONEY_LEVELS) {
					// Honey bottles while the chests are short of them, honeycomb after that (or without bottles).
					bottle = bottles && (!shears || SupplyContainers.count(level, own, Items.HONEY_BOTTLE) < KEEP_HONEY);
					task = Task.HARVEST;
					target = hive;
					return;
				}
			}
		}
		boolean flowers = has(level, own, bag, BeekeeperWork::isFlower);
		if (!flowers) {
			return;
		}
		for (BlockPos hive : hives) {
			if (flowersNear(level, hive) < FLOWERS_PER_HIVE) {
				BlockPos spot = flowerSpot(level, hive);
				if (spot != null) {
					task = Task.PLANT;
					target = spot;
					return;
				}
			}
		}
		List<Bee> bees = level.getEntitiesOfClass(Bee.class, new AABB(station).inflate(RADIUS), Bee::isAlive);
		int inHives = 0;
		for (BlockPos hive : hives) {
			if (level.getBlockEntity(hive) instanceof BeehiveBlockEntity entity) {
				inHives += entity.getOccupantCount();
			}
		}
		if (bees.size() + inHives < hives.size() * BEES_PER_HIVE) {
			List<Bee> ready = bees.stream().filter(b -> !b.isBaby() && b.canFallInLove() && b.getAge() == 0)
				.sorted(Comparator.comparingDouble(villager::distanceToSqr)).toList();
			if (ready.size() >= 2) {
				task = Task.BREED;
				bee = ready.get(0);
				mate = ready.get(1);
			}
		}
	}

	private static boolean has(ServerLevel level, List<BlockPos> own, BuilderBag bag, Predicate<ItemStack> test) {
		return bag.stacks().stream().anyMatch(test) || SupplyContainers.firstMatching(level, own, test) != null;
	}

	static boolean isFlower(ItemStack stack) {
		return stack.is(ItemTags.SMALL_FLOWERS) && !stack.is(Items.WITHER_ROSE) && stack.getItem() instanceof BlockItem;
	}

	private static boolean isTool(ItemStack stack) {
		return stack.is(Items.GLASS_BOTTLE) || stack.is(Items.SHEARS);
	}

	/** Takes one thing passing {@code test} from the chests into the bag unless it's already there: true once it is. */
	private boolean fetch(ServerLevel level, Villager villager, List<BlockPos> own, BuilderBag bag, Predicate<ItemStack> test) {
		if (bag.stacks().stream().anyMatch(test)) {
			return true;
		}
		BlockPos chest = SupplyContainers.firstMatching(level, own, test);
		if (chest == null) {
			done();
			return false;
		}
		if (!walker.walkTo(level, villager, chest, REACH)) {
			return false;
		}
		ItemStack got = SupplyContainers.takeOne(level, List.of(chest), test);
		if (got.isEmpty()) {
			done();
			return false;
		}
		bag.add(got);
		level.playSound(null, chest, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.4f, 1.1f);
		walker.reset();
		return false; // next tick: on to the job
	}

	private void harvest(ServerLevel level, Villager villager, List<BlockPos> own, BuilderBag bag) {
		Predicate<ItemStack> tool = bottle ? s -> s.is(Items.GLASS_BOTTLE) : s -> s.is(Items.SHEARS);
		if (!fetch(level, villager, own, bag, tool)) {
			return;
		}
		BlockState state = level.getBlockState(target);
		if (!state.hasProperty(BeehiveBlock.HONEY_LEVEL) || state.getValue(BeehiveBlock.HONEY_LEVEL) < BeehiveBlock.MAX_HONEY_LEVELS) {
			done();
			return;
		}
		if (!reach(level, villager, target)) {
			return;
		}
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target));
		if (timer < 0) {
			timer = Math.max(10, (int) (BuilderLevels.delay(30, villager) * Partners.factor(villager)));
		}
		if (--timer > 0) {
			return;
		}
		villager.swing(InteractionHand.MAIN_HAND);
		if (bottle) {
			bag.remove(Items.GLASS_BOTTLE, 1);
			bag.add(new ItemStack(Items.HONEY_BOTTLE));
			level.playSound(null, target, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1f, 1f);
		} else {
			ItemStack shears = bag.takeFirst(s -> s.is(Items.SHEARS));
			shears.setDamageValue(shears.getDamageValue() + 1);
			if (shears.getDamageValue() < shears.getMaxDamage()) {
				bag.add(shears);
			}
			bag.add(new ItemStack(Items.HONEYCOMB, 3));
			level.playSound(null, target, SoundEvents.BEEHIVE_SHEAR, SoundSource.BLOCKS, 1f, 1f);
		}
		level.setBlock(target, state.setValue(BeehiveBlock.HONEY_LEVEL, 0), Block.UPDATE_ALL);
		// A Bug or Grass partner circles the hive being harvested (ROADMAP 28.6).
		io.github.jcondedata.aliveworkplace.work.PartnerShows.cue(villager, "harvest", target);
		// Smoked, the bees stay put; otherwise they come out (they don't go for villagers).
		if (level.getBlockEntity(target) instanceof BeehiveBlockEntity hive && !hive.isFireNearby()) {
			hive.emptyAllLivingFromHive(null, state, BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
		}
		ModAttachments.HIVES_HARVESTED.set(villager, ModAttachments.HIVES_HARVESTED.getOrElse(villager, 0) + 1);
		BuilderLevels.addXp(level, villager, 2, null);
		done();
	}

	private void plant(ServerLevel level, Villager villager, List<BlockPos> own, BuilderBag bag) {
		if (!fetch(level, villager, own, bag, BeekeeperWork::isFlower)) {
			return;
		}
		if (!level.getBlockState(target).isAir()) {
			done();
			return;
		}
		if (!reach(level, villager, target)) {
			return;
		}
		ItemStack flower = bag.takeFirst(BeekeeperWork::isFlower);
		if (flower.isEmpty()) {
			done();
			return;
		}
		BlockState plant = ((BlockItem) flower.getItem()).getBlock().defaultBlockState();
		if (plant.canSurvive(level, target)) {
			level.setBlockAndUpdate(target, plant);
			level.playSound(null, target, SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 0.8f, 1f);
			villager.swing(InteractionHand.MAIN_HAND);
			flower.shrink(1);
		}
		if (!flower.isEmpty()) {
			bag.add(flower);
		}
		done();
	}

	private void breed(ServerLevel level, Villager villager, List<BlockPos> own, BuilderBag bag) {
		if (bag.stacks().stream().filter(BeekeeperWork::isFlower).mapToInt(ItemStack::getCount).sum() < 2) {
			BlockPos chest = SupplyContainers.firstMatching(level, own, BeekeeperWork::isFlower);
			if (chest == null) {
				done();
				return;
			}
			if (!walker.walkTo(level, villager, chest, REACH)) {
				return;
			}
			for (int i = 0; i < 2; i++) {
				ItemStack got = SupplyContainers.takeOne(level, own, BeekeeperWork::isFlower);
				if (!got.isEmpty()) {
					bag.add(got);
				}
			}
			walker.reset();
			return;
		}
		Bee next = bee != null && bee.isAlive() && !bee.isInLove() ? bee : mate != null && mate.isAlive() && !mate.isInLove() ? mate : null;
		if (next == null) {
			done();
			return;
		}
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(next, true));
		if (villager.distanceToSqr(next) > 4.5 * 4.5) {
			walker.walkTo(level, villager, next.blockPosition(), REACH);
			if (++stuck > 400) {
				done(); // it won't come down
			}
			return;
		}
		ItemStack flower = bag.takeFirst(BeekeeperWork::isFlower);
		if (flower.isEmpty() || !next.canFallInLove()) {
			done();
			return;
		}
		flower.shrink(1);
		if (!flower.isEmpty()) {
			bag.add(flower);
		}
		next.setInLove(null);
		villager.swing(InteractionHand.MAIN_HAND);
		level.playSound(null, next.blockPosition(), SoundEvents.BEE_POLLINATE, SoundSource.NEUTRAL, 1f, 1f);
		stuck = 0;
		if (next == mate) {
			BuilderLevels.addXp(level, villager, 1, null);
			done();
		}
	}

	/** Puts the honey and honeycomb in the chests (and the tools and flowers back too). */
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
		villager.swing(InteractionHand.MAIN_HAND);
		level.playSound(null, own.get(0), SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.4f, 1f);
		done();
	}

	private boolean reach(ServerLevel level, Villager villager, BlockPos pos) {
		if (walker.reach(level, villager, pos, REACH)) {
			return true;
		}
		if (walker.noSpot() || ++stuck > 600) {
			done();
		}
		return false;
	}

	/** Flowers within 4 blocks of {@code hive}. */
	static int flowersNear(ServerLevel level, BlockPos hive) {
		int count = 0;
		for (BlockPos p : BlockPos.betweenClosed(hive.offset(-4, -3, -4), hive.offset(4, 2, 4))) {
			if (level.getBlockState(p).is(BlockTags.FLOWERS)) {
				count++;
			}
		}
		return count;
	}

	/** Somewhere to plant a flower near {@code hive}: grass or dirt with air above, nearest first. */
	@Nullable
	static BlockPos flowerSpot(ServerLevel level, BlockPos hive) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(hive.offset(-3, -3, -3), hive.offset(3, 1, 3))) {
			BlockState ground = level.getBlockState(p.below());
			if (level.getBlockState(p).isAir() && (ground.is(Blocks.GRASS_BLOCK) || ground.is(BlockTags.DIRT) && !ground.is(Blocks.MUD))) {
				double d = p.distSqr(hive);
				if (d < bestDistance) {
					bestDistance = d;
					best = p.immutable();
				}
			}
		}
		return best;
	}

	private static void status(Villager villager, String state, Object... args) {
		Component title = Component.translatable("message.aliveworkplace.beekeeper.title", ModAttachments.HIVES_HARVESTED.getOrElse(villager, 0));
		boolean warn = state.equals("no_chest") || state.equals("no_hives");
		WorkerStatus.set(villager, title, -1f, Component.translatable("message.aliveworkplace.beekeeper.state." + state, args)
			.withStyle(warn ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
	}
}
