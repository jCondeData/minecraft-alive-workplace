package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import io.github.jcondedata.aliveworkplace.move.MoverWork;
import io.github.jcondedata.aliveworkplace.move.PendingMoves;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * B1234-B1238 (lane a): the cases lane c's QaMoverCrateHolds1114c tests leave out: a decorated pot and a chiseled
 * bookshelf with no room above the Mover's Crate, and a crate walled in all round (no free spot within 2 at all), where
 * the dropped block borrows a plain block's spot for a moment and puts it back. Set up as QaMoverCrateDrop1114eGameTests:
 * MoverGameTests' house with his only crate inside, moved two blocks south-east so the chest's spot lands on his crate.
 */
public class MoverCrateNoRoomB1234GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos SPOT = new BlockPos(4, 3, 4);

	/** MoverGameTests' house, his only crate inside, the chest's spot landing on it (the chest kept with its items). */
	static MoverGameTests.Setup crateInside(GameTestHelper helper, String name) {
		Leftovers.clear(helper);
		return QaMoverCrateDrop1114eGameTests.onHisCrate(helper, name, null, null);
	}

	/** The chest at the spot that lands on his crate replaced by {@code state}, its block entity filled by {@code fill}. */
	static void replaceChest(GameTestHelper helper, BlockState state, Consumer<BlockEntity> fill) {
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(SPOT);
		Clearable.tryClear(level.getBlockEntity(at));
		level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		level.setBlock(at, state, Block.UPDATE_CLIENTS);
		BlockEntity be = level.getBlockEntity(at);
		helper.assertTrue(level.getBlockState(at).is(state.getBlock()) && be != null, "setup: no " + state.getBlock() + " at the spot");
		fill.accept(be);
		be.setChanged();
	}

	/** A chiseled bookshelf with one book where the chest stood. */
	static void shelf(GameTestHelper helper) {
		replaceChest(helper, Blocks.CHISELED_BOOKSHELF.defaultBlockState(), be -> ((ChiseledBookShelfBlockEntity) be).setItem(0, new ItemStack(Items.BOOK)));
	}

	static MoveSite work(ServerLevel level, MoverGameTests.Setup s, Predicate<MoveSite> stop) {
		return MoverGameTests.work(level, s, stop);
	}

	/** Stone in the {@code high} blocks right above his crate (where there's air). */
	static void column(GameTestHelper helper, int high) {
		BlockPos crate = helper.absolutePos(QaMoverCrateDrop1114eGameTests.OWN_CRATE);
		for (int dy = 1; dy <= high; dy++) {
			if (helper.getLevel().getBlockState(crate.above(dy)).isAir()) {
				helper.getLevel().setBlock(crate.above(dy), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
	}

	/** The move ends whole: done, {@code skipped} left out, his crate where it stood and still his, the move put away. */
	static void assertEnded(GameTestHelper helper, ServerLevel level, MoverGameTests.Setup s, MoveSite site, int skipped, String when) {
		helper.assertTrue(site.stage() == MoveSite.Stage.DONE && site.waiting() == null && !site.canceled(),
			when + ": the move ended " + site.stage() + ", waiting " + site.waiting() + ", canceled " + site.canceled());
		helper.assertTrue(site.skipped() == skipped, when + ": skipped " + site.skipped() + ", not " + skipped);
		helper.assertTrue(level.getBlockState(helper.absolutePos(QaMoverCrateDrop1114eGameTests.OWN_CRATE)).is(ModBlocks.MOVERS_CRATE),
			when + ": his crate isn't where it stood");
		helper.assertTrue(MoverWork.hasCrate(level, s.mover()), when + ": the Mover lost his crate (workstation)");
		helper.assertTrue(PendingMoves.get(level).forBuilding(s.from()).isEmpty() && MoveSites.get(level).forBuilding(s.from()).isEmpty(),
			when + ": the move wasn't put away");
	}

	/** Exactly {@code n} of {@code item} round the test (in containers, loose or carried). */
	static void assertExactly(GameTestHelper helper, Item item, int n, String what) {
		int now = QaMoverCrateDrop1114eGameTests.count(helper, item);
		helper.assertTrue(now == n, what + ": " + now + " of " + item + ", not " + n + " (B1172: nothing packed is ever destroyed)");
	}

	/** A decorated pot holding 10 gold ingots, the 16 blocks over his crate built up: the pot and its gold are dropped. */
	//$ gametest_batch AREA '"moverCrateNoRoomPot"'
	@GameTest(template = AREA, batch = "moverCrateNoRoomPot")
	public void aDecoratedPotOnACrateWithNoRoomAboveKeepsWhatItHolds(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = crateInside(helper, "cratenoroompot");
		replaceChest(helper, Blocks.DECORATED_POT.defaultBlockState(), be -> ((DecoratedPotBlockEntity) be).setTheItem(new ItemStack(Items.GOLD_INGOT, 10)));
		helper.runAfterDelay(2, () -> {
			assertExactly(helper, Items.GOLD_INGOT, 10, "setup: the pot's gold");
			work(level, s, x -> x.stage() != MoveSite.Stage.DISMANTLE);
			column(helper, 16);
			MoveSite site = work(level, s, x -> false);
			assertEnded(helper, level, s, site, 1, "no room over the crate");
			assertExactly(helper, Items.DECORATED_POT, 1, "the pot");
			assertExactly(helper, Items.GOLD_INGOT, 10, "the pot's gold (16 blocks over the crate all built up)");
			helper.succeed();
		});
	}

	/** A chiseled bookshelf with its book, the 16 blocks over his crate built up: the shelf itself and its book are dropped. */
	//$ gametest_batch AREA '"moverCrateNoRoomShelf"'
	@GameTest(template = AREA, batch = "moverCrateNoRoomShelf")
	public void aChiseledBookshelfOnACrateWithNoRoomAboveIsDroppedWithItsBook(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = crateInside(helper, "cratenoroomshelf");
		shelf(helper);
		helper.runAfterDelay(2, () -> {
			work(level, s, x -> x.stage() != MoveSite.Stage.DISMANTLE);
			column(helper, 16);
			MoveSite site = work(level, s, x -> false);
			assertEnded(helper, level, s, site, 1, "no room over the crate");
			assertExactly(helper, Items.CHISELED_BOOKSHELF, 1, "the chiseled bookshelf");
			assertExactly(helper, Items.BOOK, 1, "the shelf's book");
			helper.succeed();
		});
	}

	/**
	 * His crate walled in all round once the house is down (every block within 2 of it stone, none free): the lectern
	 * borrows a plain block's spot for a moment, so it and its book are dropped, and that block stands again after.
	 */
	//$ gametest_batch AREA '"moverCrateWalledIn"'
	@GameTest(template = AREA, batch = "moverCrateWalledIn")
	public void aLecternOnACrateWalledInAllRoundKeepsItsBook(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = crateInside(helper, "cratewalledin");
		replaceChest(helper, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.HAS_BOOK, true),
			be -> ((LecternBlockEntity) be).setBook(new ItemStack(Items.WRITABLE_BOOK)));
		BlockPos crate = helper.absolutePos(QaMoverCrateDrop1114eGameTests.OWN_CRATE);
		helper.runAfterDelay(2, () -> {
			work(level, s, x -> x.stage() != MoveSite.Stage.DISMANTLE);
			column(helper, 16);
			for (BlockPos p : BlockPos.betweenClosed(crate.offset(-2, -2, -2), crate.offset(2, 2, 2))) {
				if (level.getBlockState(p).isAir()) {
					level.setBlock(p, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
			MoveSite site = work(level, s, x -> false);
			assertEnded(helper, level, s, site, 1, "his crate walled in");
			assertExactly(helper, Items.LECTERN, 1, "the lectern");
			assertExactly(helper, Items.WRITABLE_BOOK, 1, "the lectern's book (his crate walled in all round)");
			helper.assertTrue(!level.getBlockState(crate.above()).isAir(), "the block over his crate wasn't put back: " + level.getBlockState(crate.above()));
			helper.succeed();
		});
	}
}
