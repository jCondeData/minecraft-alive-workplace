package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank;
import io.github.jcondedata.aliveworkplace.guard.RaidDens;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.trainer.RaidAssist;
import io.github.jcondedata.aliveworkplace.trainer.RaidHelpers;
import io.github.jcondedata.aliveworkplace.work.Extension;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane d, 2026-10-25) for ROADMAP 38.1a (with 38.3a and 38.4a) as built (docs/design/M38.md, "As built in 38.1a"):
 * the corners the earlier QA left: a raid found by the helpers' own look while
 * the player was by the den within the last minute, and not after it; a lost raid (home, no chronicle line, no battle
 * XP); a won raid seen by the helpers written once, a later win statistic adding nothing; a win statistic before any
 * raid was joined is no win; two players never share a trainer and a player's helpers serve one den at a time; a
 * helper stuck on the way home is moved there after two minutes and not before. The raid mod's raids are stood in for
 * by {@link Raids}, which hands every raid it doesn't know to the real reflection. Each test runs alone in its batch.
 */
public class QaDenHelperRaidCompatTests implements FabricGameTest {
	static final long PRICE = RaidHelperCompatTests.PRICE;

	/** Stands in for Raid Dens' running raids for this test's player; anything else goes to what was there. */
	static final class Raids implements RaidAssist {
		final RaidAssist real;
		final Map<UUID, RaidHelperCompatTests.FakeRaid> in = new HashMap<>();

		Raids(RaidAssist real) {
			this.real = real;
		}

		@Override
		public Optional<RaidAssist.Raid> raidOf(ServerPlayer player) {
			RaidHelperCompatTests.FakeRaid r = in.get(player.getUUID());
			if (r != null) {
				return Optional.of(r);
			}
			return real != null ? real.raidOf(player) : Optional.empty();
		}

		@Override
		public Optional<RaidAssist.Raid> raid(MinecraftServer server, UUID id) {
			for (RaidHelperCompatTests.FakeRaid r : in.values()) {
				if (r.id.equals(id)) {
					return Optional.of(r);
				}
			}
			return real != null ? real.raid(server, id) : Optional.empty();
		}
	}

	static java.lang.reflect.Field filling() {
		try {
			java.lang.reflect.Field f = Extension.class.getDeclaredField("filling");
			f.setAccessible(true);
			return f;
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("can't reach the raid extension: " + e);
		}
	}

	/** Puts {@link Raids} in front of the real raids; {@link #putBack} undoes it. */
	static Raids stand(GameTestHelper helper) {
		try {
			Object was = filling().get(RaidAssist.EXTENSION);
			if (was instanceof Raids r) { // an earlier test of this class that failed mid-way
				was = r.real;
			}
			helper.assertTrue(was != null, "Raid Dens 0.11.3's raids weren't reached by reflection");
			Raids raids = new Raids((RaidAssist) was);
			filling().set(RaidAssist.EXTENSION, raids);
			return raids;
		} catch (IllegalAccessException e) {
			throw new AssertionError(e);
		}
	}

	static void putBack(Raids raids) {
		try {
			if (filling().get(RaidAssist.EXTENSION) == raids) {
				filling().set(RaidAssist.EXTENSION, raids.real);
			}
		} catch (IllegalAccessException e) {
			throw new AssertionError(e);
		}
	}

	@SuppressWarnings("unchecked")
	static Map<UUID, Long> homeward(ServerLevel level) {
		try {
			java.lang.reflect.Field f = RaidHelpers.Data.class.getDeclaredField("homeward");
			f.setAccessible(true);
			return (Map<UUID, Long>) f.get(RaidHelpers.Data.get(level));
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("can't read who walks home: " + e);
		}
	}

	/** Holds the 3 by 3 chunks round {@code middle} loaded, or lets them go. */
	static void hold(ServerLevel level, ChunkPos middle, boolean on) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				level.setChunkForced(middle.x + dx, middle.z + dz, on);
			}
		}
	}

	/** Whether the 3 by 3 chunks round {@code middle} are loaded with their entities and ticking. */
	static boolean loaded(ServerLevel level, ChunkPos middle) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				ChunkPos c = new ChunkPos(middle.x + dx, middle.z + dz);
				if (!level.areEntitiesLoaded(c.toLong()) || !level.isPositionEntityTicking(c.getMiddleBlockPosition(-60))) {
					return false;
				}
			}
		}
		return true;
	}

	static VillageHallBlockEntity hall(ServerLevel level, BlockPos pos) {
		return (VillageHallBlockEntity) level.getBlockEntity(pos);
	}

	/**
	 * The helpers' own look finds the raid: the player in a raid while by the den within the last minute is joined (the
	 * raid's own id kept, so the blows land); a player whose last time by the den was over a minute ago is not.
	 */
	//$ gametest_ticks_batch 'CompatGameTests.AREA' '400' '"qaDenRaidLook"'
	@GameTest(template = CompatGameTests.AREA, timeoutTicks = 400, batch = "qaDenRaidLook")
	public void theHelpersLookFindsARaidOnlyWithinAMinuteOfTheDen(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos[] at = RaidHelperCompatTests.village(helper);
		Villager master = RaidHelperCompatTests.trainer(helper, 0, 5);
		ServerPlayer player = RaidHelperCompatTests.player(helper, helper.absolutePos(new BlockPos(3, 2, 3)), PRICE);
		Raids raids = stand(helper);
		RaidHelperCompatTests.FakeRaid raid = new RaidHelperCompatTests.FakeRaid(400f);
		RaidHelpers.hire(level, at[0], at[1], player);
		RaidHelpers.Hire hire = RaidHelperCompatTests.hire(helper, player);
		// raids are fought far off (the raid mod's own dimension): the player is 40 blocks from the den. A real player keeps
		// the chunks round them loaded, and the helper who catches up is moved beside them; a mock player loads nothing, so
		// those chunks are held here as a player's would be (B482: the helper was moved into an unloaded chunk, out of
		// reach, and the raid was joined with no one to cheer whenever no earlier test happened to have loaded it)
		BlockPos far = BlockPos.containing(at[1].getX() + 40.5, at[1].getY(), at[1].getZ() + 0.5);
		ChunkPos held = new ChunkPos(far);
		hold(level, held, true);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(loaded(level, held), "waiting for the chunks round " + far + " to load"))
			.thenExecute(() -> {
				// the raid only now (the player, by the den while the chunks load, would join it at once)
				raids.in.put(player.getUUID(), raid);
				player.teleportTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
				hire.lastNear = level.getGameTime() - RaidHelpers.RECENT - 5;
			})
			.thenExecuteFor(45, () -> RaidHelpers.tick(level, hire))
			.thenExecute(() -> {
				helper.assertTrue(!hire.joined && RaidHelperCompatTests.cheers(player) == 0,
					"a raid joined over a minute after leaving the den was taken as the den's");
				helper.assertTrue(RaidHelpers.helpers(level, hire).contains(master) && master.distanceTo(player) <= 8,
					"the helper didn't catch up with the player at " + player.blockPosition() + ": at " + master.blockPosition()
						+ ", reachable " + (level.getEntity(master.getUUID()) != null));
			})
			.thenExecute(() -> hire.lastNear = level.getGameTime() - 20)
			.thenExecuteFor(25, () -> RaidHelpers.tick(level, hire))
			.thenExecute(() -> {
				helper.assertTrue(hire.joined && RaidHelperCompatTests.cheers(player) == 1,
					"a raid joined within the minute: joined " + hire.joined + ", " + RaidHelperCompatTests.cheers(player) + " cheers, helpers reachable "
						+ RaidHelpers.helpers(level, hire).size() + " of " + hire.helpers.size() + " (the helper at " + master.blockPosition()
						+ ", the player at " + player.blockPosition() + ")");
				helper.assertTrue(raid.id.equals(hire.raid), "the raid's id wasn't kept: " + hire.raid);
				putBack(raids);
				RaidHelperCompatTests.cleanUp(helper, at[1], player);
				hold(level, held, false);
			})
			.thenSucceed();
	}

	/**
	 * A lost raid: the player reads that it is lost, the helpers head home, the chronicle gets no line and no helper gets
	 * a battle's experience.
	 */
	//$ gametest_batch 'CompatGameTests.AREA' '"qaDenRaidLost"'
	@GameTest(template = CompatGameTests.AREA, batch = "qaDenRaidLost")
	public void aLostRaidSendsTheHelpersHomeWithNoLineAndNoExperience(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos[] at = RaidHelperCompatTests.village(helper);
		Villager master = RaidHelperCompatTests.trainer(helper, 0, 5);
		QaDenHelperCompatTests.Talker talker = QaDenHelperCompatTests.talker(helper, at[1].offset(3, 0, 0), PRICE);
		ServerPlayer player = talker.player();
		Raids raids = stand(helper);
		try {
			RaidHelpers.hire(level, at[0], at[1], player);
			RaidHelpers.Hire hire = RaidHelperCompatTests.hire(helper, player);
			RaidHelperCompatTests.FakeRaid raid = new RaidHelperCompatTests.FakeRaid(400f);
			raids.in.put(player.getUUID(), raid);
			RaidHelpers.joined(level, hire, player, raid.id);
			int lines = hall(level, at[0]).chronicle().size();
			int xp = master.getVillagerXp();
			raid.state = RaidAssist.State.LOST;
			hire.nextStrike = level.getGameTime();
			talker.seen().clear();
			RaidHelpers.tick(level, hire);
			helper.assertTrue(RaidHelpers.hireOf(level.getServer(), player.getUUID()).isEmpty() && RaidHelpers.homeward(level, master.getUUID()),
				"after a lost raid the helper should head home");
			helper.assertTrue(talker.said().contains("The raid at the tier 3 Fire raid den is lost. Your raid helpers head home"),
				"the player read: " + talker.said());
			helper.assertTrue(hall(level, at[0]).chronicle().size() == lines, "a lost raid went in the chronicle");
			helper.assertTrue(master.getVillagerXp() == xp, "a lost raid gave the helper experience: " + xp + " to " + master.getVillagerXp());
			// the win statistic arriving afterwards (it can't, but a late one) changes nothing
			RaidHelperCompatTests.stat(player, "tier_three_completed");
			helper.assertTrue(hall(level, at[0]).chronicle().size() == lines, "a win statistic after the loss went in the chronicle");
		} finally {
			putBack(raids);
			RaidHelperCompatTests.cleanUp(helper, at[1], player);
		}
		helper.succeed();
	}

	/**
	 * A raid the helpers see won (its own state) is written once and they head home; Raid Dens' win statistic arriving
	 * after it adds no second line. A win statistic while no raid was joined is no win at all.
	 */
	//$ gametest_batch 'CompatGameTests.AREA' '"qaDenRaidWonOnce"'
	@GameTest(template = CompatGameTests.AREA, batch = "qaDenRaidWonOnce")
	public void aWonRaidIsWrittenOnceAndAWinBeforeJoiningIsNone(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos[] at = RaidHelperCompatTests.village(helper);
		Villager master = RaidHelperCompatTests.trainer(helper, 0, 5);
		ServerPlayer player = RaidHelperCompatTests.player(helper, at[1].offset(3, 0, 0), PRICE);
		Raids raids = stand(helper);
		try {
			RaidHelpers.hire(level, at[0], at[1], player);
			RaidHelpers.Hire hire = RaidHelperCompatTests.hire(helper, player);
			int lines = hall(level, at[0]).chronicle().size();
			RaidHelperCompatTests.stat(player, "tier_three_completed");
			helper.assertTrue(RaidHelpers.hireOf(level.getServer(), player.getUUID()).isPresent() && hall(level, at[0]).chronicle().size() == lines,
				"a win statistic before any raid was joined counted as a win");
			RaidHelperCompatTests.FakeRaid raid = new RaidHelperCompatTests.FakeRaid(400f);
			raids.in.put(player.getUUID(), raid);
			RaidHelpers.joined(level, hire, player, raid.id);
			int xp = master.getVillagerXp();
			raid.state = RaidAssist.State.WON;
			hire.nextStrike = level.getGameTime();
			RaidHelpers.tick(level, hire);
			helper.assertTrue(RaidHelpers.hireOf(level.getServer(), player.getUUID()).isEmpty() && RaidHelpers.homeward(level, master.getUUID()),
				"after a won raid the helper should head home");
			helper.assertTrue(hall(level, at[0]).chronicle().size() == lines + 1, "chronicle lines for the win: " + (hall(level, at[0]).chronicle().size() - lines));
			Chronicle.Entry line = hall(level, at[0]).chronicle().get(hall(level, at[0]).chronicle().size() - 1);
			helper.assertTrue(line.text().getString().matches(".+ and .+'s band beat the tier 3 Fire raid den"), "the line: " + line.text().getString());
			helper.assertTrue(master.getVillagerXp() > xp, "no battle experience for the win");
			RaidHelperCompatTests.stat(player, "tier_three_completed");
			helper.assertTrue(hall(level, at[0]).chronicle().size() == lines + 1, "the late win statistic wrote a second line");
		} finally {
			putBack(raids);
			RaidHelperCompatTests.cleanUp(helper, at[1], player);
		}
		helper.succeed();
	}

	/**
	 * Two players at one village: the second gets the next best trainer, never the first one's, and then nobody; a player
	 * whose helpers are hired for one den is refused at another (an active, known den) and pays nothing.
	 */
	//$ gametest_batch 'CompatGameTests.AREA' '"qaDenRaidTwoPlayers"'
	@GameTest(template = CompatGameTests.AREA, batch = "qaDenRaidTwoPlayers")
	public void twoPlayersNeverShareATrainerAndHelpersServeOneDen(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos[] at = RaidHelperCompatTests.village(helper);
		Villager master = RaidHelperCompatTests.trainer(helper, 0, 5);
		Villager journeyman = RaidHelperCompatTests.trainer(helper, 1, 3);
		BlockPos second = helper.absolutePos(new BlockPos(4, 2, 14));
		level.setBlockAndUpdate(second, RaidDenCompatTests.den("tier_two", "water", true));
		RaidDens.chunkLoaded(level, level.getChunkAt(second));
		RaidDens.tick(level);
		ServerPlayer first = RaidHelperCompatTests.player(helper, helper.absolutePos(new BlockPos(3, 2, 3)), PRICE * 3);
		ServerPlayer other = RaidHelperCompatTests.player(helper, helper.absolutePos(new BlockPos(5, 2, 3)), PRICE * 3);
		try {
			helper.assertTrue(RaidDenCompatTests.listed(helper, at[0], second).isPresent(), "the second den isn't known");
			RaidHelpers.hire(level, at[0], at[1], first);
			helper.assertTrue(RaidHelperCompatTests.hire(helper, first).helpers.equals(List.of(master.getUUID())), "the first player's helper");
			String said = RaidHelpers.hire(level, at[0], at[1], other).getString();
			helper.assertTrue(said.matches(".+ will help you raid the tier 3 Fire raid den \\(.+\\): 1 of 3 hired\\. Lead the way!"), "the second player: " + said);
			helper.assertTrue(RaidHelperCompatTests.hire(helper, other).helpers.equals(List.of(journeyman.getUUID())),
				"the second player got " + RaidHelperCompatTests.hire(helper, other).helpers + ", the Journeyman is " + journeyman.getUUID());
			String none = RaidHelpers.hire(level, at[0], at[1], other).getString();
			helper.assertTrue(none.equals("No trainer of the village is free to help") && CobbleDollarsBank.balance(other) == PRICE * 2,
				"with every trainer taken: " + none + ", balance " + CobbleDollarsBank.balance(other));
			String elsewhere = RaidHelpers.hire(level, at[0], second, first).getString();
			helper.assertTrue(elsewhere.equals("Your raid helpers are hired for another den") && CobbleDollarsBank.balance(first) == PRICE * 2,
				"the first player at the second den: " + elsewhere + ", balance " + CobbleDollarsBank.balance(first));
			helper.assertTrue(RaidHelperCompatTests.hire(helper, first).den.equals(at[1]) && RaidHelperCompatTests.hire(helper, first).helpers.size() == 1,
				"the first player's hiring changed");
		} finally {
			level.setBlockAndUpdate(second, Blocks.AIR.defaultBlockState());
			RaidHelperCompatTests.cleanUp(helper, at[1], first, other);
		}
		helper.succeed();
	}

	/**
	 * A helper who can't get home (walled in) is still walking home after a while, and is moved home once two minutes
	 * have passed since the hiring ended; then free to hire again.
	 */
	//$ gametest_ticks_batch 'CompatGameTests.AREA' '400' '"qaDenRaidStuck"'
	@GameTest(template = CompatGameTests.AREA, timeoutTicks = 400, batch = "qaDenRaidStuck")
	public void aHelperStuckOnTheWayHomeIsMovedThereAfterTwoMinutes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos[] at = RaidHelperCompatTests.village(helper);
		Villager master = RaidHelperCompatTests.trainer(helper, 0, 5);
		ServerPlayer player = RaidHelperCompatTests.player(helper, helper.absolutePos(new BlockPos(3, 2, 3)), PRICE);
		BlockPos cell = new BlockPos(12, 2, 3);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int dy = 0; dy <= 2; dy++) {
					if (dx != 0 || dz != 0 || dy == 2) {
						helper.setBlock(cell.offset(dx, dy, dz), Blocks.GLASS);
					}
				}
			}
		}
		BlockPos post = helper.absolutePos(RaidHelperCompatTests.POSTS[0]);
		BlockPos inCell = helper.absolutePos(cell);
		RaidHelpers.hire(level, at[0], at[1], player);
		RaidHelpers.Hire hire = RaidHelperCompatTests.hire(helper, player);
		master.teleportTo(inCell.getX() + 0.5, inCell.getY(), inCell.getZ() + 0.5);
		RaidHelpers.over(level, hire, "message.aliveworkplace.raid_helpers.day_over");
		helper.startSequence()
			.thenIdle(60)
			.thenExecute(() -> {
				helper.assertTrue(RaidHelpers.homeward(level, master.getUUID()) && master.blockPosition().distSqr(inCell) <= 2,
					"three seconds after the hiring ended the walled-in helper is at " + master.blockPosition() + " (cell " + inCell + ")");
				helper.assertTrue(!RaidHelpers.free(level, at[0]).contains(master), "a helper on the way home is free to hire");
				homeward(level).put(master.getUUID(), level.getGameTime() - 2401);
			})
			.thenWaitUntil(() -> helper.assertTrue(!RaidHelpers.homeward(level, master.getUUID()), "two minutes on, the helper is still on the way home"))
			.thenExecute(() -> {
				double flat = Math.hypot(master.getX() - post.getX() - 0.5, master.getZ() - post.getZ() - 0.5);
				helper.assertTrue(flat <= 5.5, "the helper was moved " + Math.round(flat) + " blocks from the post, at " + master.blockPosition());
				helper.assertTrue(RaidHelpers.free(level, at[0]).contains(master), "home again, the helper isn't free to hire");
				RaidHelperCompatTests.cleanUp(helper, at[1], player);
			})
			.thenSucceed();
	}
}
