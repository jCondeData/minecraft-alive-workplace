package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.HaulerGolems;
import io.github.jcondedata.aliveworkplace.store.Porters;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * QA for B806 (a Hauler Golem and a Storehouse chest whose every slot holds a part stack) and B820 (porters and such a
 * store chest), tested from the specs' "Expected": a store with room in its stacks for the load is not full, a store
 * whose stacks can't take the goods is, and no goods are ever lost or made on the way.
 */
public class QaStackRoom1102GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String AREA = "aliveworkplace_test:big_area";

	// the hauler's village (HaulerEveryChestGameTests.village)
	private static final BlockPos H_STORE_CHEST = new BlockPos(4, 2, 4);
	private static final BlockPos H_SECOND_CHEST = new BlockPos(4, 2, 7);
	private static final BlockPos[] H_CHESTS = {new BlockPos(12, 2, 27), new BlockPos(22, 2, 27), new BlockPos(26, 2, 15)};

	// the porter's village (PorterGameTests)
	private static final BlockPos MINERS_BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos MINERS_CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos STOREHOUSE = new BlockPos(19, 2, 19);
	private static final BlockPos STORE_CHEST = new BlockPos(19, 2, 17);
	private static final BlockPos DROP_BOX = new BlockPos(8, 2, 8);

	/**
	 * The Storehouse chest holds one cobblestone in each of its 27 slots: room for 1,701 cobblestone but none for a log.
	 * The hauler takes no logs (it has nowhere to put them), and all 192 stay in the lumberjacks' chests.
	 */
	//$ gametest_ticks_batch HUGE '2500' '"qaStackRoomHaulerNoFit"'
	@GameTest(template = HUGE, timeoutTicks = 2500, batch = "qaStackRoomHaulerNoFit")
	public void qaHaulerLeavesTheLogsWhenEveryStackIsCobblestone(GameTestHelper helper) {
		HaulerEveryChestGameTests.village(helper);
		Container store = helper.getBlockEntity(H_STORE_CHEST);
		fill(store, Items.COBBLESTONE, 1);
		IronGolem golem = HaulerEveryChestGameTests.hauler(helper);
		helper.runAfterDelay(2400, () -> {
			helper.assertTrue(jacksLogs(helper) == 192, "the lumberjacks have " + jacksLogs(helper) + " of 192 logs; the golem carries "
				+ HaulerGolems.load(golem) + "; the Storehouse chest has " + store.countItem(Items.OAK_LOG) + " logs");
			helper.assertTrue(HaulerGolems.load(golem).isEmpty(), "the golem carries " + HaulerGolems.load(golem) + " it can't store");
			helper.assertTrue(store.countItem(Items.OAK_LOG) == 0 && store.countItem(Items.COBBLESTONE) == 27,
				"the Storehouse chest has " + store.countItem(Items.OAK_LOG) + " logs and " + store.countItem(Items.COBBLESTONE) + " of 27 cobblestone");
			helper.succeed();
		});
	}

	/**
	 * The first Storehouse chest has a cobblestone in every slot (no room for logs); a second, empty chest by the
	 * Storehouse does have room: all 192 logs end up in the second chest.
	 */
	//$ gametest_ticks_batch HUGE '4800' '"qaStackRoomHaulerSecondChest"'
	@GameTest(template = HUGE, timeoutTicks = 4800, batch = "qaStackRoomHaulerSecondChest")
	public void qaHaulerTakesTheLogsToTheChestWithRoom(GameTestHelper helper) {
		HaulerEveryChestGameTests.village(helper);
		Container store = helper.getBlockEntity(H_STORE_CHEST);
		fill(store, Items.COBBLESTONE, 1);
		helper.setBlock(H_SECOND_CHEST, Blocks.CHEST);
		Container second = helper.getBlockEntity(H_SECOND_CHEST);
		IronGolem golem = HaulerEveryChestGameTests.hauler(helper);
		helper.succeedWhen(() -> helper.assertTrue(jacksLogs(helper) == 0 && second.countItem(Items.OAK_LOG) == 192
				&& store.countItem(Items.OAK_LOG) == 0 && store.countItem(Items.COBBLESTONE) == 27 && HaulerGolems.load(golem).isEmpty(),
			"the lumberjacks have " + jacksLogs(helper) + " logs; the second chest " + second.countItem(Items.OAK_LOG) + " of 192; the cobblestone chest "
				+ store.countItem(Items.OAK_LOG) + " logs and " + store.countItem(Items.COBBLESTONE) + " cobblestone; the golem carries " + HaulerGolems.load(golem)));
	}

	/**
	 * Boundary: 26 slots hold 64 cobblestone, the last holds 54 logs, so the chest has room for exactly 10 logs. The
	 * hauler brings exactly 10 (no more vanish on the way, none are left on it), and the rest stay with the lumberjacks.
	 */
	//$ gametest_ticks_batch HUGE '2500' '"qaStackRoomHaulerExact"'
	@GameTest(template = HUGE, timeoutTicks = 2500, batch = "qaStackRoomHaulerExact")
	public void qaHaulerBringsExactlyTheRoomThatIsLeft(GameTestHelper helper) {
		HaulerEveryChestGameTests.village(helper);
		Container store = helper.getBlockEntity(H_STORE_CHEST);
		roomForTenLogs(store);
		IronGolem golem = HaulerEveryChestGameTests.hauler(helper);
		helper.runAfterDelay(2400, () -> {
			helper.assertTrue(store.countItem(Items.OAK_LOG) == 64, "the Storehouse chest (room for 10) has " + store.countItem(Items.OAK_LOG) + " of 64 logs");
			helper.assertTrue(jacksLogs(helper) == 182, "the lumberjacks have " + jacksLogs(helper) + " of 182 logs; the golem carries " + HaulerGolems.load(golem));
			helper.assertTrue(HaulerGolems.load(golem).isEmpty(), "the golem carries " + HaulerGolems.load(golem));
			helper.assertTrue(store.countItem(Items.COBBLESTONE) == 26 * 64, "the cobblestone changed: " + store.countItem(Items.COBBLESTONE));
			helper.succeed();
		});
	}

	/**
	 * Boundary, one over: the chest has room for 10 logs and the golem already carries 11. Ten go in; the eleventh is
	 * not lost (it is still on the golem or back in a lumberjack's chest).
	 */
	//$ gametest_ticks_batch HUGE '1300' '"qaStackRoomHaulerOneOver"'
	@GameTest(template = HUGE, timeoutTicks = 1300, batch = "qaStackRoomHaulerOneOver")
	public void qaHaulerCarryingOneMoreThanTheRoomKeepsTheExtraLog(GameTestHelper helper) {
		HaulerEveryChestGameTests.village(helper);
		for (BlockPos c : H_CHESTS) {
			((Container) helper.getBlockEntity(c)).clearContent();
		}
		Container store = helper.getBlockEntity(H_STORE_CHEST);
		roomForTenLogs(store);
		IronGolem golem = HaulerEveryChestGameTests.hauler(helper);
		ModAttachments.HAULER_LOAD.set(golem, Map.of(Items.OAK_LOG, 11));
		helper.runAfterDelay(1200, () -> {
			int carried = HaulerGolems.load(golem).getOrDefault(Items.OAK_LOG, 0);
			helper.assertTrue(store.countItem(Items.OAK_LOG) == 64, "the Storehouse chest (room for 10) has " + store.countItem(Items.OAK_LOG) + " of 64 logs");
			helper.assertTrue(carried + jacksLogs(helper) == 1, "the eleventh log: the golem carries " + carried + ", the lumberjacks have "
				+ jacksLogs(helper) + " (one should be left between them)");
			helper.succeed();
		});
	}

	/**
	 * Save and reload on the way: the golem carries 64 logs toward a Storehouse chest with an oak log in every slot; it
	 * is saved and loaded again after 20 ticks. The reloaded golem still stores all 64 (27 + 64 = 91 in the chest).
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qaStackRoomHaulerReload"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaStackRoomHaulerReload")
	public void qaAReloadedHaulerStillStoresItsLoadInPartStacks(GameTestHelper helper) {
		HaulerEveryChestGameTests.village(helper);
		for (BlockPos c : H_CHESTS) {
			((Container) helper.getBlockEntity(c)).clearContent();
		}
		Container store = helper.getBlockEntity(H_STORE_CHEST);
		fill(store, Items.OAK_LOG, 1);
		IronGolem golem = HaulerEveryChestGameTests.hauler(helper);
		ModAttachments.HAULER_LOAD.set(golem, Map.of(Items.OAK_LOG, 64));
		IronGolem[] now = {golem};
		helper.runAfterDelay(20, () -> {
			now[0] = QaHaulerWaterVerify1102GameTests.reloaded(helper, golem);
			helper.assertTrue(HaulerGolems.load(now[0]).getOrDefault(Items.OAK_LOG, 0) == 64, "after the reload the golem carries " + HaulerGolems.load(now[0]));
		});
		helper.succeedWhen(() -> helper.assertTrue(now[0] != golem && store.countItem(Items.OAK_LOG) == 91 && HaulerGolems.load(now[0]).isEmpty()
				&& jacksLogs(helper) == 0,
			"the Storehouse chest has " + store.countItem(Items.OAK_LOG) + " of 91 logs; the reloaded golem carries " + HaulerGolems.load(now[0])
				+ "; the lumberjacks have " + jacksLogs(helper)));
	}

	/**
	 * A 16-stack item at the boundary: each of the store's 27 slots holds 15 ender pearls (room for 27), and the Drop Box
	 * holds 32. The porter brings exactly 27; the other 5 stay in the Drop Box, and none are lost or made.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaStackRoomPorterPearls"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaStackRoomPorterPearls")
	public void qaPorterBringsAsManyPearlsAsTheStacksHaveRoomFor(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		helper.setBlock(DROP_BOX, ModBlocks.DROP_BOX);
		Container box = helper.getBlockEntity(DROP_BOX);
		box.setItem(0, new ItemStack(Items.ENDER_PEARL, 16));
		box.setItem(1, new ItemStack(Items.ENDER_PEARL, 16));
		Villager porter = porter(helper);
		Container store = helper.getBlockEntity(STORE_CHEST);
		fill(store, Items.ENDER_PEARL, 15);
		helper.succeedWhen(() -> {
			int carried = ModAttachments.BUILDER_BAG.getOrCreate(porter).count(Items.ENDER_PEARL);
			helper.assertTrue(store.countItem(Items.ENDER_PEARL) + box.countItem(Items.ENDER_PEARL) + carried == 27 * 15 + 32,
				"pearls lost or made: store " + store.countItem(Items.ENDER_PEARL) + ", box " + box.countItem(Items.ENDER_PEARL) + ", porter " + carried);
			helper.assertTrue(store.countItem(Items.ENDER_PEARL) == 27 * 16 && box.countItem(Items.ENDER_PEARL) == 5 && carried == 0,
				"the store has " + store.countItem(Items.ENDER_PEARL) + " of 432 pearls, the box " + box.countItem(Items.ENDER_PEARL) + " of 5, the porter carries " + carried);
		});
	}

	/**
	 * Full in every sense: 14 slots hold 16 eggs and 13 a single saddle (both at their most), the Drop Box holds 20 eggs
	 * and 2 saddles. The store is full: after 500 ticks nothing has moved and the porter carries nothing.
	 */
	//$ gametest_ticks_batch AREA '600' '"qaStackRoomPorterMaxed"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaStackRoomPorterMaxed")
	public void qaPorterTakesNothingForAStoreOfMaxedEggsAndSaddles(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		helper.setBlock(DROP_BOX, ModBlocks.DROP_BOX);
		Container box = helper.getBlockEntity(DROP_BOX);
		box.setItem(0, new ItemStack(Items.EGG, 16));
		box.setItem(1, new ItemStack(Items.EGG, 4));
		box.setItem(2, new ItemStack(Items.SADDLE));
		box.setItem(3, new ItemStack(Items.SADDLE));
		Villager porter = porter(helper);
		Container store = helper.getBlockEntity(STORE_CHEST);
		for (int i = 0; i < store.getContainerSize(); i++) {
			store.setItem(i, i < 14 ? new ItemStack(Items.EGG, 16) : new ItemStack(Items.SADDLE));
		}
		helper.runAfterDelay(500, () -> {
			helper.assertTrue(box.countItem(Items.EGG) == 20 && box.countItem(Items.SADDLE) == 2,
				"the porter took from the Drop Box for a full store: it has " + box.countItem(Items.EGG) + " of 20 eggs and " + box.countItem(Items.SADDLE) + " of 2 saddles");
			helper.assertTrue(store.countItem(Items.EGG) == 14 * 16 && store.countItem(Items.SADDLE) == 13,
				"the store has " + store.countItem(Items.EGG) + " eggs and " + store.countItem(Items.SADDLE) + " saddles");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(porter).isEmpty(), "the porter carries " + ModAttachments.BUILDER_BAG.getOrCreate(porter).stacks());
			helper.succeed();
		});
	}

	/**
	 * A double chest (54 slots) and a barrel (27 slots) by the Storehouse, every slot holding one cobblestone: room in
	 * the stacks only. The porter still carries the miner's spare cobblestone into them.
	 */
	//$ gametest_ticks_batch AREA '1600' '"qaStackRoomPorterDoubleBarrel"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaStackRoomPorterDoubleBarrel")
	public void qaPorterFillsADoubleChestAndABarrelWithAStackInEverySlot(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		Leftovers.village(helper, 48);
		miner(helper);
		Container minersChest = helper.getBlockEntity(MINERS_CHEST);
		minersChest.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
		minersChest.setItem(1, new ItemStack(Items.COBBLESTONE, 64));
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		BlockPos left = STORE_CHEST, right = STORE_CHEST.east(), barrelPos = STORE_CHEST.west().west();
		helper.setBlock(left, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.TYPE, ChestType.LEFT));
		helper.setBlock(right, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.TYPE, ChestType.RIGHT));
		helper.setBlock(barrelPos, Blocks.BARREL);
		Container a = helper.getBlockEntity(left), b = helper.getBlockEntity(right), barrel = helper.getBlockEntity(barrelPos);
		fill(a, Items.COBBLESTONE, 1);
		fill(b, Items.COBBLESTONE, 1);
		fill(barrel, Items.COBBLESTONE, 1);
		Villager porter = spawnPorter(helper);
		int before = 81;
		int want = before + 128 - Porters.KEEP_FILLER;
		helper.succeedWhen(() -> {
			int stored = a.countItem(Items.COBBLESTONE) + b.countItem(Items.COBBLESTONE) + barrel.countItem(Items.COBBLESTONE);
			helper.assertTrue(stored == want && minersChest.countItem(Items.COBBLESTONE) == Porters.KEEP_FILLER
					&& ModAttachments.BUILDER_BAG.getOrCreate(porter).isEmpty(),
				"the double chest and barrel hold " + stored + " of " + want + " cobblestone; the miner has " + minersChest.countItem(Items.COBBLESTONE)
					+ "; the porter carries " + ModAttachments.BUILDER_BAG.getOrCreate(porter).stacks());
		});
	}

	/**
	 * A named stack doesn't merge into a plain part stack: every store slot holds one plain cobblestone and the Drop Box
	 * holds 20 cobblestone named "Keepsake". The named stack has no room; after 500 ticks it is still in the Drop Box,
	 * name and all, and the store's plain cobblestone is unchanged.
	 */
	//$ gametest_ticks_batch AREA '600' '"qaStackRoomPorterNamed"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaStackRoomPorterNamed")
	public void qaPorterLeavesANamedStackThatNoPartStackCanTake(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		helper.setBlock(DROP_BOX, ModBlocks.DROP_BOX);
		Container box = helper.getBlockEntity(DROP_BOX);
		ItemStack named = new ItemStack(Items.COBBLESTONE, 20);
		named.set(DataComponents.CUSTOM_NAME, Component.literal("Keepsake"));
		box.setItem(0, named);
		Villager porter = porter(helper);
		Container store = helper.getBlockEntity(STORE_CHEST);
		fill(store, Items.COBBLESTONE, 1);
		helper.runAfterDelay(500, () -> {
			int namedInBox = 0, namedInStore = 0;
			for (int i = 0; i < box.getContainerSize(); i++) {
				namedInBox += box.getItem(i).has(DataComponents.CUSTOM_NAME) ? box.getItem(i).getCount() : 0;
			}
			for (int i = 0; i < store.getContainerSize(); i++) {
				namedInStore += store.getItem(i).has(DataComponents.CUSTOM_NAME) ? store.getItem(i).getCount() : 0;
			}
			helper.assertTrue(namedInBox == 20, "the Drop Box has " + namedInBox + " of 20 named cobblestone (" + box.countItem(Items.COBBLESTONE)
				+ " cobblestone in all); the store has " + store.countItem(Items.COBBLESTONE) + " cobblestone, " + namedInStore
				+ " of it named; the porter carries " + ModAttachments.BUILDER_BAG.getOrCreate(porter).stacks());
			helper.assertTrue(store.countItem(Items.COBBLESTONE) == 27, "the store has " + store.countItem(Items.COBBLESTONE) + " of 27 cobblestone");
			helper.succeed();
		});
	}

	private static void fill(Container chest, Item item, int count) {
		for (int i = 0; i < chest.getContainerSize(); i++) {
			chest.setItem(i, new ItemStack(item, count));
		}
	}

	/** 26 slots of 64 cobblestone and one of 54 oak logs: room for exactly 10 logs. */
	private static void roomForTenLogs(Container store) {
		for (int i = 0; i < store.getContainerSize() - 1; i++) {
			store.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		}
		store.setItem(store.getContainerSize() - 1, new ItemStack(Items.OAK_LOG, 54));
	}

	private static int jacksLogs(GameTestHelper helper) {
		int n = 0;
		for (BlockPos c : H_CHESTS) {
			n += HaulerEveryChestGameTests.logs(helper, c);
		}
		return n;
	}

	private static Villager miner(GameTestHelper helper) {
		helper.setBlock(MINERS_BENCH, ModBlocks.MINERS_BENCH);
		helper.setBlock(MINERS_CHEST, Blocks.CHEST);
		Villager miner = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		io.github.jcondedata.aliveworkplace.mine.Miners.employ(helper.getLevel(), miner, helper.absolutePos(MINERS_BENCH));
		return miner;
	}

	/** The Storehouse with its single store chest and a porter (as PorterGameTests). */
	private static Villager porter(GameTestHelper helper) {
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		return spawnPorter(helper);
	}

	private static Villager spawnPorter(GameTestHelper helper) {
		Villager porter = helper.spawn(EntityType.VILLAGER, new BlockPos(18, 2, 18));
		Porters.employ(helper.getLevel(), porter, helper.absolutePos(STOREHOUSE));
		return porter;
	}
}
