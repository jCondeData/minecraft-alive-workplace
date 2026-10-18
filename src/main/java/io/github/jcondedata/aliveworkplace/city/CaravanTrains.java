package io.github.jcondedata.aliveworkplace.city;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * Trains link villages (ROADMAP 39.7, design note M39, "Trains"). With Create installed and the {@code villageTrains}
 * switch on (its expansion, M39, open):
 * <ul>
 * <li>A village has a <b>station</b> when a Create {@code track_station} stands within its hall's reach (the Train
 * Station build has one beside its track). Each hall's round notes its stations on the caravans' list
 * ({@link Caravans.Data#stations}), so the other village knows them where the world isn't loaded.</li>
 * <li>When two villages with a caravan route both have a station and a village's half of the road between them is
 * built ({@link CaravanRoads}), an idle builder of that village lays Create track down the middle of its half: one
 * build site of straight pieces ({@code xo}, {@code zo}, or a diagonal), node by node, skipping the steps where the road
 * climbs a block. When it's done the half is marked on the list ({@link Caravans.Data#tracked}).</li>
 * <li>A caravan between two villages whose road is finished, tracked both halves and with a station at either end goes
 * by train ({@link Caravans.Shipment#byTrain}): a third of the trip on the road (never less than {@link #TRAIN_MIN}
 * ticks, never longer than walking), never lost on the way, seen leaving and arriving as steam and a bell at the
 * stations. Its goods, sale and arrival are a caravan's as before. A station gone at the end when the train comes in:
 * the caravan gets off and walks the rest ({@code Caravans.round}).</li>
 * </ul>
 * The train is the trip, not a Create train assembled from bogeys (design note M39, question 1): no Create class is
 * used, everything goes by block id. Without Create, with the switch off, or with caravan roads off, nothing is laid
 * and caravans walk as before.
 */
public final class CaravanTrains {
	/** Config {@code villageTrains} (and M39 open). */
	public static volatile boolean ENABLED = true;
	/** Create's mod id. */
	public static final String CREATE = "create";
	/** Create's station block. */
	public static final ResourceLocation STATION = ResourceLocation.fromNamespaceAndPath(CREATE, "track_station");
	/** Create's track block (and item). */
	public static final ResourceLocation TRACK = ResourceLocation.fromNamespaceAndPath(CREATE, "track");
	/** The shortest train trip, in ticks. */
	public static long TRAIN_MIN = 400;
	/** A track site's id part, after the hall's folder of road segments. */
	static final String TRACK_PART = "track_";
	/** How often (ticks) a hall looks for track to lay. */
	static final int CHECK_EVERY = 100;
	/** A village's stations noted on the list: at most this many. */
	static final int MAX_STATIONS = 4;

	private CaravanTrains() {
	}

	/** Whether trains run at all: the switch on and Create installed. */
	public static boolean live() {
		return ENABLED && Platform.get().isModLoaded(CREATE);
	}

	/** Whether the Train Station build may be listed: any other build, or the station while trains run. */
	public static boolean listed(ResourceLocation id) {
		return !BlueprintStyles.base(id).equals(StarterBlueprints.TRAIN_STATION.id()) || live();
	}

	/** Whether {@code state} is Create's station. */
	public static boolean isStation(BlockState state) {
		return STATION.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
	}

	/** Whether {@code state} is Create's track. */
	public static boolean isTrack(BlockState state) {
		return TRACK.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
	}

	/** The stations within the hall's reach, in the world loaded now (nearest first, at most {@link #MAX_STATIONS}). */
	public static List<BlockPos> find(ServerLevel level, BlockPos hall) {
		int r = VillageHalls.RADIUS;
		List<BlockPos> out = new ArrayList<>();
		for (int cx = (hall.getX() - r) >> 4; cx <= (hall.getX() + r) >> 4; cx++) {
			for (int cz = (hall.getZ() - r) >> 4; cz <= (hall.getZ() + r) >> 4; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				for (BlockPos p : chunk.getBlockEntities().keySet()) {
					if (Math.abs(p.getX() - hall.getX()) <= r && Math.abs(p.getZ() - hall.getZ()) <= r && Math.abs(p.getY() - hall.getY()) <= r
						&& isStation(level.getBlockState(p))) {
						out.add(p.immutable());
					}
				}
			}
		}
		out.sort(Comparator.comparingDouble(p -> p.distSqr(hall)));
		return out.size() > MAX_STATIONS ? List.copyOf(out.subList(0, MAX_STATIONS)) : List.copyOf(out);
	}

	/** The hall's round (with trains running): its stations noted on the list. */
	public static void noteStations(ServerLevel level, BlockPos hall, Caravans.Data data) {
		if (live() && level.isLoaded(hall)) {
			data.setStations(hall, find(level, hall));
		}
	}

	/** Whether a caravan from {@code from} to {@code to} goes by train now. */
	public static boolean rides(Caravans.Data data, BlockPos from, BlockPos to) {
		return live() && CaravanRoads.ENABLED && !data.stations(from).isEmpty() && !data.stations(to).isEmpty()
			&& data.roadFinished(from, to) && data.tracked(from, to) && data.tracked(to, from);
	}

	/** A train trip in place of a walk of {@code walking} ticks: a third of it, at least {@link #TRAIN_MIN}, never longer. */
	public static long trainTrip(long walking) {
		return Math.min(walking, Math.max(TRAIN_MIN, walking / 3));
	}

	/** A train leaves or comes in at the village's station: steam over it and a bell, for whoever is near. */
	public static void whistle(ServerLevel level, BlockPos hall, Caravans.Data data) {
		List<BlockPos> stations = data.stations(hall);
		BlockPos at = stations.isEmpty() ? hall : stations.get(0);
		if (!level.isLoaded(at)) {
			return;
		}
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.getX() + 0.5, at.getY() + 2.5, at.getZ() + 0.5, 8, 0.4, 0.3, 0.4, 0.02);
		level.sendParticles(ParticleTypes.CLOUD, at.getX() + 0.5, at.getY() + 1.5, at.getZ() + 0.5, 10, 0.8, 0.3, 0.8, 0.02);
		level.playSound(null, at, SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 1f, 1.4f);
	}

	// ---- laying track ---------------------------------------------------------------------------------------------

	/** From the hall's road tick: every {@link #CHECK_EVERY} ticks, a track site opens where one is due. */
	static void tick(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		if (Math.floorMod(level.getGameTime() + hall.hashCode(), CHECK_EVERY) == 0) {
			open(level, hall, entity);
		}
	}

	/** The village's half of the road to {@code other} wants track: both have a station, the half is built, none laid yet. */
	public static boolean due(ServerLevel level, BlockPos hall, BlockPos other, CityPlan plan, Caravans.Data data) {
		if (!live() || !CaravanRoads.ENABLED || data.tracked(hall, other) || data.stations(hall).isEmpty() || data.stations(other).isEmpty()) {
			return false;
		}
		Caravans.Half half = data.half(hall, other);
		CityPlan.Road road = CaravanRoads.roadTo(plan, hall, other);
		return half != null && half.finished() && road != null && road.routed() && road.finished() && !road.route().isEmpty();
	}

	/**
	 * Opens a track site for the first half that's due, with an idle builder of the village (any of them: the road goes
	 * out past the benches' reach). Returns the site, or null: nothing due, a track site already open, or nobody free.
	 */
	@Nullable
	public static BuildSite open(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		if (!live()) {
			return null;
		}
		Caravans.Data data = Caravans.Data.get(level);
		for (BuildSite site : Roads.openSegments(level, hall)) {
			if (isTrackSite(site.structure())) {
				return null; // one at a time
			}
		}
		for (BlockPos other : data.partners(hall)) {
			if (!due(level, hall, other, entity.plan(), data)) {
				continue;
			}
			CityPlan.Road road = CaravanRoads.roadTo(entity.plan(), hall, other);
			Optional<Roads.Segment> track = blueprint(level, hall, other, road);
			if (track.isEmpty()) {
				continue;
			}
			Villager builder = Roads.builderFor(level, hall, track.get().box(), true);
			if (builder == null || !Roads.save(level, track.get().blueprint())) {
				return null;
			}
			UUID owner = entity.owner() != null ? entity.owner() : builder.getUUID();
			String ownerName = entity.owner() != null ? entity.ownerName() : "";
			BuildSite site = Builders.start(level, builder, owner, ownerName, track.get().blueprint().id(), track.get().placement());
			site.setLevelGround(false); // laid on the road as it is
			return site;
		}
		return null;
	}

	/** Whether {@code id} is a track site's blueprint. */
	public static boolean isTrackSite(ResourceLocation id) {
		return Roads.isSegment(id) && id.getPath().substring(Roads.FOLDER.length()).contains("/" + TRACK_PART);
	}

	/** The track site's blueprint id for the hall's half of the road to {@code other} (the road's way in it, so a new way is a new blueprint). */
	public static ResourceLocation trackId(BlockPos hall, BlockPos other, CityPlan.Road road) {
		return AliveWorkplace.id(Roads.FOLDER + Roads.hallKey(hall) + "/" + TRACK_PART + Roads.hallKey(other).replace('-', 'm') + "_" + Roads.roadKey(road));
	}

	/**
	 * The track for the hall's half of the road to {@code other}: Create track on every node of its way (feet height, the
	 * road's middle), straight along the way there ({@code xo}, {@code zo}) or diagonal ({@code pd}, {@code nd}), but not
	 * where the road steps up or down a block, nor where something stands on the road (the hall it starts from). Empty without Create's track block, or with no node to lay on.
	 */
	public static Optional<Roads.Segment> blueprint(ServerLevel level, BlockPos hall, BlockPos other, CityPlan.Road road) {
		if (!BuiltInRegistries.BLOCK.containsKey(TRACK) || road == null || road.route().isEmpty()) {
			return Optional.empty();
		}
		BlockState track = Lookup.value(BuiltInRegistries.BLOCK, TRACK).defaultBlockState();
		List<BlockPos> route = road.route().stream().map(p -> p.offset(hall)).toList();
		Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
		for (int j = 0; j < route.size(); j++) {
			BlockPos node = route.get(j);
			BlockPos before = route.get(Math.max(0, j - 1));
			BlockPos after = route.get(Math.min(route.size() - 1, j + 1));
			if (before.getY() != node.getY() || after.getY() != node.getY()) {
				continue; // the road steps here: stairs, no flat track
			}
			BlockState here = level.getBlockState(node);
			if (!here.isAir() && !here.canBeReplaced() && !isTrack(here)) {
				continue; // something stands on the road there (the hall it starts from, a lamp): the track goes round it
			}
			int dx = after.getX() - before.getX();
			int dz = after.getZ() - before.getZ();
			String shape = Math.abs(dx) > 2 * Math.abs(dz) ? "xo" : Math.abs(dz) > 2 * Math.abs(dx) ? "zo" : dx * dz > 0 ? "pd" : "nd";
			blocks.put(node, with(track, "shape", shape));
		}
		if (blocks.isEmpty()) {
			return Optional.empty();
		}
		BoundingBox box = BoundingBox.encapsulatingPositions(blocks.keySet()).orElseThrow();
		BlockPos origin = new BlockPos(box.minX(), box.minY(), box.minZ());
		List<Blueprint.Entry> entries = new ArrayList<>();
		blocks.forEach((pos, state) -> entries.add(new Blueprint.Entry(pos.subtract(origin), state, null)));
		ResourceLocation id = trackId(hall, other, road);
		Blueprint blueprint = new Blueprint(id, new Vec3i(box.getXSpan(), box.getYSpan(), box.getZSpan()), entries);
		return Optional.of(new Roads.Segment(blueprint, new BlueprintData.Placement(Ids.of(level.dimension()), origin, Rotation.NONE, Mirror.NONE), box));
	}

	private static BlockState with(BlockState state, String name, String value) {
		Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
		return property == null ? state : set(state, property, value);
	}

	private static <T extends Comparable<T>> BlockState set(BlockState state, Property<T> property, String value) {
		return property.getValue(value).map(v -> state.setValue(property, v)).orElse(state);
	}

	/**
	 * A builder finished a track site ({@code Roads.segmentBuilt}): the half is marked tracked on the list, the chronicle
	 * says so, and when the other half is tracked too, both chronicles say the railway is open. False if it's no track site.
	 */
	static boolean trackBuilt(ServerLevel level, BuildSite site, @Nullable Villager builder) {
		if (!isTrackSite(site.structure())) {
			return false;
		}
		for (BlockPos hall : Roads.hallsOf(level, site)) {
			if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
				continue;
			}
			Caravans.Data data = Caravans.Data.get(level);
			for (BlockPos other : data.partners(hall)) {
				CityPlan.Road road = CaravanRoads.roadTo(entity.plan(), hall, other);
				if (road == null || !trackId(hall, other, road).equals(site.structure())) {
					continue;
				}
				data.setTracked(hall, other, true);
				Component who = builder != null ? builder.getDisplayName() : Component.translatable("steward.aliveworkplace.jobs.someone");
				Chronicle.record(level, hall, Chronicle.Kind.CARAVAN, Component.translatable("chronicle.aliveworkplace.train_track_laid", who,
					CaravanRoads.nameOf(level, other)));
				if (data.tracked(other, hall)) {
					Chronicle.record(level, hall, Chronicle.Kind.CARAVAN, Component.translatable("chronicle.aliveworkplace.railway_open",
						CaravanRoads.nameOf(level, other)));
					if (level.isLoaded(other) && level.getBlockEntity(other) instanceof VillageHallBlockEntity) {
						Chronicle.record(level, other, Chronicle.Kind.CARAVAN, Component.translatable("chronicle.aliveworkplace.railway_open",
							CaravanRoads.nameOf(level, hall)));
					}
				}
				return true;
			}
		}
		return true; // a track site whose road is gone: nothing to mark
	}
}
