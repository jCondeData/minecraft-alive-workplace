package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.realm.Pacts;
import io.github.jcondedata.aliveworkplace.test.QaPactsRealmEdictsGameTests.Two;
import io.github.jcondedata.aliveworkplace.test.RealmGameTests.Land;
import io.github.jcondedata.aliveworkplace.trade.TradeGoods;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * QA (2026-10-31), 33.18: failures where the mod departs from the spec. Kept apart from {@link QaPacts1031GameTests}
 * so the passing suite stays green; each test's message names the spec line.
 */
public class QaPacts1031BugTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/**
	 * docs/design/M33.md: "Pre-1.7 routes to another owner's village become pacts once ... with gifts on for the sender,
	 * so cargo that went free stays free until an owner turns gifts off." A route 0 to 1 saved before 1.7: after the
	 * load, the sender gives, and the receiver's treasury pays nothing for that cargo.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa1031BugMigrateGifts"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1031BugMigrateGifts")
	public void anOldRoutesCargoStaysFreeAfterTheLoad(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		Caravans.Data caravans = Caravans.Data.get(level);
		helper.assertTrue(caravans.toggleRoute(land.hall(0), land.hall(1)), "setup: a route");
		Leftovers.after(helper, () -> {
			if (caravans.routesFrom(land.hall(0)).contains(land.hall(1))) {
				caravans.toggleRoute(land.hall(0), land.hall(1));
			}
		});
		Pacts.Data.get(server).setMigrated(false);
		Pacts.settle(server);
		Pacts.Pact pact = Pacts.Data.get(server).pact(land.at(0), land.at(1));
		helper.assertTrue(pact != null, "the old route didn't become a pact");
		TradeGoods.Good good = TradeGoods.all().get(0);
		land.entity(1).setTreasury(100_000);
		int paid = Caravans.payPact(level, land.hall(1), land.hall(0), List.of(new ItemStack(good.icon(), good.bundle())), caravans);
		helper.assertTrue(Pacts.gives(level, land.hall(0), land.hall(1)) && paid == 0,
			"M33 design: a pre-1.7 route's pact has gifts on for the sender, so its cargo stays free; got gifts "
				+ Pacts.gives(level, land.hall(0), land.hall(1)) + ", the receiver paid " + paid + " cents for a bundle");
		helper.succeed();
	}

	/**
	 * 33.18: cargo is paid for "unless the sending owner turns on gifts". An owner turns gifts on for their trade pact,
	 * then the pact becomes an alliance (the other side accepts an alliance offer on the Pacts tab): the owner never
	 * turned gifts off, so they stay on.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa1031BugUpgradeGifts"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1031BugUpgradeGifts")
	public void giftsStayOnWhenAPactBecomesAnAlliance(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		QaPactsRealmEdictsGameTests.pact(land, false);
		helper.assertTrue(Pacts.toggleGifts(level, land.hall(0), land.at(1), land.owner()), "setup: gifts didn't turn on");
		helper.assertTrue(Pacts.offer(level, land.hall(0), land.hall(1), true, land.owner()).ok(), "the alliance offer was refused");
		ChoiceMenu menu = QaPactsRealmEdictsGameTests.tab(land, 1, two.them(), TradePage.Tab.PACTS);
		int accept = QaPactsRealmEdictsGameTests.find(menu, "Accept an alliance with " + land.name(0));
		helper.assertTrue(accept >= 0, "no Accept for the alliance");
		menu.press(accept, two.them());
		Pacts.Pact pact = Pacts.Data.get(server).pact(land.at(0), land.at(1));
		helper.assertTrue(pact != null && pact.alliance(), "not an alliance: " + pact);
		helper.assertTrue(Pacts.gives(level, land.hall(0), land.hall(1)),
			"33.18: the sending owner turned gifts on and never off, but the alliance upgrade turned them off: " + pact);
		helper.succeed();
	}
}
