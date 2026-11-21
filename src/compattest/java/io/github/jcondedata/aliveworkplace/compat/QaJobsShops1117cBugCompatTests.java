package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.ArrayList;
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
 * QA (lane-c-1117-1253), B1347's neighbour: with CobbleDollars, every sum of money the mod writes goes through
 * Money.describe (the Village Hall's treasury, a quest's reward, a retraining price, the family tree's price, the cup's
 * purse). At a rate of 1 CobbleDollar an emerald, one emerald's worth reads "1 CobbleDollars": the unit stays plural for
 * one, as the treasury hint did before B1347. Expected "1 CobbleDollar", and "2 CobbleDollars" from 2 up.
 *
 * <p>And B1290's neighbour, with Chipped and Carved Wood: Carved Wood's mixed crafting table recipe makes Minecraft's
 * crafting table from any 2x2 of planks no other recipe takes, mixed kinds included (2 Chipped mosaic planks and 2 oak,
 * 2 oak and 2 acacia), but the planner reads it only as four of one kind: with such a mix the line reads "short of 2 Oak
 * Planks" and no crafter makes the table a player makes from those four planks.
 */
public class QaJobsShops1117cBugCompatTests implements FabricGameTest {
	/** A sum of 1, 2 and 1,000 CobbleDollars, each in its number. */
	//$ gametest_ticks EMPTY_STRUCTURE '20'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void oneCobbleDollarReadsInTheSingular(GameTestHelper helper) {
		helper.assertTrue(Money.cobbleDollars(), "CobbleDollars isn't installed here; the test proves nothing");
		helper.assertValueEqual(Money.describe(2, 2).getString(), "2 CobbleDollars", "a sum of 2");
		helper.assertValueEqual(Money.describe(1_000, 10).getString(), "1,000 CobbleDollars", "a sum of 1,000");
		helper.assertValueEqual(Money.describe(1, 1).getString(), "1 CobbleDollar", "a sum of 1 (one emerald's worth at a rate of 1)");
		helper.succeed();
	}

	private static List<Item> chippedPlanks() {
		List<Item> out = new ArrayList<>();
		for (Holder<Item> h : BuiltInRegistries.ITEM.getTagOrEmpty(ItemTags.PLANKS)) {
			if (BuiltInRegistries.ITEM.getKey(h.value()).getNamespace().equals("chipped")) {
				out.add(h.value());
			}
		}
		if (out.size() < 2) {
			throw new GameTestAssertException("setup: fewer than two Chipped planks in the planks tag");
		}
		return out;
	}

	private static String id(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	/** What the game's own crafting table makes of these four in a 2x2 grid (empty when nothing). */
	private static ItemStack gameMakes(ServerLevel level, Item... four) {
		List<ItemStack> grid = new ArrayList<>();
		for (Item i : four) {
			grid.add(new ItemStack(i));
		}
		CraftingInput input = CraftingInput.of(2, 2, grid);
		Optional<RecipeHolder<CraftingRecipe>> recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
		return recipe.map(r -> r.value().assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY);
	}

	/**
	 * The game's table decides: for four planks (one Chipped kind, two Chipped kinds, Chipped beside oak, two vanilla
	 * woods, oak alone, acacia alone), when the game makes Minecraft's crafting table from them, the planner lacks
	 * nothing for one from those four; when it doesn't (four acacia make Carved Wood's own table), the planner lacks
	 * something. Each mix on a fresh tick of planning.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '100' '"qaJobsShops1117cTableMixes"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100, batch = "qaJobsShops1117cTableMixes")
	public void planksMakeACraftingTableExactlyWhenTheGamesTableDoes(GameTestHelper helper) {
		helper.runAfterDelay(1, () -> {
			ServerLevel level = helper.getLevel();
			List<Item> chipped = chippedPlanks();
			Item a = chipped.get(0);
			Item b = chipped.get(chipped.size() - 1);
			List<Item[]> mixes = List.of(
				new Item[] {a, a, a, a},
				new Item[] {a, a, b, b},
				new Item[] {a, a, a, Items.OAK_PLANKS},
				new Item[] {a, a, Items.OAK_PLANKS, Items.OAK_PLANKS},
				new Item[] {Items.OAK_PLANKS, Items.OAK_PLANKS, Items.ACACIA_PLANKS, Items.ACACIA_PLANKS},
				new Item[] {Items.OAK_PLANKS, Items.OAK_PLANKS, Items.OAK_PLANKS, Items.OAK_PLANKS},
				new Item[] {Items.ACACIA_PLANKS, Items.ACACIA_PLANKS, Items.ACACIA_PLANKS, Items.ACACIA_PLANKS});
			List<String> wrong = new ArrayList<>();
			List<String> seen = new ArrayList<>();
			int tables = 0;
			for (Item[] mix : mixes) {
				Map<Item, Long> chest = new java.util.LinkedHashMap<>();
				for (Item i : mix) {
					chest.merge(i, 1L, Long::sum);
				}
				boolean table = gameMakes(level, mix).is(Items.CRAFTING_TABLE);
				tables += table ? 1 : 0;
				Crafting.freshPlanning(level);
				Map<Item, Integer> lack = Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.CRAFTING_TABLE, 1, chest);
				String what = chest.entrySet().stream().map(e -> e.getValue() + " " + id(e.getKey())).toList() + " -> game: "
					+ (table ? "crafting table" : id(gameMakes(level, mix).getItem())) + ", planner lacks " + lack;
				seen.add(what);
				if (table != lack.isEmpty()) {
					wrong.add(what);
				}
			}
			helper.assertTrue(tables >= 3, "setup: the game's table made Minecraft's crafting table from only " + tables + " mixes: " + seen);
			helper.assertTrue(wrong.isEmpty(), "the planner and the game's table disagree: " + wrong + " (all: " + seen + ")");
			helper.succeed();
		});
	}

}
