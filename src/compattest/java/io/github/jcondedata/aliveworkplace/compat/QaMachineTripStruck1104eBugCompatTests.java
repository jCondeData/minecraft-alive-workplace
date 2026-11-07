package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.mechanic.MachineJob;
import io.github.jcondedata.aliveworkplace.mechanic.MachineWorkFiles;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA lane E, 2026-11-04 (Create machine work 39.3, B928 kin): MachineWork promises "a worker killed mid-trip drops what
 * they carried where they fell (it's never lost with them)", and MachineWorkCompatTests checks the death. B928 made a
 * worker struck by lightning (vanilla's Villager.thunderHit: a witch, no death) leave his work as a death does, but its
 * hook drops only the worker's bag, not a machine trip's load. Fails on main: register with the fix.
 */
public class QaMachineTripStruck1104eBugCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos STATION = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	/** A farmer on the way to the millstone with 12 wheat, struck into a witch: the 12 wheat lie where he stood. */
	//$ gametest_ticks_batch AREA '200' '"qa_e1104_machine_trip_struck"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_e1104_machine_trip_struck")
	public void aWorkerStruckMidTripLeavesWhatTheyCarried(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		MachineWorkCompatTests.clearMachines(helper);
		helper.setBlock(CHEST, Blocks.CHEST.defaultBlockState());
		Container store = (Container) helper.getBlockEntity(CHEST);
		BlockPos machine = MachineWorkCompatTests.rig(helper, MachineWorkCompatTests.Kind.MILL, false);
		MachineWorkCompatTests.Kind kind = MachineWorkCompatTests.Kind.MILL;
		helper.setBlock(STATION, kind.station());
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Stations.assign(helper.getLevel(), villager, helper.absolutePos(STATION), kind.profession());
		helper.setDayTime(2000);
		MachineWorkFiles.Machine file = MachineWorkFiles.all().stream().filter(m -> m.id().equals(AliveWorkplace.id(kind.file))).findFirst().orElseThrow();
		ModAttachments.MACHINE_JOB.set(villager, new MachineJob(helper.absolutePos(machine), file.id(), MachineJob.Stage.FEED,
			List.of(new ItemStack(Items.WHEAT, 12))));
		QaMechanicPartStruck1104eCompatTests.strike(helper.getLevel(), villager);
		helper.assertTrue(villager.isRemoved(), "setup: lightning didn't turn the farmer into a witch");
		helper.runAfterDelay(5, () -> {
			int ground = helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(4), e -> e.getItem().is(Items.WHEAT))
				.stream().mapToInt(e -> e.getItem().getCount()).sum();
			int inStore = 0;
			for (int i = 0; i < store.getContainerSize(); i++) {
				inStore += store.getItem(i).is(Items.WHEAT) ? store.getItem(i).getCount() : 0;
			}
			helper.assertTrue(ground + inStore == 12, "the struck farmer left " + (ground + inStore) + " of the 12 wheat he carried to the millstone (ground "
				+ ground + ", store " + inStore + "): the rest vanished with him");
			MachineWorkCompatTests.clearMachines(helper);
			helper.succeed();
		});
	}
}
