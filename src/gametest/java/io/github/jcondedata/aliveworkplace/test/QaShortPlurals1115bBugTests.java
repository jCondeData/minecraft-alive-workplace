package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
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
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-b-1115-2137), written from crafter.md (B426/B920: "items named in the plural after a count above one ... and so
 * do the countable things crafters run short of"). A sweep of every job-made thing's shortage line from nothing (base game)
 * names these countable things with no plural in the lang file, so a line reads "short of 12 Snowball", "4 Tuff Slab":
 * snowballs for a snow block, a torchflower for orange dye, the slabs chiseled blocks are crafted from, copper doors and
 * trapdoors for their waxed forms, disc fragments, and more. (Vines read right: Minecraft's own name is "Vines".)
 */
public class QaShortPlurals1115bBugTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos STATION = new BlockPos(14, 2, 2);

	/** Each countable thing a shortage line names reads its plural for a count above one. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaShortPlurals1115bWords"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaShortPlurals1115bWords")
	public void countableMakingsReadThePlural(GameTestHelper helper) {
		Map<Item, String> plural = new LinkedHashMap<>();
		plural.put(Items.SNOWBALL, "Snowballs");
		plural.put(Items.TORCHFLOWER, "Torchflowers");
		plural.put(Items.TUFF_SLAB, "Tuff Slabs");
		plural.put(Items.TUFF_BRICK_SLAB, "Tuff Brick Slabs");
		plural.put(Items.QUARTZ_SLAB, "Quartz Slabs");
		plural.put(Items.PURPUR_SLAB, "Purpur Slabs");
		plural.put(Items.COBBLED_DEEPSLATE_SLAB, "Cobbled Deepslate Slabs");
		plural.put(Items.NETHER_BRICK_SLAB, "Nether Brick Slabs");
		plural.put(Items.EXPOSED_CUT_COPPER_SLAB, "Exposed Cut Copper Slabs");
		plural.put(Items.WEATHERED_CUT_COPPER_SLAB, "Weathered Cut Copper Slabs");
		plural.put(Items.OXIDIZED_CUT_COPPER_SLAB, "Oxidized Cut Copper Slabs");
		plural.put(Items.EXPOSED_COPPER_DOOR, "Exposed Copper Doors");
		plural.put(Items.WEATHERED_COPPER_DOOR, "Weathered Copper Doors");
		plural.put(Items.OXIDIZED_COPPER_DOOR, "Oxidized Copper Doors");
		plural.put(Items.EXPOSED_COPPER_TRAPDOOR, "Exposed Copper Trapdoors");
		plural.put(Items.WEATHERED_COPPER_TRAPDOOR, "Weathered Copper Trapdoors");
		plural.put(Items.OXIDIZED_COPPER_TRAPDOOR, "Oxidized Copper Trapdoors");
		plural.put(Items.DISC_FRAGMENT_5, "Disc Fragments");
		plural.put(Items.GOAT_HORN, "Goat Horns");
		plural.put(Items.CREEPER_HEAD, "Creeper Heads");
		plural.put(Items.WITHER_SKELETON_SKULL, "Wither Skeleton Skulls");
		plural.put(Items.TOTEM_OF_UNDYING, "Totems of Undying");
		plural.put(Items.HEAVY_CORE, "Heavy Cores");
		plural.put(Items.SCULK_SENSOR, "Sculk Sensors");
		List<String> wrong = new ArrayList<>();
		plural.forEach((item, many) -> {
			String got = Words.itemName(item, 4).getString();
			if (!many.equals(got)) {
				wrong.add("4 " + got + " (want 4 " + many + ")");
			}
		});
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " countable makings read without their plural: " + wrong);
		helper.succeed();
	}

	/** The sentences: "short of 12 Snowballs to make 3 Snow Blocks", "short of 3 Vines to make 3 Mossy Cobblestone". */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaShortPlurals1115bLines"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaShortPlurals1115bLines")
	public void theLinesReadThePlural(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		String snow = JobShortages.lackLine(Map.of(Items.SNOWBALL, 12), 3, Items.SNOW_BLOCK).getString();
		if (!"short of 12 Snowballs to make 3 Snow Blocks".equals(snow)) {
			wrong.add(snow);
		}
		String moss = JobShortages.lackLine(Map.of(Items.VINE, 3), 3, Items.MOSSY_COBBLESTONE).getString();
		if (!"short of 3 Vines to make 3 Mossy Cobblestone".equals(moss)) {
			wrong.add(moss);
		}
		helper.assertTrue(wrong.isEmpty(), "lines: " + wrong);
		helper.succeed();
	}

	/** Through the player's entry point: a builder short of 3 snow blocks with an empty chest and a Carpenter. */
	//$ gametest_ticks_batch AREA '1400' '"qaShortPlurals1115bSnow"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaShortPlurals1115bSnow")
	public void aBuilderShortOfSnowBlocksReadsSnowballs(GameTestHelper helper) {
		QaJobChainsGameTests.prepare(helper);
		QaJobChainsGameTests.Asker a = QaJobChainsGameTests.asker(helper, Blocks.SNOW_BLOCK);
		helper.setBlock(STATION, ModBlocks.CARPENTERS_BENCH);
		Villager carpenter = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), carpenter, helper.absolutePos(STATION), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		helper.runAfterDelay(1200, () -> {
			BuildSite live = BuildSiteManager.get(helper.getLevel()).get(a.site().id());
			helper.assertTrue(live != null, "the build ended with no snow");
			Component line = JobShortages.line(helper.getLevel(), live.id(), live.missing());
			String text = line == null ? null : line.getString();
			helper.assertTrue("Can't be made yet: short of 12 Snowballs to make 3 Snow Blocks".equals(text), "the builder's line: '" + text
				+ "', expected 'Can't be made yet: short of 12 Snowballs to make 3 Snow Blocks'");
			helper.succeed();
		});
	}
}
