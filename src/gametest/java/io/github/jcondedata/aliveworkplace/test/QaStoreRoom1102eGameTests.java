package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.Porters;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-e-1102-1323), B820 from its spec: a porter asks whether the load fits the store, so a store chest with a part
 * stack in every slot takes what it has room for and no more. These cover the edges PorterGameTests skips: room only for
 * other items, room for part of the load, room for exactly the load spread over several stacks, and a store that fills up
 * while the porter is on his way and the porter saved and loaded meanwhile. Each counts every cobblestone (the miner's
 * chest, the porter's bag, the store, items on the ground) so nothing is lost or made.
 */
public class QaStoreRoom1102eGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos MINERS_BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos MINERS_CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos STOREHOUSE = new BlockPos(19, 2, 19);
	private static final BlockPos STORE_CHEST = new BlockPos(19, 2, 17);

	/**
	 * Wrong-kind room: one dirt in each of the store's 27 slots (room for 1,701 more dirt, none for stone). The porter
	 * must not carry the miner's spare cobblestone off: it stays in the miner's chest, nothing is dropped.
	 */
	//$ gametest_ticks_batch AREA '700' '"qa_store_room_wrong_kind"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "qa_store_room_wrong_kind")
	public void porterLeavesTheStoneForAStoreWithRoomOnlyForDirt(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000); // work hours
		Leftovers.village(helper, 48);
		Container minersChest = miner(helper, 128);
		Villager porter = porter(helper);
		Container store = helper.getBlockEntity(STORE_CHEST);
		for (int i = 0; i < store.getContainerSize(); i++) {
			store.setItem(i, new ItemStack(Items.DIRT, 1));
		}
		helper.assertTrue(SupplyContainers.freeSlots(helper.getLevel(), List.of(helper.absolutePos(STORE_CHEST))) == 0, "setup: the store chest has an empty slot");
		helper.runAfterDelay(600, () -> {
			helper.assertTrue(minersChest.countItem(Items.COBBLESTONE) == 128, "the porter took " + (128 - minersChest.countItem(Items.COBBLESTONE))
				+ " cobblestone for a store with room only for dirt");
			helper.assertTrue(bag(porter, Items.COBBLESTONE) == 0, "the porter carries " + ModAttachments.BUILDER_BAG.getOrCreate(porter).stacks()
				+ " he can't store anywhere");
			helper.assertTrue(store.countItem(Items.COBBLESTONE) == 0 && store.countItem(Items.DIRT) == 27,
				"the store holds " + store.countItem(Items.COBBLESTONE) + " cobblestone and " + store.countItem(Items.DIRT) + " dirt, not 0 and 27");
			helper.assertTrue(dropped(helper) == 0, dropped(helper) + " items lie on the ground");
			helper.succeed();
		});
	}

	/**
	 * Partial fit: 63 cobblestone in each of the 27 slots (room for 27) and 96 spare at the miner's. Exactly 27 go to the
	 * store, the other 69 stay with the miner (32 kept + 69 = 101), none in the bag or on the ground.
	 */
	//$ gametest_ticks_batch AREA '1600' '"qa_store_room_partial"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qa_store_room_partial")
	public void porterCarriesOnlyWhatTheStoreHasRoomFor(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000); // work hours
		Leftovers.village(helper, 48);
		Container minersChest = miner(helper, 128);
		Villager porter = porter(helper);
		Container store = helper.getBlockEntity(STORE_CHEST);
		for (int i = 0; i < store.getContainerSize(); i++) {
			store.setItem(i, new ItemStack(Items.COBBLESTONE, 63));
		}
		int total = 128 + 27 * 63;
		helper.succeedWhen(() -> {
			int inStore = store.countItem(Items.COBBLESTONE);
			int atMiner = minersChest.countItem(Items.COBBLESTONE);
			int inBag = bag(porter, Items.COBBLESTONE);
			int ground = dropped(helper);
			helper.assertTrue(atMiner + inBag + inStore + ground == total, "cobblestone lost or made: miner " + atMiner + " + bag " + inBag + " + store "
				+ inStore + " + ground " + ground + " != " + total);
			helper.assertTrue(inStore == 27 * 64, "the store (room for 27) holds " + inStore + ", not " + 27 * 64);
			helper.assertTrue(inBag == 0, "the porter still carries " + inBag + " cobblestone the store can't take");
			helper.assertTrue(atMiner == 128 - 27, "the miner has " + atMiner + " cobblestone, not " + (128 - 27));
			helper.assertTrue(ground == 0, ground + " items lie on the ground");
		});
	}

	/**
	 * Exact boundary, room spread over several part stacks: four slots of 40 cobblestone (room 24 each, 96 in all) and
	 * the other 23 full; the miner's spare is 96. All 96 go in, the store is brim-full, the miner keeps his 32.
	 */
	//$ gametest_ticks_batch AREA '1600' '"qa_store_room_exact"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qa_store_room_exact")
	public void porterFillsRoomSpreadOverFourStacksToTheBrim(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000); // work hours
		Leftovers.village(helper, 48);
		Container minersChest = miner(helper, 128);
		Villager porter = porter(helper);
		Container store = helper.getBlockEntity(STORE_CHEST);
		for (int i = 0; i < store.getContainerSize(); i++) {
			store.setItem(i, new ItemStack(Items.COBBLESTONE, i % 7 == 3 ? 40 : 64)); // slots 3, 10, 17, 24
		}
		int before = store.countItem(Items.COBBLESTONE);
		helper.assertTrue(27 * 64 - before == 128 - Porters.KEEP_FILLER, "setup: the store's room is " + (27 * 64 - before) + ", not the miner's spare");
		helper.succeedWhen(() -> {
			int inStore = store.countItem(Items.COBBLESTONE);
			int atMiner = minersChest.countItem(Items.COBBLESTONE);
			int inBag = bag(porter, Items.COBBLESTONE);
			helper.assertTrue(inStore == 27 * 64, "the store holds " + inStore + " cobblestone, not brim-full " + 27 * 64);
			helper.assertTrue(atMiner == Porters.KEEP_FILLER, "the miner has " + atMiner + ", not the " + Porters.KEEP_FILLER + " he keeps");
			helper.assertTrue(inBag == 0, "the porter still carries " + inBag);
			helper.assertTrue(dropped(helper) == 0, dropped(helper) + " items lie on the ground");
		});
	}

	/**
	 * The store fills up mid-trip: a cobblestone in every slot when the porter loads up at the miner's; while he walks
	 * back a player tops every slot up to 64, and the porter is saved and loaded. He keeps the load (none dropped, none
	 * lost); once the player empties the store he puts all of it in.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qa_store_room_filled_mid_trip"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qa_store_room_filled_mid_trip")
	public void aStoreFilledWhileThePorterWalksLosesNothingAcrossAReload(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000); // work hours
		Leftovers.village(helper, 48);
		Container minersChest = miner(helper, 128);
		Villager[] porter = {porter(helper)};
		Container store = helper.getBlockEntity(STORE_CHEST);
		for (int i = 0; i < store.getContainerSize(); i++) {
			store.setItem(i, new ItemStack(Items.COBBLESTONE, 1));
		}
		int load = 128 - Porters.KEEP_FILLER;
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(bag(porter[0], Items.COBBLESTONE) == load, "the porter hasn't loaded up yet: bag "
				+ ModAttachments.BUILDER_BAG.getOrCreate(porter[0]).stacks()))
			.thenExecute(() -> {
				helper.assertTrue(store.countItem(Items.COBBLESTONE) == 27, "setup: the porter stored before the player filled the store: "
					+ store.countItem(Items.COBBLESTONE));
				for (int i = 0; i < store.getContainerSize(); i++) {
					store.setItem(i, new ItemStack(Items.COBBLESTONE, 64)); // the player tops it up
				}
				CompoundTag saved = new CompoundTag();
				porter[0].save(saved);
				porter[0].discard();
				Villager copy = (Villager) EntityType.loadEntityRecursive(saved, helper.getLevel(), e -> e);
				helper.assertTrue(copy != null, "the porter didn't load");
				helper.assertTrue(bag(copy, Items.COBBLESTONE) == load, "the bag didn't survive the save: " + ModAttachments.BUILDER_BAG.getOrCreate(copy).stacks());
				helper.getLevel().addFreshEntity(copy);
				porter[0] = copy;
			})
			.thenIdle(400)
			.thenExecute(() -> {
				int inBag = bag(porter[0], Items.COBBLESTONE);
				int atMiner = minersChest.countItem(Items.COBBLESTONE);
				int inStore = store.countItem(Items.COBBLESTONE);
				int ground = dropped(helper);
				helper.assertTrue(inStore == 27 * 64, "the brim-full store holds " + inStore);
				helper.assertTrue(ground == 0, ground + " items lie on the ground by a brim-full store");
				helper.assertTrue(inBag + atMiner == 128, "with the store full, miner " + atMiner + " + bag " + inBag + " != 128 (cobblestone lost or made)");
				for (int i = 0; i < store.getContainerSize(); i++) {
					store.setItem(i, ItemStack.EMPTY); // the player empties the store
				}
			})
			.thenWaitUntil(() -> {
				int inBag = bag(porter[0], Items.COBBLESTONE);
				int atMiner = minersChest.countItem(Items.COBBLESTONE);
				int inStore = store.countItem(Items.COBBLESTONE);
				helper.assertTrue(inBag == 0, "the porter still carries " + inBag + " (store " + inStore + ", miner " + atMiner + ")");
				helper.assertTrue(inStore + atMiner == 128, "after the trip, store " + inStore + " + miner " + atMiner + " != 128");
				helper.assertTrue(dropped(helper) == 0, dropped(helper) + " items lie on the ground");
			})
			.thenSucceed();
	}

	private static int bag(Villager villager, Item item) {
		return ModAttachments.BUILDER_BAG.getOrCreate(villager).count(item);
	}

	private static int dropped(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(4)).stream().mapToInt(e -> e.getItem().getCount()).sum();
	}

	/** A miner at his bench with {@code cobblestone} in his chest. */
	private static Container miner(GameTestHelper helper, int cobblestone) {
		helper.setBlock(MINERS_BENCH, ModBlocks.MINERS_BENCH);
		helper.setBlock(MINERS_CHEST, Blocks.CHEST);
		Villager miner = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		io.github.jcondedata.aliveworkplace.mine.Miners.employ(helper.getLevel(), miner, helper.absolutePos(MINERS_BENCH));
		Container chest = helper.getBlockEntity(MINERS_CHEST);
		for (int i = 0; cobblestone > 0; i++) {
			int n = Math.min(64, cobblestone);
			chest.setItem(i, new ItemStack(Items.COBBLESTONE, n));
			cobblestone -= n;
		}
		return chest;
	}

	private static Villager porter(GameTestHelper helper) {
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		Villager porter = helper.spawn(EntityType.VILLAGER, new BlockPos(18, 2, 18));
		Porters.employ(helper.getLevel(), porter, helper.absolutePos(STOREHOUSE));
		return porter;
	}
}
