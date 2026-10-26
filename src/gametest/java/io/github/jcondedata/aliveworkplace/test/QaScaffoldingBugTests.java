package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.work.Requests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;

/**
 * QA (2026-10-25) bug tests for scaffolding (ROADMAP 35.6), registered with their fixes (B499, lane-a-1026-1823).
 */
public class QaScaffoldingBugTests {
	private static final String BIG = "aliveworkplace_test:big_area";

	/**
	 * Spec: "With none in the chests it's asked for on the requests board"; wiki: "the shortfall is added to the site's
	 * missing list (the Storehouse's requests board shows it)". With no scaffolding anywhere, at some point while the
	 * pillar is built the builder's requests (what the board and the village hall list) include scaffolding, and the
	 * request reads "Scaffolding" with a count above zero.
	 */
	//$ gametest_ticks_batch BIG '12000' '"qaScaffoldBoard"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qaScaffoldBoard")
	public void noScaffoldingShowsOnTheRequestsBoard(GameTestHelper helper) {
		QaScaffoldingGameTests.Site s = QaScaffoldingGameTests.pillar(helper, 0);
		boolean[] onList = {false};
		String[] shown = {null};
		helper.onEachTick(() -> {
			onList[0] |= s.site().missing().getOrDefault(Items.SCAFFOLDING, 0) > 0;
			if (shown[0] == null) {
				for (Requests.Request r : QaScaffoldingGameTests.board(s)) {
					if (r.icon().is(Items.SCAFFOLDING)) {
						shown[0] = r.count() + " x " + r.what().getString();
					}
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(s.level()).get(s.site().id()) == null, "still building: stage " + s.site().stage());
			helper.assertTrue(onList[0], "scaffolding never went on the site's missing list");
			helper.assertTrue(shown[0] != null, "scaffolding was on the site's missing list but never on the builder's requests (the board) "
				+ "for the whole build");
			helper.assertTrue(shown[0].endsWith("Scaffolding") && !shown[0].startsWith("0 "), "the board read: " + shown[0]);
		});
	}

}
