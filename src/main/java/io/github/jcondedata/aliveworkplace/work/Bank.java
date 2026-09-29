package io.github.jcondedata.aliveworkplace.work;

import net.minecraft.server.level.ServerPlayer;

/**
 * A money mod's bank (CobbleDollars in the Cobbleverse pack): when one fills this in, {@link Money} pays and charges in
 * its money instead of emeralds. Filled in by {@code compat/cobbledollars}.
 */
public interface Bank {
	Extension<Bank> EXTENSION = new Extension<>("CobbleDollars money");

	long balance(ServerPlayer player);

	void add(ServerPlayer player, long amount);

	/** Takes {@code amount}; false (and nothing taken) if the player hasn't got it. */
	boolean take(ServerPlayer player, long amount);
}
