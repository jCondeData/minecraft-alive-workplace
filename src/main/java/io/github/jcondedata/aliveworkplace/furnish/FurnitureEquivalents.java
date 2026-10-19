package io.github.jcondedata.aliveworkplace.furnish;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * Furniture pieces that stand for each other (ROADMAP 37.13): a spruce chair is a spruce chair whichever furniture mod
 * made it, the way {@code MaterialFamilies} makes Chipped's and Rechiseled's variants interchangeable for vanilla
 * blocks. Read from {@code data/<namespace>/furniture_equivalents/*.json}: for each kind of piece, whether its pieces
 * match by wood or by colour, and each mod's block id ({@code {wood}} or {@code {colour}} filled in). Every file adds to
 * the table, so a datapack can add a mod. By block id only: no mod class is used, and a mod that isn't installed simply
 * has no block. Only the mods in {@code furnitureMods} count, in its order, and nothing at all with furnished builds off.
 *
 * <p>Used by the material list (a piece is listed once, "Spruce Chair (any furniture mod)", the mods' pieces on hover, and
 * any of them in the chests counts) and by the Builder, who builds whichever mod's piece the chests hold
 * ({@link FurnitureChoice#restocked}).
 */
public final class FurnitureEquivalents {
	private static final ResourceLocation ID = AliveWorkplace.id("furniture_equivalents");
	public static final String FOLDER = "furniture_equivalents";
	/** Every wood the furniture mods share (M37 design note), used when a file names none. */
	public static final List<String> DEFAULT_WOODS = List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo",
		"crimson", "warped");
	/** The sixteen dye colours, used when a file names none. */
	public static final List<String> DEFAULT_COLOURS = List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
		"light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");

	/** A kind's equivalent pieces: matched by {@code wood} or {@code colour}, each mod's block id template. */
	public record Piece(FurnitureSpot.Kind kind, boolean byColour, Map<String, String> mods) {
	}

	/** One set of equivalent pieces: the kind, its wood or colour, and each mod's block (installed or not). */
	public record Group(FurnitureSpot.Kind kind, String value, Map<String, ResourceLocation> byMod) {
		/** The block of {@code mod} in this group, if that mod has one and it is installed. */
		@Nullable
		public Block block(String mod) {
			ResourceLocation id = byMod.get(mod);
			return id != null && BuiltInRegistries.BLOCK.containsKey(id) ? Lookup.value(BuiltInRegistries.BLOCK, id) : null;
		}

		/** The mods of {@code order} whose piece of this group is installed, in that order. */
		public List<String> installed(List<String> order) {
			List<String> out = new ArrayList<>();
			for (String mod : order) {
				if (block(mod) != null) {
					out.add(mod);
				}
			}
			return out;
		}
	}

	private static volatile Map<FurnitureSpot.Kind, Piece> pieces = Map.of();
	private static volatile List<String> woods = DEFAULT_WOODS;
	private static volatile List<String> colours = DEFAULT_COLOURS;
	/** Every installed block of a group, to its group; worked out on first use after a reload. */
	@Nullable
	private static volatile Map<Block, Group> index;

	private FurnitureEquivalents() {
	}

	public static void init() {
		Platform.get().onDataReload(ID, new Loader());
	}

	/** The table as loaded (for tests). */
	public static Map<FurnitureSpot.Kind, Piece> all() {
		return pieces;
	}

	/** The group a spot's piece belongs to (its kind, and its wood or colour), if the table has its kind. */
	public static Optional<Group> of(FurnitureSpot spot) {
		Piece piece = pieces.get(spot.kind());
		if (piece == null) {
			return Optional.empty();
		}
		String value = piece.byColour() ? (spot.colour() != null ? spot.colour() : "white") : (spot.wood() != null ? spot.wood() : "oak");
		return Optional.of(group(piece, value));
	}

	/** The group {@code block} is one of the pieces of, if any. */
	public static Optional<Group> of(Block block) {
		return Optional.ofNullable(index().get(block));
	}

	/**
	 * The pieces a builder may use in place of {@code item}: the installed pieces of its group whose mods are in
	 * {@code furnitureMods}, in that order (the item itself among them). Just the item when it is no furniture piece, its
	 * mod isn't in the order, or furnished builds are off.
	 */
	public static List<Item> options(Item item) {
		return List.copyOf(byMod(item).values());
	}

	/** {@link #options}, each with the mod (as {@code furnitureMods} names it) whose piece it is. */
	public static Map<String, Item> byMod(Item item) {
		if (!Furnishings.ENABLED || item == Items.AIR || !(item instanceof net.minecraft.world.item.BlockItem blockItem)) {
			return Map.of("", item);
		}
		Group group = index().get(blockItem.getBlock());
		if (group == null) {
			return Map.of("", item);
		}
		Map<String, Item> out = new LinkedHashMap<>();
		for (String mod : group.installed(Furnishings.MODS)) {
			Item option = group.block(mod).asItem();
			if (option != Items.AIR && !out.containsValue(option)) {
				out.put(mod, option);
			}
		}
		return out.containsValue(item) ? Collections.unmodifiableMap(out) : Map.of("", item);
	}

	/** The item that stands for {@code item}'s group in a material list: the first mod's piece in {@code furnitureMods}. */
	public static Item key(Item item) {
		return options(item).get(0);
	}

	/**
	 * {@code item}'s name in a material list: "Spruce Chair (any furniture mod)" with each mod's piece on hover when
	 * several mods have it, otherwise the item's own name.
	 */
	public static Component name(Item item) {
		Map<String, Item> options = byMod(item);
		if (options.size() < 2) {
			return item.getDescription();
		}
		MutableComponent hover = Component.translatable("book.aliveworkplace.material_list.mod_options").withStyle(ChatFormatting.GRAY);
		options.forEach((mod, option) -> hover.append("\n")
			.append(Component.translatable("book.aliveworkplace.material_list.mod_option", option.getDescription(), modName(mod))));
		return Component.translatable("book.aliveworkplace.material_list.any_mod", item.getDescription())
			.withStyle(s -> s.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
	}

	/** A furniture mod's name for players ({@code furniture_mod.aliveworkplace.<mod>}, else its namespace). */
	public static Component modName(String mod) {
		return Component.translatableWithFallback("furniture_mod.aliveworkplace." + mod, mod);
	}

	private static Group group(Piece piece, String value) {
		Map<String, ResourceLocation> byMod = new LinkedHashMap<>();
		piece.mods().forEach((mod, template) -> {
			ResourceLocation id = ResourceLocation.tryParse(template.replace(piece.byColour() ? "{colour}" : "{wood}", value));
			if (id != null) {
				byMod.put(mod, id);
			}
		});
		return new Group(piece.kind(), value, Collections.unmodifiableMap(byMod));
	}

	private static Map<Block, Group> index() {
		Map<Block, Group> m = index;
		if (m == null) {
			m = new HashMap<>();
			for (Piece piece : pieces.values()) {
				for (String value : piece.byColour() ? colours : woods) {
					Group group = group(piece, value);
					for (String mod : group.byMod().keySet()) {
						Block block = group.block(mod);
						if (block != null) {
							m.putIfAbsent(block, group);
						}
					}
				}
			}
			index = m;
		}
		return m;
	}

	/** One file's pieces, added to {@code into}; throws with a message naming the mistake. */
	public static void read(JsonElement json, Map<FurnitureSpot.Kind, Piece> into, Set<String> woodsInto, Set<String> coloursInto) {
		JsonObject root = json.getAsJsonObject();
		if (!root.has("pieces") || !root.get("pieces").isJsonObject()) {
			throw new IllegalArgumentException("no \"pieces\" object");
		}
		if (root.has("woods")) {
			root.getAsJsonArray("woods").forEach(w -> woodsInto.add(w.getAsString().toLowerCase(Locale.ROOT)));
		}
		if (root.has("colours")) {
			root.getAsJsonArray("colours").forEach(c -> coloursInto.add(c.getAsString().toLowerCase(Locale.ROOT)));
		}
		for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("pieces").entrySet()) {
			FurnitureSpot.Kind kind = FurnitureSpot.Kind.of(e.getKey());
			if (kind == null) {
				throw new IllegalArgumentException("unknown kind " + e.getKey());
			}
			JsonObject o = e.getValue().getAsJsonObject();
			String by = o.has("by") ? o.get("by").getAsString() : "wood";
			if (!by.equals("wood") && !by.equals("colour")) {
				throw new IllegalArgumentException(e.getKey() + ": by " + by + " is not wood or colour");
			}
			if (!o.has("mods") || !o.get("mods").isJsonObject()) {
				throw new IllegalArgumentException(e.getKey() + ": no \"mods\" object");
			}
			boolean byColour = by.equals("colour");
			Piece before = into.get(kind);
			if (before != null && before.byColour() != byColour) {
				throw new IllegalArgumentException(e.getKey() + ": matched by " + by + " here and the other way in another file");
			}
			Map<String, String> mods = new LinkedHashMap<>(before == null ? Map.of() : before.mods());
			for (Map.Entry<String, JsonElement> m : o.getAsJsonObject("mods").entrySet()) {
				mods.put(m.getKey().toLowerCase(Locale.ROOT), m.getValue().getAsString());
			}
			into.put(kind, new Piece(kind, byColour, Collections.unmodifiableMap(mods)));
		}
	}

	private static final class Loader implements ResourceManagerReloadListener {
		@Override
		public void onResourceManagerReload(ResourceManager manager) {
			Map<FurnitureSpot.Kind, Piece> found = new EnumMap<>(FurnitureSpot.Kind.class);
			Set<String> foundWoods = new LinkedHashSet<>(DEFAULT_WOODS);
			Set<String> foundColours = new LinkedHashSet<>(DEFAULT_COLOURS);
			// In id order, so which file adds a mod last doesn't depend on the pack's file listing.
			List<Map.Entry<ResourceLocation, Resource>> files = new ArrayList<>(manager.listResources(FOLDER, p -> p.getPath().endsWith(".json")).entrySet());
			files.sort(Map.Entry.comparingByKey());
			for (Map.Entry<ResourceLocation, Resource> e : files) {
				Map<FurnitureSpot.Kind, Piece> attempt = new EnumMap<>(found);
				try (Reader reader = e.getValue().openAsReader()) {
					read(JsonParser.parseReader(reader), attempt, foundWoods, foundColours);
					found = attempt;
				} catch (Exception ex) {
					AliveWorkplace.LOG.warn("Skipping furniture equivalents {}: {}", e.getKey(), ex.getMessage());
				}
			}
			pieces = Collections.unmodifiableMap(found);
			woods = List.copyOf(foundWoods);
			colours = List.copyOf(foundColours);
			index = null;
			AliveWorkplace.LOG.info("Furniture equivalents: {}", pieces.keySet());
		}
	}
}
