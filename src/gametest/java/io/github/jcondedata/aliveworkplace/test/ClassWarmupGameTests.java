package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.ClassWarmup;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
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
}
