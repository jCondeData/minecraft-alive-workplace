package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane B, 2026-11-23, lane-b-1123-0338): ROADMAP 50.12's Pokémon Center, every level of every look that ships (the
 * rebuilt default and Mountain Lodge, and the Sunny Plaza, which kept its old drawing), checked from the template blocks
 * alone against its blind brief ({@code tools/blueprints/briefs/pokemon_center.md}, not the designers' recipes), with
 * the real Cobblemon loaded, so the Healing Machine, the PC and the Pasture Block are real here where the main suite
 * reads them as air and its reach checks ({@code QaBuildReachGameTests}) can't see them.
 *
 * <p>What the brief pins, each level: exactly one Healing Machine, at (6, 1, 7); Cobblemon's PC at (10, 1, 7) and (10, 2, 7);
 * (6, 1, 8) a cell a villager can stand in; a chest or barrel within 8 across and 4 up or down of the machine; every chest,
 * barrel, bed head and job block within 2 across and 1 up or down of a cell walked from outside; a front door on the
 * ground floor on the z = 0 side opening onto no wall; the plot (I at most 13 x 16 x 13; II 20 x 18 x 26, Lodge II
 * 21 x 18 x 26). Level II adds a Pasture Block and a Shop Counter, both reachable, and keeps the machine, the PC, every
 * chest and barrel and every other block entity of its own I in the same cell. Not repeated: the 60 % kept and the
 * 300-block floor ({@code PokemonCenterGameTests}), the builds and upgrades ({@link QaRebuiltPokemonCenter1121cCompatTests}).
 *
 * <p>Two ways of walking: a villager's walk over the drawing (as {@link QaRebuiltCobblemon1122dCompatTests}: from the street
 * in front, wooden doors open, never standing on a fence, wall or gate, a step up, up to three down, ladders), and, with
 * each level placed in the world, the game's own pathfinder from a villager on the street to every job block, chest and
 * bed, then a real villager walked in to the Healing Machine.
 */
public class QaRebuiltPokemonCenter1123bCompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos MACHINE = new BlockPos(6, 1, 7);
	private static final BlockPos NURSE_SPOT = new BlockPos(6, 1, 8);
	private static final BlockPos PC = new BlockPos(10, 1, 7);
	private static final ResourceLocation HEALING_MACHINE = ResourceLocation.fromNamespaceAndPath("cobblemon", "healing_machine");
	private static final ResourceLocation PC_BLOCK = ResourceLocation.fromNamespaceAndPath("cobblemon", "pc");

	/** One level of one look: its plot from the brief, and for a II the I it is built over. */
	record Level(StarterBlueprints.Entry entry, StarterBlueprints.Entry one, int maxX, int maxY, int maxZ) {
		boolean two() {
			return one != null;
		}

		String name() {
			return entry.id().getPath();
		}
	}

	static final List<Level> LEVELS = List.of(
		new Level(StarterBlueprints.POKEMON_CENTER, null, 13, 16, 13),
		new Level(StarterBlueprints.POKEMON_CENTER_2, StarterBlueprints.POKEMON_CENTER, 20, 18, 26),
		new Level(StarterBlueprints.POKEMON_CENTER_LODGE, null, 13, 16, 13),
		new Level(StarterBlueprints.POKEMON_CENTER_LODGE_2, StarterBlueprints.POKEMON_CENTER_LODGE, 21, 18, 26),
		new Level(StarterBlueprints.POKEMON_CENTER_PLAZA, null, 13, 16, 13),
		new Level(StarterBlueprints.POKEMON_CENTER_PLAZA_2, StarterBlueprints.POKEMON_CENTER_PLAZA, 20, 18, 26));

	// ---- the drawings, from their blocks ----------------------------------------------------------------------------------

	/**
	 * Each level of each look, from its template blocks: the brief's machine, PC, Nurse's cell, storage radius, reach of
	 * every job block, chest, barrel and bed from the street, its front door, its plot, and in II the Pasture Block and the
	 * Shop Counter. All problems of all six drawings are listed together.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyPokemonCenterLevelAndLookKeepsItsBriefsContract(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		StringBuilder seen = new StringBuilder();
		for (Level lvl : LEVELS) {
			Drawing d = drawing(helper, lvl.entry());
			String n = lvl.name();
			var size = d.blueprint().size();
			if (size.getX() > lvl.maxX() || size.getY() > lvl.maxY() || size.getZ() > lvl.maxZ()) {
				problems.add(n + ": " + size.getX() + " x " + size.getY() + " x " + size.getZ() + " doesn't fit its plot of " + lvl.maxX() + " x "
					+ lvl.maxY() + " x " + lvl.maxZ());
			}
			for (BlockPos p : d.blocks().keySet()) {
				if (!d.at(p).isAir() && (p.getX() < 0 || p.getZ() < 0 || p.getX() >= size.getX() || p.getZ() >= size.getZ() || p.getY() >= size.getY())) {
					problems.add(n + ": a block outside its own size at " + p.toShortString());
					break;
				}
			}
			// the job block: one Healing Machine, at (6, 1, 7)
			List<BlockPos> machines = d.where(HEALING_MACHINE);
			if (!machines.equals(List.of(MACHINE))) {
				problems.add(n + ": the Healing Machines are " + machines + ", the brief says exactly one, at " + MACHINE.toShortString());
			}
			if (!id(d.at(PC)).equals(PC_BLOCK) || !id(d.at(PC.above())).equals(PC_BLOCK)) {
				problems.add(n + ": the PC isn't at " + PC.toShortString() + "/" + PC.above().toShortString() + ": " + d.at(PC) + " / " + d.at(PC.above()));
			}
			if (!standable(d::at, NURSE_SPOT, Mode.VILLAGER)) {
				problems.add(n + ": a villager can't stand at the Nurse's cell " + NURSE_SPOT.toShortString() + ": " + d.at(NURSE_SPOT) + " / "
					+ d.at(NURSE_SPOT.above()) + " on " + d.at(NURSE_SPOT.below()));
			}
			Set<BlockPos> walked = d.walk(Mode.VILLAGER);
			// reach: every job block, chest, barrel and bed head within 2 across and 1 up or down of a walked cell
			Map<String, BlockPos> reach = new java.util.LinkedHashMap<>();
			reach.put("the Healing Machine", MACHINE);
			reach.put("the PC", PC);
			List<BlockPos> containers = d.containers();
			for (BlockPos c : containers) {
				reach.put("the " + id(d.at(c)).getPath() + " " + c.toShortString(), c);
			}
			for (BlockPos b : d.bedHeads()) {
				reach.put("the bed " + b.toShortString(), b);
			}
			List<BlockPos> pastures = d.jobSitesOf(ModVillagers.PASTURE_BLOCK);
			List<BlockPos> counters = d.where(BuiltInRegistries.BLOCK.getKey(ModBlocks.SHOP_COUNTER));
			for (BlockPos p : pastures) {
				reach.put("the Pasture Block " + p.toShortString(), p);
			}
			for (BlockPos c : counters) {
				reach.put("the Shop Counter " + c.toShortString(), c);
			}
			reach.forEach((what, at) -> {
				if (!near(walked, at, 2)) {
					problems.add(n + ": " + what + " isn't within 2 across / 1 up or down of a cell walked from the street");
				}
			});
			// storage the Nurse works from
			if (containers.stream().noneMatch(c -> within(c, MACHINE, 8, 4))) {
				problems.add(n + ": no chest or barrel within 8 across / 4 up or down of the Healing Machine (containers " + containers + ")");
			}
			// Level II adds a Pasture Block and a Shop Counter (Level I needs neither)
			if (lvl.two()) {
				if (pastures.isEmpty()) {
					problems.add(n + ": no Pasture Block job site");
				}
				if (counters.isEmpty()) {
					problems.add(n + ": no Shop Counter");
				}
			}
			problems.addAll(frontDoor(d, n));
			// II keeps every block entity of its own I in the same cell (builders never break them while clearing)
			if (lvl.two()) {
				Drawing one = drawing(helper, lvl.one());
				for (Map.Entry<BlockPos, BlockState> e : one.blocks().entrySet()) {
					if (e.getValue().hasBlockEntity() && !d.at(e.getKey()).is(e.getValue().getBlock())) {
						problems.add(n + ": " + lvl.one().id().getPath() + "'s " + id(e.getValue()) + " at " + e.getKey().toShortString() + " is "
							+ d.at(e.getKey()) + " in II");
					}
				}
			}
			seen.append(n).append(": ").append(walked.size()).append(" walked, ").append(containers.size()).append(" containers, ")
				.append(d.bedHeads().size()).append(" beds, ").append(pastures.size()).append(" pastures, ").append(counters.size()).append(" counters; ");
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.getLevel().getServer().sendSystemMessage(Component.literal("[QA 1123b] Pokémon Center drawings: " + seen));
		helper.succeed();
	}

	/**
	 * A front door on the ground floor on the z = 0 side opening onto no wall: a wooden door (lower half) at y = 1 in a
	 * wall facing the front or back, whose outside cell (z - 1) is reached from the street with every door shut (no other
	 * door on the way), and whose inside cell (z + 1) a villager can stand in.
	 */
	private static List<String> frontDoor(Drawing d, String n) {
		Set<BlockPos> shut = d.walk(Mode.SHUT);
		List<BlockPos> doors = new ArrayList<>();
		for (Map.Entry<BlockPos, BlockState> e : d.blocks().entrySet()) {
			BlockState s = e.getValue();
			if (s.getBlock() instanceof DoorBlock && !s.is(Blocks.IRON_DOOR) && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
				doors.add(e.getKey());
			}
		}
		for (BlockPos door : doors) {
			BlockState s = d.at(door);
			if (door.getY() == 1 && s.getValue(DoorBlock.FACING).getAxis() == Direction.Axis.Z && shut.contains(door.north())
				&& standable(d::at, door.south(), Mode.VILLAGER)) {
				return List.of();
			}
		}
		return List.of(n + ": no front door: no wooden door at y = 1 facing the front with its outside reached from the street and a floor "
			+ "inside (doors " + doors + ")");
	}

	// ---- placed in the world: the game's own pathfinder and a real walk ---------------------------------------------------

	/** The default look's I, placed: the pathfinder reaches every job block, chest and bed; a villager walks in to the machine. */
	//$ gametest_ticks_batch HUGE_AREA '1200' '"qa_pc1123b_paths_default"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 1200, batch = "qa_pc1123b_paths_default")
	public void theDefaultPokemonCenterIsWalkedToFromTheStreet(GameTestHelper helper) {
		pathsAndWalk(helper, LEVELS.get(0));
	}

	/** The default look's II. */
	//$ gametest_ticks_batch HUGE_AREA '1200' '"qa_pc1123b_paths_default_2"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 1200, batch = "qa_pc1123b_paths_default_2")
	public void theDefaultPokemonCenterIIIsWalkedToFromTheStreet(GameTestHelper helper) {
		pathsAndWalk(helper, LEVELS.get(1));
	}

	/** The Mountain Lodge's I. */
	//$ gametest_ticks_batch HUGE_AREA '1200' '"qa_pc1123b_paths_lodge"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 1200, batch = "qa_pc1123b_paths_lodge")
	public void theLodgePokemonCenterIsWalkedToFromTheStreet(GameTestHelper helper) {
		pathsAndWalk(helper, LEVELS.get(2));
	}

	/** The Mountain Lodge's II. */
	//$ gametest_ticks_batch HUGE_AREA '1200' '"qa_pc1123b_paths_lodge_2"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 1200, batch = "qa_pc1123b_paths_lodge_2")
	public void theLodgePokemonCenterIIIsWalkedToFromTheStreet(GameTestHelper helper) {
		pathsAndWalk(helper, LEVELS.get(3));
	}

	/** The Sunny Plaza's I (its old drawing, which ships). */
	//$ gametest_ticks_batch HUGE_AREA '1200' '"qa_pc1123b_paths_plaza"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 1200, batch = "qa_pc1123b_paths_plaza")
	public void thePlazaPokemonCenterIsWalkedToFromTheStreet(GameTestHelper helper) {
		pathsAndWalk(helper, LEVELS.get(4));
	}

	/** The Sunny Plaza's II. */
	//$ gametest_ticks_batch HUGE_AREA '1200' '"qa_pc1123b_paths_plaza_2"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 1200, batch = "qa_pc1123b_paths_plaza_2")
	public void thePlazaPokemonCenterIIIsWalkedToFromTheStreet(GameTestHelper helper) {
		pathsAndWalk(helper, LEVELS.get(5));
	}

	/**
	 * {@code lvl} placed at z 3 (centred in x) on the area's floor, its y = 0 on top of the floor as a site puts it. A
	 * villager (a nitwit: no job site to wander off to) stands on the street a block in front of it. Once he is on the
	 * ground, the game's pathfinder must find a whole path from him to a cell beside the Healing Machine, the PC, each
	 * Pasture Block and Shop Counter and each bed, and to a cell within 2 across and 1 up or down of each chest and barrel
	 * (the brief's reach), or, for a bed or container upstairs, to the foot of a ladder the walk over the drawing climbs
	 * to reach it (vanilla's paths have no ladders; BedLadders takes villagers up to their beds); then he is walked (his brain's walk target, by day) to the machine and must end beside it.
	 */
	private static void pathsAndWalk(GameTestHelper helper, Level lvl) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(6000);
		Drawing d = drawing(helper, lvl.entry());
		var size = d.blueprint().size();
		BlockPos origin = new BlockPos(Math.max(2, (30 - size.getX()) / 2), 2, 3);
		StructureTemplate template = level.getStructureManager().get(lvl.entry().id())
			.orElseThrow(() -> new GameTestAssertException("(setup) no structure " + lvl.entry().id()));
		BlockPos at = helper.absolutePos(origin);
		template.placeInWorld(level, at, at, new StructurePlaceSettings(), RandomSource.create(1123L), 2);
		helper.assertTrue(id(level.getBlockState(at.offset(MACHINE))).equals(HEALING_MACHINE), "(setup) " + lvl.name() + " placed without its Healing Machine: "
			+ level.getBlockState(at.offset(MACHINE)));

		Map<String, Set<BlockPos>> goals = new java.util.LinkedHashMap<>();
		// beds and containers up a ladder: vanilla's paths have no ladders (the mod takes villagers up to a bed by one,
		// BedLadders; players climb), so those count when the pathfinder reaches the foot of a ladder and the walk over
		// the drawing, ladders climbed, reaches them from it
		Set<String> byLadder = new HashSet<>();
		Set<BlockPos> walked = d.walk(Mode.VILLAGER);
		Set<BlockPos> ladderFeet = new LinkedHashSet<>();
		for (BlockPos p : d.where(BuiltInRegistries.BLOCK.getKey(Blocks.LADDER))) {
			if (!d.at(p.below()).is(Blocks.LADDER) && standable(d::at, p, Mode.VILLAGER)) {
				ladderFeet.add(at.offset(p));
			}
		}
		Function<BlockPos, Set<BlockPos>> beside = p -> standableAround(level, p, 1, 0, -1);
		Function<BlockPos, Set<BlockPos>> inReach = p -> standableAround(level, p, 2, 1, -1);
		goals.put("the Healing Machine", beside.apply(at.offset(MACHINE)));
		goals.put("the PC", beside.apply(at.offset(PC)));
		for (BlockPos p : d.jobSitesOf(ModVillagers.PASTURE_BLOCK)) {
			goals.put("the Pasture Block " + p.toShortString(), beside.apply(at.offset(p)));
		}
		for (BlockPos p : d.where(BuiltInRegistries.BLOCK.getKey(ModBlocks.SHOP_COUNTER))) {
			goals.put("the Shop Counter " + p.toShortString(), beside.apply(at.offset(p)));
		}
		for (BlockPos p : d.bedHeads()) {
			BlockState head = d.at(p);
			Set<BlockPos> both = new LinkedHashSet<>(beside.apply(at.offset(p)));
			both.addAll(beside.apply(at.offset(p).relative(head.getValue(BedBlock.FACING).getOpposite())));
			goals.put("the bed " + p.toShortString(), both);
			if (near(walked, p, 2)) {
				byLadder.add("the bed " + p.toShortString());
			}
		}
		for (BlockPos c : d.containers()) {
			goals.put("the " + id(d.at(c)).getPath() + " " + c.toShortString(), inReach.apply(at.offset(c)));
			if (near(walked, c, 2)) {
				byLadder.add("the " + id(d.at(c)).getPath() + " " + c.toShortString());
			}
		}

		BlockPos streetCell = origin.offset(NURSE_SPOT.getX(), 0, -1);
		BlockPos street = helper.absolutePos(streetCell);
		Villager villager = helper.spawn(EntityType.VILLAGER, streetCell);
		villager.getRandom().setSeed(1123L);
		villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NITWIT));
		villager.refreshBrain(level);
		BlockPos machine = at.offset(MACHINE);
		boolean[] pathed = {false};
		helper.onEachTick(() -> {
			if (pathed[0]) {
				villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(machine));
				villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(machine, 0.6f, 1));
			}
		});
		helper.succeedWhen(() -> {
			if (!pathed[0]) {
				helper.assertTrue(villager.onGround(), "(setup) the villager isn't on the street's ground yet: at " + villager.position() + " (street "
					+ street.toShortString() + ", " + level.getBlockState(street.below()) + " below, alive " + villager.isAlive() + ")");
				List<String> unreached = new ArrayList<>();
				List<String> upLadders = new ArrayList<>();
				goals.forEach((what, cells) -> {
					if (cells.isEmpty()) {
						unreached.add(what + " (no cell to stand in by it)");
						return;
					}
					Path path = villager.getNavigation().createPath(cells, 0);
					if (path == null || !path.canReach()) {
						if (byLadder.contains(what) && !ladderFeet.isEmpty()) {
							Path ladder = villager.getNavigation().createPath(ladderFeet, 0);
							if (ladder != null && ladder.canReach()) {
								upLadders.add(what);
								return;
							}
						}
						unreached.add(what + (path == null || path.getEndNode() == null ? " (no path)"
							: " (path ends at " + path.getEndNode().asBlockPos().subtract(at).toShortString() + ")"));
					}
				});
				helper.assertTrue(unreached.isEmpty(), lvl.name() + ": from the street the pathfinder can't reach " + unreached);
				level.getServer().sendSystemMessage(Component.literal("[QA 1123b] " + lvl.name() + ": the pathfinder reached all " + goals.size()
					+ " job blocks, containers and beds from the street" + (upLadders.isEmpty() ? "" : " (up a ladder: " + upLadders + ")")));
				pathed[0] = true;
				throw new GameTestAssertException("pathed; walking in");
			}
			Vec3 v = villager.position();
			double dx = v.x - (machine.getX() + 0.5);
			double dz = v.z - (machine.getZ() + 0.5);
			helper.assertTrue(dx * dx + dz * dz <= 2.25 && Math.abs(v.y - machine.getY()) <= 1.0, lvl.name() + ": the villager hasn't walked in "
				+ "to the Healing Machine " + MACHINE.toShortString() + " from the street: at " + BlockPos.containing(v).subtract(at).toShortString());
			level.getServer().sendSystemMessage(Component.literal("[QA 1123b] " + lvl.name() + ": a villager walked from the street to the Healing "
				+ "Machine in " + helper.getTick() + " ticks"));
		});
	}

	/**
	 * The cells round {@code p} (within {@code across} each way, {@code up} up and {@code down} down, not {@code p}
	 * itself and, for {@code across} 1, only its four sides) where a villager can stand: feet and head clear, a floor below
	 * that is no fence, wall or gate.
	 */
	private static Set<BlockPos> standableAround(ServerLevel level, BlockPos p, int across, int up, int down) {
		Set<BlockPos> out = new LinkedHashSet<>();
		for (BlockPos c : BlockPos.betweenClosed(p.offset(-across, down, -across), p.offset(across, up, across))) {
			int dx = Math.abs(c.getX() - p.getX());
			int dz = Math.abs(c.getZ() - p.getZ());
			if (dx + dz == 0 || across == 1 && dx + dz != 1) {
				continue;
			}
			if (standable(level::getBlockState, c, Mode.VILLAGER)) {
				out.add(c.immutable());
			}
		}
		return out;
	}

	// ---- shared: drawings and the walk ------------------------------------------------------------------------------------

	private static ResourceLocation id(BlockState s) {
		return BuiltInRegistries.BLOCK.getKey(s.getBlock());
	}

	/** A drawing's cells; outside them air above the ground, dirt below. */
	private record Drawing(String name, Blueprint blueprint, Map<BlockPos, BlockState> blocks) {
		BlockState at(BlockPos p) {
			BlockState s = blocks.get(p);
			return s != null ? s : p.getY() >= 0 ? Blocks.AIR.defaultBlockState() : Blocks.DIRT.defaultBlockState();
		}

		List<BlockPos> where(ResourceLocation block) {
			return blocks.entrySet().stream().filter(e -> id(e.getValue()).equals(block)).map(Map.Entry::getKey).sorted().toList();
		}

		/** The cells of {@code block} that are a job site (one per two-block-tall block). */
		List<BlockPos> jobSitesOf(ResourceLocation block) {
			return blocks.entrySet().stream().filter(e -> id(e.getValue()).equals(block) && PoiTypes.forState(e.getValue()).isPresent())
				.map(Map.Entry::getKey).sorted().toList();
		}

		List<BlockPos> containers() {
			return blocks.entrySet().stream().filter(e -> e.getValue().getBlock() instanceof ChestBlock || e.getValue().getBlock() instanceof BarrelBlock)
				.map(Map.Entry::getKey).sorted().toList();
		}

		List<BlockPos> bedHeads() {
			return blocks.entrySet().stream().filter(e -> e.getValue().getBlock() instanceof BedBlock && e.getValue().getValue(BedBlock.PART) == BedPart.HEAD)
				.map(Map.Entry::getKey).sorted().toList();
		}

		Set<BlockPos> walk(Mode mode) {
			return QaRebuiltPokemonCenter1123bCompatTests.walk(this::at, blueprint.size().getX(), blueprint.size().getZ(), blueprint.size().getY(), mode);
		}
	}

	private static Drawing drawing(GameTestHelper helper, StarterBlueprints.Entry entry) {
		Blueprint blueprint = BlueprintLibrary.get(helper.getLevel(), entry.id()).orElseThrow(() -> new GameTestAssertException("missing " + entry.id()));
		Map<BlockPos, BlockState> cells = new HashMap<>();
		for (Blueprint.Entry e : blueprint.blocks()) {
			cells.put(e.pos(), e.state());
		}
		helper.assertTrue(cells.values().stream().anyMatch(s -> id(s).equals(HEALING_MACHINE) || id(s).equals(PC_BLOCK)),
			"(setup) " + entry.id() + " loaded with no Cobblemon block: is Cobblemon in this run?");
		return new Drawing(entry.id().getPath(), blueprint, cells);
	}

	static boolean within(BlockPos a, BlockPos b, int across, int vertical) {
		return Math.abs(a.getX() - b.getX()) <= across && Math.abs(a.getZ() - b.getZ()) <= across && Math.abs(a.getY() - b.getY()) <= vertical;
	}

	/** VILLAGER: wooden doors open; SHUT: every door shut. */
	enum Mode { VILLAGER, SHUT }

	/** A villager's feet or head can be in it: a wooden door (VILLAGER), a ladder, or nothing over a fifth of a block tall. */
	private static boolean open(BlockState state, Mode mode) {
		if (state.getBlock() instanceof DoorBlock) {
			return mode == Mode.VILLAGER && !state.is(Blocks.IRON_DOOR);
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

	private static boolean standable(Function<BlockPos, BlockState> at, BlockPos feet, Mode mode) {
		BlockState here = at.apply(feet);
		if (!open(here, mode) || !open(at.apply(feet.above()), mode) || !here.getFluidState().isEmpty()) {
			return false;
		}
		BlockState below = at.apply(feet.below());
		return here.is(Blocks.LADDER) || below.is(Blocks.LADDER) || (!open(below, Mode.SHUT) && !(below.getBlock() instanceof DoorBlock) && !noFloor(below)
			&& below.getFluidState().isEmpty());
	}

	/** Every spot reached on foot from the street in front (z -2..-1 at y 0): level, a step up, up to three down, ladders. */
	private static Set<BlockPos> walk(Function<BlockPos, BlockState> at, int sx, int sz, int top, Mode mode) {
		Set<BlockPos> reached = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		for (int x = -2; x <= sx + 1; x++) {
			for (int z = -2; z <= -1; z++) {
				BlockPos s = new BlockPos(x, 0, z);
				if (standable(at, s, mode) && reached.add(s)) {
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
				if (open(at.apply(pos.above(2)), mode)) {
					next.add(side.above());
				}
				for (int drop = 1; drop <= 3; drop++) {
					if (!open(at.apply(side.below(drop - 1)), mode) || !open(at.apply(side.below(drop - 1).above()), mode)) {
						break;
					}
					next.add(side.below(drop));
				}
			}
			if (at.apply(pos).is(Blocks.LADDER) || at.apply(pos.above()).is(Blocks.LADDER) && !open(at.apply(pos.below()), Mode.SHUT)) {
				next.add(pos.above());
			}
			if (at.apply(pos.below()).is(Blocks.LADDER)) {
				next.add(pos.below());
			}
			for (BlockPos nx : next) {
				if (nx.getX() < -2 || nx.getX() > sx + 1 || nx.getZ() < -2 || nx.getZ() > sz + 1 || nx.getY() < -8 || nx.getY() > top) {
					continue;
				}
				if (!reached.contains(nx) && standable(at, nx, mode)) {
					reached.add(nx);
					queue.add(nx);
				}
			}
		}
		return reached;
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
}
