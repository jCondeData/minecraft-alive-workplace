package io.github.jcondedata.aliveworkplace.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;

/**
 * QA stage 5's failing tests (2026-10-23), for the bug lane: each is a click a player makes that the mod doesn't answer
 * the way README or CHANGELOG says. They live on branch {@code tests/qa-stage5-clicks} until the fix lands with them.
 */
public class QaStageFiveBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/**
	 * The insignia in the OFF hand (main hand empty), right-clicked on the Bounty Board, is paid like from the main hand
	 * (README: "Hand it in at the board ... (right-click with it)"; stage 4's markers all work from the off hand).
	 */
	//$ gametest_ticks_batch HUGE '60' '"qa5InsigniaOff"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qa5InsigniaOff")
	public void insigniaFromTheOffHandIsPaidAtTheBoard(GameTestHelper helper) {
		QaStageFiveGameTests.insigniaHandedInAtTheBoard(helper, InteractionHand.OFF_HAND);
	}
	/** The insignia in the OFF hand on the village hall (README: "at the hall (right-click with it)") is paid. */
	//$ gametest_ticks_batch HUGE '60' '"qa5InsigniaHallOff"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qa5InsigniaHallOff")
	public void insigniaFromTheOffHandIsPaidAtTheHall(GameTestHelper helper) {
		QaStageFiveGameTests.insigniaHandedIn(helper, InteractionHand.OFF_HAND, true);
	}
}
