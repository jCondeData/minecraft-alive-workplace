package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;

/** QA lane B (2026-11-21): the rebuilt workshops' failing checks, each a bug in ROADMAP "Bugs" (see the test's doc). */
public class QaRebuiltWorkshops1121bBugTests {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/**
	 * carpenters_workshop.md in the world: a Builder builds the Carpenter's Workshop from exactly its materials across a
	 * save and reload mid-STRUCTURE; it ends DONE with every block right (fails today: the tripwire hook at (8, 3, 8)
	 * faces north onto the glass pane at (8, 3, 9), can't stand, and is skipped) and nothing left over; the built workshop has one
	 * crafting table, a job site, a chest within 8 / 4 of it and a bed.
	 */
	//$ gametest_ticks_batch HUGE '40000' '"qaWorkshops1121bCarpenterBuild"'
	@GameTest(template = HUGE, timeoutTicks = 40000, batch = "qaWorkshops1121bCarpenterBuild")
	public void qaABuilderBuildsTheCarpentersWorkshopAcrossAReloadFromExactlyItsMaterials(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaRebuiltWorkshops1121bGameTests.buildAcrossAReload(helper, StarterBlueprints.CARPENTERS_WORKSHOP, new BlockPos(9, 2, 9), placement -> {
			Vec3i size = StarterBlueprints.CARPENTERS_WORKSHOP.size();
			List<BlockPos> tables = QaRebuiltWorkshops1121bGameTests.built(level, placement.origin(), size, Blocks.CRAFTING_TABLE);
			helper.assertTrue(tables.size() == 1, "the built workshop has " + tables.size() + " crafting tables");
			helper.assertTrue(level.getPoiManager().getType(tables.get(0)).isPresent(), "the built crafting table is no job site");
			helper.assertTrue(QaRebuiltWorkshops1121bGameTests.built(level, placement.origin(), size, Blocks.CHEST).stream().anyMatch(c -> QaRebuiltWorkshops1121bGameTests.within(c, tables.get(0), 8, 4)),
				"no built chest within 8 / 4 of the crafting table");
			boolean bed = false;
			for (BlockPos p : BlockPos.betweenClosed(placement.origin(), placement.origin().offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
				bed |= level.getBlockState(p).getBlock() instanceof BedBlock;
			}
			helper.assertTrue(bed, "the built workshop has no bed");
		});
	}
}
