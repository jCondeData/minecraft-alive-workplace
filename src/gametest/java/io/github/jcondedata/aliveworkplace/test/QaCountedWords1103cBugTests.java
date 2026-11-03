package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;

/**
 * QA (lane C, 2026-11-03; B367 leftover, not registered until fixed): a Jeweller making two rings names them in the
 * singular, "fetching what 2 Amethyst Ring take" and "making 2 Amethyst Ring", though the lang has the plural
 * (plural.aliveworkplace.aliveworkplace.amethyst_ring "Amethyst Rings") and B367 asks that "many read as many".
 */
public class QaCountedWords1103cBugTests implements FabricGameTest {
	private static final String LUX = QaLuxuryJobsGameTests.AREA;

	/** Two rings: the fetching and making lines name the rings in the plural. */
	//$ gametest_ticks_batch LUX '2400' '"qaCw1103cLuxTwoBug"'
	@GameTest(template = LUX, timeoutTicks = 2400, batch = "qaCw1103cLuxTwoBug")
	public void aJewellerMakingTwoRingsNamesThemInThePlural(GameTestHelper helper) {
		Set<String> lines = QaCountedWords1103cGameTests.jewellerLines(helper, 6, 2);
		Container c = QaLuxuryJobsGameTests.chest(helper);
		helper.succeedWhen(() -> {
			helper.assertTrue(c.countItem(ModItems.AMETHYST_RING) == 8, c.countItem(ModItems.AMETHYST_RING) + " rings, expected 8");
			helper.assertTrue(lines.contains("fetching what 2 Amethyst Rings take"), "want 'fetching what 2 Amethyst Rings take'; lines seen: " + lines);
			helper.assertTrue(lines.contains("making 2 Amethyst Rings"), "want 'making 2 Amethyst Rings'; lines seen: " + lines);
		});
	}
}
