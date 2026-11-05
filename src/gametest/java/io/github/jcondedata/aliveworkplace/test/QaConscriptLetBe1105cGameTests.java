package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.MilitiaCombat;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.Conscription;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * QA tests for B954 (lane c 2026-11-05), written from the spec (docs/wiki/edicts.md): "A raider he can't get a block
 * closer to in 5 seconds he lets be for 10 seconds; one within 4 blocks (behind a fence) he stands by." So a raider he
 * let be that comes within 4 blocks is his again at once, and one that stays beyond 4 stays let be for the 10 seconds.
 *
 * <p>Every test strands the conscript on a 1x1 stone pillar (top at y = 11, he stands at (4, 12, 4)) and puts the raider
 * on a pillar of its own at his height, across a gap he can't cross. Helpers are {@link QaConscriptReach1104d2GameTests}'.
 */
public class QaConscriptLetBe1105cGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos POST = new BlockPos(4, 12, 4);

	/**
	 * The raider he let be (6 blocks off, across a gap) comes 3.5 blocks from him with a stone bridge between them,
	 * early in the let-be: he walks over and strikes it within 3 seconds, not after the 10-second let-be.
	 */
	//$ gametest_ticks_batch HUGE '700' '"qaConscriptLetBeClose1105c"'
	@GameTest(template = HUGE, timeoutTicks = 700, batch = "qaConscriptLetBeClose1105c")
	public void theRaiderHeLetBeComingThreeAndAHalfBlocksOffIsStruckSoon(GameTestHelper helper) {
		stage(helper, 10);
		Track track = new Track();
		Zombie[] raider = new Zombie[1];
		AtomicInteger arrivedAt = new AtomicInteger(-1);
		AtomicInteger hurtAt = new AtomicInteger(-1);
		helper.onEachTick(() -> {
			track.tick();
			if (track.firstStop() >= 0 && arrivedAt.get() < 0 && track.now - track.firstStop() >= 5) {
				for (int x = 5; x <= 8; x++) {
					helper.setBlock(new BlockPos(x, 11, 4), Blocks.STONE);
				}
				Vec3 to = helper.absoluteVec(new Vec3(8.0, 12, 4.5));
				raider[0].teleportTo(to.x, to.y, to.z);
				arrivedAt.set(track.now);
			}
			if (raider[0] != null && hurtAt.get() < 0 && raider[0].getHealth() < raider[0].getMaxHealth()) {
				hurtAt.set(track.now);
			}
		});
		helper.runAfterDelay(5, () -> {
			Villager farmer = start(helper, track, 1);
			raider[0] = QaConscriptReach1104d2GameTests.raider(helper, new BlockPos(10, 12, 4));
			helper.runAfterDelay(MilitiaCombat.GIVE_UP_TICKS + MilitiaCombat.IGNORE_TICKS + 200, () -> {
				int arrived = arrivedAt.get();
				int hurt = hurtAt.get();
				double d = Math.sqrt(farmer.distanceToSqr(raider[0]));
				raider[0].discard();
				VillageRaids.forget();
				helper.assertTrue(track.firstStop() >= 0, "the conscript on his pillar never let the raider across the gap be");
				helper.assertTrue(arrived >= 0, "the raider was never moved up to him");
				helper.assertTrue(arrived - track.firstStop() < MilitiaCombat.IGNORE_TICKS - 100,
					"the raider came up late in the let-be (" + (arrived - track.firstStop()) + " ticks in), so the test proves nothing");
				helper.assertTrue(hurt < 0 || hurt >= arrived, "the raider across the gap was struck before it came up");
				helper.assertTrue(hurt >= 0 && hurt - arrived <= 60, "the raider he let be came 3.5 blocks from the conscript over a bridge, "
					+ (arrived - track.firstStop()) + " ticks into the let-be, and he didn't strike it within 3 seconds: "
					+ (hurt < 0 ? "never struck" : "struck " + (hurt - arrived) + " ticks after it came")
					+ String.format(" (now %.1f blocks apart, fights started at %s)", d, track.starts));
				helper.succeed();
			});
		});
	}

	/**
	 * The raider he let be stays 5 blocks off across the gap: for the whole 10-second let-be he stays out of the fight
	 * (doesn't turn back to it early) and never strikes it.
	 */
	//$ gametest_ticks_batch HUGE '600' '"qaConscriptLetBeFive1105c"'
	@GameTest(template = HUGE, timeoutTicks = 600, batch = "qaConscriptLetBeFive1105c")
	public void aRaiderFiveBlocksOffAcrossAGapStaysLetBeForTheLetBe(GameTestHelper helper) {
		stage(helper, 9);
		Track track = new Track();
		helper.onEachTick(track::tick);
		helper.runAfterDelay(5, () -> {
			Villager farmer = start(helper, track, 1);
			Zombie raider = QaConscriptReach1104d2GameTests.raider(helper, new BlockPos(9, 12, 4));
			double start = Math.sqrt(farmer.distanceToSqr(raider));
			helper.runAfterDelay(MilitiaCombat.GIVE_UP_TICKS + MilitiaCombat.IGNORE_TICKS + 60, () -> {
				float health = raider.getHealth();
				double d = Math.sqrt(farmer.distanceToSqr(raider));
				BlockPos at = helper.relativePos(farmer.blockPosition());
				raider.discard();
				VillageRaids.forget();
				helper.assertTrue(start > 4.3 && start < 5.7, String.format("the raider isn't about 5 blocks off: %.1f", start));
				helper.assertTrue(track.starts.size() >= 1, "the conscript never went for the raider 5 blocks off, in sight");
				helper.assertTrue(track.firstStop() >= 0, "the conscript never let be a raider 5 blocks off he couldn't get closer to");
				int stop = track.firstStop();
				for (int s : track.starts) {
					helper.assertTrue(s <= stop || s >= stop + MilitiaCombat.IGNORE_TICKS - 10, "the conscript went back for the raider he let be, 5 blocks off and no closer, "
						+ (s - stop) + " ticks into a " + MilitiaCombat.IGNORE_TICKS + "-tick let-be (distance now " + String.format("%.1f", d) + ")");
				}
				helper.assertTrue(health == raider.getMaxHealth(), "the conscript struck a raider 5 blocks off across a gap: " + health);
				helper.assertTrue(at.equals(POST), "the conscript left his pillar: he's at " + at);
				helper.succeed();
			});
		});
	}

	/**
	 * The raider he let be comes 3 blocks off (still across the gap, out of his reach): he turns back to it and stands by
	 * it. It then backs off to 6 blocks: about 5 seconds later he lets it be again, and he never struck it.
	 */
	//$ gametest_ticks_batch HUGE '800' '"qaConscriptLetBeBackOff1105c"'
	@GameTest(template = HUGE, timeoutTicks = 800, batch = "qaConscriptLetBeBackOff1105c")
	public void aLetBeRaiderComingCloseThenBackingOffIsStoodByThenLetBeAgain(GameTestHelper helper) {
		stage(helper, 10);
		QaConscriptReach1104d2GameTests.pillar(helper, new BlockPos(7, 2, 4), 10);
		Track track = new Track();
		Zombie[] raider = new Zombie[1];
		AtomicInteger closeAt = new AtomicInteger(-1);
		AtomicInteger backAt = new AtomicInteger(-1);
		helper.onEachTick(() -> {
			track.tick();
			if (raider[0] == null) {
				return;
			}
			if (track.firstStop() >= 0 && closeAt.get() < 0 && track.now - track.firstStop() >= 5) {
				Vec3 to = helper.absoluteVec(new Vec3(7.5, 12, 4.5));
				raider[0].teleportTo(to.x, to.y, to.z);
				closeAt.set(track.now);
			}
			if (closeAt.get() >= 0 && backAt.get() < 0 && track.now - closeAt.get() >= 40) {
				Vec3 to = helper.absoluteVec(new Vec3(10.5, 12, 4.5));
				raider[0].teleportTo(to.x, to.y, to.z);
				backAt.set(track.now);
			}
		});
		helper.runAfterDelay(5, () -> {
			Villager farmer = start(helper, track, 1);
			raider[0] = QaConscriptReach1104d2GameTests.raider(helper, new BlockPos(10, 12, 4));
			helper.runAfterDelay(MilitiaCombat.GIVE_UP_TICKS + 5 + 40 + MilitiaCombat.GIVE_UP_TICKS + 150, () -> {
				int close = closeAt.get();
				int back = backAt.get();
				float health = raider[0].getHealth();
				raider[0].discard();
				VillageRaids.forget();
				helper.assertTrue(track.firstStop() >= 0, "the conscript never let the raider across the gap be");
				helper.assertTrue(close >= 0 && back >= 0, "the raider was never moved close and back");
				int again = track.startAfter(close - 1);
				helper.assertTrue(again >= 0 && again - close <= 25, "the raider he let be came 3 blocks off and he didn't turn back to it within 25 ticks: "
					+ (again < 0 ? "never" : (again - close) + " ticks") + " (fights started " + track.starts + ", stopped " + track.stops + ")");
				int letBeAgain = track.stopAfter(again);
				helper.assertTrue(letBeAgain < 0 || letBeAgain >= back, "the conscript let the raider be while it stood 3 blocks off ("
					+ (letBeAgain - close) + " ticks after it came): one within 4 blocks he stands by");
				helper.assertTrue(letBeAgain >= 0, "the raider backed off to 6 blocks, out of reach, and the conscript never let it be again (fights started "
					+ track.starts + ", stopped " + track.stops + ", backed off at " + back + ")");
				helper.assertTrue(letBeAgain - back <= MilitiaCombat.GIVE_UP_TICKS + 20, "the conscript took " + (letBeAgain - back)
					+ " ticks to let be a raider that backed off to 6 blocks out of reach");
				helper.assertTrue(health == raider[0].getMaxHealth(), "a raider he could never reach across the gap was struck: " + health);
				helper.succeed();
			});
		});
	}

	/**
	 * After the 10-second let-be, the raider still 6 blocks off across the gap: he goes for it again (not earlier, not
	 * much later), and 5 seconds on, still no closer, lets it be a second time. He isn't stuck at it all night.
	 */
	//$ gametest_ticks_batch HUGE '800' '"qaConscriptLetBeTimeout1105c"'
	@GameTest(template = HUGE, timeoutTicks = 800, batch = "qaConscriptLetBeTimeout1105c")
	public void afterTheLetBeAStillUnreachableRaiderIsTriedAgainThenLetBeAgain(GameTestHelper helper) {
		stage(helper, 10);
		Track track = new Track();
		helper.onEachTick(track::tick);
		helper.runAfterDelay(5, () -> {
			Villager farmer = start(helper, track, 1);
			Zombie raider = QaConscriptReach1104d2GameTests.raider(helper, new BlockPos(10, 12, 4));
			helper.runAfterDelay(2 * MilitiaCombat.GIVE_UP_TICKS + MilitiaCombat.IGNORE_TICKS + 150, () -> {
				float health = raider.getHealth();
				BlockPos at = helper.relativePos(farmer.blockPosition());
				raider.discard();
				VillageRaids.forget();
				String seen = " (fights started " + track.starts + ", stopped " + track.stops + ")";
				helper.assertTrue(track.firstStop() >= 0, "the conscript never let be the raider 6 blocks off across the gap" + seen);
				int stop = track.firstStop();
				int again = track.startAfter(stop);
				helper.assertTrue(again >= 0, "after the 10-second let-be the conscript never went for the raider again, still 6 blocks off in sight" + seen);
				helper.assertTrue(again >= stop + MilitiaCombat.IGNORE_TICKS - 10, "the conscript went back for a raider 6 blocks off only "
					+ (again - stop) + " ticks into a " + MilitiaCombat.IGNORE_TICKS + "-tick let-be" + seen);
				helper.assertTrue(again <= stop + MilitiaCombat.IGNORE_TICKS + 40, "the conscript took " + (again - stop)
					+ " ticks to go back for the raider after a " + MilitiaCombat.IGNORE_TICKS + "-tick let-be" + seen);
				int second = track.stopAfter(again);
				helper.assertTrue(second >= 0 && second - again <= MilitiaCombat.GIVE_UP_TICKS + 30, "tried again, the conscript didn't let the still unreachable raider be within "
					+ (MilitiaCombat.GIVE_UP_TICKS + 30) + " ticks: he's stuck at the edge" + seen);
				helper.assertTrue(health == raider.getMaxHealth(), "a raider 6 blocks off across a gap was struck: " + health);
				helper.assertTrue(at.equals(POST), "the conscript left his pillar: he's at " + at);
				helper.succeed();
			});
		});
	}

	/**
	 * While he lets be a raider 5 blocks off across the gap, a second raider stands 7 blocks off at the end of a bridge
	 * from his pillar. The let-be one is nearer, but he goes for the one he can reach: he walks over and strikes it, and
	 * the let-be one is never struck.
	 */
	//$ gametest_ticks_batch HUGE '700' '"qaConscriptLetBeTwo1105c"'
	@GameTest(template = HUGE, timeoutTicks = 700, batch = "qaConscriptLetBeTwo1105c")
	public void withARaiderLetBeNearerHeFightsTheOneHeCanReach(GameTestHelper helper) {
		stage(helper, 9);
		Track track = new Track();
		Zombie[] raiders = new Zombie[2];
		AtomicInteger placedAt = new AtomicInteger(-1);
		AtomicInteger hurtAt = new AtomicInteger(-1);
		helper.onEachTick(() -> {
			track.tick();
			if (raiders[0] != null && track.firstStop() >= 0 && placedAt.get() < 0 && track.now - track.firstStop() >= 5) {
				for (int z = 5; z <= 11; z++) {
					helper.setBlock(new BlockPos(4, 11, z), Blocks.STONE);
				}
				raiders[1] = QaConscriptReach1104d2GameTests.raider(helper, new BlockPos(4, 12, 11));
				placedAt.set(track.now);
			}
			if (raiders[1] != null && hurtAt.get() < 0 && raiders[1].getHealth() < raiders[1].getMaxHealth()) {
				hurtAt.set(track.now);
			}
		});
		helper.runAfterDelay(5, () -> {
			Villager farmer = start(helper, track, 2);
			raiders[0] = QaConscriptReach1104d2GameTests.raider(helper, new BlockPos(9, 12, 4));
			helper.runAfterDelay(MilitiaCombat.GIVE_UP_TICKS + 5 + 160, () -> {
				int placed = placedAt.get();
				int hurt = hurtAt.get();
				float letBeHealth = raiders[0].getHealth();
				double d = raiders[1] == null ? -1 : Math.sqrt(farmer.distanceToSqr(raiders[1]));
				raiders[0].discard();
				if (raiders[1] != null) {
					raiders[1].discard();
				}
				VillageRaids.forget();
				helper.assertTrue(track.firstStop() >= 0, "the conscript never let be the raider 5 blocks off across the gap");
				helper.assertTrue(placed >= 0, "the second raider was never placed");
				helper.assertTrue(hurt >= 0, String.format("with a raider he let be 5 blocks off, the conscript never struck the one 7 blocks"
					+ " off at the end of a bridge (now %.1f blocks from it, fights started %s, stopped %s)", d, track.starts, track.stops));
				helper.assertTrue(hurt - placed <= 140, "the conscript took " + (hurt - placed) + " ticks to strike a reachable raider 7 blocks off");
				helper.assertTrue(letBeHealth == raiders[0].getMaxHealth(), "the raider across the gap, which he can't reach, was struck: " + letBeHealth);
				helper.succeed();
			});
		});
	}

	/**
	 * The raider he let be is killed (by someone else) 1 second into the let-be: nothing breaks, and a fresh raider 6
	 * blocks off at the end of a bridge is fought and struck.
	 */
	//$ gametest_ticks_batch HUGE '700' '"qaConscriptLetBeDies1105c"'
	@GameTest(template = HUGE, timeoutTicks = 700, batch = "qaConscriptLetBeDies1105c")
	public void theRaiderHeLetBeDyingMidLetBeBreaksNothing(GameTestHelper helper) {
		stage(helper, 10);
		Track track = new Track();
		Zombie[] raiders = new Zombie[2];
		AtomicInteger killedAt = new AtomicInteger(-1);
		AtomicInteger placedAt = new AtomicInteger(-1);
		AtomicInteger hurtAt = new AtomicInteger(-1);
		helper.onEachTick(() -> {
			track.tick();
			if (raiders[0] != null && track.firstStop() >= 0 && killedAt.get() < 0 && track.now - track.firstStop() >= 20) {
				raiders[0].kill();
				killedAt.set(track.now);
			}
			if (killedAt.get() >= 0 && placedAt.get() < 0 && track.now - killedAt.get() >= 20) {
				for (int z = 5; z <= 10; z++) {
					helper.setBlock(new BlockPos(4, 11, z), Blocks.STONE);
				}
				raiders[1] = QaConscriptReach1104d2GameTests.raider(helper, new BlockPos(4, 12, 10));
				placedAt.set(track.now);
			}
			if (raiders[1] != null && hurtAt.get() < 0 && raiders[1].getHealth() < raiders[1].getMaxHealth()) {
				hurtAt.set(track.now);
			}
		});
		helper.runAfterDelay(5, () -> {
			Villager farmer = start(helper, track, 2);
			raiders[0] = QaConscriptReach1104d2GameTests.raider(helper, new BlockPos(10, 12, 4));
			helper.runAfterDelay(MilitiaCombat.GIVE_UP_TICKS + 5 + 20 + 20 + 160, () -> {
				int placed = placedAt.get();
				int hurt = hurtAt.get();
				boolean dead = !raiders[0].isAlive();
				if (raiders[1] != null) {
					raiders[1].discard();
				}
				raiders[0].discard();
				VillageRaids.forget();
				helper.assertTrue(track.firstStop() >= 0, "the conscript never let be the raider across the gap");
				helper.assertTrue(killedAt.get() >= 0 && dead, "the raider he let be was never killed");
				helper.assertTrue(placed >= 0, "the fresh raider was never placed");
				helper.assertTrue(farmer.isAlive(), "the conscript died");
				helper.assertTrue(hurt >= 0 && hurt - placed <= 140, "after the raider he let be died, the conscript didn't strike a fresh raider 6 blocks off"
					+ " at the end of a bridge within 7 seconds: " + (hurt < 0 ? "never struck" : (hurt - placed) + " ticks")
					+ " (fights started " + track.starts + ", stopped " + track.stops + ")");
				helper.succeed();
			});
		});
	}

	// ---- helpers ----

	/** Every transition of one conscript's fight, in test ticks. */
	static final class Track {
		Villager villager;
		int now;
		boolean was;
		final List<Integer> starts = new ArrayList<>();
		final List<Integer> stops = new ArrayList<>();

		void tick() {
			now++;
			if (villager == null) {
				return;
			}
			boolean fighting = MilitiaCombat.isFighting(villager);
			if (fighting && !was) {
				starts.add(now);
			} else if (!fighting && was) {
				stops.add(now);
			}
			was = fighting;
		}

		int firstStop() {
			return stops.isEmpty() ? -1 : stops.get(0);
		}

		/** The first fight start after tick {@code t}, or -1. */
		int startAfter(int t) {
			return starts.stream().filter(s -> s > t).findFirst().orElse(-1);
		}

		/** The first stop after tick {@code t}, or -1. */
		int stopAfter(int t) {
			return stops.stream().filter(s -> s > t).findFirst().orElse(-1);
		}
	}

	/** The village, its hall, the conscript's 1x1 pillar at (4, 4) and the raider's at ({@code raiderX}, 4), tops at y = 11. */
	private static void stage(GameTestHelper helper, int raiderX) {
		Leftovers.clear(helper);
		QaConscriptReach1104d2GameTests.village(helper, 32);
		Leftovers.halls(helper);
		helper.setBlock(QaConscriptReach1104d2GameTests.HALL, ModBlocks.VILLAGE_HALL);
		QaConscriptReach1104d2GameTests.pillar(helper, new BlockPos(4, 2, 4), 10);
		QaConscriptReach1104d2GameTests.pillar(helper, new BlockPos(raiderX, 2, 4), 10);
	}

	/** Conscription in force at night, the conscript on his pillar, a raid of {@code raiders} tracked; the track watches him. */
	private static Villager start(GameTestHelper helper, Track track, int raiders) {
		ServerLevel level = helper.getLevel();
		QaConscriptReach1104d2GameTests.conscription(helper, QaConscriptReach1104d2GameTests.HALL);
		Villager farmer = QaConscriptReach1104d2GameTests.conscript(helper, POST);
		VillageRaids.track(level, helper.absolutePos(QaConscriptReach1104d2GameTests.HALL), raiders);
		CivicEffects.forget();
		Conscription.forget();
		track.villager = farmer;
		return farmer;
	}
}
