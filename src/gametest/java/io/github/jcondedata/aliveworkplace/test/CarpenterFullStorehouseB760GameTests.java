package io.github.jcondedata.aliveworkplace.test;

import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * B760: a chestless builder's only store is the village Storehouse. When it's full, the Carpenter must not leave planks
 * on the ground by it or keep turning logs into planks; once room comes back, the planks reach the builder. Uses
 * {@link QaCarpenterStorehouse1101GameTests}'s scene (a 3-plank row, the Storehouse chest at STORE_CHEST).
 */
public class CarpenterFullStorehouseB760GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";

	/**
	 * The Storehouse is full from the start and room is made at tick 800: he waits instead of crafting, then makes the
	 * planks from one log and the row is built; no plank ever lies on the ground.
	 */
	//$ gametest_ticks_batch BIG '4000' '"b760FreedLater"'
	@GameTest(template = BIG, timeoutTicks = 4000, batch = "b760FreedLater")
	public void aFullStorehouseThatFreesUpGetsThePlanksThen(GameTestHelper helper) {
		QaCarpenterStorehouse1101GameTests.Run run = QaCarpenterStorehouse1101GameTests.setUp(helper, "b760free", 64, false, false, false);
		Container store = helper.getBlockEntity(QaCarpenterStorehouse1101GameTests.STORE_CHEST);
		fill(store);
		helper.onEachTick(() -> {
			QaCarpenterStorehouse1101GameTests.conserve(helper, run, 4 * 64);
			int lying = QaCarpenterStorehouse1101GameTests.onGround(helper, Items.SPRUCE_PLANKS);
			if (lying > 0) {
				throw new GameTestAssertException("at tick " + run.tick() + " " + lying + " spruce planks lie on the ground");
			}
			if (run.tick() < 800 && QaCarpenterStorehouse1101GameTests.logsAnywhere(helper) < 64) {
				throw new GameTestAssertException("at tick " + run.tick() + " a log was used while the Storehouse was full");
			}
			if (run.tick() == 800) {
				for (int i = 1; i < store.getContainerSize(); i++) {
					store.setItem(i, ItemStack.EMPTY);
				}
			}
			if (QaCarpenterStorehouse1101GameTests.logsAnywhere(helper) < 63) {
				throw new GameTestAssertException("at tick " + run.tick() + " more than the one log 3 planks need was used: "
					+ (64 - QaCarpenterStorehouse1101GameTests.logsAnywhere(helper)));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(QaCarpenterStorehouse1101GameTests.done(helper, run.a()), "A's row isn't finished: " + run.a().status()
				+ " missing " + run.a().missing());
			helper.assertTrue(QaCarpenterStorehouse1101GameTests.placed(helper, QaCarpenterStorehouse1101GameTests.ROW_A) == 3, "A's row isn't 3 planks");
		});
	}

	/**
	 * The Storehouse fills up while the Carpenter carries the planks to it, and room comes back 400 ticks later: he holds
	 * on to them (none on the ground, none made twice) and puts them in, and the row is built.
	 */
	//$ gametest_ticks_batch BIG '4000' '"b760FilledOnTheWay"'
	@GameTest(template = BIG, timeoutTicks = 4000, batch = "b760FilledOnTheWay")
	public void aStorehouseFilledOnTheWayIsWaitedFor(GameTestHelper helper) {
		QaCarpenterStorehouse1101GameTests.Run run = QaCarpenterStorehouse1101GameTests.setUp(helper, "b760way", 1, false, false, false);
		Container store = helper.getBlockEntity(QaCarpenterStorehouse1101GameTests.STORE_CHEST);
		AtomicInteger filledAt = new AtomicInteger(-1);
		helper.onEachTick(() -> {
			QaCarpenterStorehouse1101GameTests.conserve(helper, run, 4);
			int lying = QaCarpenterStorehouse1101GameTests.onGround(helper, Items.SPRUCE_PLANKS);
			if (lying > 0) {
				throw new GameTestAssertException("at tick " + run.tick() + " " + lying + " spruce planks lie on the ground");
			}
			if (filledAt.get() < 0 && QaCarpenterStorehouse1101GameTests.inBags(helper, Items.SPRUCE_PLANKS) > 0) {
				fillAll(store);
				filledAt.set((int) run.tick());
			}
			if (filledAt.get() >= 0 && run.tick() == filledAt.get() + 400) {
				for (int i = 0; i < store.getContainerSize(); i++) {
					store.setItem(i, ItemStack.EMPTY);
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(filledAt.get() >= 0, "the Carpenter never set off with the planks");
			helper.assertTrue(run.tick() > filledAt.get() + 400, "the Storehouse is still full");
			helper.assertTrue(QaCarpenterStorehouse1101GameTests.done(helper, run.a()), "A's row isn't finished: " + run.a().status()
				+ " missing " + run.a().missing());
		});
	}

	/** Cobblestone in every slot but the first (the logs'). */
	private static void fill(Container store) {
		for (int i = 1; i < store.getContainerSize(); i++) {
			store.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		}
	}

	/** Cobblestone in every empty slot. */
	private static void fillAll(Container store) {
		for (int i = 0; i < store.getContainerSize(); i++) {
			if (store.getItem(i).isEmpty()) {
				store.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
			}
		}
	}
}
