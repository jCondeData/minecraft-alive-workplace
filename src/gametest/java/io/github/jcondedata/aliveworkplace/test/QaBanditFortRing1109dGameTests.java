package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.BanditFort;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * QA (lane-d-1109-1314): B660's fix, from the spec of 31.15: the Bandit King's fort goes up "on fit ground 110 to 150
 * blocks from the hall". The builder's test checks the column; these go through {@code BanditFort.site} on real loaded
 * ground (from qa-lane-d-1028-0109's test on tests/bandit-king-1028), and check the ring was not shrunk to pass.
 */
public class QaBanditFortRing1109dGameTests {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/**
	 * With the flat ground round a far-off hall loaded, 1000 fixed seeds: every site found lies 110 to 150 blocks out
	 * (straight line across the ground), most seeds find one, and the sites reach both edges of the ring (some under 112,
	 * some over 148), so the fix kept the whole ring rather than a narrower one.
	 */
	//$ gametest_ticks_batch HUGE '1200' '"qaBanditFortRing1109d"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaBanditFortRing1109d")
	public void everyFortSiteOnLoadedGroundLies110To150OutAndTheRingIsWhole(GameTestHelper helper) {
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
				double min = Double.MAX_VALUE;
				double max = 0;
				StringBuilder wrong = new StringBuilder();
				for (int seed = 0; seed < 1000; seed++) {
					BlockPos site = BanditFort.site(level, hall, BanditFort.NEAR, BanditFort.FAR, RandomSource.create(31015L + seed));
					if (site == null) {
						continue;
					}
					found++;
					double d = flat(site, hall);
					min = Math.min(min, d);
					max = Math.max(max, d);
					if (d < 110 || d > 150) {
						wrong.append(String.format(" seed %d: %.2f out;", seed, d));
					}
				}
				if (found < 500) {
					result[0] = "only " + found + " of 1000 seeds found fit ground on flat loaded land";
				} else if (!wrong.isEmpty()) {
					result[0] = "fort sites outside 110-150:" + wrong;
				} else if (min >= 112 || max <= 148) {
					result[0] = String.format("the sites only span %.2f to %.2f blocks out, not the whole 110-150 ring", min, max);
				} else {
					result[0] = "";
				}
			}
			helper.assertTrue(result[0].isEmpty(), result[0]);
		});
	}

	/** The spec's numbers are the code's: 110 near and 150 far. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theRingIs110To150(GameTestHelper helper) {
		helper.assertTrue(BanditFort.NEAR == 110 && BanditFort.FAR == 150,
				"the fort's ring is " + BanditFort.NEAR + " to " + BanditFort.FAR + ", not 110 to 150");
		helper.succeed();
	}

	private static double flat(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}
}
