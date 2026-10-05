package io.github.jcondedata.aliveworkplace.legend;

import com.google.gson.JsonObject;

/**
 * The toolbox effects the Gifted use (29.6), read from {@code gifted/<id>.json} like a Legend's powers
 * ({@link Powers#parse}). Each is read where it acts: {@link Gifted#xpFactor}, {@link Gifted#noPanic},
 * {@link Gifted#tradeDiscount} and {@link Gifted#nightShift}.
 */
public final class GiftPowers {
	/** {@code xp}: learns {@code factor} times as fast (with Clever too, still {@code factor} times). */
	public record Xp(float factor) implements Power {
		static Xp read(JsonObject json) {
			float factor = json.has("factor") ? json.get("factor").getAsFloat() : 3f;
			if (factor < 1) {
				throw new IllegalArgumentException("'factor' below 1");
			}
			return new Xp(factor);
		}

		@Override
		public String type() {
			return "xp";
		}
	}

	/** {@code no_panic}: never panics, and keeps working through raids and the bell. */
	public record NoPanic() implements Power {
		static NoPanic read(JsonObject json) {
			return new NoPanic();
		}

		@Override
		public String type() {
			return "no_panic";
		}
	}

	/** {@code trade_discount}: every trade with a player is {@code percent}% cheaper. */
	public record TradeDiscount(int percent) implements Power {
		static TradeDiscount read(JsonObject json) {
			int percent = json.has("percent") ? json.get("percent").getAsInt() : 20;
			if (percent < 0 || percent > 100) {
				throw new IllegalArgumentException("'percent' outside 0-100");
			}
			return new TradeDiscount(percent);
		}

		@Override
		public String type() {
			return "trade_discount";
		}
	}

	/** {@code night_shift}: works from dusk to dawn and sleeps from mid-morning to mid-afternoon. */
	public record NightShift() implements Power {
		static NightShift read(JsonObject json) {
			return new NightShift();
		}

		@Override
		public String type() {
			return "night_shift";
		}
	}

	static void register() {
		Powers.register("xp", Xp::read);
		Powers.register("no_panic", NoPanic::read);
		Powers.register("trade_discount", TradeDiscount::read);
		Powers.register("night_shift", NightShift::read);
	}

	private GiftPowers() {
	}
}
