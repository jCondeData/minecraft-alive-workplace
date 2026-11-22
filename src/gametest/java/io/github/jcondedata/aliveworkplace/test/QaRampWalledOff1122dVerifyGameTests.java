package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.threat.SandRamps;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane-d-1122-1609) verifying B1493 (32.9 sand ramps; desert.md: "a second path then asked right up to the hall
 * says whether the climber can walk in round it, past a lamp post or through an open doorway beside the line, and then
 * it walks in and no sand is piled"), written from the spec. Both sides of the rule: ways in a climber can walk (a
 * doorway on the east wall, a one-wide two-high doorway on the west wall, several lamp posts and a pillar, a save and
 * load on the way) pile no sand; a gap only one high, and a yard whose wall stands right by the hall, still get a ramp
 * outside the wall. Setup as QaRampWalledOff1122dGameTests (hall at (14, 2, 14)).
 */
public class QaRampWalledOff1122dVerifyGameTests implements FabricGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("aliveworkplace-qa");

	private static void done(GameTestHelper helper, String what) {
		LOGGER.info("qa 1122d verify: {}", what);
		helper.succeed();
	}

	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = QaRampWalledOff1122dGameTests.HALL;
	private static final int R = QaRampWalledOff1122dGameTests.R;
	private static final int RAMP_BY = 900;

	private static String where(GameTestHelper helper, Mob mob) {
		return String.format("(%.2f, %.2f, %.2f)", helper.relativeVec(mob.position()).x, helper.relativeVec(mob.position()).y,
			helper.relativeVec(mob.position()).z);
	}

	private static List<String> rel(GameTestHelper helper, List<BlockPos> sand) {
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		return sand.stream().map(p -> p.subtract(base).toShortString()).toList();
	}

	/** The siege says ramped and the husk is counted over the ramp by {@link #RAMP_BY}; every block of sand passes {@code sandOk}. */
	private static void expectRampOver(GameTestHelper helper, QaRampWalledOff1122dGameTests.Yard yard, Mob husk, String what, Predicate<BlockPos> sandOk) {
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		boolean[] ramped = {false};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			List<BlockPos> sand = SandRamps.sand(yard.level(), yard.hall());
			for (BlockPos p : sand) {
				helper.assertTrue(sandOk.test(p.subtract(base)), what + ": sand at " + p.subtract(base).toShortString() + " isn't outside the wall: " + rel(helper, sand));
				helper.assertTrue(yard.level().getEntitiesOfClass(Mob.class, new AABB(p), m -> m == husk).isEmpty(),
					what + ": sand piled onto the husk at " + p.subtract(base).toShortString());
			}
			Optional<ThreatData.Siege> siege = Sieges.siege(yard.level(), yard.hall());
			if (siege.isPresent() && siege.get().ramped) {
				ramped[0] = true;
			}
			if (ramped[0] && SandRamps.over(yard.level(), yard.hall(), husk.getUUID())) {
				helper.succeed();
			} else if (tick > RAMP_BY) {
				helper.fail(what + ": " + (ramped[0] ? "the ramp stands but the husk isn't over it" : "no ramp in time") + " by tick " + tick + ": piled "
					+ rel(helper, sand) + ", the husk at " + where(helper, husk) + (husk.isAlive() ? "" : " (dead)"));
			}
		});
	}

	/** Opens a hole in the wall at ({@code x}, {@code z}) from y 2 up to {@code top} (inclusive). */
	private static void hole(GameTestHelper helper, int x, int z, int top) {
		for (int y = 2; y <= top; y++) {
			helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
		}
	}

	/**
	 * The edge yard (stone bricks four high, four out from the hall) with a doorway two wide, open full height, in the
	 * EAST wall (x 18) at z 16 and 17, two south of the husk's line to the hall: the husk comes from the east and walks in.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaWalledOffVerify1122dEast"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaWalledOffVerify1122dEast")
	public void qaAClimberBesideAnEastDoorwayWalksInWithNoSand(GameTestHelper helper) {
		QaRampWalledOff1122dGameTests.Yard yard = QaRampWalledOff1122dGameTests.village(helper);
		QaRampWalledOff1122dGameTests.walls(helper, R, 4, Blocks.STONE_BRICKS);
		hole(helper, HALL.getX() + R, 16, 5);
		hole(helper, HALL.getX() + R, 17, 5);
		Mob husk = QaRampWalledOff1122dGameTests.husk(helper, yard, 22.5, 14.5);
		QaRampWalledOff1122dGameTests.expectNoRamp(helper, yard, husk, "doorway in the east wall two blocks south");
	}

	/**
	 * A doorway only one wide and two high (a house door's size, the lintel above it) in the WEST wall (x 10) at z 12, two
	 * north of the husk's line: a husk fits through, so it walks in and piles no sand.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaWalledOffVerify1122dNarrow"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaWalledOffVerify1122dNarrow")
	public void qaAClimberBesideAOneWideTwoHighDoorwayWalksInWithNoSand(GameTestHelper helper) {
		QaRampWalledOff1122dGameTests.Yard yard = QaRampWalledOff1122dGameTests.village(helper);
		QaRampWalledOff1122dGameTests.walls(helper, R, 4, Blocks.STONE_BRICKS);
		hole(helper, HALL.getX() - R, 12, 3);
		Mob husk = QaRampWalledOff1122dGameTests.husk(helper, yard, 6.5, 14.5);
		QaRampWalledOff1122dGameTests.expectNoRamp(helper, yard, husk, "one-wide two-high doorway in the west wall");
	}

	/**
	 * A gap only one high (y 2 open, the wall above it) in the north wall at x 16: no husk fits through, so it can't walk
	 * in, and a ramp goes up outside the north wall and the husk gets over.
	 */
	//$ gametest_ticks_batch AREA '1000' '"qaWalledOffVerify1122dLowGap"'
	@GameTest(template = AREA, timeoutTicks = 1000, batch = "qaWalledOffVerify1122dLowGap")
	public void qaAGapOnlyOneHighIsNoWayInSoTheClimberRamps(GameTestHelper helper) {
		QaRampWalledOff1122dGameTests.Yard yard = QaRampWalledOff1122dGameTests.village(helper);
		QaRampWalledOff1122dGameTests.walls(helper, R, 4, Blocks.STONE_BRICKS);
		hole(helper, 16, HALL.getZ() - R, 2);
		Mob husk = QaRampWalledOff1122dGameTests.husk(helper, yard, 14.5, 6.5);
		expectRampOver(helper, yard, husk, "one-high gap in the north wall", p -> p.getZ() < HALL.getZ() - R);
	}

	/**
	 * No walls: three lamp posts (oak fence and glowstone, two high) two apart across the husk's line at z 11, and a stone
	 * pillar two high right behind the middle one at z 12. Posts and a pillar in an open village are no wall: no sand.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaWalledOffVerify1122dPosts"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaWalledOffVerify1122dPosts")
	public void qaLampPostsAndAPillarInAnOpenVillageGetNoRamp(GameTestHelper helper) {
		QaRampWalledOff1122dGameTests.Yard yard = QaRampWalledOff1122dGameTests.village(helper);
		for (int x : new int[] {12, 14, 16}) {
			helper.setBlock(new BlockPos(x, 2, 11), Blocks.OAK_FENCE);
			helper.setBlock(new BlockPos(x, 3, 11), Blocks.GLOWSTONE);
		}
		helper.setBlock(new BlockPos(14, 2, 12), Blocks.STONE_BRICKS);
		helper.setBlock(new BlockPos(14, 3, 12), Blocks.STONE_BRICKS);
		Mob husk = QaRampWalledOff1122dGameTests.husk(helper, yard, 14.5, 5.5);
		QaRampWalledOff1122dGameTests.expectNoRamp(helper, yard, husk, "three lamp posts and a pillar");
	}

	/**
	 * Regression guard for B1445/B1479: a closed yard whose walls (four high) stand only two out from the hall, so the hall
	 * is just inside them and the husk's path ends well within five of it. No way in: a ramp outside the north wall, and over.
	 */
	//$ gametest_ticks_batch AREA '1000' '"qaWalledOffVerify1122dTight"'
	@GameTest(template = AREA, timeoutTicks = 1000, batch = "qaWalledOffVerify1122dTight")
	public void qaATightYardRightRoundTheHallStillGetsARamp(GameTestHelper helper) {
		QaRampWalledOff1122dGameTests.Yard yard = QaRampWalledOff1122dGameTests.village(helper);
		QaRampWalledOff1122dGameTests.walls(helper, 2, 4, Blocks.STONE_BRICKS);
		Mob husk = QaRampWalledOff1122dGameTests.husk(helper, yard, 14.5, 7.5);
		int in = 2;
		expectRampOver(helper, yard, husk, "walls two out from the hall", p -> p.getX() < HALL.getX() - in || p.getX() > HALL.getX() + in
			|| p.getZ() < HALL.getZ() - in || p.getZ() > HALL.getZ() + in);
	}

	/**
	 * A closed oak door (one wide, two high) in the north wall at x 16. The spec doesn't say whether a husk counts a shut
	 * door as a way in, so only what it does say is checked: whatever sand is piled goes against the wall outside, never
	 * inside the yard, and none onto the husk, for 400 ticks.
	 */
	//$ gametest_ticks_batch AREA '500' '"qaWalledOffVerify1122dDoor"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qaWalledOffVerify1122dDoor")
	public void qaAShutDoorInTheWallNeverGetsSandInsideTheYard(GameTestHelper helper) {
		QaRampWalledOff1122dGameTests.Yard yard = QaRampWalledOff1122dGameTests.village(helper);
		QaRampWalledOff1122dGameTests.walls(helper, R, 4, Blocks.STONE_BRICKS);
		BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.OPEN, false);
		helper.setBlock(new BlockPos(16, 2, HALL.getZ() - R), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		helper.setBlock(new BlockPos(16, 3, HALL.getZ() - R), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		Mob husk = QaRampWalledOff1122dGameTests.husk(helper, yard, 14.5, 6.5);
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		helper.onEachTick(() -> {
			List<BlockPos> sand = SandRamps.sand(yard.level(), yard.hall());
			for (BlockPos p : sand) {
				BlockPos r = p.subtract(base);
				boolean inside = r.getX() >= HALL.getX() - R && r.getX() <= HALL.getX() + R && r.getZ() >= HALL.getZ() - R && r.getZ() <= HALL.getZ() + R;
				helper.assertTrue(!inside, "shut door: sand at " + r.toShortString() + " is in the yard or its wall: " + rel(helper, sand));
				helper.assertTrue(yard.level().getEntitiesOfClass(Mob.class, new AABB(p), m -> m == husk).isEmpty(), "shut door: sand piled onto the husk at "
					+ r.toShortString());
			}
			if (helper.getTick() >= 400) {
				done(helper, "shut door: sand " + rel(helper, sand) + ", the husk at " + where(helper, husk));
			}
		});
	}

	/**
	 * The north-wall doorway of the filed B1493 case (two wide, full height, at x 16 and 17), with the siege saved and loaded
	 * (the ramps' and sieges' memory forgotten, as a restart does) at tick 20, while the husk is on its way: still no sand.
	 */
	//$ gametest_ticks_batch AREA '500' '"qaWalledOffVerify1122dReload"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qaWalledOffVerify1122dReload")
	public void qaAClimberBesideADoorwayStillWalksInAfterASaveAndLoad(GameTestHelper helper) {
		QaRampWalledOff1122dGameTests.Yard yard = QaRampWalledOff1122dGameTests.village(helper);
		QaRampWalledOff1122dGameTests.walls(helper, R, 4, Blocks.STONE_BRICKS);
		hole(helper, 16, HALL.getZ() - R, 5);
		hole(helper, 17, HALL.getZ() - R, 5);
		Mob husk = QaRampWalledOff1122dGameTests.husk(helper, yard, 14.5, 6.5);
		boolean[] reloaded = {false};
		helper.onEachTick(() -> {
			if (!reloaded[0] && helper.getTick() >= 20) {
				reloaded[0] = true;
				ThreatData.get(yard.level()).roundTrip(yard.level().registryAccess());
				SandRamps.forget();
				Sieges.forget();
				helper.assertTrue(Sieges.siege(yard.level(), yard.hall()).isPresent(), "no siege after the save and load");
			}
			List<BlockPos> sand = SandRamps.sand(yard.level(), yard.hall());
			helper.assertTrue(sand.isEmpty(), "after a save and load: sand piled by tick " + helper.getTick() + ": " + rel(helper, sand) + ", the husk at "
				+ where(helper, husk));
			if (helper.getTick() >= 320) {
				helper.succeed();
			}
		});
	}
}
