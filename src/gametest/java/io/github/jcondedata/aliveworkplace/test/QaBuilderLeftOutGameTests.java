package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-b-1027-0338), from the spec (wiki builder.md, "Unfinished blocks are never claimed done": "Blocks already
 * given up (deferred, then skipped) are not gone back for"), for the STRUCTURE stage. The decoration case is
 * {@link QaBuilderVerify1026bGameTests#aTorchGivenUpWhileItsWallWasGoneIsNotGoneBackFor}; this one kills the nightly
 * mutation survivor in BuildSite#defer (run 40296140142, {@code stage == STRUCTURE} turned to {@code !=}): a wall
 * plank given up during the structure was then gone back for at the build's last look.
 *
 * <p>A player sets a chest down where a late wall plank goes, just as the structure starts. The builder never breaks a
 * container, so the plank is deferred, then given up (skipped). Once the decoration starts the player picks the chest
 * up again. The build ends without going back for that plank: its spot stays empty, it counts in skipped, rechecks 0.
 * Upkeep (the later repair round) is off, so only the build itself could put the plank back.
 */
public class QaBuilderLeftOutGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");

	//$ gametest_ticks_batch AREA '6000' '"qaLeftOutStructure"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qaLeftOutStructure")
	public void aWallPlankGivenUpForAChestIsNotGoneBackForOnceTheChestIsGone(GameTestHelper helper) {
		QaBuilderDisruptionGameTests.Run r = QaBuilderDisruptionGameTests.start(helper, ModBlocks.BUILDERS_BENCH);
		boolean upkeep = Upkeep.ENABLED;
		Upkeep.ENABLED = false;
		Leftovers.after(helper, () -> Upkeep.ENABLED = upkeep);
		// the structure's last bare wall plank (no decoration on or beside it), so the chest stands in a finished wall
		BlockPos spot = r.plan().steps(BuildPlan.Stage.STRUCTURE).stream()
			.filter(s -> s.state().is(Blocks.OAK_PLANKS))
			.filter(s -> r.plan().steps(BuildPlan.Stage.DECORATION).stream().noneMatch(d -> !d.state().isAir() && d.pos().distManhattan(s.pos()) <= 1))
			.map(s -> s.pos().immutable())
			.max(Comparator.comparingInt(BlockPos::getY))
			.orElseThrow(() -> new GameTestAssertException("setup: the test hut has no bare wall plank in its structure"));
		int[] phase = {0}; // 0 waiting for the structure, 1 chest down, 2 chest taken back
		helper.onEachTick(() -> {
			if (phase[0] == 0 && r.site().stage() == BuildPlan.Stage.STRUCTURE) {
				helper.assertTrue(r.level().getBlockState(spot).isAir(), "setup: the plank was up before the structure started");
				r.level().setBlockAndUpdate(spot, Blocks.CHEST.defaultBlockState());
				phase[0] = 1;
				LOGGER.info("qa left out: chest set down at {} at the start of the structure", helper.relativePos(spot));
			} else if (phase[0] == 1 && (r.site().stage() == BuildPlan.Stage.DECORATION || r.site().stage() == BuildPlan.Stage.LANDSCAPE)) {
				helper.assertTrue(r.level().getBlockState(spot).is(Blocks.CHEST), "the builder broke the player's chest at "
					+ helper.relativePos(spot) + ": " + r.level().getBlockState(spot));
				helper.assertTrue(r.site().skipped() >= 1, "the plank under the chest wasn't given up by the end of the structure: skipped "
					+ r.site().skipped());
				r.level().removeBlock(spot, false); // the player picks the chest back up
				phase[0] = 2;
				LOGGER.info("qa left out: chest taken back at {}, skipped {}", r.site().stage(), r.site().skipped());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(phase[0] == 2, "setup: the build never got past its structure: " + QaBuilderDisruptionGameTests.state(helper, r));
			helper.assertFalse(QaBuilderDisruptionGameTests.building(r), "still building: " + QaBuilderDisruptionGameTests.state(helper, r));
			helper.assertTrue(r.level().getBlockState(spot).isAir(), "the wall plank given up during the structure was gone back for and laid "
				+ "(wiki: blocks already given up are not gone back for): " + r.level().getBlockState(spot) + ", rechecks " + r.site().rechecks());
			helper.assertTrue(r.site().rechecks() == 0, "the build went back " + r.site().rechecks() + " times; nothing it laid was broken");
			helper.assertTrue(r.site().skipped() >= 1, "the given-up plank isn't counted in skipped: " + r.site().skipped());
			LOGGER.info("qa left out: done, plank left, skipped {}, rechecks {}", r.site().skipped(), r.site().rechecks());
		});
	}
}
