package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trainer.MegaShrines;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;

/**
 * QA (mutation survivor, MegaShrines.watching): ROADMAP 38.7 says villagers off work gather at the Mega Shrine and get its
 * +3 mood; the crowd is gathered as {@code Festivals.gatherAt} gathers it, so a villager seated (riding, as in a Cup's
 * stands) counts even at work, and one off work counts without a seat. Each is checked alone, beside a worker on foot who
 * never came (no mood), by holding the show at the pedestal directly (no Mega Showdown needed).
 */
public class QaMegaShrineIdleGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";

	private static Villager villager(GameTestHelper helper, BlockPos at) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		return v;
	}

	/** {@code v} becomes a farmer at his job site, so not off work. */
	private static void atWork(GameTestHelper helper, Villager v, BlockPos jobSite) {
		v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
		v.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(jobSite)));
		v.getBrain().setActiveActivityIfPossible(Activity.WORK);
	}

	/** Holds the show at a pedestal in the middle of the area with a trainer; nobody "came" (only those near and watching). */
	private static void holdShow(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager trainer = villager(helper, new BlockPos(1, 2, 1));
		trainer.setVillagerData(trainer.getVillagerData().setProfession(ModVillagers.TRAINER).setLevel(5));
		BlockPos pedestal = helper.absolutePos(new BlockPos(4, 2, 4));
		MegaShrines.show(level, pedestal, pedestal, trainer, Set.<UUID>of());
	}

	private static void cleanUp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MegaShrines.forgetShows(level);
		for (Entity e : level.getEntitiesOfClass(Entity.class, helper.getBounds().inflate(2), e -> !(e instanceof net.minecraft.world.entity.player.Player))) {
			e.discard();
		}
	}

	/** A farmer at work but seated (riding a minecart) by the pedestal is one of the crowd and gets the show's mood. */
	//$ gametest_batch AREA '"qaMegaShrineSeated"'
	@GameTest(template = AREA, batch = "qaMegaShrineSeated")
	public void qaASeatedVillagerAtWorkWatchesTheShow(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		try {
			Villager seated = villager(helper, new BlockPos(6, 2, 4));
			atWork(helper, seated, new BlockPos(7, 2, 5));
			Entity seat = helper.spawn(EntityType.MINECART, new BlockPos(6, 2, 4));
			helper.assertTrue(seated.startRiding(seat, true) && seated.isPassenger(), "the farmer couldn't sit in the minecart");
			Villager worker = villager(helper, new BlockPos(2, 2, 6));
			atWork(helper, worker, new BlockPos(1, 2, 6));
			helper.assertTrue(!Festivals.offWork(seated) && !Festivals.offWork(worker),
				"the farmers aren't at work: " + seated.getBrain().getActiveNonCoreActivity() + " " + worker.getBrain().getActiveNonCoreActivity());
			holdShow(helper);
			long today = Chronicle.day(level);
			helper.assertTrue(Long.valueOf(today).equals(ModAttachments.MEGA_SHOW_DAY.get(seated)) && MegaShrines.mood(level, seated) == MegaShrines.MOOD,
				"a seated villager by the pedestal didn't watch the show: mood " + MegaShrines.mood(level, seated));
			helper.assertTrue(ModAttachments.MEGA_SHOW_DAY.get(worker) == null && MegaShrines.mood(level, worker) == 0,
				"a farmer at work on foot got the show's mood: " + MegaShrines.mood(level, worker));
		} finally {
			cleanUp(helper);
		}
		helper.succeed();
	}

	/** A villager off work standing (not riding) by the pedestal is one of the crowd and gets the show's mood. */
	//$ gametest_batch AREA '"qaMegaShrineIdle"'
	@GameTest(template = AREA, batch = "qaMegaShrineIdle")
	public void qaAnIdleVillagerOnFootWatchesTheShow(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		try {
			Villager idle = villager(helper, new BlockPos(6, 2, 4));
			Villager worker = villager(helper, new BlockPos(2, 2, 6));
			atWork(helper, worker, new BlockPos(1, 2, 6));
			helper.assertTrue(!idle.isPassenger() && Festivals.offWork(idle), "the villager isn't off work on foot: " + idle.getBrain().getActiveNonCoreActivity());
			helper.assertTrue(!Festivals.offWork(worker), "the farmer isn't at work: " + worker.getBrain().getActiveNonCoreActivity());
			holdShow(helper);
			long today = Chronicle.day(level);
			helper.assertTrue(Long.valueOf(today).equals(ModAttachments.MEGA_SHOW_DAY.get(idle)) && MegaShrines.mood(level, idle) == MegaShrines.MOOD,
				"a villager off work by the pedestal didn't watch the show: mood " + MegaShrines.mood(level, idle));
			helper.assertTrue(ModAttachments.MEGA_SHOW_DAY.get(worker) == null && MegaShrines.mood(level, worker) == 0,
				"a farmer at work on foot got the show's mood: " + MegaShrines.mood(level, worker));
		} finally {
			cleanUp(helper);
		}
		helper.succeed();
	}
}
