package io.github.jcondedata.aliveworkplace.test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * QA (lane E, saves and optional mods, 2026-11-02; B791's kind for the saved data files): a mod taken out of the pack
 * leaves its item and block ids in the world's files with nothing behind them. Each of the mod's files (made and filled
 * as {@link QaSavedDataOneBadEntry1101GameTests} does) is saved; then every place in the saved tag that names an item or
 * a block (a string, or a map's key) is in turn renamed to an id no mod registers, and the file is loaded. The load must
 * not throw (Minecraft would drop the whole file), and no list, set or map of the file may come back with fewer entries
 * than a clean load gives: the entry naming the gone thing keeps its place and loses only that thing (as a chest keeps
 * its other slots), unless {@link #BY_DESIGN} says why the whole entry goes.
 */
public class QaSavedDataRemovedModId1102GameTests implements FabricGameTest {

	static final String GONE = "gonemod:gone_thing";

	/** Renamed ids that rightly take their whole entry with them (the place's path, indexes left out), each with why. */
	static final Map<String, String> BY_DESIGN = new LinkedHashMap<>();

	/** Every place in {@code tag} that names a registered item or block: its parent's path, then the key or index. */
	static void ids(Tag tag, List<Object> path, List<List<Object>> out) {
		if (tag instanceof CompoundTag c) {
			for (String key : new java.util.TreeSet<>(c.getAllKeys())) {
				List<Object> p = new ArrayList<>(path);
				p.add(key);
				if (isId(key)) {
					List<Object> k = new ArrayList<>(p);
					k.add(KEY);
					out.add(k);
				}
				ids(c.get(key), p, out);
			}
		} else if (tag instanceof ListTag l) {
			for (int i = 0; i < l.size(); i++) {
				List<Object> p = new ArrayList<>(path);
				p.add(i);
				ids(l.get(i), p, out);
			}
		} else if (tag instanceof StringTag s && isId(s.getAsString())) {
			out.add(path);
		}
	}

	/** Marks a path whose last step is a compound key that is itself the id. */
	static final Object KEY = new Object() {
		@Override
		public String toString() {
			return "<key>";
		}
	};

	static boolean isId(String s) {
		if (!s.contains(":")) {
			return false;
		}
		ResourceLocation id = ResourceLocation.tryParse(s);
		return id != null && !id.getPath().equals("air")
			&& (BuiltInRegistries.ITEM.containsKey(id) || BuiltInRegistries.BLOCK.containsKey(id));
	}

	/** {@code tag} with the id at {@code place} renamed to {@link #GONE}. */
	static CompoundTag renamed(CompoundTag tag, List<Object> place) {
		CompoundTag hurt = tag.copy();
		boolean key = place.get(place.size() - 1) == KEY;
		List<Object> path = key ? place.subList(0, place.size() - 1) : place;
		Tag parent = QaSavedDataOneBadEntry1101GameTests.at(hurt, path.subList(0, path.size() - 1));
		Object last = path.get(path.size() - 1);
		if (key) {
			CompoundTag c = (CompoundTag) parent;
			Tag v = c.get((String) last);
			c.remove((String) last);
			c.put(GONE, v);
		} else if (last instanceof String k) {
			((CompoundTag) parent).putString(k, GONE);
		} else {
			((ListTag) parent).set((Integer) last, StringTag.valueOf(GONE));
		}
		return hurt;
	}

	static String show(List<Object> place) {
		boolean key = place.get(place.size() - 1) == KEY;
		String p = QaSavedDataOneBadEntry1101GameTests.show(key ? place.subList(0, place.size() - 1) : place);
		return QaSavedDataOneBadEntry1101GameTests.pattern(p) + (key ? " (key)" : "");
	}

	static int size(Object o) {
		return o instanceof Map<?, ?> m ? m.size() : o instanceof Collection<?> c ? c.size() : -1;
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aRemovedModsIdInASavedFileLosesOnlyThatThing(GameTestHelper helper) {
		QaSavedDataCollections1101GameTests.Maker maker = new QaSavedDataCollections1101GameTests.Maker();
		maker.registries = helper.getLevel().registryAccess();
		HolderLookup.Provider registries = helper.getLevel().registryAccess();
		List<String> lost = new ArrayList<>();
		List<String> notes = new ArrayList<>();
		Set<String> used = new LinkedHashSet<>();
		int files = 0;
		int places = 0;
		List<String> renamedAt = new ArrayList<>();
		for (Class<?> c : QaSavedDataCollections1101GameTests.savedDataClasses()) {
			String file = c.getName().substring(QaSavedDataCollections1101GameTests.MOD.length());
			CompoundTag tag;
			Object clean;
			try {
				Object sample = QaSavedDataOneBadEntry1101GameTests.sample(c, maker, notes);
				tag = ((SavedData) sample).save(new CompoundTag(), registries);
				clean = QaSavedDataCollections1101GameTests.loader(c).invoke(null, tag.copy(), registries);
			} catch (Exception x) {
				lost.add(file + ": its sample couldn't be made, saved or loaded: " + (x.getCause() != null ? x.getCause() : x));
				continue;
			}
			files++;
			List<List<Object>> found = new ArrayList<>();
			ids(tag, new ArrayList<>(), found);
			Set<String> seen = new LinkedHashSet<>();
			for (List<Object> place : found) {
				String where = file + " " + show(place);
				if (!seen.add(where)) {
					continue; // one of each place's kind is enough
				}
				if (BY_DESIGN.containsKey(where)) {
					used.add(where);
					notes.add(where + " (by design: " + BY_DESIGN.get(where) + ")");
					continue;
				}
				places++;
				renamedAt.add(where);
				Object read;
				try {
					read = QaSavedDataCollections1101GameTests.loader(c).invoke(null, renamed(tag, place), registries);
				} catch (java.lang.reflect.InvocationTargetException x) {
					lost.add(where + ": the load threw " + x.getCause());
					continue;
				} catch (ReflectiveOperationException | RuntimeException x) {
					lost.add(where + ": " + x);
					continue;
				}
				for (Field f : QaSavedDataCollections1101GameTests.collections(c)) {
					try {
						int had = size(f.get(clean));
						int got = size(f.get(read));
						if (had >= 0 && got < had) {
							lost.add(where + ": " + f.getName() + " came back with " + got + " of " + had + " entries");
						}
					} catch (IllegalAccessException x) {
						lost.add(where + ": can't read " + f.getName());
					}
				}
			}
		}
		io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.info("QA removed mod ids: {} files, {} places renamed ({}); notes: {}", files, places, renamedAt, notes);
		lost.forEach(l -> io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.error("QA removed mod id lost: {}", l));
		List<String> stale = BY_DESIGN.keySet().stream().filter(k -> !used.contains(k)).toList();
		helper.assertTrue(stale.isEmpty(), "BY_DESIGN names places no saved file holds any more: " + stale);
		helper.assertTrue(files >= 30, "only " + files + " saved data files sampled");
		helper.assertTrue(places >= 5, "only " + places + " item or block ids found in the saved files: the samples are too thin");
		helper.assertTrue(lost.isEmpty(), QaSavedDataCollections1101GameTests.cut(lost.size()
			+ " renamed id(s) lost more than their own thing or threw (each in the log): " + lost));
		helper.succeed();
	}
}
