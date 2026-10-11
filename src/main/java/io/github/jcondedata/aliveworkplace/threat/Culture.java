package io.github.jcondedata.aliveworkplace.threat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
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
 * {@code feet}, {@code mainhand}, {@code offhand}: an item id, with components as {@code /give} takes them if it likes,
 * or {@code ominous_banner}); {@code name}, the lang key
 * of what its raiders are called;</li>
 * <li>{@code captain}: {@code entity}, {@code gear}, {@code health} (extra), {@code names} (the lang key of his list
 * of names: {@code <names>.1} to {@code <names>.20}) and {@code title} (the lang key of what goes before the name:
 * "Chief" of "Chief Harl Ashgrave"), or {@code name}: the lang key of the one name he always has ("Silas Brine", 32.8),
 * {@code raises} (32.9: {@code entity}, {@code radius}, {@code delay}, {@code health}: while he lives, such a mob
 * falling near him rises once more), {@code zombifies_band} (32.10, the Warlord: when he falls, his band left in the
 * Overworld turns zombified, {@link Piglins}), and {@code leads}: from this rank of village on ({@code town}, {@code city}) he leads
 * the raids from his lair himself (32.7), and falls with his lair wherever he is brought down;</li>
 * <li>{@code extras}: who comes with every raid besides the roster, by the village's rank, as many as {@code count}
 * gives for the highest rank it names that the village has reached ({@code {"town": 1, "city": 2}}): the warband's
 * ravager rams and its evoker (32.7). They are no part of a lair's strength: the band never loses them;</li>
 * <li>{@code tactics}: names the engine looks up ({@link Threats#registerTactic}); one it doesn't know is skipped;</li>
 * <li>{@code lair}: {@code structure}, {@code strength} (and {@code growth} a day, {@code max}, {@code home_max}) and
 * {@code icon} (the item that stands for it on the hall's Defence page; a campfire when it names none); for a lair on
 * the sea (32.8, {@link Ships}) {@code waterline} (the template's y of the sea's top block), {@code near} and {@code far}
 * (blocks from the hall), {@code depth} (the least depth of water under it) and {@code sinks} (when its captain falls
 * it burns and sinks, its chest left on a raft); {@code portal} (32.10: the lair has a lit Nether portal its raiders
 * step out of, put out when it is broken, {@link Piglins}); for a lair on land {@code near} and {@code far} (blocks from the
 * hall it makes camp; 80 and 104 when not given); and {@code captain_at}, where in the template its captain stands;</li>
 * <li>{@code loot} (a loot table: the chests of its lair hold it), {@code messages} and {@code chronicle}: a key prefix
 * ({@code <prefix>.raid}), or the keys themselves by event ({@code {"raid": "message.aliveworkplace.raid.begins"}}).
 * The events: {@code raid}; for a lair {@code camp}, {@code broken} and (chronicle) {@code broken_by}; and, once the
 * lair's captain has a name, {@code raid_named}, {@code camp_named}, {@code broken_named} and {@code broken_by_named},
 * which get his name as well ({@code threat/Lairs}, 32.3).</li>
 * </ul>
 * Ours may leave the namespace off an id. A key that isn't known is logged and ignored.
 */
public record Culture(ResourceLocation id, Conditions where, int weight, Arrival arrival, Hours hours, List<Member> roster, Optional<String> name,
	Optional<Captain> captain, List<String> tactics, Optional<Lair> lair, Optional<ResourceLocation> loot, Keys messages, Keys chronicle, List<Extra> extras) {
	/** Gear that isn't an item id: the banner a raid captain carries. */
	public static final String OMINOUS_BANNER = "ominous_banner";
	private static final Set<String> KEYS = Set.of("where", "weight", "arrival", "hours", "roster", "name", "captain", "tactics", "lair", "loot",
		"messages", "chronicle", "extras");

	/** A culture without {@code extras} (as cultures were before 32.7). */
	public Culture(ResourceLocation id, Conditions where, int weight, Arrival arrival, Hours hours, List<Member> roster, Optional<String> name,
		Optional<Captain> captain, List<String> tactics, Optional<Lair> lair, Optional<ResourceLocation> loot, Keys messages, Keys chronicle) {
		this(id, where, weight, arrival, hours, roster, name, captain, tactics, lair, loot, messages, chronicle, List.of());
	}

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

	/**
	 * What a captain raises (32.9, the Pharaoh): while he lives, a mob of {@code entity} that falls within {@code radius}
	 * blocks of him rises once more after {@code delay} ticks, at {@code health} of its full health.
	 */
	public record Raise(ResourceLocation entity, int radius, int delay, double health) {
	}

	/** One line of the roster: the mob, its share of the raid, its role and what it carries (slot → item id or {@link #OMINOUS_BANNER}). */
	public record Member(ResourceLocation entity, int share, Role role, Map<EquipmentSlot, String> gear) {
	}

	/**
	 * The captain: his mob, gear, extra health, the lang key of his list of names and that of his title ("Chief"), and
	 * the rank of village from which he leads his lair's raids himself (none: he never leaves it).
	 */
	public record Captain(ResourceLocation entity, Map<EquipmentSlot, String> gear, double health, Optional<String> names, Optional<String> title,
		Optional<VillageRanks.Rank> leads, Optional<String> name, Optional<Raise> raises, boolean zombifiesBand) {
		public Captain(ResourceLocation entity, Map<EquipmentSlot, String> gear, double health, Optional<String> names, Optional<String> title,
			Optional<VillageRanks.Rank> leads, Optional<String> name, Optional<Raise> raises) {
			this(entity, gear, health, names, title, leads, name, raises, false);
		}

		public Captain(ResourceLocation entity, Map<EquipmentSlot, String> gear, double health, Optional<String> names, Optional<String> title,
			Optional<VillageRanks.Rank> leads, Optional<String> name) {
			this(entity, gear, health, names, title, leads, name, Optional.empty());
		}

		public Captain(ResourceLocation entity, Map<EquipmentSlot, String> gear, double health, Optional<String> names, Optional<String> title) {
			this(entity, gear, health, names, title, Optional.empty(), Optional.empty());
		}

		public Captain(ResourceLocation entity, Map<EquipmentSlot, String> gear, double health, Optional<String> names, Optional<String> title,
			Optional<VillageRanks.Rank> leads) {
			this(entity, gear, health, names, title, leads, Optional.empty());
		}

		/** Whether he has a name of his own: one from a list, or the one he always has. */
		public boolean hasName() {
			return names.isPresent() || name.isPresent();
		}

		/** Whether he leads the raids on a village of {@code rank}. */
		public boolean leadsAt(VillageRanks.Rank rank) {
			return leads.isPresent() && rank.ordinal() >= leads.get().ordinal();
		}
	}

	/**
	 * One of the {@code extras}: the mob, its role and gear, and how many come by the village's rank (the highest rank
	 * listed that the village has reached; none below the lowest).
	 */
	public record Extra(ResourceLocation entity, Role role, Map<EquipmentSlot, String> gear, Map<VillageRanks.Rank, Integer> count) {
		/** How many come to a village of {@code rank}. */
		public int count(VillageRanks.Rank rank) {
			int n = 0;
			for (Map.Entry<VillageRanks.Rank, Integer> e : count.entrySet()) {
				if (e.getKey().ordinal() <= rank.ordinal()) {
					n = e.getValue(); // (an EnumMap: in rank order, so the highest reached is the last)
				}
			}
			return n;
		}

		/** As a line of the roster, for outfitting. */
		public Member member() {
			return new Member(entity, 0, role, gear);
		}
	}

	/**
	 * The lair: its structure, the band's strength when founded, what it gains a day, its most, how many stand at home,
	 * and the item that stands for it on the Defence page.
	 */
	public record Lair(ResourceLocation structure, int strength, int growth, int max, int homeMax, ResourceLocation icon, Optional<Sea> sea,
		Optional<BlockPos> captainAt, boolean portal, int near, int far) {
		public Lair(ResourceLocation structure, int strength, int growth, int max, int homeMax, ResourceLocation icon, Optional<Sea> sea,
			Optional<BlockPos> captainAt) {
			this(structure, strength, growth, max, homeMax, icon, sea, captainAt, false, Lairs.NEAR, Lairs.FAR);
		}

		public Lair(ResourceLocation structure, int strength, int growth, int max, int homeMax, ResourceLocation icon) {
			this(structure, strength, growth, max, homeMax, icon, Optional.empty(), Optional.empty());
		}
	}

	/**
	 * A lair on the sea (32.8): the template's y of the sea's top block, how far from the hall it anchors, the least depth
	 * of water under it, and whether it burns and sinks when its captain falls.
	 */
	public record Sea(int waterline, int near, int far, int depth, boolean sinks) {
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
			captain = Optional.of(new Captain(entity(c), gear(c), health, Optional.ofNullable(c.has("names") ? c.get("names").getAsString() : null),
				Optional.ofNullable(c.has("title") ? c.get("title").getAsString() : null), c.has("leads") ? Optional.of(rank(c.get("leads").getAsString())) : Optional.empty(),
				Optional.ofNullable(c.has("name") ? c.get("name").getAsString() : null), raise(c),
				c.has("zombifies_band") && c.get("zombifies_band").getAsBoolean()));
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
			ResourceLocation icon = l.has("icon") ? location(l.get("icon").getAsString(), "icon") : ResourceLocation.withDefaultNamespace("campfire");
			if (!BuiltInRegistries.ITEM.containsKey(icon)) {
				throw new IllegalArgumentException("no such item: " + icon);
			}
			Optional<Sea> sea = Optional.empty();
			if (l.has("waterline")) {
				int near = integer(l, "near", 40, 8, 160);
				sea = Optional.of(new Sea(integer(l, "waterline", null, 0, 32), near, integer(l, "far", near + 30, near, 200), integer(l, "depth", 5, 1, 32),
					l.has("sinks") && l.get("sinks").getAsBoolean()));
			}
			Optional<BlockPos> captainAt = Optional.empty();
			if (l.has("captain_at")) {
				JsonArray at = l.getAsJsonArray("captain_at");
				if (at.size() != 3) {
					throw new IllegalArgumentException("captain_at must be [x, y, z]");
				}
				captainAt = Optional.of(new BlockPos(at.get(0).getAsInt(), at.get(1).getAsInt(), at.get(2).getAsInt()));
			}
			lair = Optional.of(new Lair(location(l.get("structure").getAsString(), "structure"), strength, integer(l, "growth", 0, 0, 64), max,
				integer(l, "home_max", Math.min(8, max), 1, 16), icon, sea, captainAt, l.has("portal") && l.get("portal").getAsBoolean(),
				sea.isPresent() ? Lairs.NEAR : near(l), sea.isPresent() ? Lairs.FAR : integer(l, "far", Math.max(near(l), Lairs.FAR), near(l), 200)));
			if (l.has("loot")) {
				loot = Optional.of(location(l.get("loot").getAsString(), "loot"));
			}
		}
		List<Extra> extras = new ArrayList<>();
		if (json.has("extras")) {
			for (JsonElement e : json.getAsJsonArray("extras")) {
				JsonObject x = e.getAsJsonObject();
				if (!x.has("count")) {
					throw new IllegalArgumentException("an extra without a count");
				}
				Map<VillageRanks.Rank, Integer> count = new EnumMap<>(VillageRanks.Rank.class);
				for (Map.Entry<String, JsonElement> c : x.getAsJsonObject("count").entrySet()) {
					int n = c.getValue().getAsInt();
					if (n < 0 || n > 8) {
						throw new IllegalArgumentException("an extra's count must be 0 to 8, not " + n);
					}
					count.put(rank(c.getKey()), n);
				}
				extras.add(new Extra(entity(x), constant(Role.class, x, "role", Role.MELEE), gear(x), Collections.unmodifiableMap(count)));
			}
		}
		Optional<String> name = Optional.ofNullable(json.has("name") ? json.get("name").getAsString() : null);
		return new Culture(id, where, weight, arrival, hours, List.copyOf(roster), name, captain, List.copyOf(tactics), lair, loot,
			keys(json, "messages"), keys(json, "chronicle"), List.copyOf(extras));
	}

	/** A land lair's {@code near} (32.10: how close to the hall it may stand; {@link Lairs#NEAR} when the file doesn't say). */
	private static int near(JsonObject lair) {
		return lair.has("waterline") ? Lairs.NEAR : integer(lair, "near", Lairs.NEAR, 24, 200);
	}

	/** A captain's {@code raises} (32.9): {@code entity}, {@code radius} (blocks, 24), {@code delay} (ticks, 60) and {@code health} (0.5). */
	private static Optional<Raise> raise(JsonObject captain) {
		if (!captain.has("raises")) {
			return Optional.empty();
		}
		JsonObject r = captain.getAsJsonObject("raises");
		double health = r.has("health") ? r.get("health").getAsDouble() : 0.5;
		if (health <= 0 || health > 1) {
			throw new IllegalArgumentException("raises: health must be over 0 and at most 1, not " + health);
		}
		return Optional.of(new Raise(entity(r), integer(r, "radius", 24, 1, 64), integer(r, "delay", 60, 1, 1200), health));
	}

	private static VillageRanks.Rank rank(String name) {
		try {
			return VillageRanks.Rank.valueOf(name.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("a rank must be hamlet, village, town or city, not " + name);
		}
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
					// An item id, maybe with components as /give takes them (32.8: "minecraft:bow[enchantments={levels:{'minecraft:flame':1}}]").
					int bracket = item.indexOf('[');
					ResourceLocation id = location(bracket < 0 ? item : item.substring(0, bracket), "gear");
					if (!BuiltInRegistries.ITEM.containsKey(id)) {
						throw new IllegalArgumentException("no such item: " + id);
					}
					item = id + (bracket < 0 ? "" : item.substring(bracket));
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
