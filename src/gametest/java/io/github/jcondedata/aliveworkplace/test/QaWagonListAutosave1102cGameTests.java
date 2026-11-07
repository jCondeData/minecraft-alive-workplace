package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.camp.CampBeds;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

/**
 * QA (1102c) of B754 at its other door: "such a list is now saved at the first autosave, and saving it ends the look".
 * QaWagonBedsFirstSession1101BugTests covers an older list read from disk; this is the dimension that had no list until
 * after its first ticks (an older save opened with this version, its clock already running). Its first list judges
 * unlisted beds by the old camp look, and the server's own autosave must write it and end that look. The nightly
 * mutation run of 2026-11-02 found nothing covering it (CampBeds.java:111's setDirty deleted, the mutant survived).
 */
public class QaWagonListAutosave1102cGameTests implements FabricGameTest {
	/** The list a dimension starts when it has none and its clock is past a new world's first ticks. */
	private static CampBeds.Data firstListOfAnOlderSave(ServerLevel level) throws ReflectiveOperationException {
		Method fresh = CampBeds.Data.class.getDeclaredMethod("fresh", ServerLevel.class);
		fresh.setAccessible(true);
		return (CampBeds.Data) fresh.invoke(null, level);
	}

	/** The world's autosave writes that first list and ends the look; nobody has to ask about a bed first. */
	//$ gametest_ticks_batch EMPTY_STRUCTURE '40' '"qaWb1102cAutosave"'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40, batch = "qaWb1102cAutosave")
	public void qaAnOlderSavesFirstListIsWrittenByTheAutosaveAndStopsLooking(GameTestHelper helper) throws ReflectiveOperationException {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(level.getGameTime() > 100, "set-up: the clock is still in a new world's first ticks: " + level.getGameTime());
		CampBeds.Data was = CampBeds.Data.get(level);
		Leftovers.after(helper, () -> {
			level.getDataStorage().set(CampBeds.Data.NAME, was);
			was.setDirty();
		});
		CampBeds.Data first = firstListOfAnOlderSave(level);
		helper.assertTrue(first.looks() && !first.complete(), "set-up: an older save's first list doesn't look at unlisted beds (looks "
			+ first.looks() + ", complete " + first.complete() + ")");
		level.getDataStorage().set(CampBeds.Data.NAME, first);
		level.getDataStorage().save(); // what the server's autosave does with every dimension's saved data
		helper.assertTrue(!first.looks(), "the first autosave didn't write the older save's new wagon-bed list (dirty " + first.isDirty()
			+ "), so its camp look lasts until a restart (B754)");
		helper.succeed();
	}
}
