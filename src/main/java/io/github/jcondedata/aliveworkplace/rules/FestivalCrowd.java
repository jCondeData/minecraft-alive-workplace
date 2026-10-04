package io.github.jcondedata.aliveworkplace.rules;

import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** {@code festival_crowd}: the village's last festival drew {@code count} villagers to the fireworks. */
public record FestivalCrowd(int count) implements Condition {
	static FestivalCrowd read(JsonObject json) {
		return new FestivalCrowd(Conditions.count(json, "count"));
	}

	@Override
	public String type() {
		return "festival_crowd";
	}

	@Override
	public Progress progress(ServerLevel level, BlockPos hall) {
		VillageHallBlockEntity entity = Village.hall(level, hall);
		return Progress.of(type(), entity == null ? 0 : entity.festivalCrowd(), count);
	}
}
