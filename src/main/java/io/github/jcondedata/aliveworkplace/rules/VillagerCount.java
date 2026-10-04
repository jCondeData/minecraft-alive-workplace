package io.github.jcondedata.aliveworkplace.rules;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** {@code villagers}: at least {@code count} villagers (children too) live round the hall. */
public record VillagerCount(int count) implements Condition {
	static VillagerCount read(JsonObject json) {
		return new VillagerCount(Conditions.count(json, "count"));
	}

	@Override
	public String type() {
		return "villagers";
	}

	@Override
	public Progress progress(ServerLevel level, BlockPos hall) {
		return Progress.of(type(), Village.villagers(level, hall).size(), count);
	}
}
