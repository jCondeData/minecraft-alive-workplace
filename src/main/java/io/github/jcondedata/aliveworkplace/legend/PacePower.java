package io.github.jcondedata.aliveworkplace.legend;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * {@code pace}: workers of {@code trades} (none listed: every trade) within {@code radius} blocks of the Legend (0: the
 * whole village) work {@code factor} times as fast, never past {@link LegendPowers#PACE_CAP} from Legends together, and
 * never past {@code Pace}'s cap with every other bonus.
 */
public record PacePower(Set<ResourceLocation> trades, int radius, float factor) implements Power {
	static PacePower read(JsonObject json) {
		Set<ResourceLocation> trades = new HashSet<>();
		if (json.has("trades")) {
			for (JsonElement e : json.getAsJsonArray("trades")) {
				ResourceLocation id = ResourceLocation.tryParse(e.getAsString());
				if (id == null) {
					throw new IllegalArgumentException("bad trade '" + e.getAsString() + "'");
				}
				trades.add(id);
			}
		}
		float factor = json.has("factor") ? json.get("factor").getAsFloat() : 1.25f;
		if (factor < 1) {
			throw new IllegalArgumentException("'factor' below 1");
		}
		return new PacePower(Set.copyOf(trades), json.has("radius") ? Math.max(0, json.get("radius").getAsInt()) : 0, factor);
	}

	@Override
	public String type() {
		return "pace";
	}

	/** "Every Builder within 32 blocks works 2× as fast", "Every worker in the village works 1.5× as fast". */
	@Override
	public net.minecraft.network.chat.Component describe() {
		net.minecraft.network.chat.Component who = trades.isEmpty() ? net.minecraft.network.chat.Component.translatable("legend.aliveworkplace.power.pace_everyone")
			: LegendText.trades(trades.stream().sorted().toList());
		String times = LegendText.number(factor);
		return radius > 0 ? net.minecraft.network.chat.Component.translatable("legend.aliveworkplace.power.pace", who, radius, times)
			: net.minecraft.network.chat.Component.translatable("legend.aliveworkplace.power.pace_village", who, times);
	}

	public boolean covers(ResourceLocation trade) {
		return trades.isEmpty() || trades.contains(trade);
	}
}
