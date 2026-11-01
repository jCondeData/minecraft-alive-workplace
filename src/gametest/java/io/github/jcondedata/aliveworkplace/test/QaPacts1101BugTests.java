package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.CaravanSights;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.realm.Pacts;
import io.github.jcondedata.aliveworkplace.test.QaPactsRealmEdictsGameTests.Two;
import io.github.jcondedata.aliveworkplace.test.RealmGameTests.Land;
import io.github.jcondedata.aliveworkplace.trade.TradeGoods;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * QA (ROADMAP 33.18), a failing test kept out of the suite until the mod is fixed: a caravan that set out under a
 * trade pact unloads its cargo free when the pact is ended while it's on the road.
 */
public class QaPacts1101BugTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/**
	 * A caravan that set out under a trade pact and arrives after the pact has ended must not hand its cargo over for
	 * nothing: either it is paid for at the board's price, or it doesn't unload into the other village (spec: "even what
	 * the other is waiting for is paid for, unless the sending owner turns on gifts"). Otherwise ending a pact the moment
	 * a caravan leaves gets its cargo free. A control caravan with the pact standing is paid first.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qa1101CaravanUnderway"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qa1101CaravanUnderway")
	public void aCaravanUnderwayWhenThePactEndsIsNotHandedOverFree(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		ServerLevel level = land.level();
		Caravans.Data data = Caravans.Data.get(level);
		boolean sights = CaravanSights.ENABLED;
		CaravanSights.ENABLED = false;
		net.minecraft.core.BlockPos store = new net.minecraft.core.BlockPos(12, 2, 7);
		net.minecraft.core.BlockPos chestAt = new net.minecraft.core.BlockPos(12, 2, 9);
		Leftovers.after(helper, () -> {
			CaravanSights.ENABLED = sights;
			data.clearRoad(land.hall(0));
			data.clearRoad(land.hall(1));
			if (helper.getLevel().getBlockEntity(helper.absolutePos(chestAt)) instanceof net.minecraft.world.Container c) {
				c.clearContent();
			}
			helper.setBlock(chestAt, net.minecraft.world.level.block.Blocks.AIR);
			helper.setBlock(store, net.minecraft.world.level.block.Blocks.AIR);
		});
		helper.setBlock(store, io.github.jcondedata.aliveworkplace.registry.ModBlocks.STOREHOUSE);
		helper.setBlock(chestAt, net.minecraft.world.level.block.Blocks.CHEST);
		TradeGoods.Good good = TradeGoods.all().get(0);
		data.setWants(land.hall(1), net.minecraft.network.chat.Component.literal(land.name(1)), List.of(new Caravans.Want(good.icon(), good.bundle() * 2)));
		QaPactsRealmEdictsGameTests.pact(land, false);
		land.entity(1).setTreasury(100_000);
		helper.runAfterDelay(5, () -> { // the Storehouse's point of interest is registered a tick after it's set
			net.minecraft.world.Container chest = helper.getBlockEntity(chestAt);
			ship(data, new Caravans.Shipment(land.hall(0), land.hall(1), List.of(new ItemStack(good.icon(), good.bundle())), level.getGameTime()));
			Caravans.round(level, land.hall(1), null);
			int control = 100_000 - land.entity(1).treasury();
			helper.assertTrue(chest.countItem(good.icon()) == good.bundle() && control > 0,
				"setup: the control caravan under the pact: " + chest.countItem(good.icon()) + " delivered, " + control + " paid");
			// the next caravan sets out under the pact; the receiver ends the pact while it's on the road
			ship(data, new Caravans.Shipment(land.hall(0), land.hall(1), List.of(new ItemStack(good.icon(), good.bundle())), level.getGameTime() + 20));
			helper.assertTrue(Pacts.end(level, land.hall(1), land.at(0), two.them()).ok(), "couldn't end the pact");
			helper.runAfterDelay(30, () -> {
				int treasury = land.entity(1).treasury();
				Caravans.round(level, land.hall(1), null);
				int paid = treasury - land.entity(1).treasury();
				int delivered = chest.countItem(good.icon()) - good.bundle();
				helper.assertTrue(delivered == 0 || paid > 0,
					"the caravan that set out under the pact unloaded " + delivered + " × " + good.id() + " after the receiver ended the pact, and was paid " + paid
						+ " (the same cargo under the pact paid " + control + ")");
				helper.succeed();
			});
		});
	}

	private static void ship(Caravans.Data data, Caravans.Shipment shipment) {
		try {
			java.lang.reflect.Method ship = Caravans.Data.class.getDeclaredMethod("ship", Caravans.Shipment.class);
			ship.setAccessible(true);
			ship.invoke(data, shipment);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
