package io.github.jcondedata.aliveworkplace.test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * QA (lane E, saves, 2026-11-02): one damaged KEY in a saved map doesn't lose the others. {@link
 * QaSavedDataOneBadEntry1101GameTests} damages every list in the mod's saved files; maps are saved as compounds keyed by
 * a player's or villager's UUID, an id or a packed block position, and B763's fix guarded one of them (Stories' 'done').
 * Here every such compound anywhere in each file (as {@link QaSavedDataCollections1101GameTests} finds and fills the
 * files) gets one more key of its own kind that can't be read (a UUID that isn't one, an id with spaces, a position
 * that isn't a number), holding a copy of a good entry's value, and the file is loaded. The load must not throw (Minecraft
 * would drop the whole file) and every entry a clean load gives back must still come back. A hand-edited file, or one a
 * crash cut short, makes such keys; one of them must not cost every player's finished quests or a village's records.
 */
public class QaSavedDataBadMapKey1102GameTests implements FabricGameTest {

	static final Pattern UUID_KEY = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
	static final Pattern ID_KEY = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
	static final Pattern NUMBER_KEY = Pattern.compile("-?\\d+");

	/**
	 * Maps whose bad key rightly loses the record holding them (file, then the map's path), each with why: the same
	 * choices {@link QaSavedDataOneBadEntry1101GameTests#BY_DESIGN} names for a damaged list inside a story arc or quest.
	 */
	static final java.util.Map<String, String> BY_DESIGN = new java.util.LinkedHashMap<>();

	static {
		String arc = "Stories.Data#load drops a story arc it can't read, whole, with a warning (as for its damaged mobs or quests)";
		for (String m : List.of("arc.helpers", "arc.chapter_helpers", "side_arcs[].helpers", "side_arcs[].chapter_helpers")) {
			BY_DESIGN.put("story.Stories$Data halls[]." + m, arc);
		}
		BY_DESIGN.put("story.Stories$Data halls[].quests[].helpers",
			"Stories.Data#load drops an open quest it can't read, whole, with a warning (as for its damaged objectives or rewards)");
	}

	/** The bad key of the map's kind, or null when {@code c} isn't a map (its keys are field names). */
	static String badKey(CompoundTag c) {
		if (c.isEmpty()) {
			return null;
		}
		if (c.getAllKeys().stream().allMatch(k -> UUID_KEY.matcher(k).matches())) {
			return "not-a-uuid";
		}
		if (c.getAllKeys().stream().allMatch(k -> ID_KEY.matcher(k).matches())) {
			return "Not An Id!";
		}
		if (c.getAllKeys().stream().allMatch(k -> NUMBER_KEY.matcher(k).matches())) {
			return "x12";
		}
		return null;
	}

	/** Every map-like compound anywhere in {@code tag}, by its path (compound keys and list indexes). */
	static void maps(Tag tag, List<Object> path, List<List<Object>> out) {
		if (tag instanceof CompoundTag c) {
			if (!path.isEmpty() && badKey(c) != null) {
				out.add(path);
			}
			for (String key : new java.util.TreeSet<>(c.getAllKeys())) {
				List<Object> p = new ArrayList<>(path);
				p.add(key);
				maps(c.get(key), p, out);
			}
		} else if (tag instanceof ListTag l) {
			for (int i = 0; i < l.size(); i++) {
				List<Object> p = new ArrayList<>(path);
				p.add(i);
				maps(l.get(i), p, out);
			}
		}
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void oneUnreadableKeyInASavedMapDoesntLoseTheOthers(GameTestHelper helper) {
		QaSavedDataCollections1101GameTests.Maker maker = new QaSavedDataCollections1101GameTests.Maker();
		maker.registries = helper.getLevel().registryAccess();
		HolderLookup.Provider registries = helper.getLevel().registryAccess();
		List<String> lost = new ArrayList<>();
		List<String> seen = new ArrayList<>();
		java.util.Set<String> used = new java.util.HashSet<>();
		int files = 0;
		int loads = 0;
		for (Class<?> c : QaSavedDataCollections1101GameTests.savedDataClasses()) {
			String file = c.getName().substring(QaSavedDataCollections1101GameTests.MOD.length());
			CompoundTag tag;
			Object clean;
			try {
				Object sample = QaSavedDataOneBadEntry1101GameTests.sample(c, maker, new ArrayList<>());
				tag = ((SavedData) sample).save(new CompoundTag(), registries);
				clean = QaSavedDataCollections1101GameTests.loader(c).invoke(null, tag.copy(), registries);
			} catch (Exception x) {
				lost.add(file + ": its sample couldn't be made, saved or loaded: " + (x.getCause() != null ? x.getCause() : x));
				continue;
			}
			files++;
			List<List<Object>> paths = new ArrayList<>();
			maps(tag, new ArrayList<>(), paths);
			for (List<Object> path : paths) {
				CompoundTag map = (CompoundTag) QaSavedDataOneBadEntry1101GameTests.at(tag, path);
				String bad = badKey(map);
				String where = file + " " + QaSavedDataOneBadEntry1101GameTests.show(path);
				String named = QaSavedDataOneBadEntry1101GameTests.pattern(where);
				seen.add(named + " (" + bad + ")");
				if (BY_DESIGN.containsKey(named)) {
					used.add(named);
					continue;
				}
				loads++;
				CompoundTag hurt = tag.copy();
				CompoundTag hurtMap = (CompoundTag) QaSavedDataOneBadEntry1101GameTests.at(hurt, path);
				String first = new java.util.TreeSet<>(hurtMap.getAllKeys()).first();
				hurtMap.put(bad, hurtMap.get(first).copy());
				Object read;
				try {
					read = QaSavedDataCollections1101GameTests.loader(c).invoke(null, hurt, registries);
				} catch (java.lang.reflect.InvocationTargetException x) {
					lost.add(where + " with key '" + bad + "': the load threw " + x.getCause());
					continue;
				} catch (ReflectiveOperationException | RuntimeException x) {
					lost.add(where + " with key '" + bad + "': " + x);
					continue;
				}
				for (Field f : QaSavedDataCollections1101GameTests.collections(c)) {
					try {
						String d = QaSavedDataOneBadEntry1101GameTests.covers(f.get(clean), f.get(read), file + "." + f.getName());
						if (d != null) {
							lost.add(where + " with key '" + bad + "': " + d);
						}
					} catch (IllegalAccessException x) {
						lost.add(where + ": can't read " + f.getName());
					}
				}
			}
		}
		io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.info("QA bad map key: {} files, {} damaged loads; maps: {}", files, loads,
			new java.util.TreeSet<>(seen));
		lost.forEach(l -> io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.error("QA bad map key lost: {}", l));
		List<String> stale = BY_DESIGN.keySet().stream().filter(k -> !used.contains(k)).toList();
		helper.assertTrue(stale.isEmpty(), "BY_DESIGN names maps no saved file holds any more: " + stale);
		helper.assertTrue(files >= 30, "only " + files + " saved data files sampled");
		helper.assertTrue(loads >= 5, "only " + loads + " saved maps found: the samples are too thin");
		helper.assertTrue(lost.isEmpty(), QaSavedDataCollections1101GameTests.cut(lost.size()
			+ " damaged load(s) lost good entries or threw (each in the log): " + lost));
		helper.succeed();
	}
}
