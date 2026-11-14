package io.github.jcondedata.aliveworkplace.test;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (lane b, 2026-11-14), the bug test for B1234: B1184's fix drops a block whose spot is a Mover's Crate by putting it
 * up for a moment at the first free spot within 16 blocks above the crate and breaking it there. With no free spot (the
 * crate roofed in solid blocks 16 high) it falls back to the block's item and its packed Items list, so a lectern's
 * book (or a jukebox's disc, a vault's inventory) is deleted. B1172/B1184's expected: "nothing packed is ever destroyed:
 * the block waits, goes back, or is dropped with its contents". The same lectern with room 16 up passes
 * (QaMoverCrateDrop1114bGameTests#aLecternOverHisCrateWithRoomSixteenUpKeepsItsBook). Fails on main 9de19899e: "the
 * lectern's book was deleted (crate roofed 16 high): 0 of 1 left".
 */
public class QaMoverCrateDrop1114bBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/** No room: solid blocks 16 high above his crate. The lectern and its book must still not be deleted. */
	//$ gametest_batch AREA '"qaB1184bRoof16"'
	@GameTest(template = AREA, batch = "qaB1184bRoof16")
	public void aLecternOverHisCrateWithNoRoomAboveKeepsItsBook(GameTestHelper helper) {
		QaMoverCrateDrop1114bGameTests.lecternUnder(helper, "qacrateroof16", 16);
	}
}
