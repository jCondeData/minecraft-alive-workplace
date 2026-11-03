package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaHaulerWaterVerify1102GameTests.*;

import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.HaulerGolems;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA verification (lane-c-1103-0052) of B813, B814 and B815 (Hauler Golem paths), written from the bugs' text rather than
 * the fixes. The fixes' own tests (QaHaulerWaterVerify1102BugTests) approach every obstacle from one side, with the
 * smallest shape that failed. These take the other side and the next size up: B813's moat two below its banks, and one
 * three below, met from the west; B814's two-wide lava trench a block below the ground, and one two below, crossed east to
 * west; a wall with its only gate a block farther off than B815's open ends, a 19-long lava trench crossed east to
 * west, and a save and reload halfway round B815's 17-long wall. Each test is its own batch in the huge area; its timeout is the haul's tick budget.
 */
public class QaHaulerPathsVerify1103GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {

	/** Lava (or, with {@code below} under 0, a stone wall two high) in columns x {@code x0..x0+wide-1}, z {@code fromZ..toZ}. */
	private static void obstacle(GameTestHelper helper, int x0, int wide, int fromZ, int toZ, int below) {
		for (int x = x0; x < x0 + wide; x++) {
			for (int z = fromZ; z <= toZ; z++) {
				if (below < 0) {
					helper.setBlock(new BlockPos(x, TOP, z), Blocks.STONE);
					helper.setBlock(new BlockPos(x, TOP + 1, z), Blocks.STONE);
				} else {
					liquid(helper, x, z, 1, below, Blocks.LAVA);
				}
			}
		}
	}

	/** Fails the test on any tick the golem is in lava, on fire or hurt. */
	private static void neverBurns(GameTestHelper helper, IronGolem golem, String where) {
		float health = golem.getHealth();
		helper.onEachTick(() -> {
			if (golem.isInLava() || golem.isOnFire() || golem.getHealth() < health || !golem.isAlive()) {
				helper.fail(where + ": the hauler went into the lava: in lava " + golem.isInLava() + ", on fire " + golem.isOnFire() + ", health "
					+ golem.getHealth() + " of " + health + " at " + helper.relativeVec(golem.position()) + " on tick " + helper.getTick());
			}
		});
	}

	/**
	 * The moat round the island is water {@code depth} deep whose surface lies {@code below} blocks under its banks. The
	 * loaded golem comes from the west and the island's Storehouse is its nearest; an open Storehouse lies farther off to
	 * the south-west. It must not end up in the moat with its load: all 64 logs end in a chest, none carried or dropped.
	 */
	private static void westMoatRun(GameTestHelper helper, int depth, int below, String where) {
		setup(helper);
		moat(helper, depth, below);
		BlockPos isleChest = islandStorehouse(helper);
		helper.setBlock(new BlockPos(1, TOP, 23), ModBlocks.STOREHOUSE);
		BlockPos openChest = new BlockPos(1, TOP, 24);
		helper.setBlock(openChest, Blocks.CHEST);
		IronGolem golem = hauler(helper, new BlockPos(4, TOP - 1, 12));
		ModAttachments.HAULER_LOAD.set(golem, Map.of(Items.OAK_LOG, 64));
		helper.runAfterDelay(1, () -> helper.assertTrue(
			helper.absolutePos(new BlockPos(12, TOP, 12)).equals(HaulerGolems.storehouse(helper.getLevel(), golem).orElse(null)),
			"the island's Storehouse isn't the hauler's nearest, so the test proves nothing: it heads for " + HaulerGolems.storehouse(helper.getLevel(), golem)));
		helper.succeedWhen(() -> {
			int isle = count(helper, isleChest, Items.OAK_LOG);
			int open = count(helper, openChest, Items.OAK_LOG);
			helper.assertTrue(isle + open == 64, where + ": the chests hold " + (isle + open)
				+ " of 64 logs (island " + isle + ", open " + open + "); the golem carries " + carried(golem) + " at " + helper.relativeVec(golem.position())
				+ " (in water: " + golem.isInWater() + "), heading for " + HaulerGolems.storehouse(helper.getLevel(), golem));
			helper.assertTrue(carried(golem) == 0, where + ": the golem still carries " + carried(golem));
			helper.assertTrue(dropped(helper) == 0, where + ": items lie on the ground: " + droppedItems(helper));
		});
	}

	/** B813's own moat (two deep, two below its banks) met from the west instead of the east. */
	//$ gametest_ticks_batch HUGE '2400' '"qaPaths1103MoatTwoLowWest"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaPaths1103MoatTwoLowWest")
	public void aHaulerFromTheWestIsNotTrappedByAMoatTwoBelowItsBanks(GameTestHelper helper) {
		westMoatRun(helper, 2, 2, "from the west, the nearest Storehouse in a moat two below its banks");
	}

	/** The same one lower: water one deep, three below its banks. */
	//$ gametest_ticks_batch HUGE '2400' '"qaPaths1103MoatThreeLow"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaPaths1103MoatThreeLow")
	public void aHaulerFromTheWestIsNotTrappedByAMoatThreeBelowItsBanks(GameTestHelper helper) {
		westMoatRun(helper, 1, 3, "from the west, the nearest Storehouse in a moat three below its banks");
	}

	/**
	 * A two-wide lava trench whose surface is {@code below} blocks under the ground, 11 long, between a loaded hauler on
	 * the east and its only Storehouse on the west. It goes round, stores all 64 logs and on no tick is it in lava, on fire
	 * or hurt.
	 */
	private static void westLavaRun(GameTestHelper helper, int below, String where) {
		setup(helper);
		obstacle(helper, 12, 2, 7, 17, below);
		BlockPos chest = new BlockPos(4, TOP, 12);
		helper.setBlock(new BlockPos(5, TOP, 12), ModBlocks.STOREHOUSE);
		helper.setBlock(chest, Blocks.CHEST);
		IronGolem golem = hauler(helper, new BlockPos(21, TOP - 1, 12));
		ModAttachments.HAULER_LOAD.set(golem, Map.of(Items.OAK_LOG, 64));
		neverBurns(helper, golem, where);
		helper.succeedWhen(() -> delivered(helper, golem, chest, Map.of(Items.OAK_LOG, 64), where));
	}

	/** B814's own trench (two wide, a block below the ground) crossed east to west instead of west to east. */
	//$ gametest_ticks_batch HUGE '1600' '"qaPaths1103LavaLowWest"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qaPaths1103LavaLowWest")
	public void aHaulerGoingWestRoundATwoWideLavaTrenchABlockBelowTheGroundNeverBurns(GameTestHelper helper) {
		westLavaRun(helper, 1, "a two-wide lava trench a block below the ground, crossed east to west");
	}

	/** The same trench one lower: its surface two below the ground. */
	//$ gametest_ticks_batch HUGE '1600' '"qaPaths1103LavaTwoLowWest"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qaPaths1103LavaTwoLowWest")
	public void aHaulerGoingWestRoundATwoWideLavaTrenchTwoBelowTheGroundNeverBurns(GameTestHelper helper) {
		westLavaRun(helper, 2, "a two-wide lava trench two below the ground, crossed east to west");
	}

	/**
	 * B815's everyday case, a village wall with a gate off to the side: a stone wall two high across the whole ground
	 * (z 0 to 26) with its only gate, two wide, at z 22..23, ten blocks off the straight line (one more than B815's open
	 * ends, nine off). The loaded hauler goes through the gate and stores all 64 logs.
	 */
	//$ gametest_ticks_batch HUGE '2000' '"qaPaths1103WallGate"'
	@GameTest(template = HUGE, timeoutTicks = 2000, batch = "qaPaths1103WallGate")
	public void aHaulerGoesThroughAGateTenBlocksDownAVillageWall(GameTestHelper helper) {
		setup(helper);
		obstacle(helper, 12, 1, 0, 21, -1);
		obstacle(helper, 12, 1, 24, 26, -1);
		BlockPos chest = new BlockPos(21, TOP, 12);
		helper.setBlock(new BlockPos(20, TOP, 12), ModBlocks.STOREHOUSE);
		helper.setBlock(chest, Blocks.CHEST);
		IronGolem golem = hauler(helper, new BlockPos(5, TOP - 1, 12));
		ModAttachments.HAULER_LOAD.set(golem, Map.of(Items.OAK_LOG, 64));
		helper.succeedWhen(() -> delivered(helper, golem, chest, Map.of(Items.OAK_LOG, 64), "a wall with one gate ten blocks off"));
	}

	/**
	 * B815's lava form, two longer and the other way: a one-wide lava trench level with the ground, 19 long (z 3 to 21),
	 * between a loaded hauler on the east and its only Storehouse on the west. It goes round without burning and stores
	 * all 64 logs.
	 */
	//$ gametest_ticks_batch HUGE '1600' '"qaPaths1103LongLavaWest"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qaPaths1103LongLavaWest")
	public void aHaulerGoingWestRoundANineteenLongLavaTrenchStoresItsLoad(GameTestHelper helper) {
		String where = "a one-wide lava trench 19 long, crossed east to west";
		setup(helper);
		obstacle(helper, 12, 1, 3, 21, 0);
		BlockPos chest = new BlockPos(4, TOP, 12);
		helper.setBlock(new BlockPos(5, TOP, 12), ModBlocks.STOREHOUSE);
		helper.setBlock(chest, Blocks.CHEST);
		IronGolem golem = hauler(helper, new BlockPos(21, TOP - 1, 12));
		ModAttachments.HAULER_LOAD.set(golem, Map.of(Items.OAK_LOG, 64));
		neverBurns(helper, golem, where);
		helper.succeedWhen(() -> delivered(helper, golem, chest, Map.of(Items.OAK_LOG, 64), where));
	}

	/**
	 * B815's 17-long wall (z 4 to 20), and the save is closed and loaded again once the hauler is four blocks along it on
	 * its way round. The loaded golem carries on round and stores the 64 logs exactly once; the old one is gone.
	 */
	//$ gametest_ticks_batch HUGE '2000' '"qaPaths1103WallReload"'
	@GameTest(template = HUGE, timeoutTicks = 2000, batch = "qaPaths1103WallReload")
	public void aHaulerReloadedHalfwayRoundALongWallStillStoresItsLoadOnce(GameTestHelper helper) {
		setup(helper);
		obstacle(helper, 12, 1, 4, 20, -1);
		BlockPos chest = new BlockPos(21, TOP, 12);
		helper.setBlock(new BlockPos(20, TOP, 12), ModBlocks.STOREHOUSE);
		helper.setBlock(chest, Blocks.CHEST);
		IronGolem golem = hauler(helper, new BlockPos(5, TOP - 1, 12));
		ModAttachments.HAULER_LOAD.set(golem, Map.of(Items.OAK_LOG, 64));
		IronGolem[] now = {golem};
		int[] reloadedAt = {-1};
		helper.onEachTick(() -> {
			if (now[0] == golem && Math.abs(helper.relativeVec(golem.position()).z - 12.5) >= 4) {
				now[0] = reloaded(helper, golem);
				reloadedAt[0] = (int) helper.getTick();
				helper.assertTrue(carried(now[0]) == 64, "the reloaded hauler carries " + HaulerGolems.load(now[0]) + " instead of 64 logs");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(now[0] != golem, "the hauler never went four blocks along the wall (not reloaded yet); it is at "
				+ helper.relativeVec(golem.position()) + " heading for " + HaulerGolems.storehouse(helper.getLevel(), golem));
			delivered(helper, now[0], chest, Map.of(Items.OAK_LOG, 64), "reloaded on tick " + reloadedAt[0] + " going round a 17-long wall");
			helper.assertTrue(golem.isRemoved(), "the golem from before the reload is still about");
		});
	}
}
