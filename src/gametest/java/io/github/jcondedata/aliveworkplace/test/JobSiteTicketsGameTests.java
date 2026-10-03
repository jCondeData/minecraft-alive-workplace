package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Stations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
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
 * Bug B15, around the fix (work/JobSiteTickets): who holds a job site's record is known even for villagers nobody can
 * see, it survives a save, vanilla's own job taking is noted, and an ordinary change of job still frees the old block.
 */
public class JobSiteTicketsGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/** Both workers unloaded and loaded again (a server restart between the break and the new job): the lectern stays taken. */
	//$ gametest_ticks_batch AREA '100' '"b15BothReloaded"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "b15BothReloaded")
	public void b15AReloadedWorkerDoesntFreeTheReplacedBlock(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos lectern = helper.absolutePos(new BlockPos(3, 2, 3));
		BlockPos composter = helper.absolutePos(new BlockPos(9, 2, 9));
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.LECTERN);
		helper.setBlock(new BlockPos(9, 2, 9), Blocks.COMPOSTER);
		Villager first = helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 8));
		first.setNoAi(true);
		Jobs.employ(level, first, lectern, PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
		Villager firstBack = reload(helper, first); // the first worker's chunk unloads before the block goes
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.AIR);
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.LECTERN);
		Villager second = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 3));
		second.setNoAi(true);
		Jobs.employ(level, second, lectern, PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
		CompoundTag saved = new CompoundTag();
		helper.assertTrue(second.save(saved), "setup: the second villager couldn't be saved");
		second.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);

		Stations.assign(level, firstBack, composter, VillagerProfession.FARMER);
		helper.assertTrue(StationsSpecGameTests.job(firstBack) == VillagerProfession.FARMER, "the reloaded first worker isn't a farmer");
		int free = level.getPoiManager().getFreeTickets(lectern);
		helper.assertTrue(free == 0, "the reloaded first worker's new job freed the lectern the unloaded second librarian holds ("
			+ free + " free ticket)");
		helper.succeed();
	}

	/** A worker who took their block by themselves (vanilla's brain) is noted within a tick, and B15 holds for them too. */
	//$ gametest_ticks_batch AREA '100' '"b15VanillaTaken"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "b15VanillaTaken")
	public void b15AJobSiteVanillaGaveIsNoted(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos lectern = helper.absolutePos(new BlockPos(3, 2, 3));
		BlockPos composter = helper.absolutePos(new BlockPos(9, 2, 9));
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.LECTERN);
		helper.setBlock(new BlockPos(9, 2, 9), Blocks.COMPOSTER);
		Villager first = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 3));
		// What vanilla's AssignProfessionFromJobSite leaves: the ticket taken, the memory set, the profession given.
		level.getPoiManager().take(h -> h.is(PoiTypes.LIBRARIAN), (h, p) -> p.equals(lectern), lectern, 1);
		first.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), lectern));
		first.setVillagerData(first.getVillagerData().setProfession(VillagerProfession.LIBRARIAN));
		first.setVillagerXp(1);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(ModAttachments.JOB_SITE_HELD.get(first) != null
				&& ModAttachments.JOB_SITE_HELD.get(first).site().pos().equals(lectern), "the job site vanilla gave wasn't noted");
			first.setNoAi(true); // (then away)
			first.teleportTo(first.getX() + 5, first.getY(), first.getZ() + 5);
			helper.setBlock(new BlockPos(3, 2, 3), Blocks.AIR);
			helper.setBlock(new BlockPos(3, 2, 3), Blocks.LECTERN);
			Villager second = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 3));
			second.setNoAi(true);
			Jobs.employ(level, second, lectern, PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
			second.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
			Stations.assign(level, first, composter, VillagerProfession.FARMER);
			int free = level.getPoiManager().getFreeTickets(lectern);
			helper.assertTrue(free == 0, "the new job freed the lectern the unloaded second librarian holds (" + free + " free ticket)");
			helper.succeed();
		});
	}

	/** The ordinary case still frees the old block: a worker whose lectern was never broken changes job, even after a reload. */
	//$ gametest_ticks_batch AREA '100' '"b15Ordinary"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "b15Ordinary")
	public void b15AnOrdinaryChangeOfJobFreesTheOldBlock(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos lectern = helper.absolutePos(new BlockPos(3, 2, 3));
		BlockPos composter = helper.absolutePos(new BlockPos(9, 2, 9));
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.LECTERN);
		helper.setBlock(new BlockPos(9, 2, 9), Blocks.COMPOSTER);
		// A different block broken elsewhere doesn't count against this one.
		helper.setBlock(new BlockPos(6, 2, 6), Blocks.LECTERN);
		helper.setBlock(new BlockPos(6, 2, 6), Blocks.AIR);
		Villager first = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 3));
		first.setNoAi(true);
		Jobs.employ(level, first, lectern, PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
		Villager back = reload(helper, first);
		helper.assertTrue(level.getPoiManager().getFreeTickets(lectern) == 0, "setup: the librarian doesn't hold the lectern");
		Stations.assign(level, back, composter, VillagerProfession.FARMER);
		int free = level.getPoiManager().getFreeTickets(lectern);
		helper.assertTrue(free == 1, "changing job didn't free the old lectern (" + free + " free tickets)");
		helper.assertTrue(level.getPoiManager().getFreeTickets(composter) == 0, "the new composter isn't taken");
		helper.succeed();
	}

	/** Saves {@code villager}, removes them as an unloading chunk does and loads them again. */
	private static Villager reload(GameTestHelper helper, Villager villager) {
		CompoundTag saved = new CompoundTag();
		helper.assertTrue(villager.save(saved), "setup: the villager couldn't be saved");
		villager.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
		Entity back = EntityType.loadEntityRecursive(saved, helper.getLevel(), e -> e);
		helper.assertTrue(back instanceof Villager && helper.getLevel().addFreshEntity(back), "setup: the villager couldn't be loaded again");
		helper.assertTrue(ModAttachments.JOB_SITE_HELD.get(back) != null, "the villager's noted job site wasn't saved");
		return (Villager) back;
	}
}
