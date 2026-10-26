package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.trainer.MegaShrines;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA of ROADMAP 38.7: the Mega Shrine tests that failed on the mod (bugs against the spec), each registered with its fix.
 * Uses {@link QaMegaShrineCompatTests}'s set-up. (This branch's other two, a villager at work and a broken pedestal, are
 * B420's and B421's, already covered by {@code QaMegaShrineGapsCompatTests}.)
 */
public class QaMegaShrineBugCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	/**
	 * B501: the show is held once a festival. After a server restart in the show's hours (MegaShrines.forget, what a server
	 * stop loses), the trainer Mega Evolved again and the line was sent again: a villager who arrived after the show got it too.
	 */
	//$ gametest_batch AREA '"qa_mega_shrine_bug_restart"'
	@GameTest(template = AREA, batch = "qa_mega_shrine_bug_restart")
	public void qaARestartInTheShowsHoursDoesntHoldItAgain(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<BlueprintData.Placement> placements = QaMegaShrineCompatTests.setUp(helper);
		QaMegaShrineCompatTests.finished(helper, placements, StarterBlueprints.MEGA_SHRINE.id(), helper.absolutePos(QaMegaShrineCompatTests.ORIGIN));
		BlockPos hall = helper.absolutePos(QaMegaShrineCompatTests.HALL);
		QaMegaShrineCompatTests.villager(helper, new BlockPos(5, 4, 7), true);
		Villager watcher = QaMegaShrineCompatTests.villager(helper, new BlockPos(5, 2, 2), false);
		MegaShrines.tick(level, hall);
		helper.assertTrue(MegaShrines.mood(level, watcher) == MegaShrines.MOOD, "no show was held");
		Villager late = QaMegaShrineCompatTests.villager(helper, new BlockPos(6, 2, 2), false); // arrives after the show
		MegaShrines.tick(level, hall);
		helper.assertTrue(ModAttachments.MEGA_SHOW_DAY.get(late) == null, "the show was held twice without a restart");
		MegaShrines.forget(); // the server restarts in the show's hours
		MegaShrines.tick(level, hall);
		helper.assertTrue(ModAttachments.MEGA_SHOW_DAY.get(late) == null, "after a restart the show was held a second time the same festival");
		helper.succeed();
	}
}
