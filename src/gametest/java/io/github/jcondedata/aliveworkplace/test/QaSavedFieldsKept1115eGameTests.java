package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

/**
 * Lane E QA (lane-e-1115-1012), the saves: "Don't break existing saves" (CLAUDE.md) and "save and reload of every
 * saved field" (lane E's brief). Every one of the mod's world records (each {@code SavedData} of ours a level holds, in
 * whatever state the tests before this one left it: villages, sieges, mail, moves, shops, ...) is written as a save
 * writes it, read back the way the next start reads it, and written again. Nothing written the first time may be
 * missing the second time: a field the save writes only when it is set (a list, an optional position, a nested record)
 * and the load doesn't read back is lost at the first restart on the owner's live server. Values may change on load (an
 * old or broken entry may be mended or dropped), so that check is on the names of the fields, at every depth, never on
 * the values or the order of a list; a field always written can't be judged that way, so the siege, the richest record,
 * is also checked value by value with every field set.
 */
public class QaSavedFieldsKept1115eGameTests implements FabricGameTest {
	private static final String OURS = "io.github.jcondedata.aliveworkplace.";

	/**
	 * Every record of ours loaded on any level now: each written, read back with its own {@code load}, written again,
	 * and the two writings hold the same field names. A record class with no {@code load(CompoundTag, Provider)} of its
	 * own fails the test by name, so a new record can't slip past it unnoticed.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaSavedFieldsKept1115e"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaSavedFieldsKept1115e")
	public void everyWorldRecordKeepsEveryFieldThroughASaveAndReload(GameTestHelper helper) {
		HolderLookup.Provider registries = helper.getLevel().registryAccess();
		List<String> lost = new ArrayList<>();
		List<String> noLoad = new ArrayList<>();
		int checked = 0;
		for (ServerLevel level : helper.getLevel().getServer().getAllLevels()) {
			for (Map.Entry<String, SavedData> e : cache(level.getDataStorage()).entrySet()) {
				SavedData data = e.getValue();
				if (data == null || !data.getClass().getName().startsWith(OURS)) {
					continue;
				}
				Method load = loader(data.getClass());
				if (load == null) {
					noLoad.add(data.getClass().getSimpleName());
					continue;
				}
				CompoundTag first = data.save(new CompoundTag(), registries);
				SavedData again;
				try {
					again = (SavedData) load.invoke(null, first.copy(), registries);
				} catch (ReflectiveOperationException ex) {
					Throwable cause = ex.getCause() == null ? ex : ex.getCause();
					lost.add(e.getKey() + " (" + level.dimension().location() + "): reading its own save threw " + cause);
					continue;
				}
				CompoundTag second = again.save(new CompoundTag(), registries);
				TreeSet<String> before = new TreeSet<>();
				TreeSet<String> after = new TreeSet<>();
				names(first, "", before);
				names(second, "", after);
				before.removeAll(after);
				if (!before.isEmpty()) {
					lost.add(e.getKey() + " (" + level.dimension().location() + ") lost " + before);
				}
				checked++;
			}
		}
		helper.assertTrue(noLoad.isEmpty(), "these world records have no load(CompoundTag, Provider) for the check to read them back with: " + noLoad);
		helper.assertTrue(lost.isEmpty(), "fields written in a save but gone after reading it back (lost at the next restart): " + String.join("; ", lost));
		helper.assertTrue(checked > 0, "setup: no world record of ours was loaded on any level");
		helper.succeed();
	}

	/**
	 * The richest record, set by hand so the check never depends on what other tests left: a siege (32.4 to 32.9, the
	 * morning report of 32.6, B1126 and B1130's raiders over the wall) with every field it has set to something other
	 * than its default is written and read back as the next start reads it, and every value is the same. It stands far
	 * off and is added and forgotten within this one tick, so no siege code ever sees it.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaSavedFieldsKept1115eSiege"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaSavedFieldsKept1115eSiege")
	public void aSiegeWithEveryFieldSetComesBackWhole(GameTestHelper helper) throws ReflectiveOperationException {
		ServerLevel level = helper.getLevel();
		HolderLookup.Provider registries = level.registryAccess();
		BlockPos hall = helper.absolutePos(new BlockPos(9100, 70, -9100));
		ThreatData data = ThreatData.get(level);
		helper.assertTrue(data.siege(hall).isEmpty(), "setup: a siege already stands at " + hall);
		UUID guard = new UUID(1115L, 1L);
		UUID player = new UUID(1115L, 2L);
		UUID climber = new UUID(1115L, 3L);
		UUID rampRunner = new UUID(1115L, 4L);
		ThreatData.Siege s = new ThreatData.Siege(hall, 123_456L, 234_567L);
		s.over = true;
		s.breached = true;
		s.breach = hall.offset(3, 0, -7);
		s.out = Direction.WEST;
		s.gates.add(new ThreatData.Gate(hall.offset(0, 0, 20), 17, 2, true, true));
		s.gates.add(new ThreatData.Gate(hall.offset(1, 0, 20), 5, 0, false, false));
		s.gates.get(0).state = ThreatData.GateState.BROKEN;
		s.gates.get(1).state = ThreatData.GateState.WALLED;
		s.portcullis.add(hall.offset(0, 2, 21));
		s.ladders.add(hall.offset(-4, 1, 18));
		s.laddered = true;
		s.sand.add(hall.offset(6, 0, 19));
		s.rampFeet.add(hall.offset(6, 0, 18));
		s.rampDirs.add(Direction.NORTH.get2DDataValue());
		s.ramped = true;
		s.hpFactor = 2;
		s.came = 11;
		s.fled = true;
		s.toGuards = 4;
		s.toPlayers = 3;
		s.toOthers = 1;
		s.kills.put(guard, 4);
		s.names.put(guard, "Aldo the Bold");
		s.players.add(player);
		s.overByLadder.add(climber);
		s.overByRamp.add(rampRunner);
		CompoundTag saved;
		data.beginSiege(s);
		try {
			saved = data.save(new CompoundTag(), registries);
		} finally {
			data.forgetSiege(hall);
		}
		ThreatData read = (ThreatData) loader(ThreatData.class).invoke(null, saved, registries);
		ThreatData.Siege r = read.siege(hall).orElse(null);
		helper.assertTrue(r != null, "the siege is gone after a save and reload");
		List<String> wrong = new ArrayList<>();
		same(wrong, "began", s.began, r.began);
		same(wrong, "dawn", s.dawn, r.dawn);
		same(wrong, "over", s.over, r.over);
		same(wrong, "breached", s.breached, r.breached);
		same(wrong, "breach", s.breach, r.breach);
		same(wrong, "out", s.out, r.out);
		same(wrong, "gates", gates(s), gates(r));
		same(wrong, "portcullis", s.portcullis, r.portcullis);
		same(wrong, "ladders", s.ladders, r.ladders);
		same(wrong, "laddered", s.laddered, r.laddered);
		same(wrong, "sand", s.sand, r.sand);
		same(wrong, "rampFeet", s.rampFeet, r.rampFeet);
		same(wrong, "rampDirs", s.rampDirs, r.rampDirs);
		same(wrong, "ramped", s.ramped, r.ramped);
		same(wrong, "hpFactor", s.hpFactor, r.hpFactor);
		same(wrong, "came", s.came, r.came);
		same(wrong, "fled", s.fled, r.fled);
		same(wrong, "toGuards", s.toGuards, r.toGuards);
		same(wrong, "toPlayers", s.toPlayers, r.toPlayers);
		same(wrong, "toOthers", s.toOthers, r.toOthers);
		same(wrong, "kills", s.kills, r.kills);
		same(wrong, "names", s.names, r.names);
		same(wrong, "players", s.players, r.players);
		same(wrong, "overByLadder", Set.copyOf(s.overByLadder), Set.copyOf(r.overByLadder));
		same(wrong, "overByRamp", Set.copyOf(s.overByRamp), Set.copyOf(r.overByRamp));
		helper.assertTrue(wrong.isEmpty(), "after a save and reload the siege's " + String.join("; ", wrong));
		helper.assertTrue(data.siege(hall).isEmpty(), "the test's siege was left standing");
		helper.succeed();
	}

	private static List<String> gates(ThreatData.Siege s) {
		List<String> out = new ArrayList<>();
		for (ThreatData.Gate g : s.gates) {
			out.add(g.pos.toShortString() + " hp " + g.hp + " lane " + g.lane + " " + g.state + (g.high ? " high" : "") + (g.dropped ? " dropped" : ""));
		}
		return out;
	}

	private static void same(List<String> wrong, String field, Object before, Object after) {
		if (!Objects.equals(before, after)) {
			wrong.add(field + " was " + before + ", read back as " + after);
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<String, SavedData> cache(DimensionDataStorage storage) {
		try {
			for (Field f : DimensionDataStorage.class.getDeclaredFields()) {
				if (Map.class.isAssignableFrom(f.getType()) && !Modifier.isStatic(f.getModifiers())) {
					f.setAccessible(true);
					return new java.util.HashMap<>((Map<String, SavedData>) f.get(storage)); // (a miss is cached as null)
				}
			}
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("setup: can't read the level's saved records", e);
		}
		throw new IllegalStateException("setup: DimensionDataStorage has no map of saved records");
	}

	/** The record class's own static {@code load(CompoundTag, HolderLookup.Provider)}, public or not. */
	private static Method loader(Class<?> type) {
		for (Method m : type.getDeclaredMethods()) {
			Class<?>[] p = m.getParameterTypes();
			if (Modifier.isStatic(m.getModifiers()) && p.length == 2 && p[0] == CompoundTag.class && p[1] == HolderLookup.Provider.class
				&& type.isAssignableFrom(m.getReturnType())) {
				m.setAccessible(true);
				return m;
			}
		}
		return null;
	}

	/** Every field name in {@code tag} as a path ("sieges[].gates[].pos"); list positions are folded into "[]". */
	private static void names(Tag tag, String path, TreeSet<String> out) {
		if (tag instanceof CompoundTag c) {
			for (String key : c.getAllKeys()) {
				// a key that names an entry (an id, a UUID, a number, a position) rather than a field is folded into "*"
				String name = key.matches(".*[:/].*|[-0-9a-fA-F]{32,36}|-?[0-9]+([,_ ]-?[0-9]+)*") ? "*" : key;
				String p = path.isEmpty() ? name : path + "." + name;
				out.add(p);
				names(c.get(key), p, out);
			}
		} else if (tag instanceof ListTag list) {
			for (Tag t : list) {
				names(t, path + "[]", out);
			}
		}
	}
}
