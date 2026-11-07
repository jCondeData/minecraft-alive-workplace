package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane D, 2026-11-04): the height edge of B828/B849's rule ("a raider nearer another village with a raid under way,
 * within that village's reach, is that raid's"; a village's reach is RADIUS + 32 across and 48 up and down) through the
 * two places a player meets it: the hall's round, which ends a raid when none of its own raiders is left (fought off),
 * and {@code /workplace threat end}. The raider stands ABOVE the other village's hall here (QaRaidVertical1103d has it
 * below), 48 blocks (within that reach: the other raid's) and 49 blocks (out of it: this raid's). Nightly mutation run
 * 41623267594: {@code <= 48} widened to {@code <= 49} in VillageRaids.own survived.
 * <p>
 * The other village's hall is only a raid record (neither the round nor the command needs its block).
 */
public class QaRaidReachHeight1104dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation BANDITS = AliveWorkplace.id("bandits");

	/** Clears leftovers, sets the radius to 12 (reach 44 across, 48 up and down); puts it all back afterwards. */
	private static void setUp(GameTestHelper helper, BlockPos[] halls) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		AABB box = helper.getBounds();
		int radius = VillageHalls.RADIUS;
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, box.inflate(48), m -> m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			for (BlockPos hall : halls) {
				if (hall == null) {
					continue;
				}
				ThreatData.get(level).end(hall);
				ThreatData.get(level).forgetPast(hall);
				VillageRaids.forget(hall);
				if (level.getBlockState(hall).is(ModBlocks.VILLAGE_HALL)) {
					level.removeBlock(hall, false);
				}
			}
			VillageHalls.RADIUS = radius;
		});
		VillageHalls.RADIUS = 12;
	}

	private static void raids(GameTestHelper helper, BlockPos[] halls) {
		ServerLevel level = helper.getLevel();
		for (BlockPos hall : halls) {
			ThreatData.get(level).forgetPast(hall);
			ThreatData.get(level).begin(new ThreatData.Under(hall, BANDITS, 3, level.getGameTime() - 100));
			helper.assertTrue(ThreatData.get(level).raid(hall).isPresent(), "setup: expected a raid under way at " + hall.toShortString());
		}
	}

	private static Husk raider(GameTestHelper helper, BlockPos rel) {
		Husk husk = helper.spawn(EntityType.HUSK, rel);
		husk.setNoAi(true);
		husk.addTag(VillageRaids.TAG);
		return husk;
	}

	/**
	 * For the round: the raider at (15, 2, 15) is 40 blocks above the first hall (25 off each way, 2850 squared) and
	 * {@code above} blocks above the second (2304 or 2401 squared, nearer).
	 */
	private static Husk roundSetUp(GameTestHelper helper, int above, BlockPos[] halls) {
		setUp(helper, halls);
		BlockPos rel = new BlockPos(15, 2, 15);
		BlockPos at = helper.absolutePos(rel);
		halls[0] = at.offset(-25, -40, -25);
		halls[1] = at.below(above);
		raids(helper, halls);
		Husk husk = raider(helper, rel);
		ServerLevel level = helper.getLevel();
		helper.assertTrue(VillageRaids.raiders(level, halls[0]).contains(husk), "setup: the raider isn't within the first village's reach");
		helper.assertTrue(husk.blockPosition().getY() - halls[1].getY() == above, "setup: expected the raider " + above + " blocks above the second hall, is "
			+ (husk.blockPosition().getY() - halls[1].getY()));
		helper.assertTrue(husk.blockPosition().distSqr(halls[1]) < husk.blockPosition().distSqr(halls[0]), "setup: the raider isn't nearer the second hall");
		return husk;
	}

	/**
	 * 48 blocks above the second village's raided hall: within its reach and nearer it, so it's that raid's. The first
	 * village has no raider of its own left, so its round ends its raid as fought off (booked: not fled); the second raid
	 * and its raider stay.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaRaidReachRound48_1104d"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRaidReachRound48_1104d")
	public void aRaider48BlocksAboveAnotherRaidedHallDoesNotKeepThisRaidGoing(GameTestHelper helper) {
		BlockPos[] halls = new BlockPos[2];
		Husk husk = roundSetUp(helper, 48, halls);
		ServerLevel level = helper.getLevel();
		VillageRaids.tick(level, halls[0], 0, 0, 0, day -> { });
		helper.assertTrue(ThreatData.get(level).raid(halls[0]).isEmpty(),
			"the first village's raid went on at its round, kept going by a raider 48 blocks above another raided hall (that raid's)");
		List<ThreatData.Past> past = ThreatData.get(level).past(halls[0]);
		helper.assertTrue(past.size() == 1 && !past.get(0).fled(), "expected the first village's raid booked once as fought off, booked " + past);
		helper.assertTrue(husk.isAlive() && !husk.isRemoved(), "the other raid's raider was sent away by the first village's round");
		helper.assertTrue(ThreatData.get(level).raid(halls[1]).isPresent(), "the second village's raid ended at the first village's round");
		helper.succeed();
	}

	/**
	 * 49 blocks above the second village's raided hall: out of its reach, so the raider is still the first raid's, and at
	 * the first village's round its raid goes on.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaRaidReachRound49_1104d"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRaidReachRound49_1104d")
	public void aRaider49BlocksAboveAnotherRaidedHallKeepsThisRaidGoing(GameTestHelper helper) {
		BlockPos[] halls = new BlockPos[2];
		Husk husk = roundSetUp(helper, 49, halls);
		ServerLevel level = helper.getLevel();
		VillageRaids.tick(level, halls[0], 0, 0, 0, day -> { });
		helper.assertTrue(ThreatData.get(level).raid(halls[0]).isPresent(),
			"the first village's raid ended at its round though its raider (49 blocks above another raided hall, out of that one's reach) was still about; booked "
				+ ThreatData.get(level).past(halls[0]));
		helper.assertTrue(ThreatData.get(level).past(halls[0]).isEmpty(), "a raid still under way was booked as past: " + ThreatData.get(level).past(halls[0]));
		helper.assertTrue(husk.isAlive() && !husk.isRemoved(), "the raider was sent away at the round of a raid that goes on");
		helper.succeed();
	}

	/**
	 * For the command: the first hall is a real hall block at (0, 29, 0) of the area; the raider stands at (29, 2, 29),
	 * 27 below it and 29 off each way (2411 squared, within its reach), and {@code above} blocks straight above the
	 * second hall (2304 or 2401 squared, nearer).
	 */
	private static Husk commandSetUp(GameTestHelper helper, int above, BlockPos[] halls) {
		setUp(helper, halls);
		BlockPos relHall = new BlockPos(0, 29, 0);
		helper.setBlock(relHall, ModBlocks.VILLAGE_HALL);
		halls[0] = helper.absolutePos(relHall);
		if (helper.getLevel().getBlockEntity(halls[0]) instanceof VillageHallBlockEntity entity) {
			entity.setCustomName(Component.literal("Upton"));
		}
		BlockPos rel = new BlockPos(29, 2, 29);
		halls[1] = helper.absolutePos(rel).below(above);
		raids(helper, halls);
		Husk husk = raider(helper, rel);
		ServerLevel level = helper.getLevel();
		helper.assertTrue(VillageRaids.raiders(level, halls[0]).contains(husk), "setup: the raider isn't within the first village's reach");
		helper.assertTrue(husk.blockPosition().getY() - halls[1].getY() == above, "setup: expected the raider " + above + " blocks above the second hall");
		helper.assertTrue(husk.blockPosition().distSqr(halls[1]) < husk.blockPosition().distSqr(halls[0]), "setup: the raider isn't nearer the second hall");
		return husk;
	}

	private static List<String> threatEnd(GameTestHelper helper, BlockPos hall) {
		List<String> said = new ArrayList<>();
		ServerLevel level = helper.getLevel();
		level.getServer().getCommands().performPrefixedCommand(QaRaidCallOff1103cGameTests.op(level, hall.east(), said), "workplace threat end");
		helper.assertTrue(said.contains("Upton: the raid and the disaster under way are over."), "the operator was told: " + said);
		helper.assertTrue(ThreatData.get(level).raid(hall).isEmpty(), "the first village's raid is still under way after /workplace threat end");
		return said;
	}

	/** {@code /workplace threat end} at the first village leaves a raider 48 blocks above the second's hall: that raid's. */
	//$ gametest_ticks_batch AREA '100' '"qaRaidReachEnd48_1104d"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRaidReachEnd48_1104d")
	public void threatEndLeavesARaider48BlocksAboveAnotherRaidedHall(GameTestHelper helper) {
		BlockPos[] halls = new BlockPos[2];
		Husk husk = commandSetUp(helper, 48, halls);
		threatEnd(helper, halls[0]);
		helper.assertTrue(husk.isAlive() && !husk.isRemoved(),
			"/workplace threat end sent away a raider 48 blocks above another raided hall (within its reach and nearer it: that raid's)");
		helper.assertTrue(ThreatData.get(helper.getLevel()).raid(halls[1]).isPresent(), "the second village's raid ended with the first's");
		helper.succeed();
	}

	/** {@code /workplace threat end} at the first village sends away a raider 49 blocks above the second's hall: its own. */
	//$ gametest_ticks_batch AREA '100' '"qaRaidReachEnd49_1104d"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRaidReachEnd49_1104d")
	public void threatEndSendsAwayARaider49BlocksAboveAnotherRaidedHall(GameTestHelper helper) {
		BlockPos[] halls = new BlockPos[2];
		Husk husk = commandSetUp(helper, 49, halls);
		threatEnd(helper, halls[0]);
		helper.assertTrue(husk.isRemoved(),
			"/workplace threat end left a raider 49 blocks above another raided hall (out of that one's reach, so the called-off raid's)");
		helper.assertTrue(ThreatData.get(helper.getLevel()).raid(halls[1]).isPresent(), "the second village's raid ended with the first's");
		helper.succeed();
	}
}
