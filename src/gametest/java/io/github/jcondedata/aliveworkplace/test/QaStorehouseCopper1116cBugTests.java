package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.StorehouseBoard;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA, lane C 2026-11-16, ROADMAP 47.4: bug tests (they fail on main until the bug is fixed).
 */
public class QaStorehouseCopper1116cBugTests {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos STOREHOUSE = new BlockPos(24, 2, 24);

	/**
	 * Two builders each short of 16 oak logs, the player carrying 10: Give all can hand over only the 10 logs the player
	 * has, so its button must say "Give all 10" (what a press then gives), not count the same 10 logs once per row.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaC1116StoreGiveAllTwice"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaC1116StoreGiveAllTwice")
	public void giveAllCountsTheSameLogsOnceForTwoRows(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos benchA = new BlockPos(3, 2, 3);
		BlockPos benchB = new BlockPos(3, 2, 12);
		Villager a = QaStorehouseCopper1116cGameTests.builder(helper, benchA);
		Villager b = QaStorehouseCopper1116cGameTests.builder(helper, benchB);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.runAfterDelay(20, () -> QaStorehouseCopper1116cGameTests.withReach(helper, () -> {
			QaStorehouseCopper1116cGameTests.ask(a, Items.OAK_LOG, 16);
			QaStorehouseCopper1116cGameTests.ask(b, Items.OAK_LOG, 16);
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			player.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 10));
			ChoiceMenu board = StorehouseBoard.boardForTest(player, helper.absolutePos(STOREHOUSE));
			String label = board.icon(StorehouseBoard.GIVE_ALL_SLOT).getHoverName().getString();
			board.press(StorehouseBoard.GIVE_ALL_SLOT, player);
			int given = ((Container) helper.getBlockEntity(benchA.north())).countItem(Items.OAK_LOG)
				+ ((Container) helper.getBlockEntity(benchB.north())).countItem(Items.OAK_LOG);
			helper.assertTrue(given == 10, "Give all gave " + given + " logs");
			helper.assertTrue(label.equals("Give all 10"), "the button said '" + label + "' but a press gives the 10 logs carried");
		}));
	}
}
