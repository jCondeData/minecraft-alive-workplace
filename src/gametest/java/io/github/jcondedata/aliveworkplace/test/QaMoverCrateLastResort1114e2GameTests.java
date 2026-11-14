package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.move.MoveSite;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane e, 2026-11-14 evening), the fix for B1246-B1250 and B1253 (748f0f194, ca727fe8e) from their text: a packed
 * block whose new spot holds the Mover's Crate is dropped as itself with everything it held, nothing deleted and nothing
 * counted twice, "however the crate is roofed" (B1172). The bug lanes' tests drop the campfire, beehive and ender chest
 * with room round the crate (a spot to put them up for a moment); these put the crate in a storage room (a barrel on
 * every spot within 2 of it, stone up its column), so MoveSite.dropPacked takes its last resort: the drops worked out
 * from a block entity read back from the packed data, plus every item that data holds unless the block's own item keeps
 * them (moving-buildings.md). The beehive's case fails (its bees are lost): QaMoverCrateLastResort1114e2BugGameTests. Every count is exact: a shulker box's diamonds are counted inside its item too, so a
 * box dropped full and its diamonds dropped loose as well would fail as doubled.
 */
public class QaMoverCrateLastResort1114e2GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/** The block put where the chest stood is dropped in the storage room's last resort; the move ends with it skipped. */
	static void dropInTheStoreRoom(GameTestHelper helper, MoverGameTests.Setup s) {
		ServerLevel level = helper.getLevel();
		QaMoverCrateEdges1114bGameTests.untilTheDrop(helper, s, () -> QaMoverCrateEdges1114bGameTests.barrelsRound(helper));
		MoveSite site = MoverGameTests.work(level, s, x -> false);
		helper.assertTrue(site.stage() == MoveSite.Stage.DONE && site.skipped() == 1, "the move ended " + site.stage() + ", skipped " + site.skipped());
	}

	/** {@code item} round the test (in containers, on lecterns, loose, carried), plus what loose items hold in their own container. */
	private static int everywhere(GameTestHelper helper, Item item) {
		int n = QaMoverCrateEdges1114bGameTests.count(helper, item);
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(new BlockPos(0, 1, 0))).inflate(48))) {
			ItemContainerContents in = e.getItem().get(DataComponents.CONTAINER);
			if (in != null) {
				for (ItemStack stack : in.nonEmptyItems()) {
					if (stack.is(item)) {
						n += stack.getCount();
					}
				}
			}
		}
		return n;
	}

	/** {@code item} round the test as {@link QaMoverCrateEdges1114bGameTests#count} counts it, plus what campfires standing there cook. */
	private static int withCampfires(GameTestHelper helper, Item item) {
		int n = QaMoverCrateEdges1114bGameTests.count(helper, item);
		ServerLevel level = helper.getLevel();
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(29, 29, 29)))) {
			if (level.getBlockEntity(pos) instanceof CampfireBlockEntity fire) {
				for (ItemStack stack : fire.getItems()) {
					if (stack.is(item)) {
						n += stack.getCount();
					}
				}
			}
		}
		return n;
	}

	/** Bees anywhere: flying, in a beehive standing in the area, or inside a beehive item. */
	static int bees(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		AABB area = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(48);
		int n = level.getEntitiesOfClass(Bee.class, area).size();
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(29, 29, 29)))) {
			if (level.getBlockEntity(pos) instanceof BeehiveBlockEntity hive) {
				n += hive.getOccupantCount();
			}
		}
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, area)) {
			List<BeehiveBlockEntity.Occupant> in = e.getItem().get(DataComponents.BEES);
			n += in == null ? 0 : in.size();
		}
		return n;
	}

	/** An unlit campfire holding 2 beef (unlit, so nothing cooks), the crate in a storage room: the campfire itself (no charcoal) and its 2 beef. */
	//$ gametest_batch AREA '"qaE1114e2StoreCampfire"'
	@GameTest(template = AREA, batch = "qaE1114e2StoreCampfire")
	public void aCampfireOnACrateAmongContainersIsDroppedItselfWithItsFood(GameTestHelper helper) {
		MoverGameTests.Setup s = QaMoverCrateHolds1114cGameTests.crateInside(helper, "qae2storecampfire");
		QaMoverCrateHolds1114cGameTests.replaceChest(helper, Blocks.CAMPFIRE.defaultBlockState().setValue(net.minecraft.world.level.block.CampfireBlock.LIT, false), be -> {
			CampfireBlockEntity fire = (CampfireBlockEntity) be;
			fire.getItems().set(0, new ItemStack(Items.BEEF));
			fire.getItems().set(1, new ItemStack(Items.BEEF));
		});
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(withCampfires(helper, Items.BEEF) == 2, "setup: " + withCampfires(helper, Items.BEEF) + " beef, not 2");
			dropInTheStoreRoom(helper, s);
			QaMoverCrateEdges1114bGameTests.assertExactly(helper, Items.CAMPFIRE, 1, "the campfire (storage room)");
			QaMoverCrateEdges1114bGameTests.assertExactly(helper, Items.CHARCOAL, 0, "charcoal from the campfire (storage room)");
			int beef = withCampfires(helper, Items.BEEF);
			helper.assertTrue(beef == 2, "the campfire's beef (storage room): " + beef + " of 2 (" + (beef < 2 ? "lost" : "doubled") + ")");
			helper.succeed();
		});
	}

	/** A shulker box holding 5 diamonds, the crate in a storage room: one box, and exactly 5 diamonds (in it or loose, not both). */
	//$ gametest_batch AREA '"qaE1114e2StoreShulker"'
	@GameTest(template = AREA, batch = "qaE1114e2StoreShulker")
	public void aShulkerBoxOnACrateAmongContainersKeepsItsDiamondsOnce(GameTestHelper helper) {
		MoverGameTests.Setup s = QaMoverCrateHolds1114cGameTests.crateInside(helper, "qae2storeshulker");
		QaMoverCrateHolds1114cGameTests.replaceChest(helper, Blocks.SHULKER_BOX.defaultBlockState(),
			be -> ((ShulkerBoxBlockEntity) be).setItem(0, new ItemStack(Items.DIAMOND, 5)));
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(everywhere(helper, Items.DIAMOND) == 5, "setup: " + everywhere(helper, Items.DIAMOND) + " diamonds");
			dropInTheStoreRoom(helper, s);
			QaMoverCrateEdges1114bGameTests.assertExactly(helper, Items.SHULKER_BOX, 1, "the shulker box (storage room)");
			int now = everywhere(helper, Items.DIAMOND);
			helper.assertTrue(now == 5, "the shulker box's diamonds (storage room): " + now + " of 5, in the box's item or loose ("
				+ (now < 5 ? "lost" : "doubled") + ")");
			helper.succeed();
		});
	}

	/** A chiseled bookshelf holding 3 books, the crate in a storage room: the bookshelf itself and its 3 books. */
	//$ gametest_batch AREA '"qaE1114e2StoreShelf"'
	@GameTest(template = AREA, batch = "qaE1114e2StoreShelf")
	public void aChiseledBookshelfOnACrateAmongContainersIsDroppedWithItsBooks(GameTestHelper helper) {
		MoverGameTests.Setup s = QaMoverCrateHolds1114cGameTests.crateInside(helper, "qae2storeshelf");
		QaMoverCrateHolds1114cGameTests.replaceChest(helper, Blocks.CHISELED_BOOKSHELF.defaultBlockState(), be -> {
			ChiseledBookShelfBlockEntity shelf = (ChiseledBookShelfBlockEntity) be;
			for (int i = 0; i < 3; i++) {
				shelf.setItem(i, new ItemStack(Items.BOOK));
			}
		});
		helper.runAfterDelay(2, () -> {
			QaMoverCrateEdges1114bGameTests.assertExactly(helper, Items.BOOK, 3, "setup");
			dropInTheStoreRoom(helper, s);
			QaMoverCrateEdges1114bGameTests.assertExactly(helper, Items.CHISELED_BOOKSHELF, 1, "the chiseled bookshelf (storage room)");
			QaMoverCrateEdges1114bGameTests.assertExactly(helper, Items.BOOK, 3, "the chiseled bookshelf's books (storage room)");
			helper.succeed();
		});
	}

	/** Ice (no packed data, B1253), the crate in a storage room: one ice item, and no water left round the crate. */
	//$ gametest_batch AREA '"qaE1114e2StoreIce"'
	@GameTest(template = AREA, batch = "qaE1114e2StoreIce")
	public void iceOnACrateAmongContainersIsDroppedAsIceLeavingNoWater(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = QaMoverCrateHolds1114cGameTests.crateInside(helper, "qae2storeice");
		BlockPos at = helper.absolutePos(QaMoverCrateHolds1114cGameTests.CHEST);
		BlockEntity old = level.getBlockEntity(at);
		net.minecraft.world.Clearable.tryClear(old);
		level.setBlock(at, Blocks.ICE.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
		helper.runAfterDelay(2, () -> {
			dropInTheStoreRoom(helper, s);
			QaMoverCrateEdges1114bGameTests.assertExactly(helper, Items.ICE, 1, "the ice (storage room)");
			BlockPos crate = helper.absolutePos(QaMoverCrateEdges1114bGameTests.CRATE);
			int water = 0;
			for (BlockPos p : BlockPos.betweenClosed(crate.offset(-3, -1, -3), crate.offset(3, 17, 3))) {
				if (level.getFluidState(p).is(Fluids.WATER) || level.getFluidState(p).is(Fluids.FLOWING_WATER)) {
					water++;
				}
			}
			helper.assertTrue(water == 0, "the ice left " + water + " water round the crate (B1253: worked out without placing it)");
			helper.succeed();
		});
	}
}
