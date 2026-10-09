package io.github.jcondedata.aliveworkplace.threat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import org.jetbrains.annotations.Nullable;

/**
 * A raider culture (ROADMAP 32.2), one file {@code data/<ns>/raider_cultures/<id>.json}:
 * <ul>
 * <li>{@code where} ({@link Conditions}) and {@code weight}: which villages it comes to, and how often among the
 * cultures that fit;</li>
 * <li>{@code arrival} ({@code edge}, {@code lair}, {@code shore}, {@code portal}) and {@code hours} ({@code night}: its
 * raiders flee at dawn; {@code until_noon});</li>
 * <li>{@code roster}: who comes — {@code entity}, {@code share}, {@code role} ({@code melee}, {@code ranged},
 * {@code ram}, {@code climber}, {@code healer}) and {@code gear} per slot ({@code head}, {@code chest}, {@code legs},
 * {@code feet}, {@code mainhand}, {@code offhand}: an item id, or {@code ominous_banner}); {@code name}, the lang key
 * of what its raiders are called;</li>
 * <li>{@code captain}: {@code entity}, {@code gear}, {@code health} (extra) and {@code names} (the lang key of his
 * list of names);</li>
 * <li>{@code tactics}: names the engine looks up ({@link Threats#registerTactic}); one it doesn't know is skipped;</li>
 * <li>{@code lair}: {@code structure}, {@code strength} (and {@code growth} a day, {@code max}, {@code home_max});</li>
 * <li>{@code loot} (a loot table), {@code messages} and {@code chronicle}: a key prefix ({@code <prefix>.raid}), or
 * the keys themselves by event ({@code {"raid": "message.aliveworkplace.raid.begins"}}).</li>
 * </ul>
 * Ours may leave the namespace off an id. A key that isn't known is logged and ignored.
 */
public record Culture(ResourceLocation id, Conditions where, int weight, Arrival arrival, Hours hours, List<Member> roster, Optional<String> name,
	Optional<Captain> captain, List<String> tactics, Optional<Lair> lair, Optional<ResourceLocation> loot, Keys messages, Keys chronicle) {
	/** Gear that isn't an item id: the banner a raid captain carries. */
	public static final String OMINOUS_BANNER = "ominous_banner";
	private static final Set<String> KEYS = Set.of("where", "weight", "arrival", "hours", "roster", "name", "captain", "tactics", "lair", "loot",
		"messages", "chronicle");

	/** How its raiders reach the village. */
	public enum Arrival {
		/** They gather at the village's edge. */
		EDGE,
		/** They gather on the side of their lair. */
		LAIR,
		/** They come ashore: the edge of the village where the land meets water. */
		SHORE,
		/** They step out of the village's Nether portal. */
		PORTAL
	}

	/** How long its raiders stay. */
	public enum Hours {
		/** Till dawn. */
		NIGHT,
		/** Through the morning, till noon. */
		UNTIL_NOON
	}

	/** What a raider does in a siege; kept on the mob as the entity tag {@link #tag()}. */
	public enum Role {
		MELEE, RANGED, RAM, CLIMBER, HEALER;

		public String tag() {
			return "aliveworkplace_role_" + name().toLowerCase(Locale.ROOT);
		}
	}

	/** One line of the roster: the mob, its share of the raid, its role and what it carries (slot → item id or {@link #OMINOUS_BANNER}). */
	public record Member(ResourceLocation entity, int share, Role role, Map<EquipmentSlot, String> gear) {
	}

	/** The captain: his mob, gear, extra health and (once he has one) the lang key of his list of names. */
	public record Captain(ResourceLocation entity, Map<EquipmentSlot, String> gear, double health, Optional<String> names) {
	}

	/** The lair: its structure, the band's strength when founded, what it gains a day, its most, and how many stand at home. */
	public record Lair(ResourceLocation structure, int strength, int growth, int max, int homeMax) {
	}

	/** Lang keys by event ({@code raid}, {@code camp}, {@code broken}, …): the culture's own, else {@code <prefix>.<event>}. */
	public record Keys(Optional<String> prefix, Map<String, String> own) {
		public static final Keys NONE = new Keys(Optional.empty(), Map.of());

		/** The key for {@code event}, or {@code fallback} when the culture gives none. */
		public String key(String event, String fallback) {
			String key = own.get(event);
			return key != null ? key : prefix.map(p -> p + "." + event).orElse(fallback);
		}
	}

	/** The sum of the roster's shares. */
	public int shares() {
		return roster.stream().mapToInt(Member::share).sum();
	}

	/** One of the roster, each as often as its share. */
	public Member pick(RandomSource random) {
		int roll = random.nextInt(shares());
		for (Member member : roster) {
			roll -= member.share();
			if (roll < 0) {
				return member;
			}
		}
		return roster.get(roster.size() - 1);
	}

	/** Whether its raids come from a lair (and only while one stands). */
	public boolean needsLair() {
		return lair.isPresent();
	}

	/** Reads one culture; throws {@link IllegalArgumentException} naming what's wrong. */
	public static Culture read(ResourceLocation id, JsonObject json) {
		for (String key : json.keySet()) {
			if (!KEYS.contains(key)) {
				AliveWorkplace.LOG.warn("Raider culture {}: the key '{}' isn't known and is ignored", id, key);
			}
		}
		Conditions where = json.has("where") ? Conditions.read(json.getAsJsonObject("where"), "Raider culture " + id) : Conditions.ANY;
		int weight = integer(json, "weight", 10, 1, 1000);
		Arrival arrival = constant(Arrival.class, json, "arrival", Arrival.EDGE);
		Hours hours = constant(Hours.class, json, "hours", Hours.NIGHT);
		List<Member> roster = new ArrayList<>();
		if (!json.has("roster")) {
			throw new IllegalArgumentException("missing roster");
		}
		for (JsonElement e : json.getAsJsonArray("roster")) {
			JsonObject m = e.getAsJsonObject();
			roster.add(new Member(entity(m), integer(m, "share", null, 1, 1000), constant(Role.class, m, "role", Role.MELEE), gear(m)));
		}
		if (roster.isEmpty()) {
			throw new IllegalArgumentException("an empty roster");
		}
		Optional<Captain> captain = Optional.empty();
		if (json.has("captain")) {
			JsonObject c = json.getAsJsonObject("captain");
			double health = c.has("health") ? c.get("health").getAsDouble() : 0;
			if (health < 0 || health > 1000) {
				throw new IllegalArgumentException("the captain's extra health can't be " + health);
			}
			captain = Optional.of(new Captain(entity(c), gear(c), health, Optional.ofNullable(c.has("names") ? c.get("names").getAsString() : null)));
		}
		List<String> tactics = new ArrayList<>();
		if (json.has("tactics")) {
			for (JsonElement e : json.getAsJsonArray("tactics")) {
				tactics.add(e.getAsString().toLowerCase(Locale.ROOT));
			}
		}
		Optional<Lair> lair = Optional.empty();
		Optional<ResourceLocation> loot = json.has("loot") ? Optional.of(location(json.get("loot").getAsString(), "loot")) : Optional.empty();
		if (json.has("lair")) {
			JsonObject l = json.getAsJsonObject("lair");
			if (!l.has("structure")) {
				throw new IllegalArgumentException("a lair without a structure");
			}
			int strength = integer(l, "strength", null, 1, 64);
			int max = integer(l, "max", strength, strength, 64);
			lair = Optional.of(new Lair(location(l.get("structure").getAsString(), "structure"), strength, integer(l, "growth", 0, 0, 64), max,
				integer(l, "home_max", Math.min(8, max), 1, 16)));
			if (l.has("loot")) {
				loot = Optional.of(location(l.get("loot").getAsString(), "loot"));
			}
		}
		Optional<String> name = Optional.ofNullable(json.has("name") ? json.get("name").getAsString() : null);
		return new Culture(id, where, weight, arrival, hours, List.copyOf(roster), name, captain, List.copyOf(tactics), lair, loot,
			keys(json, "messages"), keys(json, "chronicle"));
	}

	private static Keys keys(JsonObject json, String key) {
		if (!json.has(key)) {
			return Keys.NONE;
		}
		JsonElement e = json.get(key);
		if (e.isJsonPrimitive()) {
			return new Keys(Optional.of(e.getAsString()), Map.of());
		}
		Map<String, String> own = new LinkedHashMap<>();
		e.getAsJsonObject().entrySet().forEach(entry -> own.put(entry.getKey(), entry.getValue().getAsString()));
		return new Keys(Optional.empty(), Collections.unmodifiableMap(own));
	}

	/** The mob named by {@code json}'s {@code entity}: it must be one the game knows. */
	private static ResourceLocation entity(JsonObject json) {
		if (!json.has("entity")) {
			throw new IllegalArgumentException("missing entity");
		}
		ResourceLocation id = location(json.get("entity").getAsString(), "entity");
		if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
			throw new IllegalArgumentException("no such entity: " + id);
		}
		return id;
	}

	private static Map<EquipmentSlot, String> gear(JsonObject json) {
		Map<EquipmentSlot, String> gear = new EnumMap<>(EquipmentSlot.class);
		if (json.has("gear")) {
			for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("gear").entrySet()) {
				EquipmentSlot slot = slot(e.getKey());
				String item = e.getValue().getAsString();
				if (!item.equals(OMINOUS_BANNER)) {
					ResourceLocation id = location(item, "gear");
					if (!BuiltInRegistries.ITEM.containsKey(id)) {
						throw new IllegalArgumentException("no such item: " + id);
					}
					item = id.toString();
				}
				gear.put(slot, item);
			}
		}
		return Collections.unmodifiableMap(gear);
	}

	private static EquipmentSlot slot(String name) {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (slot.getName().equals(name) && slot != EquipmentSlot.BODY) {
				return slot;
			}
		}
		throw new IllegalArgumentException("gear slot must be head, chest, legs, feet, mainhand or offhand, not " + name);
	}

	/** An id; ours may be written without the namespace. */
	static ResourceLocation location(String text, String what) {
		ResourceLocation id = text.contains(":") ? ResourceLocation.tryParse(text) : ResourceLocation.tryBuild(AliveWorkplace.MOD_ID, text);
		if (id == null) {
			throw new IllegalArgumentException(what + " '" + text + "' is no id");
		}
		return id;
	}

	private static int integer(JsonObject json, String key, @Nullable Integer fallback, int min, int max) {
		if (!json.has(key)) {
			if (fallback == null) {
				throw new IllegalArgumentException("missing " + key);
			}
			return fallback;
		}
		int value = json.get(key).getAsInt();
		if (value < min || value > max) {
			throw new IllegalArgumentException(key + " must be " + min + " to " + max + ", not " + value);
		}
		return value;
	}

	private static <E extends Enum<E>> E constant(Class<E> type, JsonObject json, String key, E fallback) {
		if (!json.has(key)) {
			return fallback;
		}
		String name = json.get(key).getAsString();
		try {
			return Enum.valueOf(type, name.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			List<String> names = new ArrayList<>();
			for (E value : type.getEnumConstants()) {
				names.add(value.name().toLowerCase(Locale.ROOT));
			}
			throw new IllegalArgumentException(key + " must be one of " + names + ", not " + name);
		}
	}
}
