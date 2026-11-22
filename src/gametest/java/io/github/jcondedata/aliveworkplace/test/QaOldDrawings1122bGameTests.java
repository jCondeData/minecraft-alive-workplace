package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaOldHouseNeighbors1121cGameTests.abs;
import static io.github.jcondedata.aliveworkplace.test.QaOldHouseNeighbors1121cGameTests.forgetCaches;
import static io.github.jcondedata.aliveworkplace.test.QaOldHouseNeighbors1121cGameTests.kept;
import static io.github.jcondedata.aliveworkplace.test.QaOldHouseNeighbors1121cGameTests.keptFree;
import static io.github.jcondedata.aliveworkplace.test.QaOldHouseNeighbors1121cGameTests.neighbor;
import static io.github.jcondedata.aliveworkplace.test.RebuildDrawings1117e.*;
import static io.github.jcondedata.aliveworkplace.test.RebuildMoves1117e2.assertTakenDown;
import static io.github.jcondedata.aliveworkplace.test.RebuildMoves1117e2.takeDown;

import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.people.Homes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA lane B (2026-11-22, lane-b-1122-0338): houses of an OLDER drawing after the family is rebuilt under the same id
 * (B1372 take-downs, B1435 neighbors' ground and the Steward's plots, B1436 homes), past what lanes C and E tested: a
 * take-down must take down the house as it stands and leave alone the player's blocks, including ones of TODAY's
 * drawing's materials standing in today's box outside the old house (B1372: "leaves alone blocks that aren't what it
 * says"); and a house the player has knocked about (three blocks broken: back wall, back floor, front wall) is still the
 * old house as it stands, for a take-down, its home and its neighbors. Built on {@link RebuildDrawings1117e}: old house
 * 5x5 (cobblestone floor, oak walls, bed on row 3), today's drawing 7x3 (stone bricks floor, spruce walls).
 */
public class QaOldDrawings1122bGameTests implements FabricGameTest {
	/** The player's spruce planks where today's drawing has a spruce wall, outside the old 5x5 house. */
	private static final BlockPos OWN_SPRUCE = new BlockPos(6, 1, 1);
	/** The player's stone bricks where today's drawing has its floor, outside the old house. */
	private static final BlockPos OWN_BRICKS = new BlockPos(5, 0, 2);
	/** Three blocks a player broke out of the old house: a back wall corner, a back floor block, a front wall block. */
	private static final List<BlockPos> BROKEN = List.of(new BlockPos(0, 1, 4), new BlockPos(3, 0, 4), new BlockPos(1, 1, 0));

	private static void breakThree(GameTestHelper helper) {
		for (BlockPos p : BROKEN) {
			put(helper, at(p), Blocks.AIR.defaultBlockState());
		}
	}

	private static String blockAt(GameTestHelper helper, BlockPos local) {
		return helper.getLevel().getBlockState(helper.absolutePos(at(local))).getBlock().getName().getString();
	}

	/**
	 * B1372: a sneak take-down of the old house, with the player's glowstone on its back wall and beside it, and the
	 * player's own spruce planks and stone bricks (today's drawing's materials) in today's 7x3 box outside the old house.
	 * The whole old house comes down (chest with its emeralds and lectern kept), the hall forgets it, and all four of the
	 * player's blocks still stand.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaOld1122bTdOwn"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaOld1122bTdOwn")
	public void aTakeDownOfAnOldHouseLeavesThePlayersBlocksInTodaysBox(GameTestHelper helper) {
		ResourceLocation id = id(helper, "tdown");
		Stage s = stage(helper, id, true);
		standOld(helper, s, true, true);
		put(helper, at(OWN_SPRUCE), Blocks.SPRUCE_PLANKS.defaultBlockState());
		put(helper, at(OWN_BRICKS), Blocks.STONE_BRICKS.defaultBlockState());
		Villager[] builder = {null};
		helper.runAfterDelay(2, () -> {
			rebuild(s.level(), id, true);
			reload(helper, s.level(), null);
			builder[0] = builder(helper, s);
			takeDown(helper, s, builder[0]);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(builder[0] != null, "not started");
			assertTakenDown(helper, s, builder[0]);
			helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(at(OWN_ON_OLD))).is(Blocks.GLOWSTONE),
				"the player's glowstone on the old back wall was taken: " + blockAt(helper, OWN_ON_OLD));
			helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(at(OWN_OUTSIDE))).is(Blocks.GLOWSTONE),
				"the player's glowstone beside the old house was taken: " + blockAt(helper, OWN_OUTSIDE));
			helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(at(OWN_SPRUCE))).is(Blocks.SPRUCE_PLANKS),
				"the player's spruce planks in today's box (outside the old house) were taken: " + blockAt(helper, OWN_SPRUCE));
			helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(at(OWN_BRICKS))).is(Blocks.STONE_BRICKS),
				"the player's stone bricks in today's box (outside the old house) were taken: " + blockAt(helper, OWN_BRICKS));
		});
	}

	/**
	 * B1372 on a damaged house: three of the old house's blocks broken before the update. A sneak take-down still takes
	 * the rest of the house down (chest and lectern kept) and the hall forgets it.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaOld1122bTdDamaged"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaOld1122bTdDamaged")
	public void aTakeDownOfADamagedOldHouseTakesDownTheRest(GameTestHelper helper) {
		ResourceLocation id = id(helper, "tddmg");
		Stage s = stage(helper, id, true);
		standOld(helper, s, true, false);
		breakThree(helper);
		Villager[] builder = {null};
		helper.runAfterDelay(2, () -> {
			rebuild(s.level(), id, true);
			reload(helper, s.level(), null);
			builder[0] = builder(helper, s);
			takeDown(helper, s, builder[0]);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(builder[0] != null, "not started");
			assertTakenDown(helper, s, builder[0]);
		});
	}

	/**
	 * B1436 and B1435 on a damaged house (three blocks broken, the back wall's corner among them): it is still the old
	 * house as it stands. Its bed on row 3 gives that home; a neighbor's levelling may not dig its back floor, back wall
	 * or bed spot; the Steward keeps rows 5 and 6 behind it free.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaOld1122bDamagedBoxes"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaOld1122bDamagedBoxes")
	public void aDamagedOldHouseIsStillReadAsItStandsForItsHomeAndNeighbors(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.runAfterDelay(2, () -> {
			ResourceLocation id = id(helper, "dmgbox");
			Stage s = stage(helper, id, true);
			standOld(helper, s, true, false);
			breakThree(helper);
			rebuild(s.level(), id, true);
			reload(helper, s.level(), null);
			forgetCaches();

			Optional<Homes.Home> home = Homes.at(s.level(), abs(helper, BED_HEAD));
			helper.assertTrue(home.isPresent() && home.get().structure().equals(id),
				"the bed of the damaged old house (row 3, outside today's 7x3 box) gives no home: " + home);

			BuildSiteManager manager = BuildSiteManager.get(s.level());
			List<String> open = new ArrayList<>();
			for (BlockPos local : List.of(new BlockPos(1, 0, 4), new BlockPos(2, 1, 4), new BlockPos(0, 1, 4), BED_HEAD)) {
				if (!manager.underAnotherBuild(s.level(), neighbor(helper, s), abs(helper, local))) {
					open.add(local.toShortString());
				}
			}
			helper.assertTrue(open.isEmpty(), "a neighbor's levelling may dig or fill the damaged old house at " + open);

			List<BoundingBox> boxes = keptFree(helper, s.level());
			List<String> free = new ArrayList<>();
			for (int row = 3; row <= 6; row++) {
				if (!kept(boxes, abs(helper, new BlockPos(2, 0, row)))) {
					free.add("row " + row);
				}
			}
			helper.assertTrue(free.isEmpty(), "the Steward's plots may start at " + free + " behind the damaged old house (back wall row 4)");
			helper.succeed();
		});
	}
}
