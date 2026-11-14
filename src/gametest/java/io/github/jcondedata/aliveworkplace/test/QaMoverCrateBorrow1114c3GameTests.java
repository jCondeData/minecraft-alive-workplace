package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoverWork;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.Fluids;

/**
 * QA (lane c, 2026-11-14, third pass) for B1234-B1236 and B1238, written from the bugs' expected behaviour (B1172: nothing
 * packed is ever destroyed; a block on a Mover's Crate is dropped with everything it holds) and the fix's promise for a
 * crate with no free spot near it: a plain block above or round the crate is borrowed for a moment and "put back as it
 * stood". These attack the borrow from the player's side: the borrowed block is the lower half of a door, an ice block
 * with a torch hanging on it, water all round the crate; and a barrel of 27 different stacks through save and reload
 * while walled in. Each checks the player's build round the crate is exactly as before and nothing is lost or doubled.
 * Same house and Mover as MoverCrateNoRoomB1234GameTests (the chest's spot lands on his crate).
 */
public class QaMoverCrateBorrow1114c3GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	static BlockPos crate(GameTestHelper helper) {
		return helper.absolutePos(QaMoverCrateDrop1114eGameTests.OWN_CRATE);
	}

	/** Every air block within 2 of his crate (and the 16 over it) filled with {@code with}: no free spot anywhere near. */
	static void wallIn(GameTestHelper helper, BlockState with) {
		ServerLevel level = helper.getLevel();
		BlockPos crate = crate(helper);
		for (int dy = 1; dy <= 16; dy++) {
			if (level.getBlockState(crate.above(dy)).isAir()) {
				level.setBlock(crate.above(dy), with, Block.UPDATE_CLIENTS);
			}
		}
		for (BlockPos p : BlockPos.betweenClosed(crate.offset(-2, -2, -2), crate.offset(2, 2, 2))) {
			if (level.getBlockState(p).isAir()) {
				level.setBlock(p, with, Block.UPDATE_CLIENTS);
			}
		}
	}

	/** A lectern holding a writable book where the chest stood (it lands on his crate). */
	static void lectern(GameTestHelper helper) {
		MoverCrateNoRoomB1234GameTests.replaceChest(helper, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.HAS_BOOK, true),
			be -> ((LecternBlockEntity) be).setBook(new ItemStack(Items.WRITABLE_BOOK)));
	}

	/** The lectern and its book lie there once each, and no lectern was left standing anywhere. */
	static void assertLecternDropped(GameTestHelper helper, String when) {
		MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.LECTERN, 1, when + ": the lectern");
		MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.WRITABLE_BOOK, 1, when + ": the lectern's book");
		int standing = QaMoverCrateHolds1114cGameTests.standing(helper, Blocks.LECTERN);
		helper.assertTrue(standing == 0, when + ": " + standing + " lectern(s) left standing where the player never put one");
	}

	/**
	 * The player's spruce door stands right over his crate, everything else round it built up: the lower half is the
	 * block borrowed. After the drop both halves stand as they stood (open/closed, facing), and no door item appears.
	 */
	//$ gametest_batch AREA '"qaC3BorrowDoor"'
	@GameTest(template = AREA, batch = "qaC3BorrowDoor")
	public void aDoorOverHisWalledInCrateStandsWholeAfterTheDrop(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qac3door");
		lectern(helper);
		BlockPos crate = crate(helper);
		BlockState door = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST).setValue(DoorBlock.OPEN, true);
		BlockState lower = door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
		BlockState upper = door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
		helper.runAfterDelay(2, () -> {
			MoverCrateNoRoomB1234GameTests.work(level, s, x -> x.stage() != MoveSite.Stage.DISMANTLE);
			level.setBlock(crate.above(), lower, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			level.setBlock(crate.above(2), upper, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			wallIn(helper, Blocks.STONE.defaultBlockState());
			MoveSite site = MoverCrateNoRoomB1234GameTests.work(level, s, x -> false);
			MoverCrateNoRoomB1234GameTests.assertEnded(helper, level, s, site, 1, "a door over his walled-in crate");
			assertLecternDropped(helper, "a door over his walled-in crate");
			helper.assertTrue(level.getBlockState(crate.above()) == lower,
				"the player's door lost its lower half (the borrowed block): " + level.getBlockState(crate.above()));
			helper.assertTrue(level.getBlockState(crate.above(2)) == upper,
				"the player's door lost its upper half: " + level.getBlockState(crate.above(2)));
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.SPRUCE_DOOR, 0, "the borrowed door was doubled (a door item dropped while it still stands)");
			helper.succeed();
		});
	}

	/**
	 * An ice block over his crate with a torch on its side, everything round built up: the ice is borrowed. Afterwards
	 * the ice stands (no water left where it was, as breaking ice by hand would leave), the torch still hangs on it,
	 * and neither an ice nor a torch item appears.
	 */
	//$ gametest_batch AREA '"qaC3BorrowIce"'
	@GameTest(template = AREA, batch = "qaC3BorrowIce")
	public void anIceBlockWithATorchOnItOverHisCrateIsPutBackAsItStood(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qac3ice");
		lectern(helper);
		BlockPos crate = crate(helper);
		BlockPos torch = crate.above().east();
		BlockState torchState = Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.EAST);
		helper.runAfterDelay(2, () -> {
			MoverCrateNoRoomB1234GameTests.work(level, s, x -> x.stage() != MoveSite.Stage.DISMANTLE);
			level.setBlock(crate.above(), Blocks.ICE.defaultBlockState(), Block.UPDATE_CLIENTS);
			level.setBlock(torch, torchState, Block.UPDATE_CLIENTS);
			wallIn(helper, Blocks.STONE.defaultBlockState());
			MoveSite site = MoverCrateNoRoomB1234GameTests.work(level, s, x -> false);
			MoverCrateNoRoomB1234GameTests.assertEnded(helper, level, s, site, 1, "ice over his walled-in crate");
			assertLecternDropped(helper, "ice over his walled-in crate");
			helper.assertTrue(level.getBlockState(crate.above()).is(Blocks.ICE), "the player's ice over the crate is now " + level.getBlockState(crate.above()));
			for (BlockPos p : BlockPos.betweenClosed(crate.offset(-2, -2, -2), crate.offset(2, 3, 2))) {
				helper.assertTrue(level.getFluidState(p).isEmpty(), "water appeared at " + p.immutable() + " (the borrowed ice melted)");
			}
			helper.assertTrue(level.getBlockState(torch) == torchState, "the torch on the borrowed ice fell off: " + level.getBlockState(torch));
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.TORCH, 0, "the torch on the ice popped off as an item");
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.ICE, 0, "the borrowed ice was doubled (an ice item dropped while it still stands)");
			helper.succeed();
		});
	}

	/**
	 * His crate under water: every block within 2 and the 16 over it water, no air anywhere near. The lectern and its
	 * book still come out (they float), and the water over the crate is a still water source again, not a lectern or air.
	 */
	//$ gametest_batch AREA '"qaC3BorrowWater"'
	@GameTest(template = AREA, batch = "qaC3BorrowWater")
	public void aLecternOnACrateUnderWaterKeepsItsBookAndTheWaterStays(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qac3water");
		lectern(helper);
		BlockPos crate = crate(helper);
		helper.runAfterDelay(2, () -> {
			MoverCrateNoRoomB1234GameTests.work(level, s, x -> x.stage() != MoveSite.Stage.DISMANTLE);
			wallIn(helper, Blocks.WATER.defaultBlockState());
			MoveSite site = MoverCrateNoRoomB1234GameTests.work(level, s, x -> false);
			MoverCrateNoRoomB1234GameTests.assertEnded(helper, level, s, site, 1, "his crate under water");
			assertLecternDropped(helper, "his crate under water");
			helper.assertTrue(level.getFluidState(crate.above()).is(Fluids.WATER) && level.getFluidState(crate.above()).isSource(),
				"the water over his crate is now " + level.getBlockState(crate.above()));
			helper.succeed();
		});
	}

	private static final Item[] STACKS = {Items.APPLE, Items.ARROW, Items.BONE, Items.BOOK, Items.BOWL, Items.BRICK, Items.CARROT, Items.CLAY_BALL,
		Items.COPPER_INGOT, Items.EGG, Items.FEATHER, Items.FLINT, Items.GLOWSTONE_DUST, Items.GOLD_NUGGET, Items.GUNPOWDER, Items.HONEYCOMB,
		Items.INK_SAC, Items.IRON_NUGGET, Items.LAPIS_LAZULI, Items.LEATHER, Items.MELON_SLICE, Items.PAPER, Items.POTATO, Items.QUARTZ,
		Items.REDSTONE, Items.SLIME_BALL, Items.STRING};

	/**
	 * A full barrel (27 different stacks) on his walled-in crate, the move saved and read back every 3 steps while it
	 * goes up: every stack is there, exactly as many as before, the barrel once, and the borrowed stone not doubled.
	 */
	//$ gametest_batch AREA '"qaC3BorrowBarrelReload"'
	@GameTest(template = AREA, batch = "qaC3BorrowBarrelReload")
	public void aFullBarrelOnAWalledInCrateKeepsEveryStackThroughReloads(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qac3barrel");
		MoverCrateNoRoomB1234GameTests.replaceChest(helper, Blocks.BARREL.defaultBlockState(), be -> {
			for (int i = 0; i < STACKS.length; i++) {
				((Container) be).setItem(i, new ItemStack(STACKS[i], i % 16 + 1));
			}
		});
		BlockPos crate = crate(helper);
		helper.runAfterDelay(2, () -> {
			for (int i = 0; i < STACKS.length; i++) {
				MoverCrateNoRoomB1234GameTests.assertExactly(helper, STACKS[i], i % 16 + 1, "setup: slot " + i);
			}
			MoveSite site = MoverCrateNoRoomB1234GameTests.work(level, s, x -> x.stage() != MoveSite.Stage.DISMANTLE);
			helper.assertTrue(site.stage().rebuilding(), "setup: it didn't get to putting it up: " + site.stage());
			wallIn(helper, Blocks.STONE.defaultBlockState());
			int steps = 0;
			int reloads = 0;
			while (site.stage() != MoveSite.Stage.DONE && steps++ < MoverGameTests.MAX_STEPS) {
				MoverWork.work(level, s.mover(), site);
				if (steps % 3 == 0 && site.stage() != MoveSite.Stage.DONE) {
					site = MoverGameTests.reload(level, s);
					reloads++;
				}
			}
			helper.assertTrue(reloads >= 3, "only " + reloads + " reloads");
			MoverCrateNoRoomB1234GameTests.assertEnded(helper, level, s, site, 1, "a full barrel on his walled-in crate, reloaded");
			for (int i = 0; i < STACKS.length; i++) {
				MoverCrateNoRoomB1234GameTests.assertExactly(helper, STACKS[i], i % 16 + 1, "the barrel's slot " + i);
			}
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.BARREL, 1, "the barrel");
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.STONE, 0, "the borrowed stone was doubled");
			helper.assertTrue(level.getBlockState(crate.above()).is(Blocks.STONE), "the stone over his crate (borrowed) is now " + level.getBlockState(crate.above()));
			helper.assertTrue(QaMoverCrateHolds1114cGameTests.standing(helper, Blocks.BARREL) == 1, "barrels standing: "
				+ QaMoverCrateHolds1114cGameTests.standing(helper, Blocks.BARREL) + ", not just the house's own");
			helper.succeed();
		});
	}
}
