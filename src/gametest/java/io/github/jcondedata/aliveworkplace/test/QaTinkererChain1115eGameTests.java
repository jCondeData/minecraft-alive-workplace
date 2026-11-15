package io.github.jcondedata.aliveworkplace.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane E, 2026-11-15): chains at the Tinkerer's forge (crafts/tinkerer.json lists minecraft:chain), the plain suite's
 * side of QaCreateChain1115eBugCompatTests. The spec (crafter.md, B408/B489): with the coal there and no ore, the
 * builder's line names the raw iron short, as for iron bars ("short of 6 Raw Iron to make 3 Iron Bars").
 */
public class QaTinkererChain1115eGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";

	/** A coal and no iron, chains short: the builder's line names the Raw Iron; nothing taken. */
	//$ gametest_ticks_batch AREA '1400' '"qaTinkerChain1115e"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaTinkerChain1115e")
	public void aChainShortWithCoalAndNoIronNamesTheRawIron(GameTestHelper helper) {
		QaJobChainsGameTests.prepare(helper);
		QaJobChainsGameTests.Asker a = QaJobChainsGameTests.asker(helper, Blocks.CHAIN, new ItemStack(Items.COAL, 1));
		Villager tinkerer = QaTinkererShortGameTests.tinkerer(helper);
		helper.runAfterDelay(1200, () -> {
			String text = QaTinkererShortGameTests.line(helper, a);
			helper.assertTrue(text != null && text.contains("Raw Iron"), "the builder's line: '" + text + "', expected it to name the Raw Iron short; missing "
				+ a.site().missing());
			QaTinkererShortGameTests.assertTookNothing(helper, a, tinkerer, 0, 1);
			helper.succeed();
		});
	}
}
