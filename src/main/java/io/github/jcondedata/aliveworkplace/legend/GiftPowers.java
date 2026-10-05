package io.github.jcondedata.aliveworkplace.legend;

import com.google.gson.JsonObject;

/**
 * The toolbox effects the Gifted use (29.6), read from {@code gifted/<id>.json} like a Legend's powers
 * ({@link Powers#parse}). Each is read where it acts: {@link Gifted#xpFactor}, {@link Gifted#noPanic},
 * {@link Gifted#tradeDiscount}, {@link Gifted#nightShift}, {@link Gifted#lootLuck}, {@link Gifted#noIllness} and
 * {@link Gifted#healthFactor} (29.7). Beloved and Born Leader use the Legends' {@link MoodPower} and {@link PacePower},
 * worked by {@link GiftedAuras}.
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

	/** {@code loot_luck}: {@code luck} more luck on every loot roll their work makes (29.7, Lucky). */
	public record LootLuck(int luck) implements Power {
		static LootLuck read(JsonObject json) {
			int luck = json.has("luck") ? json.get("luck").getAsInt() : 3;
			if (luck < 0) {
				throw new IllegalArgumentException("'luck' below 0");
			}
			return new LootLuck(luck);
		}

		@Override
		public String type() {
			return "loot_luck";
		}
	}

	/** {@code no_illness}: never falls ill (29.7, Hardy). */
	public record NoIllness() implements Power {
		static NoIllness read(JsonObject json) {
			return new NoIllness();
		}

		@Override
		public String type() {
			return "no_illness";
		}
	}

	/** {@code health}: {@code factor} times a villager's health (29.7, Hardy). */
	public record Health(float factor) implements Power {
		static Health read(JsonObject json) {
			float factor = json.has("factor") ? json.get("factor").getAsFloat() : 2f;
			if (factor < 1) {
				throw new IllegalArgumentException("'factor' below 1");
			}
			return new Health(factor);
		}

		@Override
		public String type() {
			return "health";
		}
	}

	static void register() {
		Powers.register("xp", Xp::read);
		Powers.register("no_panic", NoPanic::read);
		Powers.register("trade_discount", TradeDiscount::read);
		Powers.register("night_shift", NightShift::read);
		Powers.register("loot_luck", LootLuck::read);
		Powers.register("no_illness", NoIllness::read);
		Powers.register("health", Health::read);
	}

	private GiftPowers() {
	}
}
