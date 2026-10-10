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

		/** What it asks for, as a few words inside a sentence ("Journeyman", "16 Sweet Berries"): a personal request's {@code %3$s} (31.9). */
		default Component what() {
			return line();
		}
	}

	/**
	 * An objective that moves by being looked at (personal requests, 31.9): the hall's round asks it how far it has got,
	 * for the villager who gave the quest. {@code dawn} is true on the first look of each day, for the ones that are only
	 * judged at dawn. A later objective of this kind (a pet by the door, a schooled child, a revived grave) needs only
	 * this method.
	 */
	public interface Looked extends Objective {
		/** How far it has got now (0 to {@link #need()}); -1 when this look doesn't tell. */
		int look(ServerLevel level, BlockPos hall, Villager giver, Quest quest, boolean dawn);
	}

	/** What a quest is resolved against when it goes up: the village, its census and who posted it (set by objectives). */
	public static final class Context {
		public final ServerLevel level;
		public final BlockPos hall;
		public final VillageHalls.Census census;
		public final RandomSource random;
		public String poster;
		/** The villager who asks (a personal request, 31.9); null for the hall's board and for arcs. */
		@Nullable
		public Villager giver;

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
		register("bring", j -> j.has("by") ? BringBy.read(j) : Bring.read(j));
		register("bring_request", j -> new BringRequest());
		register("kill", Kill::read);
		register("battle", j -> new Battle(GsonHelper.getAsInt(j, "count", 1)));
		register("wait", j -> new Wait(positive(j, "days")));
		register("reach", Reach::read);
		register("talk", Talk::read);
		register("spread_news", SpreadNews::read);
		register("level_up", j -> new LevelUp(GsonHelper.getAsInt(j, "level", 0)));
		register("home", j -> new Home(GsonHelper.getAsInt(j, "tier", 2)));
		register("beat_giver", j -> new BeatGiver(positive(j, "days")));
		register("partner_pokemon", PartnerPokemon::read);
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

	/**
	 * An item id or a {@code #tag}, optionally with what it must carry (31.9): {@code [enchantment=<id>]} (a fishing rod
	 * with Luck of the Sea) or {@code [effect=<id>]} (a potion of Fire Resistance, plain, long or strong); throws when it
	 * names nothing.
	 */
	static String itemOrTag(JsonObject json, String field) {
		String s = GsonHelper.getAsString(json, field, "");
		String base = base(s);
		ResourceLocation id = ResourceLocation.tryParse(base.startsWith("#") ? base.substring(1) : base);
		if (id == null || !base.startsWith("#") && !BuiltInRegistries.ITEM.containsKey(id)) {
			throw new IllegalArgumentException("unknown item '" + s + "'");
		}
		if (!base.equals(s) && carries(s) == null) {
			throw new IllegalArgumentException("'" + s + "': after the item, [enchantment=<id>] or [effect=<id>]");
		}
		return s;
	}

	/** {@code item} without what it must carry. */
	private static String base(String item) {
		int at = item.indexOf('[');
		return at < 0 ? item : item.substring(0, at);
	}

	/** What {@code item} must carry: {@code {"enchantment" or "effect", id}}; null when nothing (or it can't be read). */
	@Nullable
	private static String[] carries(String item) {
		int at = item.indexOf('[');
		if (at < 0 || !item.endsWith("]")) {
			return null;
		}
		String[] parts = item.substring(at + 1, item.length() - 1).split("=", 2);
		if (parts.length != 2 || !parts[0].equals("enchantment") && !parts[0].equals("effect") || ResourceLocation.tryParse(parts[1]) == null) {
			return null;
		}
		return parts;
	}

	/** Whether {@code stack} is the item or in the tag {@code item}, carrying what it asks for. */
	public static boolean matches(String item, ItemStack stack) {
		String base = base(item);
		if (base.startsWith("#")) {
			ResourceLocation tag = ResourceLocation.tryParse(base.substring(1));
			if (tag == null || !stack.is(TagKey.create(Registries.ITEM, tag))) {
				return false;
			}
		} else {
			ResourceLocation id = ResourceLocation.tryParse(base);
			if (id == null || stack.isEmpty() || !BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id)) {
				return false;
			}
		}
		if (base.equals(item)) {
			return true;
		}
		String[] carries = carries(item);
		if (carries == null) {
			return false;
		}
		ResourceLocation what = ResourceLocation.parse(carries[1]);
		if (carries[0].equals("enchantment")) {
			return stack.getEnchantments().keySet().stream().anyMatch(h -> h.is(what));
		}
		net.minecraft.world.item.alchemy.PotionContents potion = stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
		if (potion != null) {
			for (net.minecraft.world.effect.MobEffectInstance effect : potion.getAllEffects()) {
				if (effect.getEffect().is(what)) {
					return true;
				}
			}
		}
		return false;
	}

	/** What {@code item} is called: its name, "Fishing Rod with Luck of the Sea" or "Potion of Fire Resistance". */
	public static Component name(String item) {
		Component base = icon(item).getDescription();
		String[] carries = carries(item);
		if (carries == null) {
			return base;
		}
		ResourceLocation what = ResourceLocation.parse(carries[1]);
		return carries[0].equals("enchantment")
			? Component.translatable("quest.aliveworkplace.item_with", base, Component.translatable(net.minecraft.Util.makeDescriptionId("enchantment", what)))
			: Component.translatable("quest.aliveworkplace.item_of", base, Component.translatable(net.minecraft.Util.makeDescriptionId("effect", what)));
	}

	/** The item that stands for {@code item} (a tag's first member). */
	public static Item icon(String item) {
		item = base(item);
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
			return Component.translatable("quest.aliveworkplace.bring", count, name(item));
		}

		@Override
		public Component what() {
			return Component.translatable("quest.aliveworkplace.count_of", count, name(item));
		}

		/** {@code to: giver} with someone asking (31.9): into the chests by their workstation (the hall's store when they have none). */
		@Override
		public Objective resolve(Context context) {
			if (to.equals("giver") && station.isEmpty() && context.giver != null) {
				Optional<BlockPos> site = context.giver.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE)
					.filter(p -> p.dimension() == context.level.dimension()).map(net.minecraft.core.GlobalPos::pos);
				if (site.isPresent()) {
					return new Bring(item, count, to, site);
				}
			}
			return this;
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

	/**
	 * {@code bring} with {@code "by"} (31.9): what is asked for goes by the giver's {@code villager_type} or {@code job}.
	 * {@code options} maps a villager type or profession id to its item and count; a giver whose key isn't listed isn't
	 * asked for anything (the request isn't theirs to make). It becomes a plain {@link Bring} when the quest opens.
	 */
	public record BringBy(String by, String to, Map<String, Bring> options) implements Objective {
		static BringBy read(JsonObject json) {
			String by = GsonHelper.getAsString(json, "by");
			if (!by.equals("villager_type") && !by.equals("job")) {
				throw new IllegalArgumentException("unknown 'by' '" + by + "'");
			}
			String to = GsonHelper.getAsString(json, "to", "giver");
			Map<String, Bring> options = new LinkedHashMap<>();
			for (Map.Entry<String, com.google.gson.JsonElement> e : GsonHelper.getAsJsonObject(json, "options").entrySet()) {
				if (ResourceLocation.tryParse(e.getKey()) == null) {
					throw new IllegalArgumentException("'" + e.getKey() + "' in 'options' isn't an id");
				}
				JsonObject option = e.getValue().getAsJsonObject().deepCopy();
				option.addProperty("to", to);
				options.put(ResourceLocation.parse(e.getKey()).toString(), Bring.read(option));
			}
			if (options.isEmpty()) {
				throw new IllegalArgumentException("no 'options'");
			}
			return new BringBy(by, to, java.util.Collections.unmodifiableMap(options));
		}

		@Override
		public String type() {
			return "bring";
		}

		@Override
		public int need() {
			return 1;
		}

		@Override
		public Component line() {
			return Component.translatable("quest.aliveworkplace.bring_by");
		}

		@Override
		public JsonObject json() {
			JsonObject o = new JsonObject();
			o.addProperty("type", type());
			o.addProperty("by", by);
			o.addProperty("to", to);
			JsonObject all = new JsonObject();
			options.forEach((key, bring) -> {
				JsonObject option = new JsonObject();
				option.addProperty("item", bring.item());
				option.addProperty("count", bring.count());
				all.add(key, option);
			});
			o.add("options", all);
			return o;
		}

		/** The key of {@code giver}: their villager type or their profession. */
		public String key(Villager giver) {
			return (by.equals("job") ? BuiltInRegistries.VILLAGER_PROFESSION.getKey(giver.getVillagerData().getProfession())
				: BuiltInRegistries.VILLAGER_TYPE.getKey(giver.getVillagerData().getType())).toString();
		}

		@Override
		@Nullable
		public Objective resolve(Context context) {
			Bring option = context.giver == null ? null : options.get(key(context.giver));
			return option == null ? null : option.resolve(context);
		}
	}

	/**
	 * {@code level_up} (31.9): the giver, a worker below Master, reaches their next job level (trading gives XP as in
	 * vanilla, and so does a Bottle o' Enchanting as a gift). {@code level} is the level to reach, set when the quest opens.
	 */
	public record LevelUp(int level) implements Looked {
		@Override
		public String type() {
			return "level_up";
		}

		@Override
		public int need() {
			return 1;
		}

		@Override
		public Component line() {
			return Component.translatable("quest.aliveworkplace.level_up", what());
		}

		@Override
		public Component what() {
			return Component.translatable("merchant.level." + Math.max(1, Math.min(5, level)));
		}

		@Override
		public JsonObject json() {
			JsonObject o = new JsonObject();
			o.addProperty("type", type());
			o.addProperty("level", level);
			return o;
		}

		@Override
		@Nullable
		public Objective resolve(Context context) {
			if (level > 0) {
				return this;
			}
			Villager giver = context.giver;
			if (giver == null) {
				return null;
			}
			net.minecraft.world.entity.npc.VillagerProfession job = giver.getVillagerData().getProfession();
			int now = giver.getVillagerData().getLevel();
			boolean worker = job != net.minecraft.world.entity.npc.VillagerProfession.NONE && job != net.minecraft.world.entity.npc.VillagerProfession.NITWIT;
			return worker && now < 5 ? new LevelUp(now + 1) : null;
		}

		@Override
		public int look(ServerLevel level, BlockPos hall, Villager giver, Quest quest, boolean dawn) {
			return giver.getVillagerData().getLevel() >= this.level ? 1 : 0;
		}
	}

	/**
	 * {@code home} (31.9): the giver sleeps in a bed of their own in a finished home of tier {@code tier} or better
	 * ({@link io.github.jcondedata.aliveworkplace.people.Homes}).
	 */
	public record Home(int tier) implements Looked {
		@Override
		public String type() {
			return "home";
		}

		@Override
		public int need() {
			return 1;
		}

		@Override
		public Component line() {
			return Component.translatable("quest.aliveworkplace.home", Component.translatable("enchantment.level." + Math.max(1, Math.min(10, tier))));
		}

		@Override
		public JsonObject json() {
			JsonObject o = new JsonObject();
			o.addProperty("type", type());
			o.addProperty("tier", tier);
			return o;
		}

		@Override
		@Nullable
		public Objective resolve(Context context) {
			return context.giver == null ? null : this;
		}

		@Override
		public int look(ServerLevel level, BlockPos hall, Villager giver, Quest quest, boolean dawn) {
			return giver.isSleeping() && io.github.jcondedata.aliveworkplace.people.Homes.of(level, giver)
				.map(io.github.jcondedata.aliveworkplace.people.Homes.Home::tier).orElse(0) >= tier ? 1 : 0;
		}
	}

	/**
	 * {@code beat_giver} (31.9, Cobblemon): beat the giver, a Trainer, in battle on {@code days} different days (each win
	 * of a day's first counts, whoever of the helpers wins it). Not asked for without Cobblemon or by anyone but a Trainer.
	 */
	public record BeatGiver(int days) implements Objective {
		@Override
		public String type() {
			return "beat_giver";
		}

		@Override
		public int need() {
			return days;
		}

		@Override
		public Component line() {
			return Component.translatable("quest.aliveworkplace.beat_giver", days);
		}

		@Override
		public Component what() {
			return Component.literal(Integer.toString(days));
		}

		@Override
		public JsonObject json() {
			JsonObject o = new JsonObject();
			o.addProperty("type", type());
			o.addProperty("days", days);
			return o;
		}

		@Override
		@Nullable
		public Objective resolve(Context context) {
			return Trainers.COBBLEMON && context.giver != null && Trainers.isTrainer(context.giver) ? this : null;
		}
	}

	/**
	 * {@code partner_pokemon} (31.9, Cobblemon): at dawn, a Pokémon of a type that helps the giver's job (the
	 * {@link io.github.jcondedata.aliveworkplace.work.Partners} table) is pastured within {@code radius} blocks of their
	 * workstation. {@code types} are set when the quest opens. Not asked for without Cobblemon, by someone whose job no
	 * Pokémon helps with, by someone with no workstation, or by someone who already has such a partner.
	 */
	public record PartnerPokemon(int radius, java.util.List<String> types) implements Looked {
		static PartnerPokemon read(JsonObject json) {
			java.util.List<String> types = new java.util.ArrayList<>();
			if (json.has("types")) {
				json.getAsJsonArray("types").forEach(t -> types.add(t.getAsString()));
			}
			int radius = GsonHelper.getAsInt(json, "radius", 16);
			if (radius < 1) {
				throw new IllegalArgumentException("'radius' below 1");
			}
			return new PartnerPokemon(radius, java.util.List.copyOf(types));
		}

		@Override
		public String type() {
			return "partner_pokemon";
		}

		@Override
		public int need() {
			return 1;
		}

		@Override
		public Component line() {
			return Component.translatable("quest.aliveworkplace.partner_pokemon", what());
		}

		/** "Grass, Ground or Water". */
		@Override
		public Component what() {
			net.minecraft.network.chat.MutableComponent out = Component.empty();
			for (int i = 0; i < types.size(); i++) {
				String t = types.get(i);
				Component name = Component.translatableWithFallback("cobblemon.type." + t, Character.toUpperCase(t.charAt(0)) + t.substring(1));
				if (i == 0) {
					out.append(name);
				} else if (i < types.size() - 1) {
					out.append(Component.literal(", ")).append(name);
				} else {
					out = Component.translatable("quest.aliveworkplace.or", out, name);
				}
			}
			return out;
		}

		@Override
		public JsonObject json() {
			JsonObject o = new JsonObject();
			o.addProperty("type", type());
			o.addProperty("radius", radius);
			com.google.gson.JsonArray array = new com.google.gson.JsonArray();
			types.forEach(array::add);
			o.add("types", array);
			return o;
		}

		/** How many Pokémon of {@code types} are pastured within the radius of {@code giver}'s workstation (0 without Cobblemon or a workstation). */
		public static int partners(ServerLevel level, Villager giver, int radius, java.util.Collection<String> types) {
			BlockPos site = giver.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE)
				.filter(p -> p.dimension() == level.dimension()).map(net.minecraft.core.GlobalPos::pos).orElse(null);
			if (site == null || types.isEmpty()) {
				return 0;
			}
			java.util.Set<String> set = java.util.Set.copyOf(types);
			return io.github.jcondedata.aliveworkplace.work.PokemonPartners.EXTENSION.call(p -> p.helpers(level, site, radius, set, 1).size(), 0);
		}

		@Override
		@Nullable
		public Objective resolve(Context context) {
			Villager giver = context.giver;
			if (giver == null || !io.github.jcondedata.aliveworkplace.work.PokemonPartners.EXTENSION.present()) {
				return null;
			}
			java.util.List<String> helps = new java.util.ArrayList<>(io.github.jcondedata.aliveworkplace.work.Partners.types(giver.getVillagerData().getProfession()));
			java.util.Collections.sort(helps);
			if (helps.isEmpty() || giver.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE).isEmpty()
				|| partners(context.level, giver, radius, helps) > 0) {
				return null;
			}
			return new PartnerPokemon(radius, java.util.List.copyOf(helps));
		}

		@Override
		public int look(ServerLevel level, BlockPos hall, Villager giver, Quest quest, boolean dawn) {
			return !dawn ? -1 : partners(level, giver, radius, types) > 0 ? 1 : 0;
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
