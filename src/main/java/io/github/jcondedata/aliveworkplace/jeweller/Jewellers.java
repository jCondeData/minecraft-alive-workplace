package io.github.jcondedata.aliveworkplace.jeweller;

import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Jeweller (ROADMAP 34.12): a villager at a stonecutter, picked with a gold nugget (the stonecutter's Mason stays,
 * back with a clay ball, and its Gem Grower with an amethyst shard). What they make is data: the luxury recipes naming
 * {@code aliveworkplace:jeweller} (the Amethyst Ring, the Emerald Brooch, the Gold Circlet), made by the luxury
 * workshop engine ({@link JewellerWork}, a {@code LuxuryWork}) from the gems and metal in their own chests, the village
 * store and the other workers' chests (the gem growers' among them).
 */
public final class Jewellers {
	/** Config {@code jewellers}: off, no villager can be made a Jeweller, and Jewellers make nothing. */
	public static boolean ENABLED = true;

	public static boolean isJeweller(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == ModVillagers.JEWELLER;
	}

	/** A gold nugget: what picks the job at a stonecutter (with config {@code jewellers} on). */
	public static boolean isNugget(ItemStack stack) {
		return ENABLED && stack.is(Items.GOLD_NUGGET);
	}

	private Jewellers() {
	}
}
