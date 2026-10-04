package io.github.jcondedata.aliveworkplace.city;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * The kinds of zone a City Plan can have (ROADMAP 27.2), as data: {@code data/<namespace>/city_zones/<kind>.json} holds
 * one kind's colour (a dye, as the village map's banners), map tint, icon item, order and whether anything may be
 * built there. Packs add their own; a broken file is skipped with a warning naming it.
 */
public final class CityZones implements ResourceManagerReloadListener {
	private static final ResourceLocation ID = AliveWorkplace.id("city_zones");
	public static final String FOLDER = "city_zones";

	/** One kind: its id (the file name; a pack's own as {@code namespace:name}), colour, tint, icon, order, buildable. */
	public record Kind(String id, DyeColor color, int mapTint, ResourceLocation icon, int order, boolean buildable) {
		public Component title() {
			return Component.translatable("zone.aliveworkplace." + id.replace(':', '.'));
		}

		public Item iconItem() {
			return BuiltInRegistries.ITEM.getOptional(icon).orElse(Items.PAPER);
		}
	}

	private static Map<String, Kind> kinds = Map.of();

	public static void init() {
		Platform.get().onDataReload(ID, new CityZones());
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		List<Kind> found = new ArrayList<>();
		for (Map.Entry<ResourceLocation, Resource> e : manager.listResources(FOLDER, p -> p.getPath().endsWith(".json")).entrySet()) {
			String path = e.getKey().getPath();
			String name = path.substring(FOLDER.length() + 1, path.length() - ".json".length());
			String id = e.getKey().getNamespace().equals(AliveWorkplace.MOD_ID) ? name : e.getKey().getNamespace() + ":" + name;
			try (Reader reader = e.getValue().openAsReader()) {
				found.add(read(id, JsonParser.parseReader(reader).getAsJsonObject()));
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Skipping city zone kind {}: {}", e.getKey(), ex.toString());
			}
		}
		set(found);
	}

	static void set(List<Kind> found) {
		found.sort(Comparator.comparingInt(Kind::order).thenComparing(Kind::id));
		Map<String, Kind> out = new LinkedHashMap<>();
		found.forEach(k -> out.put(k.id(), k));
		kinds = Collections.unmodifiableMap(out);
	}

	/** Reads one kind; throws on a missing or bad field (the caller skips the file). */
	public static Kind read(String id, JsonObject json) {
		DyeColor color = DyeColor.byName(json.get("color").getAsString(), null);
		if (color == null) {
			throw new IllegalArgumentException("unknown color " + json.get("color"));
		}
		int tint = json.has("map_tint") ? Integer.parseInt(json.get("map_tint").getAsString().replace("#", ""), 16)
			: color.getTextureDiffuseColor() & 0xFFFFFF;
		ResourceLocation icon = ResourceLocation.parse(json.get("icon").getAsString());
		int order = json.has("order") ? json.get("order").getAsInt() : 100;
		boolean buildable = !json.has("buildable") || json.get("buildable").getAsBoolean();
		return new Kind(id, color, tint, icon, order, buildable);
	}

	public static List<Kind> all() {
		return List.copyOf(kinds.values());
	}

	public static Optional<Kind> get(String id) {
		return Optional.ofNullable(kinds.get(id));
	}

	private CityZones() {
	}
}
