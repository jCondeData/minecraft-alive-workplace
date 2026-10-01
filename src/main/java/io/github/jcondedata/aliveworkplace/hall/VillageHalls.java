package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Requests;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
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
						 List<Requests.Request> requests, List<Build> builds) {
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
		for (Villager v : level.getEntitiesOfClass(Villager.class, area(hall), Villager::isAlive)) {
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
		PoiManager poi = level.getPoiManager();
		int beds = (int) poi.getCountInRange(h -> h.is(PoiTypes.HOME), hall, RADIUS, PoiManager.Occupancy.ANY);
		int freeBeds = (int) poi.getCountInRange(h -> h.is(PoiTypes.HOME), hall, RADIUS, PoiManager.Occupancy.HAS_SPACE);
		List<Requests.Request> requests = new ArrayList<>();
		for (Villager worker : workers) {
			requests.addAll(Requests.of(level, worker));
		}
		return new Census(List.copyOf(workers), List.copyOf(jobless), children, beds, freeBeds, food(level, hall), guards, requests,
			builds(level, hall));
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

	/** The food in the store: whatever can be eaten in the chests by the Storehouses and kitchens (smokers) of the village. */
	public static long food(ServerLevel level, BlockPos hall) {
		Set<BlockPos> chests = new LinkedHashSet<>();
		level.getPoiManager().findAll(h -> h.is(ModVillagers.STOREHOUSE_POI) || h.is(ModVillagers.KITCHEN_STOVE_POI)
			|| h.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.BUTCHER), p -> true, hall, RADIUS,
			PoiManager.Occupancy.ANY).forEach(station -> chests.addAll(SupplyContainers.find(level, station.immutable(), null)));
		long food = 0;
		for (var e : SupplyContainers.contents(level, List.copyOf(chests)).entrySet()) {
			if (new ItemStack(e.getKey()).has(DataComponents.FOOD)) {
				food += e.getValue();
			}
		}
		return food;
	}

	/** The buildings going up in the village (not the ones waiting in a builder's queue). */
	static List<Build> builds(ServerLevel level, BlockPos hall) {
		List<Build> out = new ArrayList<>();
		for (BuildSite site : BuildSiteManager.get(level).all()) {
			if (site.isDone() || site.isQueued() || !site.placement().dimension().equals(Ids.of(level.dimension()))
				|| site.placement().origin().distSqr(hall) > (double) RADIUS * RADIUS) {
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

	/** The village's free workstations, nearest the hall first. */
	public static List<FreeStation> freeStations(ServerLevel level, BlockPos hall) {
		List<FreeStation> out = new ArrayList<>();
		level.getPoiManager().getInRange(h -> h.is(net.minecraft.tags.PoiTypeTags.ACQUIRABLE_JOB_SITE), hall, RADIUS, PoiManager.Occupancy.HAS_SPACE)
			.forEach(record -> {
				net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.stream()
					.filter(p -> p != VillagerProfession.NONE && p != VillagerProfession.NITWIT && p.acquirableJobSite().test(record.getPoiType()))
					.findFirst()
					.ifPresent(p -> out.add(new FreeStation(record.getPos().immutable(), p, record.getPoiType())));
			});
		out.sort(Comparator.comparingDouble(f -> f.pos().distSqr(hall)));
		return out;
	}

	/** Gives {@code villager} the job at {@code station}, if it's still free and they can work (not a nitwit or a child). */
	public static boolean assign(ServerLevel level, Villager villager, FreeStation station) {
		if (!villager.isAlive() || villager.isBaby() || villager.getVillagerData().getProfession() == VillagerProfession.NITWIT) {
			return false;
		}
		// Whatever they had before is let go of first. A block broken while its worker was far away has no record left to
		// release (releasing it would throw "POI never registered").
		villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).ifPresent(old -> {
			if (old.dimension().equals(level.dimension()) && level.getPoiManager().getType(old.pos()).isPresent()) {
				level.getPoiManager().release(old.pos());
			}
		});
		villager.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
		boolean taken = level.getPoiManager().take(h -> h.equals(station.poi()), (h, p) -> p.equals(station.pos()), station.pos(), 1).isPresent();
		if (!taken) {
			return false;
		}
		villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, net.minecraft.core.GlobalPos.of(level.dimension(), station.pos()));
		villager.setVillagerData(villager.getVillagerData().setProfession(station.profession()));
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
	static BlockPos besideHall(ServerLevel level, BlockPos hall, int n) {
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
		return level.getPoiManager().findClosest(h -> h.is(ModVillagers.VILLAGE_HALL_POI), pos, RADIUS, PoiManager.Occupancy.ANY);
	}

	/** The village's name: the hall's own (from a Name Tag or an anvil), else one made up from where it stands. */
	public static Component name(ServerLevel level, BlockPos hall) {
		if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity && entity.getCustomName() != null) {
			return entity.getCustomName();
		}
		return madeUpName(hall);
	}

	/** "Oakbrook", "Ashford"... always the same for the same spot. */
	static Component madeUpName(BlockPos hall) {
		long seed = hall.asLong() * 0x9E3779B97F4A7C15L;
		int first = (int) Math.floorMod(seed >>> 17, (long) FIRST_HALVES);
		int second = (int) Math.floorMod(seed >>> 41, (long) SECOND_HALVES);
		return Component.translatable("village_name.aliveworkplace.format",
			Component.translatable("village_name.aliveworkplace.first." + first), Component.translatable("village_name.aliveworkplace.second." + second));
	}

	public static AABB area(BlockPos hall) {
		return new AABB(hall).inflate(RADIUS, HEIGHT, RADIUS);
	}

	private VillageHalls() {
	}
}
