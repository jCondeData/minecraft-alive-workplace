package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Bug tests from the lane-b-1024-1537 QA pass on B352/B353, not registered until fixed: register this class with the fix.
 * Helpers are QaJobChainFixesGameTests's and QaCrafterFurnitureGameTests's.
 */
public class QaJobChainFixesBugTests {
	private static final String AREA = QaJobChainFixesGameTests.AREA;

	/**
	 * Fuel is fuel: twelve clay with one coal and one charcoal is two fuels for twelve fired (a coal for every eight), so
	 * the Mason makes the stairs, or else at least doesn't take anything it can't finish.
	 */
	//$ gametest_ticks_batch AREA '3600' '"qaFixMasonMixedFuel"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaFixMasonMixedFuel")
	public void aMasonWithOneCoalAndOneCharcoalMakesTheStairs(GameTestHelper helper) {
		QaCrafterFurnitureGameTests.prepare(helper);
		QaCrafterFurnitureGameTests.Asker a = QaCrafterFurnitureGameTests.asker(helper, QaJobChainFixesGameTests.BENCH, QaJobChainFixesGameTests.CHEST, QaJobChainFixesGameTests.ORIGIN, Blocks.BRICK_STAIRS,
			new ItemStack(Items.CLAY_BALL, 12), new ItemStack(Items.COAL, 1), new ItemStack(Items.CHARCOAL, 1));
		Villager mason = QaJobChainFixesGameTests.mason(helper);
		helper.succeedWhen(() -> {
			Component line = JobShortages.line(helper.getLevel(), a.site().id(), a.site().missing());
			helper.assertTrue(QaCrafterFurnitureGameTests.loose(helper, Items.BRICK) == 0, "bricks dropped on the ground");
			if (helper.getTick() > 1500 && line != null) {
				throw new GameTestAssertException("one coal and one charcoal are two fuels for twelve clay, yet the builder says '" + line.getString() + "'");
			}
			QaCrafterFurnitureGameTests.assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.CLAY_BALL) == 0, chest.countItem(Items.CLAY_BALL) + " clay left");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(mason, 0) == 3, "the mason made " + ModAttachments.ITEMS_CRAFTED.getOrElse(mason, 0));
		});
	}
}
