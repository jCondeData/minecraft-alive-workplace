package io.github.jcondedata.aliveworkplace.city;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Road styles (ROADMAP 27.15), one per file in {@code data/aliveworkplace/road_styles/<name>.json}: the surface of a road's
 * middle and of its edges (weighted mixes, {@code {"minecraft:stone_bricks": 3, "minecraft:cracked_stone_bricks": 1}}),
 * the slab and stairs for its steps, its bridge's deck, rail and pillar, and its lamp and lantern post (27.16 reads
 * those). {@code blueprint_styles} lists the building styles ({@code BlueprintStyles} names, "" as drawn) whose roads it
 * paves; a file with {@code requires} loads only with those mods (Apricorn: Cobblemon), as blueprint styles do.
 */
public final class RoadStyles implements ResourceManagerReloadListener {
	private static final ResourceLocation ID = AliveWorkplace.id("road_styles");
	public static final String FOLDER = "road_styles";
	/** The style of roads in no style ("as drawn"), and of any style without a road style of its own. */
	public static final String AS_DRAWN = "as_drawn";

	/** A weighted mix of blocks: each block picked by its weight, the same block every time for one spot. */
	public record Mix(List<Block> blocks, List<Integer> weights) {
		public Mix {
			blocks = List.copyOf(blocks);
			weights = List.copyOf(weights);
		}

		public static Mix of(Block block) {
			return new Mix(List.of(block), List.of(1));
		}

		/** The block for {@code pos}: fixed for the spot, so a road's blueprint comes out the same each time. */
		public Block at(BlockPos pos) {
			int total = weights.stream().mapToInt(Integer::intValue).sum();
			int roll = Math.floorMod((int) (Mth.getSeed(pos.getX(), 0, pos.getZ()) >>> 16), Math.max(1, total));
			for (int i = 0; i < blocks.size(); i++) {
				roll -= weights.get(i);
				if (roll < 0) {
					return blocks.get(i);
				}
			}
			return blocks.get(0);
		}
	}

	/** One road style. */
	public record Style(String name, List<String> blueprintStyles, Mix middle, Mix edge, Block slab, Block stairs,
						Block deck, Block rail, Block pillar, Block lamp, Block lanternPost) {
		/** Every block the style paves with (its surface and steps): a road of it is walked over as road. */
		public Set<Block> surface() {
			Set<Block> out = new HashSet<>(middle.blocks());
			out.addAll(edge.blocks());
			out.add(slab);
			out.add(stairs);
			return out;
		}
	}

	private static Map<String, Style> styles = Map.of();
	private static Set<Block> paving = Set.of();

	public static void init() {
		Platform.get().onDataReload(ID, new RoadStyles());
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		Map<String, Style> out = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, Resource> e : manager.listResources(FOLDER, p -> p.getPath().endsWith(".json")).entrySet()) {
			String path = e.getKey().getPath();
			String name = path.substring(FOLDER.length() + 1, path.length() - ".json".length());
			try (Reader reader = e.getValue().openAsReader()) {
				Style style = read(name, JsonParser.parseReader(reader).getAsJsonObject());
				if (style != null) {
					out.put(name, style);
				}
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Skipping road style {}: {}", e.getKey(), ex.toString());
			}
		}
		styles = Collections.unmodifiableMap(out);
		Set<Block> all = new HashSet<>();
		out.values().forEach(s -> all.addAll(s.surface()));
		paving = Set.copyOf(all);
		AliveWorkplace.LOG.info("Road styles: {}", styles.keySet());
	}

	/** Reads one style file; null if it needs a mod that isn't here. Unknown blocks refuse the file. */
	@Nullable
	static Style read(String name, JsonObject json) {
		if (json.has("requires")) {
			for (JsonElement mod : json.getAsJsonArray("requires")) {
				if (!Platform.get().isModLoaded(mod.getAsString())) {
					return null;
				}
			}
		}
		List<String> forStyles = new ArrayList<>();
		if (json.has("blueprint_styles")) {
			json.getAsJsonArray("blueprint_styles").forEach(s -> forStyles.add(s.getAsString()));
		}
		JsonObject bridge = json.has("bridge") ? json.getAsJsonObject("bridge") : new JsonObject();
		return new Style(name, List.copyOf(forStyles), mix(json, "middle"), mix(json, "edge"), block(json, "slab"), block(json, "stairs"),
			block(bridge, "deck"), block(bridge, "rail"), block(bridge, "pillar"), block(json, "lamp"), block(json, "lantern_post"));
	}

	private static Mix mix(JsonObject json, String field) {
		if (!json.has(field)) {
			throw new IllegalArgumentException("no '" + field + "'");
		}
		JsonElement e = json.get(field);
		if (e.isJsonPrimitive()) {
			return Mix.of(block(e.getAsString(), field));
		}
		List<Block> blocks = new ArrayList<>();
		List<Integer> weights = new ArrayList<>();
		for (Map.Entry<String, JsonElement> w : e.getAsJsonObject().entrySet()) {
			blocks.add(block(w.getKey(), field));
			weights.add(Math.max(1, w.getValue().getAsInt()));
		}
		if (blocks.isEmpty()) {
			throw new IllegalArgumentException("'" + field + "' is empty");
		}
		return new Mix(blocks, weights);
	}

	private static Block block(JsonObject json, String field) {
		if (!json.has(field)) {
			throw new IllegalArgumentException("no '" + field + "'");
		}
		return block(json.get(field).getAsString(), field);
	}

	private static Block block(String id, String field) {
		ResourceLocation key = ResourceLocation.tryParse(id);
		if (key == null || !BuiltInRegistries.BLOCK.containsKey(key)) {
			throw new IllegalArgumentException("unknown block '" + id + "' in '" + field + "'");
		}
		return Lookup.value(BuiltInRegistries.BLOCK, key);
	}

	public static List<Style> all() {
		return List.copyOf(styles.values());
	}

	public static Optional<Style> get(String name) {
		return Optional.ofNullable(styles.get(name));
	}

	/**
	 * The road style for a road drawn in the building style {@code blueprintStyle} ("" as drawn): the one listing it,
	 * else the one named after it, else {@link #AS_DRAWN} (and if even that's gone, plain dirt path).
	 */
	public static Style forBlueprintStyle(String blueprintStyle) {
		for (Style s : styles.values()) {
			if (s.blueprintStyles().contains(blueprintStyle)) {
				return s;
			}
		}
		Style named = styles.get(blueprintStyle.isEmpty() ? AS_DRAWN : blueprintStyle);
		if (named != null) {
			return named;
		}
		return styles.getOrDefault(AS_DRAWN, FALLBACK);
	}

	/** The style's name for players: "Stonework". */
	public static net.minecraft.network.chat.Component title(Style style) {
		return net.minecraft.network.chat.Component.translatable("road_style.aliveworkplace." + style.name());
	}

	/** Whether {@code state} is any style's paving: a road already there, walked over as it is. */
	public static boolean isPaving(BlockState state) {
		return paving.contains(state.getBlock()) || state.is(Blocks.DIRT_PATH);
	}

	private static final Style FALLBACK = new Style(AS_DRAWN, List.of(""), Mix.of(Blocks.DIRT_PATH), Mix.of(Blocks.COARSE_DIRT),
		Blocks.OAK_SLAB, Blocks.OAK_STAIRS, Blocks.OAK_PLANKS, Blocks.OAK_FENCE, Blocks.OAK_LOG, Blocks.LANTERN, Blocks.OAK_FENCE);

	private RoadStyles() {
	}
}
