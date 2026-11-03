package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.RepairAsks;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-b-1103-1238) bug tests for 36.15, failing on main: not registered in fabric.mod.json until the fix lands.
 * Uses {@link QaRepairAsks1103bGameTests}' stage.
 */
public class QaRepairAsks1103bBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = QaRepairAsks1103bGameTests.AREA;
	private static final net.minecraft.core.BlockPos WALL = QaRepairAsks1103bGameTests.WALL;

	/**
	 * The hole mended by hand after the question, then the old chat [Yes] clicked: nothing is missing, so the reply is the
	 * mod's own "nothing is missing from it any more" line rather than a promise of a repair, nothing starts, and the wall
	 * isn't on the Repairs page (builder.md: "put right by hand meanwhile, it comes off the list").
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_ask_b_mended"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_ask_b_mended")
	public void aYesAfterTheHoleWasMendedByHandPromisesNoRepair(GameTestHelper helper) {
		QaRepairAsks1103bGameTests.Stage s = QaRepairAsks1103bGameTests.stage(helper, 1);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			QaRepairAsks1103bGameTests.look(helper, s.builder());
			helper.assertTrue(QaRepairAsks1103bGameTests.ask(helper, s) != null && QaRepairAsks1103bGameTests.ask(helper, s).state() == RepairAsks.State.ASKED, "not asked: " + QaRepairAsks1103bGameTests.ask(helper, s));
			helper.setBlock(WALL.offset(1, 0, 0), Blocks.OAK_PLANKS); // the player mends it
			QaRepairAsks1103bGameTests.click(helper, s.owner(), "yes", s.at());
			String reply = QaRepairAsks1103bGameTests.last(s.seen());
			helper.assertTrue(RepairAsks.repairOf(level, s.placement()) == null && !ModAttachments.BUILDER_JOB.has(s.builder()), "a repair started with nothing missing");
			helper.assertFalse(QaRepairAsks1103bGameTests.onPage(helper, s), "the mended wall is on the Repairs page: " + QaRepairAsks1103bGameTests.ask(helper, s));
			helper.assertTrue(reply.equals("That build is gone, or nothing is missing from it any more."),
				"with nothing missing, the [Yes] was answered: " + reply);
			helper.succeed();
		});
	}
}
