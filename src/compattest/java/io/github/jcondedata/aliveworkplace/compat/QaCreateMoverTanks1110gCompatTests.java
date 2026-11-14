package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.SPOT;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.block;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.loose;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.set;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.setupAround;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.tidy;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.work;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverMultiblock1110e2CompatTests.data;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverMultiblock1110e2CompatTests.moved;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverMultiblock1110e2CompatTests.water;

import io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.Setup;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluids;

/**
 * QA (lane E, 2026-11-10, fourth run): verifying B1135 (a two-tall Create Fluid Tank moved by the Mover lost two buckets
 * and left its bottom block behind) and B1136's fix (MoveSite.whole packs every block of a joined Create container
 * before any comes down) past the bug's own two-tall tank. 45.3's Done when and moving-buildings.md: nothing dropped,
 * nothing lost, block for block; the job canceled half way puts everything back where it was; a save and reload in any
 * stage loses nothing. A tank two wide and two tall (eight blocks, one controller), the same reloaded with it half put
 * up, and a canceled move with a two-tall tank packed: each must arrive as one tank reading all its water through every
 * block, with nothing left standing at the old spot and nothing doubled. A three-tall tank fails on main (twelve of
 * twenty buckets lost): QaCreateMoverTanks1110gBugCompatTests on tests/create-mover-tank3-1110g.
 */
public class QaCreateMoverTanks1110gCompatTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_compat:huge_area";

	/** A tank one wide and three tall from SPOT. */
	static final List<BlockPos> TALL = List.of(SPOT, SPOT.above(), SPOT.above(2));
	/** A tank two wide (x and z) and two tall from SPOT. */
	static final List<BlockPos> WIDE = List.of(SPOT, SPOT.east(), SPOT.south(), SPOT.south().east(),
		SPOT.above(), SPOT.above().east(), SPOT.above().south(), SPOT.above().south().east());

	static void place(ServerLevel level, GameTestHelper helper, List<BlockPos> cells) {
		var tank = block("create:fluid_tank").defaultBlockState();
		for (BlockPos p : cells) {
			set(level, helper.absolutePos(p), tank);
		}
	}

	/** Fills the tank through its first block, then checks every block reads the same (one tank). */
	static void fill(ServerLevel level, GameTestHelper helper, List<BlockPos> cells, long amount) {
		BlockPos first = helper.absolutePos(cells.get(0));
		Storage<FluidVariant> s = FluidStorage.SIDED.find(level, first, Direction.UP);
		if (s == null) {
			s = FluidStorage.SIDED.find(level, first, null);
		}
		helper.assertTrue(s != null, "the tank has no fluid storage");
		try (Transaction tx = Transaction.openOuter()) {
			long in = s.insert(FluidVariant.of(Fluids.WATER), amount, tx);
			helper.assertTrue(in == amount, "the tank took only " + in + " of " + amount + " droplets");
			tx.commit();
		}
		for (BlockPos p : cells) {
			long n = water(level, helper.absolutePos(p));
			helper.assertTrue(n == amount, "before the move the " + cells.size() + " tank blocks aren't one tank: " + n + " through "
				+ p.toShortString() + "; " + data(level, helper.absolutePos(p)));
		}
	}

	/** Every cell (absolute, after {@code where}) is a tank reading {@code amount} once; nothing left elsewhere. */
	static void whole(GameTestHelper helper, String when, List<BlockPos> cells, Function<BlockPos, BlockPos> where, long amount,
		Map<String, Integer> looseNow) {
		ServerLevel level = helper.getLevel();
		var tank = block("create:fluid_tank");
		StringBuilder seen = new StringBuilder();
		boolean ok = looseNow.isEmpty();
		long most = 0;
		for (BlockPos p : cells) {
			BlockPos at = where.apply(p);
			boolean isTank = level.getBlockState(at).is(tank);
			long n = isTank ? water(level, at) : -1;
			most = Math.max(most, n);
			ok &= isTank && n == amount;
			seen.append(at.toShortString()).append('=').append(isTank ? n / FluidConstants.BUCKET + "b" : "not a tank").append(' ')
				.append(data(level, at)).append("; ");
		}
		helper.assertTrue(most <= amount, when + ": " + amount / FluidConstants.BUCKET + " buckets became " + most + " droplets: doubled; " + seen);
		helper.assertTrue(ok, when + ": the " + cells.size() + "-block tank isn't one tank holding " + amount / FluidConstants.BUCKET
			+ " buckets through every block; loose " + looseNow + "; " + seen);
	}

	/** No tank block is left standing at the house's old spot (B1135 left the bottom block there). */
	static void nothingLeftBehind(GameTestHelper helper, List<BlockPos> cells) {
		var tank = block("create:fluid_tank");
		for (BlockPos p : cells) {
			helper.assertTrue(!helper.getLevel().getBlockState(helper.absolutePos(p)).is(tank), "a tank block was left standing at the old spot "
				+ p.toShortString() + " with " + water(helper.getLevel(), helper.absolutePos(p)) + " droplets");
		}
	}

	/** Moves the house with the tank at {@code cells} holding {@code buckets}, a quarter turned; checks it arrives whole. */
	static void movesWhole(GameTestHelper helper, String name, List<BlockPos> cells, long buckets) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		long amount = buckets * FluidConstants.BUCKET;
		place(level, helper, cells);
		helper.runAfterDelay(10, () -> {
			fill(level, helper, cells, amount);
			Setup s = setupAround(helper, name);
			helper.runAfterDelay(2, () -> {
				MoveSite site = work(level, s, x -> false);
				MoveSite.Stage end = site.stage();
				int skipped = site.skipped();
				helper.runAfterDelay(10, () -> {
					Map<String, Integer> looseNow = loose(helper);
					try {
						helper.assertTrue(end == MoveSite.Stage.DONE && skipped == 0, "the move ended " + end + ", skipped " + skipped);
						nothingLeftBehind(helper, cells);
						whole(helper, "after the move", cells, p -> moved(s, p), amount, looseNow);
					} finally {
						tidy(helper, s);
					}
					helper.succeed();
				});
			});
		});
	}

	/** A tank two wide and two tall (eight blocks) holding forty buckets arrives as one tank with all forty. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1110g_mover_tank8"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1110g_mover_tank8")
	public void aMovedTwoWideTwoTallTankKeepsAllItsWater(GameTestHelper helper) {
		movesWhole(helper, "tank8", WIDE, 40);
	}

	/** The move called off with the house all down: the two-tall tank goes back up where it was, one tank, its ten buckets once. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1110g_mover_tank_cancel"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1110g_mover_tank_cancel")
	public void aMoveCanceledWithTheTankPackedPutsItBackWhole(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		List<BlockPos> cells = List.of(SPOT, SPOT.above());
		long amount = 10 * FluidConstants.BUCKET;
		place(level, helper, cells);
		helper.runAfterDelay(10, () -> {
			fill(level, helper, cells, amount);
			Setup s = setupAround(helper, "tank_cancel");
			helper.runAfterDelay(2, () -> {
				MoveSite site = work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
				MoveSite.Stage down = site.stage();
				boolean gone = cells.stream().allMatch(p -> level.getBlockState(helper.absolutePos(p)).isAir());
				MoveSites.get(level).cancel(level, s.from());
				for (int i = 0; i < QaCreateMoverContents1110eCompatTests.MAX_STEPS && site.stage() != MoveSite.Stage.DONE; i++) {
					MoveSites.get(level).step(level, site);
				}
				MoveSite.Stage end = site.stage();
				helper.runAfterDelay(10, () -> {
					Map<String, Integer> looseNow = loose(helper);
					try {
						helper.assertTrue(down == MoveSite.Stage.FOUNDATION && gone, "the house didn't come down first: " + down + ", tank gone " + gone);
						helper.assertTrue(end == MoveSite.Stage.DONE, "the canceled move ended " + end);
						whole(helper, "after the canceled move", cells, helper::absolutePos, amount, looseNow);
					} finally {
						tidy(helper, s);
					}
					helper.succeed();
				});
			});
		});
	}

	/** Saves and reads the move back with the tank at {@code cells} half put up at the new spot; checks it arrives whole. */
	static void reloadedHalfUp(GameTestHelper helper, String name, List<BlockPos> cells, long buckets) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		long amount = buckets * FluidConstants.BUCKET;
		place(level, helper, cells);
		helper.runAfterDelay(10, () -> {
			fill(level, helper, cells, amount);
			Setup s = setupAround(helper, name);
			helper.runAfterDelay(2, () -> {
				var tank = block("create:fluid_tank");
				List<BlockPos> at = cells.stream().map(p -> moved(s, p)).toList();
				MoveSite site = work(level, s, x -> {
					long up = at.stream().filter(p -> level.getBlockState(p).is(tank)).count();
					return up > 0 && up < at.size();
				});
				long upThen = at.stream().filter(p -> level.getBlockState(p).is(tank)).count();
				MoveSite.Stage mid = site.stage();
				CompoundTag saved = MoveSites.get(level).save(new CompoundTag(), level.registryAccess());
				level.getDataStorage().set(MoveSites.NAME, MoveSites.load(saved, level.registryAccess()));
				MoveSite again = MoveSites.get(level).forBuilding(s.from()).orElseThrow();
				MoveSite done = work(level, s, x -> false);
				MoveSite.Stage end = done.stage();
				int skipped = done.skipped();
				helper.runAfterDelay(10, () -> {
					Map<String, Integer> looseNow = loose(helper);
					try {
						helper.assertTrue(upThen > 0 && upThen < at.size() && again != site, "the reload didn't happen with the tank half up: "
							+ upThen + " of " + at.size() + " blocks up, stage " + mid);
						helper.assertTrue(end == MoveSite.Stage.DONE && skipped == 0, "the reloaded move ended " + end + ", skipped " + skipped);
						nothingLeftBehind(helper, cells);
						whole(helper, "after a reload with the tank half up (" + upThen + " of " + cells.size() + " blocks)", cells, p -> moved(s, p), amount, looseNow);
					} finally {
						tidy(helper, s);
					}
					helper.succeed();
				});
			});
		});
	}

	/** Saved and read back with the eight-block tank half put up at the new spot: one tank, its forty buckets once. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1110g_mover_tank8_reload"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1110g_mover_tank8_reload")
	public void aMoveReloadedWithTheWideTankHalfUpLosesAndDoublesNothing(GameTestHelper helper) {
		reloadedHalfUp(helper, "tank8_reload", WIDE, 40);
	}
}
