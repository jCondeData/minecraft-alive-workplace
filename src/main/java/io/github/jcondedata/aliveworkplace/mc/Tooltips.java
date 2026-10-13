package io.github.jcondedata.aliveworkplace.mc;

import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * What an item's tooltip shows besides its name and lore. Change: 1.21.5 replaces HIDE_ADDITIONAL_TOOLTIP and the
 * per-component tooltip flags with one TOOLTIP_DISPLAY component.
 */
public final class Tooltips {
	/**
	 * Only the name and lore: hides the item's own extra lines and its attribute lines ("When in Main Hand: 6 Attack
	 * Damage"), which HIDE_ADDITIONAL_TOOLTIP alone leaves on a sword in 1.21.1. For menu buttons that use an item as
	 * their picture.
	 */
	public static ItemStack nameAndLoreOnly(ItemStack stack) {
		stack.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
		stack.set(DataComponents.ATTRIBUTE_MODIFIERS,
			stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).withTooltip(false));
		return stack;
	}

	/** Whether {@code stack} is a menu button's picture, made by {@link #nameAndLoreOnly}: its tooltip is only its name and lore. */
	public static boolean isPicture(ItemStack stack) {
		return stack.has(DataComponents.HIDE_ADDITIONAL_TOOLTIP);
	}

	/** The widest a menu button's tooltip line is drawn, in GUI pixels: longer ones wrap (a chest screen is 176 wide). */
	public static final int BUTTON_WIDTH = 200;

	/**
	 * The mouse height to hand the game's tooltip placer for a menu button whose slot's top is at {@code slotTop} (screen
	 * GUI pixels), so its tooltip opens under the button's row instead of over it (B141: the Great Library's Scholarship
	 * tooltip covered the row of topics it was part of). The placer draws a tooltip 12 above the mouse, its frame 4 above
	 * that; a slot is 16 high and its frame 1 more.
	 */
	public static int belowSlot(int slotTop) {
		return slotTop + 16 + 1 + 1 + 4 + 12;
	}

	/** The top of the frame the game draws round a tooltip placed for mouse height {@code mouseY} (when it fits on screen). */
	public static int frameTop(int mouseY) {
		return mouseY - 12 - 4;
	}

	private Tooltips() {
	}
}
