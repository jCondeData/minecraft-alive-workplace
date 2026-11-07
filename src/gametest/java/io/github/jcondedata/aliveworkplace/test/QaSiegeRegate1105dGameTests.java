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
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane D, 2026-11-05): a gate put back in the breach while the siege is still on. Builders mend the walls and gates
 * first in a siege (32.6, {@code Upkeep}: "the walls, gates and towers before anything else"), and a player may set a
 * fence gate back by hand. The siege remembers the gate as broken, so the director no longer counts it as standing; the
 * spec still says the ram goes for the gate and the others walk in through the gap the ram makes ("the ram steps aside
 * out of the gateway to let them past, then widens it"). So with the middle gate shut again the raiders must still get
 * in, never stand outside a shut gate till dawn.
 */
public class QaSiegeRegate1105dGameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/**
	 * The ram breaks the Palisade Gate's middle gate; the same gate is set back, shut, at once. The pillager waiting behind
	 * still gets through the front wall (by a gate the ram breaks again or one beside it) within 35 seconds.
	 */
	//$ gametest_ticks_batch AREA '1400' '"qa1105dRegate"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qa1105dRegate")
	public void aGateSetBackInTheBreachMidSiegeStillLetsTheRaidersIn(GameTestHelper helper) {
		SiegeGameTests.Yard yard = SiegeGameTests.yard(helper, StarterBlueprints.PALISADE_GATE);
		ServerLevel level = yard.level();
		BlockPos middle = yard.at(3, 0, 1);
		List<BlockPos> gates = List.of(yard.at(2, 0, 1), middle, yard.at(4, 0, 1));
		helper.assertTrue(SiegeGameTests.isFenceGate(level, middle), "no gate at " + middle);
		BlockState shut = level.getBlockState(middle);
		Mob ram = SiegeGameTests.raider(helper, yard, Culture.Role.RAM, 14.5, 21.5);
		Mob pillager = SiegeGameTests.raider(helper, yard, Culture.Role.RANGED, 14.5, 25.5);
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(ram, pillager));
		long[] reset = {-1};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (reset[0] < 0) {
				if (!SiegeGameTests.isFenceGate(level, middle)) {
					reset[0] = tick;
					level.setBlockAndUpdate(middle, shut); // a builder's repair, or a player's hand
					helper.assertTrue(SiegeGameTests.isFenceGate(level, middle), "the gate wasn't set back");
				} else if (tick > 500) {
					helper.fail("the ram never broke the middle gate: it has " + Sieges.hitPointsLeft(level, yard.hall(), middle));
				}
				return;
			}
			if (SiegeGameTests.through(helper, yard, pillager)) {
				helper.succeed();
			} else if (tick > reset[0] + 700) {
				ThreatData.Siege siege = yard.siege(helper);
				helper.fail("35 s after the gate was set back the pillager is still outside: pillager " + SiegeGameTests.state(helper, pillager)
					+ "; ram (alive " + ram.isAlive() + ") " + SiegeGameTests.state(helper, ram) + "; way "
					+ (Sieges.open(siege) ? "open" : "shut") + "; gates " + gates.stream().map(g -> g.subtract(yard.origin()).toShortString() + " "
						+ (SiegeGameTests.isFenceGate(level, g) ? "standing" : "gone")).toList()
					+ "; the siege's gates " + siege.gates.stream().map(g -> g.pos.subtract(yard.origin()).toShortString() + " " + g.state + " " + g.hp).toList());
			}
		});
	}
}
