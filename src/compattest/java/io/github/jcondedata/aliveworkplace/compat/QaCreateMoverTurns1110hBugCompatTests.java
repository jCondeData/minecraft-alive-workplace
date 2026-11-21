package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.block;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.set;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.setupAround;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.tidy;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.with;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverTurns1110hCompatTests.COG;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverTurns1110hCompatTests.MOTOR;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverTurns1110hCompatTests.moved;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverTurns1110hCompatTests.movedTurned;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverTurns1110hCompatTests.workOver;

import io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.Setup;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.PendingMoves;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * QA (lane E, 2026-11-10, eighth run): fails on main. B1137's spec says a Mechanical Arm moved by the Mover still takes
 * from and puts on the same depots. Its targets now turn right (QaCreateMoverTurns1110hCompatTests), but the arm doesn't
 * carry anything at the new spot: it, and the cogwheel that turns it, stand still (Speed 0) while the Creative Motor under
 * the cogwheel runs alone (its network one block big). The same rig put back where it was by a canceled move works, so
 * the move breaks the kinetic link from the motor up to the cogwheel. A motor turning shafts beside it on the same level
 * (QaCreateMoverKinetics1110eCompatTests) recovers; a motor turning a cogwheel above it does not.
 */
public class QaCreateMoverTurns1110hBugCompatTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_compat:huge_area";

	/** Moved a quarter turned (the bug's own turn): the arm carries a diamond from its take-depot to its put-depot. */
	//$ gametest_ticks_batch AREA '2400' '"qa_e1110h_bug_arm_carries_cw"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_e1110h_bug_arm_carries_cw")
	public void anArmMovedAQuarterTurnedStillCarriesBetweenItsDepots(GameTestHelper helper) {
		movedTurned(helper, "bug_arm_cw", Rotation.CLOCKWISE_90, true, 0);
	}

	/** Moved without a turn: the arm still carries (so the stop isn't the turn). */
	//$ gametest_ticks_batch AREA '2400' '"qa_e1110h_bug_arm_carries_none"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_e1110h_bug_arm_carries_none")
	public void anArmMovedWithoutATurnStillCarriesBetweenItsDepots(GameTestHelper helper) {
		movedTurned(helper, "bug_arm_none", Rotation.NONE, true, 0);
	}

	/** Moved a quarter turned with the Mover working a step a tick, as in a game: the arm still carries. */
	//$ gametest_ticks_batch AREA '6000' '"qa_e1110h_bug_arm_carries_slow"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qa_e1110h_bug_arm_carries_slow")
	public void anArmMovedStepByStepStillCarriesBetweenItsDepots(GameTestHelper helper) {
		movedTurned(helper, "bug_arm_slow", Rotation.CLOCKWISE_90, true, 1);
	}

	static float speed(ServerLevel level, BlockPos abs) {
		BlockEntity be = level.getBlockEntity(abs);
		return be == null ? 0 : Nbt.getFloat(be.saveWithoutMetadata(level.registryAccess()), "Speed");
	}

	/** No arm, no depots: a Creative Motor facing up turning a cogwheel on it, moved without a turn: the cogwheel turns. */
	//$ gametest_ticks_batch AREA '600' '"qa_e1110h_bug_motor_cog"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qa_e1110h_bug_motor_cog")
	public void aMovedCogwheelOnAMotorStillTurns(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		set(level, helper.absolutePos(MOTOR), with(block("create:creative_motor").defaultBlockState(), "facing", "up"));
		set(level, helper.absolutePos(COG), with(block("create:cogwheel").defaultBlockState(), "axis", "y"));
		helper.runAfterDelay(20, () -> {
			float before = speed(level, helper.absolutePos(COG));
			helper.assertTrue(before != 0, "before the move the cogwheel doesn't turn");
			Setup s = setupAround(helper, "bug_motor_cog");
			PendingMoves.get(level).put(new PendingMoves.Move(s.hall(), s.from(), s.id(), s.to(), Rotation.NONE, null));
			helper.runAfterDelay(2, () -> workOver(helper, s, 0, site -> {
				MoveSite.Stage end = site.stage();
				BlockPos cog = moved(s, COG, Rotation.NONE);
				BlockPos motor = moved(s, MOTOR, Rotation.NONE);
				helper.runAfterDelay(200, () -> {
					float c = speed(level, cog);
					float m = speed(level, motor);
					String data = level.getBlockEntity(motor) == null ? "-" : level.getBlockEntity(motor).saveWithoutMetadata(level.registryAccess()).toString();
					tidy(helper, s);
					helper.assertTrue(end == MoveSite.Stage.DONE, "the move ended " + end);
					helper.assertTrue(Math.abs(c) == Math.abs(before), "after the move the cogwheel turns at " + c + " (before: " + before
						+ "), the motor under it at " + m + ": " + data);
					helper.succeed();
				});
			}));
		});
	}
}
