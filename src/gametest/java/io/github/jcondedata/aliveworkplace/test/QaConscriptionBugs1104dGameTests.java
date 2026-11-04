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
import java.util.concurrent.atomic.AtomicBoolean;
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

/**
 * QA bug tests for Conscription (30.10), lane d 2026-11-04, with B926's fix (lane a): a conscript's reach is the same
 * 24-block sphere up and down as across, he gives up on a raider he can't get to, and the edicts switch still turns it off.
 */
public class QaConscriptionBugs1104dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final ResourceLocation CONSCRIPTION = AliveWorkplace.id("conscription");

	/**
	 * "Goes for the nearest raider within 24 blocks": a conscript up on a 10-block tower with a raider 6 blocks out from its
	 * foot (11.7 blocks away, in plain sight) is drawn into the fight. The search box is only 8 blocks tall each way
	 * ({@code MilitiaCombat#findFoe}), so a raider more than 8 blocks below or above is never seen, though the reformed
	 * work stop ({@code Conscription#raiderNear}) counts the same raider as near.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaConscriptBelow1104d"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaConscriptBelow1104d")
	public void aConscriptOnATowerFightsARaiderTenBlocksDown(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
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
		Leftovers.halls(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		for (int y = 2; y <= 11; y++) {
			helper.setBlock(new BlockPos(4, y, 4), Blocks.STONE);
		}
		AtomicBoolean fought = new AtomicBoolean();
		Villager[] watched = new Villager[1];
		// (registered at the start: onEachTick from inside a delayed step grows the map the test is iterating)
		helper.onEachTick(() -> fought.compareAndSet(false, watched[0] != null && MilitiaCombat.isFighting(watched[0])));
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
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
			Villager farmer = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 12, 4));
			farmer.setVillagerData(farmer.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
			farmer.setVillagerXp(10);
			farmer.refreshBrain(level);
			Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(10, 2, 4));
			zombie.setNoAi(true);
			zombie.setPersistenceRequired();
			zombie.addTag(VillageRaids.TAG);
			VillageRaids.forget();
			VillageRaids.track(level, hall, 1);
			CivicEffects.forget();
			Conscription.forget();
			watched[0] = farmer;
			helper.runAfterDelay(150, () -> {
				double d = Math.sqrt(farmer.distanceToSqr(zombie));
				boolean sight = farmer.hasLineOfSight(zombie);
				boolean near = Conscription.raiderNear(level, farmer, 24);
				boolean armed = Conscription.isMilitiaSword(farmer.getMainHandItem());
				zombie.discard();
				VillageRaids.forget();
				helper.assertTrue(armed, "the conscript on the tower isn't armed: the raid wasn't staged");
				helper.assertTrue(fought.get(), String.format("a conscript on a tower never fought the raider %.1f blocks below him (in sight: %s; the reformed work stop counts it as near: %s)", d, sight, near));
				helper.succeed();
			});
		});
	}

	/**
	 * B926, both sides of 24 blocks counting the height: two conscripts 15 blocks up on pillars, one with a raider 18
	 * across (23.4 blocks away) is drawn into the fight, the other with one 20 across (25 away) never is; the reformed
	 * work stop's "near" agrees with each.
	 */
	//$ gametest_ticks_batch HUGE '400' '"qaConscriptHeight1104d"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qaConscriptHeight1104d")
	public void aRaiderWithin24BlocksBelowDrawsAConscriptOneBeyondDoesNot(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper, 32);
		Leftovers.halls(helper);
		BlockPos hallAt = new BlockPos(15, 2, 15);
		helper.setBlock(hallAt, ModBlocks.VILLAGE_HALL);
		pillar(helper, new BlockPos(2, 2, 4), 15);
		pillar(helper, new BlockPos(2, 2, 24), 15);
		AtomicBoolean nearFought = new AtomicBoolean();
		AtomicBoolean farFought = new AtomicBoolean();
		Villager[] watched = new Villager[2];
		helper.onEachTick(() -> {
			nearFought.compareAndSet(false, watched[0] != null && MilitiaCombat.isFighting(watched[0]));
			farFought.compareAndSet(false, watched[1] != null && MilitiaCombat.isFighting(watched[1]));
		});
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			conscription(helper, hallAt);
			Villager near = conscript(helper, new BlockPos(2, 17, 4));
			Villager far = conscript(helper, new BlockPos(2, 17, 24));
			Zombie nearRaider = raider(helper, new BlockPos(20, 2, 4));
			Zombie farRaider = raider(helper, new BlockPos(22, 2, 24));
			VillageRaids.track(level, helper.absolutePos(hallAt), 2);
			CivicEffects.forget();
			Conscription.forget();
			watched[0] = near;
			watched[1] = far;
			helper.runAfterDelay(150, () -> {
				double n = Math.sqrt(near.distanceToSqr(nearRaider));
				double f = Math.sqrt(far.distanceToSqr(farRaider));
				boolean nearCounted = Conscription.raiderNear(level, near, 24);
				boolean farCounted = Conscription.raiderNear(level, far, 24);
				boolean armed = Conscription.militia(near) != null && Conscription.militia(far) != null;
				nearRaider.discard();
				farRaider.discard();
				VillageRaids.forget();
				helper.assertTrue(armed, "not both conscripts in the raid");
				helper.assertTrue(n < 24 && f > 24, String.format("the raiders aren't either side of 24 blocks: %.1f and %.1f", n, f));
				helper.assertTrue(nearFought.get(), String.format("a raider %.1f blocks away (15 below) never drew the conscript", n));
				helper.assertTrue(!farFought.get(), String.format("a raider %.1f blocks away (15 below) drew the conscript", f));
				helper.assertTrue(nearCounted && !farCounted, "the work stop's near disagrees with the fight: " + nearCounted + ", " + farCounted);
				helper.succeed();
			});
		});
	}

	/**
	 * B926: a conscript on a 10-block tower with no way down goes for the raider at its foot, can't get any closer, and
	 * lets it be within about 5 seconds (back to his evening, not stuck at the edge), then leaves it be for a while.
	 */
	//$ gametest_ticks_batch AREA '500' '"qaConscriptStranded1104d"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qaConscriptStranded1104d")
	public void aConscriptWhoCantGetDownLetsTheRaiderBe(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper, 16);
		Leftovers.halls(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		pillar(helper, new BlockPos(4, 2, 4), 10);
		AtomicInteger started = new AtomicInteger(-1);
		AtomicInteger stopped = new AtomicInteger(-1);
		AtomicInteger again = new AtomicInteger(-1);
		AtomicInteger tick = new AtomicInteger();
		Villager[] watched = new Villager[1];
		helper.onEachTick(() -> {
			int now = tick.incrementAndGet();
			if (watched[0] == null) {
				return;
			}
			boolean fighting = MilitiaCombat.isFighting(watched[0]);
			if (fighting && started.get() < 0) {
				started.set(now);
			} else if (!fighting && started.get() >= 0 && stopped.get() < 0) {
				stopped.set(now);
			} else if (fighting && stopped.get() >= 0 && again.get() < 0) {
				again.set(now);
			}
		});
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			conscription(helper, HALL);
			Villager farmer = conscript(helper, new BlockPos(4, 12, 4));
			Zombie zombie = raider(helper, new BlockPos(10, 2, 4));
			VillageRaids.track(level, helper.absolutePos(HALL), 1);
			CivicEffects.forget();
			Conscription.forget();
			watched[0] = farmer;
			helper.runAfterDelay(400, () -> {
				float health = zombie.getHealth();
				boolean onTop = farmer.getY() > helper.absolutePos(new BlockPos(4, 11, 4)).getY();
				zombie.discard();
				VillageRaids.forget();
				helper.assertTrue(started.get() >= 0, "the conscript on the tower never went for the raider below");
				helper.assertTrue(onTop, "the conscript came off the tower: " + farmer.blockPosition());
				helper.assertTrue(stopped.get() >= 0, "the conscript stood at the edge for good, after a raider he can't reach");
				helper.assertTrue(stopped.get() - started.get() <= MilitiaCombat.GIVE_UP_TICKS + 30,
					"the conscript took " + (stopped.get() - started.get()) + " ticks to let an unreachable raider be");
				helper.assertTrue(again.get() < 0 || again.get() - stopped.get() >= MilitiaCombat.IGNORE_TICKS - 20,
					"the conscript went straight back for the raider he gave up on, " + (again.get() - stopped.get()) + " ticks later");
				helper.assertTrue(health == zombie.getMaxHealth(), "a raider 10 blocks below was struck: " + health);
				helper.succeed();
			});
		});
	}

	/** B926 with the edicts switched off in the config: a conscript on a tower never fights the raider below it. */
	//$ gametest_ticks_batch AREA '300' '"qaConscriptBelowOff1104d"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaConscriptBelowOff1104d")
	public void edictsOffNoConscriptFightsTheRaiderBelow(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper, 16);
		Leftovers.halls(helper);
		boolean on = Edicts.ENABLED;
		Leftovers.after(helper, () -> {
			Edicts.setEnabled(on);
			CivicEffects.forget();
			Conscription.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		pillar(helper, new BlockPos(4, 2, 4), 10);
		AtomicBoolean fought = new AtomicBoolean();
		Villager[] watched = new Villager[1];
		helper.onEachTick(() -> fought.compareAndSet(false, watched[0] != null && MilitiaCombat.isFighting(watched[0])));
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			conscription(helper, HALL);
			Edicts.setEnabled(false);
			Villager farmer = conscript(helper, new BlockPos(4, 12, 4));
			Zombie zombie = raider(helper, new BlockPos(10, 2, 4));
			VillageRaids.track(level, helper.absolutePos(HALL), 1);
			CivicEffects.forget();
			Conscription.forget();
			watched[0] = farmer;
			helper.runAfterDelay(150, () -> {
				boolean armed = Conscription.isMilitiaSword(farmer.getMainHandItem());
				zombie.discard();
				VillageRaids.forget();
				helper.assertTrue(!fought.get(), "with edicts off a conscript fought the raider below");
				helper.assertTrue(!armed, "with edicts off a villager holds the militia's sword");
				helper.succeed();
			});
		});
	}

	private static void village(GameTestHelper helper, int size) {
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
	private static void conscription(GameTestHelper helper, BlockPos at) {
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

	private static Villager conscript(GameTestHelper helper, BlockPos at) {
		Villager villager = helper.spawn(EntityType.VILLAGER, at);
		villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
		villager.setVillagerXp(10);
		villager.refreshBrain(helper.getLevel());
		return villager;
	}

	private static Zombie raider(GameTestHelper helper, BlockPos at) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, at);
		zombie.setNoAi(true);
		zombie.setPersistenceRequired();
		zombie.addTag(VillageRaids.TAG);
		return zombie;
	}

	/** A stone pillar {@code height} blocks tall from {@code base} up (a villager on top can't walk off it). */
	private static void pillar(GameTestHelper helper, BlockPos base, int height) {
		for (int y = 0; y < height; y++) {
			helper.setBlock(base.above(y), Blocks.STONE);
		}
	}
}
