package io.github.jcondedata.aliveworkplace.rules;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** {@code chance}: holds with probability {@code chance} (0 to 1), rolled each time it's asked with the level's random. */
public record Chance(float chance) implements Condition {
	static Chance read(JsonObject json) {
		if (!json.has("chance")) {
			throw new IllegalArgumentException("missing 'chance'");
		}
		float chance = json.get("chance").getAsFloat();
		if (chance < 0 || chance > 1) {
			throw new IllegalArgumentException("'chance' outside 0 to 1");
		}
		return new Chance(chance);
	}

	@Override
	public String type() {
		return "chance";
	}

	@Override
	public Progress progress(ServerLevel level, BlockPos hall) {
		return Progress.of(type(), level.random.nextFloat() < chance ? 1 : 0, 1, Math.round(chance * 100));
	}
}
