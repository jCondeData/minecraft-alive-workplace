package io.github.jcondedata.aliveworkplace.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (lane-b-1027-0038): what {@link QaScanLooks1027GameTests} found wrong, failing until the mod is fixed.
 * <ul>
 * <li>a scanned ripe pitcher crop (age 4, both halves) is built ripe from one pitcher pod, and breaks into a pitcher
 * plant at once (builder.md: "Crops are planted, never built ripe").</li>
 * </ul>
 */
public class QaScanLooks1027BugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	//$ gametest_ticks_batch HUGE '2400' '"qaScanLooksPitcherBug"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaScanLooksPitcherBug")
	public void aScannedRipePitcherCropIsNotBuiltRipe(GameTestHelper helper) {
		QaScanLooks1027GameTests.pitcher(helper);
	}
}
