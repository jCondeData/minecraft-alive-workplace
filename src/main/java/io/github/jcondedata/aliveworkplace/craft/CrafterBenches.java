package io.github.jcondedata.aliveworkplace.craft;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * The benches a Crafter may work besides the crafting table and the stonecutter (ROADMAP 37.14): a furniture mod's bench
 * and the recipe type it makes things by. Read from {@code data/<namespace>/crafter_benches/*.json}
 * ({@code {"recipe_type": "somemod:furniture_bench", "blocks": ["somemod:furniture_bench"]}}), so a datapack can add a
 * mod's bench; by id only, never the mod's classes, and a mod that isn't installed simply has no such recipes. A bench's
 * recipes are used only while one of its blocks stands within {@link #REACH} blocks of the Crafter's Workbench.
 */
public final class CrafterBenches {
	private static final ResourceLocation ID = AliveWorkplace.id("crafter_benches");
	public static final String FOLDER = "crafter_benches";
	/** How far from the Crafter's Workbench a bench may stand (the workshop's walls). */
	public static final int REACH = 6;

	/** Each bench recipe type's id, to the ids of the blocks that make it. */
	private static volatile Map<ResourceLocation, Set<ResourceLocation>> benches = Map.of();

	private CrafterBenches() {
	}

	public static void init() {
		Platform.get().onDataReload(ID, new Loader());
	}

	/** The benches as loaded (for tests). */
	public static Map<ResourceLocation, Set<ResourceLocation>> all() {
		return benches;
	}

	/** Whether {@code type} is a bench's recipe type (used only by a Crafter whose workshop has the bench). */
	public static boolean isBenchType(RecipeType<?> type) {
		ResourceLocation id = BuiltInRegistries.RECIPE_TYPE.getKey(type);
		return id != null && benches.containsKey(id);
	}

	/**
	 * The recipe types a Crafter at {@code station} may use: every one that's no bench's, and a bench's while one of its
	 * blocks stands near.
	 */
	public static Predicate<RecipeType<?>> usableAt(ServerLevel level, BlockPos station) {
		Map<ResourceLocation, Set<ResourceLocation>> all = benches;
		if (all.isEmpty()) {
			return t -> true;
		}
		Set<ResourceLocation> standing = new LinkedHashSet<>();
		Set<ResourceLocation> wanted = new LinkedHashSet<>();
		all.values().forEach(wanted::addAll);
		for (BlockPos pos : BlockPos.betweenClosed(station.offset(-REACH, -2, -REACH), station.offset(REACH, 2, REACH))) {
			ResourceLocation block = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
			if (wanted.contains(block)) {
				standing.add(block);
			}
		}
		Set<ResourceLocation> open = new LinkedHashSet<>();
		all.forEach((type, blocks) -> {
			if (blocks.stream().anyMatch(standing::contains)) {
				open.add(type);
			}
		});
		return type -> {
			ResourceLocation id = BuiltInRegistries.RECIPE_TYPE.getKey(type);
			return id == null || !all.containsKey(id) || open.contains(id);
		};
	}

	static void read(JsonElement json, Map<ResourceLocation, Set<ResourceLocation>> into) {
		JsonObject object = json.getAsJsonObject();
		ResourceLocation type = ResourceLocation.parse(object.get("recipe_type").getAsString());
		List<ResourceLocation> blocks = new ArrayList<>();
		for (JsonElement block : object.getAsJsonArray("blocks")) {
			blocks.add(ResourceLocation.parse(block.getAsString()));
		}
		if (blocks.isEmpty()) {
			throw new IllegalArgumentException("no blocks");
		}
		into.computeIfAbsent(type, t -> new LinkedHashSet<>()).addAll(blocks);
	}

	private static final class Loader implements ResourceManagerReloadListener {
		@Override
		public void onResourceManagerReload(ResourceManager manager) {
			Map<ResourceLocation, Set<ResourceLocation>> found = new LinkedHashMap<>();
			List<Map.Entry<ResourceLocation, Resource>> files = new ArrayList<>(manager.listResources(FOLDER, p -> p.getPath().endsWith(".json")).entrySet());
			files.sort(Map.Entry.comparingByKey());
			for (Map.Entry<ResourceLocation, Resource> e : files) {
				try (Reader reader = e.getValue().openAsReader()) {
					read(JsonParser.parseReader(reader), found);
				} catch (Exception ex) {
					AliveWorkplace.LOG.warn("Skipping crafter bench {}: {}", e.getKey(), ex.getMessage());
				}
			}
			Map<ResourceLocation, Set<ResourceLocation>> frozen = new LinkedHashMap<>();
			found.forEach((type, blocks) -> frozen.put(type, Set.copyOf(blocks)));
			benches = Collections.unmodifiableMap(frozen);
		}
	}
}
