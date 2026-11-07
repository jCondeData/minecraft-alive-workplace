package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;

/**
 * B972: a gateway walled up after the ram broke its gate (a player puts cobblestone in the gap). Rams don't break walls,
 * so the siege counts the way shut there (ladders and sand ramps go on; without them the raiders wait), never open with
 * the raiders sent "in" through a wall. Cleared again, the way is open again and the raiders get in.
 */
public class SiegeGatewayGameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/**
	 * The ram breaks the Palisade Gate's middle gate; cobblestone goes in the gap at once. For ten seconds the siege counts
	 * the way shut (its own record and the world as ladder layers see it) and the gate block as walled up. Then the
	 * cobblestone goes; the way counts open again and the pillager waiting behind gets through within 35 seconds.
	 */
	//$ gametest_ticks_batch AREA '1800' '"sieges972Walled"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "sieges972Walled")
	public void aGatewayWalledUpAfterTheRamBrokeItCountsAsShutTillItIsCleared(GameTestHelper helper) {
		SiegeGameTests.Yard yard = SiegeGameTests.yard(helper, StarterBlueprints.PALISADE_GATE);
		ServerLevel level = yard.level();
		BlockPos middle = yard.at(3, 0, 1);
		helper.assertTrue(SiegeGameTests.isFenceGate(level, middle), "no gate at " + middle);
		Mob ram = SiegeGameTests.raider(helper, yard, Culture.Role.RAM, 14.5, 21.5);
		Mob pillager = SiegeGameTests.raider(helper, yard, Culture.Role.RANGED, 14.5, 25.5);
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(ram, pillager));
		long[] at = {-1, -1}; // walled up, cleared
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (at[0] < 0) {
				if (!SiegeGameTests.isFenceGate(level, middle)) {
					at[0] = tick;
					level.setBlockAndUpdate(middle, Blocks.COBBLESTONE.defaultBlockState()); // a player's hand
				} else if (tick > 500) {
					helper.fail("the ram never broke the middle gate: it has " + Sieges.hitPointsLeft(level, yard.hall(), middle));
				}
				return;
			}
			ThreatData.Siege siege = yard.siege(helper);
			if (at[1] < 0) {
				helper.assertTrue(level.getBlockState(middle).is(Blocks.COBBLESTONE), "the cobblestone in the gateway went: " + level.getBlockState(middle));
				helper.assertTrue(!Sieges.openNow(level, siege), "a walled-up gateway counts as open to the ladder layers");
				if (tick - at[0] > Sieges.EVERY) {
					helper.assertTrue(!Sieges.open(siege), "a walled-up gateway counts as open: the raiders are sent in through a wall");
					ThreatData.Gate gate = siege.gates.stream().filter(g -> g.pos.equals(middle)).findFirst().orElseThrow();
					helper.assertTrue(gate.state == ThreatData.GateState.WALLED, "the walled-up gate block is " + gate.state);
				}
				if (tick - at[0] > 200) {
					at[1] = tick;
					level.setBlockAndUpdate(middle, Blocks.AIR.defaultBlockState());
				}
				return;
			}
			if (tick - at[1] > Sieges.EVERY) {
				helper.assertTrue(Sieges.open(siege), "the gateway was cleared, but the way still counts shut");
			}
			if (SiegeGameTests.through(helper, yard, pillager)) {
				helper.succeed();
			} else if (tick > at[1] + 700) {
				helper.fail("35 s after the gateway was cleared the pillager is still outside: " + SiegeGameTests.state(helper, pillager)
					+ "; ram " + SiegeGameTests.state(helper, ram));
			}
		});
	}
}
