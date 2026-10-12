package io.github.jcondedata.aliveworkplace.threat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mixin.FireBlockAccessor;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Partners;
import io.github.jcondedata.aliveworkplace.work.PokemonPartners;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The village fire (ROADMAP 32.16), the disaster file {@code disasters/village_fire.json} ({@link Disaster.Ignition}):
 * a lightning strike in a village in a thunderstorm, a chimney spark on a dry day and a raider's burning arrow set a
 * finished wooden build alight, likelier the more wood the village is built of. The game's own fire does the rest
 * ({@code doFireTick}), but in a village it only ever burns or spreads to blocks the builders placed (a block at a
 * finished build's blueprint position that still matches it, {@link #builderMade}), so players' own builds are safe; with
 * {@code siegeDamage} off it burns nothing at all and goes out where it started.
 *
 * <p>Fires are noticed where they're placed (the fire block's {@code onPlace}, through a mixin), never by scanning: each
 * village with fire has an {@link Incident} listing its fire blocks. Firewardens ({@code work/FirewardenWork}) run to the
 * nearest, a bucket brigade of villagers off work joins them from Journeyman, and with Cobblemon their pastured Water
 * types douse fires at range. When the last fire is out the chronicle says what burned and who put it out, and the
 * builders put back what burned ({@code build/Upkeep}), even with {@code builderRepairs} off: every burned block is
 * saved on {@link ThreatData#damaged} (32.16a), so a fire never destroys a build for good.
 *
 * <p>All in memory: after a restart the fires still burning are noticed again as they spread.
 */
public final class VillageFire {
	public static final ResourceLocation ID = AliveWorkplace.id("village_fire");
	/** How far from their grindstone a firewarden answers a fire. */
	public static final int ANSWER = 48;
	/** Fire within this many blocks of a splash goes out. */
	public static final int SPLASH = 2;
	/** From Journeyman a firewarden's bell calls this many villagers off work within {@link #BRIGADE_REACH} of the fire. */
	public static final int BRIGADE_SIZE = 2;
	public static final int BRIGADE_REACH = 24;
	/** A brigade villager puts out a fire block this often (ticks), and a Water-type partner uses a move this often. */
	public static final int BRIGADE_EVERY = 40;
	public static final int PARTNER_EVERY = 40;
	/** How far a partner's move reaches from where it stands. */
	public static final double PARTNER_REACH = 20;
	/** Ticks with no fire left before a village's fire is over (and goes into the chronicle). */
	public static final int QUIET = 40;
	/** The credit key of the bucket brigade. */
	private static final UUID BRIGADE = new UUID(0, 1632);

	/** A fire in a village, from its first flame to the last put out. */
	public static final class Incident {
		final BlockPos hall;
		final LongOpenHashSet fires = new LongOpenHashSet();
		final Component place;
		final long started;
		long emptySince = -1;
		int burned;
		boolean rung;
		final Map<UUID, Integer> doused = new HashMap<>();
		final Map<UUID, Component> names = new HashMap<>();

		Incident(BlockPos hall, Component place, long started) {
			this.hall = hall;
			this.place = place;
			this.started = started;
		}

		public BlockPos hall() {
			return hall;
		}

		/** Builders' blocks the fire has burned so far. */
		public int burned() {
			return burned;
		}

		/** The fire blocks known to burn now. */
		public int fires() {
			return fires.size();
		}

		/** Fire blocks put out by the villager {@code id} (or the brigade, or a firewarden's partners: theirs). */
		public int dousedBy(UUID id) {
			return doused.getOrDefault(id, 0);
		}

		public boolean rung() {
			return rung;
		}
	}

	private record HallAt(long until, @Nullable BlockPos hall) {
	}

	private record Built(BoundingBox box, BuildSiteManager.Finished finished) {
	}

	private record Index(int changes, List<Built> builds) {
	}

	/** A villager called to a village's fire by the bell: which village (hall) and dimension. */
	private record Duty(String dimension, BlockPos hall) {
	}

	/** The open fires, by dimension and hall. */
	private static final Map<String, Map<BlockPos, Incident>> INCIDENTS = new ConcurrentHashMap<>();
	/** Which hall's area each chunk lies in (looked up by the hall's POI, kept for a few seconds). */
	private static final Map<String, Map<Long, HallAt>> HALLS = new ConcurrentHashMap<>();
	private static final Map<String, Index> INDEX = new ConcurrentHashMap<>();
	/** Each finished build's blocks by position, the most recently asked for kept. */
	private static final Map<BlueprintData.Placement, Map<Long, BlockState>> PLANS = new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<BlueprintData.Placement, Map<Long, BlockState>> eldest) {
			return size() > 64;
		}
	};
	/** The ignition factor of each hall today: {@code [day, factor]}. */
	private static final Map<String, double[]> FACTOR = new ConcurrentHashMap<>();
	/** The last day each hall rolled its chimney spark. */
	private static final Map<String, Long> CHIMNEY = new ConcurrentHashMap<>();
	private static final Map<Villager, Duty> BRIGADE_DUTY = new WeakHashMap<>();
	/** Firewardens seen at work lately (their behaviour checks in every tick), for their partners' moves. */
	private static final Map<Villager, Long> FIREWARDENS = new WeakHashMap<>();
	private static final Map<Entity, Long> LAST_MOVE = new WeakHashMap<>();

	public static void init() {
		Platform.get().onServerStarting(server -> forget());
		Platform.get().onLevelTick(VillageFire::tick);
	}

	/** Forgets everything kept in memory (a new server; tests). */
	public static synchronized void forget() {
		INCIDENTS.clear();
		HALLS.clear();
		INDEX.clear();
		synchronized (PLANS) {
			PLANS.clear();
		}
		FACTOR.clear();
		CHIMNEY.clear();
		synchronized (BRIGADE_DUTY) {
			BRIGADE_DUTY.clear();
		}
		synchronized (FIREWARDENS) {
			FIREWARDENS.clear();
		}
		synchronized (LAST_MOVE) {
			LAST_MOVE.clear();
		}
	}

	/** Whether village fires are on: the M32 expansion open and {@code village_fire} loaded and switched on. */
	public static boolean live() {
		return Disasters.live() && Disasters.on(ID).isPresent();
	}

	private static String dim(ServerLevel level) {
		return level.dimension().location().toString();
	}

	// Where the villages are, and which blocks their builders placed.

	/** The hall whose village {@code pos} is in, if any (cached by chunk for a few seconds: fire asks often). */
	public static Optional<BlockPos> hall(ServerLevel level, BlockPos pos) {
		Map<Long, HallAt> halls = HALLS.computeIfAbsent(dim(level), k -> new ConcurrentHashMap<>());
		long key = (long) (pos.getX() >> 4) << 32 | ((pos.getZ() >> 4) & 0xffffffffL);
		long now = level.getGameTime();
		HallAt known = halls.get(key);
		if (known == null || now >= known.until() || now < known.until() - 200) {
			if (halls.size() > 4096) {
				halls.clear();
			}
			BlockPos centre = new BlockPos((pos.getX() & ~15) + 8, pos.getY(), (pos.getZ() & ~15) + 8);
			// (from the chunk's middle, reaching a chunk's half-diagonal further: any block of the chunk may be in its area)
			known = new HallAt(now + 100, level.getPoiManager().findClosest(h -> h.is(ModVillagers.VILLAGE_HALL_POI), centre, VillageHalls.RADIUS + 12,
				PoiManager.Occupancy.ANY).orElse(null));
			halls.put(key, known);
		}
		BlockPos hall = known.hall();
		return hall != null && VillageHalls.area(hall).contains(Vec3.atCenterOf(pos)) ? Optional.of(hall) : Optional.empty();
	}

	/** Forgets which chunks lie in which village (a hall placed or broken; tests). */
	public static void forgetHalls() {
		HALLS.clear();
	}

	/** Whether the village fire's rules hold at {@code pos}: fires are on and it lies in a village. */
	public static boolean guarded(ServerLevel level, BlockPos pos) {
		return live() && hall(level, pos).isPresent();
	}

	private static List<Built> builds(ServerLevel level) {
		BuildSiteManager manager = BuildSiteManager.get(level);
		Index index = INDEX.get(dim(level));
		if (index == null || index.changes() != manager.changes()) {
			List<Built> out = new ArrayList<>();
			for (BuildSiteManager.Finished f : manager.finishedIn(level)) {
				BlueprintLibrary.get(level, f.structure()).ifPresent(b -> out.add(new Built(BlueprintOutline.bounds(f.placement(), b.size()), f)));
			}
			index = new Index(manager.changes(), List.copyOf(out));
			INDEX.put(dim(level), index);
		}
		return index.builds();
	}

	/** Forgets the finished builds' outlines and blocks (tests that record builds of a new blueprint at an old spot). */
	public static void forgetBuilds() {
		INDEX.clear();
		synchronized (PLANS) {
			PLANS.clear();
		}
	}

	private static Map<Long, BlockState> plan(ServerLevel level, BuildSiteManager.Finished f) {
		synchronized (PLANS) {
			Map<Long, BlockState> known = PLANS.get(f.placement());
			if (known != null) {
				return known;
			}
		}
		Map<Long, BlockState> blocks = new HashMap<>();
		Optional<Blueprint> blueprint = BlueprintLibrary.get(level, f.structure());
		if (blueprint.isPresent()) {
			BuildPlan plan = BuildPlan.create(blueprint.get(), f.placement());
			for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
				for (BuildPlan.Step step : plan.steps(stage)) {
					blocks.put(step.pos().asLong(), step.state());
				}
			}
		}
		synchronized (PLANS) {
			PLANS.put(f.placement(), blocks);
		}
		return blocks;
	}

	/** The finished build whose outline holds {@code pos}, if any. */
	public static Optional<BuildSiteManager.Finished> buildAt(ServerLevel level, BlockPos pos) {
		for (Built b : builds(level)) {
			if (b.box().isInside(pos)) {
				return Optional.of(b.finished());
			}
		}
		return Optional.empty();
	}

	/** Whether the block at {@code pos} is a builder's: a finished build's blueprint block there, still standing as drawn. */
	public static boolean builderMade(ServerLevel level, BlockPos pos) {
		BlockState here = level.getBlockState(pos);
		if (here.isAir()) {
			return false;
		}
		for (Built b : builds(level)) {
			if (b.box().isInside(pos)) {
				BlockState wanted = plan(level, b.finished()).get(pos.asLong());
				if (wanted != null && !wanted.isAir() && MaterialRules.matches(here, wanted)) {
					return true;
				}
			}
		}
		return false;
	}

	/** Whether fire can take hold of {@code state} at all (vanilla's flammable blocks). */
	public static boolean flammable(BlockState state) {
		return ((FireBlockAccessor) Blocks.FIRE).aliveworkplace$canBurn(state);
	}

	// The fire's own rules (FireBlock, through the mixin).

	/**
	 * Whether the fire may burn the block at {@code pos} (and spread to the air beside it): outside a village always; in
	 * one only a builder's block, and nothing with {@code siegeDamage} off.
	 */
	public static boolean mayBurn(ServerLevel level, BlockPos pos) {
		if (!guarded(level, pos)) {
			return true;
		}
		return Sieges.DAMAGE && builderMade(level, pos);
	}

	/** Whether any block beside {@code fire} is one it may burn. */
	public static boolean anyMayBurn(ServerLevel level, BlockPos fire) {
		for (Direction d : Direction.values()) {
			BlockPos n = fire.relative(d);
			if (flammable(level.getBlockState(n)) && mayBurn(level, n)) {
				return true;
			}
		}
		return false;
	}

	/** The fire has burned the builder's block at {@code pos}: counted, and on the list the builders repair from (32.16a). */
	public static void burned(ServerLevel level, BlockPos pos) {
		ThreatDamage.took(level, pos);
		hall(level, pos).ifPresent(hall -> {
			Incident incident = incidents(level).get(hall);
			if (incident != null) {
				incident.burned++;
			}
		});
	}

	// Noticing a fire.

	private static Map<BlockPos, Incident> incidents(ServerLevel level) {
		return INCIDENTS.computeIfAbsent(dim(level), k -> new ConcurrentHashMap<>());
	}

	/** The fire burning in the village round {@code hall} now, if any. */
	public static Optional<Incident> incident(ServerLevel level, BlockPos hall) {
		return Optional.ofNullable(incidents(level).get(hall));
	}

	/**
	 * A fire block was placed at {@code pos} (the fire block's {@code onPlace}): in a village it joins the village's fire,
	 * or starts one. A fire on a block that always burns (netherrack in a hearth) is the player's own and is left alone.
	 */
	public static void noticed(ServerLevel level, BlockPos pos) {
		if (!live()) {
			return;
		}
		Optional<BlockPos> hall = hall(level, pos);
		if (hall.isEmpty() || level.getBlockState(pos.below()).is(level.dimensionType().infiniburn())) {
			return;
		}
		Incident incident = incidents(level).get(hall.get());
		if (incident == null) {
			Component place = buildAt(level, pos).map(f -> Blueprints.displayName(f.structure()))
				.orElse(Component.translatable("chronicle.aliveworkplace.village_fire.village"));
			incident = new Incident(hall.get(), place, level.getGameTime());
			incidents(level).put(hall.get(), incident);
			tell(level, hall.get(), Component.translatable("disaster.aliveworkplace.village_fire.burning", place, VillageHalls.name(level, hall.get()))
				.withStyle(ChatFormatting.RED));
		}
		incident.fires.add(pos.asLong());
		incident.emptySince = -1;
	}

	private static void tell(ServerLevel level, BlockPos hall, Component text) {
		double reach = (double) (VillageHalls.RADIUS + 32) * (VillageHalls.RADIUS + 32);
		List<ServerPlayer> told = new ArrayList<>(level.getPlayers(p -> p.blockPosition().distSqr(hall) <= reach));
		if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity && entity.owner() != null) {
			ServerPlayer owner = level.getServer().getPlayerList().getPlayer(entity.owner());
			if (owner != null && !told.contains(owner)) {
				told.add(owner);
			}
		}
		told.forEach(p -> p.sendSystemMessage(text));
	}

	/** The nearest fire known to burn within {@code radius} of {@code from} (fire blocks that went out are dropped). */
	public static Optional<BlockPos> nearestFire(ServerLevel level, BlockPos from, double radius) {
		BlockPos best = null;
		double bestDist = radius * radius;
		for (Incident incident : incidents(level).values()) {
			for (LongIterator it = incident.fires.iterator(); it.hasNext(); ) {
				BlockPos p = BlockPos.of(it.nextLong());
				if (!level.isLoaded(p)) {
					continue;
				}
				if (!level.getBlockState(p).is(BlockTags.FIRE)) {
					it.remove();
					continue;
				}
				double d = p.distSqr(from);
				if (d <= bestDist) {
					bestDist = d;
					best = p;
				}
			}
		}
		return Optional.ofNullable(best);
	}

	// Putting it out.

	/**
	 * Puts out every fire within {@code radius} of {@code at} (0: that block alone) with a hiss of steam, to the credit of
	 * {@code by} (a firewarden; null: the bucket brigade). Returns how many fire blocks went out.
	 */
	public static int douse(ServerLevel level, BlockPos at, int radius, @Nullable Villager by) {
		int out = 0;
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-radius, -radius, -radius), at.offset(radius, radius, radius))) {
			if (p.distSqr(at) > (radius + 0.5) * (radius + 0.5) || !level.getBlockState(p).is(BlockTags.FIRE)) {
				continue;
			}
			level.removeBlock(p, false);
			level.sendParticles(ParticleTypes.CLOUD, p.getX() + 0.5, p.getY() + 0.4, p.getZ() + 0.5, 4, 0.25, 0.2, 0.25, 0.02);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX() + 0.5, p.getY() + 0.3, p.getZ() + 0.5, 2, 0.2, 0.1, 0.2, 0.01);
			out++;
		}
		level.sendParticles(ParticleTypes.SPLASH, at.getX() + 0.5, at.getY() + 0.6, at.getZ() + 0.5, 30, radius * 0.5 + 0.3, 0.3, radius * 0.5 + 0.3, 0.1);
		if (out > 0) {
			level.playSound(null, at, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 0.8f, 1.6f + level.random.nextFloat() * 0.4f);
			credit(level, at, by, out);
		}
		return out;
	}

	private static void credit(ServerLevel level, BlockPos at, @Nullable Villager by, int out) {
		Optional<BlockPos> hall = hall(level, at);
		if (hall.isEmpty()) {
			return;
		}
		Incident incident = incidents(level).get(hall.get());
		if (incident == null) {
			return;
		}
		UUID id = by == null ? BRIGADE : by.getUUID();
		incident.doused.merge(id, out, Integer::sum);
		if (by != null) {
			incident.names.put(id, by.getDisplayName());
		}
	}

	/**
	 * A Journeyman firewarden rings the bell for the fire round {@code hall} (once a fire): the nearest bell swings and
	 * rings, and up to {@link #BRIGADE_SIZE} villagers off work within {@link #BRIGADE_REACH} of {@code fire} join the
	 * bucket brigade. Returns the villagers called.
	 */
	public static List<Villager> ring(ServerLevel level, Villager warden, BlockPos fire) {
		Optional<BlockPos> hall = hall(level, fire);
		Incident incident = hall.map(h -> incidents(level).get(h)).orElse(null);
		if (incident == null || incident.rung) {
			return List.of();
		}
		incident.rung = true;
		Optional<BlockPos> bell = level.getPoiManager().findClosest(h -> h.is(PoiTypes.MEETING), warden.blockPosition(), 32, PoiManager.Occupancy.ANY);
		BlockPos at = bell.orElse(warden.blockPosition());
		if (bell.isPresent() && level.getBlockState(bell.get()).is(Blocks.BELL)) {
			// The bell swings and rings, but only the brigade answers: its own ring would send the village to hide.
			level.blockEvent(bell.get(), Blocks.BELL, 1, Direction.NORTH.get3DDataValue());
		}
		level.playSound(null, at, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2f, 1f);
		List<Villager> near = level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(fire).inflate(BRIGADE_REACH),
			v -> v != warden && v.isAlive() && !v.isBaby() && !v.isSleeping() && v.distanceToSqr(Vec3.atCenterOf(fire)) <= BRIGADE_REACH * BRIGADE_REACH
				&& !v.getBrain().isActive(Activity.WORK) && v.getVillagerData().getProfession() != ModVillagers.FIREWARDEN
				&& v.getVillagerData().getProfession() != ModVillagers.GUARD && brigadeOf(v).isEmpty());
		near.sort(Comparator.comparingDouble(v -> v.distanceToSqr(Vec3.atCenterOf(fire))));
		List<Villager> called = near.subList(0, Math.min(BRIGADE_SIZE, near.size()));
		synchronized (BRIGADE_DUTY) {
			for (Villager v : called) {
				BRIGADE_DUTY.put(v, new Duty(dim(level), hall.get()));
			}
		}
		return List.copyOf(called);
	}

	/** The hall of the fire {@code villager} was called to by the bell, while it burns. */
	public static Optional<BlockPos> brigadeOf(Villager villager) {
		Duty duty;
		synchronized (BRIGADE_DUTY) {
			duty = BRIGADE_DUTY.get(villager);
		}
		if (duty == null || !(villager.level() instanceof ServerLevel level) || !duty.dimension().equals(dim(level))
			|| !incidents(level).containsKey(duty.hall())) {
			return Optional.empty();
		}
		return Optional.of(duty.hall());
	}

	/** Lets {@code villager} go back to their day. */
	public static void release(Villager villager) {
		synchronized (BRIGADE_DUTY) {
			BRIGADE_DUTY.remove(villager);
		}
	}

	/** A firewarden checks in (their behaviour, every tick): their partners will douse fires near their grindstone. */
	public static void onDuty(Villager warden, long now) {
		synchronized (FIREWARDENS) {
			FIREWARDENS.put(warden, now);
		}
	}

	// Ignition.

	/**
	 * How much likelier fire is in the village round {@code hall} today: 0 where {@code village_fire}'s {@code when} fails
	 * or the village is quiet ({@link Threats#quiet}), half at a gentle threat level and half again at a hard one.
	 */
	public static double factor(ServerLevel level, BlockPos hall) {
		return dailyFactor(level, hall) * Threats.scale(level, hall); // the threat level and At peace (32.21)
	}

	private static double dailyFactor(ServerLevel level, BlockPos hall) {
		Optional<Disaster> fire = Disasters.on(ID);
		if (fire.isEmpty() || !Disasters.live()) {
			return 0;
		}
		long day = Chronicle.day(level);
		String key = dim(level) + "|" + hall.asLong();
		double[] known = FACTOR.get(key);
		if (known != null && known[0] == day) {
			return known[1];
		}
		VillageHalls.Census census = VillageHalls.census(level, hall);
		double factor = 0;
		if (fire.get().when().test(level, hall, census)) {
			factor = 1;
			for (Disaster.Factor f : fire.get().factors()) {
				if (f.iff().test(level, hall, census)) {
					factor *= f.times();
				}
			}
		}
		FACTOR.put(key, new double[] {day, factor});
		return factor;
	}

	/** Forgets today's factors (tests that change a village's builds). */
	public static void forgetFactors() {
		FACTOR.clear();
	}

	private static Disaster.Ignition ignition() {
		return Disasters.on(ID).map(Disaster::ignition).orElse(Disaster.Ignition.NONE);
	}

	/**
	 * A lightning bolt struck {@code at}: in a village in a thunderstorm, by the {@code lightning} chance, the roof of the
	 * finished wooden build nearest the strike catches. Returns the fire, if one was set.
	 */
	public static Optional<BlockPos> lightning(ServerLevel level, BlockPos at, RandomSource random) {
		if (!live() || !Disasters.thundering(level)) {
			return Optional.empty();
		}
		Optional<BlockPos> hall = hall(level, at);
		if (hall.isEmpty() || random.nextDouble() >= ignition().lightning() * factor(level, hall.get())) {
			return Optional.empty();
		}
		return igniteNear(level, hall.get(), at, random);
	}

	/**
	 * The hall's daily round: in a drought (the {@code chimney_when} conditions, or a drought disaster under way), by the
	 * {@code chimney} chance, a chimney spark sets one of the village's wooden builds alight. Once a day per village.
	 */
	public static Optional<BlockPos> chimney(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, VillageHalls.Census census,
			RandomSource random) {
		if (!live() || !Disasters.ROLLING) {
			return Optional.empty();
		}
		long day = Chronicle.day(level);
		Long rolled = CHIMNEY.put(dim(level) + "|" + hall.asLong(), day);
		if (rolled != null && rolled == day) {
			return Optional.empty();
		}
		return chimneySpark(level, hall, entity, census, random);
	}

	/** Today's chimney roll itself (tests call it straight). */
	public static Optional<BlockPos> chimneySpark(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, VillageHalls.Census census,
			RandomSource random) {
		Disaster.Ignition ignition = ignition();
		boolean drought = ignition.chimneyWhen().test(level, hall, census) && ignition.chimneyWhen() != DisasterConditions.ANY
			|| entity.disaster() != null && Chronicle.day(level) >= entity.disasterSince()
				&& Disasters.get(entity.disaster()).map(d -> d.kind() == Chronicle.Kind.DROUGHT).orElse(false);
		if (!drought || random.nextDouble() >= ignition.chimney() * factor(level, hall)) {
			return Optional.empty();
		}
		List<BuildSiteManager.Finished> builds = BuildSiteManager.get(level).finishedNear(level, hall, VillageHalls.RADIUS);
		if (builds.isEmpty()) {
			return Optional.empty();
		}
		BuildSiteManager.Finished f = builds.get(random.nextInt(builds.size()));
		return igniteRoof(level, f, f.placement().origin(), random);
	}

	/**
	 * A burning arrow hit a block: shot by a raider ({@link Threats#RAIDER_TAG}) with {@code mobGriefing} on, at a
	 * builder's wooden block in a village, by the {@code burning_arrow} chance it catches. Returns the fire, if set.
	 */
	public static Optional<BlockPos> arrow(ServerLevel level, @Nullable Entity shooter, BlockHitResult hit, RandomSource random) {
		if (!live() || shooter == null || !shooter.getTags().contains(Threats.RAIDER_TAG)
			|| !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
			return Optional.empty();
		}
		BlockPos pos = hit.getBlockPos();
		Optional<BlockPos> hall = hall(level, pos);
		if (hall.isEmpty() || !flammable(level.getBlockState(pos)) || !builderMade(level, pos)
			|| random.nextDouble() >= ignition().burningArrow() * factor(level, hall.get())) {
			return Optional.empty();
		}
		BlockPos at = pos.relative(hit.getDirection());
		if (!level.isEmptyBlock(at) || !BaseFireBlock.canBePlacedAt(level, at, hit.getDirection())) {
			return Optional.empty();
		}
		level.setBlock(at, BaseFireBlock.getState(level, at), 11);
		return Optional.of(at);
	}

	/** Sets alight the roof of the finished wooden build of the village nearest {@code near}. */
	public static Optional<BlockPos> igniteNear(ServerLevel level, BlockPos hall, BlockPos near, RandomSource random) {
		List<BuildSiteManager.Finished> builds = new ArrayList<>(BuildSiteManager.get(level).finishedNear(level, hall, VillageHalls.RADIUS));
		builds.sort(Comparator.comparingDouble(f -> f.placement().origin().distSqr(near)));
		for (BuildSiteManager.Finished f : builds) {
			Optional<BlockPos> fire = igniteRoof(level, f, near, random);
			if (fire.isPresent()) {
				return fire;
			}
		}
		return Optional.empty();
	}

	/**
	 * Sets the roof of {@code build} alight: on top of its highest wooden block still standing, the one nearest
	 * {@code near} among those. Empty when it has no wood to catch.
	 */
	public static Optional<BlockPos> igniteRoof(ServerLevel level, BuildSiteManager.Finished build, BlockPos near, RandomSource random) {
		BlockPos best = null;
		for (Map.Entry<Long, BlockState> e : plan(level, build).entrySet()) {
			BlockPos p = BlockPos.of(e.getKey());
			if (!flammable(e.getValue()) || !level.isLoaded(p)) {
				continue;
			}
			BlockState here = level.getBlockState(p);
			if (!MaterialRules.matches(here, e.getValue()) || !level.isEmptyBlock(p.above())) {
				continue;
			}
			if (best == null || p.getY() > best.getY()
				|| p.getY() == best.getY() && horizontal(p, near) < horizontal(best, near)) {
				best = p;
			}
		}
		if (best == null) {
			return Optional.empty();
		}
		BlockPos at = best.above();
		level.setBlock(at, BaseFireBlock.getState(level, at), 11);
		level.sendParticles(ParticleTypes.LAVA, at.getX() + 0.5, at.getY() + 0.2, at.getZ() + 0.5, 6, 0.3, 0.1, 0.3, 0.05);
		return Optional.of(at);
	}

	private static double horizontal(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}

	// Every tick: partners' moves, and fires that are over.

	private static void tick(ServerLevel level) {
		Map<BlockPos, Incident> open = INCIDENTS.get(dim(level));
		if (open == null || open.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		if (now % PARTNER_EVERY == 0) {
			partners(level, now);
		}
		if (now % 10 != 0) {
			return;
		}
		for (Incident incident : List.copyOf(open.values())) {
			for (LongIterator it = incident.fires.iterator(); it.hasNext(); ) {
				BlockPos p = BlockPos.of(it.nextLong());
				if (level.isLoaded(p) && !level.getBlockState(p).is(BlockTags.FIRE)) {
					it.remove();
				}
			}
			if (!incident.fires.isEmpty()) {
				incident.emptySince = -1;
				continue;
			}
			if (incident.emptySince < 0) {
				incident.emptySince = now;
			} else if (now - incident.emptySince >= QUIET) {
				open.remove(incident.hall);
				over(level, incident);
			}
		}
	}

	/** The village's fire is out: into the chronicle with what burned and who put it out; the brigade goes home. */
	private static void over(ServerLevel level, Incident incident) {
		UUID top = null;
		int most = 0;
		for (Map.Entry<UUID, Integer> e : incident.doused.entrySet()) {
			if (!e.getKey().equals(BRIGADE) && e.getValue() > most) {
				top = e.getKey();
				most = e.getValue();
			}
		}
		String one = incident.burned == 1 ? "_one" : "";
		Component text;
		if (top != null) {
			text = Component.translatable("chronicle.aliveworkplace.village_fire.out" + one, incident.place, incident.burned, incident.names.get(top));
		} else if (incident.doused.containsKey(BRIGADE)) {
			text = Component.translatable("chronicle.aliveworkplace.village_fire.brigade" + one, incident.place, incident.burned);
		} else {
			text = Component.translatable("chronicle.aliveworkplace.village_fire.burned_out" + one, incident.place, incident.burned);
		}
		Chronicle.atHall(level, incident.hall, Chronicle.Kind.FIRE, text);
		tell(level, incident.hall, Component.translatable("disaster.aliveworkplace.village_fire.out", incident.place, VillageHalls.name(level, incident.hall))
			.withStyle(ChatFormatting.GREEN));
		synchronized (BRIGADE_DUTY) {
			BRIGADE_DUTY.values().removeIf(d -> d.hall().equals(incident.hall) && d.dimension().equals(dim(level)));
		}
	}

	/**
	 * With Cobblemon: Water types pastured within {@link Partners#RADIUS} of a firewarden's grindstone use a move on the
	 * nearest fire within {@link #PARTNER_REACH} every {@link #PARTNER_EVERY} ticks, as guards' partners strike: the
	 * Pokémon's own animation, Cobblemon's water impact at the fire, and the fire there goes out. Nothing without it.
	 */
	private static void partners(ServerLevel level, long now) {
		List<Villager> wardens;
		synchronized (FIREWARDENS) {
			wardens = FIREWARDENS.entrySet().stream().filter(e -> now - e.getValue() < 100 && e.getKey().level() == level && e.getKey().isAlive())
				.map(Map.Entry::getKey).toList();
		}
		for (Villager warden : wardens) {
			BlockPos post = warden.getBrain().getMemory(MemoryModuleType.JOB_SITE).filter(g -> g.dimension() == level.dimension())
				.map(GlobalPos::pos).orElse(null);
			if (post == null || nearestFire(level, post, ANSWER).isEmpty()) {
				continue;
			}
			for (PokemonPartners.Fighter fighter : PokemonPartners.EXTENSION.call(p -> p.fighters(level, post, Partners.RADIUS,
					Set.of("water"), Partners.MAX), List.<PokemonPartners.Fighter>of())) {
				partnerDouses(level, warden, fighter.entity(), now);
			}
		}
	}

	/** One Water-type partner's move at the nearest fire in reach; true if it used one. */
	public static boolean partnerDouses(ServerLevel level, Villager warden, LivingEntity pokemon, long now) {
		synchronized (LAST_MOVE) {
			Long last = LAST_MOVE.get(pokemon);
			if (last != null && now - last < PARTNER_EVERY) {
				return false;
			}
		}
		Optional<BlockPos> fire = nearestFire(level, pokemon.blockPosition(), PARTNER_REACH);
		if (fire.isEmpty()) {
			return false;
		}
		synchronized (LAST_MOVE) {
			LAST_MOVE.put(pokemon, now);
		}
		Vec3 to = Vec3.atCenterOf(fire.get());
		Vec3 from = pokemon.position().add(0, pokemon.getBbHeight() * 0.6, 0);
		int steps = Math.max(4, (int) (from.distanceTo(to) * 2));
		for (int i = 1; i <= steps; i++) {
			Vec3 p = from.lerp(to, i / (double) steps);
			level.sendParticles(ParticleTypes.SPLASH, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0);
		}
		PokemonPartners.EXTENSION.run(p -> p.animate(level, pokemon, "special"));
		PokemonPartners.EXTENSION.run(p -> p.effect(level, ResourceLocation.fromNamespaceAndPath("cobblemon", "impact_water"), to));
		douse(level, fire.get(), 1, warden);
		return true;
	}

	/** Forgets the partners' move cooldowns (tests). */
	public static void resetPartners() {
		synchronized (LAST_MOVE) {
			LAST_MOVE.clear();
		}
	}

	private VillageFire() {
	}
}
