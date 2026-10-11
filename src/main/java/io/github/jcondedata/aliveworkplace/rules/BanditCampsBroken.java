package io.github.jcondedata.aliveworkplace.rules;

import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.story.Stories;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * {@code bandit_camps_broken} (ROADMAP 31.16): at least {@code count} bandit camps were broken up near the village, and
 * bandit camps are on ({@code banditCamps}). The Bandit King's arc waits for it.
 */
public record BanditCampsBroken(int count) implements Condition {
	static BanditCampsBroken read(JsonObject json) {
		return new BanditCampsBroken(Conditions.count(json, "count"));
	}

	@Override
	public String type() {
		return "bandit_camps_broken";
	}

	@Override
	public Progress progress(ServerLevel level, BlockPos hall) {
		int have = BanditCamps.ENABLED ? Stories.banditCampsBroken(level, hall) : 0;
		return Progress.of(type(), have, BanditCamps.ENABLED ? count : Integer.MAX_VALUE, have, count);
	}
}
