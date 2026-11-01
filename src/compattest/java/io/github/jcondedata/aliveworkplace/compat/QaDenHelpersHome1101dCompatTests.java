package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.trainer.RaidHelpers;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA (lane d, 2026-11-01) for ROADMAP 38.1a, the hired raid helpers' walk home, from the spec: once a hiring ends the
 * helpers walk home and are free to hire again when they get there. That arrival has to reach the world's save: if it
 * only changed memory, a server stopped soon after would load the helper as still "on the way home", and the next
 * player would hear "No trainer of the village is free to help". A mutation check (tools/modtest/mutate.py,
 * RaidHelpers:516, the arrival's {@code setDirty()} deleted) survived every other test.
 */
public class QaDenHelpersHome1101dCompatTests implements FabricGameTest {

	/** A helper who reaches home after the hiring ends is free again, and the helpers' save is marked to be written. */
	//$ gametest_ticks_batch 'CompatGameTests.AREA' '600' '"qaDenHelperHomeSaved"'
	@GameTest(template = CompatGameTests.AREA, timeoutTicks = 600, batch = "qaDenHelperHomeSaved")
	public void aHelperHomeAgainMarksTheSave(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		RaidHelperCompatTests.time(level, 1000);
		BlockPos[] at = RaidHelperCompatTests.village(helper);
		Villager master = RaidHelperCompatTests.master(helper);
		ServerPlayer player = RaidHelperCompatTests.player(helper, helper.absolutePos(new BlockPos(3, 2, 3)), RaidHelperCompatTests.PRICE);
		String said = RaidHelpers.hire(level, at[0], at[1], player).getString();
		helper.assertTrue(said.contains("will help you raid"), "hiring: " + said);
		RaidHelpers.over(level, RaidHelperCompatTests.hire(helper, player), "message.aliveworkplace.raid_helpers.day_over");
		helper.assertTrue(RaidHelpers.homeward(level, master.getUUID()), "the helper isn't walking home after the hiring ended");
		// home is its Training Post: stand it there, and let the save be written (nothing left to write)
		BlockPos post = helper.absolutePos(RaidHelperCompatTests.POSTS[0]).offset(0, 0, -1);
		master.teleportTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5);
		RaidHelpers.Data data = RaidHelpers.Data.get(level);
		data.setDirty(false);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertFalse(RaidHelpers.homeward(level, master.getUUID()),
				"the helper is still walking home " + Math.round(Math.sqrt(master.blockPosition().distSqr(post))) + " blocks from its post"))
			.thenExecute(() -> {
				boolean marked = data.isDirty();
				boolean free = RaidHelpers.free(level, at[0]).contains(master);
				RaidHelperCompatTests.cleanUp(helper, at[1], player);
				RaidHelperCompatTests.time(level, time);
				helper.assertTrue(free, "the helper home again isn't free to hire");
				helper.assertTrue(marked, "the helper got home but the save wasn't marked: a restart would load it still walking home");
			})
			.thenSucceed();
	}
}
