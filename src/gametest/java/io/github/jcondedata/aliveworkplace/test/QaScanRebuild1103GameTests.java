package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane-b-1103-0338, 2026-11-03): Scan Tool round trips of blocks with no item of their own that the scan tests left
 * out, from the Scan Tool's promise (the builders build a scan again) and docs/wiki/builder.md ("Blocks with no item of
 * their own are built from what a player would use"). Same round trip as {@link QaScanRebuild1102dGameTests}: scanned
 * with real clicks, built back with free materials.
 */
public class QaScanRebuild1103GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/**
	 * A melon grown on its stem (the attached stem has no item; a player plants melon seeds): the build ends, the melon
	 * and the farmland stand again, and nothing in the scene stops the build (kills the mutant of
	 * MaterialRules.placedByAnotherItem that throws for every block with no item).
	 */
	//$ gametest_ticks_batch HUGE '3000' '"qa1103AttachedStem"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1103AttachedStem")
	public void aMelonOnItsGrownStemIsBuiltAgain(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		for (int dx = 0; dx < 3; dx++) {
			for (int dz = 0; dz < 3; dz++) {
				s.put(dx, 0, dz, Blocks.STONE);
			}
		}
		s.put(0, 0, 1, Blocks.WATER);
		s.put(0, 0, 0, Blocks.FARMLAND);
		s.put(1, 0, 0, Blocks.DIRT);
		s.put(0, 1, 0, Blocks.ATTACHED_MELON_STEM.defaultBlockState().setValue(AttachedStemBlock.FACING, Direction.EAST));
		s.put(1, 1, 0, Blocks.MELON);
		QaScanRebuild1102dGameTests.Built b = QaScanRebuild1102dGameTests.roundTrip(helper, s, Map.of(), true);
		ServerLevel level = helper.getLevel();
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(b.b().site().id()) == null, "still building: " + b.b().site().stage() + " "
				+ b.b().site().status() + " missing " + b.b().site().missing());
			helper.assertTrue(level.getBlockState(b.at(1, 1, 0)).is(Blocks.MELON), "the melon was built as " + level.getBlockState(b.at(1, 1, 0)));
			BlockState foot = level.getBlockState(b.at(0, 0, 0));
			helper.assertTrue(foot.is(Blocks.FARMLAND) || foot.is(Blocks.DIRT), "the stem's farmland was built as " + foot);
			helper.assertTrue(level.getBlockState(b.at(0, 0, 1)).is(Blocks.WATER), "the water was built as " + level.getBlockState(b.at(0, 0, 1)));
		});
	}
}
