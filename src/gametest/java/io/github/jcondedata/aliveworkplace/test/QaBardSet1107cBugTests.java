package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.bard.BardWork;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.JukeboxSongs;

/**
 * QA 2026-11-07 (lane c): a Bard bug found next to B975/B976. It fails on the mod as it is; the fix goes in with it.
 * Setup and listening as in {@link QaBardSet1107cGameTests}.
 */
public class QaBardSet1107cBugTests implements FabricGameTest {
	private static final String AREA = QaBardSet1107cGameTests.AREA;
	private static final BlockPos TABLE = QaBardSet1107cGameTests.TABLE;
	private static final BlockPos CHEST = QaBardSet1107cGameTests.CHEST;

	/**
	 * Not in the guide, the vanilla expectation: the bard killed mid-song. The jukebox's song is the bard's playing (the
	 * jukebox itself holds no disc), so with him gone the song stops for listeners at the jukebox, once, within 5 seconds,
	 * and he is no longer listed as playing.
	 */
	//$ gametest_ticks_batch AREA '800' '"qaBardKilled1107c"'
	@GameTest(template = AREA, timeoutTicks = 800, batch = "qaBardKilled1107c")
	public void theBardKilledMidSongStopsTheSong(GameTestHelper helper) {
		List<Packet<?>> heard = Collections.synchronizedList(new ArrayList<>());
		long[] clock = {1500};
		Villager bard = QaBardSet1107cGameTests.bard(helper, heard, clock, CHEST, Items.MUSIC_DISC_CAT);
		BlockPos jukebox = helper.absolutePos(TABLE);
		long[] t0 = {-1};
		long[] killedAt = {-1};
		helper.onEachTick(() -> {
			if (t0[0] < 0 && QaBardSet1107cGameTests.plays(bard, JukeboxSongs.CAT)) {
				t0[0] = helper.getTick();
			}
			if (t0[0] >= 0 && killedAt[0] < 0 && helper.getTick() == t0[0] + 40) {
				bard.kill();
				killedAt[0] = helper.getLevel().getGameTime();
			}
			if (killedAt[0] >= 0 && helper.getLevel().getGameTime() == killedAt[0] + 100) {
				helper.assertTrue(QaBardSet1107cGameTests.events(heard, jukebox).equals(List.of(1010, 1011)), "jukebox events: " + QaBardSet1107cGameTests.log(heard, jukebox) + "; the bard was killed at game time "
					+ killedAt[0] + " (expected the start, then one stop within 100 ticks: Cat runs 3,700 ticks)");
				helper.assertTrue(BardWork.playing(bard).isEmpty(), "a dead bard still listed as playing " + BardWork.playing(bard));
				helper.succeed();
			}
		});
	}

	/**
	 * B1003, the other way a bard leaves: discarded mid-song (the same unload path a chunk unloading him or a portal takes,
	 * where his brain never runs stop()). The song stops at the jukebox once and he is no longer listed as playing.
	 */
	//$ gametest_ticks_batch AREA '800' '"qaBardDiscarded1107c"'
	@GameTest(template = AREA, timeoutTicks = 800, batch = "qaBardDiscarded1107c")
	public void theBardDiscardedMidSongStopsTheSong(GameTestHelper helper) {
		List<Packet<?>> heard = Collections.synchronizedList(new ArrayList<>());
		long[] clock = {1500};
		Villager bard = QaBardSet1107cGameTests.bard(helper, heard, clock, CHEST, Items.MUSIC_DISC_CAT);
		BlockPos jukebox = helper.absolutePos(TABLE);
		long[] t0 = {-1};
		long[] goneAt = {-1};
		helper.onEachTick(() -> {
			if (t0[0] < 0 && QaBardSet1107cGameTests.plays(bard, JukeboxSongs.CAT)) {
				t0[0] = helper.getTick();
			}
			if (t0[0] >= 0 && goneAt[0] < 0 && helper.getTick() == t0[0] + 40) {
				bard.discard();
				goneAt[0] = helper.getLevel().getGameTime();
			}
			if (goneAt[0] >= 0 && helper.getLevel().getGameTime() == goneAt[0] + 40) {
				helper.assertTrue(QaBardSet1107cGameTests.events(heard, jukebox).equals(List.of(1010, 1011)), "jukebox events: "
					+ QaBardSet1107cGameTests.log(heard, jukebox) + "; the bard was discarded at game time " + goneAt[0]
					+ " (expected the start, then one stop)");
				helper.assertTrue(BardWork.playing(bard).isEmpty(), "a discarded bard still listed as playing " + BardWork.playing(bard));
				helper.succeed();
			}
		});
	}
}
