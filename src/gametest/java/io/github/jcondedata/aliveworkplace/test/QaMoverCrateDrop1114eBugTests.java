package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaMoverCrateDrop1114eGameTests.count;
import static io.github.jcondedata.aliveworkplace.test.QaMoverCrateDrop1114eGameTests.moveToTheEnd;
import static io.github.jcondedata.aliveworkplace.test.QaMoverCrateDrop1114eGameTests.onHisCrate;
import static io.github.jcondedata.aliveworkplace.test.QaMoverCrateDrop1114eGameTests.roofOverHisCrate;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;

/**
 * QA (lane e, 2026-11-14), B1184's fallback: with no free spot within 16 blocks above the Mover's Crate, MoveSite.dropPacked
 * drops the block item and only an {@code Items} list, so a lectern's book is deleted. B1172: "nothing packed is ever
 * destroyed: the block waits, goes back, or is dropped with its contents".
 */
public class QaMoverCrateDrop1114eBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/** No free spot within 16 blocks above his crate: the lectern and its book are still dropped there (nothing packed destroyed). */
	//$ gametest_batch AREA '"qaE1114CrateRoofLectern"'
	@GameTest(template = AREA, batch = "qaE1114CrateRoofLectern")
	public void aLecternWithNoRoomAboveHisCrateKeepsItsBook(GameTestHelper helper) {
		MoverGameTests.Setup s = onHisCrate(helper, "qae1114rooflectern", Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.HAS_BOOK, true),
			be -> ((LecternBlockEntity) be).setBook(new ItemStack(Items.WRITABLE_BOOK)));
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(count(helper, Items.WRITABLE_BOOK) == 1, "setup: no book on the lectern");
			moveToTheEnd(helper, s, () -> roofOverHisCrate(helper));
			helper.assertTrue(count(helper, Items.LECTERN) == 1, "the lectern with no room above his crate: " + count(helper, Items.LECTERN) + " lie loose, not 1");
			helper.assertTrue(count(helper, Items.WRITABLE_BOOK) == 1, "the lectern's book with no room above his crate: " + count(helper, Items.WRITABLE_BOOK)
				+ " left of 1 (B1172: nothing packed is ever destroyed; the block is dropped with its contents)");
			helper.succeed();
		});
	}
}
