package io.github.jcondedata.aliveworkplace.work;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Sentences with a number in them (ROADMAP 24.5: "1 parcel", "2 parcels", never "parcel(s)"). A counted message has
 * two language keys: {@code <key>} for every number but one, and {@code <key>.one} for exactly one.
 */
public final class Words {
	/** {@code key.one} when {@code count} is 1, else {@code key}, with {@code args}. */
	public static MutableComponent counted(String key, long count, Object... args) {
		return Component.translatable(count == 1 ? key + ".one" : key, args);
	}

	private Words() {
	}
}
