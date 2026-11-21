package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * QA lane C (2026-11-21, lane-c-1121-1853), the workshops rebuilt in Milestone 50 (50.1 Builder's Workshop, 50.2
 * Carpenter's / Mason's Yard / Sawmill, 50.3 Smithy / Brass Foundry / Tinker's, 50.4 Butcher's Shop and Winery, 50.6
 * Fletcher's Lodge / Tailor's / Print Shop, 50.7 Glassblower's / Jeweler's / Crafter's / Workshop, 50.8 Physician's
 * House / Clinic / Healing Center, 50.11 Flower Shop / Supply Shop / Market Stall), written from their blind briefs
 * ({@code tools/blueprints/briefs/<name>.md}, "Must have") rather than the code.
 *
 * <p>The walk on the drawings is a villager's, as in {@link QaRebuiltMarkets1121cGameTests}: wooden doors open, a fence
 * gate stays as drawn (closed, a fence), nobody stands on a fence or wall, and it starts in the street in front of the
 * z = 0 side. A job block counts as walked to when a reached cell is right beside it (one of its four sides, its level or
 * the one below); a chest when a reached cell is within a player's reach (2 across, 1 up or down).
 */
public class QaRebuiltWorkshops1121cGameTests implements FabricGameTest {

	// ---- the briefs, level by level ---------------------------------------------------------------------------------

	/**
	 * One level of one workshop as its brief has it: the job blocks and their exact counts, whether any other job block
	 * of any job is forbidden, the job blocks that need a chest in the worker's supply reach, and the beds (min, max; -1
	 * for no maximum).
	 */
	record Level(StarterBlueprints.Entry entry, Map<Block, Integer> jobs, boolean onlyThese, List<Block> needChest, int bedsMin, int bedsMax) {
		String name() {
			return entry.id().getPath();
		}
	}

	private static Map<Block, Integer> jobs(Object... pairs) {
		Map<Block, Integer> out = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			out.put((Block) pairs[i], (Integer) pairs[i + 1]);
		}
		return out;
	}

	private static Level level(StarterBlueprints.Entry e, Map<Block, Integer> jobs, boolean only, int bedsMin, int bedsMax) {
		List<Block> chest = jobs.entrySet().stream().filter(j -> j.getValue() > 0).map(Map.Entry::getKey).toList();
		return new Level(e, jobs, only, chest, bedsMin, bedsMax);
	}

	static List<Level> levels() {
		List<Level> out = new ArrayList<>();
		// 50.1 builders_workshop.md: exactly one Blueprint Table, no other job block; a chest in reach; at least one bed
		out.add(level(StarterBlueprints.BUILDERS_WORKSHOP, jobs(ModBlocks.BLUEPRINT_TABLE, 1, ModBlocks.BUILDERS_BENCH, 0), true, 1, -1));
		// 50.2 carpenters_workshop.md: exactly one crafting table, no other; chest; at least one bed
		out.add(level(StarterBlueprints.CARPENTERS_WORKSHOP, jobs(Blocks.CRAFTING_TABLE, 1), true, 1, -1));
		// masons_yard.md: stonecutters 1 / 2, no other job block; a chest by each
		out.add(level(StarterBlueprints.MASONS_YARD, jobs(Blocks.STONECUTTER, 1), true, 0, -1));
		out.add(level(StarterBlueprints.MASONS_YARD_2, jobs(Blocks.STONECUTTER, 2), true, 0, -1));
		// sawmill.md: one fletching table (other job blocks not tested by the brief), a chest by it
		out.add(level(StarterBlueprints.SAWMILL, jobs(Blocks.FLETCHING_TABLE, 1), false, 0, -1));
		// 50.3 smithy.md: blast furnace 1 / 2, smithing table 1, grindstone 1, nothing else; a chest by each blast furnace
		out.add(new Level(StarterBlueprints.SMITHY, jobs(Blocks.BLAST_FURNACE, 1, Blocks.SMITHING_TABLE, 1, Blocks.GRINDSTONE, 1), true,
			List.of(Blocks.BLAST_FURNACE), 0, -1));
		out.add(new Level(StarterBlueprints.SMITHY_2, jobs(Blocks.BLAST_FURNACE, 2, Blocks.SMITHING_TABLE, 1, Blocks.GRINDSTONE, 1), true,
			List.of(Blocks.BLAST_FURNACE), 0, -1));
		// brass_foundry.md: one smoker, a chest by it
		out.add(level(StarterBlueprints.BRASS_FOUNDRY, jobs(Blocks.SMOKER, 1), false, 0, -1));
		// tinkers_workshop.md: one smithing table per level, no other trade's job block (no crafting table, no barrel)
		out.add(level(StarterBlueprints.TINKERS_WORKSHOP, jobs(Blocks.SMITHING_TABLE, 1), true, 0, -1));
		out.add(level(StarterBlueprints.TINKERS_WORKSHOP_2, jobs(Blocks.SMITHING_TABLE, 1), true, 0, -1));
		// 50.4 butchers_shop.md: one smoker, no other job block (cellar included); a chest by it
		out.add(level(StarterBlueprints.BUTCHERS_SHOP, jobs(Blocks.SMOKER, 1), true, 0, -1));
		// winery.md: exactly one plain cauldron in I, II and III and no other job block; a chest by it
		for (StarterBlueprints.Entry e : List.of(StarterBlueprints.WINERY, StarterBlueprints.WINERY_2, StarterBlueprints.WINERY_3)) {
			out.add(level(e, jobs(Blocks.CAULDRON, 1), true, 0, -1));
		}
		// 50.6 fletchers_lodge.md: fletching tables 1 / 2, no other job block; a chest by each
		out.add(level(StarterBlueprints.FLETCHERS_LODGE, jobs(Blocks.FLETCHING_TABLE, 1), true, 0, -1));
		out.add(level(StarterBlueprints.FLETCHERS_LODGE_2, jobs(Blocks.FLETCHING_TABLE, 2), true, 0, -1));
		// tailors_shop.md, print_shop.md: exactly one loom / cartography table, the only job block; a chest by it
		out.add(level(StarterBlueprints.TAILORS_SHOP, jobs(Blocks.LOOM, 1), true, 0, -1));
		out.add(level(StarterBlueprints.TAILORS_SHOP_2, jobs(Blocks.LOOM, 1), true, 0, -1));
		out.add(level(StarterBlueprints.PRINT_SHOP, jobs(Blocks.CARTOGRAPHY_TABLE, 1), true, 0, -1));
		out.add(level(StarterBlueprints.PRINT_SHOP_2, jobs(Blocks.CARTOGRAPHY_TABLE, 1), true, 0, -1));
		// 50.7 glassblowers_workshop.md: exactly one Glass Kiln, no furnace, no other job block; a chest by it
		out.add(level(StarterBlueprints.GLASSBLOWERS_WORKSHOP, jobs(ModBlocks.GLASS_KILN, 1, Blocks.FURNACE, 0), true, 0, -1));
		// jewellers_workshop.md: exactly one stonecutter in I, II, III, the only job block; a container by it
		for (StarterBlueprints.Entry e : List.of(StarterBlueprints.JEWELLERS_WORKSHOP, StarterBlueprints.JEWELLERS_WORKSHOP_2, StarterBlueprints.JEWELLERS_WORKSHOP_3)) {
			out.add(level(e, jobs(Blocks.STONECUTTER, 1), true, 0, -1));
		}
		// crafters_workshop.md: no Crafter's Workbench, no job block of any job
		out.add(level(StarterBlueprints.CRAFTERS_WORKSHOP, jobs(ModBlocks.CRAFTERS_WORKBENCH, 0), true, 0, -1));
		// workshop.md: one blast furnace, a chest by it
		out.add(level(StarterBlueprints.WORKSHOP, jobs(Blocks.BLAST_FURNACE, 1), false, 0, -1));
		// 50.8 physicians_house.md: exactly one brewing stand, no other job block, a chest by it, exactly four beds
		out.add(level(StarterBlueprints.PHYSICIANS_HOUSE, jobs(Blocks.BREWING_STAND, 1), true, 4, 4));
		// clinic.md: exactly one brewing stand, no other job block, a chest by it, at least 4 beds
		out.add(level(StarterBlueprints.CLINIC, jobs(Blocks.BREWING_STAND, 1), true, 4, -1));
		// healing_center.md: one brewing stand each level (in I the only workstation), a chest by it
		out.add(level(StarterBlueprints.HEALING_CENTER, jobs(Blocks.BREWING_STAND, 1), true, 0, -1));
		for (StarterBlueprints.Entry e : List.of(StarterBlueprints.HEALING_CENTER_2, StarterBlueprints.HEALING_CENTER_3, StarterBlueprints.HEALING_CENTER_4)) {
			out.add(level(e, jobs(Blocks.BREWING_STAND, 1), false, 0, -1));
		}
		// 50.11 flower_shop.md: exactly one composter in I and II, no other job block; a chest by it
		out.add(level(StarterBlueprints.FLOWER_SHOP, jobs(Blocks.COMPOSTER, 1), true, 0, -1));
		out.add(level(StarterBlueprints.FLOWER_SHOP_2, jobs(Blocks.COMPOSTER, 1), true, 0, -1));
		// supply_shop.md: one Shop Counter each level, stock in its reach (barrels allowed as stock)
		for (StarterBlueprints.Entry e : List.of(StarterBlueprints.SUPPLY_SHOP, StarterBlueprints.SUPPLY_SHOP_2, StarterBlueprints.SUPPLY_SHOP_3,
			StarterBlueprints.SUPPLY_SHOP_4)) {
			out.add(level(e, jobs(ModBlocks.SHOP_COUNTER, 1), false, 0, -1));
		}
		// market_stall.md: I no job block required and no bed; II and III exactly one Shop Counter, stores in reach; no bed
		out.add(level(StarterBlueprints.MARKET_STALL, jobs(ModBlocks.SHOP_COUNTER, 0), false, 0, 0));
		out.add(level(StarterBlueprints.MARKET_STALL_2, jobs(ModBlocks.SHOP_COUNTER, 1), false, 0, 0));
		out.add(level(StarterBlueprints.MARKET_STALL_3, jobs(ModBlocks.SHOP_COUNTER, 1), false, 0, 0));
		return out;
	}

	// ---- drawings -----------------------------------------------------------------------------------------------------

	static Blueprint blueprint(GameTestHelper helper, StarterBlueprints.Entry entry) {
		return BlueprintLibrary.get(helper.getLevel(), entry.id()).orElseThrow(() -> new AssertionError("missing " + entry.id()));
	}

	static Map<BlockPos, BlockState> cells(Blueprint blueprint) {
		return QaBasementGameTests.blocks(blueprint);
	}

	private static BlockState at(Map<BlockPos, BlockState> cells, BlockPos pos) {
		return QaBasementGameTests.at(cells, pos);
	}

	/** Every cell holding a point of interest a villager is drawn to (a job site, a bell); not a bed, rod, portal or hive. */
	static Map<BlockPos, BlockState> pois(Map<BlockPos, BlockState> cells) {
		Map<BlockPos, BlockState> out = new HashMap<>();
		cells.forEach((p, s) -> {
			if (PoiTypes.forState(s).filter(h -> !h.is(PoiTypes.HOME) && !h.is(PoiTypes.LIGHTNING_ROD) && !h.is(PoiTypes.NETHER_PORTAL)
				&& !h.is(PoiTypes.LODESTONE) && !h.is(PoiTypes.BEEHIVE) && !h.is(PoiTypes.BEE_NEST)).isPresent()) {
				out.put(p, s);
			}
		});
		return out;
	}

	static List<BlockPos> where(Map<BlockPos, BlockState> cells, Block block) {
		return cells.entrySet().stream().filter(e -> e.getValue().is(block)).map(Map.Entry::getKey).sorted().toList();
	}

	static List<BlockPos> bedHeads(Map<BlockPos, BlockState> cells) {
		return cells.entrySet().stream().filter(e -> e.getValue().getBlock() instanceof BedBlock
			&& e.getValue().getValue(BedBlock.PART) == BedPart.HEAD).map(Map.Entry::getKey).sorted().toList();
	}

	/** A store a worker's {@code SupplyContainers} reads: a chest (trapped too) or a barrel. */
	private static boolean store(BlockState s) {
		return s.getBlock() instanceof ChestBlock || s.getBlock() instanceof BarrelBlock;
	}

	/** Any block that holds items (supply_shop.md's count: every block whose block entity is a Container). */
	private static boolean holdsItems(BlockState s) {
		if (!(s.getBlock() instanceof EntityBlock eb)) {
			return false;
		}
		try {
			return eb.newBlockEntity(BlockPos.ZERO, s) instanceof Container;
		} catch (RuntimeException e) {
			return false;
		}
	}

	/** {@code SupplyContainers.RADIUS} / {@code VERTICAL} as the briefs give them: 8 across (x and z), 4 up or down. */
	private static boolean inSupplyReach(BlockPos station, BlockPos pos) {
		return Math.abs(pos.getX() - station.getX()) <= 8 && Math.abs(pos.getZ() - station.getZ()) <= 8 && Math.abs(pos.getY() - station.getY()) <= 4;
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

	private static boolean standable(Map<BlockPos, BlockState> cells, BlockPos feet) {
		BlockState here = at(cells, feet);
		if (!open(here) || !open(at(cells, feet.above()))) {
			return false;
		}
		BlockState below = at(cells, feet.below());
		return here.is(Blocks.LADDER) || below.is(Blocks.LADDER) || (!open(below) && !noFloor(below));
	}

	private static Set<BlockPos> walk(Map<BlockPos, BlockState> cells, List<BlockPos> starts, int sx, int sz, int top) {
		Set<BlockPos> reached = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		for (BlockPos s : starts) {
			if (standable(cells, s) && reached.add(s)) {
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
			if (at(cells, pos).is(Blocks.LADDER) || at(cells, pos.above()).is(Blocks.LADDER)) {
				next.add(pos.above());
			}
			if (at(cells, pos.below()).is(Blocks.LADDER)) {
				next.add(pos.below());
			}
			for (BlockPos n : next) {
				if (n.getX() < -2 || n.getX() > sx + 1 || n.getZ() < -2 || n.getZ() > sz + 1 || n.getY() < -8 || n.getY() > top) {
					continue;
				}
				if (!reached.contains(n) && standable(cells, n)) {
					reached.add(n);
					queue.add(n);
				}
			}
		}
		return reached;
	}

	/** From the street: the two rows of open ground before the z = 0 side (the walk may go round the outside after). */
	static Set<BlockPos> walkFromStreet(Blueprint blueprint, Map<BlockPos, BlockState> cells) {
		List<BlockPos> street = new ArrayList<>();
		for (int x = -2; x <= blueprint.size().getX() + 1; x++) {
			street.add(new BlockPos(x, 0, -1));
			street.add(new BlockPos(x, 0, -2));
		}
		return walk(cells, street, blueprint.size().getX(), blueprint.size().getZ(), blueprint.size().getY());
	}

	private static boolean near(Set<BlockPos> reached, BlockPos target, int across) {
		for (BlockPos p : BlockPos.betweenClosed(target.offset(-across, -1, -across), target.offset(across, 1, across))) {
			if (reached.contains(p)) {
				return true;
			}
		}
		return false;
	}

	/** Whether a reached spot is right next to {@code target} (one of its four sides, same level or the one below it). */
	private static boolean besideIt(Set<BlockPos> reached, BlockPos target) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (reached.contains(target.relative(d)) || reached.contains(target.relative(d).below())) {
				return true;
			}
		}
		return false;
	}

	// ---- 1. job blocks: the brief's count, nothing else -------------------------------------------------------------

	/**
	 * Every workshop level's job blocks are exactly the brief's ("exactly one Blueprint Table and no other job block of
	 * any job", "stonecutters 1 / 2", "blast furnace 1 / 2, smithing table 1, grindstone 1", ...); where the brief forbids
	 * every other job block (each takes a villager for another job), there is none, bells included.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEveryRebuiltWorkshopHasExactlyItsBriefsJobBlocks(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (Level l : levels()) {
			Map<BlockPos, BlockState> cells = cells(blueprint(helper, l.entry()));
			l.jobs().forEach((block, count) -> {
				List<BlockPos> found = where(cells, block);
				if (found.size() != count) {
					problems.add(l.name() + ": " + BuiltInRegistries.BLOCK.getKey(block).getPath() + " x" + found.size() + " (brief: " + count + ") " + found);
				}
			});
			if (l.onlyThese()) {
				Map<BlockPos, BlockState> others = pois(cells);
				others.values().removeIf(s -> l.jobs().containsKey(s.getBlock()));
				if (!others.isEmpty()) {
					problems.add(l.name() + ": other job blocks " + shortList(others));
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), "job blocks not as briefed: " + String.join("; ", problems));
		helper.succeed();
	}

	// ---- 2. a villager walks up to each job block -------------------------------------------------------------------

	/**
	 * Each brief: the job block is "reachable on foot from outside, through the way in"; a worker walking in from the
	 * street in front (doors open, fence gates shut) gets to a spot right beside it, on its level or the one below.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaAVillagerWalksFromTheStreetToBesideEveryWorkshopJobBlock(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int checked = 0;
		for (Level l : levels()) {
			Blueprint blueprint = blueprint(helper, l.entry());
			Map<BlockPos, BlockState> cells = cells(blueprint);
			Set<BlockPos> reached = walkFromStreet(blueprint, cells);
			for (Block block : l.needChest()) {
				for (BlockPos p : where(cells, block)) {
					checked++;
					if (!besideIt(reached, p)) {
						problems.add(l.name() + ": " + BuiltInRegistries.BLOCK.getKey(block).getPath() + " " + p.toShortString());
					}
				}
			}
		}
		helper.assertTrue(checked >= 42, "(setup) only " + checked + " job blocks checked, the briefs have 42");
		helper.assertTrue(problems.isEmpty(), "job blocks a villager can't walk up beside from the street: " + String.join("; ", problems));
		helper.succeed();
	}

	// ---- 3. a chest in the worker's supply reach, walked to --------------------------------------------------------

	/**
	 * The briefs' "Storage": at least one chest within 8 across and 4 up or down of each job block (SupplyContainers'
	 * reach; "no test checks this, but without it the worker makes nothing"), and that chest is one a villager walks to
	 * from the street (within 2 across and 1 up or down of a reached cell).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEveryWorkshopJobBlockHasAChestInReachThatAVillagerWalksTo(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int checked = 0;
		for (Level l : levels()) {
			Blueprint blueprint = blueprint(helper, l.entry());
			Map<BlockPos, BlockState> cells = cells(blueprint);
			Set<BlockPos> reached = walkFromStreet(blueprint, cells);
			List<BlockPos> stores = cells.entrySet().stream().filter(e -> store(e.getValue())).map(Map.Entry::getKey).sorted().toList();
			for (Block block : l.needChest()) {
				for (BlockPos p : where(cells, block)) {
					checked++;
					List<BlockPos> inReach = stores.stream().filter(c -> inSupplyReach(p, c)).toList();
					if (inReach.isEmpty()) {
						problems.add(l.name() + ": no chest within 8 across / 4 up or down of the " + BuiltInRegistries.BLOCK.getKey(block).getPath()
							+ " " + p.toShortString() + " (stores: " + stores + ")");
					} else if (inReach.stream().noneMatch(c -> near(reached, c, 2))) {
						problems.add(l.name() + ": the chests by the " + BuiltInRegistries.BLOCK.getKey(block).getPath() + " " + p.toShortString()
							+ " can't be walked to: " + inReach);
					}
				}
			}
		}
		helper.assertTrue(checked >= 42, "(setup) only " + checked + " job blocks checked, the briefs have 42");
		helper.assertTrue(problems.isEmpty(), "workers with no chest of their own: " + String.join("; ", problems));
		helper.succeed();
	}

	// ---- 4. beds ----------------------------------------------------------------------------------------------------

	/**
	 * The beds the briefs promise: the Builder's and the Carpenter's at least one (the worker's), the Physician's House
	 * exactly four, the Clinic at least four whole white beds, the Market Stall none at any level; and every bed drawn has
	 * a spot beside its head or foot a villager walks to from the street.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheWorkshopsHaveTheBedsTheirBriefsPromiseAndEachIsWalkedTo(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (Level l : levels()) {
			Blueprint blueprint = blueprint(helper, l.entry());
			Map<BlockPos, BlockState> cells = cells(blueprint);
			List<BlockPos> beds = bedHeads(cells);
			if (beds.size() < l.bedsMin() || (l.bedsMax() >= 0 && beds.size() > l.bedsMax())) {
				problems.add(l.name() + ": " + beds.size() + " beds (brief: " + l.bedsMin() + (l.bedsMax() < 0 ? "+" : l.bedsMax() == l.bedsMin() ? "" : "-" + l.bedsMax()) + ")");
			}
			if (beds.isEmpty()) {
				continue;
			}
			Set<BlockPos> reached = walkFromStreet(blueprint, cells);
			for (BlockPos head : beds) {
				BlockPos foot = head.relative(cells.get(head).getValue(BedBlock.FACING).getOpposite());
				if (!(at(cells, foot).getBlock() instanceof BedBlock)) {
					problems.add(l.name() + ": the bed " + head.toShortString() + " has no foot");
				}
				if (!besideIt(reached, head) && !besideIt(reached, foot)) {
					problems.add(l.name() + ": the bed " + head.toShortString() + " isn't walked to from the street");
				}
			}
		}
		Map<BlockPos, BlockState> clinic = cells(blueprint(helper, StarterBlueprints.CLINIC));
		long white = bedHeads(clinic).stream().filter(h -> clinic.get(h).is(Blocks.WHITE_BED)).count();
		if (white < 4) {
			problems.add("clinic: " + white + " white beds (brief: at least 4)");
		}
		helper.assertTrue(problems.isEmpty(), "beds not as briefed: " + String.join("; ", problems));
		helper.succeed();
	}

	// ---- 5. levels: an upgrade keeps the job blocks and chests ------------------------------------------------------

	/**
	 * The briefs' levels: each upgrade lines up over the level below and keeps its job blocks and stores in the same
	 * cells (a builder never breaks a job block or a container, so a moved one stays behind as a second job site and the
	 * chest's contents sit in the wrong room): Mason's Yard, Smithy, Tinker's, Fletcher's, Tailor's, Print Shop, Flower Shop
	 * I to II; Winery and Jeweler's I to II to III; Healing Center and Supply Shop I to IV; Market Stall I to III.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEveryWorkshopUpgradeKeepsTheJobBlocksAndChestsWhereTheyStood(GameTestHelper helper) {
		List<List<StarterBlueprints.Entry>> chains = List.of(
			List.of(StarterBlueprints.MASONS_YARD, StarterBlueprints.MASONS_YARD_2),
			List.of(StarterBlueprints.SMITHY, StarterBlueprints.SMITHY_2),
			List.of(StarterBlueprints.TINKERS_WORKSHOP, StarterBlueprints.TINKERS_WORKSHOP_2),
			List.of(StarterBlueprints.FLETCHERS_LODGE, StarterBlueprints.FLETCHERS_LODGE_2),
			List.of(StarterBlueprints.TAILORS_SHOP, StarterBlueprints.TAILORS_SHOP_2),
			List.of(StarterBlueprints.PRINT_SHOP, StarterBlueprints.PRINT_SHOP_2),
			List.of(StarterBlueprints.FLOWER_SHOP, StarterBlueprints.FLOWER_SHOP_2),
			List.of(StarterBlueprints.WINERY, StarterBlueprints.WINERY_2, StarterBlueprints.WINERY_3),
			List.of(StarterBlueprints.JEWELLERS_WORKSHOP, StarterBlueprints.JEWELLERS_WORKSHOP_2, StarterBlueprints.JEWELLERS_WORKSHOP_3),
			List.of(StarterBlueprints.HEALING_CENTER, StarterBlueprints.HEALING_CENTER_2, StarterBlueprints.HEALING_CENTER_3, StarterBlueprints.HEALING_CENTER_4),
			List.of(StarterBlueprints.SUPPLY_SHOP, StarterBlueprints.SUPPLY_SHOP_2, StarterBlueprints.SUPPLY_SHOP_3, StarterBlueprints.SUPPLY_SHOP_4),
			List.of(StarterBlueprints.MARKET_STALL, StarterBlueprints.MARKET_STALL_2, StarterBlueprints.MARKET_STALL_3));
		List<String> problems = new ArrayList<>();
		for (List<StarterBlueprints.Entry> chain : chains) {
			for (int i = 0; i + 1 < chain.size(); i++) {
				Map<BlockPos, BlockState> lower = cells(blueprint(helper, chain.get(i)));
				Map<BlockPos, BlockState> upper = cells(blueprint(helper, chain.get(i + 1)));
				Map<BlockPos, BlockState> kept = new HashMap<>(pois(lower));
				lower.forEach((p, s) -> {
					if (holdsItems(s)) {
						kept.put(p, s);
					}
				});
				List<String> lost = new ArrayList<>();
				kept.forEach((p, s) -> {
					if (!at(upper, p).is(s.getBlock())) {
						lost.add(name(s) + " " + p.toShortString() + " -> " + name(at(upper, p)));
					}
				});
				if (!lost.isEmpty()) {
					lost.sort(null);
					problems.add(chain.get(i).id().getPath() + " -> " + chain.get(i + 1).id().getPath() + ": " + lost);
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), "an upgrade moves or drops what the level below had: " + String.join("; ", problems));
		helper.succeed();
	}

	// ---- 6. the briefs' storage counts ------------------------------------------------------------------------------

	/**
	 * supply_shop.md: Level I has exactly 8 item-holding blocks besides the Shop Counter, every one within the counter's
	 * reach; later levels keep the counter. market_stall.md: Level II has at least one store and every store in the
	 * counter's reach; Level III more stores than II, all in that reach. print_shop.md: Level I has exactly one container,
	 * a single chest. healing_center.md: Level I exactly one chest or barrel.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheShopsStockIsWhereTheirBriefsPutIt(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		Map<BlockPos, BlockState> supply = cells(blueprint(helper, StarterBlueprints.SUPPLY_SHOP));
		List<BlockPos> counter = where(supply, ModBlocks.SHOP_COUNTER);
		List<BlockPos> holders = supply.entrySet().stream().filter(e -> holdsItems(e.getValue()) && !e.getValue().is(ModBlocks.SHOP_COUNTER))
			.map(Map.Entry::getKey).sorted().toList();
		if (holders.size() != 8) {
			problems.add("supply_shop: " + holders.size() + " item-holding blocks besides the counter (brief: exactly 8): "
				+ holders.stream().map(p -> name(supply.get(p)) + " " + p.toShortString()).toList());
		}
		if (counter.size() == 1) {
			List<BlockPos> far = holders.stream().filter(p -> !inSupplyReach(counter.get(0), p)).toList();
			if (!far.isEmpty()) {
				problems.add("supply_shop: stock out of the counter's reach " + far);
			}
		}
		int stallStores = -1;
		for (StarterBlueprints.Entry e : List.of(StarterBlueprints.MARKET_STALL_2, StarterBlueprints.MARKET_STALL_3)) {
			Map<BlockPos, BlockState> cells = cells(blueprint(helper, e));
			List<BlockPos> counters = where(cells, ModBlocks.SHOP_COUNTER);
			List<BlockPos> stores = cells.entrySet().stream().filter(x -> holdsItems(x.getValue()) && !x.getValue().is(ModBlocks.SHOP_COUNTER))
				.map(Map.Entry::getKey).sorted().toList();
			if (stores.isEmpty()) {
				problems.add(e.id().getPath() + ": no store");
			}
			if (counters.size() == 1) {
				List<BlockPos> far = stores.stream().filter(p -> !inSupplyReach(counters.get(0), p)).toList();
				if (!far.isEmpty()) {
					problems.add(e.id().getPath() + ": stores out of the counter's reach " + far);
				}
			}
			if (stallStores >= 0 && stores.size() <= stallStores) {
				problems.add(e.id().getPath() + ": " + stores.size() + " stores, not more than Level II's " + stallStores);
			}
			stallStores = stores.size();
		}
		Map<BlockPos, BlockState> print = cells(blueprint(helper, StarterBlueprints.PRINT_SHOP));
		List<BlockPos> printHolders = print.entrySet().stream().filter(e -> holdsItems(e.getValue())).map(Map.Entry::getKey).toList();
		if (printHolders.size() != 1 || !print.get(printHolders.get(0)).is(Blocks.CHEST)) {
			problems.add("print_shop: containers " + printHolders.stream().map(p -> name(print.get(p)) + " " + p.toShortString()).toList()
				+ " (brief: exactly one, a single chest)");
		}
		Map<BlockPos, BlockState> healing = cells(blueprint(helper, StarterBlueprints.HEALING_CENTER));
		long healingStores = healing.values().stream().filter(QaRebuiltWorkshops1121cGameTests::store).count();
		if (healingStores != 1) {
			problems.add("healing_center: " + healingStores + " chests or barrels (brief: exactly one)");
		}
		helper.assertTrue(problems.isEmpty(), "stock not as briefed: " + String.join("; ", problems));
		helper.succeed();
	}
}
