package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.SPOT;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.block;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.loose;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.set;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.setupAround;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.tidy;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.work;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverMultiblock1110e2CompatTests.EMERALDS;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverMultiblock1110e2CompatTests.data;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverMultiblock1110e2CompatTests.fillVault;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverMultiblock1110e2CompatTests.moved;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverMultiblock1110e2CompatTests.placeVault;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverMultiblock1110e2CompatTests.vaultWhole;

import io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.Setup;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * QA (lane E, 2026-11-11): the Mover (45.3) with Create containers that touch but are not one container, after B1135's
 * fix (MoveSite.whole gathers every touching block of a joined Create container and packs them together). 45.3's Done
 * when: "nothing dropped, nothing lost, containers packed with their contents kept apart". Two tanks side by side that
 * Create keeps apart (one wide each, holding different fluids), and a two-tall tank standing against a two-block Item
 * Vault, are moved a quarter turned: each must arrive holding exactly what it held, no fluid mixed, merged, lost or
 * doubled, and nothing left at the old spot. (Stacked tanks can't be kept apart this way: Create joins a tank set on
 * another into one, and the top then refuses a second fluid.)
 */
public class QaCreateMoverTanks1111eCompatTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_compat:huge_area";
	static final long EIGHT = 8 * FluidConstants.BUCKET;

	/** The most of {@code fluid} the block at {@code abs} reads through any side. */
	static long amount(ServerLevel level, BlockPos abs, Fluid fluid) {
		long most = 0;
		for (Direction side : new Direction[] {null, Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
			Storage<FluidVariant> s = FluidStorage.SIDED.find(level, abs, side);
			if (s == null) {
				continue;
			}
			long n = 0;
			for (StorageView<FluidVariant> view : s.nonEmptyViews()) {
				if (view.getResource().isOf(fluid)) {
					n += view.getAmount();
				}
			}
			most = Math.max(most, n);
		}
		return most;
	}

	/** Puts {@code n} droplets of {@code fluid} into the tank at {@code abs}; asserts it took them all. */
	static void pour(GameTestHelper helper, BlockPos abs, Fluid fluid, long n) {
		ServerLevel level = helper.getLevel();
		Storage<FluidVariant> s = FluidStorage.SIDED.find(level, abs, Direction.UP);
		if (s == null) {
			s = FluidStorage.SIDED.find(level, abs, null);
		}
		helper.assertTrue(s != null, "the tank at " + abs.toShortString() + " has no fluid storage");
		try (Transaction tx = Transaction.openOuter()) {
			long in = s.insert(FluidVariant.of(fluid), n, tx);
			helper.assertTrue(in == n, "the tank at " + abs.toShortString() + " took only " + in + " of " + n + " droplets; " + data(level, abs));
			tx.commit();
		}
	}

	/** The tank at {@code abs} holds exactly {@code n} of {@code fluid} and none of {@code other}. */
	static void holds(GameTestHelper helper, String when, BlockPos abs, Fluid fluid, long n, Fluid other) {
		ServerLevel level = helper.getLevel();
		long has = amount(level, abs, fluid);
		long wrong = amount(level, abs, other);
		helper.assertTrue(level.getBlockState(abs).is(block("create:fluid_tank")) && has == n && wrong == 0, when + ": the block at "
			+ abs.toShortString() + " is " + level.getBlockState(abs) + " holding " + has / FluidConstants.BUCKET + " buckets of "
			+ fluid.builtInRegistryHolder().key().location().getPath() + " (of " + n / FluidConstants.BUCKET + ") and " + wrong
			+ " droplets of " + other.builtInRegistryHolder().key().location().getPath() + "; " + data(level, abs));
	}

	/**
	 * Two one-block tanks, {@code first} with eight buckets of lava and {@code second} (placed after the lava is in, so
	 * Create keeps them apart) with eight of water, are moved a quarter turned: each arrives with its own eight buckets.
	 */
	static void apartTanksMove(GameTestHelper helper, String name, BlockPos first, BlockPos second) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		var tank = block("create:fluid_tank").defaultBlockState();
		set(level, helper.absolutePos(first), tank);
		helper.runAfterDelay(10, () -> {
			pour(helper, helper.absolutePos(first), Fluids.LAVA, EIGHT);
			set(level, helper.absolutePos(second), tank);
			helper.runAfterDelay(10, () -> {
				pour(helper, helper.absolutePos(second), Fluids.WATER, EIGHT);
				holds(helper, "before the move", helper.absolutePos(first), Fluids.LAVA, EIGHT, Fluids.WATER);
				holds(helper, "before the move", helper.absolutePos(second), Fluids.WATER, EIGHT, Fluids.LAVA);
				Setup s = setupAround(helper, name);
				helper.runAfterDelay(2, () -> {
					MoveSite site = work(level, s, x -> false);
					MoveSite.Stage end = site.stage();
					int skipped = site.skipped();
					helper.runAfterDelay(20, () -> { // Create re-forms (or keeps apart) its tanks on their first ticks
						Map<String, Integer> looseNow = loose(helper);
						try {
							helper.assertTrue(end == MoveSite.Stage.DONE && skipped == 0, "the move ended " + end + ", skipped " + skipped);
							for (BlockPos p : List.of(first, second)) {
								helper.assertTrue(!level.getBlockState(helper.absolutePos(p)).is(tank.getBlock()),
									"a tank was left at the old spot " + p.toShortString());
							}
							holds(helper, "after the move", moved(s, first), Fluids.LAVA, EIGHT, Fluids.WATER);
							holds(helper, "after the move", moved(s, second), Fluids.WATER, EIGHT, Fluids.LAVA);
							helper.assertTrue(looseNow.isEmpty(), "after the move items lie loose: " + looseNow);
						} finally {
							tidy(helper, s);
						}
						helper.succeed();
					});
				});
			});
		});
	}

	/** A tank of lava beside a tank of water: each keeps its own fluid. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1111_mover_tanks_beside"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1111_mover_tanks_beside")
	public void sideBySideTanksOfLavaAndWaterArriveApartWithTheirOwnFluid(GameTestHelper helper) {
		apartTanksMove(helper, "tanks_beside", SPOT, SPOT.east());
	}

	/**
	 * A two-block Item Vault (48 emeralds) with a two-tall Fluid Tank (10 buckets of water) standing against it: two joined
	 * containers of different kinds touching. Both come across whole: the vault one vault with all 48, the tank one tank
	 * with all 10 through both blocks.
	 */
	//$ gametest_ticks_batch AREA '400' '"qa_e1111_mover_tank_by_vault"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1111_mover_tank_by_vault")
	public void aTankAgainstAVaultBothArriveWhole(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		long ten = 10 * FluidConstants.BUCKET;
		List<BlockPos> tankCells = List.of(SPOT.south(), SPOT.south().above());
		placeVault(level, helper);
		var tank = block("create:fluid_tank").defaultBlockState();
		for (BlockPos p : tankCells) {
			set(level, helper.absolutePos(p), tank);
		}
		helper.runAfterDelay(10, () -> {
			fillVault(level, helper);
			pour(helper, helper.absolutePos(tankCells.get(0)), Fluids.WATER, ten);
			for (BlockPos p : tankCells) {
				holds(helper, "before the move", helper.absolutePos(p), Fluids.WATER, ten, Fluids.LAVA);
			}
			Setup s = setupAround(helper, "tank_by_vault");
			helper.runAfterDelay(2, () -> {
				MoveSite site = work(level, s, x -> false);
				MoveSite.Stage end = site.stage();
				int skipped = site.skipped();
				helper.runAfterDelay(20, () -> {
					Map<String, Integer> looseNow = loose(helper);
					try {
						helper.assertTrue(end == MoveSite.Stage.DONE && skipped == 0, "the move ended " + end + ", skipped " + skipped);
						vaultWhole(helper, "after the move", moved(s, SPOT), moved(s, SPOT.east()), looseNow, EMERALDS);
						for (BlockPos p : tankCells) {
							holds(helper, "after the move", moved(s, p), Fluids.WATER, ten, Fluids.LAVA);
							helper.assertTrue(!level.getBlockState(helper.absolutePos(p)).is(tank.getBlock()),
								"a tank block was left at the old spot " + p.toShortString());
						}
					} finally {
						tidy(helper, s);
					}
					helper.succeed();
				});
			});
		});
	}
}
