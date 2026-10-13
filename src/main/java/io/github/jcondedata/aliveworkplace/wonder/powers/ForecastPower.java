package io.github.jcondedata.aliveworkplace.wonder.powers;

import com.google.gson.JsonObject;

/**
 * {@code forecast} (35.18, the Observatory's): while the Wonder stands, the hall's calendar page shows the Coming days:
 * the next festival and market day, weddings due, caravans on the road, a traveller expected at the inn, the full moon,
 * bandit camps, the night's raid risk and what the Seer foretells ({@code wonder/ObservatoryPowers#comingDays}). It has
 * no numbers.
 */
public record ForecastPower() implements WonderPower {
	public static final String TYPE = "forecast";

	static ForecastPower read(JsonObject json) {
		return new ForecastPower();
	}

	@Override
	public String type() {
		return TYPE;
	}
}
