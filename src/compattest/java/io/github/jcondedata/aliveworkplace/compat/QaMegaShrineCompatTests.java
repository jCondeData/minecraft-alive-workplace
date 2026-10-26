package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trainer.MegaBracelets;
import io.github.jcondedata.aliveworkplace.trainer.MegaShrines;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

/**
 * QA of ROADMAP 38.7 (the Mega Shrine) with the real Mega Showdown, written from the spec, for what
 * {@code MegaShrineCompatTests} doesn't cover: no finished shrine (or another build there) holds no show, nor one outside
 * the village or another village's; a villager at work isn't gathered and isn't waited for; the mood is exactly +3, once,
 * however many ticks the show runs and through a restart; a day without a festival holds none through the real loop.
 */
public class QaMegaShrineCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	/** The shrine's origin in the test area: its pedestal at (5, 4, 8), its trainer's spot at (5, 4, 7), its crowd at (5, 2, 1). */
	static final BlockPos ORIGIN = new BlockPos(2, 1, 5);
	static final BlockPos HALL = new BlockPos(1, 2, 1);

	/** The show's hours on today's festival, a 16-block village; everything reset after the test. Returns the reset placements list. */
	static List<BlueprintData.Placement> setUp(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		List<BlueprintData.Placement> placements = new ArrayList<>();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		long time = level.getDayTime();
		Festivals.forget();
		level.setDayTime((Chronicle.day(level) - 1) * 24000L + Festivals.START + 100);
		PartnerShowsCompatTests.after(helper, () -> {
			for (BlueprintData.Placement p : placements) {
				BuildSiteManager.get(level).forgetFinished(p);
			}
			VillageHalls.RADIUS = radius;
			MegaBracelets.ENABLED = true;
			Festivals.forget();
			level.setDayTime(time);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(20))) {
				v.discard();
			}
		});
		return placements;
	}

	/** A finished build of {@code id} at {@code origin} (absolute), remembered as the builders remember one. */
	static void finished(GameTestHelper helper, List<BlueprintData.Placement> placements, net.minecraft.resources.ResourceLocation id, BlockPos origin) {
		ServerLevel level = helper.getLevel();
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(id, placement, UUID.randomUUID());
		placements.add(placement);
		if (id.equals(StarterBlueprints.MEGA_SHRINE.id())) {
			// The shrine's pedestal stands, as a builder leaves it (B421: without it there is no show).
			level.setBlockAndUpdate(io.github.jcondedata.aliveworkplace.hall.Arenas.world(placement, MegaShrines.PEDESTAL_SPOT),
				net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(MegaShrines.PEDESTAL).defaultBlockState());
		}
	}

	static Villager villager(GameTestHelper helper, BlockPos at, boolean trainer) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		if (trainer) {
			v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.TRAINER).setLevel(3));
		}
		v.setNoAi(true);
		v.setNoGravity(true);
		return v;
	}

	/** A villager at work at a workstation next to them. */
	static Villager worker(GameTestHelper helper, BlockPos at) {
		Villager v = villager(helper, at, false);
		v.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(at.east())));
		v.getBrain().setActiveActivityIfPossible(Activity.WORK);
		helper.assertTrue(v.getBrain().isActive(Activity.WORK), "the worker isn't at work: " + v.getBrain().getActiveNonCoreActivity());
		return v;
	}

	/** No finished shrine (none built, or another decoration on its spot): no show, no mood; the shrine finished: the show. */
	//$ gametest_batch AREA '"qa_mega_shrine_unfinished"'
	@GameTest(template = AREA, batch = "qa_mega_shrine_unfinished")
	public void qaNoShowWithoutAFinishedShrine(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<BlueprintData.Placement> placements = setUp(helper);
		BlockPos hall = helper.absolutePos(HALL);
		villager(helper, new BlockPos(5, 4, 7), true);
		Villager watcher = villager(helper, new BlockPos(5, 2, 2), false);
		helper.assertTrue(!MegaShrines.tick(level, hall) && MegaShrines.pedestal(level, hall).isEmpty(), "a show with no shrine built");
		finished(helper, placements, StarterBlueprints.WELL.id(), helper.absolutePos(ORIGIN));
		helper.assertTrue(!MegaShrines.tick(level, hall) && MegaShrines.pedestal(level, hall).isEmpty(), "a well was taken for the shrine");
		helper.assertTrue(ModAttachments.MEGA_SHOW_DAY.get(watcher) == null && MegaShrines.mood(level, watcher) == 0, "a show's mood without a shrine");
		finished(helper, placements, StarterBlueprints.MEGA_SHRINE.id(), helper.absolutePos(ORIGIN));
		helper.assertTrue(MegaShrines.tick(level, hall), "the finished shrine didn't take the crowd");
		helper.assertTrue(MegaShrines.mood(level, watcher) == MegaShrines.MOOD, "the finished shrine held no show: mood " + MegaShrines.mood(level, watcher));
		helper.succeed();
	}

	/**
	 * A shrine just outside the village (past the hall's radius) holds no show for it; neither does one near another
	 * village's hall: a hall 40 blocks off, whose village has no shrine, holds none at this village's.
	 */
	//$ gametest_batch AREA '"qa_mega_shrine_reach"'
	@GameTest(template = AREA, batch = "qa_mega_shrine_reach")
	public void qaAShrineOutsideTheVillageHoldsNoShow(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<BlueprintData.Placement> placements = setUp(helper);
		BlockPos hall = helper.absolutePos(HALL);
		villager(helper, new BlockPos(5, 4, 7), true);
		Villager watcher = villager(helper, new BlockPos(5, 2, 2), false);
		finished(helper, placements, StarterBlueprints.MEGA_SHRINE.id(), hall.offset(17, 0, 0)); // one block past the radius
		helper.assertTrue(!MegaShrines.tick(level, hall), "a shrine outside the village took its crowd");
		helper.assertTrue(MegaShrines.mood(level, watcher) == 0, "a shrine outside the village held a show");
		finished(helper, placements, StarterBlueprints.MEGA_SHRINE.id(), helper.absolutePos(ORIGIN));
		BlockPos otherHall = hall.offset(40, 0, 0);
		helper.assertTrue(!MegaShrines.tick(level, otherHall), "another village held its show at this village's shrine");
		helper.assertTrue(MegaShrines.mood(level, watcher) == 0, "another village's tick held this village's show");
		helper.assertTrue(MegaShrines.tick(level, hall) && MegaShrines.mood(level, watcher) == MegaShrines.MOOD, "the village's own shrine held no show");
		helper.succeed();
	}

	/**
	 * Only villagers off work gather: one at work far off isn't sent to the shrine, the show doesn't wait for them, and they
	 * don't get its mood; the idle one at the crowd's spot does.
	 */
	//$ gametest_batch AREA '"qa_mega_shrine_work"'
	@GameTest(template = AREA, batch = "qa_mega_shrine_work")
	public void qaAVillagerAtWorkIsntGatheredOrWaitedFor(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<BlueprintData.Placement> placements = setUp(helper);
		finished(helper, placements, StarterBlueprints.MEGA_SHRINE.id(), helper.absolutePos(ORIGIN));
		BlockPos hall = helper.absolutePos(HALL);
		villager(helper, new BlockPos(5, 4, 7), true);
		Villager idle = villager(helper, new BlockPos(5, 2, 2), false);
		Villager working = worker(helper, new BlockPos(5, 2, 0));
		working.moveTo(helper.absoluteVec(new Vec3(5.5, 2, -12.5))); // in the village, far from the shrine
		working.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		helper.assertTrue(MegaShrines.tick(level, hall), "the shrine didn't take the crowd");
		long today = Chronicle.day(level);
		helper.assertTrue(Long.valueOf(today).equals(ModAttachments.MEGA_SHOW_DAY.get(idle)), "the show waited for a villager at work");
		helper.assertTrue(working.getBrain().getMemory(MemoryModuleType.WALK_TARGET).isEmpty(), "a villager at work was sent to the shrine");
		helper.assertTrue(ModAttachments.MEGA_SHOW_DAY.get(working) == null && MegaShrines.mood(level, working) == 0,
			"a villager at work far off got the show's mood");
		helper.assertTrue(ModAttachments.FESTIVAL_DAY.get(working) == null, "a villager at work counted as come to the shrine");
		helper.succeed();
	}

	/**
	 * The mood is +3, once: the watcher's real mood is exactly 3 higher with the show (one reason), however many festival
	 * ticks the show's hours run after it, and a server restart in its hours (the shows held forgotten) doesn't add more.
	 */
	//$ gametest_batch AREA '"qa_mega_shrine_once"'
	@GameTest(template = AREA, batch = "qa_mega_shrine_once")
	public void qaTheMoodIsExactlyThreeOnceAShow(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<BlueprintData.Placement> placements = setUp(helper);
		finished(helper, placements, StarterBlueprints.MEGA_SHRINE.id(), helper.absolutePos(ORIGIN));
		BlockPos hall = helper.absolutePos(HALL);
		villager(helper, new BlockPos(5, 4, 7), true);
		Villager watcher = villager(helper, new BlockPos(5, 2, 2), false);
		for (int i = 0; i < 20; i++) {
			MegaShrines.tick(level, hall);
		}
		long today = Chronicle.day(level);
		helper.assertTrue(Long.valueOf(today).equals(ModAttachments.MEGA_SHOW_DAY.get(watcher)), "no show");
		Moods.Mood with = Moods.work(level, watcher);
		ModAttachments.MEGA_SHOW_DAY.remove(watcher);
		Moods.Mood without = Moods.work(level, watcher);
		helper.assertTrue(with.score() - without.score() == 3, "the show adds " + (with.score() - without.score()) + " after 20 ticks, not 3");
		long reasons = with.good().stream().filter(c -> c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
			&& t.getKey().equals("mood.aliveworkplace.reason.mega_show")).count();
		helper.assertTrue(reasons == 1, "the show's reason listed " + reasons + " times");
		ModAttachments.MEGA_SHOW_DAY.set(watcher, today);
		MegaShrines.forget(); // a server restart in the show's hours
		for (int i = 0; i < 5; i++) {
			MegaShrines.tick(level, hall);
		}
		helper.assertTrue(Moods.work(level, watcher).score() == with.score() && MegaShrines.mood(level, watcher) == MegaShrines.MOOD,
			"a restart in the show's hours changed the watcher's mood: " + Moods.work(level, watcher).score() + " vs " + with.score());
		helper.succeed();
	}

	/**
	 * The festival day is the boundary, through the real loop (the hall's rounds and the festival's tick): with a finished
	 * shrine, a trainer and villagers in the show's hours of a day without a festival, nobody gets the show's mood; once
	 * today is the village's festival, they all do.
	 */
	//$ gametest_ticks_batch AREA '500' '"qa_mega_shrine_no_festival"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_mega_shrine_no_festival")
	public void qaADayWithoutAFestivalHoldsNoShow(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<BlueprintData.Placement> placements = setUp(helper);
		finished(helper, placements, StarterBlueprints.MEGA_SHRINE.id(), helper.absolutePos(ORIGIN));
		int every = VillageNeeds.CHECK_EVERY;
		boolean festivals = Festivals.ENABLED;
		Festivals.ENABLED = true;
		VillageNeeds.CHECK_EVERY = 100;
		PartnerShowsCompatTests.after(helper, () -> {
			VillageNeeds.CHECK_EVERY = every;
			Festivals.ENABLED = festivals;
			MegaShrines.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		long today = Chronicle.day(level);
		entity.setFestivalDay(today - 1); // yesterday's festival; today none (and past the morning, when one is planned)
		villager(helper, new BlockPos(5, 4, 7), true);
		List<Villager> watchers = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			watchers.add(villager(helper, new BlockPos(2 + i, 2, 2), false));
		}
		AtomicBoolean festival = new AtomicBoolean();
		helper.runAfterDelay(250, () -> {
			helper.assertTrue(entity.festivalDay() != Chronicle.day(level), "a festival was planned today: " + entity.festivalDay());
			helper.assertTrue(!Festivals.isOn(level, hall), "a festival is on");
			helper.assertTrue(watchers.stream().allMatch(v -> MegaShrines.mood(level, v) == 0 && ModAttachments.MEGA_SHOW_DAY.get(v) == null),
				"a show on a day without a festival");
			entity.setFestivalDay(Chronicle.day(level)); // now today is the festival
			festival.set(true);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(festival.get(), "still the day without a festival");
			helper.assertTrue(Festivals.isOn(level, hall), "the festival isn't on");
			helper.assertTrue(watchers.stream().allMatch(v -> MegaShrines.mood(level, v) == MegaShrines.MOOD), "the festival held no show at the shrine");
		});
	}
}
