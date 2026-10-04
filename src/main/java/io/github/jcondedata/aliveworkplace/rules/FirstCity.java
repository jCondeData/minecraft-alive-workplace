package io.github.jcondedata.aliveworkplace.rules;

import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** {@code first_city}: the village has risen to City and the Founder's mood hasn't come yet (it comes once). */
public record FirstCity() implements Condition {
	static FirstCity read(JsonObject json) {
		return new FirstCity();
	}

	@Override
	public String type() {
		return "first_city";
	}

	@Override
	public Progress progress(ServerLevel level, BlockPos hall) {
		VillageHallBlockEntity entity = Village.hall(level, hall);
		boolean met = entity != null && entity.rank() == VillageRanks.Rank.CITY && entity.founderMoodDay() == 0;
		return Progress.of(type(), met ? 1 : 0, 1);
	}
}
