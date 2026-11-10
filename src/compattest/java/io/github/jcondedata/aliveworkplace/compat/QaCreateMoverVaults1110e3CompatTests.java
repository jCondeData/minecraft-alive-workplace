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
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverMultiblock1110e2CompatTests.EMERALDS;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverMultiblock1110e2CompatTests.data;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverMultiblock1110e2CompatTests.items;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverMultiblock1110e2CompatTests.moved;

import io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.Setup;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;

/**
 * QA (lane E, 2026-11-10, third run): the Mover (45.3) with Create Item Vaults bigger than the two-block one
 * lane-e-1110-0412 tested, the corner its note left open ("a 2x2 or 3-wide vault"). 45.3's Done when and
 * moving-buildings.md: nothing dropped, nothing lost, a container's contents kept apart; a save and reload in each stage
 * loses nothing. A vault two wide, two high and two long is eight blocks with one controller holding everything; these
 * check that moved a quarter turned it is still one vault reading the same 48 emeralds through every block, and that a
 * save and reload while it is half put up at the new spot (some blocks standing, the rest still packed) loses and
 * doubles nothing.
 */
public class QaCreateMoverVaults1110e3CompatTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_compat:huge_area";

	/** The eight cells of a vault two wide, two high, two long (along x) from SPOT: test-relative. */
	static List<BlockPos> cube() {
		List<BlockPos> out = new ArrayList<>();
		for (int dx = 0; dx < 2; dx++) {
			for (int dy = 0; dy < 2; dy++) {
				for (int dz = 0; dz < 2; dz++) {
					out.add(SPOT.offset(dx, dy, dz));
				}
			}
		}
		return out;
	}

	static void placeCube(ServerLevel level, GameTestHelper helper) {
		var state = with(block("create:item_vault").defaultBlockState(), "axis", "x");
		for (BlockPos p : cube()) {
			set(level, helper.absolutePos(p), state);
		}
	}

	/** Puts the emeralds in through the first block; every block must then read them (one vault). */
	static void fillCube(ServerLevel level, GameTestHelper helper) {
		Storage<ItemVariant> s = items(level, helper.absolutePos(SPOT));
		helper.assertTrue(s != null, "the vault has no item storage");
		try (Transaction tx = Transaction.openOuter()) {
			helper.assertTrue(s.insert(ItemVariant.of(Items.EMERALD), EMERALDS, tx) == EMERALDS, "the vault didn't take " + EMERALDS);
			tx.commit();
		}
		for (BlockPos p : cube()) {
			long n = held(level, helper.absolutePos(p), Items.EMERALD);
			helper.assertTrue(n == EMERALDS, "before the move the eight blocks aren't one vault: " + n + " through "
				+ p.toShortString() + "; " + data(level, helper.absolutePos(p)));
		}
	}

	/** Every moved cell is a vault reading the 48 once, and none lie loose. */
	static void cubeWhole(GameTestHelper helper, String when, Setup s, Map<String, Integer> looseNow) {
		ServerLevel level = helper.getLevel();
		StringBuilder seen = new StringBuilder();
		boolean ok = looseNow.isEmpty();
		long most = 0;
		for (BlockPos p : cube()) {
			BlockPos at = moved(s, p);
			boolean vault = level.getBlockState(at).is(block("create:item_vault"));
			long n = vault ? held(level, at, Items.EMERALD) : -1;
			most = Math.max(most, n);
			ok &= vault && n == EMERALDS;
			seen.append(at.toShortString()).append('=').append(vault ? n : "not a vault").append(' ').append(data(level, at)).append("; ");
		}
		int loose = looseNow.getOrDefault("minecraft:emerald", 0);
		helper.assertTrue(most + loose <= EMERALDS, when + ": the 48 emeralds became " + most + " in a block and " + loose + " loose: doubled");
		helper.assertTrue(ok, when + ": the eight-block vault isn't one vault holding " + EMERALDS + " through every block; loose "
			+ looseNow + "; " + seen);
	}

	/** An eight-block vault (2 x 2 x 2) holding 48 emeralds, moved a quarter turned: one vault, all 48 once. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1110c_mover_vault8"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1110c_mover_vault8")
	public void aMovedTwoByTwoByTwoVaultIsStillOneVaultWithItsItems(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		placeCube(level, helper);
		helper.runAfterDelay(10, () -> {
			fillCube(level, helper);
			Setup s = setupAround(helper, "vault8");
			helper.runAfterDelay(2, () -> {
				MoveSite site = work(level, s, x -> false);
				MoveSite.Stage end = site.stage();
				int skipped = site.skipped();
				helper.runAfterDelay(10, () -> { // Create re-forms its multiblocks on their first ticks
					Map<String, Integer> looseNow = loose(helper);
					try {
						helper.assertTrue(end == MoveSite.Stage.DONE && skipped == 0, "the move ended " + end + ", skipped " + skipped);
						cubeWhole(helper, "after the move", s, looseNow);
					} finally {
						tidy(helper, s);
					}
					helper.succeed();
				});
			});
		});
	}

	/**
	 * Saved and reloaded with the eight-block vault half put up at the new spot (one or more blocks standing, the rest
	 * still in the crates): the move ends with one vault, all 48 once.
	 */
	//$ gametest_ticks_batch AREA '400' '"qa_e1110c_mover_vault8_reload"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1110c_mover_vault8_reload")
	public void aMoveReloadedWithTheVaultHalfPutUpLosesAndDoublesNothing(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		placeCube(level, helper);
		helper.runAfterDelay(10, () -> {
			fillCube(level, helper);
			Setup s = setupAround(helper, "vault8_reload");
			helper.runAfterDelay(2, () -> {
				List<BlockPos> at = cube().stream().map(p -> moved(s, p)).toList();
				// stop as soon as some, but not all, of the vault's blocks stand at the new spot
				MoveSite site = work(level, s, x -> {
					long up = at.stream().filter(p -> level.getBlockState(p).is(block("create:item_vault"))).count();
					return up > 0 && up < at.size();
				});
				long upThen = at.stream().filter(p -> level.getBlockState(p).is(block("create:item_vault"))).count();
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
						helper.assertTrue(upThen > 0 && upThen < at.size() && again != site, "the reload didn't happen with the vault half up: "
							+ upThen + " of " + at.size() + " blocks up, stage " + mid);
						helper.assertTrue(end == MoveSite.Stage.DONE && skipped == 0, "the reloaded move ended " + end + ", skipped " + skipped);
						cubeWhole(helper, "after a reload with the vault half up (" + upThen + " of 8 blocks)", s, looseNow);
					} finally {
						tidy(helper, s);
					}
					helper.succeed();
				});
			});
		});
	}
}
