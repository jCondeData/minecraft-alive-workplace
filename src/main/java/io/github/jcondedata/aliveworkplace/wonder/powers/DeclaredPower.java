package io.github.jcondedata.aliveworkplace.wonder.powers;

import com.google.gson.JsonObject;

/**
 * A power type our Wonders' files name whose code comes with that Wonder's own item (35.10 to 35.24: {@code undertakers}
 * with the Cathedral's, {@code caravan_range} with the Lighthouse's, ...): it loads with its numbers as written, so the
 * files read today, and {@code Wonders.power} already answers for it; the feature it changes reads the numbers once its
 * item registers a reader of its own in its place ({@link WonderPowers#register}). {@code scope: "realm"} reaches the realm.
 */
public record DeclaredPower(String type, JsonObject numbers) implements WonderPower {
	static DeclaredPower read(String type, JsonObject json) {
		JsonObject numbers = json.deepCopy();
		numbers.remove("type");
		return new DeclaredPower(type, numbers);
	}

	@Override
	public boolean realm() {
		return numbers.has("scope") && "realm".equals(numbers.get("scope").getAsString());
	}

	/** A number of the power's ({@code factor}), or {@code fallback} when the file leaves it out. */
	public double number(String name, double fallback) {
		return numbers.has(name) && numbers.get(name).isJsonPrimitive() && numbers.get(name).getAsJsonPrimitive().isNumber()
			? numbers.get(name).getAsDouble() : fallback;
	}
}
