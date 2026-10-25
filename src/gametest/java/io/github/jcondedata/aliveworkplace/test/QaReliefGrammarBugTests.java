package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.Relief;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** QA bug (33.17): a band of one rider is told in the plural ("1 guards"). */
public class QaReliefGrammarBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/** The capital with no guards sends one rider: its hall and chronicle should say "1 guard", not "1 guards". */
	//$ gametest_ticks_batch AREA '20' '"qaReliefOne"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaReliefOne")
	public void aBandOfOneIsOneGuard(GameTestHelper helper) {
		RealmGameTests.Land land = ReliefGameTests.realm(helper, 0);
		ReliefGameTests.treasury(land, 1000);
		helper.assertTrue(Relief.call(land.level(), land.hall(1)) == 1, "not one rider");
		String line = Relief.hallLine(land.level(), land.hall(0)) == null ? "" : Relief.hallLine(land.level(), land.hall(0)).getString();
		helper.assertTrue(line.equals("1 guard away helping " + land.name(1)), "the capital's hall line: " + line);
		helper.assertTrue(ReliefGameTests.chronicleSays(land, 0, "1 guard rode to help " + land.name(1) + " in a raid"),
			"the capital's chronicle: " + land.entity(0).chronicle().stream().map(e -> e.text().getString()).toList());
		helper.succeed();
	}
}
