package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.block;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.set;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.setupAround;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.tidy;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.with;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.work;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverTurns1110hBugCompatTests.speed;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverTurns1110hCompatTests.COG;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverTurns1110hCompatTests.MOTOR;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverTurns1110hCompatTests.moved;

import io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.Setup;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import io.github.jcondedata.aliveworkplace.move.PendingMoves;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane C, 2026-11-22), from the spec of B1160 (a moved Create machine is put down at rest, without its old speed,
 * source or network, so Create joins it up again) and B1216 (once the whole house stands, every block hears of its
 * neighbors once): a cogwheel on a Creative Motor moved a half turn, a motor driving a shaft into a Mechanical Press
 * along the floor (a line whose axis the quarter turn swaps), and a house called off once its motor and cogwheel stand at
 * the new spot (carried back, the cogwheel turns again at the old spot). Each checked 200 ticks after the move.
 */
public class QaMoverSettle1122cCompatTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_compat:huge_area";
	/** The line along the floor, inside the house (x 2-8, z 2-8): a motor facing east, a shaft, a press facing east. */
	static final BlockPos LINE_MOTOR = new BlockPos(3, 3, 3);
	static final BlockPos LINE_SHAFT = new BlockPos(4, 3, 3);
	static final BlockPos LINE_PRESS = new BlockPos(5, 3, 3);

	private static void cogOnMotor(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		set(level, helper.absolutePos(MOTOR), with(block("create:creative_motor").defaultBlockState(), "facing", "up"));
		set(level, helper.absolutePos(COG), with(block("create:cogwheel").defaultBlockState(), "axis", "y"));
	}

	/** The speeds (absolute) of the blocks at {@code where} 200 ticks after the move turned {@code rotation}, checked against {@code before}. */
	private static void movedThenTurning(GameTestHelper helper, String name, Rotation rotation, List<BlockPos> parts) {
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(20, () -> {
			float[] before = new float[parts.size()];
			for (int i = 0; i < parts.size(); i++) {
				before[i] = speed(level, helper.absolutePos(parts.get(i)));
				helper.assertTrue(before[i] != 0, "setup: before the move " + level.getBlockState(helper.absolutePos(parts.get(i))) + " doesn't turn");
			}
			Setup s = setupAround(helper, name);
			PendingMoves.get(level).put(new PendingMoves.Move(s.hall(), s.from(), s.id(), s.to(), rotation, null));
			helper.runAfterDelay(2, () -> {
				MoveSite site = work(level, s, x -> false);
				MoveSite.Stage end = site.stage();
				int skipped = site.skipped();
				Rotation used = site.rotation();
				helper.runAfterDelay(200, () -> {
					StringBuilder wrong = new StringBuilder();
					for (int i = 0; i < parts.size(); i++) {
						BlockPos at = moved(s, parts.get(i), rotation);
						float now = speed(level, at);
						if (Math.abs(now) != Math.abs(before[i])) {
							wrong.append(level.getBlockState(at)).append(" at ").append(at.toShortString()).append(" turns at ").append(now)
								.append(" (before: ").append(before[i]).append("); ");
						}
					}
					tidy(helper, s);
					helper.assertTrue(used == rotation, "the move went " + used + ", not " + rotation);
					helper.assertTrue(end == MoveSite.Stage.DONE && skipped == 0, "the move ended " + end + ", skipped " + skipped);
					helper.assertTrue(wrong.length() == 0, "200 ticks after a move turned " + rotation + ": " + wrong);
					helper.succeed();
				});
			});
		});
	}

	/** 1. A cogwheel on a Creative Motor, moved a half turn: both turn at the new spot as fast as before. */
	//$ gametest_ticks_batch AREA '600' '"qa_c1122_settle_cog_half"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qa_c1122_settle_cog_half")
	public void aCogwheelOnAMotorMovedAHalfTurnStillTurns(GameTestHelper helper) {
		cogOnMotor(helper);
		movedThenTurning(helper, "settle_cog_half", Rotation.CLOCKWISE_180, List.of(MOTOR, COG));
	}

	/** 2. A motor driving a shaft into a Mechanical Press along the floor, moved a quarter turned (its axis now z): all three turn. */
	//$ gametest_ticks_batch AREA '600' '"qa_c1122_settle_press_line"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qa_c1122_settle_press_line")
	public void aMotorShaftAndPressLineMovedAQuarterTurnedStillTurns(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		set(level, helper.absolutePos(LINE_MOTOR), with(block("create:creative_motor").defaultBlockState(), "facing", "east"));
		set(level, helper.absolutePos(LINE_SHAFT), with(block("create:shaft").defaultBlockState(), "axis", "x"));
		set(level, helper.absolutePos(LINE_PRESS), with(block("create:mechanical_press").defaultBlockState(), "facing", "east"));
		movedThenTurning(helper, "settle_press_line", Rotation.CLOCKWISE_90, List.of(LINE_MOTOR, LINE_SHAFT, LINE_PRESS));
	}

	/** 3. Called off once the motor and cogwheel stand at the new spot: carried back, the cogwheel turns at the old spot again. */
	//$ gametest_ticks_batch AREA '600' '"qa_c1122_settle_cog_cancel"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qa_c1122_settle_cog_cancel")
	public void aCogwheelOnAMotorCarriedBackAfterACallOffTurnsAgain(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		cogOnMotor(helper);
		helper.runAfterDelay(20, () -> {
			float before = speed(level, helper.absolutePos(COG));
			helper.assertTrue(before != 0, "setup: before the move the cogwheel doesn't turn");
			Setup s = setupAround(helper, "settle_cog_cancel"); // a quarter turn clockwise
			helper.runAfterDelay(2, () -> {
				BlockPos cogNew = moved(s, COG, Rotation.CLOCKWISE_90);
				BlockPos motorNew = moved(s, MOTOR, Rotation.CLOCKWISE_90);
				MoveSite site = work(level, s, x -> level.getBlockState(cogNew).is(block("create:cogwheel"))
					&& level.getBlockState(motorNew).is(block("create:creative_motor")));
				boolean up = level.getBlockState(cogNew).is(block("create:cogwheel")) && site.stage() != MoveSite.Stage.DONE;
				helper.runAfterDelay(5, () -> { // Create ticks the half-put machine at the new spot
					MoveSites.get(level).cancel(level, s.from());
					PendingMoves.get(level).remove(s.from()); // the Move page's Call off
					MoveSite after = work(level, s, x -> false);
					MoveSite.Stage end = after.stage();
					boolean canceled = after.canceled();
					helper.runAfterDelay(200, () -> {
						float c = speed(level, helper.absolutePos(COG));
						float m = speed(level, helper.absolutePos(MOTOR));
						String states = level.getBlockState(helper.absolutePos(MOTOR)) + ", " + level.getBlockState(helper.absolutePos(COG));
						tidy(helper, s);
						helper.assertTrue(up, "setup: the cogwheel never stood at the new spot before the move ended");
						helper.assertTrue(end == MoveSite.Stage.DONE && canceled, "the move ended " + end + (canceled ? " canceled" : ""));
						helper.assertTrue(Math.abs(c) == Math.abs(before) && Math.abs(m) == Math.abs(before), "200 ticks after the call-off the cogwheel "
							+ "turns at " + c + " and the motor at " + m + " at the old spot (before: " + before + "): " + states);
						helper.succeed();
					});
				});
			});
		});
	}
}
