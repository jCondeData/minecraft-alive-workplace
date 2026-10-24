package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Shapes;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * Bug test (lane-b-1024-0938): drawing with the Shape Planner without a Blank Blueprint tells the player "Saving a scan
 * takes a Blank Blueprint (paper and blue dye)": he is drawing a shape, not saving a scan (Shapes.draw reuses the Scan
 * Tool's message.aliveworkplace.scan.no_blank, and scan.failed the same way). Expected: a line about drawing the shape,
 * e.g. "Drawing a shape takes a Blank Blueprint (paper and blue dye)".
 */
public class QaShapePlannerBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";

	//$ gametest_ticks_batch AREA '60' '"qaShapesNoBlankBug"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaShapesNoBlankBug")
	public void drawingWithoutABlankSpeaksOfTheShapeNotAScan(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qasX"));
		ServerPlayer p = c.player();
		p.getInventory().clearContent();
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SHAPE_PLANNER));
		p.getInventory().setItem(1, new ItemStack(Items.STONE_BRICKS, 64));
		QaStageFourGameTests.pressAir(helper, p);
		QaStageSixGameTests.click(p, Shapes.FIRST_MATERIAL, 0, ClickType.PICKUP);
		c.seen().clear();
		QaStageSixGameTests.click(p, Shapes.DRAW, 0, ClickType.PICKUP);
		List<String> said = c.seen().stream().map(Component::getString).toList();
		helper.assertTrue(said.stream().anyMatch(t -> t.contains("Blank Blueprint")), "no line about the Blank Blueprint: " + said);
		helper.assertFalse(said.stream().anyMatch(t -> t.toLowerCase(java.util.Locale.ROOT).contains("scan")),
			"drawing a shape without a Blank Blueprint talks of a scan: " + said);
		helper.succeed();
	}
}
