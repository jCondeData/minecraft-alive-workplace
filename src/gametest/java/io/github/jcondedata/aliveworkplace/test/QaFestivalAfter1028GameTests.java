package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Curfew;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

/**
 * QA 2026-10-28 (lane E, saves): B605's saved festivalAfter, which no test read back (the nightly mutant deleting
 * {@code tag.putLong("festivalAfter", ...)} in VillageHallBlockEntity survived). A finished Wonder brings its festival in
 * that evening and the festival already planned for a later day must still come after it (35.8, B605), also when the
 * server restarts in between; a hall saved before B605 has no later festival.
 */
public class QaFestivalAfter1028GameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos HALL = new BlockPos(2, 2, 2);

	private static VillageHallBlockEntity reload(ServerLevel level, BlockPos hall, CompoundTag tag) {
		VillageHallBlockEntity loaded = new VillageHallBlockEntity(hall, level.getBlockState(hall));
		loaded.loadWithComponents(tag, level.registryAccess());
		return loaded;
	}

	/**
	 * A festival planned for two days on; a Wonder's festival is brought in tonight (Festivals.before, as
	 * WonderCelebration.finished does). The server saves and restarts in the afternoon: tonight's festival is still
	 * tonight, and once it is over the later one is the village's next festival, on its own day, not the usual round's.
	 */
	//$ gametest_batch AREA '"qaFestivalAfterSave1028"'
	@GameTest(template = AREA, batch = "qaFestivalAfterSave1028")
	public void theLaterFestivalStillComesAfterTheWondersThroughARestart(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long dayStart = (level.getDayTime() / 24000 + 1) * 24000;
		level.setDayTime(dayStart + 2000);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		long today = Chronicle.day(level);
		long later = today + 2;
		entity.setFestivalDay(later);
		Festivals.before(level, hall, entity, today);
		helper.assertTrue(entity.festivalDay() == today && entity.festivalAfter() == later,
			"Festivals.before didn't bring tonight's in and keep day " + later + ": day " + entity.festivalDay() + ", after " + entity.festivalAfter());

		VillageHallBlockEntity loaded = reload(level, hall, entity.saveWithFullMetadata(level.registryAccess()));
		helper.assertTrue(loaded.festivalDay() == today && loaded.festivalAfter() == later,
			"after a save and reload the hall has festival day " + loaded.festivalDay() + " and the later one " + loaded.festivalAfter()
				+ " (saved: " + today + " and " + later + ")");
		helper.assertTrue(Festivals.nextDay(level, hall, loaded) == today, "after the reload the next festival is day "
			+ Festivals.nextDay(level, hall, loaded) + ", not tonight's (day " + today + ")");

		// tonight's festival over: the planned one is next, kept through a second save and reload
		level.setDayTime(dayStart + Curfew.festivalEnd(loaded) + 20);
		VillageHallBlockEntity again = reload(level, hall, loaded.saveWithFullMetadata(level.registryAccess()));
		long next = Festivals.nextDay(level, hall, again);
		helper.assertTrue(next == later && again.festivalDay() == later && again.festivalAfter() == -1,
			"tonight's festival is over and the one planned for day " + later + " should be next: next " + next + ", day "
				+ again.festivalDay() + ", after " + again.festivalAfter());
		VillageHallBlockEntity settled = reload(level, hall, again.saveWithFullMetadata(level.registryAccess()));
		helper.assertTrue(settled.festivalDay() == later && settled.festivalAfter() == -1,
			"the settled festival day didn't survive a save: day " + settled.festivalDay() + ", after " + settled.festivalAfter());
		helper.succeed();
	}

	/** A hall saved before B605 (no festivalAfter in its tag) loads with no later festival waiting, its own day kept. */
	//$ gametest_batch AREA '"qaFestivalAfterOld1028"'
	@GameTest(template = AREA, batch = "qaFestivalAfterOld1028")
	public void aHallSavedBeforeB605HasNoLaterFestival(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		long today = Chronicle.day(level);
		entity.setFestivalDay(today + 3);
		entity.setFestivalAfter(today + 5);
		CompoundTag tag = entity.saveWithFullMetadata(level.registryAccess());
		helper.assertTrue(tag.contains("festivalAfter"), "the hall doesn't save its later festival at all");
		tag.remove("festivalAfter");
		VillageHallBlockEntity old = reload(level, hall, tag);
		helper.assertTrue(old.festivalDay() == today + 3 && old.festivalAfter() == -1,
			"a hall saved before B605 loads with festival day " + old.festivalDay() + " and later " + old.festivalAfter()
				+ " (expected " + (today + 3) + " and none, -1)");
		helper.succeed();
	}
}
