package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA bug test (lane b, 2026-11-16, session lane-b-1116-1837), from the spec, not the code: blueprint-library.md "Builds
 * unlock ... when a builder of the village levels up", told to "every player in the village"; the server-wide set is for
 * "Tables far from any hall". The village's own builder, hired at its Table 4 blocks from the hall, levels up while he is
 * out on a job 35 blocks away (the village radius is cut to 16 so it fits huge_area; with the real 64 the same happens to
 * a builder on a site past 64 blocks from the hall, which a Table's 48-block reach allows). Expected: the three builds
 * unlock in his village and the players there are told. Today they go to the server-wide set, which every later village
 * (any player's) starts with, and his own village never gets them.
 */
public class QaLevelUpVillage1116bBugGameTests {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	//$ gametest_ticks_batch HUGE '40' '"qaLvlVillage1116bBugAway"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaLvlVillage1116bBugAway")
	public void aVillageBuilderLevelingUpAwayOnAJobUnlocksInHisVillage(GameTestHelper helper) {
		QaLevelUpVillage1116bGameTests.setUp(helper);
		QaBlueprintUnlocksGameTests.village(helper, QaLevelUpVillage1116bGameTests.HALL, QaLevelUpVillage1116bGameTests.TABLE);
		Villager v = QaLevelUpVillage1116bGameTests.builder(helper, new BlockPos(27, 2, 27));
		helper.runAfterDelay(3, () -> {
			VillageHalls.forgetNearest();
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(QaLevelUpVillage1116bGameTests.HALL);
			helper.assertTrue(BlueprintUnlocks.village(level, helper.absolutePos(QaLevelUpVillage1116bGameTests.TABLE)) != null,
				"setup: the builder's Table isn't in the village");
			Set<ResourceLocation> wild = Set.copyOf(BlueprintUnlocks.unlocked(level, null));
			List<ResourceLocation> next = BlueprintUnlocks.nextForLevelUp(level, hall);
			QaStageFiveGameTests.Client c = QaBlueprintUnlocksGameTests.client(helper, QaLevelUpVillage1116bGameTests.HALL);
			c.seen().clear();
			QaLevelUpVillage1116bGameTests.hireAndLevel(helper, v, QaLevelUpVillage1116bGameTests.TABLE);
			helper.assertTrue(BlueprintUnlocks.unlocked(level, null).equals(wild), "the village builder's level-up 35 blocks from the hall went into the "
				+ "server-wide set (every later village starts with it): " + BlueprintUnlocks.unlocked(level, null) + ", was " + wild);
			for (ResourceLocation id : next) {
				helper.assertTrue(BlueprintUnlocks.state(level, hall, id) == BlueprintUnlocks.State.UNLOCKED,
					"the village builder leveled up away on a job and his village didn't get " + id + ": " + BlueprintUnlocks.state(level, hall, id));
				String line = "The village's library has a new blueprint: " + Blueprints.displayName(id).getString();
				helper.assertTrue(QaLevelUpVillage1116bGameTests.texts(c.seen()).contains(line),
					"a player by the hall wasn't told '" + line + "', saw " + QaLevelUpVillage1116bGameTests.texts(c.seen()));
			}
			helper.succeed();
		});
	}
}
