package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.guard.Gates;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA (lane D, 2026-11-11 16:08), B1180 from its spec: "gates the village itself shut open again at dawn when no guard is
 * left to open them; gates a player shut by hand in a guardless village stay as he set them". The bug's own tests cover a
 * guard who falls in the night and a reload; these cover the rest of the spec: a guard given another job (counted by the
 * hall's own census, not a number passed in), dawn from both sides of the hour, and a player's own night-shut gates after
 * a guard opened the gates at dawn and then left the village.
 */
public class QaGatesDawnEdge1111dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 26);
	private static final BlockPos GATEHOUSE = new BlockPos(9, 2, 5);
	private static final BlockPos POST = new BlockPos(22, 2, 22);
	private static final long BASE = 10 * VillageNeeds.DAY;

	/** A hall and a finished Gatehouse at threat level 2, its gates open, on the morning of day 11. */
	private static BlockPos gatehouse(GameTestHelper helper) {
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
		Sieges.Status status = Sieges.status(level, hall);
		helper.assertTrue(status.gates() > 0 && status.open() == status.gates(), "setup: the Gatehouse's gates aren't all open: " + status);
		return hall;
	}

	/** One guard at a Guard Post inside the village, counted by the hall's census. */
	private static Villager guard(GameTestHelper helper, BlockPos hall) {
		ServerLevel level = helper.getLevel();
		BlockPos post = helper.absolutePos(POST);
		level.setBlockAndUpdate(post, ModBlocks.GUARD_POST.defaultBlockState());
		Villager guard = EntityType.VILLAGER.create(level);
		guard.moveTo(post.getX() + 0.5, post.getY(), post.getZ() - 1.5, 0f, 0f);
		guard.setNoAi(true); // stays at its post: the test is the gates, not the walk
		level.addFreshEntity(guard);
		Leftovers.after(helper, guard::discard);
		Jobs.employ(level, guard, post, ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		helper.assertTrue(guards(helper, hall) == 1, "setup: the hall's census doesn't count the guard: " + guards(helper, hall));
		return guard;
	}

	private static int guards(GameTestHelper helper, BlockPos hall) {
		return VillageHalls.census(helper.getLevel(), hall).guards();
	}

	/** The hall's round as the hall runs it: with the guards its census counts now. */
	private static void round(GameTestHelper helper, BlockPos hall, long dayTime) {
		helper.getLevel().setDayTime(dayTime);
		Gates.round(helper.getLevel(), hall, guards(helper, hall));
	}

	private static Sieges.Status status(GameTestHelper helper, BlockPos hall) {
		return Sieges.status(helper.getLevel(), hall);
	}

	/** "Or was given another job": the only guard shuts the gates at dusk, takes up no job in the night, and dawn opens them. */
	//$ gametest_ticks_batch AREA '80' '"qaGateGuardFired1111d"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaGateGuardFired1111d")
	public void aGuardWhoLeavesTheJobInTheNightLeavesTheGatesToOpenAtDawn(GameTestHelper helper) {
		BlockPos hall = gatehouse(helper);
		Villager guard = guard(helper, hall);
		round(helper, hall, BASE + 13000); // dusk
		helper.assertTrue(status(helper, hall).open() == 0, "the guard didn't shut the gates at dusk: " + status(helper, hall));
		guard.setVillagerData(guard.getVillagerData().setProfession(VillagerProfession.NONE)); // no longer a guard
		guard.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
		helper.assertTrue(guards(helper, hall) == 0, "setup: the census still counts the former guard");
		round(helper, hall, BASE + 18000); // midnight, no guard
		helper.assertTrue(status(helper, hall).open() == 0, "a guardless village opened its gates in the middle of the night: " + status(helper, hall));
		round(helper, hall, BASE + VillageNeeds.DAY + 1000); // morning
		Sieges.Status status = status(helper, hall);
		helper.assertTrue(status.open() == status.gates(), "the gates stay shut the morning after the only guard left the job in the night: " + status);
		helper.succeed();
	}

	/** Dawn from both sides: shut one tick before the hour the guards open them (23500), open from that tick. */
	//$ gametest_ticks_batch AREA '80' '"qaGateDawnHour1111d"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaGateDawnHour1111d")
	public void theGuardlessGatesOpenAtTheGuardsOwnHourNotATickBefore(GameTestHelper helper) {
		BlockPos hall = gatehouse(helper);
		ServerLevel level = helper.getLevel();
		level.setDayTime(BASE + 12500); // the first tick of the guards' night
		Gates.round(level, hall, 1);
		helper.assertTrue(status(helper, hall).open() == 0, "a guard didn't shut the gates at 12500: " + status(helper, hall));
		level.setDayTime(BASE + 23499); // the guard has fallen; one tick before dawn
		Gates.round(level, hall, 0);
		helper.assertTrue(status(helper, hall).open() == 0, "a guardless village opened its gates before dawn (23499): " + status(helper, hall));
		level.setDayTime(BASE + 23500); // dawn: the hour a guard opens them
		Gates.round(level, hall, 0);
		Sieges.Status status = status(helper, hall);
		helper.assertTrue(status.open() == status.gates(), "a guardless village didn't open its gates at dawn (23500): " + status);
		helper.succeed();
	}

	/**
	 * A guard opens the gates at dawn and leaves the village that day; that night a player shuts them by hand. Nobody
	 * shut them for the village, so they stay as the player set them, through the night and the next dawn.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaGatePlayerAfterGuard1111d"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaGatePlayerAfterGuard1111d")
	public void aPlayersNightShutGatesStayShutAfterTheGuardOpenedThemAndLeft(GameTestHelper helper) {
		BlockPos hall = gatehouse(helper);
		ServerLevel level = helper.getLevel();
		level.setDayTime(BASE + 13000); // dusk: the guard shuts them
		Gates.round(level, hall, 1);
		level.setDayTime(BASE + VillageNeeds.DAY + 1000); // dawn: the guard opens them
		Gates.round(level, hall, 1);
		helper.assertTrue(status(helper, hall).open() == status(helper, hall).gates(), "setup: the guard didn't open the gates in the morning: " + status(helper, hall));
		helper.assertTrue(!ThreatData.get(level).nightShut(hall), "the guard opened the gates, yet the night's shut is still remembered");
		level.setDayTime(BASE + VillageNeeds.DAY + 6000); // the guard leaves; noon, no guard
		Gates.round(level, hall, 0);
		level.setDayTime(BASE + VillageNeeds.DAY + 14000); // night: a player shuts the gates by hand
		Gates.shut(level, hall, true);
		Gates.round(level, hall, 0);
		helper.assertTrue(status(helper, hall).open() == 0, "a guardless village opened a player's gates in the night: " + status(helper, hall));
		level.setDayTime(BASE + 2 * VillageNeeds.DAY + 1000); // the next dawn
		Gates.round(level, hall, 0);
		helper.assertTrue(status(helper, hall).open() == 0, "a guardless village opened at dawn the gates a player shut by hand: " + status(helper, hall));
		helper.succeed();
	}
}
