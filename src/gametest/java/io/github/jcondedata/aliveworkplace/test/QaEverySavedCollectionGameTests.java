package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.platform.Attachment;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane E, saves, 2026-10-27): the companion of {@link QaEverySavedValueGameTests} for collections. Every list, set
 * and map the mod saves on a villager whose parts are plain values (each {@code Attachment<List<X>>},
 * {@code Set<X>} and {@code Map<K, V>} in ModAttachments with X, K and V an Integer, Long, String, Boolean, Double,
 * UUID, BlockPos, ResourceLocation, Item or Component, found by reflection, so one added later is covered too) comes
 * back after the villager is saved and loaded as a chunk does: the same entries, in the same order for a list. Each
 * gets its own entries, so two fields sharing a save name, or one not saved at all, fail here.
 */
public class QaEverySavedCollectionGameTests implements FabricGameTest {
	private static final Item[] ITEMS = {Items.APPLE, Items.BREAD, Items.CARROT, Items.DIAMOND, Items.EMERALD, Items.FEATHER, Items.GOLD_INGOT,
		Items.HONEYCOMB, Items.IRON_INGOT, Items.LEATHER, Items.MELON_SLICE, Items.OAK_LOG, Items.PAPER, Items.QUARTZ, Items.REDSTONE};

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everySavedListSetAndMapOfPlainValuesComesBackAfterASave(GameTestHelper helper) throws Exception {
		Villager v = EntityType.VILLAGER.create(helper.getLevel());
		v.setPos(helper.absoluteVec(new Vec3(1.5, 2, 1.5)));
		Map<String, Object> wrote = new LinkedHashMap<>();
		Map<String, Attachment<Object>> attachments = new LinkedHashMap<>();
		int n = 0;
		for (Field f : ModAttachments.class.getFields()) {
			if (!Modifier.isStatic(f.getModifiers()) || !(f.get(null) instanceof Attachment<?>)) {
				continue;
			}
			if (!(f.getGenericType() instanceof ParameterizedType p) || !(p.getActualTypeArguments()[0] instanceof ParameterizedType inner)
				|| !(inner.getRawType() instanceof Class<?> raw)) {
				continue;
			}
			Type[] args = inner.getActualTypeArguments();
			n++;
			Object value = null;
			if (raw == List.class && plain(args[0])) {
				value = List.of(element(args[0], n, 0), element(args[0], n, 1));
			} else if (raw == Set.class && plain(args[0])) {
				value = new LinkedHashSet<>(List.of(element(args[0], n, 0), element(args[0], n, 1)));
			} else if (raw == Map.class && plain(args[0]) && plain(args[1])) {
				Map<Object, Object> map = new LinkedHashMap<>();
				map.put(element(args[0], n, 0), element(args[1], n, 2));
				map.put(element(args[0], n, 1), element(args[1], n, 3));
				value = map;
			}
			if (value == null) {
				continue;
			}
			@SuppressWarnings("unchecked")
			Attachment<Object> a = (Attachment<Object>) f.get(null);
			a.set(v, value);
			wrote.put(f.getName(), value);
			attachments.put(f.getName(), a);
		}
		helper.assertTrue(wrote.size() >= 15, "only " + wrote.size() + " saved collections of plain values found in ModAttachments: " + wrote.keySet());

		CompoundTag tag = new CompoundTag();
		helper.assertTrue(v.save(tag), "the villager wasn't saved");
		Entity loaded = EntityType.loadEntityRecursive(tag, helper.getLevel(), e -> e);
		helper.assertTrue(loaded instanceof Villager && loaded != v, "the save didn't load as a new villager: " + loaded);
		List<String> wrong = new ArrayList<>();
		for (Map.Entry<String, Object> e : wrote.entrySet()) {
			Object read = attachments.get(e.getKey()).get(loaded);
			if (!same(e.getValue(), read)) {
				wrong.add(e.getKey() + " saved " + e.getValue() + ", read " + read);
			}
		}
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " of " + wrote.size() + " collections changed over a save: " + wrong);
		helper.succeed();
	}

	private static boolean plain(Type t) {
		return t == Integer.class || t == Long.class || t == String.class || t == Boolean.class || t == Double.class || t == UUID.class
			|| t == BlockPos.class || t == ResourceLocation.class || t == Item.class || t == Component.class;
	}

	/** The {@code i}th value of kind {@code t} for field number {@code n}: distinct across fields and within one. */
	private static Object element(Type t, int n, int i) {
		int k = n * 4 + i;
		if (t == Integer.class) {
			return 100 + k;
		} else if (t == Long.class) {
			return 7_000_000_000L + k;
		} else if (t == String.class) {
			return "qa_" + k;
		} else if (t == Boolean.class) {
			return i % 2 == 0;
		} else if (t == Double.class) {
			return 0.25 * k;
		} else if (t == UUID.class) {
			return new UUID(0x5A5AL, k);
		} else if (t == BlockPos.class) {
			return new BlockPos(k, 60 + i, -k);
		} else if (t == ResourceLocation.class) {
			return ResourceLocation.fromNamespaceAndPath("aliveworkplace", "qa_" + k);
		} else if (t == Item.class) {
			return ITEMS[k % ITEMS.length];
		} else {
			return Component.literal("QA line " + k);
		}
	}

	/** Equal, with components compared by what they read (a component may come back as an equal one of another class). */
	private static boolean same(Object a, Object b) {
		return a.equals(b) || a.toString().equals(String.valueOf(b)) || text(a).equals(text(b));
	}

	private static String text(Object o) {
		if (o instanceof List<?> l) {
			return l.stream().map(QaEverySavedCollectionGameTests::text).toList().toString();
		}
		if (o instanceof Map<?, ?> m) {
			StringBuilder s = new StringBuilder();
			m.forEach((k, val) -> s.append(text(k)).append('=').append(text(val)).append(';'));
			return s.toString();
		}
		return o instanceof Component c ? c.getString() : String.valueOf(o);
	}
}
