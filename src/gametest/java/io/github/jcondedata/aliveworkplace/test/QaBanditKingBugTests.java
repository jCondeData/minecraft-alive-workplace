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
import net.minecraft.world.level.Level;

/**
 * QA (lane qa-lane-d-1028-0109): bugs found testing The Bandit King (31.15, 31.16). Each test failed on the code as it
 * was and passes once the bug is fixed. (The fort-ring test of B660 stays on tests/bandit-king-1028 with that bug.)
 */
public class QaBanditKingBugTests {
	private static final String TALL = "aliveworkplace_test:tall_area";

	/**
	 * Spec 31.15: the King has "a boss bar for players within 32 blocks". A player standing 10 blocks from him who goes
	 * through a portal to the Nether is nowhere near him, yet keeps his bar on screen: the bar is only ever updated for
	 * players in the King's own dimension, so one who left it is never taken off.
	 */
	//$ gametest_ticks_batch TALL '40' '"qaBanditKingBarNether"'
	@GameTest(template = TALL, timeoutTicks = 40, batch = "qaBanditKingBarNether")
	public void qaKingsBarLeavesAPlayerWhoGoesToTheNether(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(new BlockPos(10, 2, 10));
		Mob king = BanditKing.spawn(level, at, at, helper.absolutePos(new BlockPos(12, 2, 10)));
		helper.assertTrue(king != null, "no King");
		king.setNoAi(true);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.teleportTo(king.getX() + 10, king.getY(), king.getZ());
		BanditFort.tick(level);
		ServerBossEvent bar = BanditKing.bar(king.getUUID());
		helper.assertTrue(bar != null && bar.getPlayers().contains(player), "the player 10 blocks off doesn't see the bar");
		ServerLevel nether = level.getServer().getLevel(Level.NETHER);
		player.teleportTo(nether, 0.5, 70, 0.5, 0f, 0f);
		helper.assertTrue(player.level() == nether, "the player didn't reach the Nether");
		for (ServerLevel l : level.getServer().getAllLevels()) {
			BanditFort.tick(l);
		}
		boolean stillShown = bar.getPlayers().contains(player);
		king.discard();
		BanditFort.tick(level);
		player.discard();
		helper.assertFalse(stillShown, "a player in the Nether still sees the Bandit King's boss bar");
		helper.succeed();
	}
}
