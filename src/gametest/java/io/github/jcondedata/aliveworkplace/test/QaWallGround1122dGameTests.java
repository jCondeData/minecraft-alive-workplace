package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.city.Walls;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntBinaryOperator;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (lane D, 2026-11-22), verifying B1457 from the wiki's wall spec (roads-and-walls.md: "Each piece sits at its own
 * ground height"): the wall's own pieces, finished or going up, are never the ground another piece is laid out on, so a
 * wall never stacks up on itself, while real ground (a hill, a step) still sets a piece's height; a piece laid out again
 * sits where it was built, in any finishing order, for both kits, across a save and reload, and when really built.
 */
public class QaWallGround1122dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(14, 2, 14);

	/** The area cleared to grass whose top is {@code top(x, z)} (helper y), old sites and buildings round it forgotten, a hall put down. */
	private static BlockPos ground(GameTestHelper helper, IntBinaryOperator top) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
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
				int t = top.applyAsInt(x, z);
				for (int y = 0; y < 29; y++) {
					helper.setBlock(new BlockPos(x, y, z), y < t ? Blocks.DIRT : y == t ? Blocks.GRASS_BLOCK : Blocks.AIR);
				}
			}
		}
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		return helper.absolutePos(HALL);
	}

	private static BlockPos ground(GameTestHelper helper) {
		return ground(helper, (x, z) -> 1);
	}

	private static VillageHallBlockEntity hall(GameTestHelper helper, BlockPos hall) {
		return (VillageHallBlockEntity) helper.getLevel().getBlockEntity(hall);
	}

	private static BlockPos p(int dx, int dz) {
		return new BlockPos(dx, 0, dz);
	}

	/** The absolute y a piece over the grass top {@code t} (helper y) starts at. */
	private static int onTop(GameTestHelper helper, int t) {
		return helper.absolutePos(new BlockPos(0, t + 1, 0)).getY();
	}

	/** The helper x, z of a piece's middle column (what its height is read at). */
	private static int[] middle(GameTestHelper helper, Walls.Piece piece) {
		BlockPos a = helper.absolutePos(BlockPos.ZERO);
		return new int[] {(piece.box().minX() + piece.box().maxX()) / 2 - a.getX(), (piece.box().minZ() + piece.box().maxZ()) / 2 - a.getZ()};
	}

	private static void insideArea(GameTestHelper helper, List<Walls.Piece> pieces) {
		BlockPos a = helper.absolutePos(BlockPos.ZERO);
		for (Walls.Piece p : pieces) {
			BoundingBox b = p.box();
			helper.assertTrue(b.minX() >= a.getX() && b.maxX() < a.getX() + 30 && b.minZ() >= a.getZ() && b.maxZ() < a.getZ() + 30
				&& b.maxY() < a.getY() + 29, "setup: piece " + p.index() + " " + p.kind() + " " + b + " is outside the test area");
		}
	}

	/** Builds {@code piece} as far as the ground goes: its box's air filled solid, recorded finished. */
	private static void finish(ServerLevel level, Walls.Piece piece) {
		BoundingBox b = piece.box();
		for (BlockPos at : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())) {
			if (level.getBlockState(at).isAir()) {
				level.setBlockAndUpdate(at, Blocks.SPRUCE_LOG.defaultBlockState());
			}
		}
		BuildSiteManager.get(level).recordFinished(piece.blueprint(), piece.placement(), UUID.randomUUID());
	}

	private static void assertSame(GameTestHelper helper, List<Walls.Piece> before, List<Walls.Piece> now, String when) {
		helper.assertTrue(now.size() == before.size(), when + ": the line has " + now.size() + " pieces, not " + before.size());
		for (int i = 0; i < before.size(); i++) {
			helper.assertTrue(now.get(i).placement().equals(before.get(i).placement()) && now.get(i).kind() == before.get(i).kind(),
				when + ": piece " + i + " " + now.get(i).kind() + " laid out at " + now.get(i).box() + ", not where it was at " + before.get(i).box());
		}
	}

	private static void removeSites(ServerLevel level, List<BuildSite> sites) {
		for (BuildSite s : sites) {
			BuildSiteManager.get(level).remove(s.id());
		}
	}

	/**
	 * A palisade line over ground that steps up and down (grass top 1, then 4, then 2 along the line): every piece sits on
	 * the real ground at its middle, none floating or buried. Each piece built in turn from the west, the line laid out
	 * again after every one: nothing moves, nothing climbs onto the neighbor built beside it.
	 */
	//$ gametest_ticks_batch AREA '40' '"qa1122dWallSteps"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1122dWallSteps")
	public void qaEachPieceOnSteppedGroundSitsOnItsOwnGround(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean was = Walls.ENABLED;
		Walls.ENABLED = true;
		try {
			IntBinaryOperator top = (x, z) -> z > 9 ? 1 : x < 10 ? 1 : x < 19 ? 4 : 2;
			BlockPos hall = ground(helper, top);
			VillageHallBlockEntity entity = hall(helper, hall);
			entity.setRank(VillageRanks.Rank.VILLAGE);
			entity.setPlan(CityPlan.EMPTY.withWall(new CityPlan.Wall(List.of(p(-9, -9), p(9, -9)), false).approvedWith("palisade")));
			List<Walls.Piece> pieces = Walls.pieces(level, hall);
			helper.assertTrue(pieces.size() >= 4, "setup: the line has " + pieces.size() + " pieces");
			insideArea(helper, pieces);
			java.util.Set<Integer> heights = new java.util.HashSet<>();
			for (Walls.Piece piece : pieces) {
				int[] m = middle(helper, piece);
				int want = onTop(helper, top.applyAsInt(m[0], m[1]));
				heights.add(want);
				helper.assertTrue(piece.box().minY() == want, "piece " + piece.index() + " " + piece.kind() + " with its middle at x " + m[0]
					+ " laid out at y " + piece.box().minY() + ", not on its ground at " + want);
			}
			helper.assertTrue(heights.size() >= 2, "setup: every piece's middle is on the same step: " + heights);
			for (Walls.Piece piece : pieces) {
				finish(level, piece);
				assertSame(helper, pieces, Walls.pieces(level, hall), "with piece " + piece.index() + " built");
			}
			for (Walls.Piece piece : Walls.pieces(level, hall)) {
				helper.assertTrue(Walls.stands(level, piece), "piece " + piece.index() + " " + piece.kind() + " doesn't stand once all are built");
			}
			helper.assertTrue(Walls.unbuilt(level, hall).isEmpty() && Walls.finished(level, hall), "the built line still has "
				+ Walls.unbuilt(level, hall).size() + " pieces to build");
		} finally {
			Walls.ENABLED = was;
		}
		helper.succeed();
	}

	/**
	 * The palisade test's line (towers, a gate on a road, overlapping segments) on flat ground, its pieces finished from
	 * the last to the first: after every one, every piece built or not is laid out again exactly where it was at first.
	 */
	//$ gametest_ticks_batch AREA '40' '"qa1122dWallReverse"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1122dWallReverse")
	public void qaPiecesFinishedLastToFirstAreEachLaidOutWhereTheyWere(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean was = Walls.ENABLED;
		Walls.ENABLED = true;
		try {
			BlockPos hall = ground(helper);
			VillageHallBlockEntity entity = hall(helper, hall);
			entity.setRank(VillageRanks.Rank.VILLAGE);
			CityPlan.Road road = new CityPlan.Road(List.of(p(-4, -3), p(-4, -13)), CityPlan.Road.STREET, "", false);
			entity.setPlan(CityPlan.EMPTY.addRoad(road)
				.withWall(new CityPlan.Wall(List.of(p(-9, -9), p(9, -9)), false).approvedWith("palisade")));
			List<Walls.Piece> pieces = Walls.pieces(level, hall);
			helper.assertTrue(pieces.stream().anyMatch(p -> p.kind() == Walls.Kind.GATE)
				&& pieces.stream().filter(p -> p.kind() == Walls.Kind.SEGMENT).count() >= 2, "setup: the line is " + pieces.stream().map(Walls.Piece::kind).toList());
			int floor = onTop(helper, 1);
			for (Walls.Piece piece : pieces) {
				helper.assertTrue(piece.box().minY() == floor, "setup: piece " + piece.index() + " at y " + piece.box().minY() + ", not " + floor);
			}
			for (int i = pieces.size() - 1; i >= 0; i--) {
				finish(level, pieces.get(i));
				List<Walls.Piece> again = Walls.pieces(level, hall);
				assertSame(helper, pieces, again, "with pieces " + i + ".." + (pieces.size() - 1) + " built");
				for (Walls.Piece p : again) {
					helper.assertTrue(Walls.stands(level, p) == (p.index() >= i), "piece " + p.index() + " " + p.kind()
						+ (p.index() >= i ? " (built) doesn't stand" : " (unbuilt) stands") + " with pieces from " + i + " built");
				}
			}
		} finally {
			Walls.ENABLED = was;
		}
		helper.succeed();
	}

	/**
	 * A Town's stone wall (Stone Wall, Wall Tower, Gatehouse): the odd pieces finished first, then the even ones going up
	 * (sites open, their lower blocks in), then those finished: every time, every piece is laid out again where it was, on
	 * the ground, and no wall block stands above the tallest piece's top.
	 */
	//$ gametest_ticks_batch AREA '40' '"qa1122dStoneWall"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1122dStoneWall")
	public void qaTheStoneWallAndItsTowersNeverStackOnEachOther(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean was = Walls.ENABLED;
		Walls.ENABLED = true;
		List<BuildSite> opened = new ArrayList<>();
		try {
			BlockPos hall = ground(helper);
			VillageHallBlockEntity entity = hall(helper, hall);
			entity.setRank(VillageRanks.Rank.TOWN);
			helper.assertTrue(VillageRanks.of(level, hall) == VillageRanks.Rank.TOWN, "setup: the hall is a " + VillageRanks.of(level, hall));
			CityPlan.Road road = new CityPlan.Road(List.of(p(0, -3), p(0, -13)), CityPlan.Road.STREET, "", false);
			entity.setPlan(CityPlan.EMPTY.addRoad(road)
				.withWall(new CityPlan.Wall(List.of(p(-7, -7), p(7, -7)), false).approvedWith("stone")));
			List<Walls.Piece> pieces = Walls.pieces(level, hall);
			helper.assertTrue(pieces.size() >= 3, "setup: the stone line has " + pieces.size() + " pieces");
			helper.assertTrue(pieces.stream().allMatch(p -> List.of("stone_wall", "wall_tower", "gatehouse").contains(p.blueprint().getPath())),
				"setup: the Town's line isn't stone: " + pieces.stream().map(p -> p.blueprint().getPath()).toList());
			helper.assertTrue(pieces.stream().anyMatch(p -> p.kind() == Walls.Kind.TOWER), "setup: no Wall Tower in " + pieces.stream().map(Walls.Piece::kind).toList());
			insideArea(helper, pieces);
			int floor = onTop(helper, 1);
			int tallest = Integer.MIN_VALUE;
			for (Walls.Piece piece : pieces) {
				helper.assertTrue(piece.box().minY() == floor, "piece " + piece.index() + " " + piece.blueprint().getPath() + " laid out at y "
					+ piece.box().minY() + ", not on the ground at " + floor);
				tallest = Math.max(tallest, piece.box().maxY());
			}
			for (Walls.Piece piece : pieces) {
				if (piece.index() % 2 == 1) {
					finish(level, piece);
					assertSame(helper, pieces, Walls.pieces(level, hall), "stone piece " + piece.index() + " built");
				}
			}
			BuildSiteManager manager = BuildSiteManager.get(level);
			for (Walls.Piece piece : pieces) {
				if (piece.index() % 2 == 0) {
					opened.add(manager.create(UUID.randomUUID(), "test", piece.blueprint(), piece.placement()));
					BoundingBox b = piece.box();
					for (BlockPos at : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.minY() + 1, b.maxZ())) {
						if (level.getBlockState(at).isAir()) {
							level.setBlockAndUpdate(at, Blocks.STONE_BRICKS.defaultBlockState());
						}
					}
					assertSame(helper, pieces, Walls.pieces(level, hall), "stone piece " + piece.index() + " going up");
				}
			}
			for (Walls.Piece piece : pieces) {
				if (piece.index() % 2 == 0) {
					finish(level, piece); // the builder's last block: the piece is recorded finished and its site closes
					BuildSite site = opened.stream().filter(s -> s.placement().equals(piece.placement())).findFirst().orElseThrow();
					removeSites(level, List.of(site));
					opened.remove(site);
					assertSame(helper, pieces, Walls.pieces(level, hall), "stone piece " + piece.index() + " built after going up");
				}
			}
			helper.assertTrue(Walls.finished(level, hall), "the stone line isn't finished: " + Walls.unbuilt(level, hall).size() + " to build");
			BlockPos a = helper.absolutePos(BlockPos.ZERO);
			for (Walls.Piece piece : pieces) {
				BoundingBox b = piece.box();
				for (BlockPos at : BlockPos.betweenClosed(b.minX(), tallest + 1, b.minZ(), b.maxX(), a.getY() + 28, b.maxZ())) {
					helper.assertTrue(level.getBlockState(at).isAir(), "a block above the wall's top (y " + tallest + ") at " + at.toShortString());
				}
			}
		} finally {
			removeSites(level, opened);
			Walls.ENABLED = was;
		}
		helper.succeed();
	}

	/**
	 * Only the wall's own pieces are skipped: a palisade on a hill top (grass 5 up under the whole line) is laid out on
	 * the hill, and stays there once its neighbors are built on the hill; a natural knoll under one piece's middle still
	 * lifts that piece; a player's pillar and a finished house beside a piece (not under its middle) change nothing; a
	 * finished house that is not a wall piece, under a piece's middle, is that piece's ground, never skipped as if it
	 * were the wall.
	 */
	//$ gametest_ticks_batch AREA '40' '"qa1122dWallHill"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1122dWallHill")
	public void qaARealHillStillLiftsAPieceButTheWallNeverDoes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean was = Walls.ENABLED;
		Walls.ENABLED = true;
		try {
			IntBinaryOperator top = (x, z) -> z <= 9 ? 5 : 1;
			BlockPos hall = ground(helper, top);
			VillageHallBlockEntity entity = hall(helper, hall);
			entity.setRank(VillageRanks.Rank.VILLAGE);
			entity.setPlan(CityPlan.EMPTY.withWall(new CityPlan.Wall(List.of(p(-9, -9), p(9, -9)), false).approvedWith("palisade")));
			List<Walls.Piece> pieces = Walls.pieces(level, hall);
			helper.assertTrue(pieces.size() >= 4, "setup: the line has " + pieces.size() + " pieces");
			insideArea(helper, pieces);
			int hill = onTop(helper, 5);
			for (Walls.Piece piece : pieces) {
				helper.assertTrue(piece.box().minY() == hill, "piece " + piece.index() + " " + piece.kind() + " on the hill top laid out at y "
					+ piece.box().minY() + ", not on the hill at " + hill);
			}
			// all but the last built on the hill: the last is still laid out on the hill, not on them nor down in it
			for (int i = 0; i < pieces.size() - 1; i++) {
				finish(level, pieces.get(i));
			}
			assertSame(helper, pieces, Walls.pieces(level, hall), "all but the last built on the hill");
			Walls.Piece last = pieces.get(pieces.size() - 1);
			// a player's pillar and a finished house beside the last piece, outside its middle column: nothing changes
			int[] m = middle(helper, last);
			int ax = helper.absolutePos(BlockPos.ZERO).getX();
			int bx = last.box().maxX() - ax + 1 <= 29 ? last.box().maxX() - ax + 1 : last.box().minX() - ax - 1;
			BlockPos beside = new BlockPos(bx, 6, m[1]);
			for (int y = 6; y < 12; y++) {
				helper.setBlock(beside.atY(y), Blocks.COBBLESTONE);
			}
			BuildSiteManager manager = BuildSiteManager.get(level);
			Walls.Piece segment = pieces.stream().filter(p -> p.kind() == Walls.Kind.SEGMENT).findFirst().orElseThrow();
			assertSame(helper, pieces, Walls.pieces(level, hall), "with a player's pillar beside the last piece");
			// the knoll: forget every built piece, put a natural knoll 3 high under the first segment's middle
			for (int i = 0; i < pieces.size() - 1; i++) {
				manager.forgetFinished(pieces.get(i).placement());
				BoundingBox b = pieces.get(i).box();
				for (BlockPos at : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())) {
					level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
				}
			}
			int[] sm = middle(helper, segment);
			for (int y = 6; y <= 8; y++) {
				helper.setBlock(new BlockPos(sm[0], y, sm[1]), Blocks.STONE);
			}
			Walls.Piece lifted = Walls.pieces(level, hall).get(segment.index());
			helper.assertTrue(lifted.box().minY() == onTop(helper, 8), "a segment over a natural knoll 3 high laid out at y "
				+ lifted.box().minY() + ", not on the knoll at " + onTop(helper, 8));
			for (int y = 6; y <= 8; y++) {
				helper.setBlock(new BlockPos(sm[0], y, sm[1]), Blocks.AIR);
			}
			// a finished house (not a wall piece) whose box holds the segment's middle: its roof is ground, never skipped
			BlockPos houseAt = helper.absolutePos(new BlockPos(sm[0] - 1, 6, sm[1] - 1));
			for (BlockPos at : BlockPos.betweenClosed(houseAt, houseAt.offset(2, 3, 2))) {
				level.setBlockAndUpdate(at, Blocks.OAK_PLANKS.defaultBlockState());
			}
			manager.recordFinished(io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints.SIGNPOST.id(),
				new io.github.jcondedata.aliveworkplace.blueprint.BlueprintData.Placement(io.github.jcondedata.aliveworkplace.mc.Ids.of(level.dimension()),
					houseAt, net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE), UUID.randomUUID());
			Walls.Piece onHouse = Walls.pieces(level, hall).get(segment.index());
			helper.assertTrue(onHouse.box().minY() == houseAt.getY() + 4, "a segment over a finished house's roof (not a wall piece) laid out at y "
				+ onHouse.box().minY() + ", not on the roof at " + (houseAt.getY() + 4));
		} finally {
			Walls.ENABLED = was;
		}
		helper.succeed();
	}

	/**
	 * Half the line built, then the buildings' record and the hall saved and loaded back (as on a server restart): the
	 * line is laid out again where it was, and the rest, built after the reload, too.
	 */
	//$ gametest_ticks_batch AREA '40' '"qa1122dWallReload"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1122dWallReload")
	public void qaASaveAndReloadBetweenSegmentsKeepsEveryPieceWhereItWas(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean was = Walls.ENABLED;
		Walls.ENABLED = true;
		try {
			BlockPos hall = ground(helper);
			VillageHallBlockEntity entity = hall(helper, hall);
			entity.setRank(VillageRanks.Rank.VILLAGE);
			CityPlan.Road road = new CityPlan.Road(List.of(p(-4, -3), p(-4, -13)), CityPlan.Road.STREET, "", false);
			entity.setPlan(CityPlan.EMPTY.addRoad(road)
				.withWall(new CityPlan.Wall(List.of(p(-9, -9), p(9, -9)), false).approvedWith("palisade")));
			List<Walls.Piece> pieces = Walls.pieces(level, hall);
			helper.assertTrue(pieces.size() >= 4, "setup: the line has " + pieces.size() + " pieces");
			for (int i = 1; i < pieces.size(); i += 2) {
				finish(level, pieces.get(i));
			}
			BuildSiteManager manager = BuildSiteManager.get(level);
			// the record round trip: what was finished here comes back from the saved tag, and nothing else
			CompoundTag saved = manager.save(new CompoundTag(), level.registryAccess());
			BuildSiteManager copy = BuildSiteManager.load(saved, level.registryAccess());
			List<BuildSiteManager.Finished> mine = new ArrayList<>();
			for (int i = 1; i < pieces.size(); i += 2) {
				Walls.Piece piece = pieces.get(i);
				BuildSiteManager.Finished back = copy.finishedIn(level).stream().filter(f -> f.placement().equals(piece.placement())).findFirst().orElse(null);
				helper.assertTrue(back != null && back.structure().equals(piece.blueprint()), "built piece " + i + " lost over the reload");
				mine.add(back);
			}
			for (BuildSiteManager.Finished f : mine) {
				manager.forgetFinished(f.placement());
			}
			for (BuildSiteManager.Finished f : mine) {
				manager.recordFinished(f.structure(), f.placement(), f.owner());
			}
			CompoundTag tag = entity.saveWithFullMetadata(level.registryAccess());
			VillageHallBlockEntity loaded = (VillageHallBlockEntity) net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
				hall, level.getBlockState(hall), tag, level.registryAccess());
			helper.assertTrue(loaded != null && loaded.plan().equals(entity.plan()), "the plan changed over the reload");
			entity.setPlan(loaded.plan());
			assertSame(helper, pieces, Walls.pieces(level, hall), "after the reload, half the line built");
			for (int i = 0; i < pieces.size(); i += 2) {
				finish(level, pieces.get(i));
				assertSame(helper, pieces, Walls.pieces(level, hall), "after the reload, piece " + i + " built too");
			}
			helper.assertTrue(Walls.finished(level, hall), "the line isn't finished after the reload: " + Walls.unbuilt(level, hall).size() + " to build");
		} finally {
			Walls.ENABLED = was;
		}
		helper.succeed();
	}

	/**
	 * The Steward's palisade (tower, segments, tower) really built by one builder from a chest holding exactly what the
	 * line's drawings are made of: every piece stands on the ground, nothing of the wall above its pieces' tops, and the
	 * chest isn't emptied of any wall material the line didn't use (no piece built twice or stacked).
	 */
	//$ gametest_ticks_batch AREA '12000' '"qa1122dWallBuilt"'
	@GameTest(template = AREA, timeoutTicks = 12000, batch = "qa1122dWallBuilt")
	public void qaTheStewardsPalisadeIsBuiltOnceOnTheGroundFromExactlyItsMaterials(GameTestHelper helper) {
		helper.setDayTime(1000);
		ServerLevel level = helper.getLevel();
		BlockPos hall = ground(helper);
		VillageHallBlockEntity entity = hall(helper, hall);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		entity.setPlan(CityPlan.EMPTY.withWall(new CityPlan.Wall(List.of(p(-9, -9), p(9, -9)), false).approvedWith("palisade")));
		boolean was = Walls.ENABLED;
		Walls.ENABLED = true;
		List<Walls.Piece> pieces = Walls.pieces(level, hall);
		helper.assertTrue(pieces.size() >= 3 && pieces.stream().anyMatch(p -> p.kind() == Walls.Kind.SEGMENT), "setup: the line is "
			+ pieces.stream().map(Walls.Piece::kind).toList());
		insideArea(helper, pieces);
		Map<Item, Integer> need = new LinkedHashMap<>();
		for (Walls.Piece piece : pieces) {
			for (Blueprint.Entry e : BlueprintLibrary.get(level, piece.blueprint()).orElseThrow().blocks()) {
				Item item = e.state().getBlock().asItem();
				if (!e.state().isAir() && item != Items.AIR) {
					need.merge(item, 1, Integer::sum);
				}
			}
		}
		BlockPos table = HALL.offset(-4, 0, 0);
		helper.setBlock(table, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(table.west(), Blocks.CHEST);
		Container chest = helper.getBlockEntity(table.west());
		chest.clearContent();
		int slot = 0;
		for (var n : need.entrySet()) {
			for (int left = n.getValue(); left > 0 && slot < chest.getContainerSize(); left -= 64) {
				chest.setItem(slot++, new ItemStack(n.getKey(), Math.min(64, left)));
			}
		}
		chest.setItem(slot++, new ItemStack(Items.DIRT, 64));
		helper.assertTrue(slot <= chest.getContainerSize(), "setup: the line's materials fill more than a chest");
		Villager builder = helper.spawn(EntityType.VILLAGER, table.east());
		Builders.employ(level, builder, helper.absolutePos(table));
		Villager steward = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, HALL.north(2)));
		ItemStack plan = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(level, owner, plan, hall);
		helper.assertTrue(Stewards.appoint(owner, steward, plan) == InteractionResult.SUCCESS, "setup: Steward not appointed");
		int floor = onTop(helper, 1);
		int tallest = pieces.stream().mapToInt(p -> p.box().maxY()).max().orElseThrow();
		helper.onEachTick(() -> {
			if (helper.getTick() % 200 == 0 && level.getDayTime() % 24000 > 11000) {
				level.setDayTime(1000);
			}
		});
		helper.succeedWhen(() -> {
			List<Walls.Piece> now = Walls.pieces(level, hall);
			long standing = now.stream().filter(p -> Walls.stands(level, p)).count();
			helper.assertTrue(standing == now.size(), standing + " of " + now.size() + " palisade pieces stand; open: "
				+ Walls.openSites(level, hall).stream().map(s -> s.structure().getPath() + " at y " + s.placement().origin().getY()
					+ " missing " + s.missing()).toList());
			Walls.ENABLED = was;
			for (Walls.Piece p : now) {
				helper.assertTrue(p.box().minY() == floor, "built piece " + p.index() + " " + p.kind() + " stands at y " + p.box().minY()
					+ ", not on the ground at " + floor);
			}
			for (BuildSiteManager.Finished f : BuildSiteManager.get(level).finishedIn(level)) {
				if (Walls.isPiece(f.structure()) && now.stream().anyMatch(p -> p.box().isInside(f.placement().origin().atY(p.box().minY())))) {
					helper.assertTrue(f.placement().origin().getY() == floor, "a " + f.structure().getPath() + " was finished at y "
						+ f.placement().origin().getY() + ", over the ground at " + floor);
				}
			}
			for (Walls.Piece p : now) {
				BoundingBox b = p.box();
				for (BlockPos at : BlockPos.betweenClosed(b.minX(), tallest + 1, b.minZ(), b.maxX(), tallest + 12, b.maxZ())) {
					helper.assertTrue(level.getBlockState(at).isAir(), "a wall block over the palisade's top (y " + tallest + ") at "
						+ at.toShortString() + ": " + level.getBlockState(at).getBlock());
				}
			}
			helper.assertTrue(Walls.openSites(level, hall).isEmpty(), "a wall site is still open once the whole line stands");
		});
	}
}
