package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.mechanic.MachineJob;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Stations;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane E, 2026-10-27) for B506, from its Expected ("the machine's name (the Millstone), or a line saying it's gone")
 * and the fix's new lines, not from the code, with a save in the middle: a machine trip is saved on the worker, so a
 * server restart can fall between setting off and the machine being broken. After the restart the trip must still know
 * which machine it was for, say so by name, and bring the whole load back.
 */
public class QaMachineGoneSaveCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos STATION = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	/**
	 * A farmer carrying 8 wheat to a turning millstone is saved and loaded again (a restart); then the millstone and its
	 * motor are broken. The loaded farmer reads "the Millstone is gone: bringing the load back to the store" and the store
	 * gets all 8 wheat back.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qa_machine_gone_saved"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa_machine_gone_saved")
	public void aTripSavedOnTheWayToAMillstoneBrokenAfterTheRestartSaysItIsGone(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		MachineWorkCompatTests.clearMachines(helper);
		MachineWorkCompatTests.villageWorkshop(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(CHEST, Blocks.CHEST.defaultBlockState());
		Container chest = (Container) helper.getBlockEntity(CHEST);
		BlockPos mill = MachineWorkCompatTests.rig(helper, MachineWorkCompatTests.Kind.MILL, true);
		helper.setBlock(STATION, Blocks.COMPOSTER);
		Villager before = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Stations.assign(level, before, helper.absolutePos(STATION), MachineWorkCompatTests.Kind.MILL.profession());
		helper.setDayTime(2000);
		MachineJob trip = new MachineJob(helper.absolutePos(mill), AliveWorkplace.id("millstone"), MachineJob.Stage.FEED,
			List.of(new ItemStack(Items.WHEAT, 8)));
		ModAttachments.MACHINE_JOB.set(before, trip);
		Villager farmer = reload(level, before);
		MachineJob loaded = ModAttachments.MACHINE_JOB.get(farmer);
		helper.assertTrue(loaded != null && loaded.machine().equals(trip.machine()) && loaded.file().equals(trip.file())
			&& loaded.stage() == MachineJob.Stage.FEED && loaded.carried().size() == 1 && loaded.carried().get(0).is(Items.WHEAT)
			&& loaded.carried().get(0).getCount() == 8, "the trip didn't come through the save whole: " + loaded + ", saved " + trip);
		helper.setBlock(mill, Blocks.AIR);
		helper.setBlock(mill.below(), Blocks.AIR);
		Set<String> said = new LinkedHashSet<>();
		helper.onEachTick(() -> {
			WorkerStatus.Entry e = WorkerStatus.get(farmer, level.getGameTime());
			if (e != null) {
				said.add(e.line().getString());
			}
		});
		helper.succeedWhen(() -> {
			int wheat = 0;
			for (int i = 0; i < chest.getContainerSize(); i++) {
				wheat += chest.getItem(i).is(Items.WHEAT) ? chest.getItem(i).getCount() : 0;
			}
			helper.assertTrue(ModAttachments.MACHINE_JOB.get(farmer) == null, "the trip is still on: " + ModAttachments.MACHINE_JOB.get(farmer) + "; said " + said);
			helper.assertTrue(wheat == 8, "the store got back " + wheat + " of the 8 wheat carried, over a restart, to the broken millstone");
			helper.assertTrue(said.contains("the Millstone is gone: bringing the load back to the store"),
				"the loaded farmer never said the millstone is gone, by name: " + said);
			helper.assertTrue(said.stream().noneMatch(s -> s.contains("Air") || s.contains("machine is gone")), "a line named no machine or 'Air': " + said);
			MachineWorkCompatTests.clearMachines(helper);
		});
	}

	/** The Mechanic walking to see to a village millstone that is broken on the way says "the Millstone is gone". */
	//$ gametest_ticks_batch AREA '1200' '"qa_machine_gone_mechanic_says"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa_machine_gone_mechanic_says")
	public void aMechanicWhoseMillstoneIsBrokenOnTheWaySaysItIsGone(GameTestHelper helper) {
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
		boolean[] broken = {false};
		helper.onEachTick(() -> {
			WorkerStatus.Entry e = WorkerStatus.get(mechanic, helper.getLevel().getGameTime());
			if (e != null) {
				said.add(e.line().getString());
			}
			if (!broken[0] && said.contains("seeing to the Millstone")) {
				helper.setBlock(mill, Blocks.AIR);
				broken[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(broken[0], "the Mechanic never set off for the millstone: said " + said);
			helper.assertTrue(said.contains("the Millstone is gone"), "the Mechanic never said the broken millstone is gone, by name: " + said);
			helper.assertTrue(said.stream().noneMatch(s -> s.contains("Air")), "the Mechanic named the broken millstone 'Air': " + said);
			MachineWorkCompatTests.clearMachines(helper);
		});
	}

	/** The villager saved and loaded again, as a server restart does. */
	private static Villager reload(ServerLevel level, Villager villager) {
		CompoundTag tag = new CompoundTag();
		villager.saveWithoutId(tag);
		villager.discard();
		Villager copy = EntityType.VILLAGER.create(level);
		if (copy == null) {
			throw new GameTestAssertException("no villager made");
		}
		copy.load(tag);
		level.addFreshEntity(copy);
		io.github.jcondedata.aliveworkplace.work.Village.forget(copy);
		return copy;
	}
}
