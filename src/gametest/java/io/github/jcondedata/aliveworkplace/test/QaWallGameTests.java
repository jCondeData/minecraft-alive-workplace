package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.StewardDesk;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.city.WallKits;
import io.github.jcondedata.aliveworkplace.city.Walls;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA for ROADMAP 27.18 (walls along the wall line), from the spec, beyond WallGameTests: raided 6 days ago still wants
 * a wall; a bandit camp preying on the village wants one with no raid, and not once it's gone; the Steward's own line
 * round the zones, 4 blocks out, shown on the plan and not built before approval; wall kits as data (a malformed file
 * refused, a kit needing a missing mod skipped, ranks defaulting); a gate wherever a road crosses and none for a road
 * inside; towers at least every 28 blocks on a long run; each piece at its own ground height; at most 3 wall sites open;
 * and every sentence the wall shows a player, rendered with real arguments.
 */
public class QaWallGameTests implements FabricGameTest {
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

	private static VillageHallBlockEntity hall(GameTestHelper helper, BlockPos hall) {
		return (VillageHallBlockEntity) helper.getLevel().getBlockEntity(hall);
	}

	private static BlockPos p(int dx, int dz) {
		return new BlockPos(dx, 0, dz);
	}

	private static CityPlan.Wall square(int half) {
		return new CityPlan.Wall(List.of(p(-half, -half), p(half, -half), p(half, half), p(-half, half)), true);
	}

	private static WallKits.Kit palisade() {
		return WallKits.get("palisade").orElseThrow();
	}

	/** Raided 6 days ago (within the last 7): the village still wants its wall, and the Steward proposes it. */
	//$ gametest_ticks_batch AREA '40' '"qaWallSixDays"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaWallSixDays")
	public void raidedSixDaysAgoStillGetsAWallProposal(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Village v = village(helper, new BlockPos(14, 2, 14));
		v.entity().setPlan(CityPlan.EMPTY.withWall(square(12)));
		boolean was = Walls.ENABLED;
		try {
			Walls.ENABLED = true;
			v.entity().setLastRaidDay(Chronicle.day(level) - 6);
			helper.assertTrue(Walls.wanted(level, v.hall()), "a village raided 6 days ago doesn't want a wall");
			Walls.propose(level, v.hall(), v.steward());
			helper.assertValueEqual(StewardDesk.of(level, v.hall()).proposals().size(), 1, "wall proposals after a raid 6 days ago");
			// proposing again the next morning doesn't stack a second proposal
			Walls.propose(level, v.hall(), v.steward());
			helper.assertValueEqual(StewardDesk.of(level, v.hall()).proposals().size(), 1, "wall proposals after proposing twice");
		} finally {
			Walls.ENABLED = was;
		}
		helper.succeed();
	}

	/** A bandit camp preying on a village never raided: it wants a wall, and the Steward proposes it; the camp gone, it doesn't. */
	//$ gametest_ticks_batch AREA '60' '"qaWallBandits"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaWallBandits")
	public void aBanditCampNearbyWantsAWallAndNotOnceItIsGone(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Village v = village(helper, new BlockPos(6, 2, 6));
		BanditCamps.forget(level);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48),
				m -> m.getTags().contains(Lairs.TAG) || m.getTags().contains(BanditCamps.TAG)).forEach(Mob::discard);
			BanditCamps.forget(level);
		});
		v.entity().setPlan(CityPlan.EMPTY.withWall(square(4)));
		boolean was = Walls.ENABLED;
		try {
			Walls.ENABLED = true;
			helper.assertTrue(!Walls.wanted(level, v.hall()), "setup: a village never raided, no camp, wants a wall");
			helper.assertTrue(BanditCamps.found(level, v.hall(), helper.absolutePos(new BlockPos(18, 1, 18))) != null, "setup: no camp was made");
			helper.assertTrue(Walls.wanted(level, v.hall()), "a bandit camp preys on the village and it doesn't want a wall");
			Walls.propose(level, v.hall(), v.steward());
			helper.assertValueEqual(StewardDesk.of(level, v.hall()).proposals().size(), 1, "wall proposals with bandits camped nearby");
			BanditCamps.forget(level);
			helper.assertTrue(!Walls.wanted(level, v.hall()), "the camp gone and never raided, the village still wants a wall");
		} finally {
			Walls.ENABLED = was;
		}
		helper.succeed();
	}

	/**
	 * No wall line drawn: the Steward's own goes round the zones, 4 blocks out, onto the plan (a closed line, proposed,
	 * not approved), and nothing of it is built before it's approved: no pieces, no site after the hall's rounds.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaWallOwnLine"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaWallOwnLine")
	public void theStewardsOwnLineGoesFourBlocksRoundTheZonesForApproval(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Village v = village(helper, new BlockPos(14, 2, 14));
		BlockPos hall = v.hall();
		// a homes zone: the cells under helper x 8..20, z 8..20
		BitSet cells = new BitSet();
		for (int x = 8; x <= 20; x++) {
			for (int z = 8; z <= 20; z++) {
				cells.set(CityPlan.cellAt(hall, helper.absolutePos(new BlockPos(x, 2, z))));
			}
		}
		v.entity().setPlan(CityPlan.EMPTY.addZone("homes", "Homes 1", "").paint(0, cells));
		// the zone's blocks, as the plan's cells cover them
		int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
		for (int dx = -40; dx <= 40; dx++) {
			for (int dz = -40; dz <= 40; dz++) {
				int cell = CityPlan.cellAt(hall, hall.offset(dx, 0, dz));
				if (cell >= 0 && cells.get(cell)) {
					minX = Math.min(minX, dx);
					maxX = Math.max(maxX, dx);
					minZ = Math.min(minZ, dz);
					maxZ = Math.max(maxZ, dz);
				}
			}
		}
		boolean was = Walls.ENABLED;
		try {
			Walls.ENABLED = true;
			v.entity().setLastRaidDay(Chronicle.day(level));
			Walls.propose(level, hall, v.steward());
			CityPlan.Wall wall = v.entity().plan().wall().orElseThrow(() -> new net.minecraft.gametest.framework.GameTestAssertException(
				"no wall line on the plan after the proposal"));
			helper.assertTrue(wall.closed() && !wall.approved(), "the Steward's line isn't a closed, unapproved line: " + wall);
			helper.assertValueEqual(StewardDesk.of(level, hall).proposals().size(), 1, "proposals for the Steward's own line");
			int lx = wall.points().stream().mapToInt(BlockPos::getX).min().orElseThrow();
			int hx = wall.points().stream().mapToInt(BlockPos::getX).max().orElseThrow();
			int lz = wall.points().stream().mapToInt(BlockPos::getZ).min().orElseThrow();
			int hz = wall.points().stream().mapToInt(BlockPos::getZ).max().orElseThrow();
			// round the zones with room: exactly 4 blocks out on every side (B510: the east and south were 5 out)
			helper.assertTrue(minX - lx == 4 && minZ - lz == 4, "the line is " + (minX - lx) + " (west) and " + (minZ - lz)
				+ " (north) blocks out from the zone, not 4: zone x " + minX + ".." + maxX + ", z " + minZ + ".." + maxZ + "; line " + wall.points());
			helper.assertTrue(hx - maxX == 4 && hz - maxZ == 4, "the line is " + (hx - maxX) + " (east) and " + (hz - maxZ)
				+ " (south) blocks out from the zone, not 4: zone x " + minX + ".." + maxX + ", z " + minZ + ".." + maxZ + "; line " + wall.points());
			// shown for approval, not built
			helper.assertTrue(Walls.pieces(level, hall).isEmpty(), "the proposed wall has pieces to build before it's approved");
			for (int i = 0; i < 3; i++) {
				Walls.round(level, hall, v.entity());
			}
			helper.assertTrue(Walls.openSites(level, hall).isEmpty(), "a wall site opened before the wall was approved");
			helper.assertTrue(!Walls.proposedPieces(level, hall, palisade()).isEmpty(), "the proposal shows no pieces on the desk");
		} finally {
			Walls.ENABLED = was;
		}
		helper.succeed();
	}

	/**
	 * Wall kits as data: a file missing its gate, with a gate that isn't an id, or a rank that isn't one is refused; a kit
	 * that needs a mod that isn't here is skipped; one with no ranks is for every rank.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void wallKitFilesAreReadOrRefusedWhole(GameTestHelper helper) {
		String ok = "\"segment\": \"aliveworkplace:palisade\", \"corner_tower\": \"aliveworkplace:palisade_tower\"";
		helper.assertTrue(refused("{" + ok + "}"), "a kit without a gate was read");
		helper.assertTrue(refused("{" + ok + ", \"gate\": \"Not An Id!\"}"), "a kit whose gate isn't an id was read");
		helper.assertTrue(refused("{" + ok + ", \"gate\": \"aliveworkplace:palisade_gate\", \"min_rank\": \"empire\"}"), "a kit with a made-up rank was read");
		helper.assertTrue(read("{" + ok + ", \"gate\": \"aliveworkplace:palisade_gate\", \"requires\": [\"no_such_mod_here\"]}") == null,
			"a kit needing a mod that isn't here was read");
		WallKits.Kit any = read("{" + ok + ", \"gate\": \"aliveworkplace:palisade_gate\"}");
		helper.assertTrue(any != null && any.minRank() == VillageRanks.Rank.HAMLET && any.maxRank() == VillageRanks.Rank.CITY,
			"a kit with no ranks isn't for every rank: " + any);
		WallKits.Kit town = read("{" + ok + ", \"gate\": \"aliveworkplace:palisade_gate\", \"min_rank\": \"town\"}");
		helper.assertTrue(town != null && !town.fits(VillageRanks.Rank.VILLAGE) && town.fits(VillageRanks.Rank.TOWN) && town.fits(VillageRanks.Rank.CITY),
			"a kit from a Town: " + town);
		helper.succeed();
	}

	private static WallKits.Kit read(String json) {
		try {
			Method m = WallKits.class.getDeclaredMethod("read", String.class, JsonObject.class);
			m.setAccessible(true);
			return (WallKits.Kit) m.invoke(null, "test", JsonParser.parseString(json).getAsJsonObject());
		} catch (InvocationTargetException e) {
			throw new IllegalArgumentException(e.getCause());
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static boolean refused(String json) {
		try {
			read(json);
			return false;
		} catch (IllegalArgumentException e) {
			return true;
		}
	}

	/** A gate wherever a road crosses: a street through the village crosses twice (two gates); a lane inside gets none. */
	//$ gametest_ticks_batch AREA '40' '"qaWallGates"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaWallGates")
	public void aGateWhereverARoadCrossesAndNoneForARoadInside(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = ground(helper, new BlockPos(14, 2, 14));
		CityPlan inside = CityPlan.EMPTY.withWall(square(20))
			.addRoad(new CityPlan.Road(List.of(p(-8, 4), p(8, 4)), CityPlan.Road.STREET, "", true));
		hall(helper, hall).setPlan(inside);
		long gates = Walls.layout(level, hall, hall(helper, hall).plan(), palisade()).stream().filter(x -> x.kind() == Walls.Kind.GATE).count();
		helper.assertValueEqual((int) gates, 0, "gates for a street that stays inside the wall");
		CityPlan through = CityPlan.EMPTY.withWall(square(20))
			.addRoad(new CityPlan.Road(List.of(p(0, -36), p(0, 36)), CityPlan.Road.STREET, "", true));
		hall(helper, hall).setPlan(through);
		List<Walls.Piece> pieces = Walls.layout(level, hall, hall(helper, hall).plan(), palisade());
		List<Walls.Piece> gateList = pieces.stream().filter(x -> x.kind() == Walls.Kind.GATE).toList();
		helper.assertValueEqual(gateList.size(), 2, "gates for a street through the village (crossing the line twice)");
		helper.assertTrue(gateList.stream().anyMatch(g -> Math.abs(g.box().getCenter().getZ() - (hall.getZ() - 20)) <= 4)
			&& gateList.stream().anyMatch(g -> Math.abs(g.box().getCenter().getZ() - (hall.getZ() + 20)) <= 4),
			"the gates aren't on the north and south lines: " + gateList.stream().map(Walls.Piece::box).toList());
		helper.assertTrue(gateList.stream().allMatch(g -> Math.abs(g.box().getCenter().getX() - hall.getX()) <= 4), "a gate isn't on the street");
		helper.succeed();
	}

	/** A run of 100 blocks: a tower at least every 28 blocks (no stretch of wall between two towers longer than 28). */
	//$ gametest_ticks_batch AREA '40' '"qaWallTowers"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaWallTowers")
	public void aTowerAtLeastEvery28BlocksAlongALongRun(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = ground(helper, new BlockPos(14, 2, 14));
		hall(helper, hall).setPlan(CityPlan.EMPTY.withWall(square(50)));
		List<Walls.Piece> pieces = Walls.layout(level, hall, hall(helper, hall).plan(), palisade());
		int north = hall.getZ() - 50;
		List<Walls.Piece> towers = pieces.stream().filter(x -> x.kind() == Walls.Kind.TOWER && Math.abs(x.box().getCenter().getZ() - north) <= 5)
			.sorted(Comparator.comparingInt(x -> x.box().minX())).toList();
		helper.assertTrue(towers.size() >= 5, "only " + towers.size() + " towers along a 100-block run");
		for (int i = 0; i + 1 < towers.size(); i++) {
			int gap = towers.get(i + 1).box().minX() - towers.get(i).box().maxX() - 1;
			helper.assertTrue(gap <= Walls.TOWER_EVERY, gap + " blocks of wall between towers " + i + " and " + (i + 1) + ", more than 28");
		}
		helper.succeed();
	}

	/** Each segment sits at its own ground height: the wall's north run over ground two blocks higher stands two blocks higher. */
	//$ gametest_ticks_batch AREA '40' '"qaWallGround"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaWallGround")
	public void eachPieceSitsAtItsOwnGroundHeight(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = ground(helper, new BlockPos(14, 2, 14));
		// the area's air up to its top and no barrier lid, so nothing up there is ground (as WallGameTests does)
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				for (int y = 16; y < 30; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 2, 0)), helper.absolutePos(new BlockPos(29, 29, 29))).forEach(at -> {
			if (level.getBlockState(at).is(Blocks.BARRIER)) {
				level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
			}
		});
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z <= 9; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 2, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 3, z), Blocks.GRASS_BLOCK);
			}
		}
		hall(helper, hall).setPlan(CityPlan.EMPTY.withWall(square(10)));
		List<Walls.Piece> pieces = Walls.layout(level, hall, hall(helper, hall).plan(), palisade());
		int northZ = helper.absolutePos(new BlockPos(0, 0, 9)).getZ();
		int southZ = helper.absolutePos(new BlockPos(0, 0, 11)).getZ();
		List<Walls.Piece> high = pieces.stream().filter(x -> x.box().maxZ() <= northZ && x.box().minZ() >= helper.absolutePos(BlockPos.ZERO).getZ()).toList();
		List<Walls.Piece> low = pieces.stream().filter(x -> x.box().minZ() >= southZ && x.box().maxZ() <= helper.absolutePos(new BlockPos(0, 0, 29)).getZ()).toList();
		helper.assertTrue(!high.isEmpty() && !low.isEmpty(), "setup: " + high.size() + " pieces on the high ground, " + low.size() + " on the low");
		for (Walls.Piece x : high) {
			helper.assertValueEqual(x.box().minY(), hall.getY() + 2, "the bottom of " + x.kind() + " " + x.index() + " on ground two blocks higher");
		}
		for (Walls.Piece x : low) {
			helper.assertValueEqual(x.box().minY(), hall.getY(), "the bottom of " + x.kind() + " " + x.index() + " on the low ground");
		}
		helper.succeed();
	}

	/** At most 3 wall sites open at once: five free builders, an approved wall with plenty to build, three sites. */
	//$ gametest_ticks_batch AREA '60' '"qaWallThreeOpen"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaWallThreeOpen")
	public void atMostThreeWallSitesOpenAtOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Village v = village(helper, new BlockPos(14, 2, 14));
		for (int i = 0; i < 4; i++) {
			BlockPos table = new BlockPos(6 + 3 * i, 2, 21);
			helper.setBlock(table, ModBlocks.BLUEPRINT_TABLE);
			Villager b = helper.spawn(EntityType.VILLAGER, table.east());
			Builders.employ(level, b, helper.absolutePos(table));
		}
		v.entity().setPlan(CityPlan.EMPTY.withWall(square(12).approvedWith("palisade")));
		boolean was = Walls.ENABLED;
		try {
			Walls.ENABLED = true;
			helper.assertTrue(Walls.unbuilt(level, v.hall()).size() > 3, "setup: the wall has only " + Walls.unbuilt(level, v.hall()).size() + " pieces");
			for (int i = 0; i < 3; i++) {
				Walls.round(level, v.hall(), v.entity());
			}
			helper.assertValueEqual(Walls.openSites(level, v.hall()).size(), Walls.MAX_OPEN, "wall sites open with five free builders");
			helper.assertValueEqual(Walls.MAX_OPEN, 3, "the most wall sites open at once");
		} finally {
			Walls.ENABLED = was;
		}
		helper.succeed();
	}

	/**
	 * Every sentence the wall shows a player: the proposal's name and reason, the desk's pieces and length lines, the
	 * chronicle's "set going" line on approval, each rendered with real arguments and as en_us.json has it.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaWallSentences"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaWallSentences")
	public void everyWallSentenceReadsAsWritten(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Village v = village(helper, new BlockPos(14, 2, 14));
		v.entity().setPlan(CityPlan.EMPTY.withWall(square(12)));
		boolean was = Walls.ENABLED;
		try {
			Walls.ENABLED = true;
			v.entity().setLastRaidDay(Chronicle.day(level));
			Walls.propose(level, v.hall(), v.steward());
			StewardDesk.Proposal proposal = StewardDesk.of(level, v.hall()).proposals().get(0);
			helper.assertValueEqual(proposal.name().getString(), "A Palisade wall", "the proposal's name");
			helper.assertValueEqual(proposal.reason().getString(), "The village was raided in the last 7 days, or bandits camp nearby", "the proposal's reason");
			helper.assertValueEqual(palisade().title().getString(), "Palisade", "the Palisade kit's name");
			helper.assertValueEqual(WallKits.get("stone").orElseThrow().title().getString(), "Stone", "the Stone kit's name");
			int[] counts = Walls.counts(Walls.proposedPieces(level, v.hall(), palisade()));
			String pieces = net.minecraft.network.chat.Component.translatable("screen.aliveworkplace.desk.wall_pieces",
				counts[Walls.Kind.SEGMENT.ordinal()], counts[Walls.Kind.TOWER.ordinal()], counts[Walls.Kind.GATE.ordinal()]).getString();
			helper.assertValueEqual(pieces, counts[0] + " segments, " + counts[1] + " towers and " + counts[2] + " gates", "the desk's pieces line");
			helper.assertValueEqual(Walls.lineLength(level, v.hall()), 96, "the length of a 24-block square line");
			helper.assertValueEqual(net.minecraft.network.chat.Component.translatable("screen.aliveworkplace.desk.wall_line", 96).getString(),
				"Along the wall line, 96 blocks round", "the desk's length line");
			helper.assertTrue(StewardDesk.approve(level, v.hall(), v.owner(), proposal.id()).ok(), "the wall wasn't approved");
			String name = v.steward().getDisplayName().getString();
			List<String> lines = v.entity().chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.PLANS).map(e -> e.text().getString()).toList();
			helper.assertTrue(lines.contains(name + " set the village's Palisade wall going"), "the chronicle on approval: " + lines + " (the Steward is " + name + ")");
		} finally {
			Walls.ENABLED = was;
		}
		helper.succeed();
	}

	// ---- the village (as WallGameTests') ------------------------------------------------------------------------

	private record Village(BlockPos hall, VillageHallBlockEntity entity, ServerPlayer owner, Villager steward) {
	}

	private static Village village(GameTestHelper helper, BlockPos hallAt) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = ground(helper, hallAt);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		VillageHallBlockEntity entity = hall(helper, hall);
		entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		BlockPos table = hallAt.offset(-4, 0, 0);
		if (table.getX() < 1) {
			table = hallAt.offset(4, 0, 0);
		}
		helper.setBlock(table, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(table.south(), Blocks.CHEST);
		Container chest = helper.getBlockEntity(table.south());
		chest.setItem(0, new ItemStack(Items.SPRUCE_LOG, 64));
		chest.setItem(1, new ItemStack(Items.SPRUCE_FENCE, 64));
		Villager builder = helper.spawn(EntityType.VILLAGER, table.east());
		Builders.employ(level, builder, helper.absolutePos(table));
		Villager steward = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, hallAt.south(2)));
		ItemStack plan = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(level, owner, plan, hall);
		helper.assertTrue(Stewards.appoint(owner, steward, plan) == InteractionResult.SUCCESS, "setup: Steward not appointed");
		return new Village(hall, entity, owner, steward);
	}
}
