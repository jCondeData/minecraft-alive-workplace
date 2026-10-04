package io.github.jcondedata.aliveworkplace.rules;

import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** {@code treasury_total}: the treasury has taken in {@code emeralds} emeralds in all, ever (not what it holds now). */
public record TreasuryTotal(int emeralds) implements Condition {
	static TreasuryTotal read(JsonObject json) {
		return new TreasuryTotal(Conditions.count(json, "emeralds"));
	}

	@Override
	public String type() {
		return "treasury_total";
	}

	@Override
	public Progress progress(ServerLevel level, BlockPos hall) {
		VillageHallBlockEntity entity = Village.hall(level, hall);
		return Progress.of(type(), entity == null ? 0 : (int) Math.min(Integer.MAX_VALUE, entity.treasuryTotal() / 100), emeralds);
	}
}
