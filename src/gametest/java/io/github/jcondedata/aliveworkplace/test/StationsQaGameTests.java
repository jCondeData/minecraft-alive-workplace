package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Stations;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;

/**
 * QA lane (qa-1003-1433), bug B8 from its spec: "a GameTest of that sequence leaves the second worker's block taken".
 * The same sequence, with the second worker not loaded when the first is given a new job: in a real village the block's
 * new owner is often in a chunk no player is near, while the player stands by the first worker.
 */
public class StationsQaGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/**
	 * A librarian's lectern is broken while they're away and put back; a second librarian takes the new one; the second
	 * one's chunk unloads (saved and removed, as an unload does); the first is given a farm job by the player; the
	 * second comes back. The lectern must still be the second's: its POI ticket taken, not free for a third villager.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaB8SecondWorkerUnloaded"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaB8SecondWorkerUnloaded")
	public void qaReassigningKeepsTheBlockOfAnUnloadedOwnerTaken(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos lecternAt = new BlockPos(3, 2, 3);
		BlockPos lectern = helper.absolutePos(lecternAt);
		BlockPos composter = helper.absolutePos(new BlockPos(9, 2, 9));
		helper.setBlock(lecternAt, Blocks.LECTERN);
		helper.setBlock(new BlockPos(9, 2, 9), Blocks.COMPOSTER);
		Villager first = helper.spawn(EntityType.VILLAGER, new BlockPos(10, 2, 10));
		first.setNoAi(true); // (away: their brain doesn't see the block go)
		Jobs.employ(level, first, lectern, PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
		helper.setBlock(lecternAt, Blocks.AIR);
		helper.setBlock(lecternAt, Blocks.LECTERN);
		Villager second = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 3));
		second.setNoAi(true);
		Jobs.employ(level, second, lectern, PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
		helper.assertTrue(level.getPoiManager().getFreeTickets(lectern) == 0, "setup: the second librarian didn't take the new lectern");

		// The second worker's chunk unloads: saved, then removed from the world the way an unload removes it.
		CompoundTag saved = new CompoundTag();
		second.save(saved);
		second.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);

		Stations.assign(level, first, composter, VillagerProfession.FARMER);
		helper.assertTrue(StationsSpecGameTests.job(first) == VillagerProfession.FARMER, "the first worker isn't a farmer");

		// The chunk loads again.
		Villager back = (Villager) EntityType.loadEntityRecursive(saved, level, e -> e);
		helper.assertTrue(back != null && level.addFreshEntity(back), "the second worker didn't come back");
		helper.assertTrue(StationsSpecGameTests.site(back).map(lectern::equals).orElse(false), "setup: the second worker forgot their lectern");
		helper.assertTrue(level.getPoiManager().getFreeTickets(lectern) == 0,
			"giving the first worker a new job freed the lectern of the second worker, who was unloaded at the time: a third villager can now share it");
		helper.succeed();
	}
}
