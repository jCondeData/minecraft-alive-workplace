package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.SPOT;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.block;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.held;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.setup;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.tidy;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverCrateDrop1114eCompatTests.moveOntoACrate;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverCrateDrop1114eCompatTests.roofAbove;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverMultiblock1110e2CompatTests.moved;

import io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.Setup;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;

/**
 * QA (lane e, 2026-11-14), B1184's fallback with Create: with no free spot within 16 blocks above the Mover's Crate,
 * MoveSite.dropPacked drops the block item and only a top-level {@code Items} list; an Item Vault keeps its items under
 * {@code Inventory}, so its 48 emeralds are deleted. B1172: "nothing packed is ever destroyed".
 */
public class QaCreateMoverCrateDrop1114eBugCompatTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_compat:huge_area";

	/** Stone stands in the 16 blocks above the crate: the Item Vault and its 48 emeralds are still dropped there. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1114_mover_crate_vault_roof"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1114_mover_crate_vault_roof")
	public void aVaultWithNoRoomAboveTheCrateKeepsItsEmeralds(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		Setup s = setup(helper, "crate_vault_roof_e1114", block("create:item_vault").defaultBlockState(), Items.EMERALD, 48);
		helper.runAfterDelay(2, () -> {
			String[] end = new String[1];
			Map<String, Integer> looseNow;
			long inOld;
			long inNew;
			try {
				looseNow = moveOntoACrate(helper, s, end, () -> roofAbove(level, moved(s, SPOT)));
				inOld = held(level, helper.absolutePos(SPOT), Items.EMERALD);
				inNew = held(level, moved(s, SPOT), Items.EMERALD);
			} finally {
				tidy(helper, s);
			}
			int lying = looseNow.getOrDefault("minecraft:emerald", 0);
			helper.assertTrue(end[0].startsWith("DONE") && end[0].contains("skipped 1,"), "the move: " + end[0]);
			helper.assertTrue(looseNow.getOrDefault("create:item_vault", 0) == 1, "the vault itself isn't dropped: loose " + looseNow);
			helper.assertTrue(inOld + inNew + lying == 48, "a vault holding 48 emeralds went up over a Mover's Crate with no room above it (move " + end[0]
				+ "): " + inOld + " at the old spot, " + inNew + " at the new and " + lying + " lie loose; loose " + looseNow
				+ " (B1172: nothing packed is ever destroyed; the block is dropped with its contents)");
			helper.succeed();
		});
	}
}
