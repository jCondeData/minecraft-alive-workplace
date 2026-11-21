package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * B1380: a wall segment the player upgraded (palisade_2 or stone_wall_2, built over the Steward's palisade or stone_wall
 * on the same spot, the way an upgrade is built) is still that wall piece: it stands, the wall still reads as finished,
 * and the whole wall still counts as one building for the rank, not one more per upgraded segment.
 */
public class QaWallUpgradeCountsGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/** The area cleared to flat grass (floor y 1), old sites and buildings round it forgotten, and a hall put down. */
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

	private static CityPlan.Wall square(int half) {
		return new CityPlan.Wall(List.of(new BlockPos(-half, 0, -half), new BlockPos(half, 0, -half), new BlockPos(half, 0, half),
			new BlockPos(-half, 0, half)), true);
	}

	/**
	 * Every piece of the {@code kit} wall built, then every segment upgraded on its spot (the upgrade finished over it,
	 * as a builder records it): each piece still stands, the wall is finished, and it counts as one building.
	 */
	private static void upgradedSegmentsStillCount(GameTestHelper helper, String kit, VillageRanks.Rank rank, String upgrade) {
		ServerLevel level = helper.getLevel();
		boolean was = Walls.ENABLED;
		Walls.ENABLED = true;
		try {
			BlockPos hall = ground(helper, new BlockPos(14, 2, 14));
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			entity.setRank(rank); // the kit goes by the village's rank
			entity.setPlan(CityPlan.EMPTY.withWall(square(12).approvedWith(kit)));
			helper.assertValueEqual(Walls.kitFor(level, hall).orElseThrow().name(), kit, "setup: the kit for a " + rank);
			List<Walls.Piece> pieces = Walls.pieces(level, hall);
			helper.assertTrue(pieces.size() >= 4, "setup: the " + kit + " wall has only " + pieces.size() + " pieces");
			int before = VillageRanks.score(level, hall, 0).buildings();
			BuildSiteManager manager = BuildSiteManager.get(level);
			for (Walls.Piece piece : pieces) {
				manager.recordFinished(piece.blueprint(), piece.placement(), UUID.randomUUID());
			}
			helper.assertTrue(Walls.finished(level, hall), "setup: the built " + kit + " wall doesn't read as finished");
			helper.assertValueEqual(VillageRanks.score(level, hall, 0).buildings(), before + 1, "buildings with the " + kit + " wall built");

			int upgraded = 0;
			for (Walls.Piece piece : pieces) {
				if (piece.kind() != Walls.Kind.SEGMENT) {
					continue;
				}
				ResourceLocation up = BlueprintUpgrades.upgradeOf(piece.blueprint());
				helper.assertValueEqual(up.getPath(), upgrade, "the " + kit + " segment's upgrade");
				helper.assertTrue(BlueprintLibrary.get(level, up).isPresent(), "setup: no blueprint " + up);
				manager.recordFinished(up, piece.placement(), UUID.randomUUID());
				upgraded++;
			}
			helper.assertTrue(upgraded > 0, "setup: the " + kit + " wall has no segments");

			for (Walls.Piece piece : Walls.pieces(level, hall)) {
				helper.assertTrue(Walls.stands(level, piece), "the " + piece.kind() + " " + piece.index() + " doesn't stand once its segment is upgraded to " + upgrade);
			}
			helper.assertTrue(Walls.unbuilt(level, hall).isEmpty(), Walls.unbuilt(level, hall).size() + " upgraded pieces are to be built again");
			helper.assertTrue(Walls.finished(level, hall), "the " + kit + " wall doesn't read as finished once " + upgraded + " segments are upgraded to " + upgrade);
			helper.assertTrue(Walls.isPiece(ResourceLocation.fromNamespaceAndPath("aliveworkplace", upgrade)), upgrade + " isn't a wall piece");
			helper.assertValueEqual(VillageRanks.score(level, hall, 0).buildings(), before + 1,
				"buildings with " + upgraded + " segments upgraded to " + upgrade + " (the wall counts once)");
		} finally {
			Walls.ENABLED = was;
		}
		helper.succeed();
	}

	/** Palisade segments upgraded to palisade_2 still count as the palisade wall's pieces. */
	//$ gametest_ticks_batch AREA '40' '"qaWallUpgradePalisade"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaWallUpgradePalisade")
	public void anUpgradedPalisadeSegmentStillCountsAsTheWall(GameTestHelper helper) {
		upgradedSegmentsStillCount(helper, "palisade", VillageRanks.Rank.VILLAGE, "palisade_2");
	}

	/** Stone wall segments upgraded to stone_wall_2 still count as the stone wall's pieces. */
	//$ gametest_ticks_batch AREA '40' '"qaWallUpgradeStone"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaWallUpgradeStone")
	public void anUpgradedStoneWallSegmentStillCountsAsTheWall(GameTestHelper helper) {
		upgradedSegmentsStillCount(helper, "stone", VillageRanks.Rank.TOWN, "stone_wall_2");
	}

	/**
	 * A Town's stone kit still replaces a palisade segment the player upgraded: palisade_2 is a palisade piece, so it
	 * doesn't stand for the stone wall, and the stone segment on its spot replaces it rather than being held off by it.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaWallUpgradeReplaced"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaWallUpgradeReplaced")
	public void aStoneWallStillReplacesAnUpgradedPalisadeSegment(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean was = Walls.ENABLED;
		Walls.ENABLED = true;
		try {
			BlockPos hall = ground(helper, new BlockPos(14, 2, 14));
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			entity.setRank(VillageRanks.Rank.VILLAGE);
			entity.setPlan(CityPlan.EMPTY.withWall(square(12).approvedWith("palisade")));
			List<Walls.Piece> palisade = Walls.pieces(level, hall);
			BuildSiteManager manager = BuildSiteManager.get(level);
			// only the segments, each upgraded (no towers or gates, which a stone segment could find on its spot instead)
			for (Walls.Piece piece : palisade) {
				if (piece.kind() == Walls.Kind.SEGMENT) {
					manager.recordFinished(BlueprintUpgrades.upgradeOf(piece.blueprint()), piece.placement(), UUID.randomUUID());
				}
			}
			// the village becomes a Town: its kit is stone now, laid out on the same line
			entity.setRank(VillageRanks.Rank.TOWN);
			entity.setPlan(CityPlan.EMPTY.withWall(square(12).approvedWith("stone")));
			List<Walls.Piece> stone = Walls.pieces(level, hall);
			helper.assertTrue(stone.stream().anyMatch(p -> p.blueprint().getPath().equals("stone_wall")),
				"setup: the Town's wall isn't stone: " + stone);
			helper.assertTrue(stone.stream().noneMatch(p -> Walls.stands(level, p)), "an upgraded palisade segment stands for the Town's stone wall");
			long replacing = stone.stream().filter(p -> p.kind() == Walls.Kind.SEGMENT && Walls.replaces(level, p)).count();
			helper.assertTrue(replacing > 0, "no stone segment replaces an upgraded palisade segment on its spot");
		} finally {
			Walls.ENABLED = was;
		}
		helper.succeed();
	}
}
