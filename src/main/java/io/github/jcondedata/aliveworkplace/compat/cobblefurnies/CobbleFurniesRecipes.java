package io.github.jcondedata.aliveworkplace.compat.cobblefurnies;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.craft.FurnitureRecipes;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.mc.Recipes;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * CobbleFurnies' Furnicrafter recipes for the Furnicrafter job (ROADMAP 38.12), from its data files only: no CobbleFurnies
 * class. Its recipe type {@code cobblefurnies:furni_crafting} doesn't list its materials as ingredients, so the recipes of
 * that type the server loaded are found by type id and their files read again from the data packs (the top pack's file,
 * so a data pack's change counts): {@code "materials": [{"item": id, "count": n}...]} and {@code "result"}. Checked
 * against CobbleFurnies 1.2 (348 recipes, every material a plain item and count). A material that isn't a known item
 * leaves that recipe out.
 */
public final class CobbleFurniesRecipes implements FurnitureRecipes.Source {
	public static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath("cobblefurnies", "furni_crafting");

	/** Fills {@link FurnitureRecipes#SOURCE} (CobbleFurnies is installed). */
	public static void init() {
		FurnitureRecipes.SOURCE.register("cobblefurnies", new CobbleFurniesRecipes());
	}

	@Override
	public List<FurnitureRecipes.Recipe> load(MinecraftServer server) {
		List<FurnitureRecipes.Recipe> out = new ArrayList<>();
		int skipped = 0;
		for (RecipeHolder<?> holder : Recipes.all(Recipes.manager(server))) {
			if (!TYPE.equals(BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType()))) {
				continue;
			}
			ResourceLocation id = Recipes.id(holder);
			ItemStack result = Recipes.result(holder.value(), server.registryAccess());
			Map<Item, Integer> materials = materials(server, id);
			if (result.isEmpty() || materials == null || materials.isEmpty()) {
				skipped++;
				continue;
			}
			out.add(new FurnitureRecipes.Recipe(id, materials, result.copy()));
		}
		if (skipped > 0) {
			AliveWorkplace.LOG.warn("Alive Workplace: {} CobbleFurnies recipes the Furnicrafter can't read were left out", skipped);
		}
		return out;
	}

	/** The materials in recipe {@code id}'s file, or null if it can't be read or names an unknown item. */
	static Map<Item, Integer> materials(MinecraftServer server, ResourceLocation id) {
		ResourceLocation file = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), Recipes.FOLDER + "/" + id.getPath() + ".json");
		Optional<Resource> resource = server.getResourceManager().getResource(file);
		if (resource.isEmpty()) {
			return null;
		}
		try (Reader reader = resource.get().openAsReader()) {
			JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
			Map<Item, Integer> materials = new LinkedHashMap<>();
			for (JsonElement element : json.getAsJsonArray("materials")) {
				JsonObject material = element.getAsJsonObject();
				if (!material.has("item")) {
					return null; // only items are planned (CobbleFurnies 1.2 names nothing else)
				}
				ResourceLocation itemId = ResourceLocation.tryParse(material.get("item").getAsString());
				Item item = itemId == null ? null : Lookup.value(BuiltInRegistries.ITEM, itemId);
				int count = material.has("count") ? material.get("count").getAsInt() : 1;
				if (item == null || item == Items.AIR || count <= 0) {
					return null;
				}
				materials.merge(item, count, Integer::sum);
			}
			return materials;
		} catch (Exception e) {
			return null;
		}
	}

	private CobbleFurniesRecipes() {
	}
}
