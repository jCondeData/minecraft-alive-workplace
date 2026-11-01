package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Anthems;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane E, saves, 2026-11-01): every field of every one of the mod's block entities (the Village Hall, Storehouse,
 * Mailbox, Shop Counter, Drop Box, Blueprint Table, Harvest Idol, Grave; found in the block entity registry, so one
 * added later is covered too) comes back after the block is saved and loaded as a chunk does. {@link
 * QaEveryRecordField1101GameTests} covers the records kept on villagers and in codecs; a block entity writes its fields
 * by hand in {@code saveAdditional}/{@code loadAdditional}, and a field added in an update but left out of either
 * silently resets at every restart (the hall's data is most of a village). Each field in turn gets a value that is not
 * its default (made the same way as there: records, lists, sets, maps and optionals of every part), the block entity is
 * saved and read back into a new one, and a field that comes back different is named. A field whose type the maker
 * can't build (a hand-written class, an item list vanilla saves) is listed in the log as not checked here.
 */
public class QaEveryBlockEntityField1101GameTests implements FabricGameTest {

	private static final ResourceLocation DROUGHT = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "drought");
	private static final Anthems.Anthem ANTHEM = new Anthems.Anthem("bell", new int[] {1, 3, 5, 8, 10, 12, 13, 15, 17, 20, 22, 24, 0, 2, 4, 6});

	/** Fields the maker's value can't be valid for, given one that is (a value the load checks against the game's data). */
	private static final Map<String, Object> VALID = Map.of(
		// a disaster is one of the loaded disasters' ids; a made-up id rightly reads as none
		"VillageHallBlockEntity.disaster", DROUGHT,
		// the class rules' version is 0 (never seeded) or 1 (seeded with 1.8's rules)
		"VillageHallBlockEntity.classesVersion", 1,
		// an anthem: one of the note-block instruments and its 16 notes, 0 to 24
		"VillageHallBlockEntity.anthem", ANTHEM,
		// the table's shelf keeps one stack of one per blueprint stored (36.13)
		"BlueprintTableBlockEntity.stored", List.of(new ItemStack(Items.EMERALD), new ItemStack(Items.DIAMOND)),
		// the species the village's Pokédex has seen, by id, in the order seen
		"VillageHallBlockEntity.pokedex", List.of("cobblemon:eevee", "cobblemon:pikachu"));

	/**
	 * A field saved only alongside another, which it belongs to (an owner's name with the owner, a disaster's days with the
	 * disaster): checked with that one set as well.
	 */
	private static final Map<String, Map<String, Object>> WITH = Map.of(
		".ownerName", Map.of("owner", new java.util.UUID(7, 11)),
		"VillageHallBlockEntity.disasterSince", Map.of("disaster", DROUGHT),
		"VillageHallBlockEntity.disasterEnds", Map.of("disaster", DROUGHT, "disasterSince", 4L),
		"VillageHallBlockEntity.anthemBookGiven", Map.of("anthem", ANTHEM),
		"VillageHallBlockEntity.peaceSince", Map.of("atPeace", true),
		"VillageHallBlockEntity.services", Map.of("servicesDay", 9L),
		"VillageHallBlockEntity.rushUntil", Map.of("hornDay", 3L));

	/** Worked out again from the world, never saved (each said so where it's declared and read). */
	private static final Map<String, String> RECOUNTED = Map.of(
		"VillageHallBlockEntity.needs", "the village's count, made again on the hall's first tick (VillageNeeds.check)",
		"VillageHallBlockEntity.civic", "the sum of the edicts', guilds' and reforms' effects, made again when asked",
		"VillageHallBlockEntity.civicExtra", "the extra effects that sum was made with",
		"VillageHallBlockEntity.civicGeneration", "which edicts that sum was made for");

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void everyFieldOfEveryBlockEntityComesBackAfterASave(GameTestHelper helper) {
		QaEveryRecordField1101GameTests maker = new QaEveryRecordField1101GameTests();
		maker.registries = helper.getLevel().registryAccess();
		List<String> lost = new ArrayList<>();
		List<String> unchecked = new ArrayList<>();
		int checked = 0;
		int types = 0;
		BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
		for (Map.Entry<net.minecraft.resources.ResourceKey<BlockEntityType<?>>, BlockEntityType<?>> e : BuiltInRegistries.BLOCK_ENTITY_TYPE.entrySet()) {
			if (!e.getKey().location().getNamespace().equals("aliveworkplace")) {
				continue;
			}
			BlockEntityType<?> type = e.getValue();
			BlockState state = BuiltInRegistries.BLOCK.stream().filter(b -> BuiltInRegistries.BLOCK.getKey(b).getNamespace().equals("aliveworkplace"))
				.map(Block::defaultBlockState).filter(type::isValid).findFirst().orElse(null);
			helper.assertTrue(state != null, "no block of ours holds " + e.getKey().location());
			BlockEntity sample = type.create(pos, state);
			helper.assertTrue(sample != null, e.getKey().location() + " made no block entity");
			types++;
			for (Class<?> c = sample.getClass(); c != null && c.getName().startsWith("io.github.jcondedata."); c = c.getSuperclass()) {
				for (Field f : c.getDeclaredFields()) {
					if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) {
						continue;
					}
					String name = c.getSimpleName() + "." + f.getName();
					f.setAccessible(true);
					if (RECOUNTED.containsKey(name)) {
						unchecked.add(name + " (recounted: " + RECOUNTED.get(name) + ")");
						continue;
					}
					BlockEntity wrote = type.create(pos, state);
					Object value;
					try {
						for (Map.Entry<String, Map<String, Object>> w : WITH.entrySet()) {
							if (name.endsWith(w.getKey())) {
								for (Map.Entry<String, Object> g : w.getValue().entrySet()) {
									Field gate = c.getDeclaredField(g.getKey());
									gate.setAccessible(true);
									gate.set(wrote, g.getValue());
								}
							}
						}
						value = VALID.containsKey(name) ? VALID.get(name) : maker.make(f.getGenericType(), name);
						if (!set(f, wrote, value)) {
							unchecked.add(name + " (final " + f.getType().getSimpleName() + ")");
							continue;
						}
						if (same(value, f.get(type.create(pos, state)), name) == null) {
							unchecked.add(name + " (its made value is its default)");
							continue;
						}
					} catch (QaEveryRecordField1101GameTests.Unbuildable | ReflectiveOperationException | RuntimeException x) {
						unchecked.add(name + " (" + x.getMessage() + ")");
						continue;
					}
					checked++;
					try {
						CompoundTag tag = wrote.saveWithoutMetadata(helper.getLevel().registryAccess());
						BlockEntity read = type.create(pos, state);
						read.loadWithComponents(tag, helper.getLevel().registryAccess());
						String d = same(value, f.get(read), name);
						if (d != null) {
							lost.add(d);
						}
					} catch (ReflectiveOperationException | RuntimeException x) {
						lost.add(name + ": saving or loading it threw " + x);
					}
				}
			}
		}
		io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.info("QA block entity fields: {} block entities, {} fields checked, not checked here: {}", types, checked, unchecked);
		helper.assertTrue(types >= 8, "only " + types + " block entities of ours found");
		helper.assertTrue(checked >= 70, "only " + checked + " fields checked (not checked: " + unchecked + ")");
		helper.assertTrue(lost.isEmpty(), lost.size() + " block entity field(s) don't come back after a save: " + lost);
		helper.succeed();
	}

	/**
	 * The same for every file of saved data the mod keeps per world or dimension (each {@code SavedData}: caravans, camps,
	 * realms, pacts, the wagon-bed list, ...), found among the mod's classes: each field of the file itself in turn gets a
	 * value that is not its default, the file is saved and read back with its own {@code load}, and a field that comes back
	 * different is named (the records inside are {@link QaEveryRecordField1101GameTests}'s). Only the file's own single
	 * values (its days, flags, versions, counters and records), not its lists and maps, whose entries refer to each other.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void everyFieldOfEverySavedDataFileComesBackAfterASave(GameTestHelper helper) {
		QaEveryRecordField1101GameTests maker = new QaEveryRecordField1101GameTests();
		maker.registries = helper.getLevel().registryAccess();
		net.minecraft.core.HolderLookup.Provider registries = helper.getLevel().registryAccess();
		List<String> lost = new ArrayList<>();
		List<String> unchecked = new ArrayList<>();
		int checked = 0;
		int files = 0;
		for (Class<?> c : QaEveryRecordField1101GameTests.modClasses()) {
			if (!net.minecraft.world.level.saveddata.SavedData.class.isAssignableFrom(c) || Modifier.isAbstract(c.getModifiers())) {
				continue;
			}
			java.lang.reflect.Constructor<?> make;
			java.lang.reflect.Method load = null;
			try {
				make = c.getDeclaredConstructor();
				make.setAccessible(true);
			} catch (NoSuchMethodException x) {
				unchecked.add(c.getSimpleName() + " (no plain constructor)");
				continue;
			}
			for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
				if (Modifier.isStatic(m.getModifiers()) && m.getReturnType() == c && m.getParameterCount() == 2 && m.getParameterTypes()[0] == CompoundTag.class) {
					load = m;
				}
			}
			if (load == null) {
				unchecked.add(c.getSimpleName() + " (no load(tag, registries))");
				continue;
			}
			load.setAccessible(true);
			files++;
			for (Field f : c.getDeclaredFields()) {
				if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) {
					continue;
				}
				String name = c.getSimpleName() + "." + f.getName();
				if (Map.class.isAssignableFrom(f.getType()) || Collection.class.isAssignableFrom(f.getType())) {
					// a file's lists and maps hold entries keyed by and tied to each other (a route between two listed
					// villages, a lair keyed by its own place): made up one at a time they rightly don't load
					continue;
				}
				if (DATA_RECOUNTED.containsKey(name)) {
					unchecked.add(name + " (not saved: " + DATA_RECOUNTED.get(name) + ")");
					continue;
				}
				f.setAccessible(true);
				Object value;
				Object wrote;
				try {
					wrote = make.newInstance();
					value = maker.make(f.getGenericType(), name);
					if (!set(f, wrote, value)) {
						unchecked.add(name + " (final " + f.getType().getSimpleName() + ")");
						continue;
					}
					if (same(value, f.get(make.newInstance()), name) == null) {
						unchecked.add(name + " (its made value is its default)");
						continue;
					}
				} catch (QaEveryRecordField1101GameTests.Unbuildable | ReflectiveOperationException | RuntimeException x) {
					unchecked.add(name + " (" + x.getMessage() + ")");
					continue;
				}
				checked++;
				try {
					CompoundTag tag = ((net.minecraft.world.level.saveddata.SavedData) wrote).save(new CompoundTag(), registries);
					Object read = load.invoke(null, tag, registries);
					String d = same(value, f.get(read), name);
					if (d != null) {
						lost.add(d);
					}
				} catch (ReflectiveOperationException | RuntimeException x) {
					lost.add(name + ": saving or loading it threw " + (x.getCause() != null ? x.getCause() : x));
				}
			}
		}
		io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.info("QA saved data fields: {} files, {} fields checked, not checked here: {}", files, checked, unchecked);
		helper.assertTrue(lost.isEmpty(), lost.size() + " saved data field(s) don't come back after a save: " + lost);
		helper.succeed();
	}

	/** Fields of saved data that are worked out again, never saved (each says so where it's declared). */
	private static final Map<String, String> DATA_RECOUNTED = Map.of(
		"Data.looks", "CampBeds.Data#looks: whether this session of an older save still looks at unlisted beds (B749)",
		"PostOffice.lastNoon", "the last noon mail round, in memory (a second round after a restart finds nothing new)",
		"BuildSiteManager.changes", "a count of changes this session, so a reader can tell the list hasn't changed (B85)",
		"WonderManager.layouts", "a count of Wonders laid out or forgotten this session, so footprint caches look again (B747); the sites themselves are saved (B758)");

	/** As the record test's diff, with an anthem's notes compared note by note (a record of an array). */
	private static String same(Object wrote, Object read, String name) {
		if (wrote instanceof Anthems.Anthem a) {
			return a.sameAs(read instanceof Anthems.Anthem b ? b : null) ? null : name + ": saved " + a.instrument() + " "
				+ java.util.Arrays.toString(a.notes()) + ", read " + (read instanceof Anthems.Anthem b ? b.instrument() + " " + java.util.Arrays.toString(b.notes()) : read);
		}
		return QaEveryRecordField1101GameTests.diff(wrote, read, name);
	}

	/** Puts {@code value} in {@code f}: a final collection or map gets its entries instead. False: it can't take it. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static boolean set(Field f, Object owner, Object value) throws IllegalAccessException {
		if (!Modifier.isFinal(f.getModifiers())) {
			f.set(owner, value);
			return true;
		}
		Object now = f.get(owner);
		if (now instanceof Collection held && value instanceof Collection made) {
			held.clear();
			held.addAll(made);
			return true;
		}
		if (now instanceof Map held && value instanceof Map made) {
			held.clear();
			held.putAll(made);
			return true;
		}
		return false;
	}
}
