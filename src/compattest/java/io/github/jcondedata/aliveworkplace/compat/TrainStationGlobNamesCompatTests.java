package io.github.jcondedata.aliveworkplace.compat;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;
import io.github.jcondedata.aliveworkplace.compat.create.CreateTrains;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * B938: Create reads a schedule stop's text as a glob, so the village train's stops hold its stations' names with the glob
 * characters escaped. Each name is found by its own stop and by no other name; a station named with a backslash and every
 * glob character still gets the caravan; and a stop saved before the fix (the name as it is) is re-pointed and works.
 * The runs are {@link QaTrainStationGlobNames1104eCompatTests}'s.
 */
public class TrainStationGlobNamesCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	/** Through Create's own reading of a stop (DestinationInstruction's glob to regex). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aStopFindsExactlyItsStationsName(GameTestHelper helper) {
		List<String> names = List.of("Bramble", "Bramble [East]", "Bramble E", "Platform {2}", "Platform 2", "Main [", "Main",
			"Bramble?", "BrambleX", "Platform*", "Platform 9", "Back\\slash", "Back\\", "[!x]", "{a,b}", "a", "*", "?", "Odd ]} ,");
		for (String name : names) {
			DestinationInstruction to = new DestinationInstruction();
			String text = CreateTrains.stopText(name);
			to.getData().putString("Text", text);
			String regex = to.getFilterForRegex();
			helper.assertTrue(CreateTrains.stationOf(text).equals(name), "'" + name + "' doesn't come back from its stop text '" + text + "'");
			for (String other : names) {
				boolean fits = other.matches(regex);
				helper.assertTrue(fits == other.equals(name), "the stop for '" + name + "' (text '" + text + "', pattern '" + regex + "') "
					+ (fits ? "also finds '" + other + "'" : "doesn't find its own station"));
			}
		}
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '3600' '"trainStationEveryGlobChar"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "trainStationEveryGlobChar")
	public void aStationNamedWithEveryGlobCharacterStillGetsTheCaravan(GameTestHelper helper) {
		QaTrainStationGlobNames1104eCompatTests.run(helper, QaTrainStationGlobNames1104eCompatTests.STATION_B, "B\\r*a?m[b{l,e}", t -> { });
	}

	/** A world saved before B938: the stop holds the bracketed name as it is, which Create's glob wouldn't find. */
	//$ gametest_ticks_batch AREA '3600' '"trainStationOldStop"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "trainStationOldStop")
	public void aStopSavedBeforeTheFixIsRepointed(GameTestHelper helper) {
		String name = "Bramble [East]";
		QaTrainStationGlobNames1104eCompatTests.run(helper, QaTrainStationGlobNames1104eCompatTests.STATION_B, name, owner -> {
			Train train = Create.RAILWAYS.trains.values().stream().filter(t -> owner.equals(t.owner) && !t.invalid).findFirst().orElse(null);
			helper.assertTrue(train != null && train.runtime.getSchedule() != null, "setup: the village train has no schedule");
			Schedule schedule = train.runtime.getSchedule();
			String home = QaTrainStationNames1102CompatTests.name(helper.getLevel(), helper.absolutePos(QaTrainStationGlobNames1104eCompatTests.STATION_A));
			boolean found = false;
			for (int i = 0; i < schedule.entries.size(); i++) {
				if (schedule.entries.get(i).instruction instanceof DestinationInstruction to) {
					String text = to.getData().getString("Text");
					if (!CreateTrains.stationOf(text).equals(home)) {
						to.getData().putString("Text", name); // the renamed station's stop as the mod wrote it before the fix
						found = true;
					}
				}
			}
			helper.assertTrue(found, "setup: every stop is the home station '" + home + "'");
		});
	}
}
