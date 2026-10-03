package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.LecternBlock;

/**
 * QA lane (qa-1003-1633), bugs B8 and B17: a workstation changing state (a book on a lectern, a composter filling) is not
 * a break, so the ordinary change of job, by item or by the Village Hall, still frees the old block.
 */
public class QaB17GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/** Putting a book on a lectern isn't breaking it: a librarian whose lectern got a book still frees it on a new job (item). */
	//$ gametest_ticks_batch AREA '100' '"qaB17BookItem"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaB17BookItem")
	public void qaALecternWithABookIsStillFreedOnANewJob(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos lectern = helper.absolutePos(new BlockPos(3, 2, 3));
		BlockPos composter = helper.absolutePos(new BlockPos(9, 2, 9));
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.LECTERN);
		helper.setBlock(new BlockPos(9, 2, 9), Blocks.COMPOSTER);
		Villager worker = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 3));
		worker.setNoAi(true);
		Jobs.employ(level, worker, lectern, PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.HAS_BOOK, true));
		helper.assertTrue(level.getPoiManager().getFreeTickets(lectern) == 0, "setup: the book freed the lectern");
		Stations.assign(level, worker, composter, VillagerProfession.FARMER);
		int free = level.getPoiManager().getFreeTickets(lectern);
		helper.assertTrue(free == 1, "the librarian whose lectern got a book became a farmer but the lectern wasn't freed ("
			+ free + " free tickets): no one can work there again");
		helper.succeed();
	}

	/** Filling a composter isn't breaking it: a farmer whose composter filled still frees it when the Village Hall moves them. */
	//$ gametest_ticks_batch AREA '100' '"qaB17FilledHall"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaB17FilledHall")
	public void qaAFilledComposterIsStillFreedWhenTheHallMovesItsFarmer(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos composter = helper.absolutePos(new BlockPos(3, 2, 3));
		BlockPos lectern = helper.absolutePos(new BlockPos(9, 2, 9));
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.COMPOSTER);
		helper.setBlock(new BlockPos(9, 2, 9), Blocks.LECTERN);
		Villager worker = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 3));
		worker.setNoAi(true);
		Jobs.employ(level, worker, composter, PoiTypes.FARMER, VillagerProfession.FARMER);
		for (int fill = 1; fill <= ComposterBlock.READY; fill++) {
			helper.setBlock(new BlockPos(3, 2, 3), Blocks.COMPOSTER.defaultBlockState().setValue(ComposterBlock.LEVEL, fill));
		}
		var librarian = level.registryAccess().registryOrThrow(Registries.POINT_OF_INTEREST_TYPE).getHolderOrThrow(PoiTypes.LIBRARIAN);
		helper.assertTrue(VillageHalls.assign(level, worker, new VillageHalls.FreeStation(lectern, VillagerProfession.LIBRARIAN, librarian)),
			"the Hall didn't give the farmer the lectern");
		int free = level.getPoiManager().getFreeTickets(composter);
		helper.assertTrue(free == 1, "the Hall moved the farmer whose composter had filled, but the composter wasn't freed ("
			+ free + " free tickets): no one can work there again");
		helper.succeed();
	}

}
