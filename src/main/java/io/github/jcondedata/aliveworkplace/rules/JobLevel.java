package io.github.jcondedata.aliveworkplace.rules;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

/** {@code job_level}: {@code count} villagers of the trade {@code job} at {@code level} (1 Novice to 5 Master) or above. */
public record JobLevel(ResourceLocation job, int level, int count) implements Condition {
	static JobLevel read(JsonObject json) {
		int level = Conditions.count(json, "level");
		if (level < 1 || level > 5) {
			throw new IllegalArgumentException("'level' must be 1 to 5");
		}
		return new JobLevel(Conditions.id(json, "job"), level, Conditions.count(json, "count"));
	}

	@Override
	public String type() {
		return "job_level";
	}

	@Override
	public Progress progress(ServerLevel world, BlockPos hall) {
		int n = 0;
		for (Villager v : Village.villagers(world, hall)) {
			if (!v.isBaby() && job.equals(Village.job(v)) && v.getVillagerData().getLevel() >= level) {
				n++;
			}
		}
		return Progress.of(type(), n, count, profession(job), Component.translatable("merchant.level." + level));
	}

	/** A trade's name ("Builder"), as the villager's name shows it. */
	static Component profession(ResourceLocation job) {
		return Component.translatable("entity.minecraft.villager." + ("minecraft".equals(job.getNamespace()) ? "" : job.getNamespace() + ".") + job.getPath());
	}
}
