package io.github.jcondedata.aliveworkplace.craft;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.mc.Recipes;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

/**
 * Plain stand-ins for a mod's special (code) crafting recipes, which list no ingredients and so can't be planned (B1302).
 * Carved Wood (in the owner's pack) turns Minecraft's planks chest into one carved chest per wood and makes the plain
 * chest only by its {@code carved_wood:chest_from_modded_chests} recipe: any one carved wooden chest in the grid makes a
 * plain chest. A player gets a plain chest from 8 oak planks in two crafts; the crafters plan that route through this
 * file's one-slot recipe. B1290: its {@code carved_wood:crafting_special_crafting_table} makes Minecraft's crafting table
 * from any 2x2 of planks no other recipe takes (Chipped's mosaic planks); that one is a {@code "count": 4} stand-in.
 *
 * <p>Read from {@code data/<namespace>/recipe_stand_ins/*.json}:
 * {@code {"serializer": "somemod:special_type", "from": ["#c:some_tag", "somemod:item"], "except": [...], "result": "minecraft:chest"}},
 * with an optional {@code "count"} (default 1): how many of one of them it takes. A thing that a plain recipe already takes
 * {@code count} of (four acacia planks make Carved Wood's acacia crafting table) is left out, as the special recipe only
 * matches what no other recipe does.
 * A stand-in counts only while a recipe of that serializer id is loaded (the mod installed and its config switch on), so
 * it never makes something a player can't. By id only: no class of the other mod is used.
 */
public final class RecipeStandIns {
	private static final ResourceLocation ID = AliveWorkplace.id("recipe_stand_ins");
	public static final String FOLDER = "recipe_stand_ins";

	/** One stand-in: what {@code count} of any one of {@code from} (but not {@code except}) make, while a {@code serializer} recipe is loaded. */
	public record StandIn(ResourceLocation id, ResourceLocation serializer, List<String> from, List<String> except, ResourceLocation result, int count) {
		public StandIn(ResourceLocation id, ResourceLocation serializer, List<String> from, List<String> except, ResourceLocation result) {
			this(id, serializer, from, except, result, 1);
		}
	}

	private static volatile List<StandIn> loaded = List.of();

	private RecipeStandIns() {
	}

	public static void init() {
		Platform.get().onDataReload(ID, new Loader());
	}

	/** Every stand-in read from the data files. */
	public static List<StandIn> all() {
		return loaded;
	}

	/**
	 * The plannable recipes standing in for {@code manager}'s special ones: one per stand-in whose serializer has a recipe
	 * loaded and whose result and ingredients exist (none when the other mod isn't there). Of the things it's made from, only
	 * those a crafting recipe makes are kept when there are any (B1286: Carved Wood's Pale Oak Chest, without pale oak in
	 * the game, was the thing a chest from nothing was short of).
	 */
	static List<RecipeHolder<?>> recipes(RecipeManager manager, net.minecraft.core.HolderLookup.Provider registries) {
		List<StandIn> standIns = loaded;
		if (standIns.isEmpty()) {
			return List.of();
		}
		Set<ResourceLocation> serializers = new LinkedHashSet<>();
		for (RecipeHolder<?> holder : Recipes.all(manager)) {
			ResourceLocation id = Recipes.serializerId(holder.value());
			if (id != null) {
				serializers.add(id);
			}
		}
		Set<Item> crafted = new java.util.HashSet<>();
		for (RecipeHolder<?> holder : Recipes.all(manager, net.minecraft.world.item.crafting.RecipeType.CRAFTING)) {
			if (!holder.value().isSpecial()) {
				crafted.add(Recipes.result(holder.value(), registries).getItem());
			}
		}
		List<RecipeHolder<?>> out = new ArrayList<>();
		for (StandIn standIn : standIns) {
			if (!serializers.contains(standIn.serializer()) || !BuiltInRegistries.ITEM.containsKey(standIn.result())) {
				continue;
			}
			Item result = Lookup.value(BuiltInRegistries.ITEM, standIn.result());
			Set<Item> options = items(standIn.from());
			options.removeAll(items(standIn.except()));
			options.remove(result); // never the thing from itself
			if (standIn.count() > 1) {
				List<List<net.minecraft.world.item.crafting.Ingredient>> plain = plainRecipesOf(manager, standIn.count());
				options.removeIf(item -> takenByAPlainRecipe(plain, item));
			}
			if (standIn.count() > 1) {
				// B1290: any of them that's there is used (Chipped's mosaic planks beside Cobblemon's apricorn planks, which a
				// recipe makes), so none is dropped; the crafted ones come first, for the line to name from nothing
				Set<Item> first = new LinkedHashSet<>(options);
				first.retainAll(crafted);
				first.addAll(options);
				options = first;
			} else if (options.stream().anyMatch(crafted::contains)) {
				options.retainAll(crafted);
			}
			if (!options.isEmpty()) {
				out.add(Recipes.shapeless(standIn.id(), new ItemStack(result), List.copyOf(options), standIn.count()));
			}
		}
		return out;
	}

	/**
	 * Whether a plain (not special) crafting recipe takes exactly {@code count} things, each of which can be {@code item}: then
	 * a grid of {@code count} of it is that recipe's, not the special one's (B1290: four acacia planks make Carved Wood's
	 * acacia crafting table; four of Chipped's mosaic planks, which no plain recipe takes four of, make Minecraft's).
	 */
	private static boolean takenByAPlainRecipe(List<List<net.minecraft.world.item.crafting.Ingredient>> plain, Item item) {
		ItemStack stack = new ItemStack(item);
		for (List<net.minecraft.world.item.crafting.Ingredient> slots : plain) {
			if (slots.stream().allMatch(i -> i.test(stack))) {
				return true;
			}
		}
		return false;
	}

	/** The non-empty ingredients of each plain (not special) crafting recipe that takes exactly {@code count} things. */
	private static List<List<net.minecraft.world.item.crafting.Ingredient>> plainRecipesOf(RecipeManager manager, int count) {
		List<List<net.minecraft.world.item.crafting.Ingredient>> out = new ArrayList<>();
		for (RecipeHolder<?> holder : Recipes.all(manager, net.minecraft.world.item.crafting.RecipeType.CRAFTING)) {
			if (!holder.value().isSpecial()) {
				List<net.minecraft.world.item.crafting.Ingredient> slots = Recipes.ingredients(holder.value()).stream().filter(i -> !i.isEmpty()).toList();
				if (slots.size() == count) {
					out.add(slots);
				}
			}
		}
		return out;
	}

	/** The items {@code entries} name: {@code #tag} for a tag's, else an item id (one not installed is left out). */
	private static Set<Item> items(List<String> entries) {
		Set<Item> out = new LinkedHashSet<>();
		for (String entry : entries) {
			if (entry.startsWith("#")) {
				ResourceLocation tagId = ResourceLocation.tryParse(entry.substring(1));
				if (tagId != null) {
					TagKey<Item> tag = TagKey.create(Registries.ITEM, tagId);
					Lookup.tag(BuiltInRegistries.ITEM, tag).ifPresent(set -> set.forEach(h -> out.add(h.value())));
				}
			} else {
				ResourceLocation id = ResourceLocation.tryParse(entry);
				if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
					out.add(Lookup.value(BuiltInRegistries.ITEM, id));
				}
			}
		}
		return out;
	}

	static StandIn read(ResourceLocation id, JsonElement json) {
		JsonObject object = json.getAsJsonObject();
		List<String> from = entries(object, "from");
		if (from.isEmpty()) {
			throw new IllegalArgumentException("nothing to make it from");
		}
		int count = object.has("count") ? object.get("count").getAsInt() : 1;
		if (count < 1 || count > 9) {
			throw new IllegalArgumentException("count " + count + " is not 1 to 9");
		}
		return new StandIn(id, ResourceLocation.parse(object.get("serializer").getAsString()), from, entries(object, "except"),
			ResourceLocation.parse(object.get("result").getAsString()), count);
	}

	/** {@code key}'s item ids and {@code #tag}s, each checked (B1312: a typo in one threw later, at server start). */
	private static List<String> entries(JsonObject object, String key) {
		List<String> out = strings(object, key);
		for (String entry : out) {
			if (ResourceLocation.tryParse(entry.startsWith("#") ? entry.substring(1) : entry) == null) {
				throw new IllegalArgumentException("'" + entry + "' in " + key + " is not a valid item or tag id");
			}
		}
		return out;
	}

	private static List<String> strings(JsonObject object, String key) {
		List<String> out = new ArrayList<>();
		if (object.has(key)) {
			for (JsonElement e : object.getAsJsonArray(key)) {
				out.add(e.getAsString());
			}
		}
		return out;
	}

	private static final class Loader implements ResourceManagerReloadListener {
		@Override
		public void onResourceManagerReload(ResourceManager manager) {
			List<StandIn> found = new ArrayList<>();
			List<Map.Entry<ResourceLocation, Resource>> files = new ArrayList<>(manager.listResources(FOLDER, p -> p.getPath().endsWith(".json")).entrySet());
			files.sort(Map.Entry.comparingByKey());
			for (Map.Entry<ResourceLocation, Resource> e : files) {
				ResourceLocation file = e.getKey();
				String path = file.getPath().substring(FOLDER.length() + 1, file.getPath().length() - ".json".length());
				try (Reader reader = e.getValue().openAsReader()) {
					found.add(read(ResourceLocation.fromNamespaceAndPath(file.getNamespace(), FOLDER + "/" + path), JsonParser.parseReader(reader)));
				} catch (Exception ex) {
					AliveWorkplace.LOG.warn("Skipping recipe stand-in {}: {}", file, ex.getMessage());
				}
			}
			loaded = List.copyOf(found);
		}
	}
}
