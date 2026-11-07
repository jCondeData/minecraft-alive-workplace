package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.MilitiaCombat;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.Conscription;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane D, 2026-11-05), B954's other side. The spec (edicts.md): a raider a conscript can't get a block closer to in
 * 5 seconds "he lets be for 10 seconds, so he isn't stuck at the edge all night ... and one he let be that climbs or
 * jumps up within 4 blocks of him he fights at once". So a let-be raider that comes up only to 5 blocks off, across a gap
 * he can't cross, is still let be: he doesn't take it up again before the 10 seconds are out, and does after (then he
 * walks to his platform's edge, within 4 blocks of it, and stands by it there). Helpers are
 * {@link QaConscriptReach1104d2GameTests}'.
 *
 * <p>B978: the platform's corner is 2 blocks from the raider's pillar, so a conscript strolling about his platform while
 * he lets it be came within 4 blocks of it himself, and one that close is never let be (rightly): 7 CI runs failed so.
 * He is held where he stands (no speed) for the let-be, so the raider stays the 5 blocks off this test is about.
 */
public class QaConscriptLetBe1105dGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	//$ gametest_ticks_batch HUGE '800' '"qaConscriptLetBeFive1105d"'
	@GameTest(template = HUGE, timeoutTicks = 800, batch = "qaConscriptLetBeFive1105d")
	public void aLetBeRaiderFiveBlocksOffAcrossAGapStaysLetBeForTheTenSeconds(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaConscriptReach1104d2GameTests.village(helper, 32);
		Leftovers.halls(helper);
		helper.setBlock(QaConscriptReach1104d2GameTests.HALL, ModBlocks.VILLAGE_HALL);
		QaConscriptReach1104d2GameTests.platform(helper);
		// A one-block pillar as high as the platform, two blocks of air from its corner: 5 blocks from its middle.
		QaConscriptReach1104d2GameTests.pillar(helper, new BlockPos(8, 2, 7), 10);
		QaConscriptReach1104d2GameTests.Watch watch = new QaConscriptReach1104d2GameTests.Watch();
		Zombie[] raider = new Zombie[1];
		int[] moved = {-1};
		int[] hurtAt = {-1};
		int[] fightingAt = {-1};
		double[] speed = {-1};
		double[] closest = {Double.MAX_VALUE}; // the nearest the two came in the let-be
		String[] when = {"", ""}; // where both stood when the raider was first struck, and when he first took it up
		helper.onEachTick(() -> {
			watch.tick();
			if (watch.stopped >= 0 && moved[0] < 0 && watch.now - watch.stopped >= 5) {
				Vec3 him = helper.absoluteVec(new Vec3(4.5, 12, 4.5));
				watch.villager.teleportTo(him.x, him.y, him.z);
				watch.villager.getNavigation().stop();
				speed[0] = watch.villager.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
				watch.villager.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
				Vec3 it = helper.absoluteVec(new Vec3(8.5, 12, 7.5));
				raider[0].teleportTo(it.x, it.y, it.z);
				moved[0] = watch.now;
			}
			if (moved[0] >= 0) {
				int letBeEnds = watch.stopped + MilitiaCombat.IGNORE_TICKS;
				if (speed[0] >= 0 && watch.now >= letBeEnds) {
					watch.villager.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(speed[0]); // free to walk to the edge
					speed[0] = -1;
				}
				if (watch.now < letBeEnds) {
					closest[0] = Math.min(closest[0], Math.sqrt(watch.villager.distanceToSqr(raider[0])));
				}
				if (hurtAt[0] < 0 && raider[0].getHealth() < raider[0].getMaxHealth()) {
					hurtAt[0] = watch.now;
					when[0] = where(helper, watch.villager, raider[0]);
				}
				if (fightingAt[0] < 0 && MilitiaCombat.isFighting(watch.villager)) {
					fightingAt[0] = watch.now;
					when[1] = where(helper, watch.villager, raider[0]);
				}
			}
		});
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			QaConscriptReach1104d2GameTests.conscription(helper, QaConscriptReach1104d2GameTests.HALL);
			Villager farmer = QaConscriptReach1104d2GameTests.conscript(helper, new BlockPos(4, 12, 4));
			raider[0] = QaConscriptReach1104d2GameTests.raider(helper, new BlockPos(20, 2, 4));
			VillageRaids.track(level, helper.absolutePos(QaConscriptReach1104d2GameTests.HALL), 1);
			CivicEffects.forget();
			Conscription.forget();
			watch.villager = farmer;
			helper.runAfterDelay(MilitiaCombat.GIVE_UP_TICKS + MilitiaCombat.IGNORE_TICKS + 250, () -> {
				double d = Math.sqrt(farmer.distanceToSqr(raider[0]));
				int letBeEnds = watch.stopped + MilitiaCombat.IGNORE_TICKS;
				raider[0].discard();
				VillageRaids.forget();
				helper.assertTrue(watch.stopped >= 0, "the conscript never let the unreachable raider below be");
				helper.assertTrue(moved[0] >= 0, "the raider was never moved up");
				helper.assertTrue(farmer.getY() > helper.absolutePos(new BlockPos(0, 11, 0)).getY(), "the conscript left his platform: " + farmer.position());
				helper.assertTrue(hurtAt[0] < 0 || hurtAt[0] >= letBeEnds - 5, String.format(
					"the raider 5 blocks off across a gap was struck %d ticks after it came up, inside the let-be (%s; nearest in the let-be %.1f)",
					hurtAt[0] - moved[0], when[0], closest[0]));
				helper.assertTrue(fightingAt[0] < 0 || fightingAt[0] >= letBeEnds - 5, String.format(
					"the conscript took up the let-be raider %d ticks after it came up, %d ticks before the 10 s were out (%s; nearest in the let-be %.1f, %.1f at the end)",
					fightingAt[0] - moved[0], letBeEnds - fightingAt[0], when[1], closest[0], d));
				helper.assertTrue(fightingAt[0] >= 0, "after the let-be ran out the conscript never took the raider 5 blocks off up again (moved at "
					+ moved[0] + ", let-be ends " + letBeEnds + ", now " + watch.now + ")");
				helper.succeed();
			});
		});
	}

	private static String where(GameTestHelper helper, Villager villager, Zombie raider) {
		return String.format("the conscript at %s, the raider at %s, %.1f apart", helper.relativeVec(villager.position()),
			helper.relativeVec(raider.position()), Math.sqrt(villager.distanceToSqr(raider)));
	}
}
