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
import io.github.jcondedata.aliveworkplace.store.StorehouseBoard;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
 * ROADMAP 46.1, each job's own list: the split is the {@code aliveworkplace:crafts/<job>} item tags, an item in no tag
 * goes by its recipe, the Carpenter makes only wooden things, the Mason concrete and dyed terracotta, a builder short of
 * something whose job isn't here reads "Hire a ... to make ...", and the Storehouse's details pane names who makes or
 * gathers each thing. Until 46.2-46.6, the jobs that don't craft yet have stand-ins (CraftJobs), and nothing made before
 * 46.1 lost its maker.
 */
public class JobListsGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos STATION = new BlockPos(14, 2, 2);
	private static final BlockPos ROW = new BlockPos(6, 2, 9);

	private record Asker(Villager builder, BuildSite site, BuildPlan plan, BlockPos chest) {
	}

	/** The five the owner named are never the Carpenter's, and each has the job the table gives it (or its stand-in). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theCarpenterNeverMakesTheOwnersFive(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<Item, CraftJobs.Job> own = Map.of(Items.BREWING_STAND, CraftJobs.Job.CLERIC, Items.FLOWER_POT, CraftJobs.Job.FLORIST,
			Items.BLUE_BANNER, CraftJobs.Job.SHEPHERD, Items.RED_STAINED_GLASS, CraftJobs.Job.GLASSBLOWER, Items.LANTERN, CraftJobs.Job.TINKERER);
		Map<Item, CraftJobs.Job> today = Map.of(Items.BREWING_STAND, CraftJobs.Job.LIBRARIAN, Items.FLOWER_POT, CraftJobs.Job.LEATHERWORKER,
			Items.BLUE_BANNER, CraftJobs.Job.SHEPHERD, Items.RED_STAINED_GLASS, CraftJobs.Job.MASON, Items.LANTERN, CraftJobs.Job.TINKERER);
		for (Item item : own.keySet()) {
			helper.assertFalse(CraftJobs.makes(level, CraftJobs.Job.CARPENTER, item), "the Carpenter makes " + item);
			helper.assertTrue(CraftJobs.of(level, item) == own.get(item), item + " is on " + CraftJobs.of(level, item) + "'s list");
			helper.assertTrue(CraftJobs.maker(level, item) == today.get(item), item + " is made by " + CraftJobs.maker(level, item));
		}
		// every list loaded (a tag with one wrong id doesn't load at all)
		for (CraftJobs.Job job : CraftJobs.Job.values()) {
			helper.assertTrue(BuiltInRegistries.ITEM.getTag(job.tag()).map(t -> t.size() > 0).orElse(false), "the " + job.key() + " list didn't load");
		}
		for (CraftJobs.Gatherer g : CraftJobs.Gatherer.values()) {
			helper.assertTrue(BuiltInRegistries.ITEM.getTag(g.tag()).map(t -> t.size() > 0).orElse(false), "the " + g.key() + " gathering list didn't load");
		}
		// the Carpenter's own: wood
		for (Item item : List.of(Items.OAK_STAIRS, Items.SPRUCE_DOOR, Items.CHEST, Items.LADDER, Items.BARREL, Items.TORCH, Items.CAMPFIRE,
				ModBlocks.SHOP_COUNTER.asItem())) {
			helper.assertTrue(CraftJobs.makes(level, CraftJobs.Job.CARPENTER, item), "the Carpenter doesn't make " + item);
		}
		// the Mason: concrete and dyed terracotta, every cut
		for (Item item : List.of(Items.RED_CONCRETE, Items.WHITE_CONCRETE_POWDER, Items.BLUE_TERRACOTTA, Items.STONE_BRICK_SLAB,
				Items.BRICKS, Items.RED_GLAZED_TERRACOTTA)) {
			helper.assertTrue(CraftJobs.makes(level, CraftJobs.Job.MASON, item), "the Mason doesn't make " + item);
		}
		helper.succeed();
	}

	/**
	 * An item in no tag goes by its recipe: the mod's Bounty Board (planks, paper, sticks) is the Carpenter's, the
	 * Training Dummy (wool, hay, sticks) the Shepherd's. A modded wooden block stands in for one from another mod: the
	 * rule reads only the recipe, never the item's mod.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void anUntaggedWoodenBlockGoesToTheCarpenterByItsRecipe(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Item board = ModBlocks.BOUNTY_BOARD.asItem();
		helper.assertTrue(CraftJobs.tagged(board) == null, "the bounty board is in a tag: " + CraftJobs.tagged(board));
		helper.assertTrue(CraftJobs.byRecipe(level, board) == CraftJobs.Job.CARPENTER, "by its recipe: " + CraftJobs.byRecipe(level, board));
		helper.assertTrue(CraftJobs.maker(level, board) == CraftJobs.Job.CARPENTER, "made by " + CraftJobs.maker(level, board));
		Item dummy = ModBlocks.TRAINING_DUMMY.asItem();
		helper.assertTrue(CraftJobs.byRecipe(level, dummy) == CraftJobs.Job.SHEPHERD, "the dummy by its recipe: " + CraftJobs.byRecipe(level, dummy));
		helper.succeed();
	}

	/**
	 * Nothing made before 46.1 became unmakable: every item the Carpenter (any crafting recipe), the Mason (every cut, his
	 * kiln's, crushed gravel and sand), the dyer (anything colored, concrete), the Tinkerer ({@code tinkering}) or the
	 * Librarian made then still has a job that makes it today.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everythingMadeBeforeStillHasAJob(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Set<Item> crushed = Set.of(Items.GRAVEL, Items.SAND);
		List<String> lost = new ArrayList<>();
		int checked = 0;
		for (Item item : BuiltInRegistries.ITEM) {
			boolean before = !Crafting.recipesFor(level.getRecipeManager(), level.registryAccess(), Crafting.Kind.CRAFTING, item).isEmpty()
				|| !Crafting.recipesFor(level.getRecipeManager(), level.registryAccess(), Crafting.Kind.STONECUTTING, item).isEmpty()
				|| !Crafting.recipesFor(level.getRecipeManager(), level.registryAccess(), Crafting.Kind.KILN, item).isEmpty()
				|| crushed.contains(item) || item.builtInRegistryHolder().is(io.github.jcondedata.aliveworkplace.craft.TinkererWork.TINKERING)
				|| BuiltInRegistries.ITEM.getKey(item).getPath().endsWith("_concrete");
			if (!before) {
				continue;
			}
			checked++;
			if (CraftJobs.maker(level, item) == null && !crushed.contains(item)) {
				lost.add(BuiltInRegistries.ITEM.getKey(item).toString());
			}
		}
		helper.assertTrue(checked > 500, "only " + checked + " items were made before?");
		helper.assertTrue(lost.isEmpty(), lost.size() + " made before 46.1 have no job now: " + String.join(", ", lost));
		helper.succeed();
	}

	/** The "Hire a ..." line for each job that makes things today, once the jobs have had their look. */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '700' '"jobListsHire"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 700, batch = "jobListsHire")
	public void theShortageLineNamesTheJobToHire(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<Item, String> expected = Map.of(
			Items.OAK_STAIRS, "Hire a Carpenter to make Oak Stairs",
			Items.BLUE_TERRACOTTA, "Hire a Mason to make Blue Terracotta",
			Items.IRON_BARS, "Hire a Tinkerer to make Iron Bars",
			Items.ITEM_FRAME, "Hire a Leatherworker to make Item Frames",
			Items.BOOKSHELF, "Hire a Librarian to make Bookshelves",
			Items.BREWING_STAND, "Hire a Librarian to make Brewing Stands",
			Items.RED_STAINED_GLASS, "Hire a Mason to make Red Stained Glass");
		Map<Item, UUID> sites = expected.keySet().stream().collect(Collectors.toMap(i -> i, i -> UUID.randomUUID()));
		sites.forEach((item, site) -> helper.assertTrue(JobShortages.line(level, site, Map.of(item, 3)) == null, "a line before the jobs looked"));
		helper.runAfterDelay(610, () -> {
			sites.forEach((item, site) -> {
				Component line = JobShortages.line(level, site, Map.of(item, 3));
				String text = line == null ? null : line.getString();
				JobShortages.forget(site);
				helper.assertTrue(expected.get(item).equals(text), "for " + item + ": '" + text + "', expected '" + expected.get(item) + "'");
			});
			helper.succeed();
		});
	}

	/**
	 * The Storehouse's details lines: who gathers it (logs, ore, sand, clay, wool) or makes it, by name when one works
	 * here, else the job to hire; and what no job makes or gathers.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theStorehouseSaysWhoMakesOrGathersIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		assertLines(helper, pos, Items.OAK_LOG, "Hire a Lumberjack to cut it");
		assertLines(helper, pos, Items.IRON_ORE, "Hire a Miner to dig it");
		assertLines(helper, pos, Items.RAW_IRON, "Hire a Miner to dig it");
		assertLines(helper, pos, Items.SAND, "Hire a Miner to dig it");
		assertLines(helper, pos, Items.CLAY_BALL, "Hire a Miner to dig it");
		assertLines(helper, pos, Items.WHITE_WOOL, "Hire a Shepherd to shear it");
		assertLines(helper, pos, Items.OAK_STAIRS, "Hire a Carpenter to make it");
		assertLines(helper, pos, Items.BREWING_STAND, "Hire a Librarian to make it");
		assertLines(helper, pos, Items.DRAGON_EGG, "No job makes or gathers this");
		worker(helper, ModVillagers.LUMBERJACK, "Bo");
		worker(helper, ModVillagers.CARPENTER, "Ada");
		worker(helper, ModVillagers.MINER, null);
		assertLines(helper, pos, Items.OAK_LOG, "Gathered by: the Lumberjack (Bo)");
		assertLines(helper, pos, Items.OAK_STAIRS, "Made by: the Carpenter (Ada)");
		assertLines(helper, pos, Items.IRON_ORE, "Gathered by: the Miner");
		helper.succeed();
	}

	/**
	 * Through a builder's request: a Carpenter by a builder short of brewing stands, with the cobblestone and blaze rods in
	 * his chest, makes none and takes nothing; the builder's line names the job to hire (the Librarian until 46.4).
	 */
	//$ gametest_ticks_batch AREA '1400' '"jobListsBrewingStand"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "jobListsBrewingStand")
	public void aCarpenterLeavesTheBrewingStandsAlone(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, Blocks.BREWING_STAND, new ItemStack(Items.COBBLESTONE, 9), new ItemStack(Items.BLAZE_ROD, 3));
		Villager carpenter = carpenter(helper);
		helper.runAfterDelay(200, () -> JobShortages.line(helper.getLevel(), a.site().id(), a.site().missing(), helper.absolutePos(BENCH)));
		helper.runAfterDelay(1100, () -> {
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 9 && chest.countItem(Items.BLAZE_ROD) == 3, "the carpenter took the makings");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0) == 0, "the carpenter made " + ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0));
			Component line = JobShortages.line(helper.getLevel(), a.site().id(), a.site().missing(), helper.absolutePos(BENCH));
			String text = line == null ? null : line.getString();
			helper.assertTrue("Hire a Librarian to make Brewing Stands".equals(text), "the builder's line: " + text);
			helper.succeed();
		});
	}

	/** Through a builder's request: the Mason dyes terracotta at the crafting table (no furnace needed). */
	//$ gametest_ticks_batch AREA '2400' '"jobListsTerracotta"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "jobListsTerracotta")
	public void theMasonDyesTerracottaForABuilder(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, Blocks.BLUE_TERRACOTTA, new ItemStack(Items.TERRACOTTA, 8), new ItemStack(Items.BLUE_DYE, 1));
		mason(helper);
		helper.succeedWhen(() -> assertBuilt(helper, a));
	}

	/**
	 * Through a builder's request, with a save and reload: the Mason sets concrete from its powder, and a Mason loaded
	 * again mid-shift carries on.
	 */
	//$ gametest_ticks_batch AREA '2400' '"jobListsConcrete"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "jobListsConcrete")
	public void theMasonSetsConcreteForABuilderAcrossAReload(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, Blocks.WHITE_CONCRETE, new ItemStack(Items.WHITE_CONCRETE_POWDER, 3));
		Villager mason = mason(helper);
		helper.runAfterDelay(60, () -> reload(helper.getLevel(), mason));
		helper.succeedWhen(() -> assertBuilt(helper, a));
	}

	/** Without a Leatherworker a Carpenter still won't make a flower pot: the job absent leaves it unmade, and named. */
	//$ gametest_ticks_batch AREA '1400' '"jobListsFlowerPot"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "jobListsFlowerPot")
	public void withTheJobAbsentTheFlowerPotWaitsAndIsNamed(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, Blocks.FLOWER_POT, new ItemStack(Items.BRICK, 9));
		Villager carpenter = carpenter(helper);
		helper.runAfterDelay(200, () -> JobShortages.line(helper.getLevel(), a.site().id(), a.site().missing(), helper.absolutePos(BENCH)));
		helper.runAfterDelay(1100, () -> {
			helper.assertTrue(((Container) helper.getBlockEntity(a.chest())).countItem(Items.BRICK) == 9, "the bricks were taken");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0) == 0, "the carpenter made a pot");
			Component line = JobShortages.line(helper.getLevel(), a.site().id(), a.site().missing(), helper.absolutePos(BENCH));
			String text = line == null ? null : line.getString();
			helper.assertTrue("Hire a Leatherworker to make Flower Pots".equals(text), "the builder's line: " + text);
			helper.succeed();
		});
	}

	/** Through the player's own entry point: the Storehouse's board, for a builder short of logs, names the Lumberjack. */
	//$ gametest_ticks_batch AREA '700' '"jobListsBoard"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "jobListsBoard")
	public void theStorehouseBoardNamesTheGatherer(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, Blocks.OAK_LOG);
		BlockPos storehouse = new BlockPos(14, 2, 6);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		worker(helper, ModVillagers.LUMBERJACK, "Bo");
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(a.site().status() == BuildSite.Status.WAITING_FOR_MATERIALS, "the builder should be waiting: " + a.site().status());
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			ChoiceMenu board = StorehouseBoard.boardForTest(player, helper.absolutePos(storehouse));
			String lore = null;
			for (int i = 0; i < ChoiceMenu.SIZE; i++) {
				if (board.icon(i).is(Items.OAK_LOG)) {
					var lines = board.icon(i).get(DataComponents.LORE);
					lore = lines == null ? "" : lines.lines().stream().map(Component::getString).collect(Collectors.joining(" | "));
				}
			}
			helper.assertTrue(lore != null, "the board doesn't show the logs");
			helper.assertTrue(lore.contains("Gathered by: the Lumberjack (Bo)"), "the logs' lines: " + lore);
			helper.succeed();
		});
	}

	private static void assertLines(GameTestHelper helper, BlockPos pos, Item item, String... expected) {
		String got = CraftJobs.storeLines(helper.getLevel(), pos, 48, item).stream().map(Component::getString).collect(Collectors.joining(" | "));
		helper.assertTrue(got.equals(String.join(" | ", expected)), "for " + item + ": '" + got + "', expected '" + String.join(" | ", expected) + "'");
	}

	private static Villager worker(GameTestHelper helper, VillagerProfession profession, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 1));
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(profession));
		if (name != null) {
			v.setCustomName(Component.literal(name));
		}
		return v;
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
		ResourceLocation id = row(helper, ROW, block);
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

	private static ResourceLocation row(GameTestHelper helper, BlockPos origin, Block block) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < 3; x++) {
			helper.setBlock(origin.offset(x, 0, 0), block);
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test",
			"lists_" + BuiltInRegistries.BLOCK.getKey(block).getPath() + "_" + Long.toHexString(level.getGameTime()) + "_" + origin.getX());
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(origin), new Vec3i(3, 1, 1), false, Blocks.STRUCTURE_VOID);
		for (int x = 0; x < 3; x++) {
			helper.setBlock(origin.offset(x, 0, 0), Blocks.AIR);
		}
		return id;
	}

	private static Villager carpenter(GameTestHelper helper) {
		helper.setBlock(STATION, ModBlocks.CARPENTERS_BENCH);
		Villager carpenter = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), carpenter, helper.absolutePos(STATION), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		return carpenter;
	}

	private static Villager mason(GameTestHelper helper) {
		helper.setBlock(STATION, Blocks.STONECUTTER);
		Villager mason = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), mason, helper.absolutePos(STATION), PoiTypes.MASON, VillagerProfession.MASON);
		return mason;
	}

	private static void assertBuilt(GameTestHelper helper, Asker a) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(BuildSiteManager.get(level).get(a.site().id()) == null,
			"still building: status=" + a.site().status() + " missing=" + a.site().missing());
		List<BlockPos> unfinished = a.plan().unfinished(level);
		helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " block(s) wrong after the build");
	}

	private static Villager reload(ServerLevel level, Villager villager) {
		CompoundTag tag = new CompoundTag();
		villager.saveWithoutId(tag);
		villager.discard();
		Villager copy = EntityType.VILLAGER.create(level);
		if (copy == null) {
			throw new GameTestAssertException("no villager made");
		}
		copy.load(tag);
		level.addFreshEntity(copy);
		io.github.jcondedata.aliveworkplace.work.Village.forget(copy);
		return copy;
	}
}
