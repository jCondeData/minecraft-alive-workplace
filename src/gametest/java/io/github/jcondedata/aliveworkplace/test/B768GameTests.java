package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.realm.Pacts;
import io.github.jcondedata.aliveworkplace.test.QaPactsRealmEdictsGameTests.Two;
import io.github.jcondedata.aliveworkplace.test.RealmGameTests.Land;
import io.github.jcondedata.aliveworkplace.trade.TradeGoods;
import java.lang.reflect.Method;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * B768: a caravan that set out under a trade pact is still paid at the pact's terms when the pact ends while it's on the
 * road (the arrival itself: QaPacts1101BugTests). Here the mark's edges: only the sides with gifts off are marked, the
 * mark survives a save, and a caravan unmarked (one that set out after the pact ended) or with pacts off isn't paid.
 */
public class B768GameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/**
	 * A pact with the owner's side giving and the neighbour's not ends while a caravan is on the road each way: the
	 * neighbour's caravan is marked (paid at the pact's terms on arrival) and stays marked after a save and load; the
	 * owner's, a gift, isn't. Without the mark (a caravan that left after the pact ended) or with pacts off, nothing is paid.
	 */
	//$ gametest_ticks_batch HUGE '60' '"b768Marks"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "b768Marks")
	public void onlyTheCaravansThatSetOutUnderAPaidPactArePaidAfterItEnds(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		ServerLevel level = land.level();
		Caravans.Data data = Caravans.Data.get(level);
		Leftovers.after(helper, () -> {
			data.clearRoad(land.hall(0));
			data.clearRoad(land.hall(1));
		});
		TradeGoods.Good good = TradeGoods.all().get(0);
		Pacts.Data.get(level.getServer()).put(new Pacts.Pact(land.at(0), land.at(1), false, 0, -1, true, false)); // the owner's side gives
		ship(data, new Caravans.Shipment(land.hall(0), land.hall(1), List.of(new ItemStack(good.icon(), good.bundle())), level.getGameTime() + 1000));
		ship(data, new Caravans.Shipment(land.hall(1), land.hall(0), List.of(new ItemStack(good.icon(), good.bundle())), level.getGameTime() + 1000));
		helper.assertTrue(Pacts.end(level, land.hall(1), land.at(0), two.them()).ok(), "couldn't end the pact");
		helper.assertTrue(Pacts.pact(level, land.hall(0), land.hall(1)) == null, "the pact still stands");
		helper.assertTrue(mark(data, land, 0, 1) == Boolean.FALSE, "the owner's gift caravan was marked to be paid: " + mark(data, land, 0, 1));
		helper.assertTrue(mark(data, land, 1, 0) == Boolean.TRUE, "the neighbour's paid caravan wasn't marked: " + mark(data, land, 1, 0));
		Caravans.Data loaded = Caravans.Data.load(data.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
		helper.assertTrue(mark(loaded, land, 1, 0) == Boolean.TRUE && mark(loaded, land, 0, 1) == Boolean.FALSE,
			"the marks after a save and load: " + mark(loaded, land, 1, 0) + ", " + mark(loaded, land, 0, 1));
		List<ItemStack> delivered = List.of(new ItemStack(good.icon(), good.bundle()));
		land.entity(0).setTreasury(100_000);
		helper.assertTrue(Caravans.payPact(level, land.hall(0), land.hall(1), delivered, data, false) == 0, "a caravan sent after the pact ended was paid");
		boolean pacts = Pacts.ENABLED;
		Pacts.ENABLED = false;
		int off;
		try {
			off = Caravans.payPact(level, land.hall(0), land.hall(1), delivered, data, true);
		} finally {
			Pacts.ENABLED = pacts;
		}
		helper.assertTrue(off == 0, "paid with pacts off: " + off);
		int paid = Caravans.payPact(level, land.hall(0), land.hall(1), delivered, data, true);
		helper.assertTrue(paid > 0 && land.entity(0).treasury() == 100_000 - paid, "the caravan under the ended pact wasn't paid: " + paid);
		helper.succeed();
	}

	/** The mark of the one caravan on the road from village {@code a} to {@code b}, or null when there isn't exactly one. */
	private static Boolean mark(Caravans.Data data, Land land, int a, int b) {
		List<Caravans.Shipment> road = data.onTheRoad().stream().filter(s -> s.from().equals(land.hall(a)) && s.to().equals(land.hall(b))).toList();
		return road.size() == 1 ? road.get(0).pact() : null;
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
