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
import io.github.jcondedata.aliveworkplace.city.Roads;
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
import net.minecraft.core.HolderLookup;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA (2026-11-01), written from B752 and B753's text, not the fix: the Steward's wall round skips pieces whose ground is a
 * finished building, a finished Wonder or a Wonder laid out and waiting for its goods "like a build in progress" and
 * opens the next piece on open ground; his roads route round every laid-out Wonder. Covers what
 * {@link QaStewardWalls1101Tests} and {@link RoadWonderGameTests} don't: the footprint's edge from both sides, several
 * held pieces in a row, a restart before the round, the ground freed again, a road between two buildings with a Wonder
 * between them, a turned Wonder, and a Wonder read back from its save.
 */
public class QaWallsRoadsAroundBuildings1101GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(14, 2, 14);
	private static final ResourceLocation FOLLY = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");
	private static final ResourceLocation WELL = AliveWorkplace.id("well");
	/** The Test Folly's stage I footprint, 9 by 9. */
	private static final int FOLLY_SIZE = 9;

	private record Village(BlockPos hall, VillageHallBlockEntity entity, ServerPlayer owner, Villager builder, Villager steward) {
	}

	// ---- walls (B752) -------------------------------------------------------------------------------------------------

	/** As QaStewardWalls1101Tests: flat grass, old sites, finished builds and Wonders round the area forgotten, a hall. */
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
		BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 2, 0)), helper.absolutePos(new BlockPos(29, 29, 29))).forEach(at -> {
			if (level.getBlockState(at).is(Blocks.BARRIER)) {
				level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
			}
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.wonderGround(helper, hall);
		return hall;
	}

	/** A hall with an owner, a Steward, one builder with a stocked bench, and an approved palisade 10 blocks out. */
	private static Village village(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = ground(helper);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		BlockPos table = HALL.offset(-4, 0, 0);
		helper.setBlock(table, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(table.west(), Blocks.CHEST);
		Container chest = helper.getBlockEntity(table.west());
		chest.setItem(0, new ItemStack(Items.SPRUCE_LOG, 64));
		chest.setItem(1, new ItemStack(Items.STRIPPED_SPRUCE_LOG, 64));
		chest.setItem(2, new ItemStack(Items.SPRUCE_FENCE, 64));
		chest.setItem(3, new ItemStack(Items.SPRUCE_PLANKS, 64));
		chest.setItem(4, new ItemStack(Items.LADDER, 16));
		chest.setItem(5, new ItemStack(Items.DIRT, 64));
		Villager builder = helper.spawn(EntityType.VILLAGER, table.east());
		Builders.employ(level, builder, helper.absolutePos(table));
		Villager steward = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, HALL.north(2)));
		ItemStack plan = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(level, owner, plan, hall);
		helper.assertTrue(Stewards.appoint(owner, steward, plan) == InteractionResult.SUCCESS, "setup: Steward not appointed");
		int half = 10;
		CityPlan.Wall wall = new CityPlan.Wall(List.of(new BlockPos(-half, 0, -half), new BlockPos(half, 0, -half),
			new BlockPos(half, 0, half), new BlockPos(-half, 0, half)), true).approvedWith("palisade");
		entity.setPlan(entity.plan().withWall(wall));
		return new Village(hall, entity, owner, builder, steward);
	}

	private static boolean meet(BoundingBox one, BoundingBox two) {
		return one.minX() <= two.maxX() && one.maxX() >= two.minX() && one.minZ() <= two.maxZ() && one.maxZ() >= two.minZ();
	}

	private static List<Walls.Piece> unbuilt(GameTestHelper helper, Village v) {
		List<Walls.Piece> unbuilt = Walls.unbuilt(helper.getLevel(), v.hall());
		helper.assertTrue(unbuilt.size() >= 6, "setup: the approved wall has only " + unbuilt.size() + " pieces to build");
		return unbuilt;
	}

	private static List<BuildSite> round(GameTestHelper helper, Village v) {
		Walls.round(helper.getLevel(), v.hall(), v.entity());
		return Walls.openSites(helper.getLevel(), v.hall());
	}

	private static BoundingBox box(GameTestHelper helper, ResourceLocation structure, BlueprintData.Placement at) {
		return BlueprintLibrary.get(helper.getLevel(), structure).map(b -> BlueprintOutline.bounds(at, b.size())).orElseThrow();
	}

	private static BoundingBox box(GameTestHelper helper, BuildSite site) {
		return box(helper, site.structure(), site.placement());
	}

	/** A finished Well (a builder's, not the Steward's) with its north-west corner at {@code (x, z)}; returns its footprint. */
	private static BoundingBox wellAt(GameTestHelper helper, int x, int y, int z) {
		ServerLevel level = helper.getLevel();
		BlueprintData.Placement at = new BlueprintData.Placement(Ids.of(level.dimension()), new BlockPos(x, y, z), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(WELL, at, UUID.randomUUID());
		return box(helper, WELL, at);
	}

	private static int wellSizeX(GameTestHelper helper) {
		return BlueprintLibrary.get(helper.getLevel(), WELL).orElseThrow().size().getX();
	}

	/** The Test Folly begun for the hall and laid out centred on {@code piece}; finished when asked. Returns its stage I box. */
	private static BoundingBox follyOver(GameTestHelper helper, Village v, Walls.Piece piece, boolean finished) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(Wonders.loaded().get(FOLLY) != null, "the test folly isn't loaded: " + Wonders.problems());
		WonderManager manager = WonderManager.get(level);
		WonderSite site = manager.begin(FOLLY, v.hall(), v.owner().getUUID(), v.owner().getGameProfile().getName(), 1);
		UUID id = site.id();
		Leftovers.after(helper, () -> WonderManager.get(level).remove(id)); // the manager may be a reloaded one by then
		int cx = (piece.box().minX() + piece.box().maxX()) / 2;
		int cz = (piece.box().minZ() + piece.box().maxZ()) / 2;
		BlockPos origin = new BlockPos(cx - FOLLY_SIZE / 2, v.hall().getY(), cz - FOLLY_SIZE / 2);
		manager.place(id, new BlueprintData.Placement(Ids.of(level.dimension()), origin, Rotation.NONE, Mirror.NONE), origin.offset(-1, 0, -1));
		if (finished) {
			manager.finish(id, 2);
		}
		WonderSite now = manager.site(id).orElseThrow();
		helper.assertTrue(finished ? now.done() : (now.goingUp() && !now.started() && now.parts().isEmpty()),
			"setup: the Wonder isn't " + (finished ? "finished" : "laid out and waiting for its goods"));
		return new BoundingBox(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + FOLLY_SIZE - 1, origin.getY() + 6, origin.getZ() + FOLLY_SIZE - 1);
	}

	/** The first piece, in the round's order, whose footprint meets none of {@code held}. */
	private static Walls.Piece firstFree(GameTestHelper helper, List<Walls.Piece> pieces, List<BoundingBox> held) {
		for (Walls.Piece p : pieces) {
			if (held.stream().noneMatch(h -> meet(h, p.box()))) {
				return p;
			}
		}
		throw new AssertionError("setup: every piece is held");
	}

	private static void opensOnly(GameTestHelper helper, List<BuildSite> open, Walls.Piece expected, List<BoundingBox> held, String what) {
		for (BuildSite site : open) {
			for (BoundingBox h : held) {
				helper.assertTrue(!meet(box(helper, site), h), "the Steward opened a wall site at " + box(helper, site) + " through " + what + " at " + h
					+ "; expected that piece skipped");
			}
		}
		helper.assertTrue(open.size() == 1, open.size() + " wall sites opened with one builder, not 1 (" + what + ")");
		helper.assertTrue(open.get(0).placement().equals(expected.placement()), "the wall site opened at " + box(helper, open.get(0))
			+ ", not on the first piece on open ground " + expected.box() + " (piece " + expected.index() + "; " + what + ")");
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

	/**
	 * Edge from inside: a finished Well whose footprint shares a single column with the first piece (its south-east
	 * corner on the piece's north-west corner). That's still the Well's ground: the piece is skipped.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaWallsEdgeIn1101"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaWallsEdgeIn1101")
	public void qaWallPieceSkippedWhenABuildingSharesOneColumn(GameTestHelper helper) {
		Village v = village(helper);
		helper.runAfterDelay(2, () -> withWalls(() -> {
			List<Walls.Piece> pieces = unbuilt(helper, v);
			Walls.Piece first = pieces.get(0);
			int w = wellSizeX(helper);
			int d = BlueprintLibrary.get(helper.getLevel(), WELL).orElseThrow().size().getZ();
			BoundingBox well = wellAt(helper, first.box().minX() - (w - 1), v.hall().getY(), first.box().minZ() - (d - 1));
			helper.assertTrue(well.maxX() == first.box().minX() && well.maxZ() == first.box().minZ(),
				"setup: the Well " + well + " doesn't touch the piece " + first.box() + " at one corner column");
			List<BoundingBox> held = List.of(well);
			opensOnly(helper, round(helper, v), firstFree(helper, pieces, held), held, "a finished Well sharing one column");
			helper.succeed();
		}));
	}

	/**
	 * Edge from outside: the same Well one column short of the first piece (its east side just west of the piece, the
	 * rows overlapping). Open ground: the first piece opens, as with nothing there.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaWallsEdgeOut1101"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaWallsEdgeOut1101")
	public void qaWallPieceOpensWhenABuildingStopsOneColumnShort(GameTestHelper helper) {
		Village v = village(helper);
		helper.runAfterDelay(2, () -> withWalls(() -> {
			List<Walls.Piece> pieces = unbuilt(helper, v);
			Walls.Piece first = pieces.get(0);
			BoundingBox well = wellAt(helper, first.box().minX() - wellSizeX(helper), v.hall().getY(), first.box().minZ());
			helper.assertTrue(well.maxX() == first.box().minX() - 1 && !meet(well, first.box()),
				"setup: the Well " + well + " isn't one column clear of the piece " + first.box());
			List<BuildSite> open = round(helper, v);
			helper.assertTrue(open.size() == 1, open.size() + " wall sites opened beside the Well, not 1");
			helper.assertTrue(open.get(0).placement().equals(first.placement()), "the first piece " + first.box()
				+ " was skipped though the finished Well " + well + " stops a column short; the site opened at " + box(helper, open.get(0)));
			helper.succeed();
		}));
	}

	/**
	 * Three held grounds along the line, one after another: a Wonder waiting for its bread, a finished Well and a
	 * finished Wonder, each on the next piece still on open ground. The round skips every held piece, more than one in a
	 * row, and opens the first piece past them.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaWallsManyHeld1101"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaWallsManyHeld1101")
	public void qaRoundSkipsSeveralHeldPiecesInARow(GameTestHelper helper) {
		Village v = village(helper);
		helper.runAfterDelay(2, () -> withWalls(() -> {
			List<Walls.Piece> pieces = unbuilt(helper, v);
			List<BoundingBox> held = new ArrayList<>();
			held.add(follyOver(helper, v, firstFree(helper, pieces, held), false));
			Walls.Piece next = firstFree(helper, pieces, held);
			held.add(wellAt(helper, next.box().minX(), v.hall().getY(), next.box().minZ()));
			held.add(follyOver(helper, v, firstFree(helper, pieces, held), true));
			Walls.Piece expected = firstFree(helper, pieces, held);
			long skipped = pieces.stream().takeWhile(p -> p != expected).count();
			helper.assertTrue(skipped >= 3, "setup: only " + skipped + " pieces held before the first free one");
			opensOnly(helper, round(helper, v), expected, held, "a waiting Wonder, a finished Well and a finished Wonder (" + skipped + " pieces held)");
			helper.succeed();
		}));
	}

	/** The hall, its build sites, finished buildings and Wonders read back from their saves, as a restart does. */
	private static VillageHallBlockEntity restart(GameTestHelper helper, BlockPos hall) {
		ServerLevel level = helper.getLevel();
		HolderLookup.Provider registries = level.registryAccess();
		level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
		level.getDataStorage().set(WonderManager.NAME, WonderManager.load(WonderManager.get(level).save(new CompoundTag(), registries), registries));
		CompoundTag saved = level.getBlockEntity(hall).saveWithFullMetadata(registries);
		BlockEntity loaded = BlockEntity.loadStatic(hall, level.getBlockState(hall), saved, registries);
		helper.assertTrue(loaded instanceof VillageHallBlockEntity, "setup: the hall didn't load back");
		level.setBlockEntity(loaded);
		return (VillageHallBlockEntity) level.getBlockEntity(hall);
	}

	/**
	 * A finished Well on the first piece and a Wonder waiting for its goods on the next free one, then a restart before
	 * the round. The held ground comes back from the saves: both pieces are still skipped.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaWallsReload1101"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaWallsReload1101")
	public void qaHeldPiecesStillSkippedAfterARestart(GameTestHelper helper) {
		Village before = village(helper);
		helper.runAfterDelay(2, () -> withWalls(() -> {
			List<Walls.Piece> pieces = unbuilt(helper, before);
			List<BoundingBox> held = new ArrayList<>();
			Walls.Piece first = pieces.get(0);
			held.add(wellAt(helper, first.box().minX(), before.hall().getY(), first.box().minZ()));
			held.add(follyOver(helper, before, firstFree(helper, pieces, held), false));
			VillageHallBlockEntity entity = restart(helper, before.hall());
			helper.assertTrue(entity.plan().wall().map(CityPlan.Wall::approved).orElse(false), "setup: the approved wall didn't come back with the hall");
			Village v = new Village(before.hall(), entity, before.owner(), before.builder(), before.steward());
			List<Walls.Piece> after = unbuilt(helper, v);
			helper.assertTrue(after.size() == pieces.size(), "setup: the wall has " + after.size() + " pieces after the restart, " + pieces.size() + " before");
			opensOnly(helper, round(helper, v), firstFree(helper, after, held), held, "a finished Well and a waiting Wonder read back from their saves");
			helper.succeed();
		}));
	}

	/**
	 * The ground freed again: a finished Well and a waiting Wonder over the first piece, seen once (the Wonders' ground
	 * read as the round reads it), then the Well forgotten (taken down) and the Wonder cancelled. Nothing holds the
	 * piece now: the round opens it, as with open ground.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaWallsFreed1101"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaWallsFreed1101")
	public void qaPieceOpensOnceTheBuildingAndWonderAreGone(GameTestHelper helper) {
		Village v = village(helper);
		helper.runAfterDelay(2, () -> withWalls(() -> {
			ServerLevel level = helper.getLevel();
			List<Walls.Piece> pieces = unbuilt(helper, v);
			Walls.Piece first = pieces.get(0);
			BlueprintData.Placement wellAt = new BlueprintData.Placement(Ids.of(level.dimension()),
				new BlockPos(first.box().minX(), v.hall().getY(), first.box().minZ()), Rotation.NONE, Mirror.NONE);
			BuildSiteManager.get(level).recordFinished(WELL, wellAt, UUID.randomUUID());
			follyOver(helper, v, first, false);
			helper.assertTrue(WonderPlans.footprints(level).stream().anyMatch(b -> meet(b, first.box())),
				"setup: the waiting Wonder's ground isn't read over the piece");
			BuildSiteManager.get(level).forgetFinished(wellAt);
			WonderManager manager = WonderManager.get(level);
			for (WonderSite w : List.copyOf(manager.in(v.hall()))) {
				manager.remove(w.id());
			}
			List<BuildSite> open = round(helper, v);
			helper.assertTrue(open.size() == 1, open.size() + " wall sites opened on the freed ground, not 1");
			helper.assertTrue(open.get(0).placement().equals(first.placement()), "the first piece " + first.box()
				+ " is still skipped after its Well was taken down and its Wonder cancelled; the site opened at " + box(helper, open.get(0)));
			helper.succeed();
		}));
	}

	// ---- roads (B753) -------------------------------------------------------------------------------------------------

	/** The Test Folly's stage I, laid out across the streets' lines (z 8 and z 10): test-area x 10..18, z 6..14. */
	private static final BoundingBox FOLLY_AT = new BoundingBox(10, 2, 6, 18, 8, 14);

	/**
	 * Lays the folly out for {@code hall} turned by {@code rotation}, its turned stage I footprint on {@link #FOLLY_AT};
	 * finished when asked.
	 */
	private static WonderSite follyAcross(GameTestHelper helper, BlockPos hall, Rotation rotation, boolean finished) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(Wonders.loaded().get(FOLLY) != null, "the test folly isn't loaded: " + Wonders.problems());
		// vanilla's turn of the footprint's far corner: where the turned box lands from its origin
		BlockPos far = StructureTemplate.transform(new BlockPos(FOLLY_SIZE - 1, 0, FOLLY_SIZE - 1), Mirror.NONE, rotation, BlockPos.ZERO);
		BlockPos min = helper.absolutePos(new BlockPos(FOLLY_AT.minX(), FOLLY_AT.minY(), FOLLY_AT.minZ()));
		BlockPos origin = min.offset(-Math.min(0, far.getX()), 0, -Math.min(0, far.getZ()));
		WonderManager manager = WonderManager.get(level);
		WonderSite site = manager.begin(FOLLY, hall, null, "", 1);
		UUID id = site.id();
		Leftovers.after(helper, () -> WonderManager.get(level).remove(id));
		BlueprintData.Placement at = new BlueprintData.Placement(Ids.of(level.dimension()), origin, rotation, Mirror.NONE);
		manager.place(id, at, origin.offset(-1, 0, -1));
		if (finished) {
			manager.finish(id, 2);
		}
		WonderSite now = manager.site(id).orElseThrow();
		helper.assertTrue(finished ? now.done() : (now.goingUp() && !now.started() && now.parts().isEmpty()),
			"set-up: the Wonder isn't " + (finished ? "finished" : "laid out and waiting for its goods"));
		BoundingBox drawn = box(helper, ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "wonder/test_folly/stage_1_0"), at);
		BoundingBox want = absolute(helper, FOLLY_AT);
		helper.assertTrue(drawn.minX() == want.minX() && drawn.maxX() == want.maxX() && drawn.minZ() == want.minZ() && drawn.maxZ() == want.maxZ(),
			"set-up: the turned folly's stage I is at " + drawn + ", not " + want);
		return now;
	}

	private static BoundingBox absolute(GameTestHelper helper, BoundingBox local) {
		BlockPos a = helper.absolutePos(new BlockPos(local.minX(), local.minY(), local.minZ()));
		BlockPos b = helper.absolutePos(new BlockPos(local.maxX(), local.maxY(), local.maxZ()));
		return BoundingBox.fromCorners(a, b);
	}

	/** The street (3 wide) keeps off {@code box} (absolute). */
	private static void clearOf(GameTestHelper helper, List<BlockPos> nodes, BoundingBox box, String what) {
		for (BlockPos n : nodes) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					int x = n.getX() + dx;
					int z = n.getZ() + dz;
					helper.assertTrue(!(x >= box.minX() && x <= box.maxX() && z >= box.minZ() && z <= box.maxZ()),
						"the street runs over the " + what + " at " + x + ", " + z + " (" + box + "); route " + nodes);
				}
			}
		}
	}

	/**
	 * Two finished Wells (7 by 7) at the area's north corners, x 0..6 and x 23..29, z 0..6, and a street from just south
	 * of one to just south of the other (x 3 to x 26 along z 10); the routed street, its nodes in level coordinates.
	 */
	private static List<BlockPos> betweenWells(GameTestHelper helper, BlockPos hall, List<BoundingBox> wells) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(wellSizeX(helper) == 7, "set-up: the Well isn't 7 wide any more; move the test's Wells");
		for (int x : new int[] {0, 23}) {
			BlockPos at = helper.absolutePos(new BlockPos(x, 2, 0));
			BlueprintData.Placement p = new BlueprintData.Placement(Ids.of(level.dimension()), at, Rotation.NONE, Mirror.NONE);
			BuildSiteManager.get(level).recordFinished(WELL, p, UUID.randomUUID());
			wells.add(box(helper, WELL, p));
		}
		VillageHallBlockEntity entity = QaRoadGameTests.hall(helper, hall);
		entity.setPlan(CityPlan.EMPTY.addRoad(QaRoadGameTests.road(CityPlan.Road.STREET, "stonework",
			QaRoadGameTests.p(helper, hall, 3, 10), QaRoadGameTests.p(helper, hall, 26, 10))));
		Roads.routeNow(level, hall);
		CityPlan.Road street = entity.plan().roads().get(0);
		helper.assertTrue(street.routed(), "the street between the Wells wasn't routed");
		List<BlockPos> nodes = QaRoadGameTests.route(street, hall);
		helper.assertTrue(!nodes.isEmpty() && nodes.get(nodes.size() - 1).distManhattan(helper.absolutePos(new BlockPos(26, 2, 10))) <= 2,
			"the street between the Wells stopped short: " + nodes);
		return nodes;
	}

	/**
	 * The case B753 names: a Steward street between two buildings with a Wonder laid out between them, waiting for its
	 * bread (no build sites). It goes round the Wonder and over neither Well.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaRoadWellsWonderWaiting1101"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaRoadWellsWonderWaiting1101")
	public void qaRoadBetweenTwoBuildingsGoesRoundAWaitingWonder(GameTestHelper helper) {
		BlockPos hall = QaRoadGameTests.ground(helper, new BlockPos(2, 2, 26), 14);
		Leftovers.wonderGround(helper, hall);
		follyAcross(helper, hall, Rotation.NONE, false);
		List<BoundingBox> wells = new ArrayList<>();
		List<BlockPos> nodes = betweenWells(helper, hall, wells);
		clearOf(helper, nodes, absolute(helper, FOLLY_AT), "Test Folly waiting for its bread");
		for (BoundingBox w : wells) {
			clearOf(helper, nodes, w, "finished Well");
		}
		helper.succeed();
	}

	/**
	 * The same street with a finished Wonder between the Wells, laid out turned a quarter (its footprint runs west of its
	 * origin): the street still goes round the turned ground.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaRoadWellsWonderTurned1101"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaRoadWellsWonderTurned1101")
	public void qaRoadBetweenTwoBuildingsGoesRoundATurnedFinishedWonder(GameTestHelper helper) {
		BlockPos hall = QaRoadGameTests.ground(helper, new BlockPos(2, 2, 26), 14);
		Leftovers.wonderGround(helper, hall);
		follyAcross(helper, hall, Rotation.CLOCKWISE_90, true);
		List<BoundingBox> wells = new ArrayList<>();
		List<BlockPos> nodes = betweenWells(helper, hall, wells);
		clearOf(helper, nodes, absolute(helper, FOLLY_AT), "finished Test Folly (turned a quarter)");
		for (BoundingBox w : wells) {
			clearOf(helper, nodes, w, "finished Well");
		}
		helper.succeed();
	}

	/**
	 * A Wonder waiting for its goods, then a restart (the Wonders read back from their save) before the street is
	 * routed: its ground is still kept clear.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaRoadWonderReload1101"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaRoadWonderReload1101")
	public void qaRoadGoesRoundAWaitingWonderAfterARestart(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = QaRoadGameTests.ground(helper, new BlockPos(2, 2, 26), 14);
		Leftovers.wonderGround(helper, hall);
		follyAcross(helper, hall, Rotation.NONE, false);
		WonderPlans.footprints(level); // read once before the restart, as a running server would have
		HolderLookup.Provider registries = level.registryAccess();
		level.getDataStorage().set(WonderManager.NAME, WonderManager.load(WonderManager.get(level).save(new CompoundTag(), registries), registries));
		helper.assertTrue(WonderManager.get(level).in(hall).stream().anyMatch(w -> w.placement().isPresent() && !w.done()),
			"set-up: the waiting Wonder didn't come back from its save");
		VillageHallBlockEntity entity = QaRoadGameTests.hall(helper, hall);
		entity.setPlan(CityPlan.EMPTY.addRoad(QaRoadGameTests.road(CityPlan.Road.STREET, "stonework",
			QaRoadGameTests.p(helper, hall, 2, 8), QaRoadGameTests.p(helper, hall, 27, 8))));
		Roads.routeNow(level, hall);
		CityPlan.Road street = entity.plan().roads().get(0);
		helper.assertTrue(street.routed(), "the street wasn't routed after the restart");
		clearOf(helper, QaRoadGameTests.route(street, hall), absolute(helper, FOLLY_AT), "Test Folly waiting for its bread, read back from its save");
		helper.succeed();
	}
}
