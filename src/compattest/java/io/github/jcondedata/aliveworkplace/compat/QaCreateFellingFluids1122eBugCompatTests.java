package io.github.jcondedata.aliveworkplace.compat;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (lane-e-1122-2213): the protected cases of {@link QaCreateFellingFluids1122eCompatTests} that fail today (found
 * bugs), kept here and registered only with the fix. Their controls (the village open) pass in that class.
 *
 * <p>Seen 2026-11-22 on main 54f1091b7, 3 of 3 runs (run with tools/modtest/only_tests.py --compat after registering):
 * <ul>
 *   <li>Felling: Create's saw breaks only the trunk outside (BlockBreakerMixin / MovingBreakerMixin judge that one block),
 *       then SawBlockEntity.onBlockBroken / SawMovementBehaviour.onBlockBroken call TreeCutter.findTree and
 *       Tree.destroyBlocks on every connected log and leaf, unchecked: all 7 logs and leaves in the village's cells
 *       fall.</li>
 *   <li>Portable Fluid Interface: 27864 of 648000 droplets leave Frank's tank through his interface into the stranger's
 *       docked tank in 400 ticks (B1488, the item interface, has the same cause: the docking isn't judged).</li>
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

	/** A stranger's contraption docked at Frank's Portable Fluid Interface takes none of his water. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1122_pfi_protected"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1122_pfi_protected")
	public void aStrangersContraptionDrainsNothingThroughAProtectedVillagesFluidInterface(GameTestHelper helper) {
		QaCreateFellingFluids1122eCompatTests.fluidDock(helper, true);
	}
}
