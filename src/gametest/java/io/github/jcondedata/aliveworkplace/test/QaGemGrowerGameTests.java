package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.gem.GemGrowers;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane d, 2026-10-25) of ROADMAP 28.11, the Gem Grower, from the spec: "beds within 16 blocks of the stonecutter"
 * (a full cluster 16 blocks out along one side is picked; one 17 blocks out along another is left), and "only full
 * clusters are picked, the budding block is never broken" on a budding block with a full cluster on every one of its
 * six faces (as in a geode): all six picked, the budding block still there.
 */
public class QaGemGrowerGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String AREA = "aliveworkplace_test:build_area";

	private static void bed(GameTestHelper helper, BlockPos budding, Direction face) {
		helper.setBlock(budding, Blocks.BUDDING_AMETHYST);
		helper.setBlock(budding.relative(face), Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, face));
	}

	/**
	 * The stonecutter at (2, 2, 2): a bed 16 blocks east of it is picked; a bed 17 blocks south of it is not, even 400
	 * ticks after the first was picked.
	 */
	//$ gametest_ticks_batch HUGE '3000' '"qaGemReach"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qaGemReach")
	public void qaABedSixteenBlocksOutIsKeptAndSeventeenIsNot(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos in = new BlockPos(2 + 16, 2, 2);
		BlockPos out = new BlockPos(2, 2, 2 + 17);
		bed(helper, in, Direction.UP);
		bed(helper, out, Direction.UP);
		Villager grower = GemGrowerGameTests.grower(helper);
		long[] picked = {-1};
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getBlockState(out.above()).is(Blocks.AMETHYST_CLUSTER), "the cluster 17 blocks from the stonecutter was picked");
			helper.assertFalse(GemGrowers.beds(grower).contains(helper.absolutePos(out)), "the bed 17 blocks out is one of hers: " + GemGrowers.beds(grower));
			helper.assertTrue(helper.getBlockState(in).is(Blocks.BUDDING_AMETHYST), "the budding block 16 out was broken");
			helper.assertFalse(helper.getBlockState(in.above()).is(Blocks.AMETHYST_CLUSTER),
				"the cluster 16 blocks from the stonecutter wasn't picked; her beds " + GemGrowers.beds(grower));
			long now = helper.getTick();
			if (picked[0] < 0) {
				picked[0] = now;
			}
			helper.assertTrue(now >= picked[0] + 400, "waiting to see the far bed is left");
		});
	}

	/** A budding block with a full cluster on each of its six faces: she picks all six and leaves the budding block. */
	//$ gametest_ticks_batch AREA '2400' '"qaGemSixFaces"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaGemSixFaces")
	public void qaClustersOnEveryFaceOfTheBuddingBlockArePicked(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos budding = new BlockPos(8, 3, 4);
		helper.setBlock(budding, Blocks.BUDDING_AMETHYST);
		for (Direction face : Direction.values()) {
			helper.setBlock(budding.relative(face), Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, face));
		}
		Villager grower = GemGrowerGameTests.grower(helper);
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getBlockState(budding).is(Blocks.BUDDING_AMETHYST), "the budding block was broken");
			StringBuilder left = new StringBuilder();
			for (Direction face : Direction.values()) {
				if (helper.getBlockState(budding.relative(face)).is(Blocks.AMETHYST_CLUSTER)) {
					left.append(' ').append(face);
				}
			}
			helper.assertTrue(left.isEmpty(), "full clusters left on the" + left + " side(s); her beds " + GemGrowers.beds(grower));
		});
	}
}
