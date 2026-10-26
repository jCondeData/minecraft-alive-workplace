package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * QA (lane E, saves, 2026-10-26): every data component the mod puts on an item (a blueprint's build, a patrol map's
 * route, a colony charter's hall, a luxury's making day, a wrapped gift...) comes back the same after the item is saved
 * (a chest, a player's inventory, a dropped item: all go through {@link ItemStack#CODEC}) and after the server sends it
 * to a player ({@link ItemStack#STREAM_CODEC}: what the client's tooltip and screen read). A component with no save
 * codec silently loses its data at the next restart, and a send codec that leaves a field out shows the player
 * something else than the server has. Values are made from each component's record type with a fixed seed (empty,
 * small, odd and unicode values; optional fields both ways), 40 per component.
 */
public class QaEveryComponentGameTests implements FabricGameTest {
	/** Components the mod keeps fresh while the item is held and on purpose never saves. */
	private static final List<String> NEVER_SAVED = List.of("supply_report");
	private static final int SAMPLES = 40;

	private record Kind(String name, DataComponentType<Object> type, Type valueType) {
	}

	@SuppressWarnings("unchecked")
	private static List<Kind> kinds() {
		List<Kind> kinds = new ArrayList<>();
		for (Field field : ModComponents.class.getDeclaredFields()) {
			if (!Modifier.isStatic(field.getModifiers()) || !DataComponentType.class.isAssignableFrom(field.getType())) {
				continue;
			}
			try {
				DataComponentType<Object> type = (DataComponentType<Object>) field.get(null);
				Type value = ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
				String name = net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type).getPath();
				kinds.add(new Kind(name, type, value));
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
		}
		return kinds;
	}

	private static final String[] STRINGS = {"", "a", "Old Mill", "Ünïcødé ✓ 名前", "x".repeat(60), "with \"quotes\" and \\"};
	private static final int[] INTS = {0, 1, 2, 3, 5, 16, 64, -1, -64, 1000};
	private static final Item[] ITEMS = {Items.STONE, Items.OAK_LOG, Items.BREAD, Items.IRON_INGOT, Items.WHITE_WOOL, Items.EMERALD};

	/** A value of {@code type} made from {@code random}; throws for a type it doesn't know how to make. */
	static Object sample(Type type, Random random, Level level, int depth) {
		if (type instanceof ParameterizedType p) {
			Class<?> raw = (Class<?>) p.getRawType();
			Type[] args = p.getActualTypeArguments();
			if (raw == Optional.class) {
				return random.nextBoolean() ? Optional.empty() : Optional.of(sample(args[0], random, level, depth + 1));
			}
			if (raw == List.class) {
				List<Object> list = new ArrayList<>();
				int n = depth > 2 ? random.nextInt(2) : random.nextInt(4);
				for (int i = 0; i < n; i++) {
					list.add(sample(args[0], random, level, depth + 1));
				}
				return List.copyOf(list);
			}
			if (raw == Map.class) {
				Map<Object, Object> map = new LinkedHashMap<>();
				int n = random.nextInt(3);
				for (int i = 0; i < n; i++) {
					map.put(sample(args[0], random, level, depth + 1), sample(args[1], random, level, depth + 1));
				}
				return Map.copyOf(map);
			}
			throw new IllegalArgumentException("no sample for " + type);
		}
		Class<?> c = (Class<?>) type;
		if (c == int.class || c == Integer.class) {
			return INTS[random.nextInt(INTS.length)];
		}
		if (c == float.class || c == Float.class) {
			return new float[] {0f, 0.5f, 1f, 0.25f}[random.nextInt(4)];
		}
		if (c == double.class || c == Double.class) {
			return new double[] {0d, 0.5d, 1d, -3.25d}[random.nextInt(4)];
		}
		if (c == byte[].class) {
			byte[] bytes = new byte[random.nextInt(40)];
			random.nextBytes(bytes);
			return bytes;
		}
		if (c == int[].class) {
			int[] ints = new int[random.nextInt(30)];
			for (int i = 0; i < ints.length; i++) {
				ints[i] = random.nextInt(8);
			}
			return ints;
		}
		if (c == long.class || c == Long.class) {
			return new long[] {0L, 1L, 37L, 24_000L * 9 + 5, Long.MAX_VALUE / 2}[random.nextInt(5)];
		}
		if (c == boolean.class || c == Boolean.class) {
			return random.nextBoolean();
		}
		if (c == String.class) {
			return STRINGS[random.nextInt(STRINGS.length)];
		}
		if (c == UUID.class) {
			return new UUID(random.nextLong(), random.nextLong());
		}
		if (c == BlockPos.class) {
			return new BlockPos(random.nextInt(4001) - 2000, random.nextInt(380) - 64, random.nextInt(4001) - 2000);
		}
		if (c == Vec3i.class) {
			return new Vec3i(1 + random.nextInt(40), 1 + random.nextInt(30), 1 + random.nextInt(40));
		}
		if (c == GlobalPos.class) {
			return GlobalPos.of(random.nextBoolean() ? Level.OVERWORLD : Level.NETHER, (BlockPos) sample(BlockPos.class, random, level, depth));
		}
		if (c == ResourceLocation.class) {
			return new ResourceLocation[] {ResourceLocation.withDefaultNamespace("overworld"), ResourceLocation.withDefaultNamespace("the_nether"),
				ResourceLocation.fromNamespaceAndPath("aliveworkplace", "cottage"), ResourceLocation.fromNamespaceAndPath("somepack", "a/b_c")}[random.nextInt(4)];
		}
		if (c == Component.class) {
			String s = STRINGS[random.nextInt(STRINGS.length)];
			return random.nextBoolean() ? Component.literal(s) : Component.translatable("item.minecraft.bread");
		}
		if (c == Item.class) {
			return ITEMS[random.nextInt(ITEMS.length)];
		}
		if (c == ItemStack.class) {
			ItemStack stack = new ItemStack(ITEMS[random.nextInt(ITEMS.length)], 1 + random.nextInt(16));
			if (random.nextBoolean()) {
				stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("For you"));
			}
			return stack;
		}
		if (c == BitSet.class) {
			BitSet bits = new BitSet();
			int n = random.nextInt(6);
			for (int i = 0; i < n; i++) {
				bits.set(random.nextInt(32 * 32));
			}
			return bits;
		}
		if (c.isEnum()) {
			Object[] values = c.getEnumConstants();
			return values[random.nextInt(values.length)];
		}
		if (c.isRecord()) {
			RecordComponent[] parts = c.getRecordComponents();
			Object[] args = new Object[parts.length];
			Class<?>[] types = new Class<?>[parts.length];
			for (int i = 0; i < parts.length; i++) {
				args[i] = sample(parts[i].getGenericType(), random, level, depth + 1);
				types[i] = parts[i].getType();
			}
			try {
				var constructor = c.getDeclaredConstructor(types);
				constructor.setAccessible(true);
				return constructor.newInstance(args);
			} catch (ReflectiveOperationException e) {
				throw new IllegalArgumentException("couldn't make a " + c.getSimpleName() + ": " + (e.getCause() != null ? e.getCause() : e));
			}
		}
		throw new IllegalArgumentException("no sample for " + c.getName());
	}

	private static Tag save(ItemStack stack, net.minecraft.core.HolderLookup.Provider registries) {
		return ItemStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), stack).getOrThrow();
	}

	private static ItemStack load(Tag tag, net.minecraft.core.HolderLookup.Provider registries) {
		return ItemStack.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag).getOrThrow();
	}

	private static ItemStack send(ItemStack stack, Level level) {
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
		ItemStack.STREAM_CODEC.encode(buf, stack);
		ItemStack back = ItemStack.STREAM_CODEC.decode(buf);
		if (buf.readableBytes() != 0) {
			throw new IllegalStateException(buf.readableBytes() + " bytes left unread after the item");
		}
		return back;
	}

	/** Every saved component comes back the same from a save and from the trip to a player. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaEveryComponent"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaEveryComponent")
	public void everyComponentComesBackFromASaveAndASend(GameTestHelper helper) {
		Level level = helper.getLevel();
		var registries = level.registryAccess();
		List<Kind> kinds = kinds();
		List<String> wrong = new ArrayList<>();
		int tried = 0;
		for (Kind kind : kinds) {
			if (NEVER_SAVED.contains(kind.name())) {
				continue;
			}
			if (kind.type().codec() == null) {
				wrong.add(kind.name() + " has no save codec: its data is lost when the item is saved");
				continue;
			}
			Random random = new Random(kind.name().hashCode());
			int before = wrong.size();
			for (int i = 0; i < SAMPLES && wrong.size() == before; i++) {
				Object value;
				try {
					value = sample(kind.valueType(), random, level, 0);
				} catch (IllegalArgumentException e) {
					wrong.add(kind.name() + ": the test can't make a value (teach sample() its type): " + e.getMessage());
					break;
				}
				ItemStack stack = new ItemStack(Items.PAPER, 3);
				stack.set(kind.type(), value);
				tried++;
				try {
					Tag saved = save(stack, registries);
					ItemStack loaded = load(saved, registries);
					Tag again = save(loaded, registries);
					if (!saved.equals(again)) {
						wrong.add(kind.name() + " changes in a save: " + saved + " came back as " + again);
						continue;
					}
					if (!loaded.has(kind.type())) {
						wrong.add(kind.name() + " is gone after a save: " + value);
						continue;
					}
					Tag sent = save(send(stack, level), registries);
					if (!saved.equals(sent)) {
						wrong.add(kind.name() + " reaches the player changed: " + saved + " arrived as " + sent);
					}
				} catch (RuntimeException e) {
					wrong.add(kind.name() + " with " + value + ": " + e);
				}
			}
		}
		helper.assertTrue(kinds.size() >= 18, "only " + kinds.size() + " components found in ModComponents");
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " component problems (" + tried + " items tried): " + String.join("; ", wrong));
		helper.succeed();
	}

	/**
	 * A blueprint carrying a supply report (kept fresh while held, never saved): the report is left out of the save and
	 * the blueprint's build stays, and the report still reaches the player whole.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaEveryComponent"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaEveryComponent")
	public void theSupplyReportIsSentButNeverSaved(GameTestHelper helper) {
		Level level = helper.getLevel();
		var registries = level.registryAccess();
		Random random = new Random(7);
		ItemStack stack = new ItemStack(Items.PAPER);
		DataComponentType<Object> blueprint = kinds().stream().filter(k -> k.name().equals("blueprint")).findFirst().orElseThrow().type();
		Kind report = kinds().stream().filter(k -> k.name().equals("supply_report")).findFirst().orElseThrow();
		Object build = sample(kinds().stream().filter(k -> k.name().equals("blueprint")).findFirst().orElseThrow().valueType(), random, level, 0);
		Object needs = null;
		for (int i = 0; i < 20 && (needs == null || needs.toString().contains("missing=[]")); i++) {
			needs = sample(report.valueType(), random, level, 0);
		}
		stack.set(blueprint, build);
		stack.set(report.type(), needs);
		ItemStack loaded = load(save(stack, registries), registries);
		helper.assertTrue(!loaded.has(report.type()), "the supply report was saved with the blueprint: " + loaded.get(report.type()));
		helper.assertTrue(build.equals(loaded.get(blueprint)), "the blueprint's build changed in the save: " + build + " came back as "
			+ loaded.get(blueprint));
		ItemStack sent = send(stack, level);
		helper.assertTrue(needs.equals(sent.get(report.type())), "the supply report reached the player changed: " + needs + " arrived as "
			+ sent.get(report.type()));
		helper.succeed();
	}
}
