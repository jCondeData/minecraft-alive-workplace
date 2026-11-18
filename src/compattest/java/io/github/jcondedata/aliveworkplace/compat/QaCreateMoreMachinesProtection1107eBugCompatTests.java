package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateIntoProtectedVillage1107eCompatTests.EDGE;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateIntoProtectedVillage1107eCompatTests.done;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateIntoProtectedVillage1107eCompatTests.hall;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateIntoProtectedVillage1107eCompatTests.speed;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoreMachinesProtection1107eCompatTests.*;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-e-1107-1313), the failing half of {@link QaCreateMoreMachinesProtection1107eCompatTests}: a stranger's
 * Mechanical Saw, Deployer and Mechanical Arm outside a protected village each reach into its last column (they cut a
 * log, place a block, take items off a depot). Their controls (the same machines with the village open) pass. Not
 * registered until fixed: the saw is B993's drill path (both break blocks with no player), the arm is B994's kind (item
 * transport out of a village container), and the deployer is B844 (a block put into air is never checked, so a
 * stranger's deployer places one inside the village too); when B844 is fixed, this one checks that the block is judged
 * where it goes (the village), not where the deployer stands (outside).
 */
public class QaCreateMoreMachinesProtection1107eBugCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	/** With protection on, the same saw outside leaves the log in the village's last column standing. */
	//$ gametest_ticks_batch AREA '400' '"qa_cmore_saw_protected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_cmore_saw_protected")
	public void aMechanicalSawOutsideAProtectedVillageLeavesTheLogAtItsEdge(GameTestHelper helper) {
		VillageHallBlockEntity hall = hall(helper, true);
		helper.setBlock(EDGE, Blocks.OAK_LOG);
		saw(helper, EDGE.east());
		helper.runAfterDelay(300, () -> {
			done(helper, hall);
			helper.assertTrue(speed(helper, EDGE.east()) != 0, "the saw never turned");
			helper.assertTrue(helper.getBlockState(EDGE).is(Blocks.OAK_LOG), "a stranger's Mechanical Saw outside Frank's protected village cut the log"
				+ " in its last column (village-hall.md: only the owner, friends and operators may break blocks there; B745: machines outside"
				+ " stop at the edge); it is now " + helper.getBlockState(EDGE));
			helper.succeed();
		});
	}

	/** With protection on, a stranger's Deployer outside places nothing in the village's last column. */
	//$ gametest_ticks_batch AREA '300' '"qa_cmore_deployer_protected"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa_cmore_deployer_protected")
	public void aDeployerOutsideAProtectedVillagePlacesNothingAtItsEdge(GameTestHelper helper) {
		VillageHallBlockEntity hall = hall(helper, true);
		deployer(helper);
		helper.runAfterDelay(200, () -> {
			done(helper, hall);
			helper.assertTrue(speed(helper, DEPLOYER) != 0, "the deployer never turned");
			helper.assertTrue(helper.getBlockState(EDGE).isAir(), "a stranger's Deployer outside Frank's protected village placed a block in its"
				+ " last column (village-hall.md: only the owner, friends and operators may place blocks there; B745: machines outside stop at"
				+ " the edge): " + helper.getBlockState(EDGE));
			helper.succeed();
		});
	}

	/** With protection on, a stranger's Mechanical Arm outside takes nothing from the depot in the village's last column. */
	//$ gametest_ticks_batch AREA '500' '"qa_cmore_arm_protected"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_cmore_arm_protected")
	public void aMechanicalArmOutsideAProtectedVillageTakesNothingFromTheDepotAtItsEdge(GameTestHelper helper) {
		VillageHallBlockEntity hall = hall(helper, true);
		depotsAndArm(helper);
		helper.runAfterDelay(400, () -> {
			done(helper, hall);
			helper.assertTrue(speed(helper, ARM) != 0, "the arm never turned");
			helper.assertTrue(diamonds(helper, EDGE) == 16, "a stranger's Mechanical Arm outside Frank's protected village took "
				+ (16 - diamonds(helper, EDGE)) + " of 16 diamonds off the depot in its last column (village-hall.md: only the owner,"
				+ " friends and operators may open chests (containers) there; B745: machines outside stop at the edge); the depot outside holds "
				+ diamonds(helper, OUT_DEPOT));
			helper.succeed();
		});
	}
}
