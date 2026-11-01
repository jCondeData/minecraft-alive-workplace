package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.city.Walls;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderPlans;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (lane-b-1101-1238), from B752 and B753's specs: the Steward opens no wall piece through a finished building, a
 * finished Wonder or a Wonder laid out and waiting for its goods (such a piece is skipped and the next one on open ground
 * opens), and his roads route round both kinds of Wonder. Beyond the fix's own tests: a building or Wonder covering only
 * the piece's edge column, one right next to the piece on either side (which must not hold it back), a Wonder laid out
 * after the ground was last read and one forgotten again, a save and reload of the world data in between, and a Steward's
 * own street between two finished buildings with a Wonder laid out between them, against its control.
 */
public class QaStewardWallsRoads1101bGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(14, 2, 14);
	private static final ResourceLocation FOLLY = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");
	private static final ResourceLocation WELL = AliveWorkplace.id("well");
	/** The Test Folly's stage I footprint, 9 by 9. */
	private static final int FOLLY_SIZE = 9;

	private record Village(BlockPos hall, VillageHallBlockEntity entity, ServerPlayer owner, Villager builder, Villager steward) {
	}

	// ------------------------------------------------------------------------------------------------- the village

	/** Flat grass, old sites, finished builds and Wonders round the area forgotten, the lid taken off, a hall put down. */
	private static BlockPos ground(GameTestHelper helper, BlockPos hallAt) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		Leftovers.stewardSites(helper);
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
		WonderManager wonders = WonderManager.get(level);
		for (WonderSite w : List.copyOf(wonders.sites())) {
			if (w.placement().isPresent() && near.isInside(w.placement().get().origin())) {
				wonders.remove(w.id());
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
		BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 2, 0)), helper.absolutePos(new BlockPos(29, 29, 29))).forEach(at -> {
			if (level.getBlockState(at).is(Blocks.BARRIER)) {
				level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
			}
		});
		helper.setBlock(hallAt, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(hallAt);
		Leftovers.wonderGround(helper, hall);
		return hall;
	}

	/** A hall with an owner and an appointed Steward; with {@code wall}, one builder with a stocked bench and an approved palisade. */
	private static Village village(GameTestHelper helper, BlockPos hallAt, boolean wall) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = ground(helper, hallAt);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		Villager builder = null;
		if (wall) {
			BlockPos table = hallAt.offset(-4, 0, 0);
			helper.setBlock(table, ModBlocks.BLUEPRINT_TABLE);
			helper.setBlock(table.west(), Blocks.CHEST);
			Container chest = helper.getBlockEntity(table.west());
			chest.setItem(0, new ItemStack(Items.SPRUCE_LOG, 64));
			chest.setItem(1, new ItemStack(Items.STRIPPED_SPRUCE_LOG, 64));
			chest.setItem(2, new ItemStack(Items.SPRUCE_FENCE, 64));
			chest.setItem(3, new ItemStack(Items.SPRUCE_PLANKS, 64));
			chest.setItem(4, new ItemStack(Items.LADDER, 16));
			chest.setItem(5, new ItemStack(Items.DIRT, 64));
			builder = helper.spawn(EntityType.VILLAGER, table.east());
			Builders.employ(level, builder, helper.absolutePos(table));
		}
		Villager steward = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, hallAt.north(2)));
		ItemStack plan = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(level, owner, plan, hall);
		helper.assertTrue(Stewards.appoint(owner, steward, plan) == InteractionResult.SUCCESS, "setup: the Steward wasn't appointed");
		if (wall) {
			int half = 10;
			CityPlan.Wall line = new CityPlan.Wall(List.of(new BlockPos(-half, 0, -half), new BlockPos(half, 0, -half),
				new BlockPos(half, 0, half), new BlockPos(-half, 0, half)), true).approvedWith("palisade");
			entity.setPlan(entity.plan().withWall(line));
		}
		return new Village(hall, entity, owner, builder, steward);
	}

	private static boolean meet(BoundingBox one, BoundingBox two) {
		return one.minX() <= two.maxX() && one.maxX() >= two.minX() && one.minZ() <= two.maxZ() && one.maxZ() >= two.minZ();
	}

	private static Walls.Piece first(GameTestHelper helper, Village v) {
		List<Walls.Piece> unbuilt = Walls.unbuilt(helper.getLevel(), v.hall());
		helper.assertTrue(unbuilt.size() >= 4, "setup: the approved wall has only " + unbuilt.size() + " pieces to build");
		return unbuilt.get(0);
	}

	private static List<BuildSite> round(GameTestHelper helper, Village v) {
		Walls.round(helper.getLevel(), v.hall(), v.entity());
		return Walls.openSites(helper.getLevel(), v.hall());
	}

	private static BoundingBox box(GameTestHelper helper, BuildSite site) {
		return BlueprintLibrary.get(helper.getLevel(), site.structure()).map(b -> BlueprintOutline.bounds(site.placement(), b.size())).orElseThrow();
	}

	private static void withWalls(Runnable body) {
		boolean was = Walls.ENABLED;
		Walls.ENABLED = true;
		try {
			body.run();
		} finally {
			Walls.ENABLED = was;
		}
	}

	private static Vec3i wellSize(GameTestHelper helper) {
		return BlueprintLibrary.get(helper.getLevel(), WELL).orElseThrow(() -> new AssertionError("no Well blueprint")).size();
	}

	/** A Well recorded as finished by a builder with its origin at {@code (x, hall y, z)}; its footprint. */
	private static BoundingBox wellAt(GameTestHelper helper, Village v, int x, int z) {
		ServerLevel level = helper.getLevel();
		BlueprintData.Placement at = new BlueprintData.Placement(Ids.of(level.dimension()), new BlockPos(x, v.hall().getY(), z), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(WELL, at, UUID.randomUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(at));
		return BlueprintOutline.bounds(at, wellSize(helper));
	}

	/** The Test Folly's stage I laid out with its corner at {@code origin} for {@code hall}; finished when asked. */
	private static WonderSite folly(GameTestHelper helper, BlockPos hall, BlockPos origin, boolean finished) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(Wonders.loaded().get(FOLLY) != null, "the test folly isn't loaded: " + Wonders.problems());
		WonderManager manager = WonderManager.get(level);
		WonderSite site = manager.begin(FOLLY, hall, null, "", 1);
		UUID id = site.id();
		// looked up afresh: a test that reloads the world data swaps the manager
		Leftovers.after(helper, () -> WonderManager.get(level).remove(id));
		manager.place(id, new BlueprintData.Placement(Ids.of(level.dimension()), origin, Rotation.NONE, Mirror.NONE), origin.offset(-1, 0, -1));
		if (finished) {
			manager.finish(id, 2);
		}
		WonderSite now = manager.site(id).orElseThrow();
		helper.assertTrue(finished ? now.done() : (now.goingUp() && !now.started() && now.parts().isEmpty()),
			"setup: the Wonder isn't " + (finished ? "finished" : "laid out and waiting for its goods"));
		return now;
	}

	private static BoundingBox follyBox(BlockPos origin) {
		return new BoundingBox(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + FOLLY_SIZE - 1, origin.getY() + 6, origin.getZ() + FOLLY_SIZE - 1);
	}

	/** The village's Wonders and build sites saved and loaded again, as when the server restarts. */
	private static void reloadWorldData(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CompoundTag wonders = WonderManager.get(level).save(new CompoundTag(), level.registryAccess());
		level.getDataStorage().set(WonderManager.NAME, WonderManager.load(wonders, level.registryAccess()));
		CompoundTag sites = BuildSiteManager.get(level).save(new CompoundTag(), level.registryAccess());
		level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(sites, level.registryAccess()));
	}

	private static void noSiteMeets(GameTestHelper helper, List<BuildSite> open, BoundingBox held, String what) {
		for (BuildSite site : open) {
			helper.assertTrue(!meet(box(helper, site), held), "the Steward opened a " + site.structure() + " wall site at " + box(helper, site)
				+ " through " + what + " at " + held + "; expected that piece skipped");
		}
		helper.assertTrue(open.size() == 1, open.size() + " wall sites opened beside " + what + ", not the 1 on open ground");
	}

	private static void firstOpens(GameTestHelper helper, List<BuildSite> open, Walls.Piece piece, String what) {
		helper.assertTrue(open.size() == 1, open.size() + " wall sites opened with " + what + ", not 1");
		helper.assertTrue(open.get(0).placement().equals(piece.placement()), "with " + what + " the Steward skipped the first wall piece "
			+ piece.box() + " and opened " + box(helper, open.get(0)) + " instead; a building beside the line mustn't hold a piece back");
	}

	// ------------------------------------------------------------------------------------------------- B752 walls

	/** A finished Well whose footprint covers only the first piece's west edge column: that piece is still skipped. */
	//$ gametest_ticks_batch AREA '60' '"qaWallsEdgeWell1101b"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaWallsEdgeWell1101b")
	public void aWellOnTheEdgeColumnOfAPieceHoldsIt(GameTestHelper helper) {
		Village v = village(helper, HALL, true);
		helper.runAfterDelay(2, () -> withWalls(() -> {
			Walls.Piece piece = first(helper, v);
			Vec3i w = wellSize(helper);
			BoundingBox well = wellAt(helper, v, piece.box().minX() - (w.getX() - 1), piece.box().minZ());
			helper.assertTrue(well.maxX() == piece.box().minX(), "setup: the Well " + well + " doesn't end on the piece's edge " + piece.box());
			noSiteMeets(helper, round(helper, v), well, "a finished Well covering the piece's edge column");
			helper.succeed();
		}));
	}

	/** A finished Well ending one block west of the first piece (not on it): the first piece opens as on open ground. */
	//$ gametest_ticks_batch AREA '60' '"qaWallsWellWest1101b"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaWallsWellWest1101b")
	public void aWellJustWestOfAPieceDoesntHoldIt(GameTestHelper helper) {
		Village v = village(helper, HALL, true);
		helper.runAfterDelay(2, () -> withWalls(() -> {
			Walls.Piece piece = first(helper, v);
			Vec3i w = wellSize(helper);
			BoundingBox well = wellAt(helper, v, piece.box().minX() - w.getX(), piece.box().minZ());
			helper.assertTrue(well.maxX() == piece.box().minX() - 1, "setup: the Well " + well + " isn't just west of " + piece.box());
			firstOpens(helper, round(helper, v), piece, "a finished Well just west of it");
			helper.succeed();
		}));
	}

	/** A finished Well starting one block east of the first piece: the first piece opens. */
	//$ gametest_ticks_batch AREA '60' '"qaWallsWellEast1101b"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaWallsWellEast1101b")
	public void aWellJustEastOfAPieceDoesntHoldIt(GameTestHelper helper) {
		Village v = village(helper, HALL, true);
		helper.runAfterDelay(2, () -> withWalls(() -> {
			Walls.Piece piece = first(helper, v);
			BoundingBox well = wellAt(helper, v, piece.box().maxX() + 1, piece.box().minZ());
			firstOpens(helper, round(helper, v), piece, "a finished Well just east of it");
			helper.succeed();
		}));
	}

	/** A finished Well one block north and one block south of the first piece (outside and inside the ring): it opens. */
	//$ gametest_ticks_batch AREA '60' '"qaWallsWellNorthSouth1101b"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaWallsWellNorthSouth1101b")
	public void wellsJustNorthAndSouthOfAPieceDontHoldIt(GameTestHelper helper) {
		Village v = village(helper, HALL, true);
		helper.runAfterDelay(2, () -> withWalls(() -> {
			Walls.Piece piece = first(helper, v);
			Vec3i w = wellSize(helper);
			BoundingBox north = wellAt(helper, v, piece.box().minX(), piece.box().minZ() - w.getZ());
			BoundingBox south = wellAt(helper, v, piece.box().minX(), piece.box().maxZ() + 1);
			helper.assertTrue(!meet(north, piece.box()) && !meet(south, piece.box()), "setup: a Well is on the piece " + piece.box());
			firstOpens(helper, round(helper, v), piece, "finished Wells just north and south of it");
			helper.succeed();
		}));
	}

	/** A waiting Test Folly covering only the first piece's east edge column: the piece is skipped. */
	//$ gametest_ticks_batch AREA '60' '"qaWallsEdgeWonder1101b"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaWallsEdgeWonder1101b")
	public void aWaitingWonderOnTheEdgeColumnOfAPieceHoldsIt(GameTestHelper helper) {
		Village v = village(helper, HALL, true);
		helper.runAfterDelay(2, () -> withWalls(() -> {
			Walls.Piece piece = first(helper, v);
			BlockPos origin = new BlockPos(piece.box().maxX(), v.hall().getY(), piece.box().minZ() - 2);
			folly(helper, v.hall(), origin, false);
			helper.assertTrue(WonderPlans.wonderOver(helper.getLevel(), Ids.of(helper.getLevel().dimension()), piece.box()).isPresent(),
				"setup: the Wonder's layout isn't over the piece's edge " + piece.box());
			noSiteMeets(helper, round(helper, v), follyBox(origin), "a waiting Test Folly covering the piece's edge column");
			helper.succeed();
		}));
	}

	/** A finished Test Folly starting one block east of the first piece: the piece opens. */
	//$ gametest_ticks_batch AREA '60' '"qaWallsWonderBeside1101b"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaWallsWonderBeside1101b")
	public void aFinishedWonderJustBesideAPieceDoesntHoldIt(GameTestHelper helper) {
		Village v = village(helper, HALL, true);
		helper.runAfterDelay(2, () -> withWalls(() -> {
			Walls.Piece piece = first(helper, v);
			BlockPos origin = new BlockPos(piece.box().maxX() + 1, v.hall().getY(), piece.box().minZ() - 2);
			folly(helper, v.hall(), origin, true);
			helper.assertTrue(WonderPlans.wonderOver(helper.getLevel(), Ids.of(helper.getLevel().dimension()), piece.box()).isEmpty(),
				"setup: the Wonder is on the piece " + piece.box());
			firstOpens(helper, round(helper, v), piece, "a finished Test Folly just east of it");
			helper.succeed();
		}));
	}

	/**
	 * The Steward's round reads the ground with nothing laid out; then a Wonder is laid out over the first piece. The next
	 * round must see it (no stale read of the ground): the piece is skipped.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaWallsWonderLaidLater1101b"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaWallsWonderLaidLater1101b")
	public void aWonderLaidOutAfterTheGroundWasReadStillHoldsThePiece(GameTestHelper helper) {
		Village v = village(helper, HALL, true);
		helper.runAfterDelay(2, () -> withWalls(() -> {
			Walls.Piece piece = first(helper, v);
			helper.assertTrue(WonderPlans.footprints(helper.getLevel()).stream().noneMatch(b -> meet(b, piece.box())), "setup: ground already held");
			int cx = (piece.box().minX() + piece.box().maxX()) / 2;
			int cz = (piece.box().minZ() + piece.box().maxZ()) / 2;
			BlockPos origin = new BlockPos(cx - FOLLY_SIZE / 2, v.hall().getY(), cz - FOLLY_SIZE / 2);
			folly(helper, v.hall(), origin, false);
			noSiteMeets(helper, round(helper, v), follyBox(origin), "a Test Folly laid out after the Steward last looked");
			helper.succeed();
		}));
	}

	/** A waiting Wonder over the first piece, forgotten (its plan torn up): the piece opens again, nothing kept stale. */
	//$ gametest_ticks_batch AREA '60' '"qaWallsWonderForgotten1101b"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaWallsWonderForgotten1101b")
	public void aForgottenWonderNoLongerHoldsThePiece(GameTestHelper helper) {
		Village v = village(helper, HALL, true);
		helper.runAfterDelay(2, () -> withWalls(() -> {
			Walls.Piece piece = first(helper, v);
			int cx = (piece.box().minX() + piece.box().maxX()) / 2;
			int cz = (piece.box().minZ() + piece.box().maxZ()) / 2;
			BlockPos origin = new BlockPos(cx - FOLLY_SIZE / 2, v.hall().getY(), cz - FOLLY_SIZE / 2);
			WonderSite wonder = folly(helper, v.hall(), origin, false);
			helper.assertTrue(WonderPlans.footprints(helper.getLevel()).stream().anyMatch(b -> meet(b, piece.box())), "setup: the Wonder holds no ground");
			WonderManager.get(helper.getLevel()).remove(wonder.id());
			firstOpens(helper, round(helper, v), piece, "the Test Folly over it forgotten");
			helper.succeed();
		}));
	}

	/** A waiting Wonder over the first piece, then the world saved and loaded: after the reload the piece is still skipped. */
	//$ gametest_ticks_batch AREA '60' '"qaWallsWonderReload1101b"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaWallsWonderReload1101b")
	public void aWaitingWonderStillHoldsThePieceAfterAReload(GameTestHelper helper) {
		Village v = village(helper, HALL, true);
		helper.runAfterDelay(2, () -> withWalls(() -> {
			Walls.Piece piece = first(helper, v);
			int cx = (piece.box().minX() + piece.box().maxX()) / 2;
			int cz = (piece.box().minZ() + piece.box().maxZ()) / 2;
			BlockPos origin = new BlockPos(cx - FOLLY_SIZE / 2, v.hall().getY(), cz - FOLLY_SIZE / 2);
			folly(helper, v.hall(), origin, false);
			reloadWorldData(helper);
			helper.assertTrue(WonderManager.get(helper.getLevel()).sites().stream().anyMatch(s -> s.wonder().equals(FOLLY) && s.placement().isPresent()),
				"setup: the laid-out Wonder didn't survive the reload");
			noSiteMeets(helper, round(helper, v), follyBox(origin), "a waiting Test Folly, after a save and reload");
			helper.succeed();
		}));
	}

	/** A finished Well over the first piece, then the world saved and loaded: after the reload the piece is still skipped. */
	//$ gametest_ticks_batch AREA '60' '"qaWallsWellReload1101b"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaWallsWellReload1101b")
	public void aFinishedBuildingStillHoldsThePieceAfterAReload(GameTestHelper helper) {
		Village v = village(helper, HALL, true);
		helper.runAfterDelay(2, () -> withWalls(() -> {
			Walls.Piece piece = first(helper, v);
			BoundingBox well = wellAt(helper, v, piece.box().minX(), piece.box().minZ());
			reloadWorldData(helper);
			noSiteMeets(helper, round(helper, v), well, "a finished Well, after a save and reload");
			helper.succeed();
		}));
	}

	// ------------------------------------------------------------------------------------------------- B753 roads

	/** The street's line along z 8, from by a finished Well at the west end to by one at the east end. */
	private static final int STREET_Z = 8;
	/** The Test Folly between them, across the street's line: x 10..18, z 4..12. */
	private static final BlockPos ROAD_WONDER = new BlockPos(10, 2, 4);

	private record Street(Village village, BoundingBox west, BoundingBox east, int fromX, int toX) {
	}

	/** Two finished Wells, one by each end of the street, and the street between them on the plan. */
	private static Street street(GameTestHelper helper) {
		Village v = village(helper, new BlockPos(2, 2, 26), false);
		// stewardRoads is off in the test suite's config: on for this test alone (each is alone in its batch)
		boolean was = io.github.jcondedata.aliveworkplace.city.Roads.ENABLED;
		io.github.jcondedata.aliveworkplace.city.Roads.ENABLED = true;
		Leftovers.after(helper, () -> io.github.jcondedata.aliveworkplace.city.Roads.ENABLED = was);
		Vec3i w = wellSize(helper);
		// beside the street's two ends, on its south side, clear of the Wonder's ground between them
		BlockPos westAt = helper.absolutePos(new BlockPos(0, 2, STREET_Z + 3));
		BlockPos eastAt = helper.absolutePos(new BlockPos(29 - (w.getX() - 1), 2, STREET_Z + 3));
		BoundingBox west = wellAt(helper, v, westAt.getX(), westAt.getZ());
		BoundingBox east = wellAt(helper, v, eastAt.getX(), eastAt.getZ());
		helper.assertTrue(!meet(west, follyBox(helper.absolutePos(ROAD_WONDER)).inflatedBy(1)) && !meet(east, follyBox(helper.absolutePos(ROAD_WONDER)).inflatedBy(1)),
			"setup: a Well " + w + " is on the Wonder's ground");
		int fromX = 2;
		int toX = 27;
		v.entity().setPlan(v.entity().plan().addRoad(QaRoadGameTests.road(CityPlan.Road.STREET, "stonework",
			QaRoadGameTests.p(helper, v.hall(), fromX, STREET_Z), QaRoadGameTests.p(helper, v.hall(), toX, STREET_Z))));
		return new Street(v, west, east, fromX, toX);
	}

	/** The street's way, once the Steward has found it on his own (the hall's ticks). */
	private static List<BlockPos> routed(GameTestHelper helper, Street s) {
		CityPlan.Road road = s.village().entity().plan().roads().get(0);
		helper.assertTrue(road.routed(), "the Steward hasn't found the street's way yet");
		List<BlockPos> nodes = QaRoadGameTests.route(road, s.village().hall());
		BlockPos end = helper.absolutePos(new BlockPos(s.toX(), 2, STREET_Z));
		helper.assertTrue(!nodes.isEmpty() && nodes.get(nodes.size() - 1).distManhattan(end) <= 2,
			"the street stopped short of the east Well: it ends at " + (nodes.isEmpty() ? "nothing" : nodes.get(nodes.size() - 1)) + ", not " + end);
		return nodes;
	}

	/** No column of the 3-wide street is on {@code box}. */
	private static void clearOf(GameTestHelper helper, List<BlockPos> nodes, BoundingBox box, String what) {
		for (BlockPos n : nodes) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					int x = n.getX() + dx;
					int z = n.getZ() + dz;
					helper.assertTrue(!(x >= box.minX() && x <= box.maxX() && z >= box.minZ() && z <= box.maxZ()),
						"the Steward's street runs over " + what + " at " + x + ", " + z + " (" + box + ")");
				}
			}
		}
	}

	/** Control: two finished Wells and open ground between them: the Steward's street runs straight between their doors. */
	//$ gametest_ticks_batch AREA '400' '"qaRoadControl1101b"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaRoadControl1101b")
	public void controlAStewardStreetBetweenTwoBuildingsRunsStraight(GameTestHelper helper) {
		Street s = street(helper);
		helper.succeedWhen(() -> {
			List<BlockPos> nodes = routed(helper, s);
			int z = helper.absolutePos(new BlockPos(0, 2, STREET_Z)).getZ();
			helper.assertTrue(nodes.stream().allMatch(n -> n.getZ() == z), "with nothing between the Wells the street still bends: " + nodes);
			clearOf(helper, nodes, s.west(), "the west Well");
			clearOf(helper, nodes, s.east(), "the east Well");
		});
	}

	/** A Test Folly laid out between the two Wells, waiting for its goods: the Steward's street goes round its ground. */
	//$ gametest_ticks_batch AREA '400' '"qaRoadWaitingWonder1101b"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaRoadWaitingWonder1101b")
	public void aStewardStreetBetweenTwoBuildingsGoesRoundAWaitingWonder(GameTestHelper helper) {
		Street s = street(helper);
		BlockPos origin = helper.absolutePos(ROAD_WONDER);
		folly(helper, s.village().hall(), origin, false);
		helper.succeedWhen(() -> {
			List<BlockPos> nodes = routed(helper, s);
			clearOf(helper, nodes, follyBox(origin), "the Test Folly waiting for its goods");
			clearOf(helper, nodes, s.west(), "the west Well");
			clearOf(helper, nodes, s.east(), "the east Well");
		});
	}

	/** A finished Test Folly between the two Wells: the Steward's street goes round it. */
	//$ gametest_ticks_batch AREA '400' '"qaRoadFinishedWonder1101b"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaRoadFinishedWonder1101b")
	public void aStewardStreetBetweenTwoBuildingsGoesRoundAFinishedWonder(GameTestHelper helper) {
		Street s = street(helper);
		BlockPos origin = helper.absolutePos(ROAD_WONDER);
		folly(helper, s.village().hall(), origin, true);
		helper.succeedWhen(() -> {
			List<BlockPos> nodes = routed(helper, s);
			clearOf(helper, nodes, follyBox(origin), "the finished Test Folly");
			clearOf(helper, nodes, s.west(), "the west Well");
			clearOf(helper, nodes, s.east(), "the east Well");
		});
	}

	/** A waiting Test Folly between the Wells, the world saved and loaded before the Steward routes: still round it. */
	//$ gametest_ticks_batch AREA '400' '"qaRoadWonderReload1101b"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaRoadWonderReload1101b")
	public void aStewardStreetGoesRoundAWaitingWonderAfterAReload(GameTestHelper helper) {
		Street s = street(helper);
		BlockPos origin = helper.absolutePos(ROAD_WONDER);
		folly(helper, s.village().hall(), origin, false);
		reloadWorldData(helper);
		helper.succeedWhen(() -> {
			List<BlockPos> nodes = routed(helper, s);
			clearOf(helper, nodes, follyBox(origin), "the waiting Test Folly (after a save and reload)");
		});
	}
}
