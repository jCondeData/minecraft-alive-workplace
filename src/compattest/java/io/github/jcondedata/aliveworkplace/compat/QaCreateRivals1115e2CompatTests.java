package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.test.FreshPlanning;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * QA (lane E, 2026-11-15, second run), Create loaded: B1267/B1268's fix past its own tests. The spec (crafter.md, B408,
 * B489, B1267, B1268): a job's line names the raw thing a player gathers, never Create's crushed ores, zinc, flour or
 * cardboard; "of two ways that fit as well, the one from the thing's own mod comes first (iron ingots, chains and bread by
 * Minecraft's own recipes)". The other side: preferring a way must never stop a job using a Create way the chests already
 * hold, and an item that is Create's own still names the ore a player mines.
 */
public class QaCreateRivals1115e2CompatTests implements FreshPlanning {

	/** Bread at the kitchen, a book and an item frame at the table, chains at the forge and the table: no Create item named. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void minecraftsOwnThingsNeverNameACreateItem(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<String, String> wrong = new LinkedHashMap<>();
		Map<Item, Integer> bread = Crafting.lacking(level, Crafting.Kind.KITCHEN, Items.BREAD, 2, Map.of());
		noCreate(wrong, "2 bread at the kitchen from nothing", bread);
		if (!bread.containsKey(Items.WHEAT)) {
			wrong.put("2 bread at the kitchen from nothing names the wheat", "lacks " + bread);
		}
		noCreate(wrong, "a book at the table from nothing", Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.BOOK, 1, Map.of()));
		noCreate(wrong, "an item frame at the table from nothing", Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.ITEM_FRAME, 1, Map.of()));
		Map<Item, Integer> forge = Crafting.lacking(level, Crafting.Kind.WORKSHOP, Items.CHAIN, 4, Map.of());
		noCreate(wrong, "4 chains at the forge from nothing", forge);
		if (!forge.containsKey(Items.RAW_IRON)) {
			wrong.put("4 chains at the forge from nothing names the raw iron", "lacks " + forge);
		}
		noCreate(wrong, "4 chains at the table from nothing", Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.CHAIN, 4, Map.of()));
		noCreate(wrong, "a lantern at the forge from 1 raw iron", Crafting.lacking(level, Crafting.Kind.WORKSHOP, Items.LANTERN, 1,
			Map.of(Items.RAW_IRON, 1L)));
		helper.assertTrue(wrong.isEmpty(), "a Create item named short: " + wrong);
		helper.succeed();
	}

	/** Create's own zinc with some raw zinc in the chests: the forge names only the Raw Zinc still short (the from-nothing case is B-filed). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void zincWithSomeRawZincNamesTheRestOfTheRawZinc(GameTestHelper helper) {
		Item rawZinc = create("raw_zinc");
		Map<Item, Integer> fromOne = Crafting.lacking(helper.getLevel(), Crafting.Kind.WORKSHOP, create("zinc_ingot"), 3, Map.of(rawZinc, 1L));
		helper.assertTrue(fromOne.equals(Map.of(rawZinc, 2)), "3 zinc ingots from 1 raw zinc lack " + fromOne + ", expected {raw_zinc=2}");
		Map<Item, Integer> enough = Crafting.lacking(helper.getLevel(), Crafting.Kind.WORKSHOP, create("zinc_ingot"), 3, Map.of(rawZinc, 3L));
		helper.assertTrue(enough.isEmpty(), "3 zinc ingots from 3 raw zinc lack " + enough + ", expected nothing");
		helper.succeed();
	}

	/** The other side: what the chests hold by Create's way is used, never reported short. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aCreateWayTheChestsHoldIsStillUsed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<String, String> wrong = new LinkedHashMap<>();
		Map<Item, Integer> bars = Crafting.lacking(level, Crafting.Kind.WORKSHOP, Items.IRON_BARS, 16, Map.of(create("crushed_raw_iron"), 6L));
		if (!bars.isEmpty()) {
			wrong.put("16 iron bars from 6 Crushed Raw Iron", "lacks " + bars + ", expected nothing (Create fires crushed ore into ingots)");
		}
		Map<Item, Integer> chains = Crafting.lacking(level, Crafting.Kind.WORKSHOP, Items.CHAIN, 4,
			Map.of(create("zinc_ingot"), 4L, create("zinc_nugget"), 8L));
		if (!chains.isEmpty()) {
			wrong.put("4 chains from 4 zinc ingots and 8 zinc nuggets", "lacks " + chains + ", expected nothing (Create's chain from zinc)");
		}
		Map<Item, Integer> ingots = Crafting.lacking(level, Crafting.Kind.WORKSHOP, Items.IRON_INGOT, 3, Map.of(create("crushed_raw_iron"), 3L));
		if (!ingots.isEmpty()) {
			wrong.put("3 iron ingots from 3 Crushed Raw Iron", "lacks " + ingots + ", expected nothing");
		}
		helper.assertTrue(wrong.isEmpty(), "a Create way the chests hold reported short: " + wrong);
		helper.succeed();
	}

	/**
	 * The job's own plan (strict, B1268's give-up on what nothing in the chests leads to): from Create's crushed ore or zinc
	 * in the chests he has a plan; from raw iron too; from nothing, none.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theJobsPlanUsesACreateWayTheChestsHold(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<String, String> wrong = new LinkedHashMap<>();
		if (Crafting.chain(level, Crafting.Kind.WORKSHOP, Items.IRON_BARS, 16, Map.of(create("crushed_raw_iron"), 6L, Items.COAL, 2L)) == null) {
			wrong.put("16 iron bars from 6 Crushed Raw Iron and 2 coal", "no plan");
		}
		if (Crafting.chain(level, Crafting.Kind.WORKSHOP, Items.CHAIN, 4, Map.of(create("zinc_ingot"), 4L, create("zinc_nugget"), 8L)) == null) {
			wrong.put("4 chains from 4 zinc ingots and 8 zinc nuggets", "no plan");
		}
		if (Crafting.chain(level, Crafting.Kind.WORKSHOP, Items.IRON_BARS, 16, Map.of(Items.RAW_IRON, 6L, Items.COAL, 2L)) == null) {
			wrong.put("16 iron bars from 6 raw iron and 2 coal", "no plan");
		}
		if (Crafting.chain(level, Crafting.Kind.WORKSHOP, Items.IRON_BARS, 16, Map.of()) != null) {
			wrong.put("16 iron bars from nothing", "a plan");
		}
		helper.assertTrue(wrong.isEmpty(), "the forge's plan: " + wrong);
		helper.succeed();
	}

	private static void noCreate(Map<String, String> wrong, String what, Map<Item, Integer> lacks) {
		for (Item item : lacks.keySet()) {
			if (BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("create")) {
				wrong.put(what, "lacks " + lacks);
				return;
			}
		}
	}

	private static Item create(String path) {
		Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", path));
		if (item == Items.AIR) {
			throw new GameTestAssertException("create:" + path + " isn't registered");
		}
		return item;
	}
}
