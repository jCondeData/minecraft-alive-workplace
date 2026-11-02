package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane-b-1102-0638), a bug B794's fix leaves: a scanned big dripleaf on two stems is built back with nothing at all
 * (all 3 skipped). A stem only stands with a stem or the leaf above it, and the builder places it bottom-up first.
 */
public class QaScanRebuild1102cBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos SRC = new BlockPos(18, 2, 18);

	/** B794: a big dripleaf on two stems, on moss, is built three tall from three big dripleaves. */
	//$ gametest_ticks_batch HUGE '2400' '"qa1102cDripleaf"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1102cDripleaf")
	public void aScannedBigDripleafOnTwoStemsIsBuiltThreeTall(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(SRC, Blocks.MOSS_BLOCK);
		helper.setBlock(SRC.above(1), Blocks.BIG_DRIPLEAF_STEM);
		helper.setBlock(SRC.above(2), Blocks.BIG_DRIPLEAF_STEM);
		helper.setBlock(SRC.above(3), Blocks.BIG_DRIPLEAF);
		ResourceLocation id = QaBuilders1102bGameTests.scan(helper, SRC, SRC.above(3));
		for (int dy = 3; dy >= 0; dy--) {
			helper.setBlock(SRC.above(dy), Blocks.AIR);
		}
		QaBuilders1102bGameTests.Built b = QaBuilders1102bGameTests.buildBack(helper, id, Rotation.NONE, Mirror.NONE, true);
		Map<Item, Integer> asked = b.site().plan(level).materials();
		helper.assertTrue(asked.getOrDefault(Items.BIG_DRIPLEAF, 0) == 3, "a big dripleaf on two stems asks " + asked + ", not 3 big dripleaves");
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(b.site().id()) == null, "still building: " + b.site().stage() + " " + b.site().status());
			for (int dy = 1; dy <= 2; dy++) {
				BlockState s = level.getBlockState(b.origin().above(dy));
				helper.assertTrue(s.is(Blocks.BIG_DRIPLEAF_STEM), "stem " + dy + " was built as " + s + " (" + b.site().skipped() + " skipped)");
			}
			BlockState leaf = level.getBlockState(b.origin().above(3));
			helper.assertTrue(leaf.is(Blocks.BIG_DRIPLEAF), "the leaf was built as " + leaf + " (" + b.site().skipped() + " skipped)");
		});
	}
}
