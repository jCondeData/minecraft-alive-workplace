package io.github.jcondedata.aliveworkplace.legend;

import com.google.gson.JsonObject;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * {@code banquet} (the Grand Chef, 29.18): every {@code days} days at supper the village gathers at its Market Square (or
 * the hall) and each grown-up eats {@code meals} meals from the store, of as many kinds as it has
 * ({@code hall.Banquets}). Everyone who came is {@code mood} happier for {@code mood_days} days, and for
 * {@code growth_days} days the village's wait between babies is halved.
 */
public record BanquetPower(int days, int meals, int mood, int moodDays, int growthDays) implements Power {
	static BanquetPower read(JsonObject json) {
		int days = json.has("days") ? json.get("days").getAsInt() : 5;
		int meals = json.has("meals") ? json.get("meals").getAsInt() : 2;
		int mood = json.has("mood") ? json.get("mood").getAsInt() : 20;
		int moodDays = json.has("mood_days") ? json.get("mood_days").getAsInt() : 3;
		int growthDays = json.has("growth_days") ? json.get("growth_days").getAsInt() : 3;
		if (days < 1 || meals < 1 || moodDays < 0 || growthDays < 0) {
			throw new IllegalArgumentException("'days' or 'meals' below 1, or 'mood_days' or 'growth_days' below 0");
		}
		return new BanquetPower(days, meals, mood, moodDays, growthDays);
	}

	/** The file's defaults: every 5 days, two meals each, +20 for 3 days, babies twice as often for 3 days. */
	public static final BanquetPower DEFAULT = new BanquetPower(5, 2, 20, 3, 3);

	@Override
	public String type() {
		return "banquet";
	}

	/** "Every 5 days a banquet: two meals each, everyone who comes 20 happier for 3 days, babies twice as often for 3 days". */
	@Override
	public Component describe() {
		return Component.translatable("legend.aliveworkplace.power.banquet", days, meals, mood, moodDays, growthDays);
	}

	public static Optional<BanquetPower> of(ServerLevel level, BlockPos hall) {
		return LegendPowers.ofVillage(level, hall, BanquetPower.class);
	}
}
