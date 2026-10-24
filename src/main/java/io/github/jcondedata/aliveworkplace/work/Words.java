package io.github.jcondedata.aliveworkplace.work;

import net.minecraft.locale.Language;
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

	/**
	 * {@code key.one} when the first argument is the number 1 and that key exists, else {@code key}: for keys whose
	 * singular form is optional, such as a tip or a Steward reason read from a data file.
	 */
	public static MutableComponent countedFirst(String key, Object... args) {
		boolean one = args.length > 0 && args[0] instanceof Number n && n.longValue() == 1 && Language.getInstance().has(key + ".one");
		return Component.translatable(one ? key + ".one" : key, args);
	}

	private Words() {
	}
}
