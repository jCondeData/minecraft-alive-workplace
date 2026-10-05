package io.github.jcondedata.aliveworkplace.city;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import org.jetbrains.annotations.Nullable;

/**
 * A village's plan (ROADMAP 27.2), saved on its Village Hall: a grid of {@link #GRID}×{@link #GRID} cells centred on the
 * hall, each cell a square of the village map ({@link #cellSize}: 4×4 blocks at the default hall radius of 64, 8×8 at
 * 128); up to {@link #MAX_ZONES} zones, each a kind ({@link CityZones}), a name, a style and its cells; up to
 * {@link #MAX_ROADS} roads and one wall line (27.4); and the Steward's mode (27.8).
 *
 * <p>Everything is kept relative to the hall (cells, and road and wall points as offsets), so a hall broken and put down
 * again carries its plan centred on the new spot. Immutable: every change makes a new plan.
 */
public record CityPlan(List<Zone> zones, List<Road> roads, Optional<Wall> wall, Mode mode) {
	public static final int GRID = 32;
	public static final int MAX_ZONES = 16;
	public static final int MAX_ROADS = 24;
	public static final int MAX_ROAD_POINTS = 64;
	/** Most lanes from doors (27.15) a plan keeps, besides its {@link #MAX_ROADS} roads. */
	public static final int MAX_LANES = 64;
	public static final CityPlan EMPTY = new CityPlan(List.of(), List.of(), Optional.empty(), Mode.ASK);

	/** The Steward's mode (27.8). A new plan asks first. */
	public enum Mode implements StringRepresentable {
		ASK, RUN, REST;

		public static final Codec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** One zone: its kind (a {@link CityZones} id), name, style ("" as drawn), the renew switch (27.20) and its cells. */
	public record Zone(String kind, String name, String style, boolean renew, BitSet cells) {
		public static final Codec<Zone> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("kind").forGetter(Zone::kind),
			Codec.STRING.fieldOf("name").forGetter(Zone::name),
			Codec.STRING.optionalFieldOf("style", "").forGetter(Zone::style),
			Codec.BOOL.optionalFieldOf("renew", false).forGetter(Zone::renew),
			Codec.LONG_STREAM.xmap(s -> BitSet.valueOf(s.toArray()), b -> java.util.Arrays.stream(b.toLongArray()))
				.optionalFieldOf("cells", new BitSet()).forGetter(Zone::cells)
		).apply(i, Zone::new));

		public Zone {
			cells = (BitSet) cells.clone();
		}

		public boolean has(int cell) {
			return cell >= 0 && cells.get(cell);
		}

		Zone withCells(BitSet newCells) {
			return new Zone(kind, name, style, renew, newCells);
		}
	}

	/**
	 * A road (27.4): points as offsets from the hall (y unused), 1, 3 or 5 wide ({@link #LANE}, {@link #STREET},
	 * {@link #AVENUE}), in a style ("" as drawn); {@code approved} when a player drew it, so the Steward builds it without
	 * asking (27.15). Saves from before 27.4 load with roads unapproved.
	 *
	 * <p>27.15: once routed ({@code routed}), {@code route} holds the way found over the ground (feet positions, as offsets
	 * from the hall, y included), cut into segments of {@link Roads#SEGMENT} nodes; {@code built} lists the segments
	 * finished. {@code lane} marks a lane a finished building's door was joined to the roads by (it doesn't count
	 * towards {@link #MAX_ROADS}). Older saves load unrouted, nothing built. 27.16: {@code gap} is the width of the gap the
	 * road stopped at, too wide to bridge (0: none), and {@code lamps} the street lamps on its finished segments.
	 */
	public record Road(List<BlockPos> points, int width, String style, boolean approved, List<BlockPos> route, boolean routed,
					   List<Integer> built, boolean lane, int gap, int lamps) {
		public static final int LANE = 1;
		public static final int STREET = 3;
		public static final int AVENUE = 5;
		public static final Codec<Road> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.listOf().fieldOf("points").forGetter(Road::points),
			Codec.INT.optionalFieldOf("width", STREET).forGetter(Road::width),
			Codec.STRING.optionalFieldOf("style", "").forGetter(Road::style),
			Codec.BOOL.optionalFieldOf("approved", false).forGetter(Road::approved),
			BlockPos.CODEC.listOf().optionalFieldOf("route", List.of()).forGetter(Road::route),
			Codec.BOOL.optionalFieldOf("routed", false).forGetter(Road::routed),
			Codec.INT.listOf().optionalFieldOf("built", List.of()).forGetter(Road::built),
			Codec.BOOL.optionalFieldOf("lane", false).forGetter(Road::lane),
			Codec.INT.optionalFieldOf("gap", 0).forGetter(Road::gap),
			Codec.INT.optionalFieldOf("lamps", 0).forGetter(Road::lamps)
		).apply(i, Road::new));

		public Road {
			points = List.copyOf(points);
			route = List.copyOf(route);
			built = List.copyOf(built);
		}

		public Road(List<BlockPos> points, int width, String style, boolean approved, List<BlockPos> route, boolean routed,
					List<Integer> built, boolean lane) {
			this(points, width, style, approved, route, routed, built, lane, 0, 0);
		}

		public Road(List<BlockPos> points, int width, String style, boolean approved) {
			this(points, width, style, approved, List.of(), false, List.of(), false);
		}

		public Road(List<BlockPos> points, int width, String style) {
			this(points, width, style, false);
		}

		public static boolean validWidth(int width) {
			return width == LANE || width == STREET || width == AVENUE;
		}

		/** This road with its way found ({@code route} as offsets from the hall). */
		public Road withRoute(List<BlockPos> newRoute) {
			return withRoute(newRoute, 0);
		}

		/** This road with its way found, stopping at a gap {@code gapWidth} wide too wide to bridge (27.16; 0: none). */
		public Road withRoute(List<BlockPos> newRoute, int gapWidth) {
			return new Road(points, width, style, approved, newRoute, true, List.of(), lane, gapWidth, 0);
		}

		/** This road with segment {@code n} finished. */
		public Road withBuilt(int n) {
			if (built.contains(n)) {
				return this;
			}
			List<Integer> out = new ArrayList<>(built);
			out.add(n);
			return new Road(points, width, style, approved, route, routed, out, lane, gap, lamps);
		}

		/** This road with segment {@code n} finished and {@code more} street lamps standing on it (27.16). */
		public Road withBuilt(int n, int more) {
			Road done = withBuilt(n);
			return done == this ? this : new Road(points, width, style, approved, route, routed, done.built, lane, gap, lamps + more);
		}

		/** How many segments the route is cut into. */
		public int segments() {
			return (route.size() + Roads.SEGMENT - 1) / Roads.SEGMENT;
		}

		/** Routed, and every segment built. */
		public boolean finished() {
			return routed && built.size() >= segments();
		}
	}

	/**
	 * The wall line (27.4): points as offsets from the hall, open or closed. 27.18: {@code kit} is the wall kit
	 * ({@link WallKits}) the Steward proposed for it ("" none yet), {@code approved} that it may be built, and
	 * {@code own} that the Steward drew the line himself (round the zones) rather than a player. Older saves load with
	 * no kit, unapproved, a player's line.
	 */
	public record Wall(List<BlockPos> points, boolean closed, String kit, boolean approved, boolean own) {
		public static final Codec<Wall> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.listOf().fieldOf("points").forGetter(Wall::points),
			Codec.BOOL.optionalFieldOf("closed", true).forGetter(Wall::closed),
			Codec.STRING.optionalFieldOf("kit", "").forGetter(Wall::kit),
			Codec.BOOL.optionalFieldOf("approved", false).forGetter(Wall::approved),
			Codec.BOOL.optionalFieldOf("own", false).forGetter(Wall::own)
		).apply(i, Wall::new));

		public Wall {
			points = List.copyOf(points);
		}

		public Wall(List<BlockPos> points, boolean closed) {
			this(points, closed, "", false, false);
		}

		/** This line with the kit the Steward proposes for it (not approved yet). */
		public Wall proposed(String newKit, boolean stewardsOwn) {
			return new Wall(points, closed, newKit, false, stewardsOwn || own);
		}

		/** This line approved, to be built with {@code newKit}. */
		public Wall approvedWith(String newKit) {
			return new Wall(points, closed, newKit, true, own);
		}
	}

	public static final Codec<CityPlan> CODEC = RecordCodecBuilder.create(i -> i.group(
		Zone.CODEC.listOf().optionalFieldOf("zones", List.of()).forGetter(CityPlan::zones),
		Road.CODEC.listOf().optionalFieldOf("roads", List.of()).forGetter(CityPlan::roads),
		Wall.CODEC.optionalFieldOf("wall").forGetter(CityPlan::wall),
		Mode.CODEC.optionalFieldOf("mode", Mode.ASK).forGetter(CityPlan::mode)
	).apply(i, CityPlan::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, CityPlan> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

	public CityPlan {
		zones = List.copyOf(zones);
		roads = List.copyOf(roads);
	}

	/** Roads players drew (not lanes from doors, 27.15). */
	public int drawnRoads() {
		return (int) roads.stream().filter(r -> !r.lane()).count();
	}

	public boolean isEmpty() {
		return zones.isEmpty() && roads.isEmpty() && wall.isEmpty();
	}

	// --- the grid -------------------------------------------------------------------------------------------------

	/** Blocks along a cell's side for a hall radius: the grid covers the hall's whole area. */
	public static int cellSize(int radius) {
		return Math.max(1, radius * 2 / GRID);
	}

	public static int cellSize() {
		return cellSize(VillageHalls.RADIUS);
	}

	/** Blocks from the hall to the grid's edge, east-west and north-south. */
	public static int half() {
		return cellSize() * GRID / 2;
	}

	/** True if the offset {@code point} (from the hall) is on the grid: road and wall points must be. */
	public static boolean onGrid(BlockPos point) {
		int half = half();
		return point.getX() >= -half && point.getX() < half && point.getZ() >= -half && point.getZ() < half;
	}

	/** The cell {@code pos} is in, for the hall at {@code hall} (column x + row z × GRID), or -1 outside the grid. */
	public static int cellAt(BlockPos hall, BlockPos pos) {
		int size = cellSize();
		int half = size * GRID / 2;
		int cx = Math.floorDiv(pos.getX() - hall.getX() + half, size);
		int cz = Math.floorDiv(pos.getZ() - hall.getZ() + half, size);
		return cx < 0 || cz < 0 || cx >= GRID || cz >= GRID ? -1 : cx + cz * GRID;
	}

	/** The block at the middle of {@code cell} (at the hall's height). */
	public static BlockPos cellCentre(BlockPos hall, int cell) {
		int size = cellSize();
		int half = size * GRID / 2;
		int cx = cell % GRID;
		int cz = cell / GRID;
		return new BlockPos(hall.getX() - half + cx * size + size / 2, hall.getY(), hall.getZ() - half + cz * size + size / 2);
	}

	/** The zone {@code pos} is in, on this plan of the hall at {@code hall}. */
	public Optional<Zone> zoneAt(BlockPos hall, BlockPos pos) {
		int cell = cellAt(hall, pos);
		for (Zone zone : zones) {
			if (zone.has(cell)) {
				return Optional.of(zone);
			}
		}
		return Optional.empty();
	}

	/** The zone {@code pos} is in: the nearest hall's plan. */
	public static Optional<Zone> zoneAt(ServerLevel level, BlockPos pos) {
		Optional<BlockPos> hall = VillageHalls.nearest(level, pos);
		if (hall.isEmpty() || !(level.getBlockEntity(hall.get()) instanceof VillageHallBlockEntity entity)) {
			return Optional.empty();
		}
		return entity.plan().zoneAt(hall.get(), pos);
	}

	/** True if {@code cell}'s middle is nearer another Village Hall than this one: such a cell is that village's. */
	public static boolean nearerAnotherHall(ServerLevel level, BlockPos hall, int cell) {
		BlockPos centre = cellCentre(hall, cell);
		double own = centre.distSqr(hall);
		return level.getPoiManager().findAll(h -> h.is(ModVillagers.VILLAGE_HALL_POI), p -> !p.equals(hall) && p.distSqr(centre) < own,
			centre, (int) Math.ceil(Math.sqrt(own)) + 1, PoiManager.Occupancy.ANY).findAny().isPresent();
	}

	// --- changes ----------------------------------------------------------------------------------------------------

	/** A new zone (null if the plan has {@link #MAX_ZONES} already or the kind is unknown). */
	@Nullable
	public CityPlan addZone(String kind, String name, String style) {
		if (zones.size() >= MAX_ZONES || CityZones.get(kind).isEmpty()) {
			return null;
		}
		List<Zone> out = new ArrayList<>(zones);
		out.add(new Zone(kind, name, style, false, new BitSet()));
		return new CityPlan(out, roads, wall, mode);
	}

	@Nullable
	public CityPlan editZone(int index, String kind, String name, String style, boolean renew) {
		if (index < 0 || index >= zones.size() || CityZones.get(kind).isEmpty()) {
			return null;
		}
		List<Zone> out = new ArrayList<>(zones);
		out.set(index, new Zone(kind, name, style, renew, zones.get(index).cells()));
		return new CityPlan(out, roads, wall, mode);
	}

	@Nullable
	public CityPlan removeZone(int index) {
		if (index < 0 || index >= zones.size()) {
			return null;
		}
		List<Zone> out = new ArrayList<>(zones);
		out.remove(index);
		return new CityPlan(out, roads, wall, mode);
	}

	/** {@code cells} given to zone {@code index} (taken from any other zone they were in). */
	@Nullable
	public CityPlan paint(int index, BitSet cells) {
		if (index < 0 || index >= zones.size()) {
			return null;
		}
		List<Zone> out = new ArrayList<>();
		for (int i = 0; i < zones.size(); i++) {
			BitSet next = (BitSet) zones.get(i).cells().clone();
			if (i == index) {
				next.or(cells);
			} else {
				next.andNot(cells);
			}
			out.add(zones.get(i).withCells(next));
		}
		return new CityPlan(out, roads, wall, mode);
	}

	/** {@code cells} taken out of every zone. */
	public CityPlan erase(BitSet cells) {
		List<Zone> out = new ArrayList<>();
		for (Zone zone : zones) {
			BitSet next = (BitSet) zone.cells().clone();
			next.andNot(cells);
			out.add(zone.withCells(next));
		}
		return new CityPlan(out, roads, wall, mode);
	}

	@Nullable
	public CityPlan addRoad(Road road) {
		if (!road.lane() && drawnRoads() >= MAX_ROADS || road.lane() && roads.size() - drawnRoads() >= MAX_LANES || road.points().size() < 2 || road.points().size() > MAX_ROAD_POINTS || !Road.validWidth(road.width())) {
			return null;
		}
		List<Road> out = new ArrayList<>(roads);
		out.add(road);
		return new CityPlan(zones, out, wall, mode);
	}

	/** Road {@code index} replaced by {@code road} (27.15: its route found, a segment built); null if there's no such road. */
	@Nullable
	public CityPlan withRoad(int index, Road road) {
		if (index < 0 || index >= roads.size()) {
			return null;
		}
		List<Road> out = new ArrayList<>(roads);
		out.set(index, road);
		return new CityPlan(zones, out, wall, mode);
	}

	@Nullable
	public CityPlan removeRoad(int index) {
		if (index < 0 || index >= roads.size()) {
			return null;
		}
		List<Road> out = new ArrayList<>(roads);
		out.remove(index);
		return new CityPlan(zones, out, wall, mode);
	}

	@Nullable
	public CityPlan withWall(@Nullable Wall line) {
		if (line != null && (line.points().size() < 2 || line.points().size() > MAX_ROAD_POINTS)) {
			return null;
		}
		return new CityPlan(zones, roads, Optional.ofNullable(line), mode);
	}

	public CityPlan withMode(Mode newMode) {
		return new CityPlan(zones, roads, wall, newMode);
	}
}
