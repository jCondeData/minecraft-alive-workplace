package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA past B1278 and B1277 (lane-c-1115-1252), written from crafter.md: "The line names what the chest is short of, not
 * some other way to make it ... It goes further down only when that uses something that's there"; "a book or bookshelves
 * with no leather at all name Leather, not Rabbit Hide; with some hide there it still names the rest of the hide"; "a
 * piston names Redstone Dust, not Redstone Ore"; and B489: a line never names an ore a player only gets with Silk Touch.
 */
public class QaShortLineGathered1115c4GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final TagKey<Item> ORES = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "ores"));
	private static final BlockPos STATION = new BlockPos(14, 2, 2);

	/** The leather boundary from both sides: 0, 1, 4 hides, leather there, leather asked for itself (several). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void leatherAndHideFromBothSides(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Crafting.Kind k = Crafting.Kind.CRAFTING;
		Map<String, String> wrong = new LinkedHashMap<>();
		check(wrong, "a book from 3 paper and 1 rabbit hide", Crafting.lacking(level, k, Items.BOOK, 1, Map.of(Items.PAPER, 3L, Items.RABBIT_HIDE, 1L)),
			Map.of(Items.RABBIT_HIDE, 3));
		check(wrong, "a book from 3 paper and 4 rabbit hides", Crafting.lacking(level, k, Items.BOOK, 1, Map.of(Items.PAPER, 3L, Items.RABBIT_HIDE, 4L)), Map.of());
		check(wrong, "a book from 3 paper and 1 leather", Crafting.lacking(level, k, Items.BOOK, 1, Map.of(Items.PAPER, 3L, Items.LEATHER, 1L)), Map.of());
		check(wrong, "2 books from 6 paper and 1 leather", Crafting.lacking(level, k, Items.BOOK, 2, Map.of(Items.PAPER, 6L, Items.LEATHER, 1L)),
			Map.of(Items.LEATHER, 1));
		check(wrong, "3 books from 9 paper and no leather", Crafting.lacking(level, k, Items.BOOK, 3, Map.of(Items.PAPER, 9L)), Map.of(Items.LEATHER, 3));
		check(wrong, "2 leather from nothing", Crafting.lacking(level, k, Items.LEATHER, 2, Map.of()), Map.of(Items.RABBIT_HIDE, 8));
		check(wrong, "2 leather from 5 rabbit hides", Crafting.lacking(level, k, Items.LEATHER, 2, Map.of(Items.RABBIT_HIDE, 5L)), Map.of(Items.RABBIT_HIDE, 3));
		check(wrong, "a leather from 4 rabbit hides", Crafting.lacking(level, k, Items.LEATHER, 1, Map.of(Items.RABBIT_HIDE, 4L)), Map.of());
		check(wrong, "an item frame from 8 sticks and 4 rabbit hides", Crafting.lacking(level, k, Items.ITEM_FRAME, 1,
			Map.of(Items.STICK, 8L, Items.RABBIT_HIDE, 4L)), Map.of());
		helper.assertTrue(wrong.isEmpty(), "wrong lines: " + wrong);
		helper.succeed();
	}

	/** Packing into a block is untouched: blocks of iron and gold from nothing still go down to the raw metal (B489). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void metalBlocksAndToolsStillNameTheRawMetal(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Crafting.Kind k = Crafting.Kind.WORKSHOP;
		Map<String, String> wrong = new LinkedHashMap<>();
		check(wrong, "a block of iron from nothing", Crafting.lacking(level, k, Items.IRON_BLOCK, 1, Map.of()), Map.of(Items.RAW_IRON, 9));
		check(wrong, "a block of gold from nothing", Crafting.lacking(level, k, Items.GOLD_BLOCK, 1, Map.of()), Map.of(Items.RAW_GOLD, 9));
		check(wrong, "a block of iron from 9 iron ingots", Crafting.lacking(level, k, Items.IRON_BLOCK, 1, Map.of(Items.IRON_INGOT, 9L)), Map.of());
		check(wrong, "a bucket from nothing", Crafting.lacking(level, k, Items.BUCKET, 1, Map.of()), Map.of(Items.RAW_IRON, 3));
		helper.assertTrue(wrong.isEmpty(), "wrong lines: " + wrong);
		helper.succeed();
	}

	/** Every redstone part from nothing names something, never an ore; those using redstone name Redstone Dust. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void redstonePartsFromNothingNameNoOre(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> wrong = new java.util.ArrayList<>();
		for (Item item : List.of(Items.REPEATER, Items.COMPARATOR, Items.OBSERVER, Items.DISPENSER, Items.DROPPER, Items.REDSTONE_TORCH, Items.STICKY_PISTON,
				Items.REDSTONE_LAMP, Items.TORCH)) {
			for (Crafting.Kind kind : List.of(Crafting.Kind.WORKSHOP, Crafting.Kind.CRAFTING)) {
				Map<Item, Integer> lack = Crafting.lacking(level, kind, item, 1, Map.of());
				boolean redstone = item != Items.TORCH;
				if (lack.isEmpty() || lack.keySet().stream().anyMatch(i -> i.builtInRegistryHolder().is(ORES)) || (redstone && !lack.containsKey(Items.REDSTONE))) {
					wrong.add(item + " (" + kind + ") lacks " + lack);
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "named an ore, nothing, or no Redstone Dust: " + wrong);
		helper.succeed();
	}

	/** Redstone (or its ore) there is used: a piston with the dust, or with an ore the forge can fire, isn't short of redstone. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void redstoneOrItsOreThereIsUsed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<Item, Integer> dust = Crafting.lacking(level, Crafting.Kind.WORKSHOP, Items.PISTON, 1, Map.of(Items.REDSTONE, 1L));
		Map<Item, Integer> ore = Crafting.lacking(level, Crafting.Kind.WORKSHOP, Items.PISTON, 1, Map.of(Items.REDSTONE_ORE, 1L));
		helper.assertTrue(!dust.containsKey(Items.REDSTONE) && !dust.isEmpty(), "a piston from 1 redstone lacks " + dust + ", expected no Redstone Dust");
		helper.assertTrue(!ore.containsKey(Items.REDSTONE) && !ore.containsKey(Items.REDSTONE_ORE) && !ore.isEmpty(),
			"a piston at the forge from 1 redstone ore lacks " + ore + ", expected the ore fired into its dust");
		Map<Item, Integer> full = Crafting.lacking(level, Crafting.Kind.WORKSHOP, Items.PISTON, 1,
			Map.of(Items.REDSTONE, 1L, Items.IRON_INGOT, 1L, Items.COBBLESTONE, 4L, Items.OAK_PLANKS, 3L));
		helper.assertTrue(full.isEmpty(), "a piston with all its makings lacks " + full);
		helper.succeed();
	}

	/** Through the player's entry point: a Librarian, three bookshelves, the cane, planks and 1 rabbit hide: "35 Rabbit Hides". */
	//$ gametest_ticks_batch AREA '1600' '"qaShortLineHide1115c4"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaShortLineHide1115c4")
	public void aLibrarianWithOneHideNamesTheRestOfTheHide(GameTestHelper helper) {
		ItemStack[] items = {new ItemStack(Items.SUGAR_CANE, 27), new ItemStack(Items.OAK_PLANKS, 18), new ItemStack(Items.RABBIT_HIDE, 1)};
		QaJobChainsGameTests.prepare(helper);
		QaJobChainsGameTests.Asker a = QaJobChainsGameTests.asker(helper, Blocks.BOOKSHELF, items);
		Villager librarian = QaJobChainsGameTests.librarian(helper);
		helper.succeedWhen(() -> {
			Component line = JobShortages.line(helper.getLevel(), a.site().id(), a.site().missing());
			String got = line == null ? null : line.getString();
			String want = "Can't be made yet: short of 35 Rabbit Hides to make 3 Bookshelves";
			helper.assertTrue(want.equals(got), "the builder's shortage line: " + got + " (want '" + want + "')");
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.SUGAR_CANE) == 27 && chest.countItem(Items.OAK_PLANKS) == 18 && chest.countItem(Items.RABBIT_HIDE) == 1,
				"the chest was touched");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(librarian).isEmpty(), "the librarian took something");
		});
	}

	/** Through the player's entry point: a builder short of 3 pistons, nothing in his chest, a Tinkerer at his bench. */
	//$ gametest_ticks_batch AREA '1600' '"qaShortLinePiston1115c4"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaShortLinePiston1115c4")
	public void aPistonShortReadsRedstoneDustNotOre(GameTestHelper helper) {
		QaJobChainsGameTests.prepare(helper);
		QaJobChainsGameTests.Asker a = QaJobChainsGameTests.asker(helper, Blocks.PISTON, new ItemStack(Items.COAL, 1));
		helper.setBlock(STATION, ModBlocks.TINKERS_BENCH);
		Villager t = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), t, helper.absolutePos(STATION), ModVillagers.TINKERS_BENCH_POI, ModVillagers.TINKERER);
		helper.succeedWhen(() -> {
			Component line = JobShortages.line(helper.getLevel(), a.site().id(), a.site().missing());
			String got = line == null ? null : line.getString();
			helper.assertTrue(got != null && got.startsWith("Can't be made yet: short of ") && got.endsWith(" to make 3 Pistons"),
				"the builder's shortage line: " + got);
			helper.assertTrue(got.contains("3 Redstone Dust") && !got.contains("Ore"), "the line should name 3 Redstone Dust and no ore: " + got);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.COAL) == 1, "the chest's coal was touched");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(t).isEmpty(), "the tinkerer took something");
		});
	}

	private static void check(Map<String, String> wrong, String what, Map<Item, Integer> got, Map<Item, Integer> expected) {
		if (!got.equals(expected)) {
			wrong.put(what, "lacks " + got + ", expected " + expected);
		}
	}
}
