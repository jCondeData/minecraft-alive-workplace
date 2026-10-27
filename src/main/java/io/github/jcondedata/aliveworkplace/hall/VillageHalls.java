package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Requests;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * The village a Village Hall looks after: everyone within {@link #RADIUS} blocks of it. {@link #census} counts the
 * villagers, workers, beds, food in store, guards, what the workers are waiting for and the buildings going up; the hall's
 * screen ({@link VillageHallScreen}) shows it.
 */
public final class VillageHalls {
	/** How far from the hall its village reaches. */
	public static int RADIUS = 64;
	/** How far up and down. */
	static final int HEIGHT = 32;
	/** Names made of a first and a second half ({@code village_name.aliveworkplace.first.N} + {@code .second.N}). */
	static final int FIRST_HALVES = 20;
	static final int SECOND_HALVES = 20;

	/** A building going up in the village: what, how far along, and who's building it. */
	public record Build(BuildSite site, Component name, float progress, @Nullable Villager builder) {
	}

	/** The village at a glance. */
	public record Census(List<Villager> workers, List<Villager> jobless, int children, int beds, int freeBeds, long food, int guards,
						 List<Requests.Request> requests, List<Build> builds, int wagonBeds) {
		/** B206: the village is full: no bed in its houses is free, so nobody new moves in. */
		public boolean full() {
			return freeBeds <= 0;
		}

		public int villagers() {
			return workers.size() + jobless.size() + children;
		}
	}

	/** Counts the village round the hall at {@code hall}. */
	public static Census census(ServerLevel level, BlockPos hall) {
		List<Villager> workers = new ArrayList<>();
		List<Villager> jobless = new ArrayList<>();
		int children = 0;
		int guards = 0;
		for (Villager v : villagers(level, hall)) {
			if (CaravanSights.isParty(v)) {
				continue; // a caravan's carter (33.7) is passing through: nobody's villager
			}
			if (v.isBaby()) {
				children++;
				continue;
			}
			VillagerProfession job = v.getVillagerData().getProfession();
			if (job == VillagerProfession.NONE || job == VillagerProfession.NITWIT || v.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty()) {
				jobless.add(v);
			} else {
				workers.add(v);
				if (job == ModVillagers.GUARD) {
					guards++;
				}
			}
		}
		workers.sort(Comparator.comparing((Villager v) -> v.getVillagerData().getProfession().name())
			.thenComparing(v -> -v.getVillagerData().getLevel()).thenComparing(v -> v.distanceToSqr(hall.getCenter())));
		jobless.sort(Comparator.comparingDouble(v -> v.distanceToSqr(hall.getCenter())));
		// B206, B207: a settlers' wagon's bedrolls are for the road, not the village's beds
		int wagonBeds = io.github.jcondedata.aliveworkplace.camp.CampBeds.inVillage(level, hall).size();
		int beds = (int) beds(level, hall).count() - wagonBeds;
		int freeBeds = io.github.jcondedata.aliveworkplace.camp.CampBeds.freeHomes(level, hall, RADIUS).size();
		List<Requests.Request> requests = new ArrayList<>();
		for (Villager worker : workers) {
			requests.addAll(Requests.of(level, worker));
		}
		return new Census(List.copyOf(workers), List.copyOf(jobless), children, beds, freeBeds, food(level, hall), guards, requests,
			builds(level, hall), wagonBeds);
	}

	/** How many kinds of meal the store has (see {@link io.github.jcondedata.aliveworkplace.people.Diet}). */
	public static int mealKinds(ServerLevel level, BlockPos hall) {
		int kinds = 0;
		for (var e : SupplyContainers.contents(level, VillageNeeds.store(level, hall)).entrySet()) {
			if (e.getValue() > 0 && VillageNeeds.isMeal(new ItemStack(e.getKey()))) {
				kinds++;
			}
		}
		return kinds;
	}

	/**
	 * The food in the store: the meals in the chests by the Storehouses and kitchens (smokers) of the village, counted
	 * the way growth counts them ({@link VillageGrowth#meals}), so rotten flesh and golden food never show (B153).
	 */
	public static long food(ServerLevel level, BlockPos hall) {
		return VillageGrowth.meals(level, VillageNeeds.store(level, hall));
	}

	/** The buildings going up in the village (not the ones waiting in a builder's queue). */
	static List<Build> builds(ServerLevel level, BlockPos hall) {
		List<Build> out = new ArrayList<>();
		for (BuildSite site : BuildSiteManager.get(level).all()) {
			if (site.isDone() || site.isQueued() || !site.placement().dimension().equals(Ids.of(level.dimension()))
				|| !io.github.jcondedata.aliveworkplace.work.Reach.claims(level, hall,
					io.github.jcondedata.aliveworkplace.build.Builders.spot(level, site.structure(), site.placement()))) { // B616: the claimed area
				continue;
			}
			BuildPlan plan = site.plan(level);
			Villager builder = site.builder() != null && level.getEntity(site.builder()) instanceof Villager v ? v : null;
			out.add(new Build(site, io.github.jcondedata.aliveworkplace.blueprint.Blueprints.displayName(site.structure()),
				plan == null ? 0f : site.progress(plan), builder));
		}
		out.sort(Comparator.comparingDouble(b -> b.site().placement().origin().distSqr(hall)));
		return out;
	}

	/** A workstation in the village nobody works at yet, and the job it gives. */
	public record FreeStation(BlockPos pos, VillagerProfession profession, net.minecraft.core.Holder<net.minecraft.world.entity.ai.village.poi.PoiType> poi) {
	}

	/**
	 * The village's free workstations, nearest the hall first: every block a jobless villager takes by themselves (the
	 * game's acquirable_job_site tag) and every block of ours a job is given at by an item or the hall (B635: a crafting
	 * table, a jukebox, a Mailbox are left out of that tag on purpose, so nobody takes them by themselves, and so the hall
	 * never listed the Carpenter's Workshop's table and nobody could be made a Carpenter from it).
	 */
	public static List<FreeStation> freeStations(ServerLevel level, BlockPos hall) {
		List<FreeStation> out = new ArrayList<>();
		// The job a kind of workstation gives, found once per kind rather than once per workstation (B103).
		java.util.Map<net.minecraft.core.Holder<net.minecraft.world.entity.ai.village.poi.PoiType>, Optional<VillagerProfession>> jobs = new java.util.HashMap<>();
		level.getPoiManager().getInRange(VillageHalls::jobBlock, hall, RADIUS, PoiManager.Occupancy.HAS_SPACE)
			.forEach(record -> jobs.computeIfAbsent(record.getPoiType(), VillageHalls::firstJob)
				.ifPresent(p -> out.add(new FreeStation(record.getPos().immutable(), p, record.getPoiType()))));
		out.sort(Comparator.comparingDouble(f -> f.pos().distSqr(hall)));
		return out;
	}

	/** Whether {@code poi} is a kind of block the hall lists when free (as {@link #freeStations}). */
	private static boolean jobBlock(net.minecraft.core.Holder<net.minecraft.world.entity.ai.village.poi.PoiType> poi) {
		return poi.is(net.minecraft.tags.PoiTypeTags.ACQUIRABLE_JOB_SITE)
			|| io.github.jcondedata.aliveworkplace.work.Stations.ALL.stream().anyMatch(s -> !s.byItself() && s.poi().test(poi));
	}

	/**
	 * The job {@code poi} is listed under: the first job in the registry that takes it; for a block only an item gives a
	 * job at (B635), the first one this game has (its config on).
	 */
	private static Optional<VillagerProfession> firstJob(net.minecraft.core.Holder<net.minecraft.world.entity.ai.village.poi.PoiType> poi) {
		boolean tagged = poi.is(net.minecraft.tags.PoiTypeTags.ACQUIRABLE_JOB_SITE);
		return net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.stream()
			.filter(p -> p != VillagerProfession.NONE && p != VillagerProfession.NITWIT && p.acquirableJobSite().test(poi))
			.filter(p -> tagged || io.github.jcondedata.aliveworkplace.work.Stations.available(p))
			.findFirst();
	}

	/**
	 * Every job {@code station}'s block offers, its own (first) job first (B303: the Free job blocks page lets the player
	 * choose): then the block's other jobs in {@link io.github.jcondedata.aliveworkplace.work.Stations#ALL}'s order (those
	 * whose job keeps that block, so the job stays), then any other job, another mod's, that takes and keeps that kind of
	 * block. Only jobs this game has ({@code Stations.available}), never a nitwit or the Trainer Leader (a gold block makes one).
	 */
	public static List<VillagerProfession> jobs(FreeStation station) {
		net.minecraft.core.Holder<net.minecraft.world.entity.ai.village.poi.PoiType> poi = station.poi();
		List<VillagerProfession> out = new ArrayList<>();
		out.add(station.profession());
		io.github.jcondedata.aliveworkplace.work.Stations.ALL.stream().filter(s -> s.poi().test(poi)).findFirst().ifPresent(s -> {
			for (io.github.jcondedata.aliveworkplace.work.Stations.Job job : s.jobs()) {
				VillagerProfession p = job.profession().get();
				if (offered(p, out) && p.heldJobSite().test(poi)) {
					out.add(p);
				}
			}
		});
		for (VillagerProfession p : net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION) {
			if (offered(p, out) && p.acquirableJobSite().test(poi) && p.heldJobSite().test(poi)) {
				out.add(p);
			}
		}
		return out;
	}

	private static boolean offered(VillagerProfession p, List<VillagerProfession> already) {
		return !already.contains(p) && p != VillagerProfession.NONE && p != VillagerProfession.NITWIT
			&& p != io.github.jcondedata.aliveworkplace.registry.ModVillagers.TRAINER_LEADER
			&& io.github.jcondedata.aliveworkplace.work.Stations.available(p);
	}

	/** Gives {@code villager} the job at {@code station}, if it's still free and they can work (not a nitwit or a child). */
	public static boolean assign(ServerLevel level, Villager villager, FreeStation station) {
		if (!villager.isAlive() || villager.isBaby() || villager.getVillagerData().getProfession() == VillagerProfession.NITWIT) {
			return false;
		}
		// The offered block first: if someone took it meanwhile, nothing changes and they keep their own block (bug B27).
		boolean taken = level.getPoiManager().take(h -> h.equals(station.poi()), (h, p) -> p.equals(station.pos()), station.pos(), 1).isPresent();
		if (!taken) {
			return false;
		}
		// Then whatever they had before is let go of, if it's still theirs (bugs B7, B8).
		net.minecraft.core.GlobalPos target = net.minecraft.core.GlobalPos.of(level.dimension(), station.pos());
		villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
			.filter(old -> !old.equals(target))
			.ifPresent(old -> io.github.jcondedata.aliveworkplace.work.Stations.releaseOld(level, villager, old));
		// A block they were walking to on their own is let go of, or it stays held for nobody, off the free blocks.
		io.github.jcondedata.aliveworkplace.work.Stations.forgetWalkedTo(villager, target);
		villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, net.minecraft.core.GlobalPos.of(level.dimension(), station.pos()));
		io.github.jcondedata.aliveworkplace.work.JobSiteTickets.hold(level, villager);
		io.github.jcondedata.aliveworkplace.work.WorkerLimits.order(villager, station.profession());
		villager.refreshBrain(level);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getY() + 1.2, villager.getZ(),
			8, 0.4, 0.4, 0.4, 0);
		return true;
	}

	/** How far from the hall villagers who wandered off are looked for. */
	static final int RECALL_SEARCH = 192;

	/**
	 * Calls the village's villagers home: everyone whose bed or workstation is in the village but who is out of it (and
	 * in a loaded part of the world) is brought back beside the hall. Returns how many came.
	 */
	public static int recall(ServerLevel level, BlockPos hall) {
		AABB search = new AABB(hall).inflate(RECALL_SEARCH, HEIGHT * 2, RECALL_SEARCH);
		double r2 = (double) RADIUS * RADIUS;
		int came = 0;
		for (Villager v : level.getEntitiesOfClass(Villager.class, search, Villager::isAlive)) {
			if (v.blockPosition().distSqr(hall) <= r2 || v.isPassenger()) {
				continue;
			}
			if (io.github.jcondedata.aliveworkplace.registry.ModAttachments.LEGEND.has(v)) {
				continue; // the call-home passes Legends by (ROADMAP 29.5): they come and go as they will, and never leave
			}
			boolean ours = java.util.stream.Stream.of(MemoryModuleType.HOME, MemoryModuleType.JOB_SITE)
				.map(m -> v.getBrain().getMemory(m).orElse(null))
				.anyMatch(g -> g != null && g.dimension().equals(level.dimension()) && g.pos().distSqr(hall) <= r2);
			if (!ours) {
				continue;
			}
			BlockPos spot = besideHall(level, hall, came);
			if (spot == null) {
				break;
			}
			v.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
			v.getNavigation().stop();
			v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL, v.getX(), v.getY() + 1, v.getZ(), 20, 0.3, 0.6, 0.3, 0.2);
			came++;
		}
		return came;
	}

	/** A spot to stand near the hall ({@code n}: the how-manyth, to spread them out). */
	@Nullable
	public static BlockPos besideHall(ServerLevel level, BlockPos hall, int n) {
		List<BlockPos> spots = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(hall.offset(-4, -2, -4), hall.offset(4, 2, 4))) {
			if (io.github.jcondedata.aliveworkplace.work.Walker.canStand(level, p) && !p.equals(hall)) {
				spots.add(p.immutable());
			}
		}
		if (spots.isEmpty()) {
			return null;
		}
		spots.sort(Comparator.comparingDouble(p -> p.distSqr(hall)));
		return spots.get(n % spots.size());
	}

	/** The nearest Village Hall within {@link #RADIUS} of {@code pos}. */
	public static Optional<BlockPos> nearest(ServerLevel level, BlockPos pos) {
		if (!level.getServer().isSameThread()) {
			return findNearest(level, pos);
		}
		long now = level.getGameTime();
		if (nearestLevel != level || nearestTick != now || nearestRadius != RADIUS) {
			NEAREST.clear();
			nearestLevel = level;
			nearestTick = now;
			nearestRadius = RADIUS;
		}
		long key = pos.asLong();
		Optional<BlockPos> found = NEAREST.get(key);
		if (found == null) {
			found = findNearest(level, pos);
			NEAREST.put(key, found);
		}
		return found;
	}

	private static Optional<BlockPos> findNearest(ServerLevel level, BlockPos pos) {
		return level.getPoiManager().findClosest(h -> h.is(ModVillagers.VILLAGE_HALL_POI), pos, RADIUS, PoiManager.Occupancy.ANY);
	}

	/**
	 * {@link #nearest} within one tick, by position (B103). One villager's pace asks it once for each source that
	 * depends on the village (the edicts, the horn, the guild, the hall's needs, the mood...), each a search of the
	 * points of interest within {@link #RADIUS}; the answer can't change within a tick unless a hall is placed or
	 * broken, and {@link #forgetNearest} handles that. Server thread only.
	 */
	private static final Long2ObjectOpenHashMap<Optional<BlockPos>> NEAREST = new Long2ObjectOpenHashMap<>();
	@Nullable
	private static ServerLevel nearestLevel;
	private static long nearestTick = Long.MIN_VALUE;
	private static int nearestRadius;

	/** A hall placed or broken: {@link #nearest} searches again. */
	public static void forgetNearest() {
		nearestLevel = null;
		NEAREST.clear();
	}

	/** The village's name: the hall's own (from a Name Tag or an anvil), else one made up from where it stands. */
	public static Component name(ServerLevel level, BlockPos hall) {
		if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity && entity.getCustomName() != null) {
			return entity.getCustomName();
		}
		return madeUpName(hall);
	}

	/** "Oakbrook", "Ashford"... always the same for the same spot. */
	public static Component madeUpName(BlockPos hall) {
		long seed = hall.asLong() * 0x9E3779B97F4A7C15L;
		int first = (int) Math.floorMod(seed >>> 17, (long) FIRST_HALVES);
		int second = (int) Math.floorMod(seed >>> 41, (long) SECOND_HALVES);
		return Component.translatable("village_name.aliveworkplace.format",
			Component.translatable("village_name.aliveworkplace.first." + first), Component.translatable("village_name.aliveworkplace.second." + second));
	}

	public static AABB area(BlockPos hall) {
		return new AABB(hall).inflate(RADIUS, HEIGHT, RADIUS);
	}

	/**
	 * Whether {@code pos} is in the village round {@code hall}: within {@link #RADIUS} blocks across (a circle, as the
	 * wiki tells the player) and {@link #HEIGHT} up or down. B361: the census counts its villagers and its beds by this
	 * one shape, so a villager beside his bed is never in while the bed is out.
	 */
	public static boolean contains(BlockPos hall, BlockPos pos) {
		long dx = pos.getX() - hall.getX();
		long dz = pos.getZ() - hall.getZ();
		return dx * dx + dz * dz <= (long) RADIUS * RADIUS && Math.abs(pos.getY() - hall.getY()) <= HEIGHT;
	}

	/**
	 * The hall whose village holds {@code pos} ({@link #contains}: within {@link #RADIUS} across and {@link #HEIGHT} up or
	 * down), the nearest across if two do. Unlike {@link #nearest}, a ball of {@link #RADIUS}, it finds the hall of a spot
	 * on the village's rim up a hill (B562: 60 out and 25 up is in the village but 65 blocks off in a straight line).
	 */
	public static Optional<BlockPos> containing(ServerLevel level, BlockPos pos) {
		Optional<BlockPos> near = nearest(level, pos);
		if (near.isPresent() && contains(near.get(), pos)) {
			return near;
		}
		int reach = (int) Math.ceil(Math.sqrt((double) RADIUS * RADIUS + (double) HEIGHT * HEIGHT));
		return level.getPoiManager().getInRange(h -> h.is(ModVillagers.VILLAGE_HALL_POI), pos, reach, PoiManager.Occupancy.ANY)
			.map(net.minecraft.world.entity.ai.village.poi.PoiRecord::getPos)
			.filter(h -> contains(h, pos))
			.min(Comparator.comparingLong(h -> across(h, pos)));
	}

	/** The square of the distance from {@code a} to {@code b} across (east-west and north-south only). */
	public static long across(BlockPos a, BlockPos b) {
		long dx = a.getX() - b.getX();
		long dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}

	/** The living villagers in the village round {@code hall} ({@link #contains}). */
	public static List<Villager> villagers(ServerLevel level, BlockPos hall) {
		return villagers(level, hall, v -> true);
	}

	/**
	 * The living villagers in the village round {@code hall} ({@link #contains}) that pass {@code filter}. The village is
	 * the circle, not the square {@link #area} round it (B548, B549: a villager in the square's corner is in no village).
	 */
	public static List<Villager> villagers(ServerLevel level, BlockPos hall, java.util.function.Predicate<Villager> filter) {
		return level.getEntitiesOfClass(Villager.class, area(hall), v -> v.isAlive() && contains(hall, v.blockPosition()) && filter.test(v));
	}

	/** The beds (home points of interest, taken or not) in the village round {@code hall} ({@link #contains}). */
	public static java.util.stream.Stream<BlockPos> beds(ServerLevel level, BlockPos hall) {
		int reach = (int) Math.ceil(Math.sqrt((double) RADIUS * RADIUS + (double) HEIGHT * HEIGHT));
		return level.getPoiManager().getInRange(h -> h.is(PoiTypes.HOME), hall, reach, PoiManager.Occupancy.ANY)
			.map(net.minecraft.world.entity.ai.village.poi.PoiRecord::getPos).filter(p -> contains(hall, p));
	}

	private VillageHalls() {
	}
}
