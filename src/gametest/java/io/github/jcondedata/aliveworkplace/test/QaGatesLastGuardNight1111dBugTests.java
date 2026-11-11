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
 * Bug test (lane D, 2026-11-11, B1158's kin): a village whose only guard shut the gates at dusk and fell in the night has
 * no one left to open them. QA (lane D, 2026-11-11), B1158's fix from the spec: gates a warned day shut open again once it is over, and a village
 * with no guard otherwise leaves its gates as the players set them. These cover the edges of "no guard": the players'
 * own gates in a guardless village, the gates opened once and then shut again by a player, and a village whose last
 * guard falls while its gates are shut (the night of the attack it was warned of, or any night).
 */
public class QaGatesLastGuardNight1111dBugTests implements FabricGameTest {
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

	/** Any night: the only guard shuts the gates at dusk and falls before dawn; in the morning nobody is left to open them. */
	//$ gametest_ticks_batch AREA '60' '"qaLastGuardFallsNight1111d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaLastGuardFallsNight1111d")
	public void theGatesOpenTheMorningAfterTheLastGuardFellInTheNight(GameTestHelper helper) {
		BlockPos hall = gatehouse(helper, false);
		ServerLevel level = helper.getLevel();
		level.setDayTime(BASE + 13000); // dusk, one guard
		Gates.round(level, hall, 1);
		helper.assertTrue(status(helper, hall).open() == 0, "setup: the guard didn't shut the gates at dusk: " + status(helper, hall));
		level.setDayTime(BASE + 18000); // the guard falls
		Gates.round(level, hall, 0);
		level.setDayTime(BASE + VillageNeeds.DAY + 3000); // the next morning, no guard
		Gates.round(level, hall, 0);
		Sieges.Status status = status(helper, hall);
		helper.assertTrue(status.open() == status.gates(), "the gates the last guard shut at dusk stay shut after he fell in the night: " + status);
		helper.succeed();
	}
}
