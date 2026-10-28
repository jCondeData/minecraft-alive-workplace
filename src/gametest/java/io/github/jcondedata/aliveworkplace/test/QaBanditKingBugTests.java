package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.BanditFort;
import io.github.jcondedata.aliveworkplace.guard.BanditKing;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * QA (lane qa-lane-d-1028-0109): bugs found testing The Bandit King (31.15, 31.16). Each test fails on the code as it
 * is and passes once the bug is fixed.
 */
public class QaBanditKingBugTests {
	private static final String TALL = "aliveworkplace_test:tall_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/**
	 * Spec 31.15: the fort goes up "on fit ground 110 to 150 blocks from the hall". With the ground round a far-off hall
	 * loaded (flat), 1000 fixed seeds through {@code BanditFort.site}: every site found lies 110 to 150 out (straight
	 * line). It fails: the site's column is rounded on each axis on its own (as the camps' ring was before B205), so 24 of
	 * the 1000 sites lie 109.5 to 150.4 blocks out.
	 */
	//$ gametest_ticks_batch HUGE '1200' '"qaBanditFortRing"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaBanditFortRing")
	public void qaFortSiteLies110To150Out(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos base = helper.absolutePos(new BlockPos(0, 2, 0)).offset(4000, 0, 4000);
		List<long[]> chunks = new ArrayList<>();
		for (int cx = (base.getX() - 172) >> 4; cx <= (base.getX() + 172) >> 4; cx++) {
			for (int cz = (base.getZ() - 172) >> 4; cz <= (base.getZ() + 172) >> 4; cz++) {
				chunks.add(new long[] {cx, cz});
				level.setChunkForced(cx, cz, true);
			}
		}
		Leftovers.after(helper, () -> chunks.forEach(c -> level.setChunkForced((int) c[0], (int) c[1], false)));
		String[] result = {null};
		helper.succeedWhen(() -> {
			for (long[] c : chunks) {
				helper.assertTrue(level.hasChunk((int) c[0], (int) c[1]), "still loading the ring's chunks");
			}
			if (result[0] == null) {
				BlockPos hall = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base);
				int found = 0;
				StringBuilder wrong = new StringBuilder();
				for (int seed = 0; seed < 1000; seed++) {
					BlockPos site = BanditFort.site(level, hall, BanditFort.NEAR, BanditFort.FAR, RandomSource.create(31015L + seed));
					if (site == null) {
						continue;
					}
					found++;
					double d = flat(site, hall);
					if (d < 110 || d > 150) {
						wrong.append(String.format(" seed %d: %.2f out;", seed, d));
					}
				}
				result[0] = found < 500 ? "only " + found + " of 1000 seeds found fit ground on flat loaded land"
					: wrong.isEmpty() ? "" : "fort sites outside 110-150:" + wrong;
				helper.getLevel().getServer().sendSystemMessage(Component.literal("qaFortSiteLies110To150Out: " + found + " sites checked"));
			}
			helper.assertTrue(result[0].isEmpty(), result[0]);
		});
	}

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

	private static double flat(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}
}
