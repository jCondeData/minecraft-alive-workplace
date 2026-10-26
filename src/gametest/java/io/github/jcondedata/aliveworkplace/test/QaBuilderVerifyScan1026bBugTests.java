package io.github.jcondedata.aliveworkplace.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * BUG (QA lane-b-1026-1248, not registered on main): a Mailbox scanned while holding mail is built empty (its letters
 * aren't copied) but still showing its mail flag and giving a comparator signal of 15 (builder.md: "Builders never copy
 * what is inside containers, nor what a block holds in its look"; kin of B494's lectern book and jukebox disc).
 * Expected: built with {@code has_mail=false}. The case lives in {@link QaBuilderVerifyScan1026bGameTests}.
 */
public class QaBuilderVerifyScan1026bBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	//$ gametest_ticks_batch HUGE '2400' '"qaV1026bScanMailBug"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaV1026bScanMailBug")
	public void aScannedMailboxWithMailIsBuiltEmptyAndShowsNoMail(GameTestHelper helper) {
		QaBuilderVerifyScan1026bGameTests.aScannedMailboxWithMailIsBuiltEmptyAndShowsNoMail(helper);
	}
}
