package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.mechanic.MachineJob;
import io.github.jcondedata.aliveworkplace.mechanic.MachineWork;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA for 39.3 (villagers work Create's machines, "a full store pauses the work"), with the real Create: what happens
 * when the store changes while the worker is on the way to fetch the input. The player fills the store to the last slot
 * after the farmer set off for the turning millstone: the farmer takes nothing (no room for the flour). The player takes
 * the wheat out instead: the farmer gives up the trip and is free again. Kills the nightly survivor MachineWork.java:572
 * ({@code planned.isEmpty() || freeSlots <= 0} as {@code &&}).
 */
public class QaMachineFetchCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos STATION = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	/** The farmer at the composter, the turning millstone and the store, the farmer already on a fetch trip to the mill. */
	private static Villager onTheWay(GameTestHelper helper, Container[] store) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		MachineWorkCompatTests.clearMachines(helper);
		MachineWorkCompatTests.villageWorkshop(helper);
		helper.setBlock(CHEST, Blocks.CHEST.defaultBlockState());
		store[0] = (Container) helper.getBlockEntity(CHEST);
		store[0].setItem(0, new ItemStack(Items.WHEAT, 20));
		BlockPos mill = MachineWorkCompatTests.rig(helper, MachineWorkCompatTests.Kind.MILL, true);
		helper.setBlock(STATION, Blocks.COMPOSTER);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Stations.assign(helper.getLevel(), villager, helper.absolutePos(STATION), MachineWorkCompatTests.Kind.MILL.profession());
		helper.setDayTime(2000);
		ModAttachments.MACHINE_JOB.set(villager, new MachineJob(helper.absolutePos(mill), AliveWorkplace.id("millstone"), MachineJob.Stage.FETCH, List.of()));
		return villager;
	}

	private static int wheat(Container c) {
		return c.countItem(Items.WHEAT);
	}

	//$ gametest_ticks_batch AREA '600' '"qa_machine_fetch_full"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qa_machine_fetch_full")
	public void aStoreFilledWhileTheWorkerWalksToFetchIsLeftAlone(GameTestHelper helper) {
		Container[] store = {null};
		Villager villager = onTheWay(helper, store);
		for (int i = 1; i < store[0].getContainerSize(); i++) {
			store[0].setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		}
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(wheat(store[0]) == 20, "the store is full but " + (20 - wheat(store[0])) + " wheat went to the millstone");
			helper.assertTrue(!MachineWork.busy(villager), "the farmer is still on the trip: " + ModAttachments.MACHINE_JOB.get(villager));
			MachineWorkCompatTests.clearMachines(helper);
			helper.succeed();
		});
	}

	//$ gametest_ticks_batch AREA '600' '"qa_machine_fetch_gone"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qa_machine_fetch_gone")
	public void inputTakenAwayWhileTheWorkerWalksToFetchEndsTheTrip(GameTestHelper helper) {
		Container[] store = {null};
		Villager villager = onTheWay(helper, store);
		store[0].setItem(0, ItemStack.EMPTY);
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(villager.isAlive(), "the farmer is gone");
			helper.assertTrue(!MachineWork.busy(villager), "the farmer is still on a trip with nothing to fetch: " + ModAttachments.MACHINE_JOB.get(villager));
			MachineWorkCompatTests.clearMachines(helper);
			helper.succeed();
		});
	}
}
