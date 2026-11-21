package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
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
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (lane D, 2026-11-21) for B1380, written from its ROADMAP entry: a wall piece the player upgraded on its spot
 * (palisade_2 over a palisade segment, stone_wall_2 over a stone_wall, and the stone kit's wall_tower_2 and
 * gatehouse_2) is still that wall piece. One upgraded segment in the middle of a finished wall leaves it finished and
 * counting as one building; the upgraded segment taken down leaves a gap; an upgrade somewhere else doesn't fill the
 * gap; and an ordinary building and its own upgrade still count as buildings.
 */
public class QaWallUpgradeCounts1121dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath("aliveworkplace", path);
	}

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

	private static boolean columns(BoundingBox a, BoundingBox b) {
		return a.minX() <= b.maxX() && a.maxX() >= b.minX() && a.minZ() <= b.maxZ() && a.maxZ() >= b.minZ();
	}

	/** A village of {@code rank} with an approved {@code kit} wall, every piece of it finished; its hall. */
	private static BlockPos builtWall(GameTestHelper helper, String kit, VillageRanks.Rank rank) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = ground(helper, new BlockPos(14, 2, 14));
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setRank(rank);
		entity.setPlan(CityPlan.EMPTY.withWall(square(12).approvedWith(kit)));
		helper.assertValueEqual(Walls.kitFor(level, hall).orElseThrow().name(), kit, "setup: the kit for a " + rank);
		List<Walls.Piece> pieces = Walls.pieces(level, hall);
		helper.assertTrue(pieces.size() >= 4, "setup: the " + kit + " wall has only " + pieces.size() + " pieces");
		BuildSiteManager manager = BuildSiteManager.get(level);
		for (Walls.Piece piece : pieces) {
			manager.recordFinished(piece.blueprint(), piece.placement(), UUID.randomUUID());
		}
		helper.assertTrue(Walls.finished(level, hall), "setup: the built " + kit + " wall doesn't read as finished");
		return hall;
	}

	/**
	 * One segment in the middle of a finished {@code kit} wall upgraded to {@code upgrade} on its spot: the wall still
	 * reads finished, that segment stands (also with its overlapping neighbours gone, so it stands by itself), and the
	 * wall counts once. Then that segment is taken down: the wall isn't finished and that segment is to build. Then an
	 * upgrade finished somewhere off the line (the courtyard) doesn't stand in for it.
	 */
	private static void oneUpgradedSegment(GameTestHelper helper, String kit, VillageRanks.Rank rank, String upgrade) {
		ServerLevel level = helper.getLevel();
		boolean was = Walls.ENABLED;
		Walls.ENABLED = true;
		try {
			// the count without any wall, for comparing
			BlockPos hall0 = ground(helper, new BlockPos(14, 2, 14));
			int bare = VillageRanks.score(level, hall0, 0).buildings();
			BlockPos hall = builtWall(helper, kit, rank);
			helper.assertValueEqual(VillageRanks.score(level, hall, 0).buildings(), bare + 1, "setup: buildings with the " + kit + " wall built");
			List<Walls.Piece> pieces = Walls.pieces(level, hall);
			List<Walls.Piece> segments = pieces.stream().filter(p -> p.kind() == Walls.Kind.SEGMENT).toList();
			helper.assertTrue(segments.size() >= 3, "setup: the " + kit + " wall has only " + segments.size() + " segments");
			Walls.Piece middle = segments.get(segments.size() / 2);
			// the pieces whose spots overlap its own (segments sit a little over one another, so a neighbour on its spot can
			// make it read standing): taken away below to see the upgraded segment stand on its own
			List<Walls.Piece> over = pieces.stream().filter(o -> o != middle && columns(o.box(), middle.box())).toList();
			ResourceLocation up = BlueprintUpgrades.upgradeOf(middle.blueprint());
			helper.assertValueEqual(up, id(upgrade), "the " + kit + " segment's upgrade");
			helper.assertTrue(BlueprintLibrary.get(level, up).isPresent(), "setup: no blueprint " + up);
			BuildSiteManager manager = BuildSiteManager.get(level);
			manager.recordFinished(up, middle.placement(), UUID.randomUUID()); // the upgrade is finished over the segment

			helper.assertTrue(Walls.stands(level, middle), "the " + kit + " segment " + middle.index() + " upgraded to " + upgrade + " doesn't stand");
			helper.assertTrue(Walls.unbuilt(level, hall).isEmpty(), "with one segment upgraded to " + upgrade + " the wall has pieces to build: "
				+ Walls.unbuilt(level, hall).stream().map(p -> p.kind() + " " + p.index()).toList());
			helper.assertTrue(Walls.finished(level, hall), "the " + kit + " wall doesn't read finished with one segment upgraded to " + upgrade);
			helper.assertValueEqual(Walls.standing(level, hall), pieces.size(), "pieces standing with one upgraded to " + upgrade);
			helper.assertValueEqual(VillageRanks.score(level, hall, 0).buildings(), bare + 1,
				"buildings with one segment upgraded to " + upgrade + " (the upgrade isn't an extra building)");

			// its neighbours on its spot taken down: the upgraded segment still stands, by itself
			for (Walls.Piece o : over) {
				manager.forgetFinished(o.placement());
			}
			helper.assertTrue(Walls.stands(level, middle), "the segment " + middle.index() + " upgraded to " + upgrade + " doesn't stand on its own (with "
				+ over.size() + " overlapping neighbours gone)");
			helper.assertValueEqual(VillageRanks.score(level, hall, 0).buildings(), bare + 1, "buildings with the upgraded segment's neighbours gone");
			for (Walls.Piece o : over) {
				manager.recordFinished(o.blueprint(), o.placement(), UUID.randomUUID());
			}

			// the upgraded segment taken down (with the neighbours on its spot, which would stand in for it)
			manager.forgetFinished(middle.placement());
			for (Walls.Piece o : over) {
				manager.forgetFinished(o.placement());
			}
			helper.assertTrue(!Walls.stands(level, middle), "the upgraded segment " + middle.index() + " still stands once it's taken down");
			helper.assertTrue(!Walls.finished(level, hall), "the " + kit + " wall reads finished with its upgraded segment taken down");
			helper.assertTrue(Walls.unbuilt(level, hall).stream().anyMatch(p -> p.index() == middle.index()), "the taken-down segment " + middle.index()
				+ " isn't to build: " + Walls.unbuilt(level, hall).stream().map(p -> p.kind() + " " + p.index()).toList());

			// an upgrade finished in the courtyard, off the line, doesn't fill the gap
			BlueprintData.Placement off = new BlueprintData.Placement(middle.placement().dimension(), hall.offset(-2, 0, -2), Rotation.NONE, Mirror.NONE);
			manager.recordFinished(up, off, UUID.randomUUID());
			helper.assertTrue(!Walls.stands(level, middle), "an " + upgrade + " in the courtyard stands for the missing segment " + middle.index());
			helper.assertTrue(!Walls.finished(level, hall), "an " + upgrade + " in the courtyard makes the wall with a gap read finished");
			manager.forgetFinished(off);
		} finally {
			Walls.ENABLED = was;
		}
		helper.succeed();
	}

	/** Palisade: one palisade_2 in the middle of the wall, then taken down, then one off the line. */
	//$ gametest_ticks_batch AREA '40' '"qa1121dWallUpPalisade"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1121dWallUpPalisade")
	public void qaOneUpgradedPalisadeSegmentKeepsTheWallFinishedUntilItsGone(GameTestHelper helper) {
		oneUpgradedSegment(helper, "palisade", VillageRanks.Rank.VILLAGE, "palisade_2");
	}

	/** Stone: one stone_wall_2 in the middle of the wall, then taken down, then one off the line. */
	//$ gametest_ticks_batch AREA '40' '"qa1121dWallUpStone"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1121dWallUpStone")
	public void qaOneUpgradedStoneSegmentKeepsTheWallFinishedUntilItsGone(GameTestHelper helper) {
		oneUpgradedSegment(helper, "stone", VillageRanks.Rank.TOWN, "stone_wall_2");
	}

	/**
	 * The stone kit's towers and gates have upgrades too (wall_tower_2, gatehouse_2): each tower and gate upgraded on its
	 * spot still stands as that piece, the wall reads finished, and it counts as one building.
	 */
	//$ gametest_ticks_batch AREA '40' '"qa1121dWallUpTowers"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1121dWallUpTowers")
	public void qaUpgradedStoneTowersAndGatesStillStandAsTheWall(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean was = Walls.ENABLED;
		Walls.ENABLED = true;
		try {
			BlockPos hall0 = ground(helper, new BlockPos(14, 2, 14));
			int bare = VillageRanks.score(level, hall0, 0).buildings();
			BlockPos hall = builtWall(helper, "stone", VillageRanks.Rank.TOWN);
			BuildSiteManager manager = BuildSiteManager.get(level);
			int upgraded = 0;
			List<String> kinds = new ArrayList<>();
			for (Walls.Piece piece : Walls.pieces(level, hall)) {
				if (piece.kind() == Walls.Kind.SEGMENT) {
					continue;
				}
				ResourceLocation up = BlueprintUpgrades.upgradeOf(piece.blueprint());
				if (BlueprintLibrary.get(level, up).isEmpty()) {
					continue; // this piece has no upgrade
				}
				manager.recordFinished(up, piece.placement(), UUID.randomUUID());
				upgraded++;
				kinds.add(up.getPath());
			}
			helper.assertTrue(kinds.contains("wall_tower_2"), "setup: no stone tower upgraded to wall_tower_2: " + kinds);
			for (Walls.Piece piece : Walls.pieces(level, hall)) {
				helper.assertTrue(Walls.stands(level, piece), "the " + piece.kind() + " " + piece.index() + " (" + piece.blueprint().getPath()
					+ ") doesn't stand once the towers and gates are upgraded " + kinds);
			}
			helper.assertTrue(Walls.finished(level, hall), "the stone wall doesn't read finished with " + upgraded + " towers and gates upgraded");
			helper.assertValueEqual(VillageRanks.score(level, hall, 0).buildings(), bare + 1,
				"buildings with " + upgraded + " towers and gates upgraded (the wall counts once)");
		} finally {
			Walls.ENABLED = was;
		}
		helper.succeed();
	}

	/**
	 * An upgraded segment built in a style (the styled id of palisade_2 over a palisade segment) is still that segment:
	 * it stands, and the wall reads finished.
	 */
	//$ gametest_ticks_batch AREA '40' '"qa1121dWallUpStyled"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1121dWallUpStyled")
	public void qaAStyledUpgradedSegmentStillStands(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean was = Walls.ENABLED;
		Walls.ENABLED = true;
		try {
			BlockPos hall = builtWall(helper, "palisade", VillageRanks.Rank.VILLAGE);
			Walls.Piece segment = Walls.pieces(level, hall).stream().filter(p -> p.kind() == Walls.Kind.SEGMENT).findFirst().orElseThrow();
			ResourceLocation styled = BlueprintStyles.styled(id("palisade_2"), "spruce");
			helper.assertTrue(BlueprintLibrary.get(level, styled).isPresent(), "setup: no blueprint " + styled);
			BuildSiteManager.get(level).recordFinished(styled, segment.placement(), UUID.randomUUID());
			helper.assertTrue(Walls.isPiece(styled), styled + " isn't a wall piece");
			helper.assertTrue(Walls.stands(level, segment), "a segment upgraded to " + styled + " doesn't stand");
			helper.assertTrue(Walls.finished(level, hall), "the wall doesn't read finished with a segment upgraded to " + styled);
		} finally {
			Walls.ENABLED = was;
		}
		helper.succeed();
	}

	/**
	 * What counts as buildings: an ordinary building and its own upgrade (starter_cottage, starter_cottage_2) each count;
	 * every wall piece, upgraded or not, styled or not, counts once all together.
	 */
	//$ gametest_ticks_batch AREA '20' '"qa1121dWallUpCount"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qa1121dWallUpCount")
	public void qaOrdinaryBuildingsStillCountAndWallUpgradesDont(GameTestHelper helper) {
		ResourceLocation dim = helper.getLevel().dimension().location();
		int[] n = {0};
		java.util.function.Function<String, BuildSiteManager.Finished> at = path -> new BuildSiteManager.Finished(
			path.contains(":") ? ResourceLocation.parse(path) : id(path),
			new BlueprintData.Placement(dim, new BlockPos(1000 + 40 * n[0]++, 64, 1000), Rotation.NONE, Mirror.NONE), UUID.randomUUID());
		String styledPalisade2 = BlueprintStyles.styled(id("palisade_2"), "spruce").toString();

		helper.assertValueEqual(Walls.countsAsBuildings(List.of(at.apply("starter_cottage_2"))), 1, "one upgraded cottage");
		helper.assertValueEqual(Walls.countsAsBuildings(List.of(at.apply("starter_cottage"), at.apply("starter_cottage_2"))), 2,
			"a cottage and an upgraded cottage elsewhere");
		helper.assertValueEqual(Walls.countsAsBuildings(List.of(at.apply("palisade_2"))), 1, "a lone palisade_2 (the wall, once)");
		helper.assertValueEqual(Walls.countsAsBuildings(List.of(at.apply("stone_wall_2"), at.apply("stone_wall_2"), at.apply("stone_wall"))), 1,
			"three stone wall segments, two upgraded");
		helper.assertValueEqual(Walls.countsAsBuildings(List.of(at.apply("starter_cottage"), at.apply("palisade"), at.apply("palisade_2"),
			at.apply(styledPalisade2), at.apply("stone_wall_2"), at.apply("wall_tower_2"), at.apply("gatehouse_2"), at.apply("weavers_cottage_2"))), 3,
			"two cottages and a wall of upgraded pieces");
		helper.assertTrue(!Walls.isPiece(id("starter_cottage_2")), "starter_cottage_2 counts as a wall piece");
		helper.assertTrue(!Walls.isPiece(ResourceLocation.fromNamespaceAndPath("othermod", "palisade_2")), "another mod's palisade_2 counts as our wall piece");
		for (String up : List.of("palisade_2", "stone_wall_2", "wall_tower_2", "gatehouse_2")) {
			helper.assertTrue(Walls.isPiece(id(up)), up + " isn't a wall piece");
		}
		helper.succeed();
	}
}
