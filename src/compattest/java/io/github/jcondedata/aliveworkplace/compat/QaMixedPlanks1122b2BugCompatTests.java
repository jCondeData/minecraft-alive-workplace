package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.test.FreshPlanning;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * QA (lane-b-1122-2138), B1378's neighbour with Chipped and Carved Wood: a builder's chest holds 3 planks of a mix
 * (2 Chipped acacia mosaic + 1 oak; or 1 oak, 1 acacia, 1 birch). One more oak plank makes Minecraft's crafting table at
 * the game's own table (Carved Wood's mixed recipe; checked first), so he is one plank short. The planner says short of
 * 3 Oak Planks: it ignores the mixed planks already there when it counts what's missing. Expected (B1293's rule, a
 * shortage counts the whole tag): short of 1 plank, as with 3 oak planks.
 */
public class QaMixedPlanks1122b2BugCompatTests implements FreshPlanning {
	private static Item chipped() {
		for (Holder<Item> h : BuiltInRegistries.ITEM.getTagOrEmpty(ItemTags.PLANKS)) {
			if (BuiltInRegistries.ITEM.getKey(h.value()).getNamespace().equals("chipped")) {
				return h.value();
			}
		}
		throw new GameTestAssertException("setup: no Chipped planks in the planks tag");
	}

	private static ItemStack gameMakes(ServerLevel level, Item... four) {
		List<ItemStack> grid = new ArrayList<>();
		for (Item i : four) {
			grid.add(new ItemStack(i));
		}
		CraftingInput input = CraftingInput.of(2, 2, grid);
		Optional<RecipeHolder<CraftingRecipe>> recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
		return recipe.map(r -> r.value().assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY);
	}

	/** Three mixed planks, one plank short of a crafting table at the game's table: the planner should lack exactly 1 plank. */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '100' '"qaMixedPlanks1122b2BugShort"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100, batch = "qaMixedPlanks1122b2BugShort")
	public void qaThreeMixedPlanksAreOnePlankShortNotThreeOak(GameTestHelper helper) {
		helper.runAfterDelay(1, () -> {
			ServerLevel level = helper.getLevel();
			Item mosaic = chipped();
			List<Item[]> threes = List.of(
				new Item[] {mosaic, mosaic, Items.OAK_PLANKS},
				new Item[] {Items.OAK_PLANKS, Items.ACACIA_PLANKS, Items.BIRCH_PLANKS});
			List<String> wrong = new ArrayList<>();
			for (Item[] three : threes) {
				helper.assertTrue(gameMakes(level, three[0], three[1], three[2], Items.OAK_PLANKS).is(Items.CRAFTING_TABLE),
					"setup: the game's table doesn't make a crafting table of " + List.of(three) + " and one more oak plank");
				Map<Item, Long> chest = new LinkedHashMap<>();
				for (Item i : three) {
					chest.merge(i, 1L, Long::sum);
				}
				Crafting.freshPlanning(level);
				Map<Item, Integer> lack = Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.CRAFTING_TABLE, 1, chest);
				int n = lack.values().stream().mapToInt(Integer::intValue).sum();
				if (n != 1) {
					wrong.add(chest + " lacks " + lack);
				}
			}
			helper.assertTrue(wrong.isEmpty(), "one plank short of a crafting table (one more oak plank makes it at the game's table), the planner says: " + wrong);
			helper.succeed();
		});
	}
}
