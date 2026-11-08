package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane-e-1108-0112, B1001's fix: "each village train is its own drawn train, one carriage, put together from its own
 * blocks only", and the wiki's Create page: the village "never touches a player's" train). Corners the bug's own tests
 * leave open: a player's bogey on the line not glued to anything yet (B1001's text: the village train then couldn't be
 * put together at all), a player's carriage standing right against the end of the village train's blocks (never taken; the village train waits for it to move), and a
 * player's own two-carriage train put together later at the same station (the fix limits Create's scan for the village
 * train's assembly only; a player's assembly there must scan the whole track as Create does).
 */
public class QaTrainOwnBlocks1108eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	private record Line(BlockPos a, BlockPos b, Map<BlockPos, BlockState> parts, BlockState bogey, BlockPos firstBogey, int dir, int end) {
		/** The block on the track {@code gap} blocks past the end of the village train's blocks. */
		BlockPos past(int gap) {
			return new BlockPos(end + gap * dir, firstBogey.getY(), firstBogey.getZ());
		}
	}

	/** Stations aimed and linked, then the village train's blocks put up at station a; null while Create's network isn't ready. */
	private static Line setUp(GameTestHelper helper, int tick) {
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(new BlockPos(6, 2, 13));
		BlockPos b = helper.absolutePos(new BlockPos(23, 2, 13));
		CaravanTrains.Railway r = CreateTrainCompatTests.railway();
		boolean aimed = r.aim(level, a, b, "Alpha") & r.aim(level, b, a, "Beta");
		if (!aimed || !r.linked(level, a, b)) {
			if (tick > 200) {
				helper.fail("setup: Create's railway doesn't know both stations on one network after 200 ticks");
			}
			return null;
		}
		Map<BlockPos, BlockState> parts = r.train(level, a, b);
		helper.assertTrue(parts != null, "setup: no room for the village train's blocks past the station");
		parts.forEach((pos, state) -> level.setBlockAndUpdate(pos, state));
		Map.Entry<BlockPos, BlockState> bogey = parts.entrySet().stream()
			.filter(e -> BuiltInRegistries.BLOCK.getKey(e.getValue().getBlock()).getPath().endsWith("bogey"))
			.findFirst().orElseThrow();
		int dir = Integer.signum(bogey.getKey().getX() - a.getX());
		int end = parts.keySet().stream().mapToInt(p -> p.getX() * dir).max().orElseThrow() * dir;
		return new Line(a, b, parts, bogey.getValue(), bogey.getKey(), dir, end);
	}

	/** A player's carriage: a bogey with a plank on it, glued together (Create refuses a bogey with nothing attached). */
	private static void carriage(ServerLevel level, BlockPos at, BlockState bogey, boolean glued) {
		level.setBlockAndUpdate(at, bogey);
		level.setBlockAndUpdate(at.above(), Blocks.OAK_PLANKS.defaultBlockState());
		if (glued) {
			level.addFreshEntity(new com.simibubi.create.content.contraptions.glue.SuperGlueEntity(level, AABB.encapsulatingFullBlocks(at, at.above())));
		}
	}

	/** How many carriages the (valid) train with this owner has; -1 without one. */
	private static int carriages(UUID owner) {
		for (com.simibubi.create.content.trains.entity.Train train : com.simibubi.create.Create.RAILWAYS.trains.values()) {
			if (owner.equals(train.owner) && !train.invalid) {
				return train.carriages.size();
			}
		}
		return -1;
	}

	private static void check(GameTestHelper helper, Line line, BlockPos players, UUID owner, Villager driver, String what) {
		ServerLevel level = helper.getLevel();
		CaravanTrains.Railway r = CreateTrainCompatTests.railway();
		UUID id = r.assemble(level, line.a(), line.b(), owner, driver);
		int n = carriages(owner);
		boolean bogeyStands = level.getBlockState(players).is(line.bogey().getBlock());
		boolean plankStands = level.getBlockState(players.above()).is(Blocks.OAK_PLANKS);
		r.scrap(level, owner);
		helper.assertTrue(id != null, "the station didn't put the village train together with " + what + " ("
			+ n + " carriages)");
		helper.assertTrue(n == 1 && bogeyStands && plankStands, "with " + what + ", the village train has " + n
			+ " carriages (it is one), the player's bogey " + (bogeyStands ? "still there" : "gone") + ", the plank on it "
			+ (plankStands ? "still there" : "gone"));
	}

	/** B1001's text: a player's bogey with nothing glued to it yet, further along the track, kept the village train from being put together. */
	//$ gametest_ticks_batch AREA '400' '"qaTrainOwnBlocksUnglued"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaTrainOwnBlocksUnglued")
	public void aPlayersBogeyNotYetGluedFurtherAlongTheTrackDoesntStopTheVillageTrain(GameTestHelper helper) {
		helper.assertTrue(CaravanTrains.RAILWAY.present(), "nothing fills in Create's trains with Create installed");
		CreateTrainCompatTests.line(helper);
		Villager driver = CreateTrainCompatTests.conductor(helper, new BlockPos(6, 2, 16));
		UUID owner = UUID.randomUUID();
		int[] tick = {0};
		helper.onEachTick(() -> {
			Line line = setUp(helper, ++tick[0]);
			if (line == null) {
				return;
			}
			BlockPos players = line.past(3);
			helper.assertTrue(helper.getLevel().getBlockState(players).isAir(), "setup: no room on the track for the player's bogey at " + players);
			carriage(helper.getLevel(), players, line.bogey(), false);
			check(helper, line, players, owner, driver, "a player's unglued bogey 3 blocks past its blocks");
			helper.succeed();
		});
	}

	/**
	 * The boundary: a player's carriage on the very next track block after the village train's last block. Create can't
	 * keep the two apart then (the station answers "not connected in order"), so the village train may wait; what must
	 * hold is that it never takes the player's carriage, and that it is put together, one carriage, once the player's
	 * carriage is moved off.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaTrainOwnBlocksTouching"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaTrainOwnBlocksTouching")
	public void aPlayersCarriageRightAgainstTheVillageTrainsEndIsNeverTaken(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(CaravanTrains.RAILWAY.present(), "nothing fills in Create's trains with Create installed");
		CreateTrainCompatTests.line(helper);
		Villager driver = CreateTrainCompatTests.conductor(helper, new BlockPos(6, 2, 16));
		UUID owner = UUID.randomUUID();
		int[] tick = {0};
		helper.onEachTick(() -> {
			Line line = setUp(helper, ++tick[0]);
			if (line == null) {
				return;
			}
			CaravanTrains.Railway r = CreateTrainCompatTests.railway();
			BlockPos players = line.past(1);
			helper.assertTrue(level.getBlockState(players).isAir(), "setup: no room on the track for the player's bogey at " + players);
			carriage(level, players, line.bogey(), true);
			UUID id = r.assemble(level, line.a(), line.b(), owner, driver);
			int n = carriages(owner);
			boolean bogeyStands = level.getBlockState(players).is(line.bogey().getBlock());
			boolean plankStands = level.getBlockState(players.above()).is(Blocks.OAK_PLANKS);
			r.scrap(level, owner);
			helper.assertTrue((id == null ? n == -1 : n == 1) && bogeyStands && plankStands, "with a player's carriage on the next block, the village train "
				+ (id == null ? "wasn't put together" : "was put together") + " with " + n + " carriages (one, or none while it waits), the player's bogey "
				+ (bogeyStands ? "still there" : "gone") + ", the plank on it " + (plankStands ? "still there" : "gone"));
			if (id != null) {
				helper.succeed();
				return;
			}
			// the player moves their carriage off the line: now the village train goes together
			level.getEntitiesOfClass(com.simibubi.create.content.contraptions.glue.SuperGlueEntity.class, new AABB(players).inflate(0.5))
				.forEach(net.minecraft.world.entity.Entity::discard);
			level.removeBlock(players.above(), false);
			level.removeBlock(players, false);
			helper.assertTrue(r.assemble(level, line.a(), line.b(), owner, driver) != null && carriages(owner) == 1,
				"the village train wasn't put together as one carriage once the player's carriage was moved off ("
					+ carriages(owner) + " carriages)");
			r.scrap(level, owner);
			helper.succeed();
		});
	}

	/**
	 * The fix shortens the station's bogey lists for the village train's assembly and puts them back: a player who later
	 * puts their own two-carriage train together at that station gets both carriages, as Create does everywhere else.
	 */
	//$ gametest_ticks_batch AREA '600' '"qaTrainOwnBlocksPlayerLater"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaTrainOwnBlocksPlayerLater")
	public void aPlayersTwoCarriageTrainPutTogetherLaterAtTheVillageStationKeepsBothCarriages(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(CaravanTrains.RAILWAY.present(), "nothing fills in Create's trains with Create installed");
		CreateTrainCompatTests.line(helper);
		Villager driver = CreateTrainCompatTests.conductor(helper, new BlockPos(6, 2, 16));
		UUID village = UUID.randomUUID();
		UUID player = UUID.randomUUID();
		int[] tick = {0};
		int[] assembled = {-1};
		Line[] kept = {null};
		helper.onEachTick(() -> {
			tick[0]++;
			CaravanTrains.Railway r = CreateTrainCompatTests.railway();
			if (kept[0] == null) {
				Line line = setUp(helper, tick[0]);
				if (line == null) {
					return;
				}
				helper.assertTrue(r.assemble(level, line.a(), line.b(), village, driver) != null && carriages(village) == 1,
					"setup: the village train wasn't put together as one carriage (" + carriages(village) + ")");
				driver.stopRiding(); // no Conductor at the controls: the train stays at the station
				kept[0] = line;
				assembled[0] = tick[0];
				return;
			}
			Line line = kept[0];
			if (!(level.getBlockEntity(line.a()) instanceof com.simibubi.create.content.trains.station.StationBlockEntity be)) {
				helper.fail("setup: the station at " + line.a() + " is gone");
				return;
			}
			if (carriages(village) != -1) {
				// a player takes the village train apart at the station, as Create lets anyone do with a stopped train
				be.tryDisassembleTrain(null);
				if (tick[0] - assembled[0] > 200) {
					helper.fail("setup: the village train couldn't be taken apart at its station in 200 ticks");
				}
				return;
			}
			// the player's own train: the same front carriage (seat, controls, bogey) and a second carriage 3 blocks behind it
			line.parts().forEach((pos, state) -> level.setBlockAndUpdate(pos, state));
			net.minecraft.world.level.levelgen.structure.BoundingBox box = net.minecraft.world.level.levelgen.structure.BoundingBox
				.encapsulatingPositions(line.parts().keySet()).orElseThrow();
			level.addFreshEntity(new com.simibubi.create.content.contraptions.glue.SuperGlueEntity(level, AABB.encapsulatingFullBlocks(
				new BlockPos(box.minX(), box.minY(), box.minZ()), new BlockPos(box.maxX(), box.maxY(), box.maxZ()))));
			BlockPos second = line.past(3);
			helper.assertTrue(level.getBlockState(second).isAir(), "setup: no room on the track for the player's second carriage at " + second);
			carriage(level, second, line.bogey(), true);
			be.enterAssemblyMode(null);
			be.assemble(player);
			int n = carriages(player);
			if (n > 0) {
				com.simibubi.create.content.trains.entity.Train train = com.simibubi.create.Create.RAILWAYS.trains.values().stream()
					.filter(t -> player.equals(t.owner)).findFirst().orElseThrow();
				train.invalid = true;
			}
			helper.assertTrue(n == 2, "a player's two-carriage train put together at the village's station after its village train has "
				+ n + " carriages (expected 2; -1: none; station says " + be.saveWithoutMetadata(level.registryAccess()).get("LastException") + ")");
			helper.succeed();
		});
	}
}
