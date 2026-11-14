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
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * QA (2026-11-14, pass g) verifying the Mover's Crate drop fixes B1236, B1246-B1250 and B1253, written from the bugs'
 * expected behaviour and the wiki (moving-buildings.md: "nothing packed is ever deleted, however the crate is roofed"):
 * a block whose new spot holds a Mover's Crate is dropped as ITSELF (its own item, never its no-Silk-Touch loot) with
 * everything it holds, bees released or kept in the item, nothing burnt or swept away. These cover blocks and roofs the
 * earlier classes leave out: a glass pane, a potted poppy, four candles in one block, a sea lantern on a walled-in crate, a
 * brewing stand with potions, an unlit soul campfire with food among containers (a non-Container on the last resort), a
 * shulker box on a walled-in crate (its contents stay inside the box), a bee nest with bees on a walled-in crate through
 * reloads, a chiseled bookshelf on a walled-in crate with water spilling round it, and a jukebox with its disc on a
 * crate roofed and walled with barrels, reloaded before the drop. Same house, Mover and spot as
 * MoverCrateNoRoomB1234GameTests (the chest's spot lands on his crate).
 */
public class QaMoverCrateDrops1114gGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	private static BlockPos crate(GameTestHelper helper) {
		return helper.absolutePos(QaMoverCrateDrop1114eGameTests.OWN_CRATE);
	}

	private static void exactly(GameTestHelper helper, Item item, int n, String what) {
		MoverCrateNoRoomB1234GameTests.assertExactly(helper, item, n, what);
	}

	private static void noneStanding(GameTestHelper helper, Block block, String when) {
		int n = QaMoverCrateHolds1114cGameTests.standing(helper, block);
		helper.assertTrue(n == 0, when + ": " + n + " " + block + " left standing (it must be dropped, not put up somewhere)");
	}

	/** The rest of the move run to its end; it ends whole with the one block skipped. */
	private static MoveSite finish(GameTestHelper helper, MoverGameTests.Setup s, String when) {
		ServerLevel level = helper.getLevel();
		MoveSite site = MoverCrateNoRoomB1234GameTests.work(level, s, x -> false);
		MoverCrateNoRoomB1234GameTests.assertEnded(helper, level, s, site, 1, when);
		return site;
	}

	/** A glass pane (drops nothing without Silk Touch, no block entity) on his crate: one glass pane lies there. */
	//$ gametest_batch AREA '"qaG1114gPane"'
	@GameTest(template = AREA, batch = "qaG1114gPane")
	public void aGlassPaneOnHisCrateIsDroppedAsAPane(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qag1114gpane");
		QaMoverCrateVerify1114fGameTests.place(helper, Blocks.GLASS_PANE.defaultBlockState());
		helper.runAfterDelay(2, () -> {
			exactly(helper, Items.GLASS_PANE, 0, "setup");
			MoveSite site = QaMoverCrateVerify1114fGameTests.run(helper, s, () -> { });
			MoverCrateNoRoomB1234GameTests.assertEnded(helper, level, s, site, 1, "a glass pane on his crate");
			exactly(helper, Items.GLASS_PANE, 1, "the glass pane");
			exactly(helper, Items.GLASS, 0, "the pane turned into glass");
			helper.succeed();
		});
	}

	/** A potted poppy on his crate: the pot AND the poppy lie there (the block holds the flower), once each. */
	//$ gametest_batch AREA '"qaG1114gPottedPoppy"'
	@GameTest(template = AREA, batch = "qaG1114gPottedPoppy")
	public void aPottedPoppyOnHisCrateDropsThePotAndThePoppy(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qag1114gpot");
		QaMoverCrateVerify1114fGameTests.place(helper, Blocks.POTTED_POPPY.defaultBlockState());
		helper.runAfterDelay(2, () -> {
			exactly(helper, Items.POPPY, 0, "setup");
			MoveSite site = QaMoverCrateVerify1114fGameTests.run(helper, s, () -> { });
			MoverCrateNoRoomB1234GameTests.assertEnded(helper, level, s, site, 1, "a potted poppy on his crate");
			exactly(helper, Items.FLOWER_POT, 1, "the flower pot");
			exactly(helper, Items.POPPY, 1, "the poppy in the pot");
			noneStanding(helper, Blocks.POTTED_POPPY, "a potted poppy on his crate");
			helper.succeed();
		});
	}

	/** Four lit candles in one block (a bare hand gets all four too, but a drop must not shrink them to one): 4 candles. */
	//$ gametest_batch AREA '"qaG1114gCandles"'
	@GameTest(template = AREA, batch = "qaG1114gCandles")
	public void fourCandlesOnHisCrateAreDroppedAsFourCandles(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qag1114gcandles");
		QaMoverCrateVerify1114fGameTests.place(helper, Blocks.RED_CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 4).setValue(CandleBlock.LIT, true));
		helper.runAfterDelay(2, () -> {
			MoveSite site = QaMoverCrateVerify1114fGameTests.run(helper, s, () -> { });
			MoverCrateNoRoomB1234GameTests.assertEnded(helper, level, s, site, 1, "four candles on his crate");
			exactly(helper, Items.RED_CANDLE, 4, "the block's four red candles");
			noneStanding(helper, Blocks.RED_CANDLE, "four candles on his crate");
			helper.succeed();
		});
	}

	/**
	 * A sea lantern (bare hand: 2-3 prismarine crystals) on his crate walled in all round and 16 up (the stair on it
	 * borrowed): one sea lantern, no crystals, and the borrowed stair and the stone not doubled.
	 */
	//$ gametest_batch AREA '"qaG1114gSeaLanternWalled"'
	@GameTest(template = AREA, batch = "qaG1114gSeaLanternWalled")
	public void aSeaLanternOnAWalledInCrateIsDroppedAsASeaLantern(GameTestHelper helper) {
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qag1114glantern");
		QaMoverCrateVerify1114fGameTests.place(helper, Blocks.SEA_LANTERN.defaultBlockState());
		helper.runAfterDelay(2, () -> {
			QaMoverCrateEdges1114bGameTests.untilTheDrop(helper, s, () -> QaMoverCrateEdges1114bGameTests.wallIn(helper));
			finish(helper, s, "a sea lantern on his walled-in crate");
			exactly(helper, Items.SEA_LANTERN, 1, "the sea lantern");
			exactly(helper, Items.PRISMARINE_CRYSTALS, 0, "the sea lantern broke into crystals");
			exactly(helper, Items.OAK_STAIRS, 0, "the borrowed stair was doubled");
			exactly(helper, Items.STONE, 0, "the walling stone was doubled");
			helper.assertTrue(helper.getLevel().getBlockState(crate(helper).above()).equals(QaMoverCrateEdges1114bGameTests.STAIR),
				"the stair over his crate is now " + helper.getLevel().getBlockState(crate(helper).above()));
			helper.succeed();
		});
	}

	/** A brewing stand holding 3 potions and 4 glowstone dust on his crate: the stand, the potions and the dust, exactly. */
	//$ gametest_batch AREA '"qaG1114gBrewingStand"'
	@GameTest(template = AREA, batch = "qaG1114gBrewingStand")
	public void aBrewingStandOnHisCrateKeepsItsPotions(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qag1114gbrew");
		MoverCrateNoRoomB1234GameTests.replaceChest(helper, Blocks.BREWING_STAND.defaultBlockState(), be -> {
			for (int i = 0; i < 3; i++) {
				((Container) be).setItem(i, new ItemStack(Items.POTION));
			}
			((Container) be).setItem(3, new ItemStack(Items.GLOWSTONE_DUST, 4));
		});
		helper.runAfterDelay(2, () -> {
			exactly(helper, Items.POTION, 3, "setup: the stand's potions");
			MoveSite site = QaMoverCrateVerify1114fGameTests.run(helper, s, () -> { });
			MoverCrateNoRoomB1234GameTests.assertEnded(helper, level, s, site, 1, "a brewing stand on his crate");
			exactly(helper, Items.BREWING_STAND, 1, "the brewing stand");
			exactly(helper, Items.POTION, 3, "the stand's potions");
			exactly(helper, Items.GLOWSTONE_DUST, 4, "the stand's glowstone dust");
			noneStanding(helper, Blocks.BREWING_STAND, "a brewing stand on his crate");
			helper.succeed();
		});
	}

	/**
	 * An unlit soul campfire holding 2 raw mutton on a crate in a storage room (a barrel round it everywhere, stone up
	 * the column): a campfire isn't a container, so the last resort must still find its food. One soul campfire (not
	 * soul soil), both mutton, and every barrel round it keeps its one shard.
	 */
	//$ gametest_batch AREA '"qaG1114gSoulCampfireStore"'
	@GameTest(template = AREA, batch = "qaG1114gSoulCampfireStore")
	public void aSoulCampfireWithFoodOnACrateAmongContainersDropsItselfAndItsFood(GameTestHelper helper) {
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qag1114gsoulfire");
		MoverCrateNoRoomB1234GameTests.replaceChest(helper, Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false), be -> {
			((CampfireBlockEntity) be).placeFood(null, new ItemStack(Items.MUTTON), 600);
			((CampfireBlockEntity) be).placeFood(null, new ItemStack(Items.MUTTON), 600);
		});
		helper.runAfterDelay(2, () -> {
			exactly(helper, Items.MUTTON, 0, "setup: mutton already loose");
			QaMoverCrateEdges1114bGameTests.untilTheDrop(helper, s, () -> storeRoom(helper));
			int barrels = barrelsRoundWithShard(helper);
			int shards = QaMoverCrateDrop1114eGameTests.count(helper, Items.AMETHYST_SHARD); // also those in barrels the put-up built over
			finish(helper, s, "a soul campfire on his crate among containers");
			exactly(helper, Items.SOUL_CAMPFIRE, 1, "the soul campfire");
			exactly(helper, Items.COAL, 20, "the house's own barrel's coal");
			exactly(helper, Items.SOUL_SOIL, 0, "the soul campfire turned into soul soil");
			exactly(helper, Items.MUTTON, 2, "the campfire's mutton (only containers round the crate)");
			noneStanding(helper, Blocks.SOUL_CAMPFIRE, "a soul campfire among containers");
			helper.assertTrue(barrels > 0, "setup: no barrels round the crate");
			barrelsRoundWithShard(helper);
			exactly(helper, Items.AMETHYST_SHARD, shards, "the shards of the barrels and the chest round his crate, after the drop and after the move");
			helper.succeed();
		});
	}

	/**
	 * A shulker box holding 5 diamonds and 7 emeralds on his walled-in crate: one shulker box lies there with both stacks
	 * still inside it (a shulker box keeps its contents in its item), none spilled loose and no box left standing.
	 */
	//$ gametest_batch AREA '"qaG1114gShulkerWalled"'
	@GameTest(template = AREA, batch = "qaG1114gShulkerWalled")
	public void aShulkerBoxOnAWalledInCrateKeepsItsContentsInsideTheBox(GameTestHelper helper) {
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qag1114gshulker");
		MoverCrateNoRoomB1234GameTests.replaceChest(helper, Blocks.SHULKER_BOX.defaultBlockState(), be -> {
			((Container) be).setItem(0, new ItemStack(Items.DIAMOND, 5));
			((Container) be).setItem(26, new ItemStack(Items.EMERALD, 7));
		});
		helper.runAfterDelay(2, () -> {
			QaMoverCrateEdges1114bGameTests.untilTheDrop(helper, s, () -> QaMoverCrateEdges1114bGameTests.wallIn(helper));
			finish(helper, s, "a shulker box on his walled-in crate");
			noneStanding(helper, Blocks.SHULKER_BOX, "a shulker box on his walled-in crate");
			exactly(helper, Items.DIAMOND, 0, "diamonds spilled out of the shulker box (they belong inside its item)");
			exactly(helper, Items.EMERALD, 0, "emeralds spilled out of the shulker box (they belong inside its item)");
			int boxes = 0;
			int diamonds = 0;
			int emeralds = 0;
			for (ItemEntity e : loose(helper)) {
				if (e.getItem().is(Items.SHULKER_BOX)) {
					boxes += e.getItem().getCount();
					ItemContainerContents in = e.getItem().getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
					for (ItemStack held : in.nonEmptyItems()) {
						diamonds += held.is(Items.DIAMOND) ? held.getCount() : 0;
						emeralds += held.is(Items.EMERALD) ? held.getCount() : 0;
					}
				}
			}
			helper.assertTrue(boxes == 1, "the shulker box: " + boxes + " lie there, not 1");
			helper.assertTrue(diamonds == 5 && emeralds == 7, "inside the dropped shulker box: " + diamonds + " of 5 diamonds, "
				+ emeralds + " of 7 emeralds (B1172: nothing packed is ever destroyed)");
			helper.succeed();
		});
	}

	/**
	 * A bee nest (not a beehive) with 2 bees on his walled-in crate, the move saved and read back every 3 steps of the
	 * put-up: one bee nest, and both bees still exist (flying, or inside the dropped nest).
	 */
	//$ gametest_batch AREA '"qaG1114gBeeNestReload"'
	@GameTest(template = AREA, batch = "qaG1114gBeeNestReload")
	public void aBeeNestWithBeesOnAWalledInCrateKeepsItsBeesThroughReloads(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qag1114gnest");
		MoverCrateNoRoomB1234GameTests.replaceChest(helper, Blocks.BEE_NEST.defaultBlockState(), be -> {
			for (int i = 0; i < 2; i++) {
				Bee bee = EntityType.BEE.create(level);
				bee.moveTo(be.getBlockPos().getX() + 0.5, be.getBlockPos().getY() + 1, be.getBlockPos().getZ() + 0.5);
				level.addFreshEntity(bee);
				((BeehiveBlockEntity) be).addOccupant(bee);
			}
		});
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(bees(helper) == 2, "setup: " + bees(helper) + " bees, not the nest's 2");
			MoveSite site = MoverGameTests.work(level, s, x -> x.stage() == MoveSite.Stage.STRUCTURE || x.skipped() > 0);
			helper.assertTrue(site.stage() == MoveSite.Stage.STRUCTURE && site.skipped() == 0,
				"setup: the put-up didn't get to STRUCTURE before the drop: " + site.stage() + ", skipped " + site.skipped());
			int steps = 0;
			int reloads = 0;
			while (site.stage() != MoveSite.Stage.DONE && steps++ < MoverGameTests.MAX_STEPS) {
				if (site.skipped() == 0) {
					QaMoverCrateEdges1114bGameTests.wallIn(helper);
				}
				MoverWork.work(level, s.mover(), site);
				if (steps % 3 == 0 && site.stage() != MoveSite.Stage.DONE) {
					site = MoverGameTests.reload(level, s);
					reloads++;
				}
			}
			helper.assertTrue(reloads >= 3, "only " + reloads + " reloads");
			MoverCrateNoRoomB1234GameTests.assertEnded(helper, level, s, site, 1, "a bee nest on his walled-in crate, reloaded");
			exactly(helper, Items.BEE_NEST, 1, "the bee nest");
			noneStanding(helper, Blocks.BEE_NEST, "a bee nest on his walled-in crate");
			int now = bees(helper);
			helper.assertTrue(now == 2, "the nest's bees: " + now + " of 2 left anywhere (B1249: nothing packed is ever destroyed)");
			helper.succeed();
		});
	}

	/**
	 * A chiseled bookshelf with six books on his walled-in crate, a water source spilling over the walls 8 up: after
	 * the drop and 100 ticks of water running, the shelf and every book are still there, once each, nothing swept off.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaG1114gShelfWater"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaG1114gShelfWater")
	public void aChiseledShelfOnAWalledInCrateWithWaterRunningRoundKeepsEveryBook(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qag1114gshelfwater");
		QaMoverCrateVerify1114fGameTests.sixBooks(helper);
		BlockPos source = crate(helper).above(8);
		helper.runAfterDelay(2, () -> {
			QaMoverCrateEdges1114bGameTests.untilTheDrop(helper, s, () -> {
				QaMoverCrateEdges1114bGameTests.wallIn(helper);
				if (!level.getFluidState(source).isSource()) {
					level.setBlock(source, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
				}
			});
			finish(helper, s, "a chiseled shelf on his walled-in crate, water running");
			QaMoverCrateVerify1114fGameTests.assertSixBooksAndShelf(helper, "right after the drop, water running");
			helper.runAfterDelay(100, () -> {
				QaMoverCrateVerify1114fGameTests.assertSixBooksAndShelf(helper, "100 ticks after the drop, water running");
				helper.succeed();
			});
		});
	}

	/**
	 * A jukebox with a disc on his crate in a storage room (barrels round it everywhere, stone up the column), the move
	 * saved and read back right before the put-up reaches it: one jukebox, its disc, and every barrel keeps its shard.
	 */
	//$ gametest_batch AREA '"qaG1114gJukeboxStoreReload"'
	@GameTest(template = AREA, batch = "qaG1114gJukeboxStoreReload")
	public void aJukeboxOnACrateAmongContainersKeepsItsDiscOverAReload(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qag1114gjukebox");
		MoverCrateNoRoomB1234GameTests.replaceChest(helper, Blocks.JUKEBOX.defaultBlockState().setValue(JukeboxBlock.HAS_RECORD, true),
			be -> ((JukeboxBlockEntity) be).setTheItem(new ItemStack(Items.MUSIC_DISC_STRAD)));
		helper.runAfterDelay(2, () -> {
			exactly(helper, Items.MUSIC_DISC_STRAD, 1, "setup: the disc");
			MoveSite site = MoverGameTests.work(level, s, x -> x.stage() == MoveSite.Stage.STRUCTURE || x.skipped() > 0);
			helper.assertTrue(site.stage() == MoveSite.Stage.STRUCTURE && site.skipped() == 0, "setup: " + site.stage() + ", skipped " + site.skipped());
			MoverGameTests.reload(level, s);
			QaMoverCrateEdges1114bGameTests.untilTheDrop(helper, s, () -> storeRoom(helper));
			int barrels = barrelsRoundWithShard(helper);
			int shards = QaMoverCrateDrop1114eGameTests.count(helper, Items.AMETHYST_SHARD); // also those in barrels the put-up built over
			finish(helper, s, "a jukebox on his crate among containers, reloaded");
			exactly(helper, Items.JUKEBOX, 1, "the jukebox");
			exactly(helper, Items.MUSIC_DISC_STRAD, 1, "the jukebox's disc (only containers round the crate)");
			noneStanding(helper, Blocks.JUKEBOX, "a jukebox among containers");
			helper.assertTrue(barrels > 0, "setup: no barrels round the crate");
			barrelsRoundWithShard(helper);
			exactly(helper, Items.AMETHYST_SHARD, shards, "the shards of the barrels and the chest round his crate, after the drop and after the move");
			helper.succeed();
		});
	}

	/**
	 * A storage room round his crate: QaMoverCrateEdges1114bGameTests.barrelsRound (a barrel with one amethyst shard on
	 * every spot within 2 that has no block entity, stone up the column), except that the spot his crate would have
	 * moved to (2 south-east) holds a chest with one shard instead of a barrel: a barrel is a workstation, and one standing
	 * there takes the Mover's job site when the move ends (a separate bug, QaMoverCrateDrops1114gBugTests).
	 */
	static void storeRoom(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos moved = crate(helper).offset(2, 0, 2);
		if (level.getBlockState(moved).isAir()) {
			level.setBlock(moved, Blocks.CHEST.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			((Container) level.getBlockEntity(moved)).setItem(0, new ItemStack(Items.AMETHYST_SHARD));
		}
		QaMoverCrateEdges1114bGameTests.barrelsRound(helper);
	}

	/** Barrels (and the chest) round the crate, not the house's own barrel (it holds its 20 coal), each checked to still hold exactly its one shard; how many there are. */
	private static int barrelsRoundWithShard(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int n = 0;
		for (BlockPos p : QaMoverCrateEdges1114bGameTests.round(helper)) {
			boolean store = level.getBlockState(p).is(Blocks.BARREL) || level.getBlockState(p).is(Blocks.CHEST);
			if (store && level.getBlockEntity(p) instanceof Container c && !c.getItem(3).is(Items.COAL)) {
				n++;
				helper.assertTrue(c.getItem(0).is(Items.AMETHYST_SHARD) && c.getItem(0).getCount() == 1,
					"the barrel at " + helper.relativePos(p) + " round his crate lost its shard: " + c.getItem(0));
				for (int i = 1; i < c.getContainerSize(); i++) {
					helper.assertTrue(c.getItem(i).isEmpty(), "the barrel at " + helper.relativePos(p) + " got " + c.getItem(i) + " in slot " + i);
				}
			}
		}
		return n;
	}

	private static List<ItemEntity> loose(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(48));
	}

	/** Bees anywhere: flying, in a hive or nest standing in the area, or inside a dropped hive or nest item. */
	private static int bees(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		AABB area = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(48);
		int n = level.getEntitiesOfClass(Bee.class, area).size();
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(29, 29, 29)))) {
			BlockEntity be = level.getBlockEntity(pos);
			if (be instanceof BeehiveBlockEntity hive) {
				n += hive.getOccupantCount();
			}
		}
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, area)) {
			List<BeehiveBlockEntity.Occupant> in = e.getItem().get(DataComponents.BEES);
			n += in == null ? 0 : in.size();
		}
		return n;
	}
}
