package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-b-1116-0937), verifying B1303, B1304, B1306 and B1307 from crafter.md, not the code: "The line never goes down
 * through what the thing is being made for" (honey blocks, dyed wool), "A stripped block is never raw" (bamboo), and the
 * countable things crafters run short of read in the plural after a count above one. The other side of each rule too: a
 * real chain still plans (a stripped log or block that is there still makes planks, a block of iron or honey still
 * unpacks when asked for ingots or bottles), so the fixes didn't cut a way a player has.
 */
public class QaShortageLineVerify1116bGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos STATION = new BlockPos(14, 2, 2);
	private static final Crafting.Kind[] KINDS = {Crafting.Kind.CRAFTING, Crafting.Kind.WORKSHOP};

	private static Item item(String path) {
		return BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(path));
	}

	private static boolean dyedWoolOrCarpet(Item i) {
		return i != Items.WHITE_WOOL && i != Items.WHITE_CARPET
			&& (i.builtInRegistryHolder().is(ItemTags.WOOL) || i.builtInRegistryHolder().is(ItemTags.WOOL_CARPETS));
	}

	/**
	 * Each of the 16 colors' wool (but white) and carpet, from nothing: the line names String (or White Wool) for the wool
	 * and never a dyed wool or carpet of any color, its own included.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaShortLine1116bColors"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaShortLine1116bColors")
	public void everyDyedWoolAndCarpetFromNothingNamesWhiteWoolsMakings(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> wrong = new ArrayList<>();
		for (DyeColor color : DyeColor.values()) {
			for (String kindOf : new String[] {"_wool", "_carpet"}) {
				Item target = item(color.getName() + kindOf);
				if (target == Items.WHITE_WOOL) {
					continue;
				}
				for (Crafting.Kind kind : KINDS) {
					Map<Item, Integer> lack = Crafting.lacking(level, kind, target, 3, Map.of());
					boolean white = lack.containsKey(Items.STRING) || lack.containsKey(Items.WHITE_WOOL);
					if (!white || lack.keySet().stream().anyMatch(QaShortageLineVerify1116bGameTests::dyedWoolOrCarpet)) {
						wrong.add(kind + " 3 " + color.getName() + kindOf + " lack " + names(lack));
					}
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " dyed lines name the wrong makings: " + wrong);
		helper.succeed();
	}

	/**
	 * With the color's own dye there, each dyed wool lacks only what white wool comes from (no dye of any kind); with
	 * white wool and the dye there, nothing at all (the Shepherd's real chain still plans).
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaShortLine1116bWithDye"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaShortLine1116bWithDye")
	public void everyDyedWoolWithItsDyeLacksOnlyTheWhiteWool(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> wrong = new ArrayList<>();
		for (DyeColor color : DyeColor.values()) {
			if (color == DyeColor.WHITE) {
				continue;
			}
			Item wool = item(color.getName() + "_wool");
			Item dye = item(color.getName() + "_dye");
			for (Crafting.Kind kind : KINDS) {
				Map<Item, Integer> lack = Crafting.lacking(level, kind, wool, 2, Map.of(dye, 2L));
				boolean onlyWhite = !lack.isEmpty() && lack.keySet().stream().allMatch(i -> i == Items.STRING || i == Items.WHITE_WOOL);
				if (!onlyWhite) {
					wrong.add(kind + " 2 " + color.getName() + " wool with 2 of its dye lack " + names(lack) + " (expected only String or White Wool)");
				}
				Map<Item, Integer> both = Crafting.lacking(level, kind, wool, 2, Map.of(dye, 2L, Items.WHITE_WOOL, 2L));
				if (!both.isEmpty()) {
					wrong.add(kind + " 2 " + color.getName() + " wool from 2 white wool and 2 of its dye lack " + names(both) + " (expected nothing)");
				}
				Map<Item, Integer> fromString = Crafting.lacking(level, kind, wool, 2, Map.of(dye, 2L, Items.STRING, 8L));
				if (!fromString.isEmpty()) {
					wrong.add(kind + " 2 " + color.getName() + " wool from 8 string and 2 of its dye lack " + names(fromString) + " (expected nothing)");
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong: " + wrong);
		helper.succeed();
	}

	/** Carpets from their own wool there: nothing short (the wool isn't dyed into another color first). */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaShortLine1116bCarpets"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaShortLine1116bCarpets")
	public void everyCarpetFromItsOwnWoolLacksNothing(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> wrong = new ArrayList<>();
		for (DyeColor color : DyeColor.values()) {
			Item wool = item(color.getName() + "_wool");
			Item carpet = item(color.getName() + "_carpet");
			for (Crafting.Kind kind : KINDS) {
				Map<Item, Integer> lack = Crafting.lacking(level, kind, carpet, 3, Map.of(wool, 2L));
				if (!lack.isEmpty()) {
					wrong.add(kind + " 3 " + color.getName() + " carpets from 2 of its wool lack " + names(lack));
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong: " + wrong);
		helper.succeed();
	}

	/**
	 * Every wood's planks from nothing name its own log or stem, never the stripped one (crafter.md's raw option is the
	 * log); with only the stripped log, or the wood, there they're still made; bamboo planks from nothing read exactly 18
	 * Bamboo for 3 (crafter.md), and a Stripped Block of Bamboo or a Block of Bamboo that's there still makes 2.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaShortLine1116bWoods"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaShortLine1116bWoods")
	public void everyWoodsPlanksNameTheLogAndStrippedOnesStillMakePlanks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> wrong = new ArrayList<>();
		String[][] woods = {{"oak", "log", "wood"}, {"spruce", "log", "wood"}, {"birch", "log", "wood"}, {"jungle", "log", "wood"},
			{"acacia", "log", "wood"}, {"dark_oak", "log", "wood"}, {"mangrove", "log", "wood"}, {"cherry", "log", "wood"},
			{"crimson", "stem", "hyphae"}, {"warped", "stem", "hyphae"}};
		for (String[] w : woods) {
			Item planks = item(w[0] + "_planks");
			Item log = item(w[0] + "_" + w[1]);
			Item stripped = item("stripped_" + w[0] + "_" + w[1]);
			Item wood = item(w[0] + "_" + w[2]);
			for (Crafting.Kind kind : KINDS) {
				Map<Item, Integer> none = Crafting.lacking(level, kind, planks, 4, Map.of());
				if (!none.equals(Map.of(log, 1))) {
					wrong.add(kind + " 4 " + w[0] + " planks from nothing lack " + names(none) + " (expected 1 " + w[0] + "_" + w[1] + ")");
				}
				Map<Item, Integer> eight = Crafting.lacking(level, kind, planks, 8, Map.of());
				if (!eight.equals(Map.of(log, 2))) {
					wrong.add(kind + " 8 " + w[0] + " planks from nothing lack " + names(eight) + " (expected 2 " + w[0] + "_" + w[1] + ")");
				}
				for (Item there : List.of(stripped, wood, log)) {
					Map<Item, Integer> from = Crafting.lacking(level, kind, planks, 4, Map.of(there, 1L));
					if (!from.isEmpty()) {
						wrong.add(kind + " 4 " + w[0] + " planks from 1 " + BuiltInRegistries.ITEM.getKey(there).getPath() + " lack " + names(from));
					}
				}
			}
		}
		for (Crafting.Kind kind : KINDS) {
			Map<Item, Integer> bamboo = Crafting.lacking(level, kind, Items.BAMBOO_PLANKS, 3, Map.of());
			if (!bamboo.equals(Map.of(Items.BAMBOO, 18))) {
				wrong.add(kind + " 3 bamboo planks from nothing lack " + names(bamboo) + " (crafter.md: 18 Bamboo)");
			}
			for (Item there : List.of(Items.STRIPPED_BAMBOO_BLOCK, Items.BAMBOO_BLOCK)) {
				Map<Item, Integer> from = Crafting.lacking(level, kind, Items.BAMBOO_PLANKS, 2, Map.of(there, 1L));
				if (!from.isEmpty()) {
					wrong.add(kind + " 2 bamboo planks from 1 " + BuiltInRegistries.ITEM.getKey(there).getPath() + " lack " + names(from));
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong: " + wrong);
		helper.succeed();
	}

	/**
	 * Storage blocks both ways: a honey or iron block from what packs into it lacks nothing and from nothing names only
	 * that (never the block itself); the unpacking still works when the bottles or ingots are what's asked for and the
	 * block is there. (A slime block from nothing lacking nothing is open bug B1305's, not tested here.)
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaShortLine1116bPacking"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaShortLine1116bPacking")
	public void storageBlocksPackAndUnpackWithoutGoingRoundTheLoop(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> wrong = new ArrayList<>();
		Map<Item, Integer> honeyNone = Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.HONEY_BLOCK, 1, Map.of());
		if (!honeyNone.equals(Map.of(Items.HONEY_BOTTLE, 4))) {
			wrong.add("1 honey block from nothing lacks " + names(honeyNone) + " (expected 4 honey bottles)");
		}
		Map<Item, Integer> honeyPart = Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.HONEY_BLOCK, 2, Map.of(Items.HONEY_BOTTLE, 5L));
		if (!honeyPart.equals(Map.of(Items.HONEY_BOTTLE, 3))) {
			wrong.add("2 honey blocks from 5 bottles lack " + names(honeyPart) + " (expected 3 honey bottles)");
		}
		Map<Item, Integer> bottles = Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.HONEY_BOTTLE, 4, Map.of(Items.HONEY_BLOCK, 1L, Items.GLASS_BOTTLE, 4L));
		if (!bottles.isEmpty()) {
			wrong.add("4 honey bottles from a honey block and 4 glass bottles lack " + names(bottles) + " (expected nothing)");
		}
		Map<Item, Integer> ironNone = Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.IRON_BLOCK, 1, Map.of());
		if (ironNone.isEmpty() || ironNone.containsKey(Items.IRON_BLOCK) || ironNone.containsKey(Items.IRON_NUGGET)) {
			wrong.add("1 iron block from nothing lacks " + names(ironNone) + " (expected ingots or their ore, never the block or nuggets)");
		}
		Map<Item, Integer> iron = Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.IRON_BLOCK, 1, Map.of(Items.IRON_INGOT, 9L));
		Map<Item, Integer> ingots = Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.IRON_INGOT, 9, Map.of(Items.IRON_BLOCK, 1L));
		if (!iron.isEmpty() || !ingots.isEmpty()) {
			wrong.add("iron: a block from 9 ingots lacks " + names(iron) + ", 9 ingots from a block lack " + names(ingots) + " (expected nothing both ways)");
		}
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong: " + wrong);
		helper.succeed();
	}

	/**
	 * B1307's countable things read singular after 1 and plural after more, in the line itself: "short of 1 Snowball to
	 * make 1 Snow Block", "short of 2 Torchflowers to make 2 Orange Dye", "short of 2 Blocks of Stripped Bamboo ...".
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaShortLine1116bPlurals"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaShortLine1116bPlurals")
	public void countableMakingsReadSingularAtOneAndPluralAbove(GameTestHelper helper) {
		Map<Item, String[]> words = new LinkedHashMap<>();
		words.put(Items.SNOWBALL, new String[] {"Snowball", "Snowballs"});
		words.put(Items.TORCHFLOWER, new String[] {"Torchflower", "Torchflowers"});
		words.put(Items.TOTEM_OF_UNDYING, new String[] {"Totem of Undying", "Totems of Undying"});
		words.put(Items.STRIPPED_BAMBOO_BLOCK, new String[] {"Block of Stripped Bamboo", "Blocks of Stripped Bamboo"});
		words.put(Items.WEATHERED_COPPER_TRAPDOOR, new String[] {"Weathered Copper Trapdoor", "Weathered Copper Trapdoors"});
		words.put(Items.DISC_FRAGMENT_5, new String[] {"Disc Fragment", "Disc Fragments"});
		words.put(Items.HEAVY_CORE, new String[] {"Heavy Core", "Heavy Cores"});
		words.put(Items.CREEPER_HEAD, new String[] {"Creeper Head", "Creeper Heads"});
		List<String> wrong = new ArrayList<>();
		words.forEach((item, w) -> {
			String one = Words.itemName(item, 1).getString();
			String two = Words.itemName(item, 2).getString();
			String many = Words.itemName(item, 64).getString();
			if (!w[0].equals(one) || !w[1].equals(two) || !w[1].equals(many)) {
				wrong.add("1 " + one + " / 2 " + two + " / 64 " + many + " (want 1 " + w[0] + " / 2 " + w[1] + ")");
			}
		});
		String one = JobShortages.lackLine(Map.of(Items.SNOWBALL, 1), 1, Items.SNOW_BLOCK).getString();
		if (!"short of 1 Snowball to make 1 Snow Block".equals(one)) {
			wrong.add(one);
		}
		String torch = JobShortages.lackLine(Map.of(Items.TORCHFLOWER, 2), 2, Items.ORANGE_DYE).getString();
		if (!"short of 2 Torchflowers to make 2 Orange Dye".equals(torch)) {
			wrong.add(torch);
		}
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong: " + wrong);
		helper.succeed();
	}

	/**
	 * What the dyed lines name now (flowers, ink sacs, string...) reads in the plural after a count above one too, unless
	 * it's a word English doesn't count (String, Lapis Lazuli, Bone Meal, Dye, Bamboo, Cocoa Beans, Wool).
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaShortLine1116bDyePlurals"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaShortLine1116bDyePlurals")
	public void whatDyedLinesNameReadsInThePlural(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> wrong = new ArrayList<>();
		for (DyeColor color : DyeColor.values()) {
			for (String kindOf : new String[] {"_wool", "_carpet", "_dye"}) {
				Item target = item(color.getName() + kindOf);
				if (target == Items.WHITE_WOOL) {
					continue;
				}
				for (Map.Entry<Item, Integer> e : Crafting.lacking(level, Crafting.Kind.CRAFTING, target, 3, Map.of()).entrySet()) {
					String path = BuiltInRegistries.ITEM.getKey(e.getKey()).getPath();
					if (e.getValue() < 2 || path.equals("string") || path.equals("lapis_lazuli") || path.equals("bone_meal") || path.endsWith("_dye")
						|| path.equals("bamboo") || path.equals("cocoa_beans") || path.endsWith("_wool")) {
						continue;
					}
					String one = Words.itemName(e.getKey(), 1).getString();
					String many = Words.itemName(e.getKey(), e.getValue()).getString();
					if (one.equals(many) && !wrong.contains(path)) {
						wrong.add(path);
					}
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " things dyed lines name read without a plural: " + wrong);
		helper.succeed();
	}

	/**
	 * Through the player's entry point: a builder short of 3 blue carpets, empty chest, a Shepherd at her loom: the line
	 * ends "to make 3 Blue Carpets", names String or White Wool, never another color, and nothing is made from nothing.
	 */
	//$ gametest_ticks_batch AREA '1400' '"qaShortLine1116bCarpetLine"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaShortLine1116bCarpetLine")
	public void aBuilderShortOfBlueCarpetsReadsWhiteWoolsMakings(GameTestHelper helper) {
		QaJobChainsGameTests.prepare(helper);
		QaJobChainsGameTests.Asker a = QaJobChainsGameTests.asker(helper, Blocks.BLUE_CARPET);
		helper.setBlock(STATION, Blocks.LOOM);
		helper.setBlock(STATION.east(), Blocks.CHEST);
		Villager shepherd = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), shepherd, helper.absolutePos(STATION), PoiTypes.SHEPHERD, VillagerProfession.SHEPHERD);
		helper.runAfterDelay(1200, () -> {
			BuildSite live = BuildSiteManager.get(helper.getLevel()).get(a.site().id());
			helper.assertTrue(live != null, "the build ended with no carpet anywhere");
			Component line = JobShortages.line(helper.getLevel(), live.id(), live.missing());
			String text = line == null ? null : line.getString();
			helper.assertTrue(text != null && text.startsWith("Can't be made yet: short of ") && text.endsWith(" to make 3 Blue Carpets"),
				"the builder's line: '" + text + "'");
			String named = text.substring(0, text.indexOf(" to make "));
			for (String other : new String[] {"Blue Wool", "Blue Carpet", "Black", "Red", "Orange", "Yellow", "Green", "Purple", "Cyan", "Gray", "Pink", "Magenta", "Lime", "Brown"}) {
				helper.assertTrue(!named.contains(other), "the builder's line names " + other + ": '" + text + "'");
			}
			helper.assertTrue(named.contains("String") || named.contains("White Wool"), "the builder's line names no String or White Wool: '" + text + "'");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(shepherd, 0) == 0, "the Shepherd made something from nothing");
			helper.succeed();
		});
	}

	/** Through the player's entry point: a builder short of 3 mangrove planks, empty chest, a Carpenter: "short of 1 Mangrove Log". */
	//$ gametest_ticks_batch AREA '1400' '"qaShortLine1116bMangroveLine"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaShortLine1116bMangroveLine")
	public void aBuilderShortOfMangrovePlanksReadsTheLog(GameTestHelper helper) {
		QaJobChainsGameTests.prepare(helper);
		QaJobChainsGameTests.Asker a = QaJobChainsGameTests.asker(helper, Blocks.MANGROVE_PLANKS);
		helper.setBlock(STATION, ModBlocks.CARPENTERS_BENCH);
		Villager carpenter = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), carpenter, helper.absolutePos(STATION), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		helper.runAfterDelay(1200, () -> {
			BuildSite live = BuildSiteManager.get(helper.getLevel()).get(a.site().id());
			helper.assertTrue(live != null, "the build ended with no mangrove anywhere");
			Component line = JobShortages.line(helper.getLevel(), live.id(), live.missing());
			String text = line == null ? null : line.getString();
			helper.assertTrue("Can't be made yet: short of 1 Mangrove Log to make 3 Mangrove Planks".equals(text), "the builder's line: '" + text
				+ "', expected 'Can't be made yet: short of 1 Mangrove Log to make 3 Mangrove Planks'");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0) == 0, "the Carpenter made something from nothing");
			helper.succeed();
		});
	}

	/**
	 * A builder short of 3 bamboo planks with one Stripped Block of Bamboo in his chest (a player stripped it): the Carpenter
	 * makes the planks from it (stripped blocks are never raw, but a stripped block that's there is still wood).
	 */
	//$ gametest_ticks_batch AREA '3600' '"qaShortLine1116bStrippedBamboo"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaShortLine1116bStrippedBamboo")
	public void aBuilderWithAStrippedBambooBlockGetsHisPlanks(GameTestHelper helper) {
		QaJobChainsGameTests.prepare(helper);
		QaJobChainsGameTests.Asker a = QaJobChainsGameTests.asker(helper, Blocks.BAMBOO_PLANKS, new ItemStack(Items.STRIPPED_BAMBOO_BLOCK, 2));
		helper.setBlock(STATION, ModBlocks.CARPENTERS_BENCH);
		Villager carpenter = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), carpenter, helper.absolutePos(STATION), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		helper.succeedWhen(() -> {
			BuildSite live = BuildSiteManager.get(helper.getLevel()).get(a.site().id());
			int missing = live == null ? 0 : live.missing().getOrDefault(Items.BAMBOO_PLANKS, 0);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(missing == 0, "still missing " + missing + " bamboo planks with " + chest.countItem(Items.STRIPPED_BAMBOO_BLOCK)
				+ " stripped bamboo blocks in the chest; line: " + (live == null ? null : String.valueOf(JobShortages.line(helper.getLevel(), live.id(), live.missing()) == null ? null
				: JobShortages.line(helper.getLevel(), live.id(), live.missing()).getString())));
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0) > 0, "the Carpenter made nothing");
		});
	}

	private static String names(Map<Item, Integer> m) {
		StringBuilder sb = new StringBuilder("{");
		m.forEach((k, v) -> sb.append(BuiltInRegistries.ITEM.getKey(k).getPath()).append('=').append(v).append(' '));
		return sb.append('}').toString();
	}
}
