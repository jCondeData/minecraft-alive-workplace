package io.github.jcondedata.aliveworkplace.printer;

import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Printer (ROADMAP 34.11): a villager at a cartography table, picked with an ink sac (the table's Cartographer and
 * Netherworker stay). What they make is data: the luxury recipes naming {@code aliveworkplace:printer} (books, the
 * Village Gazette, the Illuminated Book), made by the luxury workshop engine ({@link PrinterWork}, a {@code LuxuryWork});
 * the Gazette and the Illuminated Book are written from the hall as they come off the press ({@link Gazette}).
 */
public final class Printers {
	/** Config {@code printers}: off, no villager can be made a Printer, and Printers make nothing. */
	public static boolean ENABLED = true;

	public static boolean isPrinter(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == ModVillagers.PRINTER;
	}

	/** An ink sac: what picks the job at a cartography table (with config {@code printers} on). */
	public static boolean isInk(ItemStack stack) {
		return ENABLED && stack.is(Items.INK_SAC);
	}

	private Printers() {
	}
}
