package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.SandRamps;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-d-1121-1609), B726 (32.9 sand ramps), from the fix's own words: a raider held to a ramp is let go as "over
 * the wall" only if it was on the ramp's top or has the ramp's wall beside it, never by how far in from the ramp's foot
 * it is alone; and a climber whose way to the hall ends at another wall, or whose waiting spot is off the edge of the
 * land, still piles its ramp to the wall's top.
 * <ul>
 * <li>a ramp laid by hand against the back wall, raiders standing still: inside the yard two past the landing, let go;
 * outside the west and the east walls at the same depth (the bug's spot), and outside behind the ramp, never;</li>
 * <li>live husks: from outside the east wall's front corner (the bug's mirror) and from behind the back wall at the test
 * area's edge (the waiting spot off the land): the ramp reaches the wall's top.</li>
 * </ul>
 * The yard and its raiders are QaSandRampEdgeGameTests' (still husks, the B446 fix) and QaThreatFixesGameTests' (the
 * climbing husk, rampOver).
 */
public class QaSandRampCorners1121dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/**
	 * A ramp four high against the back wall (z = 4) at x = 14: its foot just outside at z = 3, the column it drops into
	 * z = 5. Still husks held to it: inside at (16.5, 7.5), two past the landing and two along from the ramp (the control,
	 * as QaSandRampEdgeGameTests' near husk), is let go; outside the west wall (3.5, 6.5) and outside the east wall
	 * (25.5, 6.5), as deep in from the foot but on the far side of a side wall, never are; nor is one outside right
	 * behind the ramp (16.5, 1.5).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaRampCorners1121dLetGo"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaRampCorners1121dLetGo")
	public void aRaiderOutsideASideWallIsNeverCountedOverTheBackWallsRamp(GameTestHelper helper) {
		Culture culture = QaSandRampEdgeGameTests.pilers(helper);
		Villager villager = QaSandRampEdgeGameTests.yard(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(QaSandRampEdgeGameTests.HALL);
		Mob inside = QaSandRampEdgeGameTests.still(helper, culture, villager, 16.5, 7.5);
		Map<String, Mob> outside = Map.of(
			"outside the west wall (3.5, 6.5)", QaSandRampEdgeGameTests.still(helper, culture, villager, 3.5, 6.5),
			"outside the east wall (25.5, 6.5)", QaSandRampEdgeGameTests.still(helper, culture, villager, 25.5, 6.5),
			"outside behind the ramp (16.5, 1.5)", QaSandRampEdgeGameTests.still(helper, culture, villager, 16.5, 1.5));
		List<Mob> all = new java.util.ArrayList<>(outside.values());
		all.add(inside);
		VillageRaids.track(level, hall, culture, all);
		Optional<ThreatData.Siege> laid = Sieges.siege(level, hall);
		helper.assertTrue(laid.isPresent(), "no siege on the yard");
		ThreatData.Siege siege = laid.get();
		// The ramp as a husk piles it: four high, each stage a whole ramp, outside in (the edge test's, mirrored).
		BlockPos foot = helper.absolutePos(new BlockPos(14, 2, 3));
		for (int s = 1; s <= 4; s++) {
			for (int d = s; d >= 1; d--) {
				BlockPos sand = foot.relative(Direction.SOUTH, -(d - 1)).above(s - d);
				level.setBlock(sand, Blocks.SAND.defaultBlockState(), 3);
				siege.sand.add(sand);
			}
		}
		siege.rampFeet.add(foot);
		siege.rampDirs.add(Direction.SOUTH.get3DDataValue());
		siege.ramped = true;
		SandRamps.forget(); // (the storm is restored from the siege's record)
		helper.onEachTick(() -> {
			outside.forEach((where, mob) -> helper.assertFalse(SandRamps.over(level, hall, mob.getUUID()),
				"the husk " + where + " was let go as over the back wall by its ramp at tick " + helper.getTick()));
			if (helper.getTick() >= 120) {
				helper.assertTrue(inside.onGround() && outside.values().stream().allMatch(Mob::onGround), "a husk isn't on the ground");
				helper.assertTrue(SandRamps.over(level, hall, inside.getUUID()),
					"the husk inside, two past the back ramp's landing, was never let go as over the wall (the control)");
				helper.succeed();
			}
		});
	}

	/**
	 * The bug's route mirrored: the husk comes from outside the east wall's front corner. Wherever its ramp stands, it
	 * reaches the wall's top (y = 5 for the four-high yard walls, more at the Stone Wall).
	 */
	//$ gametest_ticks_batch AREA '900' '"qaRampCorners1121dEast"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "qaRampCorners1121dEast")
	public void aClimberFromTheEastCornerPilesItsRampToTheTop(GameTestHelper helper) {
		QaThreatFixesGameTests.rampOver(helper, Map.of(), 25.5, 17.5, sand -> helper.assertTrue(sand.stream().anyMatch(p -> p.getY() >= 5),
			"ramped, but no sand at the wall's top: " + sand.stream().map(BlockPos::toShortString).toList()));
	}

	/**
	 * A husk behind the back wall near the test area's edge, off to the side of the villager inside: it piles its ramp
	 * at the back wall, running back to the area's edge, so its waiting spot two out from the ramp is off the land (the
	 * fix's second stall). The ramp stands at the back wall and reaches its top (y = 5 right against it).
	 */
	//$ gametest_ticks_batch AREA '900' '"qaRampCorners1121dBehind"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "qaRampCorners1121dBehind")
	public void aClimberBehindTheBackWallAtTheLandsEdgePilesItsRampToTheTop(GameTestHelper helper) {
		QaThreatFixesGameTests.rampOver(helper, Map.of(), 20.5, 1.5, sand -> {
			helper.assertTrue(!sand.isEmpty() && sand.stream().allMatch(p -> p.getZ() < 4),
				"the ramp isn't (all) behind the back wall: " + sand.stream().map(BlockPos::toShortString).toList());
			helper.assertTrue(sand.stream().anyMatch(p -> p.getY() == 5 && p.getZ() == 3),
				"the back wall's ramp isn't up to the wall's top: " + sand.stream().map(BlockPos::toShortString).toList());
		});
	}
}
