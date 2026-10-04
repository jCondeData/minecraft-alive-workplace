package io.github.jcondedata.aliveworkplace.rules;

import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** {@code caravan_routes}: the village sends caravans on {@code count} routes. */
public record CaravanRoutes(int count) implements Condition {
	static CaravanRoutes read(JsonObject json) {
		return new CaravanRoutes(Conditions.count(json, "count"));
	}

	@Override
	public String type() {
		return "caravan_routes";
	}

	@Override
	public Progress progress(ServerLevel level, BlockPos hall) {
		return Progress.of(type(), Caravans.Data.get(level).routesFrom(hall).size(), count);
	}
}
