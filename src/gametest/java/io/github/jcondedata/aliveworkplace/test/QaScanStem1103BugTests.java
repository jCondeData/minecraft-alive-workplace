package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane-b-1103-0338, 2026-11-03), bug test: a melon's grown stem (the attached stem, which has no item of its own)
 * is left out when a scan is built again. docs/wiki/builder.md: "Blocks with no item of their own are built from what a
 * player would use" (B793, B794): a player plants melon seeds, so the stem costs one melon seeds and is built again as
 * a melon stem. Same round trip as {@link QaScanRebuild1102dGameTests}.
 */
public class QaScanStem1103BugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	//$ gametest_ticks_batch HUGE '3000' '"qa1103AttachedStemBug"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1103AttachedStemBug")
	public void aMelonsGrownStemIsBuiltAgainFromMelonSeeds(GameTestHelper helper) {
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
		Map<net.minecraft.world.item.Item, Integer> asked = b.b().site().plan(level).materials();
		helper.assertTrue(asked.getOrDefault(Items.MELON_SEEDS, 0) == 1, "the list asks " + asked + ", not 1 melon seeds for the melon's stem");
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(b.b().site().id()) == null, "still building: " + b.b().site().stage() + " "
				+ b.b().site().status() + " missing " + b.b().site().missing());
			BlockState stem = level.getBlockState(b.at(0, 1, 0));
			helper.assertTrue(stem.is(Blocks.MELON_STEM) || stem.is(Blocks.ATTACHED_MELON_STEM), "the melon's stem was built as " + stem);
		});
	}

	/** B865: a pumpkin's grown stem the same: the list asks one pumpkin seeds, and a pumpkin stem is planted. */
	//$ gametest_ticks_batch HUGE '3000' '"qa1103AttachedPumpkinStem"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1103AttachedPumpkinStem")
	public void aPumpkinsGrownStemIsBuiltAgainFromPumpkinSeeds(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		for (int dx = 0; dx < 3; dx++) {
			for (int dz = 0; dz < 3; dz++) {
				s.put(dx, 0, dz, Blocks.STONE);
			}
		}
		s.put(0, 0, 1, Blocks.WATER);
		s.put(0, 0, 0, Blocks.FARMLAND);
		s.put(0, 0, 2, Blocks.DIRT);
		s.put(0, 1, 0, Blocks.ATTACHED_PUMPKIN_STEM.defaultBlockState().setValue(AttachedStemBlock.FACING, Direction.EAST));
		s.put(1, 1, 0, Blocks.PUMPKIN);
		QaScanRebuild1102dGameTests.Built b = QaScanRebuild1102dGameTests.roundTrip(helper, s, Map.of(Items.PUMPKIN_SEEDS, 1, Items.PUMPKIN, 1), true);
		ServerLevel level = helper.getLevel();
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(b.b().site().id()) == null, "still building: " + b.b().site().stage() + " "
				+ b.b().site().status() + " missing " + b.b().site().missing());
			BlockState stem = level.getBlockState(b.at(0, 1, 0));
			helper.assertTrue(stem.is(Blocks.PUMPKIN_STEM) || stem.is(Blocks.ATTACHED_PUMPKIN_STEM), "the pumpkin's stem was built as " + stem);
			helper.assertTrue(level.getBlockState(b.at(1, 1, 0)).is(Blocks.PUMPKIN), "the pumpkin was built as " + level.getBlockState(b.at(1, 1, 0)));
		});
	}
}
