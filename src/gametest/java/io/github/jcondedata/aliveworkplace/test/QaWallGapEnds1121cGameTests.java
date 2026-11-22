package io.github.jcondedata.aliveworkplace.test;

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
import java.util.function.Function;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA lane C (2026-11-21, lane-c-1121-2153): B1425's fix (a Steward wall with one segment taken down or never built is not
 * finished and the gap goes back on the to-build list; roads-and-walls.md "A segment that is taken down, or was never
 * built, leaves the wall unfinished until a builder puts it back"), tested from that spec where
 * {@code QaWallGap1121dBugTests} (a middle segment of a palisade) doesn't reach: the gap at the first and at the last
 * segment of the wall (each has a neighbor on one side only, or wraps round to the first), a Town's stone wall, neighbors
 * that were upgraded (B1380), a restart with the gap open, and the gap built again closing the wall. Each also checks that
 * the gap is the ONLY piece back on the list (its neighbors still stand on their own) and that it isn't held back as a
 * replacement of an older kit's wall.
 */
public class QaWallGapEnds1121cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/** A cleared, grassy area with the hall at (14,2,14), every site and finished building near it forgotten. */
	private static BlockPos ground(GameTestHelper helper) {
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
		BlockPos hall = new BlockPos(14, 2, 14);
		helper.setBlock(hall, ModBlocks.VILLAGE_HALL);
		return helper.absolutePos(hall);
	}

	/** The hall at {@code rank} with a square wall of the kit {@code kit} approved; its pieces. */
	private static List<Walls.Piece> wall(GameTestHelper helper, BlockPos hall, VillageRanks.Rank rank, String kit) {
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(hall);
		entity.setRank(rank);
		int half = 12;
		entity.setPlan(CityPlan.EMPTY.withWall(new CityPlan.Wall(List.of(new BlockPos(-half, 0, -half), new BlockPos(half, 0, -half),
			new BlockPos(half, 0, half), new BlockPos(-half, 0, half)), true).approvedWith(kit)));
		List<Walls.Piece> pieces = Walls.pieces(helper.getLevel(), hall);
		helper.assertTrue(pieces.stream().filter(p -> p.kind() == Walls.Kind.SEGMENT).count() >= 3,
			"(setup) the " + kit + " wall has fewer than 3 segments: " + pieces.size() + " pieces");
		return pieces;
	}

	/** Every piece recorded finished, as {@code built} names its blueprint (the piece's own, or its upgrade). */
	private static void buildAll(GameTestHelper helper, BlockPos hall, List<Walls.Piece> pieces, Function<Walls.Piece, net.minecraft.resources.ResourceLocation> built) {
		BuildSiteManager manager = BuildSiteManager.get(helper.getLevel());
		for (Walls.Piece piece : pieces) {
			manager.recordFinished(built.apply(piece), piece.placement(), UUID.randomUUID());
		}
		helper.assertTrue(Walls.finished(helper.getLevel(), hall), "(setup) the whole wall built doesn't read finished");
	}

	private static List<Walls.Piece> segments(List<Walls.Piece> pieces) {
		return pieces.stream().filter(p -> p.kind() == Walls.Kind.SEGMENT).toList();
	}

	/** With {@code gone} missing: it doesn't stand, the wall isn't finished, it alone is to build, and it replaces nothing. */
	private static void assertGap(GameTestHelper helper, BlockPos hall, Walls.Piece gone, String what) {
		ServerLevel level = helper.getLevel();
		helper.assertFalse(Walls.stands(level, gone), what + ": segment " + gone.index() + " " + gone.box() + " reads standing once it's gone");
		helper.assertFalse(Walls.finished(level, hall), what + ": the wall reads finished with segment " + gone.index() + " gone");
		List<Integer> unbuilt = Walls.unbuilt(level, hall).stream().map(Walls.Piece::index).toList();
		helper.assertTrue(unbuilt.equals(List.of(gone.index())), what + ": the pieces to build are " + unbuilt + ", wanted only the gap " + gone.index());
		helper.assertFalse(Walls.replaces(level, gone), what + ": the gap " + gone.index() + " reads as replacing an older kit's wall");
	}

	private static void reload(ServerLevel level) {
		BuildSiteManager loaded = BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), level.registryAccess()), level.registryAccess());
		level.getDataStorage().set(BuildSiteManager.NAME, loaded);
	}

	/** Runs {@code body} with walls switched on, put back after. */
	private static void withWalls(GameTestHelper helper, Runnable body) {
		boolean was = Walls.ENABLED;
		Walls.ENABLED = true;
		try {
			body.run();
		} finally {
			Walls.ENABLED = was;
		}
		helper.succeed();
	}

	/** The first segment of a finished palisade taken down (a neighbor on its far side; the line's start on the other). */
	//$ gametest_ticks_batch AREA '40' '"qa1121cWallGapFirst"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1121cWallGapFirst")
	public void theFirstSegmentTakenDownLeavesTheWallUnfinished(GameTestHelper helper) {
		withWalls(helper, () -> {
			BlockPos hall = ground(helper);
			List<Walls.Piece> pieces = wall(helper, hall, VillageRanks.Rank.VILLAGE, "palisade");
			buildAll(helper, hall, pieces, Walls.Piece::blueprint);
			Walls.Piece gone = segments(pieces).get(0);
			BuildSiteManager.get(helper.getLevel()).forgetFinished(gone.placement());
			assertGap(helper, hall, gone, "first segment");
		});
	}

	/** The last segment of a finished palisade taken down (where the closed wall comes back round to its start). */
	//$ gametest_ticks_batch AREA '40' '"qa1121cWallGapLast"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1121cWallGapLast")
	public void theLastSegmentTakenDownLeavesTheWallUnfinished(GameTestHelper helper) {
		withWalls(helper, () -> {
			BlockPos hall = ground(helper);
			List<Walls.Piece> pieces = wall(helper, hall, VillageRanks.Rank.VILLAGE, "palisade");
			buildAll(helper, hall, pieces, Walls.Piece::blueprint);
			List<Walls.Piece> segments = segments(pieces);
			Walls.Piece gone = segments.get(segments.size() - 1);
			BuildSiteManager.get(helper.getLevel()).forgetFinished(gone.placement());
			assertGap(helper, hall, gone, "last segment");
		});
	}

	/** A Town's stone wall (the other kit) with a middle segment taken down. */
	//$ gametest_ticks_batch AREA '40' '"qa1121cWallGapStone"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1121cWallGapStone")
	public void aStoneWallWithASegmentTakenDownIsUnfinished(GameTestHelper helper) {
		withWalls(helper, () -> {
			BlockPos hall = ground(helper);
			List<Walls.Piece> pieces = wall(helper, hall, VillageRanks.Rank.TOWN, "stone");
			buildAll(helper, hall, pieces, Walls.Piece::blueprint);
			List<Walls.Piece> segments = segments(pieces);
			Walls.Piece gone = segments.get(segments.size() / 2);
			BuildSiteManager.get(helper.getLevel()).forgetFinished(gone.placement());
			assertGap(helper, hall, gone, "stone wall");
		});
	}

	/**
	 * B1380 with B1425: every segment upgraded by the player (palisade_2) stands for its piece, and one of them taken down is
	 * still a gap: its upgraded neighbors reaching over its spot don't fill it.
	 */
	//$ gametest_ticks_batch AREA '40' '"qa1121cWallGapUpgraded"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1121cWallGapUpgraded")
	public void aGapBetweenUpgradedSegmentsIsStillAGap(GameTestHelper helper) {
		withWalls(helper, () -> {
			BlockPos hall = ground(helper);
			List<Walls.Piece> pieces = wall(helper, hall, VillageRanks.Rank.VILLAGE, "palisade");
			buildAll(helper, hall, pieces, p -> p.kind() == Walls.Kind.SEGMENT ? BlueprintUpgrades.upgradeOf(p.blueprint()) : p.blueprint());
			List<Walls.Piece> segments = segments(pieces);
			Walls.Piece gone = segments.get(segments.size() / 2);
			BuildSiteManager.get(helper.getLevel()).forgetFinished(gone.placement());
			assertGap(helper, hall, gone, "upgraded segments");
		});
	}

	/** The gap survives a restart (still unfinished, still the one piece to build); built again, the wall is finished. */
	//$ gametest_ticks_batch AREA '40' '"qa1121cWallGapReload"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1121cWallGapReload")
	public void aGapStaysOpenAcrossARestartAndBuildingItClosesTheWall(GameTestHelper helper) {
		withWalls(helper, () -> {
			BlockPos hall = ground(helper);
			ServerLevel level = helper.getLevel();
			List<Walls.Piece> pieces = wall(helper, hall, VillageRanks.Rank.VILLAGE, "palisade");
			buildAll(helper, hall, pieces, Walls.Piece::blueprint);
			List<Walls.Piece> segments = segments(pieces);
			Walls.Piece gone = segments.get(1);
			BuildSiteManager.get(level).forgetFinished(gone.placement());
			reload(level);
			assertGap(helper, hall, gone, "after a restart");
			BuildSiteManager.get(level).recordFinished(gone.blueprint(), gone.placement(), UUID.randomUUID());
			helper.assertTrue(Walls.stands(level, gone), "the gap " + gone.index() + " built again doesn't stand");
			helper.assertTrue(Walls.finished(level, hall), "the wall with its gap built again doesn't read finished: still to build "
				+ Walls.unbuilt(level, hall).stream().map(Walls.Piece::index).toList());
		});
	}

	/** A tower of the wall taken down: the wall isn't finished and the tower alone is to build. */
	//$ gametest_ticks_batch AREA '40' '"qa1121cWallGapTower"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1121cWallGapTower")
	public void aTowerTakenDownLeavesTheWallUnfinished(GameTestHelper helper) {
		withWalls(helper, () -> {
			BlockPos hall = ground(helper);
			List<Walls.Piece> pieces = wall(helper, hall, VillageRanks.Rank.VILLAGE, "palisade");
			buildAll(helper, hall, pieces, Walls.Piece::blueprint);
			Walls.Piece gone = pieces.stream().filter(p -> p.kind() == Walls.Kind.TOWER).findFirst().orElse(null);
			helper.assertTrue(gone != null, "(setup) the palisade wall has no tower: " + pieces.stream().map(Walls.Piece::kind).toList());
			BuildSiteManager.get(helper.getLevel()).forgetFinished(gone.placement());
			ServerLevel level = helper.getLevel();
			helper.assertFalse(Walls.stands(level, gone), "the tower " + gone.index() + " reads standing once it's gone");
			helper.assertFalse(Walls.finished(level, hall), "the wall reads finished with tower " + gone.index() + " gone");
			List<Integer> unbuilt = Walls.unbuilt(level, hall).stream().map(Walls.Piece::index).toList();
			helper.assertTrue(unbuilt.equals(List.of(gone.index())), "the pieces to build are " + unbuilt + ", wanted only the tower " + gone.index());
		});
	}
}
