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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * QA bug tests (lane c, 2026-11-14, third pass) for B1236's class and B1172's promise, NOT registered: they fail on main.
 * B1236 expected "the block itself is dropped (as the packed block's item) with its contents" for a block that drops
 * only to Silk Touch. The fix drops the block's hand-broken loot, and its own item only when that loot is nothing, so a
 * block whose hand-broken loot is SOMETHING ELSE (an ender chest: 8 obsidian; a campfire: 2 charcoal) still loses the
 * block; and a beehive's bees (kept in its data) are deleted, where breaking it by hand lets them out. A lava block
 * borrowed over a walled-in crate burns what the dropped block held.
 */
public class QaMoverCrateBorrow1114c3BugGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/** The packed block lands on his crate with room above (the usual case). Runs the move to its end. */
	private static void moveIt(GameTestHelper helper, MoverGameTests.Setup s, String when) {
		ServerLevel level = helper.getLevel();
		MoveSite site = MoverCrateNoRoomB1234GameTests.work(level, s, x -> false);
		MoverCrateNoRoomB1234GameTests.assertEnded(helper, level, s, site, 1, when);
	}

	/** An ender chest where the chest stood: the player gets his ender chest back, not 8 obsidian. */
	//$ gametest_batch AREA '"qaC3EnderChestOnCrate"'
	@GameTest(template = AREA, batch = "qaC3EnderChestOnCrate")
	public void anEnderChestOnHisCrateIsDroppedAsAnEnderChest(GameTestHelper helper) {
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qac3ender");
		MoverCrateNoRoomB1234GameTests.replaceChest(helper, Blocks.ENDER_CHEST.defaultBlockState(), be -> { });
		helper.runAfterDelay(2, () -> {
			moveIt(helper, s, "an ender chest on his crate");
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.OBSIDIAN, 0, "the ender chest was turned into obsidian (B1236: the block itself is dropped)");
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.ENDER_CHEST, 1, "the player's ender chest");
			helper.succeed();
		});
	}

	/** An unlit campfire holding 2 raw beef where the chest stood: the campfire and both beef, not 2 charcoal. */
	//$ gametest_batch AREA '"qaC3CampfireOnCrate"'
	@GameTest(template = AREA, batch = "qaC3CampfireOnCrate")
	public void aCampfireOnHisCrateIsDroppedAsACampfireWithItsFood(GameTestHelper helper) {
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qac3campfire");
		MoverCrateNoRoomB1234GameTests.replaceChest(helper, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false), be -> {
			((CampfireBlockEntity) be).placeFood(null, new ItemStack(Items.BEEF), 600);
			((CampfireBlockEntity) be).placeFood(null, new ItemStack(Items.BEEF), 600);
		});
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(loose(helper, Items.BEEF) == 0, "setup: beef already lying loose");
			moveIt(helper, s, "a campfire on his crate");
			helper.assertTrue(QaMoverCrateHolds1114cGameTests.standing(helper, Blocks.CAMPFIRE) == 0, "a campfire was left standing");
			helper.assertTrue(loose(helper, Items.BEEF) == 2, "the campfire's 2 raw beef: " + loose(helper, Items.BEEF) + " lie there");
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.CHARCOAL, 0, "the campfire was turned into charcoal (B1236: the block itself is dropped)");
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.CAMPFIRE, 1, "the player's campfire");
			helper.succeed();
		});
	}

	/** A beehive with 2 bees in it where the chest stood: both bees still exist (let out, or in the dropped hive). */
	//$ gametest_batch AREA '"qaC3BeehiveOnCrate"'
	@GameTest(template = AREA, batch = "qaC3BeehiveOnCrate")
	public void aBeehiveOnHisCrateKeepsItsBees(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qac3hive");
		MoverCrateNoRoomB1234GameTests.replaceChest(helper, Blocks.BEEHIVE.defaultBlockState(), be -> {
			for (int i = 0; i < 2; i++) {
				Bee bee = EntityType.BEE.create(level);
				bee.moveTo(be.getBlockPos().getX() + 0.5, be.getBlockPos().getY() + 1, be.getBlockPos().getZ() + 0.5);
				level.addFreshEntity(bee);
				((BeehiveBlockEntity) be).addOccupant(bee);
			}
		});
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(bees(helper) == 2, "setup: " + bees(helper) + " bees, not the hive's 2");
			moveIt(helper, s, "a beehive on his crate");
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.BEEHIVE, 1, "the beehive");
			int now = bees(helper);
			helper.assertTrue(now == 2, "the hive's bees: " + now + " of 2 left anywhere (B1172: nothing packed is ever destroyed)");
			helper.succeed();
		});
	}

	/**
	 * A lava light over his walled-in crate (lava the only block borrowable above it, stone round): the lectern goes up
	 * there for a moment and its book is popped into the lava, where it burns.
	 */
	//$ gametest_batch AREA '"qaC3BorrowLava"'
	@GameTest(template = AREA, batch = "qaC3BorrowLava")
	public void aLecternOnACrateUnderLavaKeepsItsBook(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qac3lava");
		QaMoverCrateBorrow1114c3GameTests.lectern(helper);
		BlockPos crate = QaMoverCrateBorrow1114c3GameTests.crate(helper);
		helper.runAfterDelay(2, () -> {
			MoverCrateNoRoomB1234GameTests.work(level, s, x -> x.stage() != MoveSite.Stage.DISMANTLE);
			level.setBlock(crate.above(), Blocks.LAVA.defaultBlockState(), Block.UPDATE_CLIENTS);
			QaMoverCrateBorrow1114c3GameTests.wallIn(helper, Blocks.STONE.defaultBlockState());
			MoveSite site = MoverCrateNoRoomB1234GameTests.work(level, s, x -> false);
			MoverCrateNoRoomB1234GameTests.assertEnded(helper, level, s, site, 1, "lava over his walled-in crate");
			helper.runAfterDelay(40, () -> {
				MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.WRITABLE_BOOK, 1, "the lectern's book (popped into the borrowed lava over his crate)");
				MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.LECTERN, 1, "the lectern (popped into the borrowed lava over his crate)");
				helper.succeed();
			});
		});
	}

	private static int loose(GameTestHelper helper, Item item) {
		int n = 0;
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(48))) {
			n += e.getItem().is(item) ? e.getItem().getCount() : 0;
		}
		return n;
	}

	/** Bees anywhere: flying, in a beehive standing in the area, or inside a beehive item. */
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
