package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.colony.Settlers;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * QA (lane D, 2026-11-02), from the settlers' walk-out spec (B182, 41.3): with no linked waystone the settlers walk out
 * "{@code OUT} blocks and a few more from the hall toward the spot, on the ground there", or to the spot itself when it
 * is nearer; an order meant to go by waystone whose stone isn't there walks the same road. Covers the road fallback of
 * {@code Settlers.edge} (lines after the waystone branch), which no test called directly (the previous run's "next").
 */
public class QaSettlersRoad1102dGameTests implements FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(5, 2, 5);

	private static RealmData.Order order(ServerLevel level, BlockPos hall, BlockPos spot, Optional<BlockPos> via) {
		RealmData.Order o = new RealmData.Order(GlobalPos.of(level.dimension(), hall), spot, Optional.empty(), RealmData.GATHERING, 0, 0, 100);
		return new RealmData.Order(o.mother(), o.spot(), o.name(), o.state(), o.leaves(), o.arrives(), o.cost(), o.ordered(), o.volunteers(),
			o.builder(), o.supplies(), o.settlers(), o.blessed(), via);
	}

	/** The ground over {@code column}: the first free block above the land, as a villager would stand there. */
	private static BlockPos ground(ServerLevel level, BlockPos column) {
		level.getChunk(column.getX() >> 4, column.getZ() >> 4); // (loaded, so the walk point can be there)
		return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
	}

	private static void check(GameTestHelper helper, String what, BlockPos got, BlockPos want) {
		helper.assertTrue(got.equals(want), what + ": expected the settlers to walk out to " + want.toShortString() + ", got "
			+ (got == null ? "null" : got.toShortString()));
	}

	/** A spot ten blocks east: nearer than the walk out, so they walk to the spot itself, on the ground. */
	//$ gametest_ticks BIG '40'
	@GameTest(template = BIG, timeoutTicks = 40)
	public void aNearSpotIsWalkedToItself(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos want = ground(level, hall.east(10));
		check(helper, "a spot 10 east", Settlers.edge(level, order(level, hall, hall.east(10), Optional.empty())), want);
		helper.succeed();
	}

	/** A spot a thousand blocks west: they walk out OUT + 8 blocks toward it (the loaded ground there), no farther. */
	//$ gametest_ticks BIG '40'
	@GameTest(template = BIG, timeoutTicks = 40)
	public void aFarSpotIsWalkedTowardOnlyAsFarAsTheWalkOut(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos want = ground(level, hall.west(Settlers.OUT + 8));
		check(helper, "a spot 1000 west", Settlers.edge(level, order(level, hall, hall.west(1000), Optional.empty())), want);
		helper.succeed();
	}

	/**
	 * A spot north-east at 30 across and 40 down (50 away): the walk-out point lies on the line toward it, between OUT and
	 * OUT + 8 blocks from the hall, never past the spot, on the ground.
	 */
	//$ gametest_ticks BIG '40'
	@GameTest(template = BIG, timeoutTicks = 40)
	public void aSlantedSpotIsWalkedTowardAlongTheLine(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos spot = hall.offset(30, 0, -40);
		for (int d = 0; d <= 50; d += 8) {
			level.getChunk((hall.getX() + d * 3 / 5) >> 4, (hall.getZ() - d * 4 / 5) >> 4);
		}
		BlockPos got = Settlers.edge(level, order(level, hall, spot, Optional.empty()));
		helper.assertTrue(got != null, "a slanted spot: expected a walk-out point, got null");
		double dx = got.getX() + 0.5 - (hall.getX() + 0.5);
		double dz = got.getZ() + 0.5 - (hall.getZ() + 0.5);
		double out = Math.sqrt(dx * dx + dz * dz);
		helper.assertTrue(out >= Settlers.OUT && out <= Settlers.OUT + 8 + 1 && out <= 50 + 1,
			"a slanted spot 50 away: expected the walk-out point " + Settlers.OUT + " to " + (Settlers.OUT + 8) + " blocks out, got " + String.format("%.1f", out) + " at " + got.toShortString());
		double off = Math.abs(dx * -40 - dz * 30) / 50; // distance from the line hall-spot
		helper.assertTrue(off <= 1.5, "a slanted spot: expected the walk-out point on the line to the spot, it is " + String.format("%.1f", off) + " blocks off it at " + got.toShortString());
		helper.assertTrue(dx > 0 && dz < 0, "a slanted spot north-east: expected the point north-east of the hall, got " + got.toShortString());
		check(helper, "a slanted spot (on the ground)", got, ground(level, got));
		helper.succeed();
	}

	/** A spot at the hall itself (a zero-length road): they walk to the ground by the hall, never to a nonsense point. */
	//$ gametest_ticks BIG '40'
	@GameTest(template = BIG, timeoutTicks = 40)
	public void aSpotAtTheHallIsTheGroundThere(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		check(helper, "a spot at the hall", Settlers.edge(level, order(level, hall, hall, Optional.empty())), ground(level, hall));
		helper.succeed();
	}

	/**
	 * B182: "the farthest point on the way that is loaded". A mother village far out where no chunk on the road is loaded:
	 * the walk-out point is still given, out on the road ({@code OUT + 8} toward the spot, at the hall's height), never
	 * nothing; with only the hall's own chunk loaded and the road west of it unloaded, they walk to the ground at the hall.
	 */
	//$ gametest_ticks BIG '40'
	@GameTest(template = BIG, timeoutTicks = 40)
	public void unloadedRoadStillGivesAPointAndOnlyTheHallLoadedGivesTheHall(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos lone = new BlockPos(3_200_008, 70, 3_200_008);
		helper.assertTrue(!level.isLoaded(lone) && !level.isLoaded(lone.west(Settlers.OUT + 8)), "setup: expected the far road unloaded");
		BlockPos got = Settlers.edge(level, order(level, lone, lone.west(1000), Optional.empty()));
		check(helper, "nothing loaded on the road", got, lone.west(Settlers.OUT + 8));
		// The hall at the west edge of its chunk (x a multiple of 16): the hall's chunk loaded, the one west of it not.
		BlockPos edgeHall = new BlockPos(1_600_000, 70, 1_600_008);
		level.getChunk(edgeHall.getX() >> 4, edgeHall.getZ() >> 4);
		helper.assertTrue(level.isLoaded(edgeHall) && !level.isLoaded(edgeHall.west()), "setup: expected only the hall's chunk loaded, not the one west of it");
		check(helper, "only the hall's chunk loaded", Settlers.edge(level, order(level, edgeHall, edgeHall.west(1000), Optional.empty())),
			level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, edgeHall));
		helper.succeed();
	}

	/**
	 * An order meant to go by waystone ({@code via} set) where neither village has a waystone: not linked, so they walk the
	 * road as any other settlers (the walk-out point toward the spot, not the "stone" spot).
	 */
	//$ gametest_ticks BIG '40'
	@GameTest(template = BIG, timeoutTicks = 40)
	public void aWaystoneOrderWithNoStonesWalksTheRoad(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos far = hall.south(600);
		BlockPos want = ground(level, hall.south(Settlers.OUT + 8));
		RealmData.Order o = order(level, hall, far, Optional.of(far));
		helper.assertTrue(!Settlers.byWaystone(level, o), "setup: expected an order with no waystones not to go by waystone");
		check(helper, "a waystone order with no stones", Settlers.edge(level, o), want);
		helper.succeed();
	}
}
