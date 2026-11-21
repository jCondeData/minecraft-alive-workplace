package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.guard.BattleStations;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.FirewardenWork;
import io.github.jcondedata.aliveworkplace.work.Walker;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * QA lane C (2026-11-21), the defence buildings rebuilt in 51.8 (Barracks I-III, Guard House, Fire Station, Gatehouse
 * I-II, Lookout Tower I-IV) and the Royal Hall of 51.9 (I-II), written from their blind briefs
 * ({@code tools/blueprints/briefs/<family>.md}) rather than the code: what a guard, a firewarden, an archer and a player
 * meet in the finished building.
 *
 * <p>The walk is a villager's (as in {@link QaRebuiltCivic1121cGameTests}): wooden doors open, a fence gate stays as drawn,
 * nobody stands on a fence, wall or gate, and it starts in the street in front (the z = 0 side). For the archers' stations
 * the walk climbs no ladder ("villagers can't path up a ladder", lookout_tower.md and gatehouse.md: a guard is lifted onto
 * a station once he stands within {@link BattleStations#CLIMB_REACH} flat of it and 1.5 or more below). The live tests
 * check the same with the game's own pathfinder on the placed drawing.
 */
public class QaRebuiltDefense1121cGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	// ---- drawings -----------------------------------------------------------------------------------------------------

	static Blueprint blueprint(GameTestHelper helper, StarterBlueprints.Entry entry) {
		return BlueprintLibrary.get(helper.getLevel(), entry.id()).orElseThrow(() -> new AssertionError("missing " + entry.id()));
	}

	/** The drawing's cells (a basement or cellar already below y = 0). */
	static Map<BlockPos, BlockState> cells(Blueprint blueprint) {
		return QaBasementGameTests.blocks(blueprint);
	}

	private static BlockState at(Map<BlockPos, BlockState> cells, BlockPos pos) {
		return QaBasementGameTests.at(cells, pos);
	}

	/** Every point of interest a villager is drawn to (job sites, bells, the mod's workstations), not a bed. */
	private static Map<BlockPos, BlockState> pois(Map<BlockPos, BlockState> cells) {
		Map<BlockPos, BlockState> out = new HashMap<>();
		cells.forEach((p, s) -> {
			if (PoiTypes.forState(s).filter(h -> !h.is(PoiTypes.HOME) && !h.is(PoiTypes.LIGHTNING_ROD) && !h.is(PoiTypes.NETHER_PORTAL)
				&& !h.is(PoiTypes.LODESTONE) && !h.is(PoiTypes.BEEHIVE) && !h.is(PoiTypes.BEE_NEST)).isPresent()) {
				out.put(p, s);
			}
		});
		return out;
	}

	private static List<BlockPos> where(Map<BlockPos, BlockState> cells, Block block) {
		return cells.entrySet().stream().filter(e -> e.getValue().is(block)).map(Map.Entry::getKey).sorted().toList();
	}

	private static List<BlockPos> chests(Map<BlockPos, BlockState> cells) {
		return cells.entrySet().stream().filter(e -> e.getValue().getBlock() instanceof ChestBlock).map(Map.Entry::getKey).sorted().toList();
	}

	private static List<BlockPos> bedHeads(Map<BlockPos, BlockState> cells) {
		return cells.entrySet().stream().filter(e -> e.getValue().getBlock() instanceof BedBlock
			&& e.getValue().getValue(BedBlock.PART) == BedPart.HEAD).map(Map.Entry::getKey).sorted().toList();
	}

	private static String name(BlockState s) {
		return BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
	}

	private static String shortList(Map<BlockPos, BlockState> m) {
		List<String> out = new ArrayList<>();
		m.forEach((p, s) -> out.add(name(s) + " " + p.toShortString()));
		out.sort(null);
		return out.toString();
	}

	private static boolean within(BlockPos a, BlockPos b, int across, int up) {
		return Math.abs(a.getX() - b.getX()) <= across && Math.abs(a.getZ() - b.getZ()) <= across && Math.abs(a.getY() - b.getY()) <= up;
	}

	// ---- a villager's walk ------------------------------------------------------------------------------------------

	private static boolean open(BlockState state) {
		if (state.getBlock() instanceof DoorBlock && !state.is(Blocks.IRON_DOOR)) {
			return true;
		}
		if (state.is(Blocks.LADDER) || state.is(Blocks.JIGSAW) || state.is(Blocks.STRUCTURE_VOID)) {
			return true;
		}
		var shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
		return shape.isEmpty() || shape.max(Direction.Axis.Y) <= 0.2;
	}

	private static boolean noFloor(BlockState state) {
		return state.getBlock() instanceof FenceBlock || state.getBlock() instanceof WallBlock || state.getBlock() instanceof FenceGateBlock;
	}

	private static boolean standable(Map<BlockPos, BlockState> cells, BlockPos feet, boolean ladders) {
		BlockState here = at(cells, feet);
		if (!open(here) || !open(at(cells, feet.above())) || !here.getFluidState().isEmpty()) {
			return false;
		}
		BlockState below = at(cells, feet.below());
		return (ladders && (here.is(Blocks.LADDER) || below.is(Blocks.LADDER))) || (!open(below) && !noFloor(below) && below.getFluidState().isEmpty());
	}

	/** A walk's bounds: x from {@code minX} to {@code maxX}, z from -2 to {@code sz} + 1. */
	record Bounds(int minX, int maxX, int sz, int top, boolean ladders, int wallFrom, int width) {
		Bounds(int minX, int maxX, int sz, int top, boolean ladders) {
			this(minX, maxX, sz, top, ladders, Integer.MAX_VALUE, 0);
		}

		static Bounds round(Blueprint b, boolean ladders) {
			return new Bounds(-2, b.size().getX() + 1, b.size().getZ(), b.size().getY(), ladders);
		}

		/** A wall (as the siege yards stand one) meets the build's ends from row {@code wallFrom} out: nobody walks round. */
		boolean walled(BlockPos p) {
			return p.getZ() >= wallFrom && (p.getX() < 0 || p.getX() >= width);
		}
	}

	/** Every spot a villager reaches on foot from {@code starts}: level, a step up, up to three down, ladders if allowed. */
	static Set<BlockPos> walk(Map<BlockPos, BlockState> cells, List<BlockPos> starts, Bounds bounds) {
		Set<BlockPos> reached = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		for (BlockPos s : starts) {
			if (standable(cells, s, bounds.ladders()) && reached.add(s)) {
				queue.add(s);
			}
		}
		while (!queue.isEmpty()) {
			BlockPos pos = queue.poll();
			List<BlockPos> next = new ArrayList<>();
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos side = pos.relative(d);
				next.add(side);
				if (open(at(cells, pos.above(2)))) {
					next.add(side.above());
				}
				for (int drop = 1; drop <= 3; drop++) {
					if (!open(at(cells, side.below(drop - 1))) || !open(at(cells, side.below(drop - 1).above()))) {
						break;
					}
					next.add(side.below(drop));
				}
			}
			if (bounds.ladders()) {
				if (at(cells, pos).is(Blocks.LADDER) || at(cells, pos.above()).is(Blocks.LADDER)) {
					next.add(pos.above());
				}
				if (at(cells, pos.below()).is(Blocks.LADDER)) {
					next.add(pos.below());
				}
			}
			for (BlockPos n : next) {
				if (n.getX() < bounds.minX() || n.getX() > bounds.maxX() || n.getZ() < -2 || n.getZ() > bounds.sz() + 1 || n.getY() < -8
					|| n.getY() > bounds.top() || bounds.walled(n)) {
					continue;
				}
				if (!reached.contains(n) && standable(cells, n, bounds.ladders())) {
					reached.add(n);
					queue.add(n);
				}
			}
		}
		return reached;
	}

	static List<BlockPos> street(int minX, int maxX) {
		List<BlockPos> out = new ArrayList<>();
		for (int x = minX; x <= maxX; x++) {
			out.add(new BlockPos(x, 0, -1));
			out.add(new BlockPos(x, 0, -2));
		}
		return out;
	}

	private static Set<BlockPos> walkFromStreet(Blueprint blueprint, Map<BlockPos, BlockState> cells, boolean ladders) {
		Bounds b = Bounds.round(blueprint, ladders);
		return walk(cells, street(b.minX(), b.maxX()), b);
	}

	/** Whether a reached spot lies within {@code across} blocks (each way) and one up or down of {@code target}. */
	private static boolean near(Set<BlockPos> reached, BlockPos target, int across) {
		for (BlockPos p : BlockPos.betweenClosed(target.offset(-across, -1, -across), target.offset(across, 1, across))) {
			if (reached.contains(p)) {
				return true;
			}
		}
		return false;
	}

	/** Whether a guard on a reached spot is lifted onto the station standing on {@code spot} (BattleStations' climb). */
	static boolean climbable(Set<BlockPos> reached, BlockPos spot) {
		BlockPos stand = spot.above();
		for (BlockPos p : reached) {
			double dx = p.getX() - stand.getX();
			double dz = p.getZ() - stand.getZ();
			if (stand.getY() - p.getY() >= 1.5 && dx * dx + dz * dz <= BattleStations.CLIMB_REACH * BattleStations.CLIMB_REACH) {
				return true;
			}
		}
		return false;
	}

	/** A bed's head has a block behind it (the cell past the head, along the bed's facing, is not open). */
	private static boolean headToAWall(Map<BlockPos, BlockState> cells, BlockPos head) {
		Direction facing = at(cells, head).getValue(BedBlock.FACING);
		return !open(at(cells, head.relative(facing)));
	}

	// ---- Barracks ---------------------------------------------------------------------------------------------------

	private static final List<StarterBlueprints.Entry> BARRACKS = List.of(StarterBlueprints.BARRACKS, StarterBlueprints.BARRACKS_2,
		StarterBlueprints.BARRACKS_3);

	/**
	 * barracks.md, "Must have": grindstones and no other workstation, I exactly 2, II and III exactly 4, all above the
	 * ground; beds I 2, II and III 4; each grindstone walked up to from the street, with a chest within 8 across and 4 up
	 * or down (the Guard's gear chest), and a Training Dummy within 12 across and 4 up or down that has a spot within 2.5
	 * blocks a villager walks to (or the guards never train); every bed's head walked to, its head against a wall.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEachBarracksLevelHasItsGuardsGrindstonesChestsDummyAndBeds(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (StarterBlueprints.Entry entry : BARRACKS) {
			String n = entry.id().getPath();
			int guards = entry == StarterBlueprints.BARRACKS ? 2 : 4;
			Blueprint blueprint = blueprint(helper, entry);
			Map<BlockPos, BlockState> cells = cells(blueprint);
			List<BlockPos> grindstones = where(cells, Blocks.GRINDSTONE);
			if (grindstones.size() != guards || grindstones.stream().anyMatch(g -> g.getY() < 0)) {
				problems.add(n + ": grindstones " + grindstones + ", want " + guards + " above the ground");
			}
			Map<BlockPos, BlockState> pois = pois(cells);
			pois.values().removeIf(s -> s.is(Blocks.GRINDSTONE));
			if (!pois.isEmpty()) {
				problems.add(n + ": other job blocks " + shortList(pois));
			}
			List<BlockPos> beds = bedHeads(cells);
			if (beds.size() != guards) {
				problems.add(n + ": beds " + beds + ", want " + guards);
			}
			Set<BlockPos> reached = walkFromStreet(blueprint, cells, true);
			List<BlockPos> chests = chests(cells);
			List<BlockPos> dummies = where(cells, ModBlocks.TRAINING_DUMMY);
			if (dummies.isEmpty()) {
				problems.add(n + ": no Training Dummy");
			}
			for (BlockPos g : grindstones) {
				if (!near(reached, g, 2)) {
					problems.add(n + ": grindstone " + g.toShortString() + " not walked to from the street");
				}
				if (chests.stream().noneMatch(c -> within(c, g, 8, 4))) {
					problems.add(n + ": no chest within 8 of grindstone " + g.toShortString());
				}
				List<BlockPos> its = dummies.stream().filter(d -> within(d, g, 12, 4)).toList();
				if (its.isEmpty()) {
					problems.add(n + ": no Training Dummy within 12 of grindstone " + g.toShortString());
				} else if (its.stream().noneMatch(d -> reached.stream().anyMatch(p -> p.distSqr(d) <= 2.5 * 2.5))) {
					problems.add(n + ": no walked spot within 2.5 of the dummy " + its + " of grindstone " + g.toShortString());
				}
			}
			for (BlockPos b : beds) {
				if (!near(reached, b, 2)) {
					problems.add(n + ": bed " + b.toShortString() + " not walked to");
				}
				if (!headToAWall(cells, b)) {
					problems.add(n + ": bed " + b.toShortString() + " has no wall at its head");
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * barracks.md, "Kept cells": every chest of I stays a chest in the same cell in II ("a chest left where II wants
	 * something else would block the upgrade"), and I's grindstones and beds stay where they are ("guards already work
	 * and sleep there"); the same from II to III.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaBarracksUpgradesKeepTheGuardsGrindstonesBedsAndChests(GameTestHelper helper) {
		helper.assertTrue(kept(helper, BARRACKS).isEmpty(), String.join("; ", kept(helper, BARRACKS)));
		helper.succeed();
	}

	/** What a level lost of the one below: chests, job blocks and bed heads not in the same cell as the same block. */
	private static List<String> kept(GameTestHelper helper, List<StarterBlueprints.Entry> chain) {
		List<String> problems = new ArrayList<>();
		for (int i = 1; i < chain.size(); i++) {
			Map<BlockPos, BlockState> lower = cells(blueprint(helper, chain.get(i - 1)));
			Map<BlockPos, BlockState> upper = cells(blueprint(helper, chain.get(i)));
			List<String> lost = new ArrayList<>();
			lower.forEach((p, s) -> {
				boolean watched = s.getBlock() instanceof ChestBlock || s.is(Blocks.GRINDSTONE) || s.is(ModBlocks.GUARD_POST)
					|| s.is(ModBlocks.TRAINING_DUMMY) || (s.getBlock() instanceof EntityBlock && !s.is(BlockTags.BEDS) && !s.is(BlockTags.ALL_SIGNS)
					&& !s.is(BlockTags.BANNERS) && !s.is(BlockTags.CAMPFIRES) && !s.is(Blocks.BELL))
					|| (s.getBlock() instanceof BedBlock && s.getValue(BedBlock.PART) == BedPart.HEAD);
				if (watched && !at(upper, p).is(s.getBlock())) {
					lost.add(name(s) + " " + p.toShortString() + " -> " + name(at(upper, p)));
				}
			});
			if (!lost.isEmpty()) {
				lost.sort(null);
				problems.add(chain.get(i).id().getPath() + " lost " + lost);
			}
		}
		return problems;
	}

	// ---- Guard House ------------------------------------------------------------------------------------------------

	/**
	 * guard_house.md: exactly one grindstone, no Guard Post, no barrel and no other job block; a chest within 8 across and
	 * 4 up or down of it (without one no Weaponsmith brings this Guard a weapon); at least one bed; the grindstone, the
	 * chest and the bed walked to from the street without a fence gate or trapdoor on the way in.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheGuardHousesPostHasAGearChestAndABedAGuardWalksTo(GameTestHelper helper) {
		Blueprint blueprint = blueprint(helper, StarterBlueprints.GUARD_HOUSE);
		Map<BlockPos, BlockState> cells = cells(blueprint);
		List<BlockPos> grindstones = where(cells, Blocks.GRINDSTONE);
		helper.assertTrue(grindstones.size() == 1, "grindstones: " + grindstones);
		helper.assertTrue(where(cells, ModBlocks.GUARD_POST).isEmpty(), "a Guard Post: " + where(cells, ModBlocks.GUARD_POST));
		helper.assertTrue(where(cells, Blocks.BARREL).isEmpty(), "a barrel: " + where(cells, Blocks.BARREL));
		Map<BlockPos, BlockState> pois = pois(cells);
		pois.values().removeIf(s -> s.is(Blocks.GRINDSTONE) || s.is(Blocks.BELL)); // (a bell is not a job block, guard_house.md)
		helper.assertTrue(pois.isEmpty(), "other job blocks: " + shortList(pois));
		BlockPos g = grindstones.get(0);
		List<BlockPos> chests = chests(cells).stream().filter(c -> within(c, g, 8, 4)).toList();
		helper.assertTrue(!chests.isEmpty(), "no chest within 8 across and 4 up or down of the grindstone " + g.toShortString() + ": " + chests(cells));
		Set<BlockPos> reached = walkFromStreet(blueprint, cells, true);
		helper.assertTrue(near(reached, g, 2), "the grindstone " + g.toShortString() + " can't be walked to from the street");
		helper.assertTrue(chests.stream().anyMatch(c -> near(reached, c, 2)), "no gear chest by the grindstone can be walked to: " + chests);
		List<BlockPos> beds = bedHeads(cells);
		helper.assertTrue(!beds.isEmpty(), "no bed");
		helper.assertTrue(beds.stream().allMatch(b -> near(reached, b, 2)), "a bed's head can't be walked to: " + beds);
		helper.succeed();
	}

	// ---- Fire Station -----------------------------------------------------------------------------------------------

	/** Whether water in a cell next to {@code s} stays there (FlowingFluid: it can't flow into a door, ladder, sign or solid). */
	private static boolean holdsWater(BlockState s) {
		return !s.getFluidState().isEmpty() || s.blocksMotion() || s.getBlock() instanceof DoorBlock || s.is(Blocks.LADDER)
			|| s.is(BlockTags.ALL_SIGNS) || s.getBlock() instanceof SimpleWaterloggedBlock;
	}

	/**
	 * fire_station.md, "Water the Firewarden can actually fill his buckets at": every water cell a source block, held in on
	 * every side and below so it never flows out; a basin of at least 2 water in the layer y = 1; a spot a villager stands
	 * on within 4.5 blocks of the grindstone (where a jobless villager is handed his water bucket), walked to from the
	 * street; the watch loft (y 6 to 7) reached by the ladder from inside (check.py).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheFireStationsWaterStaysInItsBasinAndTheGrindstoneIsWalkedTo(GameTestHelper helper) {
		Blueprint blueprint = blueprint(helper, StarterBlueprints.FIRE_STATION);
		Map<BlockPos, BlockState> cells = cells(blueprint);
		List<String> problems = new ArrayList<>();
		List<BlockPos> water = cells.entrySet().stream().filter(e -> !e.getValue().getFluidState().isEmpty()).map(Map.Entry::getKey).sorted().toList();
		long basin = water.stream().filter(p -> p.getY() == 1 && at(cells, p).is(Blocks.WATER)).count();
		if (basin < 2) {
			problems.add("basin water at y = 1: " + basin);
		}
		for (BlockPos w : water) {
			BlockState s = at(cells, w);
			if (!s.getFluidState().isSource() || !s.getFluidState().is(Fluids.WATER)) {
				problems.add("not a water source: " + name(s) + " " + w.toShortString());
			}
			for (Direction d : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN}) {
				BlockState by = at(cells, w.relative(d));
				if (!holdsWater(by)) {
					problems.add("water " + w.toShortString() + " flows out " + d.getName() + " into " + name(by));
				}
			}
		}
		List<BlockPos> grindstones = where(cells, Blocks.GRINDSTONE);
		Set<BlockPos> reached = walkFromStreet(blueprint, cells, true);
		if (grindstones.size() != 1) {
			problems.add("grindstones " + grindstones);
		} else {
			BlockPos g = grindstones.get(0);
			Vec3 middle = Vec3.atCenterOf(g);
			if (reached.stream().noneMatch(p -> Vec3.atBottomCenterOf(p).distanceTo(middle) <= 4.5)) {
				problems.add("no walked spot within 4.5 of the grindstone " + g.toShortString());
			}
		}
		if (reached.stream().noneMatch(p -> p.getY() >= 6 && p.getY() <= 8)) {
			problems.add("the watch loft (y 6 to 7) can't be reached by its ladder from the street");
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	// ---- Gatehouse --------------------------------------------------------------------------------------------------

	private static Map<BlockPos, BlockState> gatesOpen(Map<BlockPos, BlockState> cells) {
		Map<BlockPos, BlockState> out = new HashMap<>(cells);
		out.replaceAll((p, s) -> s.getBlock() instanceof FenceGateBlock ? s.setValue(FenceGateBlock.OPEN, true) : s);
		return out;
	}

	/**
	 * gatehouse.md, "The way through": by day, the fence gates open, a villager walks from the village side (z < 0) along
	 * the gateway through to the ground past the outside face, and back, without leaving the build's width (the wall runs
	 * on either side); with the gates shut nobody walks through. Both levels.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaAVillagerWalksThroughEachGatehouseWithItsGatesOpen(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (StarterBlueprints.Entry entry : List.of(StarterBlueprints.GATEHOUSE, StarterBlueprints.GATEHOUSE_2)) {
			String n = entry.id().getPath();
			Blueprint blueprint = blueprint(helper, entry);
			int sx = blueprint.size().getX();
			int sz = blueprint.size().getZ();
			Bounds inside = new Bounds(0, sx - 1, sz, blueprint.size().getY(), false);
			List<BlockPos> outside = new ArrayList<>();
			for (int x = 0; x < sx; x++) {
				outside.add(new BlockPos(x, 0, sz + 1));
			}
			Map<BlockPos, BlockState> open = gatesOpen(cells(blueprint));
			Set<BlockPos> out = walk(open, street(0, sx - 1), inside);
			if (outside.stream().noneMatch(out::contains)) {
				problems.add(n + ": with the gates open no walk from the village side reaches the ground outside (z " + (sz + 1) + ")");
			}
			Set<BlockPos> in = walk(open, outside, inside);
			if (street(0, sx - 1).stream().noneMatch(in::contains)) {
				problems.add(n + ": with the gates open no walk from outside reaches the village side");
			}
			Set<BlockPos> shut = walk(cells(blueprint), street(0, sx - 1), inside);
			if (outside.stream().anyMatch(shut::contains)) {
				problems.add(n + ": with the gates shut a villager still walks out to " + outside.stream().filter(shut::contains).toList());
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * gatehouse.md, "Battle stations": in a siege the gates are shut; every station is still reached by a guard starting
	 * on the village side (he walks, no ladder, to within 3.5 flat of it and 1.5 or more below; the wall meets the build's
	 * ends from row z = 3); Level II has a station on the barbican (z >= 4).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEveryGatehouseStationIsReachedFromTheVillageSideWithTheGatesShut(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (StarterBlueprints.Entry entry : List.of(StarterBlueprints.GATEHOUSE, StarterBlueprints.GATEHOUSE_2)) {
			String n = entry.id().getPath();
			Blueprint blueprint = blueprint(helper, entry);
			int sx = blueprint.size().getX();
			Set<BlockPos> reached = walk(cells(blueprint), street(-2, sx + 1),
				new Bounds(-2, sx + 1, blueprint.size().getZ(), blueprint.size().getY(), false, 3, sx));
			List<BlockPos> spots = BattleStations.spots(blueprint);
			if (spots.isEmpty()) {
				problems.add(n + ": no battle stations");
			}
			// (Gatehouse II's barbican stations, z >= 4, are QaRebuiltDefense1121cBugTests' on tests/rebuilt-defense-1121c.)
			List<BlockPos> unreached = spots.stream().filter(s -> s.getZ() < 4 && !climbable(reached, s)).map(BlockPos::above).toList();
			if (!unreached.isEmpty()) {
				problems.add(n + ": stations (standing) a guard on the village side can't climb to: " + unreached + " (the walk reaches y up to "
					+ reached.stream().mapToInt(BlockPos::getY).max().orElse(-99) + ", z up to " + reached.stream().mapToInt(BlockPos::getZ).max().orElse(-99) + ")");
			}
			if (entry == StarterBlueprints.GATEHOUSE_2 && spots.stream().noneMatch(s -> s.getZ() >= 4)) {
				problems.add(n + ": no barbican station (z >= 4); stations " + spots);
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	// ---- Lookout Tower ----------------------------------------------------------------------------------------------

	private static final List<StarterBlueprints.Entry> TOWERS = List.of(StarterBlueprints.LOOKOUT_TOWER, StarterBlueprints.LOOKOUT_TOWER_2,
		StarterBlueprints.LOOKOUT_TOWER_3, StarterBlueprints.LOOKOUT_TOWER_4);

	/**
	 * lookout_tower.md, "Battle stations" and "Getting up to a station": every level has stations, and every station is
	 * within 3.5 blocks flat of ground a villager walks to (no ladder: villagers can't path up one) and 1.5 or more below.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEveryLookoutTowerStationIsClimbedToFromGroundAVillagerWalks(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (StarterBlueprints.Entry entry : TOWERS) {
			Blueprint blueprint = blueprint(helper, entry);
			List<BlockPos> spots = BattleStations.spots(blueprint);
			if (spots.isEmpty()) {
				problems.add(entry.id().getPath() + ": no battle stations");
				continue;
			}
			Set<BlockPos> reached = walkFromStreet(blueprint, cells(blueprint), false);
			List<BlockPos> unreached = spots.stream().filter(s -> !climbable(reached, s)).map(BlockPos::above).toList();
			if (!unreached.isEmpty()) {
				problems.add(entry.id().getPath() + ": stations (standing, of " + spots.size() + ") no guard can climb to: " + unreached);
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * lookout_tower.md, "Job blocks" and "Beds": I no job block needed, II at least one (Guard Post or grindstone) inside,
	 * III at least two, IV III's; no other job block; beds I and II none, III the bunks (at least two, heads against a
	 * wall), IV no more than III; every job block walked to from the street without climbing a ladder (the watchman takes
	 * it on foot), every bed's head walked to.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEachLookoutTowerLevelHasItsWatchmensPostsAndBunks(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int[] posts = {0, 1, 2, 2};
		int bedsOfThree = -1;
		for (int i = 0; i < TOWERS.size(); i++) {
			StarterBlueprints.Entry entry = TOWERS.get(i);
			String n = entry.id().getPath();
			Blueprint blueprint = blueprint(helper, entry);
			Map<BlockPos, BlockState> cells = cells(blueprint);
			Map<BlockPos, BlockState> pois = pois(cells);
			List<BlockPos> jobs = pois.entrySet().stream().filter(e -> e.getValue().is(ModBlocks.GUARD_POST) || e.getValue().is(Blocks.GRINDSTONE))
				.map(Map.Entry::getKey).sorted().toList();
			pois.values().removeIf(s -> s.is(ModBlocks.GUARD_POST) || s.is(Blocks.GRINDSTONE) || s.is(Blocks.BELL));
			if (!pois.isEmpty()) {
				problems.add(n + ": other job blocks " + shortList(pois));
			}
			if (jobs.size() < posts[i]) {
				problems.add(n + ": guard job blocks " + jobs + ", want at least " + posts[i]);
			}
			Set<BlockPos> onFoot = walkFromStreet(blueprint, cells, false);
			for (BlockPos j : jobs) {
				if (!near(onFoot, j, 1)) {
					problems.add(n + ": job block " + name(at(cells, j)) + " " + j.toShortString() + " not walked to without a ladder");
				}
			}
			List<BlockPos> beds = bedHeads(cells);
			Set<BlockPos> withLadders = walkFromStreet(blueprint, cells, true);
			if (i <= 1 && !beds.isEmpty()) {
				problems.add(n + ": beds " + beds + " (I and II have none)");
			}
			if (i == 2) {
				bedsOfThree = beds.size();
				if (beds.size() < 2) {
					problems.add(n + ": bunks " + beds);
				}
			}
			if (i == 3 && beds.size() > bedsOfThree) {
				problems.add(n + ": more beds (" + beds.size() + ") than III (" + bedsOfThree + ")");
			}
			for (BlockPos b : beds) {
				if (!near(withLadders, b, 2)) {
					problems.add(n + ": bed " + b.toShortString() + " not walked to");
				}
				if (!headToAWall(cells, b)) {
					problems.add(n + ": bed " + b.toShortString() + " has no wall at its head");
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * lookout_tower.md, "Kept cells": every block with block-entity data (chests, barrels; beds, bells, signs, banners and
	 * campfires excepted) stays in its cell in every later level, and so do the watchmen's job blocks and bunks (they
	 * already work and sleep there).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaLookoutTowerUpgradesKeepPostsBunksAndContainers(GameTestHelper helper) {
		List<String> problems = kept(helper, TOWERS);
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	// ---- Royal Hall -------------------------------------------------------------------------------------------------

	/**
	 * royal_hall.md: no job block; at least four chests in each level and II more than I (the treasury grows); every
	 * chest of I still a chest in the same cell of II (a builder never breaks a container, so the upgrade would stall);
	 * every vault chest walked to from the front (wooden doors open); I's door and II's added east door walked through.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheRoyalHallsVaultGrowsAndEveryChestIsWalkedTo(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		List<BlockPos> first = null;
		for (StarterBlueprints.Entry entry : List.of(StarterBlueprints.ROYAL_HALL, StarterBlueprints.ROYAL_HALL_2)) {
			String n = entry.id().getPath();
			Blueprint blueprint = blueprint(helper, entry);
			Map<BlockPos, BlockState> cells = cells(blueprint);
			Map<BlockPos, BlockState> pois = pois(cells);
			pois.values().removeIf(s -> s.is(Blocks.BELL)); // (a meeting point, not a job block)
			if (!pois.isEmpty()) {
				problems.add(n + ": job blocks " + shortList(pois));
			}
			List<BlockPos> chests = chests(cells);
			if (chests.size() < 4) {
				problems.add(n + ": chests " + chests.size());
			}
			Set<BlockPos> reached = walkFromStreet(blueprint, cells, true);
			List<BlockPos> unreached = chests.stream().filter(c -> !near(reached, c, 2)).toList();
			if (!unreached.isEmpty()) {
				problems.add(n + ": chests not walked to from the front: " + unreached);
			}
			if (first == null) {
				first = chests;
			} else {
				if (chests.size() <= first.size()) {
					problems.add(n + ": " + chests.size() + " chests, I has " + first.size());
				}
				List<BlockPos> moved = first.stream().filter(c -> !(at(cells, c).getBlock() instanceof ChestBlock)).toList();
				if (!moved.isEmpty()) {
					problems.add(n + ": I's chests not chests in II: " + moved);
				}
				int sx = blueprint.size().getX();
				List<BlockPos> east = new ArrayList<>();
				for (int z = 0; z < blueprint.size().getZ(); z++) {
					east.add(new BlockPos(sx + 1, 0, z));
				}
				Set<BlockPos> fromEast = walk(cells, east, Bounds.round(blueprint, true));
				boolean inside = fromEast.stream().anyMatch(p -> p.getX() >= 1 && p.getX() < sx - 1 && p.getZ() >= 1 && p.getZ() < blueprint.size().getZ() - 1
					&& !open(at(cells, p.above(2))) && reached.contains(p));
				if (!inside) {
					problems.add(n + ": no way in from the east side (no indoor spot reached both from the east and from the front)");
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	// ---- live: the placed drawing -----------------------------------------------------------------------------------

	private static BlockPos place(GameTestHelper helper, StarterBlueprints.Entry entry, BlockPos at) {
		ServerLevel level = helper.getLevel();
		StructureTemplate template = level.getStructureManager().get(entry.id()).orElseThrow();
		BlockPos origin = helper.absolutePos(at);
		template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), net.minecraft.util.RandomSource.create(173L), 2);
		return origin;
	}

	private static List<BlockPos> find(GameTestHelper helper, BlockPos origin, StarterBlueprints.Entry entry, Block block) {
		var size = entry.size();
		return BlockPos.betweenClosedStream(origin.offset(0, -entry.basement(), 0), origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))
			.filter(p -> helper.getLevel().getBlockState(p).is(block)).map(BlockPos::immutable).toList();
	}

	private static String ends(Path path, BlockPos origin) {
		return path == null ? " (no path)" : ", it ends at " + path.getEndNode().asBlockPos().subtract(origin).toShortString();
	}

	/**
	 * fire_station.md in the world: the water the Firewarden fills at is the one nearest his grindstone
	 * ({@link FirewardenWork#findWater}); from the street in front the game's pathfinder finds him a way to a spot he can
	 * stand on with his eyes within 3.0 blocks of that water's centre (or he never fills a bucket).
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaDefense1121cFireWater"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaDefense1121cFireWater")
	public void qaTheFirewardenWalksUpToTheWaterNearestHisGrindstone(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos origin = place(helper, StarterBlueprints.FIRE_STATION, new BlockPos(7, 2, 10));
		List<BlockPos> grindstones = find(helper, origin, StarterBlueprints.FIRE_STATION, Blocks.GRINDSTONE);
		helper.assertTrue(grindstones.size() == 1, "(setup) grindstones placed: " + grindstones.size());
		Villager walker = helper.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 7));
		walker.setNoAi(true);
		helper.runAfterDelay(10, () -> {
			walker.setOnGround(true);
			Optional<BlockPos> water = FirewardenWork.findWater(level, grindstones.get(0));
			helper.assertTrue(water.isPresent(), "no water within reach of the grindstone");
			Vec3 middle = Vec3.atCenterOf(water.get());
			List<BlockPos> spots = BlockPos.betweenClosedStream(water.get().offset(-4, -4, -4), water.get().offset(4, 3, 4)).map(BlockPos::immutable)
				.filter(p -> Walker.canStand(level, p) && Walker.eyeFrom(p).distanceTo(middle) <= 3.0).toList();
			boolean reached = false;
			for (BlockPos s : spots) {
				Path path = walker.getNavigation().createPath(s, 0);
				if (path != null && path.canReach()) {
					reached = true;
					break;
				}
			}
			helper.assertTrue(reached, "no path from the street to a spot within 3.0 (eyes) of the water nearest the grindstone, "
				+ water.get().subtract(origin).toShortString() + " (" + name(level.getBlockState(water.get())) + "); standing spots by it: "
				+ spots.stream().map(p -> p.subtract(origin).toShortString()).toList());
			walker.discard();
			helper.succeed();
		});
	}

	/**
	 * barracks.md in the world, Barracks II placed: from the street in front the game's pathfinder finds a villager a way
	 * to each of the four grindstones and each bed's head (within a block), and to within 2.5 of a Training Dummy.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaDefense1121cBarracksPaths"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaDefense1121cBarracksPaths")
	public void qaAVillagerFindsPathsToBarracksIIsGrindstonesBedsAndDummy(GameTestHelper helper) {
		Leftovers.clear(helper);
		StarterBlueprints.Entry entry = StarterBlueprints.BARRACKS_2;
		BlockPos origin = place(helper, entry, new BlockPos(4, 2, 10));
		List<BlockPos> targets = new ArrayList<>(find(helper, origin, entry, Blocks.GRINDSTONE));
		helper.assertTrue(targets.size() == 4, "(setup) grindstones placed: " + targets.size());
		ServerLevel level = helper.getLevel();
		BlockPos.betweenClosedStream(origin, origin.offset(entry.size().getX() - 1, entry.size().getY() - 1, entry.size().getZ() - 1))
			.filter(p -> level.getBlockState(p).getBlock() instanceof BedBlock && level.getBlockState(p).getValue(BedBlock.PART) == BedPart.HEAD)
			.forEach(p -> targets.add(p.immutable()));
		List<BlockPos> dummies = find(helper, origin, entry, ModBlocks.TRAINING_DUMMY);
		helper.assertTrue(!dummies.isEmpty(), "(setup) no Training Dummy placed");
		Villager walker = helper.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 7));
		walker.setNoAi(true);
		helper.runAfterDelay(10, () -> {
			walker.setOnGround(true);
			List<String> unreached = new ArrayList<>();
			for (BlockPos t : targets) {
				Path path = walker.getNavigation().createPath(t, 1);
				if (path == null || !path.canReach()) {
					unreached.add(name(level.getBlockState(t)) + " " + t.subtract(origin).toShortString() + ends(path, origin));
				}
			}
			boolean dummy = false;
			for (BlockPos d : dummies) {
				Path path = walker.getNavigation().createPath(d, 2);
				dummy |= path != null && path.canReach();
			}
			if (!dummy) {
				unreached.add("training dummies " + dummies.stream().map(p -> p.subtract(origin).toShortString()).toList());
			}
			helper.assertTrue(unreached.isEmpty(), "no path from the street to: " + unreached);
			walker.discard();
			helper.succeed();
		});
	}

	/**
	 * lookout_tower.md in the world, Lookout Tower III placed: from the street in front the game's pathfinder finds a
	 * villager a way to each of the watchmen's job blocks (a Guard Post or grindstone).
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaDefense1121cTowerPaths"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaDefense1121cTowerPaths")
	public void qaAVillagerFindsPathsToLookoutTowerIIIsGuardPosts(GameTestHelper helper) {
		Leftovers.clear(helper);
		StarterBlueprints.Entry entry = StarterBlueprints.LOOKOUT_TOWER_3;
		BlockPos origin = place(helper, entry, new BlockPos(5, 2, 10));
		List<BlockPos> jobs = new ArrayList<>(find(helper, origin, entry, ModBlocks.GUARD_POST));
		jobs.addAll(find(helper, origin, entry, Blocks.GRINDSTONE));
		helper.assertTrue(jobs.size() >= 2, "(setup) guard job blocks placed: " + jobs.size());
		ServerLevel level = helper.getLevel();
		Villager walker = helper.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 7));
		walker.setNoAi(true);
		helper.runAfterDelay(10, () -> {
			walker.setOnGround(true);
			List<String> unreached = new ArrayList<>();
			for (BlockPos t : jobs) {
				Path path = walker.getNavigation().createPath(t, 1);
				if (path == null || !path.canReach()) {
					unreached.add(name(level.getBlockState(t)) + " " + t.subtract(origin).toShortString() + ends(path, origin));
				}
			}
			helper.assertTrue(unreached.isEmpty(), "no path from the street to: " + unreached);
			walker.discard();
			helper.succeed();
		});
	}

	/**
	 * gatehouse.md in the world: each Gatehouse placed in a stone wall along its row z = 3 (as the siege yards stand it),
	 * its fence gates opened as the guards' round does at dawn; the game's pathfinder finds a villager on the village side
	 * a way through the gateway to the ground past the outside face.
	 */
	private void walksThrough(GameTestHelper helper, StarterBlueprints.Entry entry) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos at = new BlockPos(9, 2, 8);
		BlockPos origin = place(helper, entry, at);
		int sx = entry.size().getX();
		int sz = entry.size().getZ();
		for (int x = 0; x < 30; x++) {
			if (x >= at.getX() && x < at.getX() + sx) {
				continue;
			}
			for (int y = 2; y <= 5; y++) {
				helper.setBlock(new BlockPos(x, y, at.getZ() + 3), Blocks.STONE_BRICKS);
			}
		}
		for (BlockPos p : BlockPos.betweenClosed(origin, origin.offset(sx - 1, entry.size().getY() - 1, sz - 1))) {
			BlockState s = level.getBlockState(p);
			if (s.getBlock() instanceof FenceGateBlock) {
				level.setBlock(p, s.setValue(FenceGateBlock.OPEN, true), 2);
			}
		}
		Villager walker = helper.spawn(EntityType.VILLAGER, new BlockPos(at.getX() + 5, 2, at.getZ() - 3));
		walker.setNoAi(true);
		helper.runAfterDelay(10, () -> {
			walker.setOnGround(true);
			BlockPos beyond = origin.offset(5, 0, sz + 2);
			Path path = walker.getNavigation().createPath(beyond, 0);
			helper.assertTrue(path != null && path.canReach(), entry.id().getPath() + ": no path through the open gateway to "
				+ beyond.subtract(origin).toShortString() + ends(path, origin));
			walker.discard();
			helper.succeed();
		});
	}

	//$ gametest_ticks_batch HUGE '100' '"qaDefense1121cGate1"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaDefense1121cGate1")
	public void qaAVillagerFindsAPathThroughTheOpenGatehouse(GameTestHelper helper) {
		walksThrough(helper, StarterBlueprints.GATEHOUSE);
	}

	//$ gametest_ticks_batch HUGE '100' '"qaDefense1121cGate2"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaDefense1121cGate2")
	public void qaAVillagerFindsAPathThroughTheOpenGatehouseII(GameTestHelper helper) {
		walksThrough(helper, StarterBlueprints.GATEHOUSE_2);
	}
}
