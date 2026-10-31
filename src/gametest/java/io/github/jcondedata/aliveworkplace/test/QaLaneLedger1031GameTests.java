package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CaravanRoads;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.CityPlans;
import io.github.jcondedata.aliveworkplace.city.PlayerBuilt;
import io.github.jcondedata.aliveworkplace.city.Roads;
import io.github.jcondedata.aliveworkplace.city.StewardSafety;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (qa-lane-b-1031-0338, B520 / 27.19): the Steward's own roads (a lane he lays from a finished house's door, his half
 * of a road to another village) never go through a 16x16x16 section of the player ledger; a road the owner draws on the
 * City Plan by hand does. Each village is laid out against the absolute section grid, so the boundary cases (the lane's
 * very tip in a marked section, the marked section one block past its tip) hold wherever the test grid puts the area.
 */
public class QaLaneLedger1031GameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	/** The village hall, the builder's bench and its chest, in the area's corner away from the street and the house. */
	private static final BlockPos HALL = new BlockPos(27, 2, 28);
	private static final BlockPos TABLE = new BlockPos(22, 2, 27);
	/** The street runs east-west at the chosen row from x 2 to x 18; the house stands south of it. */
	private static final int STREET_FROM = 2;
	private static final int STREET_TO = 18;
	private static final int HOUSE_X = 9;
	private static final int HOUSE_GAP = 6;

	/** A village with its street laid, a Steward and an idle builder. */
	private record Village(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, ServerPlayer owner, BuildSiteManager manager, int streetZ) {
	}

	private static BlockPos p(GameTestHelper helper, BlockPos hall, int x, int z) {
		BlockPos at = helper.absolutePos(new BlockPos(x, 2, z));
		return new BlockPos(at.getX() - hall.getX(), 0, at.getZ() - hall.getZ());
	}

	/** The street's row (relative z, 4 to 19) whose absolute z is {@code mod} modulo 16. */
	private static int streetRow(GameTestHelper helper, int mod) {
		int z0 = helper.absolutePos(BlockPos.ZERO).getZ();
		for (int z = 4; z < 20; z++) {
			if (Math.floorMod(z0 + z, 16) == mod) {
				return z;
			}
		}
		throw new IllegalStateException("no row");
	}

	/** Clean flat ground, a hall owned by a mock player, a builder at his bench, a seasoned Steward, nothing on the plan. */
	private static Village ground(GameTestHelper helper, int streetZ) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		Leftovers.stewardSites(helper);
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(BlockPos.ZERO);
		BoundingBox near = new BoundingBox(a.getX() - 40, level.getMinBuildHeight(), a.getZ() - 40, a.getX() + 70, level.getMaxBuildHeight(), a.getZ() + 70);
		BuildSiteManager manager = BuildSiteManager.get(level);
		for (BuildSite s : new ArrayList<>(manager.all())) {
			if (near.isInside(s.placement().origin())) {
				manager.remove(s.id());
			}
		}
		for (BuildSiteManager.Finished f : manager.finishedIn(level)) {
			if (near.isInside(f.placement().origin())) {
				manager.forgetFinished(f.placement());
			}
		}
		PlayerBuilt.get(level).forget(near);
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
				for (int y = 2; y < 14; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(TABLE.west(), Blocks.CHEST);
		Container chest = helper.getBlockEntity(TABLE.west());
		chest.setItem(0, new ItemStack(Items.STONE_BRICKS, 64));
		chest.setItem(1, new ItemStack(Items.CRACKED_STONE_BRICKS, 32));
		chest.setItem(2, new ItemStack(Items.COBBLESTONE, 64));
		chest.setItem(3, new ItemStack(Items.STONE_BRICK_STAIRS, 16));
		chest.setItem(4, new ItemStack(Items.DIRT, 64));
		Villager builder = helper.spawn(EntityType.VILLAGER, TABLE.east());
		Builders.employ(level, builder, helper.absolutePos(TABLE));
		Villager steward = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, HALL.north(2)));
		ItemStack planItem = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(level, owner, planItem, hall);
		helper.assertTrue(Stewards.appoint(owner, steward, planItem) == InteractionResult.SUCCESS, "setup: Steward not appointed");
		Leftovers.after(helper, () -> {
			for (BuildSite s : new ArrayList<>(Roads.openSegments(level, hall))) {
				manager.remove(s.id());
			}
		});
		return new Village(level, hall, entity, owner, manager, streetZ);
	}

	/** {@link #ground}, plus the owner's street at row {@code streetZ}, routed and laid as if built. */
	private static Village withStreet(GameTestHelper helper, int streetZ) {
		Village v = ground(helper, streetZ);
		v.entity.setPlan(CityPlan.EMPTY.addRoad(new CityPlan.Road(List.of(p(helper, v.hall, STREET_FROM, streetZ), p(helper, v.hall, STREET_TO, streetZ)),
			CityPlan.Road.STREET, "stonework", true)));
		helper.assertTrue(Roads.routeNow(v.level, v.hall), "setup: the street's routing paused");
		CityPlan.Road street = v.entity.plan().roads().get(0);
		for (int n = 0; n < street.segments(); n++) {
			street = v.entity.plan().roads().get(0);
			Optional<Roads.Segment> seg = Roads.segment(v.level, v.hall, street, n);
			helper.assertTrue(seg.isPresent(), "setup: street segment " + n + " has no blueprint");
			for (Blueprint.Entry e : seg.get().blueprint().blocks()) {
				v.level.setBlock(seg.get().placement().origin().offset(e.pos()), e.state(), Block.UPDATE_CLIENTS);
			}
			BuildSite s = v.manager.create(v.owner.getUUID(), "", seg.get().blueprint().id(), seg.get().placement());
			Roads.save(v.level, seg.get().blueprint());
			Roads.segmentBuilt(v.level, s, null);
			v.manager.remove(s.id());
		}
		helper.assertTrue(v.entity.plan().roads().get(0).finished(), "setup: the street isn't laid");
		return v;
	}

	/** A house finished south of the street: the Steward joins its door to the street with a lane by himself, routed. */
	private static CityPlan.Road houseLane(GameTestHelper helper, Village v) {
		BlueprintData.Placement placement = new BlueprintData.Placement(Ids.of(v.level.dimension()),
			helper.absolutePos(new BlockPos(HOUSE_X, 2, v.streetZ + HOUSE_GAP)), Rotation.NONE, Mirror.NONE);
		BuildSite house = v.manager.create(v.owner.getUUID(), "", AliveWorkplace.id("well"), placement);
		boolean was = Roads.ENABLED; // the Steward's roads switch is off in GameTests (WorkplaceConfig)
		boolean joined;
		try {
			Roads.ENABLED = true;
			joined = Roads.joinNearest(v.level, house);
		} finally {
			Roads.ENABLED = was;
		}
		helper.assertTrue(joined, "setup: no lane made for the finished house");
		v.manager.remove(house.id());
		helper.assertTrue(Roads.routeNow(v.level, v.hall), "setup: the lane's routing paused");
		helper.assertTrue(v.entity.plan().roads().size() == 2, "setup: no lane on the plan: " + v.entity.plan().roads());
		CityPlan.Road lane = v.entity.plan().roads().get(1);
		helper.assertTrue(lane.lane() && lane.routed() && lane.route().size() >= 3 && lane.segments() == 1,
			"setup: the lane isn't a routed one-segment lane: " + lane);
		return lane;
	}

	/** The lane's node nearest the street (its tip, on the street's south edge row), absolute. */
	private static BlockPos tip(Village v, CityPlan.Road lane) {
		BlockPos best = null;
		for (BlockPos o : lane.route()) {
			BlockPos at = o.offset(v.hall);
			if (best == null || at.getZ() < best.getZ()) {
				best = at;
			}
		}
		return best;
	}

	/** A player (the owner, by hand) places planks one block north of the lane's tip, on the street's middle: its section is marked. */
	private static BlockPos markPastTip(GameTestHelper helper, Village v, CityPlan.Road lane) {
		BlockPos tip = tip(v, lane);
		BlockPos spot = tip.north();
		helper.assertTrue(tip.getZ() == helper.absolutePos(new BlockPos(0, 0, v.streetZ + 1)).getZ(),
			"setup: the lane's tip " + tip + " isn't on the street's south edge, row " + (v.streetZ + 1) + ": " + lane.route());
		helper.assertTrue(v.level.getBlockState(spot).isAir(), "setup: no room north of the lane's tip at " + spot);
		helper.assertTrue(StewardSafetyGameTests.place(helper, v.owner, spot, Blocks.OAK_PLANKS), "setup: the player couldn't place at " + spot);
		helper.assertTrue(v.level.getBlockState(spot).is(Blocks.OAK_PLANKS) && PlayerBuilt.get(v.level).marked(spot),
			"setup: the player's planks didn't mark their section");
		return spot;
	}

	private static Roads.Segment segment(GameTestHelper helper, Village v, CityPlan.Road road, int n) {
		Optional<Roads.Segment> seg = Roads.segment(v.level, v.hall, road, n);
		helper.assertTrue(seg.isPresent(), "setup: segment " + n + " of " + road + " has no blueprint");
		return seg.get();
	}

	private static boolean opened(Village v, CityPlan.Road road, int n) {
		ResourceLocation id = Roads.segmentId(v.hall, road, n);
		return Roads.openSegments(v.level, v.hall).stream().anyMatch(s -> s.structure().equals(id));
	}

	private static void rounds(Village v) {
		for (int i = 0; i < 3; i++) {
			Roads.round(v.level, v.hall, v.entity);
		}
	}

	/**
	 * The lane's tip (the node on the street) is the only part of it in the marked section: the rest of the lane is in the
	 * next section south. Its one segment still waits; with the mark gone, it opens (the ledger was the only thing holding
	 * it back).
	 */
	//$ gametest_ticks_batch AREA '60' '"qaLaneLedger1031Tip"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaLaneLedger1031Tip")
	public void aLaneOnlyItsTipInAMarkedSectionStillWaits(GameTestHelper helper) {
		Village v = withStreet(helper, streetRow(helper, 14)); // the tip (row + 1) is a section's last row
		CityPlan.Road lane = houseLane(helper, v);
		BlockPos spot = markPastTip(helper, v, lane);
		BlockPos tip = tip(v, lane);
		long marked = SectionPos.asLong(spot);
		long others = lane.route().stream().map(o -> o.offset(v.hall)).filter(at -> !at.equals(tip) && SectionPos.asLong(at) == marked).count();
		helper.assertTrue(SectionPos.asLong(tip) == marked && others == 0, "setup: not only the tip is in the marked section: " + others + " more nodes");
		Roads.Segment seg = segment(helper, v, lane, 0);
		helper.assertTrue(PlayerBuilt.get(v.level).marked(seg.box()), "setup: the lane's box " + seg.box() + " doesn't reach the marked section");
		rounds(v);
		lane = v.entity.plan().roads().get(1);
		helper.assertTrue(!opened(v, lane, 0), "the Steward's own lane was opened with its tip in the player's section (planks at " + spot + ", box " + seg.box() + ")");
		helper.assertTrue(Roads.openSegments(v.level, v.hall).isEmpty(), "a road site opened anyway: " + Roads.openSegments(v.level, v.hall).size());
		PlayerBuilt.get(v.level).forget(new BoundingBox(spot));
		rounds(v);
		helper.assertTrue(opened(v, v.entity.plan().roads().get(1), 0), "with the mark gone the lane still didn't open: something other than the ledger holds it back");
		helper.succeed();
	}

	/**
	 * The other side of the boundary: the lane's tip is the first row of a section, so the marked section (one block north of
	 * the lane's tip) is one the lane's box never reaches. The lane opens.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaLaneLedger1031Past"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaLaneLedger1031Past")
	public void aMarkedSectionJustPastTheLanesTipDoesntHoldItBack(GameTestHelper helper) {
		Village v = withStreet(helper, streetRow(helper, 15)); // the tip (row + 1) is a section's first row
		CityPlan.Road lane = houseLane(helper, v);
		BlockPos spot = markPastTip(helper, v, lane);
		Roads.Segment seg = segment(helper, v, lane, 0);
		helper.assertTrue(!PlayerBuilt.get(v.level).marked(seg.box()), "setup: the lane's box " + seg.box() + " reaches the section marked at " + spot);
		helper.assertTrue(SectionPos.asLong(spot) != SectionPos.asLong(tip(v, lane)), "setup: the planks are in the tip's section");
		rounds(v);
		helper.assertTrue(opened(v, v.entity.plan().roads().get(1), 0), "the lane didn't open though the player's section (planks at " + spot
			+ ") is one block past its box " + seg.box());
		helper.succeed();
	}

	/**
	 * The owner takes the Steward's lane off the City Plan and draws the same line himself: that road is approved by hand,
	 * so it opens through the same marked section the lane waited at.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaLaneLedger1031Hand"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaLaneLedger1031Hand")
	public void theOwnerDrawingTheSameLineByHandBuildsThroughTheSection(GameTestHelper helper) {
		Village v = withStreet(helper, streetRow(helper, 14));
		CityPlan.Road lane = houseLane(helper, v);
		BlockPos spot = markPastTip(helper, v, lane);
		rounds(v);
		helper.assertTrue(!opened(v, v.entity.plan().roads().get(1), 0), "setup: the Steward's lane opened through the marked section");
		List<BlockPos> line = lane.points();
		helper.assertTrue(CityPlans.apply(v.owner, QaCityPlanGameTests.sent(v.level, CityPlans.Edit.removeRoad(v.hall, 1))), "the owner couldn't take the lane off");
		helper.assertTrue(CityPlans.apply(v.owner, QaCityPlanGameTests.sent(v.level, CityPlans.Edit.addRoad(v.hall, line, CityPlan.Road.LANE, "stonework"))),
			"the owner couldn't draw the line " + line);
		helper.assertTrue(v.entity.plan().roads().size() == 2, "the plan doesn't have the street and the owner's road: " + v.entity.plan().roads());
		helper.assertTrue(Roads.routeNow(v.level, v.hall), "the owner's road's routing paused");
		CityPlan.Road drawn = v.entity.plan().roads().get(1);
		helper.assertTrue(!drawn.lane() && !drawn.caravan() && drawn.byHand() && drawn.approved() && drawn.routed(), "the owner's road isn't approved by hand: " + drawn);
		Roads.Segment seg = segment(helper, v, drawn, 0);
		helper.assertTrue(PlayerBuilt.get(v.level).marked(seg.box()), "setup: the owner's road doesn't reach the marked section at " + spot);
		rounds(v);
		helper.assertTrue(opened(v, v.entity.plan().roads().get(1), 0), "the road the owner drew by hand didn't open through the marked section (planks at " + spot + ")");
		helper.assertTrue(v.level.getBlockState(spot).is(Blocks.OAK_PLANKS), "the player's planks are gone");
		helper.succeed();
	}

	/**
	 * Save and reload with the lane waiting: the hall's plan (with the lane) and the ledger both read back from what they
	 * saved, and the lane still waits; whether a road was drawn by hand isn't lost in the save.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaLaneLedger1031Reload"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaLaneLedger1031Reload")
	public void aWaitingLaneStillWaitsAfterSaveAndReload(GameTestHelper helper) {
		Village v = withStreet(helper, streetRow(helper, 14));
		CityPlan.Road lane = houseLane(helper, v);
		BlockPos spot = markPastTip(helper, v, lane);
		rounds(v);
		helper.assertTrue(!opened(v, v.entity.plan().roads().get(1), 0), "setup: the lane opened before the reload");
		CompoundTag hallTag = v.entity.saveWithoutMetadata(v.level.registryAccess());
		v.entity.setPlan(CityPlan.EMPTY);
		v.entity.loadWithComponents(hallTag, v.level.registryAccess());
		PlayerBuilt ledger = PlayerBuilt.get(v.level);
		PlayerBuilt back = PlayerBuilt.load(ledger.save(new CompoundTag(), v.level.registryAccess()), v.level.registryAccess());
		v.level.getDataStorage().set(PlayerBuilt.NAME, back);
		helper.assertTrue(PlayerBuilt.get(v.level) == back && back.marked(spot), "the reloaded ledger lost the player's section at " + spot);
		helper.assertTrue(v.entity.plan().roads().size() == 2, "the reloaded plan lost a road: " + v.entity.plan().roads());
		CityPlan.Road loaded = v.entity.plan().roads().get(1);
		helper.assertTrue(loaded.lane() && !loaded.byHand() && loaded.routed() && loaded.route().equals(lane.route()), "the lane didn't come back as the Steward's own: " + loaded);
		rounds(v);
		helper.assertTrue(!opened(v, v.entity.plan().roads().get(1), 0) && Roads.openSegments(v.level, v.hall).isEmpty(),
			"after a reload the Steward's lane was opened through the player's section at " + spot);
		helper.succeed();
	}

	/** The test's own chunk tickets, let go when it ends (as in CaravanRoadGameTests). */
	private static final net.minecraft.server.level.TicketType<net.minecraft.world.level.ChunkPos> TICKET =
		net.minecraft.server.level.TicketType.create("aliveworkplace_qa_lane_ledger_test", java.util.Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong));

	/** Loads the chunks within {@link CaravanRoads#LOADED_MARGIN} of the area, where a caravan half is routed only. */
	private static void load(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(BlockPos.ZERO);
		int m = CaravanRoads.LOADED_MARGIN + 16;
		List<net.minecraft.world.level.ChunkPos> held = new ArrayList<>();
		for (int cx = (a.getX() - m) >> 4; cx <= (a.getX() + 30 + m) >> 4; cx++) {
			for (int cz = (a.getZ() - m) >> 4; cz <= (a.getZ() + 30 + m) >> 4; cz++) {
				net.minecraft.world.level.ChunkPos pos = new net.minecraft.world.level.ChunkPos(cx, cz);
				level.getChunkSource().addRegionTicket(TICKET, pos, 2, pos);
				held.add(pos);
				level.getChunk(cx, cz);
			}
		}
		Leftovers.after(helper, () -> held.forEach(pos -> level.getChunkSource().removeRegionTicket(TICKET, pos, 2, pos)));
	}

	/** The Steward's half of a road to another village, along the area's row 8 from x 23 to x 5 (one segment), routed. */
	private static CityPlan.Road caravanHalf(GameTestHelper helper, Village v) {
		load(helper);
		CityPlan.Road half = new CityPlan.Road(List.of(p(helper, v.hall, 23, 8), p(helper, v.hall, 5, 8)), CityPlan.Road.STREET, "", true,
			List.of(), false, List.of(), false, 0, 0, Optional.of(new BlockPos(-400, 0, 0)));
		v.entity.setPlan(CityPlan.EMPTY.addRoad(half));
		helper.assertTrue(Roads.routeNow(v.level, v.hall), "setup: the half's routing paused");
		CityPlan.Road routed = v.entity.plan().roads().get(0);
		helper.assertTrue(routed.caravan() && !routed.byHand() && routed.routed() && routed.segments() == 1, "setup: not a routed one-segment caravan half: " + routed);
		return routed;
	}

	/** A player places planks beside the middle of the half (two blocks south of its line): that section is marked. */
	private static BlockPos markBesideHalf(GameTestHelper helper, Village v, CityPlan.Road half) {
		BlockPos mid = half.route().get(half.route().size() / 2).offset(v.hall);
		BlockPos spot = null;
		for (BlockPos c : List.of(mid.south(2), mid.north(2), mid.south(3), mid.north(3))) {
			if (SectionPos.asLong(c) == SectionPos.asLong(mid) && v.level.getBlockState(c).isAir()) {
				spot = c;
				break;
			}
		}
		helper.assertTrue(spot != null, "setup: no free spot beside the half's middle " + mid + " in its section");
		helper.assertTrue(StewardSafetyGameTests.place(helper, v.owner, spot, Blocks.OAK_PLANKS), "setup: the player couldn't place at " + spot);
		helper.assertTrue(PlayerBuilt.get(v.level).marked(mid), "setup: the planks didn't mark the half's section");
		return spot;
	}

	/** The Steward's half of a road to another village waits at a player's section; once the mark is gone it opens. */
	//$ gametest_ticks_batch AREA '60' '"qaLaneLedger1031Caravan"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaLaneLedger1031Caravan")
	public void aCaravanHalfWaitsAtAPlayersSection(GameTestHelper helper) {
		Village v = ground(helper, 0);
		CityPlan.Road half = caravanHalf(helper, v);
		BlockPos spot = markBesideHalf(helper, v, half);
		Roads.Segment seg = segment(helper, v, half, 0);
		helper.assertTrue(StewardSafety.check(v.level, v.hall, seg.box(), false).orElse(null) == StewardSafety.Refusal.PLAYER_BUILT,
			"setup: the half isn't refused for the ledger alone: " + StewardSafety.check(v.level, v.hall, seg.box(), false));
		rounds(v);
		helper.assertTrue(!opened(v, v.entity.plan().roads().get(0), 0), "the Steward's half of a caravan road was opened through the player's section (planks at " + spot + ")");
		PlayerBuilt.get(v.level).forget(seg.box());
		rounds(v);
		helper.assertTrue(opened(v, v.entity.plan().roads().get(0), 0), "with the mark gone the caravan half still didn't open");
		helper.succeed();
	}

	/**
	 * The half's far end runs towards another village's hall, nearer that hall than its own (the Other Village refusal a
	 * caravan half is let through by design). With a player's section on it, it still waits; without, it opens.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaLaneLedger1031Other"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaLaneLedger1031Other")
	public void aCaravanHalfNearTheOtherVillageStillKeepsToTheLedger(GameTestHelper helper) {
		Village v = ground(helper, 0);
		CityPlan.Road half = caravanHalf(helper, v);
		BlockPos other = helper.absolutePos(new BlockPos(1, 2, 2));
		v.level.setBlock(other, ModBlocks.VILLAGE_HALL.defaultBlockState(), Block.UPDATE_ALL);
		Leftovers.after(helper, () -> v.level.removeBlock(other, false));
		Roads.Segment seg = segment(helper, v, half, 0);
		helper.assertTrue(StewardSafety.check(v.level, v.hall, seg.box(), true).orElse(null) == StewardSafety.Refusal.OTHER_VILLAGE,
			"setup: the half's far end isn't nearer the other hall: " + StewardSafety.check(v.level, v.hall, seg.box(), true));
		BlockPos spot = markBesideHalf(helper, v, half);
		rounds(v);
		helper.assertTrue(!opened(v, v.entity.plan().roads().get(0), 0),
			"the caravan half near the other village was opened through the player's section (planks at " + spot + ")");
		PlayerBuilt.get(v.level).forget(seg.box());
		rounds(v);
		helper.assertTrue(opened(v, v.entity.plan().roads().get(0), 0), "unmarked, the caravan half towards the other village didn't open (its Other Village pass is gone)");
		helper.succeed();
	}
}
