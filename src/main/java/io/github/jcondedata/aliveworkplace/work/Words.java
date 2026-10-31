package io.github.jcondedata.aliveworkplace.work;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

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
	 * {@code key.one} when {@code count} is 1 and that key exists, else {@code key}, with {@code args}: for a count that
	 * isn't the first argument of a key whose singular is optional, such as a raider culture's own line.
	 */
	public static MutableComponent countedIfAny(String key, long count, Object... args) {
		boolean one = count == 1 && Language.getInstance().has(key + ".one");
		return Component.translatable(one ? key + ".one" : key, args);
	}

	/**
	 * A sentence with "a" before a name (B505: "an Armorer", "an Omanyte", never "a Armorer"): {@code key.an} when the
	 * argument at {@code nameIndex} begins with a vowel sound and that key exists, else {@code key}, with {@code args}.
	 */
	public static MutableComponent articled(String key, int nameIndex, Object... args) {
		boolean an = nameIndex >= 0 && nameIndex < args.length && startsWithVowelSound(text(args[nameIndex]))
			&& Language.getInstance().has(key + ".an");
		return Component.translatable(an ? key + ".an" : key, args);
	}

	/**
	 * {@link #articled} for a key whose name argument isn't known in code, such as a tip or a request line read from a
	 * data file (B523): the name is the argument after the plain line's first "a %s" ("a %3$s" names it outright).
	 */
	public static MutableComponent articledAuto(String key, Object... args) {
		return articled(key, articleIndex(Language.getInstance().getOrDefault(key, key)), args);
	}

	/** The argument index after the first "a %s" or "A %2$s" in {@code template}, or -1 when it has none. */
	static int articleIndex(String template) {
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("%(?:(\\d+)\\$)?([s%])").matcher(template);
		int next = 0;
		while (m.find()) {
			if (m.group(2).equals("%")) {
				continue;
			}
			int index = m.group(1) != null ? Integer.parseInt(m.group(1)) - 1 : next++;
			int at = m.start();
			if (at >= 2 && template.charAt(at - 1) == ' ' && (template.charAt(at - 2) == 'a' || template.charAt(at - 2) == 'A')
				&& (at == 2 || !Character.isLetter(template.charAt(at - 3)))) {
				return index;
			}
		}
		return -1;
	}

	/**
	 * Whether "an" goes before {@code word}: it starts with a, e, i or o, or with a u that isn't said "you" (an Ultra
	 * Ball, an Umbreon; a Unicorn, a Useful one, a Utopia). "Eu" is said "you" too (a Eulogy).
	 */
	public static boolean startsWithVowelSound(String word) {
		String w = word.strip().toLowerCase(java.util.Locale.ROOT);
		if (w.isEmpty()) {
			return false;
		}
		char c = w.charAt(0);
		if (Character.isDigit(c)) {
			return numberStartsWithVowelSound(w);
		}
		if (c == 'e') {
			return !w.startsWith("eu") && !w.startsWith("ewe");
		}
		if (c == 'u') {
			return !(w.startsWith("uni") || w.startsWith("use") || w.startsWith("usu") || w.startsWith("uti") || w.startsWith("uto") || w.startsWith("ura")
				|| w.startsWith("uro") || w.startsWith("ubi"));
		}
		return c == 'a' || c == 'i' || c == 'o';
	}

	/**
	 * B607: a number said with a vowel sound takes "an": eight, eighty, eight hundred (any number that starts with 8),
	 * and eleven or eighteen when said whole or as thousands ("an 11 × 11", "an 18,000"), never "a 110" or "a 12".
	 */
	private static boolean numberStartsWithVowelSound(String w) {
		StringBuilder digits = new StringBuilder();
		for (int i = 0; i < w.length(); i++) {
			char d = w.charAt(i);
			if (Character.isDigit(d)) {
				digits.append(d);
			} else if (d != ',') {
				break;
			}
		}
		String n = digits.toString();
		if (n.startsWith("8")) {
			return true;
		}
		return (n.startsWith("11") || n.startsWith("18")) && n.length() % 3 == 2;
	}

	private static String text(Object arg) {
		return arg instanceof Component c ? c.getString() : String.valueOf(arg);
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

	/**
	 * A creature's plural for a count above one (B568: "Defeat 2 Zombies"), {@code plural.aliveworkplace.entity.<namespace>.<path>},
	 * or null when the lang has none (a modded creature): the caller then writes "Defeat Grub ×2" instead of guessing.
	 */
	@Nullable
	public static Component entityPlural(EntityType<?> type) {
		ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
		String key = "plural.aliveworkplace.entity." + id.getNamespace() + "." + id.getPath();
		return Language.getInstance().has(key) ? Component.translatable(key) : null;
	}

	/**
	 * A trade's plural for a count of villagers (B707: "Fishermen at Apprentice or above"),
	 * {@code plural.aliveworkplace.profession.<namespace>.<path>}, or null when the lang has none (another mod's trade): the
	 * caller then names the trade in the singular instead of guessing ("Fishermans").
	 */
	@Nullable
	public static Component professionPlural(ResourceLocation job) {
		String key = "plural.aliveworkplace.profession." + job.getNamespace() + "." + job.getPath();
		return Language.getInstance().has(key) ? Component.translatable(key) : null;
	}

	private Words() {
	}
}
