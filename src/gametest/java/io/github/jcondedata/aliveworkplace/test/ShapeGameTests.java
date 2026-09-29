package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.Shapes;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The Shape Planner: shapes drawn up as blueprints for the builders. */
public class ShapeGameTests implements FabricGameTest {
	/** Each shape has the blocks it should: walls, hollow insides cleared, open tops and bottoms, the arch's opening. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void shapesHaveTheRightBlocks(GameTestHelper helper) {
		helper.assertTrue(Shapes.count(new Shapes.Settings("box", 5, 3, 1, false, java.util.Optional.empty())) == 15, "a 5 x 3 wall");
		Boolean[][][] box = Shapes.cells(Shapes.Kind.BOX, 5, 5, 5, true);
		helper.assertTrue(Shapes.count(new Shapes.Settings("box", 5, 5, 5, true, java.util.Optional.empty())) == 98, "a hollow 5-box's shell");
		helper.assertTrue(Boolean.FALSE.equals(box[2][2][2]), "a hollow box is cleared inside");
		Boolean[][][] tower = Shapes.cells(Shapes.Kind.CYLINDER, 7, 4, 7, true);
		helper.assertTrue(Boolean.TRUE.equals(tower[3][0][3]), "a hollow cylinder has a floor");
		helper.assertTrue(Boolean.FALSE.equals(tower[3][3][3]), "a hollow cylinder is open on top");
		helper.assertTrue(tower[0][2][0] == null, "the corners are outside a cylinder");
		helper.assertTrue(Boolean.TRUE.equals(tower[0][2][3]), "a cylinder's wall");
		Boolean[][][] dome = Shapes.cells(Shapes.Kind.DOME, 9, 5, 9, true);
		helper.assertTrue(Boolean.TRUE.equals(dome[4][4][4]), "a dome's top");
		helper.assertTrue(Boolean.FALSE.equals(dome[4][0][4]), "a dome has no floor");
		Boolean[][][] arch = Shapes.cells(Shapes.Kind.ARCH, 7, 5, 1, false);
		helper.assertTrue(Boolean.FALSE.equals(arch[3][0][0]) && Boolean.FALSE.equals(arch[3][2][0]), "the way through an arch");
		helper.assertTrue(Boolean.TRUE.equals(arch[0][0][0]) && Boolean.TRUE.equals(arch[3][4][0]), "an arch's sides and top");
		Boolean[][][] pyramid = Shapes.cells(Shapes.Kind.PYRAMID, 7, 4, 7, false);
		helper.assertTrue(Boolean.TRUE.equals(pyramid[3][3][3]) && pyramid[0][3][0] == null, "a pyramid steps in");
		Boolean[][][] sphere = Shapes.cells(Shapes.Kind.SPHERE, 7, 7, 7, true);
		helper.assertTrue(Boolean.TRUE.equals(sphere[3][0][3]) && Boolean.TRUE.equals(sphere[3][6][3]) && Boolean.FALSE.equals(sphere[3][3][3]),
			"a hollow sphere");
		helper.succeed();
	}

	/** Pick a cylinder, stone bricks and a size on the planner's screen, draw it: a Blank Blueprint becomes its blueprint. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void plannerDrawsAShapeBlueprint(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		player.getAbilities().instabuild = false;
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SHAPE_PLANNER));
		player.getInventory().setItem(1, new ItemStack(Items.STONE_BRICKS, 64));
		player.getInventory().setItem(2, new ItemStack(Items.CHEST, 4)); // not a building block
		player.getInventory().setItem(3, new ItemStack(ModItems.BLANK_BLUEPRINT));
		ChoiceMenu menu = ChoiceMenu.detached(player, m -> Shapes.fill(m, player, InteractionHand.MAIN_HAND));
		helper.assertTrue(menu.icon(Shapes.FIRST_MATERIAL).is(Items.STONE_BRICKS), "stone bricks to choose");
		helper.assertTrue(menu.icon(Shapes.FIRST_MATERIAL + 1).isEmpty(), "a chest isn't a building block");
		menu.press(Shapes.FIRST_KIND + Shapes.Kind.CYLINDER.ordinal(), player);
		menu.press(Shapes.FIRST_MATERIAL, player);
		menu.press(Shapes.WIDTH + 1, player);
		menu.press(Shapes.WIDTH + 1, player);
		menu.press(Shapes.HEIGHT - 1, player);
		Shapes.Settings settings = player.getMainHandItem().get(ModComponents.SHAPE);
		helper.assertTrue(settings != null && settings.shape() == Shapes.Kind.CYLINDER && settings.width() == 9 && settings.height() == 3
			&& settings.depth() == 7 && settings.hollow(), "settings: " + settings);
		menu.press(Shapes.DRAW, player);
		helper.assertTrue(player.getInventory().countItem(ModItems.BLANK_BLUEPRINT) == 0, "the blank blueprint wasn't used");
		ResourceLocation id = null;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			var data = BlueprintItem.data(player.getInventory().getItem(i));
			if (data.isPresent()) {
				id = data.get().structure();
			}
		}
		helper.assertTrue(id != null && id.getPath().endsWith("/cylinder_9x3x7_hollow_stone_bricks"), "no blueprint: " + id);
		Blueprint blueprint = BlueprintLibrary.get(helper.getLevel(), id).orElseThrow();
		helper.assertTrue(blueprint.size().getX() == 9 && blueprint.size().getY() == 3 && blueprint.size().getZ() == 7, "size " + blueprint.size());
		helper.assertTrue(blueprint.solidBlockCount() == Shapes.count(settings), "blocks " + blueprint.solidBlockCount());
		helper.succeed();
	}
}
