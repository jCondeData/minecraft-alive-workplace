package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
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

/** QA (lane-c-1117-0652): market days, the two failures found (see ROADMAP Bugs). Lives on tests/market-days-1117c until fixed. */
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
}
