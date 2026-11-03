package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane-b-1103-0338, 2026-11-03), bug test: a scanned soul sand elevator is built back dry. The Scan Tool's promise
 * (the builders build a scan again) and docs/wiki/builder.md ("Blocks with no item of their own are built from what a
 * player would use"): a bubble column is water a player poured on soul sand, so it costs a water bucket and is poured
 * again; MaterialRules.NEVER_PLACE lists Blocks.BUBBLE_COLUMN, so it is skipped and the tube stays air. Same round trip as {@link QaScanRebuild1102dGameTests}: scanned
 * with real clicks, built back with free materials.
 */
public class QaScanRebuild1103BugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/**
	 * A soul sand elevator: soul sand under a glass tube of three bubble-column blocks (water a player poured on soul
	 * sand). Built again, the tube holds its water and the bubbles rise again (a dry tube is no elevator).
	 */
	//$ gametest_ticks_batch HUGE '3000' '"qa1103BubbleColumn"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1103BubbleColumn")
	public void aScannedSoulSandElevatorIsBuiltWithItsWater(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		for (int dx = 0; dx < 3; dx++) {
			for (int dz = 0; dz < 3; dz++) {
				s.put(dx, 0, dz, Blocks.STONE);
				for (int dy = 1; dy <= 4; dy++) {
					if (dx != 1 || dz != 1) {
						s.put(dx, dy, dz, Blocks.GLASS);
					}
				}
			}
		}
		s.put(1, 1, 1, Blocks.SOUL_SAND);
		for (int dy = 2; dy <= 4; dy++) {
			s.put(1, dy, 1, Blocks.BUBBLE_COLUMN.defaultBlockState().setValue(BubbleColumnBlock.DRAG_DOWN, false));
		}
		QaScanRebuild1102dGameTests.Built b = QaScanRebuild1102dGameTests.roundTrip(helper, s, Map.of(), true);
		ServerLevel level = helper.getLevel();
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(b.b().site().id()) == null, "still building: " + b.b().site().stage() + " "
				+ b.b().site().status() + " missing " + b.b().site().missing());
			helper.assertTrue(level.getBlockState(b.at(1, 1, 1)).is(Blocks.SOUL_SAND), "the soul sand was built as " + level.getBlockState(b.at(1, 1, 1)));
			StringBuilder tube = new StringBuilder();
			boolean wet = true;
			for (int dy = 2; dy <= 4; dy++) {
				BlockState now = level.getBlockState(b.at(1, dy, 1));
				tube.append(dy).append('=').append(now.getBlock()).append(' ');
				wet &= now.getFluidState().isSource();
			}
			helper.assertTrue(wet, "the scanned elevator's tube was built dry (" + tube.toString().trim() + "); its three blocks of rising water "
				+ "should be poured again");
		});
	}

	/**
	 * B861: a magma block's sinking column (DRAG_DOWN) is the same water: the list asks a water bucket for each of its
	 * blocks, and built again the tube holds water whose bubbles pull down over the magma.
	 */
	//$ gametest_ticks_batch HUGE '3000' '"qa1103BubbleColumnMagma"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1103BubbleColumnMagma")
	public void aScannedMagmaDownColumnIsBuiltWithItsWater(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		for (int dx = 0; dx < 3; dx++) {
			for (int dz = 0; dz < 3; dz++) {
				s.put(dx, 0, dz, Blocks.STONE);
				for (int dy = 1; dy <= 3; dy++) {
					if (dx != 1 || dz != 1) {
						s.put(dx, dy, dz, Blocks.GLASS);
					}
				}
			}
		}
		s.put(1, 1, 1, Blocks.MAGMA_BLOCK);
		for (int dy = 2; dy <= 3; dy++) {
			s.put(1, dy, 1, Blocks.BUBBLE_COLUMN.defaultBlockState().setValue(BubbleColumnBlock.DRAG_DOWN, true));
		}
		QaScanRebuild1102dGameTests.Built b = QaScanRebuild1102dGameTests.roundTrip(helper, s,
			Map.of(net.minecraft.world.item.Items.WATER_BUCKET, 2, net.minecraft.world.item.Items.MAGMA_BLOCK, 1), true);
		ServerLevel level = helper.getLevel();
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(b.b().site().id()) == null, "still building: " + b.b().site().stage() + " "
				+ b.b().site().status() + " missing " + b.b().site().missing());
			for (int dy = 2; dy <= 3; dy++) {
				BlockState now = level.getBlockState(b.at(1, dy, 1));
				helper.assertTrue(now.is(Blocks.BUBBLE_COLUMN) && now.getValue(BubbleColumnBlock.DRAG_DOWN),
					"the magma's column at y" + dy + " was built as " + now + ", not sinking bubbles");
			}
		});
	}
}
