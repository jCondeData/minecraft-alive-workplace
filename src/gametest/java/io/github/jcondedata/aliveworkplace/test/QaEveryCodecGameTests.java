package io.github.jcondedata.aliveworkplace.test;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * QA (lane E, saves, 2026-10-26): every codec the mod keeps in a static field (its own classes: not compat, mixin or
 * the client), read from saved data of another kind. A codec reads a village's quests, a villager's job, a Wonder's
 * stages; when it finds something it can't read it must answer with an error (the caller then keeps a default), never
 * throw: a throw escapes the load of the whole block, file or villager it is in, and Minecraft drops that whole
 * thing (B509: a Village Hall with one quest of an unknown kind lost all its data). Each codec gets words, numbers,
 * lists and compounds, and a record codec a compound with every one of its keys holding each of those (so a field
 * read with an enum's {@code valueOf} sees a name it doesn't know).
 */
public class QaEveryCodecGameTests implements FabricGameTest {
	/**
	 * Codecs that never read saved data: data-pack files only (their loaders skip a file whose read throws, with a line
	 * naming it: EdictGameTests, ReformGameTests, ResearchTrees, Guilds, Tonics, RealmResearch).
	 */
	private static final List<String> FILES_ONLY = List.of("hall.Edicts.ID_CODEC", "hall.CivicEffects.CODEC");

	private static List<Tag> odd() {
		ListTag strings = new ListTag();
		strings.add(StringTag.valueOf("x"));
		ListTag compounds = new ListTag();
		compounds.add(new CompoundTag());
		return List.of(StringTag.valueOf("x"), StringTag.valueOf(""), StringTag.valueOf("NO_SUCH_KIND"), StringTag.valueOf("Not An Id!"),
			IntTag.valueOf(-7), IntTag.valueOf(Integer.MAX_VALUE), new CompoundTag(), strings, compounds, new ListTag(),
			new IntArrayTag(new int[] {-1, 999, Integer.MIN_VALUE}));
	}

	/** Every static Codec or MapCodec field of the mod's own classes, as "Class.FIELD" to its value. */
	private static List<java.util.Map.Entry<String, Object>> codecs() {
		List<java.util.Map.Entry<String, Object>> found = new ArrayList<>();
		String p = "io.github.jcondedata.aliveworkplace.";
		try {
			java.nio.file.Path root = java.nio.file.Path.of(io.github.jcondedata.aliveworkplace.AliveWorkplace.class.getProtectionDomain()
				.getCodeSource().getLocation().toURI());
			java.nio.file.FileSystem jar = java.nio.file.Files.isDirectory(root) ? null : java.nio.file.FileSystems.newFileSystem(root);
			java.nio.file.Path pkg = (jar == null ? root : jar.getPath("/")).resolve(p.replace('.', '/'));
			try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(pkg)) {
				for (java.nio.file.Path f : (Iterable<java.nio.file.Path>) files::iterator) {
					String rel = pkg.relativize(f).toString().replace('\\', '/');
					if (!rel.endsWith(".class") || rel.startsWith("compat/") || rel.startsWith("mixin/") || rel.startsWith("platform/")
						|| rel.contains("client/") || rel.contains("Screen") || rel.contains("Renderer")) {
						continue;
					}
					String name = rel.substring(0, rel.length() - ".class".length()).replace('/', '.');
					Class<?> type;
					try {
						type = Class.forName(p + name, false, QaEveryCodecGameTests.class.getClassLoader());
						if (net.minecraft.world.level.block.Block.class.isAssignableFrom(type)) {
							continue; // a block's CODEC (Minecraft asks every block for one) reads no save
						}
						for (Field field : type.getDeclaredFields()) {
							if (Modifier.isStatic(field.getModifiers()) && (Codec.class.isAssignableFrom(field.getType())
								|| MapCodec.class.isAssignableFrom(field.getType()))) {
								field.setAccessible(true);
								Object value = field.get(null);
								if (value != null) {
									found.add(java.util.Map.entry(name + "." + field.getName(), value));
								}
							}
						}
					} catch (Throwable e) {
						// a class that can't be looked at on the server: no saved data
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

	private static String tryParse(Codec<?> codec, DynamicOps<Tag> ops, Tag input) {
		try {
			codec.parse(ops, input);
			return null;
		} catch (Throwable e) {
			return input + ": " + e;
		}
	}

	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaEveryCodec"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaEveryCodec")
	public void noCodecThrowsOnDataOfAnotherKind(GameTestHelper helper) {
		DynamicOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		List<java.util.Map.Entry<String, Object>> codecs = codecs();
		List<String> wrong = new ArrayList<>();
		int tried = 0;
		int records = 0;
		for (java.util.Map.Entry<String, Object> e : codecs) {
			if (FILES_ONLY.contains(e.getKey())) {
				continue;
			}
			Codec<?> codec = e.getValue() instanceof MapCodec<?> map ? map.codec() : (Codec<?>) e.getValue();
			List<String> keys = new ArrayList<>();
			MapCodec<?> fields = e.getValue() instanceof MapCodec<?> map ? map
				: codec instanceof MapCodec.MapCodecCodec<?> wrapped ? wrapped.codec() : null;
			if (fields != null) {
				fields.keys(ops).forEach(k -> keys.add(NbtOps.INSTANCE.getStringValue(k).result().orElse("")));
				records++;
			}
			for (Tag odd : odd()) {
				List<Tag> inputs = new ArrayList<>();
				inputs.add(odd);
				if (!keys.isEmpty()) {
					CompoundTag all = new CompoundTag();
					keys.forEach(k -> all.put(k, odd.copy()));
					inputs.add(all);
					ListTag listOfAll = new ListTag();
					listOfAll.add(all.copy());
					inputs.add(listOfAll);
				}
				for (Tag input : inputs) {
					tried++;
					String thrown = tryParse(codec, ops, input);
					if (thrown != null && wrong.size() < 30) {
						wrong.add(e.getKey() + " with " + thrown);
					}
				}
			}
		}
		helper.assertTrue(codecs.size() >= 100, "only " + codecs.size() + " codecs found among the mod's classes");
		helper.assertTrue(records >= 50, "only " + records + " record codecs found");
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " codecs that throw on data of another kind (" + tried + " tried, "
			+ codecs.size() + " codecs): " + wrong);
		helper.succeed();
	}
}
