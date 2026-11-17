package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Ships;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA bug tests (lane-d-1116-0408): what the mutation pass on threat/Ships.java turned up where the mod and ROADMAP 32.8
 * disagree, kept as regression tests once fixed (B1317). On {@link QaShipsMutants1116dGameTests}'s coast.
 */
public class QaShipsMutants1116dBugTests {
	private static final String AREA = "aliveworkplace_test:tall_area";

	/**
	 * "What sank below the waterline turns to sea": five seconds after the ship has sunk, every block of it that lay below
	 * the waterline is still sea, the same still water as the sea round it, not water left running down into the old hold.
	 */
	//$ gametest_ticks_batch AREA '420' '"qa1116dBugHoldStill"'
	@GameTest(template = AREA, timeoutTicks = 420, batch = "qa1116dBugHoldStill")
	public void qaTheSunkenHoldIsStillSeaNotRunningWater(GameTestHelper helper) {
		sinksToStillSea(helper, false);
	}

	/** The same after a save and load halfway through the sinking (B1317): the sinking goes on and the hold still ends still sea. */
	//$ gametest_ticks_batch AREA '420' '"qa1116dBugHoldStillReload"'
	@GameTest(template = AREA, timeoutTicks = 420, batch = "qa1116dBugHoldStillReload")
	public void qaTheSunkenHoldIsStillSeaAfterAReloadMidSinking(GameTestHelper helper) {
		sinksToStillSea(helper, true);
	}

	private static void sinksToStillSea(GameTestHelper helper, boolean reloadMidway) {
		BlockPos hall = QaShipsMutants1116dGameTests.coast(helper);
		ServerLevel level = helper.getLevel();
		Ships.Placed[] ship = {null};
		List<Map<BlockPos, BlockState>> blocks = new ArrayList<>();
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = QaShipsMutants1116dGameTests.anchored(helper, hall, 170);
			ship[0] = Ships.of(level, hall).orElseThrow();
			blocks.add(Ships.blocks(level, ship[0]));
			QaShipsMutants1116dGameTests.captainFalls(level, lair);
		});
		if (reloadMidway) {
			helper.runAfterDelay(2 + Ships.SINK_TICKS / 2, () -> {
				helper.assertTrue(!Ships.sinking(level).isEmpty(), "the ship isn't sinking halfway through");
				Ships.Data.get(level).roundTrip(level.registryAccess());
				helper.assertTrue(!Ships.sinking(level).isEmpty(), "after a save and load halfway through the ship isn't sinking any more");
			});
		}
		helper.runAfterDelay(2 + Ships.SINK_TICKS + 100, () -> {
			helper.assertTrue(Ships.sinking(level).isEmpty(), "the ship is still sinking");
			List<String> running = new ArrayList<>();
			for (Map.Entry<BlockPos, BlockState> e : blocks.get(0).entrySet()) {
				BlockPos p = e.getKey();
				if (p.getY() <= ship[0].waterTop() && !(level.getFluidState(p).is(FluidTags.WATER) && level.getFluidState(p).isSource())) {
					running.add(helper.relativePos(p) + " " + level.getBlockState(p) + " (was " + e.getValue() + ")");
				}
			}
			helper.assertTrue(running.isEmpty(), "five seconds after the ship sank " + running.size() + " blocks below the waterline where it lay aren't still sea: "
				+ running.subList(0, Math.min(6, running.size())));
			helper.succeed();
		});
	}
}
