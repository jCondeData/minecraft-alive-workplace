package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.MarketDays;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.printer.Gazette;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;

/** QA (lane-c-1117-0652): market days, the two failures found (B1361 and B1362, fixed by lane-a-1117-0923), with their neighbors. */
public class QaMarketDays1117cBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(20, 2, 20);
	private static final Pattern MARKET_DATE = Pattern.compile("Next market day: (today|tomorrow|in \\d+ days)");

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

	private static String calendar(ServerLevel level, BlockPos hall) {
		List<Component> pages = Gazette.gazettePages(level, hall, (VillageHallBlockEntity) level.getBlockEntity(hall));
		return pages.stream().map(Component::getString).filter(p -> p.startsWith("THE CALENDAR")).findFirst().orElse("(no calendar page)");
	}

	/**
	 * The Gazette's calendar tells a village with no Market Square (and one with market days switched off) its "next
	 * market day", a market that never comes; the Seer says nothing then (Seer.java checks both).
	 */
	//$ gametest_batch AREA '"qaMarketGazetteBug1117c"'
	@GameTest(template = AREA, batch = "qaMarketGazetteBug1117c")
	public void theGazetteTellsNoMarketDayWithoutAMarket(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		helper.assertTrue(MarketDays.square(level, hall).isEmpty(), "a square before one was built");
		String noSquare = calendar(level, hall);
		helper.assertFalse(MARKET_DATE.matcher(noSquare).find(), "with no Market Square the Gazette says: " + noSquare.replace('\n', ' '));
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(2, 2, 2)), Rotation.NONE,
			Mirror.NONE);
		sites.recordFinished(StarterBlueprints.MARKET_SQUARE.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		helper.assertTrue(MARKET_DATE.matcher(calendar(level, hall)).find(), "with a Market Square the Gazette tells no market day: " + calendar(level, hall));
		MarketDays.ENABLED = false;
		String off = calendar(level, hall);
		helper.assertFalse(MARKET_DATE.matcher(off).find(), "with marketDays off the Gazette says: " + off.replace('\n', ' '));
		// the same for festivals: switched off, the Gazette still dates the next one
		boolean festivals = io.github.jcondedata.aliveworkplace.hall.Festivals.ENABLED;
		io.github.jcondedata.aliveworkplace.hall.Festivals.ENABLED = false;
		try {
			String noFestivals = calendar(level, hall);
			helper.assertFalse(Pattern.compile("Next festival: (today|tomorrow|in \\d+ days)").matcher(noFestivals).find(),
				"with festivals off the Gazette says: " + noFestivals.replace('\n', ' '));
		} finally {
			io.github.jcondedata.aliveworkplace.hall.Festivals.ENABLED = festivals;
		}
		helper.succeed();
	}

	/** The traders "leave again by nightfall": a market that begins late in the morning (a village loaded at 5000) keeps them into the night. */
	//$ gametest_batch AREA '"qaMarketNightfallBug1117c"'
	@GameTest(template = AREA, batch = "qaMarketNightfallBug1117c")
	public void lateMorningTradersLeaveByNightfall(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(2, 2, 2)), Rotation.NONE,
			Mirror.NONE);
		sites.recordFinished(StarterBlueprints.MARKET_SQUARE.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		long day = 80;
		while (!MarketDays.isMarketDay(hall, day)) {
			day++;
		}
		for (int start : new int[] {1000, 5000, 5999}) {
			level.setDayTime((day - 1) * 24000L + start);
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			entity.setLastMarketDay(-1);
			helper.assertTrue(MarketDays.tick(level, hall, entity), "no market at " + start);
			List<WanderingTrader> came = level.getEntitiesOfClass(WanderingTrader.class, new AABB(hall).inflate(40), WanderingTrader::isAlive);
			for (WanderingTrader t : came) {
				long leaves = start + t.getDespawnDelay();
				t.discard();
				// nightfall: the sun sets at 12000 and night (monsters) is from about 13000
				helper.assertTrue(leaves <= 13000, "a trader who came at " + start + " stays until " + leaves + " (night begins about 13000)");
			}
		}
		helper.succeed();
	}

	/** B1361's neighbors: what the Seer, the Gazette and the Observatory tell comes only when it will come. */
	//$ gametest_batch AREA '"qaMarketForetold1117c"'
	@GameTest(template = AREA, batch = "qaMarketForetold1117c")
	public void onlyAMarketAndAFestivalThatWillComeAreForetold(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		helper.assertTrue(MarketDays.foretold(level, hall, -1) == -1, "a market day foretold with no Market Square");
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(2, 2, 2)), Rotation.NONE,
			Mirror.NONE);
		sites.recordFinished(StarterBlueprints.MARKET_SQUARE.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		helper.assertTrue(MarketDays.foretold(level, hall, -1) == MarketDays.nextDay(level, hall, -1), "the square's market day: " + MarketDays.foretold(level, hall, -1));
		MarketDays.ENABLED = false;
		helper.assertTrue(MarketDays.foretold(level, hall, -1) == -1, "a market day foretold with market days off");
		String off = calendar(level, hall);
		helper.assertTrue(off.contains("Next market day: none, this world holds no markets"), "with market days off: " + off.replace('\n', ' '));
		MarketDays.ENABLED = true;
		sites.forgetFinished(placement);
		String noSquare = calendar(level, hall);
		helper.assertTrue(noSquare.contains("Next market day: none until the village builds a Market Square"), "with no square: " + noSquare.replace('\n', ' '));
		boolean festivals = Festivals.ENABLED;
		long festivalDay = entity.festivalDay();
		try {
			Festivals.ENABLED = true;
			long next = Festivals.nextDay(level, hall, entity);
			helper.assertTrue(Festivals.foretold(level, hall, entity, Festivals.MIN_VILLAGERS) == next, "a big enough village's festival: " + next);
			helper.assertTrue(Festivals.foretold(level, hall, entity, Festivals.MIN_VILLAGERS - 1) == -1, "a festival foretold for too few villagers");
			helper.assertTrue(calendar(level, hall).contains("Next festival: none planned"), "an empty village's calendar: " + calendar(level, hall).replace('\n', ' '));
			Festivals.ENABLED = false;
			helper.assertTrue(Festivals.foretold(level, hall, entity, 20) == -1, "a festival foretold with festivals off");
			// a festival already planned (an edict's, a Cup's) is told whatever the switch and the count
			long planned = io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level) + 2;
			entity.setFestivalDay(planned);
			helper.assertTrue(Festivals.foretold(level, hall, entity, 0) == planned, "the planned festival wasn't told: " + Festivals.foretold(level, hall, entity, 0));
		} finally {
			Festivals.ENABLED = festivals;
			entity.setFestivalDay(festivalDay);
		}
		helper.succeed();
	}

	/** B1362's neighbors: the stay is never past dusk at any hour, never more than STAY, and never nothing at all. */
	//$ gametest_batch AREA '"qaMarketStay1117c"'
	@GameTest(template = AREA, batch = "qaMarketStay1117c")
	public void tradersStayUntilDuskAtMost(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		long day = 80;
		for (int[] c : new int[][] {{1000, MarketDays.STAY}, {2000, MarketDays.STAY}, {3000, 9000}, {5000, 7000}, {11990, 20}, {18000, 20}}) {
			level.setDayTime((day - 1) * 24000L + c[0]);
			int stay = MarketDays.stay(level, hall);
			helper.assertTrue(stay == c[1], "traders arriving at " + c[0] + " stay " + stay + ", expected " + c[1]);
		}
		helper.succeed();
	}
}
