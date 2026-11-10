package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.MIN;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.SPOT;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.block;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.loose;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.movesOnce;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.set;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.setupAround;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.tidy;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.work;

import io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.Setup;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import java.util.Map;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * QA (lane E, 2026-11-10, run 1012), from 45.5 ("containers come back with exactly their contents") and 45.3 ("nothing
 * dropped, nothing lost") with Create 6 installed: Create's machines that hold items or fluid between jobs (a Chute, a
 * Smart Chute, a Millstone's input, a Basin's items and its water, a Spout's water) are moved; what
 * they held arrives once at the new spot and nothing lies loose. QaCreateMoverContents1110eCompatTests covers the vault,
 * depot, crafter and ejector.
 */
public class QaCreateMoverMachines1110fCompatTests implements FabricGameTest {
	static final String AREA = QaCreateMoverContents1110eCompatTests.AREA;

	/**
	 * A Chute with 16 cobblestone in it. A chute keeps its item in its own data ("Item") and only offers it to a hopper
	 * while it's at the chute's bottom, so the count is read from the data: the 16 once, nothing loose.
	 */
	//$ gametest_batch AREA '"qa_e1110f_mover_chute"'
	@GameTest(template = AREA, batch = "qa_e1110f_mover_chute")
	public void aMovedChuteKeepsItsItemsOnce(GameTestHelper helper) {
		movesChuteItemOnce(helper, "chute", block("create:chute").defaultBlockState());
	}

	/** A Smart Chute (unpowered) with 16 cobblestone in it, read as for the Chute. */
	//$ gametest_batch AREA '"qa_e1110f_mover_smart_chute"'
	@GameTest(template = AREA, batch = "qa_e1110f_mover_smart_chute")
	public void aMovedSmartChuteKeepsItsItemsOnce(GameTestHelper helper) {
		movesChuteItemOnce(helper, "smart_chute", QaCreateMoverContents1110eCompatTests.with(block("create:smart_chute").defaultBlockState(), "powered", "false"));
	}

	/** A Millstone (not turning) with 16 wheat waiting to be ground. */
	//$ gametest_batch AREA '"qa_e1110f_mover_millstone"'
	@GameTest(template = AREA, batch = "qa_e1110f_mover_millstone")
	public void aMovedMillstoneKeepsItsWheatOnce(GameTestHelper helper) {
		movesOnce(helper, "millstone", block("create:millstone").defaultBlockState(), Items.WHEAT, 16);
	}

	/** A Basin with 16 iron ingots in it. */
	//$ gametest_batch AREA '"qa_e1110f_mover_basin"'
	@GameTest(template = AREA, batch = "qa_e1110f_mover_basin")
	public void aMovedBasinKeepsItsItemsOnce(GameTestHelper helper) {
		movesOnce(helper, "basin", block("create:basin").defaultBlockState(), Items.IRON_INGOT, 16);
	}

	/** A Basin with a bucket of water in it: the water arrives once, none left at the old spot. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1110f_mover_basin_water"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1110f_mover_basin_water")
	public void aMovedBasinKeepsItsWaterOnce(GameTestHelper helper) {
		movesFluidOnce(helper, "basin_water", block("create:basin").defaultBlockState(), FluidConstants.BUCKET);
	}

	/** A Spout holding a bucket of water. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1110f_mover_spout"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1110f_mover_spout")
	public void aMovedSpoutKeepsItsWaterOnce(GameTestHelper helper) {
		movesFluidOnce(helper, "spout", block("create:spout").defaultBlockState(), FluidConstants.BUCKET);
	}

	/** {@code inside} at SPOT holding {@code amount} of water, moved: the same amount at the new spot, none left or loose. */
	static void movesFluidOnce(GameTestHelper helper, String name, BlockState inside, long amount) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		set(level, helper.absolutePos(SPOT), inside);
		helper.runAfterDelay(5, () -> {
			long in = 0;
			for (Direction side : new Direction[] {Direction.UP, null, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN}) {
				Storage<FluidVariant> s = FluidStorage.SIDED.find(level, helper.absolutePos(SPOT), side);
				if (s == null) {
					continue;
				}
				try (Transaction tx = Transaction.openOuter()) {
					in = s.insert(FluidVariant.of(Fluids.WATER), amount, tx);
					if (in == amount) {
						tx.commit();
						break;
					}
				}
			}
			helper.assertTrue(in == amount, "setup: " + inside.getBlock() + " took " + in + " of " + amount + " droplets of water");
			Setup s = setupAround(helper, name);
			helper.runAfterDelay(2, () -> {
				MoveSite site = work(level, s, x -> false);
				MoveSite.Stage end = site.stage();
				int skipped = site.skipped();
				BlockPos newSpot = s.before().at(SPOT.subtract(MIN), s.to(), Rotation.CLOCKWISE_90);
				helper.runAfterDelay(5, () -> {
					BlockState there = level.getBlockState(newSpot);
					long moved = QaCreateMoverMultiblock1110e2CompatTests.water(level, newSpot);
					long left = QaCreateMoverMultiblock1110e2CompatTests.water(level, helper.absolutePos(SPOT));
					Map<String, Integer> looseNow = loose(helper);
					try {
						helper.assertTrue(end == MoveSite.Stage.DONE && skipped == 0, "the move ended " + end + ", skipped " + skipped);
						helper.assertTrue(there.is(inside.getBlock()) && moved == amount && left == 0, inside.getBlock() + " held " + amount
							+ " droplets of water; after the move the new spot has " + there + " with " + moved + " and the old spot " + left);
						helper.assertTrue(looseNow.isEmpty(), "loose after the move: " + looseNow);
					} finally {
						tidy(helper, s);
					}
					helper.succeed();
				});
			});
		});
	}

	/** The stack a chute keeps in its data: "16 minecraft:cobblestone", or what it has. */
	static String chuteItem(ServerLevel level, BlockPos abs) {
		net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(abs);
		if (be == null) {
			return "no block entity";
		}
		net.minecraft.nbt.CompoundTag item = be.saveWithoutMetadata(level.registryAccess()).getCompound("Item");
		net.minecraft.world.item.ItemStack stack = net.minecraft.world.item.ItemStack.parseOptional(level.registryAccess(), item);
		return stack.isEmpty() ? "nothing" : stack.getCount() + " " + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
	}

	/** {@code inside} (a chute) at SPOT with 16 cobblestone put in, moved: its data holds the 16 at the new spot, none loose. */
	static void movesChuteItemOnce(GameTestHelper helper, String name, BlockState inside) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		set(level, helper.absolutePos(SPOT), inside);
		helper.runAfterDelay(5, () -> {
			Setup s = QaCreateMoverContents1110eCompatTests.setup(helper, name, inside, Items.COBBLESTONE, 16);
			helper.runAfterDelay(2, () -> {
				String before = chuteItem(level, helper.absolutePos(SPOT));
				MoveSite site = work(level, s, x -> false);
				MoveSite.Stage end = site.stage();
				int skipped = site.skipped();
				BlockPos newSpot = s.before().at(SPOT.subtract(MIN), s.to(), Rotation.CLOCKWISE_90);
				helper.runAfterDelay(5, () -> {
					BlockState there = level.getBlockState(newSpot);
					String after = chuteItem(level, newSpot);
					Map<String, Integer> looseNow = loose(helper);
					try {
						helper.assertTrue(before.equals("16 minecraft:cobblestone"), "setup: the chute holds " + before);
						helper.assertTrue(end == MoveSite.Stage.DONE && skipped == 0, "the move ended " + end + ", skipped " + skipped);
						helper.assertTrue(there.is(inside.getBlock()) && after.equals(before) && looseNow.isEmpty(), inside.getBlock() + " held "
							+ before + "; after the move the new spot has " + there + " holding " + after + ", loose " + looseNow);
					} finally {
						tidy(helper, s);
					}
					helper.succeed();
				});
			});
		});
	}
}
