package io.github.jcondedata.aliveworkplace.work;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

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
	 * A sentence with two counts, each picking its own form ("3 guards would march, 1 keeps the walls"): {@code key},
	 * then {@code .one} when {@code first} is 1, then {@code .rest_one} when {@code second} is 1. Such a key has four
	 * forms: {@code key}, {@code key.one}, {@code key.rest_one} and {@code key.one.rest_one}.
	 */
	public static MutableComponent countedTwo(String key, long first, long second, Object... args) {
		return Component.translatable(key + (first == 1 ? ".one" : "") + (second == 1 ? ".rest_one" : ""), args);
	}

	/**
	 * {@code key.one} when the first argument is the number 1 and that key exists, else {@code key}: for keys whose
	 * singular form is optional, such as a tip or a Steward reason read from a data file.
	 */
	public static MutableComponent countedFirst(String key, Object... args) {
		boolean one = args.length > 0 && args[0] instanceof Number n && n.longValue() == 1 && Language.getInstance().has(key + ".one");
		return Component.translatable(one ? key + ".one" : key, args);
	}

	/**
	 * An item's name after a count (B407: "4 Baked Potatoes", "1 Pumpkin Pie"): for any count but one, its plural
	 * {@code plural.aliveworkplace.<namespace>.<path>} when the lang has it, else the item's own name.
	 */
	public static Component itemName(Item item, long count) {
		Component name = item.getDescription();
		if (count == 1) {
			return name;
		}
		ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
		String key = "plural.aliveworkplace." + id.getNamespace() + "." + id.getPath();
		return Language.getInstance().has(key) ? Component.translatable(key) : name;
	}

	private Words() {
	}
}
