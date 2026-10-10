package io.github.jcondedata.aliveworkplace.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Guilds;
import io.github.jcondedata.aliveworkplace.people.Traits;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * What villagers think of a gift (ROADMAP 31.6), read from {@code data/<namespace>/villager_tastes/<id>.json}:
 *
 * <pre>{@code
 * {
 *   "for": {"family": ["aliveworkplace:builder", "minecraft:mason"]},
 *   "loved": ["aliveworkplace:blueprint", "minecraft:spyglass"],
 *   "liked": ["minecraft:glass", "#minecraft:planks"],
 *   "disliked": [],
 *   "hated": []
 * }
 * }</pre>
 *
 * {@code for} says whose tastes the file holds: {@code "everyone"}, {@code {"job": "<profession id>"}} (one job),
 * {@code {"family": ["<profession id>", ...]}} (a family of jobs; {@code minecraft:none} is the jobless) or
 * {@code {"trait": "glutton"}}. Each band lists items or {@code #tags}; ids of mods that aren't installed are kept
 * and simply never match. For a villager and an item, the most specific file that names the item wins: a job's, then
 * a family's, then a trait's, then everyone's; between two files as specific as each other, the kinder band. A data
 * pack replaces a file of ours by shipping one with the same id, or adds its own. An item no file names is
 * {@link Band#NEUTRAL}.
 */
public final class Tastes implements ResourceManagerReloadListener {
	private static final ResourceLocation ID = AliveWorkplace.id("villager_tastes");
	public static final String FOLDER = "villager_tastes";

	/** How much a villager likes a gift, kindest first, with the friendship it's worth. */
	public enum Band {
		LOVED(80),
		LIKED(45),
		NEUTRAL(20),
		DISLIKED(-20),
		HATED(-40);

		public final int points;

		Band(int points) {
			this.points = points;
		}

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** Whose tastes a file holds, most specific first. */
	public enum Scope {
		JOB, FAMILY, TRAIT, EVERYONE
	}

	/** One entry of a band: an item id or a tag. */
	public record Entry(@Nullable ResourceLocation item, @Nullable TagKey<Item> tag) {
		public boolean matches(ItemStack stack) {
			return tag != null ? stack.is(tag) : BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(item);
		}
	}

	/** One taste file: who it's for ({@code jobs} for a job or a family, {@code trait} for a trait) and its bands. */
	public record Taste(ResourceLocation id, Scope scope, Set<ResourceLocation> jobs, @Nullable Traits.Trait trait, Map<Band, List<Entry>> bands) {
		/** Whether this file speaks for {@code villager}. */
		public boolean isFor(Villager villager) {
			return switch (scope) {
				case EVERYONE -> true;
				case TRAIT -> trait != null && Traits.has(villager, trait);
				case JOB, FAMILY -> jobs.contains(BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession()));
			};
		}

		/** The band this file puts {@code stack} in (the first of loved, liked, disliked, hated that names it), or null. */
		@Nullable
		public Band band(ItemStack stack) {
			for (Band band : Band.values()) {
				for (Entry entry : bands.getOrDefault(band, List.of())) {
					if (entry.matches(stack)) {
						return band;
					}
				}
			}
			return null;
		}
	}

	private static volatile Map<ResourceLocation, Taste> tastes = Map.of();

	public static void init() {
		Platform.get().onDataReload(ID, new Tastes());
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		load(files(manager));
	}

	/** The taste files {@code manager} holds (the top data pack's of each), by id; an unreadable one is skipped with a warning. */
	public static Map<ResourceLocation, JsonElement> files(ResourceManager manager) {
		Map<ResourceLocation, JsonElement> files = new TreeMap<>();
		for (Map.Entry<ResourceLocation, Resource> e : manager.listResources(FOLDER, p -> p.getPath().endsWith(".json")).entrySet()) {
			try (Reader reader = e.getValue().openAsReader()) {
				String path = e.getKey().getPath();
				files.put(ResourceLocation.fromNamespaceAndPath(e.getKey().getNamespace(), path.substring(FOLDER.length() + 1, path.length() - ".json".length())),
					JsonParser.parseReader(reader));
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Skipping villager tastes {}: {}", e.getKey(), ex.getMessage());
			}
		}
		return files;
	}

	/** Reads every taste file; a broken one is skipped with a warning naming it, one switched off is left out. */
	public static void load(Map<ResourceLocation, JsonElement> files) {
		Map<ResourceLocation, Taste> out = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, JsonElement> e : new TreeMap<>(files).entrySet()) {
			try {
				Taste taste = read(e.getKey(), e.getValue());
				if (taste != null) {
					out.put(taste.id(), taste);
				}
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Skipping villager tastes {}: {}", e.getKey(), ex.getMessage());
			}
		}
		tastes = Map.copyOf(out);
	}

	/** Reads one taste file; null if it is switched off ({@code "enabled": false}) or its load conditions fail; throws naming the bad field. */
	@Nullable
	public static Taste read(ResourceLocation id, JsonElement json) {
		JsonObject o = GsonHelper.convertToJsonObject(json, "villager tastes");
		if (!GsonHelper.getAsBoolean(o, "enabled", true) || !Guilds.conditionsMet(o)) {
			return null;
		}
		if (!o.has("for")) {
			throw new JsonSyntaxException("for: missing (\"everyone\", or an object with job, family or trait)");
		}
		Scope scope;
		Set<ResourceLocation> jobs = new LinkedHashSet<>();
		Traits.Trait trait = null;
		JsonElement who = o.get("for");
		if (who.isJsonPrimitive()) {
			if (!who.getAsString().equals("everyone")) {
				throw new JsonSyntaxException("for: \"" + who.getAsString() + "\" isn't \"everyone\"");
			}
			scope = Scope.EVERYONE;
		} else {
			JsonObject f = GsonHelper.convertToJsonObject(who, "for");
			if (f.has("job")) {
				scope = Scope.JOB;
				jobs.add(id(GsonHelper.getAsString(f, "job"), "for.job"));
			} else if (f.has("family")) {
				scope = Scope.FAMILY;
				for (JsonElement job : GsonHelper.getAsJsonArray(f, "family")) {
					jobs.add(id(GsonHelper.convertToString(job, "for.family"), "for.family"));
				}
				if (jobs.isEmpty()) {
					throw new JsonSyntaxException("for.family: no jobs");
				}
			} else if (f.has("trait")) {
				scope = Scope.TRAIT;
				String name = GsonHelper.getAsString(f, "trait");
				try {
					trait = Traits.Trait.valueOf(name.toUpperCase(Locale.ROOT));
				} catch (IllegalArgumentException ex) {
					throw new JsonSyntaxException("for.trait: no trait \"" + name + "\"");
				}
			} else {
				throw new JsonSyntaxException("for: needs job, family or trait");
			}
		}
		Map<Band, List<Entry>> bands = new EnumMap<>(Band.class);
		for (Band band : Band.values()) {
			if (band == Band.NEUTRAL || !o.has(band.id())) {
				continue;
			}
			JsonArray list = GsonHelper.getAsJsonArray(o, band.id());
			List<Entry> entries = new ArrayList<>();
			for (JsonElement e : list) {
				String s = GsonHelper.convertToString(e, band.id());
				entries.add(s.startsWith("#")
					? new Entry(null, TagKey.create(Registries.ITEM, id(s.substring(1), band.id())))
					: new Entry(id(s, band.id()), null));
			}
			bands.put(band, List.copyOf(entries));
		}
		return new Taste(id, scope, Set.copyOf(jobs), trait, Map.copyOf(bands));
	}

	private static ResourceLocation id(String text, String field) {
		ResourceLocation id = ResourceLocation.tryParse(text);
		if (id == null) {
			throw new JsonSyntaxException(field + ": \"" + text + "\" isn't an id");
		}
		return id;
	}

	/** Every loaded taste file, by id. */
	public static Map<ResourceLocation, Taste> all() {
		return tastes;
	}

	/**
	 * What {@code villager} thinks of {@code stack}: the band of the most specific file for them that names it (job,
	 * family, trait, everyone; between equals the kinder band), or null when no file names it.
	 */
	@Nullable
	public static Band named(Villager villager, ItemStack stack) {
		Scope bestScope = null;
		Band best = null;
		for (Taste taste : tastes.values()) {
			if (bestScope != null && taste.scope().compareTo(bestScope) > 0 || !taste.isFor(villager)) {
				continue;
			}
			Band band = taste.band(stack);
			if (band == null) {
				continue;
			}
			if (bestScope == null || taste.scope().compareTo(bestScope) < 0 || band.compareTo(best) < 0) {
				bestScope = taste.scope();
				best = band;
			}
		}
		return best;
	}

	/** {@link #named}, with {@link Band#NEUTRAL} for an item no file names. */
	public static Band of(Villager villager, ItemStack stack) {
		Band band = named(villager, stack);
		return band == null ? Band.NEUTRAL : band;
	}

	private Tastes() {
	}
}
