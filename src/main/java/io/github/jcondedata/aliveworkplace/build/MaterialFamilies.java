package io.github.jcondedata.aliveworkplace.build;

import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

/**
 * Blocks that are free to turn into each other, so a builder can use any of them. Chipped's
 * workbenches and Rechiseled's chisel convert a block into any of its variants at no cost
 * (herringbone oak planks ⇄ oak planks), so a builder who needs a variant takes the plain block — or
 * any other variant — from the chest and converts it on the spot. Families come from the data those
 * mods ship (Chipped's item tags such as {@code #chipped:oak_planks}, Rechiseled's chiseling
 * recipes), so this works for whatever version is installed, and does nothing when they are absent.
 */
public final class MaterialFamilies {
	/** Tag namespaces whose item tags are free-conversion families. */
	private static final Set<String> FAMILY_TAG_NAMESPACES = Set.of("chipped");

	private static volatile Map<Item, List<Item>> families;
	/** Families read from data files (Rechiseled), see {@link ChiselingFamilies}. */
	private static volatile List<List<Item>> dataFamilies = List.of();

	public static void init() {
		Platform.get().onTagsLoaded(() -> families = null);
		ChiselingFamilies.init();
	}

	static void setDataFamilies(List<List<Item>> loaded) {
		dataFamilies = List.copyOf(loaded);
		families = null;
	}

	/** The items a builder accepts in place of {@code item}: the item itself first, then its family. */
	public static List<Item> accepted(Item item) {
		List<Item> family = map().get(item);
		if (family == null) {
			return List.of(item);
		}
		List<Item> out = new ArrayList<>(family.size());
		out.add(item);
		for (Item other : family) {
			if (other != item) {
				out.add(other);
			}
		}
		return out;
	}

	/**
	 * The item that stands for the whole family in "missing materials" messages: the plain vanilla block
	 * when the family has one (so "needs 12 Oak Planks" rather than a variant name).
	 */
	public static Item key(Item item) {
		List<Item> family = map().get(item);
		return family == null ? item : family.get(0);
	}

	private static Map<Item, List<Item>> map() {
		Map<Item, List<Item>> m = families;
		if (m == null) {
			m = build();
			families = m;
		}
		return m;
	}

	private static Map<Item, List<Item>> build() {
		Map<Item, List<Item>> out = new HashMap<>();
		BuiltInRegistries.ITEM.getTags().forEach(pair -> {
			if (!FAMILY_TAG_NAMESPACES.contains(pair.getFirst().location().getNamespace())) {
				return;
			}
			List<Item> members = new ArrayList<>();
			for (Holder<Item> holder : pair.getSecond()) {
				members.add(holder.value());
			}
			if (members.size() < 2) {
				return;
			}
			addFamily(out, members);
		});
		for (List<Item> members : dataFamilies) {
			addFamily(out, new ArrayList<>(members));
		}
		return out;
	}

	private static void addFamily(Map<Item, List<Item>> out, List<Item> members) {
		// Vanilla block first: it is what players have and what messages should name.
		members.sort((a, b) -> Boolean.compare(!isVanilla(a), !isVanilla(b)));
		List<Item> family = List.copyOf(members);
		for (Item item : family) {
			out.putIfAbsent(item, family);
		}
	}

	private static boolean isVanilla(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("minecraft");
	}

	private MaterialFamilies() {
	}
}
