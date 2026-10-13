package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.ClassWarmup;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/**
 * B103: no day-1 tick stalls on our code. The mod's classes are loaded ahead on a background thread while the server
 * starts (never mixins, client code or tests; integrations only with their mod), and a worker's chests are looked for only in loaded
 * chunks, without waiting for one.
 */
public class ClassWarmupGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final Predicate<String> NONE = mod -> false;
	private static final Predicate<String> ALL = mod -> true;

	/** Ours are loaded ahead, an integration only with its mod; mixins, client code, tests and other mods' classes never. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"b103_warmup"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "b103_warmup")
	public void onlyOurOwnPlainClassesAreLoadedAhead(GameTestHelper helper) {
		String ours = "io.github.jcondedata.aliveworkplace.";
		helper.assertTrue(ClassWarmup.wanted(ours + "work.Village$Cached", NONE), "Village$Cached isn't loaded ahead");
		helper.assertTrue(ClassWarmup.wanted(ours + "hall.Cradles", NONE), "Cradles isn't loaded ahead");
		helper.assertTrue(ClassWarmup.wanted(ours + "AliveWorkplace", NONE), "AliveWorkplace isn't loaded ahead");
		for (String skipped : List.of("mixin.VillagerMixin", "client.AliveWorkplaceClient", "compat.cobblemon.CobblemonCompat",
			"test.ClassWarmupGameTests", "devclient.Shots", "work.package-info", "compat.Compat")) {
			helper.assertFalse(ClassWarmup.wanted(ours + skipped, NONE), skipped + " would be loaded ahead");
		}
		helper.assertTrue(ClassWarmup.wanted(ours + "compat.cobblemon.CobblemonCompat", "cobblemon"::equals), "an installed mod's integration isn't loaded ahead");
		helper.assertFalse(ClassWarmup.wanted(ours + "compat.rct.RctCompat", "cobblemon"::equals), "a missing mod's integration would be loaded ahead");
		helper.assertFalse(ClassWarmup.wanted(ours + "mixin.VillagerMixin", ALL), "a mixin would be loaded ahead");
		helper.assertFalse(ClassWarmup.wanted("net.minecraft.world.entity.npc.Villager", ALL), "a vanilla class would be loaded ahead");
		helper.assertFalse(ClassWarmup.wanted("io.github.jcondedata.aliveworkplacex.Other", ALL), "another mod's class would be loaded ahead");
		helper.succeed();
	}

	/** The mod's own roots list its classes (the ones the hall round and the village-mates check use among them), and every one loads. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"b103_warmup"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "b103_warmup")
	public void everyClassListedLoadsWithoutError(GameTestHelper helper) {
		List<String> names = ClassWarmup.classNames(Platform.get().modRoots(), Platform.get()::isModLoaded);
		for (String expected : List.of("work.Village$Cached", "work.Village$Mate", "work.Gated", "hall.Cradles", "hall.CradleSeat")) {
			helper.assertTrue(names.contains("io.github.jcondedata.aliveworkplace." + expected), expected + " isn't among the " + names.size() + " classes listed");
		}
		helper.assertTrue(names.stream().noneMatch(n -> n.contains(".mixin.") || n.contains(".compat.") && !Platform.get().isModLoaded("cobblemon") || n.contains(".test.")),
			"a skipped class is listed");
		ClassWarmup.Result result = ClassWarmup.run(Platform.get().modRoots());
		helper.assertTrue(result.failed() == 0, result.failed() + " classes failed to load ahead (see the debug log)");
		helper.assertTrue(result.loaded() == names.size() && result.loaded() > 500, "loaded " + result.loaded() + " of " + names.size());
		helper.succeed();
	}

	/** A chest by a workstation is found; a workstation in a chunk that isn't loaded has none, and the chunk stays unloaded. */
	//$ gametest_batch AREA '"b103_chests"'
	@GameTest(template = AREA, batch = "b103_chests")
	public void chestsAreFoundInLoadedChunksOnlyWithoutLoadingOne(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bench = helper.absolutePos(new BlockPos(2, 2, 2));
		helper.setBlock(new BlockPos(4, 2, 2), Blocks.CHEST);
		List<BlockPos> found = SupplyContainers.find(level, bench, null);
		helper.assertTrue(found.equals(List.of(helper.absolutePos(new BlockPos(4, 2, 2)))), "chests by the bench: " + found);
		BlockPos far = new BlockPos(2_000_016, 70, 2_000_016);
		helper.assertTrue(level.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null, "the far chunk was loaded already");
		helper.assertTrue(SupplyContainers.find(level, far, null).isEmpty(), "chests found in an unloaded chunk");
		helper.assertTrue(SupplyContainers.furnaces(level, far).isEmpty(), "furnaces found in an unloaded chunk");
		helper.assertTrue(level.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null, "looking loaded the far chunk");
		helper.succeed();
	}

	/** The shape math a growing child runs, done ahead on fresh shapes: the free point beside the blocks is found. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"b103_warmup"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "b103_warmup")
	public void theShapeWarmUpFindsAFreePoint(GameTestHelper helper) {
		var point = ClassWarmup.shapes();
		helper.assertTrue(point.isPresent(), "no free point found");
		helper.assertTrue(point.get().y >= 0 && point.get().x <= 1.0, "the free point is inside a block: " + point.get());
		helper.succeed();
	}

	/**
	 * The nearest hall is remembered within a tick (a pace asks for it once per source), and still follows a hall set
	 * down or broken in that same tick.
	 */
	//$ gametest_batch AREA '"b103_halls"'
	@GameTest(template = AREA, batch = "b103_halls")
	public void theNearestHallFollowsAHallPlacedOrBrokenWithinATick(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(new BlockPos(3, 2, 3));
		BlockPos hall = helper.absolutePos(new BlockPos(4, 2, 3));
		long tick = level.getGameTime();
		helper.assertFalse(VillageHalls.nearest(level, at).map(hall::equals).orElse(false), "setup: a hall before one is placed");
		helper.setBlock(new BlockPos(4, 2, 3), ModBlocks.VILLAGE_HALL);
		helper.assertTrue(VillageHalls.nearest(level, at).map(hall::equals).orElse(false), "the hall just placed isn't the nearest: " + VillageHalls.nearest(level, at));
		helper.assertTrue(VillageHalls.nearest(level, at).equals(VillageHalls.nearest(level, at.immutable())), "asked twice, two answers");
		helper.setBlock(new BlockPos(4, 2, 3), Blocks.AIR);
		helper.assertFalse(VillageHalls.nearest(level, at).map(hall::equals).orElse(false), "the broken hall is still the nearest");
		helper.setBlock(new BlockPos(4, 2, 3), ModBlocks.VILLAGE_HALL);
		helper.assertTrue(VillageHalls.nearest(level, at).map(hall::equals).orElse(false), "the hall placed again isn't the nearest");
		helper.assertTrue(level.getGameTime() == tick, "setup: the checks ran over more than one tick");
		helper.runAfterDelay(1, () -> {
			helper.assertTrue(VillageHalls.nearest(level, at).map(hall::equals).orElse(false), "the hall isn't the nearest a tick later");
			helper.succeed();
		});
	}

	/** Free workstations: two of the same kind and one of another each come with their job (the job is found once per kind). */
	//$ gametest_batch AREA '"b103_halls"'
	@GameTest(template = AREA, batch = "b103_halls")
	public void freeStationsGiveEachWorkstationItsJob(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(new BlockPos(1, 2, 1));
		helper.setBlock(new BlockPos(3, 2, 2), ModBlocks.BUILDERS_BENCH);
		helper.setBlock(new BlockPos(5, 2, 2), ModBlocks.BUILDERS_BENCH);
		helper.setBlock(new BlockPos(3, 2, 5), Blocks.COMPOSTER);
		helper.runAfterDelay(2, () -> {
			var mine = VillageHalls.freeStations(level, hall).stream().filter(f -> f.pos().getX() >= hall.getX() && f.pos().getX() < hall.getX() + 8
				&& f.pos().getZ() >= hall.getZ() && f.pos().getZ() < hall.getZ() + 8 && f.pos().getY() == hall.getY()).toList();
			helper.assertTrue(mine.size() == 3, "free stations: " + mine);
			helper.assertTrue(mine.stream().filter(f -> f.profession() == ModVillagers.BUILDER).count() == 2, "builders' benches: " + mine);
			helper.assertTrue(mine.stream().anyMatch(f -> f.profession() == net.minecraft.world.entity.npc.VillagerProfession.FARMER
				&& f.pos().equals(helper.absolutePos(new BlockPos(3, 2, 5)))), "the composter: " + mine);
			helper.succeed();
		});
	}

	/**
	 * The buildings a bed is looked up in are worked out once a tick (B103: the pace of every villager at once read every
	 * building's size per villager), and still follow a building finished or taken down in that same tick.
	 */
	//$ gametest_batch AREA '"b103_homes"'
	@GameTest(template = AREA, batch = "b103_homes")
	public void homesFollowABuildingFinishedOrForgottenWithinATick(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		io.github.jcondedata.aliveworkplace.build.BuildSiteManager sites = io.github.jcondedata.aliveworkplace.build.BuildSiteManager.get(level);
		io.github.jcondedata.aliveworkplace.blueprint.BlueprintData.Placement placement = new io.github.jcondedata.aliveworkplace.blueprint.BlueprintData.Placement(
			level.dimension().location(), helper.absolutePos(new BlockPos(2, 2, 2)), net.minecraft.world.level.block.Rotation.NONE,
			net.minecraft.world.level.block.Mirror.NONE);
		net.minecraft.resources.ResourceLocation house = io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints.STONE_HOUSE.id();
		BlockPos bed = helper.absolutePos(new BlockPos(4, 3, 5));
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		long tick = level.getGameTime();
		helper.assertTrue(io.github.jcondedata.aliveworkplace.people.Homes.building(level, bed).isEmpty(), "setup: a building before one is finished");
		sites.recordFinished(house, placement, java.util.UUID.randomUUID());
		helper.assertTrue(io.github.jcondedata.aliveworkplace.people.Homes.building(level, bed).map(b -> b.home().structure().equals(house)).orElse(false),
			"the house just finished isn't the bed's home: " + io.github.jcondedata.aliveworkplace.people.Homes.building(level, bed));
		sites.forgetFinished(placement);
		helper.assertTrue(io.github.jcondedata.aliveworkplace.people.Homes.building(level, bed).isEmpty(), "the house taken down is still the bed's home");
		sites.recordFinished(house, placement, java.util.UUID.randomUUID());
		helper.assertTrue(level.getGameTime() == tick, "setup: the checks ran over more than one tick");
		helper.runAfterDelay(1, () -> {
			helper.assertTrue(io.github.jcondedata.aliveworkplace.people.Homes.building(level, bed).isPresent(), "the house isn't the bed's home a tick later");
			helper.succeed();
		});
	}
}
