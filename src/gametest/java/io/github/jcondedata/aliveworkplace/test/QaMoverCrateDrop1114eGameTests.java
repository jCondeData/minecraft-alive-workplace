package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.move.MovePlan;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoverWork;
import io.github.jcondedata.aliveworkplace.move.PendingMoves;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.function.Consumer;
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
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane e, 2026-11-14) for B1184, written from B1172/B1184's promise and the wiki (moving-buildings.md: "nothing
 * packed is ever destroyed: the block waits, goes back, or is dropped with its contents"). The Mover's own crate stands
 * inside the house and the move (two blocks south-east, not turned) puts the chest's spot on it, as in
 * QaMoverCrate1111bGameTests; here the block at the chest's spot is a jukebox with a disc, a decorated pot holding gold,
 * or a lectern with its book. Also: what already stands above the crate (a torch, a player's barrel) is left as it is;
 * with no free spot within 16 blocks above, the chest still keeps what it holds (the lectern's case is in
 * QaMoverCrateDrop1114eBugTests); and a save and reload after packing still drops the block with what it holds.
 */
public class QaMoverCrateDrop1114eGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos SPOT = new BlockPos(4, 3, 4);
	/** His only Mover's Crate, inside the house: where SPOT's block lands with the move to (4, 2, 4). */
	static final BlockPos OWN_CRATE = new BlockPos(6, 3, 6);
	private static final BlockPos MOVER_AT = new BlockPos(24, 2, 24);

	/** MoverGameTests' house with his only crate inside and the move set so the block at SPOT lands on it; SPOT's block replaced. */
	static MoverGameTests.Setup onHisCrate(GameTestHelper helper, String name, BlockState block, Consumer<BlockEntity> fill) {
		MoverGameTests.Setup s = MoverGameTests.setup(helper, name);
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(12, 2, 11), Blocks.AIR);
		helper.setBlock(OWN_CRATE, ModBlocks.MOVERS_CRATE);
		s.mover().teleportTo(helper.absolutePos(MOVER_AT).getX() + 0.5, helper.absolutePos(MOVER_AT).getY(), helper.absolutePos(MOVER_AT).getZ() + 0.5);
		s.mover().getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(OWN_CRATE)));
		BlockPos absTo = helper.absolutePos(new BlockPos(4, 2, 4));
		PendingMoves.Move pending = PendingMoves.get(level).forBuilding(s.from()).orElseThrow();
		PendingMoves.get(level).remove(s.from());
		PendingMoves.get(level).put(new PendingMoves.Move(pending.hall(), pending.from(), pending.structure(), absTo, Rotation.NONE, null));
		if (block != null) {
			BlockPos at = helper.absolutePos(SPOT);
			Clearable.tryClear(level.getBlockEntity(at));
			level.setBlock(at, block, Block.UPDATE_CLIENTS);
			helper.assertTrue(level.getBlockState(at).is(block.getBlock()) && level.getBlockEntity(at) != null, "setup: no " + block.getBlock() + " at the spot");
			fill.accept(level.getBlockEntity(at));
		}
		MoverGameTests.Setup moved = new MoverGameTests.Setup(s.placement(), s.before(), s.hall(), s.from(), absTo, s.mover());
		boolean lands = false;
		for (MovePlan.Placed p : moved.before().placed(absTo, Rotation.NONE)) {
			lands |= p.pos().equals(helper.absolutePos(OWN_CRATE));
		}
		helper.assertTrue(lands, "setup: nothing of the house lands on his crate");
		return moved;
	}

	/** How many {@code item} there are round the test: in containers, lying loose, or carried by villagers. */
	static int count(GameTestHelper helper, Item item) {
		ServerLevel level = helper.getLevel();
		int n = 0;
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(29, 25, 29)))) {
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
		AABB area = new AABB(helper.absolutePos(new BlockPos(0, 1, 0))).inflate(40);
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

	/** The move run to its end (after {@code between} runs once the house is down); done, his crate still his. */
	static MoveSite moveToTheEnd(GameTestHelper helper, MoverGameTests.Setup s, Runnable between) {
		ServerLevel level = helper.getLevel();
		MoveSite site = MoverGameTests.work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
		helper.assertTrue(site.stage() == MoveSite.Stage.FOUNDATION, "the house didn't come down: " + site.stage());
		between.run();
		site = MoverGameTests.work(level, s, x -> false);
		helper.assertTrue(site.stage() == MoveSite.Stage.DONE && !site.canceled(), "the move ended " + site.stage() + ", waiting " + site.waiting());
		helper.assertTrue(level.getBlockState(helper.absolutePos(OWN_CRATE)).is(ModBlocks.MOVERS_CRATE), "his crate isn't where it stood");
		helper.assertTrue(MoverWork.hasCrate(level, s.mover()), "the Mover lost his crate");
		helper.assertTrue(site.skipped() == 1, "skipped " + site.skipped() + ", not just the block where his crate stands");
		return site;
	}

	/** Solid stone in the 16 blocks right above his crate: no free spot for the dropped block to go up at. */
	static void roofOverHisCrate(GameTestHelper helper) {
		for (int dy = 1; dy <= 16; dy++) {
			helper.getLevel().setBlock(helper.absolutePos(OWN_CRATE.above(dy)), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	/** A jukebox playing nothing but holding a disc: the jukebox and its disc are both still somewhere after the move. */
	//$ gametest_batch AREA '"qaE1114CrateJukebox"'
	@GameTest(template = AREA, batch = "qaE1114CrateJukebox")
	public void aJukeboxWhoseSpotIsHisCrateKeepsItsDisc(GameTestHelper helper) {
		MoverGameTests.Setup s = onHisCrate(helper, "qae1114jukebox", Blocks.JUKEBOX.defaultBlockState().setValue(JukeboxBlock.HAS_RECORD, true),
			be -> ((JukeboxBlockEntity) be).setTheItem(new ItemStack(Items.MUSIC_DISC_CAT)));
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(count(helper, Items.MUSIC_DISC_CAT) == 1, "setup: the disc isn't in the jukebox");
			moveToTheEnd(helper, s, () -> { });
			helper.assertTrue(count(helper, Items.MUSIC_DISC_CAT) == 1, "the jukebox's disc: " + count(helper, Items.MUSIC_DISC_CAT) + " left of 1 (B1172)");
			helper.assertTrue(count(helper, Items.JUKEBOX) == 1, "the jukebox: " + count(helper, Items.JUKEBOX) + " lie loose, not 1");
			helper.assertTrue(count(helper, Items.BREAD) == 0 && count(helper, Items.EMERALD) == 0, "the old chest's items came back from somewhere");
			helper.succeed();
		});
	}

	/** A decorated pot holding 10 gold ingots: the pot and its gold are both still somewhere after the move. */
	//$ gametest_batch AREA '"qaE1114CratePot"'
	@GameTest(template = AREA, batch = "qaE1114CratePot")
	public void aDecoratedPotWhoseSpotIsHisCrateKeepsWhatItHolds(GameTestHelper helper) {
		MoverGameTests.Setup s = onHisCrate(helper, "qae1114pot", Blocks.DECORATED_POT.defaultBlockState(),
			be -> ((DecoratedPotBlockEntity) be).setTheItem(new ItemStack(Items.GOLD_INGOT, 10)));
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(count(helper, Items.GOLD_INGOT) == 10, "setup: the pot holds " + count(helper, Items.GOLD_INGOT));
			moveToTheEnd(helper, s, () -> { });
			helper.assertTrue(count(helper, Items.GOLD_INGOT) == 10, "the pot's gold: " + count(helper, Items.GOLD_INGOT) + " left of 10 (B1172)");
			helper.assertTrue(count(helper, Items.DECORATED_POT) == 1, "the pot: " + count(helper, Items.DECORATED_POT) + " lie loose, not 1");
			helper.succeed();
		});
	}

	/**
	 * What already stands above his crate is left as it stands: a torch on the crate and a player's barrel (7 amethyst
	 * shards) above it. The chest goes up at the first free spot above them, and every item of it is still there.
	 */
	//$ gametest_batch AREA '"qaE1114CrateAbove"'
	@GameTest(template = AREA, batch = "qaE1114CrateAbove")
	public void whatStandsAboveHisCrateIsLeftAsItStands(GameTestHelper helper) {
		MoverGameTests.Setup s = onHisCrate(helper, "qae1114above", null, null);
		ServerLevel level = helper.getLevel();
		BlockPos torch = helper.absolutePos(OWN_CRATE.above());
		BlockPos barrel = helper.absolutePos(OWN_CRATE.above(2));
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(count(helper, Items.BREAD) == 12 && count(helper, Items.EMERALD) == 64, "setup: the chest's items");
			moveToTheEnd(helper, s, () -> {
				level.setBlock(torch, Blocks.TORCH.defaultBlockState(), Block.UPDATE_CLIENTS);
				level.setBlock(barrel, Blocks.BARREL.defaultBlockState(), Block.UPDATE_CLIENTS);
				((Container) level.getBlockEntity(barrel)).setItem(5, new ItemStack(Items.AMETHYST_SHARD, 7));
			});
			helper.assertTrue(level.getBlockState(torch).is(Blocks.TORCH), "the torch on his crate is gone: " + level.getBlockState(torch));
			helper.assertTrue(level.getBlockState(barrel).is(Blocks.BARREL), "the player's barrel above his crate is gone: " + level.getBlockState(barrel));
			helper.assertTrue(level.getBlockEntity(barrel) instanceof Container c && c.getItem(5).is(Items.AMETHYST_SHARD) && c.getItem(5).getCount() == 7,
				"the player's barrel lost its 7 amethyst shards");
			helper.assertTrue(count(helper, Items.AMETHYST_SHARD) == 7, "amethyst shards: " + count(helper, Items.AMETHYST_SHARD) + ", not 7");
			helper.assertTrue(count(helper, Items.TORCH) == 0, "a torch was dropped: " + count(helper, Items.TORCH));
			helper.assertTrue(count(helper, Items.BREAD) == 12 && count(helper, Items.EMERALD) == 64,
				"the chest's items: " + count(helper, Items.BREAD) + " bread and " + count(helper, Items.EMERALD) + " emeralds of 12 and 64");
			helper.assertTrue(count(helper, Items.CHEST) == 1, "the chest: " + count(helper, Items.CHEST) + " lie loose, not 1");
			helper.succeed();
		});
	}

	/** No free spot within 16 blocks above his crate: the chest and its 12 bread and 64 emeralds are still dropped there. */
	//$ gametest_batch AREA '"qaE1114CrateRoofChest"'
	@GameTest(template = AREA, batch = "qaE1114CrateRoofChest")
	public void aChestWithNoRoomAboveHisCrateKeepsItsItems(GameTestHelper helper) {
		MoverGameTests.Setup s = onHisCrate(helper, "qae1114roofchest", null, null);
		helper.runAfterDelay(2, () -> {
			moveToTheEnd(helper, s, () -> roofOverHisCrate(helper));
			helper.assertTrue(count(helper, Items.BREAD) == 12 && count(helper, Items.EMERALD) == 64,
				"the chest's items with no room above his crate: " + count(helper, Items.BREAD) + " bread and " + count(helper, Items.EMERALD) + " emeralds of 12 and 64");
			helper.assertTrue(count(helper, Items.CHEST) == 1, "the chest: " + count(helper, Items.CHEST) + " lie loose, not 1");
			helper.succeed();
		});
	}

	/** Packed, then saved and reloaded twice before the lectern's spot comes up: the lectern and its book are dropped all the same. */
	//$ gametest_batch AREA '"qaE1114CrateReload"'
	@GameTest(template = AREA, batch = "qaE1114CrateReload")
	public void aLecternOnHisCrateKeepsItsBookOverAReload(GameTestHelper helper) {
		MoverGameTests.Setup s = onHisCrate(helper, "qae1114reload", Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.HAS_BOOK, true),
			be -> ((LecternBlockEntity) be).setBook(new ItemStack(Items.WRITABLE_BOOK)));
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(count(helper, Items.WRITABLE_BOOK) == 1, "setup: no book on the lectern");
			moveToTheEnd(helper, s, () -> {
				MoverGameTests.reload(level, s);
				MoverGameTests.work(level, s, x -> x.stage() == MoveSite.Stage.STRUCTURE);
				MoverGameTests.reload(level, s);
			});
			helper.assertTrue(count(helper, Items.WRITABLE_BOOK) == 1, "the lectern's book after a reload: " + count(helper, Items.WRITABLE_BOOK) + " left of 1");
			helper.assertTrue(count(helper, Items.LECTERN) == 1, "the lectern after a reload: " + count(helper, Items.LECTERN) + " lie loose, not 1");
			helper.succeed();
		});
	}
}
