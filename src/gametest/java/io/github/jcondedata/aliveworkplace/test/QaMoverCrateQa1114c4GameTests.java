package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoverWork;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane c, 2026-11-14, fourth pass) verifying B1236, B1246-B1250, B1252 and B1253 from the bug texts and
 * moving-buildings.md, not the code. The promise: a block packed onto a spot where a Mover's Crate stands is never
 * destroyed; it drops as its own item with everything it holds, once, nothing doubled. These cover what the earlier QA
 * classes leave out: a bee nest with bees and honey; a soul campfire with four foods (also across a save and reload at
 * every step of the put-up); a chiseled bookshelf, a shulker box and a brewing stand in a storage room (no spot at all,
 * the last resort); a lava source over the crate in a storage room and fire over a walled-in crate (never a drop spot);
 * Silk-Touch-only plain blocks with no block entity (packed ice, blue ice, tinted glass, a glass pane, a sea lantern);
 * (a door whose lower half lands on the crate is QaMoverCrateQa1114c4BugGameTests). Same house and Mover as
 * MoverCrateNoRoomB1234GameTests (the chest's spot lands on his crate).
 */
public class QaMoverCrateQa1114c4GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos SPOT = new BlockPos(4, 3, 4);

	// ---- helpers ---------------------------------------------------------------------------------------------------

	/** Bees anywhere: flying, in a hive or nest standing in the area, or inside a hive or nest item lying there. */
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

	/** {@code item} round the test as QaMoverCrateEdges1114bGameTests counts it, plus inside a shulker box item lying there. */
	static int countWithBoxes(GameTestHelper helper, Item item) {
		int n = QaMoverCrateEdges1114bGameTests.count(helper, item);
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(48))) {
			ItemContainerContents in = e.getItem().get(DataComponents.CONTAINER);
			if (in != null) {
				for (ItemStack s : in.nonEmptyItems()) {
					n += s.is(item) ? s.getCount() : 0;
				}
			}
		}
		return n;
	}

	static void assertWithBoxes(GameTestHelper helper, Item item, int n, String what) {
		int had = countWithBoxes(helper, item);
		helper.assertTrue(had == n, what + ": " + had + " " + item + " round the test, not " + n + (had < n ? " (lost)" : " (doubled)"));
	}

	/** Runs the move to its end after the house is down, {@code between} run once there; done, skipped 1, crate kept. */
	static MoveSite toTheEnd(GameTestHelper helper, MoverGameTests.Setup s, Runnable between, String when) {
		MoveSite site = QaMoverCrateVerify1114fGameTests.run(helper, s, between);
		MoverCrateNoRoomB1234GameTests.assertEnded(helper, helper.getLevel(), s, site, 1, when);
		return site;
	}

	/** In a storage room until the drop (QaMoverCrateEdges1114bGameTests' room), then to the end; done, skipped 1. */
	static void storageRoom(GameTestHelper helper, MoverGameTests.Setup s, String when) {
		QaMoverCrateEdges1114bGameTests.untilTheDrop(helper, s, () -> QaMoverCrateEdges1114bGameTests.barrelsRound(helper));
		MoveSite site = MoverGameTests.work(helper.getLevel(), s, x -> false);
		helper.assertTrue(site.stage() == MoveSite.Stage.DONE && !site.canceled() && site.skipped() == 1,
			when + ": the move ended " + site.stage() + ", canceled " + site.canceled() + ", skipped " + site.skipped());
	}

	/** A plain block (no block entity) where the chest stood, the move run to the end: its own item once, {@code notThese} none. */
	private static void plainBlock(GameTestHelper helper, String name, BlockState state, Item own, Item... notThese) {
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, name);
		QaMoverCrateVerify1114fGameTests.place(helper, state);
		helper.runAfterDelay(2, () -> {
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, own, 0, "setup");
			toTheEnd(helper, s, () -> { }, state.getBlock() + " on his crate");
			QaMoverCrateVerify1114fGameTests.assertOwnItem(helper, own, "the " + state.getBlock().getName().getString(), notThese);
			helper.assertTrue(QaMoverCrateHolds1114cGameTests.standing(helper, state.getBlock()) == 0, "a " + state.getBlock() + " was left standing");
			QaMoverCrateVerify1114fGameTests.assertNoWaterNear(helper, state.getBlock() + " on his crate");
			helper.succeed();
		});
	}

	// ---- blocks with a block entity, room above ---------------------------------------------------------------------

	/**
	 * A bee nest with honey level 5 and 2 bees in it on his crate: the nest itself lies there once (not nothing, as a bare
	 * hand gets), both bees still exist (let out, or inside the nest item), and if the nest item keeps a honey level it is 5.
	 */
	//$ gametest_batch AREA '"qa1114c4BeeNest"'
	@GameTest(template = AREA, batch = "qa1114c4BeeNest")
	public void aBeeNestWithBeesAndHoneyOnHisCrateKeepsItsBees(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qa1114c4nest");
		MoverCrateNoRoomB1234GameTests.replaceChest(helper, Blocks.BEE_NEST.defaultBlockState().setValue(BeehiveBlock.HONEY_LEVEL, 5), be -> {
			for (int i = 0; i < 2; i++) {
				Bee bee = EntityType.BEE.create(level);
				bee.moveTo(be.getBlockPos().getX() + 0.5, be.getBlockPos().getY() + 1, be.getBlockPos().getZ() + 0.5);
				level.addFreshEntity(bee);
				((BeehiveBlockEntity) be).addOccupant(bee);
			}
		});
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(bees(helper) == 2, "setup: " + bees(helper) + " bees, not the nest's 2");
			toTheEnd(helper, s, () -> { }, "a bee nest with bees on his crate");
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.BEE_NEST, 1, "the bee nest");
			helper.assertTrue(QaMoverCrateHolds1114cGameTests.standing(helper, Blocks.BEE_NEST) == 0, "a bee nest was left standing");
			int now = bees(helper);
			helper.assertTrue(now == 2, "the nest's bees: " + now + " of 2 left anywhere (B1172: nothing packed is ever destroyed)");
			for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(48))) {
				BlockItemStateProperties props = e.getItem().is(Items.BEE_NEST) ? e.getItem().get(DataComponents.BLOCK_STATE) : null;
				Integer honey = props == null ? null : props.get(BeehiveBlock.HONEY_LEVEL);
				helper.assertTrue(honey == null || honey == 5, "the dropped nest's honey level is " + honey + ", not the 5 it had");
			}
			helper.succeed();
		});
	}

	/** An unlit soul campfire with 4 different foods where the chest stood. */
	static void soulCampfire(GameTestHelper helper) {
		MoverCrateNoRoomB1234GameTests.replaceChest(helper, Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false), be -> {
			for (Item food : FOODS) {
				((CampfireBlockEntity) be).placeFood(null, new ItemStack(food), 600);
			}
		});
	}

	static final Item[] FOODS = {Items.BEEF, Items.PORKCHOP, Items.CHICKEN, Items.MUTTON};

	static void assertSoulCampfire(GameTestHelper helper, String when) {
		for (Item food : FOODS) {
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, food, 1, when + ": the campfire's " + food);
		}
		QaMoverCrateVerify1114fGameTests.assertOwnItem(helper, Items.SOUL_CAMPFIRE, when + ": the soul campfire", Items.SOUL_SOIL, Items.CAMPFIRE,
			Items.COOKED_BEEF, Items.COOKED_PORKCHOP, Items.COOKED_CHICKEN, Items.COOKED_MUTTON);
		helper.assertTrue(QaMoverCrateHolds1114cGameTests.standing(helper, Blocks.SOUL_CAMPFIRE) == 0, when + ": a soul campfire was left standing");
	}

	/** A soul campfire holding 4 foods on his crate: the soul campfire once (not soul soil) and each food once. */
	//$ gametest_batch AREA '"qa1114c4SoulCampfire"'
	@GameTest(template = AREA, batch = "qa1114c4SoulCampfire")
	public void aSoulCampfireWithFourFoodsOnHisCrateDropsItselfAndEachFoodOnce(GameTestHelper helper) {
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qa1114c4soulfire");
		soulCampfire(helper);
		helper.runAfterDelay(2, () -> {
			for (Item food : FOODS) {
				MoverCrateNoRoomB1234GameTests.assertExactly(helper, food, 0, "setup: loose " + food);
			}
			toTheEnd(helper, s, () -> { }, "a soul campfire on his crate");
			assertSoulCampfire(helper, "room above");
			helper.succeed();
		});
	}

	/**
	 * The same soul campfire, the move saved and read back (a server restart) after every step of the put-up, so the
	 * Mover's work restarts right before the drop too: still the soul campfire once and each food once.
	 */
	//$ gametest_batch AREA '"qa1114c4SoulCampfireReload"'
	@GameTest(template = AREA, batch = "qa1114c4SoulCampfireReload")
	public void aSoulCampfireOnHisCrateReloadedAtEveryStepDropsOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qa1114c4soulreload");
		soulCampfire(helper);
		helper.runAfterDelay(2, () -> {
			MoveSite site = MoverCrateNoRoomB1234GameTests.work(level, s, x -> x.stage() != MoveSite.Stage.DISMANTLE);
			helper.assertTrue(site.stage().rebuilding(), "setup: it didn't get to putting it up: " + site.stage());
			int steps = 0;
			int reloads = 0;
			int reloadedRightBefore = 0;
			while (site.stage() != MoveSite.Stage.DONE && steps++ < MoverGameTests.MAX_STEPS) {
				int skippedBefore = site.skipped();
				site = MoverGameTests.reload(level, s);
				reloads++;
				MoverWork.work(level, s.mover(), site);
				if (site.skipped() > skippedBefore) {
					reloadedRightBefore++;
				}
			}
			helper.assertTrue(reloads > 10 && reloadedRightBefore == 1, "setup: " + reloads + " reloads, " + reloadedRightBefore + " drops right after one");
			MoverCrateNoRoomB1234GameTests.assertEnded(helper, level, s, site, 1, "a soul campfire on his crate, reloaded every step");
			assertSoulCampfire(helper, "reloaded every step");
			helper.succeed();
		});
	}

	// ---- a storage room: no spot at all (the last resort) ----------------------------------------------------------

	/** A chiseled bookshelf with six books on his crate in a storage room: the shelf once, each book once. */
	//$ gametest_batch AREA '"qa1114c4StoreShelf"'
	@GameTest(template = AREA, batch = "qa1114c4StoreShelf")
	public void aChiseledShelfWithSixBooksInAStorageRoomDropsItselfAndEachBookOnce(GameTestHelper helper) {
		MoverGameTests.Setup s = QaMoverCrateHolds1114cGameTests.crateInside(helper, "qa1114c4storeshelf");
		QaMoverCrateHolds1114cGameTests.replaceChest(helper, Blocks.CHISELED_BOOKSHELF.defaultBlockState(), be -> {
			for (int i = 0; i < QaMoverCrateVerify1114fGameTests.BOOKS.length; i++) {
				((ChiseledBookShelfBlockEntity) be).setItem(i, new ItemStack(QaMoverCrateVerify1114fGameTests.BOOKS[i]));
			}
		});
		helper.runAfterDelay(2, () -> {
			QaMoverCrateEdges1114bGameTests.assertExactly(helper, Items.BOOK, 2, "setup");
			storageRoom(helper, s, "a chiseled bookshelf in a storage room");
			QaMoverCrateEdges1114bGameTests.assertExactly(helper, Items.BOOK, 2, "the shelf's two books (storage room)");
			for (int i = 1; i < 5; i++) {
				QaMoverCrateEdges1114bGameTests.assertExactly(helper, QaMoverCrateVerify1114fGameTests.BOOKS[i], 1, "the shelf's book (storage room)");
			}
			QaMoverCrateEdges1114bGameTests.assertExactly(helper, Items.CHISELED_BOOKSHELF, 1, "the chiseled bookshelf (storage room)");
			helper.assertTrue(QaMoverCrateHolds1114cGameTests.standing(helper, Blocks.CHISELED_BOOKSHELF) == 0, "a chiseled bookshelf was left standing");
			helper.succeed();
		});
	}

	/**
	 * A lime shulker box holding 40 diamonds and 3 ender pearls on his crate in a storage room: the box once, and its
	 * items exactly once (inside the box item or loose, never both, never neither).
	 */
	//$ gametest_batch AREA '"qa1114c4StoreShulker"'
	@GameTest(template = AREA, batch = "qa1114c4StoreShulker")
	public void aShulkerBoxInAStorageRoomKeepsItsItemsExactlyOnce(GameTestHelper helper) {
		MoverGameTests.Setup s = QaMoverCrateHolds1114cGameTests.crateInside(helper, "qa1114c4storeshulker");
		QaMoverCrateHolds1114cGameTests.replaceChest(helper, Blocks.LIME_SHULKER_BOX.defaultBlockState(), be -> {
			((Container) be).setItem(0, new ItemStack(Items.DIAMOND, 40));
			((Container) be).setItem(26, new ItemStack(Items.ENDER_PEARL, 3));
		});
		helper.runAfterDelay(2, () -> {
			assertWithBoxes(helper, Items.DIAMOND, 40, "setup");
			storageRoom(helper, s, "a shulker box in a storage room");
			assertWithBoxes(helper, Items.LIME_SHULKER_BOX, 1, "the shulker box (storage room)");
			assertWithBoxes(helper, Items.DIAMOND, 40, "the box's diamonds (storage room)");
			assertWithBoxes(helper, Items.ENDER_PEARL, 3, "the box's ender pearls (storage room)");
			helper.assertTrue(QaMoverCrateHolds1114cGameTests.standing(helper, Blocks.LIME_SHULKER_BOX) == 0, "a shulker box was left standing");
			helper.succeed();
		});
	}

	/** A brewing stand with 3 potions, a nether wart and 5 blaze powder (one burned as fuel at once, as vanilla does) on his crate in a storage room: all of it, once. */
	//$ gametest_batch AREA '"qa1114c4StoreBrewing"'
	@GameTest(template = AREA, batch = "qa1114c4StoreBrewing")
	public void aBrewingStandInAStorageRoomKeepsItsPotionsAndFuel(GameTestHelper helper) {
		MoverGameTests.Setup s = QaMoverCrateHolds1114cGameTests.crateInside(helper, "qa1114c4storebrew");
		QaMoverCrateHolds1114cGameTests.replaceChest(helper, Blocks.BREWING_STAND.defaultBlockState(), be -> {
			for (int i = 0; i < 3; i++) {
				((Container) be).setItem(i, PotionContents.createItemStack(Items.POTION, Potions.AWKWARD));
			}
			((Container) be).setItem(3, new ItemStack(Items.NETHER_WART));
			((Container) be).setItem(4, new ItemStack(Items.BLAZE_POWDER, 5));
		});
		helper.runAfterDelay(2, () -> {
			QaMoverCrateEdges1114bGameTests.assertExactly(helper, Items.POTION, 3, "setup");
			int powder = QaMoverCrateEdges1114bGameTests.count(helper, Items.BLAZE_POWDER); // the stand fuels itself with one at once (vanilla)
			helper.assertTrue(powder == 4 || powder == 5, "setup: " + powder + " blaze powder");
			storageRoom(helper, s, "a brewing stand in a storage room");
			QaMoverCrateEdges1114bGameTests.assertExactly(helper, Items.BREWING_STAND, 1, "the brewing stand (storage room)");
			QaMoverCrateEdges1114bGameTests.assertExactly(helper, Items.POTION, 3, "the stand's potions (storage room)");
			QaMoverCrateEdges1114bGameTests.assertExactly(helper, Items.NETHER_WART, 1, "the stand's nether wart (storage room)");
			QaMoverCrateEdges1114bGameTests.assertExactly(helper, Items.BLAZE_POWDER, powder, "the stand's blaze powder (storage room)");
			helper.assertTrue(QaMoverCrateHolds1114cGameTests.standing(helper, Blocks.BREWING_STAND) == 0, "a brewing stand was left standing");
			helper.succeed();
		});
	}

	// ---- never a liquid or fire as a drop spot ----------------------------------------------------------------------

	/**
	 * A lectern with its book on his crate in a storage room, a lava source right on the crate (not borrowable: a
	 * liquid): the lectern and its book lie there once each 60 ticks later (nothing burned), and the lava source is
	 * still there.
	 */
	//$ gametest_batch AREA '"qa1114c4StoreLava"'
	@GameTest(template = AREA, batch = "qa1114c4StoreLava")
	public void aLecternInAStorageRoomWithLavaOverHisCrateKeepsItsBook(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = QaMoverCrateHolds1114cGameTests.crateInside(helper, "qa1114c4storelava");
		QaMoverCrateHolds1114cGameTests.replaceChest(helper, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.HAS_BOOK, true),
			be -> ((LecternBlockEntity) be).setBook(new ItemStack(Items.WRITABLE_BOOK)));
		BlockPos on = helper.absolutePos(QaMoverCrateEdges1114bGameTests.CRATE.above());
		helper.runAfterDelay(2, () -> {
			QaMoverCrateEdges1114bGameTests.untilTheDrop(helper, s, () -> {
				if (!level.getFluidState(on).is(Fluids.LAVA)) {
					level.setBlock(on, Blocks.LAVA.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
				barrelsRoundBut(helper, on);
			});
			helper.assertTrue(level.getBlockState(on).is(Blocks.LAVA) && level.getFluidState(on).isSource(),
				"the lava source over his crate is now " + level.getBlockState(on));
			MoveSite site = MoverGameTests.work(level, s, x -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE && site.skipped() == 1, "the move ended " + site.stage() + ", skipped " + site.skipped());
			helper.runAfterDelay(60, () -> {
				QaMoverCrateEdges1114bGameTests.assertExactly(helper, Items.LECTERN, 1, "the lectern (lava over his crate in a storage room)");
				QaMoverCrateEdges1114bGameTests.assertExactly(helper, Items.WRITABLE_BOOK, 1, "the lectern's book (lava over his crate in a storage room)");
				helper.succeed();
			});
		});
	}

	/** QaMoverCrateEdges1114bGameTests.barrelsRound (a barrel with 1 amethyst shard on every spot round the crate, stone up the column), {@code keep} left as it is. */
	static void barrelsRoundBut(GameTestHelper helper, BlockPos keep) {
		ServerLevel level = helper.getLevel();
		for (BlockPos p : QaMoverCrateEdges1114bGameTests.round(helper)) {
			BlockState there = level.getBlockState(p);
			if (!p.equals(keep) && !there.hasBlockEntity() && !there.is(io.github.jcondedata.aliveworkplace.registry.ModBlocks.MOVERS_CRATE)) {
				level.setBlock(p, Blocks.BARREL.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				((Container) level.getBlockEntity(p)).setItem(0, new ItemStack(Items.AMETHYST_SHARD));
			}
		}
		for (BlockPos p : QaMoverCrateEdges1114bGameTests.column(helper)) {
			if (level.getBlockState(p).isAir() || level.getBlockState(p).canBeReplaced()) {
				level.setBlock(p, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			}
		}
	}

	/**
	 * A lectern with its book on his crate walled in with stone, fire burning right on the crate (a plain block with no
	 * block entity, but fire): the lectern and its book lie there once each 60 ticks later (nothing burned in the fire).
	 */
	//$ gametest_batch AREA '"qa1114c4WalledFire"'
	@GameTest(template = AREA, batch = "qa1114c4WalledFire")
	public void aLecternOnAWalledInCrateWithFireOnItKeepsItsBook(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qa1114c4fire");
		QaMoverCrateBorrow1114c3GameTests.lectern(helper);
		BlockPos crate = QaMoverCrateBorrow1114c3GameTests.crate(helper);
		helper.runAfterDelay(2, () -> {
			MoverCrateNoRoomB1234GameTests.work(level, s, x -> x.stage() != MoveSite.Stage.DISMANTLE);
			level.setBlock(crate.above(), Blocks.FIRE.defaultBlockState(), Block.UPDATE_CLIENTS);
			helper.assertTrue(level.getBlockState(crate.above()).is(Blocks.FIRE), "setup: no fire on his crate: " + level.getBlockState(crate.above()));
			QaMoverCrateBorrow1114c3GameTests.wallIn(helper, Blocks.STONE.defaultBlockState());
			MoveSite site = MoverCrateNoRoomB1234GameTests.work(level, s, x -> false);
			MoverCrateNoRoomB1234GameTests.assertEnded(helper, level, s, site, 1, "fire on his walled-in crate");
			helper.runAfterDelay(60, () -> {
				MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.LECTERN, 1, "the lectern (fire on his walled-in crate)");
				MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.WRITABLE_BOOK, 1, "the lectern's book (fire on his walled-in crate)");
				helper.succeed();
			});
		});
	}

	// ---- plain blocks, no block entity (B1253) ----------------------------------------------------------------------

	/** Packed ice on his crate: the packed ice itself once (a bare hand gets nothing), no water. */
	//$ gametest_batch AREA '"qa1114c4PackedIce"'
	@GameTest(template = AREA, batch = "qa1114c4PackedIce")
	public void packedIceOnHisCrateIsDroppedItselfOnce(GameTestHelper helper) {
		plainBlock(helper, "qa1114c4packedice", Blocks.PACKED_ICE.defaultBlockState(), Items.PACKED_ICE);
	}

	/** Blue ice on his crate: the blue ice itself once. */
	//$ gametest_batch AREA '"qa1114c4BlueIce"'
	@GameTest(template = AREA, batch = "qa1114c4BlueIce")
	public void blueIceOnHisCrateIsDroppedItselfOnce(GameTestHelper helper) {
		plainBlock(helper, "qa1114c4blueice", Blocks.BLUE_ICE.defaultBlockState(), Items.BLUE_ICE, Items.PACKED_ICE);
	}

	/** Tinted glass on his crate: the tinted glass itself once (not 4 amethyst shards or nothing). */
	//$ gametest_batch AREA '"qa1114c4TintedGlass"'
	@GameTest(template = AREA, batch = "qa1114c4TintedGlass")
	public void tintedGlassOnHisCrateIsDroppedItselfOnce(GameTestHelper helper) {
		plainBlock(helper, "qa1114c4tinted", Blocks.TINTED_GLASS.defaultBlockState(), Items.TINTED_GLASS, Items.AMETHYST_SHARD, Items.GLASS);
	}

	/** A glass pane on his crate: the pane itself once (a bare hand gets nothing). */
	//$ gametest_batch AREA '"qa1114c4GlassPane"'
	@GameTest(template = AREA, batch = "qa1114c4GlassPane")
	public void aGlassPaneOnHisCrateIsDroppedItselfOnce(GameTestHelper helper) {
		plainBlock(helper, "qa1114c4pane", Blocks.GLASS_PANE.defaultBlockState(), Items.GLASS_PANE, Items.GLASS);
	}

	/** A sea lantern on his crate: the sea lantern itself once, not prismarine crystals. */
	//$ gametest_batch AREA '"qa1114c4SeaLantern"'
	@GameTest(template = AREA, batch = "qa1114c4SeaLantern")
	public void aSeaLanternOnHisCrateIsDroppedItselfOnce(GameTestHelper helper) {
		plainBlock(helper, "qa1114c4lantern", Blocks.SEA_LANTERN.defaultBlockState(), Items.SEA_LANTERN, Items.PRISMARINE_CRYSTALS);
	}
}
