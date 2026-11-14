package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaMoverCrateVerify1114fGameTests.assertNoWaterNear;
import static io.github.jcondedata.aliveworkplace.test.QaMoverCrateVerify1114fGameTests.assertOwnItem;
import static io.github.jcondedata.aliveworkplace.test.QaMoverCrateVerify1114fGameTests.place;
import static io.github.jcondedata.aliveworkplace.test.QaMoverCrateVerify1114fGameTests.run;

import io.github.jcondedata.aliveworkplace.move.MoveSite;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA bug probe (2026-11-14, verifying B1236), registered with B1253's fix: a block with no packed data (glass, a plain bookshelf, ice;
 * an ender chest, whose block entity saves nothing) packed onto the spot where his Mover's Crate stands vanishes: nothing
 * of it is dropped anywhere (0 items in containers, on the ground or carried). MoveSite.dropPacked returns early for a
 * block with no data ("left out as before, nothing dropped (B1129)"), so this is the design B1129's fix chose for a wall
 * plank; B1172's expected ("nothing packed is ever destroyed: the block waits, goes back, or is dropped") and B1236's
 * report ("any other Silk-Touch-only block likely does the same (glass, ice, bookshelf-like blocks)") say otherwise. A
 * B1253 decided yes: a block that can't go up on a crate is dropped as its item, a wall's plank included.
 * Same house and Mover as MoverCrateNoRoomB1234GameTests.
 */
public class QaMoverCrateVerify1114fBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/** Glass on his crate (room above): one glass lies there, none lost. */
	//$ gametest_batch AREA '"qaVerify1114fGlass"'
	@GameTest(template = AREA, batch = "qaVerify1114fGlass")
	public void glassOnHisCrateIsDroppedAsGlass(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qaverglass1114f");
		place(helper, Blocks.GLASS.defaultBlockState());
		helper.runAfterDelay(2, () -> {
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.GLASS, 0, "setup");
			MoveSite site = run(helper, s, () -> { });
			MoverCrateNoRoomB1234GameTests.assertEnded(helper, level, s, site, 1, "glass on his crate");
			assertOwnItem(helper, Items.GLASS, "the glass");
			helper.succeed();
		});
	}

	/** A plain bookshelf on his crate (room above): the bookshelf lies there, not the 3 books a bare hand gets. */
	//$ gametest_batch AREA '"qaVerify1114fBookshelf"'
	@GameTest(template = AREA, batch = "qaVerify1114fBookshelf")
	public void aPlainBookshelfOnHisCrateIsDroppedAsABookshelf(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qaverbookshelf1114f");
		place(helper, Blocks.BOOKSHELF.defaultBlockState());
		helper.runAfterDelay(2, () -> {
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.BOOK, 0, "setup");
			MoveSite site = run(helper, s, () -> { });
			MoverCrateNoRoomB1234GameTests.assertEnded(helper, level, s, site, 1, "bookshelf on his crate");
			assertOwnItem(helper, Items.BOOKSHELF, "the bookshelf", Items.BOOK);
			helper.succeed();
		});
	}

	/** Ice on his crate (room above): one ice lies there, and no water is left anywhere near (a bare hand leaves water). */
	//$ gametest_batch AREA '"qaVerify1114fIce"'
	@GameTest(template = AREA, batch = "qaVerify1114fIce")
	public void iceOnHisCrateIsDroppedAsIceAndLeavesNoWater(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qaverice1114f");
		place(helper, Blocks.ICE.defaultBlockState());
		helper.runAfterDelay(2, () -> {
			MoveSite site = run(helper, s, () -> { });
			MoverCrateNoRoomB1234GameTests.assertEnded(helper, level, s, site, 1, "ice on his crate");
			assertOwnItem(helper, Items.ICE, "the ice");
			assertNoWaterNear(helper, "ice on his crate");
			helper.succeed();
		});
	}

	/** An ender chest on his crate (room above): the ender chest lies there, not the 8 obsidian a pickaxe gets. */
	//$ gametest_batch AREA '"qaVerify1114fEnderChest"'
	@GameTest(template = AREA, batch = "qaVerify1114fEnderChest")
	public void anEnderChestOnHisCrateIsDroppedAsAnEnderChest(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qaverender1114f");
		MoverCrateNoRoomB1234GameTests.replaceChest(helper, Blocks.ENDER_CHEST.defaultBlockState(), be -> { });
		helper.runAfterDelay(2, () -> {
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.OBSIDIAN, 0, "setup");
			MoveSite site = run(helper, s, () -> { });
			MoverCrateNoRoomB1234GameTests.assertEnded(helper, level, s, site, 1, "ender chest on his crate");
			assertOwnItem(helper, Items.ENDER_CHEST, "the ender chest", Items.OBSIDIAN);
			helper.succeed();
		});
	}

}
