package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.MilitiaCombat;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.Conscription;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;

/**
 * B926 QA bug tests (lane d 2026-11-04, second pass), registered with B954's fix. Helpers are
 * {@link QaConscriptReach1104d2GameTests}'.
 */
public class QaConscriptReach1104d2BugTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/**
	 * Spec: "one within 4 blocks (behind a fence) he stands by." The raider a stranded conscript let be climbs up to him
	 * during the 10-second let-be and stands 2 blocks off, on his level, in sight: he fights it at once, not after the
	 * let-be runs out.
	 */
	//$ gametest_ticks_batch HUGE '700' '"qaConscriptIgnoredClimbs1104d2"'
	@GameTest(template = HUGE, timeoutTicks = 700, batch = "qaConscriptIgnoredClimbs1104d2")
	public void theRaiderHeLetBeClimbingUpToHimIsFoughtAtOnce(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaConscriptReach1104d2GameTests.village(helper, 32);
		Leftovers.halls(helper);
		helper.setBlock(QaConscriptReach1104d2GameTests.HALL, ModBlocks.VILLAGE_HALL);
		QaConscriptReach1104d2GameTests.platform(helper);
		QaConscriptReach1104d2GameTests.Watch watch = new QaConscriptReach1104d2GameTests.Watch();
		Zombie[] raider = new Zombie[1];
		AtomicInteger arrivedAt = new AtomicInteger(-1);
		AtomicInteger hurtAt = new AtomicInteger(-1);
		helper.onEachTick(() -> {
			watch.tick();
			if (watch.stopped >= 0 && arrivedAt.get() < 0 && watch.now - watch.stopped >= 5) {
				QaConscriptReach1104d2GameTests.beside(helper, raider[0], watch.villager);
				arrivedAt.set(watch.now);
			}
			if (raider[0] != null && hurtAt.get() < 0 && raider[0].getHealth() < raider[0].getMaxHealth()) {
				hurtAt.set(watch.now);
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
			// Long enough to see when he does strike (after the let-be), so the message says how late.
			helper.runAfterDelay(MilitiaCombat.GIVE_UP_TICKS + MilitiaCombat.IGNORE_TICKS + 300, () -> {
				int arrived = arrivedAt.get();
				int hurt = hurtAt.get();
				double d = Math.sqrt(farmer.distanceToSqr(raider[0]));
				raider[0].discard();
				VillageRaids.forget();
				helper.assertTrue(watch.stopped >= 0, "the conscript never let the unreachable raider below be");
				helper.assertTrue(arrived >= 0, "the raider never came up");
				helper.assertTrue(hurt < 0 || hurt >= arrived, "the raider was struck before it came up");
				helper.assertTrue(hurt >= 0 && hurt - arrived <= 40, "the raider he let be came up 2 blocks from the conscript, in sight, "
					+ (arrived - watch.stopped) + " ticks into a let-be, and wasn't struck within 40 ticks: "
					+ (hurt < 0 ? "never struck" : "struck " + (hurt - arrived) + " ticks after arriving")
					+ String.format(" (now %.1f blocks apart; fighting again %s)", d,
					watch.again >= 0 ? (watch.again - arrived) + " ticks after it arrived" : "never"));
				helper.succeed();
			});
		});
	}
}
