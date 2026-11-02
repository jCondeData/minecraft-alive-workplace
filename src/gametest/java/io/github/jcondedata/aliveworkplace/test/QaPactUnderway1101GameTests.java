package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.CaravanSights;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.realm.Pacts;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.test.QaPactsRealmEdictsGameTests.Two;
import io.github.jcondedata.aliveworkplace.test.RealmGameTests.Land;
import io.github.jcondedata.aliveworkplace.trade.TradeGoods;
import java.lang.reflect.Method;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-e-1101-1913), B768 from its spec: "a caravan that set out under a pact is still paid at the pact's terms when
 * it arrives" and the mark is "saved as 'pact', false in older saves". The fix's own tests unload a caravan whole; here
 * the caravan finds the Storehouse nearly full, so part of it waits on the road for room: that part set out under the
 * pact too, so it must keep its mark through a restart and be paid when it finally unloads. And a save written before
 * the fix (no 'pact' key) loads with every caravan unmarked, the rest of the road intact.
 */
public class QaPactUnderway1101GameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/**
	 * A pact caravan of two bundles; the receiver ends the pact while it's on the road; at the Storehouse there's room for
	 * one bundle. The bundle that fits is paid; the other waits on the road, still marked, and keeps the mark through a
	 * save and load; once room is made it unloads and is paid too. Nothing of the cargo is handed over free.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qa1101PactUnderwayRoom"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qa1101PactUnderwayRoom")
	public void aPactCaravanThatWaitsForRoomIsPaidForTheRestAfterARestart(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		ServerLevel level = land.level();
		Caravans.Data data = Caravans.Data.get(level);
		boolean sights = CaravanSights.ENABLED;
		CaravanSights.ENABLED = false;
		BlockPos store = new BlockPos(12, 2, 7);
		BlockPos chestAt = new BlockPos(12, 2, 9);
		Leftovers.after(helper, () -> {
			CaravanSights.ENABLED = sights;
			data.clearRoad(land.hall(0));
			data.clearRoad(land.hall(1));
			if (helper.getLevel().getBlockEntity(helper.absolutePos(chestAt)) instanceof Container c) {
				c.clearContent();
			}
			helper.setBlock(chestAt, Blocks.AIR);
			helper.setBlock(store, Blocks.AIR);
		});
		// an earlier batch's Storehouse or chest left standing outside its own area (never cleared) is one of village 1's
		// stores too: the caravan unloaded into it, not this test's chest ("room for one bundle (20), but 0 were unloaded", B804)
		Leftovers.storehouses(helper);
		Leftovers.stores(helper, helper.absolutePos(store));
		helper.setBlock(store, ModBlocks.STOREHOUSE);
		helper.setBlock(chestAt, Blocks.CHEST);
		TradeGoods.Good good = TradeGoods.all().get(0);
		int bundle = good.bundle();
		int max = new ItemStack(good.icon()).getMaxStackSize();
		helper.assertTrue(bundle >= 1 && bundle <= max, "setup: the first good's bundle " + bundle + " doesn't fit one stack of " + max);
		data.setWants(land.hall(1), Component.literal(land.name(1)), List.of(new Caravans.Want(good.icon(), bundle * 2)));
		QaPactsRealmEdictsGameTests.pact(land, false);
		land.entity(1).setTreasury(100_000);
		helper.runAfterDelay(5, () -> { // the Storehouse's point of interest is registered a tick after it's set
			Container chest = helper.getBlockEntity(chestAt);
			// room for exactly one bundle: a part stack of the good, every other slot full of stone
			for (int i = 0; i < chest.getContainerSize(); i++) {
				chest.setItem(i, i == 0 ? (max - bundle > 0 ? new ItemStack(good.icon(), max - bundle) : ItemStack.EMPTY) : new ItemStack(Items.STONE, 64));
			}
			int before = chest.countItem(good.icon());
			ship(data, new Caravans.Shipment(land.hall(0), land.hall(1),
				List.of(new ItemStack(good.icon(), bundle), new ItemStack(good.icon(), bundle)), level.getGameTime() + 20));
			helper.assertTrue(Pacts.end(level, land.hall(1), land.at(0), two.them()).ok(), "couldn't end the pact");
			helper.assertTrue(Boolean.TRUE.equals(mark(data, land)), "the caravan on the road when the pact ended wasn't marked: " + mark(data, land));
			helper.runAfterDelay(30, () -> {
				int t0 = land.entity(1).treasury();
				Caravans.round(level, land.hall(1), null);
				int first = t0 - land.entity(1).treasury();
				int in = chest.countItem(good.icon()) - before;
				helper.assertTrue(in == bundle, "setup: room for one bundle (" + bundle + "), but " + in + " were unloaded");
				helper.assertTrue(first > 0, "the bundle that fit was unloaded and paid " + first);
				helper.assertTrue(Boolean.TRUE.equals(mark(data, land)),
					"the bundle left waiting for room lost the pact's mark (it set out under the pact): " + mark(data, land) + ", " + road(data, land));
				helper.assertTrue(goods(data, land) == bundle, "on the road for room: " + goods(data, land) + ", not " + bundle);
				// a restart while it waits
				Caravans.Data loaded = Caravans.Data.load(data.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
				helper.assertTrue(Boolean.TRUE.equals(mark(loaded, land)) && goods(loaded, land) == bundle,
					"after a save and load the waiting bundle reads mark " + mark(loaded, land) + " with " + goods(loaded, land) + " items");
				// room is made; the rest comes in
				chest.setItem(1, ItemStack.EMPTY);
				data.hurry(land.hall(1));
				int t1 = land.entity(1).treasury();
				Caravans.round(level, land.hall(1), null);
				int second = t1 - land.entity(1).treasury();
				int all = chest.countItem(good.icon()) - before;
				helper.assertTrue(all == bundle * 2, "with room made, " + all + " of " + (bundle * 2) + " are in");
				helper.assertTrue(second > 0,
					"the waiting bundle that set out under the ended pact was unloaded free: paid " + second + " (the first bundle paid " + first + ")");
				helper.assertTrue(road(data, land).isEmpty(), "still on the road: " + road(data, land));
				helper.succeed();
			});
		});
	}

	/**
	 * A save from before B768 has no 'pact' key: the caravans on the road load unmarked (so a pact that ended before the
	 * update pays nothing new) and keep their goods, arrival and kind; a marked caravan's key is the only thing read.
	 */
	//$ gametest_ticks_batch HUGE '60' '"qa1101PactOldSave"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qa1101PactOldSave")
	public void aSaveFromBeforeTheMarkLoadsEveryCaravanUnmarkedAndWhole(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		ServerLevel level = land.level();
		Caravans.Data data = Caravans.Data.get(level);
		Leftovers.after(helper, () -> {
			data.clearRoad(land.hall(0));
			data.clearRoad(land.hall(1));
		});
		TradeGoods.Good good = TradeGoods.all().get(0);
		long arrives = level.getGameTime() + 1000;
		ship(data, new Caravans.Shipment(land.hall(0), land.hall(1), List.of(new ItemStack(good.icon(), good.bundle())), arrives));
		data.pactEnded(land.hall(0), land.hall(1));
		helper.assertTrue(Boolean.TRUE.equals(mark(data, land)), "setup: the caravan isn't marked");
		CompoundTag tag = data.save(new CompoundTag(), level.registryAccess());
		ListTag road = Nbt.getList(tag, "road", Tag.TAG_COMPOUND);
		int stripped = 0;
		for (int i = 0; i < road.size(); i++) {
			if (road.getCompound(i).contains("pact")) {
				road.getCompound(i).remove("pact");
				stripped++;
			}
		}
		helper.assertTrue(stripped >= 1, "setup: the save holds no 'pact' key to take out (saved as " + road + ")");
		Caravans.Data old = Caravans.Data.load(tag, level.registryAccess());
		helper.assertTrue(Boolean.FALSE.equals(mark(old, land)), "a caravan from an older save loaded marked: " + mark(old, land));
		List<Caravans.Shipment> back = road(old, land);
		helper.assertTrue(back.size() == 1 && back.get(0).arrives() == arrives && !back.get(0).back() && !back.get(0).byTrain()
				&& goods(old, land) == good.bundle(),
			"the older save's caravan didn't load whole: " + back);
		helper.succeed();
	}

	private static List<Caravans.Shipment> road(Caravans.Data data, Land land) {
		return data.onTheRoad().stream().filter(s -> s.from().equals(land.hall(0)) && s.to().equals(land.hall(1))).toList();
	}

	/** The mark of the one caravan on the road from village 0 to 1, or null when there isn't exactly one. */
	private static Boolean mark(Caravans.Data data, Land land) {
		List<Caravans.Shipment> road = road(data, land);
		return road.size() == 1 ? road.get(0).pact() : null;
	}

	private static int goods(Caravans.Data data, Land land) {
		return road(data, land).stream().flatMap(s -> s.goods().stream()).mapToInt(ItemStack::getCount).sum();
	}

	private static void ship(Caravans.Data data, Caravans.Shipment shipment) {
		try {
			Method ship = Caravans.Data.class.getDeclaredMethod("ship", Caravans.Shipment.class);
			ship.setAccessible(true);
			ship.invoke(data, shipment);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
