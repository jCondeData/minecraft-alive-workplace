package io.github.jcondedata.aliveworkplace.test;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane B, 2026-11-11) bug tests (B1195, fixed by lane-a-1114-0023): a survival player scans a box holding only a
 * fluid (water, lava, a bubble column). The scan isn't refused as empty (only air is), so it spends a Blank Blueprint and
 * the Blueprint Table lists the build by its non-air blocks, but its preview leaves out every invisible block, so the
 * Table shows the build with an empty preview. Promise: either "There's nothing built in that box" with the Blank kept,
 * or a preview that draws something. Shares {@link QaScanPreview1111bGameTests#scanAndCheck} (which deletes the scans).
 */
public class QaScanPreview1111bBugTests implements FabricGameTest {
	private static final String AREA = QaScanPreview1111bGameTests.AREA;
	private static final net.minecraft.core.BlockPos AT = QaScanPreview1111bGameTests.AT;

	// ---- fluids (invisible to the preview) ----

	/** A water source, scanned the tick it is poured (before it flows). */
	//$ gametest_batch AREA '"qaScanPreview1111bWater"'
	@GameTest(template = AREA, batch = "qaScanPreview1111bWater")
	public void aScannedPondOfWaterIsRefusedOrHasAPreview(GameTestHelper helper) {
		QaScanPreview1111bGameTests.scanAndCheck(helper, "a water source", AT, AT.offset(1, 0, 1), h -> {
			h.setBlock(AT, Blocks.WATER);
			h.setBlock(AT.east(), Blocks.WATER);
			h.setBlock(AT.south(), Blocks.WATER);
			h.setBlock(AT.offset(1, 0, 1), Blocks.WATER);
		});
	}

	/** A lava source, scanned the tick it is poured. */
	//$ gametest_batch AREA '"qaScanPreview1111bLava"'
	@GameTest(template = AREA, batch = "qaScanPreview1111bLava")
	public void aScannedLavaPoolIsRefusedOrHasAPreview(GameTestHelper helper) {
		QaScanPreview1111bGameTests.scanAndCheck(helper, "a lava source", AT, AT, h -> h.setBlock(AT, Blocks.LAVA));
	}

	/** A bubble column two tall over soul sand; only the column (not the soul sand) is in the box. */
	//$ gametest_batch AREA '"qaScanPreview1111bBubbles"'
	@GameTest(template = AREA, batch = "qaScanPreview1111bBubbles")
	public void aScannedBubbleColumnIsRefusedOrHasAPreview(GameTestHelper helper) {
		QaScanPreview1111bGameTests.scanAndCheck(helper, "a bubble column", AT.above(), AT.above(2), h -> {
			h.setBlock(AT, Blocks.SOUL_SAND);
			h.setBlock(AT.above(), Blocks.BUBBLE_COLUMN);
			h.setBlock(AT.above(2), Blocks.BUBBLE_COLUMN);
			h.assertTrue(h.getBlockState(AT.above(2)).is(Blocks.BUBBLE_COLUMN), "no bubble column to scan: " + h.getBlockState(AT.above(2)));
		});
	}
}
