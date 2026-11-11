package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.block;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.fill;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.set;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.with;

import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane E, 2026-11-11): the storage scan kept near a workstation (B455/B412, commit 43b2f71a8) with Create in the
 * pack. Its promise: the list of storage a job sees near its station is what a fresh scan finds, at once, whatever was
 * placed or broken, and a scan with another mod's block entity in reach is never kept, because Create's storage can
 * change without its block changing (an Item Vault joining a second block). These tests build what a player builds
 * beside a workstation with Create (a vault grown block by block, a toolbox, a shaft by the chests) and compare the
 * answer every job gets with a fresh scan, and the vault's items counted once, as a job counts its supplies.
 */
public class QaStoreScanCreate1111eCompatTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos BENCH = new BlockPos(15, 2, 15);

	/** The answer a job gets and a fresh scan's: the same list in the same order. */
	private static List<BlockPos> both(GameTestHelper helper, String when) {
		ServerLevel level = helper.getLevel();
		BlockPos bench = helper.absolutePos(BENCH);
		List<BlockPos> kept = SupplyContainers.find(level, bench, null);
		List<BlockPos> fresh;
		SupplyContainers.KEEP_SCANS = false;
		try {
			fresh = SupplyContainers.find(level, bench, null);
		} finally {
			SupplyContainers.KEEP_SCANS = true;
		}
		helper.assertTrue(kept.equals(fresh), when + ": the list a job gets " + kept + " isn't what a scan finds, " + fresh);
		return kept;
	}

	/**
	 * A vault placed beside the station, filled, then grown to two blocks: the job sees it at once, and once Create has
	 * joined the blocks it is one store whose 48 emeralds count once, every ask the same as a fresh scan; with Create's
	 * block entities in reach no answer is kept.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_e1111_scan_vault"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_e1111_scan_vault")
	public void aVaultGrownBesideAStationIsOneStoreAtOnce(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		BlockPos chest = new BlockPos(13, 2, 13), a = new BlockPos(17, 2, 15), b = new BlockPos(18, 2, 15);
		helper.setBlock(chest, Blocks.CHEST);
		fill(level, helper.absolutePos(chest), Items.OAK_PLANKS, 10);
		List<BlockPos> start = both(helper, "a chest only");
		helper.assertTrue(start.contains(helper.absolutePos(chest)), "the chest isn't listed: " + start);

		var vault = with(block("create:item_vault").defaultBlockState(), "axis", "x");
		set(level, helper.absolutePos(a), vault);
		List<BlockPos> one = both(helper, "a one-block vault placed");
		helper.assertTrue(one.contains(helper.absolutePos(a)), "the vault just placed isn't listed: " + one);
		fill(level, helper.absolutePos(a), Items.EMERALD, 48);
		helper.assertTrue(SupplyContainers.count(level, both(helper, "the vault filled"), Items.EMERALD) == 48,
			"the vault's 48 emeralds aren't counted once");
		set(level, helper.absolutePos(b), vault);
		helper.runAfterDelay(10, () -> {
			// Create has joined the blocks on its own tick: no block entity placed, the job's answer must follow anyway
			long before = SupplyContainers.scans;
			List<BlockPos> joined = null;
			for (int i = 0; i < 10; i++) {
				joined = both(helper, "the vault grown to two blocks (ask " + (i + 1) + ")");
			}
			long scanned = SupplyContainers.scans - before;
			long vaultBlocks = joined.stream().filter(p -> p.equals(helper.absolutePos(a)) || p.equals(helper.absolutePos(b))).count();
			helper.assertTrue(vaultBlocks == 1, "the two-block vault is listed " + vaultBlocks + " times, not once: " + joined);
			long emeralds = SupplyContainers.count(level, joined, Items.EMERALD);
			helper.assertTrue(emeralds == 48, "the job counts " + emeralds + " emeralds in the two-block vault, not 48");
			helper.assertTrue(SupplyContainers.count(level, joined, Items.OAK_PLANKS) == 10, "the chest's planks aren't counted");
			// 10 asks, each a job's and a fresh one: with Create's vault in reach nothing is kept
			helper.assertTrue(scanned == 20, "with a vault in reach 20 asks scanned " + scanned + " times (a kept answer near Create)");
			helper.succeed();
		});
	}

	/**
	 * A Create Toolbox and a shaft beside the station's chests: the toolbox is never a store (a player's tools are
	 * nobody's supplies), the chests are, and a chest broken or placed beside them shows in the very next ask.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa_e1111_scan_toolbox"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa_e1111_scan_toolbox")
	public void aToolboxBesideTheChestsIsNeverAStore(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		BlockPos chest = new BlockPos(13, 2, 15), box = new BlockPos(17, 2, 17), shaft = new BlockPos(14, 2, 15), barrel = new BlockPos(16, 2, 13);
		helper.setBlock(chest, Blocks.CHEST);
		set(level, helper.absolutePos(box), block("create:brown_toolbox").defaultBlockState());
		set(level, helper.absolutePos(shaft), block("create:shaft").defaultBlockState());
		helper.assertTrue(level.getBlockEntity(helper.absolutePos(box)) != null, "the toolbox has no block entity");
		List<BlockPos> first = both(helper, "a chest, a toolbox and a shaft");
		helper.assertTrue(first.contains(helper.absolutePos(chest)), "the chest isn't listed: " + first);
		helper.assertFalse(first.contains(helper.absolutePos(box)), "the toolbox is listed as storage: " + first);
		helper.assertFalse(first.contains(helper.absolutePos(shaft)), "the shaft is listed as storage: " + first);
		helper.setBlock(barrel, Blocks.BARREL);
		helper.assertTrue(both(helper, "a barrel placed").contains(helper.absolutePos(barrel)), "the barrel just placed isn't listed");
		level.destroyBlock(helper.absolutePos(chest), false);
		helper.assertFalse(both(helper, "the chest broken").contains(helper.absolutePos(chest)), "the broken chest is still listed");
		level.destroyBlock(helper.absolutePos(box), false);
		level.destroyBlock(helper.absolutePos(shaft), false);
		List<BlockPos> plain = both(helper, "Create's blocks taken away");
		helper.assertTrue(plain.contains(helper.absolutePos(barrel)) && !plain.contains(helper.absolutePos(chest)), "after Create's blocks went: " + plain);
		helper.succeed();
	}
}
