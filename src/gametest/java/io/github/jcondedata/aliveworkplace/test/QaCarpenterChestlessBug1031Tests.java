package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Bug test (qa-lane-b-1031-1238): a builder whose Blueprint Table has no chest of its own, in a village whose Storehouse
 * holds a spare spruce log, waits for spruce planks. The Carpenter takes the log from the Storehouse and crafts it into
 * 4 planks, finds no chest by the builder to bring them to, and drops them on the ground by his own bench, where they
 * stay: the builder waits for ever and the log is gone. Expected: what the Carpenter makes reaches the builder who asked
 * (the Storehouse he fetches from, or the bench), or the Carpenter doesn't craft for a builder he can't deliver to.
 */
public class QaCarpenterChestlessBug1031Tests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";

	//$ gametest_ticks_batch BIG '4000' '"qaChestless1031"'
	@GameTest(template = BIG, timeoutTicks = 4000, batch = "qaChestless1031")
	public void aCarpentersPlanksForABuilderWithNoChestReachHim(GameTestHelper helper) {
		QaCarpenterReserve1031GameTests.Run run = QaCarpenterReserve1031GameTests.setUp(helper, 4, "chestless", false);
		helper.onEachTick(() -> {
			int lying = QaCarpenterReserve1031GameTests.onGround(helper, Items.SPRUCE_PLANKS);
			if (lying > 0 && BuildSiteManager.get(helper.getLevel()).get(run.a().id()) != null && run.tick() > 600) {
				helper.fail("at tick " + run.tick() + " the Carpenter's " + lying + " spruce planks lie on the ground by his bench while builder A (no chest of its own)"
					+ " still waits for " + run.a().missing() + "; the Storehouse has " + QaCarpenterReserve1031GameTests.inChest(helper, Items.SPRUCE_LOG) + " logs left");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(run.cobble()[0], "the cobblestone isn't there yet");
			helper.assertTrue(BuildSiteManager.get(helper.getLevel()).get(run.a().id()) == null, "A's planks row isn't finished: " + run.a().status()
				+ " missing " + run.a().missing());
			helper.assertTrue(QaCarpenterReserve1031GameTests.placed(helper, QaCarpenterReserve1031GameTests.ROW_A, Blocks.SPRUCE_PLANKS) == 3, "A's row isn't built");
		});
	}
}
