package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.compat.QaCrafterOwnModdedStore1111eCompatTests.Asker;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateRivals1115cCompatTests.create;

/**
 * QA past B1273, B1277 and B1279 with the pack's mods (lane-c-1115-1252), from crafter.md: "Of an ingredient's options the
 * base game's come first: with Chipped loaded a torch's sticks go down to oak logs or bamboo, never Chipped's decorative
 * planks"; "a sticky piston names the Slimeball, never Create's Dough and Wheat Flour"; "of Create's two ways to its zinc,
 * the one short of the Raw Zinc a player mines, never the Crushed Raw Zinc"; and "It goes further down only when that uses
 * something that's there" (what the chests hold, Chipped's planks or Create's crushed zinc, is still used).
 */
public class QaShortLineGathered1115c4CompatTests implements FabricGameTest {
	private static final String AREA = QaCreateRivals1115cCompatTests.AREA;

	/** Other things made of planks or sticks, from nothing at the table or forge: something named, never a Chipped, Create or Cobblemon item. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void otherPlankThingsFromNothingNameNoChippedItem(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> wrong = new ArrayList<>();
		for (Item item : List.of(Items.CRAFTING_TABLE, Items.BOOKSHELF, Items.BARREL, Items.LADDER, Items.BOWL, Items.WOODEN_PICKAXE, Items.ITEM_FRAME,
				Items.TRIPWIRE_HOOK, Items.STICKY_PISTON, Items.LEVER, Items.CHEST)) {
			for (Crafting.Kind kind : List.of(Crafting.Kind.WORKSHOP, Crafting.Kind.CRAFTING)) {
				Map<Item, Integer> lack = Crafting.lacking(level, kind, item, 1, Map.of());
				// a chest from nothing naming nothing with the pack is B1286, filed already: only its Chipped side is checked here
				if ((lack.isEmpty() && item != Items.CHEST) || lack.keySet().stream().anyMatch(i -> ns(i).equals("chipped")) || QaCreateRivals1115cCompatTests.foreign(lack)) {
					wrong.add(item + " (" + kind + ") lacks " + lack);
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "from nothing, nothing named or another mod's item named: " + wrong);
		helper.succeed();
	}

	/**
	 * Chipped's planks the chest does hold are still used for sticks and a torch's stick; one plank too few names that plank
	 * (B409: an option the chests hold some of). A crafting table or bookshelf from them is QaShortLineGathered1115c4BugCompatTests.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void chippedPlanksThereMakeSticksAndTorches(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Item chipped = null;
		for (Holder<Item> h : BuiltInRegistries.ITEM.getTagOrEmpty(ItemTags.PLANKS)) {
			if (ns(h.value()).equals("chipped")) {
				chipped = h.value();
				break;
			}
		}
		if (chipped == null) {
			throw new GameTestAssertException("setup: no Chipped planks in the planks tag");
		}
		Map<String, String> wrong = new LinkedHashMap<>();
		Crafting.Kind k = Crafting.Kind.CRAFTING;
		QaCreateRivals1115cCompatTests.check(wrong, "4 sticks from 2 " + chipped, Crafting.lacking(level, k, Items.STICK, 4, Map.of(chipped, 2L)), Map.of());
		QaCreateRivals1115cCompatTests.check(wrong, "a torch from 2 " + chipped + " and a coal", Crafting.lacking(level, k, Items.TORCH, 1,
			Map.of(chipped, 2L, Items.COAL, 1L)), Map.of());
		QaCreateRivals1115cCompatTests.check(wrong, "a torch from 1 " + chipped + " and a coal", Crafting.lacking(level, k, Items.TORCH, 1,
			Map.of(chipped, 1L, Items.COAL, 1L)), Map.of(chipped, 1));
		helper.assertTrue(wrong.isEmpty(), "Chipped planks there not used: " + wrong);
		helper.succeed();
	}

	/** Create's zinc from both sides: raw zinc or crushed raw zinc there is used; nuggets and blocks from nothing name Raw Zinc. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void zincFromBothSides(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Crafting.Kind k = Crafting.Kind.WORKSHOP;
		Item raw = create("raw_zinc");
		Item crushed = create("crushed_raw_zinc");
		Item ingot = create("zinc_ingot");
		Item nugget = create("zinc_nugget");
		Item block = create("zinc_block");
		Map<String, String> wrong = new LinkedHashMap<>();
		QaCreateRivals1115cCompatTests.check(wrong, "9 zinc nuggets from nothing", Crafting.lacking(level, k, nugget, 9, Map.of()), Map.of(raw, 1));
		QaCreateRivals1115cCompatTests.check(wrong, "2 zinc blocks from nothing", Crafting.lacking(level, k, block, 2, Map.of()), Map.of(raw, 18));
		QaCreateRivals1115cCompatTests.check(wrong, "2 zinc blocks from 9 raw zinc", Crafting.lacking(level, k, block, 2, Map.of(raw, 9L)), Map.of(raw, 9));
		QaCreateRivals1115cCompatTests.check(wrong, "3 zinc ingots from 3 raw zinc", Crafting.lacking(level, k, ingot, 3, Map.of(raw, 3L)), Map.of());
		QaCreateRivals1115cCompatTests.check(wrong, "3 zinc ingots from 3 crushed raw zinc", Crafting.lacking(level, k, ingot, 3, Map.of(crushed, 3L)), Map.of());
		QaCreateRivals1115cCompatTests.check(wrong, "a zinc block from 9 zinc ingots", Crafting.lacking(level, k, block, 1, Map.of(ingot, 9L)), Map.of());
		helper.assertTrue(wrong.isEmpty(), "wrong lines: " + wrong);
		helper.succeed();
	}

	/** With Create: a sticky piston with its slimeball there isn't short of one, and names no Create, Cobblemon or Chipped item. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aStickyPistonWithItsSlimeballThereNamesNoneOfIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<Item, Integer> lack = Crafting.lacking(level, Crafting.Kind.WORKSHOP, Items.STICKY_PISTON, 2, Map.of(Items.SLIME_BALL, 1L));
		helper.assertTrue(lack.getOrDefault(Items.SLIME_BALL, 0) == 1, "2 sticky pistons from 1 slimeball lack " + lack + ", expected 1 more Slimeball named");
		helper.assertTrue(!QaCreateRivals1115cCompatTests.foreign(lack) && lack.keySet().stream().noneMatch(i -> ns(i).equals("chipped")),
			"2 sticky pistons name another mod's item: " + lack);
		Map<Item, Integer> one = Crafting.lacking(level, Crafting.Kind.WORKSHOP, Items.STICKY_PISTON, 1, Map.of(Items.SLIME_BALL, 1L, Items.PISTON, 1L));
		helper.assertTrue(one.isEmpty(), "a sticky piston from a piston and a slimeball lacks " + one);
		helper.succeed();
	}

	/** Through the player's entry point: a builder short of a sticky piston, a slimeball and a coal there, a Tinkerer at his bench. */
	//$ gametest_ticks_batch AREA '1400' '"qaShortLineSticky1115c4"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaShortLineSticky1115c4")
	public void aStickyPistonWithItsSlimeballReadsNoForeignItem(GameTestHelper helper) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		ItemStack[] items = {new ItemStack(Items.SLIME_BALL, 1), new ItemStack(Items.COAL, 1)};
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.STICKY_PISTON, 1, 1, "qasticky4"),
			Blocks.CHEST.defaultBlockState(), QaCreateRivals1115cCompatTests.copies(items));
		Villager t = QaCreateRivals1115cCompatTests.tinkerer(helper);
		helper.runAfterDelay(1200, () -> {
			String text = QaCrafterOwnModdedStore1111eCompatTests.line(helper, a);
			helper.assertTrue(text != null && text.startsWith("Can't be made yet: short of ") && text.endsWith(" to make 1 Sticky Piston"),
				"the builder's line: '" + text + "'; site " + QaCreateRivals1115cCompatTests.site(helper, a));
			for (String bad : List.of("Slimeball", "Wheat Flour", "Dough", "Ore", "Mosaic", "Crushed")) {
				helper.assertTrue(!text.contains(bad), "the builder's line: '" + text + "' names " + bad);
			}
			helper.assertTrue(text.contains("Redstone Dust") && text.contains("Raw Iron"), "the builder's line: '" + text + "', expected Redstone Dust and Raw Iron");
			QaCreateRivals1115cCompatTests.tookNothing(helper, t, items);
			helper.succeed();
		});
	}

	private static String ns(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).getNamespace();
	}
}
