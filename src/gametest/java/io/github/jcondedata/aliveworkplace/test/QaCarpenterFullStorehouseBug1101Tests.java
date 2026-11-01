package io.github.jcondedata.aliveworkplace.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Bug test (lane-b-1101-0638, after B721): a chestless builder waits for 3 spruce planks; the village Storehouse is full
 * (64 spruce logs and 26 stacks of cobblestone, so the log slot stays taken after one is crafted). The Carpenter crafts
 * a log into planks, finds no room in the Storehouse and drops them on the ground by it; the builder never picks them up,
 * still waits, and the Carpenter crafts again: by tick 3500, 17 logs had become 68 planks lying on the ground. Expected
 * (B721's spec): what the Carpenter makes reaches the builder who asked, or he doesn't craft for a builder he can't
 * deliver to; and never more than the one log the 3 planks need.
 */
public class QaCarpenterFullStorehouseBug1101Tests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";

	//$ gametest_ticks_batch BIG '4000' '"qaStore1101FullBug"'
	@GameTest(template = BIG, timeoutTicks = 4000, batch = "qaStore1101FullBug")
	public void aFullStorehouseNeverLeavesThePlanksOnTheGround(GameTestHelper helper) {
		QaCarpenterStorehouse1101GameTests.Run run = QaCarpenterStorehouse1101GameTests.setUp(helper, "fullbug", 64, false, false, false);
		Container store = helper.getBlockEntity(QaCarpenterStorehouse1101GameTests.STORE_CHEST);
		for (int i = 1; i < store.getContainerSize(); i++) {
			store.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		}
		helper.onEachTick(() -> {
			QaCarpenterStorehouse1101GameTests.conserve(helper, run, 4 * 64);
			int logs = QaCarpenterStorehouse1101GameTests.logsAnywhere(helper);
			int lying = QaCarpenterStorehouse1101GameTests.onGround(helper, Items.SPRUCE_PLANKS);
			boolean waiting = !QaCarpenterStorehouse1101GameTests.done(helper, run.a());
			if (waiting && (logs < 63 || (lying > 0 && run.tick() > 600))) {
				throw new GameTestAssertException("at tick " + run.tick() + " " + lying + " spruce planks lie on the ground by the full Storehouse and "
					+ (64 - logs) + " of its 64 logs are used up, while the chestless builder still waits for " + run.a().missing());
			}
		});
		helper.succeedWhen(() -> {
			boolean built = QaCarpenterStorehouse1101GameTests.done(helper, run.a());
			boolean neverMade = run.tick() >= 3000 && QaCarpenterStorehouse1101GameTests.planksAnywhere(helper) == 0
				&& QaCarpenterStorehouse1101GameTests.logsAnywhere(helper) == 64;
			helper.assertTrue(built || neverMade, "A neither built nor left alone: " + run.a().status() + " missing " + run.a().missing());
		});
	}
}
