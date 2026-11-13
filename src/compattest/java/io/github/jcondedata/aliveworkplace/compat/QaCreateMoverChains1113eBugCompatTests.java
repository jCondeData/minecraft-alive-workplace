package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.MAX_STEPS;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.MIN;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.block;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.loose;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.set;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.setupAround;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.tidy;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.speedController.SpeedControllerBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.Instruction;
import com.simibubi.create.content.kinetics.transmission.sequencer.InstructionSpeedModifiers;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencerInstructions;
import io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.Setup;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import io.github.jcondedata.aliveworkplace.move.MoverWork;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * QA (lane E, 2026-11-13, run 2212), a bug test (fails on main): from 45.3 ("nothing dropped, nothing lost") and 45.5 (a
 * block comes back as it was, turned with the building) with Create 6. Two Chain Conveyors joined by a chain inside
 * the house (the player paid chains for it), the Mover working one swing a tick as he does in a world: after the
 * quarter-turned move neither conveyor is joined to the other and no chain lies anywhere: the chain is gone. Moved
 * with every swing in one tick (no conveyor tick between them) the chain survives, so a conveyor's own tick drops its
 * link while its partner is packed or not yet put back up (Create's removeInvalidConnections, run on a conveyor's
 * first tick and whenever it is told to check).
 */
public class QaCreateMoverChains1113eBugCompatTests implements FabricGameTest {
	static final String AREA = QaCreateMoverContents1110eCompatTests.AREA;
	static final BlockPos A = QaCreateMoverChains1113eCompatTests.A;
	static final BlockPos B = QaCreateMoverChains1113eCompatTests.B;

	/**
	 * Two Chain Conveyors joined by a chain, moved a quarter turn one swing a tick: at the new spots each is joined to the
	 * other (its link turned with the house), and no chain was dropped on the way down, the way up or after.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qa_e1113_mover_chains"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qa_e1113_mover_chains")
	public void twoChainConveyorsJoinedByAChainStayJoinedAfterAMove(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		set(level, helper.absolutePos(A), block("create:chain_conveyor").defaultBlockState());
		set(level, helper.absolutePos(B), block("create:chain_conveyor").defaultBlockState());
		helper.runAfterDelay(5, () -> {
			BlockPos a = helper.absolutePos(A);
			BlockPos b = helper.absolutePos(B);
			helper.assertTrue(level.getBlockEntity(a) instanceof ChainConveyorBlockEntity ca && level.getBlockEntity(b) instanceof ChainConveyorBlockEntity cb
				&& ca.addConnectionTo(b) && cb.addConnectionTo(a), "setup: the conveyors weren't joined: " + QaCreateMoverChains1113eCompatTests.links(level.getBlockEntity(a)));
			helper.runAfterDelay(5, () -> {
				helper.assertTrue(QaCreateMoverChains1113eCompatTests.links(level.getBlockEntity(a)).equals(Set.of(b.subtract(a)).toString())
					&& QaCreateMoverChains1113eCompatTests.links(level.getBlockEntity(b)).equals(Set.of(a.subtract(b)).toString()),
					"setup: the chain didn't hold: " + QaCreateMoverChains1113eCompatTests.links(level.getBlockEntity(a)) + " / " + QaCreateMoverChains1113eCompatTests.links(level.getBlockEntity(b)));
				Setup s = setupAround(helper, "chains");
				helper.runAfterDelay(2, () -> QaCreateMoverChains1113eCompatTests.paced(helper, s, x -> x.stage() == MoveSite.Stage.FOUNDATION, down -> {
					Map<String, Integer> afterDown = loose(helper);
					QaCreateMoverChains1113eCompatTests.paced(helper, s, x -> false, site -> {
						MoveSite.Stage end = site.stage();
						int skipped = site.skipped();
						BlockPos na = QaCreateMoverChains1113eCompatTests.movedTo(s, A);
						BlockPos nb = QaCreateMoverChains1113eCompatTests.movedTo(s, B);
						helper.runAfterDelay(20, () -> {
							String linksA = QaCreateMoverChains1113eCompatTests.links(level.getBlockEntity(na));
							String linksB = QaCreateMoverChains1113eCompatTests.links(level.getBlockEntity(nb));
							Map<String, Integer> looseNow = loose(helper);
							try {
								helper.assertTrue(end == MoveSite.Stage.DONE && skipped == 0, "the move ended " + end + ", skipped " + skipped);
								helper.assertTrue(afterDown.isEmpty(), "taking the house down dropped " + afterDown);
								helper.assertTrue(linksA.equals(Set.of(nb.subtract(na)).toString()) && linksB.equals(Set.of(na.subtract(nb)).toString()),
									"two Chain Conveyors joined by a chain; after the move the one at " + na + " links " + linksA + " and the one at " + nb
									+ " links " + linksB + " (wanted " + nb.subtract(na) + " and " + na.subtract(nb) + "), loose " + looseNow);
								helper.assertTrue(looseNow.isEmpty(), "loose after the move: " + looseNow);
							} finally {
								tidy(helper, s);
							}
							helper.succeed();
						});
					});
				}));
			});
		});
	}

}
