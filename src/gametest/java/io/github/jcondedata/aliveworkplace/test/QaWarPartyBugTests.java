package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.DefencePage;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * QA findings for ROADMAP 32.13, registered as each is fixed (B387). The lone guard's lair-break check
 * (qaALoneGuardsKillAtTheLairIsNotAWarParty) stays on tests/war-party-1024 until its own bug is settled.
 */
public class QaWarPartyBugTests implements FabricGameTest {
	private static final String AREA = QaWarPartyGameTests.AREA;

	/**
	 * Four guards: the muster's chat and the page's line say one guard is left on the walls, so it should read "1 keeps
	 * the walls", not "1 keep the walls".
	 */
	//$ gametest_batch AREA '"qaWpBugOneKeeps"'
	@GameTest(template = AREA, batch = "qaWpBugOneKeeps")
	public void qaOneGuardLeftOnTheWallsReadsInTheSingular(GameTestHelper helper) {
		BlockPos hall = QaWarPartyGameTests.village(helper);
		ServerLevel level = helper.getLevel();
		QaStageFiveGameTests.Client c = QaWarPartyGameTests.client(helper);
		ServerPlayer p = c.player();
		for (int i = 0; i < 4; i++) {
			QaWarPartyGameTests.guard(helper, new BlockPos(3 + i, 2, 7));
		}
		ChoiceMenu menu = QaWarPartyGameTests.defencePage(p, level, hall);
		List<String> lore = QaStageSixGameTests.lore(menu.icon(DefencePage.MUSTER));
		helper.assertTrue(lore.contains("3 guards would march, 1 keeps the walls"), "the page says: " + lore);
		p.getInventory().add(new ItemStack(ModItems.RALLY_BANNER));
		QaWarPartyGameTests.click(menu, DefencePage.MUSTER, p);
		String said = QaWarPartyGameTests.last(c.seen());
		helper.assertTrue(said.equals("3 guards gather by the hall under your Rally Banner; 1 keeps the walls"), "said: " + said);
		helper.succeed();
	}
}
