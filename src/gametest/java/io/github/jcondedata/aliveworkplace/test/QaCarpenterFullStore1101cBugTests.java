package io.github.jcondedata.aliveworkplace.test;

import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA (2026-11-01), found by {@link QaCarpenterHomePlanks1101GameTests} while verifying B773.
 * <p>
 * After B773: when the chests by the Carpenter's own bench have room, the Storehouse staying full past the 2400-tick wait
 * still sends a chestless builder's planks home into them. The Carpenter isn't a worker whose chests builders take from
 * ({@code Village.takesPart}), so the builder never looks there: he waits for good, even after the Storehouse has room
 * again (no logs are left to make more). Expected (B721/B760): what the Carpenter makes reaches the builder who asked.
 */
public class QaCarpenterFullStore1101cBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";

	/**
	 * Room by his bench, Storehouse full past the wait: the planks go home. A Storehouse slot then frees; within 1500
	 * ticks the chestless builder should have his row (no plank on the ground, the wood adding up, at every tick).
	 */
	//$ gametest_ticks_batch BIG '7000' '"qaFull1101cHomeStranded"'
	@GameTest(template = BIG, timeoutTicks = 7000, batch = "qaFull1101cHomeStranded")
	public void planksTakenHomeStillReachTheBuilderWhoAsked(GameTestHelper helper) {
		QaCarpenterHomePlanks1101GameTests.Run run = QaCarpenterHomePlanks1101GameTests.setUp(helper, "stranded", 1,
			QaCarpenterStorehouse1101GameTests.ROW_A, 3, 1, QaCarpenterHomePlanks1101GameTests.Home.ROOM);
		Container store = helper.getBlockEntity(QaCarpenterStorehouse1101GameTests.STORE_CHEST);
		AtomicInteger filledAt = new AtomicInteger(-1);
		AtomicInteger homeAt = new AtomicInteger(-1);
		AtomicInteger freedAt = new AtomicInteger(-1);
		helper.onEachTick(() -> {
			int tick = (int) run.tick();
			QaCarpenterHomePlanks1101GameTests.everyTick(helper, run, 4);
			if (filledAt.get() < 0 && QaCarpenterHomePlanks1101GameTests.carrying(run.carpenter()) > 0) {
				QaVillageEconomySaves1101GameTests.fillEmpty(store);
				filledAt.set(tick);
			}
			if (homeAt.get() < 0 && QaCarpenterStorehouse1101GameTests.inChest(helper, QaCarpenterStorehouse1101GameTests.CARPENTER_CHEST, Items.SPRUCE_PLANKS) > 0) {
				homeAt.set(tick);
			}
			if (homeAt.get() >= 0 && freedAt.get() < 0 && tick == homeAt.get() + 200) {
				store.setItem(store.getContainerSize() - 1, ItemStack.EMPTY);
				freedAt.set(tick);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(freedAt.get() >= 0 && run.tick() >= freedAt.get() + 1500, "still watching");
			helper.assertTrue(QaCarpenterStorehouse1101GameTests.done(helper, run.site()), "at tick " + run.tick() + " the chestless builder still waits for "
				+ run.site().missing() + " (" + run.site().status() + "): the Carpenter took his planks home " + (homeAt.get() - filledAt.get())
				+ " ticks after the Storehouse filled, and 1500 ticks after a Storehouse slot freed they are still in the Carpenter's chest ("
				+ QaCarpenterStorehouse1101GameTests.inChest(helper, QaCarpenterStorehouse1101GameTests.CARPENTER_CHEST, Items.SPRUCE_PLANKS)
				+ "), where the builder never looks; Storehouse " + store.countItem(Items.SPRUCE_PLANKS));
		});
	}
}
