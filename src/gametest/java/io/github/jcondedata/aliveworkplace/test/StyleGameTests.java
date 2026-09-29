package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.blueprint.StylePicker;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Blueprints built in other styles: the same building with its blocks swapped. */
public class StyleGameTests implements FabricGameTest {
	private static Set<String> blockIds(Blueprint blueprint) {
		return blueprint.blocks().stream().map(e -> BuiltInRegistries.BLOCK.getKey(e.state().getBlock()).toString()).collect(Collectors.toSet());
	}

	/** The styles that ship load (Apricorn only with Cobblemon), and a styled id takes apart into its style and base. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void stylesLoadAndIdsRoundTrip(GameTestHelper helper) {
		List<String> names = BlueprintStyles.all().stream().map(BlueprintStyles.Style::name).toList();
		helper.assertTrue(names.equals(List.of("stonework", "sandstone", "dark_oak", "cherry")), "styles: " + names);
		ResourceLocation base = StarterBlueprints.STONE_HOUSE.id();
		ResourceLocation dark = BlueprintStyles.styled(base, "dark_oak");
		helper.assertTrue(dark.toString().equals("aliveworkplace:styled/dark_oak/aliveworkplace/stone_house"), "styled id: " + dark);
		helper.assertTrue(BlueprintStyles.base(dark).equals(base) && BlueprintStyles.styleOf(dark).orElseThrow().equals("dark_oak"), "parse");
		helper.assertTrue(BlueprintStyles.styled(dark, "cherry").equals(BlueprintStyles.styled(base, "cherry")), "restyling keeps one style");
		helper.assertTrue(BlueprintStyles.styled(dark, "").equals(base), "no style gives the base back");
		// Upgrades go by name, so a styled building's upgrade is the upgrade in the same style.
		helper.assertTrue(BlueprintUpgrades.upgradeOf(dark).equals(BlueprintStyles.styled(StarterBlueprints.STONE_HOUSE_2.id(), "dark_oak")),
			"upgrade of " + dark + ": " + BlueprintUpgrades.upgradeOf(dark));
		helper.assertTrue(BlueprintUpgrades.baseOf(BlueprintStyles.styled(StarterBlueprints.STONE_HOUSE_3.id(), "dark_oak")).orElseThrow()
			.equals(BlueprintStyles.styled(StarterBlueprints.STONE_HOUSE_2.id(), "dark_oak")), "base of a styled upgrade");
		String name = Blueprints.displayName(BlueprintStyles.styled(StarterBlueprints.STONE_HOUSE_2.id(), "dark_oak")).getString();
		helper.assertTrue(name.contains("Stone House II") && name.contains("Dark Oak"), "name: " + name);
		helper.succeed();
	}

	/** A styled blueprint is the base with its blocks swapped: same size and shape, stairs facing the same way. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aStyleSwapsTheBlocks(GameTestHelper helper) {
		var server = helper.getLevel().getServer();
		Blueprint base = BlueprintLibrary.get(server, StarterBlueprints.STARTER_COTTAGE.id()).orElseThrow();
		Blueprint dark = BlueprintLibrary.get(server, BlueprintStyles.styled(StarterBlueprints.STARTER_COTTAGE.id(), "dark_oak")).orElseThrow();
		helper.assertTrue(dark.size().equals(base.size()) && dark.blocks().size() == base.blocks().size(), "the shape changed");
		Set<String> baseIds = blockIds(base);
		Set<String> darkIds = blockIds(dark);
		helper.assertTrue(baseIds.contains("minecraft:spruce_log") && baseIds.contains("minecraft:cobblestone"), "base: " + baseIds);
		// (Spruce turns to dark oak, oak to spruce: the cottage's oak door is a spruce one now.)
		helper.assertTrue(darkIds.stream().noneMatch(id -> id.matches("minecraft:spruce_(log|planks|stairs|slab|trapdoor|fence)") || id.equals("minecraft:cobblestone")),
			"dark oak still has: " + darkIds);
		helper.assertTrue(darkIds.contains("minecraft:spruce_door") && !darkIds.contains("minecraft:oak_door"), "the oak door: " + darkIds);
		helper.assertTrue(darkIds.contains("minecraft:dark_oak_log") && darkIds.contains("minecraft:cobbled_deepslate"), "dark oak: " + darkIds);
		for (int i = 0; i < base.blocks().size(); i++) {
			BlockState a = base.blocks().get(i).state();
			BlockState b = dark.blocks().get(i).state();
			helper.assertTrue(base.blocks().get(i).pos().equals(dark.blocks().get(i).pos()), "positions moved");
			if (a.getBlock() instanceof StairBlock && b.getBlock() instanceof StairBlock) {
				helper.assertTrue(a.getValue(StairBlock.FACING) == b.getValue(StairBlock.FACING) && a.getValue(StairBlock.HALF) == b.getValue(StairBlock.HALF),
					"stairs turned: " + a + " -> " + b);
			}
		}
		// Blocks no rule turns into a real block stay as they are (glass, beds, chests).
		helper.assertTrue(darkIds.contains("minecraft:glass_pane") && darkIds.contains("minecraft:chest"), "kept: " + darkIds);
		Set<String> sand = blockIds(BlueprintLibrary.get(server, BlueprintStyles.styled(StarterBlueprints.STARTER_COTTAGE.id(), "sandstone")).orElseThrow());
		helper.assertTrue(sand.contains("minecraft:sandstone") && sand.contains("minecraft:jungle_stairs") && !sand.contains("minecraft:cobblestone"), "sandstone: " + sand);
		// A style that isn't there (a data pack took it away): built as drawn rather than lost.
		Blueprint gone = BlueprintLibrary.get(server, BlueprintStyles.styled(StarterBlueprints.STARTER_COTTAGE.id(), "no_such_style")).orElseThrow();
		helper.assertTrue(blockIds(gone).equals(baseIds), "an unknown style changed the blocks");
		helper.succeed();
	}

	/** Sneak-right-clicking the air with a blueprint opens the style screen; a click restyles the blueprint in hand. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theStylePickerRestylesTheBlueprintInHand(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setItemInHand(InteractionHand.MAIN_HAND, BlueprintItem.create(StarterBlueprints.STONE_HOUSE.id(), StarterBlueprints.STONE_HOUSE.size()));
		ChoiceMenu menu = StylePicker.forTest(player, InteractionHand.MAIN_HAND);
		helper.assertTrue(menu.icon(StylePicker.slot(0)).is(Blocks.SPRUCE_LOG.asItem()), "as drawn: " + menu.icon(StylePicker.slot(0)));
		helper.assertTrue(menu.icon(StylePicker.slot(3)).is(Blocks.DARK_OAK_LOG.asItem()), "third style: " + menu.icon(StylePicker.slot(3)));
		menu.press(StylePicker.slot(3), player);
		ResourceLocation now = BlueprintItem.data(player.getMainHandItem()).orElseThrow().structure();
		helper.assertTrue(now.equals(BlueprintStyles.styled(StarterBlueprints.STONE_HOUSE.id(), "dark_oak")), "restyled to " + now);
		helper.assertTrue(menu.icon(StylePicker.slot(3)).hasFoil(), "the chosen style isn't marked");
		menu.press(StylePicker.slot(0), player);
		helper.assertTrue(BlueprintItem.data(player.getMainHandItem()).orElseThrow().structure().equals(StarterBlueprints.STONE_HOUSE.id()), "back as drawn");
		helper.succeed();
	}

	/**
	 * The style screen's mirror button flips the blueprint in hand; placed, it's built flipped left to right: a block at
	 * the template's left end lands at the right end, the front still the front.
	 */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aMirroredBlueprintIsBuiltFlipped(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setItemInHand(InteractionHand.MAIN_HAND, BlueprintItem.create(StarterBlueprints.STONE_HOUSE.id(), StarterBlueprints.STONE_HOUSE.size()));
		ChoiceMenu menu = StylePicker.forTest(player, InteractionHand.MAIN_HAND);
		menu.press(StylePicker.MIRROR, player);
		var data = BlueprintItem.data(player.getMainHandItem()).orElseThrow();
		helper.assertTrue(data.mirrored() && data.mirror() == net.minecraft.world.level.block.Mirror.FRONT_BACK, "not mirrored");
		helper.assertTrue(menu.icon(StylePicker.MIRROR).hasFoil(), "the mirror button isn't marked");
		net.minecraft.core.Vec3i size = StarterBlueprints.STONE_HOUSE.size();
		var dim = helper.getLevel().dimension().location();
		net.minecraft.core.BlockPos anchor = new net.minecraft.core.BlockPos(100, 64, 100);
		var plain = BlueprintItem.placementAt(dim, size, anchor, net.minecraft.world.level.block.Rotation.NONE);
		var flipped = BlueprintItem.placementAt(dim, size, anchor, net.minecraft.world.level.block.Rotation.NONE, data.mirror());
		var a = io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline.bounds(plain, size);
		var b = io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline.bounds(flipped, size);
		helper.assertTrue(a.minZ() == b.minZ() && a.maxZ() == b.maxZ() && a.getXSpan() == b.getXSpan(), "the flipped build moved: " + a + " vs " + b);
		net.minecraft.core.BlockPos leftEnd = net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.transform(
			new net.minecraft.core.BlockPos(0, 0, 0), flipped.mirror(), flipped.rotation(), net.minecraft.core.BlockPos.ZERO).offset(flipped.origin());
		helper.assertTrue(leftEnd.getX() == a.maxX(), "the left end should land on the right: " + leftEnd + " in " + a);
		menu.press(StylePicker.MIRROR, player);
		helper.assertFalse(BlueprintItem.data(player.getMainHandItem()).orElseThrow().mirrored(), "flipped back");
		helper.succeed();
	}
}
