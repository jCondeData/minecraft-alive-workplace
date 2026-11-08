package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.bard.BardWork;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.JukeboxSongs;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane qa-lane-c-1108-0352): the nightly mutation run of 2026-11-07 found BardWork's set check ("the set is over:
 * the next disc waits for the next one", {@code gameTime > setEnds}) turned into {@code >=} undetected. A Bard's set is
 * 1,200 ticks (a song under way is always played to its end, B975); a disc ending with the set's last tick still gets
 * the next disc, and one ending a tick after the set waits. The behavior is driven by hand on a NoAI Bard hired the
 * player's way, with the game time the test chooses, so the song's end lands on the exact tick.
 */
public class QaBardSetEdge1108cGameTests implements FabricGameTest {
	private static final String AREA = QaPrinterNurseBard1105cGameTests.AREA;
	private static final BlockPos JUKEBOX = QaPrinterNurseBard1105cGameTests.TABLE;
	private static final BlockPos CHEST = QaPrinterNurseBard1105cGameTests.CHEST;
	private static final BlockPos STANDING = QaPrinterNurseBard1105cGameTests.STANDING;
	/** BardWork's set: how long a set lasts before the brain looks again. */
	private static final int SET = 1200;

	/** 11 ends on the set's last tick (game time = the set's start + 1,200): Mellohi, the next disc, starts at once. */
	//$ gametest_ticks_batch AREA '200' '"qaBardSetEdgeOn"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaBardSetEdgeOn")
	public void aSongEndingOnTheSetsLastTickGetsTheNextDisc(GameTestHelper helper) {
		edge(helper, 0, true);
	}

	/** 11 ends a tick after the set (the set's start + 1,201): no next disc, nothing playing until the next set. */
	//$ gametest_ticks_batch AREA '200' '"qaBardSetEdgeAfter"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaBardSetEdgeAfter")
	public void aSongEndingATickAfterTheSetWaitsForTheNextSet(GameTestHelper helper) {
		edge(helper, 1, false);
	}

	/** Plays 11 to its end with the end's game time at the set's end plus {@code past}, then checks whether Mellohi started. */
	private static void edge(GameTestHelper helper, int past, boolean nextDisc) {
		Leftovers.clear(helper);
		helper.setDayTime(1500); // the morning set
		helper.setBlock(JUKEBOX, Blocks.JUKEBOX);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.MUSIC_DISC_11));
		chest.setItem(1, new ItemStack(Items.MUSIC_DISC_MELLOHI));
		Villager bard = QaPrinterNurseBard1105cGameTests.hired(helper, STANDING, Items.MUSIC_DISC_11, ModVillagers.BARD);
		bard.setNoAi(true); // the test plays the Bard's behavior, not his brain
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			bard.getBrain().setActiveActivityIfPossible(Activity.WORK);
			helper.assertTrue(bard.getBrain().isActive(Activity.WORK), "setup: the Bard isn't at work");
			BardWork work = new BardWork();
			long start = level.getGameTime();
			helper.assertTrue(work.tryStart(level, bard, start) && work.getStatus() == Behavior.Status.RUNNING, "setup: the Bard's set didn't start");
			for (int i = 0; i < 100 && BardWork.playing(bard).isEmpty(); i++) {
				work.tickOrStop(level, bard, start);
			}
			helper.assertTrue(BardWork.playing(bard).map(s -> s.is(JukeboxSongs.ELEVEN)).orElse(false), "setup: the Bard isn't playing 11: " + BardWork.playing(bard));
			int length = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.JUKEBOX_SONG)
				.getHolderOrThrow(JukeboxSongs.ELEVEN).value().lengthInTicks();
			// The song is over on its (length + 60)th tick after it started (a short pause after the disc): the ticks before
			// that are mid-set, the last one at the set's end plus past.
			for (int i = 1; i < length + 60; i++) {
				work.tickOrStop(level, bard, start + 1);
			}
			helper.assertTrue(BardWork.playing(bard).map(s -> s.is(JukeboxSongs.ELEVEN)).orElse(false), "11 ended early: " + BardWork.playing(bard));
			work.tickOrStop(level, bard, start + SET + past);
			boolean mellohi = BardWork.playing(bard).map(s -> s.is(JukeboxSongs.MELLOHI)).orElse(false);
			helper.assertTrue(BardWork.playing(bard).map(s -> !s.is(JukeboxSongs.ELEVEN)).orElse(true), "11 is still playing after its length: " + BardWork.playing(bard));
			helper.assertTrue(mellohi == nextDisc, "11 ended " + past + " tick(s) after the " + SET + "-tick set: next disc started " + mellohi
				+ " (expected " + nextDisc + "), playing " + BardWork.playing(bard));
			work.doStop(level, bard, start + SET + past);
			helper.succeed();
		});
	}
}
