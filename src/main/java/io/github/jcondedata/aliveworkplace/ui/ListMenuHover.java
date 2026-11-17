package io.github.jcondedata.aliveworkplace.ui;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * What the shared list menu (ROADMAP 42.1, Copperline 48.4) reads out for the entry under the mouse: the whisper line's
 * words and whether the entry's floating tooltip draws. Pure, so GameTests check it; {@code ChoiceTableScreen} calls it.
 *
 * <p>Every entry under the mouse, a rail tab too, draws its whole tooltip (B1355: 48.4 left a rail tab's tooltip out,
 * so a tab's later lines, such as the Move Tutor's "N more from a more experienced tutor" and Research's "Click: show
 * this research", could not be read anywhere; Milestone 48: nothing a screen showed may be lost). The whisper line adds
 * a short reading: a rail tab shows only its icon, so it names itself first.
 */
public final class ListMenuHover {
	/** The entry's lore lines (its tooltip below the name), or none. */
	public static List<Component> lore(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines();
	}

	/** Whether the entry under the mouse draws its floating tooltip: any shown entry does, a rail tab included. */
	public static boolean tooltip(ItemStack hovered) {
		return !hovered.isEmpty() && !hovered.has(DataComponents.HIDE_TOOLTIP);
	}

	/**
	 * The whisper line's words with {@code hovered} under the mouse ({@code null}: nothing): a rail tab ({@code tab})
	 * reads "name: first line" (just its name with no lore), a row or button its first lore line, and anything else
	 * {@code fallback} (the menu's own hint).
	 */
	public static Component whisper(ItemStack hovered, boolean tab, Component fallback) {
		if (hovered == null || hovered.isEmpty()) {
			return fallback;
		}
		List<Component> lore = lore(hovered);
		String first = lore.isEmpty() ? "" : lore.get(0).getString();
		if (tab) {
			return first.isEmpty() ? hovered.getHoverName()
				: Component.translatable("screen.aliveworkplace.list.tab_whisper", hovered.getHoverName(), first);
		}
		return first.isEmpty() ? fallback : Component.literal(first);
	}

	private ListMenuHover() {
	}
}
