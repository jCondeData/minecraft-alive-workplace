package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.compat.QaCrafterOwnModdedStore1111eCompatTests.Asker;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.test.FreshPlanning;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane B, 2026-11-15), verifying B1267 and B1268 with Create (and Cobblemon) loaded. The spec (crafter.md, B408/B489):
 * a builder's shortage line names the raw thing a player mines or gathers, never a crushing wheel's output or another
 * mod's item melted down, and "the one closest to what's there" still wins when the chest holds something. These cases
 * sit beside QaCreateForge1115eBugCompatTests, QaCreateChain1115eCompatTests and QaCreateForgeKiln1115eCompatTests and
 * don't repeat them: other Create rivals read off the recipes (Create's bread from dough, book and item frame from
 * cardboard, chain from zinc), the other side of the boundary (a strict plan must still
 * make what the chests can lead to, through Create's items too), and Create's own items.
 *
 * Every read here is one per tick: a tick's planning is capped at {@link Crafting#TICK_TRIALS}, and a capped plan reads
 * as "nothing short", which would hide what's being checked.
 */
public class QaCreateRivals1115bCompatTests implements FreshPlanning {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	private static Item create(String path) {
		Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", path));
		if (item == Items.AIR) {
			throw new GameTestAssertException("create:" + path + " isn't registered (is Create loaded?)");
		}
		return item;
	}

	private static String ns(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).getNamespace();
	}

	private static String path(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).getPath();
	}

	/** Runs each check on its own tick (so each plan has the tick's whole budget), then reports every one that was wrong. */
	private static void oneATick(GameTestHelper helper, Map<String, Function<ServerLevel, String>> checks) {
		Map<String, String> wrong = new LinkedHashMap<>();
		List<Map.Entry<String, Function<ServerLevel, String>>> list = new ArrayList<>(checks.entrySet());
		for (int i = 0; i < list.size(); i++) {
			Map.Entry<String, Function<ServerLevel, String>> check = list.get(i);
			helper.runAfterDelay(1 + i, () -> {
				String problem = check.getValue().apply(helper.getLevel());
				if (problem != null) {
					wrong.put(check.getKey(), problem);
				}
			});
		}
		helper.runAfterDelay(2 + list.size(), () -> {
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong: " + wrong);
			helper.succeed();
		});
	}

	private static String exactly(Map<Item, Integer> got, Map<Item, Integer> expected) {
		return got.equals(expected) ? null : "lacks " + got + ", expected " + expected;
	}

	/** Neither a crushed ore (a crushing wheel's output), nor a Cobblemon item melted down, nor Create's {@code banned}. */
	private static String noRival(Map<Item, Integer> got, String... banned) {
		for (Item item : got.keySet()) {
			if (path(item).startsWith("crushed_") || ns(item).equals("cobblemon")) {
				return "lacks " + got + ": names " + BuiltInRegistries.ITEM.getKey(item);
			}
			for (String b : banned) {
				if (ns(item).equals("create") && path(item).contains(b)) {
					return "lacks " + got + ": names Create's " + BuiltInRegistries.ITEM.getKey(item);
				}
			}
		}
		return null;
	}

	/**
	 * Read off the recipes from nothing, Create's rivals for vanilla things: each names the vanilla raw thing. Bread at the
	 * Chef's kitchen is 3 wheat (not Create's dough or wheat flour, fired in a smoker or on a campfire); a book at the table
	 * names 3 sugar cane and the leather (or the rabbit hide it's made from), never Create's cardboard; an item frame
	 * names the leather (or its hide), never cardboard; 4 chains at
	 * the forge are raw iron only (not zinc, nor crushed ore); a chain at the table names iron, never zinc; 9 gold nuggets
	 * at the forge are 1 raw gold.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '200' '"qaCreateRivals1115bRead"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200, batch = "qaCreateRivals1115bRead")
	public void withCreateVanillaThingsNameTheVanillaRawThing(GameTestHelper helper) {
		Item flour = create("wheat_flour");
		Item dough = create("dough");
		Item cardboard = create("cardboard");
		Item zincIngot = create("zinc_ingot");
		Map<String, Function<ServerLevel, String>> checks = new LinkedHashMap<>();
		checks.put("1 bread at the kitchen from nothing", level -> {
			Map<Item, Integer> got = Crafting.lacking(level, Crafting.Kind.KITCHEN, Items.BREAD, 1, Map.of());
			return got.containsKey(flour) || got.containsKey(dough) ? "lacks " + got + ": names Create's flour or dough"
				: exactly(got, Map.of(Items.WHEAT, 3));
		});
		checks.put("a book at the table from nothing", level -> {
			Map<Item, Integer> got = Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.BOOK, 1, Map.of());
			// the leather may be named as the 4 rabbit hide it's made from (the plain table's own way down from nothing);
			// what matters here is Create's cardboard never standing in for it
			if (got.containsKey(cardboard)) {
				return "lacks " + got + ": names Create's cardboard";
			}
			return Integer.valueOf(3).equals(got.get(Items.SUGAR_CANE)) && (got.containsKey(Items.LEATHER) || got.containsKey(Items.RABBIT_HIDE)) ? null
				: "lacks " + got + ", expected the 3 sugar cane and the leather (or its rabbit hide) named";
		});
		checks.put("an item frame at the table from nothing", level -> {
			Map<Item, Integer> got = Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.ITEM_FRAME, 1, Map.of());
			if (got.containsKey(cardboard)) {
				return "lacks " + got + ": names Create's cardboard";
			}
			return got.containsKey(Items.LEATHER) || got.containsKey(Items.RABBIT_HIDE) ? null
				: "lacks " + got + ", expected the leather (or its rabbit hide) named, as vanilla's frame takes";
		});
		checks.put("4 chains at the forge from nothing", level -> {
			Map<Item, Integer> got = Crafting.lacking(level, Crafting.Kind.WORKSHOP, Items.CHAIN, 4, Map.of());
			String rival = noRival(got, "zinc");
			if (rival != null) {
				return rival;
			}
			return got.keySet().equals(java.util.Set.of(Items.RAW_IRON)) ? null : "lacks " + got + ", expected only raw iron named";
		});
		checks.put("a chain at the table from nothing", level -> {
			Map<Item, Integer> got = Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.CHAIN, 1, Map.of());
			if (got.containsKey(zincIngot) || noRival(got, "zinc") != null) {
				return "lacks " + got + ": names zinc or a rival";
			}
			return got.containsKey(Items.IRON_INGOT) || got.containsKey(Items.IRON_NUGGET) ? null : "lacks " + got + ", expected iron named";
		});
		checks.put("9 gold nuggets at the forge from nothing", level ->
			exactly(Crafting.lacking(level, Crafting.Kind.WORKSHOP, Items.GOLD_NUGGET, 9, Map.of()), Map.of(Items.RAW_GOLD, 1)));
		oneATick(helper, checks);
	}

	/**
	 * A thing only Create makes (B1267's own-mod preference must not break it): Andesite Alloy at the table still gets a
	 * shortage at all (andesite and iron or zinc nuggets), never its storage block unpacked nor a crushed ore.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '200' '"qaCreateRivals1115bOwn"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200, batch = "qaCreateRivals1115bOwn")
	public void withCreateCreatesOwnThingsStillNameWhatsShort(GameTestHelper helper) {
		Item alloy = create("andesite_alloy");
		Item alloyBlock = create("andesite_alloy_block");
		Map<String, Function<ServerLevel, String>> checks = new LinkedHashMap<>();
		checks.put("an andesite alloy at the table from nothing", level -> {
			Map<Item, Integer> got = Crafting.lacking(level, Crafting.Kind.CRAFTING, alloy, 1, Map.of());
			if (got.isEmpty()) {
				return "lacks nothing: no line at all for a thing only Create makes";
			}
			if (got.containsKey(alloyBlock)) {
				return "lacks " + got + ": names its storage block unpacked";
			}
			return noRival(got);
		});
		oneATick(helper, checks);
	}

	/**
	 * The other side of the boundary, read off the forge's plans for real (the Tinkerer's {@code Crafting.chain}, strict):
	 * the new "nothing in the chests leads to it" exit must never refuse what can be made. 16 iron bars from 6 raw iron and
	 * a coal; a lantern two steps down from 1 raw iron, 2 coal and a stick (nuggets from an ingot fired from the ore, the
	 * torch from coal and the stick); a chain from 2 crushed raw iron and a coal (Create's ore fired, what's there); and a
	 * chain from 2 zinc ingots (Create's chain, the zinc being what's there).
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '200' '"qaCreateRivals1115bPlans"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200, batch = "qaCreateRivals1115bPlans")
	public void withCreateStrictPlansStillMakeWhatTheChestLeadsTo(GameTestHelper helper) {
		Item crushed = create("crushed_raw_iron");
		Item zincIngot = create("zinc_ingot");
		Map<String, Function<ServerLevel, String>> checks = new LinkedHashMap<>();
		checks.put("16 iron bars from 6 raw iron and a coal", level -> {
			Crafting.Plan p = Crafting.chain(level, Crafting.Kind.WORKSHOP, Items.IRON_BARS, 16, Map.of(Items.RAW_IRON, 6L, Items.COAL, 1L));
			return p != null && p.count() >= 16 && Integer.valueOf(6).equals(p.takes().get(Items.RAW_IRON)) ? null
				: "plan " + (p == null ? "none" : p.count() + " taking " + p.takes()) + ", expected 16 bars taking 6 raw iron";
		});
		checks.put("a lantern from 1 raw iron, 2 coal and a stick", level -> {
			Crafting.Plan p = Crafting.chain(level, Crafting.Kind.WORKSHOP, Items.LANTERN, 1, Map.of(Items.RAW_IRON, 1L, Items.COAL, 2L, Items.STICK, 1L));
			return p != null && Integer.valueOf(1).equals(p.takes().get(Items.RAW_IRON)) && Integer.valueOf(1).equals(p.takes().get(Items.STICK)) ? null
				: "plan " + (p == null ? "none" : "taking " + p.takes() + " in " + p.steps()) + ", expected a lantern taking the raw iron and the stick";
		});
		checks.put("a chain from 2 crushed raw iron and a coal", level -> {
			Crafting.Plan p = Crafting.chain(level, Crafting.Kind.WORKSHOP, Items.CHAIN, 1, Map.of(crushed, 2L, Items.COAL, 1L));
			return p != null && Integer.valueOf(2).equals(p.takes().get(crushed)) ? null
				: "plan " + (p == null ? "none" : "taking " + p.takes()) + ", expected a chain taking the 2 crushed raw iron";
		});
		checks.put("a chain from 2 zinc ingots", level -> {
			Crafting.Plan p = Crafting.chain(level, Crafting.Kind.WORKSHOP, Items.CHAIN, 1, Map.of(zincIngot, 2L));
			return p != null && Integer.valueOf(2).equals(p.takes().get(zincIngot)) ? null
				: "plan " + (p == null ? "none" : "taking " + p.takes()) + ", expected a chain taking the 2 zinc ingots";
		});
		oneATick(helper, checks);
	}

	private static Villager tinkerer(GameTestHelper helper) {
		BlockPos station = QaCrafterOwnModdedStore1111eCompatTests.STATION;
		helper.setBlock(station, ModBlocks.TINKERS_BENCH);
		Villager v = helper.spawn(EntityType.VILLAGER, station.south());
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(station), ModVillagers.TINKERS_BENCH_POI, ModVillagers.TINKERER);
		return v;
	}

	/** How many {@code block}s stand in the blueprint's {@code dx} x {@code dz} footprint (the helper's ROW, 6 2 8). */
	private static int placed(GameTestHelper helper, net.minecraft.world.level.block.Block block, int dx, int dz) {
		int n = 0;
		for (int x = 0; x < dx; x++) {
			for (int z = 0; z < dz; z++) {
				n += helper.getBlockState(new BlockPos(6 + x, 2, 8 + z)).is(block) ? 1 : 0;
			}
		}
		return n;
	}

	private static String site(GameTestHelper helper, Asker a) {
		io.github.jcondedata.aliveworkplace.build.BuildSite live = BuildSiteManager.get(helper.getLevel()).get(a.site().id());
		return live == null ? "done" : live.stage() + "/" + live.status() + " missing " + live.missing();
	}

	/**
	 * Through the player's entry point: a builder needing 16 iron bars, his chest holding 6 Crushed Raw Iron and a coal (a
	 * player with a crushing wheel), a Tinkerer at his bench. What's there leads to the bars, so they get made and the build
	 * finishes, the crushed ore used up.
	 */
	//$ gametest_ticks_batch AREA '6000' '"qaCreateRivals1115bCrushed"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qaCreateRivals1115bCrushed")
	public void withCreateBarsGetMadeFromCrushedOreInTheChest(GameTestHelper helper) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		Item crushed = create("crushed_raw_iron");
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.IRON_BARS, 4, 4, "rivalcrushed"),
			Blocks.CHEST.defaultBlockState(), new ItemStack(crushed, 6), new ItemStack(Items.COAL, 1));
		Villager t = tinkerer(helper);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(helper.getLevel()).get(a.site().id()) == null, "still building (" + site(helper, a) + "); line '"
				+ QaCrafterOwnModdedStore1111eCompatTests.line(helper, a) + "'; the Tinkerer made " + ModAttachments.ITEMS_CRAFTED.getOrElse(t, 0));
			Container chest = helper.getBlockEntity(new BlockPos(2, 2, 4));
			helper.assertTrue(chest.countItem(crushed) == 0, "the crushed ore should be used up: " + chest.countItem(crushed) + " left");
			helper.assertTrue(placed(helper, Blocks.IRON_BARS, 4, 4) == 16, placed(helper, Blocks.IRON_BARS, 4, 4) + " iron bars placed, expected 16");
		});
	}

	/**
	 * Two steps down for real with Create's rival chain recipe around: 4 chains from 5 raw iron and a coal (4 ingots, and
	 * one more cut into the 8 nuggets). The Tinkerer fires the ore, cuts the nuggets and makes the chains; the build
	 * finishes and the raw iron is used up.
	 */
	//$ gametest_ticks_batch AREA '6000' '"qaCreateRivals1115bChains"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qaCreateRivals1115bChains")
	public void withCreateChainsGetMadeFromRawIron(GameTestHelper helper) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.CHAIN, 2, 2, "rivalchains"),
			Blocks.CHEST.defaultBlockState(), new ItemStack(Items.RAW_IRON, 5), new ItemStack(Items.COAL, 1));
		Villager t = tinkerer(helper);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(helper.getLevel()).get(a.site().id()) == null, "still building (" + site(helper, a) + "); line '"
				+ QaCrafterOwnModdedStore1111eCompatTests.line(helper, a) + "'; the Tinkerer made " + ModAttachments.ITEMS_CRAFTED.getOrElse(t, 0));
			Container chest = helper.getBlockEntity(new BlockPos(2, 2, 4));
			helper.assertTrue(chest.countItem(Items.RAW_IRON) == 0, "the raw iron should be used up: " + chest.countItem(Items.RAW_IRON) + " left");
			helper.assertTrue(placed(helper, Blocks.CHAIN, 2, 2) == 4, placed(helper, Blocks.CHAIN, 2, 2) + " chains placed, expected 4");
		});
	}
}
