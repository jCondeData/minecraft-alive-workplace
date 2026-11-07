package io.github.jcondedata.aliveworkplace.test;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * Bugs found by QA (2026-11-05) in B952/B964 (what a struck builder leaves); helpers in {@link QaBuilderGaps1105GameTests}.
 * Fail today. docs/wiki/builder.md: the bag and blueprint go "on top of their workstation (a Builder's Blueprint Table)
 * when that is 4 or more blocks from the bolt, else on the nearest open ground ... the message names that spot". When
 * the Table has been broken (the builder still remembers its spot, as a builder working more than 16 blocks from it
 * does), everything is put in the air where the Table's top was and falls, so the message names a spot in mid-air, not
 * where the items lie.
 */
public class QaBuilderGaps1105BugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/**
	 * The builder's Table, out of the bolt's reach, broken a tick before a real bolt strikes him (no workstation to put
	 * things on): the bag and blueprint must lie together on open ground out of the bolt's reach, at the spot the message
	 * names. Today they're put where the Table's top was and fall a block, and the message names the spot above them.
	 */
	//$ gametest_ticks_batch HUGE '400' '"qaGapsBenchBroken"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qaGapsBenchBroken")
	public void aBuilderWhoseBenchWasBrokenGetsTheBagOnTheGroundTheMessageNames(GameTestHelper helper) {
		BlockPos bench = new BlockPos(16, 2, 10);
		QaBuilderGaps1105GameTests.struck(helper, bench, new BlockPos(10, 2, 10), h -> {
		}, h -> h.getLevel().destroyBlock(h.absolutePos(bench), false), (told, lying) -> {
		});
	}

	/**
	 * The same, the Table on a stone mound 4 high (its top at y 5), 20 blocks from the builder, blown up with the mound's
	 * middle under it (a creeper's crater, 3 by 3, 4 deep) a tick before the bolt: the bag and blueprint must lie at the
	 * spot the message names. Today the message names the air where the Table's top was (y 7), and they lie at the
	 * crater's bottom (y 3), four blocks lower.
	 */
	//$ gametest_ticks_batch HUGE '400' '"qaGapsBenchCrater"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qaGapsBenchCrater")
	public void aBuilderWhoseBenchWasBlownUpGetsTheBagWhereTheMessageSays(GameTestHelper helper) {
		BlockPos bench = new BlockPos(24, 6, 24);
		QaBuilderGaps1105GameTests.struck(helper, bench, new BlockPos(6, 2, 6), h -> {
			for (int x = 20; x <= 28; x++) {
				for (int z = 20; z <= 28; z++) {
					for (int y = 2; y <= 5; y++) {
						h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
					}
				}
			}
		}, h -> {
			for (int x = 23; x <= 25; x++) {
				for (int z = 23; z <= 25; z++) {
					for (int y = 3; y <= 6; y++) {
						h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
					}
				}
			}
		}, (told, lying) -> {
		});
	}
}
