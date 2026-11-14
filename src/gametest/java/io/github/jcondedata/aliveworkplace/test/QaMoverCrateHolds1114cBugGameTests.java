package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.move.MoveSite;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;

/**
 * QA bug probe (lane c, 2026-11-14) for B1184's fix: when no block is free in the 16 over the Mover's Crate (his crate
 * in a cellar, under a tower), the fix falls back to the block item and an Items list, so a block holding its things
 * another way (a lectern's book, a jukebox's disc) loses them again. B1184/B1172 expected: nothing packed is ever
 * destroyed. Same house, Mover and spot as QaMoverCrateHolds1114cGameTests.
 */
public class QaMoverCrateHolds1114cBugGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/** The 16 blocks over his crate built up once the house is down: the lectern and its book must still be there. */
	//$ gametest_batch AREA '"qaB1184NoRoomLectern1114c"'
	@GameTest(template = AREA, batch = "qaB1184NoRoomLectern1114c")
	public void aLecternOnACrateWithNoRoomAboveKeepsItsBook(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = QaMoverCrateHolds1114cGameTests.crateInside(helper, "qanoroom1114c");
		QaMoverCrateHolds1114cGameTests.replaceChest(helper, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.HAS_BOOK, true),
			be -> ((LecternBlockEntity) be).setBook(new ItemStack(Items.WRITABLE_BOOK)));
		helper.runAfterDelay(2, () -> {
			QaMoverCrateHolds1114cGameTests.work(level, s, x -> x.stage() != MoveSite.Stage.DISMANTLE);
			QaMoverCrateHolds1114cGameTests.column(helper, 16);
			MoveSite site = QaMoverCrateHolds1114cGameTests.work(level, s, x -> false);
			QaMoverCrateHolds1114cGameTests.assertEnded(helper, level, s, site, 1, "no room over the crate");
			QaMoverCrateHolds1114cGameTests.assertExactly(helper, Items.LECTERN, 1, "the lectern");
			QaMoverCrateHolds1114cGameTests.assertExactly(helper, Items.WRITABLE_BOOK, 1, "the lectern's book (16 blocks over the crate all built up)");
			helper.succeed();
		});
	}

	/**
	 * A chiseled bookshelf with three books on his crate (room above): the fix breaks it with no tool, and vanilla drops a
	 * chiseled bookshelf only to Silk Touch, so the books lie there but the shelf itself is gone (before the fix, its Items
	 * list meant it was dropped as its item with them). Expected: the shelf lies there too.
	 */
	//$ gametest_batch AREA '"qaB1184ShelfLost1114c"'
	@GameTest(template = AREA, batch = "qaB1184ShelfLost1114c")
	public void aChiseledBookshelfOnHisCrateIsDroppedItself(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = QaMoverCrateHolds1114cGameTests.crateInside(helper, "qashelflost1114c");
		QaMoverCrateHolds1114cGameTests.shelf(helper);
		helper.runAfterDelay(2, () -> {
			MoveSite site = QaMoverCrateHolds1114cGameTests.work(level, s, x -> false);
			QaMoverCrateHolds1114cGameTests.assertEnded(helper, level, s, site, 1, "chiseled bookshelf on the crate");
			QaMoverCrateHolds1114cGameTests.assertExactly(helper, Items.BOOK, 1, "the shelf's book");
			QaMoverCrateHolds1114cGameTests.assertExactly(helper, Items.CHISELED_BOOKSHELF, 1, "the chiseled bookshelf");
			helper.succeed();
		});
	}

	/** The same with a jukebox and its disc. */
	//$ gametest_batch AREA '"qaB1184NoRoomJukebox1114c"'
	@GameTest(template = AREA, batch = "qaB1184NoRoomJukebox1114c")
	public void aJukeboxOnACrateWithNoRoomAboveKeepsItsDisc(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = QaMoverCrateHolds1114cGameTests.crateInside(helper, "qanoroomj1114c");
		QaMoverCrateHolds1114cGameTests.replaceChest(helper, Blocks.JUKEBOX.defaultBlockState().setValue(JukeboxBlock.HAS_RECORD, true),
			be -> ((JukeboxBlockEntity) be).setSongItemWithoutPlaying(new ItemStack(Items.MUSIC_DISC_CAT)));
		helper.runAfterDelay(2, () -> {
			QaMoverCrateHolds1114cGameTests.work(level, s, x -> x.stage() != MoveSite.Stage.DISMANTLE);
			QaMoverCrateHolds1114cGameTests.column(helper, 16);
			MoveSite site = QaMoverCrateHolds1114cGameTests.work(level, s, x -> false);
			QaMoverCrateHolds1114cGameTests.assertEnded(helper, level, s, site, 1, "no room over the crate");
			QaMoverCrateHolds1114cGameTests.assertExactly(helper, Items.JUKEBOX, 1, "the jukebox");
			QaMoverCrateHolds1114cGameTests.assertExactly(helper, Items.MUSIC_DISC_CAT, 1, "the jukebox's disc (16 blocks over the crate all built up)");
			helper.succeed();
		});
	}
}
