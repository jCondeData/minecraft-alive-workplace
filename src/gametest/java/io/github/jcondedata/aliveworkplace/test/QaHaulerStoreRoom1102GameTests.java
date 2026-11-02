package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.store.HaulerGolems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA for the Hauler Golem (29.15, B627) and the Storehouse's room (37.8: "stacks that still fit", as porters count it):
 * a Storehouse chest whose every slot holds something, but whose stacks still have room for the logs, is not full. A
 * player's busy Storehouse often has a stack of every kind; a Sophisticated barrel with stack upgrades always does.
 */
public class QaHaulerStoreRoom1102GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos STORE_CHEST = new BlockPos(4, 2, 4);
	private static final BlockPos[] CHESTS = {new BlockPos(12, 2, 27), new BlockPos(22, 2, 27), new BlockPos(26, 2, 15)};

	/**
	 * The village of HaulerEveryChestGameTests (three lumberjacks, 64 logs each), but the Storehouse chest holds one oak
	 * log in each of its 27 slots: room for 1,701 more. The hauler still brings all 192 logs in.
	 */
	//$ gametest_ticks_batch HUGE '4800' '"qaHaulerStoreRoom1102"'
	@GameTest(template = HUGE, timeoutTicks = 4800, batch = "qaHaulerStoreRoom1102")
	public void qaAStorehouseWithAStackInEverySlotStillTakesTheLogs(GameTestHelper helper) {
		HaulerEveryChestGameTests.village(helper);
		Container store = (Container) helper.getBlockEntity(STORE_CHEST);
		for (int i = 0; i < store.getContainerSize(); i++) {
			store.setItem(i, new ItemStack(Items.OAK_LOG, 1));
		}
		int before = store.getContainerSize();
		IronGolem golem = HaulerEveryChestGameTests.hauler(helper);
		helper.succeedWhen(() -> {
			int left = 0;
			for (BlockPos c : CHESTS) {
				left += HaulerEveryChestGameTests.logs(helper, c);
			}
			int stored = HaulerEveryChestGameTests.logs(helper, STORE_CHEST);
			helper.assertTrue(left == 0 && stored == before + 192 && HaulerGolems.load(golem).isEmpty(),
				"the lumberjacks still have " + left + " logs; the Storehouse chest (a log in every slot, room for "
					+ (64 * before - before) + ") has " + stored + " of " + (before + 192) + "; the golem carries " + HaulerGolems.load(golem));
		});
	}

	/** The same village with one free slot left in the Storehouse chest (26 slots of a log each): the hauler brings all 192 in. */
	//$ gametest_ticks_batch HUGE '4800' '"qaHaulerStoreRoomFreeSlot1102"'
	@GameTest(template = HUGE, timeoutTicks = 4800, batch = "qaHaulerStoreRoomFreeSlot1102")
	public void qaAStorehouseWithOneFreeSlotTakesTheLogs(GameTestHelper helper) {
		HaulerEveryChestGameTests.village(helper);
		Container store = (Container) helper.getBlockEntity(STORE_CHEST);
		for (int i = 0; i < store.getContainerSize() - 1; i++) {
			store.setItem(i, new ItemStack(Items.OAK_LOG, 1));
		}
		int before = store.getContainerSize() - 1;
		IronGolem golem = HaulerEveryChestGameTests.hauler(helper);
		helper.succeedWhen(() -> {
			int left = 0;
			for (BlockPos c : CHESTS) {
				left += HaulerEveryChestGameTests.logs(helper, c);
			}
			int stored = HaulerEveryChestGameTests.logs(helper, STORE_CHEST);
			helper.assertTrue(left == 0 && stored == before + 192 && HaulerGolems.load(golem).isEmpty(),
				"the lumberjacks still have " + left + " logs; the Storehouse chest has " + stored + " of " + (before + 192) + "; the golem carries " + HaulerGolems.load(golem));
		});
	}
}
