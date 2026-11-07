package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA (lane-b-1101-1238), from B760's spec: with the village Storehouse full, the Carpenter must not leave crafted planks
 * on the ground; what he makes reaches the chestless builder who asked, or he doesn't craft, and only the one log the
 * 3 planks need is used. Beyond the fix's tests: a Storehouse with exactly one free slot (room for the planks), one slot
 * freed while he waits, and a save and reload of the Carpenter while he holds the planks at a Storehouse that filled up
 * on his way, and while he waits at a full one. Uses {@link QaCarpenterStorehouse1101GameTests}'s scene (a 3-plank row,
 * the Storehouse chest at STORE_CHEST, slot 0 the logs). Each test has its own batch.
 */
public class QaCarpenterFullStore1101bGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";

	/** Cobblestone in every slot from 1 up, except {@code leaveFree} of the last ones. */
	private static void fill(Container store, int leaveFree) {
		for (int i = 1; i < store.getContainerSize() - leaveFree; i++) {
			store.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		}
	}

	private static void noPlanksOnTheGround(GameTestHelper helper, QaCarpenterStorehouse1101GameTests.Run run) {
		int lying = QaCarpenterStorehouse1101GameTests.onGround(helper, Items.SPRUCE_PLANKS);
		if (lying > 0) {
			throw new GameTestAssertException("at tick " + run.tick() + " " + lying + " spruce planks lie on the ground by the Storehouse, where the builder never looks");
		}
	}

	private static void atMostOneLog(GameTestHelper helper, QaCarpenterStorehouse1101GameTests.Run run, int stocked) {
		int used = stocked - QaCarpenterStorehouse1101GameTests.logsAnywhere(helper);
		if (used > 1) {
			throw new GameTestAssertException("at tick " + run.tick() + " " + used + " spruce logs were turned into planks for a row of 3 planks; one is enough");
		}
	}

	private static void rowBuilt(GameTestHelper helper, QaCarpenterStorehouse1101GameTests.Run run) {
		helper.assertTrue(QaCarpenterStorehouse1101GameTests.done(helper, run.a()), "the chestless builder's row isn't finished: " + run.a().status()
			+ ", still waiting for " + run.a().missing());
		helper.assertTrue(QaCarpenterStorehouse1101GameTests.placed(helper, QaCarpenterStorehouse1101GameTests.ROW_A) == 3,
			"the builder's row has " + QaCarpenterStorehouse1101GameTests.placed(helper, QaCarpenterStorehouse1101GameTests.ROW_A) + " of its 3 planks");
	}

	/**
	 * The Storehouse has exactly one free slot (room for the 3 planks): the Carpenter crafts one log, the planks go in that
	 * slot and the builder builds his row; nothing on the ground.
	 */
	//$ gametest_ticks_batch BIG '4000' '"qaFullStore1101bOneSlot"'
	@GameTest(template = BIG, timeoutTicks = 4000, batch = "qaFullStore1101bOneSlot")
	public void aStorehouseWithOneFreeSlotGetsThePlanks(GameTestHelper helper) {
		QaCarpenterStorehouse1101GameTests.Run run = QaCarpenterStorehouse1101GameTests.setUp(helper, "q1101b_one", 64, false, false, false);
		Container store = helper.getBlockEntity(QaCarpenterStorehouse1101GameTests.STORE_CHEST);
		fill(store, 1);
		helper.onEachTick(() -> {
			QaCarpenterStorehouse1101GameTests.conserve(helper, run, 4 * 64);
			noPlanksOnTheGround(helper, run);
			atMostOneLog(helper, run, 64);
		});
		helper.succeedWhen(() -> rowBuilt(helper, run));
	}

	/**
	 * The Storehouse is full from the start; at tick 600 exactly one slot is emptied. Before that no log is used; after,
	 * one log becomes the 3 planks, the row is built, nothing lies on the ground.
	 */
	//$ gametest_ticks_batch BIG '4000' '"qaFullStore1101bOneFreed"'
	@GameTest(template = BIG, timeoutTicks = 4000, batch = "qaFullStore1101bOneFreed")
	public void oneSlotFreedWhileHeWaitsIsEnough(GameTestHelper helper) {
		QaCarpenterStorehouse1101GameTests.Run run = QaCarpenterStorehouse1101GameTests.setUp(helper, "q1101b_freed", 64, false, false, false);
		Container store = helper.getBlockEntity(QaCarpenterStorehouse1101GameTests.STORE_CHEST);
		fill(store, 0);
		helper.onEachTick(() -> {
			QaCarpenterStorehouse1101GameTests.conserve(helper, run, 4 * 64);
			noPlanksOnTheGround(helper, run);
			atMostOneLog(helper, run, 64);
			if (run.tick() < 600 && QaCarpenterStorehouse1101GameTests.logsAnywhere(helper) < 64) {
				throw new GameTestAssertException("at tick " + run.tick() + " a log was made into planks while the Storehouse had no room for them");
			}
			if (run.tick() == 600) {
				store.setItem(store.getContainerSize() - 1, ItemStack.EMPTY);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(run.tick() > 600, "the row was built before the Storehouse had room");
			rowBuilt(helper, run);
		});
	}

	/**
	 * The Storehouse fills up while the Carpenter carries the planks to it; 100 ticks later the game is saved and loaded
	 * (the Carpenter reloaded); 400 ticks after the fill room comes back. He still has the planks (none on the ground, none
	 * made twice), puts them in, the row is built and no job stays saved.
	 */
	//$ gametest_ticks_batch BIG '4000' '"qaFullStore1101bReloadHeld"'
	@GameTest(template = BIG, timeoutTicks = 4000, batch = "qaFullStore1101bReloadHeld")
	public void reloadedWhileHoldingThePlanksAtAFullStorehouse(GameTestHelper helper) {
		QaCarpenterStorehouse1101GameTests.Run run = QaCarpenterStorehouse1101GameTests.setUp(helper, "q1101b_reload", 1, false, false, false);
		Container store = helper.getBlockEntity(QaCarpenterStorehouse1101GameTests.STORE_CHEST);
		AtomicReference<Villager> carpenter = new AtomicReference<>(run.carpenter());
		AtomicInteger filledAt = new AtomicInteger(-1);
		AtomicBoolean reloaded = new AtomicBoolean();
		helper.onEachTick(() -> {
			QaCarpenterStorehouse1101GameTests.conserve(helper, run, 4);
			noPlanksOnTheGround(helper, run);
			if (filledAt.get() < 0 && QaCarpenterStorehouse1101GameTests.inBags(helper, Items.SPRUCE_PLANKS) > 0) {
				for (int i = 0; i < store.getContainerSize(); i++) {
					if (store.getItem(i).isEmpty()) {
						store.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
					}
				}
				filledAt.set((int) run.tick());
			}
			if (filledAt.get() >= 0 && !reloaded.get() && run.tick() == filledAt.get() + 100) {
				carpenter.set(QaCarpenterStorehouse1101GameTests.reload(helper.getLevel(), carpenter.get()));
				reloaded.set(true);
			}
			if (filledAt.get() >= 0 && run.tick() == filledAt.get() + 400) {
				for (int i = 0; i < store.getContainerSize(); i++) {
					if (store.getItem(i).is(Items.COBBLESTONE)) {
						store.setItem(i, ItemStack.EMPTY);
					}
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(filledAt.get() >= 0, "the Carpenter never set off with the planks");
			helper.assertTrue(reloaded.get() && run.tick() > filledAt.get() + 400, "the Storehouse is still full");
			rowBuilt(helper, run);
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(carpenter.get()) == null, "the delivered job is still saved on the Carpenter");
		});
	}

	/**
	 * The Storehouse is full from the start and the Carpenter is saved and loaded at tick 300, while he holds off: after the
	 * reload he still doesn't craft until room comes back at tick 800, then the row is built from one log.
	 */
	//$ gametest_ticks_batch BIG '4000' '"qaFullStore1101bReloadWaiting"'
	@GameTest(template = BIG, timeoutTicks = 4000, batch = "qaFullStore1101bReloadWaiting")
	public void reloadedWhileWaitingAtAFullStorehouseStillWaits(GameTestHelper helper) {
		QaCarpenterStorehouse1101GameTests.Run run = QaCarpenterStorehouse1101GameTests.setUp(helper, "q1101b_rwait", 64, false, false, false);
		Container store = helper.getBlockEntity(QaCarpenterStorehouse1101GameTests.STORE_CHEST);
		fill(store, 0);
		AtomicReference<Villager> carpenter = new AtomicReference<>(run.carpenter());
		helper.onEachTick(() -> {
			QaCarpenterStorehouse1101GameTests.conserve(helper, run, 4 * 64);
			noPlanksOnTheGround(helper, run);
			atMostOneLog(helper, run, 64);
			if (run.tick() < 800 && QaCarpenterStorehouse1101GameTests.logsAnywhere(helper) < 64) {
				throw new GameTestAssertException("at tick " + run.tick() + " a log was made into planks while the Storehouse was full"
					+ (run.tick() > 300 ? " (after a save and reload)" : ""));
			}
			if (run.tick() == 300) {
				carpenter.set(QaCarpenterStorehouse1101GameTests.reload(helper.getLevel(), carpenter.get()));
			}
			if (run.tick() == 800) {
				for (int i = 1; i < store.getContainerSize(); i++) {
					store.setItem(i, ItemStack.EMPTY);
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(run.tick() > 800, "the row was built before the Storehouse had room");
			rowBuilt(helper, run);
		});
	}
}
