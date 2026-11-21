package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.compat.QaCrafterOwnModdedStore1111eCompatTests.Asker;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.test.FreshPlanning;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane C, 2026-11-15) for B1267 and B1268, written from the spec, not the code. With Create (and Cobblemon) loaded,
 * crafter.md (B408/B489/B1267/B1268): "it still names the raw metal a player mines: never Create's Crushed Raw Iron, which
 * only a crushing wheel makes, nor Cobblemon's plaques melted into nuggets; and 4 chains short read 'Raw Iron', never
 * Create's Zinc. Of two ways that fit as well, the one from the thing's own mod comes first (iron ingots, chains and bread
 * by Minecraft's own recipes)." And: "The line names what the chest is short of, not some other way to make it ... It goes
 * further down only when that uses something that's there."
 *
 * <p>Covers: exact raw-metal counts at the forge beyond the fix's own test; the builder's line through the player's entry point for a gold pressure plate, a copper block
 * and a lantern; the rivals B1268 lists beyond chains (Create's dough for bread at the Chef's kitchen, its cardboard for a
 * book and an item frame at the table, and a Librarian's bookshelves); the counter-cases (Create's own ingredients in the
 * chest are used: crushed raw iron for bars, zinc for chains, dough for bread); and a line always said when short. The bugs
 * this class found (a sticky piston naming Create's Wheat Flour, no leather naming Rabbit Hide) are
 * QaCreateRivals1115cBugCompatTests.
 */
public class QaCreateRivals1115cCompatTests implements FreshPlanning {
	static final String AREA = "aliveworkplace_compat:huge_area";
	static final String SMALL = CompatGameTests.AREA;
	static final BlockPos CHEST = new BlockPos(2, 2, 4);
	/** The Chef's stove (in SMALL, laid out as QaCampfirePotStockCompatTests). */
	static final BlockPos STOVE = new BlockPos(2, 2, 2);
	static final BlockPos CHEF_AT = new BlockPos(3, 2, 3);

	// ---- read off the recipes --------------------------------------------------------------------------------------

	/** Raw-metal counts beyond the fix's own test: bigger orders, gold and copper ingots and nuggets, the heavy plate, chains. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theForgeCountsTheRawMetalForEveryRival(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Crafting.Kind kind = Crafting.Kind.WORKSHOP;
		Map<String, String> wrong = new LinkedHashMap<>();
		check(wrong, "32 iron bars from nothing", Crafting.lacking(level, kind, Items.IRON_BARS, 32, Map.of()), Map.of(Items.RAW_IRON, 12));
		check(wrong, "18 iron nuggets from nothing", Crafting.lacking(level, kind, Items.IRON_NUGGET, 18, Map.of()), Map.of(Items.RAW_IRON, 2));
		check(wrong, "9 gold nuggets from nothing", Crafting.lacking(level, kind, Items.GOLD_NUGGET, 9, Map.of()), Map.of(Items.RAW_GOLD, 1));
		check(wrong, "an iron ingot from nothing", Crafting.lacking(level, kind, Items.IRON_INGOT, 1, Map.of()), Map.of(Items.RAW_IRON, 1));
		check(wrong, "a gold ingot from nothing", Crafting.lacking(level, kind, Items.GOLD_INGOT, 1, Map.of()), Map.of(Items.RAW_GOLD, 1));
		check(wrong, "a copper ingot from nothing", Crafting.lacking(level, kind, Items.COPPER_INGOT, 1, Map.of()), Map.of(Items.RAW_COPPER, 1));
		check(wrong, "a heavy pressure plate from nothing", Crafting.lacking(level, kind, Items.HEAVY_WEIGHTED_PRESSURE_PLATE, 1, Map.of()),
			Map.of(Items.RAW_IRON, 2));
		check(wrong, "2 gold pressure plates from 1 raw gold", Crafting.lacking(level, kind, Items.LIGHT_WEIGHTED_PRESSURE_PLATE, 2,
			Map.of(Items.RAW_GOLD, 1L)), Map.of(Items.RAW_GOLD, 3));
		// chains: the iron chain (Minecraft's own) over Create's zinc one, at the forge and at a plain crafting table
		for (int n : new int[] {1, 4}) {
			Map<Item, Integer> forge = Crafting.lacking(level, kind, Items.CHAIN, n, Map.of());
			if (!forge.keySet().equals(java.util.Set.of(Items.RAW_IRON))) {
				wrong.put(n + " chains at the forge from nothing", "lacks " + forge + ", expected only raw iron");
			}
		}
		Map<Item, Integer> table = Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.CHAIN, 4, Map.of());
		if (table.isEmpty() || !(table.containsKey(Items.IRON_INGOT) || table.containsKey(Items.IRON_NUGGET)) || foreign(table)) {
			wrong.put("4 chains at the table from nothing", "lacks " + table + ", expected iron (ingots or nuggets), no zinc");
		}
		helper.assertTrue(wrong.isEmpty(), "the forge names the wrong thing short: " + wrong);
		helper.succeed();
	}

	/**
	 * B1268's other rivals, read off the recipes: bread at the Chef's kitchen names the wheat (never Create's flour or dough);
	 * a book from 3 paper and an item frame from 8 sticks lack 1 Leather (never Create's Cardboard), and from nothing they
	 * name the leather and no Create item.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void breadBooksAndFramesNameWhatAPlayerGathers(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<String, String> wrong = new LinkedHashMap<>();
		check(wrong, "a bread from nothing", Crafting.lacking(level, Crafting.Kind.KITCHEN, Items.BREAD, 1, Map.of()), Map.of(Items.WHEAT, 3));
		check(wrong, "2 bread from nothing", Crafting.lacking(level, Crafting.Kind.KITCHEN, Items.BREAD, 2, Map.of()), Map.of(Items.WHEAT, 6));
		check(wrong, "a bread from 2 wheat", Crafting.lacking(level, Crafting.Kind.KITCHEN, Items.BREAD, 1, Map.of(Items.WHEAT, 2L)), Map.of(Items.WHEAT, 1));
		// Create's cardboard book and item frame: never named (whatever else the line names; see the leather test below)
		Map<String, Map<Item, Integer>> cardboard = new LinkedHashMap<>();
		cardboard.put("a book from 3 paper", Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.BOOK, 1, Map.of(Items.PAPER, 3L)));
		cardboard.put("an item frame from 8 sticks", Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.ITEM_FRAME, 1, Map.of(Items.STICK, 8L)));
		cardboard.put("a book from nothing", Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.BOOK, 1, Map.of()));
		cardboard.put("an item frame from nothing", Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.ITEM_FRAME, 1, Map.of()));
		cardboard.forEach((what, lack) -> {
			if (lack.isEmpty() || foreign(lack)) {
				wrong.put(what, "lacks " + lack + ", expected something named and no Create item");
			}
		});
		helper.assertTrue(wrong.isEmpty(), "the wrong thing is named short: " + wrong);
		helper.succeed();
	}

	static Villager librarian(GameTestHelper helper) {
		BlockPos station = QaCrafterOwnModdedStore1111eCompatTests.STATION;
		helper.setBlock(station, Blocks.LECTERN);
		Villager v = helper.spawn(EntityType.VILLAGER, station.south());
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(station), PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
		return v;
	}

	// ---- the builder's line, through the player's entry point ---------------------------------------------------------

	/** One gold pressure plate, a coal and no ore: exactly "Can't be made yet: short of 2 Raw Gold to make 1 Light Weighted Pressure Plate". */
	//$ gametest_ticks_batch AREA '1400' '"qaCreateRivals1115cGoldPlate"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaCreateRivals1115cGoldPlate")
	public void aGoldPlateShortNamesRawGold(GameTestHelper helper) {
		exactLine(helper, Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE, "goldplate",
			QaCreateForge1115eBugCompatTests.cantMake("2", "Raw Gold", "1", "Light Weighted Pressure Plate"), new ItemStack(Items.COAL, 1));
	}

	/** One copper block, two coal (nine to fire) and no ore: exactly "Can't be made yet: short of 9 Raw Copper to make 1 Block of Copper". */
	//$ gametest_ticks_batch AREA '1400' '"qaCreateRivals1115cCopper"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaCreateRivals1115cCopper")
	public void aCopperBlockShortNamesRawCopper(GameTestHelper helper) {
		exactLine(helper, Blocks.COPPER_BLOCK, "copper", QaCreateForge1115eBugCompatTests.cantMake("9", "Raw Copper", "1", "Block of Copper"),
			new ItemStack(Items.COAL, 2));
	}

	/** One lantern, two coal: a line is said and names the Raw Iron, never a Crushed ore, Zinc, a plaque or the nuggets. */
	//$ gametest_ticks_batch AREA '1400' '"qaCreateRivals1115cLantern"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaCreateRivals1115cLantern")
	public void aLanternShortNamesRawIron(GameTestHelper helper) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		ItemStack[] items = {new ItemStack(Items.COAL, 2)};
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.LANTERN, 1, 1, "rivlantern"),
			Blocks.CHEST.defaultBlockState(), copies(items));
		Villager t = tinkerer(helper);
		helper.runAfterDelay(1200, () -> {
			String text = QaCrafterOwnModdedStore1111eCompatTests.line(helper, a);
			helper.assertTrue(text != null && text.contains("Raw Iron") && !text.contains("Crushed") && !text.contains("Zinc") && !text.contains("Plaque")
				&& !text.contains("Nugget"), "the builder's line: '" + text + "', expected the Raw Iron named (no Crushed ore, Zinc, plaque or nuggets); site "
				+ site(helper, a));
			tookNothing(helper, t, items);
			helper.succeed();
		});
	}

	/**
	 * A Librarian and three bookshelves, 27 cane, 18 planks and 1 leather (as the plain suite's
	 * aLibrarianEightLeatherShortSaysEightLeather): with Create's cardboard book recipe there, the line still names the 8
	 * Leather, never Cardboard; nothing is taken.
	 */
	//$ gametest_ticks_batch AREA '1600' '"qaCreateRivals1115cShelves"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaCreateRivals1115cShelves")
	public void bookshelvesShortNameLeatherNotCardboard(GameTestHelper helper) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		ItemStack[] items = {new ItemStack(Items.SUGAR_CANE, 27), new ItemStack(Items.LEATHER, 1), new ItemStack(Items.OAK_PLANKS, 18)};
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.BOOKSHELF, 3, 1, "rivshelves"),
			Blocks.CHEST.defaultBlockState(), copies(items));
		Villager librarian = librarian(helper);
		helper.runAfterDelay(1400, () -> {
			String text = QaCrafterOwnModdedStore1111eCompatTests.line(helper, a);
			helper.assertTrue(text != null && text.contains("8 Leather") && !text.contains("Cardboard"),
				"the builder's line: '" + text + "', expected it to name 8 Leather (the plain suite: \"Can't be made yet: short of 8 Leather to make 3 Bookshelves\"), "
					+ "never Cardboard; site " + site(helper, a));
			tookNothing(helper, librarian, items);
			helper.succeed();
		});
	}

	/**
	 * Some of Create's own ore there but too little: 16 iron bars, a coal and 2 Crushed Raw Iron. Whatever it names, a line is
	 * said after the waiting period (never none, B1268), the crushed ore and coal stay, and the Tinkerer takes nothing.
	 */
	//$ gametest_ticks_batch AREA '1400' '"qaCreateRivals1115cSomeCrushed"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaCreateRivals1115cSomeCrushed")
	public void tooLittleCrushedOreStillGetsALine(GameTestHelper helper) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		ItemStack[] items = {new ItemStack(Items.COAL, 1), new ItemStack(create("crushed_raw_iron"), 2)};
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.IRON_BARS, 4, 4, "rivsome"),
			Blocks.CHEST.defaultBlockState(), copies(items));
		Villager t = tinkerer(helper);
		helper.runAfterDelay(1200, () -> {
			String text = QaCrafterOwnModdedStore1111eCompatTests.line(helper, a);
			helper.assertTrue(text != null && !text.isBlank() && !text.startsWith("(the build"), "no shortage line while short of 16 iron bars: '" + text + "'; site "
				+ site(helper, a));
			tookNothing(helper, t, items);
			helper.succeed();
		});
	}

	// ---- the counter-cases: Create's own ingredients in the chest are used ----------------------------------------------

	/** 6 Crushed Raw Iron and a coal for 16 iron bars: the Tinkerer blasts and makes them, the crushed ore all used. */
	//$ gametest_ticks_batch AREA '5000' '"qaCreateRivals1115cCrushedMade"'
	@GameTest(template = AREA, timeoutTicks = 5000, batch = "qaCreateRivals1115cCrushedMade")
	public void crushedRawIronInTheChestIsMadeIntoBars(GameTestHelper helper) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		Item crushed = create("crushed_raw_iron");
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.IRON_BARS, 4, 4, "rivcrushed"),
			Blocks.CHEST.defaultBlockState(), new ItemStack(crushed, 6), new ItemStack(Items.COAL, 1));
		Villager t = tinkerer(helper);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(helper.getLevel()).get(a.site().id()) == null, "still building; line '"
				+ QaCrafterOwnModdedStore1111eCompatTests.line(helper, a) + "'; site " + site(helper, a) + "; the Tinkerer made "
				+ ModAttachments.ITEMS_CRAFTED.getOrElse(t, 0));
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(crushed) == 0, chest.countItem(crushed) + " crushed raw iron left, expected all 6 used");
		});
	}

	/** 3 Zinc Ingots and 6 Zinc Nuggets (no iron) for 3 chains: Create's zinc chain is used, the build finishes, the zinc gone. */
	//$ gametest_ticks_batch AREA '5000' '"qaCreateRivals1115cZincMade"'
	@GameTest(template = AREA, timeoutTicks = 5000, batch = "qaCreateRivals1115cZincMade")
	public void zincInTheChestIsMadeIntoChains(GameTestHelper helper) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		Item ingot = create("zinc_ingot");
		Item nugget = create("zinc_nugget");
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.CHAIN, 3, 1, "rivzinc"),
			Blocks.CHEST.defaultBlockState(), new ItemStack(ingot, 3), new ItemStack(nugget, 6));
		Villager t = tinkerer(helper);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(helper.getLevel()).get(a.site().id()) == null, "still building; line '"
				+ QaCrafterOwnModdedStore1111eCompatTests.line(helper, a) + "'; site " + site(helper, a) + "; the Tinkerer made "
				+ ModAttachments.ITEMS_CRAFTED.getOrElse(t, 0));
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(ingot) == 0 && chest.countItem(nugget) == 0, "the zinc should be used up: " + chest.countItem(ingot)
				+ " ingots, " + chest.countItem(nugget) + " nuggets");
		});
	}

	/** 6 wheat by the Kitchen Stove: the Chef bakes 2 bread by Minecraft's recipe (Create's dough is no reason to refuse). */
	//$ gametest_ticks_batch SMALL '3000' '"qaCreateRivals1115cChefWheat"'
	@GameTest(template = SMALL, timeoutTicks = 3000, batch = "qaCreateRivals1115cChefWheat")
	public void theChefBakesBreadFromWheat(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Container chest = chefChest(helper, new ItemStack(Items.WHEAT, 6));
		Villager chef = chef(helper);
		helper.succeedWhen(() -> {
			int bread = chest.countItem(Items.BREAD) + chef.getInventory().countItem(Items.BREAD);
			helper.assertTrue(bread == 2 && chest.countItem(Items.WHEAT) == 0 && chef.getInventory().countItem(Items.WHEAT) == 0,
				bread + " bread and " + chest.countItem(Items.WHEAT) + " wheat in the chest, expected 2 bread from 6 wheat");
		});
	}

	/** 2 of Create's Dough by the Kitchen Stove (no wheat): the Chef bakes them into 2 bread (what's there is used). */
	//$ gametest_ticks_batch SMALL '3000' '"qaCreateRivals1115cChefDough"'
	@GameTest(template = SMALL, timeoutTicks = 3000, batch = "qaCreateRivals1115cChefDough")
	public void theChefBakesCreatesDoughIntoBread(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Item dough = create("dough");
		Container chest = chefChest(helper, new ItemStack(dough, 2));
		Villager chef = chef(helper);
		helper.succeedWhen(() -> {
			int bread = chest.countItem(Items.BREAD) + chef.getInventory().countItem(Items.BREAD);
			helper.assertTrue(bread == 2 && chest.countItem(dough) == 0, bread + " bread and " + chest.countItem(dough) + " dough in the chest, expected 2 bread");
		});
	}

	// ---- helpers ----------------------------------------------------------------------------------------------------

	static void exactLine(GameTestHelper helper, net.minecraft.world.level.block.Block block, String what, String expected, ItemStack... items) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, block, 1, 1, "riv" + what),
			Blocks.CHEST.defaultBlockState(), copies(items));
		Villager t = tinkerer(helper);
		helper.runAfterDelay(1200, () -> {
			String text = QaCrafterOwnModdedStore1111eCompatTests.line(helper, a);
			helper.assertTrue(expected.equals(text), "the builder's line: '" + text + "', expected '" + expected + "'; site " + site(helper, a));
			tookNothing(helper, t, items);
			helper.succeed();
		});
	}

	static Villager tinkerer(GameTestHelper helper) {
		BlockPos station = QaCrafterOwnModdedStore1111eCompatTests.STATION;
		helper.setBlock(station, ModBlocks.TINKERS_BENCH);
		Villager v = helper.spawn(EntityType.VILLAGER, station.south());
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(station), ModVillagers.TINKERS_BENCH_POI, ModVillagers.TINKERER);
		return v;
	}

	static Container chefChest(GameTestHelper helper, ItemStack... items) {
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		for (int i = 0; i < items.length; i++) {
			chest.setItem(i, items[i]);
		}
		return chest;
	}

	static Villager chef(GameTestHelper helper) {
		helper.setDayTime(2000);
		helper.setBlock(STOVE, ModBlocks.KITCHEN_STOVE);
		Villager chef = helper.spawn(EntityType.VILLAGER, CHEF_AT);
		Jobs.employ(helper.getLevel(), chef, helper.absolutePos(STOVE), ModVillagers.KITCHEN_STOVE_POI, ModVillagers.CHEF);
		return chef;
	}

	/** The crafter's bag is empty and the builder's chest still holds exactly {@code items}. */
	static void tookNothing(GameTestHelper helper, Villager crafter, ItemStack... items) {
		helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(crafter).isEmpty(), "the crafter took something into his bag");
		Container chest = helper.getBlockEntity(CHEST);
		for (ItemStack s : items) {
			helper.assertTrue(chest.countItem(s.getItem()) == s.getCount(), "the chest's " + s.getHoverName().getString() + " was touched: "
				+ chest.countItem(s.getItem()) + " of " + s.getCount());
		}
	}

	static ItemStack[] copies(ItemStack... items) {
		ItemStack[] out = new ItemStack[items.length];
		for (int i = 0; i < items.length; i++) {
			out[i] = items[i].copy();
		}
		return out;
	}

	static void check(Map<String, String> wrong, String what, Map<Item, Integer> got, Map<Item, Integer> expected) {
		if (!got.equals(expected)) {
			wrong.put(what, "lacks " + got + ", expected " + expected);
		}
	}

	/** Names an item of Create's or Cobblemon's. */
	static boolean foreign(Map<Item, Integer> lack) {
		for (Item k : lack.keySet()) {
			String ns = BuiltInRegistries.ITEM.getKey(k).getNamespace();
			if (ns.equals("create") || ns.equals("cobblemon")) {
				return true;
			}
		}
		return false;
	}

	static String site(GameTestHelper helper, Asker a) {
		BuildSite live = BuildSiteManager.get(helper.getLevel()).get(a.site().id());
		return live == null ? "done" : live.stage() + "/" + live.status() + " missing " + live.missing();
	}

	static Item create(String path) {
		Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", path));
		if (item == Items.AIR) {
			throw new GameTestAssertException("create:" + path + " isn't registered");
		}
		return item;
	}
}
