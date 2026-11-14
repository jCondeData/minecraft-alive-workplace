package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.MIN;
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

/**
 * QA (lane E, 2026-11-10, second run): the Mover (45.3) moving a building with Create's multiblocks in it, the corners
 * lane-e-1110-0114 left untested. 45.3's Done when and moving-buildings.md: "nothing dropped, nothing lost, containers
 * packed with their contents kept apart"; the job canceled half way puts everything taken down back up where it was; a
 * save and reload in each stage loses nothing. Create keeps a two-block Item Vault's items (and a two-tall Fluid Tank's
 * fluid) in one block, the controller, which the other block names; these tests check the moved vault and tank still
 * hold everything as one container, read through either block, and that a reload with them packed loses and doubles
 * nothing. A one-block tank is the control for the two-tall one. The two-tall tank and a canceled vault move fail on main:
 * QaCreateMoverMultiblock1110e2BugCompatTests on tests/create-mover-multiblock-1110.
 */
public class QaCreateMoverMultiblock1110e2CompatTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_compat:huge_area";
	static final int EMERALDS = 48;
	/** Ten buckets: more than one tank block's eight, so the two blocks must work as one tank. */
	static final long WATER = 10 * FluidConstants.BUCKET;

	/** A two-block Item Vault along x at SPOT and SPOT.east(). */
	static void placeVault(ServerLevel level, GameTestHelper helper) {
		var state = with(block("create:item_vault").defaultBlockState(), "axis", "x");
		set(level, helper.absolutePos(SPOT), state);
		set(level, helper.absolutePos(SPOT.east()), state);
	}

	static Storage<ItemVariant> items(ServerLevel level, BlockPos abs) {
		for (Direction side : new Direction[] {null, Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
			Storage<ItemVariant> s = ItemStorage.SIDED.find(level, abs, side);
			if (s != null) {
				return s;
			}
		}
		return null;
	}

	static long water(ServerLevel level, BlockPos abs) {
		long most = 0;
		for (Direction side : new Direction[] {null, Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
			Storage<FluidVariant> s = FluidStorage.SIDED.find(level, abs, side);
			if (s == null) {
				continue;
			}
			long n = 0;
			for (StorageView<FluidVariant> view : s.nonEmptyViews()) {
				if (view.getResource().isOf(Fluids.WATER)) {
					n += view.getAmount();
				}
			}
			most = Math.max(most, n);
		}
		return most;
	}

	/** Fills the vault through its first block; the second block must then show the same items (it's one vault). */
	static void fillVault(ServerLevel level, GameTestHelper helper) {
		BlockPos a = helper.absolutePos(SPOT);
		Storage<ItemVariant> s = items(level, a);
		helper.assertTrue(s != null, "the vault has no item storage");
		try (Transaction tx = Transaction.openOuter()) {
			helper.assertTrue(s.insert(ItemVariant.of(Items.EMERALD), EMERALDS, tx) == EMERALDS, "the vault didn't take " + EMERALDS + " emeralds");
			tx.commit();
		}
		long first = held(level, a, Items.EMERALD);
		long second = held(level, helper.absolutePos(SPOT.east()), Items.EMERALD);
		helper.assertTrue(first == EMERALDS && second == EMERALDS, "before the move the two vault blocks aren't one vault: "
			+ first + " and " + second + " emeralds");
	}

	/** The block entity data at {@code abs} that says how Create joins the blocks (for the failure messages). */
	static String data(ServerLevel level, BlockPos abs) {
		var be = level.getBlockEntity(abs);
		if (be == null) {
			return "{no block entity}";
		}
		CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
		CompoundTag out = new CompoundTag();
		for (String key : new String[] {"Controller", "Size", "Height", "Length", "Width", "LastKnownPos"}) {
			if (tag.contains(key)) {
				out.put(key, tag.get(key));
			}
		}
		return out + " at " + abs.toShortString();
	}

	/** Where a cell of the house (test-relative) stands after the move. */
	static BlockPos moved(Setup s, BlockPos rel) {
		return s.before().at(rel.subtract(MIN), s.to(), Rotation.CLOCKWISE_90);
	}

	/** The vault at {@code a}, {@code b} holds the emeralds once, as one vault, and none lie loose. */
	static void vaultWhole(GameTestHelper helper, String when, BlockPos a, BlockPos b, Map<String, Integer> looseNow) {
		vaultWhole(helper, when, a, b, looseNow, EMERALDS);
	}

	/** The vault at {@code a}, {@code b} holds {@code count} emeralds once, as one vault, and none lie loose. */
	static void vaultWhole(GameTestHelper helper, String when, BlockPos a, BlockPos b, Map<String, Integer> looseNow, int count) {
		ServerLevel level = helper.getLevel();
		long first = held(level, a, Items.EMERALD);
		long second = held(level, b, Items.EMERALD);
		int loose = looseNow.getOrDefault("minecraft:emerald", 0);
		helper.assertTrue(level.getBlockState(a).is(block("create:item_vault")) && level.getBlockState(b).is(block("create:item_vault")),
			when + ": the vault blocks are " + level.getBlockState(a) + " and " + level.getBlockState(b));
		helper.assertTrue(Math.max(first, second) + loose <= count, when + ": " + count + " emeralds became " + first + " (first block), "
			+ second + " (second block) and " + loose + " loose: doubled");
		helper.assertTrue(first == count && second == count && looseNow.isEmpty(), when + ": the vault holds " + first + " through one block and "
			+ second + " through the other, of " + count + " emeralds (one vault shows the same through both); loose: " + looseNow
			+ "; " + level.getBlockState(a) + " " + data(level, a) + ", " + level.getBlockState(b) + " " + data(level, b));
	}

	/** A two-block vault holding 48 emeralds is moved a quarter turned: one vault at the new spot, all 48 in it once. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1110b_mover_vault2"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1110b_mover_vault2")
	public void aMovedTwoBlockVaultIsStillOneVaultWithItsItems(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		placeVault(level, helper);
		helper.runAfterDelay(10, () -> {
			fillVault(level, helper);
			Setup s = setupAround(helper, "vault2");
			helper.runAfterDelay(2, () -> {
				MoveSite site = work(level, s, x -> false);
				MoveSite.Stage end = site.stage();
				int skipped = site.skipped();
				BlockPos a = moved(s, SPOT);
				BlockPos b = moved(s, SPOT.east());
				helper.runAfterDelay(10, () -> { // Create re-forms its multiblocks on their first ticks
					Map<String, Integer> looseNow = loose(helper);
					try {
						helper.assertTrue(end == MoveSite.Stage.DONE && skipped == 0, "the move ended " + end + ", skipped " + skipped);
						vaultWhole(helper, "after the move", a, b, looseNow);
					} finally {
						tidy(helper, s);
					}
					helper.succeed();
				});
			});
		});
	}

	/** The control: a one-block Fluid Tank with eight buckets of water (all it holds) is moved; the eight come across. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1110b_mover_tank1"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1110b_mover_tank1")
	public void aMovedOneBlockFluidTankKeepsItsWater(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		long eight = 8 * FluidConstants.BUCKET;
		set(level, helper.absolutePos(SPOT), block("create:fluid_tank").defaultBlockState());
		helper.runAfterDelay(10, () -> {
			Storage<FluidVariant> in = FluidStorage.SIDED.find(level, helper.absolutePos(SPOT), Direction.UP);
			helper.assertTrue(in != null, "the tank has no fluid storage");
			try (Transaction tx = Transaction.openOuter()) {
				helper.assertTrue(in.insert(FluidVariant.of(Fluids.WATER), eight, tx) == eight, "the tank didn't take 8 buckets");
				tx.commit();
			}
			Setup s = setupAround(helper, "tank1");
			helper.runAfterDelay(2, () -> {
				MoveSite site = work(level, s, x -> false);
				MoveSite.Stage end = site.stage();
				int skipped = site.skipped();
				BlockPos a = moved(s, SPOT);
				helper.runAfterDelay(10, () -> {
					long there = water(level, a);
					long left = water(level, helper.absolutePos(SPOT));
					var state = level.getBlockState(a);
					tidy(helper, s);
					helper.assertTrue(end == MoveSite.Stage.DONE && skipped == 0, "the move ended " + end + ", skipped " + skipped);
					helper.assertTrue(state.is(block("create:fluid_tank")) && there == eight && left == 0, "after the move the new spot has " + state
						+ " with " + there + " droplets and the old spot " + left + ", of " + eight);
					helper.succeed();
				});
			});
		});
	}

	/** Saved and reloaded with the vault packed in the Mover's crates: the vault comes across once, whole. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1110b_mover_vault_reload"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1110b_mover_vault_reload")
	public void aMoveReloadedWithTheVaultPackedLosesAndDoublesNothing(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		placeVault(level, helper);
		helper.runAfterDelay(10, () -> {
			fillVault(level, helper);
			Setup s = setupAround(helper, "vault_reload");
			helper.runAfterDelay(2, () -> {
				work(level, s, x -> true); // the move starts: the building is captured as it stands
				// the village puts 16 more in after the move started: the Mover packs the vault as it is when he takes it down,
				// and that packing (not the capture) is what must come through the reload
				Storage<ItemVariant> more = items(level, helper.absolutePos(SPOT.east()));
				try (Transaction tx = Transaction.openOuter()) {
					helper.assertTrue(more.insert(ItemVariant.of(Items.EMERALD), 16, tx) == 16, "the vault didn't take 16 more");
					tx.commit();
				}
				MoveSite site = work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
				MoveSite.Stage down = site.stage();
				CompoundTag saved = MoveSites.get(level).save(new CompoundTag(), level.registryAccess());
				level.getDataStorage().set(MoveSites.NAME, MoveSites.load(saved, level.registryAccess()));
				MoveSite again = MoveSites.get(level).forBuilding(s.from()).orElseThrow();
				MoveSite done = work(level, s, x -> false);
				MoveSite.Stage end = done.stage();
				int skipped = done.skipped();
				BlockPos a = moved(s, SPOT);
				BlockPos b = moved(s, SPOT.east());
				helper.runAfterDelay(10, () -> {
					Map<String, Integer> looseNow = loose(helper);
					try {
						helper.assertTrue(down == MoveSite.Stage.FOUNDATION && again != site, "the reload didn't happen with the house down: " + down);
						helper.assertTrue(end == MoveSite.Stage.DONE && skipped == 0, "the reloaded move ended " + end + ", skipped " + skipped);
						vaultWhole(helper, "after a reload with the vault packed", a, b, looseNow, EMERALDS + 16);
					} finally {
						tidy(helper, s);
					}
					helper.succeed();
				});
			});
		});
	}
}
