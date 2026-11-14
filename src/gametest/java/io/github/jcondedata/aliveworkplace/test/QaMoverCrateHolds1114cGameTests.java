package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.move.MovePlan;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import io.github.jcondedata.aliveworkplace.move.MoverWork;
import io.github.jcondedata.aliveworkplace.move.PendingMoves;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane c, 2026-11-14) for B1184, written from the bug report: when a Mover's Crate stands where a packed block goes
 * up, nothing packed is ever destroyed; the block and whatever its data holds are dropped there (or it waits / goes back).
 * QaMoverCrate1111bGameTests covers a chest and a lectern with a book. These add the other ways a vanilla block holds
 * things (a jukebox's disc, a decorated pot's item, a chiseled bookshelf's books, a furnace's three slots), a sign (data,
 * nothing held: just the sign), exact counts (nothing doubled), two blocks on two crates in one move, a save and reload
 * all through the put-up, and a crate with the column above it built up: the drop's spot 16 blocks up, and none free.
 * Same house, Mover and spot as QaMoverCrate1111bGameTests: moved 2 east and 2 south, the chest's spot on his crate.
 */
public class QaMoverCrateHolds1114cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	/** MoverGameTests' chest: the block whose new spot is his crate. */
	static final BlockPos CHEST = new BlockPos(4, 3, 4);
	static final BlockPos OWN_CRATE = new BlockPos(6, 3, 6);
	private static final BlockPos MOVER_AT = new BlockPos(24, 2, 24);
	private static final BlockPos TO = new BlockPos(4, 2, 4);

	// ---- helpers ---------------------------------------------------------------------------------------------------

	/** MoverGameTests' house and Mover, his only crate inside the house, moved to {@link #TO} not turned (the chest lands on the crate). */
	static MoverGameTests.Setup crateInside(GameTestHelper helper, String name) {
		MoverGameTests.Setup s = MoverGameTests.setup(helper, name);
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(12, 2, 11), Blocks.AIR); // no crate outside: his only one is in the house
		helper.setBlock(OWN_CRATE, ModBlocks.MOVERS_CRATE);
		s.mover().teleportTo(helper.absolutePos(MOVER_AT).getX() + 0.5, helper.absolutePos(MOVER_AT).getY(), helper.absolutePos(MOVER_AT).getZ() + 0.5);
		s.mover().getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(OWN_CRATE)));
		BlockPos absTo = helper.absolutePos(TO);
		PendingMoves.Move pending = PendingMoves.get(level).forBuilding(s.from()).orElseThrow();
		PendingMoves.get(level).remove(s.from());
		PendingMoves.get(level).put(new PendingMoves.Move(pending.hall(), pending.from(), pending.structure(), absTo, Rotation.NONE, null));
		return new MoverGameTests.Setup(s.placement(), s.before(), s.hall(), s.from(), absTo, s.mover());
	}

	/** The chest emptied and {@code state} set in its place, then {@code fill} given its block entity. */
	static void replaceChest(GameTestHelper helper, BlockState state, Consumer<BlockEntity> fill) {
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(CHEST);
		Clearable.tryClear(level.getBlockEntity(at));
		level.setBlock(at, state, Block.UPDATE_CLIENTS);
		BlockEntity be = level.getBlockEntity(at);
		helper.assertTrue(be != null && level.getBlockState(at).is(state.getBlock()), "setup: no " + state.getBlock() + " where the chest stood");
		fill.accept(be);
		be.setChanged();
	}

	static MoveSite work(ServerLevel level, MoverGameTests.Setup s, Predicate<MoveSite> stop) {
		return MoverGameTests.work(level, s, stop);
	}

	/** How many of {@code item} there are: in containers standing in the area, lying loose, or carried by villagers. */
	static int count(GameTestHelper helper, Item item) {
		ServerLevel level = helper.getLevel();
		int n = 0;
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(29, 29, 29)))) {
			if (level.getBlockEntity(pos) instanceof Container c) {
				for (int i = 0; i < c.getContainerSize(); i++) {
					if (c.getItem(i).is(item)) {
						n += c.getItem(i).getCount();
					}
				}
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

	/** Whether a block of {@code block} stands anywhere in the area (the dropped block must not be left standing somewhere). */
	static int standing(GameTestHelper helper, Block block) {
		int n = 0;
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(29, 29, 29)))) {
			if (helper.getLevel().getBlockState(pos).is(block)) {
				n++;
			}
		}
		return n;
	}

	/** The move ended with the one block on his crate skipped, the crate standing and his, and the barrel's coal moved with it. */
	static void assertEnded(GameTestHelper helper, ServerLevel level, MoverGameTests.Setup s, MoveSite site, int skipped, String when) {
		helper.assertTrue(site.stage() == MoveSite.Stage.DONE && site.waiting() == null && !site.canceled(),
			when + ": the move ended " + site.stage() + ", waiting " + site.waiting() + ", canceled " + site.canceled());
		helper.assertTrue(level.getBlockState(helper.absolutePos(OWN_CRATE)).is(ModBlocks.MOVERS_CRATE), when + ": his crate isn't where it stood");
		helper.assertTrue(MoverWork.hasCrate(level, s.mover()), when + ": the Mover lost his crate");
		helper.assertTrue(site.skipped() == skipped, when + ": skipped " + site.skipped() + ", not " + skipped);
		helper.assertTrue(count(helper, Items.COAL) == 20, when + ": the barrel's 20 coal are now " + count(helper, Items.COAL));
		helper.assertTrue(PendingMoves.get(level).forBuilding(s.from()).isEmpty() && MoveSites.get(level).forBuilding(s.from()).isEmpty(), when + ": the move wasn't put away");
	}

	/** {@code item} is there exactly {@code n} times, none lost and none doubled. */
	static void assertExactly(GameTestHelper helper, Item item, int n, String what) {
		int had = count(helper, item);
		helper.assertTrue(had == n, what + ": " + had + " " + item + " in the world, not " + n + (had < n ? " (lost)" : " (doubled)"));
	}

	private static void assertLandsOnCrate(GameTestHelper helper, MoverGameTests.Setup s) {
		BlockPos crate = helper.absolutePos(OWN_CRATE);
		for (MovePlan.Placed p : s.before().placed(s.to(), Rotation.NONE)) {
			if (p.pos().equals(crate)) {
				return;
			}
		}
		helper.fail("setup: nothing of the house lands on his crate");
	}

	// ---- one block holding things on his crate ----------------------------------------------------------------------

	/** A jukebox holding a disc (kept in its own tag, not an Items list): the jukebox and the disc lie there, once each. */
	//$ gametest_batch AREA '"qaB1184JukeboxOnCrate1114c"'
	@GameTest(template = AREA, batch = "qaB1184JukeboxOnCrate1114c")
	public void aJukeboxWhoseSpotIsHisCrateKeepsItsDisc(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = crateInside(helper, "qajukebox1114c");
		replaceChest(helper, Blocks.JUKEBOX.defaultBlockState().setValue(JukeboxBlock.HAS_RECORD, true),
			be -> ((JukeboxBlockEntity) be).setSongItemWithoutPlaying(new ItemStack(Items.MUSIC_DISC_CAT)));
		helper.runAfterDelay(2, () -> {
			assertLandsOnCrate(helper, s);
			assertExactly(helper, Items.MUSIC_DISC_CAT, 1, "setup");
			MoveSite site = work(level, s, x -> false);
			assertEnded(helper, level, s, site, 1, "jukebox on the crate");
			assertExactly(helper, Items.MUSIC_DISC_CAT, 1, "the jukebox's disc");
			assertExactly(helper, Items.JUKEBOX, 1, "the jukebox");
			helper.assertTrue(standing(helper, Blocks.JUKEBOX) == 0, "a jukebox was left standing: " + standing(helper, Blocks.JUKEBOX));
			helper.succeed();
		});
	}

	/** A decorated pot holding 7 diamonds: the pot (as a pot, not sherds) and the 7 diamonds lie there. */
	//$ gametest_batch AREA '"qaB1184PotOnCrate1114c"'
	@GameTest(template = AREA, batch = "qaB1184PotOnCrate1114c")
	public void aDecoratedPotWhoseSpotIsHisCrateKeepsItsItem(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = crateInside(helper, "qapot1114c");
		replaceChest(helper, Blocks.DECORATED_POT.defaultBlockState(), be -> ((DecoratedPotBlockEntity) be).setTheItem(new ItemStack(Items.DIAMOND, 7)));
		helper.runAfterDelay(2, () -> {
			assertLandsOnCrate(helper, s);
			int diamonds = diamonds(helper); // the item frame's diamond too
			helper.assertTrue(diamonds == 8, "setup: " + diamonds + " diamonds, not the pot's 7 and the frame's 1");
			MoveSite site = work(level, s, x -> false);
			assertEnded(helper, level, s, site, 1, "decorated pot on the crate");
			int now = diamonds(helper);
			helper.assertTrue(now == 8, "the pot's diamonds: " + now + " diamonds (with the frame's), not 8");
			assertExactly(helper, Items.DECORATED_POT, 1, "the decorated pot");
			assertExactly(helper, Items.BRICK, 0, "the pot broke into sherds");
			helper.succeed();
		});
	}

	/** The diamonds in the area, the item frames' included. */
	private static int diamonds(GameTestHelper helper) {
		int n = count(helper, Items.DIAMOND);
		for (net.minecraft.world.entity.decoration.ItemFrame f : helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.decoration.ItemFrame.class,
			new AABB(helper.absolutePos(new BlockPos(0, 1, 0))).inflate(48))) {
			n += f.getItem().is(Items.DIAMOND) ? f.getItem().getCount() : 0;
		}
		return n;
	}

	/** A chiseled bookshelf with three books in slots 0, 2 and 5 on his crate: the shelf as it stood. */
	static void shelf(GameTestHelper helper) {
		replaceChest(helper, Blocks.CHISELED_BOOKSHELF.defaultBlockState(), be -> {
			ChiseledBookShelfBlockEntity shelf = (ChiseledBookShelfBlockEntity) be;
			shelf.setItem(0, new ItemStack(Items.BOOK));
			shelf.setItem(2, new ItemStack(Items.ENCHANTED_BOOK));
			shelf.setItem(5, new ItemStack(Items.WRITABLE_BOOK));
		});
	}

	/** A chiseled bookshelf with three books (in slots 0, 2 and 5): the three books lie there, once each (the shelf itself: the bug class). */
	//$ gametest_batch AREA '"qaB1184ShelfOnCrate1114c"'
	@GameTest(template = AREA, batch = "qaB1184ShelfOnCrate1114c")
	public void aChiseledBookshelfWhoseSpotIsHisCrateKeepsItsBooks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = crateInside(helper, "qashelf1114c");
		shelf(helper);
		helper.runAfterDelay(2, () -> {
			assertLandsOnCrate(helper, s);
			MoveSite site = work(level, s, x -> false);
			assertEnded(helper, level, s, site, 1, "chiseled bookshelf on the crate");
			assertExactly(helper, Items.BOOK, 1, "the shelf's book");
			assertExactly(helper, Items.ENCHANTED_BOOK, 1, "the shelf's enchanted book");
			assertExactly(helper, Items.WRITABLE_BOOK, 1, "the shelf's book and quill");
			helper.succeed();
		});
	}

	/** A furnace with ore, fuel and its output (an Items list: the boundary the old code handled): all three slots and the furnace. */
	//$ gametest_batch AREA '"qaB1184FurnaceOnCrate1114c"'
	@GameTest(template = AREA, batch = "qaB1184FurnaceOnCrate1114c")
	public void aFurnaceWhoseSpotIsHisCrateKeepsAllThreeSlots(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = crateInside(helper, "qafurnace1114c");
		replaceChest(helper, Blocks.FURNACE.defaultBlockState(), be -> {
			Container furnace = (Container) be;
			furnace.setItem(0, new ItemStack(Items.BONE, 9)); // nothing to smelt: the fuel isn't burnt while the test runs
			furnace.setItem(1, new ItemStack(Items.CHARCOAL, 4));
			furnace.setItem(2, new ItemStack(Items.IRON_INGOT, 11));
		});
		helper.runAfterDelay(2, () -> {
			assertLandsOnCrate(helper, s);
			MoveSite site = work(level, s, x -> false);
			assertEnded(helper, level, s, site, 1, "furnace on the crate");
			assertExactly(helper, Items.BONE, 9, "the furnace's input");
			assertExactly(helper, Items.CHARCOAL, 4, "the furnace's fuel");
			assertExactly(helper, Items.IRON_INGOT, 11, "the furnace's output");
			assertExactly(helper, Items.FURNACE, 1, "the furnace");
			helper.succeed();
		});
	}

	/** A standing sign with words on it (data, nothing held): the sign lies there, once, and nothing else appears. */
	//$ gametest_batch AREA '"qaB1184SignOnCrate1114c"'
	@GameTest(template = AREA, batch = "qaB1184SignOnCrate1114c")
	public void aSignWhoseSpotIsHisCrateIsDroppedOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = crateInside(helper, "qasign1114c");
		replaceChest(helper, Blocks.OAK_SIGN.defaultBlockState(), be -> ((net.minecraft.world.level.block.entity.SignBlockEntity) be).setText(
			new net.minecraft.world.level.block.entity.SignText().setMessage(0, net.minecraft.network.chat.Component.literal("Mover")), true));
		helper.runAfterDelay(2, () -> {
			assertLandsOnCrate(helper, s);
			MoveSite site = work(level, s, x -> false);
			assertEnded(helper, level, s, site, 1, "sign on the crate");
			assertExactly(helper, Items.OAK_SIGN, 1, "the sign");
			int loose = level.getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(new BlockPos(0, 1, 0))).inflate(48)).size();
			helper.assertTrue(loose == 1, "besides the sign, " + (loose - 1) + " more stacks lie loose");
			helper.assertTrue(standing(helper, Blocks.OAK_SIGN) == 0, "a sign was left standing");
			helper.succeed();
		});
	}

	// ---- two at once, a reload, the column above the crate ------------------------------------------------------------

	/**
	 * Two crates, two blocks: a lectern with its book on his crate and a jukebox with its disc on a second Mover's Crate
	 * inside the house. Both crates stay, both blocks and both things they hold lie there, once each.
	 */
	//$ gametest_batch AREA '"qaB1184TwoCrates1114c"'
	@GameTest(template = AREA, batch = "qaB1184TwoCrates1114c")
	public void twoBlocksOnTwoCratesBothKeepWhatTheyHold(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = crateInside(helper, "qatwo1114c");
		BlockPos jukebox = new BlockPos(5, 3, 5);
		BlockPos second = new BlockPos(7, 3, 7);
		replaceChest(helper, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.HAS_BOOK, true),
			be -> ((LecternBlockEntity) be).setBook(new ItemStack(Items.WRITABLE_BOOK)));
		level.setBlock(helper.absolutePos(jukebox), Blocks.JUKEBOX.defaultBlockState().setValue(JukeboxBlock.HAS_RECORD, true), Block.UPDATE_CLIENTS);
		((JukeboxBlockEntity) level.getBlockEntity(helper.absolutePos(jukebox))).setSongItemWithoutPlaying(new ItemStack(Items.MUSIC_DISC_13));
		helper.setBlock(second, ModBlocks.MOVERS_CRATE);
		helper.runAfterDelay(2, () -> {
			MoveSite site = work(level, s, x -> false);
			assertEnded(helper, level, s, site, 2, "two blocks on two crates");
			helper.assertTrue(level.getBlockState(helper.absolutePos(second)).is(ModBlocks.MOVERS_CRATE), "the second crate isn't where it stood");
			assertExactly(helper, Items.LECTERN, 1, "the lectern");
			assertExactly(helper, Items.WRITABLE_BOOK, 1, "the lectern's book");
			assertExactly(helper, Items.JUKEBOX, 1, "the jukebox");
			assertExactly(helper, Items.MUSIC_DISC_13, 1, "the jukebox's disc");
			helper.succeed();
		});
	}

	/** A lectern with its book on his crate, the site saved and read back every 3 steps all through the put-up. */
	//$ gametest_batch AREA '"qaB1184CrateReload1114c"'
	@GameTest(template = AREA, batch = "qaB1184CrateReload1114c")
	public void aLecternOnHisCrateKeepsItsBookThroughReloads(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = crateInside(helper, "qareload1114c");
		replaceChest(helper, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.HAS_BOOK, true),
			be -> ((LecternBlockEntity) be).setBook(new ItemStack(Items.WRITTEN_BOOK)));
		helper.runAfterDelay(2, () -> {
			MoveSite site = work(level, s, x -> x.stage() != MoveSite.Stage.DISMANTLE);
			helper.assertTrue(site.stage().rebuilding(), "setup: it didn't get to putting it up: " + site.stage());
			helper.assertTrue(count(helper, Items.WRITTEN_BOOK) == 0, "setup: the book wasn't packed: " + count(helper, Items.WRITTEN_BOOK));
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
			assertEnded(helper, level, s, site, 1, "reloaded every 3 steps");
			assertExactly(helper, Items.WRITTEN_BOOK, 1, "the lectern's book");
			assertExactly(helper, Items.LECTERN, 1, "the lectern");
			helper.succeed();
		});
	}

	/**
	 * The column over his crate built up 15 blocks once the house is down, so the first free block is the 16th up: the
	 * lectern and its book are still dropped (they lie up there), none lost, nothing left standing.
	 */
	//$ gametest_batch AREA '"qaB1184Column15_1114c"'
	@GameTest(template = AREA, batch = "qaB1184Column15_1114c")
	public void aCrateUnderAFifteenBlockColumnStillGetsTheLecternAndBook(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = crateInside(helper, "qacol15_1114c");
		replaceChest(helper, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.HAS_BOOK, true),
			be -> ((LecternBlockEntity) be).setBook(new ItemStack(Items.WRITABLE_BOOK)));
		helper.runAfterDelay(2, () -> {
			MoveSite site = work(level, s, x -> x.stage() != MoveSite.Stage.DISMANTLE);
			column(helper, 15);
			site = work(level, s, x -> false);
			assertEnded(helper, level, s, site, 1, "a 15-block column over the crate");
			assertExactly(helper, Items.WRITABLE_BOOK, 1, "the lectern's book");
			assertExactly(helper, Items.LECTERN, 1, "the lectern");
			helper.assertTrue(standing(helper, Blocks.LECTERN) == 0, "a lectern was left standing");
			helper.succeed();
		});
	}

	/** Stone on the {@code n} blocks over his crate. */
	static void column(GameTestHelper helper, int n) {
		for (int y = 1; y <= n; y++) {
			helper.getLevel().setBlock(helper.absolutePos(OWN_CRATE.above(y)), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
	}
}
