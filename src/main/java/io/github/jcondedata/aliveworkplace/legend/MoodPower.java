package io.github.jcondedata.aliveworkplace.legend;

import com.google.gson.JsonObject;

/** {@code mood}: grown villagers within {@code radius} blocks of the Legend (0: the whole village) are {@code points} happier. */
public record MoodPower(int points, int radius) implements Power {
	static MoodPower read(JsonObject json) {
		if (!json.has("points")) {
			throw new IllegalArgumentException("missing 'points'");
		}
		return new MoodPower(json.get("points").getAsInt(), json.has("radius") ? Math.max(0, json.get("radius").getAsInt()) : 16);
	}

	@Override
	public String type() {
		return "mood";
	}

	/** "Villagers within 8 blocks are 7 happier", "Everyone in the village is 7 happier". */
	@Override
	public net.minecraft.network.chat.Component describe() {
		return radius > 0 ? net.minecraft.network.chat.Component.translatable("legend.aliveworkplace.power.mood", points, radius)
			: net.minecraft.network.chat.Component.translatable("legend.aliveworkplace.power.mood_village", points);
	}
}
