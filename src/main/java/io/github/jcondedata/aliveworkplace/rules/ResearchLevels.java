package io.github.jcondedata.aliveworkplace.rules;

import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * {@code research_levels}: the village's scholars have finished {@code count} research levels in all, or in one
 * {@code tree} (its topics are kept as {@code <tree>/<topic>}; the first tree's topics have no prefix).
 */
public record ResearchLevels(int count, Optional<String> tree) implements Condition {
	static ResearchLevels read(JsonObject json) {
		return new ResearchLevels(Conditions.count(json, "count"), json.has("tree") ? Optional.of(json.get("tree").getAsString()) : Optional.empty());
	}

	@Override
	public String type() {
		return "research_levels";
	}

	@Override
	public Progress progress(ServerLevel level, BlockPos hall) {
		VillageHallBlockEntity entity = Village.hall(level, hall);
		int n = 0;
		if (entity != null) {
			for (Map.Entry<String, Integer> e : entity.research().levels().entrySet()) {
				if (io.github.jcondedata.aliveworkplace.research.Research.State.isLevel(e.getKey())
					&& (tree.isEmpty() || e.getKey().startsWith(tree.get() + "/"))) {
					n += Math.max(0, e.getValue());
				}
			}
		}
		return Progress.of(type(), n, count);
	}
}
