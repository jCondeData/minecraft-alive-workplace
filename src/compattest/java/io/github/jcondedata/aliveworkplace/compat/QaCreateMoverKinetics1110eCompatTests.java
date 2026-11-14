package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.block;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.set;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.setupAround;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.tidy;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.with;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.work;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverMultiblock1110e2CompatTests.moved;

import io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.Setup;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * QA (lane E, 2026-11-10): the Mover (45.3) moving a building with a running Create machine: a Creative Motor turning
 * two shafts. 45.3 and moving-buildings.md: the building comes across block for block, so what turned before turns
 * after, and nothing keeps turning without its motor. Create saves each shaft's speed and its source (the motor) as a
 * world position, so a moved shaft must find its motor at the new spot.
 */
public class QaCreateMoverKinetics1110eCompatTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_compat:huge_area";
	static final BlockPos MOTOR = new BlockPos(4, 3, 5);
	static final BlockPos SHAFT1 = MOTOR.east();
	static final BlockPos SHAFT2 = MOTOR.east(2);

	static float speed(ServerLevel level, BlockPos abs) {
		BlockEntity be = level.getBlockEntity(abs);
		return be == null ? 0 : Nbt.getFloat(be.saveWithoutMetadata(level.registryAccess()), "Speed");
	}

	static void place(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		set(level, helper.absolutePos(MOTOR), with(block("create:creative_motor").defaultBlockState(), "facing", "east"));
		set(level, helper.absolutePos(SHAFT1), with(block("create:shaft").defaultBlockState(), "axis", "x"));
		set(level, helper.absolutePos(SHAFT2), with(block("create:shaft").defaultBlockState(), "axis", "x"));
	}

	/** A motor turning two shafts, moved a quarter turned: the shafts turn at the new spot, as fast as before. */
	//$ gametest_ticks_batch AREA '600' '"qa_e1110b_mover_kinetic"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qa_e1110b_mover_kinetic")
	public void aMovedMotorAndShaftsStillTurn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		place(helper);
		helper.runAfterDelay(20, () -> {
			float before = speed(level, helper.absolutePos(SHAFT2));
			helper.assertTrue(before != 0 && speed(level, helper.absolutePos(SHAFT1)) == before, "before the move the shafts turn at "
				+ speed(level, helper.absolutePos(SHAFT1)) + " / " + before);
			Setup s = setupAround(helper, "kinetic");
			helper.runAfterDelay(2, () -> {
				MoveSite site = work(level, s, x -> false);
				MoveSite.Stage end = site.stage();
				int skipped = site.skipped();
				BlockPos motor = moved(s, MOTOR);
				BlockPos a = moved(s, SHAFT1);
				BlockPos b = moved(s, SHAFT2);
				helper.runAfterDelay(200, () -> { // long enough for Create to check each shaft's source again
					float m = speed(level, motor);
					float sa = speed(level, a);
					float sb = speed(level, b);
					String states = level.getBlockState(motor) + ", " + level.getBlockState(a) + ", " + level.getBlockState(b);
					tidy(helper, s);
					helper.assertTrue(end == MoveSite.Stage.DONE && skipped == 0, "the move ended " + end + ", skipped " + skipped);
					helper.assertTrue(Math.abs(sa) == Math.abs(before) && Math.abs(sb) == Math.abs(before) && Math.abs(m) == Math.abs(before),
						"after the move the motor turns at " + m + " and the shafts at " + sa + " / " + sb + " (before: " + before + "): " + states);
					helper.succeed();
				});
			});
		});
	}

	/** The same moved, then its motor broken: the shafts stop (nothing turns on without a source at its old spot). */
	//$ gametest_ticks_batch AREA '600' '"qa_e1110b_mover_kinetic_stop"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qa_e1110b_mover_kinetic_stop")
	public void aMovedShaftStopsWhenItsMotorIsBroken(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		place(helper);
		helper.runAfterDelay(20, () -> {
			Setup s = setupAround(helper, "kinetic_stop");
			helper.runAfterDelay(2, () -> {
				MoveSite site = work(level, s, x -> false);
				MoveSite.Stage end = site.stage();
				BlockPos motor = moved(s, MOTOR);
				BlockPos a = moved(s, SHAFT1);
				BlockPos b = moved(s, SHAFT2);
				helper.runAfterDelay(20, () -> {
					level.destroyBlock(motor, false);
					helper.runAfterDelay(200, () -> {
						float sa = speed(level, a);
						float sb = speed(level, b);
						tidy(helper, s);
						helper.assertTrue(end == MoveSite.Stage.DONE, "the move ended " + end);
						helper.assertTrue(sa == 0 && sb == 0, "the motor broken after the move, the shafts still turn at " + sa + " / " + sb);
						helper.succeed();
					});
				});
			});
		});
	}
}
