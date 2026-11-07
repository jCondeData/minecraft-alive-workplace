package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.bard.BardWork;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.JukeboxSongs;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * QA 2026-11-07 (lane c): the fixes for B975 (a disc is played to its end, the set ends with the shift) and B976 (the
 * stop goes to the jukebox's spot when it is broken), tested from docs/guide/bard.md ("Every morning and evening the bard
 * plays at the jukebox ... they play them one after another, going round all the discs there (the discs stay in the
 * chest)") and the bugs' words, not from BardWork. What a client hears is read from the jukebox level events a listening
 * player is sent (1010 starts a song at a spot, 1011 stops it), with {@link QaPrinterNurseBard1105cGameTests#ears}.
 * QaPrinterNurseBard1105c* already cover: Cat not cut off at 2,400 ticks, 11 then Mellohi, the morning set's end, and the
 * jukebox set to air mid-song; these add the rest.
 */
public class QaBardSet1107cGameTests implements FabricGameTest {
	static final String AREA = QaPrinterNurseBard1105cGameTests.AREA;
	static final BlockPos TABLE = QaPrinterNurseBard1105cGameTests.TABLE;
	static final BlockPos CHEST = QaPrinterNurseBard1105cGameTests.CHEST;
	static final BlockPos STANDING = QaPrinterNurseBard1105cGameTests.STANDING;
	static final BlockPos LISTENER = new BlockPos(6, 2, 6);

	/** The song's real length, from the registry (vanilla's jukebox_song data). */
	static int length(GameTestHelper helper, ResourceKey<JukeboxSong> key) {
		return helper.getLevel().registryAccess().registryOrThrow(Registries.JUKEBOX_SONG).getHolderOrThrow(key).value().lengthInTicks();
	}

	static boolean plays(Villager bard, ResourceKey<JukeboxSong> key) {
		return BardWork.playing(bard).map(s -> s.is(key)).orElse(false);
	}

	/**
	 * A jukebox at TABLE, a chest at {@code chestAt} holding {@code discs}, a listener, and a bard hired by a player's
	 * sneak-right-click with the first disc. The day's clock is {@code clock[0]} on every tick (the test moves it).
	 */
	static Villager bard(GameTestHelper helper, List<Packet<?>> heard, long[] clock, BlockPos chestAt, Item... discs) {
		Leftovers.clear(helper);
		helper.setDayTime((int) clock[0]);
		helper.onEachTick(() -> helper.setDayTime((int) clock[0]));
		helper.setBlock(TABLE, Blocks.JUKEBOX);
		helper.setBlock(chestAt, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestAt);
		for (int i = 0; i < discs.length; i++) {
			chest.setItem(i, new ItemStack(discs[i]));
		}
		QaPrinterNurseBard1105cGameTests.ears(helper, heard, LISTENER);
		return QaPrinterNurseBard1105cGameTests.hired(helper, STANDING, discs[0], ModVillagers.BARD);
	}

	static List<Integer> events(List<Packet<?>> heard, BlockPos pos) {
		return QaPrinterNurseBard1105cGameTests.songEvents(heard, pos);
	}

	static List<String> log(List<Packet<?>> heard, BlockPos pos) {
		return QaPrinterNurseBard1105cGameTests.songLog(heard, pos);
	}

	static long at(String entry) {
		return Long.parseLong(entry.substring(5));
	}

	/**
	 * B975, past two set lengths: Pigstep (149 s, 2,980 ticks, more than twice the 1,200-tick set) is played through,
	 * started once and never stopped before its end; then it is stopped once and the next disc in the chest (Mellohi)
	 * starts straight away. Exactly one start/stop pair for Pigstep.
	 */
	//$ gametest_ticks_batch AREA '3600' '"qaBardPigstep1107c"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaBardPigstep1107c")
	public void pigstepIsPlayedThroughTwoSetLengthsThenTheNextDisc(GameTestHelper helper) {
		List<Packet<?>> heard = Collections.synchronizedList(new ArrayList<>());
		long[] clock = {1500};
		Villager bard = bard(helper, heard, clock, CHEST, Items.MUSIC_DISC_PIGSTEP, Items.MUSIC_DISC_MELLOHI);
		BlockPos jukebox = helper.absolutePos(TABLE);
		int len = length(helper, JukeboxSongs.PIGSTEP);
		helper.assertTrue(len > 2400, "setup: Pigstep is only " + len + " ticks");
		long[] t0 = {-1};
		helper.onEachTick(() -> {
			if (t0[0] < 0 && !events(heard, jukebox).isEmpty()) {
				t0[0] = helper.getTick();
				helper.assertTrue(plays(bard, JukeboxSongs.PIGSTEP), "the first song isn't Pigstep: " + BardWork.playing(bard));
			}
			if (t0[0] < 0) {
				return;
			}
			long since = helper.getTick() - t0[0];
			if (since == 2500) {
				helper.assertTrue(events(heard, jukebox).equals(List.of(1010)), "Pigstep (" + len + " ticks) cut off within 2,500 ticks: "
					+ log(heard, jukebox) + " (1010@t starts a song at game time t, 1011@t stops one)");
				helper.assertTrue(plays(bard, JukeboxSongs.PIGSTEP), "not playing Pigstep 2,500 ticks in: " + BardWork.playing(bard));
			}
			if (since == len + 160) {
				List<String> log = log(heard, jukebox);
				helper.assertTrue(events(heard, jukebox).equals(List.of(1010, 1011, 1010)), "jukebox events once Pigstep ended: " + log
					+ " (expected its start, one stop, the next start)");
				long played = at(log.get(1)) - at(log.get(0));
				helper.assertTrue(played >= len, "Pigstep stopped after " + played + " ticks of " + len);
				long gap = at(log.get(2)) - at(log.get(1));
				helper.assertTrue(gap <= 40, "Mellohi started " + gap + " ticks after Pigstep ended: " + log);
				helper.assertTrue(plays(bard, JukeboxSongs.MELLOHI), "not playing Mellohi: " + BardWork.playing(bard));
				helper.succeed();
			}
		});
	}

	/**
	 * The evening set (9000 to 12500) and its boundary: 11 starts mid-set and the clock is set so 11 ends about 20 ticks
	 * before the set does. 11 is played to its end; whatever the bard does in those last ticks, every start is followed
	 * by exactly one stop (never a stop sent twice, never a start left without its stop), and once the set has ended
	 * nothing is playing.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaBardEveningEdge1107c"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaBardEveningEdge1107c")
	public void aSongEndingJustBeforeTheEveningSetEndsIsStoppedOnce(GameTestHelper helper) {
		List<Packet<?>> heard = Collections.synchronizedList(new ArrayList<>());
		long[] clock = {10000};
		Villager bard = bard(helper, heard, clock, CHEST, Items.MUSIC_DISC_11);
		BlockPos jukebox = helper.absolutePos(TABLE);
		int len = length(helper, JukeboxSongs.ELEVEN);
		// 11 ends at most 3 seconds after its length; the set must end after that, but only just
		long base = 12500 - len - 60 - 20;
		long[] t0 = {-1};
		helper.onEachTick(() -> {
			if (t0[0] < 0 && plays(bard, JukeboxSongs.ELEVEN)) {
				t0[0] = helper.getTick();
			}
			if (t0[0] < 0) {
				return;
			}
			clock[0] = base + (helper.getTick() - t0[0]);
			if (clock[0] == 12500 + 100) {
				List<Integer> events = events(heard, jukebox);
				List<String> log = log(heard, jukebox);
				helper.assertTrue(!events.isEmpty() && events.get(0) == 1010, "no start: " + log);
				for (int i = 0; i < events.size(); i++) {
					helper.assertTrue(events.get(i) == (i % 2 == 0 ? 1010 : 1011), "starts and stops don't alternate: " + log
						+ " (each song starts once and is stopped once)");
				}
				helper.assertTrue(events.size() % 2 == 0, "a song left without its stop after the set ended: " + log);
				long played = at(log.get(1)) - at(log.get(0));
				helper.assertTrue(played >= len, "11 stopped after " + played + " of its " + len + " ticks, though it ended before the set: " + log);
				helper.assertTrue(BardWork.playing(bard).isEmpty(), "still playing " + BardWork.playing(bard) + " 100 ticks after the evening set");
				helper.succeed();
			}
		});
	}

	/**
	 * B976 through the player's own entry point: a survival player breaks the jukebox (mining it) while Cat plays. As
	 * vanilla's jukebox does, the song stops for listeners at that spot, promptly (within 5 seconds), once; the bard
	 * isn't still "playing" and the disc stays in the chest.
	 */
	//$ gametest_ticks_batch AREA '800' '"qaBardPlayerBreaks1107c"'
	@GameTest(template = AREA, timeoutTicks = 800, batch = "qaBardPlayerBreaks1107c")
	public void aPlayerMiningTheJukeboxMidSongStopsIt(GameTestHelper helper) {
		List<Packet<?>> heard = Collections.synchronizedList(new ArrayList<>());
		long[] clock = {1500};
		Villager bard = bard(helper, heard, clock, CHEST, Items.MUSIC_DISC_CAT);
		ServerPlayer miner = StationsSpecGameTests.player(helper);
		BlockPos jukebox = helper.absolutePos(TABLE);
		long[] t0 = {-1};
		long[] brokenAt = {-1};
		helper.onEachTick(() -> {
			if (t0[0] < 0 && plays(bard, JukeboxSongs.CAT)) {
				t0[0] = helper.getTick();
			}
			if (t0[0] >= 0 && brokenAt[0] < 0 && helper.getTick() == t0[0] + 40) {
				helper.assertTrue(miner.gameMode.destroyBlock(jukebox), "setup: the player couldn't break the jukebox");
				helper.assertBlockNotPresent(Blocks.JUKEBOX, TABLE);
				brokenAt[0] = helper.getLevel().getGameTime();
			}
			if (brokenAt[0] >= 0 && helper.getLevel().getGameTime() == brokenAt[0] + 100) {
				List<String> log = log(heard, jukebox);
				helper.assertTrue(events(heard, jukebox).equals(List.of(1010, 1011)), "jukebox events: " + log + "; the player broke it at game time "
					+ brokenAt[0] + " (expected the start, then one stop within 100 ticks)");
				helper.assertTrue(BardWork.playing(bard).isEmpty(), "still playing " + BardWork.playing(bard) + " with the jukebox gone");
				helper.assertTrue(QaPrinterNurseBard1105cGameTests.chest(helper).countItem(Items.MUSIC_DISC_CAT) == 1, "the disc left the chest");
				helper.succeed();
			}
		});
	}

	/**
	 * The jukebox blown up (a TNT-sized explosion, no player) while Cat plays: the stop still goes to its spot, once, and
	 * the bard stops playing. The bard is made invulnerable and the chest stands 7 blocks off, out of the blast.
	 */
	//$ gametest_ticks_batch AREA '800' '"qaBardExplosion1107c"'
	@GameTest(template = AREA, timeoutTicks = 800, batch = "qaBardExplosion1107c")
	public void anExplosionTakingTheJukeboxMidSongStopsIt(GameTestHelper helper) {
		List<Packet<?>> heard = Collections.synchronizedList(new ArrayList<>());
		long[] clock = {1500};
		BlockPos farChest = new BlockPos(2, 2, 9);
		Villager bard = bard(helper, heard, clock, farChest, Items.MUSIC_DISC_CAT);
		bard.setInvulnerable(true);
		BlockPos jukebox = helper.absolutePos(TABLE);
		long[] t0 = {-1};
		long[] boomAt = {-1};
		helper.onEachTick(() -> {
			if (t0[0] < 0 && plays(bard, JukeboxSongs.CAT)) {
				t0[0] = helper.getTick();
			}
			if (t0[0] >= 0 && boomAt[0] < 0 && helper.getTick() == t0[0] + 40) {
				helper.getLevel().explode(null, jukebox.getX() + 0.5, jukebox.getY() + 0.5, jukebox.getZ() + 0.5, 3f, Level.ExplosionInteraction.TNT);
				helper.assertBlockNotPresent(Blocks.JUKEBOX, TABLE);
				boomAt[0] = helper.getLevel().getGameTime();
			}
			if (boomAt[0] >= 0 && helper.getLevel().getGameTime() == boomAt[0] + 100) {
				List<String> log = log(heard, jukebox);
				helper.assertTrue(events(heard, jukebox).equals(List.of(1010, 1011)), "jukebox events: " + log + "; it was blown up at game time "
					+ boomAt[0] + " (expected the start, then one stop within 100 ticks)");
				helper.assertTrue(BardWork.playing(bard).isEmpty(), "still playing " + BardWork.playing(bard) + " with the jukebox gone");
				helper.assertTrue(bard.isAlive(), "setup: the bard died in the blast");
				helper.succeed();
			}
		});
	}

	/**
	 * "The discs stay in the chest": a player takes the only disc (11) out while it plays. Nothing plays forever and no
	 * song starts again with no disc there: by the time 11 would have ended there was one start and one stop, the bard
	 * plays nothing, and no new start follows (he is back to the harp).
	 */
	//$ gametest_ticks_batch AREA '2000' '"qaBardDiscTaken1107c"'
	@GameTest(template = AREA, timeoutTicks = 2000, batch = "qaBardDiscTaken1107c")
	public void theDiscTakenFromTheChestMidSongIsNotStartedAgain(GameTestHelper helper) {
		List<Packet<?>> heard = Collections.synchronizedList(new ArrayList<>());
		long[] clock = {1500};
		Villager bard = bard(helper, heard, clock, CHEST, Items.MUSIC_DISC_11);
		BlockPos jukebox = helper.absolutePos(TABLE);
		int len = length(helper, JukeboxSongs.ELEVEN);
		long[] t0 = {-1};
		helper.onEachTick(() -> {
			if (t0[0] < 0 && plays(bard, JukeboxSongs.ELEVEN)) {
				t0[0] = helper.getTick();
			}
			if (t0[0] < 0) {
				return;
			}
			long since = helper.getTick() - t0[0];
			if (since == 40) {
				Container chest = QaPrinterNurseBard1105cGameTests.chest(helper);
				helper.assertTrue(chest.removeItemNoUpdate(0).is(Items.MUSIC_DISC_11), "setup: 11 wasn't in the chest's first slot");
				chest.setChanged();
			}
			if (since == len + 200) {
				helper.assertTrue(events(heard, jukebox).equals(List.of(1010, 1011)), "jukebox events after 11 was taken mid-song: " + log(heard, jukebox)
					+ " (expected one start and one stop, no new start without a disc)");
				helper.assertTrue(BardWork.playing(bard).isEmpty(), "still playing " + BardWork.playing(bard) + " with no disc in the chest, "
					+ since + " ticks after 11 began");
				helper.succeed();
			}
		});
	}

	/**
	 * The jukebox mined mid-song and a new one put down 4 blocks away, the bard hired at it again (sneak-right-click with
	 * Cat): the old spot got its start and exactly one stop, and the next song starts at the new jukebox. The click itself
	 * changes nothing (he is already a bard); he takes the new jukebox by himself, as vanilla villagers retake a workstation,
	 * which took 275 to 2,257 ticks over 20 runs, hence the long timeout.
	 */
	//$ gametest_ticks_batch AREA '6000' '"qaBardMoved1107c"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qaBardMoved1107c")
	public void aJukeboxRebuiltElsewherePlaysThereAndTheOldSpotIsStopped(GameTestHelper helper) {
		List<Packet<?>> heard = Collections.synchronizedList(new ArrayList<>());
		long[] clock = {1500};
		Villager bard = bard(helper, heard, clock, CHEST, Items.MUSIC_DISC_CAT);
		ServerPlayer player = StationsSpecGameTests.player(helper);
		BlockPos oldSpot = helper.absolutePos(TABLE);
		BlockPos newTable = new BlockPos(6, 2, 3);
		BlockPos newSpot = helper.absolutePos(newTable);
		long[] t0 = {-1};
		long[] movedAt = {-1};
		String[] before = {"<not yet>"};
		helper.onEachTick(() -> {
			if (t0[0] < 0 && plays(bard, JukeboxSongs.CAT)) {
				t0[0] = helper.getTick();
			}
			if (t0[0] >= 0 && movedAt[0] < 0 && helper.getTick() == t0[0] + 40) {
				helper.assertTrue(player.gameMode.destroyBlock(oldSpot), "setup: the player couldn't break the jukebox");
				movedAt[0] = helper.getTick();
			}
			if (movedAt[0] >= 0 && helper.getTick() == movedAt[0] + 60) {
				helper.setBlock(newTable, Blocks.JUKEBOX);
				before[0] = BuiltInRegistries.VILLAGER_PROFESSION.getKey(bard.getVillagerData().getProfession()) + " xp " + bard.getVillagerXp()
					+ " site " + bard.getBrain().getMemory(MemoryModuleType.JOB_SITE);
				StationsSpecGameTests.rightClick(player, bard, new ItemStack(Items.MUSIC_DISC_CAT), true);
				helper.assertTrue(bard.getVillagerData().getProfession() == ModVillagers.BARD, "setup: not a bard after the rehire: "
					+ bard.getVillagerData().getProfession());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(movedAt[0] >= 0, "Cat never started");
			helper.assertTrue(events(heard, newSpot).equals(List.of(1010)), "events at the new jukebox: " + log(heard, newSpot)
				+ "; old spot: " + log(heard, oldSpot) + "; before the rehire: " + before[0] + "; now: "
				+ BuiltInRegistries.VILLAGER_PROFESSION.getKey(bard.getVillagerData().getProfession()) + " site " + bard.getBrain().getMemory(MemoryModuleType.JOB_SITE)
				+ ", status " + QaPrinterNurseBard1105cGameTests.status(helper, bard));
			helper.assertTrue(events(heard, oldSpot).equals(List.of(1010, 1011)), "events at the old spot: " + log(heard, oldSpot)
				+ " (expected Cat's start and one stop)");
			helper.assertTrue(BardWork.playing(bard).isPresent(), "nothing playing at the new jukebox");
		});
	}
}
