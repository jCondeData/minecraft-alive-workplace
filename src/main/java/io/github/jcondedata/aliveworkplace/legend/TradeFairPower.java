package io.github.jcondedata.aliveworkplace.legend;

import com.google.gson.JsonObject;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * {@code trade_fair} (the Merchant Prince, 29.17): every {@code days} days the village holds a fair
 * ({@code hall.TradeFairs}): {@code traders} travelling traders and a stall for each village it trades with, every
 * trade there {@code discount} percent cheaper.
 */
public record TradeFairPower(int days, int traders, int discount) implements Power {
	static TradeFairPower read(JsonObject json) {
		int days = json.has("days") ? json.get("days").getAsInt() : 10;
		int traders = json.has("traders") ? json.get("traders").getAsInt() : 6;
		int discount = json.has("discount") ? json.get("discount").getAsInt() : 10;
		if (days < 1 || traders < 0 || discount < 0 || discount > 100) {
			throw new IllegalArgumentException("'days' below 1, 'traders' below 0 or 'discount' outside 0 to 100");
		}
		return new TradeFairPower(days, traders, discount);
	}

	@Override
	public String type() {
		return "trade_fair";
	}

	/** "Every 10 days a trade fair: 6 traders and a stall for each village we trade with, every trade 10% cheaper". */
	@Override
	public Component describe() {
		return Component.translatable("legend.aliveworkplace.power.trade_fair", days, traders, discount);
	}

	public static Optional<TradeFairPower> of(ServerLevel level, BlockPos hall) {
		return LegendPowers.ofVillage(level, hall, TradeFairPower.class);
	}
}
