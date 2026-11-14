package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.move.MoveSite;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * QA (2026-11-14, verifying B1236's fix), written from the bug's expected behavior: a block packed onto a spot where a
 * Mover's Crate stands is dropped as the packed block's own item with whatever it holds, even when vanilla drops it only
 * to Silk Touch; a plain block borrowed round a walled-in crate is put back as it stood. QaMoverCrateHolds1114c* cover a
 * chiseled bookshelf, lectern, jukebox, pot, furnace and sign; QaMoverCrateBorrow1114c3 a door, ice with a torch and water
 * round the crate. These add other Silk-Touch-only blocks with a block entity on the crate (a bee nest, a sculk sensor, a
 * sculk catalyst: nothing to a bare hand), and a chiseled bookshelf with six books (each once) with no room above but air
 * beside, on a walled-in crate whose borrowed block is an upside-down stair, and on one whose borrowed block is glass
 * (itself Silk-Touch-only: it must stand again, not drop). Plain blocks with no block entity (glass, a bookshelf, ice, an
 * ender chest) are QaMoverCrateVerify1114fBugTests (unregistered: they vanish, as B1129 left a plain wall block out). Same house and Mover as MoverCrateNoRoomB1234GameTests.
 */
public class QaMoverCrateVerify1114fGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos SPOT = new BlockPos(4, 3, 4);

	/** The chest at the spot that lands on his crate emptied and replaced by {@code state} (a block with no block entity). */
	static void place(GameTestHelper helper, BlockState state) {
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(SPOT);
		Clearable.tryClear(level.getBlockEntity(at));
		level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		level.setBlock(at, state, Block.UPDATE_CLIENTS);
		helper.assertTrue(level.getBlockState(at).is(state.getBlock()), "setup: no " + state.getBlock() + " at the spot");
	}

	/** {@code block} placed on the crate's spot, the move worked to its end (after {@code between} once the house is down). */
	static MoveSite run(GameTestHelper helper, MoverGameTests.Setup s, Runnable between) {
		ServerLevel level = helper.getLevel();
		MoveSite site = MoverCrateNoRoomB1234GameTests.work(level, s, x -> x.stage() != MoveSite.Stage.DISMANTLE);
		helper.assertTrue(site.stage().rebuilding(), "setup: it didn't get to putting it up: " + site.stage());
		between.run();
		return MoverCrateNoRoomB1234GameTests.work(level, s, x -> false);
	}

	/** The block's own item once, and {@code notThese} (what a bare hand would drop instead) none. */
	static void assertOwnItem(GameTestHelper helper, Item own, String what, Item... notThese) {
		MoverCrateNoRoomB1234GameTests.assertExactly(helper, own, 1, what);
		for (Item other : notThese) {
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, other, 0, what + " broke into " + other + " instead of dropping itself");
		}
	}

	static void assertNoWaterNear(GameTestHelper helper, String when) {
		ServerLevel level = helper.getLevel();
		BlockPos crate = MoverCrateBorrowHelpers.crate(helper);
		for (BlockPos p : BlockPos.betweenClosed(crate.offset(-3, -1, -3), crate.offset(3, 17, 3))) {
			helper.assertTrue(level.getFluidState(p).isEmpty(), when + ": water at " + helper.relativePos(p.immutable()) + " (the ice melted as a bare hand breaks it)");
		}
	}

	/** A Silk-Touch-only block with a block entity on his crate (room above): the block's own item lies there, once. */
	private static void assertDroppedItself(GameTestHelper helper, String name, BlockState state, Item own) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, name);
		MoverCrateNoRoomB1234GameTests.replaceChest(helper, state, be -> { });
		helper.runAfterDelay(2, () -> {
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, own, 0, "setup");
			MoveSite site = run(helper, s, () -> { });
			MoverCrateNoRoomB1234GameTests.assertEnded(helper, level, s, site, 1, state.getBlock() + " on his crate");
			assertOwnItem(helper, own, "the " + state.getBlock().getName().getString());
			helper.assertTrue(QaMoverCrateHolds1114cGameTests.standing(helper, state.getBlock()) == 0, "a " + state.getBlock() + " was left standing");
			helper.succeed();
		});
	}

	/** A bee nest full of honey (vanilla drops it only to Silk Touch, else nothing): the nest itself lies there. */
	//$ gametest_batch AREA '"qaVerify1114fBeeNest"'
	@GameTest(template = AREA, batch = "qaVerify1114fBeeNest")
	public void aBeeNestOnHisCrateIsDroppedItself(GameTestHelper helper) {
		assertDroppedItself(helper, "qaverbeenest1114f", Blocks.BEE_NEST.defaultBlockState().setValue(BeehiveBlock.HONEY_LEVEL, 5), Items.BEE_NEST);
	}

	/** A sculk sensor (vanilla drops it only to Silk Touch, else experience): the sensor itself lies there. */
	//$ gametest_batch AREA '"qaVerify1114fSculkSensor"'
	@GameTest(template = AREA, batch = "qaVerify1114fSculkSensor")
	public void aSculkSensorOnHisCrateIsDroppedItself(GameTestHelper helper) {
		assertDroppedItself(helper, "qaversensor1114f", Blocks.SCULK_SENSOR.defaultBlockState(), Items.SCULK_SENSOR);
	}

	/** A sculk catalyst (vanilla drops it only to Silk Touch, else experience): the catalyst itself lies there. */
	//$ gametest_batch AREA '"qaVerify1114fSculkCatalyst"'
	@GameTest(template = AREA, batch = "qaVerify1114fSculkCatalyst")
	public void aSculkCatalystOnHisCrateIsDroppedItself(GameTestHelper helper) {
		assertDroppedItself(helper, "qavercatalyst1114f", Blocks.SCULK_CATALYST.defaultBlockState(), Items.SCULK_CATALYST);
	}

	/** A chiseled bookshelf holding six different books, put where the chest stood. */
	static void sixBooks(GameTestHelper helper) {
		MoverCrateNoRoomB1234GameTests.replaceChest(helper, Blocks.CHISELED_BOOKSHELF.defaultBlockState(), be -> {
			for (int i = 0; i < BOOKS.length; i++) {
				((ChiseledBookShelfBlockEntity) be).setItem(i, new ItemStack(BOOKS[i]));
			}
		});
	}

	static final Item[] BOOKS = {Items.BOOK, Items.ENCHANTED_BOOK, Items.WRITABLE_BOOK, Items.WRITTEN_BOOK, Items.KNOWLEDGE_BOOK, Items.BOOK};

	static void assertSixBooksAndShelf(GameTestHelper helper, String when) {
		MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.BOOK, 2, when + ": the shelf's two books");
		for (int i = 1; i < 5; i++) {
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, BOOKS[i], 1, when + ": the shelf's " + BOOKS[i]);
		}
		assertOwnItem(helper, Items.CHISELED_BOOKSHELF, when + ": the chiseled bookshelf");
		helper.assertTrue(QaMoverCrateHolds1114cGameTests.standing(helper, Blocks.CHISELED_BOOKSHELF) == 0, when + ": a chiseled bookshelf was left standing");
	}

	/**
	 * A chiseled bookshelf with six books on his crate, the 16 blocks over it built up but air beside it: the shelf and
	 * each book lie there exactly once.
	 */
	//$ gametest_batch AREA '"qaVerify1114fShelfAirBeside"'
	@GameTest(template = AREA, batch = "qaVerify1114fShelfAirBeside")
	public void aChiseledBookshelfWithNoRoomAboveButAirBesideDropsItselfAndEachBookOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qavershelfbeside1114f");
		sixBooks(helper);
		helper.runAfterDelay(2, () -> {
			MoveSite site = run(helper, s, () -> MoverCrateNoRoomB1234GameTests.column(helper, 16));
			MoverCrateNoRoomB1234GameTests.assertEnded(helper, level, s, site, 1, "a chiseled bookshelf, no room above");
			assertSixBooksAndShelf(helper, "no room above, air beside");
			helper.succeed();
		});
	}

	/**
	 * A chiseled bookshelf with six books on his crate, walled in all round with stone except an upside-down oak stair
	 * facing west right above the crate: the shelf and each book lie there once; the stair (or whichever plain block was borrowed) stands exactly as it stood, and
	 * no stair or stone item appears.
	 */
	//$ gametest_batch AREA '"qaVerify1114fShelfBorrowStair"'
	@GameTest(template = AREA, batch = "qaVerify1114fShelfBorrowStair")
	public void aChiseledShelfOnAWalledInCrateDropsItselfAndTheStairStandsAsItStood(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qaverpane1114f");
		sixBooks(helper);
		BlockPos crate = MoverCrateBorrowHelpers.crate(helper);
		BlockState stair = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST).setValue(StairBlock.HALF, Half.TOP);
		int[] cobble = new int[2];
		helper.runAfterDelay(2, () -> {
			cobble[0] = QaMoverCrateDrop1114eGameTests.count(helper, Items.COBBLESTONE); // the house's own
			MoveSite site = run(helper, s, () -> {
				level.setBlock(crate.above(), stair, Block.UPDATE_CLIENTS);
				QaMoverCrateBorrow1114c3GameTests.wallIn(helper, Blocks.STONE.defaultBlockState());
				for (io.github.jcondedata.aliveworkplace.move.MovePlan.Placed p : s.before().placed(s.to(), net.minecraft.world.level.block.Rotation.NONE)) {
					if (level.getBlockState(p.pos()).is(Blocks.STONE)) {
						cobble[1]++; // stone in the house's own spots: cleared as in the way when it goes up there
					}
				}
			});
			MoverCrateNoRoomB1234GameTests.assertEnded(helper, level, s, site, 1, "a chiseled bookshelf on his walled-in crate");
			assertSixBooksAndShelf(helper, "walled in, a stair borrowed");
			helper.assertTrue(level.getBlockState(crate.above()) == stair, "the stair over his crate is now " + level.getBlockState(crate.above()) + ", not " + stair);
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.OAK_STAIRS, 0, "a stair item appeared (the borrowed stair doubled)");
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.STONE, 0, "a stone item appeared (a borrowed stone doubled)");
			int now = QaMoverCrateDrop1114eGameTests.count(helper, Items.COBBLESTONE);
			helper.assertTrue(now <= cobble[0] + cobble[1], now + " cobblestone, more than the house's " + cobble[0] + " and the " + cobble[1]
				+ " stone the house's own spots cleared (a borrowed stone was broken)");
			helper.succeed();
		});
	}

	/**
	 * A chiseled bookshelf with six books on his crate walled in all round with glass (no air within 2, nor 16 up; glass
	 * too drops only to Silk Touch): the shelf and each book lie there once, and every glass block round the crate stands again: none missing, no glass item.
	 */
	//$ gametest_batch AREA '"qaVerify1114fShelfBorrowGlass"'
	@GameTest(template = AREA, batch = "qaVerify1114fShelfBorrowGlass")
	public void aChiseledShelfOnACrateWalledInWithGlassDropsItselfAndTheGlassStands(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qavershelfglass1114f");
		sixBooks(helper);
		BlockPos crate = MoverCrateBorrowHelpers.crate(helper);
		java.util.Set<BlockPos> glass = new java.util.HashSet<>();
		helper.runAfterDelay(2, () -> {
			MoveSite site = run(helper, s, () -> {
				QaMoverCrateBorrow1114c3GameTests.wallIn(helper, Blocks.GLASS.defaultBlockState());
				glass.addAll(glassNear(level, crate));
				for (net.minecraft.world.level.block.Rotation r : java.util.List.of(net.minecraft.world.level.block.Rotation.NONE)) {
					for (io.github.jcondedata.aliveworkplace.move.MovePlan.Placed p : s.before().placed(s.to(), r)) {
						glass.remove(p.pos()); // the house's own spots: the move may build there
					}
				}
			});
			helper.assertTrue(glass.size() > 16, "setup: only " + glass.size() + " glass round the crate");
			MoverCrateNoRoomB1234GameTests.assertEnded(helper, level, s, site, 1, "a chiseled bookshelf on a crate walled in with glass");
			assertSixBooksAndShelf(helper, "walled in with glass");
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.GLASS, 0, "a glass item appeared (the borrowed glass dropped)");
			java.util.List<BlockPos> gone = glass.stream().filter(p -> !level.getBlockState(p).is(Blocks.GLASS)).map(helper::relativePos).toList();
			helper.assertTrue(gone.isEmpty(), gone.size() + " of the player's " + glass.size() + " glass round the crate no longer stand (the borrowed glass not put back): " + gone);
			helper.succeed();
		});
	}

	private static java.util.Set<BlockPos> glassNear(ServerLevel level, BlockPos crate) {
		java.util.Set<BlockPos> out = new java.util.HashSet<>();
		for (BlockPos p : BlockPos.betweenClosed(crate.offset(-2, -2, -2), crate.offset(2, 16, 2))) {
			if (level.getBlockState(p).is(Blocks.GLASS)) {
				out.add(p.immutable());
			}
		}
		return out;
	}

	/** His crate, as QaMoverCrateBorrow1114c3GameTests finds it. */
	static final class MoverCrateBorrowHelpers {
		static BlockPos crate(GameTestHelper helper) {
			return QaMoverCrateBorrow1114c3GameTests.crate(helper);
		}
	}
}
