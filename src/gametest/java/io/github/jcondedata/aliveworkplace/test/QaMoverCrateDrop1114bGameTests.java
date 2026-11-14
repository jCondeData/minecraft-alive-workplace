package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import io.github.jcondedata.aliveworkplace.move.MoverWork;
import io.github.jcondedata.aliveworkplace.move.PendingMoves;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane b, 2026-11-14) for B1184, written from the bug report (B1172's expected: "nothing packed is ever destroyed:
 * the block waits, goes back, or is dropped with its contents"), not the code. The fix's tests cover a chest and a
 * lectern with room above the crate, and Create's vault (compat). These add the other kinds the report names (a
 * jukebox's disc, a decorated pot, and a shulker box, whose contents travel inside its item), and the crate with no
 * room above it: the boundary with the only room 16 up passes; with no room at all (roofed 16 high) the book is deleted,
 * which is B-filed with its test on tests/mover-crate-roof-1114b (QaMoverCrateDrop1114bBugTests).
 * Set up like QaMoverCrate1111bGameTests: MoverGameTests' house moved two blocks south-east, not turned, so the block
 * where the chest stood lands on his crate inside the house.
 */
public class QaMoverCrateDrop1114bGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos CHEST = new BlockPos(4, 3, 4);
	private static final BlockPos OWN_CRATE = new BlockPos(6, 3, 6);
	private static final BlockPos MOVER_AT = new BlockPos(24, 2, 24);

	/** MoverGameTests' house with his only crate inside it, the move set two blocks south-east, and {@code state} where the chest stood. */
	private static MoverGameTests.Setup setup(GameTestHelper helper, String name, BlockState state, Consumer<BlockEntity> fill) {
		MoverGameTests.Setup s = MoverGameTests.setup(helper, name);
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(12, 2, 11), Blocks.AIR); // no crate outside: his only one is in the house
		helper.setBlock(OWN_CRATE, ModBlocks.MOVERS_CRATE);
		BlockPos at = helper.absolutePos(MOVER_AT);
		s.mover().teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		s.mover().getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(OWN_CRATE)));
		BlockPos to = helper.absolutePos(new BlockPos(4, 2, 4));
		PendingMoves.Move pending = PendingMoves.get(level).forBuilding(s.from()).orElseThrow();
		PendingMoves.get(level).remove(s.from());
		PendingMoves.get(level).put(new PendingMoves.Move(pending.hall(), pending.from(), pending.structure(), to, Rotation.NONE, null));
		BlockPos chest = helper.absolutePos(CHEST);
		Clearable.tryClear(level.getBlockEntity(chest));
		level.setBlock(chest, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		level.setBlock(chest, state, Block.UPDATE_CLIENTS);
		BlockEntity be = level.getBlockEntity(chest);
		helper.assertTrue(be != null, "setup: no block entity in " + state);
		fill.accept(be);
		be.setChanged();
		return new MoverGameTests.Setup(s.placement(), s.before(), s.hall(), s.from(), to, s.mover());
	}

	/** How many of {@code item} there are round the test: in containers, lying loose (also inside a loose box), or carried. */
	private static int count(GameTestHelper helper, Item item) {
		ServerLevel level = helper.getLevel();
		int n = 0;
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(29, 28, 29)))) {
			BlockEntity be = level.getBlockEntity(pos);
			if (be instanceof Container c) {
				for (int i = 0; i < c.getContainerSize(); i++) {
					n += of(c.getItem(i), item);
				}
			}
			if (be instanceof LecternBlockEntity l) {
				n += of(l.getBook(), item);
			}
		}
		AABB area = new AABB(helper.absolutePos(new BlockPos(0, 1, 0))).inflate(40);
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, area)) {
			n += of(e.getItem(), item);
		}
		for (Villager v : level.getEntitiesOfClass(Villager.class, area)) {
			for (int i = 0; i < v.getInventory().getContainerSize(); i++) {
				n += of(v.getInventory().getItem(i), item);
			}
		}
		return n;
	}

	private static String loose(GameTestHelper helper) {
		StringBuilder b = new StringBuilder();
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(new BlockPos(0, 1, 0))).inflate(40))) {
			b.append(e.getItem()).append(' ').append(e.getItem().get(DataComponents.CONTAINER)).append("; ");
		}
		return b.toString();
	}

	private static int of(ItemStack stack, Item item) {
		int n = stack.is(item) ? stack.getCount() : 0;
		ItemContainerContents in = stack.get(DataComponents.CONTAINER);
		if (in != null) {
			for (ItemStack s : in.nonEmptyItems()) {
				n += (s.is(item) ? s.getCount() : 0) * stack.getCount();
			}
		}
		return n;
	}

	/** The move ends whole: done, his crate where it stood and still his, the move put away. */
	private static void assertDone(GameTestHelper helper, ServerLevel level, MoverGameTests.Setup s, MoveSite site, String when) {
		helper.assertTrue(site.stage() == MoveSite.Stage.DONE && site.waiting() == null && !site.canceled(),
			when + ": the move ended " + site.stage() + ", waiting " + site.waiting() + ", canceled " + site.canceled());
		helper.assertTrue(level.getBlockState(helper.absolutePos(OWN_CRATE)).is(ModBlocks.MOVERS_CRATE),
			when + ": his crate isn't where it stood: " + level.getBlockState(helper.absolutePos(OWN_CRATE)));
		helper.assertTrue(MoverWork.hasCrate(level, s.mover()), when + ": the Mover lost his crate (workstation)");
		helper.assertTrue(PendingMoves.get(level).forBuilding(s.from()).isEmpty() && MoveSites.get(level).forBuilding(s.from()).isEmpty(),
			when + ": the move wasn't put away");
	}

	/** Works the move to its end and checks that {@code item} ({@code n} of it) and the block's own item are still there. */
	private static void moveAndCount(GameTestHelper helper, MoverGameTests.Setup s, Item block, Item item, int n, String what) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(count(helper, item) == n, "setup: " + count(helper, item) + " " + item + ", not " + n);
		MoveSite site = MoverGameTests.work(level, s, x -> false);
		assertDone(helper, level, s, site, what);
		helper.assertTrue(site.skipped() == 1, what + ": skipped " + site.skipped() + ", not just the block where his crate stands");
		helper.assertTrue(count(helper, item) == n, what + ": what it held was lost: " + count(helper, item) + " of " + n + " " + item + " left");
		helper.assertTrue(count(helper, block) >= 1, what + ": the block itself was lost (no " + block + " anywhere)");
	}

	// ---- the other kinds the report names ----------------------------------------------------------------------------

	/** A jukebox playing a disc lands on his crate: the jukebox and its disc are both dropped there, never deleted. */
	//$ gametest_batch AREA '"qaB1184bJukebox"'
	@GameTest(template = AREA, batch = "qaB1184bJukebox")
	public void aJukeboxWhoseSpotIsHisCrateKeepsItsDisc(GameTestHelper helper) {
		Leftovers.clear(helper);
		MoverGameTests.Setup s = setup(helper, "qacratejukebox", Blocks.JUKEBOX.defaultBlockState().setValue(JukeboxBlock.HAS_RECORD, true),
			be -> ((JukeboxBlockEntity) be).setTheItem(new ItemStack(Items.MUSIC_DISC_CAT)));
		helper.runAfterDelay(2, () -> {
			moveAndCount(helper, s, Items.JUKEBOX, Items.MUSIC_DISC_CAT, 1, "the jukebox's spot over the crate");
			helper.succeed();
		});
	}

	/** A decorated pot holding 40 emeralds lands on his crate: the emeralds are not deleted. */
	//$ gametest_batch AREA '"qaB1184bPot"'
	@GameTest(template = AREA, batch = "qaB1184bPot")
	public void aDecoratedPotWhoseSpotIsHisCrateKeepsWhatItHolds(GameTestHelper helper) {
		Leftovers.clear(helper);
		MoverGameTests.Setup s = setup(helper, "qacratepot", Blocks.DECORATED_POT.defaultBlockState(),
			be -> ((DecoratedPotBlockEntity) be).setTheItem(new ItemStack(Items.EMERALD, 40)));
		helper.runAfterDelay(2, () -> {
			moveAndCount(helper, s, Items.DECORATED_POT, Items.EMERALD, 40, "the pot's spot over the crate");
			helper.succeed();
		});
	}

	/** A shulker box holding 64 emeralds and 5 bread lands on his crate: the box comes out with all of it. */
	//$ gametest_batch AREA '"qaB1184bShulker"'
	@GameTest(template = AREA, batch = "qaB1184bShulker")
	public void aShulkerBoxWhoseSpotIsHisCrateKeepsWhatItHolds(GameTestHelper helper) {
		Leftovers.clear(helper);
		MoverGameTests.Setup s = setup(helper, "qacrateshulker", Blocks.SHULKER_BOX.defaultBlockState(), be -> {
			((Container) be).setItem(0, new ItemStack(Items.EMERALD, 64));
			((Container) be).setItem(13, new ItemStack(Items.BREAD, 5));
		});
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(count(helper, Items.BREAD) == 5, "setup: the box's bread");
			moveAndCount(helper, s, Items.SHULKER_BOX, Items.EMERALD, 64, "the shulker box's spot over the crate");
			helper.assertTrue(count(helper, Items.BREAD) == 5, "the box's bread was lost: " + count(helper, Items.BREAD) + " of 5; loose: " + loose(helper));
			helper.succeed();
		});
	}

	// ---- no room above the crate ------------------------------------------------------------------------------------

	/**
	 * A lectern holding a book lands on his crate, and the crate has solid blocks above it {@code roofed} high (filled
	 * in once the floor is down, before the lectern goes up with the walls). Nothing packed may be deleted however the crate is roofed.
	 */
	static void lecternUnder(GameTestHelper helper, String name, int roofed) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = setup(helper, name, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.HAS_BOOK, true),
			be -> ((LecternBlockEntity) be).setBook(new ItemStack(Items.WRITABLE_BOOK)));
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(count(helper, Items.WRITABLE_BOOK) == 1, "setup: the lectern's book");
			MoveSite site = MoverGameTests.work(level, s, x -> x.stage() == MoveSite.Stage.STRUCTURE);
			helper.assertTrue(site.stage() == MoveSite.Stage.STRUCTURE && site.skipped() == 0,
				"setup: the move didn't reach its walls with nothing skipped: " + site.stage() + ", skipped " + site.skipped());
			BlockPos crate = helper.absolutePos(OWN_CRATE);
			for (int dy = 1; dy <= roofed; dy++) {
				if (level.getBlockState(crate.above(dy)).isAir()) {
					level.setBlock(crate.above(dy), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
			site = MoverGameTests.work(level, s, x -> false);
			assertDone(helper, level, s, site, "the lectern over a crate roofed " + roofed + " high");
			helper.assertTrue(site.skipped() == 1, "skipped " + site.skipped() + ", not just the lectern where his crate stands");
			helper.assertTrue(count(helper, Items.WRITABLE_BOOK) == 1,
				"the lectern's book was deleted (crate roofed " + roofed + " high): " + count(helper, Items.WRITABLE_BOOK) + " of 1 left");
			helper.assertTrue(count(helper, Items.LECTERN) >= 1, "the lectern was deleted (crate roofed " + roofed + " high)");
			for (int dy = 1; dy <= roofed; dy++) {
				// the lectern is dropped at the first free spot: nothing standing above his crate is put over or broken for it
				helper.assertTrue(!level.getBlockState(crate.above(dy)).isAir(),
					"a block standing " + dy + " above his crate was replaced to drop the lectern: " + level.getBlockState(crate.above(dy)));
			}
			helper.succeed();
		});
	}

	/** The boundary from inside: the only room above his crate is 16 blocks up. The lectern and its book come out there. */
	//$ gametest_batch AREA '"qaB1184bRoof15"'
	@GameTest(template = AREA, batch = "qaB1184bRoof15")
	public void aLecternOverHisCrateWithRoomSixteenUpKeepsItsBook(GameTestHelper helper) {
		lecternUnder(helper, "qacrateroof15", 15);
	}
}
