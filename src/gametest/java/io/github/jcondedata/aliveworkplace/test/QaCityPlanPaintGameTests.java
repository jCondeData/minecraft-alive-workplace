package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaCityPlanGameTests.cells;
import static io.github.jcondedata.aliveworkplace.test.QaCityPlanGameTests.hall;
import static io.github.jcondedata.aliveworkplace.test.QaCityPlanGameTests.otherHalls;
import static io.github.jcondedata.aliveworkplace.test.QaCityPlanGameTests.reload;
import static io.github.jcondedata.aliveworkplace.test.QaCityPlanGameTests.sent;

import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import java.util.BitSet;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * QA (adversarial, from the spec of ROADMAP 27.2 and 27.3, not the code): painting the plan through the packets the
 * screen sends. A dragged rectangle is one undo step; a deleted zone comes back with its cells on undo; strokes that
 * change nothing don't eat an undo step; a stranger's side-panel edits in a protected village change nothing; the first
 * painter's claim on a hall nobody owned is saved with the hall; the world's zoneAt goes by the nearest hall's plan; and
 * at a radius of 128 a painted cell covers exactly its 8×8 blocks.
 *
 * <p>Every test has its own batch and clears halls other batches left nearby (a cell is refused when another hall is
 * nearer).
 */
public class QaCityPlanPaintGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	private static int cell(BlockPos hall, int dx, int dz) {
		return CityPlan.cellAt(hall, hall.offset(dx, 0, dz));
	}

	private static BitSet cellsOf(VillageHallBlockEntity entity, int zone) {
		return entity.plan().zones().get(zone).cells();
	}

	/** A brush rectangle dragged over 3×3 cells goes on in one packet, is one undo step, and one undo takes all of it back. */
	//$ gametest_ticks_batch AREA '20' '"qaCityPaintRect"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaCityPaintRect")
	public void aDraggedRectangleIsOneStrokeAndOneUndo(GameTestHelper helper) {
		Leftovers.clear(helper);
		otherHalls(helper, HALL);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, HALL);
		BlockPos hall = entity.getBlockPos();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.addZone(hall, "homes", "Homes", ""))), "no zone added");
		BitSet rect = new BitSet();
		int corner = cell(hall, -6, -6);
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 3; col++) {
				rect.set(corner + col + row * CityPlan.GRID);
			}
		}
		int steps = entity.planUndoSteps();
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.paint(hall, 0, rect))), "the rectangle was refused");
		helper.assertTrue(cellsOf(entity, 0).equals(rect) && rect.cardinality() == 9, "the rectangle painted " + cellsOf(entity, 0) + ", not " + rect);
		helper.assertTrue(entity.planUndoSteps() == steps + 1, "a rectangle took " + (entity.planUndoSteps() - steps) + " undo steps");
		for (int c = rect.nextSetBit(0); c >= 0; c = rect.nextSetBit(c + 1)) {
			Optional<CityPlan.Zone> at = CityPlan.zoneAt(level, CityPlan.cellCentre(hall, c));
			helper.assertTrue(at.isPresent() && at.get().name().equals("Homes"), "cell " + c + " of the rectangle isn't Homes in the world: " + at);
		}
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.undo(hall))), "undo was refused");
		helper.assertTrue(cellsOf(entity, 0).isEmpty(), "one undo left part of the rectangle: " + cellsOf(entity, 0));
		helper.assertTrue(CityPlan.zoneAt(level, CityPlan.cellCentre(hall, corner)).isEmpty(), "the world still has the undone zone");
		helper.succeed();
	}

	/** Deleting a zone takes its cells off the map; its index is gone for the brush; undo brings it back, cells, place and all. */
	//$ gametest_ticks_batch AREA '20' '"qaCityPaintDelete"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaCityPaintDelete")
	public void aDeletedZoneComesBackWithItsCellsOnUndo(GameTestHelper helper) {
		Leftovers.clear(helper);
		otherHalls(helper, HALL);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, HALL);
		BlockPos hall = entity.getBlockPos();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		int a = cell(hall, -4, 0), b = cell(hall, 4, 0);
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.addZone(hall, "homes", "Homes", ""))), "no Homes");
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.addZone(hall, "gardens", "Park", ""))), "no Park");
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.paint(hall, 0, cells(a)))), "Homes not painted");
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.paint(hall, 1, cells(b)))), "Park not painted");
		CityPlan full = entity.plan();

		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.removeZone(hall, 0))), "deleting Homes was refused");
		helper.assertTrue(entity.plan().zones().size() == 1 && entity.plan().zones().get(0).name().equals("Park") && cellsOf(entity, 0).equals(cells(b)),
			"after deleting Homes: " + entity.plan().zones());
		helper.assertTrue(CityPlan.zoneAt(level, CityPlan.cellCentre(hall, a)).isEmpty(), "a deleted zone's cell is still a zone in the world");
		CityPlan oneZone = entity.plan();
		helper.assertFalse(CityPlans.apply(player, sent(level, CityPlans.Edit.paint(hall, 1, cells(a)))), "the brush painted into a zone that no longer exists");
		helper.assertTrue(entity.plan().equals(oneZone), "painting a gone zone changed the plan: " + entity.plan());

		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.undo(hall))), "undoing the delete was refused");
		helper.assertTrue(entity.plan().equals(full), "undo didn't bring the zone back as it was: " + entity.plan().zones());
		Optional<CityPlan.Zone> back = CityPlan.zoneAt(level, CityPlan.cellCentre(hall, a));
		helper.assertTrue(back.isPresent() && back.get().name().equals("Homes"), "the restored zone isn't in the world: " + back);
		helper.succeed();
	}

	/** Strokes that change nothing (paint over the same cell, erase bare land, an unchanged side panel, the same mode, a bad index) leave the last real change for undo. */
	//$ gametest_ticks_batch AREA '20' '"qaCityPaintNoop"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaCityPaintNoop")
	public void strokesThatChangeNothingDontEatAnUndoStep(GameTestHelper helper) {
		Leftovers.clear(helper);
		otherHalls(helper, HALL);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, HALL);
		BlockPos hall = entity.getBlockPos();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		int a = cell(hall, 0, 4), bare = cell(hall, 4, 4);
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.addZone(hall, "farms", "Fields", ""))), "no zone");
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.paint(hall, 0, cells(a)))), "no paint");
		CityPlan painted = entity.plan();
		int steps = entity.planUndoSteps();
		CityPlans.apply(player, sent(level, CityPlans.Edit.paint(hall, 0, cells(a))));
		CityPlans.apply(player, sent(level, CityPlans.Edit.erase(hall, cells(bare))));
		CityPlans.apply(player, sent(level, CityPlans.Edit.erase(hall, new BitSet())));
		CityPlans.apply(player, sent(level, CityPlans.Edit.editZone(hall, 0, "farms", "Fields", "", false)));
		CityPlans.apply(player, sent(level, CityPlans.Edit.mode(hall, painted.mode())));
		CityPlans.apply(player, sent(level, CityPlans.Edit.removeZone(hall, 7)));
		CityPlans.apply(player, sent(level, CityPlans.Edit.paint(hall, -1, cells(bare))));
		helper.assertTrue(entity.plan().equals(painted), "a stroke that should change nothing changed the plan: " + entity.plan());
		helper.assertTrue(entity.planUndoSteps() == steps, "strokes that changed nothing took undo steps: " + steps + " -> " + entity.planUndoSteps());
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.undo(hall))), "undo refused");
		helper.assertTrue(cellsOf(entity, 0).isEmpty(), "one undo after the no-op strokes didn't take back the real paint: " + cellsOf(entity, 0));
		helper.succeed();
	}

	/** In a protected village a stranger's eraser, rename, new zone and Steward mode are all refused: plan, owner and undo untouched. */
	//$ gametest_ticks_batch AREA '20' '"qaCityPaintStranger"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaCityPaintStranger")
	public void aStrangersSidePanelAndEraserAreRefused(GameTestHelper helper) {
		Leftovers.clear(helper);
		otherHalls(helper, HALL);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, HALL);
		BlockPos hall = entity.getBlockPos();
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		int a = cell(hall, -4, 4);
		helper.assertTrue(CityPlans.apply(owner, sent(level, CityPlans.Edit.addZone(hall, "market", "Square", ""))), "no zone");
		helper.assertTrue(CityPlans.apply(owner, sent(level, CityPlans.Edit.paint(hall, 0, cells(a)))), "no paint");
		entity.setProtected(true);
		Leftovers.after(helper, () -> entity.setProtected(false)); // a protected hall left behind refuses later tests' strangers
		ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
		CityPlan before = entity.plan();
		int steps = entity.planUndoSteps();
		helper.assertFalse(CityPlans.apply(stranger, sent(level, CityPlans.Edit.erase(hall, cells(a)))), "a stranger erased");
		helper.assertFalse(CityPlans.apply(stranger, sent(level, CityPlans.Edit.editZone(hall, 0, "keep_clear", "Mine now", "", true))), "a stranger renamed a zone");
		helper.assertFalse(CityPlans.apply(stranger, sent(level, CityPlans.Edit.addZone(hall, "homes", "Squat", ""))), "a stranger added a zone");
		helper.assertFalse(CityPlans.apply(stranger, sent(level, CityPlans.Edit.mode(hall, CityPlan.Mode.RUN))), "a stranger set the mode");
		helper.assertFalse(CityPlans.apply(stranger, sent(level, CityPlans.Edit.removeZone(hall, 0))), "a stranger deleted a zone");
		helper.assertTrue(entity.plan().equals(before), "a stranger changed the plan: " + entity.plan());
		helper.assertTrue(entity.planUndoSteps() == steps, "a stranger's refused edits changed the undo history");
		helper.assertTrue(owner.getUUID().equals(entity.owner()), "a stranger took the hall");
		helper.assertTrue(CityPlans.apply(owner, sent(level, CityPlans.Edit.undo(hall))) && cellsOf(entity, 0).isEmpty(),
			"the owner's undo after the stranger's tries didn't take back the owner's own paint");
		helper.succeed();
	}

	/** A hall nobody owns: an undo (nothing to undo) claims nothing; the first real edit claims it, the claim survives a save and reload, and a second player is then refused. */
	//$ gametest_ticks_batch AREA '20' '"qaCityPaintClaim"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaCityPaintClaim")
	public void theFirstPaintersClaimIsSavedWithTheHall(GameTestHelper helper) {
		Leftovers.clear(helper);
		otherHalls(helper, HALL);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, HALL);
		BlockPos hall = entity.getBlockPos();
		ServerPlayer first = helper.makeMockServerPlayerInLevel();
		ServerPlayer second = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(entity.owner() == null, "setup: the hall has an owner");
		helper.assertFalse(CityPlans.apply(second, sent(level, CityPlans.Edit.undo(hall))), "undo on an empty plan did something");
		helper.assertTrue(entity.owner() == null, "an undo with nothing to undo claimed the hall");
		helper.assertTrue(CityPlans.apply(first, sent(level, CityPlans.Edit.addZone(hall, "homes", "Homes", ""))), "the first painter was refused");
		helper.assertTrue(first.getUUID().equals(entity.owner()), "the first painter didn't claim the hall");

		CompoundTag tag = entity.saveWithFullMetadata(level.registryAccess());
		VillageHallBlockEntity copy = reload(level, entity, tag);
		helper.assertTrue(copy != null && first.getUUID().equals(copy.owner()), "the claim wasn't saved: " + (copy == null ? "the hall didn't load" : copy.owner()));
		helper.assertTrue(copy.plan().equals(entity.plan()), "the plan wasn't saved with the claim");
		entity.loadWithComponents(tag, level.registryAccess());
		helper.assertTrue(first.getUUID().equals(entity.owner()), "the claim was lost on reload");

		int near = cell(hall, 2, 2);
		helper.assertFalse(CityPlans.apply(second, sent(level, CityPlans.Edit.paint(hall, 0, cells(near)))), "a second player painted a claimed hall's plan");
		helper.assertTrue(cellsOf(entity, 0).isEmpty() && first.getUUID().equals(entity.owner()), "the second player changed the plan or took the hall");
		helper.assertTrue(CityPlans.apply(first, sent(level, CityPlans.Edit.paint(hall, 0, cells(near)))), "the claimer couldn't paint after reload");
		helper.succeed();
	}

	/** Two halls 23 blocks apart diagonally: the world's zoneAt answers from the nearest hall's plan on either side. */
	//$ gametest_ticks_batch HUGE '40' '"qaCityPaintTwoHalls"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaCityPaintTwoHalls")
	public void zoneAtAnswersFromTheNearestHallsPlan(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos at = new BlockPos(4, 2, 4), bt = new BlockPos(20, 2, 20);
		otherHalls(helper, new BlockPos(12, 2, 12));
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity ea = hall(helper, at);
		VillageHallBlockEntity eb = hall(helper, bt);
		BlockPos a = ea.getBlockPos(), b = eb.getBlockPos();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.addZone(a, "homes", "West Homes", ""))), "no zone on A");
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.addZone(b, "farms", "East Fields", ""))), "no zone on B");
		int ca = cell(a, 1, 1), cb = cell(b, 1, 1);
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.paint(a, 0, cells(ca)))), "A's own cell was refused");
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.paint(b, 0, cells(cb)))), "B's own cell was refused");
		helper.assertFalse(CityPlans.apply(player, sent(level, CityPlans.Edit.paint(a, 0, cells(cell(a, 16, 16))))), "A painted the cell under hall B");
		Optional<CityPlan.Zone> byA = CityPlan.zoneAt(level, a.offset(1, 0, 1));
		Optional<CityPlan.Zone> byB = CityPlan.zoneAt(level, b.offset(1, 0, 1));
		helper.assertTrue(byA.isPresent() && byA.get().name().equals("West Homes"), "by hall A: " + byA);
		helper.assertTrue(byB.isPresent() && byB.get().name().equals("East Fields"), "by hall B: " + byB);
		helper.succeed();
	}

	/** At villageHallRadius 128 a cell is 8×8: painting the one north-west of the hall covers exactly blocks -8..-1, nothing at -9 or 0. */
	//$ gametest_ticks_batch AREA '20' '"qaCityPaint128"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaCityPaint128")
	public void atRadius128APaintedCellCoversItsEightByEightBlocks(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 128;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		otherHalls(helper, HALL);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, HALL);
		BlockPos hall = entity.getBlockPos();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(CityPlan.cellSize() == 8, "cell size at 128: " + CityPlan.cellSize());
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.addZone(hall, "civic", "Civic", ""))), "no zone");
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.paint(hall, 0, cells(cell(hall, -1, -1))))), "the cell was refused");
		for (int dx = -8; dx <= -1; dx++) {
			for (int dz = -8; dz <= -1; dz++) {
				helper.assertTrue(CityPlan.zoneAt(level, hall.offset(dx, 0, dz)).isPresent(), "block " + dx + "," + dz + " of the painted cell isn't in the zone");
			}
		}
		for (int[] d : new int[][] {{-9, -1}, {-1, -9}, {0, -1}, {-1, 0}, {0, 0}}) {
			helper.assertTrue(CityPlan.zoneAt(level, hall.offset(d[0], 0, d[1])).isEmpty(), "block " + d[0] + "," + d[1] + " outside the cell is in the zone");
		}
		helper.succeed();
	}
}
