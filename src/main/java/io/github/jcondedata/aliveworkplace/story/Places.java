package io.github.jcondedata.aliveworkplace.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import io.github.jcondedata.aliveworkplace.explore.Explorers;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.Nullable;

/**
 * A place a quest sends a player to (ROADMAP 31.3): a structure (id or {@code #tag}), a biome (id or {@code #tag}) or a
 * point an arc set, or a list of those to try in order. It's looked up once, when the quest opens ({@link #locate}, at
 * most {@link #MAX_BLOCKS} away), and the answer is saved with the quest, so nothing searches the world again.
 */
public final class Places {
	/** How far a place is looked for, in blocks. */
	public static final int MAX_BLOCKS = 3000;
	/** How many lookups have run (the tests check a quest looks its place up once). */
	public static final AtomicInteger LOOKUPS = new AtomicInteger();

	/** One way to name a place: {@code kind} is {@code structure}, {@code biome} or {@code point}. */
	public record Option(String kind, String id, Optional<BlockPos> point) {
		JsonObject json() {
			JsonObject o = new JsonObject();
			if (kind.equals("point")) {
				BlockPos p = point.orElseThrow();
				JsonArray a = new JsonArray();
				a.add(p.getX());
				a.add(p.getY());
				a.add(p.getZ());
				o.add("point", a);
			} else {
				o.addProperty(kind, id);
			}
			return o;
		}
	}

	/** The options to try, and once looked up, where it is and which option found it. */
	public record Place(List<Option> options, Optional<BlockPos> found, int option) {
		public boolean located() {
			return found.isPresent();
		}

		@Nullable
		Option which() {
			return option >= 0 && option < options.size() ? options.get(option) : null;
		}

		JsonObject json() {
			JsonObject o = new JsonObject();
			JsonArray opts = new JsonArray();
			options.forEach(op -> opts.add(op.json()));
			o.add("options", opts);
			found.ifPresent(p -> o.addProperty("found", p.asLong()));
			if (found.isPresent()) {
				o.addProperty("option", option);
			}
			return o;
		}
	}

	/** A place from JSON: one option object, a list of them, or (as saved) {@code {"options": [...], "found": ...}}. */
	public static Place read(@Nullable JsonElement json) {
		if (json == null) {
			throw new IllegalArgumentException("missing 'place'");
		}
		if (json.isJsonObject() && json.getAsJsonObject().has("options")) {
			JsonObject o = json.getAsJsonObject();
			List<Option> options = new ArrayList<>();
			for (JsonElement e : o.getAsJsonArray("options")) {
				options.add(option(e));
			}
			Optional<BlockPos> found = o.has("found") ? Optional.of(BlockPos.of(o.get("found").getAsLong())) : Optional.empty();
			return new Place(List.copyOf(options), found, o.has("option") ? o.get("option").getAsInt() : -1);
		}
		List<Option> options = new ArrayList<>();
		if (json.isJsonArray()) {
			for (JsonElement e : json.getAsJsonArray()) {
				options.add(option(e));
			}
		} else {
			options.add(option(json));
		}
		if (options.isEmpty()) {
			throw new IllegalArgumentException("'place' names nothing");
		}
		return new Place(List.copyOf(options), Optional.empty(), -1);
	}

	private static Option option(JsonElement e) {
		JsonObject o = e.getAsJsonObject();
		if (o.has("point")) {
			JsonArray a = o.getAsJsonArray("point");
			if (a.size() != 3) {
				throw new IllegalArgumentException("'point' needs [x, y, z]");
			}
			return new Option("point", "", Optional.of(new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt())));
		}
		for (String kind : List.of("structure", "biome")) {
			if (o.has(kind)) {
				String id = o.get(kind).getAsString();
				if (ResourceLocation.tryParse(id.startsWith("#") ? id.substring(1) : id) == null) {
					throw new IllegalArgumentException("bad " + kind + " '" + id + "'");
				}
				return new Option(kind, id, Optional.empty());
			}
		}
		throw new IllegalArgumentException("a place needs 'structure', 'biome' or 'point'");
	}

	/** A point an arc set, already located. */
	public static Place point(BlockPos pos) {
		return new Place(List.of(new Option("point", "", Optional.of(pos))), Optional.of(pos), 0);
	}

	/**
	 * Looks {@code place} up from {@code from}: the first option found within {@link #MAX_BLOCKS}, on the server thread.
	 * A place already located comes back as it is, without a lookup. Null when no option is found.
	 */
	@Nullable
	public static Place locate(ServerLevel level, BlockPos from, Place place) {
		if (place.located()) {
			return place;
		}
		LOOKUPS.incrementAndGet();
		for (int i = 0; i < place.options().size(); i++) {
			BlockPos found = find(level, from, place.options().get(i));
			if (found != null) {
				return new Place(place.options(), Optional.of(found.immutable()), i);
			}
		}
		return null;
	}

	@Nullable
	private static BlockPos find(ServerLevel level, BlockPos from, Option option) {
		switch (option.kind()) {
			case "point" -> {
				return option.point().orElse(null);
			}
			case "structure" -> {
				if (!level.getServer().getWorldData().worldGenOptions().generateStructures()) {
					return null;
				}
				Registry<Structure> registry = Lookup.registry(level.registryAccess(), Registries.STRUCTURE);
				Optional<? extends HolderSet<Structure>> set;
				if (option.id().startsWith("#")) {
					set = Lookup.tag(registry, TagKey.create(Registries.STRUCTURE, ResourceLocation.parse(option.id().substring(1))));
				} else {
					set = Lookup.holder(registry, ResourceKey.create(Registries.STRUCTURE, ResourceLocation.parse(option.id()))).map(HolderSet::direct);
				}
				if (set.isEmpty() || set.get().size() == 0) {
					return null;
				}
				Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
					.findNearestMapStructure(level, (HolderSet<Structure>) set.get(), from, MAX_BLOCKS / 16, false);
				if (found == null) {
					return null;
				}
				double dx = found.getFirst().getX() - from.getX();
				double dz = found.getFirst().getZ() - from.getZ();
				return dx * dx + dz * dz <= (double) MAX_BLOCKS * MAX_BLOCKS ? found.getFirst() : null;
			}
			case "biome" -> {
				java.util.function.Predicate<Holder<Biome>> test;
				if (option.id().startsWith("#")) {
					TagKey<Biome> tag = TagKey.create(Registries.BIOME, ResourceLocation.parse(option.id().substring(1)));
					test = h -> h.is(tag);
				} else {
					ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, ResourceLocation.parse(option.id()));
					test = h -> h.is(key);
				}
				Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(test, from, MAX_BLOCKS, 32, 64);
				return found == null ? null : found.getFirst();
			}
			default -> {
				return null;
			}
		}
	}

	/** What the place is called: the structure's or biome's name, or "the marked place". */
	public static Component name(Place place) {
		Option o = place.which() != null ? place.which() : place.options().get(0);
		if (o.kind().equals("point")) {
			return Component.translatable("quest.aliveworkplace.place.point");
		}
		boolean tag = o.id().startsWith("#");
		ResourceLocation id = ResourceLocation.parse(tag ? o.id().substring(1) : o.id());
		String prefix = o.kind().equals("structure") ? (tag ? "structure_tag." : "structure.") : (tag ? "biome_tag." : "biome.");
		return Component.translatableWithFallback(prefix + id.getNamespace() + "." + id.getPath().replace('/', '.'), Explorers.pretty(id.getPath()));
	}

	/**
	 * A map to a located place, drawn like an explorer map ({@code Explorers.mapTo}): the land sketched, the place marked
	 * (a structure by vanilla's icon for it, a biome or a point by a red X) and named "Map to ...".
	 */
	public static ItemStack map(ServerLevel level, Place place) {
		BlockPos pos = place.found().orElseThrow();
		ItemStack map = MapItem.create(level, pos.getX(), pos.getZ(), (byte) 2, true, true);
		MapItem.renderBiomePreviewMap(level, map);
		Option o = place.which();
		Holder<net.minecraft.world.level.saveddata.maps.MapDecorationType> marker = MapDecorationTypes.RED_X;
		if (o != null && o.kind().equals("structure") && !o.id().startsWith("#")) {
			Optional<Holder.Reference<Structure>> holder = Lookup.holder(Lookup.registry(level.registryAccess(), Registries.STRUCTURE),
				ResourceKey.create(Registries.STRUCTURE, ResourceLocation.parse(o.id())));
			if (holder.isPresent()) {
				marker = Explorers.marker(holder.get());
			}
		}
		MapItemSavedData.addTargetDecoration(map, pos, "+", marker);
		map.set(DataComponents.ITEM_NAME, Component.translatable("item.aliveworkplace.quest_map", name(place)));
		return map;
	}

	private Places() {
	}
}
