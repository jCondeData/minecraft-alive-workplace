package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/**
 * QA (lane d, 2026-10-25) for a lair's growth at dawn (ROADMAP 32.x lairs, with 32.21's threat level and B383): a lair
 * grows by its culture's {@code growth} a day up to its {@code max} (a quarter more on threat level 3), and dawn only
 * ever adds. Kills the nightly survivor at {@code Lairs.java:402} (2026-10-25): a band grown big on hard must keep its
 * raiders when the server owner turns the threat level back down; it just stops growing.
 */
public class QaLairGrowthGameTests implements FabricGameTest {
	static final ResourceLocation WARBAND = AliveWorkplace.id("pillager_warband");

	@SuppressWarnings("unchecked")
	static void drop(ServerLevel level, BlockPos hall) {
		try {
			java.lang.reflect.Field f = Lairs.Data.class.getDeclaredField("lairs");
			f.setAccessible(true);
			((Map<BlockPos, Lairs.Lair>) f.get(Lairs.Data.get(level))).remove(hall);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("can't take the test's lair away: " + e);
		}
	}

	static int afterDawn(ServerLevel level, BlockPos hall, BlockPos camp, int strength, long today) {
		Lairs.putForTest(level, new Lairs.Lair(camp, hall, new UUID(7, 7), today - 1, WARBAND, strength, today - 1, 0, 0, 0));
		Lairs.dawn(level, hall, today);
		Lairs.Lair after = Lairs.near(level, hall).orElseThrow(() -> new AssertionError("the lair was dropped at dawn"));
		if (after.grown() != today) {
			throw new AssertionError("the lair's last growth day is " + after.grown() + ", not " + today);
		}
		return after.strength();
	}

	/**
	 * A warband (max 18, growth 2): on hard it grows to 23 and no further; the level turned back to normal, a band of 23
	 * stays 23 at dawn (not cut to 18); at normal one under the max grows only to the max, and at the max stays there.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaLairGrowth"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaLairGrowth")
	public void aBandGrownOnHardKeepsItsRaidersWhenTheLevelGoesDown(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(new BlockPos(0, 1, 0));
		BlockPos camp = hall.offset(40, 0, 40);
		Culture.Lair spec = Threats.get(WARBAND).flatMap(Culture::lair).orElse(null);
		helper.assertTrue(spec != null && spec.max() == 18 && spec.growth() == 2, "setup: the warband's lair isn't max 18, growth 2: " + spec);
		long today = Chronicle.day(level);
		try {
			WorkplaceConfig.parse("{\"threatLevel\": 3}").apply();
			int hard = Lairs.most(spec);
			helper.assertTrue(hard == 23, "the most on hard is " + hard + ", not 23");
			helper.assertTrue(afterDawn(level, hall, camp, 22, today) == 23, "on hard 22 should grow to 23 (the most), not past it");
			helper.assertTrue(afterDawn(level, hall, camp, 23, today) == 23, "on hard a band at 23 grew past the most");
			WorkplaceConfig.parse("{\"threatLevel\": 2}").apply();
			int kept = afterDawn(level, hall, camp, 23, today);
			helper.assertTrue(kept == 23, "the threat level turned down, a band of 23 became " + kept + " at dawn: dawn only adds raiders");
			helper.assertTrue(afterDawn(level, hall, camp, 17, today) == 18, "at normal 17 should grow to 18 (its max), not past it");
			helper.assertTrue(afterDawn(level, hall, camp, 18, today) == 18, "at normal a band at its max grew");
		} finally {
			drop(level, hall);
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}
}
