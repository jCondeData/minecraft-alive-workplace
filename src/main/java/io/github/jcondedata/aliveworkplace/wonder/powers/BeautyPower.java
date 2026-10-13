package io.github.jcondedata.aliveworkplace.wonder.powers;

import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * {@code beauty} (35.8, generic: for server owners' own Wonders, and the Cathedral's and the Colossus's): the Wonder adds
 * {@code points} to its village's beauty ({@code hall/Decorations.beauty}), as a fountain or a chapel does.
 */
public record BeautyPower(int points) implements WonderPower {
	public static final String TYPE = "beauty";
	/** Most beauty one Wonder may add. */
	public static final int MAX = 50;

	static BeautyPower read(JsonObject json) {
		if (!json.has("points")) {
			throw new IllegalArgumentException("missing 'points'");
		}
		int points = json.get("points").getAsInt();
		if (points < 0 || points > MAX) {
			throw new IllegalArgumentException("'points' must be 0 to " + MAX + ", not " + points);
		}
		return new BeautyPower(points);
	}

	@Override
	public String type() {
		return TYPE;
	}

	/** "Adds 8 to the village's beauty". */
	@Override
	public Component describe() {
		return Component.translatable("wonder.aliveworkplace.power.beauty", points);
	}

	/** The beauty the village round {@code hall} has from its finished, standing Wonders. */
	public static int beauty(ServerLevel level, BlockPos hall) {
		int points = 0;
		for (Wonders.Granted g : Wonders.powers(level, hall, TYPE)) {
			if (g.power() instanceof BeautyPower beauty) {
				points += beauty.points();
			}
		}
		return points;
	}
}
