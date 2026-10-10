package io.github.jcondedata.aliveworkplace.story;

import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.trainer.Trainers;
import io.github.jcondedata.aliveworkplace.work.Requests;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * The objectives a quest file may ask for (ROADMAP 31.2), by type. Each is a small record read from its JSON and written
 * back the same way, so an open quest saves exactly what it asked when it was posted ({@link Quest}). An unknown type is
 * an error, so a typo never makes a quest free.
 */
public final class Objectives {
	/** One objective: its type, how many it takes, its line ("Bring 16 Bread") and its resolved form for the save. */
	public interface Objective {
		String type();

		int need();

		Component line();

		JsonObject json();

		/** What this objective asks for at posting time; null when it can't be asked for now (the file sits the morning out). */
		@Nullable
		default Objective resolve(Context context) {
			return this;
		}
	}

	/** What a quest is resolved against when it goes up: the village, its census and who posted it (set by objectives). */
	public static final class Context {
		public final ServerLevel level;
		public final BlockPos hall;
		public final VillageHalls.Census census;
		public final RandomSource random;
		public String poster;

		public Context(ServerLevel level, BlockPos hall, VillageHalls.Census census, RandomSource random, String poster) {
			this.level = level;
			this.hall = hall;
			this.census = census;
			this.random = random;
			this.poster = poster;
		}
	}

	private static final Map<String, Function<JsonObject, Objective>> KINDS = new LinkedHashMap<>();

	static {
		register("bring", Bring::read);
		register("bring_request", j -> new BringRequest());
		register("kill", Kill::read);
		register("battle", j -> new Battle(GsonHelper.getAsInt(j, "count", 1)));
		register("wait", j -> new Wait(positive(j, "days")));
		register("reach", Reach::read);
		register("talk", Talk::read);
		register("spread_news", SpreadNews::read);
	}

	public static void register(String type, Function<JsonObject, Objective> reader) {
		KINDS.put(type, reader);
	}

	/** One objective; throws {@link IllegalArgumentException} for an unknown type or a bad field. */
	public static Objective parse(JsonObject json) {
		String type = GsonHelper.getAsString(json, "type", "");
		Function<JsonObject, Objective> reader = KINDS.get(type);
		if (reader == null) {
			throw new IllegalArgumentException("unknown objective type '" + type + "'");
		}
		try {
			return reader.apply(json);
		} catch (RuntimeException e) {
			throw new IllegalArgumentException("objective '" + type + "': " + e.getMessage(), e);
		}
	}

	static int positive(JsonObject json, String field) {
		if (!json.has(field)) {
			throw new IllegalArgumentException("missing '" + field + "'");
		}
		int n = json.get(field).getAsInt();
		if (n < 1) {
			throw new IllegalArgumentException("'" + field + "' below 1");
		}
		return n;
	}

	/** An item id or a {@code #tag}; throws when it names nothing. */
	static String itemOrTag(JsonObject json, String field) {
		String s = GsonHelper.getAsString(json, field, "");
		ResourceLocation id = ResourceLocation.tryParse(s.startsWith("#") ? s.substring(1) : s);
		if (id == null || !s.startsWith("#") && !BuiltInRegistries.ITEM.containsKey(id)) {
			throw new IllegalArgumentException("unknown item '" + s + "'");
		}
		return s;
	}

	/** Whether {@code stack} is the item or in the tag {@code item}. */
	public static boolean matches(String item, ItemStack stack) {
		if (item.startsWith("#")) {
			ResourceLocation tag = ResourceLocation.tryParse(item.substring(1));
			return tag != null && stack.is(TagKey.create(Registries.ITEM, tag));
		}
		ResourceLocation id = ResourceLocation.tryParse(item);
		return id != null && !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id);
	}

	/** The item that stands for {@code item} (a tag's first member). */
	public static Item icon(String item) {
		if (item.startsWith("#")) {
			ResourceLocation tag = ResourceLocation.tryParse(item.substring(1));
			return tag == null ? Items.PAPER : Lookup.tag(BuiltInRegistries.ITEM, TagKey.create(Registries.ITEM, tag))
				.flatMap(set -> set.stream().findFirst()).map(h -> h.value()).orElse(Items.PAPER);
		}
		ResourceLocation id = ResourceLocation.tryParse(item);
		return id == null ? Items.PAPER : Lookup.value(BuiltInRegistries.ITEM, id);
	}

	/** {@code bring}: {@code count} of an item or tag, into the hall's store, the giver's chests or a worker's station. */
	public record Bring(String item, int count, String to, Optional<BlockPos> station) implements Objective {
		static Bring read(JsonObject json) {
			String to = GsonHelper.getAsString(json, "to", "hall");
			if (!to.equals("hall") && !to.equals("giver") && !to.equals("station")) {
				throw new IllegalArgumentException("unknown 'to' '" + to + "'");
			}
			Optional<BlockPos> station = json.has("station") ? Optional.of(BlockPos.of(json.get("station").getAsLong())) : Optional.empty();
			return new Bring(itemOrTag(json, "item"), positive(json, "count"), to, station);
		}

		@Override
		public String type() {
			return "bring";
		}

		@Override
		public int need() {
			return count;
		}

		@Override
		public Component line() {
			return Component.translatable("quest.aliveworkplace.bring", count, icon(item).getDescription());
		}

		@Override
		public JsonObject json() {
			JsonObject o = new JsonObject();
			o.addProperty("type", type());
			o.addProperty("item", item);
			o.addProperty("count", count);
			o.addProperty("to", to);
			station.ifPresent(s -> o.addProperty("station", s.asLong()));
			return o;
		}
	}

	/** {@code bring_request}: what a worker is waiting for today (a plain item), into their station's chests. */
	public record BringRequest() implements Objective {
		@Override
		public String type() {
			return "bring_request";
		}

		@Override
		public int need() {
			return 1;
		}

		@Override
		public Component line() {
			return Component.translatable("quest.aliveworkplace.bring_request");
		}

		@Override
		public JsonObject json() {
			JsonObject o = new JsonObject();
			o.addProperty("type", type());
			return o;
		}

		@Override
		@Nullable
		public Objective resolve(Context context) {
			for (Requests.Request request : context.census.requests()) {
				if (request.item() != null && request.item() != Items.AIR) {
					context.poster = request.worker().getDisplayName().getString();
					return new Bring(BuiltInRegistries.ITEM.getKey(request.item()).toString(), Math.min(64, Math.max(1, request.count())), "station",
						Optional.of(request.station()));
				}
			}
			return null;
		}
	}

	/**
	 * {@code kill}: {@code count} of an entity type, a {@code #tag} or {@code monster}, in the village or anywhere; or
	 * {@code role:<key>}, a mob a story arc spawned (31.4), anywhere, shown by its name ({@code who}).
	 */
	public record Kill(String entity, int count, boolean anywhere, Optional<Component> who) implements Objective {
		public Kill(String entity, int count, boolean anywhere) {
			this(entity, count, anywhere, Optional.empty());
		}

		static Kill read(JsonObject json) {
			String entity = GsonHelper.getAsString(json, "entity", "monster");
			if (entity.startsWith("role:")) {
				if (entity.length() <= "role:".length()) {
					throw new IllegalArgumentException("'role:' names no role");
				}
			} else if (!entity.equals("monster")) {
				ResourceLocation id = ResourceLocation.tryParse(entity.startsWith("#") ? entity.substring(1) : entity);
				if (id == null || !entity.startsWith("#") && !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
					throw new IllegalArgumentException("unknown entity '" + entity + "'");
				}
			}
			String where = GsonHelper.getAsString(json, "where", "village");
			if (!where.equals("village") && !where.equals("anywhere")) {
				throw new IllegalArgumentException("unknown 'where' '" + where + "'");
			}
			Optional<Component> who = json.has("who") ? Optional.of(Rewards.text(json.get("who"))) : Optional.empty();
			return new Kill(entity, positive(json, "count"), where.equals("anywhere") || entity.startsWith("role:"), who);
		}

		/** The arc role it names ({@code role:king}), or null. */
		@Nullable
		public String role() {
			return entity.startsWith("role:") ? entity.substring("role:".length()) : null;
		}

		public boolean matches(Entity killed) {
			if (role() != null) {
				return killed.getTags().contains(ArcEffects.roleTag(role()));
			}
			if (entity.equals("monster")) {
				return killed instanceof Enemy;
			}
			if (entity.startsWith("#")) {
				ResourceLocation tag = ResourceLocation.tryParse(entity.substring(1));
				return tag != null && killed.getType().is(TagKey.create(Registries.ENTITY_TYPE, tag));
			}
			return BuiltInRegistries.ENTITY_TYPE.getKey(killed.getType()).toString().equals(entity);
		}

		@Override
		public String type() {
			return "kill";
		}

		@Override
		public int need() {
			return count;
		}

		@Override
		public Component line() {
			if (role() != null) {
				return Component.translatable("quest.aliveworkplace.defeat", who.orElse(Component.literal(role())));
			}
			if (entity.equals("monster") || entity.startsWith("#")) {
				return Component.translatable("quest.aliveworkplace.slay", count);
			}
			EntityType<?> type = Lookup.value(BuiltInRegistries.ENTITY_TYPE, ResourceLocation.parse(entity));
			return Component.translatable("quest.aliveworkplace.kill", count, type.getDescription());
		}

		@Override
		public JsonObject json() {
			JsonObject o = new JsonObject();
			o.addProperty("type", type());
			o.addProperty("entity", entity);
			o.addProperty("count", count);
			o.addProperty("where", anywhere ? "anywhere" : "village");
			who.ifPresent(w -> o.add("who", Rewards.json(w)));
			return o;
		}
	}

	/**
	 * {@code talk} (31.4): right-click the villager who plays a story arc's role ({@code villager}: the role's key), once.
	 * {@code says}, a lang key, is what they tell you.
	 */
	public record Talk(String role, Optional<Component> who, Optional<String> says) implements Objective {
		static Talk read(JsonObject json) {
			String role = GsonHelper.getAsString(json, "villager", "");
			if (role.isEmpty()) {
				throw new IllegalArgumentException("missing 'villager' (an arc role)");
			}
			Optional<Component> who = json.has("who") ? Optional.of(Rewards.text(json.get("who"))) : Optional.empty();
			return new Talk(role, who, json.has("says") ? Optional.of(GsonHelper.getAsString(json, "says")) : Optional.empty());
		}

		@Override
		public String type() {
			return "talk";
		}

		@Override
		public int need() {
			return 1;
		}

		@Override
		public Component line() {
			return Component.translatable("quest.aliveworkplace.talk", who.orElse(Component.literal(role)));
		}

		@Override
		public JsonObject json() {
			JsonObject o = new JsonObject();
			o.addProperty("type", type());
			o.addProperty("villager", role);
			who.ifPresent(w -> o.add("who", Rewards.json(w)));
			says.ifPresent(k -> o.addProperty("says", k));
			return o;
		}
	}

	/** {@code battle}: beat one of the village's trainers ({@code count} times); not asked for with no trainer there. */
	public record Battle(int count) implements Objective {
		@Override
		public String type() {
			return "battle";
		}

		@Override
		public int need() {
			return Math.max(1, count);
		}

		@Override
		public Component line() {
			return count <= 1 ? Component.translatable("quest.aliveworkplace.battle") : Component.translatable("quest.aliveworkplace.battles", count);
		}

		@Override
		public JsonObject json() {
			JsonObject o = new JsonObject();
			o.addProperty("type", type());
			o.addProperty("count", count);
			return o;
		}

		@Override
		@Nullable
		public Objective resolve(Context context) {
			boolean trainer = Trainers.COBBLEMON && context.census.workers().stream().anyMatch(Trainers::isTrainer);
			return trainer ? this : null;
		}
	}

	/** {@code wait}: some days go by (counted in the hall's round). */
	public record Wait(int days) implements Objective {
		@Override
		public String type() {
			return "wait";
		}

		@Override
		public int need() {
			return days;
		}

		@Override
		public Component line() {
			return Component.translatable("quest.aliveworkplace.wait", days);
		}

		@Override
		public JsonObject json() {
			JsonObject o = new JsonObject();
			o.addProperty("type", type());
			o.addProperty("days", days);
			return o;
		}
	}

	/**
	 * {@code reach} (31.3): be within {@code radius} blocks (across the ground) of a place: a structure, a biome or a point
	 * an arc set ({@link Places}). The place is looked up once, when the quest opens ({@link Stories#locate}).
	 */
	public record Reach(Places.Place place, int radius) implements Objective {
		static Reach read(JsonObject json) {
			int radius = GsonHelper.getAsInt(json, "radius", 32);
			if (radius < 1) {
				throw new IllegalArgumentException("'radius' below 1");
			}
			return new Reach(Places.read(json.get("place")), radius);
		}

		/** Whether {@code pos} is within the radius of the found place. */
		public boolean inside(BlockPos pos) {
			if (place.found().isEmpty()) {
				return false;
			}
			BlockPos at = place.found().get();
			double dx = pos.getX() + 0.5 - (at.getX() + 0.5);
			double dz = pos.getZ() + 0.5 - (at.getZ() + 0.5);
			return dx * dx + dz * dz <= (double) radius * radius;
		}

		@Override
		public String type() {
			return "reach";
		}

		@Override
		public int need() {
			return 1;
		}

		@Override
		public Component line() {
			return Component.translatable("quest.aliveworkplace.reach", Places.name(place));
		}

		@Override
		public JsonObject json() {
			JsonObject o = new JsonObject();
			o.addProperty("type", type());
			o.add("place", place.json());
			o.addProperty("radius", radius);
			return o;
		}
	}

	/**
	 * {@code spread_news} (ROADMAP 34.11): carry this week's Gazette to a village this one has a caravan route to, and hand
	 * it in at that village's hall. Only asked while the village has a Printer (config {@code printers}) and a route out;
	 * the Printer posts it. {@code to} is the other village's hall, chosen when the quest goes up, and {@code village}
	 * its name that day.
	 */
	public record SpreadNews(Optional<BlockPos> to, String village) implements Objective {
		static SpreadNews read(JsonObject json) {
			Optional<BlockPos> to = json.has("to") ? Optional.of(BlockPos.of(json.get("to").getAsLong())) : Optional.empty();
			return new SpreadNews(to, GsonHelper.getAsString(json, "village", ""));
		}

		@Override
		public String type() {
			return "spread_news";
		}

		@Override
		public int need() {
			return 1;
		}

		@Override
		public Component line() {
			return village.isEmpty() ? Component.translatable("quest.aliveworkplace.spread_news.somewhere")
				: Component.translatable("quest.aliveworkplace.spread_news", village);
		}

		@Override
		public JsonObject json() {
			JsonObject o = new JsonObject();
			o.addProperty("type", type());
			to.ifPresent(t -> o.addProperty("to", t.asLong()));
			o.addProperty("village", village);
			return o;
		}

		@Override
		@Nullable
		public Objective resolve(Context context) {
			if (to.isPresent()) {
				return this;
			}
			if (!io.github.jcondedata.aliveworkplace.printer.Printers.ENABLED) {
				return null;
			}
			Villager printer = context.census.workers().stream().filter(io.github.jcondedata.aliveworkplace.printer.Printers::isPrinter).findFirst().orElse(null);
			io.github.jcondedata.aliveworkplace.hall.Caravans.Data caravans = io.github.jcondedata.aliveworkplace.hall.Caravans.Data.get(context.level);
			java.util.List<BlockPos> routes = new java.util.ArrayList<>(caravans.routesFrom(context.hall));
			routes.sort(java.util.Comparator.comparingLong(BlockPos::asLong)); // the same order every time, so a fixed random picks the same village
			if (printer == null || routes.isEmpty()) {
				return null; // no paper to carry, or nowhere to carry it
			}
			BlockPos there = routes.get(context.random.nextInt(routes.size()));
			io.github.jcondedata.aliveworkplace.hall.Caravans.Village known = caravans.village(there);
			context.poster = name(printer);
			return new SpreadNews(Optional.of(there), (known != null ? known.name() : VillageHalls.name(context.level, there)).getString());
		}
	}

	/** A worker's name, as a poster. */
	static String name(Villager villager) {
		return villager.getDisplayName().getString();
	}

	private Objectives() {
	}
}
