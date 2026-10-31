package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.BanditFort;
import io.github.jcondedata.aliveworkplace.guard.BanditKing;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;

/**
 * QA (lane D, 2026-10-30), ROADMAP 31.15 "a boss bar for players within 32 blocks": the bar is the King's, so it carries
 * his name and his health as they are now, not as they were when it was first shown. Kills the nightly-style survivor
 * that deleted {@code bar.setName(king.getDisplayName())} in {@code BanditKing.bar}.
 */
public class QaKingBar1030GameTests {
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

	private static void cleanUp(ServerLevel level, Mob king, ServerPlayer player) {
		king.discard();
		BanditFort.tick(level);
		level.getServer().getPlayerList().remove(player);
		player.discard();
	}

	/** The bar reads "<name>, the Bandit King" as he is called, and a name tag put on him renames it at the next look. */
	//$ gametest_ticks_batch TALL '40' '"qaKingBar1030Name"'
	@GameTest(template = TALL, timeoutTicks = 40, batch = "qaKingBar1030Name")
	public void qaKingsBarTakesTheNameHeIsGiven(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		Mob king = king(helper);
		ServerPlayer player = survivor(helper);
		player.teleportTo(king.getX() + 5, king.getY(), king.getZ());
		BanditFort.tick(level);
		ServerBossEvent bar = BanditKing.bar(king.getUUID());
		String first = bar == null ? null : bar.getName().getString();
		String born = king.getDisplayName().getString();
		king.setCustomName(Component.literal("Grimwald the Tagged"));
		BanditFort.tick(level);
		ServerBossEvent after = BanditKing.bar(king.getUUID());
		String renamed = after == null ? null : after.getName().getString();
		boolean sees = after != null && after.getPlayers().contains(player);
		cleanUp(level, king, player);
		helper.assertTrue(bar != null, "no boss bar for the King");
		helper.assertTrue(born.equals(first), "the bar reads '" + first + "', not the King's name '" + born + "'");
		helper.assertTrue("Grimwald the Tagged".equals(renamed), "after a name tag the bar still reads '" + renamed + "'");
		helper.assertTrue(sees, "the player 5 blocks off lost the bar when the King was renamed");
		helper.succeed();
	}

	/** The bar's fill is his health out of 150, and follows it down and back up (healed) at each look. */
	//$ gametest_ticks_batch TALL '40' '"qaKingBar1030Health"'
	@GameTest(template = TALL, timeoutTicks = 40, batch = "qaKingBar1030Health")
	public void qaKingsBarFollowsHisHealth(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		Mob king = king(helper);
		ServerPlayer player = survivor(helper);
		player.teleportTo(king.getX() + 5, king.getY(), king.getZ());
		float max = king.getMaxHealth();
		BanditFort.tick(level);
		ServerBossEvent bar = BanditKing.bar(king.getUUID());
		float full = bar == null ? -1 : bar.getProgress();
		// above half health, so the horn (31.15: at half health, once) stays out of it
		king.setHealth(120f);
		BanditFort.tick(level);
		float hurt = bar == null ? -1 : bar.getProgress();
		king.setHealth(135f);
		BanditFort.tick(level);
		float healed = bar == null ? -1 : bar.getProgress();
		cleanUp(level, king, player);
		helper.assertTrue(bar != null, "no boss bar for the King");
		helper.assertTrue(max == 150f, "the King's health is " + max + ", not 150");
		helper.assertTrue(full == 1f, "a King at full health shows a bar " + full + " full");
		helper.assertTrue(Math.abs(hurt - 0.8f) < 1e-4, "at 120 of 150 the bar is " + hurt + " full, not 0.8");
		helper.assertTrue(Math.abs(healed - 0.9f) < 1e-4, "healed to 135 of 150 the bar is " + healed + " full, not 0.9");
		helper.succeed();
	}
}
