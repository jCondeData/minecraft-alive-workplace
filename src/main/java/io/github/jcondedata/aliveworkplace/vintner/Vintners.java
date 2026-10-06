package io.github.jcondedata.aliveworkplace.vintner;

import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Vintner (ROADMAP 34.9): a villager at a cauldron, their vat, picked with sweet berries, glow berries or an apple.
 * What they make is data: the luxury recipes naming {@code aliveworkplace:vintner} (cider, berry wine, vintage wine),
 * made by the luxury workshop engine ({@link VintnerWork}, a {@code LuxuryWork}).
 */
public final class Vintners {
	/** Config {@code vintners}: off, no villager can be made a Vintner, and Vintners make nothing. */
	public static boolean ENABLED = true;

	public static boolean isVintner(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == ModVillagers.VINTNER;
	}

	/** Sweet berries, glow berries or an apple: what picks the job at a cauldron (with config {@code vintners} on). */
	public static boolean isFruit(ItemStack stack) {
		return ENABLED && (stack.is(Items.SWEET_BERRIES) || stack.is(Items.GLOW_BERRIES) || stack.is(Items.APPLE));
	}

	private Vintners() {
	}
}
