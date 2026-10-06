package io.github.jcondedata.aliveworkplace.rules;

import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/** {@code food_below}: the village store holds fewer than {@code count} meals (31.2: the daily food quest). */
public record FoodBelow(int count) implements Condition {
	static FoodBelow read(JsonObject json) {
		return new FoodBelow(Conditions.count(json, "count"));
	}

	@Override
	public String type() {
		return "food_below";
	}

	@Override
	public Progress progress(ServerLevel level, BlockPos hall) {
		long food = VillageHalls.food(level, hall);
		return new Progress(food < count ? 1 : 0, 1, Component.translatable("rule.aliveworkplace.food_below", food, count));
	}
}
