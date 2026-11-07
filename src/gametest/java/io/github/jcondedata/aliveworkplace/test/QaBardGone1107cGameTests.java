package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.bard.BardWork;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.JukeboxSongs;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA 2026-11-07 (lane c) of B1003 from its spec ("with the bard gone the song stops at the jukebox, as it now does when the
 * jukebox is broken (B976)"), the cases QaBardSet1107cBugTests (killed, discarded) leave out: two bards, a bard gone after
 * his song has ended (a player's own disc in that jukebox plays on), unloaded with his chunk, and the jukebox broken
 * first. What a client hears is read from the jukebox level events a listening player is sent (1010 start, 1011 stop),
 * set up as in {@link QaBardSet1107cGameTests}.
 */
public class QaBardGone1107cGameTests implements FabricGameTest {
	private static final String AREA = QaBardSet1107cGameTests.AREA;
	private static final BlockPos TABLE = QaBardSet1107cGameTests.TABLE;
	private static final BlockPos CHEST = QaBardSet1107cGameTests.CHEST;
	private static final BlockPos TABLE_2 = new BlockPos(17, 2, 17);
	private static final BlockPos CHEST_2 = new BlockPos(17, 2, 19);
	private static final BlockPos STANDING_2 = new BlockPos(18, 2, 18);

	/**
	 * Two bards, each at his own jukebox 15 blocks apart, both playing. One is killed mid-song: his song stops at his
	 * jukebox, once; the other's song plays on, never stopped, and he is still listed as playing it.
	 */
	//$ gametest_ticks_batch AREA '1000' '"qaBardGoneTwo1107c"'
	@GameTest(template = AREA, timeoutTicks = 1000, batch = "qaBardGoneTwo1107c")
	public void killingOneOfTwoBardsStopsOnlyHisSong(GameTestHelper helper) {
		List<Packet<?>> heard = Collections.synchronizedList(new ArrayList<>());
		long[] clock = {1500};
		Villager first = QaBardSet1107cGameTests.bard(helper, heard, clock, CHEST, Items.MUSIC_DISC_CAT);
		helper.setBlock(TABLE_2, Blocks.JUKEBOX);
		helper.setBlock(CHEST_2, Blocks.CHEST);
		Container chest2 = helper.getBlockEntity(CHEST_2);
		chest2.setItem(0, new ItemStack(Items.MUSIC_DISC_BLOCKS));
		Villager second = QaPrinterNurseBard1105cGameTests.hired(helper, STANDING_2, Items.MUSIC_DISC_BLOCKS, ModVillagers.BARD);
		BlockPos one = helper.absolutePos(TABLE);
		BlockPos two = helper.absolutePos(TABLE_2);
		long[] killedAt = {-1};
		helper.onEachTick(() -> {
			if (killedAt[0] < 0 && QaBardSet1107cGameTests.plays(first, JukeboxSongs.CAT) && QaBardSet1107cGameTests.plays(second, JukeboxSongs.BLOCKS)
				&& !QaBardSet1107cGameTests.events(heard, two).isEmpty()) {
				helper.runAfterDelay(40, () -> {
					first.kill();
					killedAt[0] = helper.getLevel().getGameTime();
				});
				killedAt[0] = Long.MAX_VALUE;
			}
			if (killedAt[0] > 0 && killedAt[0] != Long.MAX_VALUE && helper.getLevel().getGameTime() == killedAt[0] + 100) {
				helper.assertTrue(QaBardSet1107cGameTests.events(heard, one).equals(List.of(1010, 1011)), "the killed bard's jukebox: "
					+ QaBardSet1107cGameTests.log(heard, one) + " (killed at game time " + killedAt[0] + "; expected his start, then one stop)");
				helper.assertTrue(QaBardSet1107cGameTests.events(heard, two).equals(List.of(1010)), "the other bard's jukebox: "
					+ QaBardSet1107cGameTests.log(heard, two) + " (the first bard was killed at game time " + killedAt[0]
					+ "; Blocks runs 6,900 ticks, so it should still be playing, never stopped)");
				helper.assertTrue(QaBardSet1107cGameTests.plays(second, JukeboxSongs.BLOCKS), "the living bard no longer plays Blocks: " + BardWork.playing(second));
				helper.assertTrue(BardWork.playing(first).isEmpty(), "the dead bard still listed as playing " + BardWork.playing(first));
				helper.succeed();
			}
		});
	}

	/**
	 * A bard whose song has already ended (the morning set over at 3500) is killed after a player has put her own disc
	 * (13) in that jukebox. The player's disc is the jukebox's own, not the bard's: it keeps playing, no stop is sent for
	 * it, and the disc stays in the jukebox.
	 */
	//$ gametest_ticks_batch AREA '1000' '"qaBardGoneAfter1107c"'
	@GameTest(template = AREA, timeoutTicks = 1000, batch = "qaBardGoneAfter1107c")
	public void aBardKilledAfterHisSongLeavesAPlayersDiscPlaying(GameTestHelper helper) {
		List<Packet<?>> heard = Collections.synchronizedList(new ArrayList<>());
		long[] clock = {1500};
		Villager bard = QaBardSet1107cGameTests.bard(helper, heard, clock, CHEST, Items.MUSIC_DISC_CAT);
		ServerPlayer player = StationsSpecGameTests.player(helper);
		BlockPos jukebox = helper.absolutePos(TABLE);
		long[] t0 = {-1};
		long[] insertedAt = {-1};
		long[] killedAt = {-1};
		helper.onEachTick(() -> {
			long now = helper.getLevel().getGameTime();
			if (t0[0] < 0 && QaBardSet1107cGameTests.plays(bard, JukeboxSongs.CAT)) {
				t0[0] = now;
			}
			if (t0[0] >= 0 && now == t0[0] + 40) {
				clock[0] = 3600; // the morning set is over: his song ends
			}
			if (t0[0] >= 0 && insertedAt[0] < 0 && now >= t0[0] + 140) {
				helper.assertTrue(QaBardSet1107cGameTests.events(heard, jukebox).equals(List.of(1010, 1011)), "setup: the morning set's end didn't stop Cat once: "
					+ QaBardSet1107cGameTests.log(heard, jukebox));
				helper.assertTrue(BardWork.playing(bard).isEmpty(), "setup: still playing " + BardWork.playing(bard) + " after the morning set");
				ItemStack disc = new ItemStack(Items.MUSIC_DISC_13);
				player.setItemInHand(InteractionHand.MAIN_HAND, disc);
				player.gameMode.useItemOn(player, helper.getLevel(), disc, InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(jukebox), Direction.UP, jukebox, false));
				helper.assertTrue(QaBardSet1107cGameTests.events(heard, jukebox).equals(List.of(1010, 1011, 1010)), "setup: the player's disc didn't start: "
					+ QaBardSet1107cGameTests.log(heard, jukebox));
				insertedAt[0] = now;
			}
			if (insertedAt[0] >= 0 && killedAt[0] < 0 && now == insertedAt[0] + 40) {
				bard.kill();
				killedAt[0] = now;
			}
			if (killedAt[0] >= 0 && now == killedAt[0] + 100) {
				helper.assertTrue(QaBardSet1107cGameTests.events(heard, jukebox).equals(List.of(1010, 1011, 1010)), "jukebox events: "
					+ QaBardSet1107cGameTests.log(heard, jukebox) + "; the player's disc went in at " + insertedAt[0] + " and the bard (his song long over) was killed at "
					+ killedAt[0] + " (expected the player's 13 to play on, never stopped)");
				JukeboxBlockEntity box = (JukeboxBlockEntity) helper.getLevel().getBlockEntity(jukebox);
				helper.assertTrue(box != null && box.getTheItem().is(Items.MUSIC_DISC_13), "the player's disc left the jukebox: " + (box == null ? "no jukebox" : box.getTheItem()));
				helper.assertTrue(box.getSongPlayer().isPlaying(), "the jukebox no longer plays the player's 13");
				helper.succeed();
			}
		});
	}

	/**
	 * B1003's third way a bard leaves: his chunk unloads mid-song (the removal the game makes then, UNLOADED_TO_CHUNK).
	 * The song stops at the jukebox once and he is no longer listed as playing.
	 */
	//$ gametest_ticks_batch AREA '800' '"qaBardGoneUnloaded1107c"'
	@GameTest(template = AREA, timeoutTicks = 800, batch = "qaBardGoneUnloaded1107c")
	public void aBardUnloadedWithHisChunkMidSongStopsTheSong(GameTestHelper helper) {
		List<Packet<?>> heard = Collections.synchronizedList(new ArrayList<>());
		long[] clock = {1500};
		Villager bard = QaBardSet1107cGameTests.bard(helper, heard, clock, CHEST, Items.MUSIC_DISC_CAT);
		BlockPos jukebox = helper.absolutePos(TABLE);
		long[] t0 = {-1};
		long[] goneAt = {-1};
		helper.onEachTick(() -> {
			long now = helper.getLevel().getGameTime();
			if (t0[0] < 0 && QaBardSet1107cGameTests.plays(bard, JukeboxSongs.CAT)) {
				t0[0] = now;
			}
			if (t0[0] >= 0 && goneAt[0] < 0 && now == t0[0] + 40) {
				bard.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
				goneAt[0] = now;
			}
			if (goneAt[0] >= 0 && now == goneAt[0] + 100) {
				helper.assertTrue(QaBardSet1107cGameTests.events(heard, jukebox).equals(List.of(1010, 1011)), "jukebox events: "
					+ QaBardSet1107cGameTests.log(heard, jukebox) + "; the bard's chunk unloaded at game time " + goneAt[0] + " (expected the start, then one stop)");
				helper.assertTrue(BardWork.playing(bard).isEmpty(), "an unloaded bard still listed as playing " + BardWork.playing(bard));
				helper.succeed();
			}
		});
	}

	/**
	 * The jukebox is mined mid-song (B976 stops the song), then the bard is killed: no second stop is sent to the empty
	 * spot, nothing breaks, and he is not listed as playing.
	 */
	//$ gametest_ticks_batch AREA '800' '"qaBardGoneBroken1107c"'
	@GameTest(template = AREA, timeoutTicks = 800, batch = "qaBardGoneBroken1107c")
	public void aBardKilledAfterHisJukeboxBrokeSendsNoSecondStop(GameTestHelper helper) {
		List<Packet<?>> heard = Collections.synchronizedList(new ArrayList<>());
		long[] clock = {1500};
		Villager bard = QaBardSet1107cGameTests.bard(helper, heard, clock, CHEST, Items.MUSIC_DISC_CAT);
		ServerPlayer miner = StationsSpecGameTests.player(helper);
		BlockPos jukebox = helper.absolutePos(TABLE);
		long[] t0 = {-1};
		long[] brokenAt = {-1};
		long[] killedAt = {-1};
		helper.onEachTick(() -> {
			long now = helper.getLevel().getGameTime();
			if (t0[0] < 0 && QaBardSet1107cGameTests.plays(bard, JukeboxSongs.CAT)) {
				t0[0] = now;
			}
			if (t0[0] >= 0 && brokenAt[0] < 0 && now == t0[0] + 40) {
				helper.assertTrue(miner.gameMode.destroyBlock(jukebox), "setup: the player couldn't break the jukebox");
				brokenAt[0] = now;
			}
			if (brokenAt[0] >= 0 && killedAt[0] < 0 && now == brokenAt[0] + 5) {
				bard.kill(); // before he can walk off: the stop for the broken jukebox may still be pending
				killedAt[0] = now;
			}
			if (killedAt[0] >= 0 && now == killedAt[0] + 100) {
				helper.assertTrue(QaBardSet1107cGameTests.events(heard, jukebox).equals(List.of(1010, 1011)), "jukebox events: "
					+ QaBardSet1107cGameTests.log(heard, jukebox) + "; the jukebox was mined at " + brokenAt[0] + ", the bard killed at " + killedAt[0]
					+ " (expected the start, then exactly one stop)");
				helper.assertTrue(BardWork.playing(bard).isEmpty(), "a dead bard still listed as playing " + BardWork.playing(bard));
				helper.succeed();
			}
		});
	}
}
