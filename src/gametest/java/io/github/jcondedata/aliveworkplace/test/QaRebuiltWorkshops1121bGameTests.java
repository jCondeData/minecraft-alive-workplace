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
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA lane B (2026-11-21), the workplaces rebuilt in Milestone 50 (50.1 Builder's Workshop; 50.2 Carpenter's Workshop,
 * Mason's Yard I-II, Sawmill; 50.3 Smithy I-II, Brass Foundry, Tinker's Workshop I-II; 50.6 Fletcher's Lodge I-II,
 * Tailor's Shop I-II, Print Shop I-II; 50.7 Glassblower's, Jeweler's I-III, Crafter's Workshop, Workshop; 50.8
 * Physician's House, Clinic, Healing Center I-IV), written from their blind briefs
 * ({@code tools/blueprints/briefs/<family>.md}), not from the designers' recipes.
 *
 * <p>The walk is a villager's (as in {@code QaRebuiltCivic1121cGameTests} and {@code QaRebuiltHomes1121bGameTests}):
 * wooden doors open, a fence gate stays shut, nobody stands on a fence or wall, and it starts in the street in front
 * (the z = 0 side). A job block counts as reached when a villager can stand right beside it; a chest when he stands
 * within a player's reach of it (2 across, 1 up or down). The three Create builds (Sawmill, Brass Foundry, Workshop)
 * load here without Create, their machines as air: only their vanilla job block and chest are read.
 */
public class QaRebuiltWorkshops1121bGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/** One level as its brief pins it: the exact count of each job block, whether any other job block is barred, the plot. */
	private record Level(StarterBlueprints.Entry entry, Map<Block, Integer> jobs, boolean only, int w, int h, int d) {
	}

	/** A family; {@code chests}: the brief wants a chest within 8 across and 4 up or down of each job block. */
	private record Family(String name, boolean chests, List<Level> levels) {
	}

	private static Level lv(StarterBlueprints.Entry e, boolean only, int w, int h, int d, Object... jobs) {
		Map<Block, Integer> m = new LinkedHashMap<>();
		for (int i = 0; i < jobs.length; i += 2) {
			m.put((Block) jobs[i], (Integer) jobs[i + 1]);
		}
		return new Level(e, m, only, w, h, d);
	}

	private static final int ANY = 999;

	private static final List<Family> FAMILIES = List.of(
		new Family("builders_workshop", true, List.of(lv(StarterBlueprints.BUILDERS_WORKSHOP, true, 18, 18, 20, ModBlocks.BLUEPRINT_TABLE, 1))),
		new Family("carpenters_workshop", true, List.of(lv(StarterBlueprints.CARPENTERS_WORKSHOP, true, 18, 18, 16, Blocks.CRAFTING_TABLE, 1))),
		new Family("masons_yard", true, List.of(
			lv(StarterBlueprints.MASONS_YARD, true, 18, 28, 22, Blocks.STONECUTTER, 1),
			lv(StarterBlueprints.MASONS_YARD_2, true, 18, 28, 22, Blocks.STONECUTTER, 2))),
		new Family("sawmill", true, List.of(lv(StarterBlueprints.SAWMILL, false, 20, 26, 16, Blocks.FLETCHING_TABLE, 1))),
		new Family("smithy", true, List.of(
			lv(StarterBlueprints.SMITHY, true, 14, 20, 10, Blocks.BLAST_FURNACE, 1, Blocks.SMITHING_TABLE, 1, Blocks.GRINDSTONE, 1),
			lv(StarterBlueprints.SMITHY_2, true, 18, 28, 22, Blocks.BLAST_FURNACE, 2, Blocks.SMITHING_TABLE, 1, Blocks.GRINDSTONE, 1))),
		new Family("brass_foundry", true, List.of(lv(StarterBlueprints.BRASS_FOUNDRY, false, 20, 27, 16, Blocks.SMOKER, 1))),
		new Family("tinkers_workshop", true, List.of(
			lv(StarterBlueprints.TINKERS_WORKSHOP, true, 13, 11, 13, Blocks.SMITHING_TABLE, 1),
			lv(StarterBlueprints.TINKERS_WORKSHOP_2, true, 13, 16, 14, Blocks.SMITHING_TABLE, 1))),
		new Family("fletchers_lodge", true, List.of(
			lv(StarterBlueprints.FLETCHERS_LODGE, true, 15, 18, 10, Blocks.FLETCHING_TABLE, 1),
			lv(StarterBlueprints.FLETCHERS_LODGE_2, true, 18, 18, 22, Blocks.FLETCHING_TABLE, 2))),
		new Family("tailors_shop", true, List.of(
			lv(StarterBlueprints.TAILORS_SHOP, true, 18, 18, 22, Blocks.LOOM, 1),
			lv(StarterBlueprints.TAILORS_SHOP_2, true, 18, 18, 22, Blocks.LOOM, 1))),
		new Family("print_shop", true, List.of(
			lv(StarterBlueprints.PRINT_SHOP, true, 18, 18, 22, Blocks.CARTOGRAPHY_TABLE, 1),
			lv(StarterBlueprints.PRINT_SHOP_2, true, 18, 18, 22, Blocks.CARTOGRAPHY_TABLE, 1))),
		new Family("glassblowers_workshop", true, List.of(lv(StarterBlueprints.GLASSBLOWERS_WORKSHOP, true, ANY, ANY, ANY, ModBlocks.GLASS_KILN, 1))),
		new Family("jewellers_workshop", true, List.of(
			lv(StarterBlueprints.JEWELLERS_WORKSHOP, true, 18, 13, 21, Blocks.STONECUTTER, 1),
			lv(StarterBlueprints.JEWELLERS_WORKSHOP_2, true, 18, 13, 21, Blocks.STONECUTTER, 1),
			lv(StarterBlueprints.JEWELLERS_WORKSHOP_3, true, 18, 13, 21, Blocks.STONECUTTER, 1))),
		new Family("crafters_workshop", false, List.of(lv(StarterBlueprints.CRAFTERS_WORKSHOP, true, ANY, ANY, ANY))),
		new Family("workshop", true, List.of(lv(StarterBlueprints.WORKSHOP, true, 20, 18, 20, Blocks.BLAST_FURNACE, 1))),
		new Family("physicians_house", true, List.of(lv(StarterBlueprints.PHYSICIANS_HOUSE, true, 18, 18, 22, Blocks.BREWING_STAND, 1))),
		new Family("clinic", true, List.of(lv(StarterBlueprints.CLINIC, true, 18, 18, 16, Blocks.BREWING_STAND, 1))),
		new Family("healing_center", true, List.of(
			lv(StarterBlueprints.HEALING_CENTER, true, 13, 16, 13, Blocks.BREWING_STAND, 1),
			lv(StarterBlueprints.HEALING_CENTER_2, false, 21, 18, 26, Blocks.BREWING_STAND, 1),
			lv(StarterBlueprints.HEALING_CENTER_3, false, 21, 18, 26, Blocks.BREWING_STAND, 1),
			lv(StarterBlueprints.HEALING_CENTER_4, false, 21, 18, 26, Blocks.BREWING_STAND, 1))));

	// ---- drawings -----------------------------------------------------------------------------------------------------

	private static Blueprint blueprint(GameTestHelper helper, StarterBlueprints.Entry entry) {
		return BlueprintLibrary.get(helper.getLevel(), entry.id()).orElseThrow(() -> new AssertionError("missing " + entry.id()));
	}

	/** The drawing's cells, its basement (if any) below y = 0, so y = 0 is the ground floor in every level. */
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

	private static List<BlockPos> chests(Map<BlockPos, BlockState> cells) {
		return cells.entrySet().stream().filter(e -> e.getValue().getBlock() instanceof ChestBlock).map(Map.Entry::getKey).sorted().toList();
	}

	private static List<BlockPos> bedHeads(Map<BlockPos, BlockState> cells) {
		return cells.entrySet().stream().filter(e -> e.getValue().getBlock() instanceof BedBlock
			&& e.getValue().getValue(BedBlock.PART) == BedPart.HEAD).map(Map.Entry::getKey).sorted().toList();
	}

	private static BlockPos bedFoot(Map<BlockPos, BlockState> cells, BlockPos head) {
		return head.relative(at(cells, head).getValue(BedBlock.FACING).getOpposite());
	}

	private static boolean container(BlockState s) {
		Block b = s.getBlock();
		return b instanceof ChestBlock || b instanceof BarrelBlock || b instanceof AbstractFurnaceBlock || b instanceof HopperBlock
			|| b instanceof BrewingStandBlock || b instanceof DispenserBlock || b instanceof ShulkerBoxBlock;
	}

	/** Every job site a villager is drawn to, the bell included (not a bed, lightning rod, portal, lodestone or hive). */
	private static Map<BlockPos, BlockState> jobSites(Map<BlockPos, BlockState> cells) {
		Map<BlockPos, BlockState> out = new HashMap<>();
		cells.forEach((p, s) -> {
			if (PoiTypes.forState(s).filter(h -> !h.is(PoiTypes.HOME) && !h.is(PoiTypes.LIGHTNING_ROD) && !h.is(PoiTypes.NETHER_PORTAL)
				&& !h.is(PoiTypes.LODESTONE) && !h.is(PoiTypes.BEEHIVE) && !h.is(PoiTypes.BEE_NEST)).isPresent()) {
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

	static boolean within(BlockPos a, BlockPos b, int across, int vertical) {
		return Math.abs(a.getX() - b.getX()) <= across && Math.abs(a.getZ() - b.getZ()) <= across && Math.abs(a.getY() - b.getY()) <= vertical;
	}

	// ---- a villager's walk ------------------------------------------------------------------------------------------

	/** A villager's feet or head can be in it: a wooden door (he opens it), a ladder, or nothing over a fifth of a block tall. */
	private static boolean open(BlockState state) {
		return open(state, false);
	}

	/** {@code player}: any door, trapdoor or fence gate opens too (a player's walk, the iron door's button pressed). */
	private static boolean open(BlockState state, boolean player) {
		if (state.getBlock() instanceof DoorBlock && (player || !state.is(Blocks.IRON_DOOR))) {
			return true;
		}
		if (player && (state.getBlock() instanceof TrapDoorBlock || state.getBlock() instanceof FenceGateBlock)) {
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

	private static boolean standable(Map<BlockPos, BlockState> cells, BlockPos feet, boolean player) {
		BlockState here = at(cells, feet);
		if (!open(here, player) || !open(at(cells, feet.above()), player) || !here.getFluidState().isEmpty()) {
			return false;
		}
		BlockState below = at(cells, feet.below());
		return here.is(Blocks.LADDER) || below.is(Blocks.LADDER) || (!open(below) && !noFloor(below) && below.getFluidState().isEmpty());
	}

	/** Every spot a villager reaches on foot from the street in front: level, a step up, up to three down, ladders. */
	private static Set<BlockPos> walk(Blueprint blueprint, Map<BlockPos, BlockState> cells) {
		return walk(blueprint, cells, false);
	}

	private static Set<BlockPos> walk(Blueprint blueprint, Map<BlockPos, BlockState> cells, boolean player) {
		int sx = blueprint.size().getX();
		int sz = blueprint.size().getZ();
		int top = blueprint.size().getY();
		Set<BlockPos> reached = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		for (int x = -2; x <= sx + 1; x++) {
			for (int z = -2; z <= -1; z++) {
				BlockPos s = new BlockPos(x, 0, z);
				if (standable(cells, s, player) && reached.add(s)) {
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
				if (nx.getX() < -2 || nx.getX() > sx + 1 || nx.getZ() < -2 || nx.getZ() > sz + 1 || nx.getY() < -8 || nx.getY() > top) {
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

	/** A spot right beside {@code target} (one of its four sides, its level or the one below) was reached. */
	private static boolean beside(Set<BlockPos> reached, BlockPos target) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (reached.contains(target.relative(d)) || reached.contains(target.relative(d).below())) {
				return true;
			}
		}
		return false;
	}

	/** A reached spot within {@code across} blocks each way and one up or down of {@code target}. */
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
	 * Every brief's "Job block": each level holds exactly the job blocks its brief names, in the counts it names (Mason's
	 * Yard 1 then 2 stonecutters, Smithy 1 then 2 blast furnaces with one smithing table and one grindstone, Fletcher's
	 * Lodge 1 then 2 fletching tables, one of the trade's block in every other level, none in the Crafter's Workshop), and,
	 * where the brief says "no other job block of any job" (all but the Create builds and Healing Center II-IV), no
	 * other job block or bell, cellars included. And each of those job blocks has a spot right beside it that a villager
	 * walks to from the street in front, through wooden doors, with every fence gate shut.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEveryWorkplaceLevelHasItsJobBlocksAndAVillagerWalksUpToEach(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int checked = 0;
		for (Family family : FAMILIES) {
			for (Level level : family.levels()) {
				Blueprint blueprint = blueprint(helper, level.entry());
				Map<BlockPos, BlockState> cells = cells(blueprint);
				String n = n(level.entry());
				Set<BlockPos> reached = walk(blueprint, cells);
				for (Map.Entry<Block, Integer> job : level.jobs().entrySet()) {
					List<BlockPos> found = where(cells, job.getKey());
					if (found.size() != job.getValue()) {
						problems.add(n + ": " + found.size() + " " + name(job.getKey().defaultBlockState()) + " " + found + ", the brief says " + job.getValue());
					}
					for (BlockPos p : found) {
						checked++;
						if (!beside(reached, p)) {
							problems.add(n + ": the " + name(job.getKey().defaultBlockState()) + " at " + p.toShortString()
								+ " has no spot beside it a villager walks to from the street (doors open, gates shut)");
						}
					}
				}
				if (level.only()) {
					Map<BlockPos, BlockState> others = jobSites(cells);
					others.values().removeIf(s -> level.jobs().containsKey(s.getBlock()));
					if (!others.isEmpty()) {
						problems.add(n + ": other job blocks " + shortList(others));
					}
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.assertTrue(checked == 34, "(setup) only " + checked + " job blocks checked");
		helper.succeed();
	}

	/**
	 * Every brief's "Storage": a chest within 8 across and 4 up or down of each job block (the worker's own supplies,
	 * {@code SupplyContainers}), in every level, and every chest in the drawing within a player's reach (2 across, 1 up or
	 * down) of a spot a player walks to from the street, every door, trapdoor and gate opened (the Jeweler's vault is
	 * behind iron doors, the cellar under a hatch: the briefs' "reachable on foot" is the player's); and no barrel where the brief bars it (a Fisherman's job block: all but the
	 * Create builds, which only "prefer none").
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEveryJobBlockHasAChestInReachAndEveryChestCanBeWalkedTo(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (Family family : FAMILIES) {
			for (Level level : family.levels()) {
				Blueprint blueprint = blueprint(helper, level.entry());
				Map<BlockPos, BlockState> cells = cells(blueprint);
				String n = n(level.entry());
				List<BlockPos> chests = chests(cells);
				if (family.chests()) {
					for (Block job : level.jobs().keySet()) {
						for (BlockPos p : where(cells, job)) {
							if (chests.stream().noneMatch(c -> within(c, p, 8, 4))) {
								problems.add(n + ": no chest within 8 across / 4 up or down of the " + name(job.defaultBlockState()) + " " + p.toShortString());
							}
						}
					}
				}
				Set<BlockPos> reached = walk(blueprint, cells, true);
				List<String> cut = chests.stream().filter(c -> !near(reached, c, 2)).map(BlockPos::toShortString).toList();
				if (!cut.isEmpty()) {
					problems.add(n + ": chests nobody walks up to from the street " + cut);
				}
				if (level.only() && !where(cells, Blocks.BARREL).isEmpty()) {
					problems.add(n + ": barrels " + where(cells, Blocks.BARREL));
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * Every brief's "Kept cells" / "Upgrades": each level keeps the level below's job blocks and containers (and, in the
	 * Healing Center, every block-entity block) in the same cell as the same block. A builder never breaks a job block
	 * or a container, so a moved one would stay behind as a second job site and the player's chest would sit in the wrong
	 * room. Also each level is at least the level below's size in x, y and z. Mason's Yard, Smithy, Tinker's Workshop,
	 * Fletcher's Lodge, Tailor's Shop and Print Shop I to II; Jeweler's I to II to III; Healing Center I to IV.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEveryWorkshopUpgradeKeepsItsJobBlocksAndChestsWhereTheyStood(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int steps = 0;
		for (Family family : FAMILIES) {
			boolean healing = family.name().equals("healing_center");
			for (int i = 1; i < family.levels().size(); i++) {
				steps++;
				StarterBlueprints.Entry lowE = family.levels().get(i - 1).entry();
				StarterBlueprints.Entry upE = family.levels().get(i).entry();
				Blueprint low = blueprint(helper, lowE);
				Blueprint up = blueprint(helper, upE);
				Map<BlockPos, BlockState> lower = cells(low);
				Map<BlockPos, BlockState> upper = cells(up);
				List<String> lost = new ArrayList<>();
				lower.forEach((p, s) -> {
					boolean kept = container(s) || PoiTypes.forState(s).isPresent() || (healing && s.hasBlockEntity());
					if (kept && !at(upper, p).is(s.getBlock())) {
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
		}
		helper.assertTrue(steps == 11, "(setup) " + steps + " upgrade steps, want 11");
		helper.assertTrue(problems.isEmpty(), "an upgrade moves or drops what the level below had: " + String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * Every brief's "Plot" and "Declared size": each drawing's size equals its declared size and fits the plot the brief
	 * gives (the GameTest area that builds it, or the Steward's renewal footprint: Fletcher's Lodge I about 15 x 10, Smithy
	 * I at most 10 deep, Tinker's Workshop and Healing Center I at most 13 x 13).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEveryWorkshopFitsThePlotItsBriefGives(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (Family family : FAMILIES) {
			for (Level level : family.levels()) {
				Blueprint blueprint = blueprint(helper, level.entry());
				Vec3i size = blueprint.size();
				String n = n(level.entry());
				if (!size.equals(level.entry().size())) {
					problems.add(n + ": drawn " + size.toShortString() + ", declared " + level.entry().size().toShortString());
				}
				if (size.getX() > level.w() || size.getY() > level.h() || size.getZ() > level.d()) {
					problems.add(n + ": " + size.toShortString() + " (w h d) is over the brief's plot " + level.w() + ", " + level.h() + ", " + level.d());
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * The beds the briefs ask for: the Builder's and the Carpenter's Workshop at least one bed each (the worker's), the
	 * Physician's House exactly four, the Clinic at least four white beds; each bed's head or foot has a spot beside it a
	 * villager walks to from the street (gates shut).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheWorkshopsWithBedsHaveTheirCountAndEachBedIsWalkedTo(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		Map<StarterBlueprints.Entry, int[]> want = new LinkedHashMap<>(); // {min, max}
		want.put(StarterBlueprints.BUILDERS_WORKSHOP, new int[] {1, ANY});
		want.put(StarterBlueprints.CARPENTERS_WORKSHOP, new int[] {1, ANY});
		want.put(StarterBlueprints.PHYSICIANS_HOUSE, new int[] {4, 4});
		want.put(StarterBlueprints.CLINIC, new int[] {4, ANY});
		for (Map.Entry<StarterBlueprints.Entry, int[]> w : want.entrySet()) {
			Blueprint blueprint = blueprint(helper, w.getKey());
			Map<BlockPos, BlockState> cells = cells(blueprint);
			String n = n(w.getKey());
			List<BlockPos> heads = bedHeads(cells);
			if (heads.size() < w.getValue()[0] || heads.size() > w.getValue()[1]) {
				problems.add(n + ": " + heads.size() + " beds " + heads);
			}
			if (w.getKey() == StarterBlueprints.CLINIC) {
				long white = heads.stream().filter(h -> at(cells, h).is(Blocks.WHITE_BED)).count();
				if (white < 4) {
					problems.add(n + ": " + white + " white beds, want at least 4");
				}
			}
			Set<BlockPos> reached = walk(blueprint, cells);
			for (BlockPos h : heads) {
				if (!beside(reached, h) && !beside(reached, bedFoot(cells, h))) {
					problems.add(n + ": the bed at " + h.toShortString() + " can't be walked to from the street");
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * The other numbers the briefs pin: Mason's Yard at least 8 fence or wall blocks a level; Smithy II more coal and ore
	 * store than I (blocks of coal, raw iron/copper/gold, coal or iron ore, chests); Tinker's Workshop I a double chest;
	 * Fletcher's Lodge at least 20 logs and a target or hay block a level; Tailor's Shop I at least 8 wool in 6 colors and no
	 * ladder, II 12 more wool and at least 9 ladder rungs; Print Shop I exactly one container (a single chest), II at
	 * least 24 bookshelves and I none; Jeweler's I no iron bars, II exactly three iron doors (6 blocks), at least 10 iron
	 * bars and at least 10 chests in the vault (z 9 or more), III's basement 4 deep; Healing Center I exactly one chest or
	 * barrel, IV's basement 4 deep; Smithy cellar 4 deep in both; Glassblower's 1-64 glass panes.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheWorkshopsHoldTheCountsTheirBriefsPin(GameTestHelper helper) {
		List<String> p = new ArrayList<>();
		for (StarterBlueprints.Entry e : List.of(StarterBlueprints.MASONS_YARD, StarterBlueprints.MASONS_YARD_2)) {
			long fences = cells(blueprint(helper, e)).values().stream().filter(s -> s.is(BlockTags.FENCES) || s.is(BlockTags.WALLS)).count();
			if (fences < 8) {
				p.add(n(e) + ": " + fences + " fence or wall blocks, want 8");
			}
		}
		Map<BlockPos, BlockState> s1 = cells(blueprint(helper, StarterBlueprints.SMITHY));
		Map<BlockPos, BlockState> s2 = cells(blueprint(helper, StarterBlueprints.SMITHY_2));
		if (store(s2) <= store(s1)) {
			p.add("smithy_2: coal and ore store " + store(s2) + ", not more than Level I's " + store(s1));
		}
		for (StarterBlueprints.Entry e : List.of(StarterBlueprints.SMITHY, StarterBlueprints.SMITHY_2, StarterBlueprints.JEWELLERS_WORKSHOP_3,
			StarterBlueprints.HEALING_CENTER_4)) {
			int b = blueprint(helper, e).basement();
			if (b != 4) {
				p.add(n(e) + ": basement " + b + " deep, want 4");
			}
		}
		Map<BlockPos, BlockState> t1 = cells(blueprint(helper, StarterBlueprints.TINKERS_WORKSHOP));
		if (t1.values().stream().noneMatch(s -> s.getBlock() instanceof ChestBlock && s.getValue(ChestBlock.TYPE) != ChestType.SINGLE)) {
			p.add("tinkers_workshop: no double chest");
		}
		for (StarterBlueprints.Entry e : List.of(StarterBlueprints.FLETCHERS_LODGE, StarterBlueprints.FLETCHERS_LODGE_2)) {
			Map<BlockPos, BlockState> c = cells(blueprint(helper, e));
			long logs = c.values().stream().filter(s -> s.is(BlockTags.LOGS)).count();
			if (logs < 20 || (where(c, Blocks.TARGET).isEmpty() && where(c, Blocks.HAY_BLOCK).isEmpty())) {
				p.add(n(e) + ": " + logs + " logs, target " + where(c, Blocks.TARGET).size() + ", hay " + where(c, Blocks.HAY_BLOCK).size());
			}
		}
		Map<BlockPos, BlockState> ta1 = cells(blueprint(helper, StarterBlueprints.TAILORS_SHOP));
		Map<BlockPos, BlockState> ta2 = cells(blueprint(helper, StarterBlueprints.TAILORS_SHOP_2));
		long wool1 = ta1.values().stream().filter(s -> s.is(BlockTags.WOOL)).count();
		long colors1 = ta1.values().stream().filter(s -> s.is(BlockTags.WOOL)).map(BlockState::getBlock).distinct().count();
		long wool2 = ta2.values().stream().filter(s -> s.is(BlockTags.WOOL)).count();
		if (wool1 < 8 || colors1 < 6) {
			p.add("tailors_shop: " + wool1 + " wool in " + colors1 + " colors, want 8 in 6");
		}
		if (wool2 < wool1 + 12) {
			p.add("tailors_shop_2: " + wool2 + " wool, want at least " + (wool1 + 12));
		}
		if (!where(ta1, Blocks.LADDER).isEmpty() || where(ta2, Blocks.LADDER).size() < 9) {
			p.add("tailors_shop ladders: I " + where(ta1, Blocks.LADDER).size() + " (want 0), II " + where(ta2, Blocks.LADDER).size() + " (want 9+)");
		}
		Map<BlockPos, BlockState> p1 = cells(blueprint(helper, StarterBlueprints.PRINT_SHOP));
		Map<BlockPos, BlockState> p2 = cells(blueprint(helper, StarterBlueprints.PRINT_SHOP_2));
		List<BlockState> boxes = p1.values().stream().filter(QaRebuiltWorkshops1121bGameTests::container).toList();
		if (boxes.size() != 1 || !boxes.get(0).is(Blocks.CHEST) || boxes.get(0).getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
			p.add("print_shop: containers " + boxes.stream().map(QaRebuiltWorkshops1121bGameTests::name).toList() + ", want one single chest");
		}
		if (!where(p1, Blocks.BOOKSHELF).isEmpty() || where(p2, Blocks.BOOKSHELF).size() < 24) {
			p.add("print_shop bookshelves: I " + where(p1, Blocks.BOOKSHELF).size() + " (want 0), II " + where(p2, Blocks.BOOKSHELF).size() + " (want 24+)");
		}
		Map<BlockPos, BlockState> j1 = cells(blueprint(helper, StarterBlueprints.JEWELLERS_WORKSHOP));
		Map<BlockPos, BlockState> j2 = cells(blueprint(helper, StarterBlueprints.JEWELLERS_WORKSHOP_2));
		if (!where(j1, Blocks.IRON_BARS).isEmpty() || where(j1, Blocks.IRON_DOOR).size() < 2) {
			p.add("jewellers_workshop: iron bars " + where(j1, Blocks.IRON_BARS).size() + " (want 0), iron door blocks " + where(j1, Blocks.IRON_DOOR).size());
		}
		long vault = j2.entrySet().stream().filter(e -> e.getValue().is(Blocks.CHEST) && e.getKey().getZ() >= 9).count();
		if (where(j2, Blocks.IRON_DOOR).size() != 6 || where(j2, Blocks.IRON_BARS).size() < 10 || vault < 10) {
			p.add("jewellers_workshop_2: iron door blocks " + where(j2, Blocks.IRON_DOOR).size() + " (want 6), bars " + where(j2, Blocks.IRON_BARS).size()
				+ " (want 10+), vault chests " + vault + " (want 10+)");
		}
		long hcStore = cells(blueprint(helper, StarterBlueprints.HEALING_CENTER)).values().stream()
			.filter(s -> s.getBlock() instanceof ChestBlock || s.is(Blocks.BARREL)).count();
		if (hcStore != 1) {
			p.add("healing_center: " + hcStore + " chests or barrels, want exactly 1");
		}
		long panes = cells(blueprint(helper, StarterBlueprints.GLASSBLOWERS_WORKSHOP)).values().stream().filter(s -> s.is(Blocks.GLASS_PANE)).count();
		if (panes < 1 || panes > 64) {
			p.add("glassblowers_workshop: " + panes + " glass panes, want 1-64");
		}
		helper.assertTrue(p.isEmpty(), String.join("; ", p));
		helper.succeed();
	}

	private static long store(Map<BlockPos, BlockState> cells) {
		return cells.values().stream().filter(s -> s.is(Blocks.COAL_BLOCK) || s.is(Blocks.RAW_IRON_BLOCK) || s.is(Blocks.RAW_COPPER_BLOCK)
			|| s.is(Blocks.RAW_GOLD_BLOCK) || s.is(Blocks.COAL_ORE) || s.is(Blocks.IRON_ORE) || s.is(Blocks.CHEST) || s.is(Blocks.BARREL)).count();
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

	static List<BlockPos> built(ServerLevel level, BlockPos origin, Vec3i size, Block block) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(origin, origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
			if (level.getBlockState(p).is(block)) {
				out.add(p.immutable());
			}
		}
		return out;
	}

	/**
	 * A real Builder builds {@code entry} at {@code origin} from exactly its material list, saved and loaded (the site and
	 * the builder) once 40 blocks of the walls are up; the build ends DONE, every block right, nothing left over; then
	 * {@code after} checks the finished build.
	 */
	static void buildAcrossAReload(GameTestHelper helper, StarterBlueprints.Entry entry, BlockPos origin, Consumer<BlueprintData.Placement> after) {
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

	/**
	 * tinkers_workshop.md in the world, the small build: a Builder builds Tinker's Workshop I from bare ground from exactly
	 * its materials across a save and reload mid-STRUCTURE; it ends DONE with every block right and nothing left over; the
	 * built workshop has one smithing table, a job site, a chest within 8 / 4 of it, and a double chest (joined as built).
	 */
	//$ gametest_ticks_batch HUGE '40000' '"qaWorkshops1121bTinkerBuild"'
	@GameTest(template = HUGE, timeoutTicks = 40000, batch = "qaWorkshops1121bTinkerBuild")
	public void qaABuilderBuildsTinkersWorkshopIAcrossAReloadFromExactlyItsMaterials(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		buildAcrossAReload(helper, StarterBlueprints.TINKERS_WORKSHOP, new BlockPos(9, 2, 9), placement -> {
			Vec3i size = StarterBlueprints.TINKERS_WORKSHOP.size();
			List<BlockPos> tables = built(level, placement.origin(), size, Blocks.SMITHING_TABLE);
			helper.assertTrue(tables.size() == 1, "the built workshop has " + tables.size() + " smithing tables");
			helper.assertTrue(level.getPoiManager().getType(tables.get(0)).isPresent(), "the built smithing table is no job site");
			List<BlockPos> chests = built(level, placement.origin(), size, Blocks.CHEST);
			helper.assertTrue(chests.stream().anyMatch(c -> within(c, tables.get(0), 8, 4)), "no built chest within 8 / 4 of the smithing table");
			helper.assertTrue(chests.stream().anyMatch(c -> level.getBlockState(c).getValue(ChestBlock.TYPE) != ChestType.SINGLE),
				"no double chest in the built workshop (its halves didn't join)");
		});
	}

	/**
	 * fletchers_lodge.md in the world, the bigger build: a Builder builds Fletcher's Lodge II from bare ground from exactly
	 * its materials across a save and reload; DONE, every block right, nothing left over; two fletching tables, both job
	 * sites, each with a built chest within 8 / 4.
	 */
	//$ gametest_ticks_batch HUGE '45000' '"qaWorkshops1121bFletcherBuild"'
	@GameTest(template = HUGE, timeoutTicks = 45000, batch = "qaWorkshops1121bFletcherBuild")
	public void qaABuilderBuildsFletchersLodgeIIAcrossAReloadFromExactlyItsMaterials(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		buildAcrossAReload(helper, StarterBlueprints.FLETCHERS_LODGE_2, new BlockPos(9, 2, 9), placement -> {
			Vec3i size = StarterBlueprints.FLETCHERS_LODGE_2.size();
			List<BlockPos> tables = built(level, placement.origin(), size, Blocks.FLETCHING_TABLE);
			List<BlockPos> chests = built(level, placement.origin(), size, Blocks.CHEST);
			helper.assertTrue(tables.size() == 2, "the built lodge has " + tables.size() + " fletching tables");
			for (BlockPos t : tables) {
				helper.assertTrue(level.getPoiManager().getType(t).isPresent(), "a built fletching table is no job site");
				helper.assertTrue(chests.stream().anyMatch(c -> within(c, t, 8, 4)), "no built chest within 8 / 4 of the fletching table "
					+ t.subtract(placement.origin()).toShortString());
			}
		});
	}

	/**
	 * masons_yard.md, "Level I's stonecutter stays where it is in Level II (a Mason already working there keeps his
	 * block)", in the world: Mason's Yard I stands finished, a Mason holds its stonecutter (the POI ticket taken, his
	 * JOB_SITE on it) and a player's cobblestone sits in its chest. A Builder stocked with exactly what the upgrade still
	 * needs builds Level II over it. DONE: every block of II right; the stonecutter is still there, still taken (never
	 * broken and re-placed), the Mason still a Mason holding it; the chest still holds the player's cobblestone; and the
	 * yard now has two stonecutters.
	 */
	//$ gametest_ticks_batch HUGE '40000' '"qaWorkshops1121bMasonUpgrade"'
	@GameTest(template = HUGE, timeoutTicks = 40000, batch = "qaWorkshops1121bMasonUpgrade")
	public void qaUpgradingAMasonsYardToIIKeepsTheClaimedStonecutterAndThePlayersChest(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		rules(helper);
		BlockPos origin = helper.absolutePos(new BlockPos(9, 2, 9));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		level.getStructureManager().get(StarterBlueprints.MASONS_YARD.id()).orElseThrow()
			.placeInWorld(level, origin, origin, new StructurePlaceSettings(), net.minecraft.util.RandomSource.create(1121L), 2);
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.MASONS_YARD.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		List<BlockPos> cutters = built(level, origin, StarterBlueprints.MASONS_YARD.size(), Blocks.STONECUTTER);
		List<BlockPos> chests = built(level, origin, StarterBlueprints.MASONS_YARD.size(), Blocks.CHEST);
		helper.assertTrue(cutters.size() == 1 && !chests.isEmpty(), "(setup) Mason's Yard I placed with " + cutters.size() + " stonecutters, "
			+ chests.size() + " chests");
		BlockPos cutter = cutters.get(0);
		helper.assertTrue(level.getPoiManager().take(h -> h.is(PoiTypes.MASON), (h, p) -> p.equals(cutter), cutter, 1).isPresent(),
			"(setup) the stonecutter couldn't be claimed");
		Villager mason = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 6));
		mason.setNoAi(true);
		mason.setVillagerData(mason.getVillagerData().setProfession(VillagerProfession.MASON));
		mason.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), cutter));
		BlockPos chest = chests.get(0);
		((Container) level.getBlockEntity(chest)).setItem(0, new ItemStack(Items.COBBLESTONE, 37));
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		var report = BlueprintSupplies.check(level, new BlueprintData(StarterBlueprints.MASONS_YARD_2.id(), Optional.of(StarterBlueprints.MASONS_YARD_2.size()),
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
		BuildSite site = Builders.start(level, builder, null, StarterBlueprints.MASONS_YARD_2.id(), placement);
		helper.assertTrue(site != null, "Mason's Yard II didn't start over the finished Level I");
		UUID id = site.id();
		helper.onEachTick(() -> {
			if (level.getDayTime() % 24000 > 11000) {
				helper.setDayTime(2000);
			}
		});
		helper.succeedWhen(() -> {
			BuildSite s = BuildSiteManager.get(level).get(id);
			helper.assertTrue(s == null, s == null ? "" : "still building: " + s.stage() + " " + s.status() + ", " + s.placed() + " placed, missing " + s.missing());
			BuildPlan plan = BuildPlan.create(blueprint(helper, StarterBlueprints.MASONS_YARD_2), placement);
			List<BlockPos> wrong = plan.unfinished(level);
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) of Mason's Yard II wrong, e.g. "
				+ wrong.stream().limit(5).map(p -> p.subtract(origin).toShortString() + "=" + level.getBlockState(p)).toList());
			String c = cutter.subtract(origin).toShortString();
			helper.assertTrue(level.getBlockState(cutter).is(Blocks.STONECUTTER), "the claimed stonecutter at " + c + " is gone after the upgrade");
			helper.assertTrue(level.getPoiManager().getFreeTickets(cutter) == 0, "the claimed stonecutter at " + c
				+ " was freed by the upgrade (broken and re-placed): the Mason lost it");
			helper.assertTrue(mason.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).filter(cutter::equals).isPresent(),
				"the Mason lost his job site: " + mason.getBrain().getMemory(MemoryModuleType.JOB_SITE));
			helper.assertTrue(mason.getVillagerData().getProfession() == VillagerProfession.MASON, "the Mason lost his job");
			ItemStack kept = level.getBlockEntity(chest) instanceof Container box ? box.getItem(0) : ItemStack.EMPTY;
			helper.assertTrue(kept.is(Items.COBBLESTONE) && kept.getCount() == 37, "the player's chest at " + chest.subtract(origin).toShortString()
				+ " lost its cobblestone in the upgrade: " + level.getBlockState(chest) + " holds " + kept);
			int now = built(level, origin, StarterBlueprints.MASONS_YARD_2.size(), Blocks.STONECUTTER).size();
			helper.assertTrue(now == 2, "Mason's Yard II built over I has " + now + " stonecutters, want 2");
			mason.discard();
		});
	}
}
