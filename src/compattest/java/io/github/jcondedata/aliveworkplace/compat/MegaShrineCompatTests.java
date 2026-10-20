package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trainer.MegaBracelets;
import io.github.jcondedata.aliveworkplace.trainer.MegaShrines;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * ROADMAP 38.7 with the real Mega Showdown: the Mega Shrine is listed at the Blueprint Table (not with {@code villageMegas}
 * off); on a festival day a village with a finished shrine holds its show there: the trainer at the pedestal Mega
 * Evolves their ace once, and the villagers watching are {@link MegaShrines#MOOD} happier, kept through a save.
 */
public class MegaShrineCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	/** The shrine's origin in the test area: its pedestal at (5, 4, 8), its trainer's spot at (5, 4, 7). */
	private static final BlockPos ORIGIN = new BlockPos(2, 1, 5);
	private static final BlockPos HALL = new BlockPos(1, 2, 1);

	/** A finished shrine remembered as the builders remember one; forgotten after the test. */
	private static void shrine(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.MEGA_SHRINE.id(), placement, UUID.randomUUID());
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		long time = level.getDayTime();
		level.setDayTime((Chronicle.day(level) - 1) * 24000L + Festivals.START + 100);
		PartnerShowsCompatTests.after(helper, () -> {
			BuildSiteManager.get(level).forgetFinished(placement);
			VillageHalls.RADIUS = radius;
			MegaBracelets.ENABLED = true;
			Festivals.forget();
			level.setDayTime(time);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(4))) {
				v.discard();
			}
		});
	}

	private static Villager villager(GameTestHelper helper, BlockPos at, boolean trainer) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		if (trainer) {
			v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.TRAINER).setLevel(3));
		}
		v.setNoAi(true);
		return v;
	}

	/** With Mega Showdown and {@code villageMegas} on, the Table lists the shrine; with it off, not (it still loads by id). */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theShrineIsListedWithMegaShowdown(GameTestHelper helper) {
		helper.assertTrue(MegaShrines.blocksExist() && MegaShrines.live(), "Mega Showdown's pedestal or villageMegas missing");
		List<?> listed = BlueprintLibrary.list(helper.getLevel().getServer(), false);
		helper.assertTrue(listed.contains(MegaShrines.SHRINE), "the Mega Shrine isn't at the Table with Mega Showdown");
		helper.assertTrue(BlueprintLibrary.get(helper.getLevel(), MegaShrines.SHRINE).isPresent(), "the Mega Shrine doesn't load");
		helper.succeed();
	}

	/** The Done when: at a finished shrine on a festival day the trainer's show is held once and its watchers get +3 mood, through a save. */
	//$ gametest_batch AREA '"mega_shrine"'
	@GameTest(template = AREA, batch = "mega_shrine")
	public void theFestivalShowIsHeldAtAFinishedShrine(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		shrine(helper);
		BlockPos hall = helper.absolutePos(HALL);
		villager(helper, new BlockPos(5, 4, 7), true);
		Villager near = villager(helper, new BlockPos(3, 2, 2), false);
		Villager other = villager(helper, new BlockPos(7, 2, 2), false);
		helper.assertTrue(MegaShrines.pedestal(level, hall).equals(java.util.Optional.of(helper.absolutePos(new BlockPos(5, 4, 8)))),
			"the pedestal is at " + MegaShrines.pedestal(level, hall));
		helper.assertTrue(MegaShrines.tick(level, hall), "the shrine didn't take the festival's crowd");
		long today = Chronicle.day(level);
		helper.assertTrue(Long.valueOf(today).equals(ModAttachments.MEGA_SHOW_DAY.get(near)) && Long.valueOf(today).equals(ModAttachments.MEGA_SHOW_DAY.get(other)),
			"the watchers didn't see the show");
		helper.assertTrue(MegaShrines.mood(level, near) == MegaShrines.MOOD && MegaShrines.MOOD == 3, "a watcher's mood: " + MegaShrines.mood(level, near));
		// Once a festival: a second tick holds no second show.
		ModAttachments.MEGA_SHOW_DAY.remove(other);
		MegaShrines.tick(level, hall);
		helper.assertTrue(ModAttachments.MEGA_SHOW_DAY.get(other) == null, "the show was held twice");
		// Saved with the villager.
		Villager copy = EntityType.VILLAGER.create(level);
		copy.load(near.saveWithoutId(new CompoundTag()));
		helper.assertTrue(MegaShrines.mood(level, copy) == MegaShrines.MOOD, "the show's mood lost in a save");
		ModAttachments.MEGA_SHOW_DAY.set(copy, today - Festivals.MOOD_DAYS - 1);
		helper.assertTrue(MegaShrines.mood(level, copy) == 0, "the show's mood lasts past a festival's");
		copy.discard();
		helper.succeed();
	}

	/**
	 * B191: the show comes with the festival itself, as in a real world. A village of six with a hall and a finished
	 * shrine, on the morning of its festival day: the hall's round plans the festival, and once its hours come the
	 * festival's own tick ({@code Festivals}, not the show's tick called by hand) holds the show and the watchers are happier.
	 */
	//$ gametest_ticks_batch AREA '400' '"mega_shrine_festival"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "mega_shrine_festival")
	public void theShowComesWithTheVillagesFestivalDay(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.MEGA_SHRINE.id(), placement, UUID.randomUUID());
		int radius = VillageHalls.RADIUS;
		int every = io.github.jcondedata.aliveworkplace.hall.VillageNeeds.CHECK_EVERY;
		long time = level.getDayTime();
		boolean festivals = Festivals.ENABLED;
		Festivals.ENABLED = true; // festivals are off in GameTest runs (the config): on for this test only
		VillageHalls.RADIUS = 16;
		io.github.jcondedata.aliveworkplace.hall.VillageNeeds.CHECK_EVERY = 100; // the hall's rounds come quicker than every 600 ticks
		PartnerShowsCompatTests.after(helper, () -> {
			BuildSiteManager.get(level).forgetFinished(placement);
			VillageHalls.RADIUS = radius;
			io.github.jcondedata.aliveworkplace.hall.VillageNeeds.CHECK_EVERY = every;
			Festivals.ENABLED = festivals;
			MegaBracelets.ENABLED = true;
			Festivals.forget();
			MegaShrines.forget();
			level.setDayTime(time);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(4))) {
				v.discard();
			}
		});
		helper.setBlock(HALL, io.github.jcondedata.aliveworkplace.registry.ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		var entity = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(hall);
		long festival = Festivals.nextDay(level, hall, entity);
		level.setDayTime((festival - 1) * 24000L + Festivals.START - 200); // the festival day's morning
		villager(helper, new BlockPos(5, 4, 7), true);
		List<Villager> watchers = new java.util.ArrayList<>();
		for (int i = 0; i < 5; i++) {
			watchers.add(villager(helper, new BlockPos(2 + i, 2, 2), false));
		}
		helper.assertTrue(!Festivals.isOn(level, hall) && watchers.stream().allMatch(v -> MegaShrines.mood(level, v) == 0), "a show before the festival");
		helper.succeedWhen(() -> {
			if (entity.festivalDay() == festival && Math.floorMod(level.getDayTime(), 24000L) < Festivals.START) {
				level.setDayTime((festival - 1) * 24000L + Festivals.START + 20); // planned: the afternoon comes, the festival begins
			}
			helper.assertTrue(entity.festivalDay() == festival, "the hall's round didn't plan the festival for day " + festival + ": " + entity.festivalDay());
			helper.assertTrue(Festivals.isOn(level, hall), "the festival isn't on");
			helper.assertTrue(watchers.stream().allMatch(v -> MegaShrines.mood(level, v) == MegaShrines.MOOD), "the festival held no show at the shrine");
		});
	}

	/**
	 * B215: the trainer at the pedestal waits for the villagers off work to come, and everyone who came cheers, even one
	 * at the far edge of the crowd (farther than {@link MegaShrines#WATCH_RANGE} from the pedestal). The show used to be
	 * held the moment the trainer got there, and only those standing near the pedestal just then got the mood. A villager
	 * who never comes doesn't hold it back past the show's last call.
	 */
	//$ gametest_batch AREA '"mega_shrine_crowd"'
	@GameTest(template = AREA, batch = "mega_shrine_crowd")
	public void theShowWaitsForTheCrowdAndCountsEveryoneWhoCame(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		shrine(helper);
		BlockPos hall = helper.absolutePos(HALL);
		villager(helper, new BlockPos(5, 4, 7), true); // at the pedestal already
		Villager near = villager(helper, new BlockPos(5, 2, 2), false); // at the crowd's spot
		Villager late = villager(helper, new BlockPos(5, 2, 0), false);
		late.setNoGravity(true);
		late.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(5.5, 2, -12.5))); // still on the way, off work
		helper.assertTrue(MegaShrines.tick(level, hall), "the shrine didn't take the festival's crowd");
		helper.assertTrue(ModAttachments.MEGA_SHOW_DAY.get(near) == null && ModAttachments.MEGA_SHOW_DAY.get(late) == null,
			"the show was held before the crowd had come");
		// The late one arrives at the crowd's far edge: at the crowd, but more than WATCH_RANGE from the pedestal.
		late.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(5.5, 2, -4.6)));
		BlockPos pedestal = MegaShrines.pedestal(level, hall).orElseThrow();
		helper.assertTrue(!late.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(pedestal).inflate(12)), "the far edge is within the watch range");
		MegaShrines.tick(level, hall);
		long today = Chronicle.day(level);
		helper.assertTrue(Long.valueOf(today).equals(ModAttachments.MEGA_SHOW_DAY.get(near)), "the show wasn't held once the crowd had come");
		helper.assertTrue(MegaShrines.mood(level, late) == MegaShrines.MOOD, "a villager who came to the shrine missed the show: mood " + MegaShrines.mood(level, late));
		// Next festival: one never comes (off work, far off); at the show's last call it is held without them.
		Festivals.forget();
		ModAttachments.MEGA_SHOW_DAY.remove(near);
		ModAttachments.MEGA_SHOW_DAY.remove(late);
		late.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(5.5, 2, -12.5)));
		MegaShrines.tick(level, hall);
		helper.assertTrue(ModAttachments.MEGA_SHOW_DAY.get(near) == null, "the show didn't wait for the one still coming");
		level.setDayTime((today - 1) * 24000L + Festivals.START + MegaShrines.SHOW_TICKS - 50);
		MegaShrines.tick(level, hall);
		helper.assertTrue(Long.valueOf(today).equals(ModAttachments.MEGA_SHOW_DAY.get(near)), "the show waited past its last call");
		helper.assertTrue(ModAttachments.MEGA_SHOW_DAY.get(late) == null, "one who never came saw the show");
		helper.succeed();
	}

	/** {@code villageMegas} off, no trainer, or past the show's hours: no show and nobody gets its mood. */
	//$ gametest_batch AREA '"mega_shrine_off"'
	@GameTest(template = AREA, batch = "mega_shrine_off")
	public void noShowWhenOffWithoutATrainerOrLate(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		shrine(helper);
		BlockPos hall = helper.absolutePos(HALL);
		Villager watcher = villager(helper, new BlockPos(3, 2, 2), false);
		MegaBracelets.ENABLED = false;
		helper.assertTrue(!MegaShrines.tick(level, hall), "the shrine's show with villageMegas off");
		helper.assertTrue(!BlueprintLibrary.list(level.getServer(), false).contains(MegaShrines.SHRINE), "the shrine listed with villageMegas off");
		MegaBracelets.ENABLED = true;
		helper.assertTrue(MegaShrines.tick(level, hall), "the crowd should gather at the shrine without a trainer too");
		helper.assertTrue(ModAttachments.MEGA_SHOW_DAY.get(watcher) == null, "a show without a trainer");
		level.setDayTime((Chronicle.day(level) - 1) * 24000L + Festivals.START + MegaShrines.SHOW_TICKS + 10);
		villager(helper, new BlockPos(5, 4, 7), true);
		helper.assertTrue(!MegaShrines.tick(level, hall), "the show after its hours");
		helper.assertTrue(ModAttachments.MEGA_SHOW_DAY.get(watcher) == null, "a show after its hours");
		helper.succeed();
	}

	/**
	 * The crowd is remembered for the whole festival day: a villager who came to the shrine and wandered off again still
	 * counts as come (the show doesn't wait for them) and still sees the show once the last one arrives.
	 */
	//$ gametest_batch AREA '"mega_shrine_came_and_left"'
	@GameTest(template = AREA, batch = "mega_shrine_came_and_left")
	public void oneWhoCameAndWanderedOffStillCounts(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		shrine(helper);
		Festivals.forget();
		BlockPos hall = helper.absolutePos(HALL);
		villager(helper, new BlockPos(5, 4, 7), true); // at the pedestal already
		Villager early = villager(helper, new BlockPos(5, 2, 2), false); // at the crowd's spot
		Villager late = villager(helper, new BlockPos(5, 2, 0), false);
		early.setNoGravity(true);
		late.setNoGravity(true);
		late.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(5.5, 2, -12.5))); // still on the way, off work
		MegaShrines.tick(level, hall);
		helper.assertTrue(ModAttachments.MEGA_SHOW_DAY.get(early) == null, "the show was held before the crowd had come");
		// The early one wanders off; the late one arrives.
		early.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(6.5, 2, -12.5)));
		late.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(5.5, 2, 2.5)));
		MegaShrines.tick(level, hall);
		long today = Chronicle.day(level);
		helper.assertTrue(Long.valueOf(today).equals(ModAttachments.MEGA_SHOW_DAY.get(late)), "the show waited for one who had already come and wandered off");
		helper.assertTrue(Long.valueOf(today).equals(ModAttachments.MEGA_SHOW_DAY.get(early)), "one who came and wandered off missed the show");
		helper.succeed();
	}
}
