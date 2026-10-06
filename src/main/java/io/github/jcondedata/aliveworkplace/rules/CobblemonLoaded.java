package io.github.jcondedata.aliveworkplace.rules;

import io.github.jcondedata.aliveworkplace.trainer.Trainers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** {@code cobblemon}: Cobblemon is installed. */
public record CobblemonLoaded() implements Condition {
	@Override
	public String type() {
		return "cobblemon";
	}

	@Override
	public Progress progress(ServerLevel level, BlockPos hall) {
		return Progress.of(type(), Trainers.COBBLEMON ? 1 : 0, 1);
	}
}
