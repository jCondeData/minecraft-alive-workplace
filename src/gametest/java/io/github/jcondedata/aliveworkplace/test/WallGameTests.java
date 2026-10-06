package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.StewardDesk;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.city.WallKits;
import io.github.jcondedata.aliveworkplace.city.Walls;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
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

/** The village's wall (ROADMAP 27.18): wall kits as data, the line laid out into pieces, proposed, built and counted. */
public class WallGameTests implements FabricGameTest {
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

	private static VillageHallBlockEntity hall(GameTestHelper helper, BlockPos hall) {
		return (VillageHallBlockEntity) helper.getLevel().getBlockEntity(hall);
	}

	/** An offset from the hall. */
	private static BlockPos p(int dx, int dz) {
		return new BlockPos(dx, 0, dz);
	}

	/** A square wall line {@code half} blocks out from the hall on every side. */
	private static CityPlan.Wall square(int half) {
		return new CityPlan.Wall(List.of(p(-half, -half), p(half, -half), p(half, half), p(-half, half)), true);
	}

	/** Whether {@code piece}'s footprint covers {@code at} (x and z: each piece sits at its own ground height). */
	private static boolean covers(Walls.Piece piece, BlockPos at) {
		return at.getX() >= piece.box().minX() - 1 && at.getX() <= piece.box().maxX() + 1
			&& at.getZ() >= piece.box().minZ() - 1 && at.getZ() <= piece.box().maxZ() + 1;
	}

	private static WallKits.Kit palisade() {
		return WallKits.get("palisade").orElseThrow();
	}

	/** Wall kits ship as data: a Palisade up to a Village, Stone from a Town, each with its three blueprints. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void bothWallKitsLoadWithTheirBlueprintsAndRanks(GameTestHelper helper) {
		WallKits.Kit palisade = WallKits.get("palisade").orElseThrow(() -> new net.minecraft.gametest.framework.GameTestAssertException(
			"no Palisade kit: " + WallKits.all()));
		WallKits.Kit stone = WallKits.get("stone").orElseThrow(() -> new net.minecraft.gametest.framework.GameTestAssertException(
			"no Stone kit: " + WallKits.all()));
		helper.assertTrue(palisade.segment().equals(AliveWorkplace.id("palisade")) && palisade.cornerTower().equals(AliveWorkplace.id("palisade_tower"))
			&& palisade.gate().equals(AliveWorkplace.id("palisade_gate")), "the Palisade kit's pieces: " + palisade);
		helper.assertTrue(stone.segment().equals(AliveWorkplace.id("stone_wall")) && stone.cornerTower().equals(AliveWorkplace.id("wall_tower"))
			&& stone.gate().equals(AliveWorkplace.id("gatehouse")), "the Stone kit's pieces: " + stone);
		helper.assertTrue(WallKits.forRank(VillageRanks.Rank.HAMLET).orElseThrow().name().equals("palisade")
			&& WallKits.forRank(VillageRanks.Rank.VILLAGE).orElseThrow().name().equals("palisade"), "a Village doesn't build a palisade");
		helper.assertTrue(WallKits.forRank(VillageRanks.Rank.TOWN).orElseThrow().name().equals("stone")
			&& WallKits.forRank(VillageRanks.Rank.CITY).orElseThrow().name().equals("stone"), "a Town doesn't build stone");
		for (ResourceLocation id : WallKits.allBlueprints()) {
			helper.assertTrue(BlueprintLibrary.get(helper.getLevel(), id).isPresent(), "a kit's blueprint is missing: " + id);
			helper.assertTrue(Walls.isPiece(id), id + " isn't read as a wall piece");
		}
		helper.assertTrue(!Walls.isPiece(AliveWorkplace.id("well")), "a well counts as a wall piece");
		helper.succeed();
	}

	/** A square wall line: four corner towers, whole segments between them, and a gate where the road crosses. */
	//$ gametest_ticks_batch AREA '40' '"wallLayout"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "wallLayout")
	public void aSquareWallLineGetsSegmentsFourTowersAndAGateOnTheRoad(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = ground(helper, new BlockPos(14, 2, 14));
		VillageHallBlockEntity entity = hall(helper, hall);
		// a street out of the village to the north, crossing the line
		CityPlan plan = CityPlan.EMPTY.withWall(square(40))
			.addRoad(new CityPlan.Road(List.of(p(0, 0), p(0, -60)), CityPlan.Road.STREET, "", true));
		entity.setPlan(plan);
		List<Walls.Piece> pieces = Walls.layout(level, hall, entity.plan(), palisade());
		helper.assertTrue(!pieces.isEmpty(), "the wall line got no pieces");
		long towers = pieces.stream().filter(piece -> piece.kind() == Walls.Kind.TOWER).count();
		long gates = pieces.stream().filter(piece -> piece.kind() == Walls.Kind.GATE).count();
		long segments = pieces.stream().filter(piece -> piece.kind() == Walls.Kind.SEGMENT).count();
		helper.assertTrue(towers >= 4, towers + " towers, not four corners and more every " + Walls.TOWER_EVERY + " blocks");
		helper.assertTrue(gates == 1, gates + " gates, not one where the street crosses");
		helper.assertTrue(segments >= 30, segments + " segments round a 80-block square");
		// a tower in each corner of the square
		for (BlockPos corner : List.of(p(-40, -40), p(40, -40), p(40, 40), p(-40, 40))) {
			BlockPos at = hall.offset(corner.getX(), 0, corner.getZ());
			helper.assertTrue(pieces.stream().anyMatch(piece -> piece.kind() == Walls.Kind.TOWER && covers(piece, at)), "no corner tower at " + corner);
		}
		// the gate stands on the street's line
		Walls.Piece gate = pieces.stream().filter(piece -> piece.kind() == Walls.Kind.GATE).findFirst().orElseThrow();
		helper.assertTrue(Math.abs(gate.box().getCenter().getX() - hall.getX()) <= 4, "the gate isn't on the street: " + gate.box());
		helper.assertTrue(Math.abs(gate.box().getCenter().getZ() - (hall.getZ() - 40)) <= 4, "the gate isn't on the wall line: " + gate.box());
		// towers and gates never overlap anything; only the last segment of a run may sit flush over its neighbour
		for (int i = 0; i < pieces.size(); i++) {
			for (int j = i + 1; j < pieces.size(); j++) {
				Walls.Piece one = pieces.get(i);
				Walls.Piece two = pieces.get(j);
				if (!one.box().intersects(two.box())) {
					continue;
				}
				helper.assertTrue(one.kind() == Walls.Kind.SEGMENT && two.kind() == Walls.Kind.SEGMENT,
					"a " + one.kind() + " overlaps a " + two.kind() + ": " + one.box() + " and " + two.box());
				helper.assertTrue(j == i + 1, "segments " + i + " and " + j + " overlap but aren't next to each other");
			}
		}
		// towers never further apart than TOWER_EVERY plus a segment along a run
		for (BlockPos corner : List.of(p(-40, -40), p(40, -40))) {
			helper.assertTrue(pieces.stream().anyMatch(piece -> piece.kind() == Walls.Kind.TOWER
				&& Math.abs(piece.box().getCenter().getZ() - (hall.getZ() + corner.getZ())) <= 4
				&& Math.abs(piece.box().getCenter().getX() - hall.getX()) < 30), "no tower along the run from " + corner);
		}
		helper.succeed();
	}

	/** A village never raided and with no bandits near gets no wall proposal; raided, it gets one, and approving it builds. */
	//$ gametest_ticks_batch AREA '60' '"wallPropose"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "wallPropose")
	public void aVillageNeverRaidedGetsNoWallProposal(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Village v = village(helper, new BlockPos(14, 2, 14));
		v.entity().setPlan(CityPlan.EMPTY.withWall(square(12)));
		boolean was = Walls.ENABLED;
		try {
			Walls.ENABLED = true;
			helper.assertTrue(!Walls.wanted(level, v.hall()), "a village never raided wants a wall");
			Walls.propose(level, v.hall(), v.steward());
			helper.assertTrue(StewardDesk.of(level, v.hall()).proposals().isEmpty(), "a wall was proposed to a village never raided");
			v.entity().setLastRaidDay(Chronicle.day(level));
			helper.assertTrue(Walls.wanted(level, v.hall()), "a village raided today doesn't want a wall");
			Walls.propose(level, v.hall(), v.steward());
			List<StewardDesk.Proposal> proposals = StewardDesk.of(level, v.hall()).proposals();
			helper.assertTrue(proposals.size() == 1, "the wall wasn't proposed: " + proposals);
			StewardDesk.Proposal proposal = proposals.get(0);
			helper.assertTrue(proposal.wall().isPresent() && proposal.wall().get().name().equals("palisade"), "not a Palisade proposal: " + proposal);
			helper.assertTrue(!proposal.isBuild(), "the wall is offered as an ordinary build");
			helper.assertTrue(!v.entity().plan().wall().get().approved(), "the wall is built before it is approved");
			helper.assertTrue(Walls.pieces(level, v.hall()).isEmpty(), "an unapproved wall has pieces to build");
			// a raid 8 days ago is too long ago
			v.entity().setLastRaidDay(Chronicle.day(level) - Walls.RAID_DAYS - 1);
			helper.assertTrue(!Walls.wanted(level, v.hall()), "a raid " + (Walls.RAID_DAYS + 1) + " days ago still wants a wall");
			v.entity().setLastRaidDay(Chronicle.day(level));

			helper.assertTrue(StewardDesk.approve(level, v.hall(), v.owner(), proposal.id()).ok(), "the wall proposal wasn't approved");
			CityPlan.Wall wall = v.entity().plan().wall().orElseThrow();
			helper.assertTrue(wall.approved() && wall.kit().equals("palisade"), "the wall isn't approved with its kit: " + wall);
			helper.assertTrue(!Walls.pieces(level, v.hall()).isEmpty(), "an approved wall has no pieces");
			helper.assertTrue(v.entity().chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.PLANS), "no chronicle line for the wall");
			Walls.round(level, v.hall(), v.entity());
			List<BuildSite> open = Walls.openSites(level, v.hall());
			helper.assertTrue(open.size() == 1, open.size() + " wall sites opened with one builder, not 1");
			helper.assertTrue(!open.get(0).levelGround(), "a wall piece would level the ground round it");
			helper.assertTrue(v.hall().equals(open.get(0).stewardHall()), "the wall site isn't the Steward's");
		} finally {
			Walls.ENABLED = was;
		}
		helper.succeed();
	}

	/** The whole wall counts as one building for the rank, however many pieces it has. */
	//$ gametest_ticks_batch AREA '40' '"wallRank"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "wallRank")
	public void theWholeWallCountsAsOneBuildingForTheRank(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean was = Walls.ENABLED;
		Walls.ENABLED = true;
		try {
		BlockPos hall = ground(helper, new BlockPos(14, 2, 14));
		VillageHallBlockEntity entity = hall(helper, hall);
		entity.setPlan(CityPlan.EMPTY.withWall(square(12).approvedWith("palisade")));
		int before = VillageRanks.score(level, hall, 0).buildings();
		// an ordinary building counts for one
		record(level, AliveWorkplace.id("well"), helper.absolutePos(new BlockPos(20, 2, 20)));
		helper.assertValueEqual(VillageRanks.score(level, hall, 0).buildings(), before + 1, "buildings after a well");
		// and six wall pieces for one more, together
		int n = 0;
		for (Walls.Piece piece : Walls.pieces(level, hall)) {
			if (n++ >= 6) {
				break;
			}
			BuildSiteManager.get(level).recordFinished(piece.blueprint(), piece.placement(), UUID.randomUUID());
		}
		helper.assertTrue(n >= 6, "the wall has only " + n + " pieces");
		helper.assertValueEqual(VillageRanks.score(level, hall, 0).buildings(), before + 2, "buildings after six wall pieces");
		} finally {
			Walls.ENABLED = was;
		}
		helper.succeed();
	}

	/** An approved wall, its kit and which pieces stand survive save and reload. */
	//$ gametest_ticks_batch AREA '40' '"wallReload"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "wallReload")
	public void anApprovedWallSurvivesSaveAndReload(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean was = Walls.ENABLED;
		Walls.ENABLED = true;
		try {
		BlockPos hall = ground(helper, new BlockPos(14, 2, 14));
		VillageHallBlockEntity entity = hall(helper, hall);
		entity.setPlan(CityPlan.EMPTY.withWall(square(12).approvedWith("palisade")));
		List<Walls.Piece> pieces = Walls.pieces(level, hall);
		helper.assertTrue(pieces.size() >= 4, "the wall has only " + pieces.size() + " pieces");
		BuildSiteManager.get(level).recordFinished(pieces.get(0).blueprint(), pieces.get(0).placement(), UUID.randomUUID());
		helper.assertTrue(Walls.stands(level, pieces.get(0)), "a finished piece doesn't stand");
		helper.assertTrue(Walls.unbuilt(level, hall).size() == pieces.size() - 1, "the built piece is still to build");

		CompoundTag tag = entity.saveWithFullMetadata(level.registryAccess());
		VillageHallBlockEntity copy = (VillageHallBlockEntity) net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
			hall, level.getBlockState(hall), tag, level.registryAccess());
		helper.assertTrue(copy != null && copy.plan().equals(entity.plan()), "the plan didn't survive: " + (copy == null ? null : copy.plan()));
		CityPlan.Wall wall = copy.plan().wall().orElseThrow();
		helper.assertTrue(wall.approved() && wall.kit().equals("palisade"), "the wall lost its kit or approval: " + wall);
		helper.assertTrue(Walls.pieces(level, hall).equals(pieces), "the layout changed over a reload");

		// a wall line saved before 27.18 (no kit, no approval) still loads, unapproved
		CompoundTag old = (CompoundTag) CityPlan.Wall.CODEC.encodeStart(NbtOps.INSTANCE, wall).getOrThrow();
		old.remove("kit");
		old.remove("approved");
		CityPlan.Wall loaded = CityPlan.Wall.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow();
		helper.assertTrue(!loaded.approved() && loaded.kit().isEmpty() && loaded.points().equals(wall.points()), "an old wall line: " + loaded);
		} finally {
			Walls.ENABLED = was;
		}
		helper.succeed();
	}

	/** Config off: nothing is proposed, nothing is laid out and no site opens, however long the village waits. */
	//$ gametest_ticks_batch AREA '120' '"wallOff"'
	@GameTest(template = AREA, timeoutTicks = 120, batch = "wallOff")
	public void withStewardWallsOffNothingIsProposedOrBuilt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(!Walls.ENABLED, "setup: stewardWalls is on in tests");
		Village v = village(helper, new BlockPos(14, 2, 14));
		v.entity().setPlan(CityPlan.EMPTY.withWall(square(12).approvedWith("palisade")));
		v.entity().setLastRaidDay(Chronicle.day(level));
		Walls.propose(level, v.hall(), v.steward());
		helper.assertTrue(StewardDesk.of(level, v.hall()).proposals().isEmpty(), "a wall was proposed with the config off");
		helper.assertTrue(Walls.pieces(level, v.hall()).isEmpty(), "an approved wall has pieces with the config off");
		helper.runAfterDelay(100, () -> {
			helper.assertTrue(Walls.openSites(level, v.hall()).isEmpty(), "a wall site opened with the config off");
			helper.succeed();
		});
	}

	/** A builder really builds the new Palisade Tower, from his chest, and it stands where the wall wants it. */
	//$ gametest_ticks_batch AREA '4000' '"wallTowerBuilt"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "wallTowerBuilt")
	public void aBuilderBuildsThePalisadeTower(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Village v = village(helper, new BlockPos(24, 2, 24));
		Blueprint tower = BlueprintLibrary.get(level, AliveWorkplace.id("palisade_tower")).orElseThrow();
		BlueprintData.Placement placement = new BlueprintData.Placement(Ids.of(level.dimension()),
			helper.absolutePos(new BlockPos(6, 2, 6)), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, v.builders().get(0), v.owner().getUUID(), "", AliveWorkplace.id("palisade_tower"), placement);
		site.setStewardHall(v.hall());
		site.setLevelGround(false);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "the tower isn't finished yet");
			int solid = 0;
			for (Blueprint.Entry e : tower.blocks()) {
				if (!e.state().isAir()) {
					solid++;
					BlockPos at = placement.origin().offset(e.pos());
					helper.assertTrue(!level.getBlockState(at).isAir(), "nothing at " + e.pos() + " of the Palisade Tower");
				}
			}
			helper.assertTrue(solid > 100, "the Palisade Tower is only " + solid + " blocks");
			helper.assertTrue(level.getBlockState(placement.origin().offset(2, 5, 3)).is(Blocks.LADDER), "no ladder up the Palisade Tower");
		});
	}

	/** B86: a wall line past every bench's reach (60 blocks out in the City run, the reach 48) still gets its pieces opened. */
	//$ gametest_ticks_batch AREA '40' '"wallFar"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "wallFar")
	public void aWallPastEveryBenchsReachStillOpensAPiece(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Village v = village(helper, new BlockPos(14, 2, 14));
		v.entity().setPlan(CityPlan.EMPTY.withWall(square(12).approvedWith("palisade")));
		boolean was = Walls.ENABLED;
		int reach = Builders.MAX_SITE_DISTANCE;
		try {
			Walls.ENABLED = true;
			Builders.MAX_SITE_DISTANCE = 4;
			helper.assertTrue(!Walls.unbuilt(level, v.hall()).isEmpty(), "setup: the wall has nothing to build");
			Walls.round(level, v.hall(), v.entity());
			List<BuildSite> open = Walls.openSites(level, v.hall());
			helper.assertTrue(open.size() == 1, open.size() + " wall sites opened past the benches' reach, not 1");
			helper.assertTrue(v.builders().get(0).getUUID().equals(open.get(0).builder()), "the village's builder isn't on the wall piece");
		} finally {
			Walls.ENABLED = was;
			Builders.MAX_SITE_DISTANCE = reach;
		}
		helper.succeed();
	}

	/**
	 * B86: an approved road and an approved wall, one builder. The roads' round runs first in the hall's tick, so it took
	 * every free builder and no wall piece opened until every road was built; now they take turns, each piece and segment
	 * finished before the next round.
	 */
	//$ gametest_ticks_batch AREA '100' '"wallTurns"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "wallTurns")
	public void theRoadsAndTheWallTakeTurnsWithAFreeBuilder(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Village v = village(helper, new BlockPos(14, 2, 14));
		CityPlan.Road lane = new CityPlan.Road(List.of(p(-8, 6), p(8, 6), p(8, -8)), CityPlan.Road.LANE, "", true);
		v.entity().setPlan(CityPlan.EMPTY.withWall(square(12).approvedWith("palisade")).addRoad(lane));
		boolean was = Walls.ENABLED;
		try {
			Walls.ENABLED = true;
			io.github.jcondedata.aliveworkplace.city.Roads.routeNow(level, v.hall());
			helper.assertTrue(v.entity().plan().roads().get(0).segments() >= 2, "setup: the lane is only "
				+ v.entity().plan().roads().get(0).segments() + " segment(s)");
			StringBuilder order = new StringBuilder();
			for (int round = 0; round < 6; round++) {
				// as the hall's tick runs them: the roads' round, then the wall's
				io.github.jcondedata.aliveworkplace.city.Roads.round(level, v.hall(), v.entity());
				Walls.round(level, v.hall(), v.entity());
				List<BuildSite> roads = io.github.jcondedata.aliveworkplace.city.Roads.openSegments(level, v.hall());
				List<BuildSite> walls = Walls.openSites(level, v.hall());
				helper.assertTrue(roads.size() + walls.size() == 1, "round " + round + ": " + roads.size() + " segments and " + walls.size()
					+ " wall pieces open with one builder");
				order.append(roads.isEmpty() ? 'W' : 'R');
				// the builder finishes it (gone from the sites, as a finished site is)
				for (BuildSite site : roads.isEmpty() ? walls : roads) {
					BuildSiteManager.get(level).remove(site.id());
				}
			}
			String went = order.toString();
			helper.assertTrue(!went.contains("RR") && !went.contains("WW"), "the builder went to " + went + " (R a road, W the wall), not in turns");
		} finally {
			Walls.ENABLED = was;
		}
		helper.succeed();
	}

	// ---- the village ------------------------------------------------------------------------------------------

	private record Village(BlockPos hall, VillageHallBlockEntity entity, ServerPlayer owner, List<Villager> builders, Villager steward) {
	}

	/** A hall with an owner, a Steward and one builder with a stocked bench. */
	private static Village village(GameTestHelper helper, BlockPos hallAt) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = ground(helper, hallAt);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		VillageHallBlockEntity entity = hall(helper, hall);
		entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		BlockPos table = hallAt.offset(-4, 0, 0);
		helper.setBlock(table, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(table.west(), Blocks.CHEST);
		Container chest = helper.getBlockEntity(table.west());
		chest.setItem(0, new ItemStack(Items.SPRUCE_LOG, 64));
		chest.setItem(1, new ItemStack(Items.STRIPPED_SPRUCE_LOG, 64));
		chest.setItem(2, new ItemStack(Items.SPRUCE_FENCE, 64));
		chest.setItem(3, new ItemStack(Items.SPRUCE_PLANKS, 64));
		chest.setItem(4, new ItemStack(Items.DARK_OAK_STAIRS, 64));
		chest.setItem(5, new ItemStack(Items.DARK_OAK_SLAB, 16));
		chest.setItem(6, new ItemStack(Items.LADDER, 16));
		chest.setItem(7, new ItemStack(Items.LANTERN, 8));
		chest.setItem(8, new ItemStack(Items.COBBLESTONE, 64));
		chest.setItem(9, new ItemStack(Items.STONE, 64));
		chest.setItem(10, new ItemStack(Items.ANDESITE, 64));
		chest.setItem(11, new ItemStack(Items.DIRT, 64));
		List<Villager> builders = new ArrayList<>();
		Villager builder = helper.spawn(EntityType.VILLAGER, table.east());
		Builders.employ(level, builder, helper.absolutePos(table));
		builders.add(builder);
		Villager steward = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, hallAt.north(2)));
		ItemStack plan = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(level, owner, plan, hall);
		helper.assertTrue(Stewards.appoint(owner, steward, plan) == InteractionResult.SUCCESS, "setup: Steward not appointed");
		return new Village(hall, entity, owner, builders, steward);
	}

	private static void record(ServerLevel level, ResourceLocation structure, BlockPos at) {
		Optional<Blueprint> blueprint = BlueprintLibrary.get(level, structure);
		if (blueprint.isPresent()) {
			BuildSiteManager.get(level).recordFinished(structure,
				new BlueprintData.Placement(Ids.of(level.dimension()), at, Rotation.NONE, Mirror.NONE), UUID.randomUUID());
		}
	}
}
