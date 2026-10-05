package io.github.jcondedata.aliveworkplace.city;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.VillageAdvice;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Stations;
import io.github.jcondedata.aliveworkplace.work.WorkerLimits;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * The Steward's {@code assign_jobs} effect (ROADMAP 27.9): each morning every grown jobless villager (not a nitwit, nor
 * a worker who has traded and only lost his block) gets a free workstation of the village
 * ({@link VillageHalls#freeStations}), nearest the hall first, for the village's biggest gap: a builder while there's
 * none, a farmer while food is short (one a morning: a farmer doesn't fill the store by noon), guards while guards are
 * short, a porter at a free Storehouse, a scholar while research is idle (one a morning), then the nearest free block.
 * <p>
 * At a block several jobs share ({@link Stations}) he can give its other jobs, which the hall's own list can't: a block
 * inside a finished building made for one of them ({@link #BUILDING_JOBS}, a Berry Farm's composter for an Orchard
 * Keeper) goes to that job while the building has none, and no other gap takes it. A gap with no free block left is
 * handed to {@link #WORKPLACE_WANTED}, and 27.11's workplace rules propose its building ({@link StewardConditions.Facts#wanted}).
 */
public final class StewardJobs {
	/** One job given: who, which job, at which block. */
	public record Job(UUID villager, ResourceLocation profession, BlockPos station) {
		public static final Codec<Job> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("villager").forGetter(Job::villager),
			ResourceLocation.CODEC.fieldOf("profession").forGetter(Job::profession),
			BlockPos.CODEC.fieldOf("station").forGetter(Job::station)
		).apply(i, Job::new));

		public Optional<VillagerProfession> job() {
			return BuiltInRegistries.VILLAGER_PROFESSION.getOptional(profession);
		}
	}

	/** The morning's jobs, and the jobs the village wants that had no free block. */
	public record Plan(List<Job> jobs, List<VillagerProfession> wanted) {
	}

	/** The village's gaps, biggest first. */
	public enum Gap {
		BUILDER, FARMER, GUARDS, PORTER, SCHOLAR;

		public VillagerProfession profession() {
			return switch (this) {
				case BUILDER -> ModVillagers.BUILDER;
				case FARMER -> VillagerProfession.FARMER;
				case GUARDS -> ModVillagers.GUARD;
				case PORTER -> ModVillagers.PORTER;
				case SCHOLAR -> ModVillagers.SCHOLAR;
			};
		}
	}

	/**
	 * A job the village wants has no free block left, called once a morning per job. The building itself is proposed by
	 * 27.11's {@code workplace_*} rules, whose {@code worker_without_workstation} reads the same wants
	 * ({@link StewardConditions.Facts#wanted}); this hook stays a seam for anything else that wants to know.
	 */
	@FunctionalInterface
	public interface WorkplaceWanted {
		void want(ServerLevel level, BlockPos hall, VillagerProfession profession);
	}

	public static WorkplaceWanted WORKPLACE_WANTED = (level, hall, profession) -> {
	};

	/** The building (by its family's path) each job at a shared block is for. */
	public static final Map<String, Supplier<VillagerProfession>> BUILDING_JOBS = Map.of(
		"berry_farm", () -> ModVillagers.ORCHARD_KEEPER,
		"flower_shop", () -> ModVillagers.FLORIST,
		"compost_yard", () -> ModVillagers.COMPOSTER,
		"sifting_shed", () -> ModVillagers.SIFTER,
		"ranch", () -> ModVillagers.RANCHER,
		"tinkers_workshop", () -> ModVillagers.TINKERER,
		"barracks", () -> ModVillagers.GUARD,
		"schoolhouse", () -> ModVillagers.TEACHER,
		"inn", () -> ModVillagers.INNKEEPER);

	/** How far outside a building's walls its block may stand. */
	static final int BUILDING_MARGIN = 2;

	/** Whether the Steward may give {@code villager} a job: grown, not a nitwit, and jobless (a trader who lost his block waits for one of its kind). */
	public static boolean wantsJob(Villager villager) {
		if (!villager.isAlive() || villager.isBaby() || Stewards.isSteward(villager)) {
			return false;
		}
		VillagerProfession job = villager.getVillagerData().getProfession();
		if (job == VillagerProfession.NITWIT) {
			return false;
		}
		if (job == VillagerProfession.NONE) {
			return true;
		}
		return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty() && villager.getVillagerXp() == 0;
	}

	/** The morning's jobs for the village round {@code hall}; nothing is changed. */
	public static Plan plan(ServerLevel level, BlockPos hall) {
		StewardConditions.Facts facts = StewardConditions.Facts.of(level, hall);
		VillageHalls.Census census = facts.census();
		List<Villager> jobless = census.jobless().stream().filter(StewardJobs::wantsJob).toList(); // nearest the hall first
		List<VillageHalls.FreeStation> free = new ArrayList<>(VillageHalls.freeStations(level, hall));
		List<Building> buildings = buildings(level, hall);
		long builders = VillageAdvice.workers(census, ModVillagers.BUILDER);
		int guards = census.guards();
		int villagers = census.villagers();
		int guardsNeeded = villagers <= 0 ? 0 : (villagers + VillageNeeds.VILLAGERS_PER_GUARD - 1) / VillageNeeds.VILLAGERS_PER_GUARD;
		boolean foodShort = new StewardConditions.FoodShort(VillageAdvice.MEALS_PER_ADULT).test(facts).held();
		boolean researchIdle = new StewardConditions.ResearchIdle().test(facts).held();
		boolean farmer = false;
		boolean scholar = false;
		List<Job> jobs = new ArrayList<>();
		Set<VillagerProfession> wanted = new LinkedHashSet<>();
		for (Villager villager : jobless) {
			if (free.isEmpty()) {
				break;
			}
			VillageHalls.FreeStation at = null;
			VillagerProfession job = null;
			for (Gap gap : Gap.values()) {
				boolean holds = switch (gap) {
					case BUILDER -> builders == 0;
					case FARMER -> foodShort && !farmer;
					case GUARDS -> guards < guardsNeeded;
					case PORTER -> true;
					case SCHOLAR -> researchIdle && !scholar;
				};
				if (!holds) {
					continue;
				}
				VillagerProfession p = gap.profession();
				Optional<VillageHalls.FreeStation> found = free.stream()
					.filter(f -> p.acquirableJobSite().test(f.poi()) && (gap != Gap.PORTER || f.poi().is(ModVillagers.STOREHOUSE_POI)))
					.filter(f -> buildingJob(level, buildings, f.pos()).map(b -> b == p).orElse(true))
					.min(Comparator.comparingDouble(f -> f.pos().distToCenterSqr(villager.position())));
				if (found.isPresent()) {
					at = found.get();
					job = p;
					break;
				}
				if (gap != Gap.PORTER) {
					wanted.add(p); // no free block for it: its building (27.11)
				}
			}
			if (at == null) {
				at = free.stream().min(Comparator.comparingDouble(f -> f.pos().distToCenterSqr(villager.position()))).orElseThrow();
				job = jobAt(level, buildings, at);
			}
			free.remove(at);
			jobs.add(new Job(villager.getUUID(), BuiltInRegistries.VILLAGER_PROFESSION.getKey(job), at.pos()));
			if (job == ModVillagers.BUILDER) {
				builders++;
			} else if (job == VillagerProfession.FARMER) {
				farmer = true;
			} else if (job == ModVillagers.GUARD) {
				guards++;
			} else if (job == ModVillagers.SCHOLAR) {
				scholar = true;
			}
		}
		if (jobless.size() > jobs.size()) {
			// villagers left with no block at all: the gaps that still hold want their buildings too
			if (builders == 0) {
				wanted.add(ModVillagers.BUILDER);
			}
			if (foodShort && !farmer) {
				wanted.add(VillagerProfession.FARMER);
			}
			if (guards < guardsNeeded) {
				wanted.add(ModVillagers.GUARD);
			}
			if (researchIdle && !scholar) {
				wanted.add(ModVillagers.SCHOLAR);
			}
		}
		wanted.removeIf(p -> jobs.stream().anyMatch(j -> j.profession().equals(BuiltInRegistries.VILLAGER_PROFESSION.getKey(p))));
		return new Plan(List.copyOf(jobs), List.copyOf(wanted));
	}

	/** A finished building by the hall, its walls, and the job it's for. */
	record Building(BoundingBox box, VillagerProfession job) {
	}

	static List<Building> buildings(ServerLevel level, BlockPos hall) {
		List<Building> out = new ArrayList<>();
		for (BuildSiteManager.Finished f : BuildSiteManager.get(level).finishedNear(level, hall, VillageHalls.RADIUS + 16)) {
			Supplier<VillagerProfession> job = BUILDING_JOBS.get(StewardConditions.family(f.structure()).getPath());
			if (job == null) {
				continue;
			}
			BlueprintLibrary.get(level, f.structure()).ifPresent(b ->
				out.add(new Building(BlueprintOutline.bounds(f.placement(), b.size()).inflatedBy(BUILDING_MARGIN), job.get())));
		}
		return out;
	}

	/** The job of the building {@code pos} stands in, while that building has nobody of that job; empty if none. */
	static Optional<VillagerProfession> buildingJob(ServerLevel level, List<Building> buildings, BlockPos pos) {
		for (Building b : buildings) {
			if (b.box().isInside(pos) && !worked(level, b)) {
				return Optional.of(b.job());
			}
		}
		return Optional.empty();
	}

	/** Whether someone of the building's job already works at a block inside it. */
	private static boolean worked(ServerLevel level, Building b) {
		return !level.getEntitiesOfClass(Villager.class, VillageHalls.area(b.box().getCenter()),
			v -> v.isAlive() && v.getVillagerData().getProfession() == b.job()
				&& v.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).filter(b.box()::isInside).isPresent()).isEmpty();
	}

	/** The job a free block gives: its building's (when the block is shared and that job is one of it), else the block's own. */
	static VillagerProfession jobAt(ServerLevel level, List<Building> buildings, VillageHalls.FreeStation free) {
		Optional<Stations.Station> station = Stations.ALL.stream().filter(s -> s.poi().test(free.poi())).findFirst();
		Optional<VillagerProfession> building = buildingJob(level, buildings, free.pos());
		if (station.isPresent() && building.isPresent()) {
			for (Stations.Job job : station.get().jobs()) {
				if (job.profession().get() == building.get() && Stations.available(job)) {
					return building.get();
				}
			}
		}
		return free.profession();
	}

	/**
	 * Gives the jobs still possible (the villager still jobless and by, the block still free, the server's worker cap not
	 * reached), with a cheer and his XP for each; the jobs given.
	 */
	public static List<Job> give(ServerLevel level, @Nullable Villager steward, List<Job> jobs) {
		List<Job> given = new ArrayList<>();
		for (Job job : jobs) {
			Optional<VillagerProfession> profession = job.job();
			if (profession.isEmpty() || !(level.getEntity(job.villager()) instanceof Villager villager) || !wantsJob(villager)
				|| level.getPoiManager().getType(job.station()).isEmpty() || level.getPoiManager().getFreeTickets(job.station()) <= 0
				|| !profession.get().acquirableJobSite().test(level.getPoiManager().getType(job.station()).get())
				|| WorkerLimits.full(level, GlobalPos.of(level.dimension(), job.station()), false)) {
				continue;
			}
			Stations.assign(level, villager, job.station(), profession.get());
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getY() + 1.2, villager.getZ(), 8, 0.4, 0.4, 0.4, 0);
			level.playSound(null, villager, SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
			if (steward != null) {
				Stewards.credit(level, steward, Stewards.XP_JOB);
			}
			given.add(job);
		}
		return given;
	}

	/** "Dara, Farmer at the Composter 12 blocks east". */
	public static Component describe(ServerLevel level, BlockPos hall, Job job) {
		Component who = level.getEntity(job.villager()) instanceof Villager v ? v.getDisplayName()
			: Component.translatable("steward.aliveworkplace.jobs.someone");
		Component trade = job.job().map(Stations::name).orElse(Component.literal(job.profession().toString()));
		return Component.translatable("steward.aliveworkplace.jobs.job", who, trade, level.getBlockState(job.station()).getBlock().getName(),
			VillageHallScreen.where(hall, job.station()));
	}

	/** "Give 3 villagers jobs" (one: "Give 1 villager a job"). */
	public static Component title(int n) {
		return Component.translatable(n == 1 ? "steward.aliveworkplace.jobs.title.one" : "steward.aliveworkplace.jobs.title", n);
	}

	/** "Give 3 villagers jobs: Dara, Farmer at the Composter 12 blocks east; ...". */
	public static Component line(ServerLevel level, BlockPos hall, List<Job> jobs) {
		return Component.translatable("steward.aliveworkplace.jobs.line", title(jobs.size()), list(level, hall, jobs));
	}

	/** "Dara, Farmer at the Composter 12 blocks east; Bram, ...". */
	public static Component list(ServerLevel level, BlockPos hall, List<Job> jobs) {
		MutableComponent list = Component.empty();
		for (int i = 0; i < jobs.size(); i++) {
			if (i > 0) {
				list.append("; ");
			}
			list.append(describe(level, hall, jobs.get(i)));
		}
		return list;
	}

	private StewardJobs() {
	}
}
