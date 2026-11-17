package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.MarketDays;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-c-1117-0652) from the market days' spec ({@link MarketDays}: once a week, in the morning, a village with a
 * Village Hall and a finished Market Square holds a market; traders come with a blueprint to sell for 6 emeralds;
 * players in the village are told; the chronicle notes it; {@code marketDays} turns it off; the Seer, the Gazette and
 * the Observatory tell the next one): the morning's edges, the weekly rhythm, the next day told against the day it
 * comes, no square, a square finished later that morning, a styled square, the switch, who hears it, the offers over
 * many markets, and the day held kept over a save.
 */
public class QaMarketDays1117cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(20, 2, 20);

	/** The village: a hall, markets on, the radius wide enough for the square; everything put back after. */
	private static BlockPos village(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		boolean enabled = MarketDays.ENABLED;
		long dayTime = helper.getLevel().getDayTime();
		VillageHalls.RADIUS = 32;
		MarketDays.ENABLED = true;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			MarketDays.ENABLED = enabled;
			helper.getLevel().setDayTime(dayTime);
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		return helper.absolutePos(HALL);
	}

	/** A finished Market Square (of {@code id}) in the village, forgotten after the test. */
	private static BlueprintData.Placement square(GameTestHelper helper, ResourceLocation id) {
		BuildSiteManager sites = BuildSiteManager.get(helper.getLevel());
		BlueprintData.Placement placement = new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(new BlockPos(2, 2, 2)),
			Rotation.NONE, Mirror.NONE);
		sites.recordFinished(id, placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		return placement;
	}

	/** The first market day (a Chronicle day) at or after {@code from} for the hall. */
	private static long marketDay(BlockPos hall, long from) {
		long day = from;
		while (!MarketDays.isMarketDay(hall, day)) {
			day++;
		}
		return day;
	}

	/** Sets the clock to {@code time} ticks into Chronicle day {@code day}. */
	private static void at(GameTestHelper helper, long day, int time) {
		helper.getLevel().setDayTime((day - 1) * 24000L + time);
	}

	private static String key(net.minecraft.network.chat.Component c) {
		return c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t ? t.getKey() : "";
	}

	private static List<WanderingTrader> traders(GameTestHelper helper, BlockPos hall) {
		return helper.getLevel().getEntitiesOfClass(WanderingTrader.class, new AABB(hall).inflate(40), WanderingTrader::isAlive);
	}

	/** Market time is the morning, 1000 to 5999 of the market day: one tick either side of each edge, and the other days. */
	//$ gametest_batch AREA '"qaMarketMorning1117c"'
	@GameTest(template = AREA, batch = "qaMarketMorning1117c")
	public void marketTimeIsTheMorningOfTheMarketDayOnly(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		long day = marketDay(hall, 20);
		int[][] cases = {{999, 0}, {1000, 1}, {3000, 1}, {5999, 1}, {6000, 0}, {12000, 0}, {23999, 0}};
		for (int[] c : cases) {
			at(helper, day, c[0]);
			helper.assertTrue(Chronicle.day(level) == day, "the clock is off: day " + Chronicle.day(level) + ", wanted " + day);
			helper.assertTrue(MarketDays.isMarketMorning(level, hall, -1) == (c[1] == 1),
				"at " + c[0] + " of market day the morning is " + MarketDays.isMarketMorning(level, hall, -1) + ", wanted " + (c[1] == 1));
			helper.assertFalse(MarketDays.isMarketMorning(level, hall, day), "a second market at " + c[0] + " on the day one was held");
		}
		for (long other = day + 1; other < day + 7; other++) {
			at(helper, other, 2000);
			helper.assertFalse(MarketDays.isMarketMorning(level, hall, -1), "day " + other + " (not market day) has a market morning");
		}
		helper.succeed();
	}

	/** Once a week: exactly one market day in every 7 days running, 7 days apart, for halls all over the map. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaMarketWeekly1117c"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaMarketWeekly1117c")
	public void everyHallHasOneMarketDayAWeek(GameTestHelper helper) {
		BlockPos[] halls = {BlockPos.ZERO, new BlockPos(1, 64, -1), new BlockPos(-30000000, 320, 29999999), new BlockPos(12345, -60, -6789),
			new BlockPos(7, 7, 7)};
		for (BlockPos hall : halls) {
			long first = -1;
			for (long day = 1; day <= 70; day++) {
				if (MarketDays.isMarketDay(hall, day)) {
					helper.assertTrue(first < 0 || (day - first) % MarketDays.EVERY_DAYS == 0, hall + ": market days " + first + " and " + day + " aren't a week apart");
					if (first < 0) {
						first = day;
					}
				}
			}
			for (long start = 1; start <= 63; start++) {
				int count = 0;
				for (long day = start; day < start + MarketDays.EVERY_DAYS; day++) {
					count += MarketDays.isMarketDay(hall, day) ? 1 : 0;
				}
				helper.assertTrue(count == 1, hall + ": " + count + " market days in the week from day " + start);
			}
		}
		helper.succeed();
	}

	/**
	 * The day the Seer, the Gazette and the Observatory tell ({@code nextDay}) is the day the market comes: today while
	 * this morning's market is still to come, else the next market day; never a day that isn't one, never more than a week.
	 */
	//$ gametest_batch AREA '"qaMarketNextDay1117c"'
	@GameTest(template = AREA, batch = "qaMarketNextDay1117c")
	public void theNextMarketDayToldIsTheDayItComes(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		long market = marketDay(hall, 30);
		// every day of the week before, at dawn, noon and night: the next is this week's market day
		for (long day = market - 6; day < market; day++) {
			for (int time : new int[] {0, 999, 6000, 18000, 23999}) {
				at(helper, day, time);
				long next = MarketDays.nextDay(level, hall, -1);
				helper.assertTrue(next == market, "day " + day + " at " + time + ": told day " + next + ", the market is day " + market);
			}
		}
		// the market day itself: today until the morning's market is held or the morning is over
		at(helper, market, 0);
		helper.assertTrue(MarketDays.nextDay(level, hall, -1) == market, "before dawn on market day: told " + MarketDays.nextDay(level, hall, -1));
		at(helper, market, 5999);
		helper.assertTrue(MarketDays.nextDay(level, hall, -1) == market, "late morning, not yet held: told " + MarketDays.nextDay(level, hall, -1));
		helper.assertTrue(MarketDays.nextDay(level, hall, market) == market + 7, "held this morning: told " + MarketDays.nextDay(level, hall, market));
		at(helper, market, 6000);
		helper.assertTrue(MarketDays.nextDay(level, hall, -1) == market + 7, "the morning is over: told " + MarketDays.nextDay(level, hall, -1));
		// whatever it says, it is a market day within the week
		for (long day = market; day < market + 14; day++) {
			at(helper, day, 13000);
			long next = MarketDays.nextDay(level, hall, -1);
			helper.assertTrue(MarketDays.isMarketDay(hall, next) && next > day && next <= day + 7, "day " + day + ": told " + next);
		}
		helper.succeed();
	}

	/**
	 * No Market Square, no market (and the day isn't spent: a square finished later that morning still brings it). The
	 * village told; a player outside it not; the chronicle notes it with the count; the traders sell a blueprint each.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaMarketNoSquare1117c"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaMarketNoSquare1117c")
	public void noSquareNoMarketAndASquareFinishedThatMorningBringsIt(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		QaStageFiveGameTests.Client near = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("Marketgoer"));
		QaStageFiveGameTests.Client far = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("Faraway"));
		far.player().moveTo(Vec3.atCenterOf(hall.offset(VillageHalls.RADIUS + 10, 0, 0)));
		long day = marketDay(hall, 40);
		at(helper, day, 1500);
		helper.assertFalse(MarketDays.tick(level, hall, entity), "a market with no Market Square");
		helper.assertTrue(entity.lastMarketDay() != day, "the market day was spent without a square: " + entity.lastMarketDay());
		helper.assertTrue(traders(helper, hall).isEmpty(), "traders came with no square: " + traders(helper, hall).size());
		square(helper, StarterBlueprints.MARKET_SQUARE.id());
		at(helper, day, 4000);
		near.seen().clear();
		far.seen().clear();
		helper.assertTrue(MarketDays.tick(level, hall, entity), "the square finished that morning brought no market");
		helper.assertTrue(entity.lastMarketDay() == day, "the market day held isn't remembered: " + entity.lastMarketDay());
		List<WanderingTrader> came = traders(helper, hall);
		helper.assertTrue(came.size() == MarketDays.traders(level, hall), came.size() + " traders came, the village's count is " + MarketDays.traders(level, hall));
		for (WanderingTrader t : came) {
			List<MerchantOffer> blueprints = t.getOffers().stream().filter(o -> o.getResult().is(ModItems.BLUEPRINT)).toList();
			helper.assertTrue(blueprints.size() == 1, "a trader sells " + blueprints.size() + " blueprints, wanted 1");
			MerchantOffer o = blueprints.get(0);
			helper.assertTrue(o.getCostA().is(Items.EMERALD) && o.getCostA().getCount() == 6 && o.getCostB().isEmpty() && o.getMaxUses() == 1,
				"the blueprint's price: " + o.getCostA() + " + " + o.getCostB() + ", " + o.getMaxUses() + " uses");
		}
		String key = "message.aliveworkplace.market.day";
		helper.assertTrue(near.seen().stream().anyMatch(c -> key(c).equals(key)), "the player in the village wasn't told: " + near.seen());
		helper.assertTrue(near.seen().stream().filter(c -> key(c).equals(key)).findFirst().map(c -> c.getString().contains("Market Square")).orElse(false),
			"the words: " + near.seen());
		helper.assertTrue(far.seen().stream().noneMatch(c -> key(c).equals(key)), "a player outside the village was told: " + far.seen());
		helper.assertTrue(entity.chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.MARKET && e.text().getString().contains(came.size() + " traders")),
			"the chronicle doesn't say " + came.size() + " traders came");
		// later the same day: no second market
		at(helper, day, 5500);
		helper.assertFalse(MarketDays.tick(level, hall, entity), "a second market the same morning");
		helper.assertTrue(traders(helper, hall).size() == came.size(), "more traders came");
		came.forEach(WanderingTrader::discard);
		helper.succeed();
	}

	/** {@code marketDays} off: a village with a square has no market on market morning, and the day isn't spent. */
	//$ gametest_batch AREA '"qaMarketOff1117c"'
	@GameTest(template = AREA, batch = "qaMarketOff1117c")
	public void theSwitchOffHoldsNoMarket(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		square(helper, StarterBlueprints.MARKET_SQUARE.id());
		long day = marketDay(hall, 50);
		at(helper, day, 1200);
		MarketDays.ENABLED = false;
		helper.assertFalse(MarketDays.tick(level, hall, entity), "a market with marketDays off");
		helper.assertTrue(traders(helper, hall).isEmpty() && entity.lastMarketDay() != day, "traders came, or the day was spent, with the switch off");
		helper.assertTrue(entity.chronicle().stream().noneMatch(e -> e.kind() == Chronicle.Kind.MARKET), "a market in the chronicle with the switch off");
		MarketDays.ENABLED = true;
		helper.assertTrue(MarketDays.tick(level, hall, entity), "switched back on that morning: no market");
		traders(helper, hall).forEach(WanderingTrader::discard);
		helper.succeed();
	}

	/** A Market Square built in another style is still the village's Market Square. */
	//$ gametest_batch AREA '"qaMarketStyled1117c"'
	@GameTest(template = AREA, batch = "qaMarketStyled1117c")
	public void aStyledMarketSquareHoldsTheMarket(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		List<BlueprintStyles.Style> styles = BlueprintStyles.all();
		helper.assertFalse(styles.isEmpty(), "no styles loaded");
		ResourceLocation styled = BlueprintStyles.styled(StarterBlueprints.MARKET_SQUARE.id(), styles.get(0).name());
		helper.assertFalse(styled.equals(StarterBlueprints.MARKET_SQUARE.id()), "the styled id is the plain one: " + styled);
		square(helper, styled);
		helper.assertTrue(MarketDays.square(level, hall).isPresent(), "a " + styled + " isn't the village's Market Square");
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		at(helper, marketDay(hall, 60), 1000);
		helper.assertTrue(MarketDays.tick(level, hall, entity), "no market at a styled square");
		traders(helper, hall).forEach(WanderingTrader::discard);
		helper.succeed();
	}

	/**
	 * Over many markets (random blueprints and styles), every trader sells exactly one blueprint, of a build the library
	 * has, for 6 emeralds: never a trader without one, never a blueprint of nothing.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaMarketOffers1117c"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaMarketOffers1117c")
	public void everyMarketTraderSellsARealBlueprint(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		square(helper, StarterBlueprints.MARKET_SQUARE.id());
		BlockPos at = MarketDays.square(level, hall).orElseThrow();
		int traders = 0;
		for (int market = 0; market < 40; market++) {
			for (WanderingTrader t : MarketDays.hold(level, hall, at)) {
				traders++;
				List<MerchantOffer> blueprints = t.getOffers().stream().filter(o -> o.getResult().is(ModItems.BLUEPRINT)).toList();
				helper.assertTrue(blueprints.size() == 1, "market " + market + ": a trader sells " + blueprints.size() + " blueprints");
				Optional<BlueprintData> data = BlueprintItem.data(blueprints.get(0).getResult());
				helper.assertTrue(data.isPresent() && BlueprintLibrary.get(level, data.get().structure()).isPresent(),
					"market " + market + ": a blueprint of a build the library hasn't: " + data.map(BlueprintData::structure));
				helper.assertTrue(blueprints.get(0).getCostA().getCount() == 6, "market " + market + ": price " + blueprints.get(0).getCostA());
				t.discard();
			}
		}
		helper.assertTrue(traders == 40 * MarketDays.traders(level, hall), traders + " traders over 40 markets");
		helper.succeed();
	}

	/**
	 * How many come: two, three from Town rank on, one more a level of Commerce research; and as many as that stand at the
	 * square on market morning.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaMarketCount1117c"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaMarketCount1117c")
	public void theTradersCountGrowsWithRankAndCommerce(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		square(helper, StarterBlueprints.MARKET_SQUARE.id());
		io.github.jcondedata.aliveworkplace.research.Research.Topic commerce = io.github.jcondedata.aliveworkplace.research.Research.Topic.COMMERCE;
		int[][] cases = {{0, 0, 2}, {1, 0, 2}, {2, 0, 3}, {3, 0, 3}, {0, 1, 3}, {2, 1, 4}, {3, commerce.maxLevel, 3 + commerce.maxLevel}};
		io.github.jcondedata.aliveworkplace.hall.VillageRanks.Rank[] ranks = io.github.jcondedata.aliveworkplace.hall.VillageRanks.Rank.values();
		long day = marketDay(hall, 90);
		for (int[] c : cases) {
			entity.setRank(ranks[c[0]]);
			entity.setResearch(new io.github.jcondedata.aliveworkplace.research.Research.State(
				c[1] == 0 ? java.util.Map.of() : java.util.Map.of(commerce.key(), c[1]), Optional.empty(), 0, false));
			helper.assertTrue(MarketDays.traders(level, hall) == c[2],
				ranks[c[0]] + " with Commerce " + c[1] + ": " + MarketDays.traders(level, hall) + " traders, wanted " + c[2]);
			at(helper, day, 1000);
			entity.setLastMarketDay(-1);
			helper.assertTrue(MarketDays.tick(level, hall, entity), "no market for " + ranks[c[0]]);
			List<WanderingTrader> came = traders(helper, hall);
			helper.assertTrue(came.size() == c[2], ranks[c[0]] + " with Commerce " + c[1] + ": " + came.size() + " came, wanted " + c[2]);
			came.forEach(WanderingTrader::discard);
		}
		helper.succeed();
	}

	/** The market day held is kept over a save: after a reload the same morning brings no second market. */
	//$ gametest_batch AREA '"qaMarketSaved1117c"'
	@GameTest(template = AREA, batch = "qaMarketSaved1117c")
	public void theMarketHeldIsKeptOverASave(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		square(helper, StarterBlueprints.MARKET_SQUARE.id());
		long day = marketDay(hall, 70);
		at(helper, day, 1100);
		helper.assertTrue(MarketDays.tick(level, hall, entity), "no market");
		traders(helper, hall).forEach(WanderingTrader::discard);
		CompoundTag tag = entity.saveWithoutMetadata(level.registryAccess());
		VillageHallBlockEntity copy = new VillageHallBlockEntity(hall, level.getBlockState(hall));
		copy.loadWithComponents(tag, level.registryAccess());
		helper.assertTrue(copy.lastMarketDay() == day, "the market day held after a reload: " + copy.lastMarketDay());
		at(helper, day, 3000);
		helper.assertFalse(MarketDays.isMarketMorning(level, hall, copy.lastMarketDay()), "a second market after the reload");
		// an old save without the field: never held, so this morning's market still comes
		tag.remove("lastMarketDay");
		VillageHallBlockEntity old = new VillageHallBlockEntity(hall, level.getBlockState(hall));
		old.loadWithComponents(tag, level.registryAccess());
		helper.assertTrue(old.lastMarketDay() == -1, "an old save's market day: " + old.lastMarketDay());
		helper.assertTrue(MarketDays.isMarketMorning(level, hall, old.lastMarketDay()), "an old save has no market this morning");
		helper.succeed();
	}
}
