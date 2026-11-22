package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.people.Homes;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * QA lane B (2026-11-21): the rebuilt homes' biome looks (49.10a-g: {@code looks/<culture>/<family>[_n]}, desert,
 * savanna, snowy, taiga), the Daub House (49.2, {@code house_t1_b}) and the Starter Cottage (49.3, version B), written
 * from 49.10a's text ("the same rooms, beds, job blocks and furniture spots, so every home test holds; the shape, roof
 * and materials of that climate"), {@code briefs/starter_cottage.md} and the home briefs, not from the designers' recipes.
 *
 * <p>What the families' own {@code *LooksGameTests} already prove (culture picks, fallbacks, bed and job-block counts per
 * level, builds to DONE, a desert reload) isn't repeated. These add what a villager meets: every bed of every look is
 * walked to from the street (wooden doors open, fence gates shut, nobody stands on a fence, wall or pane), each look has
 * a front door at its z = 0 side walked through from the street, and each look's upgrade (its {@code _n}, the styled II
 * in the same style) keeps its level below's beds, job blocks and chests in their cells. The walk is the one from
 * {@code QaRebuiltHomes1121bGameTests}.
 */
public class QaRebuiltLooks1121bGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final List<String> CULTURES = List.of("desert", "savanna", "snowy", "taiga");

	/** Every rebuilt home family with looks (49.10a-g) and its level count. */
	private static final Map<String, Integer> FAMILIES = new LinkedHashMap<>();

	static {
		FAMILIES.put("starter_cottage", 4);
		FAMILIES.put("stone_house", 4);
		FAMILIES.put("townhouse", 4);
		FAMILIES.put("terrace", 2);
		FAMILIES.put("manor", 4);
		FAMILIES.put("weavers_cottage", 2);
		FAMILIES.put("house_t1_b", 1);
		FAMILIES.put("farmstead", 3);
		FAMILIES.put("ranch", 2);
		FAMILIES.put("artisans_house", 3);
	}

	/** The families the briefs call homes, with a front door on the ground floor at the z = 0 side. */
	private static final Set<String> FRONT_DOOR = Set.of("starter_cottage", "stone_house", "townhouse", "terrace", "manor", "house_t1_b",
		"artisans_house");

	private static String levelPath(String family, int level) {
		return level == 0 ? family : family + "_" + (level + 1);
	}

	/** One drawing: the base ({@code culture} null) or a look, with its family and level. */
	private record Drawing(String family, int level, String culture, ResourceLocation id, Blueprint blueprint) {
		String label() {
			return id.getPath();
		}
	}

	static Optional<Blueprint> load(GameTestHelper helper, ResourceLocation id) {
		return BlueprintLibrary.get(helper.getLevel(), id);
	}

	/** Every look that exists, family by family, level by level, culture by culture; each base too when {@code bases}. */
	private static List<Drawing> drawings(GameTestHelper helper, boolean bases) {
		List<Drawing> out = new ArrayList<>();
		FAMILIES.forEach((family, levels) -> {
			for (int level = 0; level < levels; level++) {
				String path = levelPath(family, level);
				if (bases) {
					ResourceLocation id = AliveWorkplace.id(path);
					out.add(new Drawing(family, level, null, id, load(helper, id).orElseThrow(() -> new AssertionError("missing " + id))));
				}
				for (String culture : CULTURES) {
					ResourceLocation id = AliveWorkplace.id("looks/" + culture + "/" + path);
					int lv = level;
					load(helper, id).ifPresent(b -> out.add(new Drawing(family, lv, culture, id, b)));
				}
			}
		});
		return out;
	}

	static Map<BlockPos, BlockState> cells(Blueprint blueprint) {
		return QaBasementGameTests.blocks(blueprint);
	}

	static BlockState at(Map<BlockPos, BlockState> cells, BlockPos pos) {
		return QaBasementGameTests.at(cells, pos);
	}

	private static String name(BlockState s) {
		return BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
	}

	static List<BlockPos> bedHeads(Map<BlockPos, BlockState> cells) {
		return cells.entrySet().stream().filter(e -> e.getValue().getBlock() instanceof BedBlock
			&& e.getValue().getValue(BedBlock.PART) == BedPart.HEAD).map(Map.Entry::getKey).sorted().toList();
	}

	static BlockPos bedFoot(Map<BlockPos, BlockState> cells, BlockPos head) {
		return head.relative(at(cells, head).getValue(BedBlock.FACING).getOpposite());
	}

	// ---- a villager's walk (as QaRebuiltHomes1121bGameTests; panes added to the no-floor blocks) --------------------

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
		return state.getBlock() instanceof FenceBlock || state.getBlock() instanceof WallBlock || state.getBlock() instanceof FenceGateBlock
			|| state.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock;
	}

	private static boolean standable(Map<BlockPos, BlockState> cells, BlockPos feet, boolean gates) {
		BlockState here = at(cells, feet);
		if (!open(here, gates) || !open(at(cells, feet.above()), gates) || !here.getFluidState().isEmpty()) {
			return false;
		}
		BlockState below = at(cells, feet.below());
		return here.is(Blocks.LADDER) || below.is(Blocks.LADDER) || (!open(below, false) && !noFloor(below) && below.getFluidState().isEmpty());
	}

	/** Every spot a villager reaches on foot from the street in front (z < 0): level, a step up, up to three down, ladders. */
	static Set<BlockPos> walk(Blueprint blueprint, Map<BlockPos, BlockState> cells, boolean gates) {
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

	static boolean beside(Set<BlockPos> reached, BlockPos target) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (reached.contains(target.relative(d)) || reached.contains(target.relative(d).below())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A villager gets to the bed: he stands beside its head or foot, or beside a bed right next to it (the game's
	 * pathfinder steps onto a bed, 9/16 high, so it reaches a bed boxed in beside another: see
	 * {@link #qaAVillagerFindsAPathToEveryBedOfTheDaubHouseAndTheDesertStoneHouseI}).
	 */
	static boolean bedReached(Set<BlockPos> reached, Map<BlockPos, BlockState> cells, BlockPos head) {
		BlockPos foot = bedFoot(cells, head);
		if (beside(reached, head) || beside(reached, foot)) {
			return true;
		}
		for (BlockPos part : List.of(head, foot)) {
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos next = part.relative(d);
				if (!next.equals(head) && !next.equals(foot) && at(cells, next).getBlock() instanceof BedBlock && beside(reached, next)) {
					return true;
				}
			}
		}
		return false;
	}

	// ---- static -----------------------------------------------------------------------------------------------------

	/**
	 * 49.10a: a look keeps "the same rooms, beds ... so every home test holds", and the home briefs say every bed is
	 * walked to from the front door (starter_cottage.md: each bed a villager's home). Every bed of every look of every
	 * level, and of the Starter Cottage's and Daub House's own drawings, has a spot beside its head or foot a villager
	 * walks to from the street (doors open, fence gates shut, no standing on fences, walls, bars or panes).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEveryBedOfEveryLookIsWalkedToFromTheStreet(GameTestHelper helper) {
		List<Drawing> all = drawings(helper, true);
		List<String> problems = new ArrayList<>();
		int looks = 0;
		for (Drawing d : all) {
			boolean mine = d.culture() != null || d.family().equals("starter_cottage") || d.family().equals("house_t1_b");
			if (!mine) {
				continue; // the other bases are QaRebuiltHomes1121bGameTests'
			}
			if ("desert".equals(d.culture()) && d.family().equals("stone_house")) {
				continue; // QaRebuiltLooks1121bBugTests' (its II-IV bedroom is shut off)
			}
			looks += d.culture() != null ? 1 : 0;
			Map<BlockPos, BlockState> cells = cells(d.blueprint());
			Set<BlockPos> reached = walk(d.blueprint(), cells, false);
			List<String> cut = new ArrayList<>();
			for (BlockPos head : bedHeads(cells)) {
				if (!bedReached(reached, cells, head)) {
					cut.add(head.toShortString());
				}
			}
			if (!cut.isEmpty()) {
				problems.add(d.label() + ": beds no villager walks to from the street " + cut);
			}
		}
		// the Starter Cottage's 16 looks and the Daub House's 4 at least (49.10a, 49.10d)
		helper.assertTrue(looks >= 20, "(setup) only " + looks + " looks found");
		helper.assertTrue(problems.isEmpty(), problems.size() + " drawings: " + String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * The home briefs: a front door on the ground floor at the z = 0 side (terrace.md: one per home, 2 in I and 3 in II).
	 * Each look of a home family has a wooden door low in the build (lower half at y 0-2) in its front rows (z 0-3) with a
	 * spot in front of it and one behind it that a villager walked to from the street; so does the Starter Cottage and
	 * the Daub House as drawn.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEveryLookHasAFrontDoorAVillagerWalksThroughFromTheStreet(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (Drawing d : drawings(helper, true)) {
			if (!FRONT_DOOR.contains(d.family())) {
				continue;
			}
			if (d.culture() == null && !d.family().equals("starter_cottage") && !d.family().equals("house_t1_b")) {
				continue;
			}
			Map<BlockPos, BlockState> cells = cells(d.blueprint());
			Set<BlockPos> reached = walk(d.blueprint(), cells, false);
			List<BlockPos> fronts = new ArrayList<>();
			cells.forEach((p, s) -> {
				if (s.getBlock() instanceof DoorBlock && !s.is(Blocks.IRON_DOOR) && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
					&& p.getY() >= 0 && p.getY() <= 2 && p.getZ() <= 3
					&& (reached.contains(p.north()) || reached.contains(p.north().below()))
					&& (reached.contains(p.south()) || reached.contains(p.south().below()))) {
					fronts.add(p);
				}
			});
			int want = d.family().equals("terrace") ? (d.level() == 0 ? 2 : 3) : 1;
			if (fronts.size() < want) {
				problems.add(d.label() + ": " + fronts.size() + " front doors walked through from the street, want " + want);
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * The briefs' "Kept cells" inside one look: a look's upgrade (the {@code _n} of the same culture, which 49.10a's
	 * picker hands an upgrade of a look) is at least its level below's size and keeps every bed (same facing and part),
	 * job block, chest and barrel (same facing) of that look's level below in its cell, so a builder upgrading a desert
	 * house never has to break a claimed bed or job site. Also the bed counts of starter_cottage.md per level for every
	 * cottage look: I exactly 1, II more than I, III the same as II, IV at least III.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEachLooksUpgradeKeepsItsLevelBelowsBedsJobBlocksAndChests(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int pairs = 0;
		for (String culture : CULTURES) {
			for (Map.Entry<String, Integer> f : FAMILIES.entrySet()) {
				int[] beds = new int[f.getValue()];
				boolean allLevels = true;
				for (int level = 0; level < f.getValue(); level++) {
					Optional<Blueprint> hi = load(helper, AliveWorkplace.id("looks/" + culture + "/" + levelPath(f.getKey(), level)));
					if (hi.isEmpty()) {
						allLevels = false;
						continue;
					}
					beds[level] = bedHeads(cells(hi.get())).size();
					if (level == 0) {
						continue;
					}
					String loName = "looks/" + culture + "/" + levelPath(f.getKey(), level - 1);
					Optional<Blueprint> lo = load(helper, AliveWorkplace.id(loName));
					String hiName = "looks/" + culture + "/" + levelPath(f.getKey(), level);
					if (lo.isEmpty()) {
						problems.add(hiName + " exists without its level below " + loName);
						continue;
					}
					pairs++;
					Vec3i ls = lo.get().size();
					Vec3i hs = hi.get().size();
					if (hs.getX() < ls.getX() || hs.getY() < ls.getY() || hs.getZ() < ls.getZ()) {
						problems.add(hiName + " (" + hs.toShortString() + ") is smaller than " + loName + " (" + ls.toShortString() + ") one way");
					}
					Map<BlockPos, BlockState> lc = cells(lo.get());
					Map<BlockPos, BlockState> hc = cells(hi.get());
					List<String> moved = new ArrayList<>();
					lc.forEach((p, s) -> {
						boolean bed = s.getBlock() instanceof BedBlock;
						boolean store = s.getBlock() instanceof ChestBlock || s.getBlock() instanceof BarrelBlock;
						boolean job = PoiTypes.forState(s).isPresent();
						if (!bed && !store && !job) {
							return;
						}
						BlockState u = at(hc, p);
						boolean same = u.getBlock() == s.getBlock();
						if (same && bed) {
							same = u.getValue(BedBlock.FACING) == s.getValue(BedBlock.FACING) && u.getValue(BedBlock.PART) == s.getValue(BedBlock.PART);
						} else if (same && store && s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
							same = u.getValue(BlockStateProperties.HORIZONTAL_FACING) == s.getValue(BlockStateProperties.HORIZONTAL_FACING);
						}
						if (!same) {
							moved.add(name(s) + " " + p.toShortString() + " -> " + name(u));
						}
					});
					moved.sort(null);
					if (!moved.isEmpty()) {
						problems.add(loName + " -> " + hiName + " moves or removes " + moved.size() + ": " + moved.subList(0, Math.min(6, moved.size())));
					}
				}
				if (f.getKey().equals("starter_cottage") && allLevels && (beds[0] != 1 || beds[1] <= beds[0] || beds[2] != beds[1] || beds[3] < beds[2])) {
					problems.add(culture + " Starter Cottage beds " + java.util.Arrays.toString(beds) + ": want 1, more than I, the same as II, at least III");
				}
			}
		}
		helper.assertTrue(pairs >= 12, "(setup) only " + pairs + " look upgrades found");
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * starter_cottage.md (version B, 49.3) as drawn: I has exactly 1 bed, one barrel and one crafting table and no other
	 * job block, a chest, and a door; II more beds than I; III the same beds as II and a smoker; IV a cellar exactly 4
	 * deep with a ladder or stairs and a lantern below the ground; every level keeps the level below's beds, job blocks
	 * and chests in their cells.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheStarterCottageBLevelsHoldTheirBriefsRooms(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int[] beds = new int[4];
		List<Map<BlockPos, BlockState>> levels = new ArrayList<>();
		for (int level = 0; level < 4; level++) {
			ResourceLocation id = AliveWorkplace.id(levelPath("starter_cottage", level));
			Blueprint b = load(helper, id).orElseThrow(() -> new AssertionError("missing " + id));
			Map<BlockPos, BlockState> c = cells(b);
			levels.add(c);
			beds[level] = bedHeads(c).size();
			if (level == 3) {
				helper.assertTrue(b.basement() == 4, "starter_cottage_4's cellar is " + b.basement() + " deep, the brief says 4");
				boolean way = c.entrySet().stream().anyMatch(e -> e.getKey().getY() < 0
					&& (e.getValue().is(Blocks.LADDER) || e.getValue().is(net.minecraft.tags.BlockTags.STAIRS)));
				boolean lamp = c.entrySet().stream().anyMatch(e -> e.getKey().getY() < 0 && e.getValue().is(Blocks.LANTERN));
				if (!way || !lamp) {
					problems.add("starter_cottage_4's cellar: way down " + way + ", lantern " + lamp);
				}
			}
		}
		Map<BlockPos, BlockState> one = levels.get(0);
		List<String> jobs = new ArrayList<>();
		one.forEach((p, s) -> {
			if (PoiTypes.forState(s).filter(h -> !h.is(PoiTypes.HOME) && !h.is(PoiTypes.MEETING)).isPresent()) {
				jobs.add(name(s));
			}
		});
		jobs.sort(null);
		if (!jobs.equals(List.of("barrel", "crafting_table"))) {
			problems.add("starter_cottage's job blocks are " + jobs + ", the brief says exactly one barrel and one crafting table");
		}
		if (one.values().stream().noneMatch(s -> s.is(Blocks.CHEST))) {
			problems.add("starter_cottage has no chest");
		}
		if (levels.get(2).values().stream().noneMatch(s -> s.is(Blocks.SMOKER))) {
			problems.add("starter_cottage_3 has no smoker");
		}
		if (beds[0] != 1 || beds[1] <= beds[0] || beds[2] != beds[1] || beds[3] < beds[2]) {
			problems.add("Starter Cottage beds " + java.util.Arrays.toString(beds) + ": want 1, more than I, the same as II, at least III");
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * The game's own pathfinder on placed drawings (the arbiter for the static walk): from the street in front, a villager
	 * finds a path to each bed of {@code ids} (fence gates as placed).
	 */
	static void pathsToEveryBed(GameTestHelper helper, List<ResourceLocation> ids, List<BlockPos> origins) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		List<Villager> walkers = new ArrayList<>();
		List<List<BlockPos>> beds = new ArrayList<>();
		for (int i = 0; i < ids.size(); i++) {
			BlockPos at = helper.absolutePos(origins.get(i));
			var template = level.getStructureManager().get(ids.get(i)).orElseThrow(() -> new AssertionError("missing template"));
			template.placeInWorld(level, at, at, new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),
				net.minecraft.util.RandomSource.create(1121L), 2);
			Vec3i size = template.getSize();
			List<BlockPos> heads = new ArrayList<>();
			for (BlockPos p : BlockPos.betweenClosed(at, at.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
				BlockState st = level.getBlockState(p);
				if (st.getBlock() instanceof BedBlock && st.getValue(BedBlock.PART) == BedPart.HEAD) {
					heads.add(p.immutable());
				}
			}
			helper.assertTrue(!heads.isEmpty(), "(setup) no beds placed for " + ids.get(i));
			beds.add(heads);
			Villager v = helper.spawn(EntityType.VILLAGER, origins.get(i).offset(size.getX() / 2, 0, -2));
			v.setNoAi(true);
			walkers.add(v);
		}
		helper.runAfterDelay(10, () -> {
			List<String> cut = new ArrayList<>();
			for (int i = 0; i < ids.size(); i++) {
				Villager v = walkers.get(i);
				v.setOnGround(true);
				BlockPos at = helper.absolutePos(origins.get(i));
				for (BlockPos bed : beds.get(i)) {
					var path = v.getNavigation().createPath(bed, 1);
					if (path == null || !path.canReach()) {
						cut.add(ids.get(i).getPath() + " bed " + bed.subtract(at).toShortString());
					}
				}
			}
			walkers.forEach(Entity::discard);
			helper.assertTrue(cut.isEmpty(), "no path from the street to " + cut);
			helper.succeed();
		});
	}

	/**
	 * The Daub House (49.2, as approved: one bed boxed in between two night stands, reached over its neighbor) and the
	 * desert Stone House I (49.10b), placed: the game's pathfinder finds a way from the street to every bed.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaLooks1121bPaths"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaLooks1121bPaths")
	public void qaAVillagerFindsAPathToEveryBedOfTheDaubHouseAndTheDesertStoneHouseI(GameTestHelper helper) {
		pathsToEveryBed(helper, List.of(AliveWorkplace.id("house_t1_b"), AliveWorkplace.id("looks/desert/stone_house")),
			List.of(new BlockPos(1, 2, 5), new BlockPos(16, 2, 5)));
	}

	// ---- live -------------------------------------------------------------------------------------------------------

	private static final BlockPos[] BARRELS = {new BlockPos(1, 2, 4), new BlockPos(3, 2, 4), new BlockPos(4, 2, 4), new BlockPos(5, 2, 4)};
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

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
	 * 49.10a in the world: a real Builder builds the snowy Starter Cottage I (looks/snowy/starter_cottage) from exactly its
	 * material list, saved and loaded (the site and the builder) mid-STRUCTURE; it ends DONE with every block right and
	 * nothing left over, still the snowy look, with its one bed a tier-1 home and its barrel and crafting table standing.
	 */
	//$ gametest_ticks_batch HUGE '45000' '"qaLooks1121bSnowyBuild"'
	@GameTest(template = HUGE, timeoutTicks = 45000, batch = "qaLooks1121bSnowyBuild")
	public void qaABuilderBuildsTheSnowyCottageAcrossAReloadFromExactlyItsMaterials(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		rules(helper);
		ResourceLocation look = AliveWorkplace.id("looks/snowy/starter_cottage");
		Blueprint blueprint = load(helper, look).orElseThrow(() -> new AssertionError("missing " + look));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(9, 2, 9)),
			Rotation.NONE, Mirror.NONE);
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		BuildPlan plan = BuildPlan.create(blueprint, placement);
		Map<Item, Integer> needs = new LinkedHashMap<>(plan.materials());
		helper.assertTrue(needs.values().stream().mapToInt(Integer::intValue).sum() >= 100, "(setup) the snowy cottage needs only " + needs);
		List<ItemStack> stock = new ArrayList<>();
		needs.forEach((item, count) -> {
			for (int left = count; left > 0; ) {
				int k = Math.min(left, item.getDefaultMaxStackSize());
				stock.add(new ItemStack(item, k));
				left -= k;
			}
		});
		helper.assertTrue(stock.size() <= BARRELS.length * 27, "(setup) the test needs more barrels: " + stock.size() + " stacks");
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
		BuildSite site = Builders.start(level, builder, null, look, placement);
		helper.assertTrue(site != null, "the snowy cottage didn't start");
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
				BuildSite before = s;
				who[0] = reload(helper, level, who[0]);
				BuildSite after = BuildSiteManager.get(level).get(id);
				helper.assertTrue(after != null, "the site didn't come back from its save");
				helper.assertTrue(look.equals(after.structure()), "the reloaded site builds " + after.structure() + ", not the snowy look (was "
					+ before.structure() + ")");
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
			Vec3i size = blueprint.size();
			List<BlockPos> beds = new ArrayList<>();
			boolean barrel = false;
			boolean table = false;
			for (BlockPos p : BlockPos.betweenClosed(placement.origin(), placement.origin().offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
				BlockState st = level.getBlockState(p);
				if (st.getBlock() instanceof BedBlock && st.getValue(BedBlock.PART) == BedPart.HEAD) {
					beds.add(p.immutable());
				}
				barrel |= st.is(Blocks.BARREL);
				table |= st.is(Blocks.CRAFTING_TABLE);
			}
			helper.assertTrue(beds.size() == 1, "the built snowy cottage has " + beds.size() + " beds, the brief says 1");
			helper.assertTrue(Homes.at(level, beds.get(0)).map(Homes.Home::tier).orElse(0) == 1, "the snowy cottage's bed isn't a tier-1 home: "
				+ Homes.at(level, beds.get(0)));
			helper.assertTrue(barrel && table, "the built snowy cottage lost a job block: barrel " + barrel + ", crafting table " + table);
		});
	}
}
