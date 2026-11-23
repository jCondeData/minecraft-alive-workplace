package io.github.jcondedata.aliveworkplace.compat;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (lane-e-1122-2213, B1518): the protected felling cases of {@link QaCreateFellingFluids1122eCompatTests}, which
 * failed before B1518's fix (FellingMixin). Their controls (the village open) pass in that class. The Portable Fluid
 * Interface case (B1519) moved to QaCreatePortableTrains1122eBugCompatTests.
 *
 * <p>Seen 2026-11-22 on main 54f1091b7, 3 of 3 runs (run with tools/modtest/only_tests.py --compat after registering):
 * <ul>
 *   <li>Felling: Create's saw breaks only the trunk outside (BlockBreakerMixin / MovingBreakerMixin judge that one block),
 *       then SawBlockEntity.onBlockBroken / SawMovementBehaviour.onBlockBroken call TreeCutter.findTree and
 *       Tree.destroyBlocks on every connected log and leaf, unchecked: all 7 logs and leaves in the village's cells
 *       fall.</li>
 * </ul>
 */
public class QaCreateFellingFluids1122eBugCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	/** A stranger's saw outside the protected village cuts a tree whose branch reaches in: nothing inside may fall. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122_fell_saw_protected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122_fell_saw_protected")
	public void aStrangersSawFellsNothingInsideAProtectedVillage(GameTestHelper helper) {
		QaCreateFellingFluids1122eCompatTests.felling(helper, true, false);
	}

	/** The same tree cut by a saw on a stranger's piston outside the protected village: nothing inside may fall. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122_fell_piston_protected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122_fell_piston_protected")
	public void aStrangersPistonSawFellsNothingInsideAProtectedVillage(GameTestHelper helper) {
		QaCreateFellingFluids1122eCompatTests.felling(helper, true, true);
	}
}
