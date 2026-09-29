package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
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

	/** The food in the store: whatever can be eaten in the chests by the Storehouses and Kitchen Stoves of the village. */
	public static long food(ServerLevel level, BlockPos hall) {
		Set<BlockPos> chests = new LinkedHashSet<>();
		level.getPoiManager().findAll(h -> h.is(ModVillagers.STOREHOUSE_POI) || h.is(ModVillagers.KITCHEN_STOVE_POI), p -> true, hall, RADIUS,
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
			if (site.isDone() || site.isQueued() || !site.placement().dimension().equals(level.dimension().location())
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

	static AABB area(BlockPos hall) {
		return new AABB(hall).inflate(RADIUS, HEIGHT, RADIUS);
	}

	private VillageHalls() {
	}
}
