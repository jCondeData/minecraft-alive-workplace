package io.github.jcondedata.aliveworkplace.people;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.GsonHelper;

/**
 * A home's grade (ROADMAP 34.15): its blueprint's tier ({@code <name>_2} is II, {@link BlueprintUpgrades}), unless a data
 * file gives it more. {@code data/<ns>/homes/<name>.json} holds {@code {"blueprint": "aliveworkplace:townhouse",
 * "grade": 2}}: that blueprint (in any style) is that grade, and each of its upgrades one more, up to {@link #MAX}. A
 * file never lowers a grade, and a blueprint's own tier above III (an imported {@code manor_4}) stays what it is. A
 * broken file is skipped with a warning naming it.
 */
public final class HomeGrades implements ResourceManagerReloadListener {
	private static final ResourceLocation ID = AliveWorkplace.id("home_grades");
	public static final String FOLDER = "homes";
	/** The highest grade a data file can give (the classes ask for I to III). */
	public static final int MAX = 3;

	/** The grade each family's base blueprint is given ({@link ClassNeeds#root}: no style, no tier). */
	private static volatile Map<ResourceLocation, Integer> grades = Map.of();

	public static void init() {
		Platform.get().onDataReload(ID, new HomeGrades());
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		reload(manager);
	}

	/** Reads the home grade files {@code manager} holds (the top data pack's of each). */
	public static void reload(ResourceManager manager) {
		Map<ResourceLocation, JsonElement> files = new TreeMap<>();
		for (Map.Entry<ResourceLocation, Resource> e : manager.listResources(FOLDER, p -> p.getPath().endsWith(".json")).entrySet()) {
			try (Reader reader = e.getValue().openAsReader()) {
				files.put(e.getKey(), JsonParser.parseReader(reader));
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Skipping home grade {}: {}", e.getKey(), ex.getMessage());
			}
		}
		load(files);
	}

	/** Reads every home grade file (by file id); a broken one is skipped with a warning naming it. */
	public static void load(Map<ResourceLocation, JsonElement> files) {
		Map<ResourceLocation, Integer> out = new HashMap<>();
		for (Map.Entry<ResourceLocation, JsonElement> e : new TreeMap<>(files).entrySet()) {
			try {
				JsonObject o = GsonHelper.convertToJsonObject(e.getValue(), "home grade");
				ResourceLocation blueprint = ClassNeeds.id(GsonHelper.getAsString(o, "blueprint"), "blueprint");
				int grade = GsonHelper.getAsInt(o, "grade");
				if (grade < 1 || grade > MAX) {
					throw new JsonSyntaxException("grade: " + grade + " is not between 1 and " + MAX);
				}
				out.merge(ClassNeeds.root(blueprint), grade, Math::max);
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Skipping home grade {}: {}", e.getKey(), ex.getMessage());
			}
		}
		grades = Map.copyOf(out);
	}

	/** The grade a data file gives {@code blueprint}'s family (its base), or 0 for none. */
	public static int given(ResourceLocation blueprint) {
		return grades.getOrDefault(ClassNeeds.root(blueprint), 0);
	}

	/** The grade of a home built from {@code structure}: its tier, or what a data file gives it (one more a tier, up to III) if that is more. */
	public static int grade(ResourceLocation structure) {
		int tier = BlueprintUpgrades.tier(structure);
		int given = given(structure);
		return given == 0 ? tier : Math.max(tier, Math.min(MAX, given + tier - 1));
	}

	private HomeGrades() {
	}
}
