package io.github.jcondedata.aliveworkplace.work;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Vanilla's trade display (a player stands by: the villager shows wares in its main hand, then empties the hand) treats
 * that hand as its own. Our workers keep their real tools there (the Farmer's hoe, the Shepherd's shears, the Fisher's
 * rod, the Miner's pickaxe, a conscript's sword, …), so the display wiped them (B766, and B324 and the fisher's rod
 * before it). The rule now: the display only ever touches an empty hand or a ware it put there itself, which it marks
 * as it shows it. Whatever else is in the hand belongs to the worker and stays.
 *
 * <p>The other side of that rule (B776): a ware is the display's, never the worker's. A worker that takes, bags, stores
 * or uses whatever is in its hand reads the hand through {@link #workHand}, which lets a ware go first, so a shown
 * emerald, bread or rod is never pocketed, put in a chest or worn down as a tool.
 */
public final class TradeDisplay {
	private static final String MARK = "aliveworkplace_trade_display";

	private TradeDisplay() {
	}

	/** Marks {@code ware} (vanilla's own copy of an offer's result, about to be shown) as the display's. */
	public static void mark(ItemStack ware) {
		if (!ware.isEmpty()) {
			CustomData.update(DataComponents.CUSTOM_DATA, ware, tag -> tag.putBoolean(MARK, true));
		}
	}

	/** Whether {@code stack} is a ware the trade display put in a hand. */
	public static boolean isWare(ItemStack stack) {
		return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).contains(MARK);
	}

	/** Whether the trade display may show a ware in, or empty, {@code villager}'s main hand: nothing of theirs is there. */
	public static boolean mayUseHand(Villager villager) {
		ItemStack held = villager.getItemBySlot(EquipmentSlot.MAINHAND);
		return held.isEmpty() || isWare(held);
	}

	/** {@code entity}'s main-hand item if it is their own; a ware the display showed there counts as empty (left in place). */
	public static ItemStack ownHand(LivingEntity entity) {
		ItemStack held = entity.getItemBySlot(EquipmentSlot.MAINHAND);
		return isWare(held) ? ItemStack.EMPTY : held;
	}

	/**
	 * {@code entity}'s own main-hand item, for a worker about to take, bag, store or use it: a ware the display left
	 * there (still on show as work starts, or kept through a save and load) goes first, and the hand is then empty.
	 */
	public static ItemStack workHand(LivingEntity entity) {
		ItemStack held = entity.getItemBySlot(EquipmentSlot.MAINHAND);
		if (isWare(held)) {
			entity.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			return ItemStack.EMPTY;
		}
		return held;
	}
}
