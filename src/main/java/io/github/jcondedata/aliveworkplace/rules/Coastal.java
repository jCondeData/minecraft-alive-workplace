package io.github.jcondedata.aliveworkplace.rules;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * {@code coastal}: an ocean or beach biome within 48 blocks of the hall (35.2, the Lighthouse). It asks M32's coast check
 * ({@link io.github.jcondedata.aliveworkplace.threat.Conditions#coastToday}, a 16-block grid looked at once a day per
 * hall), so the Lighthouse and the drowned pirates agree on what a coast is.
 */
public record Coastal() implements Condition {
	@Override
	public String type() {
		return "coastal";
	}

	@Override
	public Progress progress(ServerLevel level, BlockPos hall) {
		boolean coast = io.github.jcondedata.aliveworkplace.threat.Conditions.coastToday(level, hall);
		return Progress.of(type(), coast ? 1 : 0, 1, io.github.jcondedata.aliveworkplace.threat.Conditions.COAST);
	}
}
