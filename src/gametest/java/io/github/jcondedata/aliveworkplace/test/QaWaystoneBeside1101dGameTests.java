package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageWaystones;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane D, 2026-11-01), 41.3 from the spec: caravans and settlers "walk to their stone and step out at the other
 * village's"; where a traveler stands to step into or out of a stone is "the nearest open spot beside it (footing, two
 * blocks of room) nearest" the way he goes ({@code VillageWaystones.beside}, used by caravans and settlers). The stone
 * here is a two-block pillar on the floor, as a waystone stands. A mutant that looked only at radius 1
 * (VillageWaystones:280, r <= 2 to r < 2) survived the waystone suite: with the stone ringed, travelers then stood in the stone itself.
 */
public class QaWaystoneBeside1101dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos STONE = new BlockPos(4, 2, 4);

	private static BlockPos stone(GameTestHelper helper) {
		helper.setBlock(STONE, Blocks.STONE_BRICKS);
		helper.setBlock(STONE.above(), Blocks.STONE_BRICKS);
		return helper.absolutePos(STONE);
	}

	/** Open floor all round: the spot is one of the eight blocks right beside the stone, at its level, on the side toward the way. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void onOpenGroundATravelerStepsOutRightBesideTheStoneOnHisSide(GameTestHelper helper) {
		BlockPos stone = stone(helper);
		for (int[] way : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
			BlockPos toward = stone.offset(way[0] * 20, 0, way[1] * 20);
			BlockPos spot = VillageWaystones.beside(helper.getLevel(), stone, toward);
			helper.assertTrue(spot.equals(stone.offset(way[0], 0, way[1])),
				"toward " + way[0] + "," + way[1] + " the traveler stands " + spot.subtract(stone).toShortString() + " from the stone, not right beside it on that side");
		}
		helper.succeed();
	}

	/** The block beside the stone on the traveler's side is taken (a lantern post): he stands on the next open spot beside it, never in the stone. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void aBlockedSideStillLeavesASpotBesideTheStone(GameTestHelper helper) {
		BlockPos stone = stone(helper);
		helper.setBlock(STONE.east(), Blocks.OAK_FENCE);
		helper.setBlock(STONE.east().above(), Blocks.OAK_FENCE);
		BlockPos spot = VillageWaystones.beside(helper.getLevel(), stone, stone.offset(20, 0, 0));
		BlockPos d = spot.subtract(stone);
		helper.assertTrue(d.getY() == 0 && d.getX() == 1 && Math.abs(d.getZ()) == 1,
			"with the east side taken the traveler stands " + d.toShortString() + " from the stone (expected a corner beside it on the east)");
		helper.succeed();
	}

	/**
	 * A stone with a low wall right round it (the plaza's railings, a player's fences), open ground one step further out:
	 * the traveler stands out there, never inside the stone.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void aStoneRingedByAWallNeverPutsTheTravelerInsideIt(GameTestHelper helper) {
		BlockPos stone = stone(helper);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx != 0 || dz != 0) {
					helper.setBlock(STONE.offset(dx, 0, dz), Blocks.COBBLESTONE_WALL);
					helper.setBlock(STONE.offset(dx, 1, dz), Blocks.COBBLESTONE_WALL);
				}
			}
		}
		BlockPos spot = VillageWaystones.beside(helper.getLevel(), stone, stone.offset(20, 0, 0));
		helper.assertTrue(!spot.equals(stone), "with a wall round the stone the traveler is put inside the stone");
		helper.assertTrue(spot.subtract(stone).equals(new BlockPos(2, 0, 0)),
			"past the wall the traveler stands " + spot.subtract(stone).toShortString() + " from the stone (expected 2 out on his side, at its level)");
		helper.succeed();
	}
}
