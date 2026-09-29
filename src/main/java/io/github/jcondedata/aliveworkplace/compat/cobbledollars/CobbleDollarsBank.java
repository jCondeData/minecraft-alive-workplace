//? if cobbledollars {
package io.github.jcondedata.aliveworkplace.compat.cobbledollars;

import fr.harmex.cobbledollars.common.utils.extensions.PlayerExtensionKt;
import java.math.BigInteger;
import net.minecraft.server.level.ServerPlayer;

// The only code that touches CobbleDollars classes (a player's balance). Call it only when CobbleDollars
// is installed; go through {@code work.Money}, which falls back to emeralds.
public final class CobbleDollarsBank {
	public static long balance(ServerPlayer player) {
		BigInteger balance = PlayerExtensionKt.getCobbleDollars(player);
		return balance == null ? 0 : balance.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
	}

	public static void add(ServerPlayer player, long amount) {
		BigInteger balance = PlayerExtensionKt.getCobbleDollars(player);
		PlayerExtensionKt.setCobbleDollars(player, (balance == null ? BigInteger.ZERO : balance).add(BigInteger.valueOf(amount)));
	}

	// Takes {@code amount} if the player has it; false (and nothing taken) otherwise.
	public static boolean take(ServerPlayer player, long amount) {
		BigInteger balance = PlayerExtensionKt.getCobbleDollars(player);
		BigInteger price = BigInteger.valueOf(amount);
		if (balance == null || balance.compareTo(price) < 0) {
			return false;
		}
		PlayerExtensionKt.setCobbleDollars(player, balance.subtract(price));
		return true;
	}

	private CobbleDollarsBank() {
	}
}
//?}
