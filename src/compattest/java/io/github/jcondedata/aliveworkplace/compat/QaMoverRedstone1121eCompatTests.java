package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.MAX_STEPS;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.block;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.set;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.setupAround;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.tidy;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.with;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverTurns1110hBugCompatTests.speed;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverTurns1110hCompatTests.COG;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverTurns1110hCompatTests.MOTOR;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverTurns1110hCompatTests.moved;

import io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.Setup;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import io.github.jcondedata.aliveworkplace.move.MoverWork;
import java.util.EnumSet;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane E, 2026-11-21): B1160 (a moved Create machine keeps running) through a server restart. A Creative Motor
 * facing up turning a cogwheel on it, inside a house moved a quarter turned, the move's saved record written and read
 * back again and again while the Mover takes it down and puts it up: 200 ticks after the move the cogwheel turns as
 * fast as before at the new spot. Once with the whole move in one tick, once a step a tick (Create ticks the half-put
 * machine between the Mover's steps and the restarts, as on a server).
 */
public class QaMoverRedstone1121eCompatTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_compat:huge_area";

	private static MoveSite reload(ServerLevel level, Setup s) {
		CompoundTag saved = MoveSites.get(level).save(new CompoundTag(), level.registryAccess());
		level.getDataStorage().set(MoveSites.NAME, MoveSites.load(saved, level.registryAccess()));
		return MoveSites.get(level).forBuilding(s.from()).orElseThrow();
	}

	/** {@code perTick} steps a tick (0: all now), a reload every {@code every} steps; {@code then} with the site and the stages reloaded in. */
	private static void stepOver(GameTestHelper helper, Setup s, MoveSite site, int perTick, int every, int steps, Set<MoveSite.Stage> reloadedIn,
		java.util.function.BiConsumer<MoveSite, Set<MoveSite.Stage>> then) {
		ServerLevel level = helper.getLevel();
		int budget = perTick <= 0 ? MAX_STEPS : perTick;
		for (int i = 0; i < budget && steps < MAX_STEPS && site.stage() != MoveSite.Stage.DONE; i++) {
			MoverWork.work(level, s.mover(), site);
			steps++;
			if (steps % every == 0 && site.stage() != MoveSite.Stage.DONE) {
				reloadedIn.add(site.stage());
				site = reload(level, s);
			}
		}
		if (steps >= MAX_STEPS || site.stage() == MoveSite.Stage.DONE) {
			then.accept(site, reloadedIn);
			return;
		}
		MoveSite next = site;
		int done = steps;
		helper.runAfterDelay(1, () -> stepOver(helper, s, next, perTick, every, done, reloadedIn, then));
	}

	private static void cogMovedThroughReloads(GameTestHelper helper, String name, int perTick, int every) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		set(level, helper.absolutePos(MOTOR), with(block("create:creative_motor").defaultBlockState(), "facing", "up"));
		set(level, helper.absolutePos(COG), with(block("create:cogwheel").defaultBlockState(), "axis", "y"));
		helper.runAfterDelay(20, () -> {
			float before = speed(level, helper.absolutePos(COG));
			helper.assertTrue(before != 0, "setup: before the move the cogwheel doesn't turn");
			Setup s = setupAround(helper, name); // a quarter turn clockwise
			helper.runAfterDelay(2, () -> {
				MoveSite start = MoveSites.get(level).work(level, s.hall()).orElseThrow();
				stepOver(helper, s, start, perTick, every, 0, EnumSet.noneOf(MoveSite.Stage.class), (site, reloadedIn) -> {
					MoveSite.Stage end = site.stage();
					int skipped = site.skipped();
					BlockPos cog = moved(s, COG, Rotation.CLOCKWISE_90);
					BlockPos motor = moved(s, MOTOR, Rotation.CLOCKWISE_90);
					helper.runAfterDelay(200, () -> {
						float c = speed(level, cog);
						float m = speed(level, motor);
						String states = level.getBlockState(motor) + " at " + motor.toShortString() + ", " + level.getBlockState(cog) + " at " + cog.toShortString();
						tidy(helper, s);
						helper.assertTrue(end == MoveSite.Stage.DONE && skipped == 0, "the move ended " + end + ", skipped " + skipped);
						helper.assertTrue(reloadedIn.containsAll(java.util.List.of(MoveSite.Stage.DISMANTLE, MoveSite.Stage.STRUCTURE)),
							"reloaded only in " + reloadedIn);
						helper.assertTrue(Math.abs(c) == Math.abs(before) && Math.abs(m) == Math.abs(before), "200 ticks after a move reloaded every "
							+ every + " steps the cogwheel turns at " + c + " and the motor at " + m + " (before: " + before + "): " + states);
						helper.succeed();
					});
				});
			});
		});
	}

	/** The whole move in one tick, saved and reloaded every other step. */
	//$ gametest_ticks_batch AREA '900' '"qa_e1121_mover_cog_reload"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "qa_e1121_mover_cog_reload")
	public void aMovedCogwheelOnAMotorTurnsAfterRestartsMidMove(GameTestHelper helper) {
		cogMovedThroughReloads(helper, "cog_reload", 0, 2);
	}

	/** A step a tick (Create ticks the half-put machine), saved and reloaded every third step. */
	//$ gametest_ticks_batch AREA '2400' '"qa_e1121_mover_cog_reload_slow"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_e1121_mover_cog_reload_slow")
	public void aMovedCogwheelOnAMotorTurnsAfterRestartsMidMoveStepByStep(GameTestHelper helper) {
		cogMovedThroughReloads(helper, "cog_reload_slow", 1, 3);
	}
}
