package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA (lane-c-1123-0653) bug tests for 33.6 with 33.10's sister routes: they fail on the code as it is and travel on
 * branch tests/caravans-1123c until fixed.
 */
public class QaCaravanTrade1123cBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/**
	 * colony-born.md: "Sister routes carry no goods for sale", and the caravan indeed sells nothing there. caravan-trade.md:
	 * the Routes tab's "Our caravan sells there:" lines say what the caravan takes to sell. So on a sister village's
	 * entry the Routes tab must not promise a sale ("Our caravan sells there: Test Timber ×2 for 2.57 emeralds") that the
	 * caravan will never make.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa1123cSisterTab"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1123cSisterTab")
	public void theRoutesTabPromisesNoSaleOnASisterRoute(GameTestHelper helper) {
		QaCaravansGameTests.Two two = QaCaravansGameTests.build(helper, false);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			two.data().addSisters(two.a(), two.b());
			QaCaravansGameTests.open(helper, two, 0, 1000, List.of());
			QaCaravansGameTests.market(two, two.a(), List.of(QaCaravansGameTests.TIMBER), List.of(), Map.of(QaCaravansGameTests.TIMBER, 100));
			QaCaravansGameTests.market(two, two.b(), List.of(), List.of(QaCaravansGameTests.TIMBER), Map.of(QaCaravansGameTests.TIMBER, 130));
			two.ours().setItem(0, new ItemStack(Items.OAK_LOG, 48));
			ChoiceMenu menu = TradePage.forTest(player, two.a(), TradePage.Tab.ROUTES);
			List<String> lines = BoardTradeGameTests.lore(menu.icon(BoardTradeGameTests.slot(helper, menu, "Ashford")));
			// what the caravan really does: nothing for sale
			Caravans.round(two.level(), two.a(), null);
			helper.assertTrue(two.ours().countItem(Items.OAK_LOG) == 48, "setup: the sister caravan took logs to sell");
			helper.assertTrue(lines.contains("Sister village: caravans both ways, free, outside the limit"), "setup: not shown as a sister: " + lines);
			helper.assertTrue(lines.stream().noneMatch(l -> l.contains("sells there") || l.contains("would sell") || l.contains("Test Timber ×")),
				"the Routes tab promises a sale the sister caravan never makes: " + lines);
			helper.succeed();
		});
	}
}
