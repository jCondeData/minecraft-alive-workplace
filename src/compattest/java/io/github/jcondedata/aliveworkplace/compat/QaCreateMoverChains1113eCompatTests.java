package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.MAX_STEPS;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.MIN;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.block;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.loose;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.set;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.setupAround;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.tidy;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.with;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.speedController.SpeedControllerBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.Instruction;
import com.simibubi.create.content.kinetics.transmission.sequencer.InstructionSpeedModifiers;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencerInstructions;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
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
 * QA (lane E, 2026-11-13, run 2212), from 45.3 ("nothing dropped, nothing lost") and 45.5 (a block comes back as it
 * was, turned with the building) with Create 6: Create blocks whose settings live in their block entity and that no
 * earlier Mover class put in a house.
 * A Sequenced Gearshift with its instruction list set and a Rotation Speed Controller set to 64 RPM: both keep their
 * settings through a quarter-turned move. The Mover works one swing a tick here ({@link #paced}), as he does in a
 * world (one swing every few ticks), so the blocks' own ticks run between swings. A Display Link aimed at a Nixie Tube still aims at it.
 * Two Chain Conveyors joined by a chain, an eight-block Item Vault and an eight-block Fluid Tank are in
 * QaCreateMoverChains1113eBugCompatTests (on tests/create-mover-chains-1113e: the chain is lost, the vault and the tank
 * come back split, the tank short of water).
 */
public class QaCreateMoverChains1113eCompatTests implements FabricGameTest {
	static final String AREA = QaCreateMoverContents1110eCompatTests.AREA;
	static final BlockPos A = new BlockPos(3, 3, 4);
	static final BlockPos B = new BlockPos(7, 3, 6);

	/** Lets the Mover work one swing a tick until {@code stop} or the end, then {@code then} with the site. */
	static void paced(GameTestHelper helper, Setup s, Predicate<MoveSite> stop, Consumer<MoveSite> then) {
		ServerLevel level = helper.getLevel();
		MoveSite site = MoveSites.get(level).work(level, s.hall()).orElseThrow();
		swing(helper, s, site, stop, then, 0);
	}

	private static void swing(GameTestHelper helper, Setup s, MoveSite site, Predicate<MoveSite> stop, Consumer<MoveSite> then, int n) {
		if (site.stage() == MoveSite.Stage.DONE || stop.test(site) || n >= MAX_STEPS) {
			then.accept(site);
			return;
		}
		MoverWork.work(helper.getLevel(), s.mover(), site);
		helper.runAfterDelay(1, () -> swing(helper, s, site, stop, then, n + 1));
	}

	static BlockPos movedTo(Setup s, BlockPos rel) {
		return s.before().at(rel.subtract(MIN), s.to(), Rotation.CLOCKWISE_90);
	}

	static String links(BlockEntity be) {
		return be instanceof ChainConveyorBlockEntity c ? c.connections.toString() : "no chain conveyor (" + be + ")";
	}

	/**
	 * A Sequenced Gearshift with three instructions (turn 90 degrees fast backwards, wait 40 ticks, turn 2 blocks) and a
	 * Rotation Speed Controller set to 64 RPM: at the new spots both keep their settings, nothing loose.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qa_e1113_mover_settings"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qa_e1113_mover_settings")
	public void aSequencedGearshiftAndASpeedControllerKeepTheirSettingsAfterAMove(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		set(level, helper.absolutePos(A), block("create:sequenced_gearshift").defaultBlockState());
		set(level, helper.absolutePos(B), block("create:rotation_speed_controller").defaultBlockState());
		helper.runAfterDelay(5, () -> {
			BlockPos a = helper.absolutePos(A);
			BlockPos b = helper.absolutePos(B);
			helper.assertTrue(level.getBlockEntity(a) instanceof SequencedGearshiftBlockEntity, "setup: no gearshift: " + level.getBlockState(a));
			helper.assertTrue(level.getBlockEntity(b) instanceof SpeedControllerBlockEntity, "setup: no speed controller: " + level.getBlockState(b));
			SequencedGearshiftBlockEntity gearshift = (SequencedGearshiftBlockEntity) level.getBlockEntity(a);
			gearshift.getInstructions().clear();
			gearshift.getInstructions().add(new Instruction(SequencerInstructions.TURN_ANGLE, InstructionSpeedModifiers.BACK_FAST, 90));
			gearshift.getInstructions().add(new Instruction(SequencerInstructions.DELAY, 40));
			gearshift.getInstructions().add(new Instruction(SequencerInstructions.TURN_DISTANCE, 2));
			gearshift.getInstructions().add(new Instruction(SequencerInstructions.END));
			gearshift.setChanged();
			((SpeedControllerBlockEntity) level.getBlockEntity(b)).targetSpeed.setValue(64);
			Tag wanted = instructions(level, a);
			helper.assertTrue(wanted != null && wanted.toString().contains("90") && wanted.toString().contains("40"), "setup: the instructions read " + wanted);
			Setup s = setupAround(helper, "settings");
			helper.runAfterDelay(2, () -> paced(helper, s, x -> false, site -> {
				MoveSite.Stage end = site.stage();
				int skipped = site.skipped();
				BlockPos na = movedTo(s, A);
				BlockPos nb = movedTo(s, B);
				helper.runAfterDelay(10, () -> {
					Tag now = instructions(level, na);
					String speed = level.getBlockEntity(nb) instanceof SpeedControllerBlockEntity c ? String.valueOf(c.targetSpeed.getValue())
						: "no speed controller (" + level.getBlockState(nb) + ")";
					Map<String, Integer> looseNow = loose(helper);
					try {
						helper.assertTrue(end == MoveSite.Stage.DONE && skipped == 0, "the move ended " + end + ", skipped " + skipped);
						helper.assertTrue(wanted.equals(now), "the Sequenced Gearshift's instructions were " + wanted + "; after the move at " + na + ": " + now);
						helper.assertTrue(speed.equals("64"), "the Rotation Speed Controller was set to 64 RPM; after the move at " + nb + ": " + speed);
						helper.assertTrue(looseNow.isEmpty(), "loose after the move: " + looseNow);
					} finally {
						tidy(helper, s);
					}
					helper.succeed();
				});
			}));
		});
	}

	/**
	 * A Display Link on the floor linked to a Nixie Tube across the room (lane E's 11-10 test moved it all in one tick),
	 * moved a quarter turn one swing a tick: the link is put up before its tube and ticks alone in between, and at the new
	 * spot it still targets its tube.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qa_e1113_mover_display_link_paced"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qa_e1113_mover_display_link_paced")
	public void aDisplayLinkMovedSwingBySwingStillTargetsItsTube(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		BlockPos link = A;
		BlockPos tube = B;
		set(level, helper.absolutePos(link), with(block("create:display_link").defaultBlockState(), "facing", "up"));
		set(level, helper.absolutePos(tube), block("create:nixie_tube").defaultBlockState());
		helper.runAfterDelay(2, () -> {
			BlockEntity be = level.getBlockEntity(helper.absolutePos(link));
			helper.assertTrue(be instanceof DisplayLinkBlockEntity, "setup: the link's block entity is " + be);
			((DisplayLinkBlockEntity) be).target(helper.absolutePos(tube));
			be.setChanged();
			helper.assertTrue(helper.absolutePos(tube).equals(((DisplayLinkBlockEntity) be).getTargetPosition()), "setup: the link doesn't target the tube");
			Setup s = setupAround(helper, "display_link_paced");
			helper.runAfterDelay(2, () -> paced(helper, s, x -> false, site -> {
				MoveSite.Stage end = site.stage();
				int skipped = site.skipped();
				BlockPos linkNow = movedTo(s, link);
				BlockPos tubeNow = movedTo(s, tube);
				helper.runAfterDelay(20, () -> {
					BlockEntity after = level.getBlockEntity(linkNow);
					BlockPos target = after instanceof DisplayLinkBlockEntity d ? d.getTargetPosition() : null;
					Map<String, Integer> looseNow = loose(helper);
					try {
						helper.assertTrue(end == MoveSite.Stage.DONE && skipped == 0, "the move ended " + end + ", skipped " + skipped);
						helper.assertTrue(tubeNow.equals(target), "moved swing by swing, the display link at " + linkNow.toShortString() + " ("
							+ level.getBlockState(linkNow) + ") targets " + (target == null ? null : target.toShortString()) + ", not its tube at "
							+ tubeNow.toShortString() + " (" + level.getBlockState(tubeNow) + ")");
						helper.assertTrue(looseNow.isEmpty(), "loose after the move: " + looseNow);
					} finally {
						tidy(helper, s);
					}
					helper.succeed();
				});
			}));
		});
	}

	/** The gearshift's saved instruction list at {@code abs}, or null. */
	static Tag instructions(ServerLevel level, BlockPos abs) {
		BlockEntity be = level.getBlockEntity(abs);
		return be instanceof SequencedGearshiftBlockEntity ? be.saveWithoutMetadata(level.registryAccess()).get("Instructions") : null;
	}
}
