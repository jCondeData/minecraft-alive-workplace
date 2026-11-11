package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.guard.Gates;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.guard.Warnings;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
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
 * QA (lane D, 2026-11-11), B1158's fix from the spec: gates a warned day shut open again once it is over, and a village
 * with no guard otherwise leaves its gates as the players set them. These cover the edges of "no guard": the players'
 * own gates in a guardless village, the gates opened once and then shut again by a player, and a village whose last
 * guard falls while its gates are shut (the night of the attack it was warned of, or any night).
 */
public class QaGatesNoGuardLeft1111dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 26);
	private static final BlockPos GATEHOUSE = new BlockPos(9, 2, 5);
	private static final long BASE = 10 * VillageNeeds.DAY;

	/** A hall and a finished Gatehouse at threat level 2, its gates open, warned of day 12's attack if asked. */
	private static BlockPos gatehouse(GameTestHelper helper, boolean warned) {
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
		VillageRaids.ENABLED = true;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		level.getStructureManager().get(StarterBlueprints.GATEHOUSE.id()).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(),
			RandomSource.create(1L), 2);
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.GATEHOUSE.id(), placement, UUID.randomUUID());
		ThreatData.get(level).forgetClock(hall);
		level.setDayTime(BASE + 3000); // the morning of day 11
		Gates.shut(level, hall, false);
		if (warned) {
			level.setDayTime(BASE + VillageNeeds.DAY + 12100); // dusk of day 11: tomorrow night's attack, and its warning
			ThreatData.get(level).rolled(hall, 11, new ThreatData.Attack(12, Threats.MONSTERS, -Math.PI / 2, 13600));
			ThreatData.get(level).warned(hall, 12);
		}
		Sieges.Status status = Sieges.status(level, hall);
		helper.assertTrue(status.gates() > 0 && status.open() == status.gates(), "setup: the Gatehouse's gates aren't all open: " + status);
		return hall;
	}

	private static Sieges.Status status(GameTestHelper helper, BlockPos hall) {
		return Sieges.status(helper.getLevel(), hall);
	}

	/** No guard and no warning: gates a player shut stay shut, morning or night (the village leaves them as he set them). */
	//$ gametest_ticks_batch AREA '60' '"qaNoGuardPlayersGates1111d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaNoGuardPlayersGates1111d")
	public void aGuardlessVillageLeavesAPlayersShutGatesAlone(GameTestHelper helper) {
		BlockPos hall = gatehouse(helper, false);
		ServerLevel level = helper.getLevel();
		Gates.shut(level, hall, true); // as a player shuts them by hand
		level.setDayTime(BASE + 3500);
		Gates.round(level, hall, 0);
		helper.assertTrue(status(helper, hall).open() == 0, "a guardless village that was never warned opened the gates a player shut: " + status(helper, hall));
		Gates.shut(level, hall, false); // and opened by hand
		level.setDayTime(BASE + 14000); // night
		Gates.round(level, hall, 0);
		Sieges.Status status = status(helper, hall);
		helper.assertTrue(status.open() == status.gates(), "a guardless village shut the gates a player left open at night: " + status);
		helper.succeed();
	}

	/** After the warned day the gates open once; a player who shuts them again the next morning finds them left shut. */
	//$ gametest_ticks_batch AREA '60' '"qaNoGuardOpensOnce1111d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaNoGuardOpensOnce1111d")
	public void theGatesOpenOnceAfterTheWarnedDayThenAreThePlayersAgain(GameTestHelper helper) {
		BlockPos hall = gatehouse(helper, true);
		ServerLevel level = helper.getLevel();
		level.setDayTime(BASE + VillageNeeds.DAY + 5900); // just before noon of day 12
		Gates.round(level, hall, 0);
		helper.assertTrue(status(helper, hall).open() == status(helper, hall).gates(), "the gates shut before noon of the warned day: " + status(helper, hall));
		level.setDayTime(BASE + VillageNeeds.DAY + 6100); // noon
		Gates.round(level, hall, 0);
		helper.assertTrue(status(helper, hall).open() == 0, "the guardless village's gates aren't shut at noon of the warned day: " + status(helper, hall));
		level.setDayTime(BASE + 2 * VillageNeeds.DAY + 3000); // the morning of day 13
		Gates.round(level, hall, 0);
		Sieges.Status status = status(helper, hall);
		helper.assertTrue(status.open() == status.gates(), "the gates are still shut the morning after the warned day: " + status);
		Gates.shut(level, hall, true); // a player shuts them
		level.setDayTime(BASE + 2 * VillageNeeds.DAY + 4000);
		Gates.round(level, hall, 0);
		Gates.round(level, hall, 0);
		helper.assertTrue(status(helper, hall).open() == 0, "after opening them once, the village keeps opening gates a player shut: " + status(helper, hall));
		helper.succeed();
	}

	/**
	 * A guarded village warned of an attack shuts its gates at noon; its only guard falls in the night's fight. The next
	 * morning, with no guard left, the gates the warning shut open again (B1158's "once the warned day is over").
	 */
	//$ gametest_ticks_batch AREA '60' '"qaLastGuardFallsWarned1111d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaLastGuardFallsWarned1111d")
	public void theGatesOpenTheMorningAfterTheWarnedNightKilledTheLastGuard(GameTestHelper helper) {
		BlockPos hall = gatehouse(helper, true);
		ServerLevel level = helper.getLevel();
		level.setDayTime(BASE + VillageNeeds.DAY + 6100); // noon of day 12, one guard
		Gates.round(level, hall, 1);
		helper.assertTrue(status(helper, hall).open() == 0, "setup: the guarded village's gates aren't shut at noon of the warned day: " + status(helper, hall));
		level.setDayTime(BASE + VillageNeeds.DAY + 14000); // the attack's night; the guard falls
		Gates.round(level, hall, 0);
		level.setDayTime(BASE + 2 * VillageNeeds.DAY + 3000); // the morning of day 13, no guard
		Gates.round(level, hall, 0);
		Warnings.round(level, hall, 5, 0);
		Sieges.Status status = status(helper, hall);
		helper.assertTrue(status.open() == status.gates(), "the warned day is over, but the gates its guard shut stay shut now that he is dead (villagers can't open them): " + status);
		helper.succeed();
	}

	/** Control at the boundary: a village with exactly one guard shuts its gates at dusk and opens them at dawn (his round). */
	//$ gametest_ticks_batch AREA '60' '"qaOneGuardNight1111d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaOneGuardNight1111d")
	public void aVillageWithOneGuardShutsAtDuskAndOpensAtDawn(GameTestHelper helper) {
		BlockPos hall = gatehouse(helper, false);
		ServerLevel level = helper.getLevel();
		level.setDayTime(BASE + 13000); // dusk
		Gates.round(level, hall, 1);
		helper.assertTrue(status(helper, hall).open() == 0, "one guard didn't shut the gates at dusk: " + status(helper, hall));
		level.setDayTime(BASE + VillageNeeds.DAY + 1000); // the next morning
		Gates.round(level, hall, 1);
		Sieges.Status status = status(helper, hall);
		helper.assertTrue(status.open() == status.gates(), "one guard didn't open the gates in the morning: " + status);
		helper.succeed();
	}
}
