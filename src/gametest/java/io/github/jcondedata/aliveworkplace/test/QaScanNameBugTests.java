package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * QA bug test (lane-b-1025-0937), registered with the fix (B458, BlueprintNames). The guide (scan-tool.md):
 * "Want a name? Rename the Scan Tool in an anvil first, and the build is called that." A tool renamed "Jesse's Café"
 * saves a build whose name (the saved line, the Blueprint Table, the blueprint) is made from its file id, so the
 * apostrophe and the accent are lost: "Jesse S Caf".
 */
public class QaScanNameBugTests {
	private static final String AREA = "aliveworkplace_test:build_area";

	//$ gametest_batch AREA '"qaScanNameApostrophe"'
	@GameTest(template = AREA, batch = "qaScanNameApostrophe")
	public void aScanNamedWithAnApostropheAndAnAccentKeepsItsName(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageThreeGameTests.listener(helper, seen);
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.OAK_PLANKS);
		ItemStack tool = new ItemStack(ModItems.SCAN_TOOL);
		tool.set(DataComponents.CUSTOM_NAME, Component.literal("Jesse's Café"));
		player.setItemInHand(InteractionHand.MAIN_HAND, tool);
		player.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT));
		QaStageThreeGameTests.click(helper, player, new BlockPos(3, 2, 3));
		QaStageThreeGameTests.click(helper, player, new BlockPos(4, 3, 4));
		player.setShiftKeyDown(true);
		QaStageThreeGameTests.clickAir(helper, player);
		player.setShiftKeyDown(false);
		ItemStack blueprint = ItemStack.EMPTY;
		for (ItemStack stack : player.getInventory().items) {
			if (stack.is(ModItems.BLUEPRINT)) {
				blueprint = stack;
			}
		}
		helper.assertTrue(!blueprint.isEmpty(), "not saved: " + QaStageThreeGameTests.last(seen));
		ResourceLocation id = BlueprintItem.data(blueprint).orElseThrow().structure();
		String name = Blueprints.displayName(id).getString();
		helper.assertTrue(name.equals("Jesse's Café"), "the build named \"Jesse's Café\" in the anvil is called \"" + name + "\" (id " + id
			+ ", the player saw \"" + QaStageThreeGameTests.last(seen) + "\")");
		// the saved line names the build as typed (its words with the sentence's name filled in: last() gives only its key)
		helper.assertTrue(BlueprintNamesGameTests.lastText(seen).startsWith("message.aliveworkplace.scan.saved")
			&& BlueprintNamesGameTests.lastText(seen).contains("Jesse's Café"), "the saved line: " + BlueprintNamesGameTests.lastText(seen));
		helper.succeed();
	}
}
