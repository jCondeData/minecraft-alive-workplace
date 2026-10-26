package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.trainer.RaidAssist;
import io.github.jcondedata.aliveworkplace.trainer.RaidHelpers;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * QA (lane d, 2026-10-25), ROADMAP 38.1a: the raid as Raid Dens starts it. Raid Dens awards {@code raids_joined} as the
 * player enters the raid, so the hiring is joined by that statistic before the helpers' own look (every second) can find
 * the raid; the helpers must still strike that raid's boss and see the raid end. Fails on main 8370f50: the
 * statistic's join keeps no raid id, so the helpers never strike and a lost raid keeps them hired for the rest of the day.
 * Uses {@link QaDenHelperRaidCompatTests.Raids} for the running raid.
 */
public class QaDenHelperRaidBugTests implements FabricGameTest {
	static final long PRICE = RaidHelperCompatTests.PRICE;

	/**
	 * The raid as a player starts it: by the den, they join, and Raid Dens awards {@code raids_joined} as they enter (the
	 * raid is already running). The helpers cheer, and then their Pokémon must strike that raid's boss (the strike line
	 * says they do); when the raid is lost they head home, not a day later.
	 */
	//$ gametest_batch 'CompatGameTests.AREA' '"qaDenRaidStatJoin"'
	@GameTest(template = CompatGameTests.AREA, batch = "qaDenRaidStatJoin")
	public void aRaidJoinedAsRaidDensTellsItIsStruckAndItsLossSeen(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos[] at = RaidHelperCompatTests.village(helper);
		RaidHelperCompatTests.trainer(helper, 0, 5);
		ServerPlayer player = RaidHelperCompatTests.player(helper, at[1].offset(3, 0, 0), PRICE);
		QaDenHelperRaidCompatTests.Raids raids = QaDenHelperRaidCompatTests.stand(helper);
		try {
			RaidHelpers.hire(level, at[0], at[1], player);
			RaidHelperCompatTests.FakeRaid raid = new RaidHelperCompatTests.FakeRaid(400f);
			raids.in.put(player.getUUID(), raid);
			RaidHelperCompatTests.stat(player, RaidHelpers.JOINED);
			RaidHelpers.Hire hire = RaidHelperCompatTests.hire(helper, player);
			helper.assertTrue(hire.joined && RaidHelperCompatTests.cheers(player) == 1, "the join: " + RaidHelperCompatTests.cheers(player) + " cheers");
			hire.nextStrike = level.getGameTime();
			RaidHelpers.tick(level, hire);
			helper.assertTrue(raid.health < 400f, "a running raid joined as Raid Dens tells it: the helpers never struck its boss (health "
				+ raid.health + " of 400, the hiring's raid " + hire.raid + ")");
			raid.state = RaidAssist.State.LOST;
			hire.nextStrike = level.getGameTime();
			RaidHelpers.tick(level, hire);
			helper.assertTrue(RaidHelpers.hireOf(level.getServer(), player.getUUID()).isEmpty(), "the raid was lost but the helpers stay hired");
		} finally {
			QaDenHelperRaidCompatTests.putBack(raids);
			RaidHelperCompatTests.cleanUp(helper, at[1], player);
		}
		helper.succeed();
	}

	/**
	 * B476's other half: the statistic comes before the raid can be found (the player is still on the way in). The helpers'
	 * own look, on the server's real ticks, must then find the raid within a second or so in any world (it once needed the
	 * world's age and the server's tick count to line up), strike its boss, and the raid's id must survive a save.
	 */
	//$ gametest_ticks_batch 'CompatGameTests.AREA' '200' '"qaDenRaidStatJoinLate"'
	@GameTest(template = CompatGameTests.AREA, timeoutTicks = 200, batch = "qaDenRaidStatJoinLate")
	public void aRaidFoundAfterTheStatisticIsStillStruckAndKeptOverASave(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos[] at = RaidHelperCompatTests.village(helper);
		RaidHelperCompatTests.trainer(helper, 0, 5);
		ServerPlayer player = RaidHelperCompatTests.player(helper, at[1].offset(3, 0, 0), PRICE);
		QaDenHelperRaidCompatTests.Raids raids = QaDenHelperRaidCompatTests.stand(helper);
		RaidHelperCompatTests.FakeRaid raid = new RaidHelperCompatTests.FakeRaid(400f);
		RaidHelpers.Hire hire;
		try {
			RaidHelpers.hire(level, at[0], at[1], player);
			RaidHelperCompatTests.stat(player, RaidHelpers.JOINED);
			hire = RaidHelperCompatTests.hire(helper, player);
			helper.assertTrue(hire.joined && hire.raid == null, "joined by the statistic with no raid to find yet: joined " + hire.joined + ", raid " + hire.raid);
			raids.in.put(player.getUUID(), raid);
		} catch (RuntimeException | AssertionError e) {
			QaDenHelperRaidCompatTests.putBack(raids);
			RaidHelperCompatTests.cleanUp(helper, at[1], player);
			throw e;
		}
		RaidHelpers.Hire joined = hire;
		helper.runAfterDelay(40, () -> {
			try {
				helper.assertTrue(raid.id.equals(joined.raid), "two seconds on, the helpers' own look never found the player's raid (the hiring's raid "
					+ joined.raid + ")");
				joined.nextStrike = level.getGameTime();
				RaidHelpers.tick(level, joined);
				helper.assertTrue(raid.health < 400f, "the raid found by the helpers' look was never struck (health " + raid.health + " of 400)");
				CompoundTag saved = RaidHelpers.Data.get(level).save(new CompoundTag(), level.registryAccess());
				RaidHelpers.Hire loaded = RaidHelpers.Data.load(saved, level.registryAccess()).hires().stream()
					.filter(h -> h.player.equals(player.getUUID())).findFirst().orElseThrow(() -> new AssertionError("the hiring was lost in a save"));
				helper.assertTrue(loaded.joined && raid.id.equals(loaded.raid), "after a save the hiring's raid is " + loaded.raid);
			} finally {
				QaDenHelperRaidCompatTests.putBack(raids);
				RaidHelperCompatTests.cleanUp(helper, at[1], player);
			}
			helper.succeed();
		});
	}
}
