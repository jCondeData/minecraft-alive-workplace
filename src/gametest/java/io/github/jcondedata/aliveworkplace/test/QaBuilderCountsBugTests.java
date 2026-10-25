package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Shapes;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
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
 * Bug tests (QA 2026-10-24, the sweep after B377/B395/B402/B403/B404 for builder count lines with no singular). The Shape
 * Planner (a builder's blueprint maker) lets each side go down to 1 (Shapes.clamp, QaShapePlannerGameTests), so a 1 x 1 x 1
 * box is one block, and the planner reads "1 blocks of Stone Bricks" on its info book
 * (screen.aliveworkplace.shapes.count, Shapes.fill) and "Drew <name>: 1 blocks of Stone Bricks. Give it to a builder."
 * when drawn (message.aliveworkplace.shapes.drawn, Shapes.draw). Neither key has a ".one". Expected: "1 block of Stone
 * Bricks" (ROADMAP 24.5: "1 parcel", "2 parcels").
 */
public class QaBuilderCountsBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";

	private static void click(ServerPlayer p, int slot, boolean shift) {
		QaStageSixGameTests.click(p, slot, 0, shift ? ClickType.QUICK_MOVE : ClickType.PICKUP);
	}

	private static ChoiceMenu open(GameTestHelper helper, ServerPlayer p) {
		QaStageFourGameTests.pressAir(helper, p);
		helper.assertTrue(p.containerMenu instanceof ChoiceMenu, "a right-click with the planner opened " + p.containerMenu.getClass().getSimpleName());
		return (ChoiceMenu) p.containerMenu;
	}

	/**
	 * A survival player with the Shape Planner, 64 stone bricks and a Blank Blueprint: on the planner's screen he picks the
	 * box and the stone bricks and clicks each side down to 1. The info book's line must read "1 block of Stone Bricks",
	 * and drawing it "Drew ...: 1 block of Stone Bricks. Give it to a builder.".
	 */
	//$ gametest_ticks_batch AREA '60' '"qaCountsShapeOne"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaCountsShapeOne")
	public void aOneBlockShapeSaysOneBlock(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qacS"));
		ServerPlayer p = c.player();
		p.getInventory().clearContent();
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SHAPE_PLANNER));
		p.getInventory().setItem(1, new ItemStack(Items.STONE_BRICKS, 64));
		p.getInventory().setItem(2, new ItemStack(ModItems.BLANK_BLUEPRINT));
		open(helper, p);
		click(p, Shapes.FIRST_KIND + Shapes.Kind.BOX.ordinal(), false);
		click(p, Shapes.FIRST_MATERIAL, false);
		for (int slot : List.of(Shapes.WIDTH, Shapes.HEIGHT, Shapes.DEPTH)) {
			for (int i = 0; i < 8; i++) {
				click(p, slot - 1, true);
			}
		}
		Shapes.Settings s = p.getMainHandItem().get(ModComponents.SHAPE);
		helper.assertTrue(s != null && s.width() == 1 && s.height() == 1 && s.depth() == 1 && s.material().isPresent(), "settings: " + s);
		helper.assertTrue(Shapes.count(s) == 1, "a 1 x 1 x 1 box counts " + Shapes.count(s) + " blocks");
		ChoiceMenu menu = (ChoiceMenu) p.containerMenu;
		List<String> info = QaStageSixGameTests.lore(menu.icon(Shapes.INFO));
		c.seen().clear();
		click(p, Shapes.DRAW, false);
		List<String> said = c.seen().stream().map(Component::getString).toList();
		String drawn = said.stream().filter(t -> t.startsWith("Drew ")).findFirst().orElse("(nothing drawn: " + said + ")");
		helper.assertTrue(info.contains("1 block of Stone Bricks") && drawn.endsWith(": 1 block of Stone Bricks. Give it to a builder."),
			"a one-block shape reads '" + String.join(" / ", info) + "' on its screen and '" + drawn
				+ "' when drawn (want '1 block of Stone Bricks' and '...: 1 block of Stone Bricks. Give it to a builder.')");
		helper.succeed();
	}
}
