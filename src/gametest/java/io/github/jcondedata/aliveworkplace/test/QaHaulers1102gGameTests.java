package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaHaulerWaterVerify1102GameTests.TOP;

import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.HaulerGolems;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-b-1102-2137), verifying B830, B813, B814 and B815 from the bugs' text, on the corners their own tests leave
 * out. B830 ("skip it and haul the logs that fit"): two stashes in a row whose goods don't fit (a Drop Box and a miner),
 * room for exactly one more cobblestone (the nearer stash fits for one item, then no more), and a save and reload with
 * the logs aboard. B813 ("doesn't path into water it can't leave"): water one deep two and three blocks below its banks.
 * B814 ("never sets foot in lava and keeps clear of a sunken trench too"): trenches one, two and three blocks down and
 * one wide. B815 ("it goes round and stores the load; a village wall with a gate off to the side is the everyday case"):
 * a 21-long wall, a wall across the whole ground with a two-wide gate near its end, and a reload mid-detour.
 */
public class QaHaulers1102gGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	// ---- B830: goods that don't fit are passed over ----

	private static int count(GameTestHelper helper, BlockPos pos, net.minecraft.world.item.Item item) {
		return QaStackRoom1102cGameTests.count(helper, pos, item);
	}

	/**
	 * A Storehouse chest of 26 full cobblestone stacks and one log; a Drop Box with 32 cobblestone and a miner with 64,
	 * both nearer than a lumberjack with 64 logs. Neither stash's goods fit, so the hauler passes both and brings 63 logs.
	 */
	//$ gametest_ticks_batch HUGE '4800' '"qa1102gHaulerTwoMisfits"'
	@GameTest(template = HUGE, timeoutTicks = 4800, batch = "qa1102gHaulerTwoMisfits")
	public void qaTheHaulerPassesADropBoxAndAMinerWhoseGoodsDontFit(GameTestHelper helper) {
		Container store = QaStackRoom1102cGameTests.storehouse(helper);
		QaStackRoom1102cGameTests.fillEach(store, Items.COBBLESTONE, 64);
		store.setItem(26, new ItemStack(Items.OAK_LOG, 1));
		helper.setBlock(QaStackRoom1102cGameTests.HAUL_BOX, ModBlocks.DROP_BOX);
		((Container) helper.getBlockEntity(QaStackRoom1102cGameTests.HAUL_BOX)).setItem(0, new ItemStack(Items.COBBLESTONE, 32));
		QaStackRoom1102cGameTests.miner(helper, 64);
		QaStackRoom1102cGameTests.lumberjack(helper, QaStackRoom1102cGameTests.FAR_JACK_CHEST, 64);
		IronGolem golem = QaStackRoom1102cGameTests.hauler(helper);
		helper.succeedWhen(() -> {
			int stored = count(helper, QaStackRoom1102cGameTests.STORE_CHEST, Items.OAK_LOG);
			int left = count(helper, QaStackRoom1102cGameTests.FAR_JACK_CHEST, Items.OAK_LOG);
			helper.assertTrue(stored == 64 && left == 1 && HaulerGolems.load(golem).isEmpty(), "past a Drop Box and a miner whose cobblestone doesn't fit, the Storehouse has "
				+ stored + " of 64 logs; the lumberjack keeps " + left + "; the golem carries " + HaulerGolems.load(golem));
			helper.assertTrue(count(helper, QaStackRoom1102cGameTests.HAUL_BOX, Items.COBBLESTONE) == 32
				&& count(helper, QaStackRoom1102cGameTests.MINER_CHEST, Items.COBBLESTONE) == 64
				&& count(helper, QaStackRoom1102cGameTests.STORE_CHEST, Items.COBBLESTONE) == 26 * 64,
				"cobblestone moved though none fits: box " + count(helper, QaStackRoom1102cGameTests.HAUL_BOX, Items.COBBLESTONE) + ", miner "
					+ count(helper, QaStackRoom1102cGameTests.MINER_CHEST, Items.COBBLESTONE));
		});
	}

	/**
	 * The boundary: the Storehouse has room for exactly one cobblestone (a stack of 63 among 25 full ones) and 63 logs.
	 * The nearer miner's cobblestone fits for one; after that it doesn't, so the hauler goes on to the lumberjack's logs.
	 * In the end the Storehouse holds 26 x 64 cobblestone and 64 logs, the miner 63, the lumberjack 1, nothing lost.
	 */
	//$ gametest_ticks_batch HUGE '4800' '"qa1102gHaulerRoomForOne"'
	@GameTest(template = HUGE, timeoutTicks = 4800, batch = "qa1102gHaulerRoomForOne")
	public void qaRoomForOneCobblestoneTakesOneThenTheHaulerBringsTheLogs(GameTestHelper helper) {
		Container store = QaStackRoom1102cGameTests.storehouse(helper);
		QaStackRoom1102cGameTests.fillEach(store, Items.COBBLESTONE, 64);
		store.setItem(25, new ItemStack(Items.COBBLESTONE, 63));
		store.setItem(26, new ItemStack(Items.OAK_LOG, 1));
		QaStackRoom1102cGameTests.miner(helper, 64);
		QaStackRoom1102cGameTests.lumberjack(helper, QaStackRoom1102cGameTests.FAR_JACK_CHEST, 64);
		IronGolem golem = QaStackRoom1102cGameTests.hauler(helper);
		helper.succeedWhen(() -> {
			int logs = count(helper, QaStackRoom1102cGameTests.STORE_CHEST, Items.OAK_LOG);
			int cobble = count(helper, QaStackRoom1102cGameTests.STORE_CHEST, Items.COBBLESTONE);
			int miner = count(helper, QaStackRoom1102cGameTests.MINER_CHEST, Items.COBBLESTONE);
			int jack = count(helper, QaStackRoom1102cGameTests.FAR_JACK_CHEST, Items.OAK_LOG);
			helper.assertTrue(cobble + miner + QaStackRoom1102cGameTests.carried(golem, Items.COBBLESTONE) == 25 * 64 + 63 + 64,
				"cobblestone lost or made: store " + cobble + ", miner " + miner + ", carried " + HaulerGolems.load(golem));
			helper.assertTrue(logs == 64 && jack == 1 && HaulerGolems.load(golem).isEmpty(), "with room for one cobblestone and 63 logs the Storehouse has "
				+ logs + " of 64 logs (lumberjack " + jack + "), the golem carries " + HaulerGolems.load(golem));
			helper.assertTrue(cobble == 26 * 64 && miner == 63, "the one cobblestone that fits: the Storehouse has " + cobble + " (want " + 26 * 64
				+ "), the miner " + miner + " (want 63)");
		});
	}

	/**
	 * Past a nearer miner whose cobblestone doesn't fit, the golem picks up the logs; the save is closed and loaded the
	 * moment it carries them. The loaded golem still stores them: 64 logs in the Storehouse, 1 with the lumberjack.
	 */
	//$ gametest_ticks_batch HUGE '4800' '"qa1102gHaulerReload"'
	@GameTest(template = HUGE, timeoutTicks = 4800, batch = "qa1102gHaulerReload")
	public void qaAHaulerReloadedWithLogsPastAMisfitStillStoresThem(GameTestHelper helper) {
		Container store = QaStackRoom1102cGameTests.storehouse(helper);
		QaStackRoom1102cGameTests.fillEach(store, Items.COBBLESTONE, 64);
		store.setItem(26, new ItemStack(Items.OAK_LOG, 1));
		QaStackRoom1102cGameTests.miner(helper, 64);
		QaStackRoom1102cGameTests.lumberjack(helper, QaStackRoom1102cGameTests.FAR_JACK_CHEST, 64);
		IronGolem golem = QaStackRoom1102cGameTests.hauler(helper);
		IronGolem[] now = {golem};
		helper.onEachTick(() -> {
			if (now[0] == golem && QaStackRoom1102cGameTests.carried(golem, Items.OAK_LOG) > 0) {
				int had = QaStackRoom1102cGameTests.carried(golem, Items.OAK_LOG);
				now[0] = QaHaulerWaterVerify1102GameTests.reloaded(helper, golem);
				helper.assertTrue(QaStackRoom1102cGameTests.carried(now[0], Items.OAK_LOG) == had,
					"the reloaded golem carries " + HaulerGolems.load(now[0]) + " instead of " + had + " logs");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(now[0] != golem, "the golem never picked the logs up (not reloaded)");
			int stored = count(helper, QaStackRoom1102cGameTests.STORE_CHEST, Items.OAK_LOG);
			int left = count(helper, QaStackRoom1102cGameTests.FAR_JACK_CHEST, Items.OAK_LOG);
			helper.assertTrue(stored == 64 && left == 1 && HaulerGolems.load(now[0]).isEmpty(), "reloaded with logs aboard: the Storehouse has " + stored
				+ " of 64 logs, the lumberjack keeps " + left + ", the golem carries " + HaulerGolems.load(now[0]));
			helper.assertTrue(count(helper, QaStackRoom1102cGameTests.MINER_CHEST, Items.COBBLESTONE) == 64, "the miner's cobblestone moved");
		});
	}

	// ---- B813: water too low to climb out of ----

	/**
	 * The nearest Storehouse stands on an island in a moat one deep whose surface is {@code below} blocks under its banks;
	 * an open Storehouse lies farther off. Every log ends up in a chest, none carried, none on the ground.
	 */
	private static void lowMoat(GameTestHelper helper, int depth, int below, String where) {
		QaHaulerWaterVerify1102GameTests.setup(helper);
		QaHaulerWaterVerify1102GameTests.moat(helper, depth, below);
		BlockPos isleChest = QaHaulerWaterVerify1102GameTests.islandStorehouse(helper);
		helper.setBlock(new BlockPos(24, TOP, 2), ModBlocks.STOREHOUSE);
		BlockPos openChest = new BlockPos(25, TOP, 2);
		helper.setBlock(openChest, Blocks.CHEST);
		IronGolem golem = QaHaulerWaterVerify1102GameTests.hauler(helper, new BlockPos(19, TOP - 1, 12));
		ModAttachments.HAULER_LOAD.set(golem, Map.of(Items.OAK_LOG, 64));
		helper.runAfterDelay(1, () -> helper.assertTrue(
			helper.absolutePos(new BlockPos(12, TOP, 12)).equals(HaulerGolems.storehouse(helper.getLevel(), golem).orElse(null)),
			where + ": the island's Storehouse isn't the hauler's nearest, so the test proves nothing: " + HaulerGolems.storehouse(helper.getLevel(), golem)));
		helper.succeedWhen(() -> {
			int isle = QaHaulerWaterVerify1102GameTests.count(helper, isleChest, Items.OAK_LOG);
			int open = QaHaulerWaterVerify1102GameTests.count(helper, openChest, Items.OAK_LOG);
			helper.assertTrue(isle + open == 64, where + ": the chests hold " + (isle + open) + " of 64 logs (island " + isle + ", open " + open
				+ "); the golem carries " + QaHaulerWaterVerify1102GameTests.carried(golem) + " at " + helper.relativeVec(golem.position())
				+ " (in water " + golem.isInWater() + "), heading for " + HaulerGolems.storehouse(helper.getLevel(), golem));
			helper.assertTrue(QaHaulerWaterVerify1102GameTests.carried(golem) == 0, where + ": the golem still carries " + HaulerGolems.load(golem));
			helper.assertTrue(QaHaulerWaterVerify1102GameTests.dropped(helper) == 0, where + ": on the ground " + QaHaulerWaterVerify1102GameTests.droppedItems(helper));
		});
	}

	//$ gametest_ticks_batch HUGE '2400' '"qa1102gMoatOneDeepTwoBelow"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1102gMoatOneDeepTwoBelow")
	public void qaAHaulerIsNotTrappedByOneDeepWaterTwoBelowItsBanks(GameTestHelper helper) {
		lowMoat(helper, 1, 2, "a moat one deep, two below its banks");
	}

	//$ gametest_ticks_batch HUGE '2400' '"qa1102gMoatThreeBelow"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1102gMoatThreeBelow")
	public void qaAHaulerIsNotTrappedByWaterThreeBelowItsBanks(GameTestHelper helper) {
		lowMoat(helper, 1, 3, "a moat one deep, three below its banks");
	}

	// ---- B814: sunken lava ----

	//$ gametest_ticks_batch HUGE '1600' '"qa1102gLavaTwoBelow"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qa1102gLavaTwoBelow")
	public void qaAHaulerKeepsClearOfATwoWideTrenchTwoBelowTheGround(GameTestHelper helper) {
		QaHaulerWaterVerify1102GameTests.lavaRun(helper, 2, 2, 7, 17, "a two-wide lava trench two blocks below the ground");
	}

	//$ gametest_ticks_batch HUGE '1600' '"qa1102gLavaThreeBelow"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qa1102gLavaThreeBelow")
	public void qaAHaulerKeepsClearOfATwoWideTrenchThreeBelowTheGround(GameTestHelper helper) {
		QaHaulerWaterVerify1102GameTests.lavaRun(helper, 2, 3, 7, 17, "a two-wide lava trench three blocks below the ground");
	}

	//$ gametest_ticks_batch HUGE '1600' '"qa1102gLavaOneWideLow"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qa1102gLavaOneWideLow")
	public void qaAHaulerKeepsClearOfAOneWideTrenchABlockBelowTheGround(GameTestHelper helper) {
		QaHaulerWaterVerify1102GameTests.lavaRun(helper, 1, 1, 7, 17, "a one-wide lava trench a block below the ground");
	}

	// ---- B815: long detours ----

	/** A stone wall two high and 21 long (z 2 to 22), the Storehouse 15 blocks off behind it: the hauler goes round. */
	//$ gametest_ticks_batch HUGE '2000' '"qa1102gWall21"'
	@GameTest(template = HUGE, timeoutTicks = 2000, batch = "qa1102gWall21")
	public void qaAHaulerGoesRoundATwentyOneLongWall(GameTestHelper helper) {
		QaHaulerWaterVerify1102GameTests.lavaRun(helper, 1, -1, 2, 22, "a 21-long stone wall");
	}

	/**
	 * A wall two high across the whole raised ground (z 0 to 26) with a two-wide gate at z 22 and 23 (an Iron Golem is 1.4 wide), ten blocks off to the side
	 * of the straight line; the ground ends in a four-block drop on every side, so the gate is the only way. The hauler
	 * goes through it and stores all 64 logs.
	 */
	//$ gametest_ticks_batch HUGE '2000' '"qa1102gWallGate"'
	@GameTest(template = HUGE, timeoutTicks = 2000, batch = "qa1102gWallGate")
	public void qaAHaulerGoesThroughAGateOffToTheSideOfAVillageWall(GameTestHelper helper) {
		QaHaulerWaterVerify1102GameTests.setup(helper);
		for (int z = 0; z <= 26; z++) {
			if (z != 22 && z != 23) {
				helper.setBlock(new BlockPos(12, TOP, z), Blocks.STONE);
				helper.setBlock(new BlockPos(12, TOP + 1, z), Blocks.STONE);
			}
		}
		BlockPos chest = new BlockPos(21, TOP, 12);
		helper.setBlock(new BlockPos(20, TOP, 12), ModBlocks.STOREHOUSE);
		helper.setBlock(chest, Blocks.CHEST);
		IronGolem golem = QaHaulerWaterVerify1102GameTests.hauler(helper, new BlockPos(5, TOP - 1, 12));
		ModAttachments.HAULER_LOAD.set(golem, Map.of(Items.OAK_LOG, 64));
		helper.succeedWhen(() -> QaHaulerWaterVerify1102GameTests.delivered(helper, golem, chest, Map.of(Items.OAK_LOG, 64),
			"a wall with a gate ten blocks off to the side"));
	}

	/**
	 * B815's 17-long wall, and the save is closed and loaded 40 ticks into the walk (on its way round). The loaded golem
	 * still finds the way round and stores all 64 logs once.
	 */
	//$ gametest_ticks_batch HUGE '2000' '"qa1102gWallReload"'
	@GameTest(template = HUGE, timeoutTicks = 2000, batch = "qa1102gWallReload")
	public void qaAHaulerReloadedMidDetourStillGoesRoundTheWall(GameTestHelper helper) {
		QaHaulerWaterVerify1102GameTests.setup(helper);
		for (int z = 4; z <= 20; z++) {
			helper.setBlock(new BlockPos(12, TOP, z), Blocks.STONE);
			helper.setBlock(new BlockPos(12, TOP + 1, z), Blocks.STONE);
		}
		BlockPos chest = new BlockPos(21, TOP, 12);
		helper.setBlock(new BlockPos(20, TOP, 12), ModBlocks.STOREHOUSE);
		helper.setBlock(chest, Blocks.CHEST);
		IronGolem golem = QaHaulerWaterVerify1102GameTests.hauler(helper, new BlockPos(5, TOP - 1, 12));
		ModAttachments.HAULER_LOAD.set(golem, Map.of(Items.OAK_LOG, 64));
		IronGolem[] now = {golem};
		helper.runAtTickTime(40, () -> {
			now[0] = QaHaulerWaterVerify1102GameTests.reloaded(helper, golem);
			helper.assertTrue(QaHaulerWaterVerify1102GameTests.carried(now[0]) == 64, "the reloaded hauler carries " + HaulerGolems.load(now[0]));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(now[0] != golem, "not reloaded yet");
			QaHaulerWaterVerify1102GameTests.delivered(helper, now[0], chest, Map.of(Items.OAK_LOG, 64), "reloaded on its way round a 17-long wall");
		});
	}
}
