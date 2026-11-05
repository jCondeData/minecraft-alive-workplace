package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.Paths;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-b, 2026-11-05): the {@code builderPaths} switch (config-switches.md: "Whether builders lay a dirt path from
 * each finished building to the village's bell or hall") turned off while a builder still has a path to lay. Setup in
 * {@link QaBuilderPaths1105GameTests}.
 */
public class QaBuilderPaths1105BugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final net.minecraft.resources.ResourceLocation TEST_HUT = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");

	/**
	 * "Roads already there are walked over as they are": an old dirt path (a village's own, or one a builder laid for
	 * an earlier house) runs right across the way from the hut to the bell. The new path crosses it, leaves it as it
	 * is, and reaches the bell.
	 */
	//$ gametest_ticks_batch HUGE '4000' '"qaPaths1105OldPath"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qaPaths1105OldPath")
	public void anOldDirtPathAcrossTheWayIsWalkedOverAndTheBellReached(GameTestHelper helper) {
		net.minecraft.server.level.ServerLevel level = QaBuilderPaths1105GameTests.arena(helper);
		for (int x = 0; x < 30; x++) {
			helper.setBlock(new BlockPos(x, 1, 15), Blocks.DIRT_PATH);
		}
		BlockPos bell = new BlockPos(14, 2, 27);
		helper.setBlock(bell, Blocks.BELL);
		Villager builder = QaBenchMovePathsPaceGameTests.bench(helper, new BlockPos(2, 2, 24), QaBenchMovePathsPaceGameTests.hutMaterials());
		var site = io.github.jcondedata.aliveworkplace.build.Builders.start(level, builder, null, TEST_HUT, QaBenchMovePathsPaceGameTests.placement(helper, new BlockPos(12, 2, 2)));
		helper.succeedWhen(() -> {
			QaBuilderPaths1105GameTests.done(helper, level, site, builder);
			int north = QaBuilderPaths1105GameTests.paths(helper, 0, 14);
			int south = QaBuilderPaths1105GameTests.paths(helper, 16, 29);
			helper.assertTrue(north >= 4 && south >= 4, "the new path should run on both sides of the old one: " + north + " north, " + south + " south");
			helper.assertTrue(QaBuilderPaths1105GameTests.near(helper, bell, 3), "the path doesn't reach the bell");
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
		net.minecraft.server.level.ServerLevel level = QaBuilderPaths1105GameTests.arena(helper);
		BlockPos bell = new BlockPos(14, 2, 24);
		for (int x = 9; x <= 19; x++) {
			for (int z = 19; z <= 29; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.DIRT_PATH);
			}
		}
		helper.setBlock(bell, Blocks.BELL);
		Villager builder = QaBenchMovePathsPaceGameTests.bench(helper, new BlockPos(2, 2, 10), QaBenchMovePathsPaceGameTests.hutMaterials());
		var site = io.github.jcondedata.aliveworkplace.build.Builders.start(level, builder, null, TEST_HUT, QaBenchMovePathsPaceGameTests.placement(helper, new BlockPos(12, 2, 2)));
		helper.succeedWhen(() -> {
			QaBuilderPaths1105GameTests.done(helper, level, site, builder);
			int laid = QaBuilderPaths1105GameTests.paths(helper, 7, 18);
			helper.assertTrue(laid >= 3, "only " + laid + " blocks of new path between the hut and the square round the bell");
			helper.succeed();
		});
	}

	/**
	 * An operator who doesn't want the builders' paths switches {@code builderPaths} off while a builder has 10 blocks
	 * of path left to lay: no more grass is turned into path, and the builder drops what was left (he's free for other
	 * work).
	 */
	//$ gametest_ticks_batch HUGE '1600' '"qaPaths1105Off"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qaPaths1105Off")
	public void pathsSwitchedOffStopAPathAlreadyPlanned(GameTestHelper helper) {
		QaBuilderPaths1105GameTests.arena(helper);
		Villager builder = QaBenchMovePathsPaceGameTests.bench(helper, new BlockPos(2, 2, 14), QaBenchMovePathsPaceGameTests.hutMaterials());
		List<BlockPos> spots = QaBuilderPaths1105GameTests.row(helper, 6, 14, 10);
		ModAttachments.PATH.set(builder, QaBuilderPaths1105GameTests.absolute(helper, spots));
		Paths.ENABLED = false; // arena() puts the old value back after the test
		helper.runAfterDelay(1200, () -> {
			int laid = 0;
			for (BlockPos p : spots) {
				laid += helper.getBlockState(p).is(Blocks.DIRT_PATH) ? 1 : 0;
			}
			helper.assertTrue(laid == 0, laid + " of " + spots.size() + " blocks of path laid after builderPaths was switched off");
			helper.succeed();
		});
	}
}
