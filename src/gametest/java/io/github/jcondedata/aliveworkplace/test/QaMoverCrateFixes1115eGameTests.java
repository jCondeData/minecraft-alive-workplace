package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoverWork;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.PotDecorations;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane e, 2026-11-15), the Mover's Crate drop fixes B1258, B1260 (f8cbf4c9f) and B1261, B1262 (a369fdc79), tested
 * from the bugs' text, not the code: a packed block whose new spot holds his crate is dropped as exactly what was packed
 * (moving-buildings.md: nothing packed is ever deleted, nothing made from nothing). Turtle eggs at 2 and 4 (B1258 was 3,
 * in the open and in a storage room), sea pickles that the loot already counts (must not be doubled), a door whose UPPER
 * half lands on the crate and a bed whose FOOT does (B1260 tested the lower half and the head), a full beehive with 3 bees
 * and honey 5 in a storage room through reloads (B1262 was 2 bees, no reload), and a decorated pot with four sherds and
 * nothing inside in a storage room (the last resort must drop the pot alone, its sherds kept on it, nothing loose).
 * Same house, Mover and spot as MoverCrateNoRoomB1234GameTests (the chest's spot, (4, 3, 4), lands on his crate).
 */
public class QaMoverCrateFixes1115eGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos SPOT = new BlockPos(4, 3, 4);

	// ---- helpers ---------------------------------------------------------------------------------------------------

	/** Every loose item round the test, by id with its total count (to show what lies there and to catch extras). */
	private static Map<String, Integer> loose(GameTestHelper helper) {
		Map<String, Integer> out = new LinkedHashMap<>();
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(new BlockPos(0, 1, 0))).inflate(48))) {
			out.merge(BuiltInRegistries.ITEM.getKey(e.getItem().getItem()).toString(), e.getItem().getCount(), Integer::sum);
		}
		return out;
	}

	/** The one loose item stack of {@code item} round the test; fails unless there is exactly one stack of it. */
	private static ItemStack theOne(GameTestHelper helper, net.minecraft.world.item.Item item, String what) {
		ItemStack found = null;
		int stacks = 0;
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(new BlockPos(0, 1, 0))).inflate(48))) {
			if (e.getItem().is(item)) {
				found = e.getItem();
				stacks++;
			}
		}
		helper.assertTrue(stacks == 1 && found != null && found.getCount() == 1, what + ": " + stacks + " loose stacks of " + item + ", not 1; loose " + loose(helper));
		return found;
	}

	/** Where {@code block} stands in the area (test-relative) and as what, for failure messages. */
	private static String standingAt(GameTestHelper helper, Block block) {
		StringBuilder out = new StringBuilder();
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(29, 29, 29)))) {
			BlockState there = helper.getLevel().getBlockState(pos);
			if (there.is(block)) {
				out.append(' ').append(helper.relativePos(pos).toShortString()).append('=').append(there);
			}
		}
		return out.toString();
	}

	/** The open-room case: {@code state} where the chest stood, the move run to its end (done, skipped 1, crate kept). */
	private static void inTheOpen(GameTestHelper helper, String name, BlockState state, Runnable then) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, name);
		QaMoverCrateVerify1114fGameTests.place(helper, state);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(level.getBlockState(helper.absolutePos(SPOT)).equals(state), "setup: the spot holds " + level.getBlockState(helper.absolutePos(SPOT)));
			MoveSite site = QaMoverCrateVerify1114fGameTests.run(helper, s, () -> { });
			MoverCrateNoRoomB1234GameTests.assertEnded(helper, level, s, site, 1, name + " on his crate");
			then.run();
			helper.succeed();
		});
	}

	// ---- B1258: blocks that are several of their item ------------------------------------------------------------------

	/** Two turtle eggs in one block on his crate (room round it): exactly 2 turtle eggs, none standing. */
	//$ gametest_batch AREA '"qaE1115eTwoEggs"'
	@GameTest(template = AREA, batch = "qaE1115eTwoEggs")
	public void twoTurtleEggsOnHisCrateAreDroppedAsTwoEggs(GameTestHelper helper) {
		inTheOpen(helper, "qae1115eegg2", Blocks.TURTLE_EGG.defaultBlockState().setValue(TurtleEggBlock.EGGS, 2), () -> {
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.TURTLE_EGG, 2, "two turtle eggs in one block");
			helper.assertTrue(QaMoverCrateHolds1114cGameTests.standing(helper, Blocks.TURTLE_EGG) == 0, "turtle eggs left standing");
		});
	}

	/** Four turtle eggs in one block on his crate in a storage room (the last resort): exactly 4 turtle eggs, none standing. */
	//$ gametest_batch AREA '"qaE1115eFourEggsStore"'
	@GameTest(template = AREA, batch = "qaE1115eFourEggsStore")
	public void fourTurtleEggsOnACrateAmongContainersAreDroppedAsFourEggs(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Leftovers.clear(helper);
		MoverGameTests.Setup s = QaMoverCrateHolds1114cGameTests.crateInside(helper, "qae1115eegg4");
		QaMoverCrateVerify1114fGameTests.place(helper, Blocks.TURTLE_EGG.defaultBlockState().setValue(TurtleEggBlock.EGGS, 4));
		helper.runAfterDelay(2, () -> {
			BlockState egg = level.getBlockState(helper.absolutePos(SPOT));
			helper.assertTrue(egg.is(Blocks.TURTLE_EGG) && egg.getValue(TurtleEggBlock.EGGS) == 4, "setup: the eggs are now " + egg);
			QaMoverCrateLastResort1114e2GameTests.dropInTheStoreRoom(helper, s);
			QaMoverCrateHolds1114cGameTests.assertExactly(helper, Items.TURTLE_EGG, 4, "four turtle eggs in one block (storage room)");
			helper.assertTrue(QaMoverCrateHolds1114cGameTests.standing(helper, Blocks.TURTLE_EGG) == 0, "turtle eggs left standing (storage room)");
			helper.succeed();
		});
	}

	/** Three sea pickles in one block on his crate: their loot counts them already, so exactly 3 (not 5 or 7: never added twice). */
	//$ gametest_batch AREA '"qaE1115ePickles"'
	@GameTest(template = AREA, batch = "qaE1115ePickles")
	public void threeSeaPicklesOnHisCrateAreThreeNotMore(GameTestHelper helper) {
		inTheOpen(helper, "qae1115epickle", Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, 3)
			.setValue(SeaPickleBlock.WATERLOGGED, false), () -> {
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.SEA_PICKLE, 3, "three sea pickles in one block");
			helper.assertTrue(QaMoverCrateHolds1114cGameTests.standing(helper, Blocks.SEA_PICKLE) == 0, "sea pickles left standing");
		});
	}

	// ---- B1260: two-block things, the other half on the crate -----------------------------------------------------------

	/**
	 * An warped door whose UPPER half stands where the chest stood (its lower half in the floor under it), so the upper half
	 * lands on his crate and the lower half right under it: one warped door, and no half door left standing anywhere.
	 */
	//$ gametest_batch AREA '"qaE1115eDoorUpper"'
	@GameTest(template = AREA, batch = "qaE1115eDoorUpper")
	public void aDoorWhoseUpperHalfLandsOnHisCrateIsOneDoor(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qae1115edoor");
		BlockState door = Blocks.WARPED_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
		BlockPos upper = helper.absolutePos(SPOT);
		QaMoverCrateVerify1114fGameTests.place(helper, Blocks.AIR.defaultBlockState());
		level.setBlock(upper.below(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		level.setBlock(upper, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(level.getBlockState(upper).is(Blocks.WARPED_DOOR) && level.getBlockState(upper.below()).is(Blocks.WARPED_DOOR),
				"setup: no whole door: " + level.getBlockState(upper.below()) + " / " + level.getBlockState(upper));
			QaMoverCrateQa1114c4GameTests.toTheEnd(helper, s, () -> { }, "a door's upper half on his crate");
			int halves = QaMoverCrateHolds1114cGameTests.standing(helper, Blocks.WARPED_DOOR);
			BlockState under = level.getBlockState(QaMoverCrateBorrow1114c3GameTests.crate(helper).below());
			helper.assertTrue(halves == 0, halves + " warped door half(s) left standing (the upper half dropped); right under his crate: " + under
				+ "; standing at" + standingAt(helper, Blocks.WARPED_DOOR) + "; crate at " + helper.relativePos(QaMoverCrateBorrow1114c3GameTests.crate(helper)).toShortString());
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.WARPED_DOOR, 1, "one warped door packed");
			helper.succeed();
		});
	}

	/**
	 * A lime bed whose FOOT stands where the chest stood (its head just north), so the foot lands on his crate and the head
	 * right north of it: one lime bed, and no half bed left standing.
	 */
	//$ gametest_batch AREA '"qaE1115eBedFoot"'
	@GameTest(template = AREA, batch = "qaE1115eBedFoot")
	public void aBedWhoseFootLandsOnHisCrateIsOneBed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qae1115ebed");
		BlockState bed = Blocks.LIME_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
		BlockPos foot = helper.absolutePos(SPOT);
		QaMoverCrateVerify1114fGameTests.place(helper, Blocks.AIR.defaultBlockState());
		level.setBlock(foot, bed.setValue(BedBlock.PART, BedPart.FOOT), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		level.setBlock(foot.north(), bed.setValue(BedBlock.PART, BedPart.HEAD), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(level.getBlockState(foot).is(Blocks.LIME_BED) && level.getBlockState(foot.north()).is(Blocks.LIME_BED), "setup: no whole bed");
			QaMoverCrateQa1114c4GameTests.toTheEnd(helper, s, () -> { }, "a bed's foot on his crate");
			int halves = QaMoverCrateHolds1114cGameTests.standing(helper, Blocks.LIME_BED);
			BlockState beside = level.getBlockState(QaMoverCrateBorrow1114c3GameTests.crate(helper).north());
			helper.assertTrue(halves == 0, halves + " lime bed half(s) left standing (the foot dropped); right north of his crate: " + beside
				+ "; standing at" + standingAt(helper, Blocks.LIME_BED) + "; crate at " + helper.relativePos(QaMoverCrateBorrow1114c3GameTests.crate(helper)).toShortString());
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.LIME_BED, 1, "one lime bed packed");
			helper.succeed();
		});
	}

	// ---- B1262 and the last resort --------------------------------------------------------------------------------------

	/**
	 * A full beehive (3 bees, honey level 5) on his crate in a storage room, the move saved and read back every 3 steps of
	 * the put-up: one beehive that still holds its 3 bees and its honey level 5, no bee lost or doubled.
	 */
	//$ gametest_batch AREA '"qaE1115eHiveReload"'
	@GameTest(template = AREA, batch = "qaE1115eHiveReload")
	public void aFullBeehiveOnACrateAmongContainersKeepsBeesAndHoneyThroughReloads(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Leftovers.clear(helper);
		MoverGameTests.Setup s = QaMoverCrateHolds1114cGameTests.crateInside(helper, "qae1115ehive");
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
			MoveSite site = MoverGameTests.work(level, s, x -> x.stage() == MoveSite.Stage.STRUCTURE || x.skipped() > 0);
			helper.assertTrue(site.stage() == MoveSite.Stage.STRUCTURE && site.skipped() == 0,
				"setup: the put-up didn't get to STRUCTURE before the drop: " + site.stage() + ", skipped " + site.skipped());
			int steps = 0;
			int reloads = 0;
			int reloadsBeforeDrop = 0;
			while (site.stage() != MoveSite.Stage.DONE && steps++ < MoverGameTests.MAX_STEPS) {
				if (site.skipped() == 0) {
					QaMoverCrateEdges1114bGameTests.barrelsRound(helper);
				}
				MoverWork.work(level, s.mover(), site);
				if (steps % 3 == 0 && site.stage() != MoveSite.Stage.DONE) {
					if (site.skipped() == 0) {
						reloadsBeforeDrop++;
					}
					site = MoverGameTests.reload(level, s);
					reloads++;
				}
			}
			helper.assertTrue(reloadsBeforeDrop >= 1 && reloads >= 3, "only " + reloads + " reloads, " + reloadsBeforeDrop + " before the drop");
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE && !site.canceled() && site.skipped() == 1,
				"the move ended " + site.stage() + ", canceled " + site.canceled() + ", skipped " + site.skipped());
			QaMoverCrateHolds1114cGameTests.assertExactly(helper, Items.BEEHIVE, 1, "the beehive (storage room, reloaded)");
			helper.assertTrue(QaMoverCrateHolds1114cGameTests.standing(helper, Blocks.BEEHIVE) == 0, "a beehive left standing");
			int now = QaMoverCrateLastResort1114e2GameTests.bees(helper);
			helper.assertTrue(now == 3, "the hive's bees (storage room, reloaded): " + now + " of 3 anywhere (" + (now < 3 ? "lost" : "doubled") + ")");
			ItemStack hive = theOne(helper, Items.BEEHIVE, "the beehive item");
			java.util.List<BeehiveBlockEntity.Occupant> in = hive.get(DataComponents.BEES);
			helper.assertTrue(in != null && in.size() == 3, "the dropped beehive holds " + (in == null ? 0 : in.size()) + " bees, not 3");
			BlockItemStateProperties props = hive.get(DataComponents.BLOCK_STATE);
			String honey = props == null ? null : props.properties().get("honey_level");
			helper.assertTrue("5".equals(honey), "the dropped beehive's honey level is " + honey + ", not 5 (its honey lost)");
			helper.succeed();
		});
	}

	/**
	 * A decorated pot with four sherds and nothing inside, on his crate in a storage room: one decorated pot with the same
	 * four sherds on it, and none of its pieces (no sherd, no brick) anywhere: nothing taken from its data as an item.
	 */
	//$ gametest_batch AREA '"qaE1115ePotStore"'
	@GameTest(template = AREA, batch = "qaE1115ePotStore")
	public void anEmptySherdPotOnACrateAmongContainersIsThePotAlone(GameTestHelper helper) {
		Leftovers.clear(helper);
		MoverGameTests.Setup s = QaMoverCrateHolds1114cGameTests.crateInside(helper, "qae1115epot");
		PotDecorations sherds = new PotDecorations(Items.ANGLER_POTTERY_SHERD, Items.ARCHER_POTTERY_SHERD, Items.BLADE_POTTERY_SHERD, Items.SKULL_POTTERY_SHERD);
		ItemStack withSherds = new ItemStack(Items.DECORATED_POT);
		withSherds.set(DataComponents.POT_DECORATIONS, sherds);
		QaMoverCrateHolds1114cGameTests.replaceChest(helper, Blocks.DECORATED_POT.defaultBlockState(), be -> be.applyComponentsFromItemStack(withSherds));
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(loose(helper).isEmpty(), "setup: items already lie loose: " + loose(helper));
			QaMoverCrateLastResort1114e2GameTests.dropInTheStoreRoom(helper, s);
			for (net.minecraft.world.item.Item piece : new net.minecraft.world.item.Item[] {Items.ANGLER_POTTERY_SHERD, Items.ARCHER_POTTERY_SHERD,
					Items.BLADE_POTTERY_SHERD, Items.SKULL_POTTERY_SHERD, Items.BRICK}) {
				QaMoverCrateHolds1114cGameTests.assertExactly(helper, piece, 0, "an empty sherd pot (storage room) broke into pieces; loose " + loose(helper));
			}
			QaMoverCrateHolds1114cGameTests.assertExactly(helper, Items.DECORATED_POT, 1, "the decorated pot (storage room)");
			ItemStack pot = theOne(helper, Items.DECORATED_POT, "the decorated pot");
			helper.assertTrue(sherds.equals(pot.get(DataComponents.POT_DECORATIONS)), "the pot's sherds are now " + pot.get(DataComponents.POT_DECORATIONS) + ", not " + sherds);
			helper.assertTrue(QaMoverCrateHolds1114cGameTests.standing(helper, Blocks.DECORATED_POT) == 0, "a decorated pot left standing");
			helper.succeed();
		});
	}
}
