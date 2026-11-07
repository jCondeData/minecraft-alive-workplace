package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (lane-c-1107-1553) bug test for B1009's fix and older saves: a novice Carpenter whose crafting job was saved before
 * the fix (no `profession` in it) is loaded, his bench broken before he works again, and he takes a free lectern as a
 * Scholar. The old rule counts the lectern as his bench, so the job, the builder's request and the 6 logs in his bag are
 * kept for good. Setup: {@link QaBagNewJob1107cGameTests#carpenter}.
 */
public class QaBagNewJob1107cBugTests {
	private static final String AREA = QaCrafterFurnitureGameTests.AREA;

	/** Loaded from an old save, bench broken, made a Scholar: the request must still be let go and the logs come back. */
	//$ gametest_ticks_batch AREA '7000' '"qa1107cCarpenterOldSaveScholar"'
	@GameTest(template = AREA, timeoutTicks = 7000, batch = "qa1107cCarpenterOldSaveScholar")
	public void qaACarpenterFromAnOldSaveWhoBecomesAScholarFreesTheRequest1107c(GameTestHelper helper) {
		QaBagNewJob1107cGameTests.carpenter(helper, true, true, ModVillagers.SCHOLAR);
	}
}
