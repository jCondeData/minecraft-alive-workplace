package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.compat.QaCrafterOwnModdedStore1111eCompatTests.Asker;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.test.FreshPlanning;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-b-1116-1238), with the compat pack, written from B1308's spec and crafter.md: "Nothing is ever made of itself:
 * with Create, a stone a player mines (andesite, diorite, granite, tuff) is never short of 3 Andesite to make 3 Andesite;
 * it is cut from another form of it only when one is in the chest". Covers the other self-naming things B1308 listed
 * (Create's cut stones, table cloths, filters, stock link and ticker; Supplementaries' globe; Tom's framed cable;
 * Handcrafted's sheet), the one-form-short boundary (2 polished andesite for 3 andesite), and the Mason cutting andesite
 * from polished andesite through the builder's own request.
 */
public class QaShortageLine1116bCompatTests implements FreshPlanning {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	/** Every thing B1308 named as reading itself short of itself; ids absent from the pack are skipped (and listed). */
	private static final List<String> SELF_NAMERS = List.of("create:cut_asurine", "create:cut_crimsite", "create:cut_limestone", "create:cut_ochrum",
		"create:cut_scorchia", "create:cut_scoria", "create:cut_veridium", "create:white_table_cloth", "create:black_table_cloth", "create:filter",
		"create:attribute_filter", "create:stock_link", "create:stock_ticker", "supplementaries:globe", "toms_storage:ts.inventory_cable_framed",
		"handcrafted:black_sheet", "minecraft:andesite", "minecraft:diorite", "minecraft:granite", "minecraft:tuff");

	/**
	 * From nothing, by the stonecutter, the crafting table and every job's recipe types: none of them names itself. One
	 * item a tick, so the tick's planning budget never decides the answer.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '400' '"qaShortLine1116bSelf"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400, batch = "qaShortLine1116bSelf")
	public void nothingInTheB1308ListNamesItselfFromNothing(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		List<String> absent = new ArrayList<>();
		List<Item> items = new ArrayList<>();
		for (String id : SELF_NAMERS) {
			BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).ifPresentOrElse(items::add, () -> absent.add(id));
		}
		int tick = 1;
		for (Item item : items) {
			for (int way = 0; way < 3; way++) {
				int w = way;
				helper.runAfterDelay(tick, () -> {
					Map<Item, Integer> lack = switch (w) {
						case 0 -> Crafting.lacking(helper.getLevel(), Crafting.Kind.STONECUTTING, item, 4, Map.of());
						case 1 -> Crafting.lacking(helper.getLevel(), Crafting.Kind.CRAFTING, item, 4, Map.of());
						default -> Crafting.lacking(helper.getLevel(), item, 4, Map.of(), t -> true);
					};
					if (lack.containsKey(item)) {
						wrong.add(id(item) + " (" + List.of("stonecutting", "crafting", "every type").get(w) + "): '"
							+ JobShortages.lackLine(lack, 4, item).getString() + "'");
					}
				});
				tick += 2;
			}
		}
		helper.runAfterDelay(tick + 1, () -> {
			helper.assertTrue(items.size() >= 11, "too few of B1308's items in the pack to test (" + items.size() + "), absent: " + absent);
			helper.assertTrue(wrong.isEmpty(), "things naming themselves (absent, not tested: " + absent + "): " + wrong);
			helper.succeed();
		});
	}

	/**
	 * The boundary of the rule: with 2 polished andesite in the chest (another form is there, so cutting is allowed) for 3
	 * andesite, the line still never reads "Andesite to make 3 Andesite"; with 1 polished andesite for 1 andesite neither.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '100' '"qaShortLine1116bPolished"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100, batch = "qaShortLine1116bPolished")
	public void withAnotherFormInTheChestTheStoneStillNeverNamesItself(GameTestHelper helper) {
		AtomicReference<String> first = new AtomicReference<>();
		helper.runAfterDelay(1, () -> {
			Map<Item, Integer> two = Crafting.lacking(helper.getLevel(), Crafting.Kind.STONECUTTING, Items.ANDESITE, 3, Map.of(Items.POLISHED_ANDESITE, 2L));
			first.set(two.containsKey(Items.ANDESITE) ? "2 polished andesite for 3 andesite read '" + JobShortages.lackLine(two, 3, Items.ANDESITE).getString()
				+ "'" : null);
		});
		helper.runAfterDelay(3, () -> {
			Map<Item, Integer> one = Crafting.lacking(helper.getLevel(), Crafting.Kind.STONECUTTING, Items.ANDESITE, 2, Map.of(Items.POLISHED_DIORITE, 1L,
				Items.POLISHED_ANDESITE, 1L));
			helper.assertTrue(first.get() == null, first.get() + ": andesite named as its own making");
			helper.assertTrue(!one.containsKey(Items.ANDESITE), "1 polished andesite and 1 polished diorite for 2 andesite read '"
				+ JobShortages.lackLine(one, 2, Items.ANDESITE).getString() + "': andesite named as its own making");
			helper.succeed();
		});
	}

	/** Through the player's entry point, from an empty chest: a builder short of 3 Tuff never reads "Tuff to make 3 Tuff". */
	//$ gametest_ticks_batch AREA '1400' '"qaShortLine1116bTuff"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaShortLine1116bTuff")
	public void aBuilderShortOfTuffIsNotToldTuffMakesIt(GameTestHelper helper) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.TUFF, 3, 1,
			"qa1116btuff"), Blocks.CHEST.defaultBlockState(), new ItemStack[0]);
		BlockPos station = QaCrafterOwnModdedStore1111eCompatTests.STATION;
		helper.setBlock(station, Blocks.STONECUTTER);
		Villager mason = helper.spawn(EntityType.VILLAGER, station.south());
		Jobs.employ(helper.getLevel(), mason, helper.absolutePos(station), PoiTypes.MASON, VillagerProfession.MASON);
		helper.runAfterDelay(1200, () -> {
			String text = QaCrafterOwnModdedStore1111eCompatTests.line(helper, a);
			helper.assertTrue(text == null || !text.contains("Tuff to make"), "the builder's line: '" + text + "', a Mason can't make tuff out of tuff");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(mason, 0) == 0, "the Mason made something from an empty chest");
			helper.succeed();
		});
	}

	private static String id(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}
}
