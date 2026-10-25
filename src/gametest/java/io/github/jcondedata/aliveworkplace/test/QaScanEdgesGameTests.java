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
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-b-1025-0937) of the Scan Tool's save from the guide (scan-tool.md: "Sneak-right-click the air to save it: you
 * get a Blueprint of it, and it takes a Blank Blueprint from your inventory") and en_us.json's scan.empty and
 * scan.no_blank, which no test read: a box with nothing built in it saves nothing and keeps the Blank; with no Blank in a
 * survival inventory nothing is saved, with the sentence; in creative no Blank is needed. Helpers are
 * QaStageThreeGameTests's (the player's own clicks).
 */
public class QaScanEdgesGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";

	private static ServerPlayer scanner(GameTestHelper helper, List<Component> seen, boolean blank) {
		ServerPlayer player = QaStageThreeGameTests.listener(helper, seen);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SCAN_TOOL));
		if (blank) {
			player.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT));
		}
		return player;
	}

	private static void save(GameTestHelper helper, ServerPlayer player) {
		player.setShiftKeyDown(true);
		QaStageThreeGameTests.clickAir(helper, player);
		player.setShiftKeyDown(false);
	}

	private static String said(List<Component> seen) {
		return seen.isEmpty() ? "(nothing)" : seen.get(seen.size() - 1).getString();
	}

	/** Two corners marked on planks, then the planks taken away: "There's nothing built in that box", the Blank kept. */
	//$ gametest_batch AREA '"qaScanEmpty"'
	@GameTest(template = AREA, batch = "qaScanEmpty")
	public void anEmptyBoxSavesNothingAndKeepsTheBlank(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = scanner(helper, seen, true);
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.OAK_PLANKS);
		helper.setBlock(new BlockPos(4, 3, 4), Blocks.OAK_PLANKS);
		QaStageThreeGameTests.click(helper, player, new BlockPos(3, 2, 3));
		QaStageThreeGameTests.click(helper, player, new BlockPos(4, 3, 4));
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.AIR);
		helper.setBlock(new BlockPos(4, 3, 4), Blocks.AIR);
		save(helper, player);
		helper.assertTrue(said(seen).equals("There's nothing built in that box"), "an empty box said \"" + said(seen) + "\"");
		helper.assertTrue(player.getInventory().countItem(ModItems.BLANK_BLUEPRINT) == 1, "the Blank was used for nothing");
		helper.assertTrue(player.getInventory().countItem(ModItems.BLUEPRINT) == 0, "an empty box made a blueprint");
		helper.succeed();
	}

	/** No Blank Blueprint in a survival inventory: "Saving a scan takes a Blank Blueprint (paper and blue dye)", nothing made. */
	//$ gametest_batch AREA '"qaScanNoBlank"'
	@GameTest(template = AREA, batch = "qaScanNoBlank")
	public void withNoBlankInSurvivalNothingIsSaved(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = scanner(helper, seen, false);
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.OAK_PLANKS);
		QaStageThreeGameTests.click(helper, player, new BlockPos(3, 2, 3));
		QaStageThreeGameTests.click(helper, player, new BlockPos(4, 3, 4));
		save(helper, player);
		helper.assertTrue(said(seen).equals("Saving a scan takes a Blank Blueprint (paper and blue dye)"), "with no Blank: \"" + said(seen) + "\"");
		helper.assertTrue(player.getInventory().countItem(ModItems.BLUEPRINT) == 0, "a blueprint was made without a Blank");
		helper.succeed();
	}

	/**
	 * In creative no Blank is needed and none is used: with none the blueprint is made, and with one in the inventory
	 * it's still there after a second save.
	 */
	//$ gametest_batch AREA '"qaScanCreative"'
	@GameTest(template = AREA, batch = "qaScanCreative")
	public void inCreativeNoBlankIsNeededNorUsed(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = scanner(helper, seen, false);
		player.setGameMode(GameType.CREATIVE);
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.OAK_PLANKS);
		QaStageThreeGameTests.click(helper, player, new BlockPos(3, 2, 3));
		QaStageThreeGameTests.click(helper, player, new BlockPos(4, 3, 4));
		save(helper, player);
		helper.assertTrue(player.getInventory().countItem(ModItems.BLUEPRINT) == 1, "creative made no blueprint: \"" + said(seen) + "\"");
		player.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT));
		save(helper, player);
		helper.assertTrue(player.getInventory().countItem(ModItems.BLUEPRINT) == 2, "the second creative save made no blueprint: \"" + said(seen) + "\"");
		helper.assertTrue(player.getInventory().countItem(ModItems.BLANK_BLUEPRINT) == 1, "creative used up the Blank");
		helper.succeed();
	}

	/**
	 * In survival the save takes the Blank, wherever it is in the inventory (here the very first slot, the tool in the
	 * hotbar's fifth): one blueprint made, no Blank left.
	 */
	//$ gametest_batch AREA '"qaScanBlankUsed"'
	@GameTest(template = AREA, batch = "qaScanBlankUsed")
	public void inSurvivalTheSaveTakesTheBlankFromTheFirstSlot(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageThreeGameTests.listener(helper, seen);
		player.getInventory().selected = 4;
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SCAN_TOOL));
		player.getInventory().setItem(0, new ItemStack(ModItems.BLANK_BLUEPRINT));
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.OAK_PLANKS);
		QaStageThreeGameTests.click(helper, player, new BlockPos(3, 2, 3));
		QaStageThreeGameTests.click(helper, player, new BlockPos(4, 3, 4));
		save(helper, player);
		helper.assertTrue(player.getInventory().countItem(ModItems.BLUEPRINT) == 1, "no blueprint from the first slot's Blank: \"" + said(seen) + "\"");
		helper.assertTrue(player.getInventory().countItem(ModItems.BLANK_BLUEPRINT) == 0, "the Blank wasn't used up");
		helper.succeed();
	}
}
