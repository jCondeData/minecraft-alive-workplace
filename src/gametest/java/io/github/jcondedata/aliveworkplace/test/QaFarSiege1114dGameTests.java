package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * Lane D QA (lane-d-1114-0708), B1225 from its spec and fix decision: a siege whose village nobody is near (its hall's
 * chunk isn't entity-ticking) "stands still and loads nothing", ladders, sand ramps and a dawn already past included;
 * "once someone is back the hall's tick ends the raid and the director lifts it at once if its dawn has passed"; and a
 * siege at a village someone is at still goes on while a far one waits (the guard must not stop every siege).
 */
public class QaFarSiege1114dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	private static BlockPos far(GameTestHelper helper, int dx, int dz) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		return new BlockPos(origin.getX() + dx, 70, origin.getZ() + dz);
	}

	private static boolean loaded(ServerLevel level, BlockPos pos) {
		return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
	}

	/** A saved siege at {@code hall} whose raid has ended, with a gate, two ladders and a sand ramp foot 40 blocks east. */
	private static ThreatData.Siege siege(ServerLevel level, BlockPos hall, long dawn) {
		BlockPos gate = hall.offset(40, 0, 0);
		ThreatData.Siege siege = new ThreatData.Siege(hall, level.getGameTime(), dawn);
		siege.came = 4;
		siege.breach = gate;
		siege.gates.add(new ThreatData.Gate(gate, 100, 0, false, false));
		siege.ladders.add(gate.offset(1, 0, 3));
		siege.ladders.add(gate.offset(1, 1, 3));
		siege.laddered = true;
		siege.sand.add(gate.offset(2, 0, -3));
		siege.rampFeet.add(gate.offset(3, 0, -3));
		siege.rampDirs.add(0);
		siege.ramped = true;
		return siege;
	}

	/**
	 * Its raid over, its dawn past, ladders and a ramp saved: everything the director would tidy up or lift once someone
	 * is near. Far from everyone it waits: for 160 ticks no chunk of the village is loaded and the siege is still saved.
	 */
	//$ gametest_ticks_batch AREA '240' '"qa1114dFarSiegeWaits"'
	@GameTest(template = AREA, timeoutTicks = 240, batch = "qa1114dFarSiegeWaits")
	public void aFarSiegePastItsDawnWithLaddersAndRampsWaitsAndLoadsNothing(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, -7600, -7600);
		BlockPos gate = hall.offset(40, 0, 0);
		ThreatData data = ThreatData.get(level);
		helper.assertTrue(!loaded(level, hall) && !loaded(level, gate), "setup: the far chunks are already loaded");
		Leftovers.after(helper, () -> {
			data.forgetSiege(hall);
			data.end(hall);
			Sieges.forget();
		});
		data.beginSiege(siege(level, hall, level.getDayTime() - 1)); // (no raid under way: the raid has ended)

		int[] ticks = {0};
		helper.onEachTick(() -> {
			helper.assertTrue(!loaded(level, hall), "a far siege loaded its village hall's chunk (tick " + ticks[0] + ")");
			helper.assertTrue(!loaded(level, gate), "a far siege loaded its gate, ladder or ramp chunk (tick " + ticks[0] + ")");
			if (++ticks[0] >= 160) {
				helper.assertTrue(data.siege(hall).isPresent(), "a far siege was lifted while nobody was near its village");
				helper.succeed();
			}
		});
	}

	/**
	 * The same siege, its dawn past: once its hall's chunk ticks again (a player back; here the chunk is force-loaded,
	 * which ticks it the same way) the director lifts it within a few seconds. Before that, it waited.
	 */
	//$ gametest_ticks_batch AREA '1000' '"qa1114dFarSiegeLifted"'
	@GameTest(template = AREA, timeoutTicks = 1000, batch = "qa1114dFarSiegeLifted")
	public void aWaitingSiegePastItsDawnIsLiftedOnceItsVillageTicksAgain(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, 7600, -7600);
		ChunkPos chunk = new ChunkPos(hall);
		ThreatData data = ThreatData.get(level);
		helper.assertTrue(!loaded(level, hall), "setup: the far chunk is already loaded");
		Leftovers.after(helper, () -> {
			level.setChunkForced(chunk.x, chunk.z, false);
			data.forgetSiege(hall);
			data.end(hall);
			Sieges.forget();
		});
		data.beginSiege(siege(level, hall, level.getDayTime() - 1));

		int[] ticks = {0};
		helper.onEachTick(() -> {
			ticks[0]++;
			if (ticks[0] < 80) {
				helper.assertTrue(data.siege(hall).isPresent(), "the far siege was lifted before anyone was near (tick " + ticks[0] + ")");
				return;
			}
			if (ticks[0] == 80) {
				level.setChunkForced(chunk.x, chunk.z, true);
				return;
			}
			if (!level.isPositionEntityTicking(hall)) {
				helper.assertTrue(ticks[0] < 800, "setup: the force-loaded hall chunk never became entity-ticking");
				return;
			}
			if (data.siege(hall).isEmpty()) {
				helper.succeed();
			} else {
				helper.assertTrue(ticks[0] < 920, "the siege, its raid over and its dawn past, was not lifted once its village ticked again");
			}
		});
	}

	/**
	 * Two sieges, both raids over and both dawns past: one at the test's own (ticking) village, one far. The near one is
	 * lifted as before; the far one waits and loads nothing. A dawn still to come keeps a near siege standing.
	 */
	//$ gametest_ticks_batch AREA '240' '"qa1114dNearSiegeGoesOn"'
	@GameTest(template = AREA, timeoutTicks = 240, batch = "qa1114dNearSiegeGoesOn")
	public void aNearSiegeIsStillLiftedWhileAFarOneWaits(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos near = helper.absolutePos(new BlockPos(2, 2, 2));
		BlockPos notYet = helper.absolutePos(new BlockPos(8, 2, 2));
		BlockPos far = far(helper, -7600, 7600);
		ThreatData data = ThreatData.get(level);
		helper.assertTrue(!loaded(level, far), "setup: the far chunk is already loaded");
		helper.assertTrue(level.isPositionEntityTicking(near), "setup: the test's own village isn't entity-ticking");
		Leftovers.after(helper, () -> {
			for (BlockPos h : new BlockPos[] {near, notYet, far}) {
				data.forgetSiege(h);
				data.end(h);
			}
			Sieges.forget();
		});
		ThreatData.Siege nearSiege = new ThreatData.Siege(near, level.getGameTime(), level.getDayTime() - 1);
		nearSiege.came = 3;
		data.beginSiege(nearSiege);
		ThreatData.Siege waiting = new ThreatData.Siege(notYet, level.getGameTime(), level.getDayTime() + 12000);
		waiting.came = 3;
		data.beginSiege(waiting);
		data.beginSiege(siege(level, far, level.getDayTime() - 1));

		int[] ticks = {0};
		helper.onEachTick(() -> {
			helper.assertTrue(!loaded(level, far), "the far siege loaded its village's chunk (tick " + ticks[0] + ")");
			if (++ticks[0] >= 160) {
				helper.assertTrue(data.siege(near).isEmpty(), "a siege at a village someone is at, raid over and dawn past, was not lifted");
				helper.assertTrue(data.siege(notYet).isPresent(), "a near siege whose dawn is still to come was lifted early");
				helper.assertTrue(data.siege(notYet).get().over, "a near siege whose raid has ended was not marked over");
				helper.assertTrue(data.siege(far).isPresent(), "the far siege was lifted while nobody was near");
				helper.assertTrue(!data.siege(far).get().over, "the far siege's director ran (it was marked over) while nobody was near");
				helper.succeed();
			}
		});
	}
}
