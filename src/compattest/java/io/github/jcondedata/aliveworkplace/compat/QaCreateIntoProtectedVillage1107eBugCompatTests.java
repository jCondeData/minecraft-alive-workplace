package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateIntoProtectedVillage1107eCompatTests.*;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-e-1107-0413), the failing half of {@link QaCreateIntoProtectedVillage1107eCompatTests}: Create's Mechanical
 * Piston and Mechanical Drill outside a protected village push a block into it and break its edge, which a vanilla
 * piston (B745) and a stranger can't. The same setup's controls pass: the machines turn, and with the village open they
 * push and break.
 */
public class QaCreateIntoProtectedVillage1107eBugCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	/** B745's rule for Create's piston: one outside a protected village can't push a block into it. */
	//$ gametest_ticks_batch AREA '400' '"qa_cprot_piston_protected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_cprot_piston_protected")
	public void aMechanicalPistonOutsideAProtectedVillageCantPushIn(GameTestHelper helper) {
		VillageHallBlockEntity hall = QaCreateIntoProtectedVillage1107eCompatTests.hall(helper, true);
		helper.setBlock(OUTSIDE_PLANK, Blocks.OAK_PLANKS);
		piston(helper, OUTSIDE_PLANK.east());
		helper.runAfterDelay(300, () -> {
			done(helper, hall);
			int at = where(helper, OUTSIDE_PLANK, Blocks.OAK_PLANKS);
			helper.assertTrue(speed(helper, OUTSIDE_PLANK.east()) != 0, "the piston never turned, so the test proves nothing");
			helper.assertTrue(at > HALL.getX() + REACH,
				"a stranger's Mechanical Piston outside Frank's protected village pushed a plank into it (village-hall.md, B745: a piston"
					+ " outside stops at the village's edge): the plank is at x " + at + ", the village ends at x " + (HALL.getX() + REACH)
					+ ": " + row(helper, OUTSIDE_PLANK));
			helper.succeed();
		});
	}

	/** A drill outside a protected village can't break the village's edge, as a stranger standing there can't. */
	//$ gametest_ticks_batch AREA '400' '"qa_cprot_drill_protected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_cprot_drill_protected")
	public void aMechanicalDrillOutsideAProtectedVillageCantBreakIn(GameTestHelper helper) {
		VillageHallBlockEntity hall = QaCreateIntoProtectedVillage1107eCompatTests.hall(helper, true);
		helper.setBlock(EDGE, Blocks.STONE);
		drill(helper, EDGE.east());
		helper.runAfterDelay(300, () -> {
			done(helper, hall);
			helper.assertTrue(speed(helper, EDGE.east()) != 0, "the drill never turned, so the test proves nothing");
			helper.assertTrue(helper.getBlockState(EDGE).is(Blocks.STONE),
				"a Mechanical Drill outside Frank's protected village broke the stone in its last column (village-hall.md: only the"
					+ " owner, friends and operators may break blocks there; B745: machines outside stop at the edge): now "
					+ helper.getBlockState(EDGE));
			helper.succeed();
		});
	}
}
