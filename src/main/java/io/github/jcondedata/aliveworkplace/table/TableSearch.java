package io.github.jcondedata.aliveworkplace.table;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * The Blueprint Table's search box (36.10): what the library list shows for what's typed. A build matches when every
 * word typed is found, ignoring case, in its name, its id or the names (and ids) of the jobs it brings: "smith" finds
 * the forges, "inn" the inns, "fisherman" the fisher's hut. A build's levels ({@code inn_2}, {@code inn_3}) and styles
 * stay with it: when any of them matches, the build and all its levels are listed together, the build first and its
 * levels under it, where the first of them comes in the library, so a level is never shown without the build above it. An empty box lists everything; a "???" (26.10) is only listed then, since
 * nothing about it may be found by name.
 */
public final class TableSearch {
	/** Longest search the box takes. */
	public static final int MAX_LENGTH = 48;

	/** The entries to list for {@code query}, in the order given. */
	public static List<TablePayloads.Entry> filter(List<TablePayloads.Entry> entries, String query) {
		List<String> words = words(query);
		if (words.isEmpty()) {
			return List.copyOf(entries);
		}
		Set<ResourceLocation> ids = new HashSet<>();
		for (TablePayloads.Entry e : entries) {
			ids.add(e.id());
		}
		Set<ResourceLocation> found = new HashSet<>();
		for (TablePayloads.Entry e : entries) {
			if (!e.hidden() && matches(e, words)) {
				found.add(family(e.id(), ids));
			}
		}
		// Each build found, where it first comes in the library, with its levels right under it by level (an unlocked
		// build and its locked levels sit apart in the full list, 26.10, but together here).
		Map<ResourceLocation, List<TablePayloads.Entry>> families = new LinkedHashMap<>();
		for (TablePayloads.Entry e : entries) {
			ResourceLocation root = family(e.id(), ids);
			if (!e.hidden() && found.contains(root)) {
				families.computeIfAbsent(root, r -> new ArrayList<>()).add(e);
			}
		}
		List<TablePayloads.Entry> out = new ArrayList<>();
		for (List<TablePayloads.Entry> members : families.values()) {
			members.sort(Comparator.comparingInt(e -> BlueprintUpgrades.tier(BlueprintStyles.base(e.id())))); // stable: styles stay in order
			out.addAll(members);
		}
		return out;
	}

	/** Whether {@code entry} itself matches {@code query} (not counting its levels). */
	public static boolean matches(TablePayloads.Entry entry, String query) {
		List<String> words = words(query);
		return words.isEmpty() || !entry.hidden() && matches(entry, words);
	}

	/**
	 * What stays selected once the list shows {@code shown}: the selected build while it is listed, else the first build
	 * listed (so the pane beside the list always shows one of them), else (nothing matches) the selected build still.
	 */
	@Nullable
	public static ResourceLocation selection(List<TablePayloads.Entry> shown, @Nullable ResourceLocation selected) {
		for (TablePayloads.Entry e : shown) {
			if (e.id().equals(selected)) {
				return selected;
			}
		}
		return shown.isEmpty() ? selected : shown.get(0).id();
	}

	/** The words of a search, lower case. */
	static List<String> words(String query) {
		List<String> out = new ArrayList<>();
		for (String w : query.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
			if (!w.isEmpty()) {
				out.add(w);
			}
		}
		return out;
	}

	private static boolean matches(TablePayloads.Entry entry, List<String> words) {
		String text = text(entry);
		for (String w : words) {
			if (!text.contains(w)) {
				return false;
			}
		}
		return true;
	}

	/** Everything a build is found by, lower case: its name, its id and its jobs' names and ids. */
	static String text(TablePayloads.Entry entry) {
		StringBuilder out = new StringBuilder();
		out.append(Blueprints.displayName(entry.id()).getString()).append('\n').append(entry.id());
		for (ResourceLocation job : entry.jobs()) {
			out.append('\n').append(BuildJobs.name(job).getString()).append('\n').append(job.getPath());
		}
		return out.toString().toLowerCase(Locale.ROOT);
	}

	/** The build {@code id} is a level or style of: its first level that the listing holds. */
	static ResourceLocation family(ResourceLocation id, Set<ResourceLocation> listed) {
		ResourceLocation at = BlueprintStyles.base(id);
		for (int guard = 0; guard < 100; guard++) {
			Optional<ResourceLocation> base = BlueprintUpgrades.baseOf(at);
			if (base.isEmpty() || !listed.contains(base.get())) {
				break;
			}
			at = base.get();
		}
		return at;
	}

	private TableSearch() {
	}
}
