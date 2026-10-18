package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntUnaryOperator;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ROADMAP 39.7c with the owner's Create port: the roads 39.7b left with gaps. A Train Station standing off the road gets
 * a spur from its own track to the road's; a road that dips a block and comes back up, and a road that zig-zags
 * diagonally (the road planner's steps of one block), get track Create takes as one line. In each, the track is laid
 * the way a village's builder lays it ({@link CaravanTrains#plan}, its curves joined as the track site's end joins
 * them), the drawn village train is put up past one station, assembled with a conductor, and runs to the other station
 * and back on its Create schedule.
 */
public class CreateTrackCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	private static BlockState state(String path) {
		return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", path)).defaultBlockState();
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static BlockState shaped(String shape) {
		BlockState track = state("track");
		net.minecraft.world.level.block.state.properties.Property p = track.getBlock().getStateDefinition().getProperty("shape");
		return track.setValue(p, (Comparable) p.getValue(shape).orElseThrow());
	}

	private static CaravanTrains.Railway railway() {
		return CaravanTrains.RAILWAY.call(r -> r, null);
	}

	/** The area emptied: stone up to {@code top(x, z)}, air above it to y = 12. */
	private static void ground(GameTestHelper helper, java.util.function.BiFunction<Integer, Integer, Integer> top) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				int t = top.apply(x, z);
				for (int y = 1; y < 12; y++) {
					helper.setBlock(new BlockPos(x, y, z), y <= t ? Blocks.STONE : Blocks.AIR);
				}
			}
		}
	}

	/** The road's track laid as a village's builder lays it along {@code way} (relative), its curves joined; the plan. */
	private static CaravanTrains.TrackPlan lay(GameTestHelper helper, List<BlockPos> way) {
		List<BlockPos> route = way.stream().map(helper::absolutePos).toList();
		CaravanTrains.TrackPlan plan = CaravanTrains.plan(route, p -> true);
		plan.shapes().forEach((pos, shape) -> helper.getLevel().setBlockAndUpdate(pos, shaped(shape)));
		for (CaravanTrains.Curve curve : plan.curves()) { // as the track site's end joins them (CaravanTrains.trackBuilt)
			helper.assertTrue(railway().curve(helper.getLevel(), curve.a(), curve.outA(), curve.b(), curve.outB()), "Create's curve didn't join " + curve);
		}
		return plan;
	}

	/**
	 * The drawn train put up past station {@code a} (as the builder would), assembled with a conductor, and run to
	 * {@code b} and back on its schedule: the test passes when it's back at {@code a}.
	 */
	private static void runs(GameTestHelper helper, BlockPos stationA, BlockPos stationB, BlockPos driverAt, String what) {
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(stationA);
		BlockPos b = helper.absolutePos(stationB);
		UUID owner = UUID.randomUUID();
		Villager driver = EntityType.VILLAGER.spawn(level, helper.absolutePos(driverAt), MobSpawnType.COMMAND);
		driver.setNoAi(true);
		int[] stage = {0};
		int[] tick = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			CaravanTrains.Railway r = railway();
			if (stage[0] == 0) {
				boolean aimed = r.aim(level, a, b, "Alpha " + what) & r.aim(level, b, a, "Beta " + what);
				if (aimed && r.linked(level, a, b)) {
					Map<BlockPos, BlockState> parts = r.train(level, a, b);
					helper.assertTrue(parts != null && parts.size() == CaravanTrains.trainBlocks(level).size(), "no room for the drawn train past the station: " + parts);
					parts.forEach((pos, st) -> level.setBlockAndUpdate(pos, st));
					helper.assertTrue(r.assemble(level, a, b, owner, driver) != null, "the station didn't assemble the drawn train (" + what + ")");
					stage[0] = 1;
				} else if (tick[0] > 200) {
					helper.fail("Create's railway doesn't join the two stations " + what + " (aimed " + aimed + ")");
				}
			} else if (tick[0] == 2300) {
				helper.fail("the train didn't run " + what + " to the other station and back (stage " + stage[0] + "): " + r.status(level, owner)
					+ ", linked " + r.linked(level, a, b));
			} else if (stage[0] == 1 && r.at(level, owner, b)) {
				stage[0] = 2;
			} else if (stage[0] == 2 && r.at(level, owner, a)) {
				r.scrap(level, owner);
				helper.succeed();
			}
		});
	}

	/**
	 * A Train Station built off the road (its own track along x at z = 6, x = 1..12, the station beside it) and the road's
	 * track along z = 14: the spur joins the station's track to the road's heading for the other station, and the train
	 * runs out of the spur to the station by the road and back into it.
	 */
	//$ gametest_ticks_batch AREA '2400' '"createTrackSpur"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "createTrackSpur")
	public void aTrainStationOffTheRoadGetsASpurAndTheTrainRuns(GameTestHelper helper) {
		ground(helper, (x, z) -> 1);
		List<BlockPos> way = new ArrayList<>();
		for (int x = 1; x <= 28; x++) {
			way.add(new BlockPos(x, 2, 14));
		}
		CaravanTrains.TrackPlan plan = lay(helper, way);
		for (int x = 1; x <= 12; x++) {
			helper.setBlock(new BlockPos(x, 2, 6), shaped("xo"));
		}
		BlockPos stationA = new BlockPos(6, 2, 7);
		BlockPos stationB = new BlockPos(24, 2, 15);
		helper.setBlock(stationA, state("track_station"));
		helper.setBlock(stationB, state("track_station"));
		ServerLevel level = helper.getLevel();
		List<BlockPos> route = way.stream().map(helper::absolutePos).toList();
		helper.assertTrue(CaravanTrains.spur(level, helper.absolutePos(stationA), route, plan), "no spur from the Train Station's track to the road's");
		helper.assertTrue(CaravanTrains.spur(level, helper.absolutePos(stationB), route, plan), "the station by the road wants a spur");
		runs(helper, stationA, stationB, new BlockPos(6, 2, 9), "off the road");
	}

	/** A road that dips a block at x = 14 and comes back up: sloped curves down into the dip and up out of it, and the train runs over it. */
	//$ gametest_ticks_batch AREA '2400' '"createTrackDip"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "createTrackDip")
	public void aOneBlockDipGetsTrackAndTheTrainRunsOverIt(GameTestHelper helper) {
		IntUnaryOperator feet = x -> x == 14 ? 2 : 3;
		ground(helper, (x, z) -> z >= 10 && z <= 14 ? feet.applyAsInt(x) - 1 : 1);
		List<BlockPos> way = new ArrayList<>();
		for (int x = 1; x <= 28; x++) {
			way.add(new BlockPos(x, feet.applyAsInt(x), 12));
		}
		CaravanTrains.TrackPlan plan = lay(helper, way);
		helper.assertTrue(plan.curves().size() == 2, "not a slope down into the dip and one up out of it: " + plan.curves());
		helper.setBlock(new BlockPos(3, 3, 13), state("track_station"));
		helper.setBlock(new BlockPos(24, 3, 13), state("track_station"));
		runs(helper, new BlockPos(3, 3, 13), new BlockPos(24, 3, 13), new BlockPos(3, 3, 16), "over a dip");
	}

	/**
	 * A diagonal road as the road planner routes it: east along z = 5 to x = 8, then a zig-zag of single steps east and
	 * south to (16, 13), then east again: the zig-zag is one S-bend of track, and the train runs over it.
	 */
	//$ gametest_ticks_batch AREA '2400' '"createTrackDiagonal"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "createTrackDiagonal")
	public void aZigZagDiagonalRoadGetsTrackAndTheTrainRuns(GameTestHelper helper) {
		ground(helper, (x, z) -> 1);
		List<BlockPos> way = new ArrayList<>();
		for (int x = 1; x <= 8; x++) {
			way.add(new BlockPos(x, 2, 5));
		}
		int x = 8;
		int z = 5;
		while (z < 13) {
			way.add(new BlockPos(++x, 2, z));
			way.add(new BlockPos(x, 2, ++z));
		}
		for (x = 17; x <= 28; x++) {
			way.add(new BlockPos(x, 2, 13));
		}
		CaravanTrains.TrackPlan plan = lay(helper, way);
		helper.assertTrue(plan.shapes().keySet().stream().noneMatch(p -> p.getZ() > helper.absolutePos(BlockPos.ZERO).getZ() + 5
			&& p.getZ() < helper.absolutePos(BlockPos.ZERO).getZ() + 13), "straight bits left in the zig-zag: " + plan.shapes().keySet());
		helper.setBlock(new BlockPos(3, 2, 6), state("track_station"));
		helper.setBlock(new BlockPos(24, 2, 14), state("track_station"));
		runs(helper, new BlockPos(3, 2, 6), new BlockPos(24, 2, 14), new BlockPos(3, 2, 9), "on a diagonal road");
	}
}
