package io.github.jcondedata.aliveworkplace.compat;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.material.Fluids;

/**
 * QA (lane-e-1123) bug findings, NOT registered (they fail until the mod closes these holes): the protected cases of
 * {@link QaCreatePipePackager1123eCompatTests} that fail. Spec: create.md, "Create's machines keep out of a protected
 * village" ("Fluids stop at the edge too"; "Item transport stops at the edge too: a funnel or chute outside finds no
 * inventory in a chest or barrel inside the village (B994)"), and B994 (a stranger's Create machine outside takes
 * nothing from, and puts nothing into, a container inside a protected village).
 *
 * <ul>
 *   <li>Pipes: a stranger's Mechanical Pump at (13, 2, 15) with a Fluid Pipe at (12, 2, 15) on the outside face of
 *       Frank's Fluid Tank or Basin at (11, 2, 15), the village's last column, drains and fills it (nothing guards
 *       Create's pipe network's look-up of the tank's fluid storage).</li>
 *   <li>Packager unpacking: a package pushed into a stranger's Packager at (12, 2, 15) unpacks into Frank's chest at
 *       (11, 2, 15): Create's default unpacking handler finds the chest with {@code ItemStorage.SIDED} directly, past
 *       the guarded inventory behaviour (packing from the chest is refused, and tested in the registered class).</li>
 * </ul>
 *
 * Round 3 (B1546's edges, for lane A's fix): the same drain through a Smart Fluid Pipe, a glass and an encased Fluid
 * Pipe as the stranger's piece on the edge; Frank's tank three blocks tall (a 2-wide tank can't
 * stand in the last column: Create's tanks are square, and on z = 14 or 16 x = 11 is already outside); and protection
 * switched on mid-drain.
 *
 * Their open-village controls (in the registered class) pass, so the set-up moves fluid and items. Register this class
 * in src/compattest/resources/fabric.mod.json once the holes are fixed.
 */
public class QaCreatePipePackager1123eBugCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	/** A stranger's pump and pipe outside take no water out of Frank's tank in his protected village's last column. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1123p_pipe_drain_protected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1123p_pipe_drain_protected")
	public void aStrangersPipeDrainsNothingFromATankInAProtectedVillage(GameTestHelper helper) {
		QaCreatePipePackager1123eCompatTests.drain(helper, true, false);
	}

	/** The same, with the pump, Frank's tank and the hall saved and loaded mid-run. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1123p_pipe_drain_reloaded"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1123p_pipe_drain_reloaded")
	public void aStrangersPipeStillDrainsNothingAfterASaveAndLoad(GameTestHelper helper) {
		QaCreatePipePackager1123eCompatTests.drain(helper, true, true);
	}

	/** A stranger's pump outside pushes no lava into Frank's tank in his protected village's last column. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1123p_pipe_fill_protected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1123p_pipe_fill_protected")
	public void aStrangersPipeFillsNoLavaIntoATankInAProtectedVillage(GameTestHelper helper) {
		QaCreatePipePackager1123eCompatTests.fillInto(helper, true, "fluid_tank", Fluids.LAVA);
	}

	/** A stranger's pump outside pushes no water into Frank's basin in his protected village's last column. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1123p_pipe_basin_protected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1123p_pipe_basin_protected")
	public void aStrangersPipeFillsNoWaterIntoABasinInAProtectedVillage(GameTestHelper helper) {
		QaCreatePipePackager1123eCompatTests.fillInto(helper, true, "basin", Fluids.WATER);
	}

	/** A package pushed into a stranger's packager outside unpacks nothing into Frank's chest in his protected village. */
	//$ gametest_ticks_batch AREA '200' '"qa_e1123p_packager_unpack_protected"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_e1123p_packager_unpack_protected")
	public void aStrangersPackagerUnpacksNothingIntoAChestInAProtectedVillage(GameTestHelper helper) {
		QaCreatePipePackager1123eCompatTests.unpack(helper, true);
	}

	/** A Smart Fluid Pipe on the edge (B1546). */
	//$ gametest_ticks_batch AREA '400' '"qa_e1123p_smart_pipe_protected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1123p_smart_pipe_protected")
	public void aStrangersSmartPipeDrainsNothingFromATankInAProtectedVillage(GameTestHelper helper) {
		QaCreatePipePackager1123eCompatTests.drainVia(helper, true, "smart_fluid_pipe", 1);
	}

	/** A glass Fluid Pipe on the edge (B1546). */
	//$ gametest_ticks_batch AREA '400' '"qa_e1123p_glass_pipe_protected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1123p_glass_pipe_protected")
	public void aStrangersGlassPipeDrainsNothingFromATankInAProtectedVillage(GameTestHelper helper) {
		QaCreatePipePackager1123eCompatTests.drainVia(helper, true, "glass_fluid_pipe", 1);
	}

	/** An encased Fluid Pipe on the edge (B1546). */
	//$ gametest_ticks_batch AREA '400' '"qa_e1123p_encased_pipe_protected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1123p_encased_pipe_protected")
	public void aStrangersEncasedPipeDrainsNothingFromATankInAProtectedVillage(GameTestHelper helper) {
		QaCreatePipePackager1123eCompatTests.drainVia(helper, true, "encased_fluid_pipe", 1);
	}

	/** Frank's tank three blocks tall (one multiblock) in his last column (B1546). */
	//$ gametest_ticks_batch AREA '400' '"qa_e1123p_tall_tank_protected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1123p_tall_tank_protected")
	public void aStrangersPipeDrainsNothingFromATallTankInAProtectedVillage(GameTestHelper helper) {
		QaCreatePipePackager1123eCompatTests.drainVia(helper, true, "fluid_pipe", 3);
	}

	/** Protection switched on while a stranger's pump is draining Frank's edge tank: within 20 ticks no more water leaves it (B1546). */
	//$ gametest_ticks_batch AREA '400' '"qa_e1123p_pipe_protect_midway_protected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1123p_pipe_protect_midway_protected")
	public void protectionSwitchedOnMidDrainStopsAStrangersPipe(GameTestHelper helper) {
		QaCreatePipePackager1123eCompatTests.drainMidway(helper, true);
	}
}
