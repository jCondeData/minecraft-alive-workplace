package io.github.jcondedata.aliveworkplace.rules;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** {@code full_moon}: tonight's moon is full. */
public record FullMoon() implements Condition {
	static FullMoon read(JsonObject json) {
		return new FullMoon();
	}

	@Override
	public String type() {
		return "full_moon";
	}

	@Override
	public Progress progress(ServerLevel level, BlockPos hall) {
		return Progress.of(type(), level.getMoonPhase() == 0 ? 1 : 0, 1);
	}
}
