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
 * <b>Real Create trains (39.7a, owner 2026-10-18).</b> When the two stations stand on one track network Create's railway
 * knows ({@link Railway#linked}), the first village of the pair puts its own train together at its station the way a
 * player does: a bogey on the track, a carriage of train casing with the caravan's chest, controls at both ends and a
 * seat between them where a jobless villager of the village sits as conductor, glued and assembled by the station, with
 * a schedule that runs it between the two stations ({@link Railway#assemble}). Create drives it from then on. A caravan
 * on that line waits at the station ({@link #BOARDING}) until the train stands there, rides it ({@link #ABOARD}), and is
 * unloaded the moment the train stands at the other village's station. Without that train (not yet assembled, or
 * broken by a player) the caravan takes the timed trip above. Everything Create-specific is in {@code compat/create}
 * behind {@link #RAILWAY}; without Create, with the switch off, or with caravan roads off, nothing is laid and caravans
 * walk as before. Only trains the village put together are touched (their owner is the railway's own id,
 * {@link #railwayId}).
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

	/** A caravan's arrival while it waits at the station for the village's Create train (39.7a). */
	public static final long BOARDING = Long.MAX_VALUE;
	/** A caravan's arrival while it rides the village's Create train to the other station (39.7a). */
	public static final long ABOARD = Long.MAX_VALUE - 1;
	/** How often (ticks) a hall looks at its village's Create train. */
	static final int SERVE_EVERY = 10;

	/**
	 * Create's own trains (39.7a), filled in by {@code compat/create} when Create is installed. Stations are given by
	 * their block position; a village train is known by its owner, the railway's id ({@link #railwayId}).
	 */
	public interface Railway {
		/**
		 * Points the station at {@code station} at the Create track beside it, if it isn't yet, facing {@code other}
		 * (trains coming from {@code other} stop there, and the village train is put together on that side); true when
		 * Create knows it as a station on a track network.
		 */
		boolean aim(ServerLevel level, BlockPos station, BlockPos other, String name);

		/** Whether the two stations stand on one track network (a train can run between them). */
		boolean linked(ServerLevel level, BlockPos a, BlockPos b);

		/**
		 * Puts a village train together at {@code station} (on the track leading away from {@code other}) with
		 * {@code conductor} in its seat, assembles it with the station and gives it a schedule between the two stations.
		 * Returns null when it can't (no room on the track, the station busy), with the reason in the log.
		 */
		@Nullable
		UUID assemble(ServerLevel level, BlockPos station, BlockPos other, UUID owner, Villager conductor);

		/** Whether a village train with this owner is on the railway (and not derailed). */
		boolean exists(ServerLevel level, UUID owner);

		/** Whether the village train with this owner stands at the station at {@code station} now. */
		boolean at(ServerLevel level, UUID owner, BlockPos station);

		/** A line about the village train with this owner for the log and the tests (where it's going, how fast, its conductors). */
		String status(ServerLevel level, UUID owner);

		/** Takes the village train with this owner off the railway (its carriages go with it). */
		void scrap(ServerLevel level, UUID owner);
	}

	/** Create's trains, when Create is installed (compat/create). */
	public static final io.github.jcondedata.aliveworkplace.work.Extension<Railway> RAILWAY = new io.github.jcondedata.aliveworkplace.work.Extension<>("Create's trains");

	private CaravanTrains() {
	}

	/** Whether {@code arrives} is a stage of a trip by the village's Create train (no time known yet). */
	public static boolean waiting(long arrives) {
		return arrives >= ABOARD;
	}

	/** The railway between two villages: the owner of the train they run (the same both ways). */
	public static UUID railwayId(BlockPos a, BlockPos b) {
		String ka = Roads.hallKey(a);
		String kb = Roads.hallKey(b);
		String key = ka.compareTo(kb) <= 0 ? ka + "/" + kb : kb + "/" + ka;
		return UUID.nameUUIDFromBytes(("aliveworkplace:railway/" + key).getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}

	/** Whether the village's own Create train runs between {@code from} and {@code to} now. */
	public static boolean realTrain(ServerLevel level, BlockPos from, BlockPos to) {
		return live() && RAILWAY.call(r -> r.exists(level, railwayId(from, to)), false);
	}

	/** The village that puts the pair's train together: the one whose key comes first. */
	static boolean first(BlockPos hall, BlockPos other) {
		return Roads.hallKey(hall).compareTo(Roads.hallKey(other)) <= 0;
	}

	/**
	 * The hall's look at its trains (39.7a, every {@link #SERVE_EVERY} ticks): its stations pointed at their track; per
	 * partner, the village train put together when it's due and missing; caravans loaded when the train stands at our
	 * station and unloaded when it brings ours in. Without the train, caravans waiting for it take the timed trip.
	 */
	public static void serve(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		if (!live() || !RAILWAY.present()) {
			return;
		}
		Caravans.Data data = Caravans.Data.get(level);
		List<BlockPos> stations = data.stations(hall);
		if (stations.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		for (BlockPos other : data.partners(hall)) {
			UUID id = railwayId(hall, other);
			if (!RAILWAY.call(r -> r.exists(level, id), false)) {
				long timed = now + trainTrip(Caravans.trip(level, hall, other, data));
				data.retime(hall, other, BOARDING, timed); // no train: the caravans waiting for it take the trip as before
				data.retime(hall, other, ABOARD, timed);
				if (Math.floorMod(now + hall.hashCode(), CHECK_EVERY) < SERVE_EVERY && first(hall, other) && rides(data, hall, other)) {
					build(level, hall, other, entity, data, id);
				}
				continue;
			}
			BlockPos station = stations.get(0); // the village's own station, the nearest (the other's may be in reach too)
			if (!RAILWAY.call(r -> r.at(level, id, station), false)) {
				continue;
			}
			if (data.retime(hall, other, BOARDING, ABOARD) > 0) {
				whistle(level, hall, data); // loaded onto the train
			}
			if (data.retime(other, hall, ABOARD, now) > 0) {
				whistle(level, hall, data); // the train brought them in
				Caravans.trainIn(level, hall);
			}
		}
	}

	/** Puts the pair's train together at our station, with a jobless villager of ours as its conductor. */
	private static void build(ServerLevel level, BlockPos hall, BlockPos other, VillageHallBlockEntity entity, Caravans.Data data, UUID id) {
		List<BlockPos> ours = data.stations(hall);
		List<BlockPos> theirs = data.stations(other);
		if (theirs.isEmpty() || !level.isLoaded(theirs.get(0))) {
			return;
		}
		BlockPos station = ours.get(0);
		BlockPos far = theirs.get(0);
		String ourName = VillageHalls.name(level, hall).getString();
		String theirName = VillageHalls.name(level, other).getString();
		if (ourName.equals(theirName)) { // the schedule goes by name: two stations of one name would be one stop
			ourName += " " + Roads.hallKey(hall);
			theirName += " " + Roads.hallKey(other);
		}
		String us = ourName;
		String them = theirName;
		if (!level.isLoaded(station) || !RAILWAY.call(r -> r.aim(level, station, far, us) & r.aim(level, far, station, them) && r.linked(level, station, far), false)) {
			return;
		}
		Villager conductor = level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(station).inflate(24),
				v -> v.isAlive() && !v.isBaby() && !v.isPassenger() && v.getVillagerData().getProfession() == net.minecraft.world.entity.npc.VillagerProfession.NONE)
			.stream().min(Comparator.comparingDouble(v -> v.distanceToSqr(station.getX(), station.getY(), station.getZ()))).orElse(null);
		if (conductor == null) {
			return; // nobody free to drive it yet: the caravans take the timed trip
		}
		UUID train = RAILWAY.call(r -> r.assemble(level, station, far, id, conductor), null);
		if (train != null) {
			Chronicle.atHall(level, hall, Chronicle.Kind.CARAVAN, Component.translatable("chronicle.aliveworkplace.train_assembled",
				conductor.getDisplayName(), CaravanRoads.nameOf(level, other)));
			whistle(level, hall, data);
		}
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
		if (Math.floorMod(level.getGameTime() + hall.hashCode(), SERVE_EVERY) == 0) {
			serve(level, hall, entity); // 39.7a: the village's Create train
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
