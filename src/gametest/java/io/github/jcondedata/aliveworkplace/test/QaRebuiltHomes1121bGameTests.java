package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BlueprintSupplies;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.NobleBalls;
import io.github.jcondedata.aliveworkplace.people.Homes;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA lane B (2026-11-21), the homes rebuilt in Milestone 49 (49.4 Stone House I-IV, 49.5 Townhouse I-IV, 49.6 Terrace
 * I-II, 49.7 Manor I-IV, 49.8 Artisan's House I-III and Weaver's Cottage I-II, 49.9 Farmstead I-III and Ranch I-II),
 * written from their blind briefs ({@code tools/blueprints/briefs/<family>.md}), not from the designers' recipes.
 *
 * <p>The walk is a villager's (as in {@code QaRebuiltCivic1121cGameTests}): wooden doors open, a fence gate stays shut,
 * nobody stands on a fence or wall, and it starts in the street in front (the z = 0 side). A bed, job block or chest
 * counts as reached when a villager can stand right beside it.
 */
public class QaRebuiltHomes1121bGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	private record Family(String name, List<StarterBlueprints.Entry> levels) {
	}

	private static final Family STONE = new Family("stone_house", List.of(StarterBlueprints.STONE_HOUSE, StarterBlueprints.STONE_HOUSE_2,
		StarterBlueprints.STONE_HOUSE_3, StarterBlueprints.STONE_HOUSE_4));
	private static final Family TOWN = new Family("townhouse", List.of(StarterBlueprints.TOWNHOUSE, StarterBlueprints.TOWNHOUSE_2,
		StarterBlueprints.TOWNHOUSE_3, StarterBlueprints.TOWNHOUSE_4));
	private static final Family TERRACE = new Family("terrace", List.of(StarterBlueprints.TERRACE, StarterBlueprints.TERRACE_2));
	private static final Family MANOR = new Family("manor", List.of(StarterBlueprints.MANOR, StarterBlueprints.MANOR_2,
		StarterBlueprints.MANOR_3, StarterBlueprints.MANOR_4));
	private static final Family ARTISAN = new Family("artisans_house", List.of(StarterBlueprints.ARTISANS_HOUSE,
		StarterBlueprints.ARTISANS_HOUSE_2, StarterBlueprints.ARTISANS_HOUSE_3));
	private static final Family WEAVER = new Family("weavers_cottage", List.of(StarterBlueprints.WEAVERS_COTTAGE, StarterBlueprints.WEAVERS_COTTAGE_2));
	private static final Family FARM = new Family("farmstead", List.of(StarterBlueprints.FARMSTEAD, StarterBlueprints.FARMSTEAD_2,
		StarterBlueprints.FARMSTEAD_3));
	private static final Family RANCH = new Family("ranch", List.of(StarterBlueprints.RANCH, StarterBlueprints.RANCH_2));
	private static final List<Family> ALL = List.of(STONE, TOWN, TERRACE, MANOR, ARTISAN, WEAVER, FARM, RANCH);
	/** The families the briefs call homes: each has a front door on the ground floor at the z = 0 side. */
	private static final List<Family> HOMES = List.of(STONE, TOWN, TERRACE, MANOR, ARTISAN);

	// ---- drawings -----------------------------------------------------------------------------------------------------

	private static Blueprint blueprint(GameTestHelper helper, StarterBlueprints.Entry entry) {
		return BlueprintLibrary.get(helper.getLevel(), entry.id()).orElseThrow(() -> new AssertionError("missing " + entry.id()));
	}

	private static Map<BlockPos, BlockState> cells(Blueprint blueprint) {
		return QaBasementGameTests.blocks(blueprint);
	}

	private static BlockState at(Map<BlockPos, BlockState> cells, BlockPos pos) {
		return QaBasementGameTests.at(cells, pos);
	}

	private static String n(StarterBlueprints.Entry entry) {
		return entry.id().getPath();
	}

	private static String name(BlockState s) {
		return BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
	}

	private static List<BlockPos> where(Map<BlockPos, BlockState> cells, Block block) {
		return cells.entrySet().stream().filter(e -> e.getValue().is(block)).map(Map.Entry::getKey).sorted().toList();
	}

	private static List<BlockPos> bedHeads(Map<BlockPos, BlockState> cells) {
		return cells.entrySet().stream().filter(e -> e.getValue().getBlock() instanceof BedBlock
			&& e.getValue().getValue(BedBlock.PART) == BedPart.HEAD).map(Map.Entry::getKey).sorted().toList();
	}

	private static BlockPos bedFoot(Map<BlockPos, BlockState> cells, BlockPos head) {
		return head.relative(at(cells, head).getValue(BedBlock.FACING).getOpposite());
	}

	private static boolean storage(BlockState s) {
		return s.getBlock() instanceof ChestBlock || s.getBlock() instanceof BarrelBlock;
	}

	/** Every job site a villager is drawn to (not a bed, bell, lightning rod, portal, lodestone or hive). */
	private static Map<BlockPos, BlockState> jobSites(Map<BlockPos, BlockState> cells) {
		Map<BlockPos, BlockState> out = new HashMap<>();
		cells.forEach((p, s) -> {
			if (PoiTypes.forState(s).filter(h -> !h.is(PoiTypes.HOME) && !h.is(PoiTypes.LIGHTNING_ROD) && !h.is(PoiTypes.NETHER_PORTAL)
				&& !h.is(PoiTypes.LODESTONE) && !h.is(PoiTypes.BEEHIVE) && !h.is(PoiTypes.BEE_NEST) && !h.is(PoiTypes.MEETING)).isPresent()) {
				out.put(p, s);
			}
		});
		return out;
	}

	private static String shortList(Map<BlockPos, BlockState> m) {
		List<String> out = new ArrayList<>();
		m.forEach((p, s) -> out.add(name(s) + " " + p.toShortString()));
		out.sort(null);
		return out.toString();
	}

	// ---- a villager's walk ------------------------------------------------------------------------------------------

	private static boolean open(BlockState state, boolean gates) {
		if (state.getBlock() instanceof DoorBlock && !state.is(Blocks.IRON_DOOR)) {
			return true;
		}
		if (gates && state.getBlock() instanceof FenceGateBlock) {
			return true;
		}
		if (state.is(Blocks.LADDER)) {
			return true;
		}
		var shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
		return shape.isEmpty() || shape.max(Direction.Axis.Y) <= 0.2;
	}

	private static boolean noFloor(BlockState state) {
		return state.getBlock() instanceof FenceBlock || state.getBlock() instanceof WallBlock || state.getBlock() instanceof FenceGateBlock;
	}

	private static boolean standable(Map<BlockPos, BlockState> cells, BlockPos feet, boolean gates) {
		BlockState here = at(cells, feet);
		if (!open(here, gates) || !open(at(cells, feet.above()), gates) || !here.getFluidState().isEmpty()) {
			return false;
		}
		BlockState below = at(cells, feet.below());
		return here.is(Blocks.LADDER) || below.is(Blocks.LADDER) || (!open(below, false) && !noFloor(below) && below.getFluidState().isEmpty());
	}

	/** Every spot a villager reaches on foot from the street: level, a step up, up to three down, ladders. */
	private static Set<BlockPos> walk(Blueprint blueprint, Map<BlockPos, BlockState> cells, boolean gates) {
		int sx = blueprint.size().getX();
		int sz = blueprint.size().getZ();
		int top = blueprint.size().getY();
		Set<BlockPos> reached = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		for (int x = -2; x <= sx + 1; x++) {
			for (int z = -2; z <= -1; z++) {
				BlockPos s = new BlockPos(x, 0, z);
				if (standable(cells, s, gates) && reached.add(s)) {
					queue.add(s);
				}
			}
		}
		while (!queue.isEmpty()) {
			BlockPos pos = queue.poll();
			List<BlockPos> next = new ArrayList<>();
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos side = pos.relative(d);
				next.add(side);
				if (open(at(cells, pos.above(2)), gates)) {
					next.add(side.above());
				}
				for (int drop = 1; drop <= 3; drop++) {
					if (!open(at(cells, side.below(drop - 1)), gates) || !open(at(cells, side.below(drop - 1).above()), gates)) {
						break;
					}
					next.add(side.below(drop));
				}
			}
			if (at(cells, pos).is(Blocks.LADDER) || at(cells, pos.above()).is(Blocks.LADDER)) {
				next.add(pos.above());
			}
			if (at(cells, pos.below()).is(Blocks.LADDER)) {
				next.add(pos.below());
			}
			for (BlockPos nx : next) {
				if (nx.getX() < -2 || nx.getX() > sx + 1 || nx.getZ() < -2 || nx.getZ() > sz + 1 || nx.getY() < -8 || nx.getY() > top) {
					continue;
				}
				if (!reached.contains(nx) && standable(cells, nx, gates)) {
					reached.add(nx);
					queue.add(nx);
				}
			}
		}
		return reached;
	}

	/** A spot right beside {@code target} (one of its four sides, its level or the one below) was reached. */
	private static boolean beside(Set<BlockPos> reached, BlockPos target) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (reached.contains(target.relative(d)) || reached.contains(target.relative(d).below())) {
				return true;
			}
		}
		return false;
	}

	/** A villager gets to the bed: he stands beside its head or its foot. */
	private static boolean bedReached(Set<BlockPos> reached, Map<BlockPos, BlockState> cells, BlockPos head) {
		return beside(reached, head) || beside(reached, bedFoot(cells, head));
	}

	private static boolean within(BlockPos a, BlockPos b, int across, int vertical) {
		return Math.abs(a.getX() - b.getX()) <= across && Math.abs(a.getZ() - b.getZ()) <= across && Math.abs(a.getY() - b.getY()) <= vertical;
	}

	private static void eachLevel(GameTestHelper helper, Family family, List<String> problems, LevelCheck check) {
		for (int i = 0; i < family.levels().size(); i++) {
			StarterBlueprints.Entry entry = family.levels().get(i);
			Blueprint blueprint = blueprint(helper, entry);
			check.check(i, entry, blueprint, cells(blueprint), problems);
		}
	}

	private interface LevelCheck {
		void check(int level, StarterBlueprints.Entry entry, Blueprint blueprint, Map<BlockPos, BlockState> cells, List<String> problems);
	}

	// ---- static: beds -----------------------------------------------------------------------------------------------

	/**
	 * townhouse.md, artisans_house.md: "Every bed can be walked to from the front door"; stone_house.md: "the upper one
	 * is reached from inside"; manor.md: every bed walked to from the ground outside; terrace.md: each home's beds reached
	 * from its own front door; farmstead.md, weavers_cottage.md, ranch.md: a bed, if drawn, reachable on foot. Every level
	 * of every rebuilt home: each bed has a spot right beside its head or foot that a villager walks to from the street
	 * (doors open, fence gates shut, no standing on fences).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEveryBedOfEveryRebuiltHomeIsWalkedToFromTheStreet(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (Family family : ALL) {
			eachLevel(helper, family, problems, (i, entry, blueprint, cells, out) -> {
				Set<BlockPos> reached = walk(blueprint, cells, false);
				List<String> cut = new ArrayList<>();
				for (BlockPos head : bedHeads(cells)) {
					if (!bedReached(reached, cells, head)) {
						cut.add(head.toShortString());
					}
				}
				if (!cut.isEmpty()) {
					out.add(n(entry) + ": beds no villager walks to from the street " + cut);
				}
			});
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * The briefs' bed counts (bed heads): Stone House 2, 3, more than II, not more than III (stone_house.md); Townhouse at
	 * least 2 in I (two households) and never fewer in a later level (townhouse.md); Terrace exactly 4 and 6
	 * (terrace.md); Manor exactly 3 in I, and II-IV keep at least those (manor.md); Artisan's House 2, 3, 3 and none in the
	 * cellar (artisans_house.md); Farmstead exactly 1 in I (farmstead.md). Every bed's head inside its level's box, and
	 * none below the ground in a cellar level.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEachRebuiltHomeLevelHasTheBedsItsBriefCounts(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		Map<String, int[]> counts = new LinkedHashMap<>();
		for (Family family : ALL) {
			int[] c = new int[family.levels().size()];
			counts.put(family.name(), c);
			eachLevel(helper, family, problems, (i, entry, blueprint, cells, out) -> {
				List<BlockPos> heads = bedHeads(cells);
				c[i] = heads.size();
				Vec3i size = blueprint.size();
				for (BlockPos h : heads) {
					if (h.getX() < 0 || h.getX() >= size.getX() || h.getZ() < 0 || h.getZ() >= size.getZ() || h.getY() >= size.getY()) {
						out.add(n(entry) + ": a bed head outside the box at " + h.toShortString());
					}
					if (h.getY() < 0) {
						out.add(n(entry) + ": a bed in the cellar at " + h.toShortString());
					}
				}
			});
		}
		int[] s = counts.get("stone_house");
		if (s[0] != 2 || s[1] != 3 || s[2] <= s[1] || s[3] > s[2]) {
			problems.add("Stone House beds " + java.util.Arrays.toString(s) + ": want 2, 3, more than II, not more than III");
		}
		int[] t = counts.get("townhouse");
		if (t[0] < 2 || t[1] < t[0] || t[2] < t[1] || t[3] < t[2]) {
			problems.add("Townhouse beds " + java.util.Arrays.toString(t) + ": want at least 2 in I and never fewer later");
		}
		int[] te = counts.get("terrace");
		if (te[0] != 4 || te[1] != 6) {
			problems.add("Terrace beds " + java.util.Arrays.toString(te) + ": want 4, 6");
		}
		int[] m = counts.get("manor");
		if (m[0] != 3 || m[1] < 3 || m[2] < m[1] || m[3] < m[2]) {
			problems.add("Manor beds " + java.util.Arrays.toString(m) + ": want exactly 3 in I and never fewer later");
		}
		int[] a = counts.get("artisans_house");
		if (a[0] != 2 || a[1] != 3 || a[2] != 3) {
			problems.add("Artisan's House beds " + java.util.Arrays.toString(a) + ": want 2, 3, 3");
		}
		int[] f = counts.get("farmstead");
		if (f[0] != 1) {
			problems.add("Farmstead I has " + f[0] + " beds, want exactly 1");
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	// ---- static: upgrades, plots, fronts ----------------------------------------------------------------------------

	/**
	 * Every brief's "Kept cells": each level has the same origin and front as the one below and is at least its size in
	 * x, y and z, and every block with block-entity data (beds, chests, barrels, job blocks, ...) stays in its cell, the
	 * same block facing the same way (a builder never breaks those, so an upgrade that moves one can't finish, and a
	 * villager's claimed bed or job site would be lost). Beds also: "an upgrade never removes a bed" (townhouse.md,
	 * manor.md); "II keeps I's two in their cells" (artisans_house.md).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEachUpgradeKeepsTheLevelBelowsBedsJobBlocksAndChestsInTheirCells(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (Family family : ALL) {
			for (int i = 0; i + 1 < family.levels().size(); i++) {
				StarterBlueprints.Entry lo = family.levels().get(i);
				StarterBlueprints.Entry hi = family.levels().get(i + 1);
				Blueprint lb = blueprint(helper, lo);
				Blueprint hb = blueprint(helper, hi);
				if (hb.size().getX() < lb.size().getX() || hb.size().getY() < lb.size().getY() || hb.size().getZ() < lb.size().getZ()) {
					problems.add(n(hi) + " (" + hb.size().toShortString() + ") is smaller than " + n(lo) + " (" + lb.size().toShortString() + ") one way");
				}
				Map<BlockPos, BlockState> lc = cells(lb);
				Map<BlockPos, BlockState> hc = cells(hb);
				List<String> moved = new ArrayList<>();
				lc.forEach((p, s) -> {
					if (!s.hasBlockEntity() && PoiTypes.forState(s).isEmpty()) {
						return;
					}
					BlockState u = at(hc, p);
					boolean same = u.getBlock() == s.getBlock();
					if (same && s.getBlock() instanceof BedBlock) {
						same = u.getValue(BedBlock.FACING) == s.getValue(BedBlock.FACING) && u.getValue(BedBlock.PART) == s.getValue(BedBlock.PART);
					} else if (same && s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)
						&& (s.getBlock() instanceof ChestBlock || s.getBlock() instanceof BarrelBlock)) {
						same = u.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)
							== s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
					}
					if (!same) {
						moved.add(name(s) + " " + p.toShortString() + " -> " + name(u));
					}
				});
				moved.sort(null);
				if (!moved.isEmpty()) {
					problems.add(n(lo) + " -> " + n(hi) + " moves or removes " + moved.size() + ": " + moved.subList(0, Math.min(8, moved.size())));
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * Every brief's "Fits": the drawing is its declared size, and its footprint and height (above the ground) fit the plot
	 * the brief states: Stone House I 13 x 13, II under 20 wide, III/IV 13 wide x 14 deep, 16 tall; Townhouse I 15 x 16,
	 * 28 tall, II-IV 21 x 22; Terrace I 13 x 13, 16 tall, II 23 x 14, 28 tall; Manor I 25 x 24, II-IV 29 x 25, 18 tall;
	 * Artisan's House 21 x 22, 18 tall; Weaver's Cottage and Farmstead 18 wide x 26 deep x 28 tall; Ranch I 13 x 13, 16
	 * tall, II at most 30 x 30, 28 tall. Level IV/III cellars are exactly 4 deep where the brief says so.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEveryRebuiltHomeFitsThePlotItsBriefStates(GameTestHelper helper) {
		// {wide, tall, deep} at most, per level
		Map<StarterBlueprints.Entry, int[]> plots = new LinkedHashMap<>();
		plots.put(StarterBlueprints.STONE_HOUSE, new int[] {13, 16, 13});
		plots.put(StarterBlueprints.STONE_HOUSE_2, new int[] {19, 16, 14});
		plots.put(StarterBlueprints.STONE_HOUSE_3, new int[] {13, 16, 14});
		plots.put(StarterBlueprints.STONE_HOUSE_4, new int[] {13, 16, 14});
		plots.put(StarterBlueprints.TOWNHOUSE, new int[] {15, 28, 16});
		plots.put(StarterBlueprints.TOWNHOUSE_2, new int[] {21, 28, 22});
		plots.put(StarterBlueprints.TOWNHOUSE_3, new int[] {21, 28, 22});
		plots.put(StarterBlueprints.TOWNHOUSE_4, new int[] {21, 28, 22});
		plots.put(StarterBlueprints.TERRACE, new int[] {13, 16, 13});
		plots.put(StarterBlueprints.TERRACE_2, new int[] {23, 28, 14});
		plots.put(StarterBlueprints.MANOR, new int[] {25, 18, 24});
		plots.put(StarterBlueprints.MANOR_2, new int[] {29, 18, 25});
		plots.put(StarterBlueprints.MANOR_3, new int[] {29, 18, 25});
		plots.put(StarterBlueprints.MANOR_4, new int[] {29, 18, 25});
		plots.put(StarterBlueprints.ARTISANS_HOUSE, new int[] {21, 18, 22});
		plots.put(StarterBlueprints.ARTISANS_HOUSE_2, new int[] {21, 18, 22});
		plots.put(StarterBlueprints.ARTISANS_HOUSE_3, new int[] {21, 18, 22});
		plots.put(StarterBlueprints.WEAVERS_COTTAGE, new int[] {18, 28, 26});
		plots.put(StarterBlueprints.WEAVERS_COTTAGE_2, new int[] {18, 28, 26});
		plots.put(StarterBlueprints.FARMSTEAD, new int[] {18, 28, 26});
		plots.put(StarterBlueprints.FARMSTEAD_2, new int[] {18, 28, 26});
		plots.put(StarterBlueprints.FARMSTEAD_3, new int[] {18, 28, 26});
		plots.put(StarterBlueprints.RANCH, new int[] {13, 16, 13});
		plots.put(StarterBlueprints.RANCH_2, new int[] {30, 28, 30});
		Set<StarterBlueprints.Entry> cellars = Set.of(StarterBlueprints.STONE_HOUSE_4, StarterBlueprints.TOWNHOUSE_4, StarterBlueprints.MANOR_4,
			StarterBlueprints.ARTISANS_HOUSE_3, StarterBlueprints.FARMSTEAD_3);
		List<String> problems = new ArrayList<>();
		for (Family family : ALL) {
			for (StarterBlueprints.Entry entry : family.levels()) {
				helper.assertTrue(plots.containsKey(entry), "(setup) no plot for " + n(entry));
			}
		}
		plots.forEach((entry, plot) -> {
			Blueprint b = blueprint(helper, entry);
			if (!b.size().equals(entry.size())) {
				problems.add(n(entry) + " is " + b.size().toShortString() + ", declared " + entry.size().toShortString());
			}
			int above = b.size().getY(); // (the size is the part above the ground; a cellar is drawn under y = 0)
			if (b.size().getX() > plot[0] || above > plot[1] || b.size().getZ() > plot[2]) {
				problems.add(n(entry) + " is " + b.size().getX() + " wide x " + above + " tall x " + b.size().getZ() + " deep, the plot is "
					+ plot[0] + " x " + plot[1] + " x " + plot[2]);
			}
			int want = cellars.contains(entry) ? 4 : 0;
			if (b.basement() != want) {
				problems.add(n(entry) + ": basement " + b.basement() + " deep, want " + want);
			}
			if (!b.entities().isEmpty()) {
				problems.add(n(entry) + ": " + b.entities().size() + " entities drawn in");
			}
		});
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * Every home brief: "A front door on the ground floor at the z = 0 side" (terrace.md: one per home, so at least 2 in
	 * I and 3 in II). A wooden door low in the build (its lower half at y 0-2) in the front rows (z 0-3), with a spot a
	 * villager stands on in front of it that he walked to from the street, and one inside it.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEveryRebuiltHomeHasAFrontDoorAVillagerWalksThroughFromTheStreet(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (Family family : HOMES) {
			eachLevel(helper, family, problems, (i, entry, blueprint, cells, out) -> {
				Set<BlockPos> reached = walk(blueprint, cells, false);
				List<BlockPos> fronts = new ArrayList<>();
				cells.forEach((p, s) -> {
					if (s.getBlock() instanceof DoorBlock && !s.is(Blocks.IRON_DOOR)
						&& s.getValue(DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER
						&& p.getY() >= 0 && p.getY() <= 2 && p.getZ() <= 3
						&& (reached.contains(p.north()) || reached.contains(p.north().below())) && (reached.contains(p.south()) || reached.contains(p.south().below()))) {
						fronts.add(p);
					}
				});
				int want = family == TERRACE ? (i == 0 ? 2 : 3) : 1;
				if (fronts.size() < want) {
					out.add(n(entry) + ": " + fronts.size() + " front doors a villager walks through from the street " + fronts + ", want " + want);
				}
			});
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	// ---- static: the workplaces' job blocks ---------------------------------------------------------------------------

	/**
	 * weavers_cottage.md: exactly one loom in each level and no other job block; the loom reached on foot from the street
	 * with the gates shut ("No gate ... on the way from the street to the loom"); a chest within 8 across and 4 up or down
	 * of the loom; the sheep pen: at least 8 fences and a fence gate in both levels, a gate within 16 across and 6 up or
	 * down of the loom; II has at least 8 more flowers than I; I's loom and chest stay in their cells in II.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheWeaversCottageHasOneLoomAChestAndAPenTheShepherdWalksTo(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int[] flowers = new int[2];
		eachLevel(helper, WEAVER, problems, (i, entry, blueprint, cells, out) -> {
			List<BlockPos> looms = where(cells, Blocks.LOOM);
			Map<BlockPos, BlockState> jobs = jobSites(cells);
			jobs.values().removeIf(s -> s.is(Blocks.LOOM));
			if (looms.size() != 1) {
				out.add(n(entry) + ": looms " + looms + ", want exactly 1");
			}
			if (!jobs.isEmpty()) {
				out.add(n(entry) + ": other job blocks " + shortList(jobs));
			}
			int fences = 0;
			List<BlockPos> gates = new ArrayList<>();
			for (Map.Entry<BlockPos, BlockState> e : cells.entrySet()) {
				if (e.getValue().is(BlockTags.FENCES)) {
					fences++;
				}
				if (e.getValue().is(BlockTags.FENCE_GATES)) {
					gates.add(e.getKey());
				}
				if (e.getValue().is(BlockTags.SMALL_FLOWERS) || e.getValue().is(BlockTags.TALL_FLOWERS)) {
					flowers[i]++;
				}
			}
			if (fences < 8 || gates.isEmpty()) {
				out.add(n(entry) + ": the pen has " + fences + " fences and " + gates.size() + " gates, want 8 and 1");
			}
			if (looms.isEmpty()) {
				return;
			}
			BlockPos loom = looms.get(0);
			Set<BlockPos> reached = walk(blueprint, cells, false);
			if (!beside(reached, loom)) {
				out.add(n(entry) + ": the loom " + loom.toShortString() + " has no spot beside it a villager walks to from the street, gates shut");
			}
			if (cells.entrySet().stream().noneMatch(e -> e.getValue().is(Blocks.CHEST) && within(e.getKey(), loom, 8, 4))) {
				out.add(n(entry) + ": no chest within 8 across and 4 up or down of the loom " + loom.toShortString());
			} else if (cells.entrySet().stream().noneMatch(e -> e.getValue().is(Blocks.CHEST) && within(e.getKey(), loom, 8, 4) && beside(reached, e.getKey()))) {
				out.add(n(entry) + ": no chest by the loom that a villager walks up to from the street");
			}
			if (gates.stream().noneMatch(g -> within(g, loom, 16, 6))) {
				out.add(n(entry) + ": the pen's gate is out of the Shepherd's range of the loom " + loom.toShortString());
			}
		});
		if (flowers[1] < flowers[0] + 8) {
			problems.add("Weaver's Cottage II has " + flowers[1] + " flowers, I has " + flowers[0] + ": want 8 more");
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * farmstead.md: exactly one composter in I and two in II and III (III adds no job block), and no other job block; each
	 * composter walked to from the street with the gates shut; a chest within 8 across and 4 up or down of every composter,
	 * walked up to; farmland within 16 across and 4 up or down of every composter (its field, which the Farmer finds from
	 * it); a carved pumpkin or jack o'lantern in I and II; II has at least 72 farmland, all on one level.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEachFarmsteadComposterHasAChestAndAFieldAFarmerWalksTo(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int[] want = {1, 2, 2};
		eachLevel(helper, FARM, problems, (i, entry, blueprint, cells, out) -> {
			List<BlockPos> composters = where(cells, Blocks.COMPOSTER);
			Map<BlockPos, BlockState> jobs = jobSites(cells);
			jobs.values().removeIf(s -> s.is(Blocks.COMPOSTER));
			if (composters.size() != want[i]) {
				out.add(n(entry) + ": composters " + composters + ", want " + want[i]);
			}
			if (!jobs.isEmpty()) {
				out.add(n(entry) + ": other job blocks " + shortList(jobs));
			}
			List<BlockPos> farmland = where(cells, Blocks.FARMLAND);
			Set<BlockPos> reached = walk(blueprint, cells, false);
			for (BlockPos c : composters) {
				if (!beside(reached, c)) {
					out.add(n(entry) + ": the composter " + c.toShortString() + " has no spot beside it a villager walks to from the street, gates shut");
				}
				if (cells.entrySet().stream().noneMatch(e -> storage(e.getValue()) && within(e.getKey(), c, 8, 4) && beside(reached, e.getKey()))) {
					out.add(n(entry) + ": no chest a villager walks up to within 8 across and 4 up or down of the composter " + c.toShortString());
				}
				if (farmland.stream().noneMatch(f -> within(f, c, 16, 4))) {
					out.add(n(entry) + ": no field within 16 across and 4 up or down of the composter " + c.toShortString());
				}
			}
			if (i < 2 && where(cells, Blocks.CARVED_PUMPKIN).isEmpty() && where(cells, Blocks.JACK_O_LANTERN).isEmpty()) {
				out.add(n(entry) + ": no scarecrow's head");
			}
			if (i == 1) {
				if (farmland.size() < 72) {
					out.add(n(entry) + ": " + farmland.size() + " farmland, want at least 72");
				}
				if (farmland.stream().map(BlockPos::getY).distinct().count() > 1) {
					out.add(n(entry) + ": the farmland isn't on one level");
				}
			}
		});
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * ranch.md: at least one smoker or Feed Trough inside the build's box in each level; each walked up to from the street
	 * "without a fence gate" ("Today's drawing reaches its smoker only through a closed fence gate; don't carry that
	 * over"); a chest or barrel within 8 across and 4 up or down of every one, walked up to the same way (I's job block and
	 * storage staying in their cells in II is checked by the upgrade test). Herd room: at least 9 cells of open ground
	 * inside the box within 16 blocks of the job block, the gates opened (a pen counts).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheRanchersJobBlockAndStorageAreWalkedToWithoutAGate(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		eachLevel(helper, RANCH, problems, (i, entry, blueprint, cells, out) -> {
			Vec3i size = blueprint.size();
			List<BlockPos> sites = cells.entrySet().stream().filter(e -> e.getValue().is(Blocks.SMOKER) || e.getValue().is(ModBlocks.FEED_TROUGH))
				.map(Map.Entry::getKey).filter(p -> p.getX() >= 0 && p.getX() < size.getX() && p.getZ() >= 0 && p.getZ() < size.getZ()).sorted().toList();
			if (sites.isEmpty()) {
				out.add(n(entry) + ": no smoker or Feed Trough inside the box");
				return;
			}
			Set<BlockPos> reached = walk(blueprint, cells, false);
			Set<BlockPos> withGates = walk(blueprint, cells, true);
			for (BlockPos site : sites) {
				if (!beside(reached, site)) {
					out.add(n(entry) + ": the job block " + name(cells.get(site)) + " " + site.toShortString()
						+ " is walked to only through a fence gate (or not at all)" + (beside(withGates, site) ? " (through a gate)" : ""));
				}
				if (cells.entrySet().stream().noneMatch(e -> storage(e.getValue()) && within(e.getKey(), site, 8, 4))) {
					out.add(n(entry) + ": no chest or barrel within 8 across and 4 up or down of " + site.toShortString());
				} else if (cells.entrySet().stream().noneMatch(e -> storage(e.getValue()) && within(e.getKey(), site, 8, 4) && beside(reached, e.getKey()))) {
					out.add(n(entry) + ": no storage by the job block " + site.toShortString() + " that a villager walks up to without a gate");
				}
				long herd = withGates.stream().filter(p -> p.getX() >= 0 && p.getX() < size.getX() && p.getZ() >= 0 && p.getZ() < size.getZ()
					&& within(p, site, 16, 4)).count();
				if (herd < 9) {
					out.add(n(entry) + ": only " + herd + " cells of herd room in the box near " + site.toShortString());
				}
			}
		});
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * manor.md, "The ball's venues": in I and II the cell (width / 2, 1, depth / 2) of the level's declared size, in III and
	 * IV the ballroom's middle {@code NobleBalls.BALLROOM}, is a floor cell a guest stands on (air there and above, a sturdy
	 * floor under it) inside the build, with a spot within 1 of it a villager walks to from the street.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEveryManorLevelsBallVenueIsAFloorAVillagerWalksTo(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		eachLevel(helper, MANOR, problems, (i, entry, blueprint, cells, out) -> {
			BlockPos venue = i < 2 ? new BlockPos(entry.size().getX() / 2, 1, entry.size().getZ() / 2) : NobleBalls.BALLROOM;
			BlockState under = at(cells, venue.below());
			boolean floor = at(cells, venue).getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()
				&& at(cells, venue.above()).getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()
				&& under.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, Direction.UP);
			if (!floor) {
				out.add(n(entry) + ": the venue " + venue.toShortString() + " isn't a floor a guest stands on (in it " + name(at(cells, venue))
					+ ", under it " + name(under) + ")");
			}
			Set<BlockPos> reached = walk(blueprint, cells, false);
			boolean near = false;
			for (BlockPos p : BlockPos.betweenClosed(venue.offset(-1, -1, -1), venue.offset(1, 1, 1))) {
				near |= reached.contains(p);
			}
			if (!near) {
				out.add(n(entry) + ": no villager walks from the street to the venue " + venue.toShortString());
			}
		});
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	// ---- live -------------------------------------------------------------------------------------------------------

	private static Villager reload(GameTestHelper helper, ServerLevel level, Villager old) {
		BuildSiteManager loaded = BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), level.registryAccess()), level.registryAccess());
		level.getDataStorage().set(BuildSiteManager.NAME, loaded);
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(old.save(tag), "the villager wasn't saved");
		old.discard();
		Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(back instanceof Villager, "the villager didn't load back: " + back);
		level.addFreshEntity(back);
		return (Villager) back;
	}

	private static final BlockPos[] BARRELS = {new BlockPos(1, 2, 4), new BlockPos(3, 2, 4), new BlockPos(4, 2, 4), new BlockPos(5, 2, 4)};
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	private static void stock(GameTestHelper helper, List<ItemStack> stock) {
		helper.assertTrue(stock.size() <= BARRELS.length * 27, "(setup) the test needs more barrels: " + stock.size() + " stacks");
		for (int i = 0; i < BARRELS.length; i++) {
			helper.setBlock(BARRELS[i], Blocks.BARREL);
			BaseContainerBlockEntity barrel = helper.getBlockEntity(BARRELS[i]);
			for (int slot = 0; slot < barrel.getContainerSize() && i * 27 + slot < stock.size(); slot++) {
				barrel.setItem(slot, stock.get(i * 27 + slot));
			}
		}
	}

	private static void rules(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
		});
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
	}

	/**
	 * A real Builder builds {@code entry} at {@code origin} from exactly its material list, saved and loaded (the site and
	 * the builder) once 40 blocks of the walls are up; the build ends DONE, every block right, nothing left over; then
	 * {@code after} checks the finished build.
	 */
	private static void buildAcrossAReload(GameTestHelper helper, StarterBlueprints.Entry entry, BlockPos origin, Consumer<BlueprintData.Placement> after) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		rules(helper);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		BuildPlan plan = BuildPlan.create(blueprint(helper, entry), placement);
		Map<Item, Integer> needs = new LinkedHashMap<>(plan.materials());
		List<ItemStack> stock = new ArrayList<>();
		needs.forEach((item, count) -> {
			for (int left = count; left > 0; ) {
				int k = Math.min(left, item.getDefaultMaxStackSize());
				stock.add(new ItemStack(item, k));
				left -= k;
			}
		});
		stock(helper, stock);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, entry.id(), placement);
		helper.assertTrue(site != null, n(entry) + " didn't start");
		UUID id = site.id();
		Villager[] who = {builder};
		boolean[] reloaded = {false};
		helper.onEachTick(() -> {
			if (level.getDayTime() % 24000 > 11000) {
				helper.setDayTime(2000);
			}
			BuildSite s = BuildSiteManager.get(level).get(id);
			if (reloaded[0] || s == null) {
				return;
			}
			if (s.stage() == BuildPlan.Stage.STRUCTURE && s.placed() >= 40) {
				reloaded[0] = true;
				who[0] = reload(helper, level, who[0]);
				helper.assertTrue(BuildSiteManager.get(level).get(id) != null, "the site didn't come back from its save");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "never saved and loaded while the walls were going up");
			BuildSite s = BuildSiteManager.get(level).get(id);
			helper.assertTrue(s == null, s == null ? "" : "still building: " + s.stage() + " " + s.status() + ", " + s.placed() + " placed, missing "
				+ s.missing() + ", builder at " + helper.relativePos(who[0].blockPosition()) + (who[0].isAlive() ? "" : " (dead)"));
			List<BlockPos> wrong = plan.unfinished(level);
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) wrong after the build, e.g. "
				+ wrong.stream().limit(5).map(p -> p.subtract(placement.origin()).toShortString() + "=" + level.getBlockState(p)).toList());
			Map<Item, Integer> left = new LinkedHashMap<>();
			List<Container> containers = new ArrayList<>();
			for (BlockPos b : BARRELS) {
				containers.add(helper.getBlockEntity(b));
			}
			containers.add(helper.getBlockEntity(CHEST));
			containers.add(who[0].getInventory());
			for (Container c : containers) {
				for (int slot = 0; slot < c.getContainerSize(); slot++) {
					ItemStack st = c.getItem(slot);
					if (!st.isEmpty() && needs.containsKey(st.getItem())) {
						left.merge(st.getItem(), st.getCount(), Integer::sum);
					}
				}
			}
			helper.assertTrue(left.isEmpty(), "materials left over after the build (it should use exactly what it needs): " + left);
			after.accept(placement);
		});
	}

	private static List<BlockPos> builtBedHeads(ServerLevel level, BlockPos origin, Vec3i size) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(origin, origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
			BlockState s = level.getBlockState(p);
			if (s.getBlock() instanceof BedBlock && s.getValue(BedBlock.PART) == BedPart.HEAD) {
				out.add(p.immutable());
			}
		}
		return out;
	}

	/**
	 * stone_house.md in the world: a Builder builds Stone House I from exactly its materials across a save and reload
	 * mid-STRUCTURE; it ends DONE with every block right and nothing left over, and the house holds its two beds, at two
	 * different heights, each a tier-1 home, and its barrel on the ground floor.
	 */
	//$ gametest_ticks_batch HUGE '45000' '"qaHomes1121bStoneBuild"'
	@GameTest(template = HUGE, timeoutTicks = 45000, batch = "qaHomes1121bStoneBuild")
	public void qaABuilderBuildsStoneHouseIAcrossAReloadFromExactlyItsMaterials(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		buildAcrossAReload(helper, StarterBlueprints.STONE_HOUSE, new BlockPos(9, 2, 9), placement -> {
			List<BlockPos> beds = builtBedHeads(level, placement.origin(), StarterBlueprints.STONE_HOUSE.size());
			helper.assertTrue(beds.size() == 2, "the built Stone House has " + beds.size() + " beds, the brief says 2");
			helper.assertTrue(beds.get(0).getY() != beds.get(1).getY(), "both beds of the built Stone House are on one floor");
			for (BlockPos head : beds) {
				helper.assertTrue(Homes.at(level, head).map(Homes.Home::tier).orElse(0) == 1, "a bed of the built Stone House isn't a tier-1 home: "
					+ Homes.at(level, head));
			}
			boolean barrel = false;
			for (BlockPos p : BlockPos.betweenClosed(placement.origin(), placement.origin().offset(10, 2, 10))) {
				barrel |= level.getBlockState(p).is(Blocks.BARREL);
			}
			helper.assertTrue(barrel, "no barrel on the built Stone House's ground floor");
		});
	}

	/**
	 * farmstead.md in the world: a Builder builds Farmstead I from exactly its materials across a save and reload
	 * mid-STRUCTURE; it ends DONE with every block right (farmland, water, crops) and nothing left over, with one composter,
	 * one bed and a chest within 8 across and 4 up or down of the composter.
	 */
	//$ gametest_ticks_batch HUGE '45000' '"qaHomes1121bFarmBuild"'
	@GameTest(template = HUGE, timeoutTicks = 45000, batch = "qaHomes1121bFarmBuild")
	public void qaABuilderBuildsFarmsteadIAcrossAReloadFromExactlyItsMaterials(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		buildAcrossAReload(helper, StarterBlueprints.FARMSTEAD, new BlockPos(9, 2, 9), placement -> {
			Vec3i size = StarterBlueprints.FARMSTEAD.size();
			List<BlockPos> composters = new ArrayList<>();
			List<BlockPos> chests = new ArrayList<>();
			for (BlockPos p : BlockPos.betweenClosed(placement.origin(), placement.origin().offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
				BlockState s = level.getBlockState(p);
				if (s.is(Blocks.COMPOSTER)) {
					composters.add(p.immutable());
				}
				if (s.is(Blocks.CHEST)) {
					chests.add(p.immutable());
				}
			}
			helper.assertTrue(composters.size() == 1, "the built Farmstead has composters " + composters.size());
			helper.assertTrue(chests.stream().anyMatch(c -> within(c, composters.get(0), 8, 4)), "no built chest within 8 / 4 of the composter");
			helper.assertTrue(builtBedHeads(level, placement.origin(), size).size() == 1, "the built Farmstead doesn't have exactly one bed");
		});
	}

	/**
	 * stone_house.md, "Kept cells" and the bed counts, in the world: Stone House I stands finished with its two beds
	 * claimed (each bed's home ticket taken, each villager's HOME memory on it); a Builder stocked with exactly what the
	 * upgrade still needs builds Stone House II over it. When it is DONE, both beds are still there, still claimed (no free
	 * ticket: the builder never broke and re-placed them), the villagers' homes still point at them, each bed is now a
	 * tier-2 home, and the house has three beds.
	 */
	//$ gametest_ticks_batch HUGE '40000' '"qaHomes1121bStoneUpgrade"'
	@GameTest(template = HUGE, timeoutTicks = 40000, batch = "qaHomes1121bStoneUpgrade")
	public void qaUpgradingAStoneHouseToIIKeepsItsClaimedBeds(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		rules(helper);
		BlockPos originRel = new BlockPos(9, 2, 9);
		BlockPos origin = helper.absolutePos(originRel);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		level.getStructureManager().get(StarterBlueprints.STONE_HOUSE.id()).orElseThrow()
			.placeInWorld(level, origin, origin, new StructurePlaceSettings(), net.minecraft.util.RandomSource.create(1121L), 2);
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.STONE_HOUSE.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		List<BlockPos> beds = builtBedHeads(level, origin, StarterBlueprints.STONE_HOUSE.size());
		helper.assertTrue(beds.size() == 2, "(setup) Stone House I placed with " + beds.size() + " beds");
		List<Villager> sleepers = new ArrayList<>();
		for (int k = 0; k < 2; k++) {
			BlockPos bed = beds.get(k);
			helper.assertTrue(level.getPoiManager().take(h -> h.is(PoiTypes.HOME), (h, p) -> p.equals(bed), bed, 1).isPresent(),
				"(setup) the bed at " + bed.subtract(origin).toShortString() + " couldn't be claimed");
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(6 + 2 * k, 2, 6));
			v.setNoAi(true);
			v.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), bed));
			sleepers.add(v);
		}
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		var report = BlueprintSupplies.check(level, new BlueprintData(StarterBlueprints.STONE_HOUSE_2.id(), Optional.of(StarterBlueprints.STONE_HOUSE_2.size()),
			Optional.of(placement))).orElseThrow(() -> new AssertionError("no supply report for the upgrade"));
		List<ItemStack> stock = new ArrayList<>();
		for (var m : report.missing()) {
			for (int left = m.count(); left > 0; left -= m.item().getDefaultMaxStackSize()) {
				stock.add(new ItemStack(m.item(), Math.min(left, m.item().getDefaultMaxStackSize())));
			}
		}
		helper.assertTrue(!stock.isEmpty(), "(setup) the upgrade needs nothing: " + report);
		stock(helper, stock);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, StarterBlueprints.STONE_HOUSE_2.id(), placement);
		helper.assertTrue(site != null, "Stone House II didn't start over the finished Stone House I");
		UUID id = site.id();
		helper.onEachTick(() -> {
			if (level.getDayTime() % 24000 > 11000) {
				helper.setDayTime(2000);
			}
		});
		helper.succeedWhen(() -> {
			BuildSite s = BuildSiteManager.get(level).get(id);
			helper.assertTrue(s == null, s == null ? "" : "still building: " + s.stage() + " " + s.status() + ", " + s.placed() + " placed, missing " + s.missing());
			BuildPlan plan = BuildPlan.create(blueprint(helper, StarterBlueprints.STONE_HOUSE_2), placement);
			List<BlockPos> wrong = plan.unfinished(level);
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) of Stone House II wrong, e.g. "
				+ wrong.stream().limit(5).map(p -> p.subtract(origin).toShortString() + "=" + level.getBlockState(p)).toList());
			for (int k = 0; k < 2; k++) {
				BlockPos bed = beds.get(k);
				String where = bed.subtract(origin).toShortString();
				helper.assertTrue(level.getBlockState(bed).getBlock() instanceof BedBlock, "the claimed bed at " + where + " is gone after the upgrade");
				helper.assertTrue(level.getPoiManager().existsAtPosition(PoiTypes.HOME, bed), "the claimed bed at " + where + " is no home POI after the upgrade");
				helper.assertTrue(level.getPoiManager().getFreeTickets(bed) == 0, "the claimed bed at " + where
					+ " was freed by the upgrade (broken and re-placed): a resident lost it");
				Villager v = sleepers.get(k);
				helper.assertTrue(v.getBrain().getMemory(MemoryModuleType.HOME).map(GlobalPos::pos).filter(bed::equals).isPresent(),
					"the villager of the bed at " + where + " lost his home: " + v.getBrain().getMemory(MemoryModuleType.HOME));
				helper.assertTrue(Homes.at(level, bed).map(Homes.Home::tier).orElse(0) == 2, "the bed at " + where + " isn't a tier-2 home after the upgrade: "
					+ Homes.at(level, bed));
			}
			int now = builtBedHeads(level, origin, StarterBlueprints.STONE_HOUSE_2.size()).size();
			helper.assertTrue(now == 3, "Stone House II built over I has " + now + " beds, want 3");
			sleepers.forEach(Entity::discard);
		});
	}
	/**
	 * ranch.md ("Reached on foot from outside, without a fence gate") and stone_house.md ("the upper one is reached from
	 * inside") in the world, with the game's own pathfinder on the placed drawing: from the street in front a villager
	 * finds a path to beside Ranch I's job block and to each of Stone House I's beds (fence gates stay shut, as placed).
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaHomes1121bPaths"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaHomes1121bPaths")
	public void qaAVillagerFindsAPathFromTheStreetToTheRanchersBlockAndTheStoneHousesBeds(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos ranch = helper.absolutePos(new BlockPos(2, 2, 5));
		BlockPos house = helper.absolutePos(new BlockPos(17, 2, 5));
		level.getStructureManager().get(StarterBlueprints.RANCH.id()).orElseThrow()
			.placeInWorld(level, ranch, ranch, new StructurePlaceSettings(), net.minecraft.util.RandomSource.create(1121L), 2);
		level.getStructureManager().get(StarterBlueprints.STONE_HOUSE.id()).orElseThrow()
			.placeInWorld(level, house, house, new StructurePlaceSettings(), net.minecraft.util.RandomSource.create(1121L), 2);
		List<BlockPos> sites = new ArrayList<>();
		Vec3i rs = StarterBlueprints.RANCH.size();
		for (BlockPos p : BlockPos.betweenClosed(ranch, ranch.offset(rs.getX() - 1, rs.getY() - 1, rs.getZ() - 1))) {
			if (level.getBlockState(p).is(Blocks.SMOKER) || level.getBlockState(p).is(ModBlocks.FEED_TROUGH)) {
				sites.add(p.immutable());
			}
		}
		List<BlockPos> beds = builtBedHeads(level, house, StarterBlueprints.STONE_HOUSE.size());
		helper.assertTrue(!sites.isEmpty() && beds.size() == 2, "(setup) placed: job blocks " + sites.size() + ", beds " + beds.size());
		Villager a = helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 2));
		Villager b = helper.spawn(EntityType.VILLAGER, new BlockPos(22, 2, 2));
		a.setNoAi(true);
		b.setNoAi(true);
		helper.runAfterDelay(10, () -> {
			a.setOnGround(true);
			b.setOnGround(true);
			List<String> cut = new ArrayList<>();
			for (BlockPos site : sites) {
				var path = a.getNavigation().createPath(site, 1);
				if (path == null || !path.canReach()) {
					cut.add("the Ranch's job block " + site.subtract(ranch).toShortString());
				}
			}
			for (BlockPos bed : beds) {
				var path = b.getNavigation().createPath(bed, 1);
				if (path == null || !path.canReach()) {
					cut.add("the Stone House's bed " + bed.subtract(house).toShortString());
				}
			}
			helper.assertTrue(cut.isEmpty(), "no path from the street to " + cut);
			a.discard();
			b.discard();
			helper.succeed();
		});
	}
}
