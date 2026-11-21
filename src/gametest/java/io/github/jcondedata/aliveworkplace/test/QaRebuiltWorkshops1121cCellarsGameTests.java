package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.RandomJobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.pathfinder.Path;

/**
 * QA lane C (2026-11-21, lane-c-1121-1853), the rebuilt workshops of Milestone 50 that have a cellar, in the world, and
 * the workers who arrive at a workshop rather than take its block by themselves. Written from the briefs: smithy.md,
 * butchers_shop.md, winery.md, healing_center.md, supply_shop.md, jewellers_workshop.md ("a cellar exactly 4 deep, reached
 * from inside"); carpenters_workshop.md ("a Carpenter waiting in front of the building walks in and claims it"),
 * winery.md ("a Vintner standing 3 blocks before the front edge walks in ... and claims the cauldron"), flower_shop.md
 * (the composter, the Florist's), butchers_shop.md ("a jobless villager who takes it becomes a Butcher").
 *
 * <p>A cellared drawing's file starts at the cellar's floor, so it is placed with its ground floor on raised ground: stone
 * is laid round its footprint up to the ground floor, the cellar under the ground as in a world, the street on top.
 */
public class QaRebuiltWorkshops1121cCellarsGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	/** The drawing's corner (its cellar floor's level, for a cellared one); room for 26 deep in front of a 3-block street. */
	private static final BlockPos AT = new BlockPos(4, 2, 3);

	/** Where the live tests put the drawing: further in, so the villager starts well inside the area (B162: one who wandered off its open edge never came back). */
	private static final BlockPos LIVE_AT = new BlockPos(4, 2, 8);

	/**
	 * Places the drawing at {@code at} on ground raised to its ground floor: stone fills the whole area up to it first (the
	 * cellar layers' cells the file leaves out are the world's ground, as under a real build), then the drawing, whose cellar
	 * rooms are drawn as air. Returns the cellar's depth (the street level's offset).
	 */
	private static int place(GameTestHelper helper, StarterBlueprints.Entry entry, BlockPos at) {
		ServerLevel level = helper.getLevel();
		int b = entry.basement();
		for (int y = 2; y < 2 + b; y++) {
			for (int x = 0; x < 30; x++) {
				for (int z = 0; z < 30; z++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
				}
			}
		}
		StructureTemplate template = level.getStructureManager().get(entry.id()).orElseThrow();
		BlockPos origin = helper.absolutePos(at);
		template.placeInWorld(level, origin, origin, new StructurePlaceSettings().setRotation(Rotation.NONE).setMirror(Mirror.NONE),
			net.minecraft.util.RandomSource.create(173L), 2);
		return b;
	}

	private static boolean store(ServerLevel level, BlockPos p) {
		Block block = level.getBlockState(p).getBlock();
		return block instanceof ChestBlock || block instanceof BarrelBlock;
	}

	/** "  (ends at x, y, z)" in the drawing's own coordinates (y 0 its ground floor). */
	private static String end(Path path, BlockPos ground) {
		return path == null ? " (no path)" : " (ends at " + path.getEndNode().asBlockPos().subtract(ground).toShortString() + ")";
	}

	/**
	 * The drawing placed on raised ground, a villager in the street in front of it: the game's pathfinder (as a villager
	 * claiming a job site: a path to within one block) reaches every job block of {@code jobs}, the cellar's too, and for
	 * each one a store in its supply reach (8 across, 4 up or down) to within two blocks; and a cellar reached by stairs
	 * ("reached from inside") has a store the villager walks down to.
	 */
	private static void paths(GameTestHelper helper, StarterBlueprints.Entry entry, List<Block> jobs) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int b = place(helper, entry, AT);
		helper.assertTrue(b == 4, "(setup) " + entry.id().getPath() + " has a cellar " + b + " deep, the brief says 4");
		BlockPos origin = helper.absolutePos(AT);
		BlockPos ground = origin.above(b);
		var size = entry.size();
		List<BlockPos> all = BlockPos.betweenClosedStream(origin, origin.offset(size.getX() - 1, size.getY() + b - 1, size.getZ() - 1))
			.map(BlockPos::immutable).toList();
		List<BlockPos> sites = all.stream().filter(p -> jobs.stream().anyMatch(level.getBlockState(p)::is)).toList();
		List<BlockPos> stores = all.stream().filter(p -> store(level, p)).toList();
		List<BlockPos> cellar = stores.stream().filter(p -> p.getY() < ground.getY()).toList();
		helper.assertTrue(!sites.isEmpty(), "(setup) no job block placed");
		Villager walker = helper.spawn(EntityType.VILLAGER, AT.offset((size.getX() - 1) / 2, b, -3));
		walker.setNoAi(true);
		helper.runAfterDelay(10, () -> {
			walker.setOnGround(true);
			List<String> cut = new ArrayList<>();
			for (BlockPos site : sites) {
				Path path = walker.getNavigation().createPath(Set.of(site), 1);
				String name = level.getBlockState(site).getBlock().getName().getString() + " " + site.subtract(ground).toShortString();
				if (path == null || !path.canReach()) {
					cut.add(name + end(path, ground));
				}
				List<String> tried = new ArrayList<>();
				boolean any = false;
				for (BlockPos c : stores) {
					if (Math.abs(c.getX() - site.getX()) > 8 || Math.abs(c.getZ() - site.getZ()) > 8 || Math.abs(c.getY() - site.getY()) > 4) {
						continue;
					}
					Path to = walker.getNavigation().createPath(Set.of(c), 2);
					if (to != null && to.canReach()) {
						any = true;
						break;
					}
					tried.add(c.subtract(ground).toShortString() + end(to, ground));
				}
				if (!any) {
					cut.add("every store in reach of the " + name + ": " + tried);
				}
			}
			// a cellar reached by stairs: the pathfinder walks down to at least one of its stores (vanilla's has no ladders,
			// which the briefs allow, so a ladder cellar is left to the drawing tests)
			boolean ladder = all.stream().anyMatch(p -> p.getY() < ground.getY() && level.getBlockState(p).is(Blocks.LADDER));
			if (!ladder && !cellar.isEmpty()) {
				List<String> tried = new ArrayList<>();
				boolean any = false;
				for (BlockPos c : cellar) {
					Path to = walker.getNavigation().createPath(Set.of(c), 2);
					if (to != null && to.canReach()) {
						any = true;
						break;
					}
					tried.add(c.subtract(ground).toShortString() + end(to, ground));
				}
				if (!any) {
					cut.add("every store in the stair cellar: " + tried);
				}
			}
			walker.discard();
			helper.assertTrue(cut.isEmpty(), entry.id().getPath() + " (cellar stores " + cellar.size() + "): no path from the street in front to " + cut);
			helper.succeed();
		});
	}

	/** smithy.md, Level I: the blast furnace, smithing table and grindstone, a chest by the furnace, the 4-deep cellar. */
	//$ gametest_ticks_batch HUGE '100' '"qaWorkshops1121cCellarSmithy"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaWorkshops1121cCellarSmithy")
	public void qaAVillagerFindsAPathToTheSmithysForgeStoreAndCellar(GameTestHelper helper) {
		paths(helper, StarterBlueprints.SMITHY, List.of(Blocks.BLAST_FURNACE, Blocks.SMITHING_TABLE, Blocks.GRINDSTONE));
	}

	/** smithy.md, Level II: both blast furnaces, a chest by each, the cellar. */
	//$ gametest_ticks_batch HUGE '100' '"qaWorkshops1121cCellarSmithy2"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaWorkshops1121cCellarSmithy2")
	public void qaAVillagerFindsAPathToTheSmithyIIsForgesStoresAndCellar(GameTestHelper helper) {
		paths(helper, StarterBlueprints.SMITHY_2, List.of(Blocks.BLAST_FURNACE, Blocks.SMITHING_TABLE, Blocks.GRINDSTONE));
	}

	/** butchers_shop.md: the smoker upstairs above the ground, a chest by it, the freezer cellar. */
	//$ gametest_ticks_batch HUGE '100' '"qaWorkshops1121cCellarButcher"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaWorkshops1121cCellarButcher")
	public void qaAVillagerFindsAPathToTheButchersSmokerStoreAndCellar(GameTestHelper helper) {
		paths(helper, StarterBlueprints.BUTCHERS_SHOP, List.of(Blocks.SMOKER));
	}

	/** winery.md, Level III: the cauldron, the chests under the vat, the cellar under II. */
	//$ gametest_ticks_batch HUGE '100' '"qaWorkshops1121cCellarWinery"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaWorkshops1121cCellarWinery")
	public void qaAVillagerFindsAPathToTheWineryIIIsVatStoreAndCellar(GameTestHelper helper) {
		paths(helper, StarterBlueprints.WINERY_3, List.of(Blocks.CAULDRON));
	}

	/** healing_center.md, Level IV: the brewing stand, its chest, the basement under III. */
	//$ gametest_ticks_batch HUGE '100' '"qaWorkshops1121cCellarHealing"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaWorkshops1121cCellarHealing")
	public void qaAVillagerFindsAPathToTheHealingCenterIVsStandStoreAndBasement(GameTestHelper helper) {
		paths(helper, StarterBlueprints.HEALING_CENTER_4, List.of(Blocks.BREWING_STAND));
	}

	/** supply_shop.md, Level IV: the Shop Counter, the stock in its reach, the basement under III. */
	//$ gametest_ticks_batch HUGE '100' '"qaWorkshops1121cCellarSupply"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaWorkshops1121cCellarSupply")
	public void qaAVillagerFindsAPathToTheSupplyShopIVsCounterStockAndBasement(GameTestHelper helper) {
		paths(helper, StarterBlueprints.SUPPLY_SHOP_4, List.of(ModBlocks.SHOP_COUNTER));
	}

	/** jewellers_workshop.md, Level III: the stonecutter, a container in its reach, the cellar under II. */
	//$ gametest_ticks_batch HUGE '100' '"qaWorkshops1121cCellarJeweller"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaWorkshops1121cCellarJeweller")
	public void qaAVillagerFindsAPathToTheJewelersIIIsStonecutterStoreAndCellar(GameTestHelper helper) {
		paths(helper, StarterBlueprints.JEWELLERS_WORKSHOP_3, List.of(Blocks.STONECUTTER));
	}

	// ---- live: workers taking the workshop ---------------------------------------------------------------------------

	/**
	 * The drawing placed (on raised ground if cellared), one villager at template {@code (x, 0, -3)} in the street: jobless
	 * ({@code arrives} null) or already of the job {@code arrives} with no job site (his first experience point kept, so the
	 * game doesn't take an unused job back); within the time he holds one of the workshop's {@code block}s as his JOB_SITE
	 * and his job is {@code job}. Random station jobs off (a shared block's first job, the one the brief names).
	 */
	private static void takes(GameTestHelper helper, StarterBlueprints.Entry entry, int x, Block block, VillagerProfession arrives, VillagerProfession job) {
		Leftovers.clear(helper);
		boolean random = RandomJobs.ENABLED;
		RandomJobs.ENABLED = false;
		Leftovers.after(helper, () -> RandomJobs.ENABLED = random);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		int b = place(helper, entry, LIVE_AT);
		BlockPos origin = helper.absolutePos(LIVE_AT);
		var size = entry.size();
		List<BlockPos> sites = BlockPos.betweenClosedStream(origin, origin.offset(size.getX() - 1, size.getY() + b - 1, size.getZ() - 1))
			.filter(p -> level.getBlockState(p).is(block)).map(BlockPos::immutable).toList();
		helper.assertTrue(!sites.isEmpty(), "(setup) no " + block.getName().getString() + " placed");
		Villager v = helper.spawn(EntityType.VILLAGER, LIVE_AT.offset(x, b, -3));
		v.setVillagerData(v.getVillagerData().setProfession(arrives == null ? VillagerProfession.NONE : arrives));
		if (arrives != null) {
			v.setVillagerXp(1);
			v.refreshBrain(level); // as when a villager takes a job: its brain looks for that job's blocks (WorkplacesGameTests)
		}
		helper.onEachTick(() -> {
			if (level.getDayTime() % 24000 > 9000) {
				helper.setDayTime(2000);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(v.isAlive(), "the villager died");
			BlockPos site = v.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(g -> g.pos()).orElse(null);
			helper.assertTrue(site != null && sites.contains(site), entry.id().getPath() + ": the villager holds no " + block.getName().getString()
				+ " yet: job " + v.getVillagerData().getProfession() + ", site " + site + ", at "
				+ helper.relativePos(v.blockPosition()).subtract(LIVE_AT.above(b)).toShortString() + " (template coordinates)");
			helper.assertTrue(v.getVillagerData().getProfession() == job, entry.id().getPath() + ": holds it as " + v.getVillagerData().getProfession()
				+ ", wanted " + job);
			v.discard();
		});
	}

	/** carpenters_workshop.md: a Carpenter waiting in front walks in and claims the crafting table (no jobless villager takes it). */
	//$ gametest_ticks_batch HUGE '2400' '"qaWorkshops1121cCarpenterJob"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaWorkshops1121cCarpenterJob")
	public void qaACarpenterInFrontClaimsTheRebuiltWorkshopsCraftingTable(GameTestHelper helper) {
		takes(helper, StarterBlueprints.CARPENTERS_WORKSHOP, (StarterBlueprints.CARPENTERS_WORKSHOP.size().getX() - 1) / 2,
			Blocks.CRAFTING_TABLE, ModVillagers.CARPENTER, ModVillagers.CARPENTER);
	}

	/** winery.md: the Vintner spawned at template (1, 0, -3) walks in, up to the vat's floor, and claims the cauldron (Level II). */
	//$ gametest_ticks_batch HUGE '2400' '"qaWorkshops1121cVintnerJob"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaWorkshops1121cVintnerJob")
	public void qaTheVintnerFromTheStreetClaimsTheWineryIIsCauldron(GameTestHelper helper) {
		takes(helper, StarterBlueprints.WINERY_2, 1, Blocks.CAULDRON, ModVillagers.VINTNER, ModVillagers.VINTNER);
	}

	/** winery.md, Level III (the cellared one): the Vintner from the same spot claims the cauldron above the new cellar. */
	//$ gametest_ticks_batch HUGE '2400' '"qaWorkshops1121cVintnerJob3"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaWorkshops1121cVintnerJob3")
	public void qaTheVintnerFromTheStreetClaimsTheWineryIIIsCauldron(GameTestHelper helper) {
		takes(helper, StarterBlueprints.WINERY_3, 1, Blocks.CAULDRON, ModVillagers.VINTNER, ModVillagers.VINTNER);
	}

	/** flower_shop.md: the composter is the Florist's job block in the shop; a Florist in front walks in and claims it. */
	//$ gametest_ticks_batch HUGE '2400' '"qaWorkshops1121cFloristJob"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaWorkshops1121cFloristJob")
	public void qaAFloristInFrontClaimsTheFlowerShopsComposter(GameTestHelper helper) {
		takes(helper, StarterBlueprints.FLOWER_SHOP, (StarterBlueprints.FLOWER_SHOP.size().getX() - 1) / 2,
			Blocks.COMPOSTER, ModVillagers.FLORIST, ModVillagers.FLORIST);
	}

	/** butchers_shop.md: a jobless villager in front takes the smoker upstairs and becomes the Butcher (the cellared shop). */
	//$ gametest_ticks_batch HUGE '2400' '"qaWorkshops1121cButcherJob"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaWorkshops1121cButcherJob")
	public void qaAJoblessVillagerBecomesTheButcherAtTheRebuiltShop(GameTestHelper helper) {
		takes(helper, StarterBlueprints.BUTCHERS_SHOP, (StarterBlueprints.BUTCHERS_SHOP.size().getX() - 1) / 2,
			Blocks.SMOKER, null, VillagerProfession.BUTCHER);
	}
}
