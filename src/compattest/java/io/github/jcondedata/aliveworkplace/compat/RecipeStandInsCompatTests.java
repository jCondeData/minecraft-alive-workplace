package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.craft.RecipeStandIns;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * B1302, B1286 (crafter.md): Carved Wood (the owner's pack, its default config) switches off Minecraft's planks chest
 * recipe; 8 oak planks make its Oak Chest, and its special chest_from_modded_chests recipe (code, no ingredients to read)
 * makes a plain chest from any one carved chest. The {@code recipe_stand_ins} file reads that special recipe as a plain
 * one, so the planner finds a player's two crafts, and the line for a chest from nothing names logs.
 */
public class RecipeStandInsCompatTests implements FabricGameTest {
	private static Item item(String id) {
		return QaCarvedChest1115eCompatTests.block(id).asItem();
	}

	/** The pack's setup: Carved Wood's special recipe is loaded, the stand-in is read, and no plain recipe makes a chest. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void carvedWoodsChestRecipeHasAStandIn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(RecipeStandIns.all().stream().anyMatch(s -> s.serializer().toString().equals("carved_wood:chest_from_modded_chests")),
			"the shipped stand-in for Carved Wood's chest wasn't read: " + RecipeStandIns.all());
		List<String> ways = Crafting.recipesFor(io.github.jcondedata.aliveworkplace.mc.Recipes.manager(level), level.registryAccess(),
			Crafting.Kind.CRAFTING, Items.CHEST).stream().map(h -> h.id().toString()).toList();
		helper.assertTrue(ways.equals(List.of("aliveworkplace:recipe_stand_ins/carved_wood_chest")),
			"the ways to make a plain chest with Carved Wood should be the stand-in alone (its planks recipe is off): " + ways);
		helper.succeed();
	}

	/** 8 oak planks: the Carpenter's plan is a player's, the Oak Chest of Carved Wood, then the plain chest from it. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aPlainChestIsPlannedFromOakPlanksThroughCarvedWoodsOakChest(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Item carved = item("carved_wood:oak_chest");
		for (Crafting.Kind kind : List.of(Crafting.Kind.CRAFTING, Crafting.Kind.CRAFTER)) {
			Crafting.Plan plan = Crafting.chain(level, kind, Items.CHEST, 1, Map.of(Items.OAK_PLANKS, 8L));
			helper.assertTrue(plan != null && plan.takes().equals(Map.of(Items.OAK_PLANKS, 8)), kind + ": a chest from 8 oak planks: " + plan);
			List<Item> outs = plan.steps().stream().map(s -> s.out().getItem()).toList();
			helper.assertTrue(outs.equals(List.of(carved, Items.CHEST)), kind + ": the steps should be the Oak Chest, then the chest: " + outs);
		}
		helper.assertTrue(Crafting.chain(level, Crafting.Kind.CRAFTING, Items.CHEST, 1, Map.of(Items.OAK_PLANKS, 7L)) == null,
			"a chest planned from 7 oak planks");
		helper.succeed();
	}

	/** One carved chest of any wood makes a plain one; a carved trapped chest doesn't (Carved Wood's recipe refuses it). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aCarvedChestOfAnyWoodButNotATrappedOneMakesAPlainChest(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Item spruce = item("carved_wood:spruce_chest");
		Crafting.Plan plan = Crafting.plan(level, Crafting.Kind.CRAFTING, Items.CHEST, 2, Map.of(spruce, 2L));
		helper.assertTrue(plan != null && plan.takes().equals(Map.of(spruce, 2)) && plan.steps().size() == 1,
			"two chests from two carved spruce chests, in one step: " + plan);
		Item trapped = item("carved_wood:trapped_spruce_chest");
		helper.assertTrue(Crafting.plan(level, Crafting.Kind.CRAFTING, Items.CHEST, 1, Map.of(trapped, 1L)) == null,
			"a plain chest planned from a carved trapped chest");
		helper.assertTrue(Crafting.plan(level, Crafting.Kind.CRAFTING, Items.CHEST, 1, Map.of(Items.TRAPPED_CHEST, 1L)) == null,
			"a plain chest planned from a vanilla trapped chest");
		helper.succeed();
	}

	/** B1286: a chest from nothing names what a player gathers (logs), never nothing and never a carved chest. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aChestFromNothingNamesLogs(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (Crafting.Kind kind : List.of(Crafting.Kind.CRAFTING, Crafting.Kind.WORKSHOP)) {
			Map<Item, Integer> lack = Crafting.lacking(level, kind, Items.CHEST, 1, Map.of());
			helper.assertTrue(!lack.isEmpty() && lack.keySet().stream().allMatch(i -> i.builtInRegistryHolder().is(net.minecraft.tags.ItemTags.LOGS)),
				kind + ": a chest from nothing should lack logs: " + lack.entrySet().stream()
					.map(e -> e.getValue() + " " + BuiltInRegistries.ITEM.getKey(e.getKey())).toList());
		}
		helper.succeed();
	}
}
