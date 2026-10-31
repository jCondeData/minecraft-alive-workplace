package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.camp.CampBeds;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (2026-10-31) of B715 against B207's spec ("a bed nobody's camp placed stays a home"): a cabin's brown bed by a
 * campfire and hay within 12 blocks of the village builder's Blueprint Table is still taken for an old camp's bedroll and
 * listed for good (B732). Copied from QaVillageGuardsBug1031Tests on tests/village-guards-1031, whose B731 tests wait for
 * their own fix.
 */
public class QaCabinBedWorkshop1031GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/**
	 * B715: a player's cabin next to the village builder's workshop: a brown bed, a campfire 6 blocks off, a hay bale 6
	 * blocks off, and 9 blocks away the village's builder at his Blueprint Table. No camp was ever placed here: the bed
	 * stays its resident's home through the hall's round and the hall counts it.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaGuardsBugCabinByWorkshop"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaGuardsBugCabinByWorkshop")
	public void qaACabinBedByTheBuildersWorkshopIsStillAHome(GameTestHelper helper) {
		QaVillageGuards1031GameTests.campArea(helper);
		ServerLevel level = helper.getLevel();
		BlockPos head = QaVillageGuards1031GameTests.bed(helper, new BlockPos(6, 2, 10), Blocks.BROWN_BED);
		helper.setBlock(new BlockPos(12, 2, 10), Blocks.CAMPFIRE); // the cabin's cooking fire
		helper.setBlock(new BlockPos(0, 2, 10), Blocks.HAY_BLOCK); // a hay bale by the door
		BlockPos table = new BlockPos(12, 2, 4); // the builder's workshop next door
		helper.setBlock(table, ModBlocks.BLUEPRINT_TABLE);
		Villager builder = helper.spawn(EntityType.VILLAGER, table.south());
		builder.setNoAi(true);
		Builders.employ(level, builder, helper.absolutePos(table));
		Villager resident = helper.spawn(EntityType.VILLAGER, new BlockPos(7, 2, 8));
		resident.setNoAi(true);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(CampBeds.claim(level, resident, head), "setup: the resident couldn't take the cabin's bed");
			helper.setBlock(new BlockPos(15, 2, 20), ModBlocks.VILLAGE_HALL);
			helper.runAfterDelay(60, () -> {
				helper.assertTrue(!CampBeds.isWagonBed(level, head),
					"a player's brown bed by a campfire and hay, 9 blocks from the village builder's Blueprint Table, is taken for a wagon bed (no camp was ever placed)");
				helper.assertTrue(head.equals(QaVillageGuards1031GameTests.home(resident)),
					"the cabin's resident lost his home at the hall's round: " + QaVillageGuards1031GameTests.home(resident));
				VillageHalls.Census census = VillageHalls.census(level, helper.absolutePos(new BlockPos(15, 2, 20)));
				helper.assertTrue(census.beds() == 1 && census.wagonBeds() == 0, "the hall counts beds " + census.beds() + ", wagon beds " + census.wagonBeds());
				helper.succeed();
			});
		});
	}
}
