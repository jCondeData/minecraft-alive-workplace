package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA (2026-10-27), 34.6: what QaClassHallGameTests found the Classes page getting wrong for a player. Each test fails until
 * the mod is fixed.
 */
public class QaClassHallBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/**
	 * A village of one Peasant household (Ann): the Artisan button says what its counts are counted over, and with one
	 * household that must read in the singular, not "Counted over the 1 households who are Artisan or Peasant".
	 */
	//$ gametest_ticks_batch AREA '100' '"qaClassCountedOne"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaClassCountedOne")
	public void countedOverOneHouseholdReadsInTheSingular(GameTestHelper helper) {
		QaClassHallGameTests.classVillage(helper);
		QaClassHallGameTests.ladder(QaClassHallGameTests.VARIED, QaClassHallGameTests.CITY);
		Villager ann = QaClassHallGameTests.person(helper, 3, 3, "Ann", "peasant");
		QaClassHallGameTests.eats(ann, QaClassHallGameTests.VARIED_MEALS);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = QaClassHallGameTests.open(helper, player);
			int tab = QaClassHallGameTests.classesTab(helper, menu, player);
			QaClassHallGameTests.click(menu, player, tab);
			List<String> artisan = QaClassHallGameTests.lore(QaClassHallGameTests.button(menu, "Artisan"));
			helper.assertTrue(artisan.contains("  A varied diet: 1 of 1"), "setup: Ann's diet counted: " + artisan);
			String counted = artisan.stream().filter(l -> l.startsWith("Counted over")).findFirst().orElse("(none)");
			helper.assertTrue(!counted.contains("1 households"), "one household reads in the singular: " + counted);
			player.closeContainer();
			ann.discard();
			helper.succeed();
		});
	}
}
