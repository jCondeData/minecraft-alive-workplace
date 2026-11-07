package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.camp.CampBeds;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

/**
 * QA (2026-11-01, lane-e-1101-0413) of B749 on a save upgraded from before B715: the fix lets the old camp look judge
 * unlisted beds only in "the save's first session with this version". On a dedicated server that session runs until the
 * next restart (days on a live server), and the look stays on after the list has been autosaved in the current format,
 * though {@code CampBeds.Data#looks} says it lasts "until the list is saved again". A cabin bed placed in that session is
 * listed for good.
 */
public class QaWagonBedsFirstSession1101BugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/**
	 * A server upgraded from a version whose wagon-bed list predates B715 (no "version"): the list has been autosaved in
	 * this version's format since, and the server is still running (no restart). The player now builds a cabin with a
	 * brown and a green bed by a campfire, a hay bale and the village builder's own Blueprint Table. No camp placed those
	 * beds and they were placed after the village was founded. Expected (B732, B749: "a bed no camp placed stays a home on
	 * an existing server too"): both stay homes, and the list doesn't take them.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaWb1101FirstSession"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaWb1101FirstSession")
	public void qaACabinBedPlacedAfterTheUpgradesAutosaveStaysAHome(GameTestHelper helper) {
		QaVillageGuards1031GameTests.campArea(helper);
		ServerLevel level = helper.getLevel();
		// the list as a version before B715 saved it: no "version", no "complete", no beds near here
		CampBeds.Data older = CampBeds.Data.load(new CompoundTag(), level.registryAccess());
		level.getDataStorage().set(CampBeds.Data.NAME, older);
		helper.assertTrue(CampBeds.Data.get(level).looks(), "set-up: a list from before B715 doesn't look at unlisted beds");
		// the server's autosave writes the list in this version's format; the server keeps running
		CompoundTag autosaved = CampBeds.Data.get(level).save(new CompoundTag(), level.registryAccess());
		helper.assertTrue(autosaved.getInt("version") >= 2, "set-up: the autosave didn't write the current format: " + autosaved);
		QaVillageFixes1101GameTests.cabinRound(helper, (beds, residents) -> {
			CampBeds.Data live = CampBeds.Data.get(level);
			for (int i = 0; i < 2; i++) {
				boolean wagon = CampBeds.isWagonBed(level, beds.get(i));
				helper.assertTrue(!wagon && beds.get(i).equals(QaVillageGuards1031GameTests.home(residents.get(i))),
					"after the upgrade's list was autosaved, a cabin bed placed by the builder's table is a wagon bed: bed " + i + " wagon " + wagon
						+ ", listed for good " + live.has(beds.get(i)) + ", looks " + live.looks() + ", home "
						+ QaVillageGuards1031GameTests.home(residents.get(i)));
			}
			helper.succeed();
		});
	}

	/**
	 * Control (passes on the B749 fix): the same upgraded list read back from that autosave (a restart), then the same
	 * cabin: both beds are homes. Proves the cabin itself is fine and only the running session differs.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaWb1101AfterRestart"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaWb1101AfterRestart")
	public void qaTheSameCabinAfterARestartIsAHome(GameTestHelper helper) {
		QaVillageGuards1031GameTests.campArea(helper);
		ServerLevel level = helper.getLevel();
		CampBeds.Data older = CampBeds.Data.load(new CompoundTag(), level.registryAccess());
		CampBeds.Data restarted = CampBeds.Data.load(older.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
		level.getDataStorage().set(CampBeds.Data.NAME, restarted);
		helper.assertTrue(!restarted.looks(), "set-up: a list saved in this format still looks at unlisted beds after a restart");
		QaVillageFixes1101GameTests.cabinRound(helper, (beds, residents) -> {
			for (int i = 0; i < 2; i++) {
				helper.assertTrue(!CampBeds.isWagonBed(level, beds.get(i)) && beds.get(i).equals(QaVillageGuards1031GameTests.home(residents.get(i))),
					"after a restart, cabin bed " + i + " is a wagon bed or lost its resident: home " + QaVillageGuards1031GameTests.home(residents.get(i)));
			}
			helper.succeed();
		});
	}
}
