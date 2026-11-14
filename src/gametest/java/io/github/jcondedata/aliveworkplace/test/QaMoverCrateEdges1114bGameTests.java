package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane b, 2026-11-14, second run) for B1234, B1235, B1236 and B1238, written from the bug reports: when a Mover's
 * Crate stands where a packed block goes up, nothing packed is ever destroyed, however the crate is roofed or walled in;
 * the block and whatever it holds are dropped. The fix's text adds: with no free spot above or round the crate, a plain
 * block nearby is borrowed for a moment and put back as it stood; the borrowed spot is never a container. These cover
 * the cases the earlier classes don't: the crate walled in all round and above (every free spot within 2 and the
 * column 16 up filled, kept filled until the drop), so a block is borrowed (a waterlogged top-half stair right on the
 * crate: back exactly as it stood, nothing dropped from it), and the crate in a storage room where every block round it
 * holds things (barrels): their contents are left alone. Same house, Mover and spot as QaMoverCrateHolds1114cGameTests.
 */
public class QaMoverCrateEdges1114bGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	static final BlockPos CRATE = QaMoverCrateHolds1114cGameTests.OWN_CRATE;
	/** A plain block with a shape and properties of its own, on the crate: the one block a walled-in crate can borrow first. */
	static final BlockState STAIR = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST)
		.setValue(StairBlock.HALF, Half.TOP).setValue(StairBlock.WATERLOGGED, true);

	// ---- helpers ---------------------------------------------------------------------------------------------------

	/** Every spot within 2 of the crate (a 5 x 5 x 5 box, the crate left out). */
	static List<BlockPos> round(GameTestHelper helper) {
		List<BlockPos> out = new ArrayList<>();
		BlockPos at = helper.absolutePos(CRATE);
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-2, -2, -2), at.offset(2, 2, 2))) {
			if (!p.equals(at)) {
				out.add(p.immutable());
			}
		}
		return out;
	}

	/** The column over the crate, 3 to 16 up (1 and 2 up are in {@link #round}). */
	static List<BlockPos> column(GameTestHelper helper) {
		List<BlockPos> out = new ArrayList<>();
		for (int dy = 3; dy <= 16; dy++) {
			out.add(helper.absolutePos(CRATE.above(dy)));
		}
		return out;
	}

	/**
	 * Walled in: the stair on the crate and stone on every free spot round it and in the column above. Run before every
	 * step of the put-up until the crate's block is dropped, so no spot the Mover hasn't built yet is left free.
	 */
	static void wallIn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos on = helper.absolutePos(CRATE.above());
		if (!level.getBlockState(on).equals(STAIR)) {
			level.setBlock(on, STAIR, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}
		List<BlockPos> all = round(helper);
		all.addAll(column(helper));
		for (BlockPos p : all) {
			if (level.getBlockState(p).isAir() || level.getBlockState(p).canBeReplaced()) {
				level.setBlock(p, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			}
		}
	}

	/** In a storage room: a barrel holding 1 amethyst shard on every spot round the crate with no block entity; stone up the column. */
	static void barrelsRound(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (BlockPos p : round(helper)) {
			BlockState there = level.getBlockState(p);
			if (!there.hasBlockEntity() && !there.is(ModBlocks.MOVERS_CRATE)) {
				level.setBlock(p, Blocks.BARREL.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				((Container) level.getBlockEntity(p)).setItem(0, new ItemStack(Items.AMETHYST_SHARD));
			}
		}
		for (BlockPos p : column(helper)) {
			if (level.getBlockState(p).isAir() || level.getBlockState(p).canBeReplaced()) {
				level.setBlock(p, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			}
		}
	}

	/**
	 * The move run until the block on his crate is dropped, {@code keep} run before every step of the put-up until then;
	 * returns the site right after the drop (skipped 1). The drop must not come before the put-up's STRUCTURE stage.
	 */
	static MoveSite untilTheDrop(GameTestHelper helper, MoverGameTests.Setup s, Runnable keep) {
		ServerLevel level = helper.getLevel();
		MoveSite site = MoverGameTests.work(level, s, x -> x.stage() == MoveSite.Stage.STRUCTURE || x.skipped() > 0);
		helper.assertTrue(site.stage() == MoveSite.Stage.STRUCTURE && site.skipped() == 0,
			"setup: the put-up didn't get to STRUCTURE before the drop: " + site.stage() + ", skipped " + site.skipped());
		Predicate<MoveSite> dropped = x -> {
			if (x.skipped() > 0) {
				return true;
			}
			keep.run();
			return false;
		};
		site = MoverGameTests.work(level, s, dropped);
		helper.assertTrue(site.skipped() == 1, "the block on his crate was never dropped: " + site.stage() + ", skipped " + site.skipped());
		return site;
	}

	/** How many {@code item} there are round the test: in containers, on lecterns, lying loose or carried by villagers. */
	static int count(GameTestHelper helper, Item item) {
		ServerLevel level = helper.getLevel();
		int n = 0;
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(29, 29, 29)))) {
			BlockEntity be = level.getBlockEntity(pos);
			if (be instanceof Container c) {
				for (int i = 0; i < c.getContainerSize(); i++) {
					if (c.getItem(i).is(item)) {
						n += c.getItem(i).getCount();
					}
				}
			} else if (be instanceof LecternBlockEntity l && l.getBook().is(item)) {
				n += l.getBook().getCount();
			}
		}
		AABB area = new AABB(helper.absolutePos(new BlockPos(0, 1, 0))).inflate(48);
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, area)) {
			if (e.getItem().is(item)) {
				n += e.getItem().getCount();
			}
		}
		for (Villager v : level.getEntitiesOfClass(Villager.class, area)) {
			for (int i = 0; i < v.getInventory().getContainerSize(); i++) {
				if (v.getInventory().getItem(i).is(item)) {
					n += v.getInventory().getItem(i).getCount();
				}
			}
		}
		return n;
	}

	static void assertExactly(GameTestHelper helper, Item item, int n, String what) {
		int had = count(helper, item);
		helper.assertTrue(had == n, what + ": " + had + " " + item + " round the test, not " + n + (had < n ? " (lost)" : " (doubled)"));
	}

	/** The chest replaced by a barrel holding three stacks (none of them anything the house holds). */
	static void barrelOnTheCrate(GameTestHelper helper) {
		QaMoverCrateHolds1114cGameTests.replaceChest(helper, Blocks.BARREL.defaultBlockState(), be -> {
			Container barrel = (Container) be;
			barrel.setItem(0, new ItemStack(Items.GUNPOWDER, 64));
			barrel.setItem(13, new ItemStack(Items.LAPIS_LAZULI, 17));
			barrel.setItem(26, new ItemStack(Items.ENDER_PEARL, 3));
		});
	}

	static void assertBarrelsItems(GameTestHelper helper, String when) {
		assertExactly(helper, Items.GUNPOWDER, 64, when + ": the barrel's gunpowder");
		assertExactly(helper, Items.LAPIS_LAZULI, 17, when + ": the barrel's lapis");
		assertExactly(helper, Items.ENDER_PEARL, 3, when + ": the barrel's ender pearls");
	}

	// ---- walled in all round and above: a plain block borrowed ---------------------------------------------------------

	/**
	 * A barrel holding three stacks on his crate, the crate walled in all round and 16 up: every item of the barrel and
	 * the barrel itself are still there, once each, and the stair on the crate (the block borrowed) stands exactly as it
	 * stood (facing, half, waterlogged), with no stair dropped from it.
	 */
	//$ gametest_batch AREA '"qaB1238WalledBarrel1114b"'
	@GameTest(template = AREA, batch = "qaB1238WalledBarrel1114b")
	public void aBarrelOnACrateWalledInAllRoundKeepsEveryItem(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = QaMoverCrateHolds1114cGameTests.crateInside(helper, "qawalledbarrel1114b");
		barrelOnTheCrate(helper);
		helper.runAfterDelay(2, () -> {
			assertBarrelsItems(helper, "setup");
			untilTheDrop(helper, s, () -> wallIn(helper));
			BlockPos on = helper.absolutePos(CRATE.above());
			helper.assertTrue(level.getBlockState(on).equals(STAIR), "the stair on his crate isn't as it stood: " + level.getBlockState(on));
			helper.assertTrue(level.getBlockEntity(on) == null, "a block entity was left in the stair's spot: " + level.getBlockEntity(on));
			assertBarrelsItems(helper, "right after the drop");
			MoveSite site = MoverGameTests.work(level, s, x -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE && !site.canceled() && site.skipped() == 1,
				"the move ended " + site.stage() + ", canceled " + site.canceled() + ", skipped " + site.skipped());
			helper.assertTrue(level.getBlockState(helper.absolutePos(CRATE)).is(ModBlocks.MOVERS_CRATE), "his crate isn't where it stood");
			helper.assertTrue(level.getBlockState(on).equals(STAIR), "the stair on his crate isn't as it stood after the move: " + level.getBlockState(on));
			assertBarrelsItems(helper, "after the move");
			assertExactly(helper, Items.BARREL, 1, "the barrel");
			assertExactly(helper, Items.OAK_STAIRS, 0, "the borrowed stair");
			helper.succeed();
		});
	}

	/** A lectern with its book on his crate, the crate walled in all round and 16 up: the lectern and its book lie there, once each. */
	//$ gametest_batch AREA '"qaB1234WalledLectern1114b"'
	@GameTest(template = AREA, batch = "qaB1234WalledLectern1114b")
	public void aLecternOnACrateWalledInAllRoundKeepsItsBook(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = QaMoverCrateHolds1114cGameTests.crateInside(helper, "qawalledlectern1114b");
		QaMoverCrateHolds1114cGameTests.replaceChest(helper, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.HAS_BOOK, true),
			be -> ((LecternBlockEntity) be).setBook(new ItemStack(Items.WRITABLE_BOOK)));
		helper.runAfterDelay(2, () -> {
			assertExactly(helper, Items.WRITABLE_BOOK, 1, "setup");
			untilTheDrop(helper, s, () -> wallIn(helper));
			helper.assertTrue(level.getBlockState(helper.absolutePos(CRATE.above())).equals(STAIR), "the stair on his crate isn't as it stood");
			MoveSite site = MoverGameTests.work(level, s, x -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE && site.skipped() == 1, "the move ended " + site.stage() + ", skipped " + site.skipped());
			assertExactly(helper, Items.WRITABLE_BOOK, 1, "the lectern's book (crate walled in)");
			assertExactly(helper, Items.LECTERN, 1, "the lectern (crate walled in)");
			helper.assertTrue(QaMoverCrateHolds1114cGameTests.standing(helper, Blocks.LECTERN) == 0, "a lectern was left standing");
			helper.succeed();
		});
	}

	// ---- a storage room: only containers round the crate ---------------------------------------------------------------

	/**
	 * His crate in a storage room: a barrel (with 1 amethyst shard) on every spot round it, stone up the column. The
	 * barrel on his crate still keeps every item, and no barrel round it was borrowed: each still stands holding its
	 * one shard and nothing else.
	 */
	//$ gametest_batch AREA '"qaB1238StoreRoomBarrel1114b"'
	@GameTest(template = AREA, batch = "qaB1238StoreRoomBarrel1114b")
	public void aBarrelOnACrateAmongContainersLeavesTheirContentsAlone(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = QaMoverCrateHolds1114cGameTests.crateInside(helper, "qastorebarrel1114b");
		barrelOnTheCrate(helper);
		helper.runAfterDelay(2, () -> {
			untilTheDrop(helper, s, () -> barrelsRound(helper));
			int barrels = 0;
			for (BlockPos p : round(helper)) {
				if (level.getBlockState(p).is(Blocks.BARREL)) {
					Container c = (Container) level.getBlockEntity(p);
					barrels++;
					helper.assertTrue(c.getItem(0).is(Items.AMETHYST_SHARD) && c.getItem(0).getCount() == 1,
						"the barrel at " + p + " round his crate lost its shard: " + c.getItem(0));
					for (int i = 1; i < c.getContainerSize(); i++) {
						helper.assertTrue(c.getItem(i).isEmpty(), "the barrel at " + p + " round his crate got " + c.getItem(i) + " in slot " + i);
					}
				}
			}
			helper.assertTrue(barrels >= 100, "setup: only " + barrels + " barrels round his crate");
			assertBarrelsItems(helper, "right after the drop (only containers round the crate)");
			MoveSite site = MoverGameTests.work(level, s, x -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE && site.skipped() == 1, "the move ended " + site.stage() + ", skipped " + site.skipped());
			assertBarrelsItems(helper, "after the move (only containers round the crate)");
			helper.succeed();
		});
	}
}
