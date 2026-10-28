package io.github.jcondedata.aliveworkplace.wonder.powers;

import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.work.Words;
import net.minecraft.network.chat.Component;

/**
 * {@code stadium_days} (35.24, the Stadium's): every {@code every_days}th day, in the afternoon, {@code min_visitors} to
 * {@code max_visitors} visiting trainers stand in the Wonder's trainer boxes until dusk, the village's own trainers take
 * them on, and the villagers off work fill the stands ({@code wonder/StadiumDays}). The defaults are the Stadium's: every
 * second day, two to four visitors.
 */
public record StadiumDaysPower(int everyDays, int minVisitors, int maxVisitors) implements WonderPower {
	public static final String TYPE = "stadium_days";
	/** The most days between two Stadium Days. */
	public static final int MAX_EVERY = 28;
	/** The most visitors a Stadium Day may bring (two boxes, two to a box). */
	public static final int MAX_VISITORS = 4;

	static StadiumDaysPower read(JsonObject json) {
		int every = json.has("every_days") ? json.get("every_days").getAsInt() : 2;
		int min = json.has("min_visitors") ? json.get("min_visitors").getAsInt() : 2;
		int max = json.has("max_visitors") ? json.get("max_visitors").getAsInt() : 4;
		if (every < 1 || every > MAX_EVERY) {
			throw new IllegalArgumentException("'every_days' must be 1 to " + MAX_EVERY + ", not " + every);
		}
		if (min < 1 || max > MAX_VISITORS || min > max) {
			throw new IllegalArgumentException("'min_visitors' and 'max_visitors' must be 1 to " + MAX_VISITORS + ", the least first, not "
				+ min + " and " + max);
		}
		return new StadiumDaysPower(every, min, max);
	}

	@Override
	public String type() {
		return TYPE;
	}

	/** "Stadium Days every second afternoon: 2 to 4 visiting trainers". */
	@Override
	public Component describe() {
		Component when = everyDays == 1 ? Component.translatable("wonder.aliveworkplace.power.stadium_days_daily")
			: everyDays == 2 ? Component.translatable("wonder.aliveworkplace.power.stadium_days_second")
			: Component.translatable("wonder.aliveworkplace.power.stadium_days_every", everyDays);
		Component who = minVisitors == maxVisitors ? Component.literal(String.valueOf(minVisitors))
			: Component.translatable("wonder.aliveworkplace.power.stadium_days_range", minVisitors, maxVisitors);
		long count = minVisitors == maxVisitors ? minVisitors : maxVisitors; // a range reads plural ("1 to 2 visiting trainers")
		return Words.counted("wonder.aliveworkplace.power.stadium_days", count, when, who);
	}

	/** Whether {@code day} (as the chronicle counts them) is a Stadium Day. */
	public boolean on(long day) {
		return Math.floorMod(day, (long) everyDays) == 0;
	}
}
