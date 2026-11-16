package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.compat.QaCrafterOwnModdedStore1111eCompatTests.Asker;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * B1273 (crafter.md, B408/B489: the line names what a player gathers): with Chipped loaded the planks tag also holds
 * Chipped's decorative planks, which no crafting recipe makes, and the line for a lantern's torch's sticks named
 * chipped:acacia_planks_mosaic. It must name the base game's wood (or bamboo), never a Chipped item.
 */
public class ChippedShortLineCompatTests implements FabricGameTest {
	private static final String AREA = QaCreateRivals1115cCompatTests.AREA;

	/** Setup and logic: the planks tag holds Chipped planks, and a lantern, torch, sticks or piston from nothing names none. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void fromNothingTheLineNamesNoChippedPlanks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean chippedPlanks = false;
		for (Holder<Item> h : BuiltInRegistries.ITEM.getTagOrEmpty(ItemTags.PLANKS)) {
			chippedPlanks |= BuiltInRegistries.ITEM.getKey(h.value()).getNamespace().equals("chipped");
		}
		helper.assertTrue(chippedPlanks, "setup: no Chipped planks in the planks tag");
		List<String> wrong = new java.util.ArrayList<>();
		for (Item item : List.of(Items.LANTERN, Items.TORCH, Items.STICK, Items.PISTON, Items.CHEST)) {
			for (Crafting.Kind kind : List.of(Crafting.Kind.WORKSHOP, Crafting.Kind.CRAFTING)) {
				Map<Item, Integer> lack = Crafting.lacking(level, kind, item, 1, Map.of());
				if (lack.isEmpty() || lack.keySet().stream().anyMatch(i -> BuiltInRegistries.ITEM.getKey(i).getNamespace().equals("chipped"))) {
					wrong.add(item + " (" + kind + ") lacks " + lack);
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "from nothing, nothing named or a Chipped item named: " + wrong);
		helper.succeed();
	}

	/** Through the player's entry point: a builder short of a lantern with nothing but a coal: the line names no Chipped planks. */
	//$ gametest_ticks_batch AREA '1400' '"chippedShortLineLantern"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "chippedShortLineLantern")
	public void aLanternShortNamesNoChippedPlanks(GameTestHelper helper) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		ItemStack[] items = {new ItemStack(Items.COAL, 1)};
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.LANTERN, 1, 1, "chiplantern"),
			Blocks.CHEST.defaultBlockState(), QaCreateRivals1115cCompatTests.copies(items));
		Villager t = QaCreateRivals1115cCompatTests.tinkerer(helper);
		helper.runAfterDelay(1200, () -> {
			String text = QaCrafterOwnModdedStore1111eCompatTests.line(helper, a);
			helper.assertTrue(text != null && text.contains("Raw Iron"), "the builder's line: '" + text + "', expected a line naming the Raw Iron; site "
				+ QaCreateRivals1115cCompatTests.site(helper, a));
			for (Holder<Item> h : BuiltInRegistries.ITEM.getTagOrEmpty(ItemTags.PLANKS)) {
				if (BuiltInRegistries.ITEM.getKey(h.value()).getNamespace().equals("chipped")) {
					String name = h.value().getDefaultInstance().getHoverName().getString();
					helper.assertTrue(!text.contains(name), "the builder's line: '" + text + "' names Chipped's " + name);
				}
			}
			QaCreateRivals1115cCompatTests.tookNothing(helper, t, items);
			helper.succeed();
		});
	}

	/**
	 * B1290's premise checked: with Carved Wood's wooden bookshelves on and its mixed bookshelves off (its defaults, as in the
	 * pack), Minecraft's bookshelf is made of oak planks only (other planks make Carved Wood's own), so Chipped's acacia
	 * mosaic planks don't make one and the line names the oak logs; oak planks there make it, and a crafting table, with
	 * nothing short. B1288: a smoker from a furnace names 4 of Minecraft's logs, though Carved Wood's log bundles make oak logs too.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void withCarvedWoodABookshelfTakesOakPlanksNotChippedOnes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Item mosaic = BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("chipped", "acacia_planks_mosaic"));
		helper.assertTrue(mosaic != Items.AIR, "setup: no chipped:acacia_planks_mosaic");
		for (Item made : List.of(Items.BOOKSHELF)) {
			for (var holder : Crafting.recipesFor(level.getRecipeManager(), level.registryAccess(), Crafting.Kind.CRAFTING, made)) {
				for (var ingredient : io.github.jcondedata.aliveworkplace.mc.Recipes.ingredients(holder.value())) {
					helper.assertTrue(!Crafting.options(level, ingredient).contains(mosaic), "setup: " + holder.id() + " takes Chipped's mosaic planks (Carved Wood's switch off?)");
				}
			}
		}
		Crafting.Kind k = Crafting.Kind.CRAFTING;
		Map<Item, Integer> mosaicShelf = Crafting.lacking(level, k, Items.BOOKSHELF, 1, Map.of(mosaic, 6L, Items.BOOK, 3L));
		helper.assertTrue(mosaicShelf.equals(Map.of(Items.OAK_LOG, 2)), "a bookshelf from 6 mosaic planks and 3 books lacks " + mosaicShelf + ", expected {minecraft:oak_log=2}");
		Map<Item, Integer> oakShelf = Crafting.lacking(level, k, Items.BOOKSHELF, 1, Map.of(Items.OAK_PLANKS, 6L, Items.BOOK, 3L));
		helper.assertTrue(oakShelf.isEmpty(), "a bookshelf from 6 oak planks and 3 books lacks " + oakShelf);
		Map<Item, Integer> oakTable = Crafting.lacking(level, k, Items.CRAFTING_TABLE, 1, Map.of(Items.OAK_PLANKS, 4L));
		helper.assertTrue(oakTable.isEmpty(), "a crafting table from 4 oak planks lacks " + oakTable);
		for (Crafting.Kind kind : List.of(Crafting.Kind.CRAFTING, Crafting.Kind.WORKSHOP)) {
			Map<Item, Integer> smoker = Crafting.lacking(level, kind, Items.SMOKER, 1, Map.of(Items.FURNACE, 1L));
			boolean oneBaseGameLog = smoker.size() == 1 && smoker.values().iterator().next() == 4 && smoker.keySet().stream()
				.allMatch(i -> i.builtInRegistryHolder().is(ItemTags.LOGS) && BuiltInRegistries.ITEM.getKey(i).getNamespace().equals("minecraft"));
			helper.assertTrue(oneBaseGameLog, "a smoker from a furnace (" + kind + ") lacks " + smoker + ", expected 4 of one of Minecraft's logs");
		}
		helper.succeed();
	}
}
