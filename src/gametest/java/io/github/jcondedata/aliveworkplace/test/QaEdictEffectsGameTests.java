package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.MarketDays;
import io.github.jcondedata.aliveworkplace.hall.Reforms;
import io.github.jcondedata.aliveworkplace.hall.VillageGrowth;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.inn.Innkeepers;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.people.Traits;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-c-1025-1853) for the edict effects of ROADMAP 30.6 (Free Bread, Large Families), 30.7 (Open Gates) and 30.8
 * (Festival Season, Tithe), written from the spec: the boundaries the builder's tests ({@code FamilyEdictGameTests},
 * {@code OpenGatesGameTests}, {@code TreasuryEdictGameTests}) leave out, from both sides (the last tick of "fed in the
 * last day", the meal that makes a whole extra one, the fourth villager of a festival's cost, a treasury one hundredth
 * short, half an emerald of tithe), the village's edge (a villager just outside isn't reached), an edict lifted, the
 * players' messages, and Legends' visits under Open Gates. Nothing here is random.
 */
public class QaEdictEffectsGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final BlockPos CHEST = new BlockPos(17, 2, 19);
	private static final BlockPos FAR = new BlockPos(1, 2, 1); // about 14 blocks from the hall
	private static final ResourceLocation FREE_BREAD = AliveWorkplace.id("free_bread");
	private static final ResourceLocation LARGE_FAMILIES = AliveWorkplace.id("large_families");
	private static final ResourceLocation OPEN_GATES = AliveWorkplace.id("open_gates");
	private static final ResourceLocation FESTIVAL_SEASON = AliveWorkplace.id("festival_season");
	private static final ResourceLocation TITHE = AliveWorkplace.id("tithe");

	// --- 30.6 Free Bread and Large Families ------------------------------------------------------------------------

	/**
	 * "Everyone fed in the last day is 10 happier": a villager whose meal was one tick less than a day ago gets "free
	 * bread", one whose meal was a full day ago doesn't, and one who never ate doesn't.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaFreeBreadDay"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaFreeBreadDay")
	public void qaFreeBreadReachesOnlyThoseFedInTheLastDay(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper, 16);
		moodsOn(helper);
		noSickness(helper);
		Villager fed = still(helper, new BlockPos(4, 2, 4));
		Villager late = still(helper, new BlockPos(6, 2, 4));
		Villager never = still(helper, new BlockPos(8, 2, 4));
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			ready(helper);
			helper.assertTrue(Edicts.proclaim(level, hall, null, Edicts.get(FREE_BREAD).orElseThrow()).done(), "proclaimed");
			long now = level.getGameTime();
			ModAttachments.LAST_MEAL.set(fed, now - Traits.mealEvery(fed, VillageNeeds.DAY) + 1);
			ModAttachments.LAST_MEAL.set(late, now - Traits.mealEvery(late, VillageNeeds.DAY));
			ModAttachments.LAST_MEAL.remove(never);
			Moods.forget();
			helper.assertTrue(freeBread(level, fed), "fed one tick inside the day: no free bread " + good(level, fed));
			helper.assertTrue(!freeBread(level, late), "fed a full day ago: free bread " + good(level, late));
			helper.assertTrue(!freeBread(level, never), "never fed: free bread " + good(level, never));
			helper.succeed();
		});
	}

	/**
	 * 0.3 of a meal more for every meal eaten: three meals take three (0.9 kept), the fourth meal makes 1.2 and takes two
	 * (0.2 kept). Lifted: one eaten, one taken, the 0.2 left alone.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaFreeBreadWhole"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaFreeBreadWhole")
	public void qaFreeBreadTakesTheExtraMealOnTheMealThatMakesAWhole(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper, 16);
		noSickness(helper);
		helper.setBlock(new BlockPos(17, 2, 17), ModBlocks.STOREHOUSE);
		helper.setBlock(CHEST, Blocks.CHEST);
		List<Villager> villagers = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			villagers.add(still(helper, new BlockPos(3 + 2 * i, 2, 4)));
		}
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper);
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(Edicts.proclaim(level, hall, null, Edicts.get(FREE_BREAD).orElseThrow()).done(), "proclaimed");
			int three = taken(level, hall, chest, villagers);
			helper.assertTrue(three == 3 && entity.extraMeals() == 90, "3 eaten: " + three + " taken, " + entity.extraMeals() + " hundredths kept");
			int fourth = taken(level, hall, chest, villagers.subList(0, 1));
			helper.assertTrue(fourth == 2 && entity.extraMeals() == 20, "the 4th meal: " + fourth + " taken, " + entity.extraMeals() + " hundredths kept");
			// Lifted (after the three days an edict must stand): the usual meal, nothing counted.
			entity.setEdicts(List.of(new Edicts.InForce(FREE_BREAD.toString(), Chronicle.day(level) - Edicts.MIN_DAYS)));
			helper.assertTrue(Edicts.lift(level, hall, null, FREE_BREAD.toString()).done(), "lifted");
			int lifted = taken(level, hall, chest, villagers.subList(0, 1));
			helper.assertTrue(lifted == 1 && entity.extraMeals() == 20, "lifted: " + lifted + " taken, " + entity.extraMeals());
			helper.succeed();
		});
	}

	/**
	 * Large Families reaches the village only: a villager in it falls ill half again as often and the hall's births come
	 * every half day; one just outside the village keeps the usual chance. Lifted: the usual day between births, 16
	 * meals, the usual chance.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaLargeFamiliesEdge"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLargeFamiliesEdge")
	public void qaLargeFamiliesReachOnlyTheVillageAndEndWhenLifted(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper, 8);
		Villager inside = still(helper, new BlockPos(8, 2, 8));
		Villager outside = still(helper, FAR);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper);
			entity.setRank(VillageRanks.Rank.VILLAGE);
			helper.assertTrue(VillageHalls.nearest(level, outside.blockPosition()).isEmpty(), "the far villager is in the village");
			long now = level.getGameTime();
			ModAttachments.LAST_MEAL.set(inside, now);
			ModAttachments.LAST_MEAL.set(outside, now);
			float in = Sickness.dailyChance(level, inside);
			float out = Sickness.dailyChance(level, outside);
			helper.assertTrue(Edicts.proclaim(level, hall, null, Edicts.get(LARGE_FAMILIES).orElseThrow()).done(), "proclaimed");
			CivicEffects.forget();
			helper.assertTrue(Math.abs(Sickness.dailyChance(level, inside) - in * 1.5f) < 1e-6, "inside: " + in + " -> " + Sickness.dailyChance(level, inside));
			helper.assertTrue(Math.abs(Sickness.dailyChance(level, outside) - out) < 1e-6, "outside: " + out + " -> " + Sickness.dailyChance(level, outside));
			helper.assertTrue(VillageGrowth.every(level, hall) == 12000, "births every " + VillageGrowth.every(level, hall));

			entity.setEdicts(List.of(new Edicts.InForce(LARGE_FAMILIES.toString(), Chronicle.day(level) - Edicts.MIN_DAYS)));
			helper.assertTrue(Edicts.lift(level, hall, null, LARGE_FAMILIES.toString()).done(), "lifted");
			CivicEffects.forget();
			helper.assertTrue(VillageGrowth.every(level, hall) == 24000 && VillageGrowth.foodNeeded(level, hall) == 16
				&& VillageGrowth.familyMeals(level, hall) == 8, "lifted: " + VillageGrowth.every(level, hall) + ", " + VillageGrowth.foodNeeded(level, hall)
				+ ", " + VillageGrowth.familyMeals(level, hall));
			helper.assertTrue(Math.abs(Sickness.dailyChance(level, inside) - in) < 1e-6, "lifted, sickness: " + Sickness.dailyChance(level, inside));
			helper.succeed();
		});
	}

	// --- 30.7 Open Gates ---------------------------------------------------------------------------------------------

	/**
	 * Legends visit the inn twice as often under Open Gates now that M29's inn visitors exist, and still twice as often
	 * once The Watchful Gate reforms it ("the travellers keep coming"); with edicts off and once lifted, as usual.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaOpenGatesLegends"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaOpenGatesLegends")
	public void qaOpenGatesBringsLegendsTwiceAsOftenEvenReformed(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper, 16);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper);
			helper.assertTrue(Legends.visitFactor(level, hall) == 1f, "usual: " + Legends.visitFactor(level, hall));
			helper.assertTrue(Edicts.proclaim(level, hall, null, Edicts.get(OPEN_GATES).orElseThrow()).done(), "proclaimed");
			helper.assertTrue(Legends.visitFactor(level, hall) == 2f, "Open Gates: " + Legends.visitFactor(level, hall));
			entity.setReforms(List.of(new Reforms.Progress(OPEN_GATES.toString(), 3, true, 0)));
			helper.assertTrue(Legends.visitFactor(level, hall) == 2f, "reformed: " + Legends.visitFactor(level, hall));
			try {
				Edicts.setEnabled(false);
				helper.assertTrue(Legends.visitFactor(level, hall) == 1f, "off: " + Legends.visitFactor(level, hall));
			} finally {
				Edicts.setEnabled(true);
			}
			entity.setReforms(List.of());
			entity.setEdicts(List.of(new Edicts.InForce(OPEN_GATES.toString(), Chronicle.day(level) - Edicts.MIN_DAYS)));
			helper.assertTrue(Edicts.lift(level, hall, null, OPEN_GATES.toString()).done(), "lifted");
			helper.assertTrue(Legends.visitFactor(level, hall) == 1f && Math.abs(BanditCamps.dailyChance(level, hall) - BanditCamps.DAILY_CHANCE) < 1e-6,
				"lifted: legends " + Legends.visitFactor(level, hall) + ", bandits " + BanditCamps.dailyChance(level, hall));
			helper.succeed();
		});
	}

	/**
	 * "Inns in the village take 4 guests": an inn just outside the village's radius keeps the usual two guests and one
	 * traveller a morning, while one inside takes four and two. The market's extra trader is also the village's.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaOpenGatesEdge"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaOpenGatesEdge")
	public void qaOpenGatesReachesOnlyInnsInTheVillage(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper, 8);
		Villager inside = still(helper, new BlockPos(8, 2, 8));
		Villager outside = still(helper, FAR);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper);
			entity.setRank(VillageRanks.Rank.VILLAGE);
			helper.assertTrue(VillageHalls.nearest(level, outside.blockPosition()).isEmpty(), "the far inn is in the village");
			int traders = MarketDays.traders(level, hall);
			helper.assertTrue(Edicts.proclaim(level, hall, null, Edicts.get(OPEN_GATES).orElseThrow()).done(), "proclaimed");
			CivicEffects.forget();
			helper.assertTrue(Innkeepers.maxGuests(inside) == 4 && Innkeepers.arrivalsPerMorning(inside) == 2,
				"inside: " + Innkeepers.maxGuests(inside) + ", " + Innkeepers.arrivalsPerMorning(inside));
			helper.assertTrue(Innkeepers.maxGuests(outside) == Innkeepers.MAX_GUESTS && Innkeepers.arrivalsPerMorning(outside) == 1,
				"outside: " + Innkeepers.maxGuests(outside) + ", " + Innkeepers.arrivalsPerMorning(outside));
			helper.assertTrue(MarketDays.traders(level, hall) == traders + 1, "market: " + traders + " -> " + MarketDays.traders(level, hall));
			helper.succeed();
		});
	}

	// --- 30.8 Festival Season and Tithe ------------------------------------------------------------------------------

	/**
	 * "3 emeralds and 1 more for every 4 villagers": 3 for none and for 3, 4 from the 4th to the 7th, 5 from the 8th, 6
	 * from the 12th. Reformed: free. Edicts off: free and every 8 days.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaFestivalCostSteps"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaFestivalCostSteps")
	public void qaFestivalCostStepsUpAtEachFourthVillager(GameTestHelper helper) {
		Leftovers.clear(helper);
		festivalVillage(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(5, () -> {
			VillageHallBlockEntity entity = festivalReady(helper);
			helper.assertTrue(Festivals.cost(entity, 8) == 0, "without the edict a festival costs " + Festivals.cost(entity, 8));
			entity.setEdicts(List.of(new Edicts.InForce(FESTIVAL_SEASON.toString(), 1)));
			int[] want = {3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 6};
			for (int n = 0; n < want.length; n++) {
				helper.assertTrue(Festivals.cost(entity, n) == want[n], n + " villagers: " + Festivals.cost(entity, n) + ", expected " + want[n]);
			}
			entity.setReforms(List.of(new Reforms.Progress(FESTIVAL_SEASON.toString(), 3, true, 0)));
			helper.assertTrue(Festivals.cost(entity, 12) == 0 && Festivals.every(entity) == 4, "reformed: " + Festivals.cost(entity, 12) + ", every " + Festivals.every(entity));
			entity.setReforms(List.of());
			try {
				Edicts.setEnabled(false);
				helper.assertTrue(Festivals.cost(entity, 12) == 0 && Festivals.every(entity) == Festivals.EVERY_DAYS,
					"off: " + Festivals.cost(entity, 12) + ", every " + Festivals.every(entity));
			} finally {
				Edicts.setEnabled(true);
			}
			helper.succeed();
		});
	}

	/**
	 * A treasury one hundredth short (4.99 of 5) can't pay: no festival, the players near the hall are told why. Four
	 * days later, with exactly 5.00: the festival is held, the treasury is empty, and the players are told what it paid.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaFestivalExact"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaFestivalExact")
	public void qaAFestivalIsHeldWithExactlyItsCostAndNotOneHundredthLess(GameTestHelper helper) {
		Leftovers.clear(helper);
		festivalVillage(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = festivalReady(helper);
			QaStageFiveGameTests.Client client = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("QaFest"));
			client.player().moveTo(hall.getX() + 2.5, hall.getY(), hall.getZ() + 0.5);
			String name = VillageHalls.name(level, hall).getString();
			long day = seasonDay(level, hall, entity);
			entity.setTreasury(499);
			morning(level, day);
			client.seen().clear();
			Festivals.round(level, hall, entity, 8);
			helper.assertTrue(entity.festivalDay() < day && entity.festivalMissed() == day && entity.treasury() == 499,
				"4.99: festival " + entity.festivalDay() + ", missed " + entity.festivalMissed() + ", treasury " + entity.treasury());
			String noMoney = "No festival in " + name + " today: the treasury hasn't the 5 emeralds it costs. The villagers are disappointed.";
			helper.assertTrue(said(client).contains(noMoney), "told: " + said(client));

			long next = day + 4;
			entity.setTreasury(500);
			morning(level, next);
			client.seen().clear();
			Festivals.round(level, hall, entity, 8);
			helper.assertTrue(entity.festivalDay() == next && entity.treasury() == 0 && entity.festivalMissed() == day,
				"5.00: festival " + entity.festivalDay() + " (wanted " + next + "), treasury " + entity.treasury() + ", missed " + entity.festivalMissed());
			String paid = "It's festival day in " + name + "! The treasury paid 5 emeralds for it. Come to the bell after work: a feast, music, and fireworks at dusk.";
			helper.assertTrue(said(client).contains(paid), "told: " + said(client));
			helper.succeed();
		});
	}

	/**
	 * Festival Season lifted: festivals go back to every 8 days and cost nothing, and an empty treasury disappoints
	 * nobody on what would have been a 4-day festival day.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaFestivalLifted"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaFestivalLifted")
	public void qaFestivalSeasonLiftedGoesBackToEveryEightDaysFree(GameTestHelper helper) {
		Leftovers.clear(helper);
		festivalVillage(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = festivalReady(helper);
			long day = seasonDay(level, hall, entity);
			helper.assertTrue(Festivals.every(entity) == 4, "in force: every " + Festivals.every(entity));
			entity.setEdicts(List.of(new Edicts.InForce(FESTIVAL_SEASON.toString(), Chronicle.day(level) - Edicts.MIN_DAYS)));
			helper.assertTrue(Edicts.lift(level, hall, null, FESTIVAL_SEASON.toString()).done(), "lifted");
			helper.assertTrue(Festivals.every(entity) == Festivals.EVERY_DAYS && Festivals.cost(entity, 8) == 0,
				"lifted: every " + Festivals.every(entity) + ", costs " + Festivals.cost(entity, 8));
			long eight = Festivals.nextDay(level, hall, entity);
			// Every day up to the next 8-day festival: none before it, nobody disappointed, nothing taken.
			for (long d = day; d < eight; d++) {
				morning(level, d);
				Festivals.round(level, hall, entity, 8);
				helper.assertTrue(entity.festivalDay() != d && entity.festivalMissed() != d, "day " + d + " before the 8-day one at " + eight
					+ ": festival " + entity.festivalDay() + ", missed " + entity.festivalMissed());
			}
			morning(level, eight);
			Festivals.round(level, hall, entity, 8);
			helper.assertTrue(entity.festivalDay() == eight && entity.treasury() == 0 && entity.festivalMissed() != eight,
				"the 8-day festival: " + entity.festivalDay() + ", treasury " + entity.treasury());
			helper.succeed();
		});
	}

	/**
	 * "10% higher (rounded, so trades under 5 emeralds don't change)": 5 emeralds become 6, 15 become 17 (16.5 rounded),
	 * 1 stays 1; the 6 paid put 0.60 in the treasury. Reformed: the 5-emerald trade costs 5 and puts in 0.50.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaTitheRounding"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaTitheRounding")
	public void qaTitheRoundsHalfAnEmeraldUp(GameTestHelper helper) {
		Leftovers.clear(helper);
		festivalVillage(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(5, () -> {
			VillageHallBlockEntity entity = festivalReady(helper);
			entity.setEdicts(List.of(new Edicts.InForce(TITHE.toString(), 1)));
			Villager trader = librarian(helper, new BlockPos(8, 2, 8), 5, 15, 1);
			ServerPlayer player = player(helper, new BlockPos(9, 2, 8));
			MerchantMenu menu = trade(helper, trader, player);
			int[] got = {menu.getOffers().get(0).getCostA().getCount(), menu.getOffers().get(1).getCostA().getCount(), menu.getOffers().get(2).getCostA().getCount()};
			helper.assertTrue(got[0] == 6 && got[1] == 17 && got[2] == 1, "5, 15, 1 became " + got[0] + ", " + got[1] + ", " + got[2]);
			buy(helper, menu, player, 0, 6);
			helper.assertTrue(entity.treasury() == 60, "6 paid, the treasury got " + entity.treasury() + " hundredths");
			player.closeContainer();

			entity.setReforms(List.of(new Reforms.Progress(TITHE.toString(), 3, true, 0)));
			CivicEffects.forget();
			menu = trade(helper, trader, player);
			helper.assertTrue(menu.getOffers().get(0).getCostA().getCount() == 5, "reformed, 5 became " + menu.getOffers().get(0).getCostA().getCount());
			buy(helper, menu, player, 0, 5);
			helper.assertTrue(entity.treasury() == 110, "reformed, 5 paid: treasury " + entity.treasury() + " hundredths, expected 110");
			player.closeContainer();
			helper.succeed();
		});
	}

	/**
	 * The Tithe is the village's: a librarian just outside the village's radius keeps his price and fills no treasury;
	 * inside, at the cap, the price is up and the treasury stays at its cap; lifted, the price is back.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaTitheEdge"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaTitheEdge")
	public void qaTitheReachesOnlyTheVillagesVillagersAndStopsAtTheCap(GameTestHelper helper) {
		Leftovers.clear(helper);
		festivalVillage(helper);
		VillageHalls.RADIUS = 8;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = festivalReady(helper);
			entity.setEdicts(List.of(new Edicts.InForce(TITHE.toString(), 1)));
			Villager far = librarian(helper, FAR, 20, 4, 1);
			helper.assertTrue(VillageHalls.nearest(level, far.blockPosition()).isEmpty(), "the far librarian is in the village");
			ServerPlayer player = player(helper, FAR.east());
			MerchantMenu menu = trade(helper, far, player);
			helper.assertTrue(menu.getOffers().get(0).getCostA().getCount() == 20, "outside, 20 became " + menu.getOffers().get(0).getCostA().getCount());
			buy(helper, menu, player, 0, 20);
			helper.assertTrue(entity.treasury() == 0, "outside, the treasury got " + entity.treasury());
			player.closeContainer();

			Villager near = librarian(helper, new BlockPos(9, 2, 9), 20, 4, 1);
			int cap = 64 * 100; // a Hamlet's
			entity.setTreasury(cap);
			player.moveTo(near.getX() + 1, near.getY(), near.getZ());
			menu = trade(helper, near, player);
			helper.assertTrue(menu.getOffers().get(0).getCostA().getCount() == 22, "inside, 20 became " + menu.getOffers().get(0).getCostA().getCount());
			buy(helper, menu, player, 0, 22);
			helper.assertTrue(entity.treasury() == cap, "at the cap the treasury holds " + entity.treasury());
			player.closeContainer();

			entity.setEdicts(List.of(new Edicts.InForce(TITHE.toString(), Chronicle.day(level) - Edicts.MIN_DAYS)));
			helper.assertTrue(Edicts.lift(level, hall, null, TITHE.toString()).done(), "lifted");
			entity.setTreasury(0);
			CivicEffects.forget();
			menu = trade(helper, near, player);
			helper.assertTrue(menu.getOffers().get(0).getCostA().getCount() == 20, "lifted, 20 became " + menu.getOffers().get(0).getCostA().getCount());
			buy(helper, menu, player, 0, 20);
			helper.assertTrue(entity.treasury() == 0, "lifted, the treasury got " + entity.treasury());
			player.closeContainer();
			helper.succeed();
		});
	}

	// --- Helpers ---------------------------------------------------------------------------------------------------

	private static Villager still(GameTestHelper helper, BlockPos at) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		return v;
	}

	private static boolean freeBread(ServerLevel level, Villager v) {
		return Moods.work(level, v).good().stream().anyMatch(c -> c.getString().equals("free bread"));
	}

	private static List<String> good(ServerLevel level, Villager v) {
		return Moods.work(level, v).good().stream().map(Component::getString).toList();
	}

	/** Makes {@code eaters} hungry, fills the store with 64 bread and runs the hall's round; returns the meals it took. */
	private static int taken(ServerLevel level, BlockPos hall, Container chest, List<Villager> eaters) {
		chest.setItem(0, new ItemStack(Items.BREAD, 64));
		for (Villager v : eaters) {
			ModAttachments.LAST_MEAL.set(v, level.getGameTime() - 3 * VillageNeeds.DAY);
		}
		VillageNeeds.check(level, hall);
		return 64 - chest.countItem(Items.BREAD);
	}

	private static List<String> said(QaStageFiveGameTests.Client client) {
		return client.seen().stream().map(Component::getString).toList();
	}

	/** A librarian at {@code at} selling for {@code prices} emeralds (fixed offers: bookshelves, a lantern, paper). */
	private static Villager librarian(GameTestHelper helper, BlockPos at, int... prices) {
		Villager librarian = still(helper, at);
		librarian.setVillagerData(librarian.getVillagerData().setProfession(VillagerProfession.LIBRARIAN).setLevel(2));
		MerchantOffers offers = new MerchantOffers();
		ItemStack[] goods = {new ItemStack(Items.BOOKSHELF), new ItemStack(Items.LANTERN), new ItemStack(Items.PAPER)};
		for (int i = 0; i < prices.length; i++) {
			offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, prices[i]), goods[i], 12, 1, 0.05f));
		}
		librarian.setOffers(offers);
		CivicEffects.forget();
		return librarian;
	}

	private static ServerPlayer player(GameTestHelper helper, BlockPos at) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		BlockPos abs = helper.absolutePos(at);
		player.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		return player;
	}

	/** {@code player} right-clicks {@code villager}: the trade screen it opens. */
	private static MerchantMenu trade(GameTestHelper helper, Villager villager, ServerPlayer player) {
		player.moveTo(villager.getX() + 1, villager.getY(), villager.getZ(), 90f, 0f);
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		villager.mobInteract(player, InteractionHand.MAIN_HAND);
		helper.assertTrue(player.containerMenu instanceof MerchantMenu, "no trade screen: " + player.containerMenu);
		return (MerchantMenu) player.containerMenu;
	}

	/** On the trade screen: picks offer {@code index} with exactly {@code emeralds} and takes what it sells. */
	private static void buy(GameTestHelper helper, MerchantMenu menu, ServerPlayer player, int index, int emeralds) {
		player.getInventory().clearContent();
		player.getInventory().add(new ItemStack(Items.EMERALD, emeralds));
		menu.setSelectionHint(index);
		menu.tryMoveItems(index);
		helper.assertTrue(!menu.getSlot(2).getItem().isEmpty(), "nothing for sale with " + emeralds + " emeralds");
		menu.clicked(2, 0, ClickType.PICKUP, player);
		int left = player.getInventory().countItem(Items.EMERALD) + menu.getSlot(0).getItem().getCount() + menu.getSlot(1).getItem().getCount();
		helper.assertTrue(left == 0, emeralds + " emeralds paid, " + left + " left over");
		menu.setCarried(ItemStack.EMPTY);
	}

	/** Festival Season in force and the evening of today, so no round plans one early; returns the next festival's day. */
	private static long seasonDay(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		entity.setEdicts(List.of(new Edicts.InForce(FESTIVAL_SEASON.toString(), 1)));
		level.setDayTime((Chronicle.day(level) - 1) * VillageNeeds.DAY + Festivals.END + 500);
		return Festivals.nextDay(level, hall, entity);
	}

	private static void morning(ServerLevel level, long day) {
		level.setDayTime((day - 1) * VillageNeeds.DAY + 1000);
	}

	/** A village of {@code radius}; every cache and the edicts reset after the test. */
	private static void village(GameTestHelper helper, int radius) {
		int was = VillageHalls.RADIUS;
		VillageHalls.RADIUS = radius;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = was;
			VillageNeeds.forget();
			CivicEffects.forget();
			Moods.forget();
		});
	}

	/** {@link #village} of radius 16 with regular festivals on and the clock put back after. */
	private static void festivalVillage(GameTestHelper helper) {
		village(helper, 16);
		long time = helper.getLevel().getDayTime();
		boolean festivals = Festivals.ENABLED;
		Festivals.ENABLED = true;
		Leftovers.after(helper, () -> {
			Festivals.ENABLED = festivals;
			helper.getLevel().setDayTime(time);
			Festivals.forget();
		});
	}

	private static VillageHallBlockEntity ready(GameTestHelper helper) {
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
		hall.setRank(VillageRanks.Rank.HAMLET);
		hall.setEdicts(List.of());
		hall.setReforms(List.of());
		hall.setExtraMeals(0);
		VillageNeeds.forget();
		CivicEffects.forget();
		Moods.forget();
		return hall;
	}

	private static VillageHallBlockEntity festivalReady(GameTestHelper helper) {
		VillageHallBlockEntity hall = ready(helper);
		hall.setTreasury(0);
		hall.setFestivalDay(-1);
		return hall;
	}

	private static void moodsOn(GameTestHelper helper) {
		boolean moods = Moods.ENABLED;
		Moods.ENABLED = true;
		Leftovers.after(helper, () -> Moods.ENABLED = moods);
	}

	private static void noSickness(GameTestHelper helper) {
		boolean sickness = Sickness.ENABLED;
		Sickness.ENABLED = false;
		Leftovers.after(helper, () -> Sickness.ENABLED = sickness);
	}
}
