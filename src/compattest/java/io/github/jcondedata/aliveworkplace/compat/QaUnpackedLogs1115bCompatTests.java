package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.compat.QaCrafterOwnModdedStore1111eCompatTests.Asker;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.test.FreshPlanning;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-b-1115-2137) past B1288's fix (a log only unpacked from Carved Wood's bundle is raw to the shortage line),
 * written from crafter.md: "The line names what the chest is short of ... It goes further down only when that uses
 * something that's there" and "of an ingredient's options the base game's come first". Boundaries from both sides: 3
 * logs there of the recipe's 4 (1 short, not 4), another mod's log the tag takes counts, the bundles themselves in the
 * chest are used (as a slime block is for a slimeball; Carved Wood packs 4 logs into 4 bundles, each unpacks into 1), and a campfire's 3 logs; then the bundle through a builder's request.
 */
public class QaUnpackedLogs1115bCompatTests implements FreshPlanning {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	/** The one-ingredient crafting recipe that makes oak logs (Carved Wood's bundle unpacked), or null. */
	static Item oakBundle(ServerLevel level) {
		for (RecipeHolder<CraftingRecipe> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
			CraftingRecipe recipe = holder.value();
			if (recipe.getResultItem(level.registryAccess()).getItem() != Items.OAK_LOG) {
				continue;
			}
			List<Ingredient> used = recipe.getIngredients().stream().filter(i -> !i.isEmpty()).toList();
			if (used.size() == 1 && used.get(0).getItems().length > 0) {
				Item in = used.get(0).getItems()[0].getItem();
				if (in != Items.OAK_LOG && !in.builtInRegistryHolder().is(ItemTags.LOGS)) {
					return in;
				}
			}
		}
		return null;
	}

	/** Smokers and a campfire with some of their logs there, another mod's logs, or the oak log bundle. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaUnpackedLogs1115bLogic"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaUnpackedLogs1115bLogic")
	public void someLogsThereAndTheBundleCount(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> wrong = new ArrayList<>();
		Item bundle = oakBundle(level);
		helper.assertTrue(bundle != null, "setup: no recipe unpacks a bundle into oak logs (Carved Wood's log bundles off?)");
		Item apricorn = BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobblemon", "apricorn_log"));
		for (Crafting.Kind kind : List.of(Crafting.Kind.CRAFTING, Crafting.Kind.WORKSHOP)) {
			Map<Item, Integer> three = Crafting.lacking(level, kind, Items.SMOKER, 1, Map.of(Items.FURNACE, 1L, Items.OAK_LOG, 3L));
			if (logs(three) != 1 || foreign(three)) {
				wrong.add(kind + " a smoker from a furnace and 3 oak logs lacks " + three + ", expected 1 of Minecraft's logs");
			}
			Map<Item, Integer> four = Crafting.lacking(level, kind, Items.SMOKER, 1, Map.of(Items.FURNACE, 1L, Items.OAK_LOG, 4L));
			if (!four.isEmpty()) {
				wrong.add(kind + " a smoker from a furnace and 4 oak logs lacks " + four + ", expected nothing");
			}
			if (apricorn != Items.AIR && apricorn.builtInRegistryHolder().is(ItemTags.LOGS)) {
				Map<Item, Integer> other = Crafting.lacking(level, kind, Items.SMOKER, 1, Map.of(Items.FURNACE, 1L, apricorn, 4L));
				if (!other.isEmpty()) {
					wrong.add(kind + " a smoker from a furnace and 4 Apricorn Logs (in #logs) lacks " + other + ", expected nothing");
				}
			}
			// Carved Wood: 4 oak logs make 4 bundles, 1 bundle unpacks into 1 oak log
			Map<Item, Integer> unpacked = Crafting.lacking(level, kind, Items.SMOKER, 1, Map.of(Items.FURNACE, 1L, bundle, 4L));
			if (!unpacked.isEmpty()) {
				wrong.add(kind + " a smoker from a furnace and 4 " + BuiltInRegistries.ITEM.getKey(bundle) + " lacks " + unpacked + ", expected nothing (each unpacks into an oak log)");
			}
			Map<Item, Integer> fire = Crafting.lacking(level, kind, Items.CAMPFIRE, 1, Map.of(Items.STICK, 3L, Items.COAL, 1L));
			if (logs(fire) != 3 || fire.size() != 1 || foreign(fire)) {
				wrong.add(kind + " a campfire from 3 sticks and a coal lacks " + fire + ", expected 3 of Minecraft's logs");
			}
		}
		helper.assertTrue(wrong.isEmpty(), "logs with the pack: " + wrong);
		helper.succeed();
	}

	/**
	 * Through the player's entry point: a builder short of 1 Smoker with a furnace and 4 of Carved Wood's oak log bundles
	 * (4 oak logs packed) in his chest and a Carpenter at his bench: the smoker is made (the bundles unpacked into logs), never a line naming logs.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaUnpackedLogs1115bLine"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaUnpackedLogs1115bLine")
	public void aBuilderWithTheBundleGetsHisSmoker(GameTestHelper helper) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		Item bundle = oakBundle(helper.getLevel());
		helper.assertTrue(bundle != null, "setup: no recipe unpacks a bundle into oak logs");
		ItemStack[] items = {new ItemStack(Items.FURNACE, 1), new ItemStack(bundle, 4)};
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.SMOKER, 1, 1,
			"qa1115bbundle"), Blocks.CHEST.defaultBlockState(), QaCreateRivals1115cCompatTests.copies(items));
		BlockPos station = QaCrafterOwnModdedStore1111eCompatTests.STATION;
		helper.setBlock(station, ModBlocks.CARPENTERS_BENCH);
		Villager carpenter = helper.spawn(EntityType.VILLAGER, station.south());
		Jobs.employ(helper.getLevel(), carpenter, helper.absolutePos(station), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		helper.onEachTick(() -> {
			String text = QaCrafterOwnModdedStore1111eCompatTests.line(helper, a);
			helper.assertTrue(text == null || !text.startsWith("Can't be made yet"), "the builder's line with the bundle there: '" + text + "'; site "
				+ QaCreateRivals1115cCompatTests.site(helper, a));
		});
		helper.runAfterDelay(2200, () -> {
			helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(6, 2, 8))).is(Blocks.SMOKER),
				"no smoker built in 2200 ticks; site " + QaCreateRivals1115cCompatTests.site(helper, a));
			helper.succeed();
		});
	}

	private static int logs(Map<Item, Integer> lack) {
		return lack.entrySet().stream().filter(e -> e.getKey().builtInRegistryHolder().is(ItemTags.LOGS)).mapToInt(Map.Entry::getValue).sum();
	}

	private static boolean foreign(Map<Item, Integer> lack) {
		return lack.keySet().stream().anyMatch(i -> !"minecraft".equals(BuiltInRegistries.ITEM.getKey(i).getNamespace()));
	}
}
