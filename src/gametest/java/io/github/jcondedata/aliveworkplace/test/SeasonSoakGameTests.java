package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.command.SeasonSoak;
import io.github.jcondedata.aliveworkplace.command.SeasonSoak.Day;
import io.github.jcondedata.aliveworkplace.command.SeasonSoak.Part;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.work.Pace;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * ROADMAP 30.22, a season under the edicts ({@code /workplace season}, run on the pack server by {@code SEASON=true
 * tools/packtest/run.sh}): how the run's result line judges the days it recorded. The costs show only when the store
 * gave out more a day under the edicts than reformed and the treasury paid for festivals under the edicts alone;
 * a store empty for a whole day, a pace beyond the cap or a village that shrank each count as running away; a part
 * run on its own isn't judged on costs. The run's four edicts are ones the mod has.
 */
public class SeasonSoakGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	/** A day as a healthy run records it: {@code taken} meals from the store, {@code paid} emeralds for a festival. */
	private static Day day(int number, boolean reformed, int taken, int paid, int villagers) {
		return new Day(number, reformed, taken, 200, false, 1, villagers, 0, 1, 60f, 50f, 20, paid, paid > 0, false,
			List.of(20, 20, 20, 20, 20), List.of(80, 80, 80, 80, 80), 100, Pace.cap(), 4.0, 40.0, 0, 90, 120);
	}

	/** Four days under the edicts, four reformed, villagers growing by one a day. */
	private static List<Day> season(int takenEdicts, int takenReformed, int paidEdicts, int paidReformed) {
		List<Day> days = new ArrayList<>();
		for (int d = 1; d <= 8; d++) {
			boolean reformed = d > 4;
			days.add(day(d, reformed, reformed ? takenReformed : takenEdicts, d == 2 ? paidEdicts : d == 6 ? paidReformed : 0, 35 + d));
		}
		return days;
	}

	/** More meals a day under the edicts, a festival paid for under them and free once reformed, nothing off: it passes, with the numbers in the line. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aSeasonWhoseCostsShowAndNothingRunsAwayPasses(GameTestHelper helper) {
		String result = SeasonSoak.result(season(80, 60, 11, 0), Part.BOTH, 35);
		helper.assertTrue(SeasonSoak.passed(result), result);
		helper.assertTrue(result.contains("under the edicts (4 days): 80.0 meals a day from the store, 4 births, 11 emeralds for festivals"), result);
		helper.assertTrue(result.contains("reformed (4 days): 60.0 meals a day from the store, 4 births, 0 emeralds for festivals"), result);
		helper.assertTrue(result.contains("costs show; ran away: nothing") && result.contains("villagers 35 to 43, never fewer"), result);
		helper.assertTrue(result.contains("fastest pace +100%") && result.contains("worst 40 ms, 0 over 50 ms"), result);
		helper.succeed();
	}

	/** The store gave out no more under the edicts, or the treasury paid nothing under them, or still paid once reformed: the costs don't show. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void costsThatDontShowFailTheSeason(GameTestHelper helper) {
		String sameStore = SeasonSoak.result(season(60, 60, 11, 0), Part.BOTH, 35);
		helper.assertTrue(!SeasonSoak.passed(sameStore) && sameStore.contains("the store gave out no more under the edicts"), sameStore);
		String noFestivalBill = SeasonSoak.result(season(80, 60, 0, 0), Part.BOTH, 35);
		helper.assertTrue(!SeasonSoak.passed(noFestivalBill) && noFestivalBill.contains("the treasury paid 0 under the edicts, 0 reformed"), noFestivalBill);
		String stillBilled = SeasonSoak.result(season(80, 60, 11, 11), Part.BOTH, 35);
		helper.assertTrue(!SeasonSoak.passed(stillBilled) && stillBilled.contains("the treasury paid 11 under the edicts, 11 reformed"), stillBilled);
		helper.succeed();
	}

	/** A store empty for a whole day, work faster than the cap allows, or a villager fewer than the day before: each is running away. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void anythingThatRunsAwayFailsTheSeason(GameTestHelper helper) {
		List<Day> empty = season(80, 60, 11, 0);
		Day d3 = empty.get(2);
		empty.set(2, new Day(d3.number(), d3.reformed(), d3.taken(), 0, true, d3.births(), d3.villagers(), d3.ill(), d3.illPeak(), d3.moodNoon(),
			d3.moodDusk(), d3.treasury(), d3.festivalPaid(), d3.festival(), d3.festivalMissed(), d3.pace(), d3.rushPace(), d3.fastest(),
			d3.slowestFactor(), d3.tickMs(), d3.worstMs(), d3.over50(), d3.stored(), d3.harvested()));
		String emptied = SeasonSoak.result(empty, Part.BOTH, 35);
		helper.assertTrue(!SeasonSoak.passed(emptied) && emptied.contains("ran away: YES (store empty a whole day: yes"), emptied);

		List<Day> fast = season(80, 60, 11, 0);
		Day d5 = fast.get(4);
		fast.set(4, new Day(d5.number(), d5.reformed(), d5.taken(), d5.left(), false, d5.births(), d5.villagers(), d5.ill(), d5.illPeak(), d5.moodNoon(),
			d5.moodDusk(), d5.treasury(), d5.festivalPaid(), d5.festival(), d5.festivalMissed(), d5.pace(), d5.rushPace(), 130,
			Pace.cap() - 0.07f, d5.tickMs(), d5.worstMs(), d5.over50(), d5.stored(), d5.harvested()));
		String beyond = SeasonSoak.result(fast, Part.BOTH, 35);
		helper.assertTrue(!SeasonSoak.passed(beyond) && beyond.contains("BEYOND THE CAP") && beyond.contains("fastest pace +130%"), beyond);

		List<Day> fewer = season(80, 60, 11, 0);
		fewer.set(6, day(7, true, 60, 0, 40)); // 41 the day before
		String shrank = SeasonSoak.result(fewer, Part.BOTH, 35);
		helper.assertTrue(!SeasonSoak.passed(shrank) && shrank.contains(", SHRANK"), shrank);
		// Fewer than at the start on the very first day counts too.
		String first = SeasonSoak.result(List.of(day(1, false, 80, 0, 34)), Part.EDICTS, 35);
		helper.assertTrue(!SeasonSoak.passed(first) && first.contains("villagers 35 to 34, SHRANK"), first);
		helper.succeed();
	}

	/** One part on its own has nothing to compare its costs with: they aren't judged, and the run still fails on anything that ran away. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aPartOnItsOwnIsJudgedOnlyOnWhatRunsAway(GameTestHelper helper) {
		List<Day> days = season(80, 60, 11, 0).subList(0, 4);
		String result = SeasonSoak.result(days, Part.EDICTS, 35);
		helper.assertTrue(SeasonSoak.passed(result) && result.contains("costs not judged (one part)") && result.contains("reformed (0 days): not run"), result);
		helper.assertTrue(!SeasonSoak.passed("Season: a city of 35 villagers"), "a line that isn't a result passed");
		helper.succeed();
	}

	/** The run's four edicts exist, and each has a reform (the second half of the season reforms them all). */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theSeasonsFourEdictsExistAndCanBeReformed(GameTestHelper helper) {
		helper.assertTrue(SeasonSoak.EDICTS.size() == 4, "edicts: " + SeasonSoak.EDICTS);
		for (String id : SeasonSoak.EDICTS) {
			helper.assertTrue(Edicts.find(id).isPresent(), "no edict " + id);
			helper.assertTrue(Edicts.find(id).get().reform().isPresent(), id + " has no reform");
		}
		helper.succeed();
	}
}
