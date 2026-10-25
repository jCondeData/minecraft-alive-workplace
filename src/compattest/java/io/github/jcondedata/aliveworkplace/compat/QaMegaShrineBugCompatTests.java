package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.trainer.MegaShrines;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;

/**
 * QA of ROADMAP 38.7: the Mega Shrine tests that fail on the mod as it is (bugs against the spec). NOT registered in
 * fabric.mod.json: each goes in with its fix. Uses {@link QaMegaShrineCompatTests}'s set-up.
 */
public class QaMegaShrineBugCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	/**
	 * Spec: "villagers off work gather and get +3 mood". A villager at work at a workstation by the pedestal (within the
	 * watch range) neither gathered nor came, yet gets the show's +3.
	 */
	//$ gametest_batch AREA '"qa_mega_shrine_bug_work"'
	@GameTest(template = AREA, batch = "qa_mega_shrine_bug_work")
	public void qaAVillagerAtWorkByThePedestalGetsNoShowMood(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<BlueprintData.Placement> placements = QaMegaShrineCompatTests.setUp(helper);
		QaMegaShrineCompatTests.finished(helper, placements, StarterBlueprints.MEGA_SHRINE.id(), helper.absolutePos(QaMegaShrineCompatTests.ORIGIN));
		BlockPos hall = helper.absolutePos(QaMegaShrineCompatTests.HALL);
		QaMegaShrineCompatTests.villager(helper, new BlockPos(5, 4, 7), true);
		Villager idle = QaMegaShrineCompatTests.villager(helper, new BlockPos(5, 2, 2), false);
		Villager working = QaMegaShrineCompatTests.worker(helper, new BlockPos(7, 2, 6)); // at work 2-3 blocks from the pedestal
		MegaShrines.tick(level, hall);
		helper.assertTrue(MegaShrines.mood(level, idle) == MegaShrines.MOOD, "no show was held");
		helper.assertTrue(working.getBrain().isActive(net.minecraft.world.entity.schedule.Activity.WORK), "the worker stopped working");
		helper.assertTrue(MegaShrines.mood(level, working) == 0, "a villager at work (who never gathered) got the show's +"
			+ MegaShrines.mood(level, working));
		helper.succeed();
	}

	/**
	 * The show is held once a festival. After a server restart in the show's hours (MegaShrines.forget, what a server stop
	 * calls), the trainer Mega Evolves again and the line is sent again: a villager who arrives after the show gets it too.
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

	/**
	 * Spec: "a village with a finished shrine". With the pedestal there the show is held; with it broken (the shrine no
	 * longer whole) the next festival still holds the show, at a pedestal that isn't there.
	 */
	//$ gametest_batch AREA '"qa_mega_shrine_bug_broken"'
	@GameTest(template = AREA, batch = "qa_mega_shrine_bug_broken")
	public void qaABrokenPedestalHoldsNoShow(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<BlueprintData.Placement> placements = QaMegaShrineCompatTests.setUp(helper);
		QaMegaShrineCompatTests.finished(helper, placements, StarterBlueprints.MEGA_SHRINE.id(), helper.absolutePos(QaMegaShrineCompatTests.ORIGIN));
		BlockPos hall = helper.absolutePos(QaMegaShrineCompatTests.HALL);
		BlockPos pedestal = new BlockPos(5, 4, 8);
		helper.setBlock(pedestal, BuiltInRegistries.BLOCK.get(MegaShrines.PEDESTAL));
		QaMegaShrineCompatTests.villager(helper, new BlockPos(5, 4, 7), true);
		Villager watcher = QaMegaShrineCompatTests.villager(helper, new BlockPos(5, 2, 2), false);
		MegaShrines.tick(level, hall);
		helper.assertTrue(MegaShrines.mood(level, watcher) == MegaShrines.MOOD, "no show with the pedestal there");
		// The next festival: the pedestal has been broken.
		Festivals.forget();
		ModAttachments.MEGA_SHOW_DAY.remove(watcher);
		helper.setBlock(pedestal, Blocks.AIR);
		MegaShrines.tick(level, hall);
		helper.assertTrue(ModAttachments.MEGA_SHOW_DAY.get(watcher) == null && MegaShrines.mood(level, watcher) == 0,
			"a show held at a broken pedestal (day " + Chronicle.day(level) + ")");
		helper.succeed();
	}
}
