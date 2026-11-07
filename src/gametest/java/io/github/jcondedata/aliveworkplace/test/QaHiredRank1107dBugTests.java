package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.guard.Mercenaries;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane-d-1107-0708), B990 kin: hired mercenaries "don't count as the village's own" (guard/Mercenaries), yet the
 * hall's round ranks the village by a census that counts them, so hiring a band can rank a Hamlet up.
 */
public class QaHiredRank1107dBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/**
	 * The rank counts the village's villagers too. A Hamlet with 8 villagers of its own and 5 finished buildings (a
	 * Village needs 10 and 5) hires a band of three at the hall. The hall's round, counting what its census counts, must
	 * leave it a Hamlet: hired swords "don't count as the village's own", and a rank won only while they stay (fireworks,
	 * 'is now a Village!', a bigger growth cap and more caravan routes) would be lost again when they leave at dawn.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHiredHelp1107dRank"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHiredHelp1107dRank")
	public void qaAHiredBandDoesntRaiseTheVillagesRank(GameTestHelper helper) {
		BlockPos hall = QaHiredHelp1107dGameTests.village(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		BuildSiteManager sites = BuildSiteManager.get(level);
		List<BlueprintData.Placement> placed = new ArrayList<>();
		Leftovers.after(helper, () -> placed.forEach(sites::forgetFinished));
		for (int i = 0; i < 5; i++) {
			BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(),
				helper.absolutePos(new BlockPos(1 + i * 2, 2, 16)), Rotation.NONE, Mirror.NONE);
			placed.add(placement);
			sites.recordFinished(StarterBlueprints.PARK_BENCH.id(), placement, UUID.randomUUID());
		}
		for (int i = 0; i < 6; i++) { // 2 already in beds: 8 of its own
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(1 + i, 2, 14));
			v.setNoAi(true);
		}
		ServerPlayer player = QaHiredHelp1107dGameTests.payer(helper, hall);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		helper.runAfterDelay(3, () -> {
			VillageRanks.round(level, hall, entity, VillageHalls.census(level, hall).villagers()); // the hall's round (VillageHallBlockEntity.serverTick)
			helper.assertTrue(entity.rank() == VillageRanks.Rank.HAMLET, "setup: 8 villagers and 5 buildings made a " + entity.rank());
			Component said = Mercenaries.hire(level, hall, player);
			List<Villager> band = Mercenaries.near(level, hall);
			helper.assertTrue(band.size() == Mercenaries.BAND, "setup: no band hired: " + said.getString());
			band.forEach(v -> v.setNoAi(true));
			int counted = VillageHalls.census(level, hall).villagers();
			VillageRanks.round(level, hall, entity, counted);
			helper.assertTrue(entity.rank() == VillageRanks.Rank.HAMLET,
				"a Hamlet of 8 villagers and 5 buildings became a " + entity.rank() + " by hiring a band of " + Mercenaries.BAND
					+ " (the hall's round counted " + counted + " villagers)");
			helper.succeed();
		});
	}
}
