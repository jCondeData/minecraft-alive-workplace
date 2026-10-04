package io.github.jcondedata.aliveworkplace.rules;

import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** {@code meal_kinds}: the village store holds {@code count} kinds of meal. */
public record MealKinds(int count) implements Condition {
	static MealKinds read(JsonObject json) {
		return new MealKinds(Conditions.count(json, "count"));
	}

	@Override
	public String type() {
		return "meal_kinds";
	}

	@Override
	public Progress progress(ServerLevel level, BlockPos hall) {
		return Progress.of(type(), VillageHalls.mealKinds(level, hall), count);
	}
}
