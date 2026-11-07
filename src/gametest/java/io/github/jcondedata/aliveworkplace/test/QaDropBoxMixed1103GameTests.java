package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.HaulerGolems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA (lane-b-1103-0338, 2026-11-03) for B847/B848 (Drop Boxes, nearest whose goods fit first) with B820 (part of a load
 * that fits is moved): the nearer Drop Box holds goods that partly fit, 32 cobblestone with no room and 8 logs with
 * room, the farther one 32 logs. The fit logs come out of the nearer box, its cobblestone stays, and then the farther
 * box is not hidden behind the cobblestone left: all 40 logs reach the Storehouse, nothing is lost or dropped. For the
 * porter and the Hauler Golem.
 */
public class QaDropBoxMixed1103GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String AREA = "aliveworkplace_test:big_area";

	private static int count(GameTestHelper helper, BlockPos pos, net.minecraft.world.item.Item item) {
		return QaStackRoom1102cGameTests.count(helper, pos, item);
	}

	private static int onGround(GameTestHelper helper) {
		int n = 0;
		for (ItemEntity drop : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(16))) {
			n += drop.getItem().getCount();
		}
		return n;
	}

	//$ gametest_ticks_batch AREA '3600' '"qa1103DropBoxMixedPorter"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qa1103DropBoxMixedPorter")
	public void aPorterTakesTheFitLogsOfAMixedNearerBoxThenTheFartherBoxsLogs(GameTestHelper helper) {
		Villager porter = QaLaneC1102eGameTests.porterTwoBoxes(helper, true);
		((Container) helper.getBlockEntity(QaLaneC1102eGameTests.P_NEAR_BOX)).setItem(1, new ItemStack(Items.OAK_LOG, 8));
		BlockPos chest = QaLaneC1102eGameTests.P_STORE_CHEST;
		BlockPos near = QaLaneC1102eGameTests.P_NEAR_BOX;
		BlockPos far = QaLaneC1102eGameTests.P_FAR_BOX;
		helper.succeedWhen(() -> {
			var bag = ModAttachments.BUILDER_BAG.getOrCreate(porter);
			int stored = count(helper, chest, Items.OAK_LOG);
			helper.assertTrue(stored == 41 && count(helper, near, Items.OAK_LOG) == 0 && count(helper, far, Items.OAK_LOG) == 0 && bag.count(Items.OAK_LOG) == 0,
				"the Storehouse has " + stored + " of 41 logs; the nearer box keeps " + count(helper, near, Items.OAK_LOG) + " logs, the farther "
					+ count(helper, far, Items.OAK_LOG) + "; the porter carries " + bag.count(Items.OAK_LOG));
			helper.assertTrue(count(helper, near, Items.COBBLESTONE) == 32 && bag.count(Items.COBBLESTONE) == 0 && onGround(helper) == 0,
				"the nearer box keeps " + count(helper, near, Items.COBBLESTONE) + " of 32 cobblestone, the porter carries " + bag.count(Items.COBBLESTONE)
					+ ", " + onGround(helper) + " items lie on the ground");
			helper.assertTrue(count(helper, chest, Items.COBBLESTONE) == 26 * 64, "the full Storehouse's cobblestone changed: " + count(helper, chest, Items.COBBLESTONE));
		});
	}

	//$ gametest_ticks_batch HUGE '6000' '"qa1103DropBoxMixedHauler"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qa1103DropBoxMixedHauler")
	public void aHaulerTakesTheFitLogsOfAMixedNearerBoxThenTheFartherBoxsLogs(GameTestHelper helper) {
		Container store = QaStackRoom1102cGameTests.storehouse(helper);
		BlockPos near = QaStackRoom1102cGameTests.HAUL_BOX;
		BlockPos far = QaLaneC1102eGameTests.FAR_BOX;
		helper.setBlock(near, ModBlocks.DROP_BOX);
		((Container) helper.getBlockEntity(near)).setItem(0, new ItemStack(Items.COBBLESTONE, 32));
		((Container) helper.getBlockEntity(near)).setItem(1, new ItemStack(Items.OAK_LOG, 8));
		helper.setBlock(far, ModBlocks.DROP_BOX);
		((Container) helper.getBlockEntity(far)).setItem(0, new ItemStack(Items.OAK_LOG, 32));
		QaStackRoom1102cGameTests.lumberjack(helper, QaStackRoom1102cGameTests.FAR_JACK_CHEST, 0);
		QaStackRoom1102cGameTests.fillEach(store, Items.COBBLESTONE, 64);
		store.setItem(26, new ItemStack(Items.OAK_LOG, 1));
		IronGolem golem = QaStackRoom1102cGameTests.hauler(helper);
		BlockPos chest = QaStackRoom1102cGameTests.STORE_CHEST;
		helper.succeedWhen(() -> {
			int stored = count(helper, chest, Items.OAK_LOG);
			helper.assertTrue(stored == 41 && count(helper, near, Items.OAK_LOG) == 0 && count(helper, far, Items.OAK_LOG) == 0 && HaulerGolems.load(golem).isEmpty(),
				"the Storehouse has " + stored + " of 41 logs; the nearer box keeps " + count(helper, near, Items.OAK_LOG) + " logs, the farther "
					+ count(helper, far, Items.OAK_LOG) + "; the golem carries " + HaulerGolems.load(golem));
			helper.assertTrue(count(helper, near, Items.COBBLESTONE) == 32 && onGround(helper) == 0, "the nearer box keeps "
				+ count(helper, near, Items.COBBLESTONE) + " of 32 cobblestone, " + onGround(helper) + " items lie on the ground");
		});
	}
}
