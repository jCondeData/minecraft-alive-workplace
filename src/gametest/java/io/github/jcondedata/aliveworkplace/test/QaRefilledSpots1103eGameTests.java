package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.LOGGER;

import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.Run;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;

/**
 * QA (lane-e-1103-1613, saves and the builder): B868 checked from its spec (ROADMAP B868, builder.md "A spot filled again
 * each time it is dug ... once he has dug one spot 16 times on a list it is deferred ... if it is still being refilled on
 * the retry it is left, counted once in skipped, and the build goes on ... Ordinary digging never gets near 16 digs of one
 * spot"), on cases B868GameTests doesn't cover:
 * <ul>
 * <li>ordinary digging past 16 digs of one spot: a sand column 24 high stands on a dirt-filled box, so one spot of its top
 * layer is filled again by the column each time it is dug (24 times, more than 16 and less than 32): it is all dug out,
 * nothing skipped, none lost;</li>
 * <li>a save and load at the 16th dig (the boundary) and in the middle of the retry (the deferred spot's second round,
 * the `retrying` branch): CLEAR still ends and the spot is counted once;</li>
 * <li>two spots refilled for ever at once: each counted once (skipped 2), no pass back over CLEAR;</li>
 * <li>with two helpers (workplaceBuildersHelp on), who leave such a spot to the lead: counted once, the hut finishes.</li>
 * </ul>
 * Layout: {@link QaBuilderTerrain1102GameTests} (the test hut, 5x4x5 at (6..10, 2..5, 6..10), stocked chest and barrels).
 */
public class QaRefilledSpots1103eGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	/** BuilderWork.MOST_REDIGS. */
	private static final int MOST = 16;
	private static final BlockPos SPOT_A = QaBuilderRestartClear1103bGameTests.SPOT_A;
	private static final BlockPos SPOT_B = QaBuilderRestartClear1103bGameTests.SPOT_B;

	/**
	 * The hut's box is all dirt, and a one-block sand column 24 high stands on its top layer at (8, 6..29, 8): each time the
	 * builder digs the spot under it, the next sand drops in. That is ordinary terrain (a sand pillar over a hillside build),
	 * dug more than 16 times but less than twice that: the hut ends as drawn, no sand left in the box, nothing skipped, and
	 * the 100 dirt and 24 sand are all loose (chests, bags, ground).
	 */
	//$ gametest_ticks_batch HUGE '14000' '"qaRefill1103eColumn"'
	@GameTest(template = HUGE, timeoutTicks = 14000, batch = "qaRefill1103eColumn")
	public void aSandColumn24HighOverADirtBoxIsAllDugOutWithNothingSkipped(GameTestHelper helper) {
		setUp(helper);
		for (int x = 6; x <= 10; x++) {
			for (int z = 6; z <= 10; z++) {
				for (int y = 2; y <= 5; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.DIRT);
				}
			}
		}
		for (int y = 6; y <= 29; y++) {
			helper.setBlock(new BlockPos(8, y, 8), Blocks.SAND);
		}
		Run r = QaBuilderTerrain1102GameTests.start(helper, 2, new BlockPos(6, 2, 6));
		QaBuilderTerrain1102GameTests.watchDeath(helper, r);
		int[] skipped = {0};
		helper.onEachTick(() -> {
			BuildSite s = r.site();
			if (s != null) {
				skipped[0] = Math.max(skipped[0], s.skipped());
			}
		});
		helper.succeedWhen(() -> {
			QaBuilderTerrain1102GameTests.assertFinished(helper, r);
			QaBuilderTerrain1102GameTests.assertNoForeign(helper, r, s -> s.getBlock() instanceof FallingBlock);
			QaBuilderTerrain1102GameTests.assertNothingFalling(helper, r);
			helper.assertTrue(skipped[0] == 0, "ordinary digging under a 24-high sand column skipped " + skipped[0] + " step(s)");
			for (int y = 6; y <= 29; y++) {
				helper.assertTrue(!helper.getBlockState(new BlockPos(8, y, 8)).is(Blocks.SAND), "sand still standing over the hut at y " + y);
			}
			QaBuilderTerrain1102GameTests.assertNothingMadeOrLost(helper, r);
			assertDugKept(helper, r, Map.of(Items.DIRT, 100, Items.SAND, 24));
		});
	}

	/** Refilled for ever, saved and loaded exactly at the 16th dig (the boundary): CLEAR ends, the spot counted once. */
	//$ gametest_ticks_batch HUGE '12000' '"qaRefill1103eAt16"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qaRefill1103eAt16")
	public void aRefilledSpotSavedAtItsSixteenthDigIsStillSkippedOnce(GameTestHelper helper) {
		long[] out = new long[6];
		Run r = refill(helper, List.of(SPOT_A), MOST, out);
		helper.succeedWhen(() -> {
			helper.assertTrue(out[4] == 1, "setup: never reloaded");
			assertLeftOnce(helper, r, out, 1);
		});
	}

	/**
	 * Refilled for ever, saved and loaded in the middle of the retry (the deferred spot's second round, dig 16 + 4): the
	 * load keeps the site retrying, so the spot is still given up then: CLEAR ends, counted once.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qaRefill1103eRetry"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qaRefill1103eRetry")
	public void aRefilledSpotSavedMidRetryIsStillSkippedOnce(GameTestHelper helper) {
		long[] out = new long[6];
		Run r = refill(helper, List.of(SPOT_A), MOST + 4, out);
		helper.succeedWhen(() -> {
			helper.assertTrue(out[4] == 1, "setup: never reloaded");
			helper.assertTrue(out[5] == 1, "setup: the load at dig " + (MOST + 4) + " wasn't during the retry of the deferred spot");
			assertLeftOnce(helper, r, out, 1);
		});
	}

	/** Two spots side by side refilled for ever: each deferred, then given up once: skipped 2, no pass back over CLEAR. */
	//$ gametest_ticks_batch HUGE '12000' '"qaRefill1103eTwo"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qaRefill1103eTwo")
	public void twoSpotsRefilledForeverAreEachSkippedOnce(GameTestHelper helper) {
		long[] out = new long[6];
		Run r = refill(helper, List.of(SPOT_A, SPOT_B), 0, out);
		helper.succeedWhen(() -> assertLeftOnce(helper, r, out, 2));
	}

	/**
	 * builder.md "Helpers and Wonder crews leave such a spot to the lead" (B868): with workplaceBuildersHelp on and two
	 * idle builders helping, a spot refilled for ever still lets CLEAR end, counted once in skipped, and the hut finishes.
	 * (A helper who kept digging it would keep its digs coming and hold the lead's deferral off.)
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qaRefill1103eHelpers"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qaRefill1103eHelpers")
	public void aRefilledSpotWithTwoHelpersStillLetsClearEndOnce(GameTestHelper helper) {
		long[] out = new long[6];
		ServerLevel level = helper.getLevel();
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		Run r = refill(helper, List.of(SPOT_A), 0, out);
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(true, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer()));
		Villager[] mates = new Villager[2];
		BlockPos[] benches = {new BlockPos(2, 2, 8), new BlockPos(2, 2, 11)};
		for (int i = 0; i < mates.length; i++) {
			helper.setBlock(benches[i], ModBlocks.BUILDERS_BENCH);
			QaBuilderDisruptionGameTests.ensurePoi(level, helper.absolutePos(benches[i]));
			mates[i] = helper.spawn(EntityType.VILLAGER, benches[i].east());
			Builders.employ(level, mates[i], helper.absolutePos(benches[i]));
		}
		boolean[] helped = {false};
		helper.onEachTick(() -> {
			for (Villager mate : mates) {
				helped[0] |= Builders.isHelping(mate);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helped[0], "setup: no idle builder ever helped");
			assertLeftOnce(helper, r, out, 1);
		});
	}

	// ===================================================================================================== helpers

	private static void assertLeftOnce(GameTestHelper helper, Run r, long[] out, int spots) {
		helper.assertTrue(out[1] >= 0, "CLEAR not left yet after " + out[0] + " digs");
		// a load gives a spot its digs afresh (not saved): at most one more round of them
		helper.assertTrue(out[0] <= spots * (3 * MOST + 2), "the refilled spot(s) were dug " + out[0] + " times");
		helper.assertTrue(out[2] == spots, "each refilled spot left in CLEAR should be counted once in skipped: " + spots + " spot(s), skipped " + out[2]);
		helper.assertTrue(out[3] == 0, "refilled spots given up sent CLEAR back over its list " + out[3] + " times");
		QaBuilderTerrain1102GameTests.assertFinished(helper, r);
	}

	/**
	 * Sand on {@code spots}, put back each time one is dug while the site is in CLEAR, for ever. With {@code reloadAtDig}
	 * &gt; 0 the world is saved and loaded at that dig (counted over all spots). {@code out}: digs, the tick CLEAR was left,
	 * skipped and CLEAR passes then, 1 if reloaded, 1 if the site was retrying its deferred steps at the load.
	 */
	private static Run refill(GameTestHelper helper, List<BlockPos> spots, int reloadAtDig, long[] out) {
		setUp(helper);
		for (BlockPos p : spots) {
			helper.setBlock(p, Blocks.SAND);
		}
		Run r = QaBuilderTerrain1102GameTests.start(helper, 2, new BlockPos(6, 2, 6));
		QaBuilderTerrain1102GameTests.watchDeath(helper, r);
		out[1] = -1;
		helper.onEachTick(() -> {
			BuildSite s = r.site();
			if (s == null || out[1] >= 0) {
				return;
			}
			if (s.stage() != BuildPlan.Stage.CLEAR) {
				out[1] = helper.getTick();
				out[2] = s.skipped();
				out[3] = s.clearPasses();
				LOGGER.info("qa refill 1103e: CLEAR left at {} after {} digs, skipped {}, passes {}", out[1], out[0], out[2], out[3]);
				return;
			}
			for (BlockPos p : spots) {
				if (helper.getBlockState(p).isAir()) {
					helper.setBlock(p, Blocks.SAND);
					out[0]++;
					if (out[0] == reloadAtDig) {
						out[5] = s.isRetrying() ? 1 : 0;
						r.builder()[0] = QaBuilderTerrain1102GameTests.reload(helper, r.level(), r.builder()[0]);
						out[4] = 1;
					}
				}
			}
			helper.assertTrue(helper.getTick() < 9000, "the builder is still in CLEAR at tick " + helper.getTick() + " after " + out[0]
				+ " digs of the refilled sand: " + QaBuilderTerrain1102GameTests.state(helper, r));
		});
		return r;
	}

	/** prepare(), stale finished buildings forgotten, levelling at 2, the clock kept by day. */
	private static void setUp(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int ring = level.getGameRules().getInt(ModGameRules.LEVEL_GROUND);
		level.getGameRules().getRule(ModGameRules.LEVEL_GROUND).set(2, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.LEVEL_GROUND).set(ring, level.getServer()));
		helper.onEachTick(() -> {
			if (helper.getTick() % 20 == 0) {
				helper.setDayTime(2000);
			}
		});
	}

	private static void assertDugKept(GameTestHelper helper, Run r, Map<Item, Integer> dug) {
		Map<Item, String> off = new HashMap<>();
		for (Map.Entry<Item, Integer> e : dug.entrySet()) {
			int got = QaBuilderTerrain1102GameTests.loose(helper, r, e.getKey());
			if (got != e.getValue()) {
				off.put(e.getKey(), got + " loose of " + e.getValue() + " dug");
			}
		}
		helper.assertTrue(off.isEmpty(), "dug blocks lost or made: " + off);
	}
}
