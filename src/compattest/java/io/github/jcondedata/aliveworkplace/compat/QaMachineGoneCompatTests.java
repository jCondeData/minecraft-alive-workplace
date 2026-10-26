package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.mechanic.MachineJob;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Stations;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
 * QA (lane E, 2026-10-25), 39.3 with the real Create, the likeliest way a machine trip breaks: the machine is broken
 * while the worker walks to it with the input. What they carry must come back to the store (none lost, none dropped),
 * and the lines a player reads on the worker (the status over their head) must still name the machine, never "the Air".
 */
public class QaMachineGoneCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos STATION = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	/** A farmer carrying 8 wheat to a turning millstone; the millstone (and its motor) broken as they set off. */
	//$ gametest_ticks_batch AREA '1200' '"qa_machine_gone"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa_machine_gone")
	public void aMillstoneBrokenMidTripGetsItsWheatBackInTheStore(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		MachineWorkCompatTests.clearMachines(helper);
		MachineWorkCompatTests.villageWorkshop(helper);
		helper.setBlock(CHEST, Blocks.CHEST.defaultBlockState());
		Container chest = (Container) helper.getBlockEntity(CHEST);
		BlockPos mill = MachineWorkCompatTests.rig(helper, MachineWorkCompatTests.Kind.MILL, true);
		helper.setBlock(STATION, Blocks.COMPOSTER);
		Villager farmer = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Stations.assign(helper.getLevel(), farmer, helper.absolutePos(STATION), MachineWorkCompatTests.Kind.MILL.profession());
		helper.setDayTime(2000);
		ModAttachments.MACHINE_JOB.set(farmer, new MachineJob(helper.absolutePos(mill), AliveWorkplace.id("millstone"), MachineJob.Stage.FEED,
			List.of(new ItemStack(Items.WHEAT, 8))));
		helper.setBlock(mill, Blocks.AIR);
		helper.setBlock(mill.below(), Blocks.AIR);
		Set<String> said = new LinkedHashSet<>();
		helper.onEachTick(() -> {
			WorkerStatus.Entry e = WorkerStatus.get(farmer, helper.getLevel().getGameTime());
			if (e != null) {
				said.add(e.title().getString() + ": " + e.line().getString());
			}
		});
		helper.succeedWhen(() -> {
			int wheat = 0;
			for (int i = 0; i < chest.getContainerSize(); i++) {
				wheat += chest.getItem(i).is(Items.WHEAT) ? chest.getItem(i).getCount() : 0;
			}
			helper.assertTrue(ModAttachments.MACHINE_JOB.get(farmer) == null, "the trip is still on: " + ModAttachments.MACHINE_JOB.get(farmer) + "; said " + said);
			helper.assertTrue(wheat == 8, "the store got back " + wheat + " of the 8 wheat carried to the broken millstone");
			helper.assertTrue(said.stream().noneMatch(s -> s.contains("Air")), "the farmer's status named the broken millstone 'Air': " + said);
			MachineWorkCompatTests.clearMachines(helper);
		});
	}

	/**
	 * The Mechanic's round: walking to see to a village millstone (that doesn't turn) that is broken on the way, they
	 * never say "seeing to the Air" (B506's note: MechanicWork.machine names the block at the spot).
	 */
	//$ gametest_ticks_batch AREA '1200' '"qa_machine_gone_mechanic"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa_machine_gone_mechanic")
	public void aMechanicWhoseMillstoneIsBrokenOnTheWayNeverSeesToTheAir(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		MachineWorkCompatTests.clearMachines(helper);
		BuildSiteManager sites = BuildSiteManager.get(helper.getLevel());
		for (BuildSiteManager.Finished f : sites.finishedNear(helper.getLevel(), helper.absolutePos(STATION), 64)) {
			sites.forgetFinished(f.placement());
		}
		MachineWorkCompatTests.noVillage(helper);
		MachineWorkCompatTests.villageWorkshop(helper);
		BlockPos mill = new BlockPos(12, 2, 12);
		helper.setBlock(mill, MechanicCompatTests.create("millstone").defaultBlockState());
		helper.setBlock(STATION, MechanicCompatTests.create("brown_toolbox").defaultBlockState());
		Villager mechanic = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Stations.assign(helper.getLevel(), mechanic, helper.absolutePos(STATION), ModVillagers.MECHANIC);
		helper.setDayTime(2000);
		Set<String> said = new LinkedHashSet<>();
		long[] brokenAt = {-1};
		helper.onEachTick(() -> {
			WorkerStatus.Entry e = WorkerStatus.get(mechanic, helper.getLevel().getGameTime());
			if (e != null) {
				said.add(e.title().getString() + ": " + e.line().getString());
			}
			if (brokenAt[0] < 0 && said.contains("Mechanic: seeing to the Millstone")) {
				helper.setBlock(mill, Blocks.AIR);
				brokenAt[0] = helper.getLevel().getGameTime();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(brokenAt[0] >= 0 && helper.getLevel().getGameTime() - brokenAt[0] > 200,
				"waiting for the Mechanic to set off for the millstone and 200 ticks after it was broken: said " + said);
			helper.assertTrue(said.stream().noneMatch(s -> s.contains("Air")), "the Mechanic named the broken millstone 'Air': " + said);
			MachineWorkCompatTests.clearMachines(helper);
		});
	}
}
