package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.craft.CraftJobs;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (qa-lane-c-1110-0353) for ROADMAP 46.1, each job's own list, written from the Milestone 46 table and rules: every
 * job gets the things the table lists and no other job's, the tag wins over the ingredient rule, the ingredient rule's
 * order (logs/planks, stone/bricks, ingots, wool/dye, else wood), every stonecutter cut is the Mason's, the "Hire a ..."
 * line groups by job in the plural, the Storehouse's lines for every gatherer and for workers out of reach, and through a
 * builder's real request the Mason (the Glassblower's stand-in) makes stained glass while a Carpenter leaves it and
 * lanterns alone.
 */
public class QaJobCraftListsGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos STATION = new BlockPos(14, 2, 2);
	private static final BlockPos ROW = new BlockPos(6, 2, 9);

	private record Asker(Villager builder, BuildSite site, BuildPlan plan, BlockPos chest) {
	}

	/** The table, item by item: each thing it names is on its job's list (CraftJobs.of), and that job is nobody else. */
	private static Map<Item, CraftJobs.Job> table() {
		Map<Item, CraftJobs.Job> t = new LinkedHashMap<>();
		for (Item i : List.of(Items.OAK_PLANKS, Items.OAK_SLAB, Items.OAK_STAIRS, Items.OAK_FENCE, Items.OAK_FENCE_GATE, Items.OAK_DOOR,
				Items.OAK_TRAPDOOR, Items.OAK_SIGN, Items.OAK_HANGING_SIGN, Items.LADDER, Items.CHEST, Items.BARREL, Items.OAK_BOAT,
				Items.CRAFTING_TABLE, Items.TORCH, Items.CAMPFIRE, Items.SCAFFOLDING, Items.ARMOR_STAND, Items.LEVER, Items.GRINDSTONE,
				Items.SMOKER, Items.BEEHIVE, Items.NOTE_BLOCK, Items.SPRUCE_STAIRS, Items.BIRCH_DOOR, ModBlocks.SHOP_COUNTER.asItem())) {
			t.put(i, CraftJobs.Job.CARPENTER);
		}
		for (Item i : List.of(Items.STONE_BRICKS, Items.STONE_BRICK_STAIRS, Items.STONE_SLAB, Items.COBBLESTONE_WALL, Items.BRICKS,
				Items.BRICK, Items.TERRACOTTA, Items.RED_TERRACOTTA, Items.BLUE_TERRACOTTA, Items.RED_GLAZED_TERRACOTTA, Items.RED_CONCRETE_POWDER,
				Items.RED_CONCRETE, Items.WHITE_CONCRETE, Items.SANDSTONE, Items.CUT_SANDSTONE, Items.MOSSY_COBBLESTONE, Items.MOSSY_STONE_BRICKS)) {
			t.put(i, CraftJobs.Job.MASON);
		}
		for (Item i : List.of(Items.GLASS, Items.GLASS_PANE, Items.RED_STAINED_GLASS, Items.BLACK_STAINED_GLASS, Items.RED_STAINED_GLASS_PANE,
				Items.TINTED_GLASS, Items.GLASS_BOTTLE)) {
			t.put(i, CraftJobs.Job.GLASSBLOWER);
		}
		for (Item i : List.of(Items.RAIL, Items.POWERED_RAIL, Items.TRIPWIRE_HOOK, Items.REDSTONE_TORCH, Items.DAYLIGHT_DETECTOR,
				Items.COPPER_BULB, Items.MINECART, Items.CHAIN, Items.LANTERN, Items.SOUL_LANTERN)) {
			t.put(i, CraftJobs.Job.TINKERER);
		}
		for (Item i : List.of(Items.WHITE_WOOL, Items.RED_WOOL, Items.RED_CARPET, Items.WHITE_BED, Items.RED_BED, Items.RED_BANNER,
				Items.FLOWER_BANNER_PATTERN, Items.PAINTING)) {
			t.put(i, CraftJobs.Job.SHEPHERD);
		}
		for (Item i : List.of(Items.FLOWER_POT, Items.DECORATED_POT, Items.MOSS_CARPET, Items.RED_DYE, Items.WHITE_DYE, Items.BLUE_DYE)) {
			t.put(i, CraftJobs.Job.FLORIST);
		}
		for (Item i : List.of(Items.BREWING_STAND, Items.BLAZE_POWDER, Items.ENDER_EYE, Items.ENDER_CHEST, Items.END_ROD, Items.BEACON)) {
			t.put(i, CraftJobs.Job.CLERIC);
		}
		for (Item i : List.of(Items.CANDLE, Items.RED_CANDLE, Items.HONEYCOMB_BLOCK)) {
			t.put(i, CraftJobs.Job.BEEKEEPER);
		}
		for (Item i : List.of(Items.ITEM_FRAME, Items.GLOW_ITEM_FRAME, Items.LEAD, Items.LEATHER, Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE,
				Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS)) {
			t.put(i, CraftJobs.Job.LEATHERWORKER);
		}
		for (Item i : List.of(Items.BOOK, Items.PAPER, Items.BOOKSHELF, Items.LECTERN, Items.ENCHANTING_TABLE, Items.CARTOGRAPHY_TABLE)) {
			t.put(i, CraftJobs.Job.LIBRARIAN);
		}
		return t;
	}

	/** Every row of the Milestone 46 table: each item is on its own job's list, and has a job that makes it today. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyItemOfTheTableIsOnItsOwnJobsList(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> wrong = new ArrayList<>();
		table().forEach((item, job) -> {
			CraftJobs.Job got = CraftJobs.of(level, item);
			CraftJobs.Job maker = CraftJobs.maker(level, item);
			if (got != job) {
				wrong.add(id(item) + " is on " + got + "'s list, not " + job + "'s");
			}
			// today's maker is a job that crafts now and is a real villager job (the stand-in until 46.2-46.6)
			if (maker == null || !maker.craftsNow() || maker.profession() == null || maker != job.standIn()) {
				wrong.add(id(item) + " is made today by " + maker + ", expected " + job.standIn());
			}
		});
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong: " + String.join("; ", wrong));
		helper.succeed();
	}

	/**
	 * The Carpenter makes nothing of another job's list (the owner: "the carpenter shouldn't build brewing stands, or
	 * anything that doesn't have wood"), and the Leatherworker no longer dyes glass, terracotta or concrete; the Mason
	 * makes none of the Carpenter's wooden things, and no job but the Carpenter makes them.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void eachJobKeepsToItsOwnKind(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> wrong = new ArrayList<>();
		table().forEach((item, job) -> {
			for (CraftJobs.Job other : CraftJobs.Job.values()) {
				if (other != job.standIn() && CraftJobs.makes(level, other, item)) {
					wrong.add("the " + other + " makes " + id(item));
				}
			}
		});
		for (Item item : List.of(Items.RED_STAINED_GLASS, Items.RED_STAINED_GLASS_PANE, Items.GLASS, Items.RED_TERRACOTTA, Items.RED_CONCRETE,
				Items.RED_GLAZED_TERRACOTTA, Items.RED_CONCRETE_POWDER)) {
			if (CraftJobs.makes(level, CraftJobs.Job.LEATHERWORKER, item)) {
				wrong.add("the Leatherworker still dyes " + id(item));
			}
		}
		// the Leatherworker's own list holds no wool, glass or terracotta (only as the stand-in does he make wool)
		for (Item item : List.of(Items.WHITE_WOOL, Items.RED_WOOL, Items.RED_STAINED_GLASS, Items.RED_TERRACOTTA)) {
			if (CraftJobs.of(level, item) == CraftJobs.Job.LEATHERWORKER) {
				wrong.add(id(item) + " is on the Leatherworker's own list");
			}
		}
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong: " + String.join("; ", wrong));
		helper.succeed();
	}

	/**
	 * The ingredient rule, in its order: logs/planks before stone, stone before ingots, ingots before wool/dye, wool/dye
	 * before plain wood (a stick), and nothing for none of them; the tag wins over the rule (a lever and a brewing stand
	 * read as stone by their recipe, but are the Carpenter's and the Cleric's by the table).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theIngredientRuleGoesInItsOrderAndTheTagWins(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<Item, CraftJobs.Job> rule = new LinkedHashMap<>();
		rule.put(Items.BOWL, CraftJobs.Job.CARPENTER); // planks
		rule.put(Items.SHIELD, CraftJobs.Job.CARPENTER); // planks and an iron ingot: timber first
		rule.put(Items.PISTON, CraftJobs.Job.CARPENTER); // planks, cobblestone, iron, redstone
		rule.put(Items.FURNACE, CraftJobs.Job.MASON); // cobblestone
		rule.put(Items.LEVER, CraftJobs.Job.MASON); // a stick and cobblestone: stone before plain wood
		rule.put(Items.BREWING_STAND, CraftJobs.Job.MASON); // blaze rod and cobblestone
		rule.put(Items.BUCKET, CraftJobs.Job.TINKERER); // iron ingots
		rule.put(Items.HOPPER, CraftJobs.Job.TINKERER); // iron ingots and a chest: ingots before plain wood
		rule.put(Items.IRON_SWORD, CraftJobs.Job.TINKERER); // ingots and a stick
		rule.put(Items.PAINTING, CraftJobs.Job.SHEPHERD); // sticks and wool: wool before plain wood
		rule.put(Items.WHITE_BANNER, CraftJobs.Job.SHEPHERD); // wool and a stick
		rule.put(Items.ARROW, CraftJobs.Job.CARPENTER); // flint, a stick, a feather: wood only
		rule.put(Items.BREAD, null); // wheat: no rule fits
		rule.put(Items.GLASS_BOTTLE, null); // glass
		rule.put(Items.DRAGON_EGG, null); // no recipe at all
		List<String> wrong = new ArrayList<>();
		rule.forEach((item, job) -> {
			CraftJobs.Job got = CraftJobs.byRecipe(level, item);
			if (got != job) {
				wrong.add(id(item) + " by its recipe: " + got + ", expected " + job);
			}
		});
		// the table's tag wins over the recipe
		if (CraftJobs.of(level, Items.LEVER) != CraftJobs.Job.CARPENTER) {
			wrong.add("a lever is " + CraftJobs.of(level, Items.LEVER) + "'s");
		}
		if (CraftJobs.of(level, Items.BREWING_STAND) != CraftJobs.Job.CLERIC) {
			wrong.add("a brewing stand is " + CraftJobs.of(level, Items.BREWING_STAND) + "'s");
		}
		if (CraftJobs.of(level, Items.GLASS_BOTTLE) != CraftJobs.Job.GLASSBLOWER) {
			wrong.add("a glass bottle is " + CraftJobs.of(level, Items.GLASS_BOTTLE) + "'s");
		}
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong: " + String.join("; ", wrong));
		helper.succeed();
	}

	/** "Mason: every stonecutter cut": no stonecutter result is the Carpenter's, and every one has a maker today. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyStonecutterCutHasAStoneOrMetalJob(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> wrong = new ArrayList<>();
		int cuts = 0;
		for (Item item : BuiltInRegistries.ITEM) {
			if (Crafting.recipesFor(level.getRecipeManager(), level.registryAccess(), Crafting.Kind.STONECUTTING, item).isEmpty()) {
				continue;
			}
			cuts++;
			CraftJobs.Job job = CraftJobs.maker(level, item);
			boolean metal = id(item).contains("copper");
			if (job != CraftJobs.Job.MASON && !(metal && job == CraftJobs.Job.TINKERER)) {
				wrong.add(id(item) + " -> " + job);
			}
		}
		helper.assertTrue(cuts > 100, "only " + cuts + " stonecutter results?");
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " cuts not the Mason's: " + String.join(", ", wrong));
		helper.succeed();
	}

	/**
	 * The shortage line for things with no job here: one "Hire a ..." per job, the things of a job together and in the
	 * plural, and the jobs that make things today named (never one that doesn't craft yet, as the Glassblower was until 46.6).
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '700' '"qaJobListsHire"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 700, batch = "qaJobListsHire")
	public void theHireLineGroupsThingsByJobInThePlural(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<Item, String> single = new LinkedHashMap<>();
		single.put(Items.LANTERN, "Hire a Tinkerer to make Lanterns");
		single.put(Items.RAIL, "Hire a Tinkerer to make Rails");
		single.put(Items.TORCH, "Hire a Carpenter to make Torches");
		single.put(Items.LADDER, "Hire a Carpenter to make Ladders");
		single.put(Items.GLASS_PANE, "Hire a Glassblower to make Glass Panes");
		single.put(Items.GLASS_BOTTLE, "Hire a Glassblower to make Glass Bottles");
		single.put(Items.ENDER_EYE, "Hire a Cleric to make Eyes of Ender");
		single.put(Items.END_ROD, "Hire a Cleric to make End Rods");
		single.put(Items.ENCHANTING_TABLE, "Hire a Librarian to make Enchanting Tables");
		single.put(Items.CARTOGRAPHY_TABLE, "Hire a Librarian to make Cartography Tables");
		single.put(Items.RED_BED, "Hire a Shepherd to make Red Beds");
		single.put(Items.RED_BANNER, "Hire a Shepherd to make Red Banners");
		single.put(Items.CANDLE, "Hire a Beekeeper to make Candles");
		single.put(Items.DECORATED_POT, "Hire a Florist to make Decorated Pots");
		Map<Item, UUID> sites = new LinkedHashMap<>();
		single.keySet().forEach(i -> sites.put(i, UUID.randomUUID()));
		UUID sameJob = UUID.randomUUID();
		UUID twoJobs = UUID.randomUUID();
		Map<Item, Integer> sameJobMissing = new LinkedHashMap<>();
		sameJobMissing.put(Items.LADDER, 4);
		sameJobMissing.put(Items.BARREL, 2);
		Map<Item, Integer> twoJobsMissing = new LinkedHashMap<>();
		twoJobsMissing.put(Items.BOOKSHELF, 3);
		twoJobsMissing.put(Items.CHAIN, 5);
		sites.forEach((item, site) -> JobShortages.line(level, site, Map.of(item, 3)));
		JobShortages.line(level, sameJob, sameJobMissing);
		JobShortages.line(level, twoJobs, twoJobsMissing);
		helper.runAfterDelay(610, () -> {
			List<String> wrong = new ArrayList<>();
			sites.forEach((item, site) -> {
				String text = text(JobShortages.line(level, site, Map.of(item, 3)));
				JobShortages.forget(site);
				if (!single.get(item).equals(text)) {
					wrong.add("'" + text + "', expected '" + single.get(item) + "'");
				}
			});
			String same = text(JobShortages.line(level, sameJob, sameJobMissing));
			JobShortages.forget(sameJob);
			if (!"Hire a Carpenter to make Ladders, Barrels".equals(same) && !"Hire a Carpenter to make Barrels, Ladders".equals(same)) {
				wrong.add("one job's two things: '" + same + "'");
			}
			String two = text(JobShortages.line(level, twoJobs, twoJobsMissing));
			JobShortages.forget(twoJobs);
			if (!"Hire a Librarian to make Bookshelves; Hire a Tinkerer to make Chains".equals(two)
					&& !"Hire a Tinkerer to make Chains; Hire a Librarian to make Bookshelves".equals(two)) {
				wrong.add("two jobs: '" + two + "'");
			}
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong: " + String.join("; ", wrong));
			helper.succeed();
		});
	}

	/**
	 * The Storehouse's lines for every gatherer, and the workers it counts: the nearest worker by name, one with no name
	 * as "the Carpenter", never a child or one out of reach (then the job to hire), and glass named by its maker today.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaJobListsStore"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaJobListsStore")
	public void theStorehouseNamesEveryGathererAndOnlyWorkersInReach(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		List<String> wrong = new ArrayList<>();
		lines(helper, pos, Items.SPRUCE_LOG, "Hire a Lumberjack to cut it", wrong);
		lines(helper, pos, Items.CHERRY_LOG, "Hire a Lumberjack to cut it", wrong);
		lines(helper, pos, Items.DEEPSLATE_IRON_ORE, "Hire a Miner to dig it", wrong);
		lines(helper, pos, Items.COAL_ORE, "Hire a Miner to dig it", wrong);
		lines(helper, pos, Items.RAW_GOLD, "Hire a Miner to dig it", wrong);
		lines(helper, pos, Items.RED_SAND, "Hire a Miner to dig it", wrong);
		lines(helper, pos, Items.CLAY, "Hire a Miner to dig it", wrong);
		lines(helper, pos, Items.COBBLESTONE, "Hire a Miner to dig it", wrong);
		lines(helper, pos, Items.RED_WOOL, "Hire a Shepherd to shear it", wrong);
		lines(helper, pos, Items.WHEAT, "Hire a Farmer to grow it", wrong);
		lines(helper, pos, Items.COD, "Hire a Fisherman to catch it", wrong);
		lines(helper, pos, Items.HONEYCOMB, "Hire a Beekeeper to collect it", wrong);
		lines(helper, pos, Items.POPPY, "Hire a Florist to grow it", wrong);
		lines(helper, pos, Items.GLASS, "Hire a Glassblower to make it", wrong); // the Mason's until 46.6
		lines(helper, pos, Items.LANTERN, "Hire a Tinkerer to make it", wrong);
		lines(helper, pos, Items.BARRIER, "No job makes or gathers this", wrong);
		// a child carpenter and a grown one out of reach don't count
		Villager child = worker(helper, ModVillagers.CARPENTER, "Kid", new BlockPos(1, 2, 1));
		child.setAge(-24000);
		worker(helper, ModVillagers.CARPENTER, "Far", new BlockPos(1, 2, 12));
		lines(helper, pos, Items.OAK_STAIRS, "Hire a Carpenter to make it", wrong);
		// the nearest grown one, by name; an unnamed one as the job
		worker(helper, ModVillagers.CARPENTER, "Ada", new BlockPos(1, 2, 3));
		worker(helper, ModVillagers.CARPENTER, "Zed", new BlockPos(1, 2, 6));
		lines(helper, pos, Items.OAK_STAIRS, "Made by: the Carpenter (Ada)", wrong);
		worker(helper, VillagerProfession.MASON, null, new BlockPos(1, 2, 2));
		lines(helper, pos, Items.GLASS, "Hire a Glassblower to make it", wrong); // a Mason no longer fires it (46.6)
		worker(helper, ModVillagers.GLASSBLOWER, null, new BlockPos(1, 2, 4));
		lines(helper, pos, Items.GLASS, "Made by: the Glassblower", wrong);
		lines(helper, pos, Items.RED_CONCRETE, "Made by: the Mason", wrong);
		worker(helper, VillagerProfession.FARMER, "Fern", new BlockPos(1, 2, 2));
		lines(helper, pos, Items.WHEAT, "Gathered by: the Farmer (Fern)", wrong);
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong: " + String.join("; ", wrong));
		helper.succeed();
	}

	/**
	 * Through a builder's real request: stained glass (the Glassblower's since 46.6, made by the Mason before, the dyer
	 * before 46.1) is still made, by the Glassblower from glass and dye, so it didn't become unmakable.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaJobListsStainedGlass"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaJobListsStainedGlass")
	public void aGlassblowerMakesStainedGlassForABuilder(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, Blocks.RED_STAINED_GLASS, new ItemStack(Items.GLASS, 8), new ItemStack(Items.RED_DYE, 1));
		helper.setBlock(STATION, ModBlocks.GLASS_KILN);
		Villager glassblower = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), glassblower, helper.absolutePos(STATION), ModVillagers.GLASS_KILN_POI, ModVillagers.GLASSBLOWER);
		helper.succeedWhen(() -> {
			ServerLevel level = helper.getLevel();
			helper.assertTrue(BuildSiteManager.get(level).get(a.site().id()) == null,
				"still building: status=" + a.site().status() + " missing=" + a.site().missing());
			helper.assertTrue(a.plan().unfinished(level).isEmpty(), "blocks wrong after the build");
		});
	}

	/**
	 * Through a builder's real request: a Carpenter by a builder short of stained glass and lanterns, with their makings
	 * in the chest, makes neither and takes nothing.
	 */
	//$ gametest_ticks_batch AREA '1400' '"qaJobListsCarpenterLeaves"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaJobListsCarpenterLeaves")
	public void aCarpenterLeavesStainedGlassAndLanternsAlone(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, Blocks.RED_STAINED_GLASS, new ItemStack(Items.GLASS, 8), new ItemStack(Items.RED_DYE, 1),
			new ItemStack(Items.IRON_NUGGET, 16), new ItemStack(Items.TORCH, 2));
		helper.setBlock(STATION, ModBlocks.CARPENTERS_BENCH);
		Villager carpenter = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), carpenter, helper.absolutePos(STATION), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		helper.runAfterDelay(1100, () -> {
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.GLASS) == 8 && chest.countItem(Items.RED_DYE) == 1, "the carpenter took the glass or dye");
			helper.assertTrue(chest.countItem(Items.IRON_NUGGET) == 16 && chest.countItem(Items.TORCH) == 2, "the carpenter took the nuggets or torches");
			helper.assertTrue(chest.countItem(Items.LANTERN) == 0 && chest.countItem(Items.RED_STAINED_GLASS) == 0, "something was made");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0) == 0, "the carpenter made " + ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0));
			helper.succeed();
		});
	}

	private static void lines(GameTestHelper helper, BlockPos pos, Item item, String expected, List<String> wrong) {
		String got = CraftJobs.storeLines(helper.getLevel(), pos, 8, item).stream().map(Component::getString).collect(Collectors.joining(" | "));
		if (!got.equals(expected)) {
			wrong.add(id(item) + ": '" + got + "', expected '" + expected + "'");
		}
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

	private static String text(Component line) {
		return line == null ? null : line.getString();
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

	private static Asker asker(GameTestHelper helper, Block block, ItemStack... items) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < 3; x++) {
			helper.setBlock(ROW.offset(x, 0, 0), block);
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test",
			"qa_lists_" + BuiltInRegistries.BLOCK.getKey(block).getPath() + "_" + Long.toHexString(level.getGameTime()));
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
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("blueprint not found: " + id);
		}
		return new Asker(builder, site, plan, CHEST);
	}
}
