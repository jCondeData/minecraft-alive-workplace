package io.github.jcondedata.aliveworkplace.rules;

import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** {@code mod_loaded}: the mod {@code mod} is installed (35.2), asked through {@link Platform} since core can't call the loader. */
public record ModLoaded(String mod) implements Condition {
	static ModLoaded read(JsonObject json) {
		String mod = json.has("mod") ? json.get("mod").getAsString().trim() : "";
		if (mod.isEmpty()) {
			throw new IllegalArgumentException("missing 'mod'");
		}
		return new ModLoaded(mod);
	}

	@Override
	public String type() {
		return "mod_loaded";
	}

	@Override
	public Progress progress(ServerLevel level, BlockPos hall) {
		return Progress.of(type(), Platform.get().isModLoaded(mod) ? 1 : 0, 1, mod);
	}
}
