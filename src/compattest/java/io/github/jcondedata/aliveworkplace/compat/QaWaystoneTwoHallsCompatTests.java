package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageWaystones;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * QA (lane d, 2026-10-26), ROADMAP 41.1 with the real Waystones mod: "the stone is the mod's own waystone block, named
 * after the village (renamed when the village is)". Two villages 25 blocks apart, only Ashford with a plaza: renaming
 * the other village (Brookmere) and running its hall's round leave Ashford's stone alone and give Brookmere no stone;
 * renaming Ashford renames it. (Found while fixing B541, where a hall left nearby took the plaza over.)
 */
public class QaWaystoneTwoHallsCompatTests implements FabricGameTest {
	static final String AREA = WaystoneQaCompatTests.AREA;

	//$ gametest_batch AREA '"qaWaystoneTwoHalls"'
	@GameTest(template = AREA, batch = "qaWaystoneTwoHalls")
	public void renamingTheNeighbourVillageLeavesThisStoneAlone(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper);
		VillageHalls.forgetNearest();
		ServerLevel level = helper.getLevel();
		WaystoneQaCompatTests.villages(helper, false);
		helper.assertTrue(WaystoneQaCompatTests.nameAt(helper, WaystoneQaCompatTests.PLAZA_A).equals(Optional.of("Ashford")),
			"Ashford's stone after the rounds: " + WaystoneQaCompatTests.nameAt(helper, WaystoneQaCompatTests.PLAZA_A));

		VillageHallBlockEntity brookmere = helper.getBlockEntity(WaystoneQaCompatTests.HALL_B);
		brookmere.setCustomName(Component.literal("Farfield"));
		VillageWaystones.round(level, helper.absolutePos(WaystoneQaCompatTests.HALL_B));
		helper.assertTrue(WaystoneQaCompatTests.nameAt(helper, WaystoneQaCompatTests.PLAZA_A).equals(Optional.of("Ashford")),
			"the neighbour renamed Farfield renamed Ashford's stone: " + WaystoneQaCompatTests.nameAt(helper, WaystoneQaCompatTests.PLAZA_A));
		helper.assertTrue(VillageWaystones.stoneOf(level, helper.absolutePos(WaystoneQaCompatTests.HALL_B)).isEmpty(),
			"the neighbour took Ashford's stone as its own: " + VillageWaystones.stoneOf(level, helper.absolutePos(WaystoneQaCompatTests.HALL_B)));

		VillageHallBlockEntity ashford = helper.getBlockEntity(WaystoneQaCompatTests.HALL_A);
		ashford.setCustomName(Component.literal("Newford"));
		helper.assertTrue(WaystoneQaCompatTests.nameAt(helper, WaystoneQaCompatTests.PLAZA_A).equals(Optional.of("Newford")),
			"Ashford renamed Newford: its stone is " + WaystoneQaCompatTests.nameAt(helper, WaystoneQaCompatTests.PLAZA_A));
		helper.succeed();
	}
}
