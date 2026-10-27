package io.github.jcondedata.aliveworkplace.people;

import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

/**
 * What villagers have been eating: the last {@link #REMEMBERED} meals they had from the village store. When a villager
 * eats they pick something they haven't had lately if the store has it; {@link #VARIED_KINDS} or more kinds among their
 * last meals is a varied diet (their mood goes up), the same thing every time is a dull one (it goes down). So a
 * village that stocks bread, baked potatoes, cooked fish and pies eats better than one living on bread alone. Another
 * mod's dishes ({@link io.github.jcondedata.aliveworkplace.craft.CuisineDishes}, ROADMAP 38.8) count by their food
 * group: a salad, a sandwich and a curry are three kinds, three different salads one.
 */
public final class Diet {
	/** How many meals a villager remembers. */
	public static final int REMEMBERED = 5;
	/** Kinds among the remembered meals that make a varied diet. */
	public static final int VARIED_KINDS = 3;
	/** Kinds among the remembered meals that make a plain diet (fewer than {@link #VARIED_KINDS}). */
	public static final int PLAIN_KINDS = 2;
	/** Meals remembered before the diet counts one way or the other. */
	static final int ENOUGH = 3;

	public enum Kind { UNKNOWN, SAME, PLAIN, VARIED }

	/** {@code villager} had {@code meal}: remember it. */
	public static void ate(Villager villager, ItemStack meal) {
		List<ResourceLocation> recent = new ArrayList<>(ModAttachments.RECENT_MEALS.getOrElse(villager, List.of()));
		recent.add(BuiltInRegistries.ITEM.getKey(meal.getItem()));
		while (recent.size() > REMEMBERED) {
			recent.remove(0);
		}
		ModAttachments.RECENT_MEALS.set(villager, List.copyOf(recent));
	}

	/** Whether {@code meal}'s kind (the food, or a dish's group) is among the last meals {@code villager} had. */
	public static boolean hadLately(Villager villager, ItemStack meal) {
		Object kind = kind(BuiltInRegistries.ITEM.getKey(meal.getItem()));
		for (ResourceLocation id : ModAttachments.RECENT_MEALS.getOrElse(villager, List.of())) {
			if (kind(id).equals(kind)) {
				return true;
			}
		}
		return false;
	}

	/** The kind of food {@code id} is: itself, or for a dish of another mod its food group (38.8). */
	static Object kind(ResourceLocation id) {
		String group = io.github.jcondedata.aliveworkplace.craft.CuisineDishes.group(id);
		return group == null ? id : "group:" + group;
	}

	/** How {@code villager}'s diet is going. */
	public static Kind of(Villager villager) {
		List<ResourceLocation> recent = ModAttachments.RECENT_MEALS.getOrElse(villager, List.of());
		if (recent.size() < ENOUGH) {
			return Kind.UNKNOWN;
		}
		java.util.Set<Object> seen = new HashSet<>();
		recent.forEach(id -> seen.add(kind(id)));
		int kinds = seen.size();
		return kinds >= VARIED_KINDS ? Kind.VARIED : kinds >= PLAIN_KINDS ? Kind.PLAIN : Kind.SAME;
	}

	/** How many kinds of meal a diet of {@code kind} takes: 3 for varied, 2 for plain (1 for the same food, 0 unknown). */
	public static int kinds(Kind kind) {
		return switch (kind) {
			case VARIED -> VARIED_KINDS;
			case PLAIN -> PLAIN_KINDS;
			case SAME -> 1;
			case UNKNOWN -> 0;
		};
	}

	private Diet() {
	}
}
