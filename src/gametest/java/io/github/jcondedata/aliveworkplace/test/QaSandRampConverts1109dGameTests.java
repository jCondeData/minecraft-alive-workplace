package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.SandRamps;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane D, 2026-11-09) for B1085's sand-ramp half (ROADMAP 32.9 sand ramps; B1085: "swap the converted mob into ...
 * the Ladders/SandRamps over sets"). The desert's raiders are husks, and a husk under water turns into a zombie with a
 * new UUID ({@code Husk.doUnderWaterConversion}: {@code convertTo(ZOMBIE)}). One that came over the wall by the ramp and
 * then turns must still be counted over the wall, and not be held to the ramp again. Uses QaSandRampEdgeGameTests' yard:
 * a ramp laid by hand against the front wall at x 7, and husks standing still inside where the ramp lets them go.
 */
public class QaSandRampConverts1109dGameTests {
	static final String AREA = "aliveworkplace_test:huge_area";

	/** The ramp as a husk piles it, four high, against the front wall at x 7 (as QaSandRampEdgeGameTests lays it). */
	private static ThreatData.Siege ramp(GameTestHelper helper, ServerLevel level, BlockPos hall) {
		Optional<ThreatData.Siege> laid = Sieges.siege(level, hall);
		helper.assertTrue(laid.isPresent(), "no siege on the yard");
		ThreatData.Siege siege = laid.get();
		BlockPos foot = helper.absolutePos(new BlockPos(7, 2, 16));
		for (int s = 1; s <= 4; s++) {
			for (int d = s; d >= 1; d--) {
				BlockPos sand = foot.relative(Direction.NORTH, -(d - 1)).above(s - d);
				level.setBlock(sand, Blocks.SAND.defaultBlockState(), 3);
				siege.sand.add(sand);
			}
		}
		siege.rampFeet.add(foot);
		siege.rampDirs.add(Direction.NORTH.get3DDataValue());
		siege.ramped = true;
		SandRamps.forget(); // (the storm is restored from the siege's record)
		return siege;
	}

	/**
	 * A husk let go past the ramp as over the wall turns into a zombie (as under water): from that tick the zombie is
	 * counted over the wall and the husk's old UUID is not, and for the next 100 ticks the zombie is never held to the
	 * ramp nor dropped from the ones over the wall. It stays one of the raid's raiders, tagged as such.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaSandRampConverts1109d"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaSandRampConverts1109d")
	public void aHuskOverTheWallByTheRampThatTurnsIsStillOver(GameTestHelper helper) {
		Culture culture = QaSandRampEdgeGameTests.pilers(helper);
		Villager villager = QaSandRampEdgeGameTests.yard(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(QaSandRampEdgeGameTests.HALL);
		Mob husk = QaSandRampEdgeGameTests.still(helper, culture, villager, 9.5, 12.5);
		Mob other = QaSandRampEdgeGameTests.still(helper, culture, villager, 15.5, 6.5); // (keeps the raid going, far from the ramp)
		VillageRaids.track(level, hall, culture, List.of(husk, other));
		ramp(helper, level, hall);
		Mob[] zombie = {null};
		UUID[] old = {null};
		long[] turned = {-1};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (turned[0] < 0) {
				if (SandRamps.over(level, hall, husk.getUUID()) && husk.onGround()) {
					old[0] = husk.getUUID();
					zombie[0] = husk.convertTo(EntityType.ZOMBIE, true);
					helper.assertTrue(zombie[0] != null && zombie[0].isAlive(), "convertTo made no zombie");
					helper.assertTrue(!zombie[0].getUUID().equals(old[0]), "the zombie kept the husk's UUID: the test would prove nothing");
					helper.assertTrue(SandRamps.over(level, hall, zombie[0].getUUID()), "the zombie the husk turned into isn't counted over the wall");
					helper.assertTrue(!SandRamps.over(level, hall, old[0]), "the husk that is gone is still counted over the wall");
					turned[0] = tick;
				} else if (tick > 200) {
					helper.fail("the husk past the ramp was never let go as over the wall (test setup): "
						+ Sieges.directorState(level, hall, husk));
				}
				return;
			}
			Mob z = zombie[0];
			helper.assertTrue(z.getTags().contains(Threats.RAIDER_TAG), "the zombie isn't a raider any more");
			helper.assertTrue(SandRamps.over(level, hall, z.getUUID()), (tick - turned[0]) + " ticks after it turned, the zombie isn't counted over the wall: "
				+ Sieges.directorState(level, hall, z));
			helper.assertTrue(!Sieges.directorState(level, hall, z).contains(", ramp true"), (tick - turned[0])
				+ " ticks after it turned, the zombie that was over the wall is held to the ramp again: " + Sieges.directorState(level, hall, z));
			if (tick - turned[0] >= 100) {
				helper.succeed();
			}
		});
	}
}
