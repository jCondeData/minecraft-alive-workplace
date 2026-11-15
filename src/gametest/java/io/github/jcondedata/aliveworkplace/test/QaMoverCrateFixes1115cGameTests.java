package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoverWork;
import java.lang.reflect.Method;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.PinkPetalsBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane c, 2026-11-15), the Mover's Crate fixes B1258, B1260 and B1262 (f8cbf4c9f, a369fdc79) from their text and
 * moving-buildings.md ("nothing packed is ever deleted"): a block that is several of its item drops all of them and none
 * twice (every count of turtle eggs, sea pickles, candles and pink petals); a two-block thing with either half on the
 * crate drops as one item with no half left standing (a door's upper half, a bed's foot through reloads, a tall flower,
 * sunflower's upper half); and the storage-room last resort keeps a beehive's bees and honey, a named chest's name and exact items,
 * and a decorated pot's item. Same house, Mover and spot as MoverCrateNoRoomB1234GameTests (the chest's spot lands on his
 * crate). Snow layers and tall grass: QaMoverCrateFixes1115cBugTests (unregistered, fails).
 */
public class QaMoverCrateFixes1115cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	/** The chest's spot: lands on his crate. */
	private static final BlockPos SPOT = new BlockPos(4, 3, 4);

	// ---- helpers ---------------------------------------------------------------------------------------------------

	/** What MoveSite works out the block {@code state} drops as itself (MoveSite.itself, the drop on a crate). */
	@SuppressWarnings("unchecked")
	static List<ItemStack> itself(ServerLevel level, BlockPos pos, BlockState state) {
		try {
			Method m = MoveSite.class.getDeclaredMethod("itself", ServerLevel.class, BlockPos.class, BlockState.class, BlockEntity.class);
			m.setAccessible(true);
			return (List<ItemStack>) m.invoke(null, level, pos, state, null);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("MoveSite.itself", e);
		}
	}

	private static int count(List<ItemStack> stacks, Item item) {
		int n = 0;
		for (ItemStack s : stacks) {
			n += s.is(item) ? s.getCount() : 0;
		}
		return n;
	}

	private static int total(List<ItemStack> stacks) {
		int n = 0;
		for (ItemStack s : stacks) {
			n += s.getCount();
		}
		return n;
	}

	/** Every count of {@code base}'s how-many property drops exactly that many of {@code item}, and nothing else. */
	private static void eachCount(GameTestHelper helper, BlockState base, IntegerProperty p, Item item, StringBuilder bad) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
		for (int n : p.getPossibleValues()) {
			List<ItemStack> drops = itself(level, pos, base.setValue(p, n));
			if (count(drops, item) != n || total(drops) != n) {
				bad.append(base.getBlock()).append(' ').append(p.getName()).append('=').append(n).append(": ").append(drops).append("; ");
			}
		}
	}

	/** The loose {@code item} stacks round the test. */
	private static List<ItemStack> loose(GameTestHelper helper, Item item) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(new BlockPos(0, 1, 0))).inflate(48)).stream()
			.map(ItemEntity::getItem).filter(s -> s.is(item)).toList();
	}

	private static void set(ServerLevel level, BlockPos at, BlockState state) {
		level.setBlock(at, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}

	/** Exactly one {@code item} round the test and none of {@code block} standing anywhere. */
	private static void oneItemNoHalf(GameTestHelper helper, Item item, Block block, String what) {
		int items = QaMoverCrateDrop1114eGameTests.count(helper, item);
		int halves = QaMoverCrateHolds1114cGameTests.standing(helper, block);
		helper.assertTrue(halves == 0, what + ": " + halves + " half(s) of " + block + " left standing alone; " + items + " item(s)");
		helper.assertTrue(items == 1, what + ": " + items + " " + item + " round the test, not 1 (one thing packed)");
	}

	// ---- B1258: a block that is several of its item -----------------------------------------------------------------

	/**
	 * Every count of turtle eggs (1-4), sea pickles (1-4, dry and waterlogged), candles (1-4, lit and colored) and pink
	 * petals (1-4) drops exactly that many of its item and nothing else: none lost (B1258's eggs), none doubled (pickles,
	 * candles and petals, whose loot counts them already).
	 */
	//$ gametest_batch AREA '"qaC1115cEachCount"'
	@GameTest(template = AREA, batch = "qaC1115cEachCount")
	public void everyCountOfAManyBlockDropsExactlyThatMany(GameTestHelper helper) {
		Leftovers.clear(helper);
		StringBuilder bad = new StringBuilder();
		eachCount(helper, Blocks.TURTLE_EGG.defaultBlockState(), TurtleEggBlock.EGGS, Items.TURTLE_EGG, bad);
		eachCount(helper, Blocks.TURTLE_EGG.defaultBlockState().setValue(TurtleEggBlock.HATCH, 2), TurtleEggBlock.EGGS, Items.TURTLE_EGG, bad);
		eachCount(helper, Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.WATERLOGGED, false), SeaPickleBlock.PICKLES, Items.SEA_PICKLE, bad);
		eachCount(helper, Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.WATERLOGGED, true), SeaPickleBlock.PICKLES, Items.SEA_PICKLE, bad);
		eachCount(helper, Blocks.CANDLE.defaultBlockState(), CandleBlock.CANDLES, Items.CANDLE, bad);
		eachCount(helper, Blocks.RED_CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true), CandleBlock.CANDLES, Items.RED_CANDLE, bad);
		eachCount(helper, Blocks.PINK_PETALS.defaultBlockState(), PinkPetalsBlock.AMOUNT, Items.PINK_PETALS, bad);
		helper.assertTrue(bad.isEmpty(), "dropped the wrong count (B1258: nothing lost, nothing doubled): " + bad);
		helper.succeed();
	}

	/** Two turtle eggs in one block on his crate (the other side of B1258's 3): 2 eggs lie there, none standing. */
	//$ gametest_batch AREA '"qaC1115cTwoEggs"'
	@GameTest(template = AREA, batch = "qaC1115cTwoEggs")
	public void twoTurtleEggsOnHisCrateAreTwoEggs(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qac1115ctwoeggs");
		QaMoverCrateVerify1114fGameTests.place(helper, Blocks.TURTLE_EGG.defaultBlockState().setValue(TurtleEggBlock.EGGS, 2));
		helper.runAfterDelay(2, () -> {
			BlockState egg = level.getBlockState(helper.absolutePos(SPOT));
			helper.assertTrue(egg.is(Blocks.TURTLE_EGG) && egg.getValue(TurtleEggBlock.EGGS) == 2, "setup: the eggs are now " + egg);
			QaMoverCrateQa1114c4GameTests.toTheEnd(helper, s, () -> { }, "two turtle eggs on his crate");
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.TURTLE_EGG, 2, "the block's two turtle eggs");
			helper.assertTrue(QaMoverCrateHolds1114cGameTests.standing(helper, Blocks.TURTLE_EGG) == 0, "turtle eggs left standing");
			helper.succeed();
		});
	}

	// ---- B1260: a two-block thing with one half on the crate -------------------------------------------------------

	/**
	 * A crimson door whose UPPER half stands where the chest stood (its lower half replaces the floor under it, so the
	 * lower half goes up first, right under the crate, and the upper lands on the crate): one crimson door, no half left.
	 */
	//$ gametest_batch AREA '"qaC1115cDoorUpper"'
	@GameTest(template = AREA, batch = "qaC1115cDoorUpper")
	public void aDoorWhoseUpperHalfLandsOnHisCrateIsOneDoor(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qac1115cdoorup");
		BlockState door = Blocks.CRIMSON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
		BlockPos upper = helper.absolutePos(SPOT);
		QaMoverCrateVerify1114fGameTests.place(helper, Blocks.AIR.defaultBlockState());
		set(level, upper.below(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		set(level, upper, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(level.getBlockState(upper).is(Blocks.CRIMSON_DOOR) && level.getBlockState(upper.below()).is(Blocks.CRIMSON_DOOR), "setup: no door");
			QaMoverCrateQa1114c4GameTests.toTheEnd(helper, s, () -> { }, "a door's upper half on his crate");
			oneItemNoHalf(helper, Items.CRIMSON_DOOR, Blocks.CRIMSON_DOOR, "a door's upper half on his crate");
			helper.succeed();
		});
	}

	/**
	 * A lime bed whose FOOT stands where the chest stood (its head lands just south of the crate), the site saved and read
	 * back after every step of the put-up: one lime bed, no half bed standing (the head isn't put up after a reload).
	 */
	//$ gametest_batch AREA '"qaC1115cBedFootReload"'
	@GameTest(template = AREA, batch = "qaC1115cBedFootReload")
	public void aBedWhoseFootLandsOnHisCrateIsOneBedThroughReloads(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qac1115cbedfoot");
		BlockState bed = Blocks.LIME_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
		BlockPos foot = helper.absolutePos(SPOT);
		QaMoverCrateVerify1114fGameTests.place(helper, Blocks.AIR.defaultBlockState());
		set(level, foot, bed.setValue(BedBlock.PART, BedPart.FOOT));
		set(level, foot.south(), bed.setValue(BedBlock.PART, BedPart.HEAD));
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(level.getBlockState(foot).is(Blocks.LIME_BED) && level.getBlockState(foot.south()).is(Blocks.LIME_BED), "setup: no bed");
			MoveSite site = MoverGameTests.work(level, s, x -> x.stage() != MoveSite.Stage.DISMANTLE);
			helper.assertTrue(site.stage().rebuilding(), "setup: it didn't get to putting it up: " + site.stage());
			int steps = 0;
			int reloads = 0;
			while (site.stage() != MoveSite.Stage.DONE && steps++ < MoverGameTests.MAX_STEPS) {
				MoverWork.work(level, s.mover(), site);
				if (site.stage() != MoveSite.Stage.DONE) {
					site = MoverGameTests.reload(level, s);
					reloads++;
				}
			}
			helper.assertTrue(reloads >= 5, "only " + reloads + " reloads");
			MoverCrateNoRoomB1234GameTests.assertEnded(helper, level, s, site, 1, "a bed's foot on his crate, reloaded every step");
			oneItemNoHalf(helper, Items.LIME_BED, Blocks.LIME_BED, "a bed's foot on his crate, reloaded every step");
			helper.succeed();
		});
	}

	/** A rose bush whose lower half lands on his crate (its upper half right over it): one rose bush, no half left. */
	//$ gametest_batch AREA '"qaC1115cRoseBush"'
	@GameTest(template = AREA, batch = "qaC1115cRoseBush")
	public void aRoseBushWhoseLowerHalfLandsOnHisCrateIsOneRoseBush(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qac1115crose");
		BlockPos lower = helper.absolutePos(SPOT);
		QaMoverCrateVerify1114fGameTests.place(helper, Blocks.AIR.defaultBlockState());
		set(level, lower, Blocks.ROSE_BUSH.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
		set(level, lower.above(), Blocks.ROSE_BUSH.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(QaMoverCrateHolds1114cGameTests.standing(helper, Blocks.ROSE_BUSH) == 2, "setup: no rose bush");
			QaMoverCrateQa1114c4GameTests.toTheEnd(helper, s, () -> { }, "a rose bush's lower half on his crate");
			oneItemNoHalf(helper, Items.ROSE_BUSH, Blocks.ROSE_BUSH, "a rose bush's lower half on his crate");
			helper.succeed();
		});
	}

	/**
	 * A sunflower whose UPPER half lands on his crate (its lower half replaces the floor under the chest's spot, so it goes
	 * up first, right under the crate): one sunflower, no half left standing. (Tall grass: QaMoverCrateFixes1115cBugTests.)
	 */
	//$ gametest_batch AREA '"qaC1115cSunflowerUpper"'
	@GameTest(template = AREA, batch = "qaC1115cSunflowerUpper")
	public void aSunflowerWhoseUpperHalfLandsOnHisCrateIsOneSunflower(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qac1115csunflower");
		BlockPos upper = helper.absolutePos(SPOT);
		QaMoverCrateVerify1114fGameTests.place(helper, Blocks.AIR.defaultBlockState());
		set(level, upper.below(), Blocks.SUNFLOWER.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
		set(level, upper, Blocks.SUNFLOWER.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(QaMoverCrateHolds1114cGameTests.standing(helper, Blocks.SUNFLOWER) == 2, "setup: no sunflower");
			QaMoverCrateQa1114c4GameTests.toTheEnd(helper, s, () -> { }, "a sunflower's upper half on his crate");
			oneItemNoHalf(helper, Items.SUNFLOWER, Blocks.SUNFLOWER, "a sunflower's upper half on his crate");
			helper.succeed();
		});
	}

	// ---- B1262: the storage room's last resort ----------------------------------------------------------------------

	/**
	 * A beehive with 3 bees and honey level 5 on a crate in a storage room: one beehive, exactly its 3 bees (none lost,
	 * none doubled), and the hive's item keeps its honey level (Silk Touch loot copies it).
	 */
	//$ gametest_batch AREA '"qaC1115cStoreHiveHoney"'
	@GameTest(template = AREA, batch = "qaC1115cStoreHiveHoney")
	public void aHoneyFullBeehiveInAStorageRoomKeepsItsThreeBeesAndHoney(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = QaMoverCrateHolds1114cGameTests.crateInside(helper, "qac1115cstorehive");
		QaMoverCrateHolds1114cGameTests.replaceChest(helper, Blocks.BEEHIVE.defaultBlockState().setValue(BeehiveBlock.HONEY_LEVEL, 5), be -> {
			for (int i = 0; i < 3; i++) {
				Bee bee = EntityType.BEE.create(level);
				bee.moveTo(be.getBlockPos().getX() + 0.5, be.getBlockPos().getY() + 1, be.getBlockPos().getZ() + 0.5);
				level.addFreshEntity(bee);
				((BeehiveBlockEntity) be).addOccupant(bee);
			}
		});
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(QaMoverCrateLastResort1114e2GameTests.bees(helper) == 3, "setup: " + QaMoverCrateLastResort1114e2GameTests.bees(helper) + " bees, not 3");
			QaMoverCrateLastResort1114e2GameTests.dropInTheStoreRoom(helper, s);
			QaMoverCrateEdges1114bGameTests.assertExactly(helper, Items.BEEHIVE, 1, "the beehive (storage room)");
			int now = QaMoverCrateLastResort1114e2GameTests.bees(helper);
			helper.assertTrue(now == 3, "the hive's bees (storage room): " + now + " of 3 (" + (now < 3 ? "lost" : "doubled") + ")");
			List<ItemStack> hives = loose(helper, Items.BEEHIVE);
			helper.assertTrue(hives.size() == 1, "setup: " + hives.size() + " loose beehive stacks");
			BlockItemStateProperties props = hives.get(0).get(DataComponents.BLOCK_STATE);
			Integer honey = props == null ? null : props.get(BeehiveBlock.HONEY_LEVEL);
			helper.assertTrue(honey != null && honey == 5, "the beehive's honey level (storage room): " + honey + ", not 5");
			helper.succeed();
		});
	}

	/**
	 * A chest named "Pantry" holding 12 bread, 64 emeralds and 3 ender pearls on a crate in a storage room: one chest item
	 * still named Pantry (its Silk Touch loot copies the name), and every item exactly once (the container's slots dropped
	 * loose, never also inside the chest's item, never twice).
	 */
	//$ gametest_batch AREA '"qaC1115cStoreNamedChest"'
	@GameTest(template = AREA, batch = "qaC1115cStoreNamedChest")
	public void aNamedChestInAStorageRoomKeepsItsNameAndExactlyItsItems(GameTestHelper helper) {
		Leftovers.clear(helper);
		MoverGameTests.Setup s = QaMoverCrateHolds1114cGameTests.crateInside(helper, "qac1115cstorechest");
		ItemStack named = new ItemStack(Items.CHEST);
		named.set(DataComponents.CUSTOM_NAME, Component.literal("Pantry"));
		QaMoverCrateHolds1114cGameTests.replaceChest(helper, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH), be -> {
			be.applyComponentsFromItemStack(named);
			Container c = (Container) be;
			c.setItem(0, new ItemStack(Items.BREAD, 12));
			c.setItem(13, new ItemStack(Items.EMERALD, 64));
			c.setItem(26, new ItemStack(Items.ENDER_PEARL, 3));
		});
		helper.runAfterDelay(2, () -> {
			QaMoverCrateQa1114c4GameTests.assertWithBoxes(helper, Items.EMERALD, 64, "setup");
			QaMoverCrateLastResort1114e2GameTests.dropInTheStoreRoom(helper, s);
			QaMoverCrateEdges1114bGameTests.assertExactly(helper, Items.CHEST, 1, "the chest (storage room)");
			QaMoverCrateQa1114c4GameTests.assertWithBoxes(helper, Items.BREAD, 12, "the chest's bread (storage room)");
			QaMoverCrateQa1114c4GameTests.assertWithBoxes(helper, Items.EMERALD, 64, "the chest's emeralds (storage room)");
			QaMoverCrateQa1114c4GameTests.assertWithBoxes(helper, Items.ENDER_PEARL, 3, "the chest's ender pearls (storage room)");
			List<ItemStack> chests = loose(helper, Items.CHEST);
			Component name = chests.size() == 1 ? chests.get(0).get(DataComponents.CUSTOM_NAME) : null;
			helper.assertTrue(name != null && name.getString().equals("Pantry"), "the chest's name (storage room): " + name + " (" + chests.size() + " loose chests)");
			helper.succeed();
		});
	}

	/** A decorated pot holding 40 gold ingots on a crate in a storage room: one pot (not sherds or bricks) and exactly its 40 gold. */
	//$ gametest_batch AREA '"qaC1115cStorePot"'
	@GameTest(template = AREA, batch = "qaC1115cStorePot")
	public void aDecoratedPotInAStorageRoomKeepsExactlyItsGold(GameTestHelper helper) {
		Leftovers.clear(helper);
		MoverGameTests.Setup s = QaMoverCrateHolds1114cGameTests.crateInside(helper, "qac1115cstorepot");
		QaMoverCrateHolds1114cGameTests.replaceChest(helper, Blocks.DECORATED_POT.defaultBlockState(),
			be -> ((DecoratedPotBlockEntity) be).setTheItem(new ItemStack(Items.GOLD_INGOT, 40)));
		helper.runAfterDelay(2, () -> {
			QaMoverCrateEdges1114bGameTests.assertExactly(helper, Items.GOLD_INGOT, 40, "setup");
			QaMoverCrateLastResort1114e2GameTests.dropInTheStoreRoom(helper, s);
			QaMoverCrateEdges1114bGameTests.assertExactly(helper, Items.DECORATED_POT, 1, "the decorated pot (storage room)");
			QaMoverCrateEdges1114bGameTests.assertExactly(helper, Items.BRICK, 0, "bricks from the pot (storage room)");
			QaMoverCrateQa1114c4GameTests.assertWithBoxes(helper, Items.GOLD_INGOT, 40, "the pot's gold (storage room)");
			helper.succeed();
		});
	}
}
