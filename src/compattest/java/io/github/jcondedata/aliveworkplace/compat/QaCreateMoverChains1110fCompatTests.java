package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.MIN;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.block;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.loose;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.set;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.setupAround;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.tidy;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.work;

import io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.Setup;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * QA (lane E, 2026-11-10, run 1012), from 45.3 ("a building comes across block for block"; nothing dropped, nothing
 * lost) with Create 6: two Chain Conveyors inside one building, chained to each other. Create keeps each conveyor's
 * connections as offsets to the other conveyor and, when one comes down, drops the chain and unhooks the other. Moved
 * (a quarter turn), both conveyors stand at the new spot still chained to each other, and no chain lies loose. Create's
 * classes are reached by reflection (the compat tests compile without them).
 */
public class QaCreateMoverChains1110fCompatTests implements FabricGameTest {
	static final String AREA = QaCreateMoverContents1110eCompatTests.AREA;
	static final BlockPos A = new BlockPos(3, 3, 4);
	static final BlockPos B = new BlockPos(7, 3, 4);

	/** Chains the conveyor at {@code from} to the one at {@code to} (Create keeps it as the offset), as its connection packet does. */
	static boolean chain(ServerLevel level, BlockPos from, BlockPos to) {
		BlockEntity be = level.getBlockEntity(from);
		try {
			return be != null && (boolean) be.getClass().getMethod("addConnectionTo", BlockPos.class).invoke(be, to);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Create's ChainConveyorBlockEntity#addConnectionTo: " + e, e);
		}
	}

	/** The offsets the conveyor at {@code at} is chained to (empty if there's no conveyor). */
	@SuppressWarnings("unchecked")
	static Set<BlockPos> connections(ServerLevel level, BlockPos at) {
		BlockEntity be = level.getBlockEntity(at);
		if (be == null || !level.getBlockState(at).is(block("create:chain_conveyor"))) {
			return Set.of();
		}
		try {
			return Set.copyOf((Set<BlockPos>) be.getClass().getField("connections").get(be));
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Create's ChainConveyorBlockEntity#connections: " + e, e);
		}
	}

	/** Two chained conveyors moved a quarter turn: still chained to each other at the new spot, nothing dropped. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1110f_mover_chains"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1110f_mover_chains")
	public void twoChainedConveyorsComeAcrossStillChained(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		BlockPos a = helper.absolutePos(A);
		BlockPos b = helper.absolutePos(B);
		set(level, a, block("create:chain_conveyor").defaultBlockState());
		set(level, b, block("create:chain_conveyor").defaultBlockState());
		helper.runAfterDelay(3, () -> {
			boolean ab = chain(level, a, b);
			boolean ba = chain(level, b, a);
			Setup s = setupAround(helper, "chains");
			helper.runAfterDelay(5, () -> {
				helper.assertTrue(ab && ba && connections(level, a).equals(Set.of(b.subtract(a))) && connections(level, b).equals(Set.of(a.subtract(b))),
					"setup: the conveyors aren't chained: " + connections(level, a) + " / " + connections(level, b));
				MoveSite site = work(level, s, x -> false);
				MoveSite.Stage end = site.stage();
				int skipped = site.skipped();
				BlockPos a2 = s.before().at(A.subtract(MIN), s.to(), Rotation.CLOCKWISE_90);
				BlockPos b2 = s.before().at(B.subtract(MIN), s.to(), Rotation.CLOCKWISE_90);
				helper.runAfterDelay(10, () -> { // Create drops connections it finds invalid on its ticks
					Set<BlockPos> fromA = connections(level, a2);
					Set<BlockPos> fromB = connections(level, b2);
					boolean standing = level.getBlockState(a2).is(block("create:chain_conveyor")) && level.getBlockState(b2).is(block("create:chain_conveyor"));
					Map<String, Integer> looseNow = loose(helper);
					try {
						helper.assertTrue(end == MoveSite.Stage.DONE && skipped == 0, "the move ended " + end + ", skipped " + skipped);
						helper.assertTrue(standing && fromA.equals(Set.of(b2.subtract(a2))) && fromB.equals(Set.of(a2.subtract(b2))),
							"after the move (a quarter turn) the conveyors stand " + standing + "; the first is chained to " + fromA + " (should be "
							+ b2.subtract(a2).toShortString() + "), the second to " + fromB + " (should be " + a2.subtract(b2).toShortString()
							+ "); before the move they were chained " + b.subtract(a).toShortString() + " and " + a.subtract(b).toShortString()
							+ "; loose " + looseNow);
						helper.assertTrue(looseNow.isEmpty(), "loose after the move: " + looseNow);
					} finally {
						tidy(helper, s);
					}
					helper.succeed();
				});
			});
		});
	}
}
