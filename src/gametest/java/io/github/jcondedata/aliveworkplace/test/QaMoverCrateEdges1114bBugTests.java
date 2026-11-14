package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.move.MoveSite;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;

/**
 * QA (lane b, 2026-11-14, second run), probes for B1234-B1238's fix from the bug text ("nothing packed is ever
 * destroyed, however the crate is roofed"; B1236: "the block itself is dropped (as the packed block's item)").
 * Both fail on main 866f81f94:
 * - a lectern among containers: MoveSite.dropPacked's last resort (no spot at all) reads the packed data back only into a
 *   Container, and a lectern's block entity isn't one, so its book is deleted: "the lectern's book (only containers
 *   round the crate): 0 minecraft:writable_book round the test, not 1 (lost)" (the lectern itself is dropped);
 * - an ender chest: dropped as its hand-broken loot, 8 obsidian, not as itself: "the ender chest (8 obsidian lie
 *   there): 0 minecraft:ender_chest round the test, not 1 (lost)".
 */
public class QaMoverCrateEdges1114bBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/**
	 * His crate in a storage room (a barrel on every spot round it, stone up the column): no spot to put the lectern up
	 * at, so the last resort. The lectern and its book must both still be there.
	 */
	//$ gametest_batch AREA '"qaB1238StoreRoomLectern1114b"'
	@GameTest(template = AREA, batch = "qaB1238StoreRoomLectern1114b")
	public void aLecternOnACrateAmongContainersKeepsItsBook(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = QaMoverCrateHolds1114cGameTests.crateInside(helper, "qastorelectern1114b");
		QaMoverCrateHolds1114cGameTests.replaceChest(helper, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.HAS_BOOK, true),
			be -> ((LecternBlockEntity) be).setBook(new ItemStack(Items.WRITABLE_BOOK)));
		helper.runAfterDelay(2, () -> {
			QaMoverCrateEdges1114bGameTests.assertExactly(helper, Items.WRITABLE_BOOK, 1, "setup");
			QaMoverCrateEdges1114bGameTests.untilTheDrop(helper, s, () -> QaMoverCrateEdges1114bGameTests.barrelsRound(helper));
			MoveSite site = MoverGameTests.work(level, s, x -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE && site.skipped() == 1, "the move ended " + site.stage() + ", skipped " + site.skipped());
			QaMoverCrateEdges1114bGameTests.assertExactly(helper, Items.LECTERN, 1, "the lectern (only containers round the crate)");
			QaMoverCrateEdges1114bGameTests.assertExactly(helper, Items.WRITABLE_BOOK, 1, "the lectern's book (only containers round the crate)");
			helper.succeed();
		});
	}

	/** An ender chest on his crate (room above): the ender chest itself is dropped, not 8 obsidian (it drops itself only to Silk Touch). */
	//$ gametest_batch AREA '"qaB1236EnderChest1114b"'
	@GameTest(template = AREA, batch = "qaB1236EnderChest1114b")
	public void anEnderChestOnHisCrateIsDroppedItself(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = QaMoverCrateHolds1114cGameTests.crateInside(helper, "qaenderchest1114b");
		QaMoverCrateHolds1114cGameTests.replaceChest(helper, Blocks.ENDER_CHEST.defaultBlockState(), be -> { });
		helper.runAfterDelay(2, () -> {
			MoveSite site = MoverGameTests.work(level, s, x -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE && site.skipped() == 1, "the move ended " + site.stage() + ", skipped " + site.skipped());
			int obsidian = QaMoverCrateEdges1114bGameTests.count(helper, Items.OBSIDIAN);
			QaMoverCrateEdges1114bGameTests.assertExactly(helper, Items.ENDER_CHEST, 1, "the ender chest (" + obsidian + " obsidian lie there)");
			helper.succeed();
		});
	}
}
