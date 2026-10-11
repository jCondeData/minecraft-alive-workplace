package io.github.jcondedata.aliveworkplace.legend;

import com.google.gson.JsonObject;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * {@code trade_reach} (the Merchant Prince, ROADMAP 33.23): his village's board shows the prices of every village
 * within caravan range, not only its partners' ({@code trade.TradePage#goodIcon}); its caravans take their goods for sale
 * only to whichever partner pays most for them ({@code trade.CaravanTrade#plan}); and its board's spread is
 * {@code spread} percent of what it was ({@code trade.TradePage#sellPercent}).
 */
public record TradeReachPower(int spread) implements Power {
	static TradeReachPower read(JsonObject json) {
		int spread = json.has("spread") ? json.get("spread").getAsInt() : 50;
		if (spread < 0 || spread > 100) {
			throw new IllegalArgumentException("'spread' outside 0 to 100");
		}
		return new TradeReachPower(spread);
	}

	@Override
	public String type() {
		return "trade_reach";
	}

	/** "The board knows every village's prices within caravan range; goods go where they pay most; the spread is 50% of what it was". */
	@Override
	public Component describe() {
		return Component.translatable("legend.aliveworkplace.power.trade_reach", spread);
	}

	/** The power working for the village round {@code hall} (its Merchant Prince settled there), if any. */
	public static Optional<TradeReachPower> of(ServerLevel level, BlockPos hall) {
		return LegendPowers.ofVillage(level, hall, TradeReachPower.class);
	}
}
