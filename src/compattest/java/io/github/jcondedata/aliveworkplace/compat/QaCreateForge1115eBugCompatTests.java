package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.compat.QaCrafterOwnModdedStore1111eCompatTests.Asker;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane E, 2026-11-15), failing with Create loaded (the owner's 2.3 pack): the Tinkerer's forge names Create's Crushed
 * Raw ores as what a builder is short of. Create fires (and blasts) ingots from its crushed ores, which only a crushing
 * wheel makes, so a builder asking the Tinkerer for iron bars with a coal and no ore reads "short of 6 Crushed Raw Iron"
 * instead of "6 Raw Iron". The spec (crafter.md, B408/B489): "The line names what the chest is short of, not some other
 * way to make it"; the plain suite reads "short of 6 Raw Iron to make 3 Iron Bars"
 * (QaTinkererShortBugGameTests#coalButNoOreNamesTheRawIron). Moves to main with the fix.
 */
public class QaCreateForge1115eBugCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	/** What the forge names short, read straight off its recipes: the raw metal, never a crushed ore. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void withCreateTheForgeNamesRawMetalNotCrushedOre(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Crafting.Kind kind = Crafting.Kind.WORKSHOP;
		Map<String, String> wrong = new LinkedHashMap<>();
		check(wrong, "16 iron bars from nothing", Crafting.lacking(level, kind, Items.IRON_BARS, 16, Map.of()), Map.of(Items.RAW_IRON, 6));
		check(wrong, "16 iron bars from 5 raw iron", Crafting.lacking(level, kind, Items.IRON_BARS, 16, Map.of(Items.RAW_IRON, 5L)), Map.of(Items.RAW_IRON, 1));
		check(wrong, "9 iron nuggets from nothing", Crafting.lacking(level, kind, Items.IRON_NUGGET, 9, Map.of()), Map.of(Items.RAW_IRON, 1));
		check(wrong, "a copper block from nothing", Crafting.lacking(level, kind, Items.COPPER_BLOCK, 1, Map.of()), Map.of(Items.RAW_COPPER, 9));
		check(wrong, "a gold pressure plate from nothing", Crafting.lacking(level, kind, Items.LIGHT_WEIGHTED_PRESSURE_PLATE, 1, Map.of()),
			Map.of(Items.RAW_GOLD, 2));
		// a lantern: the plain suite's forge goes down to {raw_iron=1, charcoal=1, bamboo=2}; it must reach the raw iron here too
		Map<Item, Integer> lantern = Crafting.lacking(level, kind, Items.LANTERN, 1, Map.of());
		if (!lantern.containsKey(Items.RAW_IRON) || lantern.containsKey(Items.IRON_NUGGET)) {
			wrong.put("a lantern from nothing", "lacks " + lantern + ", expected the raw iron named (the plain suite: {raw_iron=1, charcoal=1, bamboo=2})");
		}
		helper.assertTrue(wrong.isEmpty(), "the forge names the wrong thing short: " + wrong);
		helper.succeed();
	}

	private static void check(Map<String, String> wrong, String what, Map<Item, Integer> got, Map<Item, Integer> expected) {
		if (!got.equals(expected)) {
			wrong.put(what, "lacks " + got + ", expected " + expected);
		}
	}

	/**
	 * Through the player's entry point: a builder short of 16 iron bars, a coal and no ore in his chest, a Tinkerer at his
	 * bench. The builder's line reads exactly "Can't be made yet: short of 6 Raw Iron to make 16 Iron Bars"; nothing taken.
	 */
	//$ gametest_ticks_batch AREA '1400' '"qaCreateForge1115eLine"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaCreateForge1115eLine")
	public void withCreateTheBuildersLineNamesRawIron(GameTestHelper helper) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.IRON_BARS, 4, 4, "forgebars"),
			Blocks.CHEST.defaultBlockState(), new ItemStack(Items.COAL, 1));
		BlockPos station = QaCrafterOwnModdedStore1111eCompatTests.STATION;
		helper.setBlock(station, ModBlocks.TINKERS_BENCH);
		Villager tinkerer = helper.spawn(EntityType.VILLAGER, station.south());
		Jobs.employ(helper.getLevel(), tinkerer, helper.absolutePos(station), ModVillagers.TINKERS_BENCH_POI, ModVillagers.TINKERER);
		helper.runAfterDelay(1200, () -> {
			String expected = cantMake("6", "Raw Iron", "16", "Iron Bars");
			String text = QaCrafterOwnModdedStore1111eCompatTests.line(helper, a);
			helper.assertTrue(expected.equals(text), "the builder's line: '" + text + "', expected '" + expected + "'");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(tinkerer).isEmpty(), "the Tinkerer took something");
			helper.succeed();
		});
	}

	static String cantMake(String n, String item, String m, String part) {
		String lack = String.format(lang("message.aliveworkplace.job_chain.lack_item"), n, item);
		String lacks = String.format(lang("message.aliveworkplace.job_chain.lacks"), lack, m, part);
		return String.format(lang("message.aliveworkplace.job_chain.cant_make"), lacks);
	}

	/** The mod's own en_us text (the server's Language has only vanilla's). */
	private static String lang(String key) {
		try (java.io.InputStream in = QaCreateForge1115eBugCompatTests.class.getResourceAsStream("/assets/aliveworkplace/lang/en_us.json")) {
			if (in == null) {
				throw new GameTestAssertException("en_us.json isn't on the classpath");
			}
			com.google.gson.JsonObject json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
				.getAsJsonObject();
			if (!json.has(key)) {
				throw new GameTestAssertException("no " + key + " in en_us.json");
			}
			return json.get(key).getAsString();
		} catch (java.io.IOException e) {
			throw new GameTestAssertException("en_us.json: " + e);
		}
	}

	@SuppressWarnings("unused")
	private static Item create(String path) {
		Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", path));
		if (item == Items.AIR) {
			throw new GameTestAssertException("create:" + path + " isn't registered");
		}
		return item;
	}
}
