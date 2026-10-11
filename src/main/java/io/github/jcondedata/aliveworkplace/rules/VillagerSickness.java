package io.github.jcondedata.aliveworkplace.rules;

import io.github.jcondedata.aliveworkplace.people.Sickness;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** {@code villager_sickness} (ROADMAP 31.18): villagers fall ill on this server (config {@code villagerSickness} on). */
public record VillagerSickness() implements Condition {
	@Override
	public String type() {
		return "villager_sickness";
	}

	@Override
	public Progress progress(ServerLevel level, BlockPos hall) {
		return Progress.of(type(), Sickness.ENABLED ? 1 : 0, 1);
	}
}
