package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA (lane-b-1027-0938) on the Scan Tool's one sentence no test showed: a box marked on two blocks the player then
 * broke, so nothing is built in it, saved says "There's nothing built in that box",
 * keeps the Blank Blueprint and gives no blueprint; filling the same box with one block then saves it.
 */
public class QaScanEmptyGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final Logger LOGGER = LoggerFactory.getLogger("aliveworkplace-qa");

	//$ gametest_ticks_batch AREA '60' '"qaScanEmptySays"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaScanEmptySays")
	public void scanningAnEmptyBoxSaysSoAndKeepsTheBlank(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageThreeGameTests.listener(helper, seen);
		Leftovers.after(helper, player::discard);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SCAN_TOOL));
		player.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 1));
		// two corner blocks marked, then taken away: the box (3, 2, 3) to (5, 3, 5) is all air
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(3, 2, 3), new BlockPos(5, 3, 5))) {
			helper.setBlock(p, Blocks.AIR);
		}
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.STONE);
		helper.setBlock(new BlockPos(5, 3, 5), Blocks.STONE);
		QaStageThreeGameTests.click(helper, player, new BlockPos(3, 2, 3));
		QaStageThreeGameTests.click(helper, player, new BlockPos(5, 3, 5));
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.AIR);
		helper.setBlock(new BlockPos(5, 3, 5), Blocks.AIR);
		player.setShiftKeyDown(true);
		QaStageThreeGameTests.clickAir(helper, player);
		player.setShiftKeyDown(false);
		String said = QaStageThreeGameTests.last(seen);
		helper.assertTrue(said.equals("message.aliveworkplace.scan.empty"), "saving an empty box said " + said);
		String text = seen.get(seen.size() - 1).getString();
		helper.assertTrue(text.equals("There's nothing built in that box"), "the empty-box sentence reads '" + text + "'");
		helper.assertTrue(player.getInventory().countItem(ModItems.BLANK_BLUEPRINT) == 1, "the empty scan used up the Blank Blueprint");
		helper.assertTrue(player.getInventory().countItem(ModItems.BLUEPRINT) == 0, "the empty scan gave a blueprint");
		// one block in the same box, still marked, and the same save goes through
		helper.setBlock(new BlockPos(4, 2, 4), Blocks.OAK_PLANKS);
		player.setShiftKeyDown(true);
		QaStageThreeGameTests.clickAir(helper, player);
		player.setShiftKeyDown(false);
		said = QaStageThreeGameTests.last(seen);
		helper.assertTrue(said.equals("message.aliveworkplace.scan.saved"), "with a block in the box the scan said " + said);
		helper.assertTrue(player.getInventory().countItem(ModItems.BLANK_BLUEPRINT) == 0 && player.getInventory().countItem(ModItems.BLUEPRINT) == 1,
			"the saved scan didn't trade the Blank Blueprint for a blueprint");
		LOGGER.info("qa scan empty: an empty box is refused ('{}') with the blank kept; with one block it saves", text);
		helper.succeed();
	}
}
