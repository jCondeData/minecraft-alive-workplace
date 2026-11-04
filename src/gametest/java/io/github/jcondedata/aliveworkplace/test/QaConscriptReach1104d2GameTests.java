package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.guard.MilitiaCombat;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.Conscription;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * QA tests for B926 (lane d 2026-11-04, second pass), written from the spec (docs/wiki/edicts.md): "A conscript goes for
 * the nearest raider he can see within 24 blocks, counting up and down as well as across. A raider he can't get a block
 * closer to in 5 seconds he lets be for 10 seconds; one within 4 blocks (behind a fence) he stands by." Covers what
 * {@link QaConscriptionBugs1104dGameTests} doesn't: another raider during the let-be, the raider after it with a way down,
 * a raider within 4 blocks he can't reach, and a long chase on flat ground.
 */
public class QaConscriptReach1104d2GameTests implements FabricGameTest {
	static final String HUGE = "aliveworkplace_test:huge_area";
	static final BlockPos HALL = new BlockPos(15, 2, 15);
	static final ResourceLocation CONSCRIPTION = AliveWorkplace.id("conscription");

	/**
	 * While a conscript stranded on a platform lets the raider at its foot be, a second raider that comes up onto his
	 * platform (2.8 blocks off, in sight) is fought: it gets hurt.
	 */
	//$ gametest_ticks_batch HUGE '600' '"qaConscriptSecondRaider1104d2"'
	@GameTest(template = HUGE, timeoutTicks = 600, batch = "qaConscriptSecondRaider1104d2")
	public void aStrandedConscriptFightsASecondRaiderOnHisPlatform(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper, 32);
		Leftovers.halls(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		platform(helper);
		Watch watch = new Watch();
		Zombie[] second = new Zombie[1];
		AtomicInteger spawnedAt = new AtomicInteger(-1);
		AtomicInteger secondHurtAt = new AtomicInteger(-1);
		helper.onEachTick(() -> {
			watch.tick();
			if (watch.stopped >= 0 && second[0] == null && watch.now - watch.stopped >= 5) {
				second[0] = raider(helper, new BlockPos(6, 12, 6));
				spawnedAt.set(watch.now);
			}
			if (second[0] != null && secondHurtAt.get() < 0 && second[0].getHealth() < second[0].getMaxHealth()) {
				secondHurtAt.set(watch.now);
			}
		});
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			conscription(helper, HALL);
			Villager farmer = conscript(helper, new BlockPos(4, 12, 4));
			Zombie below = raider(helper, new BlockPos(20, 2, 4));
			VillageRaids.track(level, helper.absolutePos(HALL), 2);
			CivicEffects.forget();
			Conscription.forget();
			watch.villager = farmer;
			helper.runAfterDelay(500, () -> {
				float belowHealth = below.getHealth();
				int spawned = spawnedAt.get();
				int hurt = secondHurtAt.get();
				below.discard();
				if (second[0] != null) {
					second[0].discard();
				}
				VillageRaids.forget();
				helper.assertTrue(watch.started >= 0, "the conscript never went for the raider below his platform");
				helper.assertTrue(watch.stopped >= 0, "the conscript never let the unreachable raider below be");
				helper.assertTrue(spawned >= 0, "the second raider was never placed");
				helper.assertTrue(belowHealth == below.getMaxHealth(), "the raider below the platform was struck: " + belowHealth);
				helper.assertTrue(hurt >= 0, "a second raider on the conscript's platform, 2.8 blocks off, was never struck while he let the one below be");
				helper.assertTrue(hurt - spawned <= 60, "the conscript took " + (hurt - spawned) + " ticks to strike a raider on his platform");
				helper.succeed();
			});
		});
	}

	/**
	 * After the 10-second let-be is over and a staircase gives the stranded conscript a way down, he goes for the raider
	 * he let be again, walks down and strikes it.
	 */
	//$ gametest_ticks_batch HUGE '1000' '"qaConscriptWayDown1104d2"'
	@GameTest(template = HUGE, timeoutTicks = 1000, batch = "qaConscriptWayDown1104d2")
	public void afterTheLetBeAConscriptGivenAWayDownStrikesTheRaider(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper, 32);
		Leftovers.halls(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		pillar(helper, new BlockPos(4, 2, 4), 10);
		Watch watch = new Watch();
		Zombie[] raider = new Zombie[1];
		AtomicInteger stairsAt = new AtomicInteger(-1);
		AtomicInteger hurtAt = new AtomicInteger(-1);
		helper.onEachTick(() -> {
			watch.tick();
			if (watch.stopped >= 0 && stairsAt.get() < 0 && watch.now - watch.stopped >= MilitiaCombat.IGNORE_TICKS - 20) {
				// A one-wide staircase from the pillar's top down to the ground, away from the raider.
				for (int i = 1; i <= 9; i++) {
					helper.setBlock(new BlockPos(4, 11 - i, 4 + i), Blocks.STONE);
				}
				stairsAt.set(watch.now);
			}
			if (raider[0] != null && hurtAt.get() < 0 && raider[0].getHealth() < raider[0].getMaxHealth()) {
				hurtAt.set(watch.now);
			}
		});
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			conscription(helper, HALL);
			Villager farmer = conscript(helper, new BlockPos(4, 12, 4));
			raider[0] = raider(helper, new BlockPos(10, 2, 4));
			VillageRaids.track(level, helper.absolutePos(HALL), 1);
			CivicEffects.forget();
			Conscription.forget();
			watch.villager = farmer;
			helper.runAfterDelay(950, () -> {
				int hurt = hurtAt.get();
				BlockPos at = helper.relativePos(farmer.blockPosition());
				raider[0].discard();
				VillageRaids.forget();
				helper.assertTrue(watch.started >= 0, "the conscript never went for the raider below his tower");
				helper.assertTrue(watch.stopped >= 0, "the conscript never let the raider below be while he had no way down");
				helper.assertTrue(stairsAt.get() >= 0, "the staircase was never built");
				helper.assertTrue(hurt < 0 || hurt > watch.stopped, "the raider was struck before the conscript let it be");
				helper.assertTrue(hurt >= 0, "given a way down after the let-be, the conscript never struck the raider (he's at " + at
					+ ", fighting again: " + (watch.again >= 0 ? (watch.again - watch.stopped) + " ticks after letting it be" : "never") + ")");
				helper.succeed();
			});
		});
	}

	/**
	 * A raider 3 blocks from a conscript penned in by a fence (he can't reach it or come a block closer) is never let
	 * be: well past the 5-second give-up he's still in the fight and still beside it.
	 */
	//$ gametest_ticks_batch HUGE '500' '"qaConscriptFence1104d2"'
	@GameTest(template = HUGE, timeoutTicks = 500, batch = "qaConscriptFence1104d2")
	public void aRaiderBehindAFenceWithinFourBlocksIsNeverLetBe(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper, 32);
		Leftovers.halls(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		// A fence pen round (4, 2, 4): the conscript inside can't get out or any closer.
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx != 0 || dz != 0) {
					helper.setBlock(new BlockPos(4 + dx, 2, 4 + dz), Blocks.OAK_FENCE);
				}
			}
		}
		Watch watch = new Watch();
		helper.onEachTick(watch::tick);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			conscription(helper, HALL);
			Villager farmer = conscript(helper, new BlockPos(4, 2, 4));
			Zombie zombie = raider(helper, new BlockPos(7, 2, 4));
			VillageRaids.track(level, helper.absolutePos(HALL), 1);
			CivicEffects.forget();
			Conscription.forget();
			watch.villager = farmer;
			// GIVE_UP_TICKS + IGNORE_TICKS + margin: a let-be would show as a stop.
			helper.runAfterDelay(MilitiaCombat.GIVE_UP_TICKS + MilitiaCombat.IGNORE_TICKS + 100, () -> {
				double d = Math.sqrt(farmer.distanceToSqr(zombie));
				boolean fighting = MilitiaCombat.isFighting(farmer);
				boolean armed = Conscription.isMilitiaSword(farmer.getMainHandItem());
				zombie.discard();
				VillageRaids.forget();
				helper.assertTrue(watch.started >= 0, "the conscript never went for the raider 3 blocks off, behind the fence");
				helper.assertTrue(d <= 4, String.format("the conscript isn't by the raider: %.1f blocks", d));
				helper.assertTrue(watch.stopped < 0, "the conscript let a raider within 4 blocks be, "
					+ (watch.stopped - watch.started) + " ticks into the fight (distance now " + String.format("%.1f", d) + ")");
				helper.assertTrue(fighting && armed, "the conscript beside a raider behind a fence isn't fighting (fighting: " + fighting + ", armed: " + armed + ")");
				helper.succeed();
			});
		});
	}

	/**
	 * A raider 20 blocks away across flat ground, which the conscript can reach, isn't given up on mid-chase: he gets
	 * there and strikes it, without ever leaving the fight.
	 */
	//$ gametest_ticks_batch HUGE '500' '"qaConscriptLongChase1104d2"'
	@GameTest(template = HUGE, timeoutTicks = 500, batch = "qaConscriptLongChase1104d2")
	public void aReachableRaiderTwentyBlocksAcrossIsChasedAndStruck(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper, 32);
		Leftovers.halls(helper);
		BlockPos hallAt = new BlockPos(15, 2, 27);
		helper.setBlock(hallAt, ModBlocks.VILLAGE_HALL);
		Watch watch = new Watch();
		Zombie[] raider = new Zombie[1];
		AtomicInteger hurtAt = new AtomicInteger(-1);
		helper.onEachTick(() -> {
			watch.tick();
			if (raider[0] != null && hurtAt.get() < 0 && raider[0].getHealth() < raider[0].getMaxHealth()) {
				hurtAt.set(watch.now);
			}
		});
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			conscription(helper, hallAt);
			Villager farmer = conscript(helper, new BlockPos(3, 2, 15));
			raider[0] = raider(helper, new BlockPos(23, 2, 15));
			double start = Math.sqrt(farmer.distanceToSqr(raider[0]));
			VillageRaids.track(level, helper.absolutePos(hallAt), 1);
			CivicEffects.forget();
			Conscription.forget();
			watch.villager = farmer;
			helper.runAfterDelay(450, () -> {
				int hurt = hurtAt.get();
				double d = Math.sqrt(farmer.distanceToSqr(raider[0]));
				raider[0].discard();
				VillageRaids.forget();
				helper.assertTrue(start > 19.5 && start < 24, String.format("the raider isn't about 20 blocks off: %.1f", start));
				helper.assertTrue(watch.started >= 0, String.format("the conscript never went for a raider %.1f blocks across flat ground", start));
				helper.assertTrue(hurt >= 0, String.format("the conscript never struck a reachable raider %.1f blocks off (now %.1f away, let it be: %s)",
					start, d, watch.stopped >= 0 ? (watch.stopped - watch.started) + " ticks in" : "no"));
				helper.assertTrue(watch.stopped < 0 || watch.stopped > hurt, "the conscript let a reachable raider be mid-chase, "
					+ (watch.stopped - watch.started) + " ticks in, before striking it");
				helper.succeed();
			});
		});
	}

	// ---- shared with QaConscriptReach1104d2BugTests ----

	/** Watches one conscript's fight: the tick it started, first stopped and started again. */
	static final class Watch {
		Villager villager;
		int now;
		int started = -1;
		int stopped = -1;
		int again = -1;

		void tick() {
			now++;
			if (villager == null) {
				return;
			}
			boolean fighting = MilitiaCombat.isFighting(villager);
			if (fighting && started < 0) {
				started = now;
			} else if (!fighting && started >= 0 && stopped < 0) {
				stopped = now;
			} else if (fighting && stopped >= 0 && again < 0) {
				again = now;
			}
		}
	}

	static void village(GameTestHelper helper, int size) {
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = size;
		long time = helper.getLevel().getDayTime();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			helper.getLevel().setDayTime(time);
			VillageRaids.forget();
			VillageNeeds.forget();
			CivicEffects.forget();
			Conscription.forget();
			Moods.forget();
		});
	}

	/** Conscription in force at the hall at {@code at}, a Village, at night. */
	static void conscription(GameTestHelper helper, BlockPos at) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(at);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setRank(VillageRanks.Rank.VILLAGE);
		entity.setReforms(List.of());
		entity.setRaidWorkUntil(0);
		entity.setEdicts(List.of(new Edicts.InForce(CONSCRIPTION.toString(), Chronicle.day(level))));
		Leftovers.after(helper, () -> {
			entity.setEdicts(List.of());
			level.setBlockAndUpdate(hall, Blocks.AIR.defaultBlockState());
		});
		level.setDayTime((Chronicle.day(level) - 1) * VillageNeeds.DAY + 14000);
	}

	static Villager conscript(GameTestHelper helper, BlockPos at) {
		Villager villager = helper.spawn(EntityType.VILLAGER, at);
		villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
		villager.setVillagerXp(10);
		villager.refreshBrain(helper.getLevel());
		return villager;
	}

	/** A raid zombie that stays put (no AI) and never despawns. */
	static Zombie raider(GameTestHelper helper, BlockPos at) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, at);
		zombie.setNoAi(true);
		zombie.setPersistenceRequired();
		zombie.addTag(VillageRaids.TAG);
		return zombie;
	}

	/** A stone pillar {@code height} blocks tall from {@code base} up (a villager on top can't walk off it). */
	static void pillar(GameTestHelper helper, BlockPos base, int height) {
		for (int y = 0; y < height; y++) {
			helper.setBlock(base.above(y), Blocks.STONE);
		}
	}

	/**
	 * A 5×5 stone platform at y = 11 (standing at y = 12) on a pillar at (4, 4), with no way down. A raider at its foot
	 * is hidden by the platform's edge, so the tests put theirs at (20, 2, 4): 18.9 blocks off, seen over the edge.
	 */
	static void platform(GameTestHelper helper) {
		pillar(helper, new BlockPos(4, 2, 4), 9);
		for (int x = 2; x <= 6; x++) {
			for (int z = 2; z <= 6; z++) {
				helper.setBlock(new BlockPos(x, 11, z), Blocks.STONE);
			}
		}
	}

	/** Moves {@code zombie} onto the platform 2 blocks from {@code villager}, on his level. */
	static void beside(GameTestHelper helper, Zombie zombie, Villager villager) {
		BlockPos rel = helper.relativePos(villager.blockPosition());
		int x = Math.max(2, Math.min(6, rel.getX()));
		int z = Math.max(2, Math.min(6, rel.getZ()));
		x = x <= 4 ? x + 2 : x - 2;
		Vec3 to = helper.absoluteVec(new Vec3(x + 0.5, 12, z + 0.5));
		zombie.teleportTo(to.x, to.y, to.z);
	}
}
