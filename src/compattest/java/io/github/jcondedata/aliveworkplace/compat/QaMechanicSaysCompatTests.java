package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.mechanic.MechanicWork;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Stations;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.LinkedHashSet;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane E, 2026-10-24), ROADMAP 39.2 from the player's side: the line over the Mechanic's head, rendered as a player
 * reads it. With nothing to mend and no machine in the village: "Mechanic: every machine is in order". On the round, at a
 * village millstone that doesn't turn: "Mechanic: seeing to the Millstone", then "Mechanic: the Millstone has stopped
 * turning". The work itself is {@code MechanicCompatTests}'.
 */
public class QaMechanicSaysCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos TOOLBOX = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	/** Forgets every finished build earlier tests left in reach (their holes would be this Mechanic's to mend). */
	private static void nothingFinishedNear(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BuildSiteManager sites = BuildSiteManager.get(level);
		for (BuildSiteManager.Finished f : sites.finishedNear(level, helper.absolutePos(TOOLBOX), 64)) {
			sites.forgetFinished(f.placement());
		}
		MachineWorkCompatTests.noVillage(helper);
	}

	private static Villager mechanic(GameTestHelper helper) {
		helper.setBlock(TOOLBOX, MechanicCompatTests.create("brown_toolbox").defaultBlockState());
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Stations.assign(helper.getLevel(), villager, helper.absolutePos(TOOLBOX), ModVillagers.MECHANIC);
		helper.assertTrue(MechanicWork.isMechanic(villager), "setup: no Mechanic at the toolbox");
		helper.setDayTime(2000);
		return villager;
	}

	/** Every line the Mechanic says from now on, as "title: line", in order, each once. */
	private static Set<String> listen(GameTestHelper helper, Villager mechanic) {
		Set<String> said = new LinkedHashSet<>();
		helper.onEachTick(() -> {
			WorkerStatus.Entry e = WorkerStatus.get(mechanic, helper.getLevel().getGameTime());
			if (e != null) {
				said.add(e.title().getString() + ": " + e.line().getString());
			}
		});
		return said;
	}

	/** Nothing to mend and no machine: "Mechanic: every machine is in order". */
	//$ gametest_ticks_batch AREA '600' '"qa_mechanic_idle_says"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qa_mechanic_idle_says")
	public void anIdleMechanicSaysEverythingIsInOrder(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		MachineWorkCompatTests.clearMachines(helper);
		nothingFinishedNear(helper);
		Villager mechanic = mechanic(helper);
		Set<String> said = listen(helper, mechanic);
		helper.succeedWhen(() -> helper.assertTrue(said.contains("Mechanic: every machine is in order"), "the Mechanic said " + said));
	}

	/** A village millstone that doesn't turn: the Mechanic goes to see to it and says it has stopped. */
	//$ gametest_ticks_batch AREA '1200' '"qa_mechanic_still_says"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa_mechanic_still_says")
	public void theMechanicSaysAMillstoneHasStopped(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		MachineWorkCompatTests.clearMachines(helper);
		nothingFinishedNear(helper);
		MachineWorkCompatTests.villageWorkshop(helper);
		helper.setBlock(CHEST, Blocks.CHEST.defaultBlockState());
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.WHEAT, 20));
		helper.setBlock(new BlockPos(6, 2, 5), MechanicCompatTests.create("millstone").defaultBlockState());
		Villager mechanic = mechanic(helper);
		Set<String> said = listen(helper, mechanic);
		helper.succeedWhen(() -> {
			helper.assertTrue(said.contains("Mechanic: seeing to the Millstone") && said.contains("Mechanic: the Millstone has stopped turning"),
				"the Mechanic said " + said);
			helper.assertTrue(chest.countItem(Items.WHEAT) == 20, "wheat went to a millstone that doesn't turn: " + chest.countItem(Items.WHEAT) + " left");
			MachineWorkCompatTests.clearMachines(helper);
		});
	}
}
