package io.github.jcondedata.aliveworkplace.rules;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;

/** {@code animals_at_job}: {@code count} animals within {@link #RADIUS} blocks of the workstations of the trades {@code jobs}. */
public record AnimalsAtJob(Set<ResourceLocation> jobs, int count) implements Condition {
	public static final int RADIUS = 16;

	static AnimalsAtJob read(JsonObject json) {
		Set<ResourceLocation> jobs = new HashSet<>();
		if (!json.has("jobs")) {
			throw new IllegalArgumentException("missing 'jobs'");
		}
		for (JsonElement e : json.getAsJsonArray("jobs")) {
			ResourceLocation id = ResourceLocation.tryParse(e.getAsString());
			if (id == null) {
				throw new IllegalArgumentException("bad job '" + e.getAsString() + "'");
			}
			jobs.add(id);
		}
		return new AnimalsAtJob(Set.copyOf(jobs), Conditions.count(json, "count"));
	}

	@Override
	public String type() {
		return "animals_at_job";
	}

	@Override
	public Progress progress(ServerLevel level, BlockPos hall) {
		List<BlockPos> stations = new ArrayList<>();
		for (Villager v : Village.villagers(level, hall)) {
			if (jobs.contains(Village.job(v))) {
				v.getBrain().getMemory(MemoryModuleType.JOB_SITE).filter(p -> p.dimension() == level.dimension()).map(GlobalPos::pos).ifPresent(stations::add);
			}
		}
		Set<Animal> animals = new HashSet<>();
		for (BlockPos station : stations) {
			animals.addAll(level.getEntitiesOfClass(Animal.class, new AABB(station).inflate(RADIUS), Animal::isAlive));
		}
		return Progress.of(type(), animals.size(), count);
	}
}
