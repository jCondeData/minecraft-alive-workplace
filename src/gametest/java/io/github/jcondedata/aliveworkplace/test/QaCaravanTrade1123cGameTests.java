package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.ClassPerks;
import io.github.jcondedata.aliveworkplace.trade.Board;
import io.github.jcondedata.aliveworkplace.trade.CaravanTrade;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-c-1123-0653) for 33.6, caravans that trade (docs/wiki/caravan-trade.md), with the sister routes of 33.10
 * (colony-born.md) where they touch it, written from the spec. What no earlier test checked: the route limit through the
 * Routes page's own clicks with a sister route beside it; a sister route carrying nothing for sale (and its Routes line
 * saying none); the 16 kept back counted over all of a Storehouse's chests; the bundle boundary at 31 and 32 logs; a
 * buyer's treasury exactly one bundle's price and a cent short; unsold goods coming home to a full Storehouse (nothing
 * lost); a caravan on its way home over a real save and reload of the caravans' list; a sale booked late dated the day
 * it was made; and every sentence of the feature rendered (no raw key, no stray %s). Two halls as in
 * {@link QaCaravansGameTests}: Thornholm (A, known for Test Timber at 1 emerald) and Ashford (B, short of it at 1.3).
 */
public class QaCaravanTrade1123cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	private static QaCaravansGameTests.Two two(GameTestHelper helper) {
		return QaCaravansGameTests.build(helper, false);
	}

	/** A known for timber at 1, B short of it at 1.3; B's treasury {@code treasuryB}; a route from A to B. */
	private static void trade(GameTestHelper helper, QaCaravansGameTests.Two two, int treasuryB, List<Caravans.Want> wants) {
		QaCaravansGameTests.open(helper, two, 0, treasuryB, wants);
		QaCaravansGameTests.market(two, two.a(), List.of(QaCaravansGameTests.TIMBER), List.of(), Map.of(QaCaravansGameTests.TIMBER, 100));
		QaCaravansGameTests.market(two, two.b(), List.of(), List.of(QaCaravansGameTests.TIMBER), Map.of(QaCaravansGameTests.TIMBER, 130));
	}

	private static List<String> chronicle(io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity hall) {
		return QaCaravansGameTests.chronicle(hall);
	}

	/** The lines under {@code village}'s name on {@code hall}'s Routes tab (the Trade page). */
	private static List<String> routeLines(GameTestHelper helper, ServerPlayer player, BlockPos hall, String village) {
		ChoiceMenu menu = TradePage.forTest(player, hall, TradePage.Tab.ROUTES);
		return BoardTradeGameTests.lore(menu.icon(BoardTradeGameTests.slot(helper, menu, village)));
	}

	private static int dropped(GameTestHelper helper, net.minecraft.world.item.Item item) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(16), e -> e.getItem().is(item)).stream()
			.mapToInt(e -> e.getItem().getCount()).sum();
	}

	/**
	 * The route limit through the Routes page's clicks (the player's own entry point): with a sister route already open,
	 * the village still opens as many trade routes as its limit; one more says "A village can send caravans to N
	 * villages at most" and opens nothing; stopping one ("No more caravans to …") frees the place for the refused one;
	 * the sister's click says it stays open and the sister never counts.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa1123cRouteLimit"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1123cRouteLimit")
	public void theRouteLimitThroughTheRoutesPageWithASisterBesideIt(GameTestHelper helper) {
		QaCaravansGameTests.Two two = two(helper);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = two.level();
			two.hallA().setCustomName(Component.literal("Thornholm"));
			two.hallB().setCustomName(Component.literal("Ashford"));
			two.data().setWants(two.a(), Component.literal("Thornholm"), List.of());
			two.data().setWants(two.b(), Component.literal("Ashford"), List.of());
			two.data().addSisters(two.a(), two.b());
			int max = VillageRanks.caravanRoutes(VillageRanks.of(level, two.a())) + ClassPerks.caravanRoutes(level, two.a());
			helper.assertTrue(max >= 1 && max <= 6, "setup: a limit of " + max);
			List<BlockPos> others = new ArrayList<>();
			for (int i = 0; i <= max; i++) {
				BlockPos p = two.a().offset(30 + 3 * i, 0, 0);
				others.add(p);
				String name = "Qa1123cTown" + i;
				two.data().setWants(p, Component.literal(name), List.of());
				Leftovers.after(helper, () -> {
					two.data().remove(p);
					two.data().clearRoad(p);
				});
			}
			Leftovers.after(helper, () -> two.data().clearRoad(two.a()));
			List<Component> seen = new ArrayList<>();
			ServerPlayer player = QaFarVillageName1114eGameTests.player(helper, seen, "qa1123c-routes");
			for (int i = 0; i < max; i++) {
				ChoiceMenu menu = QaCaravanBlankRoutes1116cGameTests.routes(player, level, two.a());
				int slot = QaCaravanBlankRoutes1116cGameTests.slotOf(menu, "Qa1123cTown" + i);
				helper.assertTrue(slot >= 0, "Qa1123cTown" + i + " isn't on the Routes page: " + QaCaravanBlankRoutes1116cGameTests.names(menu));
				menu.press(slot, player);
				helper.assertValueEqual(QaCaravanBlankRoutes1116cGameTests.last(seen), "Caravans will go to Qa1123cTown" + i + " once a day", "route " + (i + 1));
			}
			helper.assertTrue(two.data().tradeRoutes(two.a()) == max && two.data().isSister(two.a(), two.b()), "routes open: " + two.data().tradeRoutes(two.a())
				+ " of " + max + " (the sister counted?)");
			// one more: refused, nothing opened
			String extra = "Qa1123cTown" + max;
			ChoiceMenu menu = QaCaravanBlankRoutes1116cGameTests.routes(player, level, two.a());
			menu.press(QaCaravanBlankRoutes1116cGameTests.slotOf(menu, extra), player);
			helper.assertValueEqual(QaCaravanBlankRoutes1116cGameTests.last(seen), "A village can send caravans to " + max + " villages at most", "past the limit");
			helper.assertTrue(!two.data().routesFrom(two.a()).contains(others.get(max)) && two.data().tradeRoutes(two.a()) == max, "a route past the limit opened");
			// the sister's click: stays open, counts nothing
			menu = QaCaravanBlankRoutes1116cGameTests.routes(player, level, two.a());
			menu.press(QaCaravanBlankRoutes1116cGameTests.slotOf(menu, "Ashford"), player);
			helper.assertValueEqual(QaCaravanBlankRoutes1116cGameTests.last(seen), "Ashford is a sister village: its route stays open and doesn't count toward the limit", "the sister's click");
			helper.assertTrue(two.data().routesFrom(two.a()).contains(two.b()) && two.data().tradeRoutes(two.a()) == max, "the sister's click changed the routes");
			// stop one: the refused village now fits
			menu = QaCaravanBlankRoutes1116cGameTests.routes(player, level, two.a());
			menu.press(QaCaravanBlankRoutes1116cGameTests.slotOf(menu, "Qa1123cTown0"), player);
			helper.assertValueEqual(QaCaravanBlankRoutes1116cGameTests.last(seen), "No more caravans to Qa1123cTown0", "stopping a route");
			menu = QaCaravanBlankRoutes1116cGameTests.routes(player, level, two.a());
			menu.press(QaCaravanBlankRoutes1116cGameTests.slotOf(menu, extra), player);
			helper.assertValueEqual(QaCaravanBlankRoutes1116cGameTests.last(seen), "Caravans will go to " + extra + " once a day", "the freed place");
			helper.assertTrue(two.data().tradeRoutes(two.a()) == max && two.data().routesFrom(two.a()).contains(others.get(max))
				&& !two.data().routesFrom(two.a()).contains(others.get(0)), "routes after the swap: " + two.data().routesFrom(two.a()));
			helper.succeed();
		});
	}

	/**
	 * A sister route carries no goods for sale (colony-born.md, "free both ways"), even when the mother is known for
	 * timber and the colony short of it: only what the colony waits for goes, the timber stays home, no treasury moves;
	 * and the colony's caravan comes the other way without anyone opening a route.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa1123cSisterNoSale"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa1123cSisterNoSale")
	public void aSisterRouteCarriesNothingForSaleBothWays(GameTestHelper helper) {
		QaCaravansGameTests.Two two = two(helper);
		helper.runAfterDelay(2, () -> {
			two.data().addSisters(two.a(), two.b());
			trade(helper, two, 1000, List.of(new Caravans.Want(Items.BREAD, 16)));
			two.data().setWants(two.a(), Component.literal("Thornholm"), List.of(new Caravans.Want(Items.COBBLESTONE, 16)));
			helper.assertTrue(two.data().tradeRoutes(two.a()) == 0 && two.data().tradeRoutes(two.b()) == 0, "setup: a trade route was opened");
			two.ours().setItem(0, new ItemStack(Items.OAK_LOG, 48));
			two.ours().setItem(1, new ItemStack(Items.BREAD, 40));
			two.theirs().setItem(0, new ItemStack(Items.COBBLESTONE, 40));
			Caravans.round(two.level(), two.a(), null);
			Caravans.round(two.level(), two.b(), null);
			helper.assertTrue(two.ours().countItem(Items.OAK_LOG) == 48 && two.ours().countItem(Items.BREAD) == 24,
				"the sister caravan took " + (48 - two.ours().countItem(Items.OAK_LOG)) + " logs to sell, " + (40 - two.ours().countItem(Items.BREAD)) + " bread");
			helper.assertTrue(two.road().stream().allMatch(s -> s.sale().isEmpty()), "goods for sale on a sister route: " + two.road());
			helper.assertTrue(two.theirs().countItem(Items.COBBLESTONE) == 24, "the colony's caravan didn't come the other way: "
				+ two.theirs().countItem(Items.COBBLESTONE) + " cobblestone left");
		});
		helper.runAfterDelay(30, () -> {
			QaCaravansGameTests.arrive(two, two.b());
			QaCaravansGameTests.arrive(two, two.a());
			helper.assertTrue(two.theirs().countItem(Items.BREAD) == 16 && two.ours().countItem(Items.COBBLESTONE) == 16, "arrived: "
				+ two.theirs().countItem(Items.BREAD) + " bread at B, " + two.ours().countItem(Items.COBBLESTONE) + " cobblestone at A");
			helper.assertTrue(two.theirs().countItem(Items.OAK_LOG) == 0 && two.hallA().treasury() == 0 && two.hallB().treasury() == 1000,
				"a sale on a sister route: " + two.theirs().countItem(Items.OAK_LOG) + " logs, A " + two.hallA().treasury() + ", B " + two.hallB().treasury());
			helper.assertTrue(chronicle(two.hallA()).stream().noneMatch(l -> l.startsWith("Sold")) && chronicle(two.hallB()).stream().noneMatch(l -> l.startsWith("Bought")),
				"the chronicles speak of a sale: " + chronicle(two.hallA()) + " " + chronicle(two.hallB()));
			helper.succeed();
		});
	}

	/** The 16 kept back are counted over all of a Storehouse's chests: 24 oak logs in each of two chests spare 2 bundles, and 16 stay. */
	//$ gametest_ticks_batch AREA '100' '"qa1123cTwoChests"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1123cTwoChests")
	public void theSixteenKeptBackCountOverEveryChestOfTheStorehouse(GameTestHelper helper) {
		QaCaravansGameTests.Two two = two(helper);
		helper.runAfterDelay(2, () -> {
			helper.setBlock(new BlockPos(8, 2, 3), Blocks.CHEST);
			Container second = helper.getBlockEntity(new BlockPos(8, 2, 3));
			trade(helper, two, 1000, List.of());
			helper.assertTrue(Caravans.storehouse(two.level(), two.a()).size() == 2, "setup: A's Storehouse chests " + Caravans.storehouse(two.level(), two.a()));
			two.ours().setItem(0, new ItemStack(Items.OAK_LOG, 24));
			second.setItem(0, new ItemStack(Items.OAK_LOG, 24));
			List<CaravanTrade.Offer> offers = CaravanTrade.plan(two.level(), two.a(), two.b(), two.data(),
				Board.stock(two.level(), Caravans.storehouse(two.level(), two.a())));
			helper.assertTrue(offers.size() == 1 && offers.get(0).bundles() == 2, "48 logs over two chests should spare 2 bundles: " + offers.stream()
				.map(o -> o.item() + " x" + o.bundles()).toList());
			Caravans.round(two.level(), two.a(), null);
			int home = two.ours().countItem(Items.OAK_LOG) + second.countItem(Items.OAK_LOG);
			int out = two.road().stream().flatMap(s -> s.sale().stream()).mapToInt(Caravans.Lot::count).sum();
			helper.assertTrue(home == 16 && out == 32, "A kept " + home + " logs and sent " + out);
			helper.succeed();
		});
	}

	/**
	 * The bundle boundary from both sides, through a day's caravan: 31 logs (15 over the 16 kept) send nothing and write
	 * no "A caravan left" line; 32 send exactly one bundle and keep 16.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa1123cBundleEdge"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1123cBundleEdge")
	public void thirtyOneLogsSendNothingAndThirtyTwoSendOneBundle(GameTestHelper helper) {
		QaCaravansGameTests.Two two = two(helper);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			trade(helper, two, 1000, List.of());
			two.ours().setItem(0, new ItemStack(Items.OAK_LOG, 31));
			List<String> lines = routeLines(helper, player, two.a(), "Ashford");
			helper.assertTrue(lines.stream().noneMatch(l -> l.contains("sells there") || l.contains("would sell")), "the Routes tab offers a sale of 31 logs: " + lines);
			Caravans.round(two.level(), two.a(), null);
			helper.assertTrue(two.ours().countItem(Items.OAK_LOG) == 31 && two.road().isEmpty(), "31 logs: " + two.ours().countItem(Items.OAK_LOG) + " left, " + two.road());
			helper.assertTrue(chronicle(two.hallA()).stream().noneMatch(l -> l.startsWith("A caravan left")), "an empty caravan left: " + chronicle(two.hallA()));
			// the next day, one more log
			two.ours().setItem(0, new ItemStack(Items.OAK_LOG, 32));
			two.data().sent(two.a(), -1);
			lines = routeLines(helper, player, two.a(), "Ashford");
			helper.assertTrue(lines.contains("Our caravan sells there:") && lines.contains("Test Timber ×1 for 1.3 emeralds"), "the Routes tab at 32 logs: " + lines);
			Caravans.round(two.level(), two.a(), null);
			int out = two.road().stream().flatMap(s -> s.sale().stream()).mapToInt(Caravans.Lot::count).sum();
			helper.assertTrue(two.ours().countItem(Items.OAK_LOG) == 16 && out == 16, "32 logs: " + two.ours().countItem(Items.OAK_LOG) + " kept, " + out + " sent");
			helper.assertTrue(chronicle(two.hallA()).contains("A caravan left for Ashford with 16 × Oak Log"), "A's chronicle: " + chronicle(two.hallA()));
			helper.succeed();
		});
	}

	/**
	 * The buyer's treasury at the boundary: exactly 1.3 emeralds buys the first bundle and is empty after, the second
	 * comes home; a cent short (1.29) buys nothing, and everything comes home. Emeralds and logs are only ever moved.
	 */
	//$ gametest_ticks_batch AREA '300' '"qa1123cTreasuryEdge"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa1123cTreasuryEdge")
	public void aBuyerWithExactlyOneBundlesPriceBuysOneAndACentShortBuysNone(GameTestHelper helper) {
		QaCaravansGameTests.Two two = two(helper);
		helper.runAfterDelay(2, () -> {
			trade(helper, two, 130, List.of());
			two.ours().setItem(0, new ItemStack(Items.OAK_LOG, 48));
			Caravans.round(two.level(), two.a(), null);
			QaCaravansGameTests.arrive(two, two.b());
			helper.assertTrue(two.theirs().countItem(Items.OAK_LOG) == 16 && two.hallB().treasury() == 0 && two.hallA().treasury() == 130,
				"1.3 in B's treasury: " + two.theirs().countItem(Items.OAK_LOG) + " logs bought, B " + two.hallB().treasury() + ", A " + two.hallA().treasury());
			helper.assertTrue(chronicle(two.hallB()).contains("Our treasury couldn't pay for 16 × Oak Log: Thornholm's caravan took them home"), "B's chronicle: " + chronicle(two.hallB()));
			QaCaravansGameTests.arrive(two, two.a());
			helper.assertTrue(two.ours().countItem(Items.OAK_LOG) == 32 && two.everywhere(Items.OAK_LOG) == 48, "home: " + two.ours().countItem(Items.OAK_LOG)
				+ ", everywhere " + two.everywhere(Items.OAK_LOG));
			// a cent short: a new day, fresh prices, B's treasury 1.29
			two.theirs().clearContent();
			QaCaravansGameTests.market(two, two.a(), List.of(QaCaravansGameTests.TIMBER), List.of(), Map.of(QaCaravansGameTests.TIMBER, 100));
			QaCaravansGameTests.market(two, two.b(), List.of(), List.of(QaCaravansGameTests.TIMBER), Map.of(QaCaravansGameTests.TIMBER, 130));
			two.hallA().setTreasury(0);
			two.hallB().setTreasury(129);
			two.ours().setItem(0, new ItemStack(Items.OAK_LOG, 48));
			two.data().sent(two.a(), -1);
			Caravans.round(two.level(), two.a(), null);
			helper.assertTrue(two.ours().countItem(Items.OAK_LOG) == 16, "second caravan loaded: " + two.ours().countItem(Items.OAK_LOG));
			QaCaravansGameTests.arrive(two, two.b());
			helper.assertTrue(two.theirs().countItem(Items.OAK_LOG) == 0 && two.hallB().treasury() == 129 && two.hallA().treasury() == 0
				&& two.priceB(QaCaravansGameTests.TIMBER).cents() == 130, "1.29: " + two.theirs().countItem(Items.OAK_LOG) + " logs, B " + two.hallB().treasury()
				+ ", A " + two.hallA().treasury() + ", " + two.priceB(QaCaravansGameTests.TIMBER));
			helper.assertTrue(chronicle(two.hallB()).contains("Our treasury couldn't pay for 32 × Oak Log: Thornholm's caravan took them home"), "B's chronicle: " + chronicle(two.hallB()));
			QaCaravansGameTests.arrive(two, two.a());
			helper.assertTrue(two.ours().countItem(Items.OAK_LOG) == 48, "everything home: " + two.ours().countItem(Items.OAK_LOG));
			helper.assertTrue(chronicle(two.hallA()).contains("Our caravan came home from Ashford with 32 × Oak Log unsold"), "A's chronicle: " + chronicle(two.hallA()));
			helper.succeed();
		});
	}

	/**
	 * Unsold goods come home to a Storehouse with no room: not one log is lost (they wait, or lie by the Storehouse),
	 * and with room again they are in the chest.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa1123cHomeFull"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa1123cHomeFull")
	public void unsoldGoodsComingHomeToAFullStorehouseAreNotLost(GameTestHelper helper) {
		QaCaravansGameTests.Two two = two(helper);
		helper.runAfterDelay(2, () -> {
			trade(helper, two, 0, List.of());
			two.ours().setItem(0, new ItemStack(Items.OAK_LOG, 48));
			Caravans.round(two.level(), two.a(), null);
			QaCaravansGameTests.arrive(two, two.b());
			helper.assertTrue(two.road().size() == 1 && two.road().get(0).back(), "nothing coming home: " + two.road());
			for (int i = 0; i < two.ours().getContainerSize(); i++) {
				two.ours().setItem(i, new ItemStack(Items.DIRT, 64));
			}
			QaCaravansGameTests.arrive(two, two.a());
			int total = two.everywhere(Items.OAK_LOG) + dropped(helper, Items.OAK_LOG);
			helper.assertTrue(total == 32, "logs coming home to a full Storehouse: " + total + " of 32 (road " + two.road() + ")");
		});
		helper.runAfterDelay(30, () -> {
			two.ours().setItem(5, ItemStack.EMPTY);
			two.ours().setItem(6, ItemStack.EMPTY);
			QaCaravansGameTests.arrive(two, two.a());
			int total = two.everywhere(Items.OAK_LOG) + dropped(helper, Items.OAK_LOG);
			helper.assertTrue(total == 32 && (two.ours().countItem(Items.OAK_LOG) == 32 || dropped(helper, Items.OAK_LOG) > 0),
				"with room again: " + two.ours().countItem(Items.OAK_LOG) + " in the chest, " + total + " in all");
			helper.succeed();
		});
	}

	/**
	 * A caravan on its way home with unsold logs over a real save and reload of the caravans' list (the live list
	 * replaced by the one read back): it still arrives, unloads into the Storehouse and says so.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa1123cHomeReload"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa1123cHomeReload")
	public void aCaravanOnItsWayHomeSurvivesASaveAndReload(GameTestHelper helper) {
		QaCaravansGameTests.Two two = two(helper);
		helper.runAfterDelay(2, () -> {
			trade(helper, two, 0, List.of());
			two.ours().setItem(0, new ItemStack(Items.OAK_LOG, 48));
			Caravans.round(two.level(), two.a(), null);
			QaCaravansGameTests.arrive(two, two.b());
			helper.assertTrue(two.road().size() == 1 && two.road().get(0).back(), "nothing coming home: " + two.road());
			Caravans.Data read = QaFarVillageName1114eGameTests.reload(helper, tag -> { });
			QaCaravansGameTests.Two after = new QaCaravansGameTests.Two(two.level(), two.a(), two.b(), two.ours(), two.theirs(), read);
			Leftovers.after(helper, () -> {
				read.remove(two.a());
				read.remove(two.b());
				read.clearRoad(two.a());
				read.clearRoad(two.b());
			});
			List<Caravans.Shipment> home = after.road();
			helper.assertTrue(home.size() == 1 && home.get(0).back() && home.get(0).to().equals(two.a())
				&& home.get(0).goods().stream().mapToInt(ItemStack::getCount).sum() + home.get(0).sale().stream().mapToInt(Caravans.Lot::count).sum() == 32,
				"the caravan home after the reload: " + home);
			QaCaravansGameTests.arrive(after, two.a());
			helper.assertTrue(two.ours().countItem(Items.OAK_LOG) == 48 && after.road().isEmpty(), "home after the reload: " + two.ours().countItem(Items.OAK_LOG)
				+ " logs, road " + after.road());
			helper.assertTrue(chronicle(two.hallA()).contains("Our caravan came home from Ashford with 32 × Oak Log unsold"), "A's chronicle: " + chronicle(two.hallA()));
			helper.succeed();
		});
	}

	/**
	 * A sale made while the seller's hall wasn't loaded is booked at its next round "dated the day of the sale": the
	 * chronicle line carries that day, not today's, and the treasury is paid once (a second round pays nothing more).
	 */
	//$ gametest_ticks_batch AREA '100' '"qa1123cLateBooking"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1123cLateBooking")
	public void aSaleBookedLateIsDatedTheDayItWasMadeAndPaidOnce(GameTestHelper helper) {
		QaCaravansGameTests.Two two = two(helper);
		helper.runAfterDelay(2, () -> {
			trade(helper, two, 1000, List.of());
			long today = Chronicle.day(two.level());
			long then = Math.max(1, today - 3);
			helper.assertTrue(then != today || today == 1, "setup");
			two.data().sold(new Caravans.Sale(two.a(), Component.literal("Ashford"), QaCaravansGameTests.TIMBER, 32, 257, then, two.b()));
			Caravans.round(two.level(), two.a(), null);
			Caravans.round(two.level(), two.a(), null);
			helper.assertTrue(two.hallA().treasury() == 257 && two.data().unbooked(two.a()) == 0, "A's treasury " + two.hallA().treasury());
			List<Chronicle.Entry> sold = two.hallA().chronicle().stream().filter(e -> e.text().getString().equals("Sold 32 Test Timber to Ashford for 2.57 emeralds")).toList();
			helper.assertTrue(sold.size() == 1 && sold.get(0).day() == then, "the booked line: " + sold.stream().map(Chronicle.Entry::day).toList() + " (sale day " + then
				+ ", today " + today + ")");
			helper.succeed();
		});
	}

	/**
	 * Every sentence of the feature is in en_us.json and renders with its real arguments: no raw key, no stray %s or
	 * %%, and the % in the tab's own line comes out as one "%".
	 */
	//$ gametest_ticks_batch AREA '40' '"qa1123cSentences"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1123cSentences")
	public void everySentenceOfCaravanTradeRendersWithItsArguments(GameTestHelper helper) {
		Component n = Component.literal("Ashford");
		Component logs = Component.literal("16 × Oak Log");
		Component money = TradePage.money(257);
		Object[][] cases = {
			{"chronicle.aliveworkplace.caravan_sold", 32, Component.literal("Test Timber"), n, money},
			{"chronicle.aliveworkplace.caravan_bought", 32, Component.literal("Test Timber"), n, money},
			{"chronicle.aliveworkplace.caravan_unpaid", logs, n},
			{"chronicle.aliveworkplace.caravan_seller_full", n, logs},
			{"chronicle.aliveworkplace.caravan_refunded", money, n},
			{"chronicle.aliveworkplace.caravan_refund", money},
			{"chronicle.aliveworkplace.caravan_unsold", n, logs},
			{"chronicle.aliveworkplace.caravan_left", n, logs},
			{"chronicle.aliveworkplace.caravan_came", n, logs},
			{"screen.aliveworkplace.hall.route_offer", Component.literal("Test Timber"), 2, money},
			{"screen.aliveworkplace.hall.route_sells"},
			{"screen.aliveworkplace.hall.route_would_sell"},
			{"screen.aliveworkplace.hall.route_sister"},
			{"screen.aliveworkplace.hall.routes_sell", 2, 10},
			{"message.aliveworkplace.hall.route_sister", n},
			{"message.aliveworkplace.hall.route_started", n},
			{"message.aliveworkplace.hall.route_stopped", n},
			{"message.aliveworkplace.hall.route_full", n, 3},
		};
		List<String> bad = new ArrayList<>();
		for (Object[] c : cases) {
			String key = (String) c[0];
			Object[] args = java.util.Arrays.copyOfRange(c, 1, c.length);
			String text = Component.translatable(key, args).getString();
			if (!Language.getInstance().has(key) || text.contains("%") && !key.endsWith("routes_sell") || text.contains("aliveworkplace") || text.isBlank()) {
				bad.add(key + " -> " + text);
			}
		}
		String tab = Component.translatable("screen.aliveworkplace.hall.routes_sell", 2, 10).getString();
		helper.assertTrue(tab.equals("It also takes up to 2 stacks of what we're known for to sell where they're short of it or pay at least 10% more; their treasury pays ours at their board's price"),
			"the tab's line: " + tab);
		helper.assertTrue(Component.translatable("message.aliveworkplace.hall.route_full", n, 3).getString().equals("A village can send caravans to 3 villages at most"),
			"the limit line: " + Component.translatable("message.aliveworkplace.hall.route_full", n, 3).getString());
		helper.assertTrue(bad.isEmpty(), "sentences that don't render: " + bad);
		helper.succeed();
	}
}
