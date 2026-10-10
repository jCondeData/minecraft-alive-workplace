package io.github.jcondedata.aliveworkplace.trade;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.JsonOps;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Guilds;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;

/**
 * Trade goods (ROADMAP 33.2, {@code docs/design/M33.md}): what villages are known for, short of and put a price on.
 * One file is one good, {@code data/<namespace>/trade_goods/<good>.json}: its name and icon, its items (item ids or
 * {@code #tags}; any of them counts toward a bundle), the bundle size, a base price in hundredths of an emerald a bundle,
 * who makes it ({@code made_by}: professions and biomes) and who wants it ({@code wanted_in}: biomes, and the professions
 * that use it), whether it's food, and events that raise demand for a few days. A data pack replaces one by path, adds
 * its own, or switches one off with {@code "enabled": false}; {@code fabric:load_conditions} are honoured, as for guilds.
 */
public final class TradeGoods implements ResourceManagerReloadListener {
	private static final ResourceLocation ID = AliveWorkplace.id("trade_goods");
	public static final String FOLDER = "trade_goods";

	/** A night raid, a bandit raid or a vanilla raid was beaten or lost in the village. */
	public static final ResourceLocation RAID = AliveWorkplace.id("raid");
	/** While any villager in the village is ill (checked at the daily count, not saved as an event). */
	public static final ResourceLocation ILLNESS = AliveWorkplace.id("illness");

	/** Item ids and item tags; or biome ids and biome tags. */
	public record Matcher<T>(List<ResourceLocation> ids, List<TagKey<T>> tags) {
		public boolean isEmpty() {
			return ids.isEmpty() && tags.isEmpty();
		}
	}

	/** An event that raises a good's demand by {@code demand} for {@code days} days. */
	public record Event(ResourceLocation event, int demand, int days) {
	}

	/** One trade good. */
	public record Good(ResourceLocation id, Component name, Item icon, Matcher<Item> items, int bundle, int basePrice,
					   List<ResourceLocation> makers, Matcher<Biome> makingBiomes, List<ResourceLocation> users, Matcher<Biome> wantingBiomes,
					   boolean food, List<Event> events, int order) {
		/** Whether {@code item} is one of this good's items. */
		public boolean matches(Item item) {
			if (item == Items.AIR) {
				return false;
			}
			if (items.ids().contains(BuiltInRegistries.ITEM.getKey(item))) {
				return true;
			}
			if (items.tags().isEmpty()) {
				return false;
			}
			ItemStack stack = new ItemStack(item);
			return items.tags().stream().anyMatch(stack::is);
		}

		public boolean matches(ItemStack stack) {
			return !stack.isEmpty() && matches(stack.getItem());
		}

		/** Whether the good is made in {@code biome} (a good made anywhere, with no biomes, is made in none in particular). */
		public boolean madeIn(Holder<Biome> biome) {
			return in(makingBiomes, biome);
		}

		/** Whether {@code biome} wants the good. */
		public boolean wantedIn(Holder<Biome> biome) {
			return in(wantingBiomes, biome);
		}

		private static boolean in(Matcher<Biome> m, Holder<Biome> biome) {
			return m.ids().stream().anyMatch(biome::is) || m.tags().stream().anyMatch(biome::is);
		}
	}

	private static volatile List<Good> goods = List.of();

	public static void init() {
		Platform.get().onDataReload(ID, new TradeGoods());
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		load(files(manager));
	}

	/** The good files {@code manager} holds (the top data pack's of each), by good id; an unreadable one is skipped with a warning. */
	public static Map<ResourceLocation, JsonElement> files(ResourceManager manager) {
		Map<ResourceLocation, JsonElement> files = new TreeMap<>();
		for (Map.Entry<ResourceLocation, Resource> e : manager.listResources(FOLDER, p -> p.getPath().endsWith(".json")).entrySet()) {
			try (Reader reader = e.getValue().openAsReader()) {
				String path = e.getKey().getPath();
				files.put(ResourceLocation.fromNamespaceAndPath(e.getKey().getNamespace(), path.substring(FOLDER.length() + 1, path.length() - ".json".length())),
					JsonParser.parseReader(reader));
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Skipping trade good {}: {}", e.getKey(), ex.getMessage());
			}
		}
		return files;
	}

	/** Reads every good file; a broken one is skipped with a warning naming it and its field, one switched off is left out. */
	public static void load(Map<ResourceLocation, JsonElement> files) {
		List<Good> out = new ArrayList<>();
		for (Map.Entry<ResourceLocation, JsonElement> e : new TreeMap<>(files).entrySet()) {
			try {
				Good g = read(e.getKey(), e.getValue());
				if (g != null) {
					out.add(g);
				}
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Skipping trade good {}: {}", e.getKey(), ex.getMessage());
			}
		}
		out.sort(Comparator.comparingInt(Good::order).thenComparing(g -> g.id().toString()));
		goods = List.copyOf(out);
	}

	/** Reads one good; null if it's switched off or its load conditions don't hold; throws naming the bad field. */
	@Nullable
	public static Good read(ResourceLocation id, JsonElement json) {
		JsonObject o = GsonHelper.convertToJsonObject(json, "trade good");
		if (!GsonHelper.getAsBoolean(o, "enabled", true) || !Guilds.conditionsMet(o)) {
			return null;
		}
		Component name = o.has("name")
			? ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, o.get("name")).getOrThrow(m -> new JsonSyntaxException("name: " + m))
			: Component.translatable("good." + id.getNamespace() + "." + id.getPath());
		Matcher<Item> items = matcher(o, "items", Registries.ITEM);
		if (items.isEmpty()) {
			throw new JsonSyntaxException("items: expected at least one item or #tag");
		}
		for (ResourceLocation item : items.ids()) {
			if (!BuiltInRegistries.ITEM.containsKey(item)) {
				throw new JsonSyntaxException("items: no such item: " + item);
			}
		}
		Item icon;
		if (o.has("icon")) {
			ResourceLocation iconId = id(GsonHelper.getAsString(o, "icon"), "icon");
			if (!BuiltInRegistries.ITEM.containsKey(iconId)) {
				throw new JsonSyntaxException("icon: no such item: " + iconId);
			}
			icon = Lookup.value(BuiltInRegistries.ITEM, iconId);
		} else if (!items.ids().isEmpty()) {
			icon = Lookup.value(BuiltInRegistries.ITEM, items.ids().get(0));
		} else {
			throw new JsonSyntaxException("icon: required when items are all #tags");
		}
		int bundle = GsonHelper.getAsInt(o, "bundle", 1);
		if (bundle < 1) {
			throw new JsonSyntaxException("bundle: " + bundle + " is below 1");
		}
		int base = GsonHelper.getAsInt(o, "base_price");
		if (base < 1) {
			throw new JsonSyntaxException("base_price: " + base + " is below 1");
		}
		JsonObject madeBy = GsonHelper.getAsJsonObject(o, "made_by", new JsonObject());
		JsonObject wantedIn = GsonHelper.getAsJsonObject(o, "wanted_in", new JsonObject());
		List<Event> events = new ArrayList<>();
		for (JsonElement e : GsonHelper.getAsJsonArray(o, "events", new JsonArray())) {
			JsonObject eo = GsonHelper.convertToJsonObject(e, "events[]");
			int days = GsonHelper.getAsInt(eo, "days", 3);
			if (days < 1) {
				throw new JsonSyntaxException("events: days " + days + " is below 1");
			}
			events.add(new Event(id(GsonHelper.getAsString(eo, "event"), "events"), GsonHelper.getAsInt(eo, "demand", 2), days));
		}
		return new Good(id, name, icon, items, bundle, base, ids(madeBy, "jobs"), matcher(madeBy, "biomes", Registries.BIOME),
			ids(wantedIn, "jobs"), matcher(wantedIn, "biomes", Registries.BIOME), GsonHelper.getAsBoolean(o, "food", false), List.copyOf(events),
			GsonHelper.getAsInt(o, "order", 1000));
	}

	private static <T> Matcher<T> matcher(JsonObject o, String field, net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<T>> registry) {
		List<ResourceLocation> ids = new ArrayList<>();
		List<TagKey<T>> tags = new ArrayList<>();
		for (JsonElement e : GsonHelper.getAsJsonArray(o, field, new JsonArray())) {
			String text = GsonHelper.convertToString(e, field);
			if (text.startsWith("#")) {
				tags.add(TagKey.create(registry, id(text.substring(1), field)));
			} else {
				ids.add(id(text, field));
			}
		}
		return new Matcher<>(List.copyOf(ids), List.copyOf(tags));
	}

	private static List<ResourceLocation> ids(JsonObject o, String field) {
		List<ResourceLocation> out = new ArrayList<>();
		for (JsonElement e : GsonHelper.getAsJsonArray(o, field, new JsonArray())) {
			out.add(id(GsonHelper.convertToString(e, field), field));
		}
		return List.copyOf(out);
	}

	private static ResourceLocation id(String text, String field) {
		ResourceLocation id = ResourceLocation.tryParse(text.indexOf(':') >= 0 ? text : "minecraft:" + text);
		if (id == null) {
			throw new JsonSyntaxException(field + ": not an id: " + text);
		}
		return id;
	}

	/** Every good, in board order (then by id). */
	public static List<Good> all() {
		return goods;
	}

	@Nullable
	public static Good get(ResourceLocation id) {
		for (Good g : goods) {
			if (g.id().equals(id)) {
				return g;
			}
		}
		return null;
	}

	/**
	 * {@code event} happened in the village whose hall is at {@code hall} today: every good with that event gets its demand
	 * for its days, from tomorrow's count on (today's is done or would count it twice). A village not on the caravans'
	 * list yet is skipped. Returns how many goods it raised.
	 */
	public static int event(ServerLevel level, BlockPos hall, ResourceLocation event) {
		return event(level, hall, event, Chronicle.day(level));
	}

	/** {@link #event} on {@code today} (tests count several days). */
	public static int event(ServerLevel level, BlockPos hall, ResourceLocation event, long today) {
		if (!Economy.ENABLED) {
			return 0;
		}
		Caravans.Data data = Caravans.Data.get(level);
		if (data.village(hall) == null) {
			return 0;
		}
		Market market = data.market(hall);
		Map<ResourceLocation, Market.Demand> raised = new LinkedHashMap<>();
		for (Market.Demand d : market.demand()) {
			if (d.until() >= today) {
				raised.put(d.good(), d);
			}
		}
		int count = 0;
		for (Good g : goods) {
			for (Event e : g.events()) {
				if (e.event().equals(event)) {
					// A second raid while the first's demand runs: the stronger demand, until the later end.
					Market.Demand old = raised.get(g.id());
					Market.Demand fresh = new Market.Demand(g.id(), e.demand(), today + 1, today + e.days());
					raised.put(g.id(), old == null ? fresh : new Market.Demand(g.id(), Math.max(old.amount(), fresh.amount()),
						Math.min(old.from(), fresh.from()), Math.max(old.until(), fresh.until())));
					count++;
				}
			}
		}
		if (count > 0) {
			data.setMarket(hall, market.withDemand(List.copyOf(raised.values())));
		}
		return count;
	}

	private TradeGoods() {
	}
}
