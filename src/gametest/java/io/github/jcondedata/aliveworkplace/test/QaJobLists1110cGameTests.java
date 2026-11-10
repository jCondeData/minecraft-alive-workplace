package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.craft.CraftJobs;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.store.StorehouseBoard;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (qa-lane-c-1110-1252) for ROADMAP 46.1, each job's own list, and its fixed bugs B1132-B1134, written from the
 * Milestone 46 table and rules. Not repeated here: what JobListsGameTests and QaJobCraftLists(Bug)GameTests check (the
 * owner's five, the table row by row, the ingredient rule's order, every stonecutter cut, the first hire lines, oak and
 * spruce stripped logs, red concrete). New here: a pack's tag (the test mod's data adds a fire charge, on no list, to
 * {@code crafts/cleric}), a hire line in the plural for more of every job's list (concrete and its powder of other colors,
 * mass nouns kept as named, leather pants and boots not doubled), the gatherer of every wood, ore, sand, clay and wool,
 * stripped logs of other woods and the nether stems, every colored thing of all sixteen colors on its own job's list and
 * never the Leatherworker's, every listed thing on exactly one list with a job that makes it, a Leatherworker by a builder
 * short of red wool leaving it alone, and the Storehouse board (the player's own screen) for stripped birch and concrete.
 */
public class QaJobLists1110cGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos STATION = new BlockPos(14, 2, 2);
	private static final BlockPos ROW = new BlockPos(6, 2, 9);

	/**
	 * "The split is data ... so a pack can change it": a fire charge is on no job's list in the mod, and the test mod's own
	 * data (as a pack would) puts it on the Cleric's. Then the Cleric makes it, no one else does, and the Storehouse and
	 * the shortage line name him.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '700' '"qaJobLists1110cPack"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 700, batch = "qaJobLists1110cPack")
	public void aPacksTagPutsAThingOnAJobsList(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		Item charge = Items.FIRE_CHARGE;
		helper.assertTrue(CraftJobs.tagged(charge) == CraftJobs.Job.CLERIC, "the pack's tag isn't read: " + CraftJobs.tagged(charge));
		helper.assertTrue(CraftJobs.of(level, charge) == CraftJobs.Job.CLERIC, "on " + CraftJobs.of(level, charge) + "'s list");
		for (CraftJobs.Job job : CraftJobs.Job.values()) {
			helper.assertTrue(CraftJobs.makes(level, job, charge) == (job == CraftJobs.Job.CLERIC), "the " + job + " makes fire charges: " + CraftJobs.makes(level, job, charge));
		}
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		assertLines(helper, pos, charge, "Hire a Cleric to make it");
		UUID site = UUID.randomUUID();
		JobShortages.line(level, site, Map.of(charge, 2));
		helper.runAfterDelay(610, () -> {
			Component line = JobShortages.line(level, site, Map.of(charge, 2));
			String text = line == null ? null : line.getString();
			JobShortages.forget(site);
			// no plural key for a thing a pack adds: the name as the game gives it (Words.itemName), never another job
			helper.assertTrue(text != null && text.startsWith("Hire a Cleric to make Fire Charge"), "the shortage line: '" + text + "'");
			Villager cleric = worker(helper, VillagerProfession.CLERIC, "Cy", new BlockPos(1, 2, 2));
			assertLines(helper, pos, charge, "Made by: the Cleric (Cy)");
			cleric.discard();
			helper.succeed();
		});
	}

	/** The "Hire a ..." line for more of every job's list, countable things in the plural and mass nouns as named. */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '700' '"qaJobLists1110cHire"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 700, batch = "qaJobLists1110cHire")
	public void everyJobsHireLineForMoreOfItsList(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<Item, String> single = new LinkedHashMap<>();
		single.put(Items.OAK_DOOR, "Hire a Carpenter to make Oak Doors");
		single.put(Items.SPRUCE_FENCE, "Hire a Carpenter to make Spruce Fences");
		single.put(Items.CAMPFIRE, "Hire a Carpenter to make Campfires");
		single.put(Items.STONE_BRICK_STAIRS, "Hire a Mason to make Stone Brick Stairs");
		single.put(Items.BRICK, "Hire a Mason to make Bricks");
		single.put(Items.CUT_SANDSTONE, "Hire a Mason to make Cut Sandstone");
		single.put(Items.LIME_CONCRETE, "Hire a Mason to make Lime Concrete");
		single.put(Items.BLACK_CONCRETE_POWDER, "Hire a Mason to make Black Concrete Powder");
		single.put(Items.PURPLE_GLAZED_TERRACOTTA, "Hire a Mason to make Purple Glazed Terracotta");
		single.put(Items.TINTED_GLASS, "Hire a Glassblower to make Tinted Glass");
		single.put(Items.TRIPWIRE_HOOK, "Hire a Tinkerer to make Tripwire Hooks");
		single.put(Items.DAYLIGHT_DETECTOR, "Hire a Tinkerer to make Daylight Detectors");
		single.put(Items.IRON_BARS, "Hire a Tinkerer to make Iron Bars");
		single.put(Items.WHITE_CARPET, "Hire a Shepherd to make White Carpets");
		single.put(Items.MOSS_CARPET, "Hire a Florist to make Moss Carpets");
		single.put(Items.BLUE_DYE, "Hire a Florist to make Blue Dye");
		single.put(Items.BLAZE_POWDER, "Hire a Cleric to make Blaze Powder");
		single.put(Items.RED_CANDLE, "Hire a Beekeeper to make Red Candles");
		single.put(Items.LEATHER_HELMET, "Hire a Leatherworker to make Leather Caps");
		single.put(Items.LEATHER_LEGGINGS, "Hire a Leatherworker to make Leather Pants");
		single.put(Items.LEATHER_BOOTS, "Hire a Leatherworker to make Leather Boots");
		single.put(Items.LEATHER, "Hire a Leatherworker to make Leather");
		single.put(Items.LECTERN, "Hire a Librarian to make Lecterns");
		single.put(Items.BOOK, "Hire a Librarian to make Books");
		Map<Item, UUID> sites = new LinkedHashMap<>();
		single.keySet().forEach(i -> sites.put(i, UUID.randomUUID()));
		// one job's two things, the most missing first: concrete and another color's powder on one Mason line
		UUID concrete = UUID.randomUUID();
		Map<Item, Integer> concreteMissing = new LinkedHashMap<>();
		concreteMissing.put(Items.BLACK_CONCRETE_POWDER, 2);
		concreteMissing.put(Items.LIME_CONCRETE, 5);
		sites.forEach((item, site) -> JobShortages.line(level, site, Map.of(item, 3)));
		JobShortages.line(level, concrete, concreteMissing);
		helper.runAfterDelay(610, () -> {
			List<String> wrong = new ArrayList<>();
			sites.forEach((item, site) -> {
				Component line = JobShortages.line(level, site, Map.of(item, 3));
				String text = line == null ? null : line.getString();
				JobShortages.forget(site);
				if (!single.get(item).equals(text)) {
					wrong.add("'" + text + "', expected '" + single.get(item) + "'");
				}
			});
			Component both = JobShortages.line(level, concrete, concreteMissing);
			JobShortages.forget(concrete);
			String text = both == null ? null : both.getString();
			if (!"Hire a Mason to make Lime Concrete, Black Concrete Powder".equals(text)) {
				wrong.add("concrete and powder: '" + text + "'");
			}
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong: " + String.join("; ", wrong));
			helper.succeed();
		});
	}

	/**
	 * "Logs, ore, sand, clay and wool name their gatherers": every wood's log and the nether stems, ores of other metals
	 * and depths, gravel, wool of other colors; then by the gatherer's name, as "the Shepherd" with no name, and never a
	 * child or one out of reach.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaJobLists1110cGather"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaJobLists1110cGather")
	public void everyLogOreSandClayAndWoolNamesItsGatherer(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		List<String> wrong = new ArrayList<>();
		for (Item log : List.of(Items.BIRCH_LOG, Items.JUNGLE_LOG, Items.ACACIA_LOG, Items.DARK_OAK_LOG, Items.MANGROVE_LOG, Items.CRIMSON_STEM,
				Items.WARPED_STEM)) {
			lines(helper, pos, log, "Hire a Lumberjack to cut it", wrong);
		}
		for (Item dug : List.of(Items.DIAMOND_ORE, Items.DEEPSLATE_COPPER_ORE, Items.REDSTONE_ORE, Items.DEEPSLATE_EMERALD_ORE, Items.LAPIS_ORE,
				Items.RAW_COPPER, Items.GRAVEL, Items.SAND, Items.CLAY_BALL)) {
			lines(helper, pos, dug, "Hire a Miner to dig it", wrong);
		}
		for (Item wool : List.of(Items.BLUE_WOOL, Items.BLACK_WOOL, Items.LIGHT_GRAY_WOOL)) {
			lines(helper, pos, wool, "Hire a Shepherd to shear it", wrong);
		}
		// a child Lumberjack beside it and a grown one out of reach don't gather here
		Villager kid = worker(helper, ModVillagers.LUMBERJACK, "Twig", new BlockPos(1, 2, 2));
		kid.setAge(-24000);
		worker(helper, ModVillagers.LUMBERJACK, "Far", new BlockPos(1, 2, 12));
		lines(helper, pos, Items.BIRCH_LOG, "Hire a Lumberjack to cut it", wrong);
		worker(helper, ModVillagers.LUMBERJACK, "Bo", new BlockPos(1, 2, 4));
		lines(helper, pos, Items.CRIMSON_STEM, "Gathered by: the Lumberjack (Bo)", wrong);
		worker(helper, VillagerProfession.SHEPHERD, null, new BlockPos(2, 2, 2));
		lines(helper, pos, Items.BLUE_WOOL, "Gathered by: the Shepherd", wrong);
		worker(helper, ModVillagers.MINER, "Rocky", new BlockPos(2, 2, 3));
		lines(helper, pos, Items.SAND, "Gathered by: the Miner (Rocky)", wrong);
		lines(helper, pos, Items.CLAY_BALL, "Gathered by: the Miner (Rocky)", wrong);
		lines(helper, pos, Items.DIAMOND_ORE, "Gathered by: the Miner (Rocky)", wrong);
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong: " + String.join("; ", wrong));
		helper.succeed();
	}

	/**
	 * B1133 from other woods: the Lumberjack strips birch, cherry, mangrove logs and the nether stems too, so the
	 * Storehouse names him for each; stripped wood blocks (made from stripped logs) have a job too, never "No job ...".
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaJobLists1110cStripped"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaJobLists1110cStripped")
	public void strippedLogsOfEveryWoodNameTheLumberjack(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		List<String> wrong = new ArrayList<>();
		List<Item> stripped = List.of(Items.STRIPPED_BIRCH_LOG, Items.STRIPPED_CHERRY_LOG, Items.STRIPPED_MANGROVE_LOG, Items.STRIPPED_DARK_OAK_LOG,
			Items.STRIPPED_CRIMSON_STEM, Items.STRIPPED_WARPED_STEM);
		for (Item item : stripped) {
			lines(helper, pos, item, "Hire a Lumberjack to make it", wrong);
		}
		for (Item wood : List.of(Items.STRIPPED_BIRCH_WOOD, Items.STRIPPED_CHERRY_WOOD, Items.STRIPPED_CRIMSON_HYPHAE)) {
			String got = joined(helper, pos, wood);
			if (got.equals("No job makes or gathers this") || !got.startsWith("Hire a ")) {
				wrong.add(id(wood) + ": '" + got + "'");
			}
		}
		// a Carpenter near doesn't claim the stripped logs; a Lumberjack by name does
		worker(helper, ModVillagers.CARPENTER, "Ada", new BlockPos(1, 2, 2));
		lines(helper, pos, Items.STRIPPED_CHERRY_LOG, "Hire a Lumberjack to make it", wrong);
		worker(helper, ModVillagers.LUMBERJACK, "Bo", new BlockPos(1, 2, 3));
		for (Item item : stripped) {
			lines(helper, pos, item, "Made by: the Lumberjack (Bo)", wrong);
		}
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong: " + String.join("; ", wrong));
		helper.succeed();
	}

	/**
	 * Every colored thing of all sixteen colors is on the job the table gives it (wool, carpets, beds and banners the
	 * Shepherd's; stained glass and panes the Glassblower's; terracotta, glazed terracotta, concrete and its powder the
	 * Mason's; candles the Beekeeper's; dyes the Florist's), has a job that makes it today, and none is the
	 * Leatherworker's ("no longer dyes wool, glass or terracotta").
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyColorOfEveryKindIsOnItsOwnJobsList(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<String, CraftJobs.Job> kinds = new LinkedHashMap<>();
		kinds.put("wool", CraftJobs.Job.SHEPHERD);
		kinds.put("carpet", CraftJobs.Job.SHEPHERD);
		kinds.put("bed", CraftJobs.Job.SHEPHERD);
		kinds.put("banner", CraftJobs.Job.SHEPHERD);
		kinds.put("stained_glass", CraftJobs.Job.GLASSBLOWER);
		kinds.put("stained_glass_pane", CraftJobs.Job.GLASSBLOWER);
		kinds.put("terracotta", CraftJobs.Job.MASON);
		kinds.put("glazed_terracotta", CraftJobs.Job.MASON);
		kinds.put("concrete", CraftJobs.Job.MASON);
		kinds.put("concrete_powder", CraftJobs.Job.MASON);
		kinds.put("candle", CraftJobs.Job.BEEKEEPER);
		kinds.put("dye", CraftJobs.Job.FLORIST);
		List<String> wrong = new ArrayList<>();
		int checked = 0;
		for (DyeColor color : DyeColor.values()) {
			for (Map.Entry<String, CraftJobs.Job> kind : kinds.entrySet()) {
				ResourceLocation rl = ResourceLocation.withDefaultNamespace(color.getName() + "_" + kind.getKey());
				Item item = BuiltInRegistries.ITEM.get(rl);
				if (item == Items.AIR) {
					wrong.add(rl + " doesn't exist");
					continue;
				}
				checked++;
				CraftJobs.Job of = CraftJobs.of(level, item);
				CraftJobs.Job maker = CraftJobs.maker(level, item);
				if (of != kind.getValue()) {
					wrong.add(rl + " is on " + of + "'s list, not " + kind.getValue() + "'s");
				}
				if (maker == null || maker.profession() == null) {
					wrong.add(rl + " has no job that makes it");
				}
				if (maker == CraftJobs.Job.LEATHERWORKER || CraftJobs.makes(level, CraftJobs.Job.LEATHERWORKER, item)) {
					wrong.add("the Leatherworker still makes " + rl);
				}
			}
		}
		helper.assertTrue(checked == 16 * kinds.size(), "checked " + checked);
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong: " + String.join("; ", wrong));
		helper.succeed();
	}

	/**
	 * The invariant from the lists' side: every list has something on it, every thing on a list is the first such list's
	 * job's by {@code CraftJobs.of} and made by exactly one job today (the table: "and nothing else"), a real villager job.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyListedThingIsOnOneListAndHasAMaker(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> wrong = new ArrayList<>();
		Map<CraftJobs.Job, Integer> sizes = new LinkedHashMap<>();
		for (Item item : BuiltInRegistries.ITEM) {
			List<CraftJobs.Job> on = new ArrayList<>();
			for (CraftJobs.Job job : CraftJobs.Job.values()) {
				if (item.builtInRegistryHolder().is(job.tag())) {
					on.add(job);
					sizes.merge(job, 1, Integer::sum);
				}
			}
			if (on.isEmpty()) {
				continue;
			}
			// the table puts a lever with the Carpenter while the Tinkerer's own tag (tinkering) holds it too: an item on two
			// lists is the first one's, and only that job makes it
			long makers = java.util.Arrays.stream(CraftJobs.Job.values()).filter(j -> CraftJobs.makes(level, j, item)).count();
			if (makers != 1) {
				wrong.add(id(item) + " (on " + on + "'s lists) is made by " + makers + " jobs");
			}
			if (CraftJobs.of(level, item) != on.get(0)) {
				wrong.add(id(item) + " is " + CraftJobs.of(level, item) + "'s, its tag says " + on.get(0));
			}
			CraftJobs.Job maker = CraftJobs.maker(level, item);
			if (maker == null || !maker.craftsNow() || maker.profession() == null) {
				wrong.add(id(item) + " is made today by " + maker);
			}
		}
		for (CraftJobs.Job job : CraftJobs.Job.values()) {
			if (sizes.getOrDefault(job, 0) == 0) {
				wrong.add("the " + job + "'s list is empty");
			}
		}
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong: " + String.join("; ", wrong));
		helper.succeed();
	}

	/**
	 * Through a builder's real request: a Leatherworker (the dyer before 46.1) by a builder short of red wool, with white
	 * wool and red dye in the chest, dyes nothing and takes nothing, and the builder's line names the Shepherd to hire.
	 */
	//$ gametest_ticks_batch AREA '1400' '"qaJobLists1110cLeatherworker"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaJobLists1110cLeatherworker")
	public void aLeatherworkerNoLongerDyesWoolForABuilder(GameTestHelper helper) {
		prepare(helper);
		BuildSite site = asker(helper, Blocks.RED_WOOL, new ItemStack(Items.WHITE_WOOL, 8), new ItemStack(Items.RED_DYE, 2));
		helper.setBlock(STATION, Blocks.CAULDRON);
		Villager dyer = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), dyer, helper.absolutePos(STATION), PoiTypes.LEATHERWORKER, VillagerProfession.LEATHERWORKER);
		helper.runAfterDelay(200, () -> JobShortages.line(helper.getLevel(), site.id(), site.missing(), helper.absolutePos(BENCH)));
		helper.runAfterDelay(1100, () -> {
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.WHITE_WOOL) == 8 && chest.countItem(Items.RED_DYE) == 2,
				"the Leatherworker took wool or dye: " + chest.countItem(Items.WHITE_WOOL) + " wool, " + chest.countItem(Items.RED_DYE) + " dye");
			helper.assertTrue(chest.countItem(Items.RED_WOOL) == 0, "red wool was made");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(dyer, 0) == 0, "the Leatherworker made " + ModAttachments.ITEMS_CRAFTED.getOrElse(dyer, 0));
			Component line = JobShortages.line(helper.getLevel(), site.id(), site.missing(), helper.absolutePos(BENCH));
			String text = line == null ? null : line.getString();
			helper.assertTrue("Hire a Shepherd to make Red Wool".equals(text), "the builder's line: '" + text + "'");
			helper.succeed();
		});
	}

	/** The player's own screen: the Storehouse board, for a builder short of stripped birch logs, names the Lumberjack. */
	//$ gametest_ticks_batch AREA '700' '"qaJobLists1110cBoardStripped"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "qaJobLists1110cBoardStripped")
	public void theBoardNamesTheLumberjackForStrippedBirch(GameTestHelper helper) {
		prepare(helper);
		BuildSite site = asker(helper, Blocks.STRIPPED_BIRCH_LOG);
		BlockPos storehouse = new BlockPos(14, 2, 6);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		worker(helper, ModVillagers.LUMBERJACK, "Bo", new BlockPos(1, 2, 1));
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(site.status() == BuildSite.Status.WAITING_FOR_MATERIALS, "the builder should be waiting: " + site.status());
			String lore = lore(helper, storehouse, Items.STRIPPED_BIRCH_LOG);
			helper.assertTrue(lore.contains("Made by: the Lumberjack (Bo)"), "the stripped logs' lines: " + lore);
			helper.assertTrue(!lore.contains("No job makes or gathers this"), "the stripped logs' lines: " + lore);
			helper.succeed();
		});
	}

	/** The player's own screen: the board, for a builder short of lime concrete with no Mason, says to hire one. */
	//$ gametest_ticks_batch AREA '700' '"qaJobLists1110cBoardConcrete"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "qaJobLists1110cBoardConcrete")
	public void theBoardSaysToHireAMasonForConcrete(GameTestHelper helper) {
		prepare(helper);
		BuildSite site = asker(helper, Blocks.LIME_CONCRETE);
		BlockPos storehouse = new BlockPos(14, 2, 6);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(site.status() == BuildSite.Status.WAITING_FOR_MATERIALS, "the builder should be waiting: " + site.status());
			String lore = lore(helper, storehouse, Items.LIME_CONCRETE);
			helper.assertTrue(lore.contains("Hire a Mason to make it"), "the concrete's lines: " + lore);
			helper.succeed();
		});
	}

	private static String lore(GameTestHelper helper, BlockPos storehouse, Item item) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ChoiceMenu board = StorehouseBoard.boardForTest(player, helper.absolutePos(storehouse));
		for (int i = 0; i < ChoiceMenu.SIZE; i++) {
			if (board.icon(i).is(item)) {
				var lines = board.icon(i).get(DataComponents.LORE);
				return lines == null ? "" : lines.lines().stream().map(Component::getString).collect(Collectors.joining(" | "));
			}
		}
		throw new GameTestAssertException("the board doesn't show " + id(item));
	}

	private static String joined(GameTestHelper helper, BlockPos pos, Item item) {
		return CraftJobs.storeLines(helper.getLevel(), pos, 8, item).stream().map(Component::getString).collect(Collectors.joining(" | "));
	}

	private static void lines(GameTestHelper helper, BlockPos pos, Item item, String expected, List<String> wrong) {
		String got = joined(helper, pos, item);
		if (!got.equals(expected)) {
			wrong.add(id(item) + ": '" + got + "', expected '" + expected + "'");
		}
	}

	private static void assertLines(GameTestHelper helper, BlockPos pos, Item item, String expected) {
		String got = joined(helper, pos, item);
		helper.assertTrue(got.equals(expected), "for " + id(item) + ": '" + got + "', expected '" + expected + "'");
	}

	private static Villager worker(GameTestHelper helper, VillagerProfession profession, String name, BlockPos at) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(profession));
		if (name != null) {
			v.setCustomName(Component.literal(name));
		}
		return v;
	}

	private static String id(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	private static void prepare(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
	}

	private static BuildSite asker(GameTestHelper helper, Block block, ItemStack... items) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < 3; x++) {
			helper.setBlock(ROW.offset(x, 0, 0), block);
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test",
			"qa_lists_1110c_" + BuiltInRegistries.BLOCK.getKey(block).getPath() + "_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(ROW), new Vec3i(3, 1, 1), false, Blocks.STRUCTURE_VOID);
		for (int x = 0; x < 3; x++) {
			helper.setBlock(ROW.offset(x, 0, 0), Blocks.AIR);
		}
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container container = helper.getBlockEntity(CHEST);
		for (int i = 0; i < items.length; i++) {
			container.setItem(i, items[i].copy());
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.south());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, id, new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ROW),
			Rotation.NONE, Mirror.NONE));
		if (site.plan(level) == null) {
			throw new GameTestAssertException("blueprint not found: " + id);
		}
		return site;
	}
}
