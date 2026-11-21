package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.MarketDays;
import io.github.jcondedata.aliveworkplace.hall.TradeFairs;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/**
 * QA lane C (2026-11-21), bug tests from the 51.7 rebuilt Market Square's brief that fail on main (branch
 * {@code tests/rebuilt-markets-1121c}, not registered on main). Setup shared with {@link QaRebuiltMarkets1121cGameTests}.
 */
public class QaRebuiltMarkets1121cBugTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/**
	 * market_square.md, "Bunting ring (trade fairs)": banners go up on a ring 6 out from the middle (the outer edge cells),
	 * one every 3 blocks, each on the highest air cell over solid ground; "an edge that is all air with nothing to stand a
	 * banner on, or roofed solid, loses the fair's decoration there". On the placed square every one of the 16 goes up, and
	 * all come down after.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaMarkets1121cBugBunting"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaMarkets1121cBugBunting")
	public void qaTheFairsBuntingGoesUpAllRoundTheRebuiltSquare(GameTestHelper helper) {
		BlockPos hall = QaRebuiltMarkets1121cGameTests.marketVillage(helper);
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(QaRebuiltMarkets1121cGameTests.SQUARE);
		BlockPos square = MarketDays.square(level, hall).orElseThrow();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		List<BlockPos> placed = TradeFairs.putUpBunting(level, square, entity);
		int ring = 8 * TradeFairs.BUNTING_RING;
		int due = (ring + TradeFairs.BUNTING_EVERY - 1) / TradeFairs.BUNTING_EVERY;
		List<String> where = placed.stream().map(p -> p.subtract(origin).toShortString()).toList();
		TradeFairs.takeDownBunting(level, entity);
		boolean down = placed.stream().noneMatch(p -> level.getBlockState(p).is(Blocks.RED_BANNER) || level.getBlockState(p).is(Blocks.YELLOW_BANNER));
		helper.assertTrue(placed.size() == due, placed.size() + " of the fair's " + due + " banners went up round the square: " + where);
		helper.assertTrue(down, "the bunting didn't come down");
		helper.succeed();
	}

}
