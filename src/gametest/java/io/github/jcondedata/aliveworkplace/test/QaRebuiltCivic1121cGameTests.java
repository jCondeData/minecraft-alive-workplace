package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.grave.Graveyards;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
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
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.pathfinder.Path;

/**
 * QA lane C (2026-11-21), the civic buildings rebuilt in 51.4 (Library I-IV, School, Schoolhouse I-II) and 51.6 (Chapel,
 * Graveyard I-II, Clock Tower, Post Office), written from their blind briefs ({@code tools/blueprints/briefs/<family>.md})
 * rather than the code: what a player and a villager meet in the finished building.
 *
 * <p>The library-wide reach test ({@code QaBuildReachGameTests}) walks like a player: it opens fence gates, and skips the
 * Mailbox (not an acquirable job site). The briefs ask for more: "Villagers open doors, never fence gates" (graveyard.md),
 * and "the Mailbox is reached on foot from the front" (post_office.md). So the walk here is a villager's: wooden doors
 * open, a fence gate stays as drawn (a closed gate is a fence), nobody stands on a fence, wall or grave, and it starts in
 * the street in front (the z = 0 side, the way in every brief names). The live tests check the same with the game's own
 * pathfinder on the placed drawing.
 */
public class QaRebuiltCivic1121cGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	// ---- drawings -----------------------------------------------------------------------------------------------------

	private static Blueprint blueprint(GameTestHelper helper, StarterBlueprints.Entry entry) {
		return BlueprintLibrary.get(helper.getLevel(), entry.id()).orElseThrow(() -> new AssertionError("missing " + entry.id()));
	}

	/** The drawing's cells, its basement (if any) lifted so y = 0 is the ground floor as in the level above. */
	private static Map<BlockPos, BlockState> cells(Blueprint blueprint) {
		return QaBasementGameTests.blocks(blueprint);
	}

	private static BlockState at(Map<BlockPos, BlockState> cells, BlockPos pos) {
		return QaBasementGameTests.at(cells, pos);
	}

	private static boolean isPoi(BlockState state, net.minecraft.resources.ResourceKey<PoiType> key) {
		return PoiTypes.forState(state).map(h -> h.is(key)).orElse(false);
	}

	/**
	 * Every cell holding a point of interest a villager is drawn to (a job site, a bell, the mod's workstations); not a bed,
	 * a lightning rod, a portal, a lodestone or a hive.
	 */
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

	private static List<BlockPos> bedHeads(Map<BlockPos, BlockState> cells) {
		return cells.entrySet().stream().filter(e -> e.getValue().getBlock() instanceof BedBlock
			&& e.getValue().getValue(BedBlock.PART) == BedPart.HEAD).map(Map.Entry::getKey).sorted().toList();
	}

	private static boolean container(BlockState s) {
		Block b = s.getBlock();
		return b instanceof ChestBlock || b instanceof BarrelBlock || b instanceof AbstractFurnaceBlock || b instanceof HopperBlock
			|| b instanceof BrewingStandBlock || b instanceof DispenserBlock || b instanceof ShulkerBoxBlock;
	}

	private static String shortList(Map<BlockPos, BlockState> m) {
		List<String> out = new ArrayList<>();
		m.forEach((p, s) -> out.add(BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath() + " " + p.toShortString()));
		out.sort(null);
		return out.toString();
	}

	// ---- a villager's walk ------------------------------------------------------------------------------------------

	/**
	 * A villager's feet or head can be in {@code state}: a wooden door (villagers open those), a ladder, or anything with
	 * no collision or one under a fifth of a block (a carpet). A fence gate is as drawn: closed, it is a fence.
	 */
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

	/** Nobody stands on top of these (a villager's path never goes along a fence, wall, gate or headstone). */
	private static boolean noFloor(BlockState state) {
		return state.getBlock() instanceof FenceBlock || state.getBlock() instanceof WallBlock || state.getBlock() instanceof FenceGateBlock
			|| state.is(ModBlocks.GRAVE);
	}

	private static boolean standable(Map<BlockPos, BlockState> cells, BlockPos feet) {
		BlockState here = at(cells, feet);
		if (!open(here) || !open(at(cells, feet.above()))) {
			return false;
		}
		BlockState below = at(cells, feet.below());
		return here.is(Blocks.LADDER) || below.is(Blocks.LADDER) || (!open(below) && !noFloor(below));
	}

	/** Every spot a villager reaches on foot from {@code starts}: level, a step up, up to three down, ladders. */
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

	/** The street in front of the build: the two rows of open ground before its z = 0 side. */
	private static List<BlockPos> street(Blueprint blueprint) {
		List<BlockPos> out = new ArrayList<>();
		for (int x = -2; x <= blueprint.size().getX() + 1; x++) {
			out.add(new BlockPos(x, 0, -1));
			out.add(new BlockPos(x, 0, -2));
		}
		return out;
	}

	private static Set<BlockPos> walkFromStreet(Blueprint blueprint, Map<BlockPos, BlockState> cells) {
		return walk(cells, street(blueprint), blueprint.size().getX(), blueprint.size().getZ(), blueprint.size().getY());
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

	/** Whether a reached spot is right next to {@code target} (one of its four sides, same level or the one below it). */
	private static boolean besideIt(Set<BlockPos> reached, BlockPos target) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (reached.contains(target.relative(d)) || reached.contains(target.relative(d).below())) {
				return true;
			}
		}
		return false;
	}

	// ---- the Graveyard's slots --------------------------------------------------------------------------------------

	/** The Graveyard's grave slots in its own coordinates, in fill order, as the game reads them for a finished yard. */
	static List<BlockPos> yardSlots(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(new BlockPos(0, 60, 0));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.GRAVEYARD.id(), placement, UUID.randomUUID());
		try {
			Graveyards.Resting resting = Graveyards.resting(level, origin, false);
			int perYard = resting.slots().size() / Math.max(1, resting.yards().size());
			return resting.slots().subList(0, perYard).stream().map(p -> p.subtract(origin)).toList();
		} finally {
			BuildSiteManager.get(level).forgetFinished(placement);
		}
	}

	/**
	 * graveyard.md, "Grave slots": at least 32, and in each a grave can be laid: nothing drawn in the slot or the cell above
	 * it, the ground under it the world's own (or a block with a sturdy top). Level II "must leave every slot of Level I
	 * free".
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEveryGraveSlotIsOpenInBothLevels(GameTestHelper helper) {
		List<BlockPos> slots = yardSlots(helper);
		helper.assertTrue(slots.size() >= 32, "only " + slots.size() + " grave slots");
		helper.assertTrue(new HashSet<>(slots).size() == slots.size(), "a slot is listed twice: " + slots);
		List<String> problems = new ArrayList<>();
		Map<String, Blueprint> drawings = new java.util.LinkedHashMap<>();
		drawings.put("graveyard", blueprint(helper, StarterBlueprints.GRAVEYARD));
		drawings.put("graveyard_2", blueprint(helper, StarterBlueprints.GRAVEYARD_2));
		for (Map.Entry<String, Blueprint> d : drawings.entrySet()) {
			Map<BlockPos, BlockState> cells = cells(d.getValue());
			List<String> bad = new ArrayList<>();
			for (BlockPos slot : slots) {
				BlockState in = at(cells, slot);
				BlockState over = at(cells, slot.above());
				BlockState under = at(cells, slot.below());
				boolean ok = (in.isAir() || in.canBeReplaced()) && in.getFluidState().isEmpty()
					&& (over.isAir() || over.canBeReplaced())
					&& under.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, Direction.UP) && !under.is(ModBlocks.GRAVE);
				if (!ok) {
					bad.add(slot.toShortString() + " (" + BuiltInRegistries.BLOCK.getKey(in.getBlock()).getPath() + ", above "
						+ BuiltInRegistries.BLOCK.getKey(over.getBlock()).getPath() + ")");
				}
			}
			if (!bad.isEmpty()) {
				problems.add(d.getKey() + ": " + bad);
			}
			helper.assertTrue(slots.stream().allMatch(s -> s.getX() >= 0 && s.getX() < 13 && s.getZ() >= 0 && s.getZ() < 13),
				"a slot lies outside the 13 x 13 yard: " + slots);
		}
		helper.assertTrue(problems.isEmpty(), "grave slots a grave can't be laid in: " + String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * graveyard.md, "Each slot reachable on foot" and "A way in a villager can use": as graves are laid in the slots'
	 * order, each slot (the graves before it in place) has a spot a villager can stand on within 2 blocks, reached on foot
	 * from the street in front without opening a fence gate, and from the Undertaker's job block (he lays the grave, then
	 * walks over to it). The job block and its chest are walked to from the street as well. Both levels.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaAVillagerWalksToEachGraveSlotAsTheYardFillsWithTheGatesShut(GameTestHelper helper) {
		List<BlockPos> slots = yardSlots(helper);
		List<String> problems = new ArrayList<>();
		for (StarterBlueprints.Entry entry : List.of(StarterBlueprints.GRAVEYARD, StarterBlueprints.GRAVEYARD_2)) {
			Blueprint blueprint = blueprint(helper, entry);
			Map<BlockPos, BlockState> cells = new HashMap<>(cells(blueprint));
			Set<BlockPos> fromStreet = walkFromStreet(blueprint, cells);
			List<BlockPos> sites = pois(cells).keySet().stream().filter(p -> at(cells, p).is(Blocks.BREWING_STAND)
				|| at(cells, p).is(ModBlocks.UNDERTAKERS_TABLE)).sorted().toList();
			if (sites.isEmpty()) {
				problems.add(entry.id().getPath() + ": no Undertaker's job block");
				continue;
			}
			BlockPos site = sites.get(0);
			if (!near(fromStreet, site, 2)) {
				problems.add(entry.id().getPath() + ": the job block " + site.toShortString() + " can't be walked to from the street");
			}
			for (BlockPos chest : where(cells, Blocks.CHEST)) {
				if (!near(fromStreet, chest, 2)) {
					problems.add(entry.id().getPath() + ": the chest " + chest.toShortString() + " can't be walked to from the street");
				}
			}
			List<String> cut = new ArrayList<>();
			for (BlockPos slot : slots) {
				fromStreet = walkFromStreet(blueprint, cells);
				// the Undertaker sets off from his job block: every spot by it he can stand on
				List<BlockPos> byTable = new ArrayList<>();
				for (BlockPos p : BlockPos.betweenClosed(site.offset(-2, -1, -2), site.offset(2, 1, 2))) {
					if (fromStreet.contains(p)) {
						byTable.add(p.immutable());
					}
				}
				Set<BlockPos> fromTable = walk(cells, byTable, blueprint.size().getX(), blueprint.size().getZ(), blueprint.size().getY());
				if (!near(fromStreet, slot, 2) || !near(fromTable, slot, 2)) {
					cut.add(slot.toShortString());
				}
				cells.put(slot, ModBlocks.GRAVE.defaultBlockState()); // laid: the next ones have its headstone in the way
			}
			if (!cut.isEmpty()) {
				problems.add(entry.id().getPath() + ": slots nobody can walk up to as the yard fills (gates shut): " + cut);
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * graveyard.md, "Job block", "Storage", the candle and "No grave block": exactly one Undertaker job site (his table or a
	 * brewing stand) and no other job block or bell; a chest within 8 across and 4 up or down of it; a plain candle in
	 * Level I; no grave drawn in; and no barrel. Both levels.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheGraveyardHasOneUndertakersSiteAChestByItAndACandle(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (StarterBlueprints.Entry entry : List.of(StarterBlueprints.GRAVEYARD, StarterBlueprints.GRAVEYARD_2)) {
			Map<BlockPos, BlockState> cells = cells(blueprint(helper, entry));
			String n = entry.id().getPath();
			Map<BlockPos, BlockState> pois = pois(cells);
			List<BlockPos> sites = pois.keySet().stream().filter(p -> pois.get(p).is(Blocks.BREWING_STAND) || pois.get(p).is(ModBlocks.UNDERTAKERS_TABLE)).toList();
			if (sites.size() != 1 || pois.size() != 1) {
				problems.add(n + ": job blocks " + shortList(pois) + " (want exactly one Undertaker's site and nothing else)");
			}
			if (!sites.isEmpty()) {
				BlockPos site = sites.get(0);
				boolean chest = cells.entrySet().stream().anyMatch(e -> e.getValue().is(Blocks.CHEST)
					&& Math.abs(e.getKey().getX() - site.getX()) <= 8 && Math.abs(e.getKey().getZ() - site.getZ()) <= 8
					&& Math.abs(e.getKey().getY() - site.getY()) <= 4);
				if (!chest) {
					problems.add(n + ": no chest within 8 across and 4 up or down of the job block " + site.toShortString());
				}
			}
			if (!where(cells, Blocks.BARREL).isEmpty()) {
				problems.add(n + ": a barrel (a Fisherman's job block) at " + where(cells, Blocks.BARREL));
			}
			if (!where(cells, ModBlocks.GRAVE).isEmpty()) {
				problems.add(n + ": a grave drawn in at " + where(cells, ModBlocks.GRAVE));
			}
			if (entry == StarterBlueprints.GRAVEYARD && where(cells, Blocks.CANDLE).isEmpty()) {
				problems.add(n + ": no plain candle (the chandler scene needs one)");
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	// ---- the Chapel ---------------------------------------------------------------------------------------------------

	/** A guest may appear here (chapel.md): a full sturdy top under it, two cells with no collision and no fluid above. */
	private static boolean guestSpot(Map<BlockPos, BlockState> cells, BlockPos feet) {
		BlockState under = at(cells, feet.below());
		BlockState here = at(cells, feet);
		BlockState head = at(cells, feet.above());
		return under.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, Direction.UP)
			&& here.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty() && here.getFluidState().isEmpty()
			&& head.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty() && head.getFluidState().isEmpty();
	}

	private static BlockPos chapelMiddle() {
		var size = StarterBlueprints.CHAPEL.size();
		return new BlockPos(size.getX() / 2, 1, size.getZ() / 2);
	}

	/**
	 * chapel.md, "The middle is where people stand": the wedding venue and the Seer's place is the middle of the declared
	 * size at y = 1. At it or right beside it there is a plain floor cell a guest can appear on, under the roof (inside the
	 * building), and a villager walks to within 2 blocks of the middle from the street in front through the way in. No job
	 * block, bell-free of workstations, and no bed (chapel.md: "Job blocks: none", "Beds: none").
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheChapelsMiddleIsAFloorUnderTheRoofThatAVillagerWalksTo(GameTestHelper helper) {
		Blueprint blueprint = blueprint(helper, StarterBlueprints.CHAPEL);
		helper.assertTrue(blueprint.size().equals(StarterBlueprints.CHAPEL.size()), "(setup) the drawing is " + blueprint.size()
			+ ", declared " + StarterBlueprints.CHAPEL.size());
		Map<BlockPos, BlockState> cells = cells(blueprint);
		BlockPos middle = chapelMiddle();
		List<BlockPos> spots = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(middle.offset(-1, 0, -1), middle.offset(1, 0, 1))) {
			if (guestSpot(cells, p)) {
				spots.add(p.immutable());
			}
		}
		helper.assertTrue(!spots.isEmpty(), "no floor a guest can stand on at or beside the venue middle " + middle.toShortString()
			+ ": under it " + at(cells, middle.below()) + ", in it " + at(cells, middle) + ", above " + at(cells, middle.above()));
		boolean roofed = spots.stream().anyMatch(p -> {
			for (int y = p.getY() + 2; y < blueprint.size().getY(); y++) {
				if (!at(cells, p.atY(y)).isAir()) {
					return true;
				}
			}
			return false;
		});
		helper.assertTrue(roofed, "the venue middle " + middle.toShortString() + " is open to the sky (not inside the building)");
		Set<BlockPos> reached = walkFromStreet(blueprint, cells);
		helper.assertTrue(near(reached, middle, 2), "a villager can't walk from the street to within 2 blocks of the venue middle "
			+ middle.toShortString());
		Map<BlockPos, BlockState> pois = pois(cells);
		pois.values().removeIf(s -> s.is(Blocks.BELL));
		helper.assertTrue(pois.isEmpty(), "job blocks in the Chapel: " + shortList(pois));
		helper.assertTrue(bedHeads(cells).isEmpty(), "beds in the Chapel: " + bedHeads(cells));
		helper.succeed();
	}

	// ---- the Library ------------------------------------------------------------------------------------------------

	private static final List<StarterBlueprints.Entry> LIBRARIES = List.of(StarterBlueprints.LIBRARY, StarterBlueprints.LIBRARY_2,
		StarterBlueprints.LIBRARY_3, StarterBlueprints.LIBRARY_4);

	/**
	 * library.md, "Job blocks: lecterns": Level I exactly one lectern, II two, III three, IV keeps III's three; each is a
	 * plain lectern a villager walks up to from the street (gates shut); no other job block; Level I holds no container at
	 * all; IV has its basement 4 deep.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEachLibraryLevelHasItsLecternsAndAVillagerWalksToEach(GameTestHelper helper) {
		int[] want = {1, 2, 3, 3};
		List<String> problems = new ArrayList<>();
		for (int i = 0; i < LIBRARIES.size(); i++) {
			StarterBlueprints.Entry entry = LIBRARIES.get(i);
			Blueprint blueprint = blueprint(helper, entry);
			Map<BlockPos, BlockState> cells = cells(blueprint);
			String n = entry.id().getPath();
			List<BlockPos> lecterns = where(cells, Blocks.LECTERN);
			if (lecterns.size() != want[i]) {
				problems.add(n + ": " + lecterns.size() + " lecterns " + lecterns + ", want " + want[i]);
			}
			Map<BlockPos, BlockState> pois = pois(cells);
			pois.values().removeIf(s -> s.is(Blocks.LECTERN));
			if (!pois.isEmpty()) {
				problems.add(n + ": other job blocks " + shortList(pois));
			}
			Set<BlockPos> reached = walkFromStreet(blueprint, cells);
			for (BlockPos l : lecterns) {
				if (!besideIt(reached, l)) {
					problems.add(n + ": the lectern " + l.toShortString() + " has no spot beside it a villager walks to from the street");
				}
			}
			if (i == 0) {
				List<String> boxes = new ArrayList<>();
				cells.forEach((p, s) -> {
					if (container(s)) {
						boxes.add(BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath() + " " + p.toShortString());
					}
				});
				if (!boxes.isEmpty()) {
					problems.add(n + ": Level I has containers " + boxes);
				}
			}
			if (i == 3 && blueprint.basement() != 4) {
				problems.add(n + ": the basement is " + blueprint.basement() + " deep, want 4");
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * library.md, Level III: "an Enchanting Table powered by 15 bookshelves within reach of it", none of the shelves blocked
	 * (nothing but air in the cells between); and a chest within 8 across and 4 up or down of a lectern (the Scholars'
	 * store). The same in Level IV, which keeps III above its basement.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaLibraryIIIAndIVEnchantAtFullPowerWithAStoreByTheLecterns(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (StarterBlueprints.Entry entry : List.of(StarterBlueprints.LIBRARY_3, StarterBlueprints.LIBRARY_4)) {
			Map<BlockPos, BlockState> cells = cells(blueprint(helper, entry));
			String n = entry.id().getPath();
			List<BlockPos> tables = where(cells, Blocks.ENCHANTING_TABLE);
			if (tables.size() != 1) {
				problems.add(n + ": " + tables.size() + " enchanting tables");
				continue;
			}
			BlockPos table = tables.get(0);
			int shelves = 0;
			for (BlockPos offset : EnchantingTableBlock.BOOKSHELF_OFFSETS) {
				if (at(cells, table.offset(offset)).is(BlockTags.ENCHANTMENT_POWER_PROVIDER)
					&& at(cells, table.offset(offset.getX() / 2, offset.getY(), offset.getZ() / 2)).is(BlockTags.ENCHANTMENT_POWER_TRANSMITTER)) {
					shelves++;
				}
			}
			if (shelves < 15) {
				problems.add(n + ": the enchanting table " + table.toShortString() + " has " + shelves + " working bookshelves, want 15");
			}
			List<BlockPos> lecterns = where(cells, Blocks.LECTERN);
			boolean store = cells.entrySet().stream().anyMatch(e -> (e.getValue().is(Blocks.CHEST) || e.getValue().is(Blocks.BARREL))
				&& lecterns.stream().anyMatch(l -> Math.abs(l.getX() - e.getKey().getX()) <= 8 && Math.abs(l.getZ() - e.getKey().getZ()) <= 8
					&& Math.abs(l.getY() - e.getKey().getY()) <= 4));
			if (!store) {
				problems.add(n + ": no chest within 8 across and 4 up or down of any lectern");
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * library.md, schoolhouse.md, graveyard.md, "Upgrades": each level keeps the level below's job blocks and containers in
	 * the same cell as the same block (a builder never breaks a job block or a container, so a moved one would stay behind
	 * as a second job site and the chest's contents would sit in the wrong room): Library I to II to III to IV (IV's
	 * basement under it), Schoolhouse I to II, Graveyard I to II.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEveryUpgradeKeepsTheJobBlocksAndChestsWhereTheyStood(GameTestHelper helper) {
		List<StarterBlueprints.Entry[]> steps = List.of(
			new StarterBlueprints.Entry[] {StarterBlueprints.LIBRARY, StarterBlueprints.LIBRARY_2},
			new StarterBlueprints.Entry[] {StarterBlueprints.LIBRARY_2, StarterBlueprints.LIBRARY_3},
			new StarterBlueprints.Entry[] {StarterBlueprints.LIBRARY_3, StarterBlueprints.LIBRARY_4},
			new StarterBlueprints.Entry[] {StarterBlueprints.SCHOOLHOUSE, StarterBlueprints.SCHOOLHOUSE_2},
			new StarterBlueprints.Entry[] {StarterBlueprints.GRAVEYARD, StarterBlueprints.GRAVEYARD_2});
		List<String> problems = new ArrayList<>();
		for (StarterBlueprints.Entry[] step : steps) {
			Map<BlockPos, BlockState> lower = cells(blueprint(helper, step[0]));
			Map<BlockPos, BlockState> upper = cells(blueprint(helper, step[1]));
			List<String> lost = new ArrayList<>();
			lower.forEach((p, s) -> {
				if ((container(s) || PoiTypes.forState(s).isPresent()) && !at(upper, p).is(s.getBlock())) {
					lost.add(BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath() + " " + p.toShortString() + " -> "
						+ BuiltInRegistries.BLOCK.getKey(at(upper, p).getBlock()).getPath());
				}
			});
			if (!lost.isEmpty()) {
				lost.sort(null);
				problems.add(step[0].id().getPath() + " -> " + step[1].id().getPath() + ": " + lost);
			}
		}
		helper.assertTrue(problems.isEmpty(), "an upgrade moves or drops what the level below had: " + String.join("; ", problems));
		helper.succeed();
	}

	// ---- School, Schoolhouse, Post Office, Clock Tower --------------------------------------------------------------

	/**
	 * school.md: exactly one Training Post and no other job block (no Tutor's Desk, no barrel); at least one bed, the
	 * Tutor's; the post and the bed's head walked up to from the street in front.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheSchoolGivesTheTutorOnePostAndABedHeWalksTo(GameTestHelper helper) {
		Blueprint blueprint = blueprint(helper, StarterBlueprints.SCHOOL);
		Map<BlockPos, BlockState> cells = cells(blueprint);
		List<BlockPos> posts = where(cells, ModBlocks.TRAINING_POST);
		helper.assertTrue(posts.size() == 1, "Training Posts: " + posts);
		Map<BlockPos, BlockState> pois = pois(cells);
		pois.values().removeIf(s -> s.is(ModBlocks.TRAINING_POST));
		helper.assertTrue(pois.isEmpty(), "other job blocks in the School: " + shortList(pois));
		helper.assertTrue(where(cells, Blocks.BARREL).isEmpty(), "a barrel in the School: " + where(cells, Blocks.BARREL));
		List<BlockPos> beds = bedHeads(cells);
		helper.assertTrue(!beds.isEmpty(), "no bed for the Tutor");
		Set<BlockPos> reached = walkFromStreet(blueprint, cells);
		helper.assertTrue(near(reached, posts.get(0), 2), "the Training Post " + posts.get(0).toShortString() + " can't be walked to from the street");
		helper.assertTrue(beds.stream().anyMatch(b -> near(reached, b, 2)), "no bed's head can be walked to from the street: " + beds);
		helper.succeed();
	}

	/**
	 * schoolhouse.md: exactly one lectern in each level, in the same cell; no other job block (a bell is free); Level I
	 * holds no chest or barrel; the lectern has a spot beside it a villager walks to from the street (a book there makes
	 * the villager beside it a Teacher).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheSchoolhouseHasOneLecternInBothLevelsWithAVillagerBesideIt(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		List<BlockPos> first = null;
		for (StarterBlueprints.Entry entry : List.of(StarterBlueprints.SCHOOLHOUSE, StarterBlueprints.SCHOOLHOUSE_2)) {
			Blueprint blueprint = blueprint(helper, entry);
			Map<BlockPos, BlockState> cells = cells(blueprint);
			String n = entry.id().getPath();
			List<BlockPos> lecterns = where(cells, Blocks.LECTERN);
			if (lecterns.size() != 1) {
				problems.add(n + ": lecterns " + lecterns);
				continue;
			}
			if (first == null) {
				first = lecterns;
			} else if (!first.equals(lecterns)) {
				problems.add(n + ": the lectern moved from " + first + " to " + lecterns);
			}
			Map<BlockPos, BlockState> pois = pois(cells);
			pois.values().removeIf(s -> s.is(Blocks.LECTERN) || s.is(Blocks.BELL));
			if (!pois.isEmpty()) {
				problems.add(n + ": other job blocks " + shortList(pois));
			}
			if (entry == StarterBlueprints.SCHOOLHOUSE && (!where(cells, Blocks.CHEST).isEmpty() || !where(cells, Blocks.BARREL).isEmpty())) {
				problems.add(n + ": Level I holds chests " + where(cells, Blocks.CHEST) + " / barrels " + where(cells, Blocks.BARREL));
			}
			if (!besideIt(walkFromStreet(blueprint, cells), lecterns.get(0))) {
				problems.add(n + ": no spot beside the lectern " + lecterns.get(0).toShortString() + " a villager walks to from the street");
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * post_office.md: exactly one Mailbox, no Postal Desk, no barrel and no other job block; at least one bed; "the Mailbox
	 * is reached on foot from the front": a spot right next to it a villager walks to from the street (a Postman claims
	 * it from there; a player stands a villager beside it to make a Postman). The library reach test skips the Mailbox.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaThePostOfficesMailboxHasAVillagerSpotBesideItFromTheStreet(GameTestHelper helper) {
		Blueprint blueprint = blueprint(helper, StarterBlueprints.POST_OFFICE);
		Map<BlockPos, BlockState> cells = cells(blueprint);
		List<BlockPos> boxes = where(cells, ModBlocks.MAILBOX);
		helper.assertTrue(boxes.size() == 1, "Mailboxes: " + boxes);
		helper.assertTrue(where(cells, ModBlocks.POSTAL_DESK).isEmpty(), "a Postal Desk: " + where(cells, ModBlocks.POSTAL_DESK));
		helper.assertTrue(where(cells, Blocks.BARREL).isEmpty(), "a barrel: " + where(cells, Blocks.BARREL));
		Map<BlockPos, BlockState> pois = pois(cells);
		pois.values().removeIf(s -> s.is(ModBlocks.MAILBOX));
		helper.assertTrue(pois.isEmpty(), "other job blocks in the Post Office: " + shortList(pois));
		List<BlockPos> beds = bedHeads(cells);
		helper.assertTrue(!beds.isEmpty(), "no bed for the Postman");
		Set<BlockPos> reached = walkFromStreet(blueprint, cells);
		helper.assertTrue(besideIt(reached, boxes.get(0)), "no spot right next to the Mailbox " + boxes.get(0).toShortString()
			+ " that a villager walks to from the street");
		helper.assertTrue(beds.stream().anyMatch(b -> near(reached, b, 2)), "no bed's head can be walked to from the street: " + beds);
		helper.succeed();
	}

	/**
	 * clock_tower.md: one cartography table (the Cartographer's job block), no barrel (the old drawing's Fisherman site) and
	 * no other job block; the table walked up to from the street in front.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheClockTowerHasOneCartographyTableAndNoOtherJob(GameTestHelper helper) {
		Blueprint blueprint = blueprint(helper, StarterBlueprints.CLOCK_TOWER);
		Map<BlockPos, BlockState> cells = cells(blueprint);
		List<BlockPos> tables = where(cells, Blocks.CARTOGRAPHY_TABLE);
		helper.assertTrue(tables.size() == 1, "cartography tables: " + tables);
		helper.assertTrue(where(cells, Blocks.BARREL).isEmpty(), "a barrel (a Fisherman's job site): " + where(cells, Blocks.BARREL));
		Map<BlockPos, BlockState> pois = pois(cells);
		pois.values().removeIf(s -> s.is(Blocks.CARTOGRAPHY_TABLE));
		helper.assertTrue(pois.isEmpty(), "other job blocks in the Clock Tower: " + shortList(pois));
		helper.assertTrue(near(walkFromStreet(blueprint, cells), tables.get(0), 2), "the cartography table " + tables.get(0).toShortString()
			+ " can't be walked to from the street");
		helper.succeed();
	}

	// ---- live: the placed drawing -----------------------------------------------------------------------------------

	static BlockPos place(GameTestHelper helper, StarterBlueprints.Entry entry, BlockPos at) {
		ServerLevel level = helper.getLevel();
		StructureTemplate template = level.getStructureManager().get(entry.id()).orElseThrow();
		BlockPos origin = helper.absolutePos(at);
		template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), net.minecraft.util.RandomSource.create(173L), 2);
		return origin;
	}

	private static final BlockPos YARD = new BlockPos(8, 2, 8);

	/**
	 * graveyard.md in the world: the drawing placed and recorded finished, graves sent to it land in its slots one after
	 * another in the slots' order (each slot free in the placed yard), 32 of them, and the 33rd finds none; as each is laid
	 * the game's own pathfinder finds a villager a way from the street in front to it (the Undertaker walks over to the
	 * grave he laid), the graves before it in place. Both levels (one test each).
	 */
	private void yardFills(GameTestHelper helper, StarterBlueprints.Entry entry) {
		Leftovers.clear(helper);
		Leftovers.graves(helper);
		// a hall or finished Graveyard an earlier batch left in reach would take the graves (seen in the full suite)
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		BlockPos origin = place(helper, entry, YARD);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(entry.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		List<BlockPos> slots = yardSlots(helper);
		Villager walker = helper.spawn(EntityType.VILLAGER, YARD.offset(6, 0, -3));
		walker.setNoAi(true);
		helper.runAfterDelay(10, () -> {
			walker.setOnGround(true); // (a villager without AI never lands by itself: the pathfinder wants it on the ground)
			List<String> late = new ArrayList<>();
			for (int i = 0; i < slots.size(); i++) {
				Graveyards.Resting resting = Graveyards.resting(level, origin, false);
				BlockPos next = Graveyards.nextSlot(level, resting);
				BlockPos want = origin.offset(slots.get(i));
				helper.assertTrue(want.equals(next), "grave " + (i + 1) + " goes to " + (next == null ? "nowhere" : next.subtract(origin).toShortString())
					+ ", not the slot " + slots.get(i).toShortString() + " (in it " + level.getBlockState(want) + ", under it "
					+ level.getBlockState(want.below()) + ")");
				Path to = walker.getNavigation().createPath(next, 2);
				if (to == null || !to.canReach()) {
					late.add(slots.get(i).toShortString());
				}
				level.setBlockAndUpdate(next, ModBlocks.GRAVE.defaultBlockState());
			}
			helper.assertTrue(Graveyards.nextSlot(level, Graveyards.resting(level, origin, false)) == null, "a 33rd grave still finds a slot");
			helper.assertTrue(late.isEmpty(), "graves laid in order: no path from the street to within reach of the slot as each is laid: " + late);
			walker.discard();
			helper.succeed();
		});
	}

	//$ gametest_ticks_batch HUGE '200' '"qaCivic1121cYard1"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaCivic1121cYard1")
	public void qaGravesFillThePlacedGraveyardInOrderAndEachIsReachable(GameTestHelper helper) {
		yardFills(helper, StarterBlueprints.GRAVEYARD);
	}

	//$ gametest_ticks_batch HUGE '200' '"qaCivic1121cYard2"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaCivic1121cYard2")
	public void qaGravesFillThePlacedGraveyardIIInOrderAndEachIsReachable(GameTestHelper helper) {
		yardFills(helper, StarterBlueprints.GRAVEYARD_2);
	}

	/**
	 * chapel.md and post_office.md in the world, with the game's pathfinder: from the street in front a villager finds a
	 * path to within 2 blocks of the Chapel's venue middle (the Seer walks back there by day), and to the cell right next
	 * to the Post Office's Mailbox (the Postman's hand-ins).
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaCivic1121cChapelPath"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaCivic1121cChapelPath")
	public void qaAVillagerFindsAPathFromTheStreetToTheChapelsMiddle(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos origin = place(helper, StarterBlueprints.CHAPEL, new BlockPos(8, 2, 8));
		Villager walker = helper.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 5));
		walker.setNoAi(true);
		helper.runAfterDelay(10, () -> {
			walker.setOnGround(true);
			BlockPos middle = origin.offset(chapelMiddle());
			Path path = walker.getNavigation().createPath(middle, 2);
			helper.assertTrue(path != null && path.canReach(), "no path from the street to the Chapel's middle " + chapelMiddle().toShortString()
				+ (path == null ? "" : ", it ends at " + helper.relativePos(path.getTarget()).toShortString() + " / " + path.getEndNode()));
			walker.discard();
			helper.succeed();
		});
	}

	//$ gametest_ticks_batch HUGE '100' '"qaCivic1121cMailPath"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaCivic1121cMailPath")
	public void qaAVillagerFindsAPathFromTheStreetToThePostOfficesMailbox(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos origin = place(helper, StarterBlueprints.POST_OFFICE, new BlockPos(6, 2, 8));
		List<BlockPos> boxes = BlockPos.betweenClosedStream(origin, origin.offset(16, 14, 14)).filter(p -> helper.getLevel().getBlockState(p).is(ModBlocks.MAILBOX))
			.map(BlockPos::immutable).toList();
		helper.assertTrue(boxes.size() == 1, "(setup) Mailboxes placed: " + boxes.size());
		Villager walker = helper.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 5));
		walker.setNoAi(true);
		helper.runAfterDelay(10, () -> {
			walker.setOnGround(true);
			Path path = walker.getNavigation().createPath(boxes.get(0), 2); // (the target moves up off the solid Mailbox: 2 is the cell beside it)
			helper.assertTrue(path != null && path.canReach(), "no path from the street to beside the Mailbox " + boxes.get(0).subtract(origin).toShortString()
				+ (path == null ? " (none)" : ", it ends at " + path.getEndNode().asBlockPos().subtract(origin).toShortString() + " after " + path.getNodeCount() + " steps"));
			walker.discard();
			helper.succeed();
		});
	}

	/**
	 * library.md in the world: Library II placed, two jobless villagers in the street in front; each walks in and takes a
	 * lectern as its job site (a Scholar, Librarian or Teacher), one lectern each, both lecterns taken.
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qaCivic1121cLibraryJobs"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaCivic1121cLibraryJobs")
	public void qaTwoJoblessVillagersTakeLibraryIIsTwoLecterns(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		BlockPos origin = place(helper, StarterBlueprints.LIBRARY_2, new BlockPos(6, 2, 10));
		List<BlockPos> lecterns = BlockPos.betweenClosedStream(origin, origin.offset(17, 14, 11)).filter(p -> level.getBlockState(p).is(Blocks.LECTERN))
			.map(BlockPos::immutable).toList();
		helper.assertTrue(lecterns.size() == 2, "(setup) lecterns placed: " + lecterns.size());
		Villager a = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 7));
		Villager b = helper.spawn(EntityType.VILLAGER, new BlockPos(16, 2, 7));
		for (Villager v : List.of(a, b)) {
			v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.NONE));
		}
		helper.onEachTick(() -> {
			if (level.getDayTime() % 24000 > 9000) {
				helper.setDayTime(2000);
			}
		});
		helper.succeedWhen(() -> {
			List<BlockPos> sites = new ArrayList<>();
			for (Villager v : List.of(a, b)) {
				helper.assertTrue(v.isAlive(), "a villager died");
				BlockPos site = v.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(g -> g.pos()).orElse(null);
				helper.assertTrue(site != null && lecterns.contains(site), "a villager has no lectern yet: job " + v.getVillagerData().getProfession()
					+ ", site " + site + ", at " + helper.relativePos(v.blockPosition()).toShortString());
				helper.assertTrue(v.getVillagerData().getProfession() != VillagerProfession.NONE, "it holds a lectern but has no job");
				sites.add(site);
			}
			helper.assertTrue(!sites.get(0).equals(sites.get(1)), "both took the same lectern");
			a.discard();
			b.discard();
		});
	}
}
