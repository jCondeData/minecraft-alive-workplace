package io.github.jcondedata.aliveworkplace.craft;

import io.github.jcondedata.aliveworkplace.mc.Recipes;
import io.github.jcondedata.aliveworkplace.work.Extension;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;

/**
 * The furniture a Furnicrafter can make (ROADMAP 38.12): a furniture mod's own recipes, each a list of materials (so
 * many of each item) and the piece it makes. A furniture mod's integration in {@code compat/} fills {@link #SOURCE} from
 * the mod's recipe files (CobbleFurnies' {@code cobblefurnies:furni_crafting}); without one there's no furniture to
 * make and nothing changes. Read once per recipe load (a {@code /reload} reads them again).
 */
public final class FurnitureRecipes {
	/** One furniture recipe: {@code materials} (item and how many) make {@code result}. */
	public record Recipe(ResourceLocation id, Map<Item, Integer> materials, ItemStack result) {
	}

	/** Where the recipes come from: a furniture mod's integration. */
	public interface Source {
		/** Every furniture recipe the server has loaded. */
		List<Recipe> load(MinecraftServer server);
	}

	public static final Extension<Source> SOURCE = new Extension<>("furniture recipes");

	/** The recipes read for a recipe load (the manager is replaced on {@code /reload}), and by what they make. */
	private record Loaded(List<Recipe> all, Map<Item, List<Recipe>> byResult) {
	}

	private static final Map<RecipeManager, Loaded> CACHE = new WeakHashMap<>();

	/** Every furniture recipe there is (none without a furniture mod). */
	public static List<Recipe> all(MinecraftServer server) {
		return loaded(server).all();
	}

	/** The recipes that make {@code item} (none if it isn't a furniture mod's piece). */
	public static List<Recipe> making(MinecraftServer server, Item item) {
		return loaded(server).byResult().getOrDefault(item, List.of());
	}

	/** Whether {@code item} is furniture a Furnicrafter can make. */
	public static boolean makes(MinecraftServer server, Item item) {
		return !making(server, item).isEmpty();
	}

	private static Loaded loaded(MinecraftServer server) {
		RecipeManager manager = Recipes.manager(server);
		synchronized (CACHE) {
			Loaded cached = CACHE.get(manager);
			if (cached != null) {
				return cached;
			}
		}
		List<Recipe> all = SOURCE.call(source -> source.load(server), List.of());
		Map<Item, List<Recipe>> byResult = new LinkedHashMap<>();
		for (Recipe recipe : all) {
			if (!recipe.result().isEmpty() && !recipe.materials().isEmpty()) {
				byResult.computeIfAbsent(recipe.result().getItem(), k -> new ArrayList<>()).add(recipe);
			}
		}
		Loaded loaded = new Loaded(List.copyOf(all), byResult);
		synchronized (CACHE) {
			CACHE.put(manager, loaded);
		}
		return loaded;
	}

	private FurnitureRecipes() {
	}
}
