package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;

/**
 * QA (lane E, 2026-11-17): B1127 named the Create blocks the 2026-11-10 tests didn't reach ("likely others too: Mechanical
 * Arm, Item Drain, Portable Storage Interface (not tested)"). 45.3's Done when and moving-buildings.md: a building the
 * Mover moves keeps every item its containers hold, nothing dropped and nothing doubled. Each test here puts one more of
 * Create's item-holding blocks, holding items, in the small house of {@link QaCreateMoverContents1110eCompatTests}, has a
 * hired Mover move it a quarter turned, and counts the items after the take-down and at the end: all of them in the
 * block at its new spot, none loose. Only blocks a player can fill by hand or by a hopper and that hold the items while
 * they stand still (no rotation in these houses) are here (the Deployer is in QaCreateMoverHeld1111eCompatTests); the
 * Mechanical Arm and the Portable Storage Interface hold nothing a test can put in without a running contraption. The last two take B1127's own blocks through a save and
 * reload while the house is down (its blocks and their items packed in the Mover's crates, saved with the move).
 */
public class QaCreateMoverHolders1117eCompatTests implements FabricGameTest {
	static final String AREA = QaCreateMoverContents1110eCompatTests.AREA;

	/** A Basin with 32 iron ingots in it (a mixer's or press's bowl). */
	//$ gametest_batch AREA '"qa_e1117_mover_basin"'
	@GameTest(template = AREA, batch = "qa_e1117_mover_basin")
	public void aMovedBasinKeepsItsItemsOnce(GameTestHelper helper) {
		QaCreateMoverContents1110eCompatTests.movesOnce(helper, "basin",
			QaCreateMoverContents1110eCompatTests.block("create:basin").defaultBlockState(), Items.IRON_INGOT, 32);
	}

	/** A Millstone with 24 wheat waiting to be ground (it stands still, so it holds them). */
	//$ gametest_batch AREA '"qa_e1117_mover_millstone"'
	@GameTest(template = AREA, batch = "qa_e1117_mover_millstone")
	public void aMovedMillstoneKeepsItsItemsOnce(GameTestHelper helper) {
		QaCreateMoverContents1110eCompatTests.movesOnce(helper, "millstone",
			QaCreateMoverContents1110eCompatTests.block("create:millstone").defaultBlockState(), Items.WHEAT, 24);
	}

	/** A Toolbox with 40 cobblestone in it (a player's portable chest; Create's own inventory). */
	//$ gametest_batch AREA '"qa_e1117_mover_toolbox"'
	@GameTest(template = AREA, batch = "qa_e1117_mover_toolbox")
	public void aMovedToolboxKeepsItsItemsOnce(GameTestHelper helper) {
		QaCreateMoverContents1110eCompatTests.movesOnce(helper, "toolbox",
			QaCreateMoverContents1110eCompatTests.block("create:brown_toolbox").defaultBlockState(), Items.COBBLESTONE, 40);
	}

	/** An Item Drain with 1 water bucket in it (B1127 named it). */
	//$ gametest_batch AREA '"qa_e1117_mover_drain"'
	@GameTest(template = AREA, batch = "qa_e1117_mover_drain")
	public void aMovedItemDrainKeepsItsItemOnce(GameTestHelper helper) {
		QaCreateMoverContents1110eCompatTests.movesOnce(helper, "drain",
			QaCreateMoverContents1110eCompatTests.block("create:item_drain").defaultBlockState(), Items.WATER_BUCKET, 1);
	}

	/**
	 * A Chute with 16 coal in it (standing alone on a floor it holds its stack). Counted by the chute's own data: put up
	 * again, the stack is partway down the chute, where Create's item storage doesn't show it (lane E checked the data
	 * keeps all 16 and the chute holds them on), so the storage reads 0 then.
	 */
	//$ gametest_batch AREA '"qa_e1117_mover_chute"'
	@GameTest(template = AREA, batch = "qa_e1117_mover_chute")
	public void aMovedChuteKeepsItsItemsOnce(GameTestHelper helper) {
		moves(helper, "chute", QaCreateMoverContents1110eCompatTests.block("create:chute").defaultBlockState(), Items.COAL, 16, false);
	}

	/** How many {@code item} the block at {@code abs} holds: by its storage, or by its data's {@code Item} (a chute's stack). */
	static long held(ServerLevel level, BlockPos abs, Item item) {
		long n = QaCreateMoverContents1110eCompatTests.held(level, abs, item);
		net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(abs);
		if (be != null) {
			CompoundTag data = be.saveWithoutMetadata(level.registryAccess());
			if (data.contains("Item", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
				net.minecraft.world.item.ItemStack stack = net.minecraft.world.item.ItemStack.parseOptional(level.registryAccess(), data.getCompound("Item"));
				if (stack.is(item)) {
					n = Math.max(n, stack.getCount());
				}
			}
		}
		return n;
	}

	/**
	 * Like {@code movesOnce}, and with {@code reload} the move saved and loaded again (as a server restart does) once the
	 * house is down: every item comes across once, in the block at its new spot, none loose at the take-down or after.
	 */
	static void moves(GameTestHelper helper, String name, BlockState inside, Item item, int count, boolean reload) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		QaCreateMoverContents1110eCompatTests.set(level, helper.absolutePos(QaCreateMoverContents1110eCompatTests.SPOT), inside);
		helper.runAfterDelay(5, () -> {
			QaCreateMoverContents1110eCompatTests.Setup s = QaCreateMoverContents1110eCompatTests.setup(helper, name, inside, item, count);
			helper.runAfterDelay(2, () -> {
				MoveSite site = QaCreateMoverContents1110eCompatTests.work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
				MoveSite.Stage downStage = site.stage();
				Map<String, Integer> afterDown = QaCreateMoverContents1110eCompatTests.loose(helper);
				if (reload) {
					CompoundTag saved = MoveSites.get(level).save(new CompoundTag(), level.registryAccess());
					level.getDataStorage().set(MoveSites.NAME, MoveSites.load(saved, level.registryAccess()));
				}
				MoveSite again = MoveSites.get(level).forBuilding(s.from()).orElse(null);
				MoveSite done = again == null ? null : QaCreateMoverContents1110eCompatTests.work(level, s, x -> false);
				helper.runAfterDelay(10, () -> {
					BlockPos newSpot = s.before().at(QaCreateMoverContents1110eCompatTests.SPOT.subtract(QaCreateMoverContents1110eCompatTests.MIN), s.to(),
						Rotation.CLOCKWISE_90);
					long inBlock = held(level, newSpot, item);
					Map<String, Integer> afterAll = QaCreateMoverContents1110eCompatTests.loose(helper);
					String key = BuiltInRegistries.ITEM.getKey(item).toString();
					BlockState there = level.getBlockState(newSpot);
					try {
						helper.assertTrue(downStage == MoveSite.Stage.FOUNDATION, "the house didn't come down: " + downStage);
						helper.assertTrue(afterDown.isEmpty(), inside.getBlock() + ": taking the house down dropped " + afterDown);
						helper.assertTrue(again != null && (!reload || again != site), "the move didn't survive the reload");
						helper.assertTrue(done.stage() == MoveSite.Stage.DONE && done.skipped() == 0,
							"the reloaded move ended " + done.stage() + ", skipped " + done.skipped());
						helper.assertTrue(there.is(inside.getBlock()), "after the reload the new spot " + newSpot + " has " + there);
						helper.assertTrue(inBlock == count && afterAll.isEmpty(), inside.getBlock() + " held " + count + " " + key
							+ (reload ? "; after a reload with the house down, " : "; after the move, ") + inBlock + " in it at the new spot, loose: " + afterAll);
					} finally {
						QaCreateMoverContents1110eCompatTests.tidy(helper, s);
					}
					helper.succeed();
				});
			});
		});
	}

	/** B1127's Mechanical Crafter (1 iron ingot) through a save and reload with the house down. */
	//$ gametest_batch AREA '"qa_e1117_mover_crafter_reload"'
	@GameTest(template = AREA, batch = "qa_e1117_mover_crafter_reload")
	public void aMechanicalCrafterKeepsItsItemOnceThroughAReload(GameTestHelper helper) {
		moves(helper, "crafter_reload",
			QaCreateMoverContents1110eCompatTests.block("create:mechanical_crafter").defaultBlockState(), Items.IRON_INGOT, 1, true);
	}

	/** B1127's Weighted Ejector (16 gold ingots) through a save and reload with the house down. */
	//$ gametest_batch AREA '"qa_e1117_mover_ejector_reload"'
	@GameTest(template = AREA, batch = "qa_e1117_mover_ejector_reload")
	public void aWeightedEjectorKeepsItsItemsOnceThroughAReload(GameTestHelper helper) {
		moves(helper, "ejector_reload",
			QaCreateMoverContents1110eCompatTests.block("create:weighted_ejector").defaultBlockState(), Items.GOLD_INGOT, 16, true);
	}
}
