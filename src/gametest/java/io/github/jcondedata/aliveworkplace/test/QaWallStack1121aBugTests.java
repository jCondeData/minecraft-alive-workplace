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
 * B1457 (lane A, 2026-11-21): segments of a Steward wall sit a block or two over one another, and a piece's height was
 * read from the top of whatever stood at its middle, the wall's own pieces too. Since B1425 builds a segment whose
 * neighbor stands, that segment was laid out on top of the neighbor, and each one built lifted the other onto itself:
 * the wall stacked up till the builders' cobblestone ran out (WallGameTests' palisade test, red on CI twice).
 */
public class QaWallStack1121aBugTests implements FabricGameTest {
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
				for (int y = 2; y < 28; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		helper.setBlock(hallAt, ModBlocks.VILLAGE_HALL);
		return helper.absolutePos(hallAt);
	}

	/**
	 * The palisade test's line (tower, gate on a road, two segments well over one another, tower), every piece but one
	 * segment built (its box solid): laid out again, every piece sits on the ground, the built ones where they were built
	 * and the unbuilt segment beside them, not on top of its neighbor.
	 */
	//$ gametest_ticks_batch AREA '40' '"qa1121aWallStack"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1121aWallStack")
	public void qaASegmentBesideABuiltOneIsLaidOutOnTheGroundNotOnIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean was = Walls.ENABLED;
		Walls.ENABLED = true;
		try {
			BlockPos hall = ground(helper, new BlockPos(14, 2, 14));
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			entity.setRank(VillageRanks.Rank.VILLAGE);
			CityPlan.Road road = new CityPlan.Road(List.of(new BlockPos(-4, 0, -3), new BlockPos(-4, 0, -13)), CityPlan.Road.STREET, "", false);
			entity.setPlan(CityPlan.EMPTY.addRoad(road)
				.withWall(new CityPlan.Wall(List.of(new BlockPos(-9, 0, -9), new BlockPos(9, 0, -9)), false).approvedWith("palisade")));
			int groundY = helper.absolutePos(new BlockPos(0, 2, 0)).getY();
			List<Walls.Piece> pieces = Walls.pieces(level, hall);
			List<Walls.Piece> segments = pieces.stream().filter(p -> p.kind() == Walls.Kind.SEGMENT).toList();
			helper.assertTrue(segments.size() >= 2, "setup: the line has " + segments.size() + " segments");
			for (Walls.Piece p : pieces) {
				helper.assertTrue(p.box().minY() == groundY, "setup: piece " + p.index() + " " + p.kind() + " laid out at y "
					+ p.box().minY() + ", not on the ground at " + groundY);
			}
			// the segment left unbuilt: one whose middle column is inside another segment (they overlap that much here)
			Walls.Piece unbuilt = null;
			for (Walls.Piece s : segments) {
				int mx = (s.box().minX() + s.box().maxX()) / 2;
				int mz = (s.box().minZ() + s.box().maxZ()) / 2;
				if (segments.stream().anyMatch(o -> o != s && o.box().minX() <= mx && mx <= o.box().maxX() && o.box().minZ() <= mz && mz <= o.box().maxZ())) {
					unbuilt = s;
					break;
				}
			}
			helper.assertTrue(unbuilt != null, "setup: no segment's middle lies in another segment: " + segments.stream().map(Walls.Piece::box).toList());
			BuildSiteManager manager = BuildSiteManager.get(level);
			for (Walls.Piece p : pieces) {
				if (p.index() == unbuilt.index()) {
					continue;
				}
				BoundingBox b = p.box();
				for (BlockPos at : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())) {
					level.setBlockAndUpdate(at, Blocks.SPRUCE_LOG.defaultBlockState());
				}
				manager.recordFinished(p.blueprint(), p.placement(), UUID.randomUUID());
			}
			List<Walls.Piece> again = Walls.pieces(level, hall);
			helper.assertTrue(again.size() == pieces.size(), "laid out again, the line has " + again.size() + " pieces, not " + pieces.size());
			for (int i = 0; i < pieces.size(); i++) {
				Walls.Piece before = pieces.get(i);
				Walls.Piece now = again.get(i);
				helper.assertTrue(now.placement().equals(before.placement()), "piece " + i + " " + now.kind() + " laid out again at "
					+ now.box() + ", not where it was at " + before.box() + (i == unbuilt.index() ? " (the unbuilt segment)" : " (built)"));
			}
			for (Walls.Piece p : again) {
				helper.assertTrue(Walls.stands(level, p) == (p.index() != unbuilt.index()), "piece " + p.index() + " " + p.kind()
					+ (p.index() == unbuilt.index() ? " (unbuilt) stands" : " (built) doesn't stand"));
			}
		} finally {
			Walls.ENABLED = was;
		}
		helper.succeed();
	}
}
