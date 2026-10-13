package io.github.jcondedata.aliveworkplace.wonder.powers;

import com.google.gson.JsonObject;

/**
 * {@code raid_barrier} (35.16, the Great Wall's): while the Wonder stands, no raid gathers behind it. The night raids'
 * and the bandits' gathering points ({@code guard/VillageRaids.gatheringPoint}) keep out of the arc the Wonder covers as
 * seen from its village's hall, widened by {@code margin_degrees} either side (default 10, 0 to {@link #MAX}), and a
 * siege's ladders never go up against it ({@code wonder/GreatWallPowers}).
 */
public record RaidBarrierPower(double marginDegrees) implements WonderPower {
	public static final String TYPE = "raid_barrier";
	/** The widest margin a file may ask for. */
	public static final double MAX = 45;

	static RaidBarrierPower read(JsonObject json) {
		double margin = json.has("margin_degrees") ? json.get("margin_degrees").getAsDouble() : 10;
		if (!(margin >= 0 && margin <= MAX)) {
			throw new IllegalArgumentException("'margin_degrees' must be 0 to " + (int) MAX + ", not " + margin);
		}
		return new RaidBarrierPower(margin);
	}

	@Override
	public String type() {
		return TYPE;
	}
}
