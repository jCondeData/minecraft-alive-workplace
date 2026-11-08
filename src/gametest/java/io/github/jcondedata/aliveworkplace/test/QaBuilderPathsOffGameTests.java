package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.Paths;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;

/**
 * B982 (QA lane-b, 2026-11-05; test from tests/builder-paths-1105, QaBuilderPaths1105BugTests): the {@code builderPaths}
 * switch (config-switches.md: "Whether builders lay a dirt path from each finished building to the village's bell or
 * hall") turned off while a builder still has a path to lay. The branch's other two tests there are B981's.
 */
public class QaBuilderPathsOffGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/**
	 * An operator who doesn't want the builders' paths switches {@code builderPaths} off while a builder has 10 blocks
	 * of path left to lay: no more grass is turned into path, and the builder drops what was left (he's free for other
	 * work).
	 */
	//$ gametest_ticks_batch HUGE '1600' '"qaPaths1105Off"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qaPaths1105Off")
	public void pathsSwitchedOffStopAPathAlreadyPlanned(GameTestHelper helper) {
		QaBenchMovePathsPaceGameTests.prepare(helper, 2);
		boolean was = Paths.ENABLED;
		Leftovers.after(helper, () -> Paths.ENABLED = was);
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
			}
		}
		Villager builder = QaBenchMovePathsPaceGameTests.bench(helper, new BlockPos(2, 2, 14), QaBenchMovePathsPaceGameTests.hutMaterials());
		List<BlockPos> spots = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			spots.add(new BlockPos(6 + i, 1, 14));
		}
		ModAttachments.PATH.set(builder, spots.stream().map(helper::absolutePos).map(BlockPos::immutable).toList());
		Paths.ENABLED = false;
		helper.runAfterDelay(1200, () -> {
			int laid = 0;
			for (BlockPos p : spots) {
				laid += helper.getBlockState(p).is(Blocks.DIRT_PATH) ? 1 : 0;
			}
			helper.assertTrue(laid == 0, laid + " of " + spots.size() + " blocks of path laid after builderPaths was switched off");
			helper.assertTrue(!ModAttachments.PATH.has(builder), "the builder still keeps the path he planned: "
				+ ModAttachments.PATH.getOrElse(builder, List.of()).size() + " blocks");
			helper.succeed();
		});
	}
}
