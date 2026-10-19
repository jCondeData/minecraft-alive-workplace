package io.github.jcondedata.aliveworkplace.craft;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Another mod's dishes on the village's menus (ROADMAP 38.8, Cobblecuisine): data files
 * {@code data/<ns>/cuisine_dishes/<name>.json}, merged, each a list of dishes by id with the Chef's level that cooks it,
 * its food group (what {@link io.github.jcondedata.aliveworkplace.people.Diet} counts as one kind of food) and whether
 * the Innkeeper serves it ({@code inn}):
 *
 * <pre>{"requires": "cobblecuisine", "dishes": [{"id": "cobblecuisine:mixed_vegetable_salad", "level": 1, "group": "salad"}]}</pre>
 *
 * A file whose {@code requires} mod isn't installed, and a dish whose item doesn't exist (the mod renamed it), are
 * skipped quietly. A dish is cooked from the recipe whose result it is ({@link Crafting.Kind#KITCHEN}), never by its
 * recipe's id. Config {@code cuisineDishes} (behind M38's gate): off, no menu has them and the diet counts them as before.
 */
public final class CuisineDishes implements ResourceManagerReloadListener {
	public static final ResourceLocation ID = AliveWorkplace.id("cuisine_dishes");
	/** Config switch {@code cuisineDishes}. */
	public static boolean ENABLED = true;

	/** One dish: its item, the lowest Chef level that cooks it (1 to 5), its food group, and whether an Innkeeper serves it. */
	public record Dish(ResourceLocation id, Item item, int level, String group, boolean inn) {
	}

	/** A dish as its file lists it, before its item is looked up. */
	public record Entry(ResourceLocation id, int level, String group, boolean inn) {
	}

	private static List<Dish> dishes = List.of();
	private static Map<Item, Dish> byItem = Map.of();
	private static Map<ResourceLocation, Dish> byId = Map.of();
	/** Every id the loaded files list (their mod installed), whether or not the item exists. */
	private static Set<ResourceLocation> listed = Set.of();

	public static void init() {
		Platform.get().onDataReload(ID, new CuisineDishes());
		DishesTab.init(); // 38.8a: the dishes are asked for at the hall's board
	}

	/** The dishes, lowest level first (none while the switch is off). */
	public static List<Dish> dishes() {
		return ENABLED ? dishes : List.of();
	}

	/** The dishes a Chef of {@code level} cooks: those of that level and below. */
	public static List<Item> upTo(int level) {
		List<Item> out = new ArrayList<>();
		for (Dish dish : dishes()) {
			if (dish.level() <= level) {
				out.add(dish.item());
			}
		}
		return out;
	}

	/** The dish {@code item} is, or null (and null for every item while the switch is off). */
	@Nullable
	public static Dish of(Item item) {
		return ENABLED ? byItem.get(item) : null;
	}

	/** The dish with item id {@code id}, or null (and null for every id while the switch is off). */
	@Nullable
	public static Dish byId(ResourceLocation id) {
		return ENABLED ? byId.get(id) : null;
	}

	public static boolean isDish(ItemStack stack) {
		return !stack.isEmpty() && of(stack.getItem()) != null;
	}

	/** The food group of the dish with item id {@code id}, or null if it isn't one of the dishes (or the switch is off). */
	@Nullable
	public static String group(ResourceLocation id) {
		Dish dish = ENABLED ? byId.get(id) : null;
		return dish == null ? null : dish.group();
	}

	/** The drinks an Innkeeper serves. */
	public static List<Item> drinks() {
		List<Item> out = new ArrayList<>();
		for (Dish dish : dishes()) {
			if (dish.inn()) {
				out.add(dish.item());
			}
		}
		return out;
	}

	/** Whether a loaded dish list names {@code id} (its mod installed), switch or not: the Camp Cook's menu leaves it out while off. */
	public static boolean listed(ResourceLocation id) {
		return listed.contains(id);
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		Map<ResourceLocation, Resource> files = new TreeMap<>(manager.listResources("cuisine_dishes", p -> p.getPath().endsWith(".json")));
		Map<ResourceLocation, Entry> entries = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, Resource> e : files.entrySet()) {
			try (Reader reader = e.getValue().openAsReader()) {
				for (Entry entry : parse(JsonParser.parseReader(reader).getAsJsonObject(), Platform.get()::isModLoaded)) {
					entries.putIfAbsent(entry.id(), entry);
				}
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Alive Workplace: skipping cuisine dish list {}: {}", e.getKey(), ex.getMessage());
			}
		}
		set(entries.values());
		AliveWorkplace.LOG.info("Alive Workplace: {} cuisine dishes", dishes.size());
	}

	/** Uses {@code entries} as the dish lists (the reload, and tests): each whose item exists, lowest level first. */
	public static void set(Iterable<Entry> entries) {
		List<Dish> out = new ArrayList<>();
		Set<ResourceLocation> ids = new LinkedHashSet<>();
		for (Entry entry : entries) {
			ids.add(entry.id());
			BuiltInRegistries.ITEM.getOptional(entry.id())
				.ifPresent(item -> out.add(new Dish(entry.id(), item, entry.level(), entry.group(), entry.inn())));
		}
		out.sort(Comparator.comparingInt(Dish::level)); // stable: a level's dishes keep their files' order
		Map<Item, Dish> items = new HashMap<>();
		Map<ResourceLocation, Dish> byIds = new HashMap<>();
		for (Dish dish : out) {
			items.putIfAbsent(dish.item(), dish);
			byIds.putIfAbsent(dish.id(), dish);
		}
		dishes = List.copyOf(out);
		byItem = Map.copyOf(items);
		byId = Map.copyOf(byIds);
		listed = Set.copyOf(ids);
	}

	/**
	 * Reads one dish list: none if a mod it {@code requires} (a mod id, or a list of them) isn't {@code loaded}. Throws
	 * {@link IllegalArgumentException} saying what's wrong with a dish.
	 */
	public static List<Entry> parse(JsonObject json, Predicate<String> loaded) {
		if (json.has("requires")) {
			JsonElement requires = json.get("requires");
			List<String> mods = new ArrayList<>();
			if (requires.isJsonArray()) {
				requires.getAsJsonArray().forEach(m -> mods.add(m.getAsString()));
			} else {
				mods.add(requires.getAsString());
			}
			for (String mod : mods) {
				if (!loaded.test(mod)) {
					return List.of();
				}
			}
		}
		if (!json.has("dishes") || !json.get("dishes").isJsonArray()) {
			throw new IllegalArgumentException("no \"dishes\" list");
		}
		List<Entry> out = new ArrayList<>();
		for (JsonElement element : json.getAsJsonArray("dishes")) {
			JsonObject dish = element.getAsJsonObject();
			if (!dish.has("id")) {
				throw new IllegalArgumentException("a dish with no id");
			}
			ResourceLocation id = ResourceLocation.parse(dish.get("id").getAsString());
			int level = dish.has("level") ? dish.get("level").getAsInt() : 1;
			if (level < 1 || level > 5) {
				throw new IllegalArgumentException(id + ": level must be 1 to 5");
			}
			String group = dish.has("group") ? dish.get("group").getAsString().toLowerCase(Locale.ROOT) : "";
			if (group.isBlank()) {
				throw new IllegalArgumentException(id + ": no group");
			}
			out.add(new Entry(id, level, group, dish.has("inn") && dish.get("inn").getAsBoolean()));
		}
		return out;
	}

	/** Only {@link #init} makes one: the reload listener. */
	private CuisineDishes() {
	}
}
