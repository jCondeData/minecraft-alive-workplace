package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.bard.BardWork;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.JukeboxSongs;
import net.minecraft.world.level.block.Blocks;

/**
 * QA 2026-11-05 (lane c): the Bard's songs, two bugs found from docs/guide/bard.md. Both fail on the mod as it is; the
 * fix goes in with them. What a client hears is read from the jukebox level events a listening player is sent (1010
 * starts a song at the jukebox, 1011 stops it); see {@link QaPrinterNurseBard1105cGameTests#ears}.
 */
public class QaPrinterNurseBard1105cBugTests implements FabricGameTest {
	private static final String AREA = QaPrinterNurseBard1105cGameTests.AREA;
	private static final BlockPos TABLE = QaPrinterNurseBard1105cGameTests.TABLE;
	private static final BlockPos CHEST = QaPrinterNurseBard1105cGameTests.CHEST;

	/** A bard hired at a jukebox (sneak-right-click with Cat), Cat in the chest by it, a listener 4 blocks off, morning. */
	private static Villager bardWithCat(GameTestHelper helper, List<Packet<?>> heard) {
		Leftovers.clear(helper);
		helper.setDayTime(1500);
		helper.setBlock(TABLE, Blocks.JUKEBOX);
		helper.setBlock(CHEST, Blocks.CHEST);
		QaPrinterNurseBard1105cGameTests.chest(helper).setItem(0, new ItemStack(Items.MUSIC_DISC_CAT));
		QaPrinterNurseBard1105cGameTests.ears(helper, heard, new BlockPos(6, 2, 6));
		return QaPrinterNurseBard1105cGameTests.hired(helper, QaPrinterNurseBard1105cGameTests.STANDING, Items.MUSIC_DISC_CAT, ModVillagers.BARD);
	}

	/**
	 * bard.md: "they play them one after another, going round all the discs there". A disc is played through before the
	 * next one starts: Cat runs 185 seconds (3,700 ticks), so 2,400 ticks after it began it is still the one song, started
	 * once and never stopped. (Today the bard's shift behaviour times out after 1,200 ticks, which stops the song and
	 * starts the next disc: every disc longer than a minute is cut off.)
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaBardWholeSong1105c"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaBardWholeSong1105c")
	public void aDiscIsPlayedThroughNotCutOffAfterAMinute(GameTestHelper helper) {
		List<Packet<?>> heard = Collections.synchronizedList(new ArrayList<>());
		Villager bard = bardWithCat(helper, heard);
		BlockPos jukebox = helper.absolutePos(TABLE);
		long[] startedAt = {-1};
		helper.onEachTick(() -> {
			if (startedAt[0] < 0 && !QaPrinterNurseBard1105cGameTests.songEvents(heard, jukebox).isEmpty()) {
				startedAt[0] = helper.getTick();
			}
			if (startedAt[0] >= 0 && helper.getTick() == startedAt[0] + 2400) {
				List<Integer> events = QaPrinterNurseBard1105cGameTests.songEvents(heard, jukebox);
				helper.assertTrue(events.equals(List.of(1010)), "jukebox events in the 2,400 ticks after Cat began: "
					+ QaPrinterNurseBard1105cGameTests.songLog(heard, jukebox) + " (1010@t starts a song at game time t, 1011@t stops one); Cat is 3,700 ticks long");
				helper.assertTrue(BardWork.playing(bard).map(s -> s.is(JukeboxSongs.CAT)).orElse(false), "not playing Cat: " + BardWork.playing(bard));
				helper.succeed();
			}
		});
	}

	/**
	 * The jukebox broken mid-song (bard.md: the bard "plays at the jukebox"): with it gone the bard stops, and so must the
	 * song: a stop is sent for the jukebox's spot, as vanilla does when a playing jukebox is broken. (Today the bard lets
	 * the song go without telling anyone, because the stop is only sent while the bard still remembers a workstation, so
	 * listeners hear Cat play on for three minutes where the jukebox stood.) The disc stays in the chest.
	 */
	//$ gametest_ticks_batch AREA '800' '"qaBardJukeboxBroken1105c"'
	@GameTest(template = AREA, timeoutTicks = 800, batch = "qaBardJukeboxBroken1105c")
	public void aJukeboxBrokenMidSongStopsTheSong(GameTestHelper helper) {
		List<Packet<?>> heard = Collections.synchronizedList(new ArrayList<>());
		Villager bard = bardWithCat(helper, heard);
		BlockPos jukebox = helper.absolutePos(TABLE);
		long[] playingSince = {-1};
		long[] brokenAt = {-1};
		helper.onEachTick(() -> {
			if (playingSince[0] < 0 && BardWork.playing(bard).isPresent()) {
				playingSince[0] = helper.getTick();
			}
			if (brokenAt[0] < 0 && playingSince[0] >= 0 && helper.getTick() >= playingSince[0] + 40) {
				helper.setBlock(TABLE, Blocks.AIR);
				brokenAt[0] = helper.getLevel().getGameTime();
			}
		});
		helper.runAfterDelay(600, () -> {
			helper.assertTrue(brokenAt[0] >= 0, "Cat never started");
			helper.assertTrue(BardWork.playing(bard).isEmpty(), "still playing " + BardWork.playing(bard) + " with the jukebox gone");
			List<Integer> events = QaPrinterNurseBard1105cGameTests.songEvents(heard, jukebox);
			helper.assertTrue(events.equals(List.of(1010, 1011)), "jukebox events: " + QaPrinterNurseBard1105cGameTests.songLog(heard, jukebox)
				+ " (1010@t starts a song, 1011@t stops it); the jukebox was broken at game time " + brokenAt[0]
				+ " and Cat runs 3,700 ticks, so with no stop its music plays on where the jukebox stood");
			helper.assertTrue(QaPrinterNurseBard1105cGameTests.chest(helper).countItem(Items.MUSIC_DISC_CAT) == 1, "the disc left the chest");
			helper.succeed();
		});
	}
}
