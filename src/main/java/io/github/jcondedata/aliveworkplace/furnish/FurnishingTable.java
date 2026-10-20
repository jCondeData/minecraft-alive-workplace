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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

/**
 * One furniture mod's pieces (ROADMAP 37.3, design note M37, "The furnishing table format"):
 * {@code data/<namespace>/furnishings/<mod>.json}, the file's name being the mod namespace {@code furnitureMods} names.
 * For each kind of spot, the block ids to try ({@code {wood}} and {@code {colour}} filled from the spot), how the
 * piece's {@code facing} relates to the spot's, other states, and its footprint. Whether the mod is installed is only
 * ever asked of the block registry; no mod class is used. A file with a mistake is logged with its path and skipped.
 */
public record FurnishingTable(String mod, Map<FurnitureSpot.Kind, Piece> pieces) {
	private static final ResourceLocation ID = AliveWorkplace.id("furnishings");
	public static final String FOLDER = "furnishings";
	private static Map<String, FurnishingTable> tables = Map.of();

	/** One cell of a piece: where it is ({@code at}: right, up, forward of the spot's facing) and its own states. */
	public record FootCell(BlockPos at, Map<String, String> states) {
	}

	/** A kind's piece in this mod. */
	public record Piece(List<String> blocks, @Nullable String facing, Map<String, String> states, String wood, String colour, List<FootCell> footprint,
						@Nullable FurnitureSpot.Support needs) {
		/** A piece that stands anywhere its footprint fits. */
		public Piece(List<String> blocks, @Nullable String facing, Map<String, String> states, String wood, String colour, List<FootCell> footprint) {
			this(blocks, facing, states, wood, colour, footprint, null);
		}

		/**
		 * This piece in {@code spot}'s place: the state of every cell of the spot (air where the piece doesn't fill), or
		 * null when no block id resolves in the registry, the footprint doesn't fit inside the spot's cells, the spot
		 * lacks what the piece {@link #needs} (B260: a standing picture frame on a wall would float), or a two-block-tall
		 * piece (a {@code half=lower|upper} block, as a grandfather clock may be) has no cell of the spot over it for its
		 * top half.
		 */
		@Nullable
		public Map<BlockPos, BlockState> resolve(FurnitureSpot spot) {
			if (!spot.supports(needs)) {
				return null;
			}
			String wood = spot.wood() != null ? spot.wood() : this.wood;
			String colour = spot.colour() != null ? spot.colour() : this.colour;
			Set<BlockPos> cells = new HashSet<>();
			spot.blocks().forEach(c -> cells.add(c.at()));
			for (String template : blocks) {
				ResourceLocation id = ResourceLocation.tryParse(fill(template, wood, colour));
				if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
					continue;
				}
				Block block = Lookup.value(BuiltInRegistries.BLOCK, id);
				Map<BlockPos, BlockState> out = new LinkedHashMap<>();
				boolean fits = true;
				for (FootCell cell : footprint) {
					BlockPos rel = rotate(cell.at(), spot.facing());
					if (!cells.contains(rel)) {
						fits = false;
						break;
					}
					BlockState state = block.defaultBlockState();
					Direction to = facing(spot.facing());
					if (to != null) {
						state = set(state, "facing", to.getSerializedName());
					}
					for (Map.Entry<String, String> e : states.entrySet()) {
						state = set(state, e.getKey(), fill(e.getValue(), wood, colour));
					}
					for (Map.Entry<String, String> e : cell.states().entrySet()) {
						state = set(state, e.getKey(), fill(e.getValue(), wood, colour));
					}
					out.put(rel, state);
				}
				if (fits) {
					fits = withTopHalves(out, cells);
				}
				if (!fits) {
					return null; // the next id would have the same footprint
				}
				for (BlockPos cell : cells) {
					out.putIfAbsent(cell, Blocks.AIR.defaultBlockState());
				}
				return out;
			}
			return null;
		}

		/**
		 * Every lower half of a two-block-tall piece in {@code out} gets its upper half over it (B260: a clock placed as
		 * its lower half only had no top, and stood invisible in the hall); false when that cell isn't one of the
		 * spot's, so the piece doesn't fit there.
		 */
		private static boolean withTopHalves(Map<BlockPos, BlockState> out, Set<BlockPos> cells) {
			for (Map.Entry<BlockPos, BlockState> e : List.copyOf(out.entrySet())) {
				BlockState state = e.getValue();
				if (!state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) || state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) != DoubleBlockHalf.LOWER) {
					continue;
				}
				BlockPos top = e.getKey().above();
				if (!cells.contains(top)) {
					return false;
				}
				BlockState upper = state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
				BlockState there = out.get(top);
				if (there != null && !there.is(state.getBlock())) {
					return false; // the footprint puts something else there
				}
				out.put(top, upper);
			}
			return true;
		}

		@Nullable
		private Direction facing(Direction spot) {
			if (facing == null) {
				return null;
			}
			return switch (facing) {
				case "back" -> spot.getOpposite();
				case "left" -> spot.getCounterClockWise();
				case "right" -> spot.getClockWise();
				default -> spot;
			};
		}
	}

	/** {@code at} (x right, y up, z forward of {@code facing}) as an offset in the blueprint. */
	static BlockPos rotate(BlockPos at, Direction facing) {
		Direction right = facing.getClockWise();
		return new BlockPos(right.getStepX() * at.getX() + facing.getStepX() * at.getZ(), at.getY(),
			right.getStepZ() * at.getX() + facing.getStepZ() * at.getZ());
	}

	static String fill(String text, String wood, String colour) {
		return text.replace("{wood}", wood).replace("{colour}", colour);
	}

	/** {@code state} with property {@code name} set to {@code value} where it has it and the value is one of its own. */
	static BlockState set(BlockState state, String name, String value) {
		Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
		return property == null ? state : with(state, property, value);
	}

	private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, String value) {
		Optional<T> parsed = property.getValue(value);
		return parsed.map(v -> state.setValue(property, v)).orElse(state);
	}

	public static void init() {
		Platform.get().onDataReload(ID, new Loader());
	}

	/** The table of a mod named in {@code furnitureMods}, if a file gives one. */
	public static Optional<FurnishingTable> of(String mod) {
		return Optional.ofNullable(tables.get(mod));
	}

	/** For tests. */
	public static Map<String, FurnishingTable> all() {
		return tables;
	}

	/** One file's table; throws with a message naming the mistake. */
	public static FurnishingTable read(String mod, JsonElement json) {
		JsonObject root = json.getAsJsonObject();
		if (!root.has("pieces") || !root.get("pieces").isJsonObject()) {
			throw new IllegalArgumentException("no \"pieces\" object");
		}
		Map<FurnitureSpot.Kind, Piece> pieces = new EnumMap<>(FurnitureSpot.Kind.class);
		for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("pieces").entrySet()) {
			FurnitureSpot.Kind kind = FurnitureSpot.Kind.of(e.getKey());
			if (kind == null) {
				throw new IllegalArgumentException("unknown kind " + e.getKey());
			}
			JsonObject o = e.getValue().getAsJsonObject();
			List<String> blocks = new ArrayList<>();
			if (o.get("block").isJsonArray()) {
				o.getAsJsonArray("block").forEach(b -> blocks.add(b.getAsString()));
			} else {
				blocks.add(o.get("block").getAsString());
			}
			String facing = o.has("facing") ? o.get("facing").getAsString() : null;
			if (facing != null && !List.of("front", "back", "left", "right").contains(facing)) {
				throw new IllegalArgumentException(e.getKey() + ": facing " + facing + " is not front, back, left or right");
			}
			List<FootCell> footprint = new ArrayList<>();
			if (o.has("footprint")) {
				for (JsonElement c : o.getAsJsonArray("footprint")) {
					JsonObject cell = c.getAsJsonObject();
					var at = cell.getAsJsonArray("at");
					footprint.add(new FootCell(new BlockPos(at.get(0).getAsInt(), at.get(1).getAsInt(), at.get(2).getAsInt()), strings(cell, "states")));
				}
			} else {
				footprint.add(new FootCell(BlockPos.ZERO, Map.of()));
			}
			FurnitureSpot.Support needs = null;
			if (o.has("needs")) { // B260: what the piece must stand on or against (floor, wall, ceiling)
				needs = FurnitureSpot.Support.of(o.get("needs").getAsString());
				if (needs == null) {
					throw new IllegalArgumentException(e.getKey() + ": needs " + o.get("needs").getAsString() + " is not floor, wall or ceiling");
				}
			}
			pieces.put(kind, new Piece(List.copyOf(blocks), facing, strings(o, "states"),
				o.has("wood") ? o.get("wood").getAsString() : "oak", o.has("colour") ? o.get("colour").getAsString() : "white", List.copyOf(footprint), needs));
		}
		return new FurnishingTable(mod, Collections.unmodifiableMap(pieces));
	}

	private static Map<String, String> strings(JsonObject o, String key) {
		if (!o.has(key)) {
			return Map.of();
		}
		Map<String, String> out = new LinkedHashMap<>();
		o.getAsJsonObject(key).entrySet().forEach(e -> out.put(e.getKey(), e.getValue().getAsString()));
		return Collections.unmodifiableMap(out);
	}

	private static final class Loader implements ResourceManagerReloadListener {
		@Override
		public void onResourceManagerReload(ResourceManager manager) {
			Map<String, FurnishingTable> found = new HashMap<>();
			for (Map.Entry<ResourceLocation, Resource> e : manager.listResources(FOLDER, p -> p.getPath().endsWith(".json")).entrySet()) {
				String path = e.getKey().getPath();
				String mod = path.substring(FOLDER.length() + 1, path.length() - ".json".length());
				try (Reader reader = e.getValue().openAsReader()) {
					found.put(mod, read(mod, JsonParser.parseReader(reader)));
				} catch (Exception ex) {
					AliveWorkplace.LOG.warn("Skipping furnishing table {}: {}", e.getKey(), ex.getMessage());
				}
			}
			tables = Collections.unmodifiableMap(found);
			AliveWorkplace.LOG.info("Furnishing tables: {}", tables.keySet());
		}
	}
}
