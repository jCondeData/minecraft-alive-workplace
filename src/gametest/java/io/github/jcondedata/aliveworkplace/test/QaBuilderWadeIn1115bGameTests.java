package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.assertFinished;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.assertNoFluid;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.assertNothingMadeOrLost;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.prepare;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.reload;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.start;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.state;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.watchDeath;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderWater1114bGameTests.fill;

import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (2026-11-15, qa-lane-b-1115-0037) of B1257's fix, written from the spec (docs/wiki/builder.md: "A post's footing on
 * the bed of shallow water is always laid: when no bank or floor in reach is dry, the builder wades into the water beside
 * it, on the bed, to lay the footing, as a player would"; FOUNDATION fills under the build down to solid ground; ROADMAP
 * 23.1 "every build finishes; no item is duplicated or lost"), not from the code. The test hut stands out in a lake on a
 * stone plateau (lake x 4-18, z 6-16 round the hut at x 9-13, z 9-13), so the middle of its footprint is more than a
 * reach from every bank and nothing over the water is built yet when the foundation goes down:
 * <ul>
 * <li>a lake one deep and one three deep (source water): every column under the floor is filled down to the bed;</li>
 * <li>a shallow lake of flowing water (fed from both banks), filled the same;</li>
 * <li>the three-deep lake, saved and loaded while the builder is down in the water;</li>
 * <li>a lake four deep, on the test area's own floor, the builder well over his head while he lays the footings;</li>
 * <li>a lava lake three deep instead: the builder never wades into lava, lives, and the build still ends.</li>
 * </ul>
 * Every test checks the materials in the chest and barrels add up exactly (the terrain tests' stocking: the hut's list,
 * foundation included, plus 2 of each).
 */
public class QaBuilderWadeIn1115bGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");
	private static final BlockPos ORIGIN = new BlockPos(9, 6, 9);

	/** A stone plateau (y 2-5), a lake x 4-18, z 6-16 from y {@code bottom} to 5 of {@code block}, the hut at x 9-13, z 9-13. */
	private static QaBuilderTerrain1102GameTests.Run lake(GameTestHelper helper, int bottom, net.minecraft.world.level.block.Block block) {
		prepare(helper);
		fill(helper, 0, 29, 2, 5, 0, 29, Blocks.STONE);
		fill(helper, 4, 18, bottom, 5, 6, 16, block);
		return start(helper, 6, ORIGIN);
	}

	/** Every block under the hut's footprint from y {@code from} to {@code to} that is still open or fluid. */
	private static List<String> open(GameTestHelper helper, int from, int to) {
		List<String> out = new ArrayList<>();
		for (int x = 9; x <= 13; x++) {
			for (int z = 9; z <= 13; z++) {
				for (int y = from; y <= to; y++) {
					BlockState s = helper.getBlockState(new BlockPos(x, y, z));
					if (s.isAir() || s.canBeReplaced() || !s.getFluidState().isEmpty()) {
						out.add("(" + x + "," + y + "," + z + ")=" + s.getBlock().getDescriptionId());
					}
				}
			}
		}
		return out;
	}

	/** Records the first tick the builder stands in lava (or burns), and whether he was ever down in the lake's water. */
	private static void watchBuilder(GameTestHelper helper, QaBuilderTerrain1102GameTests.Run r, String[] inLava, boolean[] waded) {
		helper.onEachTick(() -> {
			Villager b = r.builder()[0];
			if (!b.isAlive()) {
				return;
			}
			if (inLava[0] == null && (b.isInLava() || b.isOnFire())) {
				inLava[0] = "at " + helper.relativePos(b.blockPosition()).toShortString() + " tick " + helper.getTick() + " (" + state(helper, r) + ")";
			}
			if (b.isInWater() && b.getY() < helper.absolutePos(new BlockPos(0, 5, 0)).getY() + 0.5) {
				waded[0] = true;
			}
		});
	}

	private static void assertFootingsOnTheBed(GameTestHelper helper, int bottom, String what) {
		List<String> open = open(helper, bottom, 5);
		helper.assertTrue(open.isEmpty(), open.size() + " block(s) under the hut's floor not filled down to the bed of the " + what + ": " + open);
	}

	/**
	 * A lake one deep round and under the hut, the middle of the footprint out of reach from every bank: the whole
	 * layer under the floor is filled, the hut ends as drawn and dry, and the materials add up.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa1115bWadeShallow"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1115bWadeShallow")
	public void aLakeOneDeepUnderTheHutIsFilledToTheBed(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.Run r = lake(helper, 5, Blocks.WATER);
		watchDeath(helper, r);
		helper.succeedWhen(() -> {
			assertFinished(helper, r);
			assertFootingsOnTheBed(helper, 5, "one-deep lake");
			assertNoFluid(helper, r);
			assertNothingMadeOrLost(helper, r);
			LOGGER.info("qa 1115b wade shallow: filled to the bed at tick {}", helper.getTick());
		});
	}

	/**
	 * The same lake three deep (bed at y 2): every column goes down three blocks to the bed, the builder (who wades
	 * in over his head) lives, and the materials add up.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa1115bWadeDeep"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1115bWadeDeep")
	public void aLakeThreeDeepUnderTheHutIsFilledToTheBedAndTheBuilderLives(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.Run r = lake(helper, 3, Blocks.WATER);
		watchDeath(helper, r);
		boolean[] waded = {false};
		watchBuilder(helper, r, new String[1], waded);
		helper.succeedWhen(() -> {
			assertFinished(helper, r);
			assertFootingsOnTheBed(helper, 3, "three-deep lake");
			assertNoFluid(helper, r);
			assertNothingMadeOrLost(helper, r);
			LOGGER.info("qa 1115b wade deep: filled to the bed at tick {}, waded in {}", helper.getTick(), waded[0]);
		});
	}

	/**
	 * A lake four deep (y 2-5, its bed the test area's floor): the builder goes down over his head to lay the bottom
	 * footings, every column reaches the bed, he never drowns, and the materials add up.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa1115bWadeFourDeep"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1115bWadeFourDeep")
	public void aLakeFourDeepIsFilledToTheBedAndTheBuilderNeverDrowns(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.Run r = lake(helper, 2, Blocks.WATER);
		watchDeath(helper, r);
		helper.succeedWhen(() -> {
			assertFinished(helper, r);
			assertFootingsOnTheBed(helper, 2, "four-deep lake");
			assertNoFluid(helper, r);
			assertNothingMadeOrLost(helper, r);
			LOGGER.info("qa 1115b wade four deep: filled to the bed at tick {}", helper.getTick());
		});
	}

	/**
	 * A shallow lake of flowing water (no source under the hut: a row of sources along each bank, x 4 and x 18, the rest
	 * flowing in from them): every column under the floor is still filled to the bed and the materials add up.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa1115bWadeFlowing"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1115bWadeFlowing")
	public void aShallowLakeOfFlowingWaterUnderTheHutIsFilledToTheBed(GameTestHelper helper) {
		prepare(helper);
		fill(helper, 0, 29, 2, 5, 0, 29, Blocks.STONE);
		fill(helper, 4, 18, 5, 5, 6, 16, Blocks.AIR);
		fill(helper, 4, 4, 5, 5, 6, 16, Blocks.WATER);
		fill(helper, 18, 18, 5, 5, 6, 16, Blocks.WATER);
		QaBuilderTerrain1102GameTests.Run r = start(helper, 6, ORIGIN);
		watchDeath(helper, r);
		// the water runs in under the footprint while CLEAR goes on (no source under the hut, only flowing water)
		int[] flowing = {0};
		helper.onEachTick(() -> {
			if (flowing[0] == 0) {
				int n = 0;
				for (int x = 9; x <= 13; x++) {
					for (int z = 9; z <= 13; z++) {
						var f = helper.getBlockState(new BlockPos(x, 5, z)).getFluidState();
						helper.assertFalse(f.isSource(), "a water source under the hut at " + x + "," + z);
						n += f.is(net.minecraft.tags.FluidTags.WATER) ? 1 : 0;
					}
				}
				flowing[0] = n >= 20 ? n : 0;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(flowing[0] > 0, "the lake never flowed in under the hut");
			assertFinished(helper, r);
			assertFootingsOnTheBed(helper, 5, "flowing lake");
			assertNoFluid(helper, r);
			assertNothingMadeOrLost(helper, r);
			LOGGER.info("qa 1115b wade flowing: filled to the bed at tick {} ({} columns had flowing water)", helper.getTick(), flowing[0]);
		});
	}

	/**
	 * The three-deep lake, with the world saved and loaded while the builder is down in the water laying the
	 * foundation: the loaded site makes its list again from the terrain, he carries on, every column reaches the bed,
	 * he lives, and nothing is doubled or lost.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa1115bWadeReload"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1115bWadeReload")
	public void aReloadWhileWadingMidFoundationStillFillsToTheBed(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.Run r = lake(helper, 3, Blocks.WATER);
		watchDeath(helper, r);
		boolean[] reloaded = {false};
		int surface = helper.absolutePos(new BlockPos(0, 5, 0)).getY();
		helper.onEachTick(() -> {
			BuildSite now = r.site();
			Villager b = r.builder()[0];
			if (reloaded[0] || now == null || now.stage() != BuildPlan.Stage.FOUNDATION || !b.isAlive()) {
				return;
			}
			if (b.isInWater() && b.getY() < surface && open(helper, 3, 5).size() < 75) {
				reloaded[0] = true;
				int filled = 75 - open(helper, 3, 5).size();
				r.builder()[0] = reload(helper, r.level(), b);
				helper.assertTrue(r.site() != null && r.site() != now, "the site didn't come back from its save");
				LOGGER.info("qa 1115b wade reload: reloaded with the builder in the water at {} (relative {}), {} of 75 filled",
					r.builder()[0].blockPosition().toShortString(), helper.relativePos(r.builder()[0].blockPosition()).toShortString(), filled);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "never reloaded with the builder down in the water mid-FOUNDATION (" + state(helper, r) + ")");
			assertFinished(helper, r);
			assertFootingsOnTheBed(helper, 3, "three-deep lake after the reload");
			assertNothingMadeOrLost(helper, r);
		});
	}

	/**
	 * A lava lake three deep instead of water, the bottom layers out of every bank's reach: the builder never
	 * steps into the lava or catches fire (wading is for water only), he lives, and the build still ends with the hut
	 * as drawn and the materials adding up (a footing he can't reach safely may be left, so the lava isn't checked).
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa1115bWadeLava"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1115bWadeLava")
	public void aLavaLakeUnderTheHutIsNeverWadedInto(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.Run r = lake(helper, 3, Blocks.LAVA);
		watchDeath(helper, r);
		String[] inLava = {null};
		watchBuilder(helper, r, inLava, new boolean[1]);
		helper.succeedWhen(() -> {
			helper.assertTrue(inLava[0] == null, "the builder stepped into the lava or caught fire " + inLava[0]);
			assertFinished(helper, r);
			assertNothingMadeOrLost(helper, r, lavaFootings(helper, r));
			LOGGER.info("qa 1115b wade lava: ended at tick {}, {} of 75 footings left open", helper.getTick(), open(helper, 3, 5).size());
		});
	}

	/** The foundation cobblestone never laid because lava is still there: it stays in the chests. */
	private static java.util.Map<net.minecraft.world.item.Item, Integer> lavaFootings(GameTestHelper helper, QaBuilderTerrain1102GameTests.Run r) {
		int left = open(helper, 3, 5).size();
		return left == 0 ? java.util.Map.of() : java.util.Map.of(net.minecraft.world.item.Items.COBBLESTONE, left);
	}

	// ------------------------------------------------------------------------------------------- far from the banks

	/** The plateau with a wide lake (x 6-26, z 6-26, y 5, one deep) of {@code block}, the hut at x 13-17, z 13-17. */
	private static QaBuilderTerrain1102GameTests.Run farLake(GameTestHelper helper, net.minecraft.world.level.block.Block block) {
		prepare(helper);
		fill(helper, 0, 29, 2, 5, 0, 29, Blocks.STONE);
		fill(helper, 6, 26, 5, 5, 6, 26, block);
		return start(helper, 6, FAR);
	}

	private static final BlockPos FAR = new BlockPos(13, 6, 13);

	/** Cobblestone and planks loose (chests, bags, ground) plus built into the hut and its footings: what was stocked. */
	private static void assertConserved(GameTestHelper helper, QaBuilderTerrain1102GameTests.Run r) {
		for (net.minecraft.world.item.Item item : List.of(net.minecraft.world.item.Items.COBBLESTONE, net.minecraft.world.item.Items.OAK_PLANKS)) {
			int placed = 0;
			for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(13, 2, 13)), helper.absolutePos(new BlockPos(17, 12, 17)))) {
				if (r.level().getBlockState(p).getBlock().asItem() == item) {
					placed++;
				}
			}
			int stocked = r.plan().materials().getOrDefault(item, 0) + QaBuilderTerrain1102GameTests.EXTRA;
			int loose = QaBuilderTerrain1102GameTests.loose(helper, r, item);
			helper.assertTrue(placed + loose == stocked, item + " made or lost: " + loose + " loose + " + placed + " built, " + stocked + " stocked");
		}
	}

	/**
	 * The test hut in the middle of a wide lake one deep, every footing more than a reach from the banks: the builder
	 * wades in and lays every footing under the floor down to the bed, lives, the site ends, and the cobblestone and
	 * planks add up (built plus loose is what was stocked). The floor and walls over the lake aren't checked: the
	 * wading is for the foundation only.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa1115bWadeFar"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1115bWadeFar")
	public void aHutFarOutInAShallowLakeHasEveryFootingOnTheBed(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.Run r = farLake(helper, Blocks.WATER);
		watchDeath(helper, r);
		helper.succeedWhen(() -> {
			helper.assertTrue(r.died()[0] == null, "the builder died on the site: " + r.died()[0]);
			helper.assertTrue(r.site() == null, "still building: " + state(helper, r));
			List<String> open = new ArrayList<>();
			for (int x = 13; x <= 17; x++) {
				for (int z = 13; z <= 17; z++) {
					BlockState s = helper.getBlockState(new BlockPos(x, 5, z));
					if (!s.getFluidState().isEmpty() || s.isAir()) {
						open.add("(" + x + ",5," + z + ")=" + s.getBlock().getDescriptionId());
					}
				}
			}
			helper.assertTrue(open.isEmpty(), open.size() + " footing(s) under the hut far out in the lake not laid: " + open);
			assertConserved(helper, r);
			LOGGER.info("qa 1115b wade far: footings laid at tick {}, unfinished blocks {}", helper.getTick(), r.plan().unfinished(r.level()).size());
		});
	}

	/**
	 * The same wide lake of lava, one deep (lava at the builder's feet, air at his head): he never steps into it or
	 * catches fire, lives, the site still ends (what he can't reach safely is skipped, every step after its two tries,
	 * nights off included: about 29000 ticks), and nothing is made or lost.
	 */
	//$ gametest_ticks_batch HUGE '48000' '"qa1115bWadeFarLava"'
	@GameTest(template = HUGE, timeoutTicks = 48000, batch = "qa1115bWadeFarLava")
	public void aHutFarOutInAShallowLavaLakeIsNeverWadedInto(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.Run r = farLake(helper, Blocks.LAVA);
		watchDeath(helper, r);
		String[] inLava = {null};
		watchBuilder(helper, r, inLava, new boolean[1]);
		helper.succeedWhen(() -> {
			helper.assertTrue(inLava[0] == null, "the builder stepped into the lava or caught fire " + inLava[0]);
			helper.assertTrue(r.died()[0] == null, "the builder died on the site: " + r.died()[0]);
			helper.assertTrue(r.site() == null, "still building: " + state(helper, r));
			assertConserved(helper, r);
			LOGGER.info("qa 1115b wade far lava: ended at tick {}, unfinished blocks {}", helper.getTick(), r.plan().unfinished(r.level()).size());
		});
	}
}
