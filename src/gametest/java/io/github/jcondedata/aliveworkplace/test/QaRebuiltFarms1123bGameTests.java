package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Refreshes;
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
import java.util.function.Predicate;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/**
 * QA lane B (2026-11-23), the farm and pit workplaces rebuilt in Milestone 50 (50.9 Berry Farm I-II, Apiary Garden I-II,
 * Compost Yard I-II, Greenhouse; 50.10 Quarry, Sifting Shed I-II), written from their blind briefs
 * ({@code tools/blueprints/briefs/<family>.md}), not from the designers' recipes. The Crushing Mill and the Windmill are
 * Create-only machines with no vanilla job block and are left to the Create suite; the Greenhouse loads here without
 * Create (its machines as air), so only its composter, chest and walk are read.
 *
 * <p>The walk is a villager's, as in {@code QaRebuiltWorkshops1121bGameTests}: wooden doors open, a fence gate stays shut,
 * nobody stands on a fence or wall, a sweet berry bush is walked round (vanilla villagers path round them; the Berry Farm
 * brief: "without going through a berry bush"), and it starts in the street in front (the z = 0 side, turned with the
 * build). Ground left undrawn below y = 0 reads as dirt (the Quarry's pit).
 */
public class QaRebuiltFarms1123bGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final double EYE = 1.62;

	/** One level as its brief pins it: its job block, how many (exact, or a minimum when {@code atLeast}), and its plot. */
	private record Level(StarterBlueprints.Entry entry, Block job, int count, boolean atLeast, int chestsByJob, double standWithin, int w, int h, int d,
						 int maxBlocks) {
	}

	private static final List<Level> LEVELS = List.of(
		// berry_farm.md: one composter, a chest within 8 / 4, I at most 13 x 16 x 13 and never over 900 blocks
		new Level(StarterBlueprints.BERRY_FARM, Blocks.COMPOSTER, 1, false, 1, 3.0, 13, 16, 13, 900),
		new Level(StarterBlueprints.BERRY_FARM_2, Blocks.COMPOSTER, 1, false, 1, 3.0, 99, 99, 99, 99999),
		// apiary_garden.md: beehives (at least one), a chest within 8 / 4 of every hive, I at most 13 x 16 x 13, II 18 x 16 x 18
		new Level(StarterBlueprints.APIARY_GARDEN, Blocks.BEEHIVE, 1, true, 1, 3.0, 13, 16, 13, 99999),
		new Level(StarterBlueprints.APIARY_GARDEN_2, Blocks.BEEHIVE, 2, true, 1, 3.0, 18, 16, 18, 99999),
		// compost_yard.md: one composter, a standing spot within 2.5, one chest (II two) within 8 / 4; never over 900 blocks
		new Level(StarterBlueprints.COMPOST_YARD, Blocks.COMPOSTER, 1, false, 1, 2.5, 13, 16, 13, 900),
		new Level(StarterBlueprints.COMPOST_YARD_2, Blocks.COMPOSTER, 1, false, 2, 2.5, 15, 16, 13, 900),
		// sifting_shed.md: one cauldron (II two), a standing spot within 2.5 of each, a chest within 8 / 4 of each
		new Level(StarterBlueprints.SIFTING_SHED, Blocks.CAULDRON, 1, false, 1, 2.5, 13, 16, 13, 900),
		new Level(StarterBlueprints.SIFTING_SHED_2, Blocks.CAULDRON, 2, false, 1, 2.5, 13, 16, 14, 900),
		// quarry.md: exactly one blast furnace (at (14, 1, 7)), a chest within 8 / 4, at most 28 x 18 x 28 above ground
		new Level(StarterBlueprints.QUARRY, Blocks.BLAST_FURNACE, 1, false, 1, 3.0, 28, 18, 28, 99999),
		// greenhouse.md (Create only, loaded here without Create): one composter, a chest within 8 / 4
		new Level(StarterBlueprints.GREENHOUSE, Blocks.COMPOSTER, 1, false, 1, 3.0, 99, 99, 99, 99999));

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

	private static List<BlockPos> where(Map<BlockPos, BlockState> cells, Predicate<BlockState> what) {
		return cells.entrySet().stream().filter(e -> what.test(e.getValue())).map(Map.Entry::getKey).sorted().toList();
	}

	private static List<BlockPos> where(Map<BlockPos, BlockState> cells, Block block) {
		return where(cells, s -> s.is(block));
	}

	private static List<BlockPos> chests(Map<BlockPos, BlockState> cells) {
		return where(cells, s -> s.getBlock() instanceof ChestBlock);
	}

	/** Every job site a villager is drawn to, the bell included, and a beehive (the Beekeeper's). */
	private static boolean jobSite(BlockState s) {
		return PoiTypes.forState(s).filter(h -> !h.is(PoiTypes.HOME) && !h.is(PoiTypes.LIGHTNING_ROD) && !h.is(PoiTypes.NETHER_PORTAL)
			&& !h.is(PoiTypes.LODESTONE) && !h.is(PoiTypes.BEE_NEST)).isPresent();
	}

	// ---- a villager's walk ------------------------------------------------------------------------------------------

	private static boolean bush(BlockState s) {
		return s.is(Blocks.SWEET_BERRY_BUSH);
	}

	private static boolean open(BlockState state, boolean player) {
		if (state.getBlock() instanceof DoorBlock && (player || !state.is(Blocks.IRON_DOOR))) {
			return true;
		}
		if (player && (state.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock || state.getBlock() instanceof FenceGateBlock)) {
			return true;
		}
		if (state.is(Blocks.LADDER)) {
			return true;
		}
		if (!player && bush(state)) {
			return false;
		}
		var shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
		return shape.isEmpty() || shape.max(Direction.Axis.Y) <= 0.2;
	}

	private static boolean noFloor(BlockState state) {
		return state.getBlock() instanceof FenceBlock || state.getBlock() instanceof WallBlock || state.getBlock() instanceof FenceGateBlock;
	}

	private static boolean standable(Map<BlockPos, BlockState> cells, BlockPos feet, boolean player) {
		BlockState here = at(cells, feet);
		if (!open(here, player) || !open(at(cells, feet.above()), player) || !here.getFluidState().isEmpty()) {
			return false;
		}
		BlockState below = at(cells, feet.below());
		return here.is(Blocks.LADDER) || below.is(Blocks.LADDER)
			|| (!open(below, false) && !bush(below) && !noFloor(below) && below.getFluidState().isEmpty());
	}

	/** A drawing turned by {@code turn} about its corner and moved back so its box starts at (0, y, 0). */
	private record Drawn(Map<BlockPos, BlockState> cells, int sx, int sz, int top, Set<BlockPos> street) {
	}

	private static Drawn drawn(Blueprint blueprint, Rotation turn) {
		Vec3i size = blueprint.size();
		Map<BlockPos, BlockState> raw = cells(blueprint);
		BlockPos far = StructureTemplate.transform(new BlockPos(size.getX() - 1, 0, size.getZ() - 1), Mirror.NONE, turn, BlockPos.ZERO);
		int dx = -Math.min(0, far.getX());
		int dz = -Math.min(0, far.getZ());
		Map<BlockPos, BlockState> out = new HashMap<>();
		raw.forEach((p, s) -> out.put(StructureTemplate.transform(p, Mirror.NONE, turn, BlockPos.ZERO).offset(dx, 0, dz), s.rotate(turn)));
		Set<BlockPos> street = new HashSet<>();
		for (int x = -2; x <= size.getX() + 1; x++) {
			for (int z = -2; z <= -1; z++) {
				street.add(StructureTemplate.transform(new BlockPos(x, 0, z), Mirror.NONE, turn, BlockPos.ZERO).offset(dx, 0, dz));
			}
		}
		boolean swap = turn == Rotation.CLOCKWISE_90 || turn == Rotation.COUNTERCLOCKWISE_90;
		return new Drawn(out, swap ? size.getZ() : size.getX(), swap ? size.getX() : size.getZ(), size.getY(), street);
	}

	/** Every spot reached on foot from the street in front: level, a step up, up to three down, ladders. */
	private static Set<BlockPos> walk(Drawn d, boolean player) {
		Map<BlockPos, BlockState> cells = d.cells();
		Set<BlockPos> reached = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		for (BlockPos s : d.street()) {
			if (standable(cells, s, player) && reached.add(s)) {
				queue.add(s);
			}
		}
		while (!queue.isEmpty()) {
			BlockPos pos = queue.poll();
			List<BlockPos> next = new ArrayList<>();
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				BlockPos side = pos.relative(dir);
				next.add(side);
				if (open(at(cells, pos.above(2)), player)) {
					next.add(side.above());
				}
				for (int drop = 1; drop <= 3; drop++) {
					if (!open(at(cells, side.below(drop - 1)), player) || !open(at(cells, side.below(drop - 1).above()), player)) {
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
				if (nx.getX() < -2 || nx.getX() > d.sx() + 1 || nx.getZ() < -2 || nx.getZ() > d.sz() + 1 || nx.getY() < -8 || nx.getY() > d.top()) {
					continue;
				}
				if (!reached.contains(nx) && standable(cells, nx, player)) {
					reached.add(nx);
					queue.add(nx);
				}
			}
		}
		return reached;
	}

	/** A reached spot whose villager's eyes (or, {@code feet}, whose feet) are within {@code reach} of the block's middle. */
	private static boolean reachable(Set<BlockPos> reached, BlockPos target, double reach, boolean feet) {
		Vec3 c = Vec3.atCenterOf(target);
		int r = (int) Math.ceil(reach) + 2;
		for (BlockPos p : BlockPos.betweenClosed(target.offset(-r, -r - 2, -r), target.offset(r, r, r))) {
			if (reached.contains(p) && new Vec3(p.getX() + 0.5, p.getY() + (feet ? 0.5 : EYE), p.getZ() + 0.5).distanceTo(c) <= reach) {
				return true;
			}
		}
		return false;
	}

	/** A reached spot within {@code across} blocks each way and one up or down of {@code target} (a player's reach). */
	private static boolean near(Set<BlockPos> reached, BlockPos target, int across) {
		for (BlockPos p : BlockPos.betweenClosed(target.offset(-across, -1, -across), target.offset(across, 1, across))) {
			if (reached.contains(p)) {
				return true;
			}
		}
		return false;
	}

	// ---- the drawings, from the briefs ------------------------------------------------------------------------------

	/**
	 * Every brief's "Job block": each level holds exactly its job block in the count the brief names (one composter in the
	 * Berry Farm, Compost Yard and Greenhouse; one cauldron in Sifting Shed I, two in II; one blast furnace in the Quarry; at
	 * least one beehive in Apiary Garden I and more in II than in I), and no other job block of any job (the Compost Yard's
	 * old crafting table, a barrel). Built the way it stands and turned a quarter, half and three quarters, each job block
	 * has a spot a villager walks to from the street in front (doors open, gates shut, berry bushes walked round) within
	 * the reach its job needs: 2.5 blocks of the composter or cauldron (CompostWork, SifterWork), 3 of a hive, a
	 * Berry Farm's composter and the Quarry's furnace.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEveryFarmLevelHasItsJobBlocksAndAVillagerWalksUpToEachTurnedAnyWay(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int checked = 0;
		for (Level level : LEVELS) {
			Blueprint blueprint = blueprint(helper, level.entry());
			Map<BlockPos, BlockState> plain = cells(blueprint);
			String n = n(level.entry());
			List<BlockPos> found = where(plain, level.job());
			if (level.atLeast() ? found.size() < level.count() : found.size() != level.count()) {
				problems.add(n + ": " + found.size() + " " + name(level.job().defaultBlockState()) + " " + found + ", the brief says "
					+ (level.atLeast() ? "at least " : "") + level.count());
			}
			List<String> others = new ArrayList<>();
			plain.forEach((p, s) -> {
				if (jobSite(s) && !s.is(level.job())) {
					others.add(name(s) + " " + p.toShortString());
				}
			});
			if (!others.isEmpty()) {
				others.sort(null);
				problems.add(n + ": other job blocks " + others);
			}
			for (Rotation turn : Rotation.values()) {
				Drawn d = drawn(blueprint, turn);
				Set<BlockPos> reached = walk(d, false);
				for (BlockPos p : where(d.cells(), level.job())) {
					checked++;
					boolean ok = level.job() == Blocks.BEEHIVE || level.standWithin() > 2.9 ? reachable(reached, p, level.standWithin(), false)
						: reachable(reached, p, level.standWithin(), true);
					if (!ok) {
						problems.add(n + " turned " + turn + ": the " + name(level.job().defaultBlockState()) + " at " + p.toShortString()
							+ " has no spot within " + level.standWithin() + " a villager walks to from the street");
					}
				}
			}
		}
		Map<BlockPos, BlockState> q = cells(blueprint(helper, StarterBlueprints.QUARRY));
		if (!at(q, new BlockPos(14, 1, 7)).is(Blocks.BLAST_FURNACE)) {
			problems.add("quarry: no blast furnace at (14, 1, 7) (QuarryBuilds.FURNACE), there is " + name(at(q, new BlockPos(14, 1, 7))));
		}
		int hives1 = where(cells(blueprint(helper, StarterBlueprints.APIARY_GARDEN)), Blocks.BEEHIVE).size();
		int hives2 = where(cells(blueprint(helper, StarterBlueprints.APIARY_GARDEN_2)), Blocks.BEEHIVE).size();
		if (hives2 <= hives1) {
			problems.add("apiary_garden_2: " + hives2 + " hives, not more than Level I's " + hives1);
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.assertTrue(checked >= 4 * 13, "(setup) only " + checked + " job blocks checked");
		helper.succeed();
	}

	/**
	 * Every brief's "Storage": a chest within 8 across and 4 up or down of each job block (SupplyContainers), two such
	 * chests for Compost Yard II's composter, and every chest within a player's reach (2 across, 1 up or down) of a spot
	 * a player walks to from the street; no barrel (a Fisherman's job block); every chest drawn empty-able (a plain chest,
	 * never a barrel, hopper or pot in a job block's reach on the Compost Yard and Sifting Shed: "put no other storage
	 * there by accident").
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEveryFarmJobBlockHasItsChestsInReachAndEveryChestCanBeWalkedTo(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (Level level : LEVELS) {
			Blueprint blueprint = blueprint(helper, level.entry());
			Map<BlockPos, BlockState> cells = cells(blueprint);
			String n = n(level.entry());
			List<BlockPos> chests = chests(cells);
			for (BlockPos p : where(cells, level.job())) {
				long inReach = chests.stream().filter(c -> QaRebuiltWorkshops1121bGameTests.within(c, p, 8, 4)).count();
				if (inReach < level.chestsByJob()) {
					problems.add(n + ": " + inReach + " chest(s) within 8 across / 4 up or down of the " + name(level.job().defaultBlockState()) + " "
						+ p.toShortString() + ", the brief wants " + level.chestsByJob());
				}
				if (level.job() == Blocks.COMPOSTER && n.startsWith("compost") || level.job() == Blocks.CAULDRON) {
					List<String> stray = new ArrayList<>();
					cells.forEach((c, s) -> {
						if (QaRebuiltWorkshops1121bGameTests.within(c, p, 8, 4) && (s.is(Blocks.HOPPER) || s.is(Blocks.DECORATED_POT) || s.getBlock() instanceof BarrelBlock)) {
							stray.add(name(s) + " " + c.toShortString());
						}
					});
					if (!stray.isEmpty()) {
						problems.add(n + ": other storage in the " + name(level.job().defaultBlockState()) + "'s reach " + stray);
					}
				}
			}
			Set<BlockPos> reached = walk(drawn(blueprint, Rotation.NONE), true);
			List<String> cut = chests.stream().filter(c -> !near(reached, c, 2)).map(BlockPos::toShortString).toList();
			if (!cut.isEmpty()) {
				problems.add(n + ": chests nobody walks up to from the street " + cut);
			}
			if (!where(cells, Blocks.BARREL).isEmpty()) {
				problems.add(n + ": barrels " + where(cells, Blocks.BARREL));
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * The briefs' "Kept cells": each level II keeps every job block and chest of I in the same cell as the same block
	 * (a builder never breaks them), and is at least I's size; and each brief's "Plot" and "Declared size": the drawing's
	 * size equals the declared one, fits the plot the brief gives, and stays under the block budget it pins (Berry Farm
	 * I, Compost Yard I-II and Sifting Shed I-II never over 900 placed blocks).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEveryFarmUpgradeKeepsItsJobBlocksAndChestsAndEveryLevelFitsItsPlot(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (Level level : LEVELS) {
			Blueprint blueprint = blueprint(helper, level.entry());
			Vec3i size = blueprint.size();
			String n = n(level.entry());
			if (!size.equals(level.entry().size())) {
				problems.add(n + ": drawn " + size.toShortString() + ", declared " + level.entry().size().toShortString());
			}
			if (size.getX() > level.w() || size.getY() > level.h() || size.getZ() > level.d()) {
				problems.add(n + ": " + size.toShortString() + " (w h d) is over the brief's plot " + level.w() + ", " + level.h() + ", " + level.d());
			}
			long placed = blueprint.blocks().stream().filter(e -> !e.state().isAir()).count();
			if (placed > level.maxBlocks()) {
				problems.add(n + ": " + placed + " placed blocks, the brief says never over " + level.maxBlocks());
			}
		}
		int steps = 0;
		for (int i = 1; i < LEVELS.size(); i++) {
			StarterBlueprints.Entry lowE = LEVELS.get(i - 1).entry();
			StarterBlueprints.Entry upE = LEVELS.get(i).entry();
			if (!upE.id().getPath().equals(lowE.id().getPath() + "_2")) {
				continue;
			}
			steps++;
			Blueprint low = blueprint(helper, lowE);
			Blueprint up = blueprint(helper, upE);
			Map<BlockPos, BlockState> upper = cells(up);
			List<String> lost = new ArrayList<>();
			cells(low).forEach((p, s) -> {
				if ((s.getBlock() instanceof ChestBlock || PoiTypes.forState(s).isPresent()) && !at(upper, p).is(s.getBlock())) {
					lost.add(name(s) + " " + p.toShortString() + " -> " + name(at(upper, p)));
				}
			});
			if (!lost.isEmpty()) {
				lost.sort(null);
				problems.add(n(lowE) + " -> " + n(upE) + ": " + lost);
			}
			Vec3i a = low.size();
			Vec3i b = up.size();
			if (b.getX() < a.getX() || b.getY() < a.getY() || b.getZ() < a.getZ()) {
				problems.add(n(upE) + " " + b.toShortString() + " is smaller than " + n(lowE) + " " + a.toShortString());
			}
		}
		helper.assertTrue(steps == 4, "(setup) " + steps + " upgrade steps, want 4");
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * berry_farm.md, "Fruit the keeper can pick, from the moment it is built": Level I has sweet berry bushes, Level II
	 * sweet berry bushes and cave vines with glow berries, and more fruit plants than I; every fruit plant is within 16
	 * across, 4 below and 10 above the composter (OrchardWork's search), within 5.5 blocks of the eyes of a villager
	 * standing on a spot he walks to from the street without going through a bush (OrchardWork.REACH), and on what holds
	 * it: a bush on grass, dirt, podzol, coarse dirt or farmland; cave vines under a sturdy block or more vines.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheBerryFarmsFruitGrowsWhereTheKeeperPicksIt(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int[] fruit = new int[2];
		int i = 0;
		for (StarterBlueprints.Entry e : List.of(StarterBlueprints.BERRY_FARM, StarterBlueprints.BERRY_FARM_2)) {
			Blueprint blueprint = blueprint(helper, e);
			Map<BlockPos, BlockState> cells = cells(blueprint);
			String n = n(e);
			List<BlockPos> bushes = where(cells, Blocks.SWEET_BERRY_BUSH);
			List<BlockPos> vines = where(cells, s -> s.is(Blocks.CAVE_VINES) || s.is(Blocks.CAVE_VINES_PLANT));
			List<BlockPos> glow = where(cells, s -> (s.is(Blocks.CAVE_VINES) || s.is(Blocks.CAVE_VINES_PLANT)) && s.getValue(CaveVines.BERRIES));
			fruit[i++] = bushes.size() + vines.size();
			if (bushes.isEmpty()) {
				problems.add(n + ": no sweet berry bush");
			}
			if (e == StarterBlueprints.BERRY_FARM_2 && glow.isEmpty()) {
				problems.add(n + ": no cave vines with glow berries (" + vines.size() + " vines)");
			}
			List<BlockPos> composters = where(cells, Blocks.COMPOSTER);
			if (composters.size() != 1) {
				problems.add(n + ": " + composters.size() + " composters");
				continue;
			}
			BlockPos c = composters.get(0);
			Set<BlockPos> reached = walk(drawn(blueprint, Rotation.NONE), false);
			List<String> far = new ArrayList<>();
			List<String> unreached = new ArrayList<>();
			List<String> loose = new ArrayList<>();
			List<BlockPos> plants = new ArrayList<>(bushes);
			plants.addAll(vines);
			for (BlockPos p : plants) {
				int dy = p.getY() - c.getY();
				if (Math.abs(p.getX() - c.getX()) > 16 || Math.abs(p.getZ() - c.getZ()) > 16 || dy < -4 || dy > 10) {
					far.add(p.toShortString());
				}
				if (!reachable(reached, p, 5.5, false)) {
					unreached.add(p.toShortString());
				}
				BlockState below = at(cells, p.below());
				BlockState above = at(cells, p.above());
				boolean holds = at(cells, p).is(Blocks.SWEET_BERRY_BUSH)
					? below.is(Blocks.GRASS_BLOCK) || below.is(Blocks.DIRT) || below.is(Blocks.PODZOL) || below.is(Blocks.COARSE_DIRT) || below.is(Blocks.FARMLAND)
					|| below.is(Blocks.ROOTED_DIRT) || below.is(Blocks.MYCELIUM) || below.is(Blocks.MOSS_BLOCK) || below.is(BlockTags.DIRT)
					: above.is(Blocks.CAVE_VINES_PLANT) || above.is(Blocks.CAVE_VINES)
					|| above.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, Direction.DOWN);
				if (!holds) {
					loose.add(name(at(cells, p)) + " " + p.toShortString() + " on " + name(at(cells, p).is(Blocks.SWEET_BERRY_BUSH) ? below : above));
				}
			}
			if (!far.isEmpty()) {
				problems.add(n + ": fruit outside the keeper's 16 / 4 down / 10 up of the composter " + c.toShortString() + ": " + far);
			}
			if (!unreached.isEmpty()) {
				problems.add(n + ": fruit no villager reaches within 5.5 from a spot he walks to (round the bushes): " + unreached);
			}
			if (!loose.isEmpty()) {
				problems.add(n + ": fruit on nothing that holds it " + loose);
			}
		}
		if (fruit[1] <= fruit[0]) {
			problems.add("berry_farm_2: " + fruit[1] + " fruit plants, not more than Level I's " + fruit[0]);
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * apiary_garden.md, "Every hive works as a hive" and "Flowers round every hive": each hive drawn empty (honey level 0),
	 * its front face onto a cell bees fly through, and at least 4 flowers within 4 across, 3 below and 2 above it
	 * (BeekeeperWork.FLOWERS_PER_HIVE), each flower on a block it grows on; and every hive harvestable on foot: a spot a
	 * villager walks to with her eyes within 3 blocks of it.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEveryApiaryHiveHasOpenAirFlowersAndAKeeperWithinReach(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (StarterBlueprints.Entry e : List.of(StarterBlueprints.APIARY_GARDEN, StarterBlueprints.APIARY_GARDEN_2)) {
			Blueprint blueprint = blueprint(helper, e);
			Map<BlockPos, BlockState> cells = cells(blueprint);
			String n = n(e);
			Set<BlockPos> reached = walk(drawn(blueprint, Rotation.NONE), false);
			for (BlockPos h : where(cells, Blocks.BEEHIVE)) {
				BlockState hive = at(cells, h);
				if (hive.getValue(BeehiveBlock.HONEY_LEVEL) != 0) {
					problems.add(n + ": the hive at " + h.toShortString() + " is drawn with honey " + hive.getValue(BeehiveBlock.HONEY_LEVEL));
				}
				BlockPos front = h.relative(hive.getValue(BeehiveBlock.FACING));
				BlockState f = at(cells, front);
				if (!f.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty() || !f.getFluidState().isEmpty()) {
					problems.add(n + ": the hive at " + h.toShortString() + " faces " + hive.getValue(BeehiveBlock.FACING) + " onto " + name(f));
				}
				long flowers = 0;
				for (BlockPos p : BlockPos.betweenClosed(h.offset(-4, -3, -4), h.offset(4, 2, 4))) {
					if (at(cells, p).is(BlockTags.FLOWERS)) {
						flowers++;
					}
				}
				if (flowers < 4) {
					problems.add(n + ": " + flowers + " flowers round the hive at " + h.toShortString() + ", want 4");
				}
				if (!reachable(reached, h, 3.0, false)) {
					problems.add(n + ": the hive at " + h.toShortString() + " has no spot within 3 a villager walks to");
				}
			}
			List<String> loose = new ArrayList<>();
			cells.forEach((p, s) -> {
				if (s.is(BlockTags.SMALL_FLOWERS) && !at(cells, p.below()).is(BlockTags.DIRT) && !at(cells, p.below()).is(Blocks.FARMLAND)) {
					loose.add(name(s) + " " + p.toShortString() + " on " + name(at(cells, p.below())));
				}
			});
			if (!loose.isEmpty()) {
				loose.sort(null);
				problems.add(n + ": flowers on nothing they grow on " + loose);
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * quarry.md, "The pit" and "The Miner's way into the pit": the two cells over the pit's top (template y 0 and 1 over x
	 * 1-8, z 2-10) hold nothing solid, so the Miner stands on each layer; and a villager walks from the street to the
	 * blast furnace and on to the pit's edge cell nearest it ((8, -1, 7): stands on it at y 0), where the stairs start.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheQuarrysMinerWalksFromTheFurnaceIntoAnOpenPit(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		Blueprint blueprint = blueprint(helper, StarterBlueprints.QUARRY);
		Map<BlockPos, BlockState> cells = cells(blueprint);
		List<String> solid = new ArrayList<>();
		for (int x = 1; x <= 8; x++) {
			for (int z = 2; z <= 10; z++) {
				for (int y = 0; y <= 1; y++) {
					BlockState s = at(cells, new BlockPos(x, y, z));
					if (!s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()) {
						solid.add(name(s) + " " + x + ", " + y + ", " + z);
					}
				}
			}
		}
		if (!solid.isEmpty()) {
			problems.add("solid over the pit " + solid);
		}
		for (Rotation turn : Rotation.values()) {
			Drawn d = drawn(blueprint, turn);
			Set<BlockPos> reached = walk(d, false);
			// the edge cell (8, -1, 7) turned with the build: a villager stands on it at y 0
			BlockPos edge = turned(blueprint, turn, new BlockPos(8, 0, 7));
			BlockPos furnace = turned(blueprint, turn, new BlockPos(14, 1, 7));
			if (!reachable(reached, furnace, 3.0, false)) {
				problems.add("turned " + turn + ": no spot within 3 of the blast furnace a villager walks to");
			}
			if (!reached.contains(edge)) {
				problems.add("turned " + turn + ": a villager can't walk onto the pit's edge by the furnace (8, 0, 7)");
			}
		}
		helper.assertTrue(problems.isEmpty(), "quarry: " + String.join("; ", problems));
		helper.succeed();
	}

	private static BlockPos turned(Blueprint blueprint, Rotation turn, BlockPos p) {
		Vec3i size = blueprint.size();
		BlockPos far = StructureTemplate.transform(new BlockPos(size.getX() - 1, 0, size.getZ() - 1), Mirror.NONE, turn, BlockPos.ZERO);
		return StructureTemplate.transform(p, Mirror.NONE, turn, BlockPos.ZERO).offset(-Math.min(0, far.getX()), 0, -Math.min(0, far.getZ()));
	}

	/**
	 * Every brief's "A refresh never moves a container or a job site": a standing build finished on an older drawing
	 * ({@code blueprint_history}) keeps its job blocks and containers when refreshed to the new one, so the refreshed build
	 * (the new drawing with every old job site and chest kept in its cell) still has the level's count of its job block
	 * (one composter; the Sifting Shed's one or two cauldrons; the Quarry's one blast furnace) and no new job block of the
	 * kind sits on top of an old cell; and no old kept cell lands in the new drawing's wall, door or berry bush.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaARefreshedOldFarmEndsWithItsLevelsCountOfJobBlocks(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int olds = 0;
		for (Level level : LEVELS) {
			if (level.job() == Blocks.BEEHIVE || level.entry() == StarterBlueprints.GREENHOUSE) {
				continue;
			}
			Map<BlockPos, BlockState> now = cells(blueprint(helper, level.entry()));
			int want = where(now, level.job()).size();
			for (String key : Refreshes.olderKeys(helper.getLevel().getServer(), level.entry().id())) {
				Optional<Blueprint> old = Refreshes.olderDrawing(helper.getLevel().getServer(), level.entry().id(), key);
				if (old.isEmpty()) {
					continue;
				}
				olds++;
				Map<BlockPos, BlockState> merged = new HashMap<>(now);
				List<String> clash = new ArrayList<>();
				cells(old.get()).forEach((p, s) -> {
					boolean kept = s.getBlock() instanceof ChestBlock || s.getBlock() instanceof BarrelBlock
						|| PoiTypes.forState(s).map(h -> h.is(PoiTypeTags.ACQUIRABLE_JOB_SITE)).orElse(false);
					if (kept) {
						BlockState there = at(now, p);
						if (!there.is(s.getBlock()) && (there.getBlock() instanceof DoorBlock || bush(there)
							|| there.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO))) {
							clash.add(name(s) + " " + p.toShortString() + " in the new " + name(there));
						}
						merged.put(p, s);
					}
				});
				int after = where(merged, level.job()).size();
				if (after != want) {
					problems.add(n(level.entry()) + " refreshed from " + key + ": " + after + " " + name(level.job().defaultBlockState())
						+ " (old " + where(cells(old.get()), level.job()) + ", new " + where(now, level.job()) + "), want " + want);
				}
				if (!clash.isEmpty()) {
					problems.add(n(level.entry()) + " refreshed from " + key + ": kept old blocks inside new ones " + clash);
				}
			}
		}
		helper.assertTrue(olds >= 5, "(setup) only " + olds + " older drawings found");
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	// ---- live -------------------------------------------------------------------------------------------------------

	private static void assertJobsBuilt(GameTestHelper helper, BlueprintData.Placement placement, StarterBlueprints.Entry entry, Block job, int count) {
		ServerLevel level = helper.getLevel();
		Vec3i size = entry.size();
		List<BlockPos> jobs = QaRebuiltWorkshops1121bGameTests.built(level, placement.origin(), size, job);
		helper.assertTrue(jobs.size() == count, "the built " + n(entry) + " has " + jobs.size() + " " + name(job.defaultBlockState()) + ", want " + count);
		List<BlockPos> chests = QaRebuiltWorkshops1121bGameTests.built(level, placement.origin(), size, Blocks.CHEST);
		for (BlockPos j : jobs) {
			helper.assertTrue(level.getPoiManager().getType(j).isPresent(), "the built " + name(job.defaultBlockState()) + " at "
				+ j.subtract(placement.origin()).toShortString() + " is no job site");
			helper.assertTrue(chests.stream().anyMatch(c -> QaRebuiltWorkshops1121bGameTests.within(c, j, 8, 4)), "no built chest within 8 / 4 of the "
				+ name(job.defaultBlockState()) + " " + j.subtract(placement.origin()).toShortString());
		}
	}

	/**
	 * berry_farm.md in the world (no other test builds Level II): a Builder builds Berry Farm II from bare ground from
	 * exactly its materials across a save and reload mid-STRUCTURE; DONE, every block right, nothing left over; one
	 * composter, a job site, a chest within 8 / 4; cave vines hung; and the sweet berries grow by themselves on the built
	 * farm: 400 random ticks (a fixed random) ripen every bush (vanilla wants light 9 above it). The glow berries the brief
	 * promises are {@code QaRebuiltFarms1123bBugTests.qaTheBuiltBerryFarmIIsVinesBearGlowBerries}.
	 */
	//$ gametest_ticks_batch HUGE '45000' '"qaFarms1123bBerryFarmII"'
	@GameTest(template = HUGE, timeoutTicks = 45000, batch = "qaFarms1123bBerryFarmII")
	public void qaABuilderBuildsBerryFarmIIAcrossAReloadFromExactlyItsMaterials(GameTestHelper helper) {
		berryFarmII(helper, false);
	}

	/** Berry Farm II built and grown (above); {@code glow}: also a vine must bear glow berries. */
	static void berryFarmII(GameTestHelper helper, boolean wantGlow) {
		ServerLevel level = helper.getLevel();
		int[] grown = {0};
		QaRebuiltWorkshops1121bGameTests.buildAcrossAReload(helper, StarterBlueprints.BERRY_FARM_2, new BlockPos(9, 2, 9), placement -> {
			assertJobsBuilt(helper, placement, StarterBlueprints.BERRY_FARM_2, Blocks.COMPOSTER, 1);
			Vec3i size = StarterBlueprints.BERRY_FARM_2.size();
			helper.assertTrue(!QaRebuiltWorkshops1121bGameTests.built(level, placement.origin(), size, Blocks.SWEET_BERRY_BUSH).isEmpty(),
				"no sweet berry bush in the built Berry Farm II");
			// Built empty (B494: no glow berries, bushes freshly planted), the farm must grow its fruit by itself: 400 random
			// ticks of every bush and vine tip (a fixed random) ripen every bush and (wantGlow) bring glow berries out on the vines.
			List<BlockPos> bushes = QaRebuiltWorkshops1121bGameTests.built(level, placement.origin(), size, Blocks.SWEET_BERRY_BUSH);
			List<BlockPos> vines = new ArrayList<>(QaRebuiltWorkshops1121bGameTests.built(level, placement.origin(), size, Blocks.CAVE_VINES));
			vines.addAll(QaRebuiltWorkshops1121bGameTests.built(level, placement.origin(), size, Blocks.CAVE_VINES_PLANT));
			helper.assertTrue(!vines.isEmpty(), "no cave vines in the built Berry Farm II");
			net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(1123L);
			// (once: succeedWhen runs this again every tick while it fails)
			int rounds = grown[0]++ == 0 ? 400 : 0;
			for (int i = 0; i < rounds; i++) {
				for (BlockPos p : QaRebuiltWorkshops1121bGameTests.built(level, placement.origin().below(), size.offset(0, 2, 0), Blocks.CAVE_VINES)) {
					level.getBlockState(p).randomTick(level, p, random);
				}
				for (BlockPos p : bushes) {
					if (level.getBlockState(p).is(Blocks.SWEET_BERRY_BUSH)) {
						level.getBlockState(p).randomTick(level, p, random);
					}
				}
			}
			List<String> unripe = bushes.stream().filter(p -> !level.getBlockState(p).is(Blocks.SWEET_BERRY_BUSH)
				|| level.getBlockState(p).getValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE) < 2)
				.map(p -> p.subtract(placement.origin()).toShortString() + " light " + level.getRawBrightness(p.above(), 0)).toList();
			helper.assertTrue(unripe.isEmpty(), unripe.size() + " of " + bushes.size() + " sweet berry bushes in the built Berry Farm II never ripen "
				+ "(400 random ticks; vanilla wants light 9 above): " + unripe);
			boolean glow = false;
			List<String> heads = new ArrayList<>();
			for (BlockPos p : BlockPos.betweenClosed(placement.origin().below(), placement.origin().offset(size.getX() - 1, size.getY(), size.getZ() - 1))) {
				BlockState v = level.getBlockState(p);
				if (v.getBlock() instanceof CaveVines) {
					glow |= v.getValue(CaveVines.BERRIES);
					if (v.is(Blocks.CAVE_VINES)) {
						heads.add(p.subtract(placement.origin()).toShortString() + " age " + v.getValue(net.minecraft.world.level.block.GrowingPlantHeadBlock.AGE)
							+ " over " + name(level.getBlockState(p.below())));
					}
				}
			}
			helper.assertTrue(glow || !wantGlow, "the built Berry Farm II's " + vines.size() + " cave vines never bear a glow berry in 400 random ticks (built "
				+ "without berries, a vine only bears when its tip grows down into air): tips " + heads);
		});
	}

	/**
	 * apiary_garden.md in the world (no other test builds Level II): a Builder builds Apiary Garden II from bare ground
	 * from exactly its materials across a save and reload; DONE, every block right, nothing left over; every drawn hive
	 * built, each a job site with a built chest within 8 / 4.
	 */
	//$ gametest_ticks_batch HUGE '45000' '"qaFarms1123bApiaryII"'
	@GameTest(template = HUGE, timeoutTicks = 45000, batch = "qaFarms1123bApiaryII")
	public void qaABuilderBuildsApiaryGardenIIAcrossAReloadFromExactlyItsMaterials(GameTestHelper helper) {
		int hives = where(cells(blueprint(helper, StarterBlueprints.APIARY_GARDEN_2)), Blocks.BEEHIVE).size();
		QaRebuiltWorkshops1121bGameTests.buildAcrossAReload(helper, StarterBlueprints.APIARY_GARDEN_2, new BlockPos(9, 2, 9),
			placement -> assertJobsBuilt(helper, placement, StarterBlueprints.APIARY_GARDEN_2, Blocks.BEEHIVE, hives));
	}

	/**
	 * sifting_shed.md in the world: a Builder builds Sifting Shed II from bare ground from exactly its materials across a
	 * save and reload; DONE, every block right, nothing left over; two empty cauldrons, both job sites, each with a built
	 * chest within 8 / 4.
	 */
	//$ gametest_ticks_batch HUGE '45000' '"qaFarms1123bSiftingShedII"'
	@GameTest(template = HUGE, timeoutTicks = 45000, batch = "qaFarms1123bSiftingShedII")
	public void qaABuilderBuildsSiftingShedIIAcrossAReloadFromExactlyItsMaterials(GameTestHelper helper) {
		QaRebuiltWorkshops1121bGameTests.buildAcrossAReload(helper, StarterBlueprints.SIFTING_SHED_2, new BlockPos(9, 2, 9),
			placement -> assertJobsBuilt(helper, placement, StarterBlueprints.SIFTING_SHED_2, Blocks.CAULDRON, 2));
	}

	private static final BlockPos[] BARRELS = {new BlockPos(1, 2, 4), new BlockPos(3, 2, 4), new BlockPos(4, 2, 4), new BlockPos(5, 2, 4)};
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	/**
	 * compost_yard.md, a missing material: a Builder stocked with everything Compost Yard I needs but its one composter
	 * builds what he can, then waits for materials and names the composter (and only it) as missing; the composter never
	 * appears by itself. Once the player puts a composter in a stock barrel the build ends DONE, every block right, with
	 * the composter a job site and a chest within 8 / 4.
	 */
	//$ gametest_ticks_batch HUGE '40000' '"qaFarms1123bCompostMissing"'
	@GameTest(template = HUGE, timeoutTicks = 40000, batch = "qaFarms1123bCompostMissing")
	public void qaACompostYardWithoutItsComposterWaitsAndNamesItThenFinishes(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
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
		StarterBlueprints.Entry entry = StarterBlueprints.COMPOST_YARD;
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(9, 2, 9)),
			Rotation.NONE, Mirror.NONE);
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		BuildPlan plan = BuildPlan.create(blueprint(helper, entry), placement);
		Map<Item, Integer> needs = new LinkedHashMap<>(plan.materials());
		helper.assertTrue(needs.getOrDefault(Items.COMPOSTER, 0) == 1, "(setup) the yard's list has " + needs.get(Items.COMPOSTER) + " composters");
		List<ItemStack> stock = new ArrayList<>();
		needs.forEach((item, count) -> {
			for (int left = item == Items.COMPOSTER ? 0 : count; left > 0; ) {
				int k = Math.min(left, item.getDefaultMaxStackSize());
				stock.add(new ItemStack(item, k));
				left -= k;
			}
		});
		helper.assertTrue(stock.size() <= BARRELS.length * 27 - 1, "(setup) the test needs more barrels: " + stock.size());
		for (int i = 0; i < BARRELS.length; i++) {
			helper.setBlock(BARRELS[i], Blocks.BARREL);
			BaseContainerBlockEntity barrel = helper.getBlockEntity(BARRELS[i]);
			for (int slot = 0; slot < barrel.getContainerSize() && i * 27 + slot < stock.size(); slot++) {
				barrel.setItem(slot, stock.get(i * 27 + slot));
			}
		}
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, entry.id(), placement);
		helper.assertTrue(site != null, "the Compost Yard didn't start");
		UUID id = site.id();
		boolean[] given = {false};
		String[] seen = {null};
		helper.onEachTick(() -> {
			if (level.getDayTime() % 24000 > 11000) {
				helper.setDayTime(2000);
			}
			BuildSite s = BuildSiteManager.get(level).get(id);
			if (given[0] || s == null) {
				return;
			}
			if (s.status() == BuildSite.Status.WAITING_FOR_MATERIALS && !s.missing().isEmpty()) {
				seen[0] = s.stage() + " " + s.missing();
				helper.assertTrue(s.missing().keySet().equals(Set.of(Items.COMPOSTER)), "the waiting builder names " + s.missing()
					+ " as missing, only the composter is");
				helper.assertTrue(QaRebuiltWorkshops1121bGameTests.built(level, placement.origin(), entry.size(), Blocks.COMPOSTER).isEmpty(),
					"a composter stands though none was supplied");
				given[0] = true;
				((Container) helper.getBlockEntity(BARRELS[BARRELS.length - 1])).setItem(26, new ItemStack(Items.COMPOSTER));
			}
		});
		helper.succeedWhen(() -> {
			BuildSite s = BuildSiteManager.get(level).get(id);
			helper.assertTrue(given[0], "the builder never waited for the composter (status " + (s == null ? "finished" : s.stage() + " " + s.status()
				+ " missing " + s.missing() + ", skipped " + s.skipped()) + ")");
			helper.assertTrue(s == null, s == null ? "" : "still building after the composter came: " + s.stage() + " " + s.status() + " missing " + s.missing());
			List<BlockPos> wrong = plan.unfinished(level);
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) wrong after the build, e.g. "
				+ wrong.stream().limit(5).map(p -> p.subtract(placement.origin()).toShortString() + "=" + level.getBlockState(p)).toList());
			assertJobsBuilt(helper, placement, entry, Blocks.COMPOSTER, 1);
		});
	}

	/**
	 * sifting_shed.md, the job block broken mid-build: a Builder builds Sifting Shed I from exactly its materials; the
	 * moment its cauldron is in, a player breaks it and puts the cauldron back in a stock barrel. The build still ends
	 * DONE with every block right: the cauldron is back in its cell, a job site, with a chest within 8 / 4.
	 */
	//$ gametest_ticks_batch HUGE '40000' '"qaFarms1123bCauldronBroken"'
	@GameTest(template = HUGE, timeoutTicks = 40000, batch = "qaFarms1123bCauldronBroken")
	public void qaASiftingShedsCauldronBrokenMidBuildIsPutBack(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
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
		StarterBlueprints.Entry entry = StarterBlueprints.SIFTING_SHED;
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(9, 2, 9)),
			Rotation.NONE, Mirror.NONE);
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		BuildPlan plan = BuildPlan.create(blueprint(helper, entry), placement);
		List<ItemStack> stock = new ArrayList<>();
		plan.materials().forEach((item, count) -> {
			for (int left = count; left > 0; ) {
				int k = Math.min(left, item.getDefaultMaxStackSize());
				stock.add(new ItemStack(item, k));
				left -= k;
			}
		});
		helper.assertTrue(stock.size() <= BARRELS.length * 27 - 1, "(setup) the test needs more barrels: " + stock.size());
		for (int i = 0; i < BARRELS.length; i++) {
			helper.setBlock(BARRELS[i], Blocks.BARREL);
			BaseContainerBlockEntity barrel = helper.getBlockEntity(BARRELS[i]);
			for (int slot = 0; slot < barrel.getContainerSize() && i * 27 + slot < stock.size(); slot++) {
				barrel.setItem(slot, stock.get(i * 27 + slot));
			}
		}
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, entry.id(), placement);
		helper.assertTrue(site != null, "the Sifting Shed didn't start");
		UUID id = site.id();
		String[] broke = {null};
		helper.onEachTick(() -> {
			if (level.getDayTime() % 24000 > 11000) {
				helper.setDayTime(2000);
			}
			if (broke[0] != null) {
				return;
			}
			List<BlockPos> c = QaRebuiltWorkshops1121bGameTests.built(level, placement.origin(), entry.size(), Blocks.CAULDRON);
			BuildSite s = BuildSiteManager.get(level).get(id);
			if (!c.isEmpty() && s != null) {
				broke[0] = c.get(0).subtract(placement.origin()).toShortString() + " in " + s.stage();
				level.destroyBlock(c.get(0), false);
				((Container) helper.getBlockEntity(BARRELS[BARRELS.length - 1])).setItem(26, new ItemStack(Items.CAULDRON));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(broke[0] != null, "(setup) the cauldron was never seen going in before the build ended");
			BuildSite s = BuildSiteManager.get(level).get(id);
			helper.assertTrue(s == null, s == null ? "" : "still building: " + s.stage() + " " + s.status() + " missing " + s.missing());
			List<BlockPos> wrong = plan.unfinished(level);
			helper.assertTrue(wrong.isEmpty(), "the cauldron broken at " + broke[0] + ": " + wrong.size() + " block(s) wrong after the build, e.g. "
				+ wrong.stream().limit(5).map(p -> p.subtract(placement.origin()).toShortString() + "=" + level.getBlockState(p)).toList());
			assertJobsBuilt(helper, placement, entry, Blocks.CAULDRON, 1);
		});
	}
}
