package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.WaystoneQaCompatTests.*;

import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.VillageWaystones;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * QA bug test (Milestone 41.2): a village whose plaza is gone must leave the waystone network at its next hall round, as
 * the wiki says, even when another village's plaza stands within the hall radius (64 blocks). Today
 * {@code VillageWaystones.round} only forgets the village when there's no plaza at all round the hall, the neighbour's
 * included, so the village stays listed ("its waystone is broken") for good.
 */
public class WaystoneQaBugCompatTests implements FabricGameTest {
	/**
	 * Ashford and Brookmere 27 blocks apart, each with its plaza; Brookmere's stone broken and its plaza demolished
	 * (forgotten): its next round takes it off the network, though Ashford's plaza stands near it.
	 */
	//$ gametest_batch AREA '"qaWaystonePlazaGoneNeighbour"'
	@GameTest(template = AREA, batch = "qaWaystonePlazaGoneNeighbour")
	public void aVillageWhosePlazaIsGoneLeavesTheNetworkWithANeighbourPlazaNearby(GameTestHelper helper) {
		cleanUp(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			villages(helper, true);
			BlockPos a = helper.absolutePos(HALL_A);
			BlockPos b = helper.absolutePos(HALL_B);
			breakStone(helper, PLAZA_B);
			BuildSiteManager.get(level).forgetFinished(placement(helper, PLAZA_B)); // Brookmere's plaza gone; Ashford's stands
			VillageWaystones.round(level, b);
			helper.assertTrue(VillageWaystones.stoneOf(level, b).isEmpty(), "Brookmere has no plaza but is still kept with stone " + VillageWaystones.stoneOf(level, b)
				+ " (Ashford's plaza is within the hall radius)");
			List<String> lines = texts(mapLore(player, a), "screen.aliveworkplace.hall.waystone");
			helper.assertTrue(lines.equals(List.of("Waystone network (1):", "Ashford, this village: not discovered yet")), "Ashford's Map: " + lines);
		} finally {
			cleanUp(helper);
		}
		helper.succeed();
	}
}
