package io.github.jcondedata.aliveworkplace.city;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintFiles;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Paths;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jetbrains.annotations.Nullable;

/**
 * Roads (ROADMAP 27.15): the approved roads on a hall's plan get built. Each hall's Steward has their way found over the
 * ground ({@link Paths.Search}, {@link #NODES_PER_TICK} nodes a tick per hall): the road's width kept clear, round water,
 * buildings and anything not natural, steps of at most one block. The way is saved on the road
 * ({@link CityPlan.Road#route}) and cut into segments of {@link #SEGMENT} nodes; each segment becomes a generated
 * blueprint ({@code aliveworkplace:roads/<hall>/<road>_<n>}, saved as the Shape Planner saves its blueprints) of the
 * surface in the road's style ({@link RoadStyles}: middle and edges, stairs at each one-block step) and of the air over
 * it where natural cover stands, and a build site for the nearest idle builder, while no building of the village waits.
 * At most {@link #MAX_OPEN} segments are open at once. Finished segments are kept on the plan ({@link CityPlan.Road#built}),
 * not in {@link BuildSiteManager}'s finished list, so ranks, the map and homes never see them.
 *
 * <p>A finished building's door joins the nearest road with a lane ({@link #joinNearest}), itself a road on the plan
 * built the same way. Config {@code stewardRoads}.
 */
public final class Roads {
	/** Config {@code stewardRoads}: off, roads on the plan are drawn but not built (and doors get the builders' dirt paths). */
	public static boolean ENABLED = true;
	/** Route nodes per segment. */
	public static final int SEGMENT = 24;
	/** Path nodes looked at per tick per hall. */
	public static final int NODES_PER_TICK = 600;
	/** Road segments open at once per hall. */
	public static final int MAX_OPEN = 2;
	/** Nodes one stretch between two of a road's points may take before it's given up. */
	static final int MAX_LEG_NODES = 30000;
	/** How often (ticks) a hall looks for a segment to open. */
	static final int CHECK_EVERY = 20;
	/** How high over the surface natural cover (grass, leaves, a bank of earth) is cleared. */
	static final int HEADROOM = 3;
	/** How far a lane may lead from a door to a road. */
	public static final int LANE_REACH = 48;
	public static final String FOLDER = "roads/";
	/** Widest gap (water, or a drop deeper than {@link #DROP}) a bridge spans (27.16). */
	public static final int BRIDGE_MAX = 16;
	/** A drop deeper than this under a road's surface is a gap to bridge. */
	static final int DROP = 2;
	/** A bridge's pillar stands under every this many blocks of its deck. */
	public static final int PILLAR_EVERY = 4;
	/** How deep a pillar goes at most. */
	static final int PILLAR_DEPTH = 12;
	/** How far past a gap's start the far bank is looked for. */
	static final int GAP_LOOK = 40;
	/** A street lamp every this many blocks of a street or avenue, on alternate sides. */
	public static final int LAMP_EVERY = 16;
	/** A lantern post every this many blocks of a lane. */
	public static final int POST_EVERY = 12;

	/** A road's way being found, one stretch (point to point) after another. */
	private static final class Routing {
		int index;
		final CityPlan.Road road;
		final List<BlockPos> goals;
		final List<BoundingBox> avoid;
		final List<BlockPos> route = new ArrayList<>();
		/** Legs laid straight over a gap (27.16): the deck's nodes, then the far bank's. */
		final Map<Integer, List<BlockPos>> bridges = new HashMap<>();
		/** Legs already looked along for a gap. */
		final java.util.Set<Integer> checked = new java.util.HashSet<>();
		/** The gap the road stops at, too wide to bridge (0: none). */
		int gap;
		int leg;
		/** A road to another village (27.17) waiting for the world round its next stretch to be loaded. */
		boolean paused;
		@Nullable
		Paths.Search search;

		Routing(int index, CityPlan.Road road, List<BlockPos> goals, List<BoundingBox> avoid) {
			this.index = index;
			this.road = road;
			this.goals = new ArrayList<>(goals);
			this.avoid = avoid;
		}
	}

	private static final Map<ServerLevel, Map<BlockPos, Routing>> ROUTING = new WeakHashMap<>();
	/** Roads to other villages (27.17) set aside, paused, while the hall's other roads have their ways found. */
	private static final Map<ServerLevel, Map<BlockPos, List<Routing>>> PARKED = new WeakHashMap<>();

	/** Every tick, from the hall's tick: the way of a road being found moves on; every second, a segment may open. */
	public static void tick(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		if (!ENABLED) {
			return;
		}
		CaravanRoads.tick(level, hall, entity); // 27.17
		if (entity.plan().roads().isEmpty()) {
			return;
		}
		Map<BlockPos, Routing> routing = ROUTING.get(level);
		Routing r = routing == null ? null : routing.get(hall);
		if (r != null) {
			route(level, hall, entity, r, NODES_PER_TICK);
		}
		if (Math.floorMod(level.getGameTime() + hall.hashCode(), CHECK_EVERY) == 0) {
			round(level, hall, entity);
		}
	}

	/** Whether the hall's Steward is at work on roads: one appointed, Stewards on, and the plan not resting. */
	static boolean working(ServerLevel level, BlockPos hall, CityPlan plan) {
		return Stewards.ENABLED && plan.mode() != CityPlan.Mode.REST && Stewards.stewardOf(level, hall) != null;
	}

	/** Starts finding the way of the first approved road without one; opens segments while fewer than {@link #MAX_OPEN} are. */
	public static void round(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		CityPlan plan = entity.plan();
		if (!working(level, hall, plan)) {
			return;
		}
		Map<BlockPos, Routing> routing = ROUTING.computeIfAbsent(level, l -> new HashMap<>());
		List<Routing> parked = PARKED.computeIfAbsent(level, l -> new HashMap<>()).computeIfAbsent(hall.immutable(), h -> new ArrayList<>());
		parked.removeIf(p -> !onPlan(plan, p));
		Routing current = routing.get(hall);
		if (current != null && !onPlan(plan, current)) {
			routing.remove(hall); // the road was changed or taken off the plan
			current = null;
		}
		if (current == null || current.paused) {
			int index = nextToRoute(plan, current);
			if (index >= 0 && (current == null || index != current.index)) {
				if (current != null) {
					parked.add(current); // a road to another village waiting for the world to load waits its turn (27.17)
				}
				Routing resumed = null;
				for (Routing p : parked) {
					if (p.index == index && p != current) {
						resumed = p;
					}
				}
				parked.remove(resumed);
				routing.put(hall.immutable(), resumed != null ? resumed : startRouting(level, hall, index, plan.roads().get(index)));
			}
		}
		openSegments(level, hall, entity);
	}

	/** Whether a routing's road is still on the plan as it was and without a way; its index brought up to date. */
	private static boolean onPlan(CityPlan plan, Routing r) {
		for (int i = 0; i < plan.roads().size(); i++) {
			CityPlan.Road road = plan.roads().get(i);
			if (sameRoad(road, r.road) && !road.routed()) {
				r.index = i;
				return true;
			}
		}
		return false;
	}

	/**
	 * The next road to find the way of: the first approved road without one, the village's own before its roads to other
	 * villages (27.17); with only those left, the one after {@code current}'s, so each paused one waits its turn. -1: none.
	 */
	private static int nextToRoute(CityPlan plan, @Nullable Routing current) {
		List<Integer> caravan = new ArrayList<>();
		for (int i = 0; i < plan.roads().size(); i++) {
			CityPlan.Road road = plan.roads().get(i);
			if (road.approved() && !road.routed()) {
				if (!road.caravan()) {
					return i;
				}
				caravan.add(i);
			}
		}
		if (caravan.isEmpty()) {
			return -1;
		}
		if (current != null) {
			for (int i : caravan) {
				if (i > current.index) {
					return i;
				}
			}
		}
		return caravan.get(0);
	}

	private static boolean sameRoad(CityPlan.Road a, CityPlan.Road b) {
		return a.points().equals(b.points()) && a.width() == b.width() && a.style().equals(b.style()) && a.lane() == b.lane()
			&& a.toward().equals(b.toward());
	}

	// ---- routing --------------------------------------------------------------------------------------------------

	private static Routing startRouting(ServerLevel level, BlockPos hall, int index, CityPlan.Road road) {
		List<BoundingBox> avoid = buildings(level, hall);
		List<BlockPos> goals = new ArrayList<>();
		for (BlockPos p : road.points()) {
			goals.add(new BlockPos(hall.getX() + p.getX(), hall.getY(), hall.getZ() + p.getZ()));
		}
		return new Routing(index, road, goals, avoid);
	}

	/** The buildings to keep off: every finished building and build site round the hall (roads' own sites aside), a block wider. */
	static List<BoundingBox> buildings(ServerLevel level, BlockPos hall) {
		List<BoundingBox> out = new ArrayList<>();
		BuildSiteManager manager = BuildSiteManager.get(level);
		for (BuildSiteManager.Finished f : manager.finishedNear(level, hall, VillageHalls.RADIUS + 32)) {
			BlueprintLibrary.get(level, f.structure()).ifPresent(b -> out.add(BlueprintOutline.bounds(f.placement(), b.size()).inflatedBy(1)));
		}
		String dimension = Ids.of(level.dimension()).toString();
		for (BuildSite site : manager.all()) {
			if (!isSegment(site.structure()) && site.placement().dimension().toString().equals(dimension)
				&& site.placement().origin().distSqr(hall) <= (double) (VillageHalls.RADIUS + 32) * (VillageHalls.RADIUS + 32)) {
				BlueprintLibrary.get(level, site.structure()).ifPresent(b -> out.add(BlueprintOutline.bounds(site.placement(), b.size()).inflatedBy(1)));
			}
		}
		return out;
	}

	/** Works on {@code r} for up to {@code budget} nodes; when the last stretch is done, the way is saved on the road. */
	static void route(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, Routing r, int budget) {
		CityPlan plan = entity.plan();
		if (!onPlan(plan, r)) {
			ROUTING.get(level).remove(hall);
			return;
		}
		int halfWidth = (r.road.width() - 1) / 2;
		boolean caravan = r.road.caravan();
		boolean stop = false;
		while (budget > 0 && !stop) {
			if (r.search == null) {
				if (r.leg + 1 >= r.goals.size()) {
					stop = true;
					break;
				}
				List<BlockPos> bridge = r.bridges.get(r.leg);
				if (bridge != null) {
					r.route.addAll(bridge); // over the gap, straight: the deck, then the far bank
					r.leg++;
					continue;
				}
				BlockPos fromColumn = r.route.isEmpty() ? r.goals.get(r.leg) : r.route.get(r.route.size() - 1);
				BlockPos toColumn = r.goals.get(r.leg + 1);
				if (caravan && !CaravanRoads.loaded(level, fromColumn, toColumn)) {
					r.paused = true; // a road to another village goes on only where the world is loaded (27.17)
					break;
				}
				r.paused = false;
				BlockPos from = r.route.isEmpty() ? feet(level, r.goals.get(r.leg), halfWidth) : r.route.get(r.route.size() - 1);
				// far from the hall, the ground is looked for round the height the road has come to, not the hall's
				BlockPos to = feet(level, caravan && from != null ? toColumn.atY(from.getY()) : toColumn, halfWidth);
				if (from == null || to == null) {
					stop = true; // a point with nowhere to stand: the road ends before it
					break;
				}
				if (r.route.isEmpty()) {
					r.route.add(from);
				}
				if (r.checked.add(r.leg) && splitAtGap(level, r, from, to)) {
					continue; // the stretch now ends at the near bank (and a bridge, or the road's end, follows)
				}
				int reach = (int) Math.sqrt(from.distSqr(to)) + (caravan ? 16 : 32); // a road to another village keeps within the loaded margin
				r.search = new Paths.Search(level, from, to, new Paths.Options(halfWidth, r.avoid, reach, Roads::clear));
			}
			int before = r.search.visited();
			boolean done = r.search.step(budget);
			budget -= Math.max(1, r.search.visited() - before);
			if (!done && r.search.visited() >= MAX_LEG_NODES) {
				stop = true; // too far round: the road ends here
				break;
			}
			if (done) {
				List<BlockPos> way = r.search.result();
				if (way.isEmpty()) {
					stop = true;
					break;
				}
				for (BlockPos p : way) {
					if (!p.equals(r.route.get(r.route.size() - 1))) {
						r.route.add(p);
					}
				}
				r.search = null;
				r.leg++;
			}
		}
		if (stop) {
			List<BlockPos> offsets = new ArrayList<>(r.route.size());
			for (BlockPos p : r.route) {
				offsets.add(p.subtract(hall));
			}
			CityPlan next = plan.withRoad(r.index, r.road.withRoute(offsets, r.gap));
			if (next != null) {
				entity.setPlan(next);
			}
			ROUTING.get(level).remove(hall);
		}
	}

	/**
	 * Runs the hall's routing to the end at once (tests, and the showcase), going on with the road being routed if there
	 * is one. False if it stopped at a road to another village waiting for the world to load (27.17): it goes on from
	 * there next time.
	 */
	public static boolean routeNow(ServerLevel level, BlockPos hall) {
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return true;
		}
		Map<BlockPos, Routing> routing = ROUTING.computeIfAbsent(level, l -> new HashMap<>());
		for (int guard = 0; guard < CityPlan.MAX_ROADS + CityPlan.MAX_LANES + CityPlan.MAX_CARAVAN_ROADS; guard++) {
			CityPlan plan = entity.plan();
			Routing r = routing.get(hall);
			if (r == null || !r.paused || !onPlan(plan, r)) { // only a paused road goes on where it was; any other starts afresh
				int index = nextToRoute(plan, null);
				if (index < 0) {
					routing.remove(hall);
					return true;
				}
				r = startRouting(level, hall, index, plan.roads().get(index));
				routing.put(hall.immutable(), r);
			}
			for (int i = 0; i < 1000 && routing.get(hall) == r; i++) {
				route(level, hall, entity, r, NODES_PER_TICK);
				if (r.paused) {
					return false;
				}
			}
		}
		return true;
	}

	/** Whether the hall's road being routed waits for the world round its next stretch to load (27.17). */
	public static boolean routingPaused(ServerLevel level, BlockPos hall) {
		Map<BlockPos, Routing> routing = ROUTING.get(level);
		Routing r = routing == null ? null : routing.get(hall);
		return r != null && r.paused;
	}

	/** How many nodes of its way the hall's road being routed has so far (0: none being routed). */
	public static int routedSoFar(ServerLevel level, BlockPos hall) {
		Map<BlockPos, Routing> routing = ROUTING.get(level);
		Routing r = routing == null ? null : routing.get(hall);
		return r == null ? 0 : r.route.size();
	}

	/** A gap on a road's line (27.16): the near bank (feet), the far bank (feet; null if none was found), its width, the nodes over it. */
	public record Gap(BlockPos near, @Nullable BlockPos far, int width, List<BlockPos> nodes) {
	}

	/**
	 * Looks along the straight line of a stretch for a gap; if there is one, the stretch is split there: to the near bank,
	 * a bridge over it ({@link Routing#bridges}) and on from the far bank, or, wider than {@link #BRIDGE_MAX}, the road
	 * ends at the near bank. False when the line has no gap.
	 */
	private static boolean splitAtGap(ServerLevel level, Routing r, BlockPos from, BlockPos to) {
		Gap g = gap(level, from, to);
		if (g == null) {
			return false;
		}
		boolean atStart = g.near().equals(from);
		if (g.far() == null || g.width() > BRIDGE_MAX) {
			r.gap = g.width();
			while (r.goals.size() > r.leg + 1) {
				r.goals.remove(r.goals.size() - 1);
			}
			if (!atStart) {
				r.goals.add(g.near());
			}
			return true;
		}
		if (atStart) {
			r.bridges.put(r.leg, g.nodes());
			r.goals.add(r.leg + 1, g.far());
		} else {
			r.goals.add(r.leg + 1, g.near());
			r.bridges.put(r.leg + 1, g.nodes());
			r.goals.add(r.leg + 2, g.far());
		}
		return true;
	}

	/**
	 * The first gap on the straight line from {@code from} to {@code to} (feet positions), following the ground: a column
	 * with water, or nothing within {@link #DROP} below the surface. The bridge goes straight on (along the line's main
	 * axis) to the first column with ground again, its deck a block over the higher bank so a stair leads up at each end.
	 */
	@Nullable
	public static Gap gap(ServerLevel level, BlockPos from, BlockPos to) {
		int dx = to.getX() - from.getX();
		int dz = to.getZ() - from.getZ();
		int n = Math.max(Math.abs(dx), Math.abs(dz));
		BlockPos last = from;
		for (int i = 1; i <= n; i++) {
			int x = from.getX() + Math.round(dx * i / (float) n);
			int z = from.getZ() + Math.round(dz * i / (float) n);
			if (x == last.getX() && z == last.getZ()) {
				continue;
			}
			Integer ground = ground(level, x, last.getY(), z);
			if (ground != null) {
				last = new BlockPos(x, ground + 1, z);
				continue;
			}
			Direction d = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
			int h = last.getY();
			for (int k = 1; k <= GAP_LOOK; k++) {
				BlockPos c = last.relative(d, k);
				Integer g = ground(level, c.getX(), h, c.getZ());
				if (g != null) {
					BlockPos far = new BlockPos(c.getX(), g + 1, c.getZ());
					int deck = Math.max(h, far.getY()) + 1;
					List<BlockPos> nodes = new ArrayList<>();
					for (int j = 1; j < k; j++) {
						nodes.add(last.relative(d, j).atY(deck));
					}
					nodes.add(far);
					return new Gap(last, far, k - 1, List.copyOf(nodes));
				}
			}
			return new Gap(last, null, GAP_LOOK + 1, List.of());
		}
		return null;
	}

	/** The top of the ground at {@code x, z} for feet at {@code feetY} (from a block up to {@link #DROP} down); null over water or a deeper drop. */
	@Nullable
	static Integer ground(ServerLevel level, int x, int feetY, int z) {
		for (int y = feetY + 1; y >= feetY - 1 - DROP; y--) {
			BlockPos p = new BlockPos(x, y, z);
			BlockState s = level.getBlockState(p);
			if (!level.getFluidState(p).isEmpty()) {
				return null;
			}
			if (!s.isAir() && !cover(s)) {
				return y;
			}
		}
		return null;
	}

	/** Whether a road's node stands over a gap (a bridge's deck): nothing solid under its surface nor under that. */
	static boolean overGap(ServerLevel level, BlockPos node) {
		return !solid(level, node.below()) && !solid(level, node.below(2));
	}

	private static boolean solid(ServerLevel level, BlockPos p) {
		BlockState s = level.getBlockState(p);
		return level.getFluidState(p).isEmpty() && !s.isAir() && !cover(s);
	}

	/** Where to stand at a road's point: the ground of its column (or the nearest clear spot within 3 blocks). */
	@Nullable
	static BlockPos feet(ServerLevel level, BlockPos column, int halfWidth) {
		for (int r = 0; r <= 3; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					int x = column.getX() + dx;
					int z = column.getZ() + dz;
					// the highest spot to stand within 16 blocks of the hall's height (not the heightmap: a roof or a
					// tree over the point would hide the ground)
					int top = Math.min(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), column.getY() + 16);
					for (int y = top; y >= column.getY() - 16; y--) {
						BlockPos feet = Paths.walkable(level, new BlockPos(x, y, z));
						if (feet != null && feet.getY() == y && clear(level, x, y, z)) {
							return feet;
						}
					}
				}
			}
		}
		return null;
	}

	/**
	 * Whether a road may take the column at {@code x, z}, its feet at {@code feetY}: natural ground or road under it (or a
	 * dip of one block onto natural ground), and over it nothing but air, natural cover or natural ground to dig away;
	 * no water. A player's fence, wall, path block or anything built is never a road's.
	 */
	public static boolean clear(ServerLevel level, int x, int feetY, int z) {
		BlockPos feet = new BlockPos(x, feetY, z);
		BlockState ground = level.getBlockState(feet.below());
		if (!level.getFluidState(feet.below()).isEmpty()) {
			return false;
		}
		if (!natural(ground) && !RoadStyles.isPaving(ground)) {
			if (!(ground.isAir() || cover(ground))) {
				return false;
			}
			BlockState under = level.getBlockState(feet.below(2));
			if (!level.getFluidState(feet.below(2)).isEmpty() || !natural(under) && !RoadStyles.isPaving(under)) {
				return false;
			}
		}
		for (int dy = 0; dy < HEADROOM; dy++) {
			BlockPos p = feet.above(dy);
			BlockState s = level.getBlockState(p);
			if (!level.getFluidState(p).isEmpty() || !(s.isAir() || cover(s) || natural(s) || dy == 0 && RoadStyles.isPaving(s))) {
				return false;
			}
		}
		return true;
	}

	/** Natural ground (dirt, grass, sand, stone, gravel...): {@link BuildPlan#isTerrain} without the plants. */
	static boolean natural(BlockState state) {
		return BuildPlan.isTerrain(state) && !state.canBeReplaced();
	}

	/** Natural cover a road clears away: grass, flowers, snow, leaves. */
	static boolean cover(BlockState state) {
		return !state.isAir() && state.getFluidState().isEmpty() && !state.hasBlockEntity()
			&& (state.canBeReplaced() || state.is(BlockTags.FLOWERS) || state.is(BlockTags.LEAVES));
	}

	// ---- segments -------------------------------------------------------------------------------------------------

	/** Whether {@code id} is a road segment's blueprint. */
	public static boolean isSegment(ResourceLocation id) {
		return id.getNamespace().equals(AliveWorkplace.MOD_ID) && id.getPath().startsWith(FOLDER);
	}

	static String hallKey(BlockPos hall) {
		return hall.getX() + "_" + hall.getY() + "_" + hall.getZ();
	}

	/** The road's part of its segments' ids: the same road routed the same way keeps it. */
	static String roadKey(CityPlan.Road road) {
		return Integer.toHexString(Objects.hash(road.points(), road.width(), road.style(), road.route(), road.lane()));
	}

	/** Segment {@code n} of {@code road}'s blueprint id. */
	public static ResourceLocation segmentId(BlockPos hall, CityPlan.Road road, int n) {
		return AliveWorkplace.id(FOLDER + hallKey(hall) + "/" + roadKey(road) + "_" + n);
	}

	/** The open road segments of the hall. */
	public static List<BuildSite> openSegments(ServerLevel level, BlockPos hall) {
		String prefix = FOLDER + hallKey(hall) + "/";
		return BuildSiteManager.get(level).all().stream()
			.filter(s -> s.structure().getNamespace().equals(AliveWorkplace.MOD_ID) && s.structure().getPath().startsWith(prefix)).toList();
	}

	/** Whether a building of the village waits for a builder: a Steward's build queued or with nobody on it. */
	static boolean buildingWaits(ServerLevel level, BlockPos hall) {
		for (BuildSite site : StewardDesk.openSites(level, hall)) {
			if (!isSegment(site.structure()) && (site.isQueued() || site.builder() == null)) {
				return true;
			}
		}
		return false;
	}

	/** Opens segments (first road first, each from its start) while fewer than {@link #MAX_OPEN} are open and builders are free. */
	static void openSegments(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		List<BuildSite> open = openSegments(level, hall);
		if (open.size() >= MAX_OPEN || buildingWaits(level, hall)) {
			return;
		}
		List<ResourceLocation> taken = new ArrayList<>(open.stream().map(BuildSite::structure).toList());
		int opened = open.size();
		CityPlan plan = entity.plan();
		for (CityPlan.Road road : plan.roads()) {
			if (!road.approved() || !road.routed()) {
				continue;
			}
			for (int n = 0; n < road.segments(); n++) {
				if (road.built().contains(n)) {
					continue;
				}
				ResourceLocation id = segmentId(hall, road, n);
				if (taken.contains(id)) {
					continue;
				}
				if (road.caravan() && !segmentLoaded(level, hall, road, n)) {
					continue; // a stretch of a road to another village where the world isn't loaded: later (27.17)
				}
				Optional<Segment> segment = segment(level, hall, road, n);
				if (segment.isEmpty()) {
					continue;
				}
				Villager builder = builderFor(level, hall, segment.get().box(), road.caravan());
				if (builder == null || !save(level, segment.get().blueprint())) {
					return; // nobody free: wait
				}
				UUID owner = entity.owner() != null ? entity.owner() : builder.getUUID();
				String ownerName = entity.owner() != null ? entity.ownerName() : "";
				BuildSite site = Builders.start(level, builder, owner, ownerName, id, segment.get().placement());
				site.setLevelGround(false); // a road is laid on the ground as it is, not levelled round
				taken.add(id);
				if (++opened >= MAX_OPEN) {
					return;
				}
			}
		}
	}

	/** Whether the world is loaded round segment {@code n} of {@code road} (its nodes, and the room its lamps and milestone take). */
	static boolean segmentLoaded(ServerLevel level, BlockPos hall, CityPlan.Road road, int n) {
		int from = n * SEGMENT;
		int to = Math.min(road.route().size(), from + SEGMENT);
		for (int j = from; j < to; j++) {
			BlockPos p = road.route().get(j).offset(hall);
			for (int dx = -8; dx <= 8; dx += 8) {
				for (int dz = -8; dz <= 8; dz += 8) {
					if (!level.hasChunk((p.getX() + dx) >> 4, (p.getZ() + dz) >> 4)) {
						return false;
					}
				}
			}
		}
		return true;
	}

	/**
	 * The nearest idle builder of the village (nothing being built, nothing queued) whose bench reaches the segment; for
	 * a road to another village (27.17, {@code far}), any idle builder of the village: the road goes out past the benches'
	 * reach, and the builder walks out to it.
	 */
	@Nullable
	static Villager builderFor(ServerLevel level, BlockPos hall, BoundingBox box, boolean far) {
		BlockPos centre = box.getCenter();
		Villager best = null;
		double bestDist = Double.MAX_VALUE;
		for (Villager v : StewardDesk.builders(level, hall)) {
			BlockPos bench = Builders.benchPos(v).orElseThrow();
			if (Builders.activeSite(level, v) != null || !Builders.queue(level, v).isEmpty()
				|| !far && Math.sqrt(centre.distSqr(bench)) > Builders.MAX_SITE_DISTANCE) {
				continue;
			}
			double d = v.distanceToSqr(centre.getX(), centre.getY(), centre.getZ());
			if (d < bestDist) {
				bestDist = d;
				best = v;
			}
		}
		return best;
	}

	/** A segment worked out: its blueprint, where it goes, and its bounds. */
	public record Segment(Blueprint blueprint, BlueprintData.Placement placement, BoundingBox box) {
	}

	/**
	 * Segment {@code n} of {@code road} as a blueprint, from the ground as it is now: every column within the road's width
	 * of a node of the segment (and nearer it than any other node) gets the style's surface (the edge's at the outermost
	 * columns) at the node's ground height, stairs where the way steps up or down a block, and air over it where natural
	 * cover stands.
	 */
	public static Optional<Segment> segment(ServerLevel level, BlockPos hall, CityPlan.Road road, int n) {
		List<BlockPos> route = road.route().stream().map(p -> p.offset(hall)).toList();
		int from = n * SEGMENT;
		int to = Math.min(route.size(), from + SEGMENT);
		if (from >= to) {
			return Optional.empty();
		}
		int r = (road.width() - 1) / 2;
		RoadStyles.Style style = RoadStyles.forBlueprintStyle(road.style());
		// each column goes to the nearest node of the whole route (the first, on a tie), so segments never overlap
		Map<Long, int[]> columns = new LinkedHashMap<>(); // column -> {node, distance, rank}
		for (int j = Math.max(0, from - 2 * r - 1); j < Math.min(route.size(), to + 2 * r + 1); j++) {
			BlockPos node = route.get(j);
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					long key = BlockPos.asLong(node.getX() + dx, 0, node.getZ() + dz);
					int d = Math.max(Math.abs(dx), Math.abs(dz));
					int rank = d * 64 + Math.abs(dx) + Math.abs(dz); // nearest across the road, then straight across
					int[] had = columns.get(key);
					if (had == null || rank < had[2]) {
						columns.put(key, new int[] {j, d, rank});
					}
				}
			}
		}
		// the other roads' columns (27.16): where this road crosses one, the crossing is paved square, all middle
		java.util.Set<Long> others = new java.util.HashSet<>();
		if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) {
			for (CityPlan.Road other : entity.plan().roads()) {
				if (other.routed() && !sameRoad(other, road)) {
					int or = (other.width() - 1) / 2;
					for (BlockPos o : other.route()) {
						for (int dx = -or; dx <= or; dx++) {
							for (int dz = -or; dz <= or; dz++) {
								others.add(BlockPos.asLong(hall.getX() + o.getX() + dx, 0, hall.getZ() + o.getZ() + dz));
							}
						}
					}
				}
			}
		}
		Map<Integer, Boolean> gaps = new HashMap<>();
		java.util.function.IntPredicate bridge = j -> gaps.computeIfAbsent(j, k -> overGap(level, route.get(k)));
		Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
		for (Map.Entry<Long, int[]> e : columns.entrySet()) {
			int j = e.getValue()[0];
			if (j < from || j >= to) {
				continue;
			}
			BlockPos column = BlockPos.of(e.getKey());
			BlockPos node = route.get(j);
			BlockPos surface = new BlockPos(column.getX(), node.getY() - 1, column.getZ());
			boolean crossing = others.contains(e.getKey());
			BlockState state = (bridge.test(j) ? RoadStyles.Mix.of(style.deck()) : r > 0 && e.getValue()[1] == r && !crossing ? style.edge() : style.middle())
				.at(surface).defaultBlockState();
			Direction up = step(route, j);
			if (up != null && style.stairs().defaultBlockState().hasProperty(StairBlock.FACING)) {
				state = style.stairs().defaultBlockState().setValue(StairBlock.FACING, up);
			}
			blocks.put(surface, state);
			for (int dy = 1; dy <= HEADROOM; dy++) {
				BlockState over = level.getBlockState(surface.above(dy));
				if (!over.isAir() && (cover(over) || natural(over))) {
					blocks.put(surface.above(dy), Blocks.AIR.defaultBlockState());
				}
			}
		}
		// bridges (27.16): rails along both sides of the deck, a pillar under every PILLAR_EVERY-th block down to the bed
		for (int j = from; j < to; j++) {
			if (!bridge.test(j)) {
				continue;
			}
			BlockPos node = route.get(j);
			Direction side = along(route, j).getClockWise();
			for (Direction d : new Direction[] {side, side.getOpposite()}) {
				BlockPos rail = node.relative(d, r + 1);
				if (!columns.containsKey(BlockPos.asLong(rail.getX(), 0, rail.getZ())) && !others.contains(BlockPos.asLong(rail.getX(), 0, rail.getZ()))) {
					blocks.putIfAbsent(rail, style.rail().defaultBlockState());
				}
			}
			int run = 1;
			while (j - run >= 0 && bridge.test(j - run)) {
				run++;
			}
			if (run % PILLAR_EVERY == 0) {
				BlockPos p = node.below(2);
				for (int depth = 0; depth < PILLAR_DEPTH && !solid(level, p); depth++, p = p.below()) {
					blocks.put(p, style.pillar().defaultBlockState());
				}
			}
		}
		lamps(level, hall, road, style, route, from, to, r, columns, others, blocks);
		Map<BlockPos, net.minecraft.nbt.CompoundTag> data = new HashMap<>();
		if (to == route.size() && CaravanRoads.endsShort(hall, road)) {
			// a road to another village that stops short of halfway ends at a milestone (27.17)
			CaravanRoads.milestone(level, hall, road, route, r, p -> fits(level, p, 0, 4, columns, others, blocks), blocks, data);
		}
		if (blocks.isEmpty()) {
			return Optional.empty();
		}
		BoundingBox box = BoundingBox.encapsulatingPositions(blocks.keySet()).orElseThrow();
		BlockPos origin = new BlockPos(box.minX(), box.minY(), box.minZ());
		List<Blueprint.Entry> entries = new ArrayList<>();
		blocks.forEach((pos, state) -> entries.add(new Blueprint.Entry(pos.subtract(origin), state, data.get(pos))));
		ResourceLocation id = segmentId(hall, road, n);
		Blueprint blueprint = new Blueprint(id, new Vec3i(box.getXSpan(), box.getYSpan(), box.getZSpan()), entries);
		return Optional.of(new Segment(blueprint, new BlueprintData.Placement(Ids.of(level.dimension()), origin, Rotation.NONE, Mirror.NONE), box));
	}

	/** Node {@code j}'s stairs' facing when the way steps a block up onto it from the last or next node (null: no step). */
	@Nullable
	static Direction step(List<BlockPos> route, int j) {
		BlockPos node = route.get(j);
		if (j > 0 && route.get(j - 1).getY() == node.getY() - 1) {
			return facing(route.get(j - 1), node);
		}
		if (j + 1 < route.size() && route.get(j + 1).getY() == node.getY() - 1) {
			return facing(route.get(j + 1), node);
		}
		return null;
	}

	/** The way the road runs at node {@code j}: from the node before to the node after, along the main axis. */
	static Direction along(List<BlockPos> route, int j) {
		BlockPos a = route.get(Math.max(0, j - 1));
		BlockPos b = route.get(Math.min(route.size() - 1, j + 1));
		int dx = b.getX() - a.getX();
		int dz = b.getZ() - a.getZ();
		if (dx == 0 && dz == 0) {
			return Direction.EAST;
		}
		return Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
	}

	/** The Street Lamp in the road's style (27.16): the blueprint {@code street_lamp} styled like the road, as drawn if it isn't there. */
	static Optional<Blueprint> lampBlueprint(ServerLevel level, CityPlan.Road road) {
		ResourceLocation base = io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints.STREET_LAMP.id();
		Optional<Blueprint> styled = BlueprintLibrary.get(level, io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles.styled(base, road.style()));
		return styled.isPresent() ? styled : BlueprintLibrary.get(level, base);
	}

	/**
	 * The lamps of a segment (27.16). A street or avenue gets the Street Lamp (in its style, the style's lamp in it) beside it
	 * every {@link #LAMP_EVERY} nodes on alternate sides, and one before every crossing; a lane gets a lantern post (the
	 * style's post two high, its lamp on top) every {@link #POST_EVERY} nodes. A lamp stands only on clear natural ground
	 * off every road, with no door within 2 blocks: else it moves up to 3 nodes on, or is left out.
	 */
	private static void lamps(ServerLevel level, BlockPos hall, CityPlan.Road road, RoadStyles.Style style, List<BlockPos> route, int from, int to,
							  int r, Map<Long, int[]> columns, java.util.Set<Long> others, Map<BlockPos, BlockState> blocks) {
		if (road.lane()) {
			for (int j = POST_EVERY; j < route.size() - 1; j += POST_EVERY) {
				for (int jj = j; jj < Math.min(j + 4, route.size() - 1); jj++) {
					BlockPos node = route.get(jj);
					BlockPos post = node.relative(along(route, jj).getCounterClockWise(), r + 1);
					if (jj < from || jj >= to) {
						break;
					}
					if (fits(level, post, 0, 3, columns, others, blocks)) {
						blocks.put(post, style.lanternPost().defaultBlockState());
						blocks.put(post.above(), style.lanternPost().defaultBlockState());
						blocks.put(post.above(2), lamp(style, Blocks.LANTERN.defaultBlockState()));
						break;
					}
				}
			}
			return;
		}
		Optional<Blueprint> lamp = lampBlueprint(level, road);
		if (lamp.isEmpty()) {
			return;
		}
		List<int[]> spots = new ArrayList<>(); // {node, side: 0 left, 1 right}
		for (int j = LAMP_EVERY, k = 0; j < route.size(); j += LAMP_EVERY, k++) {
			spots.add(new int[] {j, k % 2});
		}
		for (int j = 1; j < route.size(); j++) {
			BlockPos c = route.get(j);
			BlockPos b = route.get(j - 1);
			if (others.contains(BlockPos.asLong(c.getX(), 0, c.getZ())) && !others.contains(BlockPos.asLong(b.getX(), 0, b.getZ())) && j >= 3) {
				spots.add(new int[] {j - 3, 0}); // the crossing's corner, before it
			}
		}
		for (int[] spot : spots) {
			if (spot[0] < from || spot[0] >= to) {
				continue;
			}
			for (int jj = spot[0]; jj < Math.min(spot[0] + 4, to); jj++) {
				BlockPos node = route.get(jj);
				Direction side = spot[1] == 0 ? along(route, jj).getCounterClockWise() : along(route, jj).getClockWise();
				BlockPos centre = node.relative(side, r + 2);
				if (fits(level, centre, 1, 6, columns, others, blocks)) {
					for (Blueprint.Entry e : lamp.get().blocks()) {
						if (!e.state().isAir()) {
							blocks.put(centre.offset(e.pos().getX() - 1, e.pos().getY(), e.pos().getZ() - 1), lamp(style, e.state()));
						}
					}
					break;
				}
			}
		}
	}

	/** The style's lamp in place of a lantern (hanging as the lantern was); any other block as it is. */
	private static BlockState lamp(RoadStyles.Style style, BlockState state) {
		if (!state.is(Blocks.LANTERN)) {
			return state;
		}
		BlockState out = style.lamp().defaultBlockState();
		if (out.hasProperty(net.minecraft.world.level.block.LanternBlock.HANGING)) {
			out = out.setValue(net.minecraft.world.level.block.LanternBlock.HANGING, state.getValue(net.minecraft.world.level.block.LanternBlock.HANGING));
		}
		return out;
	}

	/**
	 * Whether a lamp {@code radius} round {@code centre} (feet), {@code height} tall, fits: every column clear natural ground
	 * with only air or cover over it, off every road and anything this segment lays, and no door within 2 blocks.
	 */
	private static boolean fits(ServerLevel level, BlockPos centre, int radius, int height, Map<Long, int[]> columns, java.util.Set<Long> others,
								Map<BlockPos, BlockState> blocks) {
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				BlockPos p = centre.offset(dx, 0, dz);
				long key = BlockPos.asLong(p.getX(), 0, p.getZ());
				if (columns.containsKey(key) || others.contains(key) || !natural(level.getBlockState(p.below())) || blocks.containsKey(p.below())) {
					return false;
				}
				for (int dy = 0; dy < height; dy++) {
					BlockState s = level.getBlockState(p.above(dy));
					if (!(s.isAir() || cover(s)) || blocks.containsKey(p.above(dy))) {
						return false;
					}
				}
			}
		}
		for (BlockPos p : BlockPos.betweenClosed(centre.offset(-radius - 2, -1, -radius - 2), centre.offset(radius + 2, 2, radius + 2))) {
			if (level.getBlockState(p).is(BlockTags.DOORS)) {
				return false; // never in front of a door
			}
		}
		return true;
	}

	/** The street lamps in segment {@code id}'s blueprint (counted by the lamp's top block, which nothing else of a road lays). */
	static int lampsIn(ServerLevel level, CityPlan.Road road, ResourceLocation id) {
		Optional<Blueprint> lamp = lampBlueprint(level, road);
		Optional<Blueprint> segment = BlueprintLibrary.get(level, id);
		if (lamp.isEmpty() || segment.isEmpty() || road.lane()) {
			return 0;
		}
		BlockState top = null;
		int topY = -1;
		for (Blueprint.Entry e : lamp.get().blocks()) {
			if (!e.state().isAir() && e.pos().getY() > topY) {
				top = e.state();
				topY = e.pos().getY();
			}
		}
		if (top == null) {
			return 0;
		}
		BlockState mark = lamp(RoadStyles.forBlueprintStyle(road.style()), top);
		int n = 0;
		for (Blueprint.Entry e : segment.get().blocks()) {
			if (e.state().equals(mark)) {
				n++;
			}
		}
		return n;
	}

	/** The village's street lamps on its roads, for beauty (27.16: they count as Street Lamps). */
	public static int lamps(ServerLevel level, BlockPos hall) {
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return 0;
		}
		int n = 0;
		for (CityPlan.Road road : entity.plan().roads()) {
			n += road.lamps();
		}
		return n;
	}

	/** What the Steward's desk says of roads that stopped at a gap too wide to bridge. */
	public static List<Component> deskNotes(CityPlan plan) {
		List<Component> out = new ArrayList<>();
		for (CityPlan.Road road : plan.roads()) {
			if (road.gap() > 0) {
				out.add(Component.translatable("screen.aliveworkplace.desk.road_gap", road.gap() > GAP_LOOK ? GAP_LOOK + "+" : String.valueOf(road.gap()), BRIDGE_MAX));
			}
		}
		return out;
	}

	/** The way up from {@code low} to {@code high}: the stairs' facing (their high side), along the step. */
	private static Direction facing(BlockPos low, BlockPos high) {
		return Direction.getNearest(high.getX() - low.getX(), 0, high.getZ() - low.getZ());
	}

	/** Saves a segment's blueprint as a structure (as the Shape Planner does), unless one is saved under its id already. */
	public static boolean save(ServerLevel level, Blueprint blueprint) {
		StructureTemplateManager manager = level.getServer().getStructureManager();
		if (manager.get(blueprint.id()).isPresent()) {
			return true;
		}
		StructureTemplate template = manager.getOrCreate(blueprint.id());
		template.load(Lookup.lookup(BuiltInRegistries.BLOCK), BlueprintFiles.toStructureNbt(blueprint));
		template.setAuthor("Steward");
		if (!manager.save(blueprint.id())) {
			manager.remove(blueprint.id());
			return false;
		}
		return true;
	}

	/**
	 * A builder finished a road segment: it's marked built on its road (the plan, not the finished buildings' list), and
	 * the chronicle notes a road finished.
	 */
	public static void segmentBuilt(ServerLevel level, BuildSite site, @Nullable Villager builder) {
		String path = site.structure().getPath();
		for (BlockPos hall : hallsOf(level, site)) {
			if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) || !path.startsWith(FOLDER + hallKey(hall) + "/")) {
				continue;
			}
			CityPlan plan = entity.plan();
			for (int i = 0; i < plan.roads().size(); i++) {
				CityPlan.Road road = plan.roads().get(i);
				String prefix = FOLDER + hallKey(hall) + "/" + roadKey(road) + "_";
				if (!path.startsWith(prefix)) {
					continue;
				}
				int n;
				try {
					n = Integer.parseInt(path.substring(prefix.length()));
				} catch (NumberFormatException e) {
					continue;
				}
				CityPlan.Road done = road.withBuilt(n, road.built().contains(n) ? 0 : lampsIn(level, road, site.structure()));
				CityPlan next = plan.withRoad(i, done);
				if (next != null) {
					entity.setPlan(next);
				}
				if (done.caravan()) {
					CaravanRoads.segmentBuilt(level, hall, done); // the caravans' list and both chronicles (27.17)
				} else if (done.finished() && !road.finished() && !done.lane()) {
					Component who = builder != null ? builder.getDisplayName() : Component.translatable("steward.aliveworkplace.jobs.someone");
					Chronicle.record(level, hall, Chronicle.Kind.PLANS, Component.translatable("chronicle.aliveworkplace.road_built", who,
						done.route().size(), RoadStyles.title(RoadStyles.forBlueprintStyle(done.style()))));
				}
				return;
			}
		}
	}

	private static List<BlockPos> hallsOf(ServerLevel level, BuildSite site) {
		String[] parts = site.structure().getPath().substring(FOLDER.length()).split("/", 2)[0].split("_");
		if (parts.length == 3) {
			try {
				return List.of(new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
			} catch (NumberFormatException ignored) {
				// not one of ours
			}
		}
		return List.of();
	}

	// ---- lanes --------------------------------------------------------------------------------------------------

	/**
	 * A building finished (27.15): its door joins the nearest road of the nearest hall's plan with a lane (a road 1 wide
	 * in that road's style, approved, built like the rest). False when the village has no road with a way found within
	 * {@link #LANE_REACH} of the door, so the builder lays {@link Paths}' dirt path to the bell instead.
	 */
	public static boolean joinNearest(ServerLevel level, BuildSite site) {
		if (!ENABLED || site.isDeconstruction() || isSegment(site.structure())) {
			return false;
		}
		Optional<Blueprint> blueprint = BlueprintLibrary.get(level, site.structure());
		if (blueprint.isEmpty()) {
			return false;
		}
		BoundingBox box = BlueprintOutline.bounds(site.placement(), blueprint.get().size());
		Optional<BlockPos> hall = VillageHalls.nearest(level, box.getCenter());
		if (hall.isEmpty() || !(level.getBlockEntity(hall.get()) instanceof VillageHallBlockEntity entity)) {
			return false;
		}
		CityPlan plan = entity.plan();
		BlockPos door = Paths.doorstep(level, box);
		if (door == null) {
			return false;
		}
		BlockPos nearest = null;
		CityPlan.Road along = null;
		for (CityPlan.Road road : plan.roads()) {
			if (road.lane() || !road.routed()) {
				continue;
			}
			for (BlockPos offset : road.route()) {
				BlockPos p = offset.offset(hall.get());
				if (nearest == null || p.distSqr(door) < nearest.distSqr(door)) {
					nearest = p;
					along = road;
				}
			}
		}
		if (nearest == null || nearest.distSqr(door) > (double) LANE_REACH * LANE_REACH) {
			return false;
		}
		if (Math.abs(nearest.getX() - door.getX()) + Math.abs(nearest.getZ() - door.getZ()) <= 1) {
			return true; // the door opens onto the road
		}
		BlockPos a = door.subtract(hall.get());
		BlockPos b = nearest.subtract(hall.get());
		CityPlan.Road lane = new CityPlan.Road(List.of(new BlockPos(a.getX(), 0, a.getZ()), new BlockPos(b.getX(), 0, b.getZ())),
			CityPlan.Road.LANE, along.style(), true, List.of(), false, List.of(), true);
		if (!CityPlan.onGrid(lane.points().get(0)) || !CityPlan.onGrid(lane.points().get(1))) {
			return false;
		}
		CityPlan next = plan.addRoad(lane);
		if (next == null) {
			return false;
		}
		entity.setPlan(next);
		return true;
	}

	private Roads() {
	}
}
