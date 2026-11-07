package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane-e-1107-0713, Create trains 39.7a/39.7b/39.7d and B898; the cause of B939): a village between two train
 * neighbors on one straight Create line, both of its trains built by the builders before any Conductor is hired (a
 * train waits for its Conductor: "nobody free to drive it yet"). Once the three Conductors come, each pair's train is
 * put together at its own station from its own blocks and runs: expected one carriage each (the drawn village train is
 * one), neither derailed. B939's derail watcher showed otherwise: the first train put together took the other pair's
 * train blocks standing further along the same track as a second carriage, and the second train then put together on
 * that carriage derailed both.
 */
public class QaTrainHubBothBuilt1107eBugCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	/** The pair's train blocks all stand at the station of the village that puts it together. */
	private static boolean built(ServerLevel level, QaTrainHub1103eCompatTests.Three v, BlockPos x, BlockPos y) {
		BlockPos hall = CaravanTrains.first(x, y) ? x : y;
		BlockPos other = hall.equals(x) ? y : x;
		BlockPos ours = CaravanTrains.stationFor(level, hall, other, v.data());
		BlockPos theirs = CaravanTrains.stationFor(level, other, hall, v.data());
		return ours != null && theirs != null && CreateTrainCompatTests.railway().built(level, ours, theirs);
	}

	/** How many carriages the village train with this owner has; -1 without one. */
	private static int carriages(UUID owner) {
		for (com.simibubi.create.content.trains.entity.Train train : com.simibubi.create.Create.RAILWAYS.trains.values()) {
			if (owner.equals(train.owner) && !train.invalid) {
				return train.carriages.size();
			}
		}
		return -1;
	}

	//$ gametest_ticks_batch AREA '3000' '"qaTrainHubBothBuilt"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaTrainHubBothBuilt")
	public void bothTrainsBuiltBeforeTheConductorsComeRunAsOneCarriageEachAndNeitherDerails(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(CaravanTrains.RAILWAY.present(), "nothing fills in Create's trains with Create installed");
		QaTrainHub1103eCompatTests.Three v = QaTrainHub1103eCompatTests.villages(helper);
		UUID westId = CaravanTrains.railwayId(v.west(), v.hub());
		UUID eastId = CaravanTrains.railwayId(v.east(), v.hub());
		int[] tick = {0};
		int[] hired = {-1};
		String[] derail = {null};
		helper.onEachTick(() -> {
			tick[0]++;
			if (derail[0] == null && hired[0] >= 0) {
				String now = "west train " + QaTrainHub1103eCompatTests.where(westId) + "; east train " + QaTrainHub1103eCompatTests.where(eastId);
				if (now.contains("derailed true")) {
					derail[0] = "tick " + (tick[0] - hired[0]) + " after the Conductors came: " + now;
				}
			}
			if (tick[0] % 10 != 0) {
				return;
			}
			if (hired[0] < 0) {
				// the builders' part: both pairs' train blocks (and the hub's second station) put up, as QaTrainHub1103e's
				// round does, the halls' looks aiming the stations; nobody to drive them yet
				QaTrainHub1103eCompatTests.buildTrain(level, v.data(), v.west(), v.hub());
				QaTrainHub1103eCompatTests.buildTrain(level, v.data(), v.east(), v.hub());
				for (BlockPos h : v.halls()) {
					CaravanTrains.serve(level, h, (VillageHallBlockEntity) level.getBlockEntity(h));
				}
				helper.assertTrue(!CaravanTrains.realTrain(level, v.west(), v.hub()) && !CaravanTrains.realTrain(level, v.east(), v.hub()),
					"setup: a train was put together with no Conductor");
				if (built(level, v, v.west(), v.hub()) && built(level, v, v.east(), v.hub())) {
					hired[0] = tick[0];
					for (BlockPos at : List.of(new BlockPos(6, 2, 16), new BlockPos(15, 2, 16), new BlockPos(23, 2, 16))) {
						CreateTrainCompatTests.conductor(helper, at);
					}
				} else if (tick[0] > 1500) {
					helper.fail("setup: both pairs' train blocks weren't standing in 1500 ticks: west " + built(level, v, v.west(), v.hub())
						+ ", east " + built(level, v, v.east(), v.hub()));
				}
				return;
			}
			QaTrainHub1103eCompatTests.round(level, v);
			if (derail[0] != null) {
				helper.fail("the hub's trains derailed, " + derail[0] + " (carriages: west " + carriages(westId) + ", east " + carriages(eastId)
					+ "; the village train is one)");
			}
			boolean both = CaravanTrains.realTrain(level, v.west(), v.hub()) && CaravanTrains.realTrain(level, v.east(), v.hub());
			if (both && tick[0] - hired[0] >= 600) {
				helper.assertTrue(carriages(westId) == 1 && carriages(eastId) == 1, "a village train took the other's blocks: "
					+ carriages(westId) + " carriages in the west train, " + carriages(eastId) + " in the east; "
					+ QaTrainHub1103eCompatTests.trains(level, v));
				QaTrainHub1103eCompatTests.tidy(level, v);
				helper.succeed();
			} else if (tick[0] - hired[0] > 1200) {
				helper.fail("the hub's two trains weren't both running 1200 ticks after the Conductors came: "
					+ QaTrainHub1103eCompatTests.trains(level, v) + " (carriages: west " + carriages(westId) + ", east " + carriages(eastId) + ")");
			}
		});
	}

	/**
	 * The same with a player's train being built on the village's line (the wiki's Create page: the village "never
	 * touches a player's" train): the village train's blocks at its station, and further along the track past their
	 * end a player's bogey with a plank glued on it (a carriage of their own). Put together, the village train is its own drawn train, one carriage, and
	 * the player's bogey and plank still stand where they were.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaTrainPlayersBogey"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaTrainPlayersBogey")
	public void aVillageTrainPutTogetherLeavesAPlayersBogeyFurtherAlongTheTrack(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(CaravanTrains.RAILWAY.present(), "nothing fills in Create's trains with Create installed");
		CreateTrainCompatTests.line(helper);
		BlockPos a = helper.absolutePos(new BlockPos(6, 2, 13));
		BlockPos b = helper.absolutePos(new BlockPos(23, 2, 13));
		UUID owner = UUID.randomUUID();
		Villager driver = CreateTrainCompatTests.conductor(helper, new BlockPos(6, 2, 16));
		int[] tick = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			CaravanTrains.Railway r = CreateTrainCompatTests.railway();
			boolean aimed = r.aim(level, a, b, "Alpha") & r.aim(level, b, a, "Beta");
			if (!aimed || !r.linked(level, a, b)) {
				if (tick[0] > 200) {
					helper.fail("setup: Create's railway doesn't know both stations on one network after 200 ticks");
				}
				return;
			}
			java.util.Map<BlockPos, BlockState> parts = r.train(level, a, b);
			helper.assertTrue(parts != null, "setup: no room for the village train's blocks past the station");
			parts.forEach((pos, state) -> level.setBlockAndUpdate(pos, state));
			java.util.Map.Entry<BlockPos, BlockState> bogey = parts.entrySet().stream()
				.filter(e -> net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(e.getValue().getBlock()).getPath().endsWith("bogey"))
				.findFirst().orElseThrow();
			int dir = Integer.signum(bogey.getKey().getX() - a.getX());
			int end = parts.keySet().stream().mapToInt(p -> p.getX() * dir).max().orElseThrow() * dir;
			BlockPos players = new BlockPos(end + 3 * dir, bogey.getKey().getY(), bogey.getKey().getZ());
			helper.assertTrue(Math.abs(players.getX() - b.getX()) >= 1 && level.getBlockState(players).isAir(),
				"setup: no room on the track for the player's bogey at " + players + " (the other station at " + b + ")");
			level.setBlockAndUpdate(players, bogey.getValue());
			level.setBlockAndUpdate(players.above(), Blocks.OAK_PLANKS.defaultBlockState());
			// glued to its bogey, as a player builds a carriage (Create refuses a bogey with nothing attached)
			level.addFreshEntity(new com.simibubi.create.content.contraptions.glue.SuperGlueEntity(level,
				net.minecraft.world.phys.AABB.encapsulatingFullBlocks(players, players.above())));
			helper.assertTrue(r.assemble(level, a, b, owner, driver) != null, "the station didn't put the village train together");
			int n = carriages(owner);
			boolean bogeyStands = level.getBlockState(players).is(bogey.getValue().getBlock());
			boolean plankStands = level.getBlockState(players.above()).is(Blocks.OAK_PLANKS);
			r.scrap(level, owner);
			helper.assertTrue(n == 1 && bogeyStands && plankStands, "the village train took a player's bogey: " + n
				+ " carriages (the village train is one), the player's bogey " + (bogeyStands ? "still there" : "gone") + ", the plank on it "
				+ (plankStands ? "still there" : "gone"));
			helper.succeed();
		});
	}
}
