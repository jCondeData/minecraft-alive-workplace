package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA lane C (2026-11-21, lane-c-1121-1853), the rebuilt workshops of Milestone 50 in the world: the shipped drawing placed
 * on flat ground, a jobless villager in the street in front, and the game's own job-taking (the villager walks in and
 * claims the workshop's job block). Written from the briefs: builders_workshop.md ("a builder villager waiting in front of
 * the building walks in and claims it"), glassblowers_workshop.md (the Glass Kiln, his), supply_shop.md (the Shop Counter,
 * the Shopkeeper's), physicians_house.md (the brewing stand, the only job block), and Level II of the Fletcher's Lodge and
 * Mason's Yard (two job blocks, two workers). Then the game's own pathfinder from the street in front to every job block
 * of 24 workshop levels and to at least one store in each job block's supply reach.
 */
public class QaRebuiltWorkshops1121cLiveGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	static final BlockPos AT = new BlockPos(6, 2, 10);

	static BlockPos place(GameTestHelper helper, StarterBlueprints.Entry entry) {
		ServerLevel level = helper.getLevel();
		StructureTemplate template = level.getStructureManager().get(entry.id()).orElseThrow();
		BlockPos origin = helper.absolutePos(AT);
		template.placeInWorld(level, origin, origin, new StructurePlaceSettings().setRotation(Rotation.NONE).setMirror(Mirror.NONE),
			net.minecraft.util.RandomSource.create(173L), 2);
		return origin;
	}

	/**
	 * The drawing placed, {@code count} jobless villagers three blocks before its front (as many as the brief's job blocks);
	 * within the time each takes a job: his JOB_SITE is one of the workshop's {@code block}s, no two the same, and his job
	 * is {@code job} (or, with {@code job} null, any job).
	 */
	private static void takesItsJob(GameTestHelper helper, StarterBlueprints.Entry entry, Block block, VillagerProfession job, int count) {
		Leftovers.clear(helper);
		// a shared block (the Shop Counter: Shopkeeper, Innkeeper) gives a job at random by design (36.17); off, the
		// block's own first job, the one the brief names
		boolean random = io.github.jcondedata.aliveworkplace.work.RandomJobs.ENABLED;
		io.github.jcondedata.aliveworkplace.work.RandomJobs.ENABLED = false;
		Leftovers.after(helper, () -> io.github.jcondedata.aliveworkplace.work.RandomJobs.ENABLED = random);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		BlockPos origin = place(helper, entry);
		var size = entry.size();
		List<BlockPos> sites = BlockPos.betweenClosedStream(origin.offset(-1, -6, -1), origin.offset(size.getX(), size.getY(), size.getZ()))
			.filter(p -> level.getBlockState(p).is(block)).map(BlockPos::immutable).toList();
		helper.assertTrue(sites.size() == count, "(setup) " + entry.id().getPath() + ": " + sites.size() + " job blocks placed, brief " + count);
		List<Villager> villagers = new java.util.ArrayList<>();
		for (int i = 0; i < count; i++) {
			Villager v = helper.spawn(EntityType.VILLAGER, AT.offset((size.getX() - 1) / 2 + 2 * i, 0, -3));
			v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.NONE));
			villagers.add(v);
		}
		helper.onEachTick(() -> {
			if (level.getDayTime() % 24000 > 9000) {
				helper.setDayTime(2000);
			}
		});
		helper.succeedWhen(() -> {
			java.util.Set<BlockPos> taken = new java.util.HashSet<>();
			for (Villager v : villagers) {
				helper.assertTrue(v.isAlive(), "a villager died");
				BlockPos site = v.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(g -> g.pos()).orElse(null);
				helper.assertTrue(site != null && sites.contains(site), entry.id().getPath() + ": a villager hasn't taken a "
					+ block.getName().getString() + " yet: job " + v.getVillagerData().getProfession() + ", site " + site + ", at "
					+ helper.relativePos(v.blockPosition()).subtract(AT).toShortString() + " (template coordinates)");
				VillagerProfession got = v.getVillagerData().getProfession();
				helper.assertTrue(job == null ? got != VillagerProfession.NONE : got == job, entry.id().getPath() + ": took the "
					+ block.getName().getString() + " as " + got + ", wanted " + (job == null ? "a job" : job));
				taken.add(site);
			}
			helper.assertTrue(taken.size() == count, entry.id().getPath() + ": two villagers on one job block");
			villagers.forEach(Villager::discard);
		});
	}

	private static void takesItsJob(GameTestHelper helper, StarterBlueprints.Entry entry, Block block, VillagerProfession job) {
		takesItsJob(helper, entry, block, job, 1);
	}

	/** builders_workshop.md: a jobless villager in front walks in and claims the one Blueprint Table, a Builder. */
	//$ gametest_ticks_batch HUGE '2400' '"qaWorkshops1121cBuilder"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaWorkshops1121cBuilder")
	public void qaAJoblessVillagerBecomesTheBuilderAtTheRebuiltWorkshop(GameTestHelper helper) {
		takesItsJob(helper, StarterBlueprints.BUILDERS_WORKSHOP, ModBlocks.BLUEPRINT_TABLE, ModVillagers.BUILDER);
	}

	/** glassblowers_workshop.md: the Glass Kiln is the Glassblower's; a jobless villager in front takes it. */
	//$ gametest_ticks_batch HUGE '2400' '"qaWorkshops1121cGlass"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaWorkshops1121cGlass")
	public void qaAJoblessVillagerBecomesTheGlassblowerAtTheRebuiltWorkshop(GameTestHelper helper) {
		takesItsJob(helper, StarterBlueprints.GLASSBLOWERS_WORKSHOP, ModBlocks.GLASS_KILN, ModVillagers.GLASSBLOWER);
	}

	/** supply_shop.md: the Shop Counter is the Shopkeeper's job site; a jobless villager in front walks in and takes it. */
	//$ gametest_ticks_batch HUGE '2400' '"qaWorkshops1121cSupply"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaWorkshops1121cSupply")
	public void qaAJoblessVillagerBecomesTheShopkeeperAtTheRebuiltSupplyShop(GameTestHelper helper) {
		takesItsJob(helper, StarterBlueprints.SUPPLY_SHOP, ModBlocks.SHOP_COUNTER, ModVillagers.SHOPKEEPER);
	}

	/** physicians_house.md: one brewing stand, the only job block; a jobless villager in front walks in and takes it. */
	//$ gametest_ticks_batch HUGE '2400' '"qaWorkshops1121cPhysician"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaWorkshops1121cPhysician")
	public void qaAJoblessVillagerTakesThePhysiciansHousesBrewingStand(GameTestHelper helper) {
		takesItsJob(helper, StarterBlueprints.PHYSICIANS_HOUSE, Blocks.BREWING_STAND, null);
	}

	/** fletchers_lodge.md, Level II: two fletching tables "so a Fletcher and a Lumberjack both work there"; two jobless villagers take one each. */
	//$ gametest_ticks_batch HUGE '2400' '"qaWorkshops1121cFletchers"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaWorkshops1121cFletchers")
	public void qaTwoJoblessVillagersTakeTheFletchersLodgeIIsTwoTables(GameTestHelper helper) {
		takesItsJob(helper, StarterBlueprints.FLETCHERS_LODGE_2, Blocks.FLETCHING_TABLE, null, 2);
	}

	/** masons_yard.md, Level II: "a second stonecutter (exactly two), so a second Mason works there". */
	//$ gametest_ticks_batch HUGE '2400' '"qaWorkshops1121cMasons"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaWorkshops1121cMasons")
	public void qaTwoJoblessVillagersTakeTheMasonsYardIIsTwoStonecutters(GameTestHelper helper) {
		takesItsJob(helper, StarterBlueprints.MASONS_YARD_2, Blocks.STONECUTTER, null, 2);
	}

	/** tailors_shop.md: the loom, the only job block, taken by a jobless villager walking in from the street. */
	//$ gametest_ticks_batch HUGE '2400' '"qaWorkshops1121cTailor"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaWorkshops1121cTailor")
	public void qaAJoblessVillagerTakesTheTailorsShopIIsLoom(GameTestHelper helper) {
		takesItsJob(helper, StarterBlueprints.TAILORS_SHOP_2, Blocks.LOOM, null);
	}

	// ---- the game's own pathfinder: job blocks and chests ---------------------------------------------------------

	/**
	 * The briefs' "reachable on foot" with the game's pathfinder: the drawing placed, a villager in the street in front; a
	 * path to within one block of every job block (how a villager claims it), and for each job block a path to within two
	 * blocks of at least one chest or barrel in its supply reach (8 across, 4 up or down: the worker's own store, which he
	 * fetches from and puts into). A loft chest up a ladder may be cut off for the pathfinder (the briefs allow ladders);
	 * the worker's own store must not be.
	 */
	private static void pathsToEveryJobBlockAndItsChest(GameTestHelper helper, StarterBlueprints.Entry entry, List<Block> jobs) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos origin = place(helper, entry);
		var size = entry.size();
		List<BlockPos> all = BlockPos.betweenClosedStream(origin.offset(0, -6, 0), origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))
			.map(BlockPos::immutable).toList();
		List<BlockPos> sites = all.stream().filter(p -> jobs.stream().anyMatch(level.getBlockState(p)::is)).toList();
		List<BlockPos> stores = all.stream().filter(p -> level.getBlockState(p).getBlock() instanceof net.minecraft.world.level.block.ChestBlock
			|| level.getBlockState(p).getBlock() instanceof net.minecraft.world.level.block.BarrelBlock).toList();
		helper.assertTrue(!sites.isEmpty(), "(setup) no job block placed");
		Villager walker = helper.spawn(EntityType.VILLAGER, AT.offset((size.getX() - 1) / 2, 0, -3));
		walker.setNoAi(true);
		helper.runAfterDelay(10, () -> {
			walker.setOnGround(true);
			List<String> cut = new java.util.ArrayList<>();
			for (BlockPos site : sites) {
				net.minecraft.world.level.pathfinder.Path path = walker.getNavigation().createPath(java.util.Set.of(site), 1);
				if (path == null || !path.canReach()) {
					cut.add(level.getBlockState(site).getBlock().getName().getString() + " " + site.subtract(origin).toShortString()
						+ (path == null ? " (no path)" : " (ends at " + path.getEndNode().asBlockPos().subtract(origin).toShortString() + ")"));
				}
				List<BlockPos> own = stores.stream().filter(c -> Math.abs(c.getX() - site.getX()) <= 8 && Math.abs(c.getZ() - site.getZ()) <= 8
					&& Math.abs(c.getY() - site.getY()) <= 4).toList();
				List<String> tried = new java.util.ArrayList<>();
				boolean any = false;
				for (BlockPos c : own) {
					net.minecraft.world.level.pathfinder.Path to = walker.getNavigation().createPath(java.util.Set.of(c), 2);
					if (to != null && to.canReach()) {
						any = true;
						break;
					}
					tried.add(c.subtract(origin).toShortString() + (to == null ? " (no path)" : " (ends at " + to.getEndNode().asBlockPos().subtract(origin).toShortString() + ")"));
				}
				if (!any) {
					cut.add("every store in reach of the " + level.getBlockState(site).getBlock().getName().getString() + " " + site.subtract(origin).toShortString()
						+ ": " + tried);
				}
			}
			walker.discard();
			helper.assertTrue(cut.isEmpty(), entry.id().getPath() + ": no path from the street in front to " + cut);
			helper.succeed();
		});
	}

	/** builders_workshop.md: the Blueprint Table and the builder's chest by it, walked to from the street. */
	//$ gametest_ticks_batch HUGE '100' '"qaWorkshops1121cBuilderPath"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaWorkshops1121cBuilderPath")
	public void qaAVillagerFindsAPathToTheBuildersWorkshopsTableAndChests(GameTestHelper helper) {
		pathsToEveryJobBlockAndItsChest(helper, StarterBlueprints.BUILDERS_WORKSHOP, List.of(ModBlocks.BLUEPRINT_TABLE));
	}

	/** carpenters_workshop.md: the crafting table and the chests, walked to from the street. */
	//$ gametest_ticks_batch HUGE '100' '"qaWorkshops1121cCarpenterPath"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaWorkshops1121cCarpenterPath")
	public void qaAVillagerFindsAPathToTheCarpentersWorkshopsTableAndChests(GameTestHelper helper) {
		pathsToEveryJobBlockAndItsChest(helper, StarterBlueprints.CARPENTERS_WORKSHOP, List.of(Blocks.CRAFTING_TABLE));
	}

	/** fletchers_lodge.md, Level II: both fletching tables and every chest. */
	//$ gametest_ticks_batch HUGE '100' '"qaWorkshops1121cFletcherPath"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaWorkshops1121cFletcherPath")
	public void qaAVillagerFindsAPathToTheFletchersLodgeIIsTablesAndChests(GameTestHelper helper) {
		pathsToEveryJobBlockAndItsChest(helper, StarterBlueprints.FLETCHERS_LODGE_2, List.of(Blocks.FLETCHING_TABLE));
	}

	/** print_shop.md, Level II: the press's cartography table and the Printer's chests. */
	//$ gametest_ticks_batch HUGE '100' '"qaWorkshops1121cPrintPath"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaWorkshops1121cPrintPath")
	public void qaAVillagerFindsAPathToThePrintShopIIsPressAndChests(GameTestHelper helper) {
		pathsToEveryJobBlockAndItsChest(helper, StarterBlueprints.PRINT_SHOP_2, List.of(Blocks.CARTOGRAPHY_TABLE));
	}

	/** flower_shop.md, Level II: the composter and her chests. */
	//$ gametest_ticks_batch HUGE '100' '"qaWorkshops1121cFlowerPath"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaWorkshops1121cFlowerPath")
	public void qaAVillagerFindsAPathToTheFlowerShopIIsComposterAndChests(GameTestHelper helper) {
		pathsToEveryJobBlockAndItsChest(helper, StarterBlueprints.FLOWER_SHOP_2, List.of(Blocks.COMPOSTER));
	}

	/** supply_shop.md, Level III: the Shop Counter and every store of the stock. */
	//$ gametest_ticks_batch HUGE '100' '"qaWorkshops1121cSupplyPath"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaWorkshops1121cSupplyPath")
	public void qaAVillagerFindsAPathToTheSupplyShopIIIsCounterAndStock(GameTestHelper helper) {
		pathsToEveryJobBlockAndItsChest(helper, StarterBlueprints.SUPPLY_SHOP_3, List.of(ModBlocks.SHOP_COUNTER));
	}

	/** clinic.md and physicians_house.md: the brewing stand and its chests. */
	//$ gametest_ticks_batch HUGE '100' '"qaWorkshops1121cCarePath"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaWorkshops1121cCarePath")
	public void qaAVillagerFindsAPathToTheClinicsBrewingStandAndChests(GameTestHelper helper) {
		pathsToEveryJobBlockAndItsChest(helper, StarterBlueprints.CLINIC, List.of(Blocks.BREWING_STAND));
	}

	/** tailors_shop.md, Level II: the loom and its chests. */
	//$ gametest_ticks_batch HUGE '100' '"qaWorkshops1121cTailorPath"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaWorkshops1121cTailorPath")
	public void qaAVillagerFindsAPathToTheTailorsShopIIsLoomAndChests(GameTestHelper helper) {
		pathsToEveryJobBlockAndItsChest(helper, StarterBlueprints.TAILORS_SHOP_2, List.of(Blocks.LOOM));
	}

	/** the Mason's Yard II: both stonecutters and the worker's own store, walked to from the street. */
	//$ gametest_ticks_batch HUGE '100' '"qaWorkshops1121cMasonsPath"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaWorkshops1121cMasonsPath")
	public void qaAVillagerFindsAPathToMasonsJobBlockAndStore(GameTestHelper helper) {
		pathsToEveryJobBlockAndItsChest(helper, StarterBlueprints.MASONS_YARD_2, List.of(Blocks.STONECUTTER));
	}

	/** the Tinker's Workshop II: the smithing table and the worker's own store, walked to from the street. */
	//$ gametest_ticks_batch HUGE '100' '"qaWorkshops1121cTinkersPath"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaWorkshops1121cTinkersPath")
	public void qaAVillagerFindsAPathToTinkersJobBlockAndStore(GameTestHelper helper) {
		pathsToEveryJobBlockAndItsChest(helper, StarterBlueprints.TINKERS_WORKSHOP_2, List.of(Blocks.SMITHING_TABLE));
	}

	/** the Glassblower's Workshop: the Glass Kiln and the worker's own store, walked to from the street. */
	//$ gametest_ticks_batch HUGE '100' '"qaWorkshops1121cGlassPath"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaWorkshops1121cGlassPath")
	public void qaAVillagerFindsAPathToGlassJobBlockAndStore(GameTestHelper helper) {
		pathsToEveryJobBlockAndItsChest(helper, StarterBlueprints.GLASSBLOWERS_WORKSHOP, List.of(ModBlocks.GLASS_KILN));
	}

	/** the Jeweler's Workshop II: the stonecutter and the worker's own store, walked to from the street. */
	//$ gametest_ticks_batch HUGE '100' '"qaWorkshops1121cJewellerPath"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaWorkshops1121cJewellerPath")
	public void qaAVillagerFindsAPathToJewellerJobBlockAndStore(GameTestHelper helper) {
		pathsToEveryJobBlockAndItsChest(helper, StarterBlueprints.JEWELLERS_WORKSHOP_2, List.of(Blocks.STONECUTTER));
	}

	/** the Workshop: the blast furnace and the worker's own store, walked to from the street. */
	//$ gametest_ticks_batch HUGE '100' '"qaWorkshops1121cWorkshopPath"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaWorkshops1121cWorkshopPath")
	public void qaAVillagerFindsAPathToWorkshopJobBlockAndStore(GameTestHelper helper) {
		pathsToEveryJobBlockAndItsChest(helper, StarterBlueprints.WORKSHOP, List.of(Blocks.BLAST_FURNACE));
	}

	/** the Sawmill: the fletching table and the worker's own store, walked to from the street. */
	//$ gametest_ticks_batch HUGE '100' '"qaWorkshops1121cSawmillPath"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaWorkshops1121cSawmillPath")
	public void qaAVillagerFindsAPathToSawmillJobBlockAndStore(GameTestHelper helper) {
		pathsToEveryJobBlockAndItsChest(helper, StarterBlueprints.SAWMILL, List.of(Blocks.FLETCHING_TABLE));
	}

	/** the Brass Foundry: the smoker and the worker's own store, walked to from the street. */
	//$ gametest_ticks_batch HUGE '100' '"qaWorkshops1121cFoundryPath"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaWorkshops1121cFoundryPath")
	public void qaAVillagerFindsAPathToFoundryJobBlockAndStore(GameTestHelper helper) {
		pathsToEveryJobBlockAndItsChest(helper, StarterBlueprints.BRASS_FOUNDRY, List.of(Blocks.SMOKER));
	}

	/** the Physician's House: the brewing stand and the worker's own store, walked to from the street. */
	//$ gametest_ticks_batch HUGE '100' '"qaWorkshops1121cPhysicianPath"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaWorkshops1121cPhysicianPath")
	public void qaAVillagerFindsAPathToPhysicianJobBlockAndStore(GameTestHelper helper) {
		pathsToEveryJobBlockAndItsChest(helper, StarterBlueprints.PHYSICIANS_HOUSE, List.of(Blocks.BREWING_STAND));
	}

	/** the Healing Center III: the brewing stand and the worker's own store, walked to from the street. */
	//$ gametest_ticks_batch HUGE '100' '"qaWorkshops1121cHealingPath"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaWorkshops1121cHealingPath")
	public void qaAVillagerFindsAPathToHealingJobBlockAndStore(GameTestHelper helper) {
		pathsToEveryJobBlockAndItsChest(helper, StarterBlueprints.HEALING_CENTER_3, List.of(Blocks.BREWING_STAND));
	}

	/** the Market Stall III: the Shop Counter and the worker's own store, walked to from the street. */
	//$ gametest_ticks_batch HUGE '100' '"qaWorkshops1121cStallPath"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaWorkshops1121cStallPath")
	public void qaAVillagerFindsAPathToStallJobBlockAndStore(GameTestHelper helper) {
		pathsToEveryJobBlockAndItsChest(helper, StarterBlueprints.MARKET_STALL_3, List.of(ModBlocks.SHOP_COUNTER));
	}

	/** the Winery II: the cauldron and the worker's own store, walked to from the street. */
	//$ gametest_ticks_batch HUGE '100' '"qaWorkshops1121cWineryPath"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaWorkshops1121cWineryPath")
	public void qaAVillagerFindsAPathToWineryJobBlockAndStore(GameTestHelper helper) {
		pathsToEveryJobBlockAndItsChest(helper, StarterBlueprints.WINERY_2, List.of(Blocks.CAULDRON));
	}

	/** the Fletcher's Lodge I: the fletching table and the worker's own store, walked to from the street. */
	//$ gametest_ticks_batch HUGE '100' '"qaWorkshops1121cFletcherOnePath"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaWorkshops1121cFletcherOnePath")
	public void qaAVillagerFindsAPathToFletcherOneJobBlockAndStore(GameTestHelper helper) {
		pathsToEveryJobBlockAndItsChest(helper, StarterBlueprints.FLETCHERS_LODGE, List.of(Blocks.FLETCHING_TABLE));
	}

	/** the Print Shop I: the press and the worker's own store, walked to from the street. */
	//$ gametest_ticks_batch HUGE '100' '"qaWorkshops1121cPrintOnePath"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaWorkshops1121cPrintOnePath")
	public void qaAVillagerFindsAPathToPrintOneJobBlockAndStore(GameTestHelper helper) {
		pathsToEveryJobBlockAndItsChest(helper, StarterBlueprints.PRINT_SHOP, List.of(Blocks.CARTOGRAPHY_TABLE));
	}

	/** the Flower Shop I: the composter and the worker's own store, walked to from the street. */
	//$ gametest_ticks_batch HUGE '100' '"qaWorkshops1121cFlowerOnePath"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaWorkshops1121cFlowerOnePath")
	public void qaAVillagerFindsAPathToFlowerOneJobBlockAndStore(GameTestHelper helper) {
		pathsToEveryJobBlockAndItsChest(helper, StarterBlueprints.FLOWER_SHOP, List.of(Blocks.COMPOSTER));
	}

	/** the Supply Shop I: the Shop Counter and the worker's own store, walked to from the street. */
	//$ gametest_ticks_batch HUGE '100' '"qaWorkshops1121cSupplyOnePath"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaWorkshops1121cSupplyOnePath")
	public void qaAVillagerFindsAPathToSupplyOneJobBlockAndStore(GameTestHelper helper) {
		pathsToEveryJobBlockAndItsChest(helper, StarterBlueprints.SUPPLY_SHOP, List.of(ModBlocks.SHOP_COUNTER));
	}

	/** the Healing Center I: the brewing stand and the worker's own store, walked to from the street. */
	//$ gametest_ticks_batch HUGE '100' '"qaWorkshops1121cHealingOnePath"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaWorkshops1121cHealingOnePath")
	public void qaAVillagerFindsAPathToHealingOneJobBlockAndStore(GameTestHelper helper) {
		pathsToEveryJobBlockAndItsChest(helper, StarterBlueprints.HEALING_CENTER, List.of(Blocks.BREWING_STAND));
	}
}
