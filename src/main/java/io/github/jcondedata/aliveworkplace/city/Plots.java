package io.github.jcondedata.aliveworkplace.city;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.wood.Trees;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

/**
 * Finding a plot (ROADMAP 27.7): where a blueprint, in its zone's style, fits in a zone of the City Plan. Each spot is
 * tried with the blueprint's four turns, as drawn and mirrored, and is good only if:
 * <ul>
 * <li>its front (the template's z = 0 side) faces the nearest road on the plan, else the hall;</li>
 * <li>the footprint plus {@link #MARGIN} blocks lies in cells of that zone and off the plan's roads;</li>
 * <li>the ground under the footprint lies within {@link #MAX_STEP} blocks from highest to lowest (the builder levels
 * the rest, {@code BlueprintData.levelGround}), at most a tenth of it is water, none of it lava;</li>
 * <li>nothing in its box is anything but natural ground, plants and natural trees ({@link BuildPlan#isTerrain},
 * {@link Trees}, flowers, saplings, unplaced leaves and the tag {@link #CLEARABLE} for packs);</li>
 * <li>it is {@link #CLEAR} blocks clear of every build site and finished building of anyone's;</li>
 * <li>its centre is within {@code maxSiteDistance} of a builder's Blueprint Table (or Builder's Bench);</li>
 * <li>no build site or finished building of the same blueprint with the same mirroring stands within
 * {@link #SAME_RANGE} blocks (the spot is then tried mirrored, or with the request's next blueprint).</li>
 * </ul>
 * Spots are tried nearest the hall first, so villages grow compact. A {@link Search} looks at most
 * {@link #COLUMNS_PER_TICK} columns of ground per tick per hall; its result is kept until a zone, a road, a table or a
 * build in the plan's area changes.
 */
public final class Plots {
	/** The most columns of ground one hall's searches look at in a tick. */
	public static final int COLUMNS_PER_TICK = 64;
	/** The most spots (a turn and mirroring of a blueprint at one place) tried in a tick, beyond the column budget. */
	static final int TRIES_PER_TICK = 1024;
	/** Blocks round the footprint that must also be in the zone. */
	public static final int MARGIN = 2;
	/** The most the ground under the footprint may rise from its lowest to its highest column. */
	public static final int MAX_STEP = 4;
	/** Blocks between a plot and any build site or finished building. */
	public static final int CLEAR = 2;
	/** No build of the same blueprint with the same mirroring within this many blocks. */
	public static final int SAME_RANGE = 24;
	/** A result not asked for in this long (two days) is forgotten. */
	static final long FORGET_TICKS = 48000;
	/** Blocks packs mark as natural growth the Steward may build over (the builder clears them). */
	public static final TagKey<Block> CLEARABLE = TagKey.create(Registries.BLOCK, AliveWorkplace.id("steward_clearable"));

	/** Why a spot was turned down. */
	public enum Reason {
		/** The footprint plus the margin crosses the zone's edge. */
		ZONE_EDGE,
		/** The footprint plus the margin crosses a road on the plan. */
		ROAD,
		/** Its front doesn't face the nearest road (or the hall). */
		FACING,
		/** Its centre is more than maxSiteDistance from every Blueprint Table. */
		NO_TABLE,
		/** Within {@link #CLEAR} blocks of a build site or finished building. */
		BUILD,
		/** The same blueprint, mirrored the same way, within {@link #SAME_RANGE} blocks. */
		SAME_NEARBY,
		/** Not loaded. */
		UNLOADED,
		/** Something in its box that isn't natural: a player's block, a chest, a placed log. */
		BLOCKED,
		/** Lava under it. */
		LAVA,
		/** More than a tenth of it over water. */
		WATER,
		/** The ground under it rises more than {@link #MAX_STEP}. */
		SLOPE,
		/** The blueprint isn't in the library. */
		NO_BLUEPRINT,
		/**
		 * A building for the shore (a Ferry House, a Fisher's Hut) with no water within {@link #SHORE} blocks of its front;
		 * for one with a jetty, also when the jetty's end isn't over water.
		 */
		SHORE,
		/** The water under a jetty more than {@link #MAX_JETTY_DEPTH} deep: its posts wouldn't reach the bed. */
		DEEP,
		/** {@link StewardSafety} refuses it: Keep Clear, another village, or a section where a player built (27.19). */
		UNSAFE
	}

	/** How far in front of a shore building (see {@link #SHORE_BUILDINGS}) the water may be. */
	public static final int SHORE = 4;
	/** Buildings that go on the shore: water within {@link #SHORE} blocks of their front (ROADMAP 27.11). */
	public static final java.util.Set<ResourceLocation> SHORE_BUILDINGS = java.util.Set.of(
		ResourceLocation.fromNamespaceAndPath("aliveworkplace", "ferry_house"),
		ResourceLocation.fromNamespaceAndPath("aliveworkplace", "fishers_hut"),
		ResourceLocation.fromNamespaceAndPath("aliveworkplace", "fishers_hut_2"));
	/**
	 * Shore buildings with a jetty out in front (ROADMAP 27.14), and how many rows of the blueprint from its front (z = 0)
	 * the jetty takes: the building's own front is that far back, the water must come within {@link #SHORE} blocks of
	 * it and lie under the jetty's end, those rows may be over water (they don't count against the tenth), and the water
	 * under the jetty may be at most {@link #MAX_JETTY_DEPTH} deep.
	 */
	public static final Map<ResourceLocation, Integer> JETTIES = Map.of(
		ResourceLocation.fromNamespaceAndPath("aliveworkplace", "fishers_hut"), 5,
		ResourceLocation.fromNamespaceAndPath("aliveworkplace", "fishers_hut_2"), 5);
	/** The deepest water a jetty's posts stand in (the builder takes them down to the bed). */
	public static final int MAX_JETTY_DEPTH = 3;

	/** What the Steward wants a plot for: blueprints in order (the next is tried where the first can't go), and a zone kind. */
	public record Request(List<ResourceLocation> blueprints, String zoneKind, int skip, @Nullable BlockPos near) {
		public Request {
			blueprints = List.copyOf(blueprints);
			skip = Math.max(0, skip);
			near = near == null ? null : near.immutable();
		}

		public Request(List<ResourceLocation> blueprints, String zoneKind, int skip) {
			this(blueprints, zoneKind, skip, null);
		}

		public Request(List<ResourceLocation> blueprints, String zoneKind) {
			this(blueprints, zoneKind, 0, null);
		}

		/** The same, passing over the first {@code skip} spots that fit ("Another spot" on the Steward's desk, 27.8). */
		public Request skipping(int skip) {
			return new Request(blueprints, zoneKind, skip, near);
		}

		/** The same, nearest {@code near} first instead of nearest the hall (a street lamp by the darkest beds, 27.12). */
		public Request near(@Nullable BlockPos near) {
			return new Request(blueprints, zoneKind, skip, near);
		}
	}

	/** A plot: the blueprint in the zone's style, where and how it goes, the zone's name and which way its front faces. */
	public record Plot(ResourceLocation blueprint, BlueprintData.Placement placement, Vec3i size, String zone, Direction facing) {
		public BoundingBox box() {
			return BlueprintOutline.bounds(placement, size);
		}
	}

	/** One spot's verdict: a plot, or why not. */
	public record Verdict(Optional<Plot> plot, Optional<Reason> reason) {
		static Verdict ok(Plot plot) {
			return new Verdict(Optional.of(plot), Optional.empty());
		}

		static Verdict no(Reason reason) {
			return new Verdict(Optional.empty(), Optional.of(reason));
		}
	}

	/**
	 * A column of ground as read once: its kind, the ground's height, how many natural blocks lie under its top, and the
	 * lowest height of anything a player made above the ground ({@link Integer#MAX_VALUE} if nothing).
	 */
	record Column(Kind kind, int ground, int depth, int obstacle) {
	}

	enum Kind {
		LAND, WATER, LAVA, BLOCKED, UNLOADED
	}

	/** A build in the way: its box grown by {@link #CLEAR}, its blueprint without style, mirroring and centre. */
	record Build(BoundingBox near, ResourceLocation base, Mirror mirror, int cx, int cz) {
	}

	private record Key(BlockPos hall, Request request) {
	}

	private static final class Entry {
		final long signature;
		final Search search;
		long asked;

		Entry(long signature, Search search, long asked) {
			this.signature = signature;
			this.search = search;
			this.asked = asked;
		}
	}

	private static final Map<ServerLevel, Map<Key, Entry>> SEARCHES = new WeakHashMap<>();
	/** Columns each hall's searches read in the level's last tick. */
	private static final Map<ServerLevel, Map<BlockPos, Integer>> LAST_TICK = new WeakHashMap<>();

	public static void init() {
		Platform.get().onLevelTick(level -> {
			long cost = StewardCost.start(); // 27.22
			try {
				tick(level);
			} finally {
				StewardCost.stop(cost, "plot search");
			}
		});
	}

	/**
	 * The search for {@code request} at the hall at {@code hall}: the one already going or done if nothing it depends on
	 * changed, else a new one (worked on {@link #COLUMNS_PER_TICK} columns a tick). Empty if the hall is gone.
	 */
	public static Optional<Search> request(ServerLevel level, BlockPos hall, Request request) {
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return Optional.empty();
		}
		CityPlan plan = entity.plan();
		long signature = signature(level, hall, plan);
		Map<Key, Entry> searches = SEARCHES.computeIfAbsent(level, l -> new HashMap<>());
		Key key = new Key(hall.immutable(), request);
		Entry entry = searches.get(key);
		if (entry == null || entry.signature != signature) {
			entry = new Entry(signature, new Search(level, hall, plan, request), level.getGameTime());
			searches.put(key, entry);
		}
		entry.asked = level.getGameTime();
		return Optional.of(entry.search);
	}

	/** The result of {@code request} at the hall, if its search has finished: a plot, or empty if none fits. */
	public static Optional<Optional<Plot>> result(ServerLevel level, BlockPos hall, Request request) {
		Map<Key, Entry> searches = SEARCHES.get(level);
		Entry entry = searches == null ? null : searches.get(new Key(hall, request));
		return entry == null || !entry.search.done() ? Optional.empty() : Optional.of(entry.search.result());
	}

	/** Every hall's searches move on, each hall's sharing {@link #COLUMNS_PER_TICK} columns; old results are forgotten. */
	static void tick(ServerLevel level) {
		Map<Key, Entry> searches = SEARCHES.get(level);
		Map<BlockPos, Integer> budget = new HashMap<>();
		LAST_TICK.put(level, Map.of());
		if (searches == null || searches.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<Key, Entry>> it = searches.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Key, Entry> e = it.next();
			if (level.getGameTime() - e.getValue().asked > FORGET_TICKS) {
				it.remove();
				continue;
			}
			Search search = e.getValue().search;
			if (search.done()) {
				continue;
			}
			int left = budget.getOrDefault(e.getKey().hall(), COLUMNS_PER_TICK);
			if (left > 0) {
				budget.put(e.getKey().hall(), left - search.step(left));
			} else {
				search.waited();
			}
		}
		Map<BlockPos, Integer> used = new HashMap<>();
		budget.forEach((hall, left) -> used.put(hall, COLUMNS_PER_TICK - left));
		LAST_TICK.put(level, used);
	}

	/** Columns the searches of the hall at {@code hall} read in the level's last tick (never more than {@link #COLUMNS_PER_TICK}). */
	public static int columnsLastTick(ServerLevel level, BlockPos hall) {
		return LAST_TICK.getOrDefault(level, Map.of()).getOrDefault(hall, 0);
	}

	/**
	 * What a result depends on besides the ground: the plan's zones and roads, the Blueprint Tables near it and the builds
	 * in its area. Any change makes the next request search again.
	 */
	static long signature(ServerLevel level, BlockPos hall, CityPlan plan) {
		long sig = plan.zones().hashCode() * 31L + plan.roads().hashCode();
		int half = CityPlan.half();
		BoundingBox area = new BoundingBox(hall.getX() - half - CLEAR, level.getMinBuildHeight(), hall.getZ() - half - CLEAR,
			hall.getX() + half + CLEAR, level.getMaxBuildHeight(), hall.getZ() + half + CLEAR);
		long builds = 0;
		for (Build b : builds(level, area)) {
			builds += b.near().hashCode() * 17L + b.base().hashCode() * 7L + b.mirror().ordinal();
		}
		long tables = 0;
		for (BlockPos t : tables(level, hall)) {
			tables += t.asLong() * 13L;
		}
		return (sig * 31L + builds) * 31L + tables;
	}

	/** Builder job sites near the hall's plan: Blueprint Tables and Builder's Benches. */
	static List<BlockPos> tables(ServerLevel level, BlockPos hall) {
		int reach = CityPlan.half() * 3 / 2 + Builders.MAX_SITE_DISTANCE;
		return level.getPoiManager().findAll(h -> h.is(ModVillagers.BLUEPRINT_TABLE_POI) || h.is(ModVillagers.BUILDERS_BENCH_POI),
			p -> true, hall, reach, PoiManager.Occupancy.ANY).map(BlockPos::immutable).sorted(Comparator.comparingLong(BlockPos::asLong)).toList();
	}

	/** Build sites and finished buildings in this level whose box (grown by {@link #CLEAR}) meets {@code area}. */
	static List<Build> builds(ServerLevel level, BoundingBox area) {
		ResourceLocation dim = Ids.of(level.dimension());
		BuildSiteManager manager = BuildSiteManager.get(level);
		List<Build> out = new ArrayList<>();
		for (BuildSite site : manager.all()) {
			if (site.placement().dimension().equals(dim)) {
				add(level, out, area, site.structure(), site.placement());
			}
		}
		for (BuildSiteManager.Finished f : manager.finishedIn(level)) {
			add(level, out, area, f.structure(), f.placement());
		}
		return out;
	}

	private static void add(ServerLevel level, List<Build> out, BoundingBox area, ResourceLocation structure, BlueprintData.Placement placement) {
		BlueprintLibrary.get(level, structure).ifPresent(b -> {
			BoundingBox box = BlueprintOutline.bounds(placement, b.size());
			BoundingBox near = box.inflatedBy(CLEAR);
			if (near.intersects(area)) {
				out.add(new Build(near, BlueprintStyles.base(structure), placement.mirror(),
					(box.minX() + box.maxX()) / 2, (box.minZ() + box.maxZ()) / 2));
			}
		});
	}

	/** Whether the Steward's builders may clear {@code state} from a plot: natural growth (logs are checked as trees). */
	static boolean clearable(BlockState state) {
		if (state.isAir() || state.is(CLEARABLE) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)) {
			return true;
		}
		if (state.getBlock() instanceof LeavesBlock && state.hasProperty(LeavesBlock.PERSISTENT) && !state.getValue(LeavesBlock.PERSISTENT)) {
			return true; // leaves that grew; placed leaves are a player's
		}
		return BuildPlan.isTerrain(state) && state.canBeReplaced(); // grass, ferns, snow
	}

	/** Natural ground a plot can stand on (and the builder may dig into when levelling). */
	static boolean ground(BlockState state) {
		return BuildPlan.isTerrain(state) && !state.canBeReplaced();
	}

	/**
	 * One search: the spots of the request's zones, nearest the hall first, each tried with its turns and mirrorings until
	 * one fits. Columns read are kept, so neighbouring spots cost nothing for ground already seen.
	 */
	public static final class Search {
		private final ServerLevel level;
		private final BlockPos hall;
		private final Request request;
		private final ResourceLocation dim;
		/** Zone index per cell of the plan for the request's kind, -1 elsewhere. */
		private final int[] zoneOf;
		private final List<CityPlan.Zone> zones;
		/** Road columns of the plan, offsets from the grid's corner, row by row. */
		private final boolean[] road;
		private final List<double[]> segments = new ArrayList<>();
		private final List<BlockPos> tables;
		private final List<Build> builds;
		/** Candidate spots (x, z) nearest the hall first. */
		private final List<int[]> spots;
		private final Map<Long, Column> columns = new HashMap<>();
		private final Set<BlockPos> treeLogs = new HashSet<>();
		private final Set<BlockPos> notTreeLogs = new HashSet<>();
		private final Map<Reason, Integer> rejected = new EnumMap<>(Reason.class);
		private int spot;
		private int option;
		/** Spots that fit passed over so far ({@link Request#skip}). */
		private int passed;
		private boolean done;
		@Nullable
		private Plot result;
		private int ticks;
		private int columnsRead;
		private int maxColumnsInTick;
		private int budget;

		Search(ServerLevel level, BlockPos hall, CityPlan plan, Request request) {
			this.level = level;
			this.hall = hall.immutable();
			this.request = request;
			this.dim = Ids.of(level.dimension());
			int cells = CityPlan.GRID * CityPlan.GRID;
			zoneOf = new int[cells];
			Arrays.fill(zoneOf, -1);
			zones = plan.zones();
			for (int z = 0; z < zones.size(); z++) {
				CityPlan.Zone zone = zones.get(z);
				if (!zone.kind().equals(request.zoneKind())) {
					continue;
				}
				for (int c = zone.cells().nextSetBit(0); c >= 0 && c < cells; c = zone.cells().nextSetBit(c + 1)) {
					if (zoneOf[c] < 0) {
						zoneOf[c] = z;
					}
				}
			}
			int side = CityPlan.cellSize() * CityPlan.GRID;
			road = new boolean[side * side];
			for (CityPlan.Road r : plan.roads()) {
				for (int i = 0; i + 1 < r.points().size(); i++) {
					BlockPos a = r.points().get(i);
					BlockPos b = r.points().get(i + 1);
					segments.add(new double[]{hall.getX() + a.getX() + 0.5, hall.getZ() + a.getZ() + 0.5, hall.getX() + b.getX() + 0.5, hall.getZ() + b.getZ() + 0.5});
					paintRoad(a, b, r.width(), side);
				}
			}
			tables = tables(level, hall);
			int half = CityPlan.half();
			builds = builds(level, new BoundingBox(hall.getX() - half - SAME_RANGE, level.getMinBuildHeight(), hall.getZ() - half - SAME_RANGE,
				hall.getX() + half + SAME_RANGE, level.getMaxBuildHeight(), hall.getZ() + half + SAME_RANGE));
			spots = spots();
		}

		private void paintRoad(BlockPos a, BlockPos b, int width, int side) {
			int half = CityPlan.half();
			int steps = Math.max(Math.abs(b.getX() - a.getX()), Math.abs(b.getZ() - a.getZ())) * 2 + 1;
			int r = width / 2;
			for (int s = 0; s <= steps; s++) {
				double t = (double) s / steps;
				int x = (int) Math.round(a.getX() + (b.getX() - a.getX()) * t) + half;
				int z = (int) Math.round(a.getZ() + (b.getZ() - a.getZ()) * t) + half;
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						int px = x + dx;
						int pz = z + dz;
						if (px >= 0 && pz >= 0 && px < side && pz < side) {
							road[px + pz * side] = true;
						}
					}
				}
			}
		}

		/** Spots in the request's zones within reach of a table, nearest the hall first (a few per cell). */
		private List<int[]> spots() {
			List<int[]> out = new ArrayList<>();
			int size = CityPlan.cellSize();
			int step = Math.max(1, size / 2);
			int half = size * CityPlan.GRID / 2;
			long reach = (long) (Builders.MAX_SITE_DISTANCE + 1) * (Builders.MAX_SITE_DISTANCE + 1);
			for (int c = 0; c < zoneOf.length; c++) {
				if (zoneOf[c] < 0) {
					continue;
				}
				int baseX = hall.getX() - half + (c % CityPlan.GRID) * size;
				int baseZ = hall.getZ() - half + (c / CityPlan.GRID) * size;
				for (int ox = step / 2; ox < size; ox += step) {
					for (int oz = step / 2; oz < size; oz += step) {
						int x = baseX + ox;
						int z = baseZ + oz;
						for (BlockPos t : tables) {
							long dx = t.getX() - x;
							long dz = t.getZ() - z;
							if (dx * dx + dz * dz <= reach) {
								out.add(new int[]{x, z});
								break;
							}
						}
					}
				}
			}
			BlockPos anchor = request.near() != null ? request.near() : hall;
			out.sort(Comparator.<int[]>comparingLong(p -> sq(p[0] - anchor.getX()) + sq(p[1] - anchor.getZ()))
				.thenComparingInt(p -> p[0]).thenComparingInt(p -> p[1]));
			return out;
		}

		private static long sq(long v) {
			return v * v;
		}

		/** Options tried at each spot: blueprint × mirroring × turn. */
		private int options() {
			return request.blueprints().size() * 2 * 4;
		}

		/** Works on until a plot is found, the spots run out or {@code columnBudget} columns were read; returns the columns read. */
		int step(int columnBudget) {
			ticks++;
			budget = columnBudget;
			int tries = 0;
			while (!done && tries++ < TRIES_PER_TICK) {
				if (spot >= spots.size()) {
					done = true;
					break;
				}
				int[] at = spots.get(spot);
				int o = option;
				ResourceLocation base = request.blueprints().get(o / 8);
				Mirror mirror = (o / 4) % 2 == 0 ? Mirror.NONE : Mirror.FRONT_BACK;
				Rotation turn = Rotation.values()[o % 4];
				Verdict verdict = tryAt(base, at[0], at[1], turn, mirror);
				if (verdict == null) {
					break; // out of columns: the same option next tick
				}
				if (verdict.plot().isPresent() && passed < request.skip()) {
					passed++; // a spot that fits, passed over for another: on to the next spot
					option = 0;
					spot++;
					continue;
				}
				if (verdict.plot().isPresent()) {
					result = verdict.plot().get();
					done = true;
					break;
				}
				rejected.merge(verdict.reason().orElseThrow(), 1, Integer::sum);
				if (++option >= options()) {
					option = 0;
					spot++;
				}
			}
			int used = columnBudget - budget;
			maxColumnsInTick = Math.max(maxColumnsInTick, used);
			return used;
		}

		void waited() {
			ticks++;
		}

		/** Runs the search to its end, a tick's budget at a time; returns the ticks it took. For commands and tests. */
		public int finish() {
			while (!done) {
				step(COLUMNS_PER_TICK);
			}
			return ticks;
		}

		public boolean done() {
			return done;
		}

		public Optional<Plot> result() {
			return Optional.ofNullable(result);
		}

		/** Ticks worked on so far. */
		public int ticks() {
			return ticks;
		}

		/** The most columns read in any one tick. */
		public int maxColumnsInTick() {
			return maxColumnsInTick;
		}

		public int columnsRead() {
			return columnsRead;
		}

		/** How many tries were turned down, by reason. */
		public Map<Reason, Integer> rejected() {
			return Map.copyOf(rejected);
		}

		/**
		 * {@code base} (in the zone's style) centred at ({@code x}, {@code z}), turned and mirrored: a plot, or why not;
		 * null if it needs more columns than are left this tick.
		 */
		@Nullable
		Verdict tryAt(ResourceLocation base, int x, int z, Rotation turn, Mirror mirror) {
			int cell = CityPlan.cellAt(hall, new BlockPos(x, hall.getY(), z));
			if (cell < 0 || zoneOf[cell] < 0) {
				return Verdict.no(Reason.ZONE_EDGE);
			}
			CityPlan.Zone zone = zones.get(zoneOf[cell]);
			ResourceLocation id = BlueprintStyles.styled(base, zone.style());
			Optional<Blueprint> blueprint = BlueprintLibrary.get(level, id);
			if (blueprint.isEmpty()) {
				return Verdict.no(Reason.NO_BLUEPRINT);
			}
			Vec3i size = blueprint.get().size();
			BoundingBox at0 = BlueprintOutline.bounds(new BlueprintData.Placement(dim, BlockPos.ZERO, turn, mirror), size);
			int w = at0.getXSpan();
			int d = at0.getZSpan();
			int minX = x - w / 2;
			int minZ = z - d / 2;
			BlockPos origin = new BlockPos(minX - at0.minX(), hall.getY(), minZ - at0.minZ());
			BoundingBox box = new BoundingBox(minX, hall.getY(), minZ, minX + w - 1, hall.getY() + size.getY() - 1, minZ + d - 1);
			// the zone, and the plan's roads, round the footprint plus the margin
			int zi = zoneOf[cell];
			int[] xs = samples(box.minX() - MARGIN, box.maxX() + MARGIN, CityPlan.cellSize());
			int[] zs = samples(box.minZ() - MARGIN, box.maxZ() + MARGIN, CityPlan.cellSize());
			for (int px : xs) {
				for (int pz : zs) {
					if (!inZone(px, pz, zi)) {
						return Verdict.no(Reason.ZONE_EDGE);
					}
				}
			}
			if (onRoad(box.minX() - MARGIN, box.minZ() - MARGIN, box.maxX() + MARGIN, box.maxZ() + MARGIN)) {
				return Verdict.no(Reason.ROAD);
			}
			Direction front = turn.rotate(Direction.NORTH);
			if (!faces(box, front)) {
				return Verdict.no(Reason.FACING);
			}
			double cx = (box.minX() + box.maxX() + 1) / 2.0;
			double cz = (box.minZ() + box.maxZ() + 1) / 2.0;
			if (!nearTable(cx, cz)) {
				return Verdict.no(Reason.NO_TABLE);
			}
			ResourceLocation plain = BlueprintStyles.base(id);
			for (Build b : builds) {
				if (b.near().minX() <= box.maxX() && b.near().maxX() >= box.minX() && b.near().minZ() <= box.maxZ() && b.near().maxZ() >= box.minZ()) {
					return Verdict.no(Reason.BUILD);
				}
			}
			for (Build b : builds) {
				if (b.base().equals(plain) && b.mirror() == mirror && sq((long) (b.cx() - cx)) + sq((long) (b.cz() - cz)) <= (long) SAME_RANGE * SAME_RANGE) {
					return Verdict.no(Reason.SAME_NEARBY);
				}
			}
			// the ground: read every column under the footprint (stopping at the first that rules it out)
			int water = 0;
			int lowest = Integer.MAX_VALUE;
			int highest = Integer.MIN_VALUE;
			Integer jetty = JETTIES.get(plain);
			// a jetty's rows may be over water: the tenth is of the rest
			int area = w * d - (jetty == null ? 0 : jetty * (front.getAxis() == Direction.Axis.Z ? w : d));
			int[] heights = new int[w * d];
			int n = 0;
			for (int px = box.minX(); px <= box.maxX(); px++) {
				for (int pz = box.minZ(); pz <= box.maxZ(); pz++) {
					Column col = column(px, pz);
					if (col == null) {
						return null;
					}
					switch (col.kind()) {
						case UNLOADED -> {
							return Verdict.no(Reason.UNLOADED);
						}
						case BLOCKED -> {
							return Verdict.no(Reason.BLOCKED);
						}
						case LAVA -> {
							return Verdict.no(Reason.LAVA);
						}
						case WATER -> {
							if (!(jetty != null && inJetty(box, front, jetty, px, pz)) && ++water * 10 > area) {
								return Verdict.no(Reason.WATER);
							}
						}
						default -> {
						}
					}
					if (col.obstacle() <= col.ground() - MAX_STEP + size.getY()) {
						return Verdict.no(Reason.BLOCKED); // in the box wherever its floor ends up
					}
					lowest = Math.min(lowest, col.ground());
					highest = Math.max(highest, col.ground());
					if (highest - lowest > MAX_STEP) {
						return Verdict.no(Reason.SLOPE);
					}
					heights[n++] = col.ground();
				}
			}
			Arrays.sort(heights, 0, n);
			int floor = heights[n / 2];
			// columns above the floor are dug down to it: only natural ground on the way
			for (int px = box.minX(); px <= box.maxX(); px++) {
				for (int pz = box.minZ(); pz <= box.maxZ(); pz++) {
					Column col = columns.get(BlockPos.asLong(px, 0, pz));
					if (col.kind() == Kind.LAND && col.ground() > floor && col.depth() < col.ground() - floor || col.obstacle() <= floor + size.getY()) {
						return Verdict.no(Reason.BLOCKED);
					}
				}
			}
			if (jetty != null) {
				Reason why = jettyShore(blueprint.get(), origin, turn, mirror, jetty);
				if (why != null) {
					return Verdict.no(why);
				}
			} else if (SHORE_BUILDINGS.contains(plain)) {
				Boolean shore = shore(box, front);
				if (shore == null) {
					return null;
				}
				if (!shore) {
					return Verdict.no(Reason.SHORE);
				}
			}
			BlueprintData.Placement placement = new BlueprintData.Placement(dim, origin.atY(floor + 1), turn, mirror);
			BoundingBox built = new BoundingBox(box.minX(), floor + 1, box.minZ(), box.maxX(), floor + size.getY(), box.maxZ());
			if (!StewardSafety.allowed(level, hall, built, false)) {
				return Verdict.no(Reason.UNSAFE); // 27.19
			}
			return Verdict.ok(new Plot(id, placement, size, zone.name(), front));
		}

		/** Whether (x, z) is in the {@code rows} rows of {@code box} on its {@code front} side: under a jetty. */
		private static boolean inJetty(BoundingBox box, Direction front, int rows, int x, int z) {
			return switch (front) {
				case NORTH -> z < box.minZ() + rows;
				case SOUTH -> z > box.maxZ() - rows;
				case WEST -> x < box.minX() + rows;
				default -> x > box.maxX() - rows;
			};
		}

		/**
		 * The shore for a building with a jetty in its first {@code rows} rows (its columns are those its blueprint builds
		 * on in its bottom layer there), placed at {@code origin}: null if it fits, else why not. The jetty's end must be
		 * over water, water must come within {@link #SHORE} blocks of the building's own front (the jetty's root), and no
		 * water under the jetty may be more than {@link #MAX_JETTY_DEPTH} deep. The columns were all read for the
		 * footprint already.
		 */
		@Nullable
		private Reason jettyShore(Blueprint blueprint, BlockPos origin, Rotation turn, Mirror mirror, int rows) {
			boolean tip = false;
			boolean near = false;
			for (Blueprint.Entry e : blueprint.blocks()) {
				BlockPos p = e.pos();
				if (p.getY() != 0 || p.getZ() >= rows || e.state().isAir()) {
					continue;
				}
				BlockPos at = origin.offset(net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.transform(p, mirror, turn, BlockPos.ZERO));
				Column col = columns.get(BlockPos.asLong(at.getX(), 0, at.getZ()));
				boolean wet = col != null && col.kind() == Kind.WATER;
				if (p.getZ() == 0) {
					if (!wet) {
						return Reason.SHORE; // the jetty's end on dry land
					}
					tip = true;
				}
				if (wet && p.getZ() >= rows - SHORE) {
					near = true;
				}
				if (wet && waterDepth(at.getX(), col.ground(), at.getZ()) > MAX_JETTY_DEPTH) {
					return Reason.DEEP;
				}
			}
			return tip && near ? null : Reason.SHORE;
		}

		/** How deep the water is from its surface at {@code top} down (counting at most one past {@link #MAX_JETTY_DEPTH}). */
		private int waterDepth(int x, int top, int z) {
			BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, top, z);
			int depth = 0;
			while (depth <= MAX_JETTY_DEPTH && p.getY() >= level.getMinBuildHeight() && level.getFluidState(p).is(FluidTags.WATER)) {
				depth++;
				p.move(Direction.DOWN);
			}
			return depth;
		}

		/** Whether there's water in the {@link #SHORE} rows in front of {@code box}; null when a column isn't read yet. */
		@Nullable
		private Boolean shore(BoundingBox box, Direction front) {
			for (int step = 1; step <= SHORE; step++) {
				int fx = front.getStepX();
				int fz = front.getStepZ();
				int ax = fx != 0 ? (fx > 0 ? box.maxX() : box.minX()) + fx * step : 0;
				int az = fz != 0 ? (fz > 0 ? box.maxZ() : box.minZ()) + fz * step : 0;
				int from = fx != 0 ? box.minZ() : box.minX();
				int to = fx != 0 ? box.maxZ() : box.maxX();
				for (int i = from; i <= to; i++) {
					Column col = fx != 0 ? column(ax, i) : column(i, az);
					if (col == null) {
						return null;
					}
					if (col.kind() == Kind.WATER) {
						return true;
					}
				}
			}
			return false;
		}

		/** {@code from} to {@code to} every {@code step} blocks and {@code to} itself: one block in every cell the span crosses. */
		private static int[] samples(int from, int to, int step) {
			int n = (to - from) / step + 2;
			int[] out = new int[n];
			for (int i = 0; i < n - 1; i++) {
				out[i] = from + i * step;
			}
			out[n - 1] = to;
			return out;
		}

		private boolean inZone(int x, int z, int zone) {
			int cell = CityPlan.cellAt(hall, new BlockPos(x, hall.getY(), z));
			return cell >= 0 && zoneOf[cell] == zone;
		}

		private boolean onRoad(int minX, int minZ, int maxX, int maxZ) {
			if (segments.isEmpty()) {
				return false;
			}
			int half = CityPlan.half();
			int side = half * 2;
			for (int x = minX; x <= maxX; x++) {
				for (int z = minZ; z <= maxZ; z++) {
					int px = x - hall.getX() + half;
					int pz = z - hall.getZ() + half;
					if (px >= 0 && pz >= 0 && px < side && pz < side && road[px + pz * side]) {
						return true;
					}
				}
			}
			return false;
		}

		/** Whether the front looks towards the nearest road on the plan (or the hall): it's the way there, or one of two on a diagonal. */
		private boolean faces(BoundingBox box, Direction front) {
			double cx = (box.minX() + box.maxX() + 1) / 2.0;
			double cz = (box.minZ() + box.maxZ() + 1) / 2.0;
			double tx = hall.getX() + 0.5;
			double tz = hall.getZ() + 0.5;
			double best = Double.MAX_VALUE;
			for (double[] s : segments) {
				double sx = s[2] - s[0];
				double sz = s[3] - s[1];
				double len = sx * sx + sz * sz;
				double t = len == 0 ? 0 : Math.max(0, Math.min(1, ((cx - s[0]) * sx + (cz - s[1]) * sz) / len));
				double px = s[0] + sx * t;
				double pz = s[1] + sz * t;
				double dist = (px - cx) * (px - cx) + (pz - cz) * (pz - cz);
				if (dist < best) {
					best = dist;
					tx = px;
					tz = pz;
				}
			}
			double dx = tx - cx;
			double dz = tz - cz;
			if (Math.abs(dx) < 0.5 && Math.abs(dz) < 0.5) {
				return true;
			}
			boolean ok = false;
			if (Math.abs(dx) >= Math.abs(dz)) {
				ok = front == (dx > 0 ? Direction.EAST : Direction.WEST);
			}
			if (Math.abs(dz) >= Math.abs(dx)) {
				ok |= front == (dz > 0 ? Direction.SOUTH : Direction.NORTH);
			}
			return ok;
		}

		private boolean nearTable(double x, double z) {
			double reach = (double) Builders.MAX_SITE_DISTANCE * Builders.MAX_SITE_DISTANCE;
			for (BlockPos t : tables) {
				double dx = t.getX() + 0.5 - x;
				double dz = t.getZ() + 0.5 - z;
				if (dx * dx + dz * dz <= reach) {
					return true;
				}
			}
			return false;
		}

		/** The column at ({@code x}, {@code z}), read now if it wasn't and the tick's budget allows; null if it doesn't. */
		@Nullable
		private Column column(int x, int z) {
			long key = BlockPos.asLong(x, 0, z);
			Column col = columns.get(key);
			if (col != null) {
				return col;
			}
			if (budget <= 0) {
				return null;
			}
			budget--;
			columnsRead++;
			col = read(x, z);
			columns.put(key, col);
			return col;
		}

		/**
		 * Reads one column from its top down: through air and natural growth (flowers, unplaced leaves, logs of natural
		 * trees, the {@link #CLEARABLE} tag) to natural ground, water or lava. Anything else on the way is a player's: the
		 * lowest such block is kept, and the plot is refused if it's in its box. Under natural ground, counts how deep it
		 * stays natural (up to {@link #MAX_STEP}).
		 */
		Column read(int x, int z) {
			BlockPos probe = new BlockPos(x, hall.getY(), z);
			if (!level.isLoaded(probe)) {
				return new Column(Kind.UNLOADED, 0, 0, Integer.MAX_VALUE);
			}
			int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
			int bottom = Math.max(level.getMinBuildHeight(), hall.getY() - 48);
			int obstacle = Integer.MAX_VALUE;
			BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, top, z);
			for (int y = top; y >= bottom; y--) {
				p.setY(y);
				BlockState state = level.getBlockState(p);
				FluidState fluid = state.getFluidState();
				if (!fluid.isEmpty()) {
					return new Column(fluid.is(FluidTags.LAVA) ? Kind.LAVA : Kind.WATER, y, 0, obstacle);
				}
				if (ground(state)) {
					int depth = 1;
					while (depth <= MAX_STEP && y - depth >= level.getMinBuildHeight() && ground(level.getBlockState(p.setY(y - depth)))) {
						depth++;
					}
					return new Column(Kind.LAND, y, depth, obstacle);
				}
				if (!clearable(state) && !(Trees.isLog(state) && naturalTree(p.immutable()))) {
					obstacle = y;
				}
			}
			return new Column(Kind.BLOCKED, bottom, 0, obstacle); // no ground: the void
		}

		private boolean naturalTree(BlockPos log) {
			if (treeLogs.contains(log)) {
				return true;
			}
			if (notTreeLogs.contains(log)) {
				return false;
			}
			Optional<Trees.Tree> tree = Trees.treeAt(level, log);
			if (tree.isPresent()) {
				treeLogs.addAll(tree.get().logs());
				return true;
			}
			notTreeLogs.add(log);
			return false;
		}
	}

	/**
	 * One spot checked at once, for commands and tests: {@code blueprint} in the zone's style centred at {@code centre}, turned
	 * and mirrored, for the plan of the hall at {@code hall} and a zone of {@code zoneKind}.
	 */
	public static Verdict check(ServerLevel level, BlockPos hall, CityPlan plan, String zoneKind, ResourceLocation blueprint,
								BlockPos centre, Rotation turn, Mirror mirror) {
		Search search = new Search(level, hall, plan, new Request(List.of(blueprint), zoneKind));
		search.budget = Integer.MAX_VALUE;
		return search.tryAt(blueprint, centre.getX(), centre.getZ(), turn, mirror);
	}

	/** A search for {@code request} on {@code plan}, not kept: for commands and tests. */
	public static Search search(ServerLevel level, BlockPos hall, CityPlan plan, Request request) {
		return new Search(level, hall, plan, request);
	}

	private Plots() {
	}
}
