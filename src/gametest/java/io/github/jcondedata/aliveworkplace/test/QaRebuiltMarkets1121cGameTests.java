package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.MarketDays;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.BedLadders;
import io.github.jcondedata.aliveworkplace.work.Walker;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.pathfinder.Path;

/**
 * QA lane C (2026-11-21), the economy buildings rebuilt in 51.5 (Leader's Hall, Trade Hall) and 51.7 (Market Square,
 * Market Cross, Market Corner), written from their blind briefs ({@code tools/blueprints/briefs/<family>.md}) rather
 * than the code: what a player, a villager and a market-day trader meet in the finished building.
 *
 * <p>The walk on the drawings is a villager's (as in {@link QaRebuiltCivic1121cGameTests}): wooden doors open, a fence
 * gate stays as drawn (closed, a fence), nobody stands on a fence or wall, and it starts in the street in front of the
 * z = 0 side. The live tests place the drawing and ask the game itself: {@link MarketDays} for the traders' cells, the
 * fair's bunting, the festival's gathering place without a bell, and the villager pathfinder for job blocks and beds.
 */
public class QaRebuiltMarkets1121cGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

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

	/** Every cell holding a point of interest a villager is drawn to (a job site, a bell); not a bed, rod, portal or hive. */
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

	private static String shortList(Map<BlockPos, BlockState> m) {
		List<String> out = new ArrayList<>();
		m.forEach((p, s) -> out.add(BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath() + " " + p.toShortString()));
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

	/** The street in front of the build: the two rows of open ground before its z = 0 side. */
	private static List<BlockPos> street(Blueprint blueprint) {
		List<BlockPos> out = new ArrayList<>();
		for (int x = -2; x <= blueprint.size().getX() + 1; x++) {
			out.add(new BlockPos(x, 0, -1));
			out.add(new BlockPos(x, 0, -2));
		}
		return out;
	}

	/** Only from the street in front: the outside ring round the other three sides is not walked (they may face a wall). */
	private static Set<BlockPos> walkFromFront(Blueprint blueprint, Map<BlockPos, BlockState> cells) {
		Map<BlockPos, BlockState> fenced = new HashMap<>(cells);
		int sx = blueprint.size().getX(), sz = blueprint.size().getZ();
		for (int y = 0; y <= 2; y++) {
			for (int z = 0; z <= sz + 1; z++) {
				fenced.putIfAbsent(new BlockPos(-1, y, z), Blocks.STONE.defaultBlockState());
				fenced.putIfAbsent(new BlockPos(-2, y, z), Blocks.STONE.defaultBlockState());
				fenced.putIfAbsent(new BlockPos(sx, y, z), Blocks.STONE.defaultBlockState());
				fenced.putIfAbsent(new BlockPos(sx + 1, y, z), Blocks.STONE.defaultBlockState());
			}
			for (int x = -2; x <= sx + 1; x++) {
				fenced.putIfAbsent(new BlockPos(x, y, sz), Blocks.STONE.defaultBlockState());
				fenced.putIfAbsent(new BlockPos(x, y, sz + 1), Blocks.STONE.defaultBlockState());
			}
		}
		return walk(fenced, street(blueprint), sx, sz, blueprint.size().getY());
	}

	private static Set<BlockPos> walkFromStreet(Blueprint blueprint, Map<BlockPos, BlockState> cells) {
		return walk(cells, street(blueprint), blueprint.size().getX(), blueprint.size().getZ(), blueprint.size().getY());
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

	// ---- the Market Square: the traders' 9 x 9 ----------------------------------------------------------------------

	/** The square's middle in its own coordinates: the declared footprint's centre, one above the bottom layer. */
	private static BlockPos squareMiddle() {
		var size = StarterBlueprints.MARKET_SQUARE.size();
		return new BlockPos((size.getX() - 1) / 2, 1, (size.getZ() - 1) / 2);
	}

	/**
	 * market_square.md, "Must have": the traders' 9 x 9 round the middle (4 out, y -1 to 4) holds "plenty" of standing
	 * cells, "several dozen" (the old one had about 70), reachable from the z = 0 front; and banquet guests walk to cells
	 * of the 9 x 9 at y = 1, so "most of it should be ground at y = 1 that a villager can walk onto from the square's
	 * edge". The walk here comes only from the front street (the other sides may face neighbours in a Market zone).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheMarketSquaresNineByNineHasDozensOfCellsWalkedToFromTheFront(GameTestHelper helper) {
		Blueprint blueprint = blueprint(helper, StarterBlueprints.MARKET_SQUARE);
		helper.assertTrue(blueprint.size().equals(StarterBlueprints.MARKET_SQUARE.size()), "(setup) drawing " + blueprint.size()
			+ " vs declared " + StarterBlueprints.MARKET_SQUARE.size());
		Map<BlockPos, BlockState> cells = cells(blueprint);
		BlockPos middle = squareMiddle();
		Set<BlockPos> reached = walkFromFront(blueprint, cells);
		int standing = 0, standingReached = 0, groundY1 = 0;
		List<String> stranded = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(middle.offset(-4, -2, -4), middle.offset(4, 3, 4))) {
			if (standable(cells, p)) {
				standing++;
				if (reached.contains(p)) {
					standingReached++;
				} else {
					stranded.add(p.toShortString());
				}
			}
			if (p.getY() == 1 && reached.contains(p)) {
				groundY1++;
			}
		}
		helper.assertTrue(standingReached >= 36, "the traders' 9 x 9 has " + standing + " standing cells, " + standingReached
			+ " reached from the front (wanted several dozen, 36 or more); cut off: " + stranded);
		helper.assertTrue(groundY1 > 81 / 2, "only " + groundY1 + " of the 81 cells at y = 1 round the middle are ground a villager walks onto from the front");
		// the lane starts one block in front of the middle of the z = 0 edge: ground a villager steps onto, and in from there
		BlockPos doorstep = new BlockPos(middle.getX(), 0, -1);
		Set<BlockPos> fromDoorstep = walk(cells, List.of(doorstep), blueprint.size().getX(), blueprint.size().getZ(), blueprint.size().getY());
		helper.assertTrue(fromDoorstep.contains(doorstep), "the lane's doorstep " + doorstep.toShortString() + " isn't ground a villager stands on");
		helper.assertTrue(near(fromDoorstep, middle, 2), "from the doorstep a villager can't walk to within 2 of the middle " + middle.toShortString());
		helper.succeed();
	}

	/**
	 * market_square.md: no job block of any kind ("a vanilla job-site block would be claimed by village workers"), no bed;
	 * a bell is the only point of interest allowed. "The middle is a meeting point, not a wall": the ring of eight cells
	 * round the middle at y = 1 is open room a villager can stand in.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheMarketSquareHasNoJobBlockAndOpenGroundRoundItsMiddle(GameTestHelper helper) {
		Blueprint blueprint = blueprint(helper, StarterBlueprints.MARKET_SQUARE);
		Map<BlockPos, BlockState> cells = cells(blueprint);
		Map<BlockPos, BlockState> pois = pois(cells);
		pois.values().removeIf(s -> s.is(Blocks.BELL));
		helper.assertTrue(pois.isEmpty(), "job blocks in the Market Square: " + shortList(pois));
		helper.assertTrue(bedHeads(cells).isEmpty(), "a bed in the Market Square: " + bedHeads(cells));
		BlockPos middle = squareMiddle();
		List<String> blocked = new ArrayList<>();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockPos p = middle.offset(dx, 0, dz);
				if ((dx != 0 || dz != 0) && !standable(cells, p)) {
					blocked.add(p.toShortString() + " " + BuiltInRegistries.BLOCK.getKey(at(cells, p).getBlock()).getPath());
				}
			}
		}
		helper.assertTrue(blocked.size() <= 2, "the middle " + middle.toShortString() + " is walled in: " + blocked);
		helper.succeed();
	}

	// ---- the Market Cross and Corner ------------------------------------------------------------------------------

	/**
	 * market_cross.md and market_corner.md, "Front and way in": a lane to a build without a door starts one block in
	 * front of the middle of the z = 0 edge, "so that spot should be ground a villager can walk onto from the front"; a
	 * villager (the cross is a meeting place) or a player (the corner's goods are browsed) steps up to it from the front.
	 * No job block or bed (both are points of interest).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheMarketCrossAndCornerAreWalkedUpToFromTheirDoorstep(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (StarterBlueprints.Entry entry : List.of(StarterBlueprints.MARKET_CROSS, StarterBlueprints.MARKET_CORNER)) {
			Blueprint blueprint = blueprint(helper, entry);
			Map<BlockPos, BlockState> cells = cells(blueprint);
			String name = entry.id().getPath();
			Map<BlockPos, BlockState> pois = pois(cells);
			if (!pois.isEmpty() || !bedHeads(cells).isEmpty()) {
				problems.add(name + ": points of interest " + shortList(pois) + " beds " + bedHeads(cells));
			}
			int sx = blueprint.size().getX(), sz = blueprint.size().getZ();
			BlockPos doorstep = new BlockPos((sx - 1) / 2, 0, -1); // (BoundingBox.getCenter on a box min..min+size-1)
			Set<BlockPos> reached = walk(cells, List.of(doorstep), sx, sz, blueprint.size().getY());
			if (!reached.contains(doorstep)) {
				problems.add(name + ": the doorstep " + doorstep.toShortString() + " isn't ground (" + at(cells, doorstep) + ")");
				continue;
			}
			// a drawn, solid piece of the build that a villager stands right next to
			boolean upTo = cells.entrySet().stream().anyMatch(e -> !open(e.getValue()) && e.getKey().getY() <= 2 && e.getKey().getY() >= 0
				&& besideIt(reached, e.getKey()));
			if (!upTo) {
				problems.add(name + ": from the doorstep a villager reaches nothing of the build");
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	// ---- the Trade Hall and the Leader's Hall -----------------------------------------------------------------------

	/**
	 * trade_hall.md, "Must have": exactly one Shop Counter, no Trade Board, no barrel and no other job block of any job;
	 * at least one bed (the Trader's), its head one block toward its facing and against a wall (no head out in a room or
	 * doorway); from the street in front a villager walks right next to the counter and to a spot beside the bed on its own
	 * floor (upper floors by ladder or stairs from inside, fence gates shut; a roof ledge outside the bedroom wall is not
	 * beside the bed).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheTradeHallGivesTheTraderOneCounterAndABedHeWalksTo(GameTestHelper helper) {
		Blueprint blueprint = blueprint(helper, StarterBlueprints.TRADE_HALL);
		Map<BlockPos, BlockState> cells = cells(blueprint);
		List<BlockPos> counters = where(cells, ModBlocks.SHOP_COUNTER);
		helper.assertTrue(counters.size() == 1, "Shop Counters: " + counters);
		Map<BlockPos, BlockState> pois = pois(cells);
		pois.values().removeIf(s -> s.is(ModBlocks.SHOP_COUNTER));
		helper.assertTrue(pois.isEmpty(), "other job blocks in the Trade Hall: " + shortList(pois));
		helper.assertTrue(where(cells, ModBlocks.TRADE_BOARD).isEmpty() && where(cells, Blocks.BARREL).isEmpty(), "a Trade Board or barrel");
		List<BlockPos> beds = bedHeads(cells);
		helper.assertTrue(!beds.isEmpty(), "no bed for the Trader");
		Set<BlockPos> reached = walkFromStreet(blueprint, cells);
		helper.assertTrue(besideIt(reached, counters.get(0)), "the Shop Counter " + counters.get(0).toShortString() + " has no spot beside it a villager walks to from the street");
		List<String> bad = new ArrayList<>();
		for (BlockPos head : beds) {
			Direction facing = cells.get(head).getValue(BedBlock.FACING);
			BlockPos foot = head.relative(facing.getOpposite());
			if (!(at(cells, foot).getBlock() instanceof BedBlock)) {
				bad.add(head.toShortString() + " has no foot behind it");
			}
			if (open(at(cells, head.relative(facing)))) {
				bad.add(head.toShortString() + "'s head isn't against a wall (" + at(cells, head.relative(facing)) + ")");
			}
			if (!besideIt(reached, head) && !besideIt(reached, foot)) {
				bad.add(head.toShortString() + " has no spot beside it a villager walks to from the street");
			}
		}
		helper.assertTrue(bad.isEmpty(), "the Trade Hall's beds: " + bad);
		helper.succeed();
	}

	/**
	 * leaders_hall.md, "Must have": exactly one Training Post, no second one and no other job block; a villager walking in
	 * from the front gets right next to it; a bed is free, but if there is one its head is reached.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheLeadersHallHasOnePostAVillagerWalksUpTo(GameTestHelper helper) {
		Blueprint blueprint = blueprint(helper, StarterBlueprints.LEADERS_HALL);
		Map<BlockPos, BlockState> cells = cells(blueprint);
		List<BlockPos> posts = where(cells, ModBlocks.TRAINING_POST);
		helper.assertTrue(posts.size() == 1, "Training Posts: " + posts);
		Map<BlockPos, BlockState> pois = pois(cells);
		pois.values().removeIf(s -> s.is(ModBlocks.TRAINING_POST));
		helper.assertTrue(pois.isEmpty(), "other job blocks in the Leader's Hall: " + shortList(pois));
		Set<BlockPos> reached = walkFromStreet(blueprint, cells);
		helper.assertTrue(besideIt(reached, posts.get(0)), "the Training Post " + posts.get(0).toShortString() + " has no spot beside it a villager walks to from the street");
		List<BlockPos> stranded = bedHeads(cells).stream().filter(h -> !besideIt(reached, h)
			&& !besideIt(reached, h.relative(cells.get(h).getValue(BedBlock.FACING).getOpposite()))).toList();
		helper.assertTrue(stranded.isEmpty(), "beds not walked to from the street: " + stranded);
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

	static final BlockPos SQUARE = new BlockPos(6, 2, 6);
	private static final BlockPos HALL = new BlockPos(26, 2, 26);

	/** A village: its hall at {@link #HALL}, the rebuilt Market Square placed at {@link #SQUARE} and recorded finished. */
	static BlockPos marketVillage(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		boolean enabled = MarketDays.ENABLED;
		VillageHalls.RADIUS = 32;
		MarketDays.ENABLED = true;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			MarketDays.ENABLED = enabled;
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> {
			level.removeBlock(hall, false);
			VillageHalls.forgetNearest();
		});
		BlockPos origin = place(helper, StarterBlueprints.MARKET_SQUARE, SQUARE);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.MARKET_SQUARE.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		return hall;
	}

	/**
	 * market_square.md in the world: the rebuilt square placed and finished in a village; the market finds its middle at
	 * the footprint's centre, one above the bottom; a market day with a fair's six more (2 + 6 traders) brings every one,
	 * each standing ({@code Walker.canStand}) on its own cell (they don't pile onto one), and the game's own pathfinder
	 * takes a villager from the street in front to every trader (players and villagers come to buy). With no bell in the
	 * square or anywhere in the village (the new drawing has none: the owner's risk in the 51.7 notes), the festival's
	 * gathering place falls back on the hall rather than nowhere, and the market itself still comes (it needs no bell).
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaMarkets1121cDay"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaMarkets1121cDay")
	public void qaAMarketDayOnTheRebuiltSquareBringsEveryTraderToHisOwnReachableCell(GameTestHelper helper) {
		BlockPos hall = marketVillage(helper);
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(SQUARE);
		Optional<BlockPos> square = MarketDays.square(level, hall);
		helper.assertTrue(square.isPresent() && square.get().equals(origin.offset(squareMiddle())), "the market's middle: " + square.map(BlockPos::toShortString)
			+ ", wanted " + origin.offset(squareMiddle()).toShortString());
		int standing = 0;
		for (BlockPos p : BlockPos.betweenClosed(square.get().offset(-4, -2, -4), square.get().offset(4, 3, 4))) {
			if (Walker.canStand(level, p)) {
				standing++;
			}
		}
		helper.assertTrue(standing >= 36, "the placed square has " + standing + " cells a trader can stand on within 4 of its middle");
		boolean bell = BlockPos.betweenClosedStream(origin.offset(-2, -1, -2), origin.offset(16, 17, 16)).anyMatch(p -> level.getBlockState(p).is(Blocks.BELL));
		BlockPos gathering = Festivals.square(level, hall);
		helper.assertTrue(bell ? level.getBlockState(gathering).is(Blocks.BELL) : gathering.equals(hall),
			"the festival gathers at " + gathering.toShortString() + " (bell in the square: " + bell + ")");
		int wanted = MarketDays.traders(level, hall) + 6;
		List<WanderingTrader> came = MarketDays.hold(level, hall, square.get(), 6, t -> {
		});
		Leftovers.after(helper, () -> came.forEach(WanderingTrader::discard));
		helper.assertTrue(came.size() == wanted, came.size() + " traders came, " + wanted + " were due");
		Set<BlockPos> spots = new HashSet<>();
		for (WanderingTrader t : came) {
			BlockPos p = t.blockPosition();
			helper.assertTrue(Walker.canStand(level, p), "a trader stands where nobody can: " + p.subtract(origin).toShortString());
			spots.add(p);
		}
		helper.assertTrue(spots.size() == came.size(), "the traders pile up: " + came.size() + " on " + spots.size() + " cells");
		Villager walker = helper.spawn(EntityType.VILLAGER, SQUARE.offset(squareMiddle().getX(), 0, -3));
		walker.setNoAi(true);
		helper.runAfterDelay(10, () -> {
			walker.setOnGround(true);
			List<String> cut = new ArrayList<>();
			for (BlockPos p : spots) {
				Path path = walker.getNavigation().createPath(p, 1);
				if (path == null || !path.canReach()) {
					cut.add(p.subtract(origin).toShortString());
				}
			}
			walker.discard();
			helper.assertTrue(cut.isEmpty(), "no path from the street in front to the traders at " + cut);
			helper.succeed();
		});
	}

	/** From the street in front, the game's pathfinder takes a villager to within one block of each target. */
	private void pathsFromTheStreet(GameTestHelper helper, StarterBlueprints.Entry entry, BlockPos at, Map<String, List<BlockPos>> targets) {
		Leftovers.clear(helper);
		BlockPos origin = place(helper, entry, at);
		Map<String, List<BlockPos>> found = new HashMap<>();
		for (String name : targets.keySet()) {
			found.put(name, targets.get(name));
		}
		Villager walker = helper.spawn(EntityType.VILLAGER, at.offset((entry.size().getX() - 1) / 2, 0, -3));
		walker.setNoAi(true);
		helper.runAfterDelay(10, () -> {
			walker.setOnGround(true);
			List<String> cut = new ArrayList<>();
			for (Map.Entry<String, List<BlockPos>> e : found.entrySet()) {
				for (BlockPos rel : e.getValue()) {
					BlockPos p = origin.offset(rel);
					Path path = walker.getNavigation().createPath(p, 1);
					if ((path == null || !path.canReach()) && e.getKey().equals("the bed")) {
						// vanilla's paths have no ladders: the claim of a bed up a ladder goes through BedLadders (B304, AcquirePoiMixin)
						var type = helper.getLevel().getPoiManager().getType(p);
						Path up = type.isPresent() ? BedLadders.pathUpALadder(walker, Set.of(com.mojang.datafixers.util.Pair.of(type.get(), p))) : null;
						if (up != null && up.canReach()) {
							path = up;
						}
					}
					if (path == null || !path.canReach()) {
						cut.add(e.getKey() + " " + rel.toShortString() + (path == null ? " (no path)" : " (ends at "
							+ path.getEndNode().asBlockPos().subtract(origin).toShortString() + ")"));
					}
				}
			}
			walker.discard();
			helper.assertTrue(cut.isEmpty(), entry.id().getPath() + ": no path from the street in front to " + cut);
			helper.succeed();
		});
	}

	/**
	 * trade_hall.md in the world: the Trader waits at template (6, 0, -3) and walks in by the front; the game's pathfinder
	 * takes a villager from there to within one block of the Shop Counter (how a villager claims a job site) and of each
	 * bed's head (how he claims it), or, for a bed up a ladder, to the ladder's foot as the mod's bed claim does (B304).
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaMarkets1121cTradePath"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaMarkets1121cTradePath")
	public void qaAVillagerFindsAPathToTheTradeHallsCounterAndBed(GameTestHelper helper) {
		Map<BlockPos, BlockState> cells = cells(blueprint(helper, StarterBlueprints.TRADE_HALL));
		pathsFromTheStreet(helper, StarterBlueprints.TRADE_HALL, new BlockPos(6, 2, 8),
			Map.of("the Shop Counter", where(cells, ModBlocks.SHOP_COUNTER), "the bed", bedHeads(cells)));
	}

	/** leaders_hall.md in the world: from the street in front, a path to within one block of the Training Post and any bed. */
	//$ gametest_ticks_batch HUGE '100' '"qaMarkets1121cLeaderPath"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaMarkets1121cLeaderPath")
	public void qaAVillagerFindsAPathToTheLeadersHallsTrainingPost(GameTestHelper helper) {
		Map<BlockPos, BlockState> cells = cells(blueprint(helper, StarterBlueprints.LEADERS_HALL));
		pathsFromTheStreet(helper, StarterBlueprints.LEADERS_HALL, new BlockPos(6, 2, 8),
			Map.of("the Training Post", where(cells, ModBlocks.TRAINING_POST), "the bed", bedHeads(cells)));
	}
}
