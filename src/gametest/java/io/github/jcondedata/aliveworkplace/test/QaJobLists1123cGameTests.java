package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.craft.CraftJobs;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * QA (lane-c-1123-0653) for docs/wiki/job-lists.md, written from the page. Not repeated: what JobListsGameTests,
 * QaJobCraftLists(Bug)GameTests and QaJobLists1110cGameTests check. New here: every thing the page names in section 1
 * for each job, made by that job and no other (the lever only the Carpenter's, section 7); every gatherer's exact
 * Storehouse line, a raw thing showing only its gatherer; the Storehouse's maker line unnamed, and never for a child,
 * a stranger or one out of reach; the builder's hire line with workers in and out of the village's reach; and every
 * Storehouse line of every item and every listed thing's hire line read as a player reads them (no raw key or %s,
 * American English).
 */
public class QaJobLists1123cGameTests {

	/** Section 1, list by list: every thing it names is made today by that job alone. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyThingThePageNamesIsItsOwnJobsAlone(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<Item, CraftJobs.Job> named = new LinkedHashMap<>();
		for (Item i : List.of(Items.OAK_PLANKS, Items.OAK_SLAB, Items.OAK_STAIRS, Items.OAK_FENCE, Items.OAK_FENCE_GATE, Items.OAK_DOOR,
				Items.OAK_TRAPDOOR, Items.OAK_SIGN, Items.LADDER, Items.CHEST, Items.BARREL, Items.OAK_BOAT, Items.CRAFTING_TABLE, Items.TORCH,
				Items.CAMPFIRE, Items.SCAFFOLDING, Items.ARMOR_STAND, Items.LEVER, Items.GRINDSTONE, Items.SMOKER, Items.BEEHIVE,
				Items.NOTE_BLOCK, ModBlocks.SHOP_COUNTER.asItem())) {
			named.put(i, CraftJobs.Job.CARPENTER);
		}
		for (Item i : List.of(Items.BRICKS, Items.BRICK, Items.TERRACOTTA, Items.RED_TERRACOTTA, Items.RED_GLAZED_TERRACOTTA, Items.RED_CONCRETE,
				Items.RED_CONCRETE_POWDER, Items.SANDSTONE, Items.MOSSY_COBBLESTONE, Items.MOSSY_STONE_BRICKS, Items.STONE_BRICK_SLAB)) {
			named.put(i, CraftJobs.Job.MASON);
		}
		for (Item i : List.of(Items.RAIL, Items.TRIPWIRE_HOOK, Items.REDSTONE_TORCH, Items.DAYLIGHT_DETECTOR, Items.COPPER_BULB, Items.MINECART,
				Items.CHAIN, Items.LANTERN)) {
			named.put(i, CraftJobs.Job.TINKERER);
		}
		for (Item i : List.of(Items.ITEM_FRAME, Items.GLOW_ITEM_FRAME, Items.LEAD, Items.LEATHER, Items.LEATHER_CHESTPLATE)) {
			named.put(i, CraftJobs.Job.LEATHERWORKER);
		}
		for (Item i : List.of(Items.BOOK, Items.PAPER, Items.BOOKSHELF, Items.LECTERN, Items.ENCHANTING_TABLE, Items.CARTOGRAPHY_TABLE)) {
			named.put(i, CraftJobs.Job.LIBRARIAN);
		}
		for (Item i : List.of(Items.WHITE_BED, Items.WHITE_CARPET, Items.WHITE_BANNER, Items.FLOWER_BANNER_PATTERN, Items.PAINTING)) {
			named.put(i, CraftJobs.Job.SHEPHERD);
		}
		for (Item i : List.of(Items.FLOWER_POT, Items.DECORATED_POT, Items.RED_DYE, Items.MOSS_CARPET)) {
			named.put(i, CraftJobs.Job.FLORIST);
		}
		for (Item i : List.of(Items.BREWING_STAND, Items.BLAZE_POWDER, Items.ENDER_EYE, Items.ENDER_CHEST, Items.END_ROD, Items.BEACON)) {
			named.put(i, CraftJobs.Job.CLERIC);
		}
		for (Item i : List.of(Items.CANDLE, Items.RED_CANDLE, Items.HONEYCOMB_BLOCK)) {
			named.put(i, CraftJobs.Job.BEEKEEPER);
		}
		for (Item i : List.of(Items.GLASS, Items.GLASS_PANE, Items.RED_STAINED_GLASS, Items.RED_STAINED_GLASS_PANE, Items.TINTED_GLASS,
				Items.GLASS_BOTTLE)) {
			named.put(i, CraftJobs.Job.GLASSBLOWER);
		}
		List<String> wrong = new ArrayList<>();
		named.forEach((item, job) -> {
			CraftJobs.Job maker = CraftJobs.maker(level, item);
			if (maker != job) {
				wrong.add(id(item) + " made by " + maker + ", not " + job);
			}
			for (CraftJobs.Job other : CraftJobs.Job.values()) {
				if (CraftJobs.makes(level, other, item) != (other == job)) {
					wrong.add(id(item) + ": makes(" + other + ") = " + CraftJobs.makes(level, other, item));
				}
			}
		});
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong: " + String.join("; ", wrong));
		helper.succeed();
	}

	/** Every gatherer's own words on the Storehouse board, one line only, though wool and raw iron can be crafted too. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyGatherersLineIsItsOwnAndAlone(GameTestHelper helper) {
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		Map<Item, String> expected = new LinkedHashMap<>();
		expected.put(Items.OAK_LOG, "Hire a Lumberjack to cut it");
		expected.put(Items.RAW_IRON, "Hire a Miner to dig it");
		expected.put(Items.COAL, "Hire a Miner to dig it");
		expected.put(Items.DIAMOND, "Hire a Miner to dig it");
		expected.put(Items.IRON_ORE, "Hire a Miner to dig it");
		expected.put(Items.COBBLESTONE, "Hire a Miner to dig it");
		expected.put(Items.SAND, "Hire a Miner to dig it");
		expected.put(Items.GRAVEL, "Hire a Miner to dig it");
		expected.put(Items.CLAY_BALL, "Hire a Miner to dig it");
		expected.put(Items.WHITE_WOOL, "Hire a Shepherd to shear it");
		expected.put(Items.WHEAT, "Hire a Farmer to grow it");
		expected.put(Items.COD, "Hire a Fisherman to catch it");
		expected.put(Items.HONEYCOMB, "Hire a Beekeeper to collect it");
		expected.put(Items.POPPY, "Hire a Florist to grow it");
		expected.put(Items.DRAGON_EGG, "No job makes or gathers this");
		List<String> wrong = new ArrayList<>();
		expected.forEach((item, line) -> check(helper, pos, item, line, wrong));
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong: " + String.join("; ", wrong));
		// with a Shepherd and a Miner in reach, by name or not: gathered, never "Made by"
		worker(helper, VillagerProfession.SHEPHERD, "Wooly", new BlockPos(1, 2, 2));
		worker(helper, ModVillagers.MINER, null, new BlockPos(2, 2, 2));
		check(helper, pos, Items.WHITE_WOOL, "Gathered by: the Shepherd (Wooly)", wrong);
		check(helper, pos, Items.RAW_IRON, "Gathered by: the Miner", wrong);
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong: " + String.join("; ", wrong));
		helper.succeed();
	}

	/** "Made by: the Carpenter" with no name; a child, a Mason or a Carpenter out of reach makes no one "Made by". */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theMakerLineCountsOnlyAGrownMakerInReach(GameTestHelper helper) {
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		List<String> wrong = new ArrayList<>();
		Villager kid = worker(helper, ModVillagers.CARPENTER, "Pip", new BlockPos(1, 2, 2));
		kid.setBaby(true);
		worker(helper, VillagerProfession.MASON, "Stoney", new BlockPos(2, 2, 2));
		worker(helper, ModVillagers.CARPENTER, "Far", new BlockPos(1, 2, 14));
		check(helper, pos, Items.BARREL, "Hire a Carpenter to make it", wrong);
		check(helper, pos, Items.BRICKS, "Made by: the Mason (Stoney)", wrong);
		worker(helper, ModVillagers.CARPENTER, null, new BlockPos(2, 2, 3));
		check(helper, pos, Items.BARREL, "Made by: the Carpenter", wrong);
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong: " + String.join("; ", wrong));
		helper.succeed();
	}

	/**
	 * The builder's line checks for a worker of the maker's job within the village's reach: a Carpenter in reach takes
	 * barrels off the hire line, a Mason (a stranger to barrels) doesn't, and one beyond the reach doesn't either.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '700' '"qaJobLists1123cReach"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 700, batch = "qaJobLists1123cReach")
	public void theHireLineCountsOnlyTheMakersJobInReach(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		ServerLevel level = helper.getLevel();
		BlockPos here = helper.absolutePos(new BlockPos(1, 2, 1));
		BlockPos far = here.offset(Village.RADIUS + 16, 0, 0);
		Map<Item, Integer> missing = new LinkedHashMap<>();
		missing.put(Items.BARREL, 4);
		missing.put(Items.LANTERN, 2);
		UUID inReach = UUID.randomUUID();
		UUID outOfReach = UUID.randomUUID();
		JobShortages.line(level, inReach, missing, here);
		JobShortages.line(level, outOfReach, missing, far);
		helper.runAfterDelay(610, () -> {
			worker(helper, ModVillagers.CARPENTER, "Ada", new BlockPos(1, 2, 2));
			worker(helper, VillagerProfession.MASON, "Stoney", new BlockPos(2, 2, 2));
			Component a = JobShortages.line(level, inReach, missing, here);
			Component b = JobShortages.line(level, outOfReach, missing, far);
			JobShortages.forget(inReach);
			JobShortages.forget(outOfReach);
			String near = a == null ? null : a.getString();
			String away = b == null ? null : b.getString();
			helper.assertTrue(near != null && near.contains("Hire a Tinkerer to make Lanterns") && !near.contains("Carpenter"),
				"with a Carpenter in reach: '" + near + "'");
			helper.assertTrue("Hire a Carpenter to make Barrels; Hire a Tinkerer to make Lanterns".equals(away),
				"with the Carpenter out of reach: '" + away + "'");
			helper.succeed();
		});
	}

	/**
	 * Every sentence the Storehouse can say about any item, in English as a player reads it: no raw key, no %s or %d,
	 * American spelling, a "Hire a ..." line naming a real job.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyStorehouseLineReadsAsEnglish(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		List<String> wrong = new ArrayList<>();
		int checked = 0;
		for (Item item : BuiltInRegistries.ITEM) {
			if (item == Items.AIR) {
				continue;
			}
			checked++;
			List<Component> lines = CraftJobs.storeLines(level, pos, 8, item);
			if (lines.size() != 1) {
				wrong.add(id(item) + ": " + lines.size() + " lines");
			}
			for (Component c : lines) {
				String s = c.getString();
				String low = s.toLowerCase();
				if (s.contains("%") || s.contains("aliveworkplace") || s.contains("screen.") || s.contains("entity.")
						|| low.contains("colour") || low.contains("armour") || low.contains("grey") || s.isBlank()
						|| !(s.startsWith("Hire a ") || s.startsWith("Made by: the ") || s.startsWith("Gathered by: the ") || s.equals("No job makes or gathers this"))
						|| (s.startsWith("Hire a ") && !s.matches("Hire a [A-Z][a-z]+ to (make|cut|dig|shear|grow|catch|collect) it"))) {
					wrong.add(id(item) + ": '" + s + "'");
				}
			}
		}
		helper.assertTrue(checked > 1000, "checked " + checked);
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong: " + wrong.stream().limit(12).collect(Collectors.joining("; ")));
		helper.succeed();
	}

	/**
	 * Every thing on any job's list, missing with no one in the village to make it, gets a builder's line a player can
	 * read: "Hire a <job> to make <things>", the job resolved and no raw key, %s or %d left in it.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '700' '"qaJobLists1123cEvery"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 700, batch = "qaJobLists1123cEvery")
	public void everyListedThingsHireLineReadsAsEnglish(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<Item, UUID> sites = new LinkedHashMap<>();
		for (Item item : BuiltInRegistries.ITEM) {
			for (CraftJobs.Job job : CraftJobs.Job.values()) {
				if (item.builtInRegistryHolder().is(job.tag())) {
					sites.put(item, UUID.randomUUID());
					break;
				}
			}
		}
		sites.forEach((item, site) -> JobShortages.line(level, site, Map.of(item, 3)));
		helper.runAfterDelay(610, () -> {
			List<String> wrong = new ArrayList<>();
			sites.forEach((item, site) -> {
				Component line = JobShortages.line(level, site, Map.of(item, 3));
				JobShortages.forget(site);
				String s = line == null ? null : line.getString();
				if (s == null || s.contains("%") || s.contains("aliveworkplace") || s.contains("item.") || s.contains("block.")
						|| !s.matches("Hire an? [A-Z][a-z]+ to make [A-Z].*")) {
					wrong.add(id(item) + ": '" + s + "'");
				}
			});
			helper.assertTrue(sites.size() > 300, "only " + sites.size() + " listed things");
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong: " + wrong.stream().limit(12).collect(Collectors.joining("; ")));
			helper.succeed();
		});
	}

	private static void check(GameTestHelper helper, BlockPos pos, Item item, String expected, List<String> wrong) {
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

	private static String id(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}
}
