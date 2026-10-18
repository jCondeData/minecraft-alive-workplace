package io.github.jcondedata.aliveworkplace.table;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * What a build brings a village, as the Blueprint Table shows it (36.6a, the owner's change to 36.6): the jobs its job
 * blocks give, "Jobs: Fisherman (1), Butcher/Chef/Rancher (3)" (one entry per kind of job block, the jobs that share it
 * joined by a slash, and how many of that block the build holds), and "Beds: N" when it has beds. Worked out from the
 * build's own blocks, so an uploaded or imported build gets its line too.
 */
public final class BuildJobs {
	/** A build's job blocks and beds. */
	public record Summary(List<TablePayloads.JobBlock> jobs, int beds) {
		public static final Summary NONE = new Summary(List.of(), 0);
	}

	/**
	 * The job blocks in {@code blueprint}, in the order the build first holds each kind, and its beds (a bed counts once,
	 * by its head). A shared workstation lists every job that can be had at it in this game ({@link Stations#available}),
	 * the one a jobless villager takes by itself first; any other job block lists the jobs that work there. A job block
	 * whose jobs are all switched off is left out.
	 */
	public static Summary of(Blueprint blueprint) {
		Map<BlockState, Optional<List<ResourceLocation>>> byState = new HashMap<>();
		Map<List<ResourceLocation>, Integer> counts = new LinkedHashMap<>();
		int beds = 0;
		for (Blueprint.Entry entry : blueprint.blocks()) {
			BlockState state = entry.state();
			if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD) {
				beds++;
				continue;
			}
			Optional<List<ResourceLocation>> jobs = byState.computeIfAbsent(state, BuildJobs::jobsAt);
			jobs.ifPresent(j -> counts.merge(j, 1, Integer::sum));
		}
		List<TablePayloads.JobBlock> out = new ArrayList<>();
		counts.forEach((jobs, count) -> out.add(new TablePayloads.JobBlock(jobs, count)));
		return new Summary(List.copyOf(out), beds);
	}

	/** The jobs a block in {@code state} gives (empty when it isn't a job block, or none of its jobs can be had). */
	private static Optional<List<ResourceLocation>> jobsAt(BlockState state) {
		Optional<Holder<PoiType>> poi = PoiTypes.forState(state);
		if (poi.isEmpty()) {
			return Optional.empty();
		}
		Holder<PoiType> holder = poi.get();
		List<VillagerProfession> professions = new ArrayList<>();
		Optional<Stations.Station> station = Stations.ALL.stream().filter(s -> s.poi().test(holder)).findFirst();
		if (station.isPresent()) {
			for (Stations.Job job : station.get().jobs()) {
				if (Stations.available(job) && !professions.contains(job.profession().get())) {
					professions.add(job.profession().get());
				}
			}
		} else {
			for (VillagerProfession profession : BuiltInRegistries.VILLAGER_PROFESSION) {
				if (profession != VillagerProfession.NONE && profession != VillagerProfession.NITWIT && profession.heldJobSite().test(holder)
					&& Stations.available(profession)) {
					professions.add(profession);
				}
			}
		}
		if (professions.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(professions.stream().map(BuiltInRegistries.VILLAGER_PROFESSION::getKey).toList());
	}

	/**
	 * The lines the Table's detail pane shows under a build's size: "Jobs: ..." when it has job blocks, "Beds: N" when it
	 * has beds; none for a build with neither.
	 */
	public static List<Component> lines(List<TablePayloads.JobBlock> jobs, int beds) {
		List<Component> lines = new ArrayList<>();
		if (!jobs.isEmpty()) {
			MutableComponent list = Component.empty();
			for (int i = 0; i < jobs.size(); i++) {
				if (i > 0) {
					list.append(Component.translatable("screen.aliveworkplace.table.jobs_separator"));
				}
				TablePayloads.JobBlock block = jobs.get(i);
				MutableComponent names = Component.empty();
				for (int j = 0; j < block.jobs().size(); j++) {
					if (j > 0) {
						names.append("/");
					}
					names.append(name(block.jobs().get(j)));
				}
				list.append(Component.translatable("screen.aliveworkplace.table.job_block", names, block.count()));
			}
			lines.add(Component.translatable("screen.aliveworkplace.table.jobs", list));
		}
		if (beds > 0) {
			lines.add(Component.translatable("screen.aliveworkplace.table.beds", beds));
		}
		return lines;
	}

	/** A job's name as the game shows it over a villager (as {@link Stations#name}). */
	private static Component name(ResourceLocation profession) {
		return BuiltInRegistries.VILLAGER_PROFESSION.getOptional(profession)
			.<Component>map(p -> Component.translatable("entity.minecraft.villager." + p.name()))
			.orElse(Component.literal(profession.getPath()));
	}

	private BuildJobs() {
	}
}
