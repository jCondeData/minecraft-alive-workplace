package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.StewardCost;
import io.github.jcondedata.aliveworkplace.command.CitySoak;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * ROADMAP 27.22, the 1.1 yardstick ({@code /workplace city}, run on the pack server by {@code CITY=true
 * tools/packtest/run.sh}): the village's plan has every zone the item names, two streets and a wall line; the check that
 * a build stands in its zone and out of Keep Clear; and the Steward's cost meter.
 */
public class CitySoakGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final BlockPos HALL = new BlockPos(1000, 70, 1000);

	/** Homes (renewing old houses), Workshops, Farms, Market, Gardens and Keep Clear; two approved streets; a closed wall line; Run the village. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theCityPlanHasEveryZoneTwoStreetsAndAWallLine(GameTestHelper helper) {
		CityPlan plan = CitySoak.plan(HALL);
		Set<String> kinds = plan.zones().stream().map(CityPlan.Zone::kind).collect(Collectors.toSet());
		helper.assertTrue(kinds.equals(Set.of("homes", "workshops", "farms", "market", "gardens", "keep_clear")), "zones: " + kinds);
		helper.assertTrue(plan.zones().stream().allMatch(z -> !z.cells().isEmpty()), "an empty zone");
		helper.assertTrue(plan.zones().stream().filter(z -> z.kind().equals("homes")).allMatch(CityPlan.Zone::renew), "homes don't renew old houses");
		helper.assertTrue(plan.roads().size() == 2 && plan.roads().stream().allMatch(r -> r.approved() && r.width() == CityPlan.Road.STREET),
			"streets: " + plan.roads());
		helper.assertTrue(plan.wall().map(w -> w.closed() && w.points().size() == 4 && !w.approved()).orElse(false), "wall line: " + plan.wall());
		helper.assertTrue(plan.wall().get().points().stream().allMatch(CityPlan::onGrid), "the wall line runs off the grid");
		helper.assertTrue(plan.mode() == CityPlan.Mode.RUN, "mode: " + plan.mode());
		helper.assertTrue(plan.zoneAt(HALL, HALL).map(z -> z.kind().equals("keep_clear")).orElse(false), "the hall isn't in Keep Clear");
		// No zone on the streets: the builds leave them to the road builders.
		for (int d = -CitySoak.STREET_END; d <= CitySoak.STREET_END; d += 4) {
			BlockPos ew = HALL.offset(d, 0, CitySoak.STREET);
			BlockPos ns = HALL.offset(CitySoak.STREET, 0, d);
			helper.assertTrue(plan.zoneAt(HALL, ew).isEmpty() && plan.zoneAt(HALL, ns).isEmpty(), "a zone on a street at " + d);
		}
		helper.succeed();
	}

	/** In one zone: fine. Touching Keep Clear, outside every zone or across two zones: each named. Roads may cross Keep Clear; wall pieces may not. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aBuildOutsideItsZoneOrInKeepClearIsCaught(GameTestHelper helper) {
		CityPlan plan = CitySoak.plan(HALL);
		helper.assertTrue(CitySoak.where(plan, HALL, CitySoak.Kind.BUILDING, box(-40, -40, -30, -30)).isEmpty(), "a home in Homes was flagged");
		String square = CitySoak.where(plan, HALL, CitySoak.Kind.BUILDING, box(-12, -12, -4, -4));
		helper.assertTrue(square.equals("in Keep Clear"), "on the hall's square: " + square);
		String street = CitySoak.where(plan, HALL, CitySoak.Kind.BUILDING, box(-30, 6, -24, 14));
		helper.assertTrue(street.equals("outside the zones"), "on the street: " + street);
		String across = CitySoak.where(plan, HALL, CitySoak.Kind.BUILDING, box(-24, 20, -16, 26));
		helper.assertTrue(across.startsWith("across "), "across Farms and Gardens: " + across);
		helper.assertTrue(CitySoak.where(plan, HALL, CitySoak.Kind.ROAD, box(-40, 9, 40, 11)).isEmpty(), "the street itself was flagged");
		helper.assertTrue(CitySoak.where(plan, HALL, CitySoak.Kind.ROAD, box(-2, 9, 2, 11)).isEmpty(), "a street by the square was flagged");
		helper.assertTrue(CitySoak.where(plan, HALL, CitySoak.Kind.ROAD, box(0, -3, 0, 3)).isEmpty(), "a lane may cross Keep Clear");
		String wall = CitySoak.where(plan, HALL, CitySoak.Kind.WALL, box(-2, -2, 2, 2));
		helper.assertTrue(wall.equals("in Keep Clear"), "a wall piece on the square: " + wall);
		helper.succeed();
	}

	/** The meter: nothing counted when not recording, nested calls once, one entry a tick, and its percentiles. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theStewardsCostIsCountedOnceATick(GameTestHelper helper) {
		helper.assertTrue(!StewardCost.recording(), "already recording");
		helper.assertTrue(StewardCost.start() == StewardCost.OFF, "counts while not recording");
		StewardCost.begin();
		try {
			long outer = StewardCost.start();
			long inner = StewardCost.start();
			busy(2_000_000L);
			StewardCost.stop(inner);
			StewardCost.stop(outer);
			StewardCost.endTick();
			StewardCost.endTick(); // a tick with nothing of his
			helper.assertTrue(StewardCost.worstCall().startsWith("other "), "slowest call: " + StewardCost.worstCall());
		} finally {
			long[] ticks = StewardCost.finish();
			helper.assertTrue(ticks.length == 2, "ticks: " + ticks.length);
			helper.assertTrue(ticks[0] >= 2_000_000L && ticks[0] < 50_000_000L, "first tick counted " + ticks[0] + " ns (nested twice?)");
			helper.assertTrue(ticks[1] == 0L, "the empty tick counted " + ticks[1]);
		}
		helper.assertTrue(!StewardCost.recording(), "still recording");
		long[] ms = new long[100];
		for (int i = 0; i < 100; i++) {
			ms[i] = (100 - i) * 1_000_000L;
		}
		helper.assertTrue(StewardCost.percentileMs(ms, 95) == 95.0, "p95 " + StewardCost.percentileMs(ms, 95));
		helper.assertTrue(StewardCost.percentileMs(ms, 50) == 50.0, "p50 " + StewardCost.percentileMs(ms, 50));
		helper.assertTrue(StewardCost.percentileMs(ms, 100) == 100.0, "worst " + StewardCost.percentileMs(ms, 100));
		helper.assertTrue(StewardCost.percentileMs(new long[0], 95) == 0.0, "none");
		helper.succeed();
	}

	private static BoundingBox box(int x0, int z0, int x1, int z1) {
		return new BoundingBox(HALL.getX() + x0, HALL.getY(), HALL.getZ() + z0, HALL.getX() + x1, HALL.getY() + 6, HALL.getZ() + z1);
	}

	private static void busy(long nanos) {
		long until = System.nanoTime() + nanos;
		while (System.nanoTime() < until) {
			Thread.onSpinWait();
		}
	}
}
