package io.github.jcondedata.aliveworkplace.build;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Reads Rechiseled's chiseling recipes ({@code data/<ns>/chiseling_recipes/*.json}) when data packs
 * load. Every block in one recipe can be chiseled into any other for free, so they form a family
 * (see {@link MaterialFamilies}); slabs and stairs form their own families. Nothing happens when
 * Rechiseled is not installed.
 */
final class ChiselingFamilies implements SimpleSynchronousResourceReloadListener {
	private static final ResourceLocation ID = AliveWorkplace.id("chiseling_families");

	static void init() {
		ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new ChiselingFamilies());
	}

	@Override
	public ResourceLocation getFabricId() {
		return ID;
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		List<List<Item>> families = new ArrayList<>();
		for (Map.Entry<ResourceLocation, Resource> e : manager.listResources("chiseling_recipes", p -> p.getPath().endsWith(".json")).entrySet()) {
			try (Reader reader = e.getValue().openAsReader()) {
				JsonObject recipe = JsonParser.parseReader(reader).getAsJsonObject();
				if (!recipe.has("entries")) {
					continue;
				}
				List<Item> blocks = new ArrayList<>();
				List<Item> slabs = new ArrayList<>();
				List<Item> stairs = new ArrayList<>();
				for (JsonElement entry : recipe.getAsJsonArray("entries")) {
					JsonObject o = entry.getAsJsonObject();
					add(blocks, o, "block");
					add(blocks, o, "connecting_block");
					add(slabs, o, "slab");
					add(slabs, o, "connecting_slab");
					add(stairs, o, "stairs");
					add(stairs, o, "connecting_stairs");
				}
				for (List<Item> family : List.of(blocks, slabs, stairs)) {
					if (family.size() > 1) {
						families.add(family);
					}
				}
			} catch (Exception ex) {
				AliveWorkplace.LOG.debug("Skipping chiseling recipe {}: {}", e.getKey(), ex.toString());
			}
		}
		MaterialFamilies.setDataFamilies(families);
	}

	private static void add(List<Item> family, JsonObject entry, String key) {
		if (!entry.has(key) || !entry.get(key).isJsonPrimitive()) {
			return;
		}
		ResourceLocation id = ResourceLocation.tryParse(entry.get(key).getAsString());
		if (id == null) {
			return;
		}
		Item item = BuiltInRegistries.ITEM.get(id);
		if (item != Items.AIR && !family.contains(item)) {
			family.add(item);
		}
	}
}
