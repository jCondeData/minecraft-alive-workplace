package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.SPOT;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.block;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.held;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.loose;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.set;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.setupAround;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.tidy;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.with;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.work;

import io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.Setup;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.material.Fluids;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverMultiblock1110e2CompatTests.AREA;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverMultiblock1110e2CompatTests.WATER;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverMultiblock1110e2CompatTests.data;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverMultiblock1110e2CompatTests.fillVault;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverMultiblock1110e2CompatTests.moved;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverMultiblock1110e2CompatTests.placeVault;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverMultiblock1110e2CompatTests.vaultWhole;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverMultiblock1110e2CompatTests.water;

/**
 * QA (lane E, 2026-11-10): the Mover (45.3) and Create's multiblocks, the two cases that fail on main (see
 * QaCreateMoverMultiblock1110e2CompatTests for the passing ones). A two-tall Fluid Tank: taking the top block down makes
 * Create re-shape the bottom one (top=true), which then no longer matches the plan, so it's left standing at the old spot
 * with 8 of its 10 buckets, the moved house gets an empty top tank and no bottom one. A two-block Item Vault whose move
 * is canceled goes back up with each block's data as it was packed, when Create had already split the vault: one vault
 * no more.
 */
public class QaCreateMoverMultiblock1110e2BugCompatTests implements FabricGameTest {
	/** A two-tall Fluid Tank holding ten buckets of water is moved: all ten buckets at the new spot, through either block. */
	//$ gametest_batch AREA '"qa_e1110b_mover_tank2"'
	@GameTest(template = AREA, batch = "qa_e1110b_mover_tank2", timeoutTicks = 400)
	public void aMovedTwoTallFluidTankKeepsAllItsWater(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		var tank = block("create:fluid_tank").defaultBlockState();
		set(level, helper.absolutePos(SPOT), tank);
		set(level, helper.absolutePos(SPOT.above()), tank);
		helper.runAfterDelay(10, () -> {
			Storage<FluidVariant> s0 = FluidStorage.SIDED.find(level, helper.absolutePos(SPOT), Direction.UP);
			if (s0 == null) {
				s0 = FluidStorage.SIDED.find(level, helper.absolutePos(SPOT), null);
			}
			helper.assertTrue(s0 != null, "the tank has no fluid storage");
			try (Transaction tx = Transaction.openOuter()) {
				long in = s0.insert(FluidVariant.of(Fluids.WATER), WATER, tx);
				helper.assertTrue(in == WATER, "the two-tall tank took only " + in + " of " + WATER + " droplets");
				tx.commit();
			}
			helper.assertTrue(water(level, helper.absolutePos(SPOT)) == WATER && water(level, helper.absolutePos(SPOT.above())) == WATER,
				"before the move the tank blocks show " + water(level, helper.absolutePos(SPOT)) + " and " + water(level, helper.absolutePos(SPOT.above())));
			Setup s = setupAround(helper, "tank2");
			helper.runAfterDelay(2, () -> {
				MoveSite site = work(level, s, x -> false);
				MoveSite.Stage end = site.stage();
				int skipped = site.skipped();
				BlockPos a = moved(s, SPOT);
				BlockPos b = moved(s, SPOT.above());
				helper.runAfterDelay(10, () -> {
					long lower = water(level, a);
					long upper = water(level, b);
					var la = level.getBlockState(a);
					var lb = level.getBlockState(b);
					String seen = "lower " + la + " " + data(level, a) + ", upper " + lb + " " + data(level, b) + ", water " + lower + " / " + upper
						+ "; at the old spot " + level.getBlockState(helper.absolutePos(SPOT)) + " " + data(level, helper.absolutePos(SPOT)) + " with "
						+ water(level, helper.absolutePos(SPOT)) + " droplets";
					tidy(helper, s);
					helper.assertTrue(end == MoveSite.Stage.DONE && skipped == 0, "the move ended " + end + ", skipped " + skipped + ": " + seen);
					helper.assertTrue(la.is(tank.getBlock()) && lb.is(tank.getBlock()), "the tank blocks at the new spot are " + la + " and " + lb);
					helper.assertTrue(Math.max(lower, upper) <= WATER, "ten buckets became " + lower + " / " + upper + " droplets: doubled");
					helper.assertTrue(lower == WATER && upper == WATER, "after the move the tank shows " + lower / FluidConstants.BUCKET + " buckets through "
						+ "the lower block and " + upper / FluidConstants.BUCKET + " through the upper, of 10 (" + lower + " / " + upper + " droplets)");
					helper.succeed();
				});
			});
		});
	}

	/** The move called off once the house is all down: the vault goes back up where it was, one vault, its 48 emeralds once. */
	//$ gametest_batch AREA '"qa_e1110b_mover_vault_cancel"'
	@GameTest(template = AREA, batch = "qa_e1110b_mover_vault_cancel", timeoutTicks = 400)
	public void aMoveCanceledWithTheVaultPackedPutsItBackWhole(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		placeVault(level, helper);
		helper.runAfterDelay(10, () -> {
			fillVault(level, helper);
			Setup s = setupAround(helper, "vault_cancel");
			helper.runAfterDelay(2, () -> {
				MoveSite site = work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
				MoveSite.Stage down = site.stage();
				boolean wasEmpty = level.getBlockState(helper.absolutePos(SPOT)).isAir() && level.getBlockState(helper.absolutePos(SPOT.east())).isAir();
				MoveSites.get(level).cancel(level, s.from());
				for (int i = 0; i < QaCreateMoverContents1110eCompatTests.MAX_STEPS && site.stage() != MoveSite.Stage.DONE; i++) {
					MoveSites.get(level).step(level, site);
				}
				MoveSite.Stage end = site.stage();
				helper.runAfterDelay(10, () -> {
					Map<String, Integer> looseNow = loose(helper);
					try {
						helper.assertTrue(down == MoveSite.Stage.FOUNDATION && wasEmpty, "the house didn't come down first: " + down + ", vault gone " + wasEmpty);
						helper.assertTrue(end == MoveSite.Stage.DONE, "the canceled move ended " + end);
						vaultWhole(helper, "after the canceled move", helper.absolutePos(SPOT), helper.absolutePos(SPOT.east()), looseNow);
					} finally {
						tidy(helper, s);
					}
					helper.succeed();
				});
			});
		});
	}

}
