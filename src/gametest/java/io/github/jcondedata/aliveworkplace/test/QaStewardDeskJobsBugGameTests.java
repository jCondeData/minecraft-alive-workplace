package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.city.StewardDesk;
import io.github.jcondedata.aliveworkplace.hall.StewardDeskPage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * Failing tests for bugs found by {@link QaStewardDeskJobsGameTests} (27.8, 27.9). Kept off {@code main} until the fix
 * lands with them (branch {@code tests/steward-desk-jobs-1025}).
 */
public class QaStewardDeskJobsBugGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/**
	 * Declining a jobs or research proposal on the desk reads "Declined: the Give 1 villager a job won't be proposed for 3
	 * days" and "Declined: the Research Swift Hands won't be proposed for 3 days": the line puts "the" before a name that
	 * is a whole phrase. Every line the player reads must be good English.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaBugDeclineLine"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaBugDeclineLine")
	public void decliningJobsOrResearchReadsAsEnglish(GameTestHelper helper) {
		List<QaStewardDeskJobsGameTests.Seen> heard = new ArrayList<>();
		QaStewardDeskJobsGameTests.Village v = QaStewardDeskJobsGameTests.village(helper, heard, QaStewardDeskJobsGameTests.jobsWish(),
			QaStewardDeskJobsGameTests.researchWish());
		helper.setBlock(new BlockPos(6, 2, 22), Blocks.COMPOSTER);
		QaStewardDeskJobsGameTests.jobless(helper, new BlockPos(12, 2, 15));
		QaStewardDeskJobsGameTests.morning(v);
		int n = StewardDesk.of(v.level(), v.hall()).proposals().size();
		helper.assertTrue(n == 2, "setup: proposals " + StewardDesk.of(v.level(), v.hall()).proposals());
		for (int i = 0; i < n; i++) {
			ChoiceMenu menu = QaStewardDeskJobsGameTests.desk(v.owner(), v.hall());
			menu.press(StewardDeskPage.FIRST_PROPOSAL, v.owner());
			menu.press(StewardDeskPage.DECLINE, v.owner());
		}
		List<QaStewardDeskJobsGameTests.Seen> declined = QaStewardDeskJobsGameTests.lines(heard, "message.aliveworkplace.steward.desk.declined", true);
		helper.assertTrue(declined.size() == 2, "decline lines: " + QaStewardDeskJobsGameTests.keys(heard));
		for (QaStewardDeskJobsGameTests.Seen s : declined) {
			String line = s.text().getString();
			helper.assertTrue(!line.contains("the Give ") && !line.contains("the Research "), "the decline line reads: \"" + line + "\"");
		}
		helper.succeed();
	}
}
