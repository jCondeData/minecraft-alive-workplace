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
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
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
 * <p>
 * <b>39.7b.</b> The track is one line Create's trains can run: straight pieces on the level, a Create curve round each
 * turn where the road runs straight for a few nodes either side, and a sloped Create curve over each run of steps
 * ({@link #plan}). The train is built by a builder of the village from a blueprint of its blocks
 * ({@link #openTrain}), then glued and assembled. The caravan's goods ride in the carriage's chest ({@link #IN_CHEST}):
 * loaded at the departing station, taken out at the arriving one (only what is still there is delivered), and taken
 * back out of a derailed train for the timed trip; a train broken up while they're aboard leaves them in its chest, never
 * a second time with the caravan.
 * <p>
 * <b>39.7c.</b> No real road leaves a gap: what the plan's first pass leaves unjoined gets a curve ({@link #join}), and a
 * Train Station standing off the road gets a spur from its own track to the road's ({@link #spur}). The train is the
 * architect's drawing ({@link #TRAIN}), turned to the track past the station.
 * <p>
 * <b>39.7d.</b> The train's seat goes to the village's Conductor, a villager whose job block is Create's Train Station
 * ({@link #conductorFor}); a village without one seats a jobless villager as before. As the train comes in at a station
 * the Conductor riding it calls the stop, once ({@link #calls}).
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
	/** A caravan's arrival while it rides the village's Create train with its goods in the carriage's chest (39.7b). */
	public static final long IN_CHEST = Long.MAX_VALUE - 2;
	/** How often (ticks) a hall looks at its village's Create train. */
	static final int SERVE_EVERY = 10;
	/** A train site's id part, after the hall's folder of road segments (39.7b). */
	static final String TRAIN_PART = "train_";
	/** How far from a turn (nodes) the curve round it starts, tried in this order (39.7b). */
	static final int[] CURVE_REACH = {3, 2};

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
		 * The village train's blocks where a builder puts them (39.7b): on the track past {@code station}, on the side
		 * away from {@code other}. Null when there's no room (the track past it not straight and clear, the station not on
		 * a track).
		 */
		@Nullable
		Map<BlockPos, BlockState> train(ServerLevel level, BlockPos station, BlockPos other);

		/** Whether the train's blocks ({@link #train}) all stand where they go now. */
		boolean built(ServerLevel level, BlockPos station, BlockPos other);

		/**
		 * Glues the train's blocks a builder put up ({@link #train}), sits {@code conductor} in its seat, assembles it
		 * with the station and gives it a schedule between the two stations. Returns null when it can't (the blocks not
		 * all there, the station busy), with the reason in the log.
		 */
		@Nullable
		UUID assemble(ServerLevel level, BlockPos station, BlockPos other, UUID owner, Villager conductor);

		/** Puts all of {@code goods} into the village train's chest, or none of them (false: no room, no train). */
		boolean load(ServerLevel level, UUID owner, List<ItemStack> goods);

		/** Takes {@code goods} out of the village train's chest (derailed too): what was there of them. */
		List<ItemStack> unload(ServerLevel level, UUID owner, List<ItemStack> goods);

		/** What the village train's chest holds now (empty without the train). */
		List<ItemStack> cargo(ServerLevel level, UUID owner);

		/** Joins two straight track pieces with a Create curve, leaving {@code a} towards {@code outA} and {@code b} towards {@code outB}; false if it can't. */
		boolean curve(ServerLevel level, BlockPos a, Direction outA, BlockPos b, Direction outB);

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
		return arrives >= IN_CHEST;
	}

	/** Whether {@code arrives} is a caravan on board the village's Create train (its goods in the chest or not). */
	public static boolean aboard(long arrives) {
		return arrives == ABOARD || arrives == IN_CHEST;
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
	public static boolean first(BlockPos hall, BlockPos other) {
		return Roads.hallKey(hall).compareTo(Roads.hallKey(other)) <= 0;
	}

	/**
	 * The hall's look at its trains (39.7a, every {@link #SERVE_EVERY} ticks): its stations pointed at their track; per
	 * partner, the village train put together when it's due and missing; caravans loaded when the train stands at our
	 * station and unloaded when it brings ours in. Without the train, caravans waiting for it take the timed trip.
	 */
	public static void serve(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		if (!live() || !RAILWAY.present()) {
			offline(level, hall, Caravans.Data.get(level)); // B359
			return;
		}
		Caravans.Data data = Caravans.Data.get(level);
		List<BlockPos> stations = data.stations(hall);
		long now = level.getGameTime();
		for (BlockPos other : data.partners(hall)) {
			UUID id = railwayId(hall, other);
			boolean exists = RAILWAY.call(r -> r.exists(level, id), false);
			long timed = now + trainTrip(Caravans.trip(level, hall, other, data));
			if (!exists || data.stations(other).isEmpty()) {
				// 39.7b: our goods in the chest of a train that's gone or can't come in: what's still in it (a derailed
				// train keeps its chest) comes back out for the timed trip; a train broken up keeps them in its chest
				int left = data.recover(hall, other, timed, goods -> RAILWAY.call(r -> r.unload(level, id, goods), List.<ItemStack>of()));
				if (left > 0) {
					Chronicle.record(level, hall, Chronicle.Kind.CARAVAN, Component.translatable("chronicle.aliveworkplace.train_goods_left",
						CaravanRoads.nameOf(level, other)));
				}
			}
			if (stations.isEmpty()) {
				continue;
			}
			if (!exists) {
				data.retime(hall, other, BOARDING, timed); // no train: the caravans waiting for it take the trip as before
				data.retime(hall, other, ABOARD, timed);
				if (Math.floorMod(now + hall.hashCode(), CHECK_EVERY) < SERVE_EVERY && first(hall, other) && rides(data, hall, other)) {
					build(level, hall, other, entity, data, id);
				}
				continue;
			}
			BlockPos station = stations.get(0); // the village's own station, the nearest (the other's may be in reach too)
			boolean standing = RAILWAY.call(r -> r.at(level, id, station), false);
			calls(level, hall, id, station, standing); // 39.7d: the Conductor calls the stop as it comes in
			if (!standing) {
				continue;
			}
			// loaded onto the train: the goods into its chest (39.7b), or as the caravan's record when they don't fit
			if (data.board(hall, other, goods -> RAILWAY.call(r -> r.load(level, id, goods), false)) > 0) {
				whistle(level, hall, data);
			}
			int in = data.unload(other, hall, now, goods -> RAILWAY.call(r -> r.unload(level, id, goods), List.<ItemStack>of()))
				+ data.retime(other, hall, ABOARD, now);
			if (in > 0) {
				whistle(level, hall, data); // the train brought them in
				Caravans.trainIn(level, hall);
			}
		}
	}

	/**
	 * B359: with trains no longer running (Create taken out of the pack, or the switch off) a caravan saved waiting at the
	 * station for the village train, or riding it, takes the timed trip and arrives (from the hall's round and its look at
	 * its trains). Goods that rode in the train's chest come back out of it while Create is there (the train still stands
	 * on its track with the switch off); with Create gone the train and its chest are gone with it, so the caravan's own
	 * record of its goods goes on. Nothing left in the chest: the chronicle says the goods stayed there.
	 */
	public static void offline(ServerLevel level, BlockPos hall, Caravans.Data data) {
		if (live() && RAILWAY.present()) {
			return;
		}
		long now = level.getGameTime();
		List<BlockPos> lost = data.offTheTrains(hall, s -> now + trainTrip(Caravans.trip(level, s.from(), s.to(), data)),
			s -> RAILWAY.present() ? RAILWAY.call(r -> r.unload(level, railwayId(s.from(), s.to()), s.goods()), List.<ItemStack>of()) : s.goods());
		for (BlockPos other : lost) {
			Chronicle.record(level, hall, Chronicle.Kind.CARAVAN, Component.translatable("chronicle.aliveworkplace.train_goods_left",
				CaravanRoads.nameOf(level, other)));
		}
	}

	/** Our station and theirs, both pointed at their track and on one network Create knows; null when not (yet). */
	@Nullable
	private static BlockPos[] aimed(ServerLevel level, BlockPos hall, BlockPos other, Caravans.Data data) {
		List<BlockPos> ours = data.stations(hall);
		List<BlockPos> theirs = data.stations(other);
		if (ours.isEmpty() || theirs.isEmpty() || !level.isLoaded(theirs.get(0))) {
			return null;
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
		if (level.isLoaded(station)) {
			spurFor(level, hall, other, station); // 39.7c: a Train Station off the road joined to the road's track first
			spurFor(level, other, hall, far);
		}
		if (!level.isLoaded(station) || !RAILWAY.call(r -> r.aim(level, station, far, us) & r.aim(level, far, station, them) && r.linked(level, station, far), false)) {
			return null;
		}
		return new BlockPos[] {station, far};
	}

	/**
	 * The pair's train at our station: built by a builder of ours from its blueprint when its blocks aren't all there
	 * (39.7b), else glued and assembled with the village's Conductor in its seat (39.7d; a jobless villager without one).
	 */
	private static void build(ServerLevel level, BlockPos hall, BlockPos other, VillageHallBlockEntity entity, Caravans.Data data, UUID id) {
		BlockPos[] pair = aimed(level, hall, other, data);
		if (pair == null) {
			return;
		}
		BlockPos station = pair[0];
		BlockPos far = pair[1];
		if (!RAILWAY.call(r -> r.built(level, station, far), false)) {
			openTrain(level, hall, other, entity, station, far);
			return;
		}
		// B483: the blocks all stand once the builder's last one is placed, but its site isn't done yet (its builder
		// finishes the stage and the site on a later tick, and the chronicle says who built it then). Put together now,
		// the blocks would go into the train under the site: it never finished and the chronicle never said so.
		ResourceLocation site = trainId(hall, other);
		if (Roads.openSegments(level, hall).stream().anyMatch(s -> s.structure().equals(site))) {
			return;
		}
		Villager conductor = conductorFor(level, station);
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

	/** How far from the station (blocks) the train's conductor is looked for. */
	static final int CONDUCTOR_REACH = 24;

	/**
	 * Who sits in the village train's seat at {@code station} (39.7d): the village's Conductor, the nearest one by the
	 * station not already riding something; without one, the nearest jobless villager as before 39.7d. Null: nobody free.
	 */
	@Nullable
	public static Villager conductorFor(ServerLevel level, BlockPos station) {
		List<Villager> near = level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(station).inflate(CONDUCTOR_REACH),
			v -> v.isAlive() && !v.isBaby() && !v.isPassenger());
		Comparator<Villager> nearest = Comparator.comparingDouble(v -> v.distanceToSqr(station.getX(), station.getY(), station.getZ()));
		return near.stream().filter(v -> isConductor(v)).min(nearest)
			.or(() -> near.stream().filter(v -> v.getVillagerData().getProfession() == net.minecraft.world.entity.npc.VillagerProfession.NONE).min(nearest))
			.orElse(null);
	}

	/** Whether {@code villager} is a grown-up Conductor (39.7d; never without Create). */
	public static boolean isConductor(Villager villager) {
		return !villager.isBaby() && io.github.jcondedata.aliveworkplace.registry.ModVillagers.isConductor(villager.getVillagerData().getProfession());
	}

	/**
	 * Whether each village train was standing at a station when last looked at, as {@code owner@station} (39.7d): an
	 * arrival is called once, and only one seen coming in (not a train first seen standing, as after a restart).
	 */
	private static final Map<String, Boolean> STANDING = new java.util.concurrent.ConcurrentHashMap<>();
	/** The last arrival a Conductor called (for the tests and the showcase), null before the first. */
	@Nullable
	public static volatile Component lastCall;

	/**
	 * The village train with this owner at {@code station} (39.7d): when it has just come in, the Conductor riding it calls
	 * the stop, once, over his head and to the players by the station. Not called again until it has left and come back.
	 */
	static void calls(ServerLevel level, BlockPos hall, UUID owner, BlockPos station, boolean standing) {
		Boolean before = STANDING.put(owner + "@" + station.asLong(), standing);
		if (!standing || before == null || before) {
			return; // gone, first seen (standing or not), or still standing there
		}
		Villager conductor = level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(station).inflate(CONDUCTOR_REACH),
				v -> v.isAlive() && v.isPassenger() && isConductor(v))
			.stream().min(Comparator.comparingDouble(v -> v.distanceToSqr(station.getX(), station.getY(), station.getZ()))).orElse(null);
		if (conductor == null) {
			return; // a jobless villager driving it (no Conductor yet) says nothing
		}
		Component line = Component.translatable("message.aliveworkplace.conductor.arriving", CaravanRoads.nameOf(level, hall))
			.withStyle(net.minecraft.ChatFormatting.ITALIC);
		io.github.jcondedata.aliveworkplace.work.WorkerStatus.set(conductor, conductor.getDisplayName().copy().withStyle(net.minecraft.ChatFormatting.GRAY), -1f, line);
		Component said = io.github.jcondedata.aliveworkplace.story.HeartEvents.said(conductor.getDisplayName(), line);
		for (net.minecraft.server.level.ServerPlayer player : level.getEntitiesOfClass(net.minecraft.server.level.ServerPlayer.class,
			new net.minecraft.world.phys.AABB(station).inflate(CONDUCTOR_REACH))) {
			io.github.jcondedata.aliveworkplace.mc.Chat.chat(player, said);
		}
		lastCall = said;
	}

	/**
	 * Opens the site of the pair's train at our station (39.7b): a blueprint of the train's blocks where they go on the
	 * track past it ({@link Railway#train}), taken by an idle builder of ours, who brings its parts from the bench's
	 * chests like any build's. Returns the site, or null: one already open, no room, or nobody free.
	 */
	@Nullable
	public static BuildSite openTrain(ServerLevel level, BlockPos hall, BlockPos other, VillageHallBlockEntity entity, BlockPos station, BlockPos far) {
		for (BuildSite site : Roads.openSegments(level, hall)) {
			if (isTrainSite(site.structure())) {
				return null; // one at a time
			}
		}
		Map<BlockPos, BlockState> parts = RAILWAY.call(r -> r.train(level, station, far), null);
		if (parts == null || parts.isEmpty()) {
			return null;
		}
		BoundingBox box = BoundingBox.encapsulatingPositions(parts.keySet()).orElseThrow();
		BlockPos origin = new BlockPos(box.minX(), box.minY(), box.minZ());
		List<Blueprint.Entry> entries = new ArrayList<>();
		parts.forEach((pos, state) -> entries.add(new Blueprint.Entry(pos.subtract(origin), state, null)));
		Blueprint blueprint = new Blueprint(trainId(hall, other), new Vec3i(box.getXSpan(), box.getYSpan(), box.getZSpan()), entries);
		Villager builder = Roads.builderFor(level, hall, box, true);
		if (builder == null || !Roads.save(level, blueprint)) {
			return null;
		}
		UUID owner = entity.owner() != null ? entity.owner() : builder.getUUID();
		String ownerName = entity.owner() != null ? entity.ownerName() : "";
		BuildSite site = Builders.start(level, builder, owner, ownerName, blueprint.id(),
			new BlueprintData.Placement(Ids.of(level.dimension()), origin, Rotation.NONE, Mirror.NONE));
		site.setLevelGround(false); // on the track as it is
		return site;
	}

	/** The train site's blueprint id for the hall's train to {@code other} (39.7b). */
	public static ResourceLocation trainId(BlockPos hall, BlockPos other) {
		return AliveWorkplace.id(Roads.FOLDER + Roads.hallKey(hall) + "/" + TRAIN_PART + Roads.hallKey(other).replace('-', 'm'));
	}

	/** Whether {@code id} is a train site's blueprint (39.7b). */
	public static boolean isTrainSite(ResourceLocation id) {
		return Roads.isSegment(id) && id.getPath().substring(Roads.FOLDER.length()).contains("/" + TRAIN_PART);
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

	// ---- 39.7c: spurs to Train Stations off the road, the drawn train ----------------------------------------------

	/** The village train as drawn (39.7c, {@code tools/blueprints/create_builds.py}): running east from x = 0, the bogey at y = 0. */
	public static final ResourceLocation TRAIN = AliveWorkplace.id("train/village_train");

	/** The drawn train's blocks (no air), running east from the bogey at the origin; empty if the drawing is missing. */
	public static List<Blueprint.Entry> trainBlocks(ServerLevel level) {
		return level.getStructureManager().get(TRAIN)
			.map(t -> Blueprint.fromTemplate(TRAIN, t, Lookup.lookup(BuiltInRegistries.BLOCK)).blocks().stream().filter(e -> !e.state().isAir()).toList())
			.orElse(List.of());
	}

	/** Where the track a station looks at may be: beside it, or a step further (the Train Station build's platform edge), nearest first. */
	private static final List<BlockPos> NEAR = List.of(new BlockPos(1, 0, 0), new BlockPos(-1, 0, 0), new BlockPos(0, 0, 1), new BlockPos(0, 0, -1),
		new BlockPos(2, 0, 0), new BlockPos(-2, 0, 0), new BlockPos(0, 0, 2), new BlockPos(0, 0, -2));

	/** The straight track beside the station at {@code station} (along it, not ending at it), or null. */
	@Nullable
	public static BlockPos stationTrack(ServerLevel level, BlockPos station) {
		for (BlockPos offset : NEAR) {
			BlockPos p = station.offset(offset);
			String shape = shapeOf(level.getBlockState(p));
			if ("xo".equals(shape) && offset.getX() == 0 || "zo".equals(shape) && offset.getZ() == 0) {
				return p;
			}
		}
		return null;
	}

	/** A Create track's straight shape ({@code xo}, {@code zo}, ...), or null for anything else. */
	@Nullable
	static String shapeOf(BlockState state) {
		if (!isTrack(state)) {
			return null;
		}
		Property<?> property = state.getBlock().getStateDefinition().getProperty("shape");
		return property == null ? null : nameOf(state, property);
	}

	private static <T extends Comparable<T>> String nameOf(BlockState state, Property<T> property) {
		return property.getName(state.getValue(property));
	}

	/**
	 * The spur of a Train Station standing off the road (39.7c): its own straight track joined to the road's by one Create
	 * curve, from an end of the station's track to a straight piece of the road's ({@code plan}, along {@code way}, which
	 * runs from this village towards the other), joining it heading towards the other village. So a train leaving the
	 * station runs on towards the other village, and one coming back takes the spur at that piece. True when the station
	 * is on the road's track or the spur is there now; false when no curve fits ({@link #MAX_SPUR} blocks at most).
	 */
	public static boolean spur(ServerLevel level, BlockPos station, List<BlockPos> way, TrackPlan plan) {
		BlockPos track = stationTrack(level, station);
		if (track == null) {
			return false;
		}
		if (plan.shapes().containsKey(track)) {
			return true; // beside the road's own track
		}
		String shape = shapeOf(level.getBlockState(track));
		Direction plus = "xo".equals(shape) ? Direction.EAST : Direction.SOUTH;
		BlockPos[] ends = new BlockPos[2];
		Direction[] outs = {plus, plus.getOpposite()};
		for (int side = 0; side < 2; side++) {
			BlockPos end = track;
			for (int i = 0; i < 64 && shape.equals(shapeOf(level.getBlockState(end.relative(outs[side])))); i++) {
				end = end.relative(outs[side]);
			}
			ends[side] = end;
		}
		BlockPos bestA = null;
		Direction bestOutA = null;
		BlockPos bestB = null;
		Direction bestOutB = null;
		double best = Double.MAX_VALUE;
		for (int side = 0; side < 2; side++) {
			for (int j = 0; j < way.size(); j++) {
				BlockPos node = way.get(j);
				String piece = plan.shapes().get(node);
				BlockPos from = j + 1 < way.size() ? node : j > 0 ? way.get(j - 1) : null;
				BlockPos to = j + 1 < way.size() ? way.get(j + 1) : node;
				if (piece == null || from == null) {
					continue;
				}
				Direction travel = out(from, piece, to);
				if (travel == null || Math.abs(to.getX() - from.getX()) + Math.abs(to.getZ() - from.getZ()) != 1) {
					continue;
				}
				Direction outB = travel.getOpposite();
				if (out(ends[side], shape, node) != outs[side] || out(node, piece, ends[side]) != outB || !curveOk(ends[side], outs[side], node, outB, MAX_SPUR)) {
					continue;
				}
				boolean used = plan.curves().stream().anyMatch(c -> c.a().equals(node) && c.outA() == outB || c.b().equals(node) && c.outB() == outB);
				double d = ends[side].distSqr(node);
				if (!used && d < best) {
					best = d;
					bestA = ends[side];
					bestOutA = outs[side];
					bestB = node;
					bestOutB = outB;
				}
			}
		}
		if (bestA == null) {
			return false;
		}
		AliveWorkplace.LOG.debug("Alive Workplace: the spur of the station at {}: from {} ({}) to {} ({})", station.toShortString(), bestA.toShortString(), bestOutA,
			bestB.toShortString(), bestOutB);
		BlockPos a = bestA;
		Direction outA = bestOutA;
		BlockPos b = bestB;
		Direction outB = bestOutB;
		return level.isLoaded(a) && level.isLoaded(b) && RAILWAY.call(r -> r.curve(level, a, outA, b, outB), false);
	}

	/** The spur of the station of {@code hall}'s village off the road to {@code other}, when its track is laid (39.7c). */
	static void spurFor(ServerLevel level, BlockPos hall, BlockPos other, BlockPos station) {
		if (!level.isLoaded(hall) || !(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) || !Caravans.Data.get(level).tracked(hall, other)) {
			return;
		}
		CityPlan.Road road = CaravanRoads.roadTo(entity.plan(), hall, other);
		if (road != null && road.routed() && !road.route().isEmpty()) {
			List<BlockPos> way = way(hall, road);
			spur(level, station, way, plan(way, p -> free(level, p)));
		}
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
	 * A Create curve of the track (39.7b), round a turn of the road or up or down its steps: from the straight piece at
	 * {@code a} towards {@code outA} to the one at {@code b} towards {@code outB}.
	 */
	public record Curve(BlockPos a, Direction outA, BlockPos b, Direction outB) {
	}

	/** The track along a way (39.7b): each node's Create track shape, and the curves joining them round turns and over steps. */
	public record TrackPlan(Map<BlockPos, String> shapes, List<Curve> curves) {
	}

	/**
	 * The track along {@code route} (feet heights, a node per block, as roads are routed) that Create's railway takes as
	 * one line trains can run (39.7b). Create's trains only pass between pieces that meet nearly straight, so neither a
	 * corner of two straight pieces nor Create's steep ascending piece next to a level one will do: like a player's
	 * track, turns and slopes are curves. Round each turn with the way straight and level for {@link #CURVE_REACH} nodes
	 * either side, a curve joins the straight pieces that far from the turn. On each straight stretch, each run of steps
	 * the same way (up, or down) becomes one sloped curve from a level node before it to one after it, two nodes out where
	 * there's room (at least two blocks along for every block up). Nothing is laid on the nodes a curve passes over. Every
	 * other node with {@code free} gets a straight piece along the way ({@code xo}, {@code zo}). What that leaves unjoined
	 * (a turn with no room, a step on a turn, a zig-zag of a diagonal road, a node not {@code free}) is joined by
	 * {@link #join} (39.7c); only a gap no curve can span stays one.
	 */
	public static TrackPlan plan(List<BlockPos> route, java.util.function.Predicate<BlockPos> free) {
		int n = route.size();
		Direction[] dir = new Direction[Math.max(0, n - 1)];
		for (int j = 0; j + 1 < n; j++) {
			int dx = route.get(j + 1).getX() - route.get(j).getX();
			int dz = route.get(j + 1).getZ() - route.get(j).getZ();
			dir[j] = Math.abs(dx) + Math.abs(dz) == 1 ? Direction.fromDelta(dx, 0, dz) : null;
		}
		String[] shape = new String[n];
		boolean[] between = new boolean[n];
		List<Curve> curves = new ArrayList<>();
		for (int c = 1; c + 1 < n; c++) {
			Direction d1 = dir[c - 1];
			Direction d2 = dir[c];
			if (d1 == null || d2 == null || d1.getAxis() == d2.getAxis()) {
				continue;
			}
			for (int k : CURVE_REACH) {
				if (curveFits(route, dir, shape, between, free, c, k)) {
					for (int i = c - k + 1; i < c + k; i++) {
						between[i] = true;
					}
					shape[c - k] = straight(d1);
					shape[c + k] = straight(d2);
					curves.add(new Curve(route.get(c - k), d1, route.get(c + k), d2.getOpposite()));
					break;
				}
			}
		}
		for (int i = 0; i < n;) {
			if (between[i]) {
				i++;
				continue;
			}
			int r1 = i;
			while (r1 + 1 < n && !between[r1 + 1] && dir[r1] != null && dir[r1] == dir[i]) {
				r1++;
			}
			if (r1 > i) {
				slopes(route, i, r1, dir[i], shape, between, curves, free);
			}
			i = r1 + 1;
		}
		for (int j = 0; j < n; j++) {
			if (between[j] || shape[j] != null || !free.test(route.get(j))) {
				continue;
			}
			Direction before = j > 0 ? dir[j - 1] : null;
			Direction after = j + 1 < n ? dir[j] : null;
			if (j > 0 && before == null || j + 1 < n && after == null || before != null && after != null && before.getAxis() != after.getAxis()) {
				continue; // a jump in the way (a bridge's deck), or a turn with no room for a curve
			}
			Direction along = after != null ? after : before;
			if (along != null) {
				shape[j] = straight(along);
			}
		}
		join(route, dir, shape, between, curves, free); // 39.7c: what's left unjoined (dips, steps on turns, zig-zags)
		Map<BlockPos, String> shapes = new LinkedHashMap<>();
		for (int j = 0; j < n; j++) {
			if (shape[j] != null) {
				shapes.put(route.get(j), shape[j]);
			}
		}
		return new TrackPlan(shapes, List.copyOf(curves));
	}

	/** The longest curve (blocks, level) that joins two pieces of track the plan couldn't join (39.7c); longer gaps get a piece between. */
	static final int MAX_JOIN = 20;
	/** The longest spur (blocks, level) from a Train Station's own track to the road's (39.7c). */
	static final int MAX_SPUR = 32;

	/**
	 * 39.7c: what {@link #plan}'s first pass left unjoined is joined. Its straight pieces and curves make lines (pieces
	 * that meet straight, or a curve's two ends); short lines of one or two pieces between gaps (the straight bits of a
	 * zig-zag) are taken out, and each gap between two lines gets one Create curve from the last piece of one to the first
	 * of the next (a turn, an S-bend sideways, a slope, or all of them), leaving each straight along its own axis, the
	 * lines shortened by up to {@link #JOIN_BACK} pieces where the curve needs the room. A gap longer than
	 * {@link #MAX_JOIN} gets a straight piece on a node about halfway (along the way's main axis) and is joined in two.
	 */
	private static void join(List<BlockPos> route, Direction[] dir, String[] shape, boolean[] between, List<Curve> curves,
			java.util.function.Predicate<BlockPos> free) {
		int n = route.size();
		Map<BlockPos, Integer> index = new java.util.HashMap<>();
		for (int j = n - 1; j >= 0; j--) {
			index.put(route.get(j), j);
		}
		// the short lines between gaps (not at the way's ends)
		List<int[]> lines = lines(route, dir, shape, curves, index);
		for (int i = 1; i + 1 < lines.size(); i++) {
			int[] line = lines.get(i);
			if (line[1] - line[0] < 2 && !line(line, curves, index)) {
				for (int j = line[0]; j <= line[1]; j++) {
					shape[j] = null;
				}
			}
		}
		for (int guard = 0; guard < 2 * n; guard++) {
			lines = lines(route, dir, shape, curves, index);
			boolean progress = false;
			for (int i = 0; i + 1 < lines.size() && !progress; i++) {
				progress = joinLines(route, dir, shape, between, curves, index, lines.get(i), lines.get(i + 1))
					|| split(route, shape, between, free, lines.get(i)[1], lines.get(i + 1)[0]);
			}
			if (!progress) {
				return;
			}
		}
	}

	/** How many pieces a line may lose at its end to give a joining curve room (39.7c). */
	static final int JOIN_BACK = 4;

	/** Whether the line {@code line} (first, last index) has a curve's end in it. */
	private static boolean line(int[] line, List<Curve> curves, Map<BlockPos, Integer> index) {
		for (Curve c : curves) {
			int a = index.getOrDefault(c.a(), -1);
			int b = index.getOrDefault(c.b(), -1);
			if (a >= line[0] && a <= line[1] || b >= line[0] && b <= line[1]) {
				return true;
			}
		}
		return false;
	}

	/** The lines of track the plan has (first and last index along the way), in order: pieces meeting straight or by a curve. */
	private static List<int[]> lines(List<BlockPos> route, Direction[] dir, String[] shape, List<Curve> curves, Map<BlockPos, Integer> index) {
		int n = route.size();
		int[] next = new int[n];
		java.util.Arrays.fill(next, -1);
		for (int j = 0; j + 1 < n; j++) {
			if (shape[j] != null && shape[j].equals(shape[j + 1]) && dir[j] != null && straight(dir[j]).equals(shape[j])
				&& route.get(j).getY() == route.get(j + 1).getY()) {
				next[j] = j + 1;
			}
		}
		for (Curve c : curves) {
			int a = index.getOrDefault(c.a(), -1);
			int b = index.getOrDefault(c.b(), -1);
			if (a >= 0 && b > a) {
				next[a] = Math.max(next[a], b);
			}
		}
		List<int[]> out = new ArrayList<>();
		int j = 0;
		while (j < n) {
			if (shape[j] == null) {
				j++;
				continue;
			}
			int first = j;
			while (next[j] > j) {
				j = next[j];
			}
			out.add(new int[] {first, j});
			j++;
		}
		return out;
	}

	/** The side of node {@code j} a curve or a neighbouring piece already leaves from, if any of {@code side}. */
	private static boolean taken(List<BlockPos> route, Direction[] dir, String[] shape, List<Curve> curves, int j, Direction side) {
		int n = route.size();
		if (j > 0 && shape[j - 1] != null && shape[j - 1].equals(shape[j]) && dir[j - 1] != null && dir[j - 1].getOpposite() == side
			&& route.get(j - 1).getY() == route.get(j).getY()) {
			return true;
		}
		if (j + 1 < n && shape[j + 1] != null && shape[j + 1].equals(shape[j]) && dir[j] == side && route.get(j + 1).getY() == route.get(j).getY()) {
			return true;
		}
		for (Curve c : curves) {
			if (c.a().equals(route.get(j)) && c.outA() == side || c.b().equals(route.get(j)) && c.outB() == side) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The way out of a straight piece at {@code from} along its axis ({@code shape}) towards {@code to}: null when {@code to}
	 * isn't ahead on that axis.
	 */
	@Nullable
	static Direction out(BlockPos from, String shape, BlockPos to) {
		int d = "xo".equals(shape) ? to.getX() - from.getX() : to.getZ() - from.getZ();
		if (d == 0) {
			return null;
		}
		return "xo".equals(shape) ? (d > 0 ? Direction.EAST : Direction.WEST) : (d > 0 ? Direction.SOUTH : Direction.NORTH);
	}

	/**
	 * Whether a Create curve can join a straight piece at {@code a} (leaving {@code outA}) to one at {@code b} (leaving
	 * {@code outB}): not doubling back, at least two blocks along for every block up, not longer than {@code max}, a turn
	 * with legs of one length and an S-bend Create draws smooth (see below).
	 */
	static boolean curveOk(BlockPos a, Direction outA, BlockPos b, Direction outB, int max) {
		if (outA == null || outB == null || outA == outB) {
			return false;
		}
		int dx = b.getX() - a.getX();
		int dz = b.getZ() - a.getZ();
		double h = Math.sqrt(dx * dx + dz * dz);
		int dy = Math.abs(b.getY() - a.getY());
		if (h < 2 || h < 2 * dy + 1 || h > max) {
			return false;
		}
		int along = Math.abs(outA.getAxis() == Direction.Axis.X ? dx : dz);
		int across = Math.abs(outA.getAxis() == Direction.Axis.X ? dz : dx);
		if (outA.getAxis() != outB.getAxis()) {
			return along > 0 && along == across; // a quarter circle, as Create's own track makes a turn
		}
		// An S-bend starts and ends at the blocks' faces, a block less along. Create's curve is smooth there only with as
		// much way along as across, the same or half as much again (in between its handles come out short and the ends
		// kink, so trains never pass them; found in CreateTrackCompatTests)
		int faces = along - 1;
		return across <= 1 || faces == across || 2 * faces >= 3 * across;
	}

	/** Joins two lines (first, last index) with a curve, shortening their facing ends where it needs the room; false if no curve fits. */
	private static boolean joinLines(List<BlockPos> route, Direction[] dir, String[] shape, boolean[] between, List<Curve> curves,
			Map<BlockPos, Integer> index, int[] first, int[] second) {
		for (int drop = 0; drop <= 2 * JOIN_BACK; drop++) {
			for (int back = Math.max(0, drop - JOIN_BACK); back <= Math.min(drop, JOIN_BACK); back++) {
				int e = first[1] - back;
				int s = second[0] + (drop - back);
				if (e < first[0] || s > second[1] || !shortens(route, dir, shape, curves, index, first[1], e) || !shortens(route, dir, shape, curves, index, second[0], s)) {
					continue;
				}
				BlockPos a = route.get(e);
				BlockPos b = route.get(s);
				Direction outA = out(a, shape[e], b);
				Direction outB = out(b, shape[s], a);
				if (!curveOk(a, outA, b, outB, MAX_JOIN)) {
					continue;
				}
				String[] trial = shape.clone();
				for (int k = e + 1; k < s; k++) {
					trial[k] = null;
				}
				if (taken(route, dir, trial, curves, e, outA) || taken(route, dir, trial, curves, s, outB)) {
					continue;
				}
				for (int k = e + 1; k < s; k++) {
					shape[k] = null;
					between[k] = true;
				}
				curves.add(new Curve(a, outA, b, outB));
				return true;
			}
		}
		return false;
	}

	/** Whether a line may lose its pieces from {@code end} to {@code to} (exclusive of {@code to}): straight pieces only, no curve's end among them. */
	private static boolean shortens(List<BlockPos> route, Direction[] dir, String[] shape, List<Curve> curves, Map<BlockPos, Integer> index, int end, int to) {
		for (int k = Math.min(end, to); k <= Math.max(end, to); k++) {
			if (k == to) {
				continue;
			}
			for (Curve c : curves) {
				if (c.a().equals(route.get(k)) || c.b().equals(route.get(k))) {
					return false;
				}
			}
		}
		return true;
	}

	/** A gap too long for one curve, between the pieces at {@code e} and {@code s}: a straight piece on a free node about halfway; false if none. */
	private static boolean split(List<BlockPos> route, String[] shape, boolean[] between, java.util.function.Predicate<BlockPos> free, int e, int s) {
		BlockPos a = route.get(e);
		BlockPos b = route.get(s);
		int dx = b.getX() - a.getX();
		int dz = b.getZ() - a.getZ();
		if (dx * dx + dz * dz <= MAX_JOIN * MAX_JOIN || s - e < 3) {
			return false;
		}
		String axis = Math.abs(dx) > Math.abs(dz) ? "xo" : Math.abs(dz) > Math.abs(dx) ? "zo" : shape[e];
		int mid = (e + s) / 2;
		for (int off = 0; off < (s - e) / 2; off++) {
			for (int m : new int[] {mid - off, mid + off}) {
				if (m > e + 1 && m < s - 1 && shape[m] == null && free.test(route.get(m))) {
					shape[m] = axis;
					between[m] = false;
					return true;
				}
			}
		}
		return false;
	}

	/** The slopes of the straight stretch {@code r0..r1} running {@code d}: each run of steps the same way, a curve between level landings. */
	private static void slopes(List<BlockPos> route, int r0, int r1, Direction d, String[] shape, boolean[] between, List<Curve> curves,
			java.util.function.Predicate<BlockPos> free) {
		List<int[]> groups = new ArrayList<>(); // {first step's lower index, last step's lower index, up or down}
		for (int j = r0; j < r1; j++) {
			int dy = Integer.signum(route.get(j + 1).getY() - route.get(j).getY());
			if (dy == 0) {
				continue;
			}
			if (!groups.isEmpty() && groups.get(groups.size() - 1)[2] == dy) {
				groups.get(groups.size() - 1)[1] = j;
			} else {
				groups.add(new int[] {j, j, dy});
			}
		}
		int prev = r0;
		for (int g = 0; g < groups.size(); g++) {
			int p = groups.get(g)[0];
			int q = groups.get(g)[1];
			int next = g + 1 < groups.size() ? groups.get(g + 1)[0] : r1;
			int rise = Math.abs(route.get(q + 1).getY() - route.get(p).getY());
			int a = Math.max(prev, p - 2);
			int b = Math.min(next, q + 3);
			while (b - a < 3 * rise + 1 && (a > prev || b < next)) {
				if (a > prev) {
					a--;
				}
				if (b - a < 3 * rise + 1 && b < next) {
					b++;
				}
			}
			String along = straight(d);
			if (b - a < 2 * rise + 1 || !free.test(route.get(a)) || !free.test(route.get(b)) || shape[a] != null && !shape[a].equals(along)
				|| shape[b] != null && !shape[b].equals(along)) {
				for (int k = p + 1; k <= q; k++) {
					between[k] = true; // too steep or taken: a gap there, never a piece that doesn't join
				}
				prev = q + 1;
				continue;
			}
			for (int k = a + 1; k < b; k++) {
				between[k] = true;
			}
			shape[a] = along;
			shape[b] = along;
			curves.add(new Curve(route.get(a), d, route.get(b), d.getOpposite()));
			prev = b;
		}
	}

	/** Whether a curve fits round the turn at node {@code c}, from {@code k} nodes before it to {@code k} after: straight and level all the way, free, untaken. */
	private static boolean curveFits(List<BlockPos> route, Direction[] dir, String[] shape, boolean[] between, java.util.function.Predicate<BlockPos> free, int c, int k) {
		int n = route.size();
		if (c - k < 0 || c + k >= n) {
			return false;
		}
		int y = route.get(c).getY();
		for (int i = c - k; i <= c + k; i++) {
			if (between[i] || shape[i] != null || route.get(i).getY() != y || !free.test(route.get(i))) {
				return false;
			}
		}
		for (int i = c - k; i < c; i++) {
			if (dir[i] != dir[c - 1]) {
				return false;
			}
		}
		for (int i = c; i < c + k; i++) {
			if (dir[i] != dir[c]) {
				return false;
			}
		}
		return true;
	}

	private static String straight(Direction along) {
		return along.getAxis() == Direction.Axis.X ? "xo" : "zo";
	}

	/** The way of the hall's half of the road to {@code other}, in the world. */
	private static List<BlockPos> way(BlockPos hall, CityPlan.Road road) {
		return road.route().stream().map(p -> p.offset(hall)).toList();
	}

	/** Where track may go: nothing standing there but track (the hall the road starts from, a lamp: the track goes round it). */
	private static boolean free(ServerLevel level, BlockPos pos) {
		BlockState here = level.getBlockState(pos);
		return here.isAir() || here.canBeReplaced() || isTrack(here);
	}

	/**
	 * The track for the hall's half of the road to {@code other}: Create track along its way at feet height, down the
	 * road's middle, as {@link #plan} lays it (the curves are joined once it's laid, {@link #trackBuilt}). Empty without
	 * Create's track block, or with no node to lay on.
	 */
	public static Optional<Roads.Segment> blueprint(ServerLevel level, BlockPos hall, BlockPos other, CityPlan.Road road) {
		if (!BuiltInRegistries.BLOCK.containsKey(TRACK) || road == null || road.route().isEmpty()) {
			return Optional.empty();
		}
		BlockState track = Lookup.value(BuiltInRegistries.BLOCK, TRACK).defaultBlockState();
		Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
		plan(way(hall, road), p -> free(level, p)).shapes().forEach((pos, shape) -> blocks.put(pos, with(track, "shape", shape)));
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
		if (isTrainSite(site.structure())) {
			// the village train's blocks are up (39.7b): the hall's next look at its trains glues and assembles it
			for (BlockPos hall : Roads.hallsOf(level, site)) {
				if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity) {
					Component who = io.github.jcondedata.aliveworkplace.hall.Chronicle.lineStarter(builder);
					Chronicle.record(level, hall, Chronicle.Kind.CARAVAN, Component.translatable("chronicle.aliveworkplace.train_built", who));
				}
			}
			return true;
		}
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
				// 39.7b: the curves round the road's turns, joining the straight pieces the builder laid either side
				for (Curve curve : plan(way(hall, road), p -> free(level, p)).curves()) {
					if (level.isLoaded(curve.a()) && level.isLoaded(curve.b())) {
						RAILWAY.call(r -> r.curve(level, curve.a(), curve.outA(), curve.b(), curve.outB()), false);
					}
				}
				for (BlockPos station : data.stations(hall)) { // 39.7c: a Train Station off the road gets its spur
					if (level.isLoaded(station)) {
						spur(level, station, way(hall, road), plan(way(hall, road), p -> free(level, p)));
					}
				}
				Component who = io.github.jcondedata.aliveworkplace.hall.Chronicle.lineStarter(builder);
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
