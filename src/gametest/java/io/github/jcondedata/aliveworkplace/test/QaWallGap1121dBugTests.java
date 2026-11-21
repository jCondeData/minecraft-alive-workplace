package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.Walls;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA bug test (lane D, 2026-11-21, found while testing B1380; NOT registered while it fails): one segment of a finished
 * Steward wall taken down leaves a hole in the wall, so that segment isn't standing, the wall isn't finished, and the
 * segment is to be built again. Today it still reads standing, because the segments on either side sit a block or two
 * over its spot (Walls.layout spreads them that way) and Walls.finishedOn takes any wall piece of the same blueprint on
 * the spot as this one.
 */
public class QaWallGap1121dBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	private static BlockPos ground(GameTestHelper helper, BlockPos hallAt) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(BlockPos.ZERO);
		BoundingBox near = new BoundingBox(a.getX() - 80, level.getMinBuildHeight(), a.getZ() - 80, a.getX() + 110, level.getMaxBuildHeight(), a.getZ() + 110);
		BuildSiteManager manager = BuildSiteManager.get(level);
		for (BuildSite site : new ArrayList<>(manager.all())) {
			if (near.isInside(site.placement().origin())) {
				manager.remove(site.id());
			}
		}
		for (BuildSiteManager.Finished f : manager.finishedIn(level)) {
			if (near.isInside(f.placement().origin())) {
				manager.forgetFinished(f.placement());
			}
		}
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
				for (int y = 2; y < 16; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		helper.setBlock(hallAt, ModBlocks.VILLAGE_HALL);
		return helper.absolutePos(hallAt);
	}

	/** A finished palisade wall with one segment in the middle of a run taken down: the wall has a hole and isn't finished. */
	//$ gametest_ticks_batch AREA '40' '"qa1121dWallGap"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1121dWallGap")
	public void qaAWallWithOneSegmentTakenDownIsNotFinished(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean was = Walls.ENABLED;
		Walls.ENABLED = true;
		try {
			BlockPos hall = ground(helper, new BlockPos(14, 2, 14));
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			entity.setRank(VillageRanks.Rank.VILLAGE);
			int half = 12;
			entity.setPlan(CityPlan.EMPTY.withWall(new CityPlan.Wall(List.of(new BlockPos(-half, 0, -half), new BlockPos(half, 0, -half),
				new BlockPos(half, 0, half), new BlockPos(-half, 0, half)), true).approvedWith("palisade")));
			List<Walls.Piece> pieces = Walls.pieces(level, hall);
			BuildSiteManager manager = BuildSiteManager.get(level);
			for (Walls.Piece piece : pieces) {
				manager.recordFinished(piece.blueprint(), piece.placement(), UUID.randomUUID());
			}
			helper.assertTrue(Walls.finished(level, hall), "setup: the built palisade wall doesn't read as finished");
			List<Walls.Piece> segments = pieces.stream().filter(p -> p.kind() == Walls.Kind.SEGMENT).toList();
			Walls.Piece gone = segments.get(segments.size() / 2);
			manager.forgetFinished(gone.placement());
			helper.assertTrue(!Walls.stands(level, gone), "the segment " + gone.index() + " " + gone.box() + " reads standing once it's taken down");
			helper.assertTrue(!Walls.finished(level, hall), "the wall reads finished with segment " + gone.index() + " taken down");
		} finally {
			Walls.ENABLED = was;
		}
		helper.succeed();
	}
}
