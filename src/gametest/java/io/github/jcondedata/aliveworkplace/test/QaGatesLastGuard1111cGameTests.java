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
import io.github.jcondedata.aliveworkplace.work.Retraining;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA (lane c, 2026-11-11) for B1180, from the spec: "gates the village itself shut open again at dawn when no guard is
 * left to open them; gates a player shut by hand in a guardless village stay as he set them". The guard count comes from
 * the hall's own census (as the hall's round passes it), and the guard loses his job through the hall's "No job"
 * ({@link Retraining#unemploy}) or is given the Guard job back ({@link Retraining#retrain}). Edges the existing tests
 * leave out: the guard given No job at night, a guard hired again before dawn, a player opening one gate by hand in the
 * night, and the next dusk in a village still without a guard.
 */
public class QaGatesLastGuard1111cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 26);
	private static final BlockPos GATEHOUSE = new BlockPos(9, 2, 5);
	private static final BlockPos POST = new BlockPos(5, 2, 20);
	private static final long BASE = 10 * VillageNeeds.DAY;

	/** A hall and a finished Gatehouse at threat level 2, its gates open in the morning of day 11, and one guard at a post. */
	private static Villager village(GameTestHelper helper) {
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
		helper.setBlock(POST, ModBlocks.GUARD_POST);
		Villager guard = helper.spawn(EntityType.VILLAGER, POST.north(2));
		guard.setNoAi(true);
		guard.setCustomName(net.minecraft.network.chat.Component.literal("Gord"));
		Jobs.employ(level, guard, helper.absolutePos(POST), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		Sieges.Status status = Sieges.status(level, hall);
		helper.assertTrue(status.gates() > 0 && status.open() == status.gates(), "setup: the Gatehouse's gates aren't all open: " + status);
		helper.assertTrue(guards(helper) == 1, "setup: the hall counts " + guards(helper) + " guards, not 1");
		return guard;
	}

	private static int guards(GameTestHelper helper) {
		return VillageHalls.census(helper.getLevel(), helper.absolutePos(HALL)).guards();
	}

	/** The hall's round at {@code dayTime}, with the guards its census counts. Returns how many gates moved. */
	private static int round(GameTestHelper helper, long dayTime) {
		ServerLevel level = helper.getLevel();
		level.setDayTime(dayTime);
		return Gates.round(level, helper.absolutePos(HALL), guards(helper));
	}

	private static Sieges.Status status(GameTestHelper helper) {
		return Sieges.status(helper.getLevel(), helper.absolutePos(HALL));
	}

	/** The Gatehouse's fence gates, found in the world. */
	private static List<BlockPos> gates(GameTestHelper helper) {
		List<BlockPos> out = new ArrayList<>();
		BlockPos origin = helper.absolutePos(GATEHOUSE);
		for (BlockPos p : BlockPos.betweenClosed(origin, origin.offset(StarterBlueprints.GATEHOUSE.size()).offset(-1, -1, -1))) {
			if (helper.getLevel().getBlockState(p).getBlock() instanceof FenceGateBlock) {
				out.add(p.immutable());
			}
		}
		return out;
	}

	private static boolean open(GameTestHelper helper, BlockPos gate) {
		BlockState state = helper.getLevel().getBlockState(gate);
		return state.getBlock() instanceof FenceGateBlock && state.getValue(FenceGateBlock.OPEN);
	}

	/** The guard given "No job" at the hall in the night: the gates he shut at dusk stay shut till dawn, then open. */
	//$ gametest_ticks_batch AREA '60' '"qaGates1111cNoJob"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaGates1111cNoJob")
	public void theGatesOpenAtDawnAfterTheLastGuardWasGivenNoJobInTheNight(GameTestHelper helper) {
		Villager guard = village(helper);
		ServerLevel level = helper.getLevel();
		round(helper, BASE + 13000); // dusk, one guard
		helper.assertTrue(status(helper).open() == 0, "setup: the guard didn't shut the gates at dusk: " + status(helper));
		level.setDayTime(BASE + 16000);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Retraining.unemploy(level, player, guard);
		helper.assertTrue(guards(helper) == 0, "setup: the hall still counts " + guards(helper) + " guards after No job");
		round(helper, BASE + 18000); // midnight, no guard
		helper.assertTrue(status(helper).open() == 0, "the guardless village opened its gates at midnight: " + status(helper));
		round(helper, BASE + 23000); // still night
		helper.assertTrue(status(helper).open() == 0, "the guardless village opened its gates before dawn: " + status(helper));
		round(helper, BASE + VillageNeeds.DAY + 1000); // the next morning
		Sieges.Status status = status(helper);
		helper.assertTrue(status.open() == status.gates(), "the gates the guard shut before he was given No job stay shut the morning after: " + status);
		helper.assertFalse(ThreatData.get(level).nightShut(helper.absolutePos(HALL)), "the night's shut is still remembered after the gates opened");
		helper.succeed();
	}

	/**
	 * The guard given No job in the night, then made a guard again before dawn (the hall's Change job): the gates stay shut
	 * the rest of the night, open once at dawn (his round), and the next round moves nothing.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaGates1111cRehired"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaGates1111cRehired")
	public void aGuardHiredAgainBeforeDawnKeepsTheNightAndOpensAtDawn(GameTestHelper helper) {
		Villager guard = village(helper);
		ServerLevel level = helper.getLevel();
		round(helper, BASE + 13000);
		int all = status(helper).gates();
		helper.assertTrue(status(helper).open() == 0, "setup: the guard didn't shut the gates at dusk: " + status(helper));
		level.setDayTime(BASE + 16000);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Retraining.unemploy(level, player, guard);
		round(helper, BASE + 17000);
		helper.assertTrue(status(helper).open() == 0, "the guardless village opened its gates in the night: " + status(helper));
		Retraining.retrain(level, player, guard, helper.absolutePos(POST), ModVillagers.GUARD);
		helper.assertTrue(guards(helper) == 1, "setup: the hall counts " + guards(helper) + " guards after he was made a guard again");
		int moved = round(helper, BASE + 20000);
		helper.assertTrue(moved == 0 && status(helper).open() == 0, "the guard hired again in the night moved " + moved + " gates: " + status(helper));
		moved = round(helper, BASE + VillageNeeds.DAY + 1000);
		helper.assertTrue(moved == all && status(helper).open() == all, "at dawn the guard opened " + moved + " of " + all + " gates: " + status(helper));
		moved = round(helper, BASE + VillageNeeds.DAY + 2000);
		helper.assertTrue(moved == 0, "the round after dawn moved " + moved + " gates again");
		helper.assertFalse(ThreatData.get(level).nightShut(helper.absolutePos(HALL)), "the night's shut is still remembered in the day");
		helper.succeed();
	}

	/**
	 * The guard falls in the night; a player opens one gate by hand: the guardless village leaves it open the rest of the
	 * night (it doesn't shut it again) and the others shut; at dawn all are open.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaGates1111cOneOpened"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaGates1111cOneOpened")
	public void aGateAPlayerOpensInTheNightStaysOpenAndAllOpenAtDawn(GameTestHelper helper) {
		Villager guard = village(helper);
		ServerLevel level = helper.getLevel();
		round(helper, BASE + 13000);
		int all = status(helper).gates();
		helper.assertTrue(status(helper).open() == 0, "setup: the guard didn't shut the gates at dusk: " + status(helper));
		guard.kill();
		helper.assertTrue(guards(helper) == 0, "setup: the hall still counts " + guards(helper) + " guards after he fell");
		round(helper, BASE + 16000);
		List<BlockPos> gates = gates(helper);
		helper.assertTrue(gates.size() == all, "setup: found " + gates.size() + " fence gates in the Gatehouse, the hall counts " + all);
		BlockPos one = gates.get(0);
		level.setBlock(one, level.getBlockState(one).setValue(FenceGateBlock.OPEN, true), 10); // a player opens it by hand
		round(helper, BASE + 18000);
		round(helper, BASE + 22000);
		helper.assertTrue(open(helper, one), "the guardless village shut again in the night a gate a player opened");
		helper.assertTrue(status(helper).open() == 1, "in the night only the player's gate should be open: " + status(helper));
		round(helper, BASE + VillageNeeds.DAY + 1000);
		helper.assertTrue(status(helper).open() == all, "at dawn the guardless village didn't open the rest: " + status(helper));
		helper.succeed();
	}

	/**
	 * The night after: the village still has no guard, so at dusk nobody shuts the gates; they stay open all night, and a
	 * gate a player shuts by hand that night stays shut the next morning.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaGates1111cNextDusk"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaGates1111cNextDusk")
	public void theNextDuskAGuardlessVillageShutsNothing(GameTestHelper helper) {
		Villager guard = village(helper);
		ServerLevel level = helper.getLevel();
		round(helper, BASE + 13000);
		int all = status(helper).gates();
		guard.kill();
		round(helper, BASE + 18000);
		round(helper, BASE + VillageNeeds.DAY + 1000);
		helper.assertTrue(status(helper).open() == all, "setup: the gates didn't open at dawn: " + status(helper));
		int moved = round(helper, BASE + VillageNeeds.DAY + 13000); // the next dusk, still no guard
		helper.assertTrue(moved == 0 && status(helper).open() == all, "a guardless village shut " + moved + " gates at the next dusk: " + status(helper));
		round(helper, BASE + VillageNeeds.DAY + 18000);
		helper.assertTrue(status(helper).open() == all, "a guardless village shut its gates the next night: " + status(helper));
		BlockPos one = gates(helper).get(0);
		level.setBlock(one, level.getBlockState(one).setValue(FenceGateBlock.OPEN, false), 10); // a player shuts one by hand
		round(helper, BASE + VillageNeeds.DAY + 20000);
		round(helper, BASE + 2 * VillageNeeds.DAY + 1000); // the morning after
		helper.assertFalse(open(helper, one), "the gate a player shut in a guardless village was opened the next morning");
		helper.assertTrue(status(helper).open() == all - 1, "the other gates: " + status(helper));
		helper.succeed();
	}
}
