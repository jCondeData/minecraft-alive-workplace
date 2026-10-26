package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.Walls;
import java.util.BitSet;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA for B510 (27.18, the Steward's own wall line with no line drawn): the rectangle goes exactly
 * {@link Walls#ZONE_MARGIN} (4) blocks out from the buildable zones' blocks on every side, whatever the zones' shape,
 * how many there are and where on the grid they sit; Keep Clear zones (not buildable) don't count, and a plan with no
 * buildable zone has no line. Written from the spec: the expected rectangle comes from the blocks each cell covers
 * ({@link CityPlan#cellAt}), not from the code under test.
 */
public class QaWallLineGameTests implements FabricGameTest {

	private static final int MARGIN = 4;

	/** Two zones of different kinds in an L, plus a Keep Clear zone further out that must not widen the line. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void twoZonesInAnLAreRingedFourOutAndKeepClearIsLeftOut(GameTestHelper helper) {
		BlockPos hall = new BlockPos(100, 70, -200);
		BitSet homes = cells(hall, -10, -10, 5, 2);
		BitSet farms = cells(hall, -10, 3, -3, 17);
		BitSet clear = cells(hall, 30, 30, 40, 40);
		CityPlan plan = CityPlan.EMPTY.addZone("homes", "Homes", "").addZone("farms", "Farms", "").addZone("keep_clear", "Keep", "")
			.paint(0, homes).paint(1, farms).paint(2, clear);
		BitSet buildable = (BitSet) homes.clone();
		buildable.or(farms);
		check(helper, hall, plan, buildable, "the L of homes and farms");
		helper.succeed();
	}

	/** One cell: the line is the cell's square widened by 4 on each side, so 8 blocks wider than the cell. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aOneCellZoneIsRingedFourOut(GameTestHelper helper) {
		BlockPos hall = new BlockPos(-37, 64, 12);
		BitSet one = cells(hall, 1, 1, 1, 1);
		helper.assertValueEqual(one.cardinality(), 1, "cells painted for one block");
		CityPlan plan = CityPlan.EMPTY.addZone("workshops", "Shops", "").paint(0, one);
		int[] box = check(helper, hall, plan, one, "one cell");
		int size = CityPlan.cellSize();
		helper.assertValueEqual(box[2] - box[0], size - 1 + 2 * MARGIN, "the line's width round one " + size + "-block cell");
		helper.assertValueEqual(box[3] - box[1], size - 1 + 2 * MARGIN, "the line's depth round one " + size + "-block cell");
		helper.succeed();
	}

	/** The grid's two far corners: the line reaches 4 past the plan's first and last blocks, both sides alike. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void zonesInTheGridsFarCornersAreRingedFourOut(GameTestHelper helper) {
		BlockPos hall = new BlockPos(0, 64, 0);
		int last = CityPlan.GRID * CityPlan.GRID - 1;
		BitSet corners = new BitSet();
		corners.set(0);
		corners.set(last);
		CityPlan plan = CityPlan.EMPTY.addZone("homes", "Homes", "").paint(0, corners);
		int[] box = check(helper, hall, plan, corners, "the grid's corners");
		int half = CityPlan.cellSize() * CityPlan.GRID / 2;
		helper.assertValueEqual(box[0], -half - MARGIN, "the west side");
		helper.assertValueEqual(box[1], -half - MARGIN, "the north side");
		helper.assertValueEqual(box[2], half - 1 + MARGIN, "the east side");
		helper.assertValueEqual(box[3], half - 1 + MARGIN, "the south side");
		helper.succeed();
	}

	/** Only a Keep Clear zone, or no zone: no line of the Steward's own. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void noBuildableZoneMeansNoLine(GameTestHelper helper) {
		BlockPos hall = new BlockPos(5, 64, 5);
		helper.assertTrue(Walls.ownLine(hall, CityPlan.EMPTY).isEmpty(), "a plan with no zones has a wall line");
		CityPlan clear = CityPlan.EMPTY.addZone("keep_clear", "Keep", "").paint(0, cells(hall, -8, -8, 8, 8));
		helper.assertTrue(Walls.ownLine(hall, clear).isEmpty(), "a plan with only a Keep Clear zone has a wall line: "
			+ Walls.ownLine(hall, clear));
		CityPlan empty = CityPlan.EMPTY.addZone("homes", "Homes", "");
		helper.assertTrue(Walls.ownLine(hall, empty).isEmpty(), "a homes zone with no cells painted has a wall line");
		helper.succeed();
	}

	// ---- helpers -------------------------------------------------------------------------------------------------

	/** The cells under the blocks hall + (x0..x1, z0..z1). */
	private static BitSet cells(BlockPos hall, int x0, int z0, int x1, int z1) {
		BitSet out = new BitSet();
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				int cell = CityPlan.cellAt(hall, hall.offset(x, 0, z));
				if (cell >= 0) {
					out.set(cell);
				}
			}
		}
		return out;
	}

	/**
	 * Checks the Steward's own line is a closed rectangle at the hall's height exactly 4 blocks out from every block of
	 * {@code buildable}; returns its {minX, minZ, maxX, maxZ} relative to the hall.
	 */
	private static int[] check(GameTestHelper helper, BlockPos hall, CityPlan plan, BitSet buildable, String what) {
		int half = CityPlan.cellSize() * CityPlan.GRID / 2;
		int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (int dx = -half - 2; dx <= half + 2; dx++) {
			for (int dz = -half - 2; dz <= half + 2; dz++) {
				int cell = CityPlan.cellAt(hall, hall.offset(dx, 0, dz));
				if (cell >= 0 && buildable.get(cell)) {
					minX = Math.min(minX, dx);
					maxX = Math.max(maxX, dx);
					minZ = Math.min(minZ, dz);
					maxZ = Math.max(maxZ, dz);
				}
			}
		}
		List<BlockPos> line = Walls.ownLine(hall, plan);
		helper.assertValueEqual(line.size(), 4, "corners of the line round " + what + " (" + line + ")");
		for (BlockPos p : line) {
			helper.assertValueEqual(p.getY(), hall.getY(), "a corner's height round " + what);
		}
		int lx = line.stream().mapToInt(BlockPos::getX).min().orElseThrow() - hall.getX();
		int hx = line.stream().mapToInt(BlockPos::getX).max().orElseThrow() - hall.getX();
		int lz = line.stream().mapToInt(BlockPos::getZ).min().orElseThrow() - hall.getZ();
		int hz = line.stream().mapToInt(BlockPos::getZ).max().orElseThrow() - hall.getZ();
		String zone = "; zone blocks x " + minX + ".." + maxX + ", z " + minZ + ".." + maxZ + ", line x " + lx + ".." + hx + ", z " + lz + ".." + hz;
		helper.assertValueEqual(minX - lx, MARGIN, "blocks out on the west round " + what + zone);
		helper.assertValueEqual(minZ - lz, MARGIN, "blocks out on the north round " + what + zone);
		helper.assertValueEqual(hx - maxX, MARGIN, "blocks out on the east round " + what + zone);
		helper.assertValueEqual(hz - maxZ, MARGIN, "blocks out on the south round " + what + zone);
		// a rectangle: each corner on two of the four sides
		for (BlockPos p : line) {
			int x = p.getX() - hall.getX();
			int z = p.getZ() - hall.getZ();
			helper.assertTrue((x == lx || x == hx) && (z == lz || z == hz), "a corner off the rectangle round " + what + ": " + p);
		}
		return new int[] {lx, lz, hx, hz};
	}
}
