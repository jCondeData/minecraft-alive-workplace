package io.github.jcondedata.aliveworkplace.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** QA lane B (2026-11-23): the rebuilt farms' failing checks (see each test's doc), kept apart so {@code main} stays green. */
public class QaRebuiltFarms1123bBugTests {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/**
	 * berry_farm.md, Level II: "More fruit, and a second kind: the player guide promises glow berries (cave vines with
	 * berries)", "Fruit the keeper can pick". A Builder builds Berry Farm II from exactly its materials across a reload
	 * (DONE, every block right); the built farm's cave vines must bear glow berries by themselves: 400 random ticks of
	 * every vine tip (a fixed random) bring at least one out. Today the drawing hangs 64 vines with berries but every tip
	 * at age 25 (fully grown, so it never grows again), and the builder plants vines without their berries (B494), so
	 * the built farm never shows a glow berry and the Orchard Keeper has none to pick.
	 */
	//$ gametest_ticks_batch HUGE '45000' '"qaFarms1123bBerryFarmIIGlow"'
	@GameTest(template = HUGE, timeoutTicks = 45000, batch = "qaFarms1123bBerryFarmIIGlow")
	public void qaTheBuiltBerryFarmIIsVinesBearGlowBerries(GameTestHelper helper) {
		QaRebuiltFarms1123bGameTests.berryFarmII(helper, true);
	}
}
