package io.github.jcondedata.aliveworkplace.test;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import io.github.jcondedata.aliveworkplace.platform.Attachment;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane E, saves, 2026-11-01): every record the mod keeps on a villager ({@code registry/ModAttachments}, found by
 * reflection, so one added later is covered too) comes back with <b>every one of its parts</b> after the villager is
 * saved and loaded as a chunk does. {@link SavedDataGameTests} reloads one hand-written sample per field, and a sample
 * that leaves a part at its default can't tell a part that's saved from one the codec forgot (a record that grew a
 * part in an update whose codec wasn't extended: the part silently resets at every restart). Here each part of each
 * record, nested records, lists, sets, maps and optionals included, gets its own value that is not its default, and a
 * part that comes back different is named. A few parts take only certain values (a job's id, an enum's name, a value
 * the record's constructor checks); those get a valid value in {@link #OVERRIDES}, never a skip.
 */
public class QaEveryRecordField1101GameTests implements FabricGameTest {

	/**
	 * Parts the generator can't make up because the save only takes certain values (the end of a part's path, as
	 * "FIELD.part.part", to its value). Every entry says why.
	 */
	private static final Map<String, Object> OVERRIDES = new LinkedHashMap<>();

	static {
		// A reform step's fallback (hall/Reforms.FALLBACK) is a stand-in for a battle and has no fallback of its own.
		OVERRIDES.put("hall.Reforms.FALLBACK.fallback", Optional.empty());
		OVERRIDES.put(".fallback.get.fallback", Optional.empty());
		// A realm upkeep effect is paid "per" route or member (realm/RealmEdicts), nothing else.
		OVERRIDES.put("<RealmUpkeep>.per", "member");
	}

	/** A running counter so every generated value differs from every other. */
	private int n = 1;

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyPartOfEverySavedRecordComesBackAfterASave(GameTestHelper helper) throws Exception {
		n = 1;
		Villager v = EntityType.VILLAGER.create(helper.getLevel());
		v.setPos(helper.absoluteVec(new Vec3(1.5, 2, 1.5)));
		Map<String, Object> wrote = new LinkedHashMap<>();
		Map<String, Attachment<Object>> attachments = new LinkedHashMap<>();
		List<String> unbuilt = new ArrayList<>();
		int records = 0;
		for (Field f : ModAttachments.class.getFields()) {
			if (!Modifier.isStatic(f.getModifiers()) || !(f.get(null) instanceof Attachment<?>)) {
				continue;
			}
			if (!(f.getGenericType() instanceof ParameterizedType p)) {
				continue;
			}
			Type type = p.getActualTypeArguments()[0];
			if (!holdsARecord(type)) {
				continue; // plain values: QaEverySavedValueGameTests
			}
			records++;
			Object value;
			try {
				value = make(type, f.getName());
			} catch (Unbuildable e) {
				unbuilt.add(f.getName() + ": " + e.getMessage());
				continue;
			}
			@SuppressWarnings("unchecked")
			Attachment<Object> a = (Attachment<Object>) f.get(null);
			a.set(v, value);
			wrote.put(f.getName(), value);
			attachments.put(f.getName(), a);
		}
		helper.assertTrue(records >= 40, "only " + records + " saved records found in ModAttachments");
		helper.assertTrue(unbuilt.isEmpty(), unbuilt.size() + " saved records couldn't be made with a value in every part: " + unbuilt);

		Villager back = reload(helper, v);
		List<String> wrong = new ArrayList<>();
		for (Map.Entry<String, Object> e : wrote.entrySet()) {
			Object read = attachments.get(e.getKey()).get(back);
			String diff = diff(e.getValue(), read, e.getKey());
			if (diff != null) {
				wrong.add(diff);
			}
		}
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " of " + wrote.size() + " saved records changed over a save: " + wrong);

		// A second round trip keeps them too.
		Villager again = reload(helper, back);
		for (Map.Entry<String, Object> e : wrote.entrySet()) {
			String diff = diff(e.getValue(), attachments.get(e.getKey()).get(again), e.getKey());
			helper.assertTrue(diff == null, "second save: " + diff);
		}
		helper.succeed();
	}

	/**
	 * The same for every record the mod saves anywhere with a codec of its own: each {@code static final Codec<R> CODEC}
	 * (and every other static Codec field) of a record type in the mod's own classes, found by looking through them, so
	 * the world's files (halls, sites, the realm, the post, Wonders, shops...) and block and item data are covered too,
	 * and one added later is. Each record is written with the save's own codec as the world writes it (NBT, with the
	 * world's registries) and read back; every part must come back as it was written.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyPartOfEveryCodecRecordComesBackAfterASave(GameTestHelper helper) {
		DynamicOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		registries = helper.getLevel().registryAccess();
		n = 1;
		List<String> unbuilt = new ArrayList<>();
		List<String> wrong = new ArrayList<>();
		int checked = 0;
		List<Class<?>> owners = new ArrayList<>(modClasses());
		owners.sort(java.util.Comparator.comparing(Class::getName));
		for (Class<?> owner : owners) {
			Field[] fields;
			try {
				fields = owner.getDeclaredFields();
			} catch (Throwable e) {
				continue; // a class the server can't look at
			}
			for (Field f : fields) {
				if (!Modifier.isStatic(f.getModifiers()) || !Codec.class.isAssignableFrom(f.getType())
					|| !(f.getGenericType() instanceof ParameterizedType p) || !(p.getActualTypeArguments()[0] instanceof Class<?> kind)
					|| !kind.isRecord()) {
					continue;
				}
				String name = owner.getName().substring(P.length()) + "." + f.getName();
				if (LOSSY.containsKey(name)) {
					continue;
				}
				Codec<Object> codec;
				try {
					f.setAccessible(true);
					@SuppressWarnings("unchecked")
					Codec<Object> c = (Codec<Object>) f.get(null);
					codec = c;
				} catch (Throwable e) {
					continue;
				}
				if (codec == null) {
					continue;
				}
				Object value;
				try {
					value = make(kind, name);
				} catch (Unbuildable e) {
					unbuilt.add(e.getMessage());
					continue;
				}
				DataResult<Tag> written = codec.encodeStart(ops, value);
				if (written.error().isPresent()) {
					unbuilt.add(name + " won't write the made value " + value + ": " + written.error().get().message());
					continue;
				}
				DataResult<Object> read = codec.parse(ops, written.getOrThrow());
				checked++;
				if (read.error().isPresent()) {
					wrong.add(name + ": its own save doesn't read back: " + read.error().get().message() + " (saved " + written.getOrThrow() + ")");
					continue;
				}
				String diff = diff(value, read.getOrThrow(), name);
				if (diff != null) {
					wrong.add(diff);
				}
			}
		}
		helper.assertTrue(checked >= 100, "only " + checked + " saved records with a codec found among the mod's classes");
		helper.assertTrue(unbuilt.isEmpty(), unbuilt.size() + " saved records couldn't be made with a value in every part: " + unbuilt);
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " of " + checked + " saved records changed over a save: " + wrong);
		helper.succeed();
	}

	private static final String P = "io.github.jcondedata.aliveworkplace.";

	/**
	 * Codecs that on purpose keep only some parts of their record (codec field to why). Empty until a run shows one; each
	 * entry is checked against the code before it goes here.
	 */
	private static final Map<String, String> LOSSY = new LinkedHashMap<>();

	static {
		LOSSY.put("people.Tonics$Ingredient.CODEC", "an ingredient is an item or a tag, never both: one string, '#' for a tag");
	}

	/**
	 * The realm-wide effects (RealmEdicts, ...) that are saved by their type alone ({@code MapCodec.unit}, no jobs to
	 * name): they hold nothing to lose, so the generator doesn't pick them for an effect.
	 */
	private static final Set<String> NOTHING_SAVED = Set.of("CommonBoard", "FestivalTogether", "NoFeuds", "NoonPost", "ReliefReach");

	private static List<Class<?>> classes;

	/** The mod's records that implement {@code kind}, by name. */
	private static List<Class<?>> implementations(Class<?> kind) {
		if (classes == null) {
			classes = modClasses();
		}
		return classes.stream().filter(c -> c.isRecord() && kind.isAssignableFrom(c) && !NOTHING_SAVED.contains(c.getSimpleName())).sorted(java.util.Comparator.comparing(Class::getName)).toList();
	}

	/** Every class of the mod's own code (not compat, mixin or the client), loaded without running it. */
	static List<Class<?>> modClasses() {
		List<Class<?>> found = new ArrayList<>();
		try {
			java.nio.file.Path root = java.nio.file.Path.of(io.github.jcondedata.aliveworkplace.AliveWorkplace.class.getProtectionDomain()
				.getCodeSource().getLocation().toURI());
			java.nio.file.FileSystem jar = java.nio.file.Files.isDirectory(root) ? null : java.nio.file.FileSystems.newFileSystem(root);
			java.nio.file.Path base = jar == null ? root : jar.getPath("/");
			java.nio.file.Path pkg = base.resolve(P.replace('.', '/'));
			try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(pkg)) {
				for (java.nio.file.Path f : (Iterable<java.nio.file.Path>) files::iterator) {
					String rel = pkg.relativize(f).toString().replace('\\', '/');
					if (!rel.endsWith(".class") || rel.startsWith("compat/") || rel.startsWith("mixin/") || rel.contains("/client/")
						|| rel.startsWith("client/") || rel.startsWith("platform/fabric/")) {
						continue;
					}
					try {
						found.add(Class.forName(P + rel.substring(0, rel.length() - ".class".length()).replace('/', '.'), false,
							QaEveryRecordField1101GameTests.class.getClassLoader()));
					} catch (Throwable e) {
						// a class the server can't load (client-only): nothing saved there
					}
				}
			}
			if (jar != null) {
				jar.close();
			}
		} catch (Exception e) {
			throw new GameTestAssertException("couldn't look through the mod's classes: " + e);
		}
		return found;
	}

	// ---- making values ---------------------------------------------------------------------------------------------

	static final class Unbuildable extends Exception {
		Unbuildable(String message) {
			super(message);
		}
	}

	/** True when the type is (or holds, in a list, set, map or optional) one of our records. */
	private static boolean holdsARecord(Type type) {
		if (type instanceof Class<?> c) {
			return c.isRecord();
		}
		if (type instanceof ParameterizedType p) {
			for (Type arg : p.getActualTypeArguments()) {
				if (holdsARecord(arg)) {
					return true;
				}
			}
		}
		return false;
	}

	/** The world's registries, for the parts that are registry entries (a banner pattern). */
	net.minecraft.core.RegistryAccess registries;

	Object make(Type type, String path) throws Unbuildable {
		for (Map.Entry<String, Object> o : OVERRIDES.entrySet()) {
			if (path.endsWith(o.getKey())) {
				return o.getValue();
			}
		}
		if (type instanceof WildcardType w) {
			return make(w.getUpperBounds()[0], path);
		}
		if (type instanceof ParameterizedType p) {
			Class<?> raw = (Class<?>) p.getRawType();
			Type[] args = p.getActualTypeArguments();
			// A record that holds itself (a tree): two levels of entries, then empty ones.
			boolean deep = path.chars().filter(ch -> ch == '[' || ch == '{').count() + (path.split("\\.get", -1).length - 1) >= 3;
			if (deep && (raw == List.class || raw == Collection.class || raw == Iterable.class)) {
				return new ArrayList<>();
			}
			if (deep && raw == Set.class) {
				return new LinkedHashSet<>();
			}
			if (deep && raw == Map.class) {
				return new LinkedHashMap<>();
			}
			if (deep && raw == Optional.class) {
				return Optional.empty();
			}
			if (raw == List.class || raw == Collection.class || raw == Iterable.class) {
				List<Object> list = new ArrayList<>();
				list.add(make(args[0], path + "[0]"));
				list.add(make(args[0], path + "[1]"));
				return list;
			}
			if (raw == Set.class) {
				Set<Object> set = new LinkedHashSet<>();
				set.add(make(args[0], path + "{0}"));
				set.add(make(args[0], path + "{1}"));
				return set;
			}
			if (raw == Map.class) {
				Map<Object, Object> map = new LinkedHashMap<>();
				map.put(make(args[0], path + ".key"), make(args[1], path + ".value"));
				return map;
			}
			if (raw == Optional.class) {
				return Optional.of(make(args[0], path + ".get"));
			}
			if (raw == net.minecraft.tags.TagKey.class && args[0] == Item.class) {
				return net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "qa_" + n++));
			}
			if (raw == net.minecraft.tags.TagKey.class && args[0] == Block.class) {
				return net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", "qa_" + n++));
			}
			if (raw == net.minecraft.core.Holder.class && args[0] == net.minecraft.world.level.block.entity.BannerPattern.class && registries != null) {
				List<? extends net.minecraft.core.Holder<?>> patterns = registries.lookupOrThrow(net.minecraft.core.registries.Registries.BANNER_PATTERN)
					.listElements().toList();
				return patterns.get(Math.min(patterns.size() - 1, 3 + n++ % 5));
			}
			if (raw == ResourceKey.class) {
				return Level.NETHER;
			}
			throw new Unbuildable(path + " is a " + p + ", which this test can't make");
		}
		if (type instanceof GenericArrayType) {
			throw new Unbuildable(path + " is an array " + type);
		}
		Class<?> c = (Class<?>) type;
		int k = n++;
		if (c == int.class || c == Integer.class) {
			return 2 + k % 7; // small: many parts only take 1 to 10 (a research level, a reform's count)
		}
		if (c == long.class || c == Long.class) {
			return 5_000_000_000L + k;
		}
		if (c == boolean.class || c == Boolean.class) {
			return Boolean.TRUE;
		}
		if (c == double.class || c == Double.class) {
			return 0.25 + (k % 3) * 0.25; // inside the 0 to 1 many factors take
		}
		if (c == float.class || c == Float.class) {
			return 0.25f + (k % 3) * 0.25f;
		}
		if (c == short.class || c == Short.class) {
			return (short) (10 + k);
		}
		if (c == byte.class || c == Byte.class) {
			return (byte) (3 + k % 100);
		}
		if (c == String.class) {
			// A part called "item" is an item's id (a reform step's): a made-up id writes but rightly won't read.
			return path.endsWith(".item") ? "minecraft:emerald" : "qa_" + k;
		}
		if (c == UUID.class) {
			return new UUID(0x1234L + k, 0x5678L + k);
		}
		if (c == java.util.BitSet.class) {
			java.util.BitSet bits = new java.util.BitSet();
			bits.set(k % 50);
			bits.set(k % 50 + 7);
			return bits;
		}
		if (c == net.minecraft.core.Vec3i.class) {
			return new net.minecraft.core.Vec3i(3 + k % 20, 4 + k % 20, 5 + k % 20);
		}
		if (c == BlockPos.class) {
			return new BlockPos(k, 64 + k % 100, -k);
		}
		if (c == GlobalPos.class) {
			return GlobalPos.of(Level.NETHER, new BlockPos(k, 70, k));
		}
		if (c == BoundingBox.class) {
			return new BoundingBox(k, 60, -k, k + 3, 63, -k + 4);
		}
		if (c == ResourceLocation.class) {
			return ResourceLocation.fromNamespaceAndPath("aliveworkplace", "qa_" + k);
		}
		if (c == net.minecraft.network.chat.Component.class) {
			return net.minecraft.network.chat.Component.literal("qa line " + k);
		}
		if (c == CompoundTag.class) {
			CompoundTag tag = new CompoundTag();
			tag.putString("Species", "qa_" + k);
			tag.putInt("Level", 10 + k);
			return tag;
		}
		if (c == Item.class) {
			return Items.EMERALD;
		}
		if (c == Block.class) {
			return Blocks.OAK_PLANKS;
		}
		if (c == ItemStack.class) {
			return new ItemStack(Items.EMERALD, 3 + k % 30);
		}
		if (c.isEnum()) {
			Object[] constants = c.getEnumConstants();
			return constants[Math.min(1, constants.length - 1)]; // not the first, which is often the default
		}
		if (c.isInterface() && !implementations(c).isEmpty()) {
			// One of the mod's records that is this kind of thing (an effect of a reform or research topic), a new one each time.
			List<Class<?>> impls = implementations(c);
			Class<?> impl = impls.get(k % impls.size());
			return make(impl, path + "<" + impl.getSimpleName() + ">");
		}
		if (c.isRecord()) {
			RecordComponent[] parts = c.getRecordComponents();
			Class<?>[] kinds = new Class<?>[parts.length];
			Object[] values = new Object[parts.length];
			for (int i = 0; i < parts.length; i++) {
				kinds[i] = parts[i].getType();
				values[i] = make(parts[i].getGenericType(), path + "." + parts[i].getName());
			}
			try {
				Constructor<?> ctor = c.getDeclaredConstructor(kinds);
				ctor.setAccessible(true);
				return ctor.newInstance(values);
			} catch (InvocationTargetException e) {
				throw new Unbuildable(path + " (" + c.getSimpleName() + ") refused the values: " + e.getCause());
			} catch (ReflectiveOperationException e) {
				throw new Unbuildable(path + " (" + c.getSimpleName() + "): " + e);
			}
		}
		throw new Unbuildable(path + " is a " + c.getName() + ", which this test can't make");
	}

	// ---- comparing -------------------------------------------------------------------------------------------------

	/** Null when the two are the same in every part; else the first part that differs and how. */
	static String diff(Object wrote, Object read, String path) {
		if (wrote == null || read == null) {
			return wrote == read ? null : path + ": saved " + wrote + ", read " + read;
		}
		if (wrote instanceof ItemStack a && read instanceof ItemStack b) {
			return ItemStack.matches(a, b) ? null : path + ": saved " + a + ", read " + b;
		}
		if (wrote instanceof Record && wrote.getClass() == read.getClass()) {
			for (RecordComponent part : wrote.getClass().getRecordComponents()) {
				try {
					part.getAccessor().setAccessible(true);
					String d = diff(part.getAccessor().invoke(wrote), part.getAccessor().invoke(read), path + "." + part.getName());
					if (d != null) {
						return d;
					}
				} catch (ReflectiveOperationException e) {
					return path + "." + part.getName() + ": can't read (" + e + ")";
				}
			}
			return null;
		}
		if (wrote instanceof Optional<?> a && read instanceof Optional<?> b) {
			return diff(a.orElse(null), b.orElse(null), path + ".get");
		}
		if (wrote instanceof Map<?, ?> a && read instanceof Map<?, ?> b) {
			if (a.size() != b.size()) {
				return path + ": saved " + a.size() + " entries, read " + b.size() + " (" + b + ")";
			}
			for (Map.Entry<?, ?> e : a.entrySet()) {
				if (!b.containsKey(e.getKey())) {
					return path + ": key " + e.getKey() + " lost (read " + b.keySet() + ")";
				}
				String d = diff(e.getValue(), b.get(e.getKey()), path + "[" + e.getKey() + "]");
				if (d != null) {
					return d;
				}
			}
			return null;
		}
		if (wrote instanceof Set<?> a && read instanceof Set<?> b) {
			if (a.size() != b.size()) {
				return path + ": saved " + a.size() + " entries, read " + b.size() + " (" + b + ")";
			}
			for (Object x : a) {
				boolean found = false;
				for (Object y : b) {
					if (diff(x, y, path) == null) {
						found = true;
						break;
					}
				}
				if (!found) {
					return path + ": " + x + " lost (read " + b + ")";
				}
			}
			return null;
		}
		if (wrote instanceof Collection<?> a && read instanceof Collection<?> b) {
			if (a.size() != b.size()) {
				return path + ": saved " + a.size() + " entries, read " + b.size() + " (" + b + ")";
			}
			Iterator<?> ia = a.iterator();
			Iterator<?> ib = b.iterator();
			for (int i = 0; ia.hasNext(); i++) {
				String d = diff(ia.next(), ib.next(), path + "[" + i + "]");
				if (d != null) {
					return d;
				}
			}
			return null;
		}
		return wrote.equals(read) ? null : path + ": saved " + wrote + ", read " + read;
	}

	private static Villager reload(GameTestHelper helper, Villager villager) {
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(villager.save(tag), "the villager wasn't saved");
		Entity loaded = EntityType.loadEntityRecursive(tag, helper.getLevel(), e -> e);
		helper.assertTrue(loaded instanceof Villager && loaded != villager, "the save didn't load as a new villager: " + loaded);
		return (Villager) loaded;
	}
}
