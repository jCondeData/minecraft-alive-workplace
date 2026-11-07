package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.legend.GolemPaths;
import io.github.jcondedata.aliveworkplace.legend.GolemRoleGoal;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.HaulerGolems;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane mutation check, 1103d) of the working golems' paths, killing two mutants that survived the nightly check:
 * <ul>
 * <li>{@code GolemPaths.besideSunkenFire} must look at the footing under a place (y - 1), not the block over it: a solid
 * floor with lava right under it is ground a hauler crosses, and an open hole with lava under it is avoided even when a
 * block hangs over the hole (B814).</li>
 * <li>{@code GolemRoleGoal.climbOut} must drop the bank it climbed out on the moment the golem stands on dry ground,
 * so the hauler takes up its walk at once instead of standing at the bank until the climb's time runs out (B783).</li>
 * </ul>
 * Each test is its own batch in the huge area and clears stray Storehouses about it first.
 */
public class QaGolemPaths1103dGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	static final String HUGE = "aliveworkplace_test:huge_area";
	private static final int TOP = QaHaulerWaterVerify1102GameTests.TOP;

	// ---- besideSunkenFire, asked directly ----

	/**
	 * A place on a one-thick stone floor laid over a lava lake (air above it) isn't beside sunken fire; take one floor block
	 * out under its footprint and it is. On solid ground with one open hole over lava beside the place, the hole counts even
	 * with a block hanging over it.
	 */
	//$ gametest_ticks_batch HUGE '20' '"qaGolemPaths1103dDirect"'
	@GameTest(template = HUGE, timeoutTicks = 20, batch = "qaGolemPaths1103dDirect")
	public void aFloorOverLavaIsNotSunkenFireButAnOpenHoleIs(GameTestHelper helper) {
		Leftovers.clear(helper);
		IronGolem golem = EntityType.IRON_GOLEM.create(helper.getLevel());
		helper.assertTrue(golem != null, "an iron golem");
		// a lava lake (x, z 2..9) at y 2, stone round it, a one-thick stone floor over it at y 3, air above
		for (int x = 1; x <= 10; x++) {
			for (int z = 1; z <= 10; z++) {
				boolean lake = x >= 2 && x <= 9 && z >= 2 && z <= 9;
				helper.setBlock(new BlockPos(x, 2, z), lake ? Blocks.LAVA : Blocks.STONE);
				helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
				for (int y = 4; y <= 7; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		BlockPos place = helper.absolutePos(new BlockPos(5, 4, 5));
		helper.assertFalse(GolemPaths.besideSunkenFire(helper.getLevel(), golem, place.getX(), place.getY(), place.getZ()),
			"a place on a solid stone floor with lava right under the floor counts as beside sunken fire: a hauler wouldn't cross the floor");
		helper.setBlock(new BlockPos(6, 3, 6), Blocks.AIR);
		helper.assertTrue(GolemPaths.besideSunkenFire(helper.getLevel(), golem, place.getX(), place.getY(), place.getZ()),
			"a place beside an open hole straight down into lava doesn't count as beside sunken fire");

		// solid stone ground (y 2 and 3) with one hole beside the place, lava at its bottom, and a block hanging over it
		for (int x = 12; x <= 21; x++) {
			for (int z = 1; z <= 10; z++) {
				helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
				helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
				for (int y = 4; y <= 7; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		BlockPos solid = helper.absolutePos(new BlockPos(16, 4, 5));
		helper.assertFalse(GolemPaths.besideSunkenFire(helper.getLevel(), golem, solid.getX(), solid.getY(), solid.getZ()),
			"a place on plain solid stone counts as beside sunken fire");
		helper.setBlock(new BlockPos(17, 2, 6), Blocks.LAVA);
		helper.setBlock(new BlockPos(17, 3, 6), Blocks.AIR);
		helper.setBlock(new BlockPos(17, 5, 6), Blocks.STONE);
		helper.assertTrue(GolemPaths.besideSunkenFire(helper.getLevel(), golem, solid.getX(), solid.getY(), solid.getZ()),
			"a place beside an open hole down into lava, with a block hanging over the hole, doesn't count as beside sunken fire");
		helper.succeed();
	}

	// ---- the same, as a hauler meets it ----

	/**
	 * The only Storehouse lies across a band of one-thick stone floor (x 10..15, the whole width of the raised ground) laid
	 * over lava: the hauler walks straight over it and stores all 64 logs, never stepping off the raised ground or into lava.
	 */
	//$ gametest_ticks_batch HUGE '1200' '"qaGolemPaths1103dFloorOverLava"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaGolemPaths1103dFloorOverLava")
	public void aHaulerCrossesAStoneFloorLaidOverLavaToTheOnlyStorehouse(GameTestHelper helper) {
		QaHaulerWaterVerify1102GameTests.setup(helper);
		for (int x = 10; x <= 15; x++) {
			for (int z = 1; z <= 25; z++) {
				helper.setBlock(new BlockPos(x, TOP - 2, z), Blocks.LAVA); // stone under it and round it, the floor over it
			}
		}
		BlockPos chest = new BlockPos(21, TOP, 12);
		helper.setBlock(new BlockPos(20, TOP, 12), ModBlocks.STOREHOUSE);
		helper.setBlock(chest, Blocks.CHEST);
		IronGolem golem = QaHaulerWaterVerify1102GameTests.hauler(helper, new BlockPos(5, TOP - 1, 12));
		ModAttachments.HAULER_LOAD.set(golem, Map.of(Items.OAK_LOG, 64));
		float health = golem.getHealth();
		helper.onEachTick(() -> {
			Vec3 at = helper.relativeVec(golem.position());
			if (golem.isInLava() || golem.isOnFire() || golem.getHealth() < health) {
				helper.fail("the hauler went into the lava under the floor at " + at + " on tick " + helper.getTick());
			}
			if (helper.getTick() > 5 && (at.y < TOP - 0.5 || at.x < -0.5 || at.x > 27.5 || at.z < -0.5 || at.z > 27.5)) {
				helper.fail("the hauler left the raised ground (to " + at + " on tick " + helper.getTick()
					+ ") instead of crossing the stone floor over the lava");
			}
		});
		helper.succeedWhen(() -> QaHaulerWaterVerify1102GameTests.delivered(helper, golem, chest, Map.of(Items.OAK_LOG, 64),
			"a stone floor over lava between the hauler and its Storehouse"));
	}

	// ---- climbOut: back to work the moment it stands on the bank ----

	@SuppressWarnings("unchecked")
	private static Map<IronGolem, double[]> exits() {
		try {
			java.lang.reflect.Field field = GolemRoleGoal.class.getDeclaredField("EXITS");
			field.setAccessible(true);
			return (Map<IronGolem, double[]>) field.get(null);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	/** The bank {@code golem} is climbing out of the water on (a climb, not a wade in), or null. */
	private static double[] climbing(IronGolem golem) {
		double[] exit = exits().get(golem);
		return exit != null && exit.length == 4 ? exit : null;
	}

	/**
	 * A loaded hauler pushed into a pool three deep a block below its rim climbs out onto the bank, and the moment it stands
	 * there on dry ground it drops the bank it climbed and walks on to its Storehouse: within a few ticks it has no climb
	 * left, within 60 it is two blocks on its way (the climb's own limit is 160 ticks), and all 64 logs get stored.
	 */
	//$ gametest_ticks_batch HUGE '1600' '"qaGolemPaths1103dClimbOut"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qaGolemPaths1103dClimbOut")
	public void aHaulerThatClimbsOutOfAPoolWalksOnAtOnce(GameTestHelper helper) {
		QaHaulerWaterVerify1102GameTests.setup(helper);
		for (int x = 3; x <= 8; x++) {
			for (int z = 3; z <= 8; z++) {
				QaHaulerWaterVerify1102GameTests.liquid(helper, x, z, 3, 1, Blocks.WATER);
			}
		}
		BlockPos chest = new BlockPos(20, TOP, 20);
		helper.setBlock(new BlockPos(19, TOP, 20), ModBlocks.STOREHOUSE);
		helper.setBlock(chest, Blocks.CHEST);
		IronGolem golem = QaHaulerWaterVerify1102GameTests.hauler(helper, new BlockPos(12, TOP - 1, 5));
		ModAttachments.HAULER_LOAD.set(golem, Map.of(Items.OAK_LOG, 64));
		// pushed in right by the east bank (the side toward its Storehouse), so its climb out takes far less than the 160 ticks
		// a climb may last: only then does dropping the bank late (when that time runs out) differ from dropping it at once
		helper.runAfterDelay(10, () -> {
			Vec3 in = helper.absoluteVec(new Vec3(8.2, 2.0, 7.5));
			golem.teleportTo(in.x, in.y, in.z);
			golem.setDeltaMovement(0, 0, 0);
		});
		long[] climbSeen = {-1};
		long[] landed = {-1};
		Vec3[] landedAt = {null};
		long[] onItsWay = {-1};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (tick <= 12) {
				return;
			}
			if (landed[0] < 0) {
				if (climbing(golem) != null && climbSeen[0] < 0) {
					climbSeen[0] = tick;
				}
				Vec3 at = helper.relativeVec(golem.position());
				if (climbSeen[0] >= 0 && golem.onGround() && !golem.isInWater() && at.y >= TOP - 0.01) {
					landed[0] = tick;
					landedAt[0] = golem.position();
				}
				return;
			}
			if (tick - landed[0] == 5) {
				double[] exit = climbing(golem);
				helper.assertTrue(exit == null, "5 ticks after the hauler stood on the bank at " + helper.relativeVec(landedAt[0]) + " (tick " + landed[0]
					+ ", the climb chosen by tick " + climbSeen[0] + ") it still holds the bank it climbed out on, so it stands there instead of walking on"
					+ " (on ground " + golem.onGround() + ", in water " + golem.isInWater() + ")");
			}
			if (onItsWay[0] < 0 && golem.position().distanceTo(landedAt[0]) >= 2.0) {
				onItsWay[0] = tick;
			}
			if (onItsWay[0] < 0 && tick - landed[0] > 60) {
				helper.fail("the hauler stood on the bank at " + helper.relativeVec(landedAt[0]) + " from tick " + landed[0] + " and 60 ticks later is still within"
					+ " two blocks of it, at " + helper.relativeVec(golem.position()) + ", instead of walking on to its Storehouse");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(climbSeen[0] >= 0, "the hauler never had to climb out of the pool (no climb chosen), so this tests nothing; at "
				+ helper.relativeVec(golem.position()) + " carrying " + HaulerGolems.load(golem));
			helper.assertTrue(landed[0] >= 0 && onItsWay[0] >= 0, "the hauler hasn't climbed out and walked on yet");
			QaHaulerWaterVerify1102GameTests.delivered(helper, golem, chest, Map.of(Items.OAK_LOG, 64), "climbed out of a pool a block below its rim");
		});
	}
}
