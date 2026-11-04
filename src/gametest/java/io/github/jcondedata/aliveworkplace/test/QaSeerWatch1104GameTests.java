package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.DefencePage;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;

/**
 * QA, lane D, 2026-11-04 (B919, a B133 follow-up): with only a Seer watching, the Defense page's idle line must not read
 * "&lt;Seer&gt; keeps watch from a tower: a raid is seen a day ahead" (the Seer has no tower and sees two days ahead). Moved
 * here from {@code QaLaneD1104BugTests} on tests/lane-d-1104 with its fix; that class's other test is B920's.
 */
public class QaSeerWatch1104GameTests implements FabricGameTest {
	private static final String BIG = QaLaneD1104GameTests.BIG;

	/**
	 * The Defense page while no attack is coming names who watches: with only a Seer, it must not say they keep watch from
	 * a tower and see a day ahead (the Seer has no tower and sees two days ahead, as 32.14 and the wiki say).
	 */
	//$ gametest_ticks_batch BIG '100' '"qa1104SeerPage"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qa1104SeerPage")
	public void qaB133TheDefensePageDoesNotPutTheSeerOnATower(GameTestHelper helper) {
		QaLaneD1104GameTests.SeerVillage v = QaLaneD1104GameTests.seerVillage(helper);
		ItemStack next = DefencePage.next(v.level(), v.hall());
		List<String> lines = QaLaneD1104GameTests.lore(next);
		String name = v.seer().getName().getString();
		helper.assertTrue(next.getHoverName().getString().equals("No attack seen coming"), "the page: " + next.getHoverName().getString());
		helper.assertTrue(lines.stream().anyMatch(l -> l.contains(name)), "the page doesn't name the Seer: " + lines);
		helper.assertTrue(lines.stream().noneMatch(l -> l.contains("from a tower") || l.contains("a day ahead")),
			"the page says the Seer keeps watch from a tower, a day ahead: " + lines);
		helper.succeed();
	}
}
