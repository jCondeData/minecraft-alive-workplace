package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;

/**
 * QA lane (qa-1003-1333), bug B8 with the second worker unloaded: their block must stay taken. Failing on main
 * (2026-10-03): Stations.someoneElseWorksAt only looks at loaded villagers.
 */
public class StationsB8UnloadedGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/**
	 * B8, the second worker away: a worker's lectern is broken while they're far off, a new one is put on the spot and a
	 * second villager takes it; then the second villager's chunk unloads (they live by the lectern, across the village from
	 * the first worker and the player). The player gives the first worker a new job. The second worker's lectern must
	 * stay taken: when they load again they still work there, and no third villager may share it.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaB8SecondWorkerUnloaded"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaB8SecondWorkerUnloaded")
	public void b8ReassigningKeepsAnUnloadedWorkersBlockTaken(GameTestHelper helper) {
		secondWorkerUnloaded(helper, false);
	}

	//$ gametest_ticks_batch AREA '100' '"qaB8SecondWorkerUnloadedHall"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaB8SecondWorkerUnloadedHall")
	public void b8HallAssignKeepsAnUnloadedWorkersBlockTaken(GameTestHelper helper) {
		secondWorkerUnloaded(helper, true);
	}

	private static void secondWorkerUnloaded(GameTestHelper helper, boolean fromTheHall) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos lectern = helper.absolutePos(new BlockPos(3, 2, 3));
		BlockPos composter = helper.absolutePos(new BlockPos(9, 2, 9));
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.LECTERN);
		helper.setBlock(new BlockPos(9, 2, 9), Blocks.COMPOSTER);
		Villager first = helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 8));
		first.setNoAi(true); // (away: their brain doesn't see the block go)
		Jobs.employ(level, first, lectern, PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.AIR);
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.LECTERN);
		Villager second = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 3));
		second.setNoAi(true);
		Jobs.employ(level, second, lectern, PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
		helper.assertTrue(level.getPoiManager().getFreeTickets(lectern) == 0, "setup: the second librarian didn't take the new lectern");

		// The second worker's chunk unloads: they're saved and leave the level, as vanilla does (their POI stays taken).
		CompoundTag saved = new CompoundTag();
		helper.assertTrue(second.save(saved), "setup: the second villager couldn't be saved");
		second.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
		helper.assertTrue(level.getPoiManager().getFreeTickets(lectern) == 0, "setup: unloading the second villager freed the lectern");

		if (fromTheHall) {
			var poi = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.POINT_OF_INTEREST_TYPE)
				.getHolderOrThrow(PoiTypes.FARMER);
			helper.assertTrue(io.github.jcondedata.aliveworkplace.hall.VillageHalls.assign(level, first,
				new io.github.jcondedata.aliveworkplace.hall.VillageHalls.FreeStation(composter, VillagerProfession.FARMER, poi)),
				"the Hall didn't give the first worker the composter");
		} else {
			io.github.jcondedata.aliveworkplace.work.Stations.assign(level, first, composter, VillagerProfession.FARMER);
		}
		helper.assertTrue(StationsSpecGameTests.job(first) == VillagerProfession.FARMER, "the first worker isn't a farmer");
		int free = level.getPoiManager().getFreeTickets(lectern);

		// The second worker loads again, still remembering the lectern.
		Entity back = EntityType.loadEntityRecursive(saved, level, e -> e);
		helper.assertTrue(back instanceof Villager && level.addFreshEntity(back), "setup: the second villager couldn't be loaded again");
		helper.assertTrue(((Villager) back).getBrain().getMemory(MemoryModuleType.JOB_SITE).map(g -> g.pos().equals(lectern)).orElse(false),
			"setup: the reloaded second villager forgot their lectern");
		helper.assertTrue(free == 0, "giving the first worker a new job (" + (fromTheHall ? "Village Hall" : "item")
			+ ") freed the lectern of a second librarian whose chunk was unloaded (" + free + " free ticket); a third villager can now share it");
		helper.succeed();
	}
}
