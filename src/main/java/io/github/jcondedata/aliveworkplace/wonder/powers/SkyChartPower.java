package io.github.jcondedata.aliveworkplace.wonder.powers;

import com.google.gson.JsonObject;

/**
 * {@code sky_chart} (35.18, the Observatory's): while the Wonder stands, the hall's map button also draws the Sky Chart,
 * about {@code size} blocks across (default 6,144: 384 chunks, 12 regions; 512 to 12,288), centred on the hall, with a
 * named marker for every structure the Observatory has found ({@code wonder/ObservatoryPowers}, {@code wonder/SkyChart}).
 */
public record SkyChartPower(int size) implements WonderPower {
	public static final String TYPE = "sky_chart";
	public static final int MIN = 512;
	public static final int MAX = 12_288;

	static SkyChartPower read(JsonObject json) {
		int size = json.has("size") ? json.get("size").getAsInt() : 6_144;
		if (size < MIN || size > MAX) {
			throw new IllegalArgumentException("'size' must be " + MIN + " to " + MAX + ", not " + size);
		}
		return new SkyChartPower(size);
	}

	@Override
	public String type() {
		return TYPE;
	}
}
