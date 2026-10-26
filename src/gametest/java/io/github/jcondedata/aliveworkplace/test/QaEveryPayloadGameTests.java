package io.github.jcondedata.aliveworkplace.test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.Level;

/**
 * QA (lane E, the whole pack, 2026-10-26): every packet the mod sends between the server and a player (the City Plan's
 * map, the table's blueprint list and uploads, a builder's status, a colony charter's map, mail...) reads back exactly
 * what was written. A packet whose reading doesn't match its writing (a field read in another order, one left out, a
 * byte left over) disconnects the player on a real server ("Packet was larger than I expected" or a decoder error),
 * and the GameTests never send one over a real connection. Each payload record is found among the mod's classes, filled
 * 40 times with values of its fields' types (fixed seed; empty and unicode strings, empty and full lists, optional
 * fields both ways), written, read and written again: the two writes must be the same bytes, with nothing left unread.
 */
public class QaEveryPayloadGameTests implements FabricGameTest {
	private static final int SAMPLES = 40;

	/** Every record of the mod's that is a payload, with its static {@code CODEC}. */
	private static List<java.util.Map.Entry<Class<?>, StreamCodec<ByteBuf, Object>>> payloads() {
		List<java.util.Map.Entry<Class<?>, StreamCodec<ByteBuf, Object>>> found = new ArrayList<>();
		String p = "io.github.jcondedata.aliveworkplace.";
		try {
			java.nio.file.Path root = java.nio.file.Path.of(io.github.jcondedata.aliveworkplace.AliveWorkplace.class.getProtectionDomain()
				.getCodeSource().getLocation().toURI());
			java.nio.file.FileSystem jar = java.nio.file.Files.isDirectory(root) ? null : java.nio.file.FileSystems.newFileSystem(root);
			java.nio.file.Path pkg = (jar == null ? root : jar.getPath("/")).resolve(p.replace('.', '/'));
			try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(pkg)) {
				for (java.nio.file.Path f : (Iterable<java.nio.file.Path>) files::iterator) {
					String rel = pkg.relativize(f).toString().replace('\\', '/');
					if (!rel.endsWith(".class") || rel.startsWith("mixin/") || rel.contains("client/")) {
						continue;
					}
					try {
						Class<?> type = Class.forName(p + rel.substring(0, rel.length() - ".class".length()).replace('/', '.'), false,
							QaEveryPayloadGameTests.class.getClassLoader());
						if (!type.isRecord() || !CustomPacketPayload.class.isAssignableFrom(type)) {
							continue;
						}
						for (Field field : type.getDeclaredFields()) {
							if (Modifier.isStatic(field.getModifiers()) && StreamCodec.class.isAssignableFrom(field.getType())
								&& (field.getName().equals("CODEC") || field.getName().equals("STREAM_CODEC"))) {
								field.setAccessible(true);
								@SuppressWarnings("unchecked")
								StreamCodec<ByteBuf, Object> codec = (StreamCodec<ByteBuf, Object>) field.get(null);
								found.add(java.util.Map.entry(type, codec));
							}
						}
					} catch (Throwable e) {
						// a class that can't be looked at on the server sends nothing
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

	private static byte[] write(StreamCodec<ByteBuf, Object> codec, Object payload, Level level) {
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
		codec.encode(buf, payload);
		return ByteBufUtil.getBytes(buf);
	}

	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaEveryPayload"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaEveryPayload")
	public void everyPacketReadsBackWhatWasWritten(GameTestHelper helper) {
		Level level = helper.getLevel();
		List<java.util.Map.Entry<Class<?>, StreamCodec<ByteBuf, Object>>> payloads = payloads();
		List<String> wrong = new ArrayList<>();
		int tried = 0;
		int refused = 0;
		for (var e : payloads) {
			String name = e.getKey().getName().substring("io.github.jcondedata.aliveworkplace.".length());
			Random random = new Random(name.hashCode());
			int before = wrong.size();
			for (int i = 0; i < SAMPLES && wrong.size() == before; i++) {
				Object payload;
				try {
					payload = QaEveryComponentGameTests.sample(e.getKey(), random, level, 0);
				} catch (IllegalArgumentException x) {
					wrong.add(name + ": the test can't make one (teach QaEveryComponentGameTests.sample its type): " + x.getMessage());
					break;
				}
				byte[] first;
				try {
					first = write(e.getValue(), payload, level);
				} catch (io.netty.handler.codec.EncoderException x) {
					refused++; // a value the packet refuses to carry (a name over its length limit): never sent
					continue;
				}
				tried++;
				try {
					RegistryFriendlyByteBuf in = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(first), level.registryAccess());
					Object read = e.getValue().decode(in);
					if (in.readableBytes() != 0) {
						wrong.add(name + ": " + in.readableBytes() + " of " + first.length + " bytes left unread from " + payload);
						continue;
					}
					byte[] again = write(e.getValue(), read, level);
					if (!java.util.Arrays.equals(first, again)) {
						wrong.add(name + " reads back as something else: " + payload + " became " + read);
					}
				} catch (RuntimeException x) {
					wrong.add(name + " with " + payload + ": " + x);
				}
			}
		}
		helper.assertTrue(tried > payloads.size() * SAMPLES * 3 / 4, "only " + tried + " packets tried, " + refused + " refused by their own limits");
		helper.assertTrue(payloads.size() >= 24, "only " + payloads.size() + " payloads found among the mod's classes");
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " packets that don't read back (" + tried + " tried, " + payloads.size()
			+ " payloads): " + String.join("; ", wrong));
		helper.succeed();
	}
}
