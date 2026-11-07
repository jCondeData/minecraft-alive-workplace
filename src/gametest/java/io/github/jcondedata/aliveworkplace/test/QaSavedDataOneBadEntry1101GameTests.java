package io.github.jcondedata.aliveworkplace.test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * QA (lane E, saves, 2026-11-01): one damaged entry in a file of saved data doesn't lose the others. Each of the mod's
 * files (as {@link QaSavedDataCollections1101GameTests} finds and fills them: every list, set and map with made entries,
 * the tied ones made consistent) is saved; then every list anywhere in the saved tag in turn gets one damaged entry put
 * first (a list of compounds: an empty compound, and separately one holding a single key of a good entry with the wrong
 * type; a list of strings: a string that is no id or JSON), and the file is loaded. The load must not throw (Minecraft
 * would drop the whole file) and every entry a clean load gives back must still come back (extra entries made from the
 * damaged one are allowed). A world that crashed mid-write, a hand-edited file or a mod removed since all make such
 * entries, and one of them must not cost a village its routes, quests or builds.
 */
public class QaSavedDataOneBadEntry1101GameTests implements FabricGameTest {

	/** Damaged lists that lose good entries, each a bug proved in {@code QaSavedDataOneBadEntry1101BugTests} (file, then the list's path). */
	static final Map<String, String> KNOWN_BUGS = new LinkedHashMap<>();

	/** Damaged lists whose good entries are rightly lost with the bad one (file, then the list's path), each with why. */
	static final Map<String, String> BY_DESIGN = new LinkedHashMap<>();

	static {
		// Each a load that throws on the one bad entry, so Minecraft drops the whole file (QaSavedDataOneBadEntry1101BugTests).
		String bounty = "Bounties.Data#load skips a bounty it can't read, with a warning (\"Skipping a saved bounty\")";
		BY_DESIGN.put("bounty.Bounties$Data bounties[].band", bounty);
		BY_DESIGN.put("bounty.Bounties$Data bounties[].takers", bounty);
		String arc = "Stories.Data#load drops a story arc it can't read, with a warning (\"Dropping a story arc that can't be read\")";
		for (String l : List.of("arc.mobs", "arc.quests", "side_arcs[].mobs", "side_arcs[].quests")) {
			BY_DESIGN.put("story.Stories$Data halls[]." + l, arc);
		}
		String quest = "Stories.Data#load drops an open quest it can't read, with a warning (\"Dropping an open quest that can't be read\")";
		BY_DESIGN.put("story.Stories$Data halls[].quests[].objectives", quest);
		BY_DESIGN.put("story.Stories$Data halls[].quests[].rewards", quest);
		String piece = "FurnitureChoice#load leaves out a furniture piece with a cell it can't read, whole (a part of a piece would be wrong)";
		BY_DESIGN.put("build.BuildSiteManager finished[].furniture[].cells", piece);
		BY_DESIGN.put("build.BuildSiteManager sites[].furniture[].cells", piece);
	}

	/** The list's path with its indexes left out, as the maps above name it. */
	static String pattern(String where) {
		return where.replaceAll("\\[\\d+\\]", "[]");
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void oneDamagedEntryInASavedFileDoesntLoseTheOthers(GameTestHelper helper) {
		QaSavedDataCollections1101GameTests.Maker maker = new QaSavedDataCollections1101GameTests.Maker();
		maker.registries = helper.getLevel().registryAccess();
		HolderLookup.Provider registries = helper.getLevel().registryAccess();
		List<String> lost = new ArrayList<>();
		List<String> notes = new ArrayList<>();
		java.util.Set<String> used = new java.util.HashSet<>();
		int files = 0;
		int lists = 0;
		int loads = 0;
		for (Class<?> c : QaSavedDataCollections1101GameTests.savedDataClasses()) {
			String file = c.getName().substring(QaSavedDataCollections1101GameTests.MOD.length());
			CompoundTag tag;
			Object clean;
			try {
				Object sample = sample(c, maker, notes);
				tag = ((SavedData) sample).save(new CompoundTag(), registries);
				clean = QaSavedDataCollections1101GameTests.loader(c).invoke(null, tag.copy(), registries);
			} catch (Exception x) {
				lost.add(file + ": its sample couldn't be made, saved or loaded: " + (x.getCause() != null ? x.getCause() : x));
				continue;
			}
			files++;
			List<List<Object>> paths = new ArrayList<>();
			lists(tag, new ArrayList<>(), paths);
			for (List<Object> path : paths) {
				ListTag list = (ListTag) at(tag, path);
				String where = file + " " + show(path);
				String named = pattern(where);
				if (KNOWN_BUGS.containsKey(named) || BY_DESIGN.containsKey(named)) {
					notes.add(where + " (" + (KNOWN_BUGS.containsKey(named) ? "known bug: " + KNOWN_BUGS.get(named) : "by design: " + BY_DESIGN.get(named)) + ")");
					used.add(named);
					continue;
				}
				lists++;
				for (Tag bad : damaged(list)) {
					loads++;
					CompoundTag hurt = tag.copy();
					((ListTag) at(hurt, path)).add(0, bad);
					String kind = "with " + bad + " first";
					Object read;
					try {
						read = QaSavedDataCollections1101GameTests.loader(c).invoke(null, hurt, registries);
					} catch (java.lang.reflect.InvocationTargetException x) {
						lost.add(where + " " + kind + ": the load threw " + x.getCause());
						continue;
					} catch (ReflectiveOperationException | RuntimeException x) {
						lost.add(where + " " + kind + ": " + x);
						continue;
					}
					for (Field f : QaSavedDataCollections1101GameTests.collections(c)) {
						try {
							String d = covers(f.get(clean), f.get(read), file + "." + f.getName());
							if (d != null) {
								lost.add(where + " " + kind + ": " + d);
							}
						} catch (IllegalAccessException x) {
							lost.add(where + ": can't read " + f.getName());
						}
					}
				}
			}
		}
		io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.info("QA one bad entry: {} files, {} lists, {} damaged loads; notes: {}", files, lists, loads, notes);
		lost.forEach(l -> io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.error("QA one bad entry lost: {}", l));
		List<String> stale = new ArrayList<>();
		KNOWN_BUGS.keySet().stream().filter(k -> !used.contains(k)).forEach(stale::add);
		BY_DESIGN.keySet().stream().filter(k -> !used.contains(k)).forEach(stale::add);
		helper.assertTrue(stale.isEmpty(), "KNOWN_BUGS or BY_DESIGN name lists no saved file holds any more: " + stale);
		helper.assertTrue(files >= 30, "only " + files + " saved data files sampled");
		helper.assertTrue(lists >= 60, "only " + lists + " lists found in the saved files: the samples are too thin");
		helper.assertTrue(lost.isEmpty(), QaSavedDataCollections1101GameTests.cut(lost.size()
			+ " damaged load(s) lost good entries or threw (each in the log): " + lost));
		helper.succeed();
	}

	/**
	 * A new file with every list, set and map filled (the tied ones made consistent as in {@link
	 * QaSavedDataCollections1101GameTests#TIED}); entries are added to what earlier fields' ties put there, never replace it.
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	static Object sample(Class<?> c, QaSavedDataCollections1101GameTests.Maker maker, List<String> notes) throws Exception {
		java.lang.reflect.Constructor<?> make = c.getDeclaredConstructor();
		make.setAccessible(true);
		Object data = make.newInstance();
		for (Field f : QaSavedDataCollections1101GameTests.collections(c)) {
			String name = QaSavedDataCollections1101GameTests.name(c, f);
			try {
				Object value = maker.make(f.getGenericType(), c.getSimpleName() + "." + f.getName());
				QaSavedDataCollections1101GameTests.Tie tie = QaSavedDataCollections1101GameTests.TIED.get(name);
				if (tie != null) {
					value = tie.fix().apply(data, value, maker);
				}
				Object now = f.get(data);
				if (now instanceof Collection held && value instanceof Collection v) {
					held.addAll(v);
				} else if (now instanceof Map held && value instanceof Map v) {
					held.putAll(v);
				} else if (!Modifier.isFinal(f.getModifiers())) {
					f.set(data, value);
				}
			} catch (QaEveryRecordField1101GameTests.Unbuildable x) {
				notes.add(name + " left empty (" + x.getMessage() + ")");
			}
		}
		return data;
	}

	/** Every non-empty list anywhere in {@code tag}, by its path (compound keys and list indexes). */
	static void lists(Tag tag, List<Object> path, List<List<Object>> out) {
		if (tag instanceof CompoundTag c) {
			for (String key : new java.util.TreeSet<>(c.getAllKeys())) {
				List<Object> p = new ArrayList<>(path);
				p.add(key);
				lists(c.get(key), p, out);
			}
		} else if (tag instanceof ListTag l && !l.isEmpty()) {
			out.add(path);
			for (int i = 0; i < l.size(); i++) {
				List<Object> p = new ArrayList<>(path);
				p.add(i);
				lists(l.get(i), p, out);
			}
		}
	}

	static Tag at(Tag tag, List<Object> path) {
		Tag t = tag;
		for (Object step : path) {
			t = step instanceof String key ? ((CompoundTag) t).get(key) : ((ListTag) t).get((Integer) step);
		}
		return t;
	}

	static String show(List<Object> path) {
		StringBuilder b = new StringBuilder();
		for (Object step : path) {
			b.append(step instanceof String key ? "." + key : "[" + step + "]");
		}
		return b.length() == 0 ? "(top)" : b.substring(b.charAt(0) == '.' ? 1 : 0);
	}

	/** The damaged entries of the list's own kind: for compounds an empty one and one with a good entry's first key mistyped. */
	static List<Tag> damaged(ListTag list) {
		List<Tag> out = new ArrayList<>();
		Tag good = list.get(0);
		if (good instanceof CompoundTag g) {
			out.add(new CompoundTag());
			if (!g.isEmpty()) {
				String key = new java.util.TreeSet<>(g.getAllKeys()).first();
				CompoundTag wrong = new CompoundTag();
				wrong.put(key, g.get(key) instanceof StringTag ? IntTag.valueOf(7) : StringTag.valueOf("qa_bad"));
				out.add(wrong);
			}
		} else if (good instanceof StringTag) {
			out.add(StringTag.valueOf("not an id: {["));
		} else if (good instanceof NumericTag n) {
			out.add(n.getId() == Tag.TAG_INT ? IntTag.valueOf(Integer.MIN_VALUE) : good.copy());
		} else if (good instanceof ListTag) {
			out.add(new ListTag());
		}
		return out;
	}

	/** Null when {@code got} holds every entry {@code want} does (and maybe more); else the first entry lost. */
	static String covers(Object want, Object got, String path) {
		if (want == null || got == null) {
			return want == null ? null : path + ": had " + want + ", read none";
		}
		if (want instanceof Object[] a && got instanceof Object[] b) {
			return covers(java.util.Arrays.asList(a), java.util.Arrays.asList(b), path);
		}
		if (want instanceof Map<?, ?> a && got instanceof Map<?, ?> b) {
			for (Map.Entry<?, ?> e : a.entrySet()) {
				if (!b.containsKey(e.getKey())) {
					return path + ": key " + e.getKey() + " lost (read " + b.keySet() + ")";
				}
				String d = covers(e.getValue(), b.get(e.getKey()), path + "[" + e.getKey() + "]");
				if (d != null) {
					return d;
				}
			}
			return null;
		}
		if (want instanceof Collection<?> a && got instanceof Collection<?> b) {
			for (Object x : a) {
				if (b.stream().noneMatch(y -> covers(x, y, path) == null)) {
					return path + ": " + x + " lost (read " + b.size() + " entries)";
				}
			}
			return null;
		}
		Class<?> k = want.getClass();
		if (k == got.getClass() && k.getName().startsWith(QaSavedDataCollections1101GameTests.MOD) && (k.isRecord() || QaSavedDataCollections1101GameTests.handWritten(k))) {
			for (Field f : k.getDeclaredFields()) {
				if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic() || QaSavedDataCollections1101GameTests.notData(f)) {
					continue;
				}
				try {
					f.setAccessible(true);
					String d = covers(f.get(want), f.get(got), path + "." + f.getName());
					if (d != null) {
						return d;
					}
				} catch (IllegalAccessException e) {
					return path + "." + f.getName() + ": can't read";
				}
			}
			return null;
		}
		return QaSavedDataCollections1101GameTests.diff(want, got, path);
	}
}
