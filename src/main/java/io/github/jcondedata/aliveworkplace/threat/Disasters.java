package io.github.jcondedata.aliveworkplace.threat;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageAdvice;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * The disaster engine (ROADMAP 32.15): disasters as data, {@code data/<ns>/disasters/<id>.json} ({@link Disaster}).
 * One disaster at a time per village and {@link #REST} days' rest after one. The hall's round rolls each disaster that
 * is switched on (config {@code disasters}) and whose conditions hold, once a day, by its chance; one foretold by a job
 * in the village or by 32.14's sources ({@link io.github.jcondedata.aliveworkplace.guard.Warnings#source}) begins
 * {@code foretell_days} later. It ends after its days or, if it says so, with rain; switched off, it ends at once.
 * Saved on the hall ({@code disaster}, {@code disasterSince}, {@code disasterEnds}, {@code lastDisasterDay}).
 *
 * <p>The effects stay cheap: the halls with a disaster in effect are a short list ({@link #afflicted}), which the crop's
 * and the farmland's random ticks check (one look each, nothing scans the area); cauldrons are found as POIs, the ill
 * and the moods through the hall's census and the villagers' own mood check. The list is rebuilt by the halls' rounds,
 * so after a restart it fills within one round.
 */
public final class Disasters implements ResourceManagerReloadListener {
	public static final ResourceLocation ID = AliveWorkplace.id("disasters");
	public static final String FOLDER = "disasters";
	/** Days of rest after a disaster before the next may start. */
	public static final int REST = 7;
	/** The disasters the config switches off ({@code disasters} entries set to false). */
	public static Set<String> DISABLED = Set.of();
	/** Whether the halls roll disasters by chance (off in GameTests, which start them themselves). */
	public static boolean ROLLING = System.getProperty("fabric-api.gametest") == null;

	private static Map<ResourceLocation, Disaster> disasters = Map.of();
	/** The halls with a disaster in effect, by dimension and hall. */
	private static final Map<String, Map<BlockPos, Afflicted>> AFFLICTED = new ConcurrentHashMap<>();
	/** The last day each hall rolled, and applied its disaster's daily effects (not saved: a restart may roll again that day). */
	private static final Map<String, Long> ROLLED = new ConcurrentHashMap<>();
	private static final Map<String, Long> APPLIED = new ConcurrentHashMap<>();
	/** The last day it rained, by dimension (not saved: a restart counts from that day). */
	private static final Map<String, Long> RAIN = new ConcurrentHashMap<>();
	/** The wood share of each hall's finished builds and the day it was sampled. */
	private static final Map<String, double[]> WOOD = new ConcurrentHashMap<>();

	/** A hall whose disaster is in effect, and its area. */
	public record Afflicted(BlockPos hall, AABB area, Disaster disaster) {
	}

	public static void init() {
		Platform.get().onDataReload(ID, new Disasters());
		Platform.get().onServerStarting(server -> forget());
	}

	/** Forgets everything kept in memory (a new server; tests). */
	public static void forget() {
		AFFLICTED.clear();
		ROLLED.clear();
		APPLIED.clear();
		RAIN.clear();
		WOOD.clear();
		Drought.forget();
	}

	/** Whether disasters are open at all (the M32 expansion). */
	public static boolean live() {
		return Expansions.on(Expansions.M32);
	}

	// The files.

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		reload(manager);
	}

	/** Reads the disasters from the datapacks, and lists any new one in the config. */
	public static void reload(ResourceManager manager) {
		Map<ResourceLocation, String> files = new TreeMap<>();
		for (Map.Entry<ResourceLocation, Resource> e : manager.listResources(FOLDER, p -> p.getPath().endsWith(".json")).entrySet()) {
			try (Reader reader = e.getValue().openAsReader()) {
				StringBuilder text = new StringBuilder();
				char[] buf = new char[4096];
				for (int n; (n = reader.read(buf)) > 0; ) {
					text.append(buf, 0, n);
				}
				files.put(e.getKey(), text.toString());
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Skipping disaster {}: {}", e.getKey(), ex.toString());
			}
		}
		disasters = load(files);
		AliveWorkplace.LOG.info("Disasters: {}", disasters.keySet());
		List<String> keys = new ArrayList<>();
		disasters.keySet().forEach(id -> keys.add(key(id)));
		WorkplaceConfig.listDisasters(Platform.get().configDir(), keys);
	}

	/** Reads every file ({@code data/ns/disasters/name.json} → its text) into disasters by id; one that can't be read is logged and skipped. */
	public static Map<ResourceLocation, Disaster> load(Map<ResourceLocation, String> files) {
		Map<ResourceLocation, Disaster> out = new TreeMap<>();
		files.forEach((file, text) -> {
			String path = file.getPath();
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath(file.getNamespace(),
				path.substring(path.lastIndexOf('/') + 1, path.length() - ".json".length()));
			try {
				out.put(id, Disaster.read(id, JsonParser.parseString(text).getAsJsonObject()));
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Skipping disaster {}: {}", file, ex.getMessage() == null ? ex.toString() : ex.getMessage());
			}
		});
		return Collections.unmodifiableMap(new LinkedHashMap<>(out));
	}

	public static Collection<Disaster> all() {
		return disasters.values();
	}

	public static Optional<Disaster> get(ResourceLocation id) {
		return Optional.ofNullable(disasters.get(id));
	}

	/** How the config names a disaster: ours by its path ({@code drought}), a pack's by its whole id. */
	public static String key(ResourceLocation id) {
		return id.getNamespace().equals(AliveWorkplace.MOD_ID) ? id.getPath() : id.toString();
	}

	public static boolean enabled(ResourceLocation id) {
		return !DISABLED.contains(key(id)) && !DISABLED.contains(id.toString());
	}

	/** The disaster {@code id}, if it's loaded and switched on. */
	public static Optional<Disaster> on(ResourceLocation id) {
		return get(id).filter(d -> enabled(d.id()));
	}

	/** The disaster's name: its {@code messages}{@code .name} key, or its id's path in words. */
	public static Component name(Disaster disaster) {
		if (!disaster.messages().isEmpty()) {
			return Component.translatable(disaster.messages() + ".name");
		}
		String path = disaster.id().getPath().replace('_', ' ');
		return Component.literal(path.isEmpty() ? path : path.substring(0, 1).toUpperCase(Locale.ROOT) + path.substring(1));
	}

	// The hall's round.

	private static String key(ServerLevel level, BlockPos hall) {
		return level.dimension().location() + "|" + hall.asLong();
	}

	/** The hall's round: a disaster under way goes on or ends; without one, once a day, one may start. */
	public static void round(ServerLevel level, BlockPos pos, VillageHallBlockEntity hall, VillageHalls.Census census) {
		long day = Chronicle.day(level);
		if (raining(level)) {
			RAIN.put(level.dimension().location().toString(), day);
		}
		VillageFire.chimney(level, pos, hall, census, level.random); // a chimney spark on a dry day (32.16), once a day
		ResourceLocation id = hall.disaster();
		if (id != null) {
			Optional<Disaster> d = on(id);
			if (d.isEmpty() || !live()) {
				end(level, pos, hall, d.orElse(null), day); // switched off (or gone from the datapacks): over at once
				return;
			}
			if (day < hall.disasterSince()) {
				unafflict(level, pos);
				return; // foretold, not yet come
			}
			if (d.get().endsWhenWell() && villagers(census).stream().anyMatch(Sickness::isIll)) {
				hall.setDisaster(id, hall.disasterSince(), day, hall.lastDisasterDay()); // someone ill today: not over before tomorrow (32.18)
			}
			boolean over = hall.disasterEnds() >= 0 && day > hall.disasterEnds() || d.get().endsWithRain() && raining(level);
			if (over) {
				end(level, pos, hall, d.get(), day);
				return;
			}
			afflict(level, pos, d.get());
			contagion(level, pos, d.get(), census, level.random);
			Long applied = APPLIED.put(key(level, pos), day);
			if (applied == null || applied != day) {
				daily(level, pos, d.get(), census, level.random);
			}
			return;
		}
		unafflict(level, pos);
		if (!ROLLING || !live()) {
			return;
		}
		Long rolled = ROLLED.put(key(level, pos), day);
		if (rolled == null || rolled != day) {
			roll(level, pos, hall, census, level.random);
		}
	}

	/**
	 * Rolls each disaster switched on whose conditions hold, by its chance, and starts the first that comes; none while
	 * one is under way or in the {@link #REST} days after one. Returns the one started.
	 */
	public static Optional<Disaster> roll(ServerLevel level, BlockPos pos, VillageHallBlockEntity hall, VillageHalls.Census census, RandomSource random) {
		long day = Chronicle.day(level);
		if (!live() || hall.disaster() != null || day - hall.lastDisasterDay() < REST) {
			return Optional.empty();
		}
		for (Disaster d : disasters.values()) {
			if (!enabled(d.id())) {
				continue;
			}
			double chance = chance(level, pos, d, census);
			if (chance > 0 && random.nextDouble() < chance) {
				start(level, pos, hall, d, census, random);
				return Optional.of(d);
			}
		}
		return Optional.empty();
	}

	/** Today's chance of {@code disaster} for the village round {@code hall}: 0 where its {@code when} or a {@code requires} fails. */
	public static double chance(ServerLevel level, BlockPos hall, Disaster disaster, VillageHalls.Census census) {
		if (!disaster.when().test(level, hall, census)) {
			return 0;
		}
		for (Disaster.Require r : disaster.requires()) {
			if (r.unless().isPresent() && r.unless().get().test(level, hall, census)) {
				continue;
			}
			if (!r.when().test(level, hall, census)) {
				return 0;
			}
		}
		double chance = disaster.chance();
		for (Disaster.Factor f : disaster.factors()) {
			if (f.iff().test(level, hall, census)) {
				chance = f.chance().orElse(chance) * f.times();
			}
		}
		return Math.max(0, Math.min(1, chance));
	}

	/** Starts {@code disaster} at the hall: at once, or foretold {@code foretell_days} ahead when the village has a foreteller. */
	public static void start(ServerLevel level, BlockPos pos, VillageHallBlockEntity hall, Disaster disaster, VillageHalls.Census census, RandomSource random) {
		long day = Chronicle.day(level);
		Optional<Component> seer = foreteller(level, pos, disaster, census);
		long since = day + (seer.isPresent() ? disaster.foretellDays() : 0);
		int days = disaster.minDays() + (disaster.maxDays() > disaster.minDays() ? random.nextInt(disaster.maxDays() - disaster.minDays() + 1) : 0);
		long ends = disaster.endsWhenWell() ? since : disaster.maxDays() == 0 ? -1 : since + days - 1;
		hall.setDisaster(disaster.id(), since, ends, hall.lastDisasterDay());
		Component name = name(disaster);
		Component village = hall.getName();
		if (Plague.is(disaster)) {
			Plague.firstCase(level, villagers(census), random); // the first case comes at once; a Physician foretells the outbreak from it (32.18)
		}
		if (seer.isPresent() && since > day) {
			tell(level, pos, hall, text(disaster.messages(), "disaster.aliveworkplace", "foretold", seer.get(), name, village, since - day));
			Chronicle.atHall(level, pos, disaster.kind(), text(disaster.chronicle(), "chronicle.aliveworkplace.disaster", "foretold", seer.get(), name));
		} else {
			tell(level, pos, hall, text(disaster.messages(), "disaster.aliveworkplace", "start", name, village));
			Chronicle.atHall(level, pos, disaster.kind(), text(disaster.chronicle(), "chronicle.aliveworkplace.disaster", "start", name));
			afflict(level, pos, disaster);
		}
	}

	/** Ends the hall's disaster today ({@code disaster} null: it's no longer loaded). */
	public static void end(ServerLevel level, BlockPos pos, VillageHallBlockEntity hall, @Nullable Disaster disaster, long day) {
		boolean began = day >= hall.disasterSince();
		hall.setDisaster(null, -1, -1, began ? day : hall.lastDisasterDay());
		unafflict(level, pos);
		APPLIED.remove(key(level, pos));
		if (disaster != null && enabled(disaster.id()) && began) {
			Component name = name(disaster);
			tell(level, pos, hall, text(disaster.messages(), "disaster.aliveworkplace", "end", name, hall.getName()));
			Chronicle.atHall(level, pos, disaster.kind(), text(disaster.chronicle(), "chronicle.aliveworkplace.disaster", "end", name));
		}
	}

	/** The line {@code prefix.what} of the disaster's own keys, or the engine's {@code fallback.what} when it names none. */
	private static Component text(String prefix, String fallback, String what, Object... args) {
		return Component.translatable((prefix.isEmpty() ? fallback : prefix) + "." + what, args);
	}

	/** Who foretells {@code disaster} in the village: a villager of one of its {@code foretold_by} jobs, or 32.14's source for {@code seer}. */
	public static Optional<Component> foreteller(ServerLevel level, BlockPos hall, Disaster disaster, VillageHalls.Census census) {
		for (String by : disaster.foretoldBy()) {
			if (by.equals(Disaster.SEER)) {
				Optional<io.github.jcondedata.aliveworkplace.guard.Warnings.Source> source = io.github.jcondedata.aliveworkplace.guard.Warnings.source(level, hall);
				if (source.isPresent()) {
					return Optional.of(source.get().who());
				}
				continue;
			}
			ResourceLocation job = ResourceLocation.parse(by);
			for (Villager v : villagers(census)) {
				if (job.equals(BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().getProfession()))) {
					return Optional.of(v.getName());
				}
			}
		}
		return Optional.empty();
	}

	private static void tell(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, Component text) {
		double reach = (double) (VillageHalls.RADIUS + 32) * (VillageHalls.RADIUS + 32);
		List<ServerPlayer> told = new ArrayList<>(level.getPlayers(p -> p.blockPosition().distSqr(hall) <= reach));
		if (entity.owner() != null) {
			ServerPlayer owner = level.getServer().getPlayerList().getPlayer(entity.owner());
			if (owner != null && !told.contains(owner)) {
				told.add(owner);
			}
		}
		told.forEach(p -> p.sendSystemMessage(text));
	}

	// The effects.

	private static void afflict(ServerLevel level, BlockPos hall, Disaster disaster) {
		AFFLICTED.computeIfAbsent(level.dimension().location().toString(), k -> new ConcurrentHashMap<>())
			.put(hall.immutable(), new Afflicted(hall.immutable(), VillageHalls.area(hall), disaster));
	}

	private static void unafflict(ServerLevel level, BlockPos hall) {
		Map<BlockPos, Afflicted> halls = AFFLICTED.get(level.dimension().location().toString());
		if (halls != null) {
			halls.remove(hall);
		}
	}

	/** The hall with a disaster in effect whose area holds {@code pos} and whose disaster has an effect of {@code type}. */
	public static Optional<Afflicted> afflicted(ServerLevel level, BlockPos pos, Disaster.Effect.Type type) {
		if (AFFLICTED.isEmpty()) {
			return Optional.empty();
		}
		Map<BlockPos, Afflicted> halls = AFFLICTED.get(level.dimension().location().toString());
		if (halls == null || halls.isEmpty()) {
			return Optional.empty();
		}
		for (Afflicted a : halls.values()) {
			if (a.area().contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) && a.disaster().effect(type).isPresent()) {
				return Optional.of(a);
			}
		}
		return Optional.empty();
	}

	/**
	 * {@code crop_growth}: whether the crop, stem or berry bush at {@code pos} may grow on this random tick (its hall's
	 * {@code factor} of the time; always on farmland a Well Keeper watered today).
	 */
	public static boolean cropGrows(ServerLevel level, BlockPos pos, RandomSource random) {
		Optional<Afflicted> a = afflicted(level, pos, Disaster.Effect.Type.CROP_GROWTH);
		if (a.isEmpty() || Drought.watered(level, pos.below())) {
			return true; // a Well Keeper's bucket (32.17): the crops on watered farmland grow at the full pace that day
		}
		return random.nextDouble() < a.get().disaster().effect(Disaster.Effect.Type.CROP_GROWTH).get().factor();
	}

	/** {@code farmland_dries}: whether the farmland at {@code pos} dries on this random tick, water or not. */
	public static boolean farmlandDries(ServerLevel level, BlockPos pos) {
		return afflicted(level, pos, Disaster.Effect.Type.FARMLAND_DRIES).isPresent();
	}

	/** The once-a-day effects: cauldrons drain, fires start, the ill fall ill. */
	public static void daily(ServerLevel level, BlockPos hall, Disaster disaster, VillageHalls.Census census, RandomSource random) {
		disaster.effect(Disaster.Effect.Type.CAULDRONS_DRAIN).ifPresent(e -> drainCauldrons(level, hall, e.amount()));
		disaster.effect(Disaster.Effect.Type.FIRES_A_DAY).ifPresent(e -> {
			for (int i = 0; i < e.amount(); i++) {
				startFire(level, hall, random);
			}
		});
		disaster.effect(Disaster.Effect.Type.ILLNESS_CHANCE).ifPresent(e -> {
			for (Villager v : villagers(census)) {
				if (!Sickness.isIll(v) && random.nextFloat() < Sickness.dailyChance(level, v) * Math.max(0, e.factor() - 1)) {
					Sickness.fallIll(level, v);
				}
			}
		});
	}

	private static List<Villager> villagers(VillageHalls.Census census) {
		List<Villager> all = new ArrayList<>(census.workers());
		all.addAll(census.jobless());
		all.removeIf(v -> !v.isAlive() || v.isBaby());
		return all;
	}

	/** {@code cauldrons_drain}: every water cauldron in the hall's area loses {@code levels} (found as POIs: nothing scans the area). */
	public static int drainCauldrons(ServerLevel level, BlockPos hall, int levels) {
		List<BlockPos> cauldrons = level.getPoiManager().getInRange(h -> h.is(PoiTypes.LEATHERWORKER), hall, VillageHalls.RADIUS, PoiManager.Occupancy.ANY)
			.map(r -> r.getPos()).toList();
		int drained = 0;
		for (BlockPos p : cauldrons) {
			for (int i = 0; i < levels; i++) {
				BlockState state = level.getBlockState(p);
				if (!state.is(Blocks.WATER_CAULDRON)) {
					break;
				}
				LayeredCauldronBlock.lowerFillLevel(state, level, p);
				drained++;
			}
		}
		return drained;
	}

	/** {@code fires_a_day}: one fire on top of a finished build of the village (or by the hall without one); false if none would take. */
	public static boolean startFire(ServerLevel level, BlockPos hall, RandomSource random) {
		List<BuildSiteManager.Finished> builds = BuildSiteManager.get(level).finishedNear(level, hall, VillageHalls.RADIUS);
		for (int tries = 0; tries < 8; tries++) {
			BlockPos around = builds.isEmpty() ? hall : builds.get(random.nextInt(builds.size())).placement().origin();
			int x = around.getX() + random.nextInt(13) - 6;
			int z = around.getZ() + random.nextInt(13) - 6;
			BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
			if (level.isEmptyBlock(at) && BaseFireBlock.canBePlacedAt(level, at, Direction.UP)) {
				level.setBlock(at, BaseFireBlock.getState(level, at), 11);
				return true;
			}
		}
		return false;
	}

	/** {@code contagion}, every round: each ill villager passes it on to the well within {@code range}, each by {@code chance}. */
	public static void contagion(ServerLevel level, BlockPos hall, Disaster disaster, VillageHalls.Census census, RandomSource random) {
		Optional<Disaster.Effect> effect = disaster.effect(Disaster.Effect.Type.CONTAGION);
		if (effect.isEmpty()) {
			return;
		}
		List<Villager> all = villagers(census);
		if (Plague.is(disaster)) {
			Plague.spread(level, effect.get(), all, random); // the plague's own rule (32.18): one roll a round, the Physician's seen kept home
			return;
		}
		List<Villager> ill = all.stream().filter(Sickness::isIll).toList();
		double range = effect.get().range() * effect.get().range();
		for (Villager sick : ill) {
			for (Villager v : all) {
				if (!Sickness.isIll(v) && v.distanceToSqr(sick) <= range && random.nextDouble() < effect.get().factor()) {
					Sickness.fallIll(level, v);
				}
			}
		}
	}

	/** {@code mood}: the reason and points for a villager in an afflicted village, unless it has one of the {@code unless_built} builds. */
	@Nullable
	public static LegendPowers.MoodReason mood(ServerLevel level, Villager villager) {
		Optional<Afflicted> a = afflicted(level, villager.blockPosition(), Disaster.Effect.Type.MOOD);
		if (a.isEmpty()) {
			return null;
		}
		Disaster.Effect e = a.get().disaster().effect(Disaster.Effect.Type.MOOD).get();
		if (!e.unlessBuilt().isEmpty()) {
			for (BuildSiteManager.Finished f : BuildSiteManager.get(level).finishedNear(level, a.get().hall(), VillageHalls.RADIUS)) {
				if (e.unlessBuilt().contains(BlueprintStyles.base(f.structure()))) {
					return null;
				}
			}
		}
		return new LegendPowers.MoodReason(Component.translatable(e.reason(), name(a.get().disaster())), e.amount());
	}

	/** {@code job_pace}: the work-time factor of a villager of the job in an afflicted village (a factor of 0.5: twice as long). */
	public static float pace(Villager villager) {
		if (AFFLICTED.isEmpty() || !(villager.level() instanceof ServerLevel level)) {
			return 1f;
		}
		Map<BlockPos, Afflicted> halls = AFFLICTED.get(level.dimension().location().toString());
		if (halls == null || halls.isEmpty()) {
			return 1f;
		}
		ResourceLocation job = BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession());
		for (Afflicted a : halls.values()) {
			if (!a.area().contains(villager.position())) {
				continue;
			}
			for (Disaster.Effect e : a.disaster().effects()) {
				if (e.type() == Disaster.Effect.Type.JOB_PACE && e.job().isPresent() && e.job().get().equals(job) && e.factor() > 0) {
					return (float) (1 / e.factor());
				}
			}
		}
		return 1f;
	}

	// The weather and the village's wood.

	/** Days since it last rained in {@code level} (counted from the first round this server saw; 0 while it rains). */
	public static long dryDays(ServerLevel level) {
		long day = Chronicle.day(level);
		String dim = level.dimension().location().toString();
		if (raining(level)) {
			RAIN.put(dim, day);
		}
		return day - RAIN.computeIfAbsent(dim, k -> day);
	}

	/** Whether it rains in {@code level} (the weather as set, not the sky's slow fade in and out). */
	public static boolean raining(ServerLevel level) {
		return level.getLevelData().isRaining();
	}

	/** Whether it thunders in {@code level}, the same way. */
	public static boolean thundering(ServerLevel level) {
		return level.getLevelData().isThundering();
	}

	/** Sets the last day it rained (tests). */
	public static void rainedOn(ServerLevel level, long day) {
		RAIN.put(level.dimension().location().toString(), day);
	}

	/** The share of wood among the blocks of the village's finished builds, sampled round each build once a day. */
	public static double woodShare(ServerLevel level, BlockPos hall) {
		long day = Chronicle.day(level);
		String key = key(level, hall);
		double[] known = WOOD.get(key);
		if (known != null && known[0] == day) {
			return known[1];
		}
		int solid = 0;
		int wood = 0;
		List<BuildSiteManager.Finished> builds = BuildSiteManager.get(level).finishedNear(level, hall, VillageHalls.RADIUS);
		for (BuildSiteManager.Finished f : builds.subList(0, Math.min(16, builds.size()))) {
			BlockPos o = f.placement().origin();
			for (int dx = -12; dx <= 12; dx += 3) {
				for (int dz = -12; dz <= 12; dz += 3) {
					for (int dy = 0; dy <= 9; dy += 3) {
						BlockPos p = o.offset(dx, dy, dz);
						if (!level.isLoaded(p)) {
							continue;
						}
						BlockState s = level.getBlockState(p);
						if (s.isAir() || !s.getFluidState().isEmpty()) {
							continue;
						}
						solid++;
						if (s.is(BlockTags.PLANKS) || s.is(BlockTags.LOGS) || s.is(BlockTags.WOODEN_STAIRS) || s.is(BlockTags.WOODEN_SLABS)
							|| s.is(BlockTags.WOODEN_FENCES) || s.is(BlockTags.WOODEN_DOORS) || s.is(BlockTags.WOODEN_TRAPDOORS) || s.is(BlockTags.FENCE_GATES)) {
							wood++;
						}
					}
				}
			}
		}
		double share = solid == 0 ? 0 : (double) wood / solid;
		WOOD.put(key, new double[] {day, share});
		return share;
	}

	// What the hall shows.

	/** The line for the wellbeing icon and What next?: the disaster under way or foretold, or null. */
	@Nullable
	public static Component line(VillageHallBlockEntity hall, long day) {
		if (hall.disaster() == null) {
			return null;
		}
		Optional<Disaster> d = get(hall.disaster());
		if (d.isEmpty()) {
			return null;
		}
		Component name = name(d.get());
		if (day < hall.disasterSince()) {
			return Component.translatable("screen.aliveworkplace.hall.disaster_foretold", name, hall.disasterSince() - day);
		}
		if (d.get().endsWhenWell()) {
			return Component.translatable("screen.aliveworkplace.hall.disaster_well", name); // the plague (32.18)
		}
		return hall.disasterEnds() < 0 ? Component.translatable("screen.aliveworkplace.hall.disaster_rain", name)
			: Component.translatable("screen.aliveworkplace.hall.disaster", name, hall.disasterEnds() - day + 1);
	}

	/** What next?'s tips: the disaster, and the job that answers it when the village has none. */
	public static List<VillageAdvice.Tip> tips(ServerLevel level, BlockPos hall, VillageHalls.Census census) {
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) || entity.disaster() == null) {
			return List.of();
		}
		Optional<Disaster> d = get(entity.disaster());
		if (d.isEmpty()) {
			return List.of();
		}
		Item icon = switch (d.get().kind()) {
			case FIRE -> Items.FLINT_AND_STEEL;
			case PLAGUE -> Items.FERMENTED_SPIDER_EYE;
			default -> Items.DEAD_BUSH;
		};
		List<VillageAdvice.Tip> tips = new ArrayList<>();
		Component name = name(d.get());
		if (d.get().answeredBy().isPresent()) {
			ResourceLocation job = d.get().answeredBy().get();
			boolean has = villagers(census).stream().anyMatch(v -> job.equals(BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().getProfession())));
			if (!has) {
				tips.add(new VillageAdvice.Tip("disaster_answer", icon, name, Component.translatable("entity.minecraft.villager." + job.getPath())));
				return tips;
			}
		}
		tips.add(new VillageAdvice.Tip("disaster", icon, name));
		return tips;
	}

	private Disasters() {
	}
}
