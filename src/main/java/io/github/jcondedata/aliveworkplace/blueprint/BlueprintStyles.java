package io.github.jcondedata.aliveworkplace.blueprint;

import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

/**
 * Styles: any blueprint built in another palette — the same house in stonework, sandstone, dark oak and deepslate,
 * cherry wood, or (with Cobblemon) apricorn wood. A style is a list of block swaps read from data packs
 * ({@code data/<ns>/blueprint_styles/<name>.json}: {@code "replace": [{"from": regex, "to": replacement}]} on block ids,
 * the first rule whose result is a real block wins, anything no rule turns into a real block stays as it is), so
 * packs can add their own.
 *
 * <p>A styled blueprint has an id of its own, {@code aliveworkplace:styled/<style>/<namespace>/<path>}, which
 * {@link BlueprintLibrary} resolves to the base blueprint with the swaps applied. Everything that goes by blueprint id —
 * build sites, previews, material lists, upgrades ({@code <name>_2} of a styled id is the styled upgrade) — works for
 * styled blueprints unchanged.
 */
public final class BlueprintStyles implements ResourceManagerReloadListener {
	private static final ResourceLocation ID = AliveWorkplace.id("blueprint_styles");
	public static final String FOLDER = "blueprint_styles";
	private static final String PREFIX = "styled/";

	/** One swap: block ids matching {@code from} become {@code to} ($1, $2... are the groups). */
	public record Rule(Pattern from, String to) {
		@Nullable
		ResourceLocation apply(ResourceLocation id) {
			Matcher m = from.matcher(id.toString());
			if (!m.matches()) {
				return null;
			}
			StringBuilder out = new StringBuilder();
			for (int i = 0; i < to.length(); i++) {
				char c = to.charAt(i);
				if (c == '$' && i + 1 < to.length() && Character.isDigit(to.charAt(i + 1))) {
					int group = to.charAt(++i) - '0';
					String value = group <= m.groupCount() ? m.group(group) : null;
					out.append(value == null ? "" : value);
				} else {
					out.append(c);
				}
			}
			return ResourceLocation.tryParse(out.toString());
		}
	}

	/** A style: its name (the file name), its place in the list, its icon and swaps. */
	public record Style(String name, int order, ResourceLocation icon, List<Rule> rules) {
		public Component title() {
			return Component.translatableWithFallback("style.aliveworkplace." + name, prettify(name));
		}

		public Item iconItem() {
			return BuiltInRegistries.ITEM.getOptional(icon).orElse(Items.PAPER);
		}
	}

	/** A styled blueprint id taken apart. */
	public record Styled(String style, ResourceLocation base) {
	}

	private static Map<String, Style> styles = Map.of();
	private static final Map<Blueprint, Map<String, Blueprint>> CACHE = Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<String, Map<Block, Block>> SWAPS = new HashMap<>();

	public static void init() {
		Platform.get().onDataReload(ID, new BlueprintStyles());
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		List<Style> found = new ArrayList<>();
		for (Map.Entry<ResourceLocation, Resource> e : manager.listResources(FOLDER, p -> p.getPath().endsWith(".json")).entrySet()) {
			String path = e.getKey().getPath();
			String name = path.substring(FOLDER.length() + 1, path.length() - ".json".length());
			try (Reader reader = e.getValue().openAsReader()) {
				Style style = read(name, JsonParser.parseReader(reader).getAsJsonObject());
				if (style != null) {
					found.add(style);
				}
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Skipping blueprint style {}: {}", e.getKey(), ex.toString());
			}
		}
		found.sort(Comparator.comparingInt(Style::order).thenComparing(Style::name));
		Map<String, Style> out = new LinkedHashMap<>();
		found.forEach(s -> out.put(s.name(), s));
		styles = Collections.unmodifiableMap(out);
		CACHE.clear();
		synchronized (SWAPS) {
			SWAPS.clear();
		}
		AliveWorkplace.LOG.info("Blueprint styles: {}", styles.keySet());
	}

	/** Reads one style file; null if it needs a mod that isn't here. */
	@Nullable
	static Style read(String name, JsonObject json) {
		if (json.has("requires")) {
			for (JsonElement mod : json.getAsJsonArray("requires")) {
				if (!Platform.get().isModLoaded(mod.getAsString())) {
					return null;
				}
			}
		}
		List<Rule> rules = new ArrayList<>();
		for (JsonElement r : json.getAsJsonArray("replace")) {
			JsonObject rule = r.getAsJsonObject();
			rules.add(new Rule(Pattern.compile(rule.get("from").getAsString()), rule.get("to").getAsString()));
		}
		int order = json.has("order") ? json.get("order").getAsInt() : 100;
		ResourceLocation icon = json.has("icon") ? ResourceLocation.parse(json.get("icon").getAsString()) : ResourceLocation.withDefaultNamespace("paper");
		return new Style(name, order, icon, List.copyOf(rules));
	}

	/** The styles there are (those needing a mod that isn't installed left out), in order. */
	public static List<Style> all() {
		return List.copyOf(styles.values());
	}

	public static Optional<Style> get(String name) {
		return Optional.ofNullable(styles.get(name));
	}

	/** The id of {@code base} built in {@code style} ({@code base} itself for no style). */
	public static ResourceLocation styled(ResourceLocation base, @Nullable String style) {
		ResourceLocation plain = base(base);
		if (style == null || style.isEmpty()) {
			return plain;
		}
		return AliveWorkplace.id(PREFIX + style + "/" + plain.getNamespace() + "/" + plain.getPath());
	}

	/** A styled id's style and base blueprint (empty for an id that isn't styled). */
	public static Optional<Styled> parse(ResourceLocation id) {
		if (!id.getNamespace().equals(AliveWorkplace.MOD_ID) || !id.getPath().startsWith(PREFIX)) {
			return Optional.empty();
		}
		String[] parts = id.getPath().substring(PREFIX.length()).split("/", 3);
		if (parts.length < 3 || parts[0].isEmpty() || parts[1].isEmpty() || parts[2].isEmpty()) {
			return Optional.empty();
		}
		ResourceLocation base = ResourceLocation.tryBuild(parts[1], parts[2]);
		return base == null ? Optional.empty() : Optional.of(new Styled(parts[0], base));
	}

	/** The unstyled blueprint of {@code id} ({@code id} itself if it isn't styled). */
	public static ResourceLocation base(ResourceLocation id) {
		return parse(id).map(Styled::base).orElse(id);
	}

	/** The style of {@code id}, or empty for the blueprint as drawn. */
	public static Optional<String> styleOf(ResourceLocation id) {
		return parse(id).map(Styled::style);
	}

	/** {@code base} in {@code style}, under {@code id}. */
	public static Blueprint apply(Style style, Blueprint base, ResourceLocation id) {
		return CACHE.computeIfAbsent(base, b -> Collections.synchronizedMap(new HashMap<>())).computeIfAbsent(style.name(), s -> {
			List<Blueprint.Entry> blocks = new ArrayList<>(base.blocks().size());
			for (Blueprint.Entry e : base.blocks()) {
				blocks.add(new Blueprint.Entry(e.pos(), apply(style, e.state()), e.nbt()));
			}
			return new Blueprint(id, base.size(), List.copyOf(blocks), base.entities());
		});
	}

	/** {@code state} in {@code style}: the swapped block with as many of the same properties as it has. */
	public static BlockState apply(Style style, BlockState state) {
		Block to;
		synchronized (SWAPS) {
			to = SWAPS.computeIfAbsent(style.name(), s -> new HashMap<>()).computeIfAbsent(state.getBlock(), b -> swap(style, b));
		}
		if (to == state.getBlock()) {
			return state;
		}
		BlockState out = to.defaultBlockState();
		for (Property<?> property : state.getProperties()) {
			out = copy(state, out, property);
		}
		return out;
	}

	private static Block swap(Style style, Block block) {
		ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
		for (Rule rule : style.rules()) {
			ResourceLocation target = rule.apply(id);
			if (target != null && BuiltInRegistries.BLOCK.containsKey(target)) {
				return Lookup.value(BuiltInRegistries.BLOCK, target);
			}
		}
		return block;
	}

	private static <T extends Comparable<T>> BlockState copy(BlockState from, BlockState to, Property<T> property) {
		for (Property<?> p : to.getProperties()) {
			if (p.getName().equals(property.getName())) {
				Optional<?> value = p.getValue(property.getName(from.getValue(property)));
				if (value.isPresent()) {
					return with(to, p, value.get());
				}
			}
		}
		return to;
	}

	@SuppressWarnings("unchecked")
	private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, Object value) {
		return state.setValue(property, (T) value);
	}

	static String prettify(String name) {
		StringBuilder out = new StringBuilder();
		for (String word : name.split("[_\\-]+")) {
			if (!word.isEmpty()) {
				out.append(out.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
			}
		}
		return out.toString();
	}

	private BlueprintStyles() {
	}
}
