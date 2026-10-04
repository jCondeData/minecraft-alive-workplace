package io.github.jcondedata.aliveworkplace.legend;

import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** How many of a Legend there may be: one per village, one per world, or as many as the villages' ranks allow (29.3). */
public enum Rarity {
	RARE(ChatFormatting.AQUA), LEGENDARY(ChatFormatting.GOLD), MYTHIC(ChatFormatting.LIGHT_PURPLE);

	public final ChatFormatting color;

	Rarity(ChatFormatting color) {
		this.color = color;
	}

	public String key() {
		return name().toLowerCase(Locale.ROOT);
	}

	public Component title() {
		return Component.translatable("legend.aliveworkplace.rarity." + key()).withStyle(color);
	}

	static Rarity parse(String s) {
		try {
			return valueOf(s.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("unknown rarity '" + s + "'");
		}
	}
}
