package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Paths;
import io.github.jcondedata.aliveworkplace.city.Roads;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * B981 (QA lane-b, 2026-11-05; the first two tests from tests/builder-paths-1105, QaBuilderPaths1105BugTests): the
 * route search for a builder's path (and a Steward's road) took only blocks with a full top as ground, so dirt path
 * (15/16 high) was a wall and a vanilla village's own paths stopped every new one. Ground a little lower than a block
 * (dirt path, farmland, mud, soul sand) is now stood on; a lectern, a chest, a fence or a cauldron still isn't. Setup in
 * {@link QaBuilderPaths1105GameTests}.
 */
public class QaBuilderPathsOldPathGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");

	/**
	 * "Roads already there are walked over as they are": an old dirt path (a village's own, or one a builder laid for
	 * an earlier house) runs right across the way from the hut to the bell. The new path crosses it, leaves it as it
	 * is, and reaches the bell.
	 */
	//$ gametest_ticks_batch HUGE '4000' '"qaPaths1105OldPath"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qaPaths1105OldPath")
	public void anOldDirtPathAcrossTheWayIsWalkedOverAndTheBellReached(GameTestHelper helper) {
		ServerLevel level = QaBuilderPaths1105GameTests.arena(helper);
		for (int x = 0; x < 30; x++) {
			helper.setBlock(new BlockPos(x, 1, 15), Blocks.DIRT_PATH);
		}
		BlockPos bell = new BlockPos(14, 2, 27);
		helper.setBlock(bell, Blocks.BELL);
		Villager builder = QaBenchMovePathsPaceGameTests.bench(helper, new BlockPos(2, 2, 24), QaBenchMovePathsPaceGameTests.hutMaterials());
		var site = Builders.start(level, builder, null, TEST_HUT, QaBenchMovePathsPaceGameTests.placement(helper, new BlockPos(12, 2, 2)));
		helper.succeedWhen(() -> {
			QaBuilderPaths1105GameTests.done(helper, level, site, builder);
			int north = QaBuilderPaths1105GameTests.paths(helper, 0, 14);
			int south = QaBuilderPaths1105GameTests.paths(helper, 16, 29);
			helper.assertTrue(north >= 4 && south >= 4, "the new path should run on both sides of the old one: " + north + " north, " + south + " south");
			helper.assertTrue(QaBuilderPaths1105GameTests.near(helper, bell, 3), "the path doesn't reach the bell");
			for (int x = 0; x < 30; x++) {
				helper.assertBlockPresent(Blocks.DIRT_PATH, new BlockPos(x, 1, 15));
			}
			helper.succeed();
		});
	}

	/**
	 * A bell in a little square of dirt path, as vanilla villages have round their meeting point: the finished hut's
	 * path still leads to it.
	 */
	//$ gametest_ticks_batch HUGE '4000' '"qaPaths1105Square"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qaPaths1105Square")
	public void aBellInASquareOfDirtPathStillGetsThePath(GameTestHelper helper) {
		ServerLevel level = QaBuilderPaths1105GameTests.arena(helper);
		BlockPos bell = new BlockPos(14, 2, 24);
		for (int x = 9; x <= 19; x++) {
			for (int z = 19; z <= 29; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.DIRT_PATH);
			}
		}
		helper.setBlock(bell, Blocks.BELL);
		Villager builder = QaBenchMovePathsPaceGameTests.bench(helper, new BlockPos(2, 2, 10), QaBenchMovePathsPaceGameTests.hutMaterials());
		var site = Builders.start(level, builder, null, TEST_HUT, QaBenchMovePathsPaceGameTests.placement(helper, new BlockPos(12, 2, 2)));
		helper.succeedWhen(() -> {
			QaBuilderPaths1105GameTests.done(helper, level, site, builder);
			int laid = QaBuilderPaths1105GameTests.paths(helper, 7, 18);
			helper.assertTrue(laid >= 3, "only " + laid + " blocks of new path between the hut and the square round the bell");
			helper.succeed();
		});
	}

	/**
	 * What counts as ground to stand on: a full top, or a flat top a little lower than a block (dirt path, farmland, mud,
	 * soul sand); never a lectern, an enchanting table, a chest, a fence, a cauldron or water.
	 */
	//$ gametest_batch HUGE '"qaPathsOldGround"'
	@GameTest(template = HUGE, batch = "qaPathsOldGround")
	public void lowGroundIsStoodOnButFurnitureIsNot(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<Block> ground = List.of(Blocks.GRASS_BLOCK, Blocks.STONE, Blocks.DIRT_PATH, Blocks.FARMLAND, Blocks.MUD, Blocks.SOUL_SAND);
		List<Block> not = List.of(Blocks.LECTERN, Blocks.ENCHANTING_TABLE, Blocks.CHEST, Blocks.OAK_FENCE, Blocks.CAULDRON, Blocks.WATER);
		int x = 1;
		for (Block b : ground) {
			helper.setBlock(new BlockPos(x, 2, 2), b);
			BlockPos feet = Paths.walkable(level, helper.absolutePos(new BlockPos(x, 3, 2)));
			helper.assertTrue(feet != null && feet.equals(helper.absolutePos(new BlockPos(x, 3, 2))),
				"a villager should stand on " + b.getName().getString() + " (got " + feet + ")");
			x += 2;
		}
		x = 1;
		for (Block b : not) {
			// a pit of air round the block, so only the block itself could be stood on
			for (int y = 0; y <= 4; y++) {
				helper.setBlock(new BlockPos(x, y, 6), Blocks.AIR);
			}
			helper.setBlock(new BlockPos(x, 2, 6), b);
			BlockPos feet = Paths.walkable(level, helper.absolutePos(new BlockPos(x, 3, 6)));
			helper.assertTrue(feet == null, "a villager can't stand on " + b.getName().getString() + ", yet the path search did at " + feet);
			x += 2;
		}
		helper.succeed();
	}

	/** Bands of farmland, mud and soul sand right across the way: the route walks over each (and turns none into path). */
	//$ gametest_batch HUGE '"qaPathsOldGround"'
	@GameTest(template = HUGE, batch = "qaPathsOldGround")
	public void aRouteCrossesFarmlandMudAndSoulSand(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
			}
			helper.setBlock(new BlockPos(x, 1, 12), Blocks.FARMLAND);
			helper.setBlock(new BlockPos(x, 1, 13), Blocks.MUD);
			helper.setBlock(new BlockPos(x, 1, 14), Blocks.SOUL_SAND);
		}
		List<BlockPos> route = Paths.route(level, helper.absolutePos(new BlockPos(15, 2, 5)), helper.absolutePos(new BlockPos(15, 2, 22)), null);
		helper.assertTrue(!route.isEmpty(), "no route over the farmland, mud and soul sand");
		for (Block b : List.of(Blocks.FARMLAND, Blocks.MUD, Blocks.SOUL_SAND)) {
			helper.assertTrue(route.stream().anyMatch(p -> level.getBlockState(p.below()).is(b)), "the route goes round the " + b.getName().getString() + " instead of over it");
		}
		helper.succeed();
	}

	/** A Steward's road (27.15, the same search with its width and Roads.clear) crosses an old dirt path too. */
	//$ gametest_batch HUGE '"qaPathsOldGround"'
	@GameTest(template = HUGE, batch = "qaPathsOldGround")
	public void aStewardsRoadCrossesAnOldDirtPath(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
			}
			helper.setBlock(new BlockPos(x, 1, 14), Blocks.DIRT_PATH);
		}
		BlockPos start = helper.absolutePos(new BlockPos(15, 2, 5));
		Paths.Search search = new Paths.Search(level, start, helper.absolutePos(new BlockPos(15, 2, 22)),
			new Paths.Options(1, List.of(), 24, Roads::clear));
		search.step(20000);
		List<BlockPos> route = search.result();
		helper.assertTrue(!route.isEmpty(), "no road across the old dirt path");
		helper.assertTrue(route.stream().anyMatch(p -> level.getBlockState(p.below()).is(Blocks.DIRT_PATH)), "the road goes round the old dirt path instead of over it");
		helper.succeed();
	}
}
