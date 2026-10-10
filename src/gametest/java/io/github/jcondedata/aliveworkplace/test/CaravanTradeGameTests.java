package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.trade.Board;
import io.github.jcondedata.aliveworkplace.trade.CaravanTrade;
import io.github.jcondedata.aliveworkplace.trade.Economy;
import io.github.jcondedata.aliveworkplace.trade.Market;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * ROADMAP 33.6, caravans that trade: besides what the other village waits for (free, as before), a caravan carries up
 * to 2 stacks of goods its village is known for that the other is short of or pays at least 10% more for; on arrival
 * the receiver's treasury pays the sender's at the receiver's board price and both boards move 2% a bundle; what the
 * receiver can't pay for comes home to the sender's Storehouse. The Routes tab says what would go and earn, the
 * chronicles what was sold. Also what's likeliest to break: no room at the other end, a save and reload with the
 * caravan on the road, the sender gone meanwhile, and the economy switched off (before loading, and mid-journey).
 * Two halls stand in one test area as in {@code caravansCarryWhatAnotherVillageNeeds}: Thornholm (A, known for Test
 * Timber at 1 emerald a bundle of 16) and Ashford (B, short of it, paying 1.3).
 */
public class CaravanTradeGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final ResourceLocation TIMBER = BoardTradeGameTests.TIMBER;
	private static final ResourceLocation STONE = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_stone");
	private static final ResourceLocation GRAIN = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_grain");
	private static final BlockPos HALL_A = new BlockPos(3, 2, 3);
	private static final BlockPos CHEST_A = new BlockPos(5, 2, 5);
	private static final BlockPos HALL_B = new BlockPos(18, 2, 18);
	private static final BlockPos CHEST_B = new BlockPos(16, 2, 20);

	/** The two villages of a test: their halls, their Storehouses' chests and the caravans' list. */
	private record Two(ServerLevel level, BlockPos a, BlockPos b, Container ours, Container theirs, Caravans.Data data) {
		VillageHallBlockEntity hallA() {
			return (VillageHallBlockEntity) level.getBlockEntity(a);
		}

		VillageHallBlockEntity hallB() {
			return (VillageHallBlockEntity) level.getBlockEntity(b);
		}

		Market.Price priceA(ResourceLocation good) {
			return data.market(a).prices().get(good);
		}

		Market.Price priceB(ResourceLocation good) {
			return data.market(b).prices().get(good);
		}
	}

	/**
	 * Builds both villages, each with a hall, a Storehouse and a chest, alone in the batch, with only the six test goods,
	 * a small village radius and caravans that arrive at once. Everything is put back when the test ends.
	 */
	private static Two build(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		TradeGoodsDataGameTests.goodsFrom(helper, "aliveworkplace_test");
		int radius = VillageHalls.RADIUS;
		long travel = Caravans.MIN_TRAVEL;
		boolean economy = Economy.ENABLED;
		VillageHalls.RADIUS = 6;
		Caravans.MIN_TRAVEL = 0;
		Economy.ENABLED = true;
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(HALL_A);
		BlockPos b = helper.absolutePos(HALL_B);
		Caravans.Data data = Caravans.Data.get(level);
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Caravans.MIN_TRAVEL = travel;
			Economy.ENABLED = economy;
			data.remove(a);
			data.remove(b);
			data.clearRoad(a);
			data.clearRoad(b);
		});
		helper.setBlock(HALL_A, ModBlocks.VILLAGE_HALL);
		helper.setBlock(new BlockPos(5, 2, 3), ModBlocks.STOREHOUSE);
		helper.setBlock(CHEST_A, Blocks.CHEST);
		helper.setBlock(HALL_B, ModBlocks.VILLAGE_HALL);
		helper.setBlock(new BlockPos(16, 2, 18), ModBlocks.STOREHOUSE);
		helper.setBlock(CHEST_B, Blocks.CHEST);
		return new Two(level, a, b, helper.getBlockEntity(CHEST_A), helper.getBlockEntity(CHEST_B), data);
	}

	/**
	 * Puts both villages on the caravans' list by name (B waiting for {@code wants}) with today's markets: A known for
	 * Test Timber at 1 emerald, B short of it at 1.3; empty treasuries but B's, which holds {@code treasuryB} cents.
	 */
	private static void open(GameTestHelper helper, Two two, int treasuryB, List<Caravans.Want> wants) {
		two.hallA().setCustomName(Component.literal("Thornholm"));
		two.hallB().setCustomName(Component.literal("Ashford"));
		two.data().setWants(two.a(), Component.literal("Thornholm"), List.of());
		two.data().setWants(two.b(), Component.literal("Ashford"), wants);
		market(two, two.a(), List.of(TIMBER), List.of(), Map.of(TIMBER, 100));
		market(two, two.b(), List.of(), List.of(TIMBER), Map.of(TIMBER, 130));
		two.hallA().setTreasury(0);
		two.hallB().setTreasury(treasuryB);
		helper.assertTrue(Caravans.storehouse(two.level(), two.a()).size() == 1 && Caravans.storehouse(two.level(), two.b()).size() == 1,
			"each village should have its own chest: " + Caravans.storehouse(two.level(), two.a()) + " " + Caravans.storehouse(two.level(), two.b()));
	}

	/** Today's market for {@code hall}: its lists, and each of {@code cents}' goods at that price (yesterday's the same). */
	private static void market(Two two, BlockPos hall, List<ResourceLocation> knownFor, List<ResourceLocation> shortOf, Map<ResourceLocation, Integer> cents) {
		Map<ResourceLocation, Market.Price> prices = new LinkedHashMap<>();
		cents.forEach((good, c) -> prices.put(good, new Market.Price(c, c, 0)));
		two.data().setMarket(hall, new Market(knownFor, shortOf, prices, Chronicle.day(two.level()), List.of()));
	}

	private static List<String> chronicle(VillageHallBlockEntity hall) {
		return hall.chronicle().stream().map(e -> e.text().getString()).toList();
	}

	/** The lines under {@code village}'s name on {@code hall}'s Routes tab. */
	private static List<String> route(GameTestHelper helper, ServerPlayer player, BlockPos hall, String village) {
		ChoiceMenu menu = TradePage.forTest(player, hall, TradePage.Tab.ROUTES);
		return BoardTradeGameTests.lore(menu.icon(BoardTradeGameTests.slot(helper, menu, village)));
	}

	private static String offers(List<CaravanTrade.Offer> offers) {
		return offers.stream().map(o -> o.good().id().getPath() + " " + o.item() + " x" + o.bundles() + " " + o.cents()).toList().toString();
	}

	/**
	 * The item's first test. A is known for timber and B is short of it, with a route on: the Routes tab says what would
	 * go and earn; the caravan carries B's bread free and two bundles of timber to sell (A's chest keeps 16 logs back);
	 * B's treasury pays A's at B's price, 1.3 then 1.27 emeralds; both prices move 2% a bundle, down in B and up in A;
	 * and both chronicles say so.
	 */
	//$ gametest_ticks_batch AREA '200' '"caravanSells"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "caravanSells")
	public void aCaravanSellsWhatItsVillageIsKnownForWhereTheyAreShortOfIt(GameTestHelper helper) {
		Two two = build(helper);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			open(helper, two, 1000, List.of(new Caravans.Want(Items.BREAD, 16)));
			two.ours().setItem(0, new ItemStack(Items.OAK_LOG, 48));
			two.ours().setItem(1, new ItemStack(Items.BREAD, 40));
			List<String> before = route(helper, player, two.a(), "Ashford");
			helper.assertTrue(before.contains("A caravan would sell there:") && before.contains("Test Timber ×2 for 2.57 emeralds"), "the Routes tab before: " + before);
			helper.assertTrue(two.data().toggleRoute(two.a(), two.b()), "no route");
			List<String> on = route(helper, player, two.a(), "Ashford");
			helper.assertTrue(on.contains("Our caravan sells there:") && on.contains("Test Timber ×2 for 2.57 emeralds"), "the Routes tab with the route on: " + on);
			List<String> header = BoardTradeGameTests.lore(TradePage.forTest(player, two.a(), TradePage.Tab.ROUTES).icon(TradePage.Tab.ROUTES.slot()));
			helper.assertTrue(header.stream().anyMatch(l -> l.startsWith("It also takes up to 2 stacks of what we're known for to sell")), "the tab's own lines: " + header);
			// B's own Routes tab offers nothing: it isn't known for anything
			List<String> theirs = route(helper, player, two.b(), "Thornholm");
			helper.assertTrue(theirs.stream().noneMatch(l -> l.contains("sell")), "B would sell: " + theirs);

			Caravans.round(two.level(), two.a(), null);
			helper.assertTrue(two.ours().countItem(Items.OAK_LOG) == 16 && two.ours().countItem(Items.BREAD) == 24,
				"loaded: A keeps " + two.ours().countItem(Items.OAK_LOG) + " logs and " + two.ours().countItem(Items.BREAD) + " bread");
			List<Caravans.Shipment> road = two.data().onTheRoad().stream().filter(s -> s.to().equals(two.b())).toList();
			helper.assertTrue(road.size() == 1 && road.get(0).goods().size() == 1 && road.get(0).goods().get(0).is(Items.BREAD) && road.get(0).sale().size() == 1
				&& road.get(0).sale().get(0).good().equals(TIMBER) && road.get(0).sale().get(0).count() == 32 && !road.get(0).back(), "on the road: " + road);
			helper.assertTrue(chronicle(two.hallA()).contains("A caravan left for Ashford with 16 × Bread, 32 × Oak Log"), "A's chronicle: " + chronicle(two.hallA()));
			helper.assertTrue(two.hallB().treasury() == 1000 && two.priceB(TIMBER).cents() == 130, "paid before it arrived");
		});
		helper.runAfterDelay(40, () -> {
			Caravans.round(two.level(), two.b(), null);
			helper.assertTrue(two.theirs().countItem(Items.OAK_LOG) == 32 && two.theirs().countItem(Items.BREAD) == 16,
				"arrived: " + two.theirs().countItem(Items.OAK_LOG) + " logs, " + two.theirs().countItem(Items.BREAD) + " bread");
			helper.assertTrue(two.hallB().treasury() == 1000 - 257 && two.hallA().treasury() == 257,
				"B's treasury should pay A's 1.3 + 1.27: B " + two.hallB().treasury() + ", A " + two.hallA().treasury());
			helper.assertTrue(two.priceB(TIMBER).equals(new Market.Price(124, 130, -2)), "B's price, 2% down a bundle: " + two.priceB(TIMBER));
			helper.assertTrue(two.priceA(TIMBER).equals(new Market.Price(104, 100, 2)), "A's price, 2% up a bundle: " + two.priceA(TIMBER));
			helper.assertTrue(chronicle(two.hallA()).contains("Sold 32 Test Timber to Ashford for 2.57 emeralds"), "A's chronicle: " + chronicle(two.hallA()));
			helper.assertTrue(chronicle(two.hallB()).contains("Bought 32 Test Timber from Thornholm for 2.57 emeralds")
				&& chronicle(two.hallB()).contains("A caravan came from Thornholm with 16 × Bread"), "B's chronicle: " + chronicle(two.hallB()));
			helper.assertTrue(two.data().onTheRoad().stream().noneMatch(s -> s.to().equals(two.a()) || s.to().equals(two.b())) && two.data().sales().isEmpty(),
				"something is still on the road or unbooked: " + two.data().onTheRoad() + " " + two.data().sales());
			// the same day no second caravan goes, however much there is to sell
			two.ours().setItem(2, new ItemStack(Items.OAK_LOG, 64));
			Caravans.round(two.level(), two.a(), null);
			helper.assertTrue(two.ours().countItem(Items.OAK_LOG) == 80, "two caravans in a day");
			helper.succeed();
		});
	}

	/**
	 * The item's second test. B can't pay for everything: its treasury holds 2 emeralds, the first bundle costs 1.3 and
	 * the second 1.27, so one bundle is bought and the other travels home in a caravan of its own, back into A's
	 * Storehouse; only the bundle sold moves the prices, and both chronicles say what happened. Then B with nothing at
	 * all: everything comes home and no price moves.
	 */
	//$ gametest_ticks_batch AREA '200' '"caravanUnpaid"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "caravanUnpaid")
	public void whatTheOtherVillageCantPayForComesHome(GameTestHelper helper) {
		Two two = build(helper);
		helper.runAfterDelay(2, () -> {
			open(helper, two, 200, List.of());
			two.ours().setItem(0, new ItemStack(Items.OAK_LOG, 48));
			helper.assertTrue(two.data().toggleRoute(two.a(), two.b()), "no route");
			Caravans.round(two.level(), two.a(), null);
			helper.assertTrue(two.ours().countItem(Items.OAK_LOG) == 16, "loaded: " + two.ours().countItem(Items.OAK_LOG));
		});
		helper.runAfterDelay(40, () -> {
			Caravans.round(two.level(), two.b(), null);
			helper.assertTrue(two.theirs().countItem(Items.OAK_LOG) == 16, "B should take the bundle it can pay for: " + two.theirs().countItem(Items.OAK_LOG));
			helper.assertTrue(two.hallB().treasury() == 70 && two.hallA().treasury() == 130, "B " + two.hallB().treasury() + ", A " + two.hallA().treasury());
			helper.assertTrue(two.priceB(TIMBER).equals(new Market.Price(127, 130, -1)) && two.priceA(TIMBER).equals(new Market.Price(102, 100, 1)),
				"one bundle moves the prices: B " + two.priceB(TIMBER) + ", A " + two.priceA(TIMBER));
			List<Caravans.Shipment> home = two.data().onTheRoad().stream().filter(s -> s.to().equals(two.a())).toList();
			helper.assertTrue(home.size() == 1 && home.get(0).back() && home.get(0).from().equals(two.b()) && home.get(0).sale().isEmpty()
				&& home.get(0).goods().stream().mapToInt(ItemStack::getCount).sum() == 16, "the caravan home: " + home);
			helper.assertTrue(two.ours().countItem(Items.OAK_LOG) == 16, "home before the caravan was");
			helper.assertTrue(chronicle(two.hallB()).contains("Our treasury couldn't pay for 16 × Oak Log: Thornholm's caravan took them home"),
				"B's chronicle: " + chronicle(two.hallB()));
			helper.assertTrue(chronicle(two.hallA()).contains("Sold 16 Test Timber to Ashford for 1.3 emeralds"), "A's chronicle: " + chronicle(two.hallA()));
		});
		helper.runAfterDelay(70, () -> {
			Caravans.round(two.level(), two.a(), null);
			helper.assertTrue(two.ours().countItem(Items.OAK_LOG) == 32, "the unpaid bundle should be back in A's Storehouse: " + two.ours().countItem(Items.OAK_LOG));
			helper.assertTrue(chronicle(two.hallA()).contains("Our caravan came home from Ashford with 16 × Oak Log unsold"), "A's chronicle: " + chronicle(two.hallA()));
			helper.assertTrue(two.hallA().treasury() == 130 && two.hallB().treasury() == 70, "the way home cost or paid something");
			helper.assertTrue(two.data().onTheRoad().stream().noneMatch(s -> s.to().equals(two.a()) || s.to().equals(two.b())), "still on the road: " + two.data().onTheRoad());

			// B with an empty treasury: the next day's caravan comes home whole, and nothing moves
			two.hallB().setTreasury(0);
			two.ours().setItem(1, new ItemStack(Items.OAK_LOG, 16)); // 48 again: two bundles to spare
			two.data().sent(two.a(), -1);
			Caravans.round(two.level(), two.a(), null);
			helper.assertTrue(two.ours().countItem(Items.OAK_LOG) == 16, "the second caravan: " + two.ours().countItem(Items.OAK_LOG));
		});
		helper.runAfterDelay(100, () -> {
			Caravans.round(two.level(), two.b(), null);
			helper.assertTrue(two.theirs().countItem(Items.OAK_LOG) == 16 && two.hallB().treasury() == 0 && two.hallA().treasury() == 130
				&& two.priceB(TIMBER).cents() == 127 && two.priceA(TIMBER).cents() == 102, "B bought with an empty treasury: " + two.theirs().countItem(Items.OAK_LOG)
				+ " logs, B " + two.hallB().treasury() + ", prices " + two.priceB(TIMBER) + " " + two.priceA(TIMBER));
		});
		helper.runAfterDelay(130, () -> {
			Caravans.round(two.level(), two.a(), null);
			helper.assertTrue(two.ours().countItem(Items.OAK_LOG) == 48, "everything should be home: " + two.ours().countItem(Items.OAK_LOG));
			helper.succeed();
		});
	}

	/**
	 * What goes: only goods the village is known for; only where they're short of it or pay at least 10% more; whole
	 * bundles of one item, a stack at most, over the 16 the chests keep; never an item the other village is waiting for
	 * (that travels free); two stacks at most, what they're short of first, then where it pays best.
	 */
	//$ gametest_ticks_batch AREA '100' '"caravanWhatGoes"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "caravanWhatGoes")
	public void whatACaravanTakesToSell(GameTestHelper helper) {
		Two two = build(helper);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = two.level();
			open(helper, two, 1000, List.of());
			two.ours().setItem(0, new ItemStack(Items.OAK_LOG, 64));
			two.ours().setItem(1, new ItemStack(Items.OAK_LOG, 64));
			two.ours().setItem(2, new ItemStack(Items.BIRCH_LOG, 40));
			List<ItemStack> stock = Board.stock(level, Caravans.storehouse(level, two.a()));
			// short of it: a stack of oak, 4 bundles (128 held, 16 kept, a stack at most), at 1.3, 1.27, 1.24, 1.22
			List<CaravanTrade.Offer> offers = CaravanTrade.plan(level, two.a(), two.b(), two.data(), stock);
			helper.assertTrue(offers.size() == 1 && offers.get(0).item() == Items.OAK_LOG && offers.get(0).bundles() == 4 && offers.get(0).items() == 64
				&& offers.get(0).cents() == 130 + 127 + 124 + 122, "short of it: " + offers(offers));
			// not short of it and paying under 10% more: nothing; at 10% more: it goes
			market(two, two.b(), List.of(), List.of(), Map.of(TIMBER, 109));
			helper.assertTrue(CaravanTrade.plan(level, two.a(), two.b(), two.data(), stock).isEmpty(), "9% more shouldn't go");
			market(two, two.b(), List.of(), List.of(), Map.of(TIMBER, 110));
			offers = CaravanTrade.plan(level, two.a(), two.b(), two.data(), stock);
			helper.assertTrue(offers.size() == 1 && offers.get(0).cents() == 110 + 108 + 106 + 104, "10% more should go: " + offers(offers));
			// not known for it: nothing, however dear it is there
			market(two, two.a(), List.of(), List.of(), Map.of(TIMBER, 100));
			market(two, two.b(), List.of(), List.of(TIMBER), Map.of(TIMBER, 200));
			helper.assertTrue(CaravanTrade.plan(level, two.a(), two.b(), two.data(), stock).isEmpty(), "sold what the village isn't known for");
			// an item they're waiting for travels free, so the caravan sells the good's next item: birch, one bundle over the 16 kept
			market(two, two.a(), List.of(TIMBER), List.of(), Map.of(TIMBER, 100));
			two.data().setWants(two.b(), Component.literal("Ashford"), List.of(new Caravans.Want(Items.OAK_LOG, 32)));
			market(two, two.b(), List.of(), List.of(TIMBER), Map.of(TIMBER, 200));
			offers = CaravanTrade.plan(level, two.a(), two.b(), two.data(), stock);
			helper.assertTrue(offers.size() == 1 && offers.get(0).item() == Items.BIRCH_LOG && offers.get(0).bundles() == 1, "what they wait for is free: " + offers(offers));
			two.data().setWants(two.b(), Component.literal("Ashford"), List.of());
			market(two, two.b(), List.of(), List.of(TIMBER), Map.of(TIMBER, 200));
			// under a bundle to spare: nothing (31 logs: 15 over what's kept)
			helper.assertTrue(CaravanTrade.plan(level, two.a(), two.b(), two.data(), List.of(new ItemStack(Items.OAK_LOG, 31))).isEmpty(), "sold under a bundle");
			// known for three goods: two stacks at most, what they're short of first, then where it pays best
			two.ours().setItem(3, new ItemStack(Items.COBBLESTONE, 64));
			two.ours().setItem(4, new ItemStack(Items.COBBLESTONE, 64));
			two.ours().setItem(5, new ItemStack(Items.WHEAT, 64));
			stock = Board.stock(level, Caravans.storehouse(level, two.a()));
			market(two, two.a(), List.of(TIMBER, STONE, GRAIN), List.of(), Map.of(TIMBER, 100, STONE, 100, GRAIN, 100));
			market(two, two.b(), List.of(), List.of(GRAIN), Map.of(TIMBER, 120, STONE, 150, GRAIN, 100));
			offers = CaravanTrade.plan(level, two.a(), two.b(), two.data(), stock);
			helper.assertTrue(offers.size() == 2 && offers.get(0).good().id().equals(GRAIN) && offers.get(0).bundles() == 2 && offers.get(0).items() == 40
				&& offers.get(1).good().id().equals(STONE) && offers.get(1).bundles() == 1, "two stacks, short of first: " + offers(offers));
			// and that's what's loaded, on top of nothing they wait for
			helper.assertTrue(two.data().toggleRoute(two.a(), two.b()), "no route");
			Caravans.round(level, two.a(), null);
			helper.assertTrue(two.ours().countItem(Items.WHEAT) == 24 && two.ours().countItem(Items.COBBLESTONE) == 64 && two.ours().countItem(Items.OAK_LOG) == 128,
				"loaded: " + two.ours().countItem(Items.WHEAT) + " wheat, " + two.ours().countItem(Items.COBBLESTONE) + " cobblestone, "
				+ two.ours().countItem(Items.OAK_LOG) + " logs");
			helper.succeed();
		});
	}

	/**
	 * A caravan on the road with goods to sell survives a save and reload, as does a sale its seller hasn't booked yet;
	 * a caravan saved before 1.7 (no goods for sale, no sales list) loads as it was; and after the reload the sale goes
	 * through as if nothing had happened.
	 */
	//$ gametest_ticks_batch AREA '200' '"caravanReload"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "caravanReload")
	public void aCaravanWithGoodsToSellSurvivesASaveAndReload(GameTestHelper helper) {
		Two two = build(helper);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = two.level();
			open(helper, two, 1000, List.of(new Caravans.Want(Items.BREAD, 16)));
			two.ours().setItem(0, new ItemStack(Items.OAK_LOG, 48));
			two.ours().setItem(1, new ItemStack(Items.BREAD, 40));
			helper.assertTrue(two.data().toggleRoute(two.a(), two.b()), "no route");
			Caravans.round(level, two.a(), null);
			two.data().sold(new Caravans.Sale(two.a(), Component.literal("Ashford"), TIMBER, 16, 130, 7));
			CompoundTag saved = two.data().save(new CompoundTag(), level.registryAccess());
			Caravans.Data loaded = Caravans.Data.load(saved, level.registryAccess());
			List<Caravans.Shipment> road = loaded.onTheRoad().stream().filter(s -> s.from().equals(two.a()) && s.to().equals(two.b())).toList();
			helper.assertTrue(road.size() == 1 && !road.get(0).back() && road.get(0).goods().size() == 1 && road.get(0).goods().get(0).getCount() == 16
				&& road.get(0).sale().size() == 1 && road.get(0).sale().get(0).good().equals(TIMBER) && road.get(0).sale().get(0).count() == 32
				&& road.get(0).sale().get(0).stacks().get(0).is(Items.OAK_LOG), "the caravan after the reload: " + road);
			helper.assertTrue(loaded.sales().size() == 1 && loaded.sales().get(0).seller().equals(two.a()) && loaded.sales().get(0).good().equals(TIMBER)
				&& loaded.sales().get(0).items() == 16 && loaded.sales().get(0).cents() == 130 && loaded.sales().get(0).day() == 7
				&& loaded.sales().get(0).buyer().getString().equals("Ashford"), "the unbooked sale after the reload: " + loaded.sales());
			// a save from before 1.7: no "sale" or "back" on a caravan, no "sales" list
			ListTag caravans = saved.getList("road", Tag.TAG_COMPOUND);
			for (int i = 0; i < caravans.size(); i++) {
				caravans.getCompound(i).remove("sale");
				caravans.getCompound(i).remove("back");
			}
			saved.remove("sales");
			Caravans.Data old = Caravans.Data.load(saved, level.registryAccess());
			List<Caravans.Shipment> oldRoad = old.onTheRoad().stream().filter(s -> s.from().equals(two.a()) && s.to().equals(two.b())).toList();
			helper.assertTrue(oldRoad.size() == 1 && oldRoad.get(0).sale().isEmpty() && !oldRoad.get(0).back() && oldRoad.get(0).goods().size() == 1
				&& old.sales().isEmpty(), "an old save's caravan: " + oldRoad);
			// a caravan on its way home survives too
			CompoundTag again = two.data().save(new CompoundTag(), level.registryAccess());
			again.getList("road", Tag.TAG_COMPOUND).getCompound(0).putBoolean("back", true);
			helper.assertTrue(Caravans.Data.load(again, level.registryAccess()).onTheRoad().get(0).back(), "the way home wasn't kept");
		});
		helper.runAfterDelay(40, () -> {
			// the game's own save of the list, then the sale: the booked sale and the caravan's
			two.data().save(new CompoundTag(), two.level().registryAccess());
			Caravans.round(two.level(), two.b(), null);
			helper.assertTrue(two.theirs().countItem(Items.OAK_LOG) == 32 && two.theirs().countItem(Items.BREAD) == 16 && two.hallB().treasury() == 743,
				"arrived: " + two.theirs().countItem(Items.OAK_LOG) + " logs, B " + two.hallB().treasury());
			helper.assertTrue(two.hallA().treasury() == 257 + 130 && two.data().sales().isEmpty(), "A should book both sales: " + two.hallA().treasury());
			helper.assertTrue(chronicle(two.hallA()).contains("Sold 16 Test Timber to Ashford for 1.3 emeralds")
				&& chronicle(two.hallA()).contains("Sold 32 Test Timber to Ashford for 2.57 emeralds"), "A's chronicle: " + chronicle(two.hallA()));
			helper.succeed();
		});
	}

	/**
	 * The {@code villageEconomy} switch off: the Routes list offers nothing, and a caravan carries only what the other
	 * village waits for, as before. Switched off with a caravan already on the road: nothing is bought, no treasury or
	 * price moves, and its goods for sale come home.
	 */
	//$ gametest_ticks_batch AREA '200' '"caravanEconomyOff"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "caravanEconomyOff")
	public void withTheEconomyOffCaravansCarryOnlyWhatIsWaitedFor(GameTestHelper helper) {
		Two two = build(helper);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = two.level();
			open(helper, two, 1000, List.of(new Caravans.Want(Items.BREAD, 16)));
			two.ours().setItem(0, new ItemStack(Items.OAK_LOG, 48));
			two.ours().setItem(1, new ItemStack(Items.BREAD, 40));
			helper.assertTrue(two.data().toggleRoute(two.a(), two.b()), "no route");
			Economy.ENABLED = false;
			helper.assertTrue(CaravanTrade.plan(level, two.a(), two.b(), two.data(), Board.stock(level, Caravans.storehouse(level, two.a()))).isEmpty(),
				"a plan with the economy off");
			ChoiceMenu routes = VillageHallScreen.forTest(player, two.a());
			routes.press(VillageHallScreen.ROUTES, player);
			List<String> lines = BoardTradeGameTests.lore(routes.icon(BoardTradeGameTests.slot(helper, routes, "Ashford")));
			helper.assertTrue(lines.stream().noneMatch(l -> l.contains("sell")), "the old routes page offers a sale: " + lines);
			helper.assertTrue(BoardTradeGameTests.lore(routes.icon(4)).stream().noneMatch(l -> l.contains("sell")),
				"the old routes page's title talks of selling: " + BoardTradeGameTests.lore(routes.icon(4)));
			Caravans.round(level, two.a(), null);
			helper.assertTrue(two.ours().countItem(Items.OAK_LOG) == 48 && two.ours().countItem(Items.BREAD) == 24,
				"with the economy off only the bread should go: " + two.ours().countItem(Items.OAK_LOG) + " logs, " + two.ours().countItem(Items.BREAD) + " bread");
			helper.assertTrue(two.data().onTheRoad().stream().filter(s -> s.to().equals(two.b())).allMatch(s -> s.sale().isEmpty()), "goods for sale on the road");
		});
		helper.runAfterDelay(40, () -> {
			ServerLevel level = two.level();
			Caravans.round(level, two.b(), null);
			helper.assertTrue(two.theirs().countItem(Items.BREAD) == 16 && two.theirs().countItem(Items.OAK_LOG) == 0 && two.hallB().treasury() == 1000
				&& two.hallA().treasury() == 0, "the free caravan: " + two.theirs().countItem(Items.BREAD) + " bread, B " + two.hallB().treasury());

			// on again: a caravan leaves with timber to sell; then the switch goes off while it's on the road
			Economy.ENABLED = true;
			two.data().sent(two.a(), -1);
			Caravans.round(level, two.a(), null);
			helper.assertTrue(two.ours().countItem(Items.OAK_LOG) == 16, "the trading caravan: " + two.ours().countItem(Items.OAK_LOG));
			Economy.ENABLED = false;
		});
		helper.runAfterDelay(80, () -> {
			Caravans.round(two.level(), two.b(), null);
			helper.assertTrue(two.theirs().countItem(Items.OAK_LOG) == 0 && two.hallB().treasury() == 1000 && two.hallA().treasury() == 0
				&& two.priceB(TIMBER).equals(new Market.Price(130, 130, 0)) && two.priceA(TIMBER).equals(new Market.Price(100, 100, 0)),
				"a sale with the economy off: " + two.theirs().countItem(Items.OAK_LOG) + " logs, B " + two.hallB().treasury() + ", " + two.priceB(TIMBER));
			helper.assertTrue(chronicle(two.hallB()).stream().noneMatch(l -> l.startsWith("Bought") || l.contains("couldn't pay")), "B's chronicle: " + chronicle(two.hallB()));
		});
		helper.runAfterDelay(120, () -> {
			Caravans.round(two.level(), two.a(), null);
			helper.assertTrue(two.ours().countItem(Items.OAK_LOG) == 48, "the timber should come home: " + two.ours().countItem(Items.OAK_LOG));
			helper.succeed();
		});
	}

	/**
	 * What's likeliest to go wrong at the other end. B's chest is full: nothing is bought or paid and the goods wait on
	 * the road until there's room, then sell. And A's hall is taken away while its caravan is out: there's nobody to pay
	 * or to send anything back to, so B simply keeps the goods and its emeralds.
	 */
	//$ gametest_ticks_batch AREA '200' '"caravanNoRoom"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "caravanNoRoom")
	public void goodsWaitForRoomAndASenderThatIsGoneIsNotPaid(GameTestHelper helper) {
		Two two = build(helper);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = two.level();
			open(helper, two, 1000, List.of());
			two.ours().setItem(0, new ItemStack(Items.OAK_LOG, 48));
			helper.assertTrue(two.data().toggleRoute(two.a(), two.b()), "no route");
			for (int i = 0; i < two.theirs().getContainerSize(); i++) {
				two.theirs().setItem(i, new ItemStack(Items.DIRT, 64));
			}
			Caravans.round(level, two.a(), null);
		});
		helper.runAfterDelay(40, () -> {
			ServerLevel level = two.level();
			Caravans.round(level, two.b(), null);
			helper.assertTrue(two.theirs().countItem(Items.OAK_LOG) == 0 && two.hallB().treasury() == 1000 && two.hallA().treasury() == 0
				&& two.priceB(TIMBER).cents() == 130, "bought without room: B " + two.hallB().treasury() + ", " + two.priceB(TIMBER));
			List<Caravans.Shipment> road = two.data().onTheRoad().stream().filter(s -> s.to().equals(two.b())).toList();
			helper.assertTrue(road.size() == 1 && road.get(0).sale().size() == 1 && road.get(0).sale().get(0).count() == 32 && !road.get(0).back()
				&& two.data().onTheRoad().stream().noneMatch(s -> s.to().equals(two.a())), "the goods should wait on the road: " + two.data().onTheRoad());
			// room for one bundle (16 beside 48 logs in one slot): one is bought, the other waits on
			two.theirs().setItem(0, new ItemStack(Items.OAK_LOG, 48));
			two.data().hurry(two.b());
			Caravans.round(level, two.b(), null);
			helper.assertTrue(two.theirs().countItem(Items.OAK_LOG) == 64 && two.hallB().treasury() == 870 && two.hallA().treasury() == 130
				&& two.priceB(TIMBER).equals(new Market.Price(127, 130, -1)), "room for one bundle: " + two.theirs().countItem(Items.OAK_LOG) + " logs, B "
				+ two.hallB().treasury() + ", " + two.priceB(TIMBER));
			road = two.data().onTheRoad().stream().filter(s -> s.to().equals(two.b())).toList();
			helper.assertTrue(road.size() == 1 && road.get(0).sale().get(0).count() == 16 && two.data().onTheRoad().stream().noneMatch(s -> s.to().equals(two.a())),
				"the other bundle should wait: " + two.data().onTheRoad());
			// room again: the bundle that waited sells at the moved price
			two.theirs().setItem(1, ItemStack.EMPTY);
			two.data().hurry(two.b());
			Caravans.round(level, two.b(), null);
			helper.assertTrue(two.theirs().countItem(Items.OAK_LOG) == 64 + 16 && two.hallB().treasury() == 743, "with room again the other bundle sells: "
				+ two.theirs().countItem(Items.OAK_LOG) + " logs, B " + two.hallB().treasury());
			helper.assertTrue(two.hallA().treasury() == 257 && two.data().onTheRoad().stream().noneMatch(s -> s.to().equals(two.b())), "A " + two.hallA().treasury());

			// the next caravan leaves, and A's hall is taken away while it's out
			two.ours().setItem(1, new ItemStack(Items.OAK_LOG, 32));
			two.data().sent(two.a(), -1);
			Caravans.round(level, two.a(), null);
			helper.assertTrue(two.data().onTheRoad().stream().anyMatch(s -> s.to().equals(two.b()) && !s.sale().isEmpty()), "no second caravan");
			two.theirs().setItem(2, ItemStack.EMPTY);
			helper.setBlock(HALL_A, Blocks.AIR);
			helper.assertTrue(two.data().village(two.a()) == null, "A is still on the list");
			two.data().hurry(two.b());
			Caravans.round(level, two.b(), null);
			helper.assertTrue(two.theirs().countItem(Items.OAK_LOG) == 64 + 16 + 32 && two.hallB().treasury() == 743 && two.data().sales().isEmpty()
				&& two.data().onTheRoad().stream().noneMatch(s -> s.to().equals(two.a()) || s.to().equals(two.b())),
				"a sender that's gone: " + two.theirs().countItem(Items.OAK_LOG) + " logs, B " + two.hallB().treasury() + ", " + two.data().onTheRoad());
			helper.succeed();
		});
	}

	/**
	 * The hall the caravan was going to is taken away mid-journey: nothing is sold, and the goods it carried to sell
	 * turn round and come back into the sender's Storehouse.
	 */
	//$ gametest_ticks_batch AREA '200' '"caravanHallGone"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "caravanHallGone")
	public void theOtherHallTakenAwayMidJourneySendsTheGoodsHome(GameTestHelper helper) {
		Two two = build(helper);
		helper.runAfterDelay(2, () -> {
			open(helper, two, 1000, List.of());
			two.ours().setItem(0, new ItemStack(Items.OAK_LOG, 48));
			helper.assertTrue(two.data().toggleRoute(two.a(), two.b()), "no route");
			Caravans.round(two.level(), two.a(), null);
			helper.assertTrue(two.ours().countItem(Items.OAK_LOG) == 16, "loaded: " + two.ours().countItem(Items.OAK_LOG));
			helper.setBlock(HALL_B, Blocks.AIR);
			helper.assertTrue(two.data().village(two.b()) == null, "B is still on the list");
			List<Caravans.Shipment> road = two.data().onTheRoad().stream().filter(s -> s.to().equals(two.a()) || s.to().equals(two.b())).toList();
			helper.assertTrue(road.size() == 1 && road.get(0).to().equals(two.a()) && road.get(0).back() && road.get(0).sale().isEmpty()
				&& road.get(0).goods().stream().mapToInt(ItemStack::getCount).sum() == 32, "the caravan should turn round: " + road);
		});
		helper.runAfterDelay(40, () -> {
			Caravans.round(two.level(), two.a(), null);
			helper.assertTrue(two.ours().countItem(Items.OAK_LOG) == 48 && two.hallA().treasury() == 0 && two.priceA(TIMBER).equals(new Market.Price(100, 100, 0)),
				"the timber should be home, unsold: " + two.ours().countItem(Items.OAK_LOG) + " logs, A " + two.hallA().treasury() + ", " + two.priceA(TIMBER));
			helper.assertTrue(chronicle(two.hallA()).contains("Our caravan came home from Someone with 32 × Oak Log unsold"), "A's chronicle: " + chronicle(two.hallA()));
			helper.succeed();
		});
	}
}
