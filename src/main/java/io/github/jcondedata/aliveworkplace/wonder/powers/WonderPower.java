package io.github.jcondedata.aliveworkplace.wonder.powers;

import net.minecraft.network.chat.Component;

/**
 * One power of a finished Wonder (35.8), read from its data file's {@code powers} by the reader its type registered
 * with ({@link WonderPowers#register}). The rest of the mod asks {@code Wonders.power(level, hall, type)} (or
 * {@code Wonders.powers} for every Wonder that grants it), which only answers while the Wonder is finished, standing
 * ({@code wonderStanding}) and Wonders are on. Each Wonder's own item (35.10 on) adds its type's class here and reads
 * its numbers where the power acts.
 */
public interface WonderPower {
	/** The type's id, as the file names it ({@code "mood"}). */
	String type();

	/**
	 * Whether the power reaches the Wonder's whole realm (its realm's villages, M33; with realms off, its caravan partners)
	 * and not only its own village.
	 */
	default boolean realm() {
		return false;
	}

	/** The line the hall's Wonders page shows for it: {@code wonder.aliveworkplace.power.<type>} unless the power says more. */
	default Component describe() {
		return Component.translatable("wonder.aliveworkplace.power." + type());
	}
}
