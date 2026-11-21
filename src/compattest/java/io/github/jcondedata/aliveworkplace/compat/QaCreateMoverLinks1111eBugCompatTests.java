package io.github.jcondedata.aliveworkplace.compat;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;

/**
 * QA (lane E, 2026-11-11, run 2212), the bug half of QaCreateMoverLinks1111eCompatTests (45.3, "the building comes back
 * as it was"): a Create Redstone Link powered by a redstone block beside it, both in a house the Mover moves, should still
 * light its receiver right after the move. It doesn't until something next to the link changes: the Mover puts blocks up
 * without neighbour updates, so the link, put up before its redstone block, never sees it (its neighbours' signal is 15,
 * its own 0).
 */
public class QaCreateMoverLinks1111eBugCompatTests implements FabricGameTest {
	static final String AREA = QaCreateMoverContents1110eCompatTests.AREA;

	/** Right after the move the link beside its redstone block lights the receiver, as before the move. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1111_mover_redstone_link_bug"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1111_mover_redstone_link_bug")
	public void aMovedRedstoneLinkBesideItsRedstoneBlockLightsTheReceiverAtOnce(GameTestHelper helper) {
		QaCreateMoverLinks1111eCompatTests.moveTheLink(helper, "redstone_link_bug", Items.GOLD_INGOT, Items.LAPIS_LAZULI, true);
	}
}
