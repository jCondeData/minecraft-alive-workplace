package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.CaravanSights;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
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
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-c-1025-0352) for 33.6 and 33.7, written from the ROADMAP spec and design note M33, not from the code.
 * 33.6, caravans that trade: the 2-stack cap with three goods to sell (on top of the free waited-for load), the "pays at
 * least 10% more" boundary at uneven prices, a good they're short of going even where it's cheaper and paid at the
 * RECEIVER's price, nothing duplicated or lost (goods and emeralds counted everywhere) through a partial payment and its
 * way home, an arrival never paid twice, the free waited-for load taking the goods first, and the singular "1 emerald"
 * on the Routes tab and in both chronicles. 33.7, caravans you can see: the 96-block boundary from both sides, the goods that arrive the same with sights on or off when there is a sale and a partial payment, a caravan
 * coming home with unsold goods shown as the sender's own, and a party offered a lead or a name tag. Two bug tests
 * (a full seller's treasury, a killed party) are in {@code QaCaravansBugTests}, not registered. Two halls stand in one big area as in {@code CaravanTradeGameTests}:
 * Thornholm (A) and Ashford (B).
 */
public class QaCaravansGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	static final ResourceLocation TIMBER = BoardTradeGameTests.TIMBER;
	static final ResourceLocation STONE = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_stone");
	static final ResourceLocation GRAIN = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_grain");
	static final BlockPos HALL_A = new BlockPos(3, 2, 3);
	static final BlockPos CHEST_A = new BlockPos(5, 2, 5);
	static final BlockPos HALL_B = new BlockPos(18, 2, 18);
	static final BlockPos CHEST_B = new BlockPos(16, 2, 20);

	/** The two villages of a test. */
	record Two(ServerLevel level, BlockPos a, BlockPos b, Container ours, Container theirs, Caravans.Data data) {
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

		/** Every caravan on the road to or from either village. */
		List<Caravans.Shipment> road() {
			return data.onTheRoad().stream().filter(s -> s.to().equals(a) || s.to().equals(b) || s.from().equals(a) || s.from().equals(b)).toList();
		}

		/** {@code item} everywhere it can be: both chests and every caravan on the road (goods and goods for sale). */
		int everywhere(Item item) {
			int n = ours.countItem(item) + theirs.countItem(item);
			for (Caravans.Shipment s : road()) {
				n += s.goods().stream().filter(st -> st.is(item)).mapToInt(ItemStack::getCount).sum();
				for (Caravans.Lot lot : s.sale()) {
					n += lot.stacks().stream().filter(st -> st.is(item)).mapToInt(ItemStack::getCount).sum();
				}
			}
			return n;
		}

		int money() {
			return hallA().treasury() + hallB().treasury();
		}
	}

	/**
	 * Both villages, alone in the batch, with only the test goods, a small village radius, caravans that arrive at once,
	 * no hall rounds of their own, the economy on and caravan sights as given. Everything is put back at the end.
	 */
	static Two build(GameTestHelper helper, boolean sights) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		TradeGoodsDataGameTests.goodsFrom(helper, "aliveworkplace_test");
		int radius = VillageHalls.RADIUS;
		long travel = Caravans.MIN_TRAVEL;
		boolean economy = Economy.ENABLED;
		int every = VillageNeeds.CHECK_EVERY;
		boolean enabled = CaravanSights.ENABLED;
		VillageHalls.RADIUS = 8;
		Caravans.MIN_TRAVEL = 0;
		Economy.ENABLED = true;
		VillageNeeds.CHECK_EVERY = Integer.MAX_VALUE;
		CaravanSights.ENABLED = sights;
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(HALL_A);
		BlockPos b = helper.absolutePos(HALL_B);
		Caravans.Data data = Caravans.Data.get(level);
		Leftovers.after(helper, () -> {
			CaravanSights.clear(level, a);
			CaravanSights.clear(level, b);
			VillageHalls.RADIUS = radius;
			Caravans.MIN_TRAVEL = travel;
			Economy.ENABLED = economy;
			VillageNeeds.CHECK_EVERY = every;
			CaravanSights.ENABLED = enabled;
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

	/** Names both villages on the caravans' list (B waiting for {@code wants}), with treasuries and a route from A to B. */
	static void open(GameTestHelper helper, Two two, int treasuryA, int treasuryB, List<Caravans.Want> wants) {
		two.hallA().setCustomName(Component.literal("Thornholm"));
		two.hallB().setCustomName(Component.literal("Ashford"));
		two.data().setWants(two.a(), Component.literal("Thornholm"), List.of());
		two.data().setWants(two.b(), Component.literal("Ashford"), wants);
		two.hallA().setTreasury(treasuryA);
		two.hallB().setTreasury(treasuryB);
		helper.assertTrue(two.data().routesFrom(two.a()).contains(two.b()) || two.data().toggleRoute(two.a(), two.b()), "no route");
	}

	/** Today's market for {@code hall}. */
	static void market(Two two, BlockPos hall, List<ResourceLocation> knownFor, List<ResourceLocation> shortOf, Map<ResourceLocation, Integer> cents) {
		Map<ResourceLocation, Market.Price> prices = new LinkedHashMap<>();
		cents.forEach((good, c) -> prices.put(good, new Market.Price(c, c, 0)));
		two.data().setMarket(hall, new Market(knownFor, shortOf, prices, Chronicle.day(two.level()), List.of()));
	}

	/** The caravans on the road to {@code to} come in now and its hall's round unloads them. */
	static void arrive(Two two, BlockPos to) {
		two.data().hurry(to);
		Caravans.round(two.level(), to, null);
	}

	static List<String> chronicle(VillageHallBlockEntity hall) {
		return hall.chronicle().stream().map(e -> e.text().getString()).toList();
	}

	/** Every caravan party member near the test area. */
	static List<Entity> tagged(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(Entity.class, helper.getBounds().inflate(48), CaravanSights::isParty);
	}

	/** Items lying near the test area. */
	static List<String> dropped(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(48), e -> true).stream().map(e -> e.getItem().toString()).toList();
	}

	/** Logs out every player within 250 blocks of either hall (only for tests alone in their batch). */
	static void nobodyNear(Two two) {
		var list = two.level().getServer().getPlayerList();
		for (ServerPlayer p : List.copyOf(two.level().players())) {
			if (p.blockPosition().distSqr(two.a()) <= 250.0 * 250 || p.blockPosition().distSqr(two.b()) <= 250.0 * 250) {
				list.remove(p);
			}
		}
	}

	// ---------------------------------------------------------------- 33.6

	/**
	 * Three goods its village is known for, all short at the other end, and B also waiting for bread: the bread goes free
	 * and, on top of it, exactly 2 stacks to sell, never a third; the third good stays whole at home. On arrival B pays
	 * exactly what A receives, and every item is still somewhere.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaCaravanTwoStacks"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCaravanTwoStacks")
	public void qaOnlyTwoStacksToSellGoOnTopOfTheWaitedForLoad(GameTestHelper helper) {
		Two two = build(helper, false);
		helper.runAfterDelay(2, () -> {
			open(helper, two, 0, 5000, List.of(new Caravans.Want(Items.BREAD, 16)));
			market(two, two.a(), List.of(TIMBER, STONE, GRAIN), List.of(), Map.of(TIMBER, 100, STONE, 100, GRAIN, 100));
			market(two, two.b(), List.of(), List.of(TIMBER, STONE, GRAIN), Map.of(TIMBER, 100, STONE, 100, GRAIN, 100));
			two.ours().setItem(0, new ItemStack(Items.OAK_LOG, 48));
			two.ours().setItem(1, new ItemStack(Items.COBBLESTONE, 64));
			two.ours().setItem(2, new ItemStack(Items.COBBLESTONE, 64));
			two.ours().setItem(3, new ItemStack(Items.WHEAT, 64));
			two.ours().setItem(4, new ItemStack(Items.BREAD, 40));
			Caravans.round(two.level(), two.a(), null);
			List<Caravans.Shipment> out = two.road();
			helper.assertTrue(out.size() == 1 && out.get(0).sale().size() == 2, "exactly two stacks to sell should go: " + out);
			helper.assertTrue(out.get(0).goods().size() == 1 && out.get(0).goods().get(0).is(Items.BREAD) && out.get(0).goods().get(0).getCount() == 16,
				"the waited-for bread should go free beside them: " + out.get(0).goods());
			int whole = (two.ours().countItem(Items.OAK_LOG) == 48 ? 1 : 0) + (two.ours().countItem(Items.COBBLESTONE) == 128 ? 1 : 0)
				+ (two.ours().countItem(Items.WHEAT) == 64 ? 1 : 0);
			helper.assertTrue(whole == 1, "exactly one good should stay whole at home: " + two.ours().countItem(Items.OAK_LOG) + " logs, "
				+ two.ours().countItem(Items.COBBLESTONE) + " cobblestone, " + two.ours().countItem(Items.WHEAT) + " wheat");
			int stack = out.get(0).sale().stream().mapToInt(Caravans.Lot::count).max().orElse(0);
			helper.assertTrue(stack <= 64, "a lot bigger than a stack: " + stack);
		});
		helper.runAfterDelay(20, () -> {
			int logs = two.everywhere(Items.OAK_LOG);
			int stone = two.everywhere(Items.COBBLESTONE);
			int wheat = two.everywhere(Items.WHEAT);
			arrive(two, two.b());
			helper.assertTrue(two.everywhere(Items.OAK_LOG) == logs && two.everywhere(Items.COBBLESTONE) == stone && two.everywhere(Items.WHEAT) == wheat
				&& logs == 48 && stone == 128 && wheat == 64, "goods were made or lost: " + two.everywhere(Items.OAK_LOG) + " logs, "
				+ two.everywhere(Items.COBBLESTONE) + " cobblestone, " + two.everywhere(Items.WHEAT) + " wheat");
			int paid = 5000 - two.hallB().treasury();
			helper.assertTrue(paid > 0 && two.hallA().treasury() == paid, "B paid " + paid + ", A got " + two.hallA().treasury());
			helper.assertTrue(two.theirs().countItem(Items.BREAD) == 16, "the bread: " + two.theirs().countItem(Items.BREAD));
			helper.succeed();
		});
	}

	/**
	 * The 10% boundary at uneven prices, B not short of timber: A sells at 1.33, so 1.46 (9.8% more) isn't enough and no
	 * caravan goes at all; 1.47 (10.5% more) is, and the timber goes.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaCaravanTenPercent"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCaravanTenPercent")
	public void qaTenPercentMoreAtUnevenPrices(GameTestHelper helper) {
		Two two = build(helper, false);
		helper.runAfterDelay(2, () -> {
			open(helper, two, 0, 5000, List.of());
			two.ours().setItem(0, new ItemStack(Items.OAK_LOG, 48));
			market(two, two.a(), List.of(TIMBER), List.of(), Map.of(TIMBER, 133));
			market(two, two.b(), List.of(), List.of(), Map.of(TIMBER, 146));
			Caravans.round(two.level(), two.a(), null);
			helper.assertTrue(two.ours().countItem(Items.OAK_LOG) == 48 && two.road().isEmpty(), "9.8% more shouldn't take the timber: "
				+ two.ours().countItem(Items.OAK_LOG) + " logs left, " + two.road());
			market(two, two.b(), List.of(), List.of(), Map.of(TIMBER, 147));
			two.data().sent(two.a(), -1);
			Caravans.round(two.level(), two.a(), null);
			helper.assertTrue(two.ours().countItem(Items.OAK_LOG) == 16 && two.road().size() == 1 && two.road().get(0).sale().size() == 1,
				"10.5% more should take the timber: " + two.ours().countItem(Items.OAK_LOG) + " logs left, " + two.road());
			helper.succeed();
		});
	}

	/**
	 * A good the other village is short of goes even where it pays LESS than at home (short of, OR 10% more), and is paid
	 * at the receiver's board price: 0.8 then 0.78, not A's 1.5. B's price goes down 2% a bundle (to 0.76), A's up 2% a
	 * bundle (1.5 to 1.53 to 1.56), and the chronicles name the sum.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaCaravanReceiversPrice"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCaravanReceiversPrice")
	public void qaShortOfGoesEvenCheaperAndIsPaidAtTheReceiversPrice(GameTestHelper helper) {
		Two two = build(helper, false);
		helper.runAfterDelay(2, () -> {
			open(helper, two, 0, 1000, List.of());
			two.ours().setItem(0, new ItemStack(Items.OAK_LOG, 48));
			market(two, two.a(), List.of(TIMBER), List.of(), Map.of(TIMBER, 150));
			market(two, two.b(), List.of(), List.of(TIMBER), Map.of(TIMBER, 80));
			Caravans.round(two.level(), two.a(), null);
			helper.assertTrue(two.ours().countItem(Items.OAK_LOG) == 16, "short of it there, the timber should go: " + two.ours().countItem(Items.OAK_LOG));
		});
		helper.runAfterDelay(20, () -> {
			arrive(two, two.b());
			helper.assertTrue(two.theirs().countItem(Items.OAK_LOG) == 32, "arrived: " + two.theirs().countItem(Items.OAK_LOG));
			helper.assertTrue(two.hallB().treasury() == 1000 - 158 && two.hallA().treasury() == 158,
				"B should pay 0.8 + 0.78 at its own price: B " + two.hallB().treasury() + ", A " + two.hallA().treasury());
			helper.assertTrue(two.priceB(TIMBER).cents() == 76 && two.priceA(TIMBER).cents() == 156, "prices: B " + two.priceB(TIMBER) + ", A " + two.priceA(TIMBER));
			helper.assertTrue(chronicle(two.hallA()).contains("Sold 32 Test Timber to Ashford for 1.58 emeralds"), "A's chronicle: " + chronicle(two.hallA()));
			helper.assertTrue(chronicle(two.hallB()).contains("Bought 32 Test Timber from Thornholm for 1.58 emeralds"), "B's chronicle: " + chronicle(two.hallB()));
			helper.succeed();
		});
	}

	/**
	 * A partial payment, counted everywhere: 4 bundles go, B can pay for 2 (1.3 + 1.27 out of 2.6); after the arrival,
	 * after a second round at B (no payment twice), on the way home and back in A's Storehouse, the logs (80) and the
	 * emeralds (2.6 between both treasuries) are all still there, never more.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaCaravanConserved"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCaravanConserved")
	public void qaAPartialPaymentLosesAndMakesNothing(GameTestHelper helper) {
		Two two = build(helper, false);
		helper.runAfterDelay(2, () -> {
			open(helper, two, 0, 260, List.of());
			market(two, two.a(), List.of(TIMBER), List.of(), Map.of(TIMBER, 100));
			market(two, two.b(), List.of(), List.of(TIMBER), Map.of(TIMBER, 130));
			two.ours().setItem(0, new ItemStack(Items.OAK_LOG, 64));
			two.ours().setItem(1, new ItemStack(Items.OAK_LOG, 16));
			Caravans.round(two.level(), two.a(), null);
			helper.assertTrue(two.ours().countItem(Items.OAK_LOG) == 16 && two.everywhere(Items.OAK_LOG) == 80 && two.money() == 260,
				"loaded: " + two.ours().countItem(Items.OAK_LOG) + " at home, " + two.everywhere(Items.OAK_LOG) + " in all, money " + two.money());
		});
		helper.runAfterDelay(20, () -> {
			arrive(two, two.b());
			helper.assertTrue(two.theirs().countItem(Items.OAK_LOG) == 32 && two.hallB().treasury() == 3 && two.hallA().treasury() == 257,
				"B should buy 2 bundles: " + two.theirs().countItem(Items.OAK_LOG) + " logs, B " + two.hallB().treasury() + ", A " + two.hallA().treasury());
			helper.assertTrue(two.everywhere(Items.OAK_LOG) == 80 && two.money() == 260, "after the arrival: " + two.everywhere(Items.OAK_LOG) + " logs, money " + two.money());
			// B's round again: nothing is paid or unloaded twice
			two.data().hurry(two.b());
			Caravans.round(two.level(), two.b(), null);
			helper.assertTrue(two.theirs().countItem(Items.OAK_LOG) == 32 && two.hallB().treasury() == 3 && two.hallA().treasury() == 257
				&& two.everywhere(Items.OAK_LOG) == 80, "a second round at B: " + two.theirs().countItem(Items.OAK_LOG) + " logs, B " + two.hallB().treasury());
			List<Caravans.Shipment> home = two.road().stream().filter(s -> s.to().equals(two.a())).toList();
			helper.assertTrue(home.size() == 1 && home.get(0).back() && home.get(0).goods().stream().mapToInt(ItemStack::getCount).sum() == 32,
				"the two unpaid bundles should be on their way home: " + two.road());
		});
		helper.runAfterDelay(60, () -> {
			arrive(two, two.a());
			helper.assertTrue(two.ours().countItem(Items.OAK_LOG) == 48 && two.theirs().countItem(Items.OAK_LOG) == 32 && two.road().isEmpty(),
				"home: " + two.ours().countItem(Items.OAK_LOG) + " logs, road " + two.road());
			helper.assertTrue(two.everywhere(Items.OAK_LOG) == 80 && two.money() == 260 && two.hallA().treasury() == 257, "at the end: "
				+ two.everywhere(Items.OAK_LOG) + " logs, A " + two.hallA().treasury() + ", B " + two.hallB().treasury());
			helper.succeed();
		});
	}

	/**
	 * The free waited-for load goes first: B waits for 32 oak logs and is also short of timber. A has 48 (16 kept back),
	 * so the 32 go free and nothing is left to sell; on arrival B's treasury pays nothing for them and no price moves.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaCaravanWaitedFirst"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCaravanWaitedFirst")
	public void qaTheWaitedForLoadTakesTheGoodsFirstAndIsFree(GameTestHelper helper) {
		Two two = build(helper, false);
		helper.runAfterDelay(2, () -> {
			open(helper, two, 0, 1000, List.of(new Caravans.Want(Items.OAK_LOG, 32)));
			market(two, two.a(), List.of(TIMBER), List.of(), Map.of(TIMBER, 100));
			market(two, two.b(), List.of(), List.of(TIMBER), Map.of(TIMBER, 130));
			two.ours().setItem(0, new ItemStack(Items.OAK_LOG, 48));
			Caravans.round(two.level(), two.a(), null);
			List<Caravans.Shipment> out = two.road();
			helper.assertTrue(two.ours().countItem(Items.OAK_LOG) == 16 && out.size() == 1 && out.get(0).sale().isEmpty()
				&& out.get(0).goods().stream().mapToInt(ItemStack::getCount).sum() == 32, "the 32 should go free, nothing for sale: " + out);
		});
		helper.runAfterDelay(20, () -> {
			arrive(two, two.b());
			helper.assertTrue(two.theirs().countItem(Items.OAK_LOG) == 32 && two.hallB().treasury() == 1000, "B paid for what it waited for: "
				+ two.hallB().treasury());
			helper.assertTrue(two.priceB(TIMBER).cents() == 130 && two.priceA(TIMBER).cents() == 100, "prices moved: " + two.priceB(TIMBER) + " " + two.priceA(TIMBER));
			helper.assertTrue(chronicle(two.hallA()).stream().noneMatch(l -> l.startsWith("Sold")), "A's chronicle sold: " + chronicle(two.hallA()));
			helper.succeed();
		});
	}

	/**
	 * Every new sentence with real arguments, at exactly 1 emerald: the Routes tab's "Test Timber ×1 for 1 emerald" and the
	 * chronicles' "Sold 16 Test Timber to Ashford for 1 emerald" / "Bought 16 Test Timber from Thornholm for 1 emerald"
	 * (singular, never "1 emeralds" or a raw key).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaCaravanOneEmerald"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCaravanOneEmerald")
	public void qaTheSentencesReadRightAtOneEmerald(GameTestHelper helper) {
		Two two = build(helper, false);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			open(helper, two, 0, 1000, List.of());
			market(two, two.a(), List.of(TIMBER), List.of(), Map.of(TIMBER, 90));
			market(two, two.b(), List.of(), List.of(TIMBER), Map.of(TIMBER, 100));
			two.ours().setItem(0, new ItemStack(Items.OAK_LOG, 32));
			ChoiceMenu menu = TradePage.forTest(player, two.a(), TradePage.Tab.ROUTES);
			List<String> lines = BoardTradeGameTests.lore(menu.icon(BoardTradeGameTests.slot(helper, menu, "Ashford")));
			helper.assertTrue(lines.contains("Test Timber ×1 for 1 emerald"), "the Routes tab: " + lines);
			helper.assertTrue(lines.stream().noneMatch(l -> l.contains("aliveworkplace.") || l.contains("1 emeralds")), "a raw key or plural: " + lines);
			Caravans.round(two.level(), two.a(), null);
		});
		helper.runAfterDelay(20, () -> {
			arrive(two, two.b());
			helper.assertTrue(chronicle(two.hallA()).contains("Sold 16 Test Timber to Ashford for 1 emerald"), "A's chronicle: " + chronicle(two.hallA()));
			helper.assertTrue(chronicle(two.hallB()).contains("Bought 16 Test Timber from Thornholm for 1 emerald"), "B's chronicle: " + chronicle(two.hallB()));
			helper.assertTrue(two.hallA().treasury() == 100 && two.hallB().treasury() == 900, "A " + two.hallA().treasury() + ", B " + two.hallB().treasury());
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- 33.7

	/**
	 * "Within 96 blocks": a player exactly 96 blocks from the hall sees the caravan leave; at 97 nobody walks. The caravan travels the saved way each time.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaCaravanSight96"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCaravanSight96")
	public void qaSeenAtNinetySixBlocksNotAtNinetySeven(GameTestHelper helper) {
		Two two = build(helper, true);
		nobodyNear(two);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			// west of Thornholm (further still from Ashford), at its hall's height
			open(helper, two, 0, 0, List.of(new Caravans.Want(Items.BREAD, 8)));
			two.ours().setItem(0, new ItemStack(Items.BREAD, 64));
			player.moveTo(two.a().getX() - 97 + 0.5, two.a().getY(), two.a().getZ() + 0.5, 0f, 0f);
			Caravans.round(two.level(), two.a(), null);
			helper.assertTrue(two.ours().countItem(Items.BREAD) == 56, "the first caravan should leave: " + two.ours().countItem(Items.BREAD));
			helper.assertTrue(CaravanSights.party(two.level(), two.a()) == null && tagged(helper).isEmpty(), "seen from 97 blocks: " + tagged(helper));

			player.moveTo(two.a().getX() - 96 + 0.5, two.a().getY(), two.a().getZ() + 0.5, 0f, 0f);
			two.data().sent(two.a(), -1);
			Caravans.round(two.level(), two.a(), null);
			helper.assertTrue(two.ours().countItem(Items.BREAD) == 48, "the second caravan should leave: " + two.ours().countItem(Items.BREAD));
			helper.assertTrue(CaravanSights.party(two.level(), two.a()) != null && tagged(helper).size() == 3, "not seen from 96 blocks: " + tagged(helper));
			helper.succeed();
		});
	}

	/** One trading run (timber to sell, B able to pay for one bundle of three): what it leaves everywhere, step by step. */
	private static String tradeRun(GameTestHelper helper, Two two) {
		two.ours().clearContent();
		two.theirs().clearContent();
		two.data().clearRoad(two.a());
		two.data().clearRoad(two.b());
		two.data().sent(two.a(), -1);
		open(helper, two, 0, 200, List.of(new Caravans.Want(Items.BREAD, 16)));
		market(two, two.a(), List.of(TIMBER), List.of(), Map.of(TIMBER, 100));
		market(two, two.b(), List.of(), List.of(TIMBER), Map.of(TIMBER, 130));
		two.ours().setItem(0, new ItemStack(Items.OAK_LOG, 64));
		two.ours().setItem(1, new ItemStack(Items.BREAD, 40));
		Caravans.round(two.level(), two.a(), null);
		CaravanSights.clear(two.level(), two.a()); // the one leaving: so the one coming home later can be seen (one a village)
		String out = "left: " + two.ours().countItem(Items.OAK_LOG) + " logs " + two.ours().countItem(Items.BREAD) + " bread";
		arrive(two, two.b());
		String there = "; B has " + two.theirs().countItem(Items.OAK_LOG) + " logs " + two.theirs().countItem(Items.BREAD) + " bread, treasuries A "
			+ two.hallA().treasury() + " B " + two.hallB().treasury() + ", prices A " + two.priceA(TIMBER) + " B " + two.priceB(TIMBER);
		arrive(two, two.a());
		return out + there + "; home: " + two.ours().countItem(Items.OAK_LOG) + " logs; road " + two.road().size();
	}

	/**
	 * The goods that arrive are the same with the switch on or off, also when the caravan sells, is only partly paid and
	 * brings the rest home (the existing test runs only a free load). With sights on and a player there, a party is seen
	 * in Ashford, and the party coming home with the unsold timber in Thornholm is Thornholm's own ("Thornholm's
	 * caravan"), not Ashford's.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaCaravanSightTrade"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCaravanSightTrade")
	public void qaATradingRunIsTheSameSeenOrNotAndComesHomeAsItsOwn(GameTestHelper helper) {
		Two two = build(helper, true);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		BlockPos stand = helper.absolutePos(new BlockPos(10, 2, 10));
		player.moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0f, 0f);
		helper.runAfterDelay(2, () -> {
			String on = tradeRun(helper, two);
			CaravanSights.Party home = CaravanSights.party(two.level(), two.a());
			helper.assertTrue(CaravanSights.party(two.level(), two.b()) != null, "no party was seen in Ashford");
			helper.assertTrue(home != null && home.phase() == CaravanSights.Phase.ARRIVING, "the caravan coming home wasn't seen: " + (home == null ? null : home.phase()));
			String name = home.carter().getCustomName() == null ? null : home.carter().getCustomName().getString();
			helper.assertTrue("Thornholm's caravan".equals(name), "the caravan coming home should be Thornholm's own: " + name);
			CaravanSights.clear(two.level(), two.a());
			CaravanSights.clear(two.level(), two.b());

			CaravanSights.ENABLED = false;
			String off = tradeRun(helper, two);
			helper.assertTrue(tagged(helper).isEmpty(), "a party with the switch off: " + tagged(helper));
			helper.assertTrue(on.equals(off), "the goods differ: on {" + on + "}, off {" + off + "}");
			helper.assertTrue(on.contains("B has 16 logs 16 bread") && on.contains("home: 48 logs"), "the run: " + on);
			helper.succeed();
		});
	}

	/** What a client sends when its player right-clicks {@code mob} with {@code held}. */
	private static void rightClick(ServerPlayer player, Mob mob, ItemStack held) {
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		player.moveTo(mob.getX() + 1.5, mob.getY(), mob.getZ(), 90f, 0f);
		player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(mob, false, InteractionHand.MAIN_HAND, new Vec3(0, mob.getBbHeight() / 2, 0)));
		player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(mob, false, InteractionHand.MAIN_HAND));
	}

	/**
	 * A player can't take the party: a lead on a llama leaves it on the carter's lead (and the lead in hand), a name tag
	 * on the carter or a llama is kept and changes no name, and the party is still taken away when its walk is done.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaCaravanSightLead"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaCaravanSightLead")
	public void qaALeadOrANameTagDoesNotTakeTheParty(GameTestHelper helper) {
		Two two = build(helper, true);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		BlockPos stand = helper.absolutePos(new BlockPos(10, 2, 3));
		player.moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0f, 0f);
		CaravanSights.Party[] seen = new CaravanSights.Party[1];
		helper.runAfterDelay(2, () -> {
			open(helper, two, 0, 0, List.of(new Caravans.Want(Items.BREAD, 16)));
			two.ours().setItem(0, new ItemStack(Items.BREAD, 40));
			Caravans.round(two.level(), two.a(), null);
			CaravanSights.Party party = CaravanSights.party(two.level(), two.a());
			helper.assertTrue(party != null, "no party");
			seen[0] = party;
			Llama llama = party.llamas().get(0);
			rightClick(player, llama, new ItemStack(Items.LEAD));
			helper.assertTrue(llama.getLeashHolder() == party.carter(), "a player's lead took the llama: " + llama.getLeashHolder());
			helper.assertTrue(player.getMainHandItem().is(Items.LEAD) && player.getMainHandItem().getCount() == 1, "the lead was used: " + player.getMainHandItem());
			ItemStack tag = new ItemStack(Items.NAME_TAG);
			tag.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Mine"));
			rightClick(player, party.carter(), tag);
			helper.assertTrue(party.carter().getCustomName().getString().equals("Thornholm's caravan") && player.getMainHandItem().is(Items.NAME_TAG),
				"a name tag renamed the carter: " + party.carter().getCustomName().getString());
			ItemStack tag2 = new ItemStack(Items.NAME_TAG);
			tag2.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Mine"));
			rightClick(player, llama, tag2);
			helper.assertTrue(llama.getCustomName() == null && player.getMainHandItem().is(Items.NAME_TAG), "a name tag named the llama: " + llama.getCustomName());
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			player.moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0f, 0f);
		});
		helper.runAfterDelay(4, () -> helper.succeedWhen(() -> {
			helper.assertTrue(CaravanSights.party(two.level(), two.a()) == null && seen[0].llamas().stream().allMatch(Entity::isRemoved)
				&& seen[0].carter().isRemoved() && tagged(helper).isEmpty(), "the party is still out: " + tagged(helper));
			helper.assertTrue(dropped(helper).isEmpty(), "left on the ground: " + dropped(helper));
		}));
	}
}
