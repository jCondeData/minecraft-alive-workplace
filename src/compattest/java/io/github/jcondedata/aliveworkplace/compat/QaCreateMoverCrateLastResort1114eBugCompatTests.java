package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.SPOT;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.block;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.held;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.loose;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.set;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.setup;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.tidy;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.work;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverMultiblock1110e2CompatTests.moved;

import io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.Setup;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane e, 2026-11-14), B1246 with Create: a Mover's Crate stands where an Item Vault holding 48 emeralds goes up,
 * and the crate is in a storage room (a barrel on every spot within 2 of it, stone 3 to 16 up), so MoveSite.dropPacked
 * finds no spot to put the vault up for a moment and takes its last resort. That reads the packed data back only into a
 * vanilla Container (or a top-level {@code Items} list); Create's vault is neither (it keeps its items under
 * {@code Inventory}). B1172 / moving-buildings.md: "nothing packed is ever deleted, however the crate is roofed".
 */
public class QaCreateMoverCrateLastResort1114eBugCompatTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_compat:huge_area";

	/** Every spot within 2 of {@code at} (a 5 x 5 x 5 box, {@code at} left out). */
	static List<BlockPos> round(BlockPos at) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-2, -2, -2), at.offset(2, 2, 2))) {
			if (!p.equals(at)) {
				out.add(p.immutable());
			}
		}
		return out;
	}

	/** A storage room round {@code at}: a barrel (1 amethyst shard) on every spot within 2 with no block entity; stone 3 to 16 up. */
	static void storageRoom(ServerLevel level, BlockPos at) {
		for (BlockPos p : round(at)) {
			BlockState there = level.getBlockState(p);
			if (!there.hasBlockEntity() && !there.is(ModBlocks.MOVERS_CRATE)) {
				set(level, p, Blocks.BARREL.defaultBlockState());
				((Container) level.getBlockEntity(p)).setItem(0, new ItemStack(Items.AMETHYST_SHARD));
			}
		}
		for (int dy = 3; dy <= 16; dy++) {
			BlockState there = level.getBlockState(at.above(dy));
			if (there.isAir() || there.canBeReplaced()) {
				set(level, at.above(dy), Blocks.STONE.defaultBlockState());
			}
		}
	}

	/** Why {@code at} isn't a storage room right now (a free or plain spot within 2, a free spot 16 up), or null when it is. */
	static String notAStorageRoom(ServerLevel level, BlockPos at) {
		for (int dy = 1; dy <= 16; dy++) {
			if (level.getBlockState(at.above(dy)).isAir()) {
				return "air " + dy + " above the crate";
			}
		}
		for (BlockPos p : round(at)) {
			BlockState there = level.getBlockState(p);
			if (there.isAir() || !there.hasBlockEntity()) {
				return there + " (no block entity) at " + p.subtract(at) + " from the crate";
			}
		}
		return null;
	}

	/**
	 * The vault's new spot holds a Mover's Crate in a storage room: the vault and its 48 emeralds must both still be there
	 * (dropped, or the vault waiting or gone back).
	 */
	//$ gametest_ticks_batch AREA '400' '"qa_e1114_mover_crate_vault_storeroom"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1114_mover_crate_vault_storeroom")
	public void aVaultOnACrateAmongContainersKeepsItsEmeralds(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		Setup s = setup(helper, "crate_vault_storeroom_e1114", block("create:item_vault").defaultBlockState(), Items.EMERALD, 48);
		helper.runAfterDelay(2, () -> {
			BlockPos landing = moved(s, SPOT);
			String end;
			String room;
			Map<String, Integer> looseNow;
			long inOld;
			long inNew;
			long inBarrels = 0;
			try {
				set(level, landing, ModBlocks.MOVERS_CRATE.defaultBlockState());
				MoveSite site = work(level, s, x -> x.stage() == MoveSite.Stage.STRUCTURE || x.skipped() > 0);
				helper.assertTrue(site.stage() == MoveSite.Stage.STRUCTURE && site.skipped() == 0,
					"setup: the put-up didn't get to STRUCTURE before the drop: " + site.stage() + ", skipped " + site.skipped());
				String[] roomAtDrop = {"never checked"};
				site = work(level, s, x -> {
					if (x.skipped() > 0) {
						return true;
					}
					storageRoom(level, landing);
					roomAtDrop[0] = notAStorageRoom(level, landing);
					return false;
				});
				room = roomAtDrop[0];
				helper.assertTrue(site.skipped() == 1, "the vault on the crate was never dropped: " + site.stage() + ", skipped " + site.skipped());
				helper.assertTrue(room == null, "setup: the crate wasn't in a storage room right before the drop: " + room);
				helper.assertTrue(level.getBlockState(landing).is(ModBlocks.MOVERS_CRATE), "setup: the crate is gone: " + level.getBlockState(landing));
				site = work(level, s, x -> false);
				end = site.stage() + ", skipped " + site.skipped();
				looseNow = loose(helper);
				inOld = held(level, helper.absolutePos(SPOT), Items.EMERALD);
				inNew = held(level, landing, Items.EMERALD);
				for (BlockPos p : round(landing)) {
					if (level.getBlockEntity(p) instanceof Container c) {
						for (int i = 0; i < c.getContainerSize(); i++) {
							if (c.getItem(i).is(Items.EMERALD)) {
								inBarrels += c.getItem(i).getCount();
							}
						}
					}
				}
			} finally {
				tidy(helper, s);
			}
			int lying = looseNow.getOrDefault("minecraft:emerald", 0);
			helper.assertTrue(end.startsWith("DONE") && end.contains("skipped 1"), "the move: " + end);
			helper.assertTrue(looseNow.getOrDefault("create:item_vault", 0) == 1, "the vault itself isn't dropped: loose " + looseNow);
			helper.assertTrue(inOld + inNew + inBarrels + lying == 48, "a vault holding 48 emeralds went up over a Mover's Crate in a storage room (move "
				+ end + "): " + inOld + " at the old spot, " + inNew + " at the new, " + inBarrels + " in the barrels round it and " + lying
				+ " lie loose; loose " + looseNow + " (B1246: dropPacked's last resort reads back only a Container; B1172: nothing packed is ever deleted)");
			helper.succeed();
		});
	}
}
