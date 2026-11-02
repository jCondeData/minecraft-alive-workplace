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
 * Bug tests (qa-lane-c-1102-0655) from verifying B722, B783 and B784: corners their fixes miss. Water two blocks below
 * its banks still traps a Hauler Golem with its load (B722's "doesn't path into water it can't leave"); a two-wide lava
 * trench whose surface is a block below the ground (B784's own shape) still burns it; and a loaded hauler gives up a
 * Storehouse it could reach round a 17-long wall or trench.
 */
public class QaHaulerWaterVerify1102BugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {

	/**
	 * Water two blocks below its banks is water a golem truly can't climb out of. The nearest Storehouse stands on an
	 * island in such a moat, an open one lies farther off. B722: the golem doesn't path into water it can't leave (or gets
	 * out) and takes the load to the reachable Storehouse: every log ends up in a chest, none carried, none dropped.
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qaVerify1102MoatTooLow"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaVerify1102MoatTooLow")
	public void aHaulerNeverGetsStuckInAMoatTwoBlocksBelowItsBanks(GameTestHelper helper) {
		setup(helper);
		moat(helper, 2, 2);
		BlockPos isleChest = islandStorehouse(helper);
		helper.setBlock(new BlockPos(24, TOP, 2), ModBlocks.STOREHOUSE);
		BlockPos openChest = new BlockPos(25, TOP, 2);
		helper.setBlock(openChest, Blocks.CHEST);
		IronGolem golem = hauler(helper, new BlockPos(19, TOP - 1, 12));
		ModAttachments.HAULER_LOAD.set(golem, Map.of(Items.OAK_LOG, 64));
		helper.runAfterDelay(1, () -> helper.assertTrue(
			helper.absolutePos(new BlockPos(12, TOP, 12)).equals(HaulerGolems.storehouse(helper.getLevel(), golem).orElse(null)),
			"the island's Storehouse isn't the hauler's nearest, so the test proves nothing: it heads for " + HaulerGolems.storehouse(helper.getLevel(), golem)));
		helper.succeedWhen(() -> {
			int isle = count(helper, isleChest, Items.OAK_LOG);
			int open = count(helper, openChest, Items.OAK_LOG);
			helper.assertTrue(isle + open == 64, "with the nearest Storehouse in a moat two below its banks the chests hold " + (isle + open)
				+ " of 64 logs (island " + isle + ", open " + open + "); the golem carries " + carried(golem) + " at " + golem.position()
				+ " (in water: " + golem.isInWater() + "), heading for " + HaulerGolems.storehouse(helper.getLevel(), golem));
			helper.assertTrue(carried(golem) == 0, "the golem still carries " + carried(golem));
			helper.assertTrue(dropped(helper) == 0, "items lie on the ground: " + droppedItems(helper));
		});
	}

	/** B784's own shape: a lava trench two wide whose surface is a block below the ground, 11 long; twice per run. */
	//$ gametest_ticks_batch HUGE '1600' '"qaVerify1102LavaLow"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qaVerify1102LavaLow")
	public void aHaulerGoesRoundATwoWideLavaTrenchABlockBelowTheGround(GameTestHelper helper) {
		lavaRun(helper, 2, 1, 7, 17, "a two-wide lava trench a block below the ground");
	}

	//$ gametest_ticks_batch HUGE '1600' '"qaVerify1102LavaLowB"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qaVerify1102LavaLowB")
	public void aHaulerGoesRoundATwoWideLavaTrenchABlockBelowTheGroundCopy1(GameTestHelper helper) {
		lavaRun(helper, 2, 1, 7, 17, "a two-wide lava trench a block below the ground (copy 1)");
	}

	/**
	 * A stone wall two high and 17 long (z 4 to 20) stands between the loaded hauler and its only Storehouse, 15 blocks
	 * off; the way round is open on both ends. The golem must go round and store the load, not give the Storehouse up.
	 */
	//$ gametest_ticks_batch HUGE '1600' '"qaVerify1102LongWall"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qaVerify1102LongWall")
	public void aHaulerGoesRoundALongStoneWall(GameTestHelper helper) {
		lavaRun(helper, 1, -1, "a 17-long stone wall");
	}

	/** The same with a one-wide lava trench level with the ground, 17 long. */
	//$ gametest_ticks_batch HUGE '1600' '"qaVerify1102LongLava"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qaVerify1102LongLava")
	public void aHaulerGoesRoundALongOneWideLavaTrench(GameTestHelper helper) {
		lavaRun(helper, 1, 0, "a one-wide lava trench, 17 long");
	}
}
