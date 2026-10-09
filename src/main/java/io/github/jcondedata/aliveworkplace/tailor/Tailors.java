package io.github.jcondedata.aliveworkplace.tailor;

import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Tailor (ROADMAP 34.10): a villager at a loom, picked with string (the loom's Shepherd stays, back with shears).
 * What they make is data: the luxury recipes naming {@code aliveworkplace:tailor} (work clothes, fine clothes, noble
 * robes), made by the luxury workshop engine ({@link TailorWork}, a {@code LuxuryWork}) from the wool in their own
 * chests, the village store and the other workers' chests (the shepherds' among them).
 */
public final class Tailors {
	/** Config {@code tailors}: off, no villager can be made a Tailor, and Tailors make nothing. */
	public static boolean ENABLED = true;

	public static boolean isTailor(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == ModVillagers.TAILOR;
	}

	/** String: what picks the job at a loom (with config {@code tailors} on). */
	public static boolean isString(ItemStack stack) {
		return ENABLED && stack.is(Items.STRING);
	}

	private Tailors() {
	}
}
