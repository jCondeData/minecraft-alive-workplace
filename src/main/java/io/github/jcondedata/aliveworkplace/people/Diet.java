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
 * village that stocks bread, baked potatoes, cooked fish and pies eats better than one living on bread alone.
 */
public final class Diet {
	/** How many meals a villager remembers. */
	public static final int REMEMBERED = 5;
	/** Kinds among the remembered meals that make a varied diet. */
	public static final int VARIED_KINDS = 3;
	/** Meals remembered before the diet counts one way or the other. */
	static final int ENOUGH = 3;

	public enum Kind { UNKNOWN, SAME, PLAIN, VARIED }

	/** {@code villager} had {@code meal}: remember it. */
	public static void ate(Villager villager, ItemStack meal) {
		List<ResourceLocation> recent = new ArrayList<>(villager.getAttachedOrElse(ModAttachments.RECENT_MEALS, List.of()));
		recent.add(BuiltInRegistries.ITEM.getKey(meal.getItem()));
		while (recent.size() > REMEMBERED) {
			recent.remove(0);
		}
		villager.setAttached(ModAttachments.RECENT_MEALS, List.copyOf(recent));
	}

	/** Whether {@code meal} is among the last meals {@code villager} had. */
	public static boolean hadLately(Villager villager, ItemStack meal) {
		return villager.getAttachedOrElse(ModAttachments.RECENT_MEALS, List.of()).contains(BuiltInRegistries.ITEM.getKey(meal.getItem()));
	}

	/** How {@code villager}'s diet is going. */
	public static Kind of(Villager villager) {
		List<ResourceLocation> recent = villager.getAttachedOrElse(ModAttachments.RECENT_MEALS, List.of());
		if (recent.size() < ENOUGH) {
			return Kind.UNKNOWN;
		}
		int kinds = new HashSet<>(recent).size();
		return kinds >= VARIED_KINDS ? Kind.VARIED : kinds == 1 ? Kind.SAME : Kind.PLAIN;
	}

	private Diet() {
	}
}
