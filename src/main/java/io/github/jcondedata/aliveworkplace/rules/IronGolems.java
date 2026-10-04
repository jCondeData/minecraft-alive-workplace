package io.github.jcondedata.aliveworkplace.rules;

import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.IronGolem;

/** {@code iron_golems}: {@code count} iron golems round the hall. */
public record IronGolems(int count) implements Condition {
	static IronGolems read(JsonObject json) {
		return new IronGolems(Conditions.count(json, "count"));
	}

	@Override
	public String type() {
		return "iron_golems";
	}

	@Override
	public Progress progress(ServerLevel level, BlockPos hall) {
		return Progress.of(type(), level.getEntitiesOfClass(IronGolem.class, VillageHalls.area(hall), IronGolem::isAlive).size(), count);
	}
}
