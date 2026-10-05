package io.github.jcondedata.aliveworkplace.legend;

import com.google.gson.JsonObject;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * {@code caravan_pay} (the Merchant Prince, 29.17): every stack one of the village's caravans brings to a village that
 * was waiting for it earns the village's treasury {@code emeralds} emeralds ({@code hall.Caravans#unload}).
 */
public record CaravanPayPower(int emeralds) implements Power {
	static CaravanPayPower read(JsonObject json) {
		int emeralds = json.has("emeralds") ? json.get("emeralds").getAsInt() : 1;
		if (emeralds < 1) {
			throw new IllegalArgumentException("'emeralds' below 1");
		}
		return new CaravanPayPower(emeralds);
	}

	@Override
	public String type() {
		return "caravan_pay";
	}

	/** "Every stack our caravans bring to a village that was waiting for it earns the treasury 1 emerald". */
	@Override
	public Component describe() {
		return Component.translatable("legend.aliveworkplace.power.caravan_pay", emeralds);
	}

	/** The caravan pay working for the village round {@code hall}. */
	public static Optional<CaravanPayPower> of(ServerLevel level, BlockPos hall) {
		return LegendPowers.ofVillage(level, hall, CaravanPayPower.class);
	}
}
