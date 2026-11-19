package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.Refreshes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * QA 2026-10-28 (lane E, saves and upgrades), B661/B662 for the modded furniture library (37.12): a build furnished from
 * the modded library is refreshed from its copy's older drawings ({@code blueprint_history/modded/<path>},
 * Refreshes.historyId). Every modded copy redrawn since v0.151.0 ships 0.151.0's drawing there (worked out with git:
 * the copy's file at the tag, by content), except the Clinic's: B639 redrew {@code structure/modded/clinic.nbt} too, but
 * {@code blueprint_history/modded/clinic/} doesn't exist, so a Clinic built with modded furniture on 0.151.0 (its
 * slab-and-carpet cots) is never offered Refresh to the latest look. tools/blueprints/history.py skips modded copies, so
 * its --check can't catch this.
 */
public class QaModdedClinicHistoryBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	/** The modded copies redrawn since v0.151.0 and the key their 0.151.0 drawing is shipped under. */
	static final List<String[]> REDRAWN = List.of(
		new String[] {"clinic", "2f10c0ccc"}, new String[] {"terrace", "11b48d517"}, new String[] {"terrace_2", "11b48d517"},
		new String[] {"townhouse", "cd1557d8c"}, new String[] {"storehouse", "cd1557d8c"}, new String[] {"daycare", "cd1557d8c"},
		new String[] {"barracks_3", "cd1557d8c"}, new String[] {"jewellers_workshop", "cd1557d8c"});

	//$ gametest_ticks EMPTY_STRUCTURE '20'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void qaEveryModdedCopyRedrawnSinceTheReleaseShipsItsReleaseDrawing(GameTestHelper helper) {
		List<String> missing = new ArrayList<>();
		for (String[] r : REDRAWN) {
			ResourceLocation copy = AliveWorkplace.id("modded/" + r[0]);
			if (Refreshes.olderDrawing(helper.getLevel().getServer(), copy, r[1]).isEmpty()) {
				missing.add(copy + " (" + r[1] + ")");
			}
		}
		helper.assertTrue(missing.isEmpty(), "modded copies redrawn since 0.151.0 ship no 0.151.0 drawing in blueprint_history, so a build "
			+ "furnished from the modded library can't be refreshed: " + missing);
		helper.succeed();
	}
}
