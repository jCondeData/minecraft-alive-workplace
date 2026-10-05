package io.github.jcondedata.aliveworkplace.legend;

import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;

/**
 * {@code golem_forge} (the Golem Smith, 29.15): from the chests by their smithing table they build one golem every
 * {@code days} days, up to one per {@code villagers_per_golem} villagers, and mend golems {@code mend_factor} times as
 * fast as a Tinkerer. See {@link GolemSmith}.
 */
public record GolemForgePower(int days, int villagersPerGolem, float mendFactor) implements Power {
	static GolemForgePower read(JsonObject json) {
		int days = json.has("days") ? json.get("days").getAsInt() : 2;
		int per = json.has("villagers_per_golem") ? json.get("villagers_per_golem").getAsInt() : 5;
		float mend = json.has("mend_factor") ? json.get("mend_factor").getAsFloat() : 2f;
		if (days < 1 || per < 1 || mend < 1f) {
			throw new IllegalArgumentException("'days' or 'villagers_per_golem' below 1, or 'mend_factor' below 1");
		}
		return new GolemForgePower(days, per, mend);
	}

	@Override
	public String type() {
		return "golem_forge";
	}

	@Override
	public Component describe() {
		return Component.translatable("legend.aliveworkplace.power.golem_forge", days, villagersPerGolem);
	}
}
