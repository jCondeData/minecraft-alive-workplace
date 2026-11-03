package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.legend.GolemSmith;
import io.github.jcondedata.aliveworkplace.store.HaulerGolems;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA (lane-c-1103-0052), verifying B830 from its spec: a Hauler Golem skips the nearest worker's stash, or a Drop Box,
 * whose goods don't fit in the Storehouse, and hauls another worker's goods that do fit, as porters do since B820. The
 * cases the bug's own tests leave out: two such stashes in front of the fitting one, the fitting one below a trip's worth
 * (16 goods, both sides of the line), room made later, and a golem saved and loaded with a load on its back. Every test
 * checks that no item is lost or made.
 */
public class QaHaulerFitVerify1103GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	private static int logs(GameTestHelper helper, java.util.List<IronGolem> golems) {
		int carried = 0;
		for (IronGolem g : golems) {
			carried += QaStackRoom1102cGameTests.carried(g, Items.OAK_LOG);
		}
		return QaStackRoom1102cGameTests.count(helper, QaStackRoom1102cGameTests.STORE_CHEST, Items.OAK_LOG)
			+ QaStackRoom1102cGameTests.count(helper, QaStackRoom1102cGameTests.FAR_JACK_CHEST, Items.OAK_LOG) + carried;
	}

	/**
	 * A Storehouse with room for logs only (one stack of cobblestone, then one stone and one log in turn), a non-empty Drop
	 * Box with 32 cobblestone AND a miner with 64 cobblestone both nearer than a lumberjack with 64 logs: the golem passes
	 * over both and brings all 64 logs in; the cobblestone stays where it was.
	 */
	//$ gametest_ticks_batch HUGE '4800' '"qaHaulFit1103TwoSkipped"'
	@GameTest(template = HUGE, timeoutTicks = 4800, batch = "qaHaulFit1103TwoSkipped")
	public void qaTheHaulerPassesADropBoxAndAMinerThatDontFitForTheLogs(GameTestHelper helper) {
		Container store = QaStackRoom1102cGameTests.storehouse(helper);
		store.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
		for (int i = 1; i < store.getContainerSize(); i++) {
			store.setItem(i, new ItemStack(i % 2 == 0 ? Items.OAK_LOG : Items.STONE, 1));
		}
		int storedBefore = store.countItem(Items.OAK_LOG);
		helper.setBlock(QaStackRoom1102cGameTests.HAUL_BOX, ModBlocks.DROP_BOX);
		((Container) helper.getBlockEntity(QaStackRoom1102cGameTests.HAUL_BOX)).setItem(0, new ItemStack(Items.COBBLESTONE, 32));
		QaStackRoom1102cGameTests.miner(helper, 64);
		QaStackRoom1102cGameTests.lumberjack(helper, QaStackRoom1102cGameTests.FAR_JACK_CHEST, 64);
		IronGolem golem = QaStackRoom1102cGameTests.hauler(helper);
		helper.succeedWhen(() -> {
			int total = logs(helper, java.util.List.of(golem));
			helper.assertTrue(total == storedBefore + 64, "logs lost or made: " + total + " of " + (storedBefore + 64) + "; the golem carries " + HaulerGolems.load(golem));
			int box = QaStackRoom1102cGameTests.count(helper, QaStackRoom1102cGameTests.HAUL_BOX, Items.COBBLESTONE);
			int mine = QaStackRoom1102cGameTests.count(helper, QaStackRoom1102cGameTests.MINER_CHEST, Items.COBBLESTONE);
			int stored = QaStackRoom1102cGameTests.count(helper, QaStackRoom1102cGameTests.STORE_CHEST, Items.COBBLESTONE);
			helper.assertTrue(box == 32 && mine == 64 && stored == 64 && QaStackRoom1102cGameTests.carried(golem, Items.COBBLESTONE) == 0,
				"cobblestone moved though none fits: Drop Box " + box + "/32, miner " + mine + "/64, Storehouse " + stored + "/64");
			int in = QaStackRoom1102cGameTests.count(helper, QaStackRoom1102cGameTests.STORE_CHEST, Items.OAK_LOG);
			helper.assertTrue(in == storedBefore + 64 && HaulerGolems.load(golem).isEmpty(), "the Storehouse has " + in + " of " + (storedBefore + 64)
				+ " logs; the golem carries " + HaulerGolems.load(golem));
		});
	}

	/** The Storehouse of the bug (26 full cobblestone stacks and one log), the miner with cobblestone, a lumberjack with {@code logs}. */
	private static IronGolem busyVillage(GameTestHelper helper, int logs) {
		Container store = QaStackRoom1102cGameTests.storehouse(helper);
		QaStackRoom1102cGameTests.fillEach(store, Items.COBBLESTONE, 64);
		store.setItem(26, new ItemStack(Items.OAK_LOG, 1));
		QaStackRoom1102cGameTests.miner(helper, 64);
		QaStackRoom1102cGameTests.lumberjack(helper, QaStackRoom1102cGameTests.FAR_JACK_CHEST, logs);
		return QaStackRoom1102cGameTests.hauler(helper);
	}

	/**
	 * The farther stash fits but holds only 15 logs, under a trip's worth (16): the golem hauls nothing at all, neither the
	 * miner's cobblestone (no room) nor the 15 logs, and nothing is lost.
	 */
	//$ gametest_ticks_batch HUGE '1300' '"qaHaulFit1103Under"'
	@GameTest(template = HUGE, timeoutTicks = 1300, batch = "qaHaulFit1103Under")
	public void qaFifteenLogsThatFitAreNotATrip(GameTestHelper helper) {
		IronGolem golem = busyVillage(helper, 15);
		helper.runAtTickTime(1200, () -> {
			int jack = QaStackRoom1102cGameTests.count(helper, QaStackRoom1102cGameTests.FAR_JACK_CHEST, Items.OAK_LOG);
			int in = QaStackRoom1102cGameTests.count(helper, QaStackRoom1102cGameTests.STORE_CHEST, Items.OAK_LOG);
			helper.assertTrue(golem.isAlive() && HaulerGolems.load(golem).isEmpty(), "the golem carries " + HaulerGolems.load(golem));
			helper.assertTrue(jack == 15 && in == 1, "15 logs (under 16) were hauled: the lumberjack has " + jack + ", the Storehouse " + in);
			helper.assertTrue(QaStackRoom1102cGameTests.count(helper, QaStackRoom1102cGameTests.MINER_CHEST, Items.COBBLESTONE) == 64
				&& QaStackRoom1102cGameTests.count(helper, QaStackRoom1102cGameTests.STORE_CHEST, Items.COBBLESTONE) == 26 * 64, "cobblestone moved though none fits");
			helper.succeed();
		});
	}

	/** The other side of the line: 16 logs that fit are a trip, and all 16 come in past the miner's cobblestone. */
	//$ gametest_ticks_batch HUGE '4800' '"qaHaulFit1103Sixteen"'
	@GameTest(template = HUGE, timeoutTicks = 4800, batch = "qaHaulFit1103Sixteen")
	public void qaSixteenLogsThatFitAreHauledPastTheMiner(GameTestHelper helper) {
		IronGolem golem = busyVillage(helper, 16);
		helper.succeedWhen(() -> {
			helper.assertTrue(logs(helper, java.util.List.of(golem)) == 17, "logs lost or made: " + logs(helper, java.util.List.of(golem)) + " of 17");
			int in = QaStackRoom1102cGameTests.count(helper, QaStackRoom1102cGameTests.STORE_CHEST, Items.OAK_LOG);
			helper.assertTrue(QaStackRoom1102cGameTests.count(helper, QaStackRoom1102cGameTests.MINER_CHEST, Items.COBBLESTONE) == 64, "the miner's cobblestone moved");
			helper.assertTrue(in == 17 && HaulerGolems.load(golem).isEmpty(), "the Storehouse has " + in + " of 17 logs; the golem carries " + HaulerGolems.load(golem));
		});
	}

	/**
	 * Room made later: a Storehouse full of cobblestone (no room for anything), the miner nearer with 64 cobblestone, the
	 * lumberjack with 64 logs. For 400 ticks nothing moves; then the player empties the Storehouse chest, and the golem
	 * brings the miner's cobblestone and the lumberjack's logs in (none of either lost or made).
	 */
	//$ gametest_ticks_batch HUGE '6000' '"qaHaulFit1103RoomLater"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qaHaulFit1103RoomLater")
	public void qaRoomMadeLaterLetsEveryonesGoodsIn(GameTestHelper helper) {
		Container store = QaStackRoom1102cGameTests.storehouse(helper);
		QaStackRoom1102cGameTests.fillEach(store, Items.COBBLESTONE, 64);
		QaStackRoom1102cGameTests.miner(helper, 64);
		QaStackRoom1102cGameTests.lumberjack(helper, QaStackRoom1102cGameTests.FAR_JACK_CHEST, 64);
		IronGolem golem = QaStackRoom1102cGameTests.hauler(helper);
		helper.runAtTickTime(400, () -> {
			helper.assertTrue(HaulerGolems.load(golem).isEmpty(), "the golem carries " + HaulerGolems.load(golem) + " before there is room");
			helper.assertTrue(QaStackRoom1102cGameTests.count(helper, QaStackRoom1102cGameTests.MINER_CHEST, Items.COBBLESTONE) == 64
				&& QaStackRoom1102cGameTests.count(helper, QaStackRoom1102cGameTests.FAR_JACK_CHEST, Items.OAK_LOG) == 64, "goods moved before there was room");
			((Container) helper.getBlockEntity(QaStackRoom1102cGameTests.STORE_CHEST)).clearContent();
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 400, "the Storehouse isn't emptied yet");
			int cobble = QaStackRoom1102cGameTests.count(helper, QaStackRoom1102cGameTests.STORE_CHEST, Items.COBBLESTONE)
				+ QaStackRoom1102cGameTests.count(helper, QaStackRoom1102cGameTests.MINER_CHEST, Items.COBBLESTONE) + QaStackRoom1102cGameTests.carried(golem, Items.COBBLESTONE);
			helper.assertTrue(cobble == 64, "cobblestone lost or made: " + cobble + " of 64");
			helper.assertTrue(logs(helper, java.util.List.of(golem)) == 64, "logs lost or made: " + logs(helper, java.util.List.of(golem)) + " of 64");
			int in = QaStackRoom1102cGameTests.count(helper, QaStackRoom1102cGameTests.STORE_CHEST, Items.OAK_LOG);
			int mined = QaStackRoom1102cGameTests.count(helper, QaStackRoom1102cGameTests.STORE_CHEST, Items.COBBLESTONE);
			helper.assertTrue(in == 64 && mined >= 16 && HaulerGolems.load(golem).isEmpty(), "with room again the Storehouse has " + in + " of 64 logs and "
				+ mined + " of the miner's cobblestone; the golem carries " + HaulerGolems.load(golem));
		});
	}

	/**
	 * Save and reload mid-trip: once the golem has the logs on its back (past the miner's cobblestone, which doesn't fit),
	 * it is saved and loaded again as a new entity (as when its chunk unloads). The loaded golem keeps the load and brings
	 * it in: the Storehouse fills its room for 63 logs, the lumberjack keeps the one over, none lost or doubled.
	 */
	//$ gametest_ticks_batch HUGE '4800' '"qaHaulFit1103Reload"'
	@GameTest(template = HUGE, timeoutTicks = 4800, batch = "qaHaulFit1103Reload")
	public void qaAGolemSavedWithItsLoadMidTripBringsItIn(GameTestHelper helper) {
		IronGolem first = busyVillage(helper, 64);
		AtomicReference<IronGolem> golem = new AtomicReference<>(first);
		AtomicReference<java.util.Map<net.minecraft.world.item.Item, Integer>> before = new AtomicReference<>();
		helper.succeedWhen(() -> {
			IronGolem now = golem.get();
			helper.assertTrue(logs(helper, java.util.List.of(now)) == 65, "logs lost or made: " + logs(helper, java.util.List.of(now)) + " of 65; the golem carries " + HaulerGolems.load(now));
			if (before.get() == null) {
				helper.assertTrue(!HaulerGolems.load(now).isEmpty(), "the golem has not picked the logs up yet");
				before.set(java.util.Map.copyOf(HaulerGolems.load(now)));
				CompoundTag saved = now.saveWithoutId(new CompoundTag());
				saved.remove("UUID");
				now.discard();
				IronGolem loaded = EntityType.IRON_GOLEM.create(helper.getLevel());
				loaded.load(saved);
				helper.getLevel().addFreshEntity(loaded);
				Leftovers.after(helper, loaded::discard);
				golem.set(loaded);
				helper.assertTrue(GolemSmith.role(loaded) == GolemSmith.Role.HAULER && HaulerGolems.load(loaded).equals(before.get()),
					"after loading: role " + GolemSmith.role(loaded) + ", load " + HaulerGolems.load(loaded) + " (was " + before.get() + ")");
				helper.assertTrue(false, "reloaded mid-trip with " + before.get());
			}
			int in = QaStackRoom1102cGameTests.count(helper, QaStackRoom1102cGameTests.STORE_CHEST, Items.OAK_LOG);
			int jack = QaStackRoom1102cGameTests.count(helper, QaStackRoom1102cGameTests.FAR_JACK_CHEST, Items.OAK_LOG);
			helper.assertTrue(in == 64 && jack == 1 && HaulerGolems.load(now).isEmpty(), "after the reload the Storehouse (room for 63) has " + in
				+ " of 64 logs, the lumberjack keeps " + jack + " of 1; the golem carries " + HaulerGolems.load(now));
			helper.assertTrue(QaStackRoom1102cGameTests.count(helper, QaStackRoom1102cGameTests.MINER_CHEST, Items.COBBLESTONE) == 64, "the miner's cobblestone moved");
		});
	}
}
