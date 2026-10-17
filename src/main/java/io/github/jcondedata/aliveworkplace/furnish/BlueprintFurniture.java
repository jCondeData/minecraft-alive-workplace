package io.github.jcondedata.aliveworkplace.furnish;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The furniture spots of every blueprint (ROADMAP 37.2): {@code data/<namespace>/blueprint_furniture/<path>.json} holds
 * the spots of blueprint {@code <namespace>:<path>}, written by {@code tools/blueprints/furniture.py} for our builds; a
 * datapack can add files for its own. A file with a mistake is logged with its path and skipped. Nothing changes in the
 * world from these alone: 37.3 reads them when a build is planned.
 */
public final class BlueprintFurniture implements ResourceManagerReloadListener {
	private static final ResourceLocation ID = AliveWorkplace.id("blueprint_furniture");
	public static final String FOLDER = "blueprint_furniture";
	private static final Pattern WOODS = Pattern.compile("minecraft:(stripped_)?(dark_oak|oak|spruce|birch|jungle|acacia|mangrove|cherry|bamboo|crimson|warped)_(.+)");

	private static Map<ResourceLocation, List<FurnitureSpot>> spots = Map.of();

	public static void init() {
		Platform.get().onDataReload(ID, new BlueprintFurniture());
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		Map<ResourceLocation, List<FurnitureSpot>> found = new HashMap<>();
		for (Map.Entry<ResourceLocation, Resource> e : manager.listResources(FOLDER, p -> p.getPath().endsWith(".json")).entrySet()) {
			ResourceLocation file = e.getKey();
			String path = file.getPath();
			ResourceLocation blueprint = ResourceLocation.fromNamespaceAndPath(file.getNamespace(),
				path.substring(FOLDER.length() + 1, path.length() - ".json".length()));
			try (Reader reader = e.getValue().openAsReader()) {
				found.put(blueprint, read(JsonParser.parseReader(reader)));
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Skipping furniture spots {}: {}", file, ex.getMessage());
			}
		}
		spots = Collections.unmodifiableMap(found);
		AliveWorkplace.LOG.info("Furniture spots: {} blueprints", spots.size());
	}

	/** The spots of a blueprint (empty for one without a file); a styled id gets its base's spots in that style. */
	public static List<FurnitureSpot> of(ResourceLocation blueprint) {
		Optional<BlueprintStyles.Styled> styled = BlueprintStyles.parse(blueprint);
		if (styled.isPresent()) {
			List<FurnitureSpot> base = spots.getOrDefault(styled.get().base(), List.of());
			return BlueprintStyles.get(styled.get().style()).map(style -> styled(base, style)).orElse(base);
		}
		return spots.getOrDefault(blueprint, List.of());
	}

	/** For tests: the spots of every blueprint that has a file. */
	public static Map<ResourceLocation, List<FurnitureSpot>> all() {
		return spots;
	}

	/**
	 * The spots as a style builds them: the blocks swapped as the style swaps the template's, and the wood carried over
	 * (a spruce chair in the Dark Oak style is a dark oak chair): the wood of the style's swap of that wood's planks.
	 */
	public static List<FurnitureSpot> styled(List<FurnitureSpot> base, BlueprintStyles.Style style) {
		List<FurnitureSpot> out = new ArrayList<>(base.size());
		for (FurnitureSpot spot : base) {
			List<FurnitureSpot.Cell> cells = spot.blocks().stream()
				.map(c -> new FurnitureSpot.Cell(c.at(), BlueprintStyles.apply(style, c.state()))).toList();
			out.add(spot.withWood(styledWood(style, spot.wood()), cells));
		}
		return out;
	}

	/** The wood a style turns {@code wood} into (its planks' swap), or the same wood. */
	@Nullable
	static String styledWood(BlueprintStyles.Style style, @Nullable String wood) {
		if (wood == null) {
			return null;
		}
		ResourceLocation planks = ResourceLocation.withDefaultNamespace(wood + "_planks");
		if (!BuiltInRegistries.BLOCK.containsKey(planks)) {
			return wood;
		}
		BlockState swapped = BlueprintStyles.apply(style, Lookup.value(BuiltInRegistries.BLOCK, planks).defaultBlockState());
		Matcher m = WOODS.matcher(BuiltInRegistries.BLOCK.getKey(swapped.getBlock()).toString());
		return m.matches() && m.group(3).equals("planks") ? m.group(2) : wood;
	}

	/** One file's spots; throws with a message naming the mistake. */
	public static List<FurnitureSpot> read(JsonElement json) {
		if (!json.isJsonObject() || !json.getAsJsonObject().has("spots") || !json.getAsJsonObject().get("spots").isJsonArray()) {
			throw new IllegalArgumentException("no \"spots\" list");
		}
		List<FurnitureSpot> out = new ArrayList<>();
		JsonArray list = json.getAsJsonObject().getAsJsonArray("spots");
		for (int i = 0; i < list.size(); i++) {
			try {
				out.add(spot(list.get(i).getAsJsonObject()));
			} catch (RuntimeException ex) {
				throw new IllegalArgumentException("spot " + i + ": " + ex.getMessage(), ex);
			}
		}
		return List.copyOf(out);
	}

	private static FurnitureSpot spot(JsonObject o) {
		String kindName = o.get("kind").getAsString();
		FurnitureSpot.Kind kind = FurnitureSpot.Kind.of(kindName);
		if (kind == null) {
			throw new IllegalArgumentException("unknown kind " + kindName);
		}
		Direction facing = Direction.byName(o.get("facing").getAsString());
		if (facing == null || facing.getAxis().isVertical()) {
			throw new IllegalArgumentException("facing " + o.get("facing") + " is not north, south, east or west");
		}
		List<FurnitureSpot.Cell> cells = new ArrayList<>();
		for (JsonElement e : o.getAsJsonArray("blocks")) {
			JsonObject cell = e.getAsJsonObject();
			cells.add(new FurnitureSpot.Cell(pos(cell.getAsJsonArray("at")), state(cell.get("state").getAsString())));
		}
		if (cells.isEmpty()) {
			throw new IllegalArgumentException("no blocks");
		}
		return new FurnitureSpot(kind, pos(o.getAsJsonArray("pos")), facing, string(o, "wood"), string(o, "colour"), List.copyOf(cells));
	}

	private static BlockPos pos(JsonArray a) {
		if (a == null || a.size() != 3) {
			throw new IllegalArgumentException("a position needs three numbers");
		}
		return new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
	}

	@Nullable
	private static String string(JsonObject o, String key) {
		return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
	}

	private static BlockState state(String text) {
		try {
			return BlockStateParser.parseForBlock(Lookup.lookup(BuiltInRegistries.BLOCK), text, false).blockState();
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException ex) {
			throw new IllegalArgumentException("block " + text + ": " + ex.getMessage(), ex);
		}
	}
}
