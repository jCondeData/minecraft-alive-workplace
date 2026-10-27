package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.cup.CupChampions;
import io.github.jcondedata.aliveworkplace.cup.CupThemes;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.DyeColor;

/**
 * Bug test (ROADMAP 28.21/28.22): the Cup banner flies "in the theme's colours", and the Workers' Cup's fireworks are
 * gold and brown (#F5C542, #835432, brown dye's own color). CupChampions.nearest compares with the dyes' firework colors,
 * so #835432 comes out red: the Workers' Cup banner is gold on red, the same as the Grand Cup's.
 */
public class QaCupBannerColoursBugGameTests implements FabricGameTest {
	/** The Workers' Cup's banner: a gold (yellow) cup on brown, not the Grand Cup's gold on red. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theWorkersCupBannerIsGoldOnBrown(GameTestHelper helper) {
		CupThemes.Theme workers = CupThemes.get(AliveWorkplace.id("workers_cup"));
		CupThemes.Theme grand = CupThemes.get(AliveWorkplace.id("grand_cup"));
		helper.assertTrue(workers != null && grand != null, "themes not loaded");
		CupChampions.Colours w = CupChampions.colours(workers);
		CupChampions.Colours g = CupChampions.colours(grand);
		helper.assertTrue(w.cup() == DyeColor.YELLOW && w.field() == DyeColor.BROWN, "the Workers' Cup banner is " + w + ", expected a yellow cup on brown");
		helper.assertTrue(!w.equals(g), "the Workers' Cup banner looks like the Grand Cup's: " + w);
		helper.succeed();
	}
}
