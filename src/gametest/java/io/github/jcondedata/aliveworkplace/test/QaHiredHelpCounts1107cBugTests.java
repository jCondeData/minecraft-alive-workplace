package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.Mercenaries;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/**
 * QA (lane-a, 2026-11-07) of B990: "hired bands and relief riders leave the hall's villager, housed and fed counts ... as
 * they were". The Wellbeing button's counts do (QaHiredHelpCounts1107cGameTests), but the People button's title, the
 * hall's villager count a player reads first, still counts the hired band.
 */
public class QaHiredHelpCounts1107cBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final int PEOPLE = 1;

	/**
	 * A village of two, each in their own bed: the People button reads 'Villagers: 2'. A band of three hired at the hall:
	 * it should still read 'Villagers: 2' (the band isn't the village's own).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHhc1107People"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHhc1107People")
	public void aHiredBandLeavesTheHallsVillagerCountAsItWas(GameTestHelper helper) {
		BlockPos hall = QaHiredHelpCounts1107cGameTests.village(helper);
		QaHiredHelpCounts1107cGameTests.ownHoused(helper, 2);
		ServerPlayer player = QaHiredHelpCounts1107cGameTests.player(helper, hall);
		helper.runAfterDelay(3, () -> {
			String two = QaHiredHelpCounts1107cGameTests.text("screen.aliveworkplace.hall.villagers", 2);
			String before = VillageHallScreen.forTest(player, hall).icon(PEOPLE).getHoverName().getString();
			helper.assertTrue(before.equals(two), "setup: the People button reads '" + before + "'");
			QaHiredHelpCounts1107cGameTests.hire(helper, player, hall, true);
			String now = VillageHallScreen.forTest(player, hall).icon(PEOPLE).getHoverName().getString();
			helper.assertTrue(now.equals(two), "with a band of " + Mercenaries.BAND + " hired the People button reads '" + now + "' (before: '" + two + "')");
			helper.succeed();
		});
	}
}
