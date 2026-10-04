package io.github.jcondedata.aliveworkplace.rules;

import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** {@code rank_at_least}: the village is at least {@code rank} (hamlet, village, town, city). */
public record RankAtLeast(VillageRanks.Rank rank) implements Condition {
	static RankAtLeast read(JsonObject json) {
		return new RankAtLeast(VillageRanks.Rank.valueOf(json.get("rank").getAsString().toUpperCase(Locale.ROOT)));
	}

	@Override
	public String type() {
		return "rank_at_least";
	}

	@Override
	public Progress progress(ServerLevel level, BlockPos hall) {
		VillageHallBlockEntity entity = Village.hall(level, hall);
		VillageRanks.Rank now = entity == null ? VillageRanks.Rank.HAMLET : entity.rank();
		return Progress.of(type(), now.ordinal(), rank.ordinal(), now.title(), rank.title());
	}
}
