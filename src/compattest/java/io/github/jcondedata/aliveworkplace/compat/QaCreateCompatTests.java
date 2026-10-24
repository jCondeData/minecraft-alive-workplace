package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.mechanic.MachineWork;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Stations;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane E, 2026-10-24), ROADMAP 39.3 from the player's side: the line over a worker's head says why a machine of the
 * village gets nothing, in the words the spec and the lang file give it, with the machine's own name. A farmer by a
 * millstone that doesn't turn reads "the Millstone isn't turning"; with the village's store full, "the store is full:
 * the Millstone waits". Both under the title "Machine work". The work itself is {@code MachineWorkCompatTests}'.
 */
public class QaCreateCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos STATION = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos MILL = new BlockPos(6, 3, 2);

	private static net.minecraft.world.level.block.Block create(String path) {
		return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", path));
	}

	/** The village's workshop with a millstone (turned by a creative motor when {@code powered}), a chest and its farmer. */
	private static Villager millAndFarmer(GameTestHelper helper, boolean powered, boolean fullStore) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		MachineWorkCompatTests.clearMachines(helper);
		MachineWorkCompatTests.villageWorkshop(helper);
		helper.setBlock(CHEST, Blocks.CHEST.defaultBlockState());
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.WHEAT, 20));
		if (fullStore) {
			for (int i = 1; i < chest.getContainerSize(); i++) {
				chest.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
			}
		}
		helper.setBlock(MILL.below(), powered ? MechanicCompatTests.with(create("creative_motor").defaultBlockState(), "facing", "up")
			: Blocks.STONE.defaultBlockState());
		helper.setBlock(MILL, create("millstone").defaultBlockState());
		helper.setBlock(STATION, Blocks.COMPOSTER);
		Villager farmer = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Stations.assign(helper.getLevel(), farmer, helper.absolutePos(STATION), VillagerProfession.FARMER);
		helper.assertTrue(farmer.getVillagerData().getProfession() == VillagerProfession.FARMER, "setup: no farmer at the composter");
		helper.setDayTime(2000);
		return farmer;
	}

	private static String said(GameTestHelper helper, Villager villager) {
		WorkerStatus.Entry e = WorkerStatus.get(villager, helper.getLevel().getGameTime());
		return e == null ? "(nothing)" : e.title().getString() + ": " + e.line().getString();
	}

	/** A farmer by a millstone that doesn't turn: "Machine work: the Millstone isn't turning", and no trip. */
	//$ gametest_ticks_batch AREA '600' '"qa_mill_still_says"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qa_mill_still_says")
	public void aStillMillstoneIsNamedOverTheFarmer(GameTestHelper helper) {
		Villager farmer = millAndFarmer(helper, false, false);
		helper.succeedWhen(() -> {
			helper.assertTrue(said(helper, farmer).equals("Machine work: the Millstone isn't turning"),
				"the farmer says \"" + said(helper, farmer) + "\"");
			helper.assertTrue(!MachineWork.busy(farmer), "the farmer set off for a still millstone: " + ModAttachments.MACHINE_JOB.get(farmer));
			MachineWorkCompatTests.clearMachines(helper);
		});
	}

	/** The store full: "Machine work: the store is full: the Millstone waits", and the wheat stays in the chest. */
	//$ gametest_ticks_batch AREA '600' '"qa_mill_full_says"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qa_mill_full_says")
	public void aFullStoreIsSaidOverTheFarmer(GameTestHelper helper) {
		Villager farmer = millAndFarmer(helper, true, true);
		Container chest = helper.getBlockEntity(CHEST);
		helper.succeedWhen(() -> {
			helper.assertTrue(said(helper, farmer).equals("Machine work: the store is full: the Millstone waits"),
				"the farmer says \"" + said(helper, farmer) + "\"");
			helper.assertTrue(chest.countItem(Items.WHEAT) == 20, "wheat left the full store: " + chest.countItem(Items.WHEAT));
			MachineWorkCompatTests.clearMachines(helper);
		});
	}
}
