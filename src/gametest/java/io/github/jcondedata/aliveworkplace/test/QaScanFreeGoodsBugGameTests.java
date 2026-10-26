package io.github.jcondedata.aliveworkplace.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Bug tests (lane-b-1025-1838; B493-B496, fixed): the cases of {@link QaScanFreeGoodsGameTests} with the checks the mod
 * fails today (builder.md "Builders never copy what is inside containers"; kin of B429, B434). A scanned build, built by
 * a real builder from exactly its material list:
 * <ul>
 * <li>a lit furnace is built burning with no fuel (it glows and shows fire for nothing);</li>
 * <li>a lectern with a book and a jukebox with a disc are built empty but still showing the book and the disc;</li>
 * <li>a ripe field is built ripe: wheat at age 7 from one seed, nether wart at age 3 from one wart, a sweet berry bush
 * at age 3 from one berry, cave vines with a glow berry (free harvest);</li>
 * <li>a bee nest with bees is built with bees in it;</li>
 * <li>after a save and load mid-build the lectern still shows a book it doesn't hold.</li>
 * </ul>
 */
public class QaScanFreeGoodsBugGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	//$ gametest_ticks_batch HUGE '2400' '"qaScanFreeBugA"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaScanFreeBugA")
	public void aScannedLitFurnaceIsNotBuiltBurning(GameTestHelper helper) {
		QaScanFreeGoodsGameTests.aScannedChargedAnchorSnowCauldronAndLitFurnaceAreBuiltEmpty(helper, true);
	}

	//$ gametest_ticks_batch HUGE '2400' '"qaScanFreeBugB"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaScanFreeBugB")
	public void aBuiltLecternAndJukeboxShowNoBookOrDiscTheyLack(GameTestHelper helper) {
		QaScanFreeGoodsGameTests.aScannedLecternAndJukeboxShowOnlyWhatTheyHold(helper, true);
	}

	//$ gametest_ticks_batch HUGE '2400' '"qaScanFreeBugC"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaScanFreeBugC")
	public void aScannedRipeFieldIsBuiltUnripe(GameTestHelper helper) {
		QaScanFreeGoodsGameTests.aScannedRipeHarvestIsNotGrownForFree(helper, true);
	}

	//$ gametest_ticks_batch HUGE '2400' '"qaScanFreeBugD"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaScanFreeBugD")
	public void aScannedBeeNestIsBuiltWithoutBees(GameTestHelper helper) {
		QaScanFreeGoodsGameTests.aTurnedScanOfShelfNestAndPotHoldsNothing(helper, true);
	}

	//$ gametest_ticks_batch HUGE '3600' '"qaScanFreeBugE"'
	@GameTest(template = HUGE, timeoutTicks = 3600, batch = "qaScanFreeBugE")
	public void aReloadedScannedBuildShowsNoBookItLacks(GameTestHelper helper) {
		QaScanFreeGoodsGameTests.aScannedBuildSavedAndLoadedMidBuildStillGivesNothing(helper, true);
	}
}
