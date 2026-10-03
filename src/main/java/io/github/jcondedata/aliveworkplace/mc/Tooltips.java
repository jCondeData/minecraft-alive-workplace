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

	private Tooltips() {
	}
}
