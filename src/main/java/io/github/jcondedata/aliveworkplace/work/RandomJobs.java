package io.github.jcondedata.aliveworkplace.work;

import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * A job block with several jobs gives one at random (ROADMAP 36.17, the owner's ask): a jobless villager who takes a
 * shared block by themselves ({@link Stations}: a composter, a lectern, a Shop Counter...) no longer always becomes its
 * vanilla (first) job. Each job the block offers in this game ({@link Stations#available}) and that the villager's class
 * may hold ({@code people/ClassJobs}) gets a weight of {@code 1 / (1 + n)}, where {@code n} is how many villagers within
 * {@link #RADIUS} blocks of the block already do it; one is drawn by those weights. A village with no florist is
 * likelier to get one than another farmer, and a new village draws evenly.
 * <p>
 * Only the vanilla path is touched (the brain of a jobless villager reaching the block): a job picked with an item, by
 * the hall's Free Jobs page, the Steward, a family trade or a successor stays the one named. The job drawn is the
 * villager's profession, which the game saves, so it stays once taken. The Trainer Leader is never drawn (a gym
 * leader is made by hand, with a gold block). {@code randomStationJobs} off: the vanilla job, as before.
 */
public final class RandomJobs {
	/** {@code randomStationJobs} in the config (on by default). */
	public static boolean ENABLED = true;
	/** How far round the block the village's workers are counted (as {@link WorkerLimits#RADIUS}'s default). */
	public static final int RADIUS = 48;

	/**
	 * The job {@code villager} takes instead of the one in {@code data}, or null to leave it: called as their data is set
	 * (mixin/VillagerMixin). Only while their own brain runs ({@link WorkerLimits#thinker()}), for a jobless villager
	 * given the first job of a shared block they work at, never by order.
	 */
	@Nullable
	public static VillagerProfession instead(Villager villager, VillagerData data) {
		if (!ENABLED || WorkerLimits.thinker() != villager || WorkerLimits.ordering(villager)
			|| !(villager.level() instanceof ServerLevel level)
			|| villager.getVillagerData().getProfession() != VillagerProfession.NONE
			|| data.getProfession() == VillagerProfession.NONE || data.getProfession() == VillagerProfession.NITWIT) {
			return null;
		}
		GlobalPos site = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
		if (site == null || site.dimension() != level.dimension()) {
			return null;
		}
		Optional<Holder<PoiType>> poi = level.getPoiManager().getType(site.pos());
		Optional<Stations.Station> station = poi.flatMap(h -> Stations.ALL.stream().filter(s -> s.poi().test(h)).findFirst());
		if (station.isEmpty() || !station.get().byItself() || station.get().jobs().get(0).profession().get() != data.getProfession()) {
			return null;
		}
		List<VillagerProfession> jobs = candidates(level, villager, station.get());
		if (jobs.size() < 2) {
			return null;
		}
		VillagerProfession drawn = pick(jobs, counts(level, site.pos(), villager, jobs), villager.getRandom());
		return drawn == data.getProfession() ? null : drawn;
	}

	/**
	 * The jobs {@code villager} could be given at {@code station} by chance, the vanilla one first: those in this game,
	 * that their class allows, never the Trainer Leader. The first job is always there (vanilla gave it).
	 */
	public static List<VillagerProfession> candidates(ServerLevel level, Villager villager, Stations.Station station) {
		List<VillagerProfession> out = new ArrayList<>();
		for (int i = 0; i < station.jobs().size(); i++) {
			VillagerProfession job = station.jobs().get(i).profession().get();
			if (i == 0 || (job != ModVillagers.TRAINER_LEADER && Stations.available(job)
				&& io.github.jcondedata.aliveworkplace.people.ClassJobs.refusal(level, villager, job) == null)) {
				out.add(job);
			}
		}
		return out;
	}

	/** How many villagers (not {@code self}) within {@link #RADIUS} of {@code at} do each of {@code jobs}. */
	public static Map<VillagerProfession, Integer> counts(ServerLevel level, BlockPos at, @Nullable Villager self, List<VillagerProfession> jobs) {
		Map<VillagerProfession, Integer> out = new HashMap<>();
		for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(at).inflate(RADIUS),
				v -> v != self && v.isAlive() && jobs.contains(v.getVillagerData().getProfession()))) {
			out.merge(v.getVillagerData().getProfession(), 1, Integer::sum);
		}
		return out;
	}

	/** One of {@code jobs}, each weighted {@code 1 / (1 + its count)}: the fewest, the likeliest. */
	public static VillagerProfession pick(List<VillagerProfession> jobs, Map<VillagerProfession, Integer> counts, RandomSource random) {
		double[] weights = new double[jobs.size()];
		double total = 0;
		for (int i = 0; i < jobs.size(); i++) {
			weights[i] = 1.0 / (1 + counts.getOrDefault(jobs.get(i), 0));
			total += weights[i];
		}
		double r = random.nextDouble() * total;
		for (int i = 0; i < jobs.size(); i++) {
			r -= weights[i];
			if (r < 0) {
				return jobs.get(i);
			}
		}
		return jobs.get(jobs.size() - 1);
	}

	private RandomJobs() {
	}
}
