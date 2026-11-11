package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.guard.Gates;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.guard.Warnings;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA (lane D, 2026-11-10): 32.14's "on the day, from noon, the gates shut" in a village with no guard. A village can be
 * warned without one (the Seer's two days, B133, and the Lighthouse keeper, 35.12, are sources with no guard posted;
 * SeerWarningGameTests shuts a guardless village's gates at noon). The gates shut for the warning must open again when
 * the warned day is over (as they do with a guard, whose round opens them by day): the morning after, or as soon as the
 * attack is called off (B1146).
 */
public class QaGuardlessGates1110dBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 26);
	private static final BlockPos GATEHOUSE = new BlockPos(9, 2, 5);
	private static final long BASE = 10 * VillageNeeds.DAY;

	/** A hall and a finished Gatehouse, warned of an attack on day 12 (as a Seer or the Lighthouse would warn). */
	private static BlockPos warnedGatehouse(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		long time = level.getDayTime();
		int radius = VillageHalls.RADIUS;
		BlockPos origin = helper.absolutePos(GATEHOUSE);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		Leftovers.after(helper, () -> {
			Sieges.raise(level, hall, false);
			VillageRaids.forget(hall);
			ThreatData.get(level).forgetSiege(hall);
			ThreatData.get(level).forgetPast(hall);
			ThreatData.get(level).forgetClock(hall);
			Sieges.forget();
			BuildSiteManager.get(level).forgetFinished(placement);
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		VillageHalls.RADIUS = 24;
		WorkplaceConfig.parse("{\"threatLevel\": 2}").apply();
		VillageRaids.ENABLED = true; // (apply() turns raids off under GameTests; the after() puts the config back)
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		level.getStructureManager().get(StarterBlueprints.GATEHOUSE.id()).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(),
			RandomSource.create(1L), 2);
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.GATEHOUSE.id(), placement, UUID.randomUUID());
		ThreatData.get(level).forgetClock(hall);
		level.setDayTime(BASE + 12100); // dusk of day 11: tomorrow night's attack, and the warning of it
		ThreatData.get(level).rolled(hall, 11, new ThreatData.Attack(12, Threats.MONSTERS, -Math.PI / 2, 13600));
		ThreatData.get(level).warned(hall, 12);
		level.setDayTime(BASE + VillageNeeds.DAY + 6100); // noon of day 12
		Gates.round(level, hall, 0);
		Sieges.Status status = Sieges.status(level, hall);
		helper.assertTrue(Warnings.ready(level, hall) && status.gates() > 0 && status.open() == 0, "setup: the guardless village's gates aren't shut at noon of the warned day: " + status);
		return hall;
	}

	/** The warned night passes with no raid (none came); the next morning the gates are open again. */
	//$ gametest_ticks_batch AREA '60' '"qaGuardlessGatesMorning1110d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaGuardlessGatesMorning1110d")
	public void aGuardlessVillagesGatesOpenTheMorningAfterTheWarnedDay(GameTestHelper helper) {
		BlockPos hall = warnedGatehouse(helper);
		ServerLevel level = helper.getLevel();
		level.setDayTime(BASE + 2 * VillageNeeds.DAY + 3000); // the morning of day 13
		helper.assertTrue(!Warnings.ready(level, hall), "setup: still the warned day");
		Gates.round(level, hall, 0);
		Warnings.round(level, hall, 5, 0);
		Sieges.Status status = Sieges.status(level, hall);
		helper.assertTrue(status.open() == status.gates(), "the gates shut for the warned day are still shut the morning after (no guard to open them): " + status);
		helper.succeed();
	}

	/** Control: with a guard, the same gates are open the morning after (the guard's round). */
	//$ gametest_ticks_batch AREA '60' '"qaGuardedGatesMorning1110d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaGuardedGatesMorning1110d")
	public void aGuardedVillagesGatesOpenTheMorningAfterTheWarnedDay(GameTestHelper helper) {
		BlockPos hall = warnedGatehouse(helper);
		ServerLevel level = helper.getLevel();
		level.setDayTime(BASE + 2 * VillageNeeds.DAY + 3000);
		Gates.round(level, hall, 1);
		Sieges.Status status = Sieges.status(level, hall);
		helper.assertTrue(status.open() == status.gates(), "with a guard the gates are still shut the morning after: " + status);
		helper.succeed();
	}

	/** The attack called off at threat level 0 in the afternoon: the gates shut at noon open again. */
	//$ gametest_ticks_batch AREA '60' '"qaGuardlessGatesCallOff1110d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaGuardlessGatesCallOff1110d")
	public void aGuardlessVillagesGatesOpenWhenTheAttackIsCalledOff(GameTestHelper helper) {
		BlockPos hall = warnedGatehouse(helper);
		ServerLevel level = helper.getLevel();
		WorkplaceConfig.parse("{\"threatLevel\": 0}").apply();
		level.setDayTime(BASE + VillageNeeds.DAY + 8000); // the afternoon
		VillageRaids.tick(level, hall, 5, 0, -1, d -> {
		});
		Gates.round(level, hall, 0);
		Warnings.round(level, hall, 5, 0);
		helper.assertTrue(!Warnings.ready(level, hall), "setup: the call-off left the village braced");
		Sieges.Status status = Sieges.status(level, hall);
		helper.assertTrue(status.open() == status.gates(), "the attack was called off, yet the guardless village's gates stay shut: " + status);
		helper.succeed();
	}
}
