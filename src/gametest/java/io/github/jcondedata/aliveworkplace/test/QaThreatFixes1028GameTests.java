package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.BanditFort;
import io.github.jcondedata.aliveworkplace.guard.BanditKing;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;

/**
 * QA (lane qa-lane-d-1028-0708), B659 (31.15 the Bandit King): "the bar is shown only to players within 32 blocks of
 * the King, in his dimension". What {@link QaBanditKing1028GameTests} and {@link QaBanditKingBugTests} leave out: the
 * 32-block edge itself (exactly 32 counts), a one-block step across it each way, a player who dies beside him, a player
 * who logs out, and a King who is killed or removed (his bar goes for everyone).
 */
public class QaThreatFixes1028GameTests {
	private static final String TALL = "aliveworkplace_test:tall_area";

	private static Mob king(GameTestHelper helper) {
		BlockPos at = helper.absolutePos(new BlockPos(10, 2, 10));
		Mob king = BanditKing.spawn(helper.getLevel(), at, at, helper.absolutePos(new BlockPos(12, 2, 10)));
		helper.assertTrue(king != null, "no King");
		king.setNoAi(true);
		return king;
	}

	private static ServerPlayer survivor(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	/** Takes the King and the players away, and lets the fort drop his bar. */
	private static void cleanUp(ServerLevel level, Mob king, ServerPlayer... players) {
		king.discard();
		BanditFort.tick(level);
		for (ServerPlayer p : players) {
			level.getServer().getPlayerList().remove(p);
			p.discard();
		}
	}

	/**
	 * A player walking out from 31 blocks to 33 loses the bar on the next look; walking back to 31 gives it again (each
	 * step is one block across the edge, with the same player and the same bar).
	 */
	//$ gametest_ticks_batch TALL '40' '"qaKingBar1028Walk"'
	@GameTest(template = TALL, timeoutTicks = 40, batch = "qaKingBar1028Walk")
	public void qaKingsBarGoesAt33AndComesBackAt31(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		Mob king = king(helper);
		ServerPlayer player = survivor(helper);
		player.teleportTo(king.getX() + 31, king.getY(), king.getZ());
		BanditFort.tick(level);
		ServerBossEvent bar = BanditKing.bar(king.getUUID());
		boolean at31 = bar != null && bar.getPlayers().contains(player);
		player.teleportTo(king.getX() + 33, king.getY(), king.getZ());
		BanditFort.tick(level);
		BanditFort.tick(level);
		boolean at33 = bar != null && bar.getPlayers().contains(player);
		player.teleportTo(king.getX() + 31, king.getY(), king.getZ());
		BanditFort.tick(level);
		boolean back = bar != null && bar.getPlayers().contains(player);
		boolean once = bar != null && bar.getPlayers().stream().filter(p -> p == player).count() == 1;
		cleanUp(level, king, player);
		helper.assertTrue(bar != null, "no boss bar for the King");
		helper.assertTrue(at31, "a player 31 blocks from the King doesn't see his bar");
		helper.assertFalse(at33, "a player who walked from 31 to 33 blocks from the King still sees his bar");
		helper.assertTrue(back, "a player who came back from 33 to 31 blocks doesn't get the King's bar again");
		helper.assertTrue(once, "the player who came back is on the bar more than once");
		helper.succeed();
	}

	/**
	 * Exactly 32 blocks is within 32: a player standing exactly 32 blocks east of the King (same height) sees the bar,
	 * and so does one exactly 32 blocks north; one 32.01 blocks off doesn't.
	 */
	//$ gametest_ticks_batch TALL '40' '"qaKingBar1028Edge"'
	@GameTest(template = TALL, timeoutTicks = 40, batch = "qaKingBar1028Edge")
	public void qaKingsBarShowsAtExactly32Blocks(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		Mob king = king(helper);
		ServerPlayer east = survivor(helper);
		ServerPlayer north = survivor(helper);
		ServerPlayer past = survivor(helper);
		east.teleportTo(king.getX() + 32, king.getY(), king.getZ());
		north.teleportTo(king.getX(), king.getY(), king.getZ() - 32);
		past.teleportTo(king.getX() - 32.01, king.getY(), king.getZ());
		BanditFort.tick(level);
		ServerBossEvent bar = BanditKing.bar(king.getUUID());
		double eastAt = Math.sqrt(east.distanceToSqr(king));
		double northAt = Math.sqrt(north.distanceToSqr(king));
		boolean eastSees = bar != null && bar.getPlayers().contains(east);
		boolean northSees = bar != null && bar.getPlayers().contains(north);
		boolean pastSees = bar != null && bar.getPlayers().contains(past);
		cleanUp(level, king, east, north, past);
		helper.assertTrue(bar != null, "no boss bar for the King");
		helper.assertTrue(eastAt == 32.0 && northAt == 32.0, "the players aren't exactly 32 blocks off: " + eastAt + ", " + northAt);
		helper.assertTrue(eastSees, "a player exactly 32 blocks east of the King doesn't see his bar");
		helper.assertTrue(northSees, "a player exactly 32 blocks north of the King doesn't see his bar");
		helper.assertFalse(pastSees, "a player 32.01 blocks from the King sees his bar");
		helper.succeed();
	}

	/**
	 * A player 10 blocks from the King who dies there loses the bar on the next look, and doesn't get it back while
	 * lying dead beside him; a living player next to him keeps it.
	 */
	//$ gametest_ticks_batch TALL '40' '"qaKingBar1028Dead"'
	@GameTest(template = TALL, timeoutTicks = 40, batch = "qaKingBar1028Dead")
	public void qaKingsBarLeavesAPlayerWhoDies(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		Mob king = king(helper);
		ServerPlayer doomed = survivor(helper);
		ServerPlayer alive = survivor(helper);
		doomed.teleportTo(king.getX() + 10, king.getY(), king.getZ());
		alive.teleportTo(king.getX(), king.getY(), king.getZ() + 10);
		BanditFort.tick(level);
		ServerBossEvent bar = BanditKing.bar(king.getUUID());
		boolean before = bar != null && bar.getPlayers().contains(doomed);
		doomed.kill();
		boolean dead = !doomed.isAlive() && !doomed.isRemoved() && doomed.level() == level;
		BanditFort.tick(level);
		boolean afterOne = bar != null && bar.getPlayers().contains(doomed);
		BanditFort.tick(level);
		BanditFort.tick(level);
		boolean afterThree = bar != null && bar.getPlayers().contains(doomed);
		boolean aliveSees = bar != null && bar.getPlayers().contains(alive);
		cleanUp(level, king, doomed, alive);
		helper.assertTrue(bar != null, "no boss bar for the King");
		helper.assertTrue(before, "the player 10 blocks off doesn't see the bar before dying");
		helper.assertTrue(dead, "the player isn't lying dead in the King's dimension (alive " + doomed.isAlive() + ", removed " + doomed.isRemoved() + ")");
		helper.assertFalse(afterOne, "a dead player 10 blocks from the King still sees his boss bar");
		helper.assertFalse(afterThree, "a dead player got the King's boss bar back while lying beside him");
		helper.assertTrue(aliveSees, "the living player 10 blocks off lost the bar when the other died");
		helper.succeed();
	}

	/**
	 * A player 10 blocks from the King who logs out is taken off his bar at the next look; another player 10 blocks off
	 * keeps it.
	 */
	//$ gametest_ticks_batch TALL '40' '"qaKingBar1028Logout"'
	@GameTest(template = TALL, timeoutTicks = 40, batch = "qaKingBar1028Logout")
	public void qaKingsBarDropsAPlayerWhoLogsOut(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		Mob king = king(helper);
		ServerPlayer leaver = survivor(helper);
		ServerPlayer stayer = survivor(helper);
		leaver.teleportTo(king.getX() + 10, king.getY(), king.getZ());
		stayer.teleportTo(king.getX() - 10, king.getY(), king.getZ());
		BanditFort.tick(level);
		ServerBossEvent bar = BanditKing.bar(king.getUUID());
		boolean before = bar != null && bar.getPlayers().contains(leaver) && bar.getPlayers().contains(stayer);
		level.getServer().getPlayerList().remove(leaver); // logs out
		boolean gone = level.getServer().getPlayerList().getPlayer(leaver.getUUID()) == null;
		BanditFort.tick(level);
		boolean leaverShown = bar != null && bar.getPlayers().contains(leaver);
		boolean stayerShown = bar != null && bar.getPlayers().contains(stayer);
		int count = bar == null ? -1 : bar.getPlayers().size();
		cleanUp(level, king, stayer);
		helper.assertTrue(bar != null, "no boss bar for the King");
		helper.assertTrue(before, "the two players 10 blocks off don't both see the bar at first");
		helper.assertTrue(gone, "the mock player didn't log out");
		helper.assertFalse(leaverShown, "a player who logged out is still on the King's boss bar");
		helper.assertTrue(stayerShown, "the player still 10 blocks off lost the bar when the other logged out");
		helper.assertTrue(count == 1, "the bar should show to 1 player, shows to " + count);
		helper.succeed();
	}

	/**
	 * The King killed (by a player's sword or anything): at the next look his bar is gone for every player who saw it, and
	 * none is kept for him; the same for a King who is removed (unloaded) without dying.
	 */
	//$ gametest_ticks_batch TALL '60' '"qaKingBar1028KingGone"'
	@GameTest(template = TALL, timeoutTicks = 60, batch = "qaKingBar1028KingGone")
	public void qaKingsBarGoesForEveryoneWhenHeIsKilledOrRemoved(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		Mob king = king(helper);
		ServerPlayer one = survivor(helper);
		ServerPlayer two = survivor(helper);
		one.teleportTo(king.getX() + 5, king.getY(), king.getZ());
		two.teleportTo(king.getX(), king.getY(), king.getZ() + 20);
		BanditFort.tick(level);
		ServerBossEvent bar = BanditKing.bar(king.getUUID());
		boolean before = bar != null && bar.getPlayers().size() == 2;
		king.kill();
		BanditFort.tick(level);
		boolean keptAfterKill = BanditKing.bar(king.getUUID()) != null;
		int shownAfterKill = bar == null ? -1 : bar.getPlayers().size();
		BanditFort.tick(level);
		boolean backAfterKill = BanditKing.bar(king.getUUID()) != null || (bar != null && !bar.getPlayers().isEmpty());

		Mob second = king(helper);
		BanditFort.tick(level);
		ServerBossEvent bar2 = BanditKing.bar(second.getUUID());
		boolean before2 = bar2 != null && bar2.getPlayers().contains(one) && bar2.getPlayers().contains(two);
		second.discard(); // unloaded, not dead
		BanditFort.tick(level);
		boolean keptAfterRemove = BanditKing.bar(second.getUUID()) != null;
		int shownAfterRemove = bar2 == null ? -1 : bar2.getPlayers().size();
		cleanUp(level, king, one, two);
		helper.assertTrue(before, "the two players near the King don't both see his bar: " + (bar == null ? "no bar" : bar.getPlayers().size()));
		helper.assertFalse(keptAfterKill, "a bar is still kept for the King after he was killed");
		helper.assertTrue(shownAfterKill == 0, "the dead King's bar still shows to " + shownAfterKill + " player(s)");
		helper.assertFalse(backAfterKill, "the dead King's bar came back at the following look");
		helper.assertTrue(before2, "the second King's bar doesn't show to both players");
		helper.assertFalse(keptAfterRemove, "a bar is still kept for a King who was removed");
		helper.assertTrue(shownAfterRemove == 0, "a removed King's bar still shows to " + shownAfterRemove + " player(s)");
		helper.succeed();
	}
}
