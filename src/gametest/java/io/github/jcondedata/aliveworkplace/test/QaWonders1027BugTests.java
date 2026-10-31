package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.wonder.WonderCelebration;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

/**
 * QA (qa-lane-b-1027-1241) bug candidates for a finished Wonder (ROADMAP 35.8). These fail on the mod as it is and are
 * not registered; each one's Javadoc says what the spec wants.
 */
public class QaWonders1027BugTests {
	private static final String TALL = "aliveworkplace_test:tall_area";

	/**
	 * 35.8: "When the last stage is done ... the village holds a festival that evening". A village with an ordinary
	 * festival already planned two days on (bought at the hall, or a Noble's ball) finishes its Wonder in the morning:
	 * the Wonder's festival should still be that evening, not wait for the later one.
	 */
	//$ gametest_batch TALL '"qaWonderLaterFestival1027"'
	@GameTest(template = TALL, batch = "qaWonderLaterFestival1027")
	public void aWonderFinishedWithALaterFestivalPlannedCelebratesThatEvening(GameTestHelper helper) {
		QaWonders1027GameTests.Finished f = QaWonders1027GameTests.finishedFolly(helper, null);
		ServerLevel level = f.level();
		long dayStart = (level.getDayTime() / 24000 + 1) * 24000;
		level.setDayTime(dayStart + 2000);
		long day = Chronicle.day(level);
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(f.hall());
		hall.setFestivalDay(day + 2);
		WonderCelebration.finished(level, f.site(), day);
		WonderSite now = f.site();
		helper.assertTrue(now.kept().festival() == day && WonderCelebration.celebrating(level, f.hall()).isPresent(),
			"finished on day " + day + " with a festival planned for day " + (day + 2) + ": the Wonder's festival is day " + now.kept().festival()
				+ ", the hall's day " + hall.festivalDay() + ", celebrating tonight: " + WonderCelebration.celebrating(level, f.hall()).isPresent());
		helper.succeed();
	}
}
