package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.trainer.RaidHelpers;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * QA (lane D, 2026-11-02), from 38.1a's spec (the raid helpers hear of "a raid joined or hosted" by the player near the
 * den, or lately near it) with Raid Dens' real statistics. Kills the nightly survivor {@code RaidHelpers.java:203}
 * (now :217, {@code (joined || hosted) && (near || recent)} to {@code ||}), which no test reached: a win statistic near
 * the den before any raid never set the helpers joining, and no test ever hosted a raid.
 * <ul>
 * <li>Near the den, a tier's win statistic with no raid joined doesn't count as joining; a raid hosted there does.</li>
 * <li>Away from the den, a raid joined counts only while the player was near it lately: {@code RECENT} ticks ago yes,
 * one tick more no.</li>
 * </ul>
 */
public class QaDenStats1102dCompatTests implements FabricGameTest {
	private static final long PRICE = RaidHelperCompatTests.PRICE;

	/** A win statistic near the den joins nothing; hosting a raid there joins the helpers (as joining one does). */
	//$ gametest_batch 'CompatGameTests.AREA' '"qaDenStatsHosted1102d"'
	@GameTest(template = CompatGameTests.AREA, batch = "qaDenStatsHosted1102d")
	public void aWinStatJoinsNothingAndAHostedRaidJoins(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos[] at = RaidHelperCompatTests.village(helper);
		RaidHelperCompatTests.trainer(helper, 0, 5);
		ServerPlayer player = RaidHelperCompatTests.player(helper, at[1].offset(3, 0, 0), PRICE);
		QaDenHelperRaidCompatTests.Raids raids = QaDenHelperRaidCompatTests.stand(helper);
		try {
			RaidHelpers.hire(level, at[0], at[1], player);
			RaidHelpers.Hire hire = RaidHelperCompatTests.hire(helper, player);
			helper.assertTrue(!hire.joined, "setup: a fresh hiring has already joined a raid");
			RaidHelperCompatTests.stat(player, "tier_three_completed");
			helper.assertTrue(!hire.joined, "a tier's win statistic by the den, with no raid joined, set the helpers joining a raid");
			RaidHelperCompatTests.stat(player, RaidHelpers.HOSTED);
			helper.assertTrue(hire.joined, "a raid hosted by the den should bring the helpers in (as one joined does), they didn't join");
		} finally {
			QaDenHelperRaidCompatTests.putBack(raids);
			RaidHelperCompatTests.cleanUp(helper, at[1], player);
		}
		helper.succeed();
	}

	/**
	 * Away from the den (NEAR + 12 blocks): a raid joined counts only if the player was by the den lately. Last near
	 * RECENT + 1 ticks ago: no; RECENT ticks ago: yes.
	 */
	//$ gametest_batch 'CompatGameTests.AREA' '"qaDenStatsRecent1102d"'
	@GameTest(template = CompatGameTests.AREA, batch = "qaDenStatsRecent1102d")
	public void aRaidJoinedAwayCountsOnlyWhileLatelyNear(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos[] at = RaidHelperCompatTests.village(helper);
		RaidHelperCompatTests.trainer(helper, 0, 5);
		ServerPlayer player = RaidHelperCompatTests.player(helper, at[1].offset(3, 0, 0), PRICE);
		QaDenHelperRaidCompatTests.Raids raids = QaDenHelperRaidCompatTests.stand(helper);
		try {
			RaidHelpers.hire(level, at[0], at[1], player);
			RaidHelpers.Hire hire = RaidHelperCompatTests.hire(helper, player);
			int away = (int) RaidHelpers.NEAR + 12;
			player.teleportTo(at[1].getX() + 0.5 + away, at[1].getY(), at[1].getZ() + 0.5);
			long now = level.getGameTime();
			hire.lastNear = now - RaidHelpers.RECENT - 1;
			RaidHelperCompatTests.stat(player, RaidHelpers.JOINED);
			helper.assertTrue(!hire.joined, "a raid joined " + away + " blocks from the den, last near it " + (RaidHelpers.RECENT + 1) + " ticks ago, brought the helpers in");
			hire.lastNear = level.getGameTime() - RaidHelpers.RECENT;
			RaidHelperCompatTests.stat(player, RaidHelpers.JOINED);
			helper.assertTrue(hire.joined, "a raid joined away from the den, last near it " + RaidHelpers.RECENT + " ticks ago (lately), should bring the helpers in");
		} finally {
			QaDenHelperRaidCompatTests.putBack(raids);
			RaidHelperCompatTests.cleanUp(helper, at[1], player);
		}
		helper.succeed();
	}
}
