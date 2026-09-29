//? if cobbledollars {
package io.github.jcondedata.aliveworkplace.compat.cobbledollars;

import io.github.jcondedata.aliveworkplace.work.Bank;
import net.minecraft.server.level.ServerPlayer;

// The CobbleDollars integration: its balances become the mod's {@link Bank} (prices and prizes in CobbleDollars).
public final class CobbleDollarsCompat {
	// The CobbleDollars versions this was tested with (the Cobbleverse pack's 2.0.0 Beta 5.1).
	public static final String TESTED = ">=2.0.0 <2.1";

	public static void init() {
		Bank.EXTENSION.register("cobbledollars", new Bank() {
			@Override
			public long balance(ServerPlayer player) {
				return CobbleDollarsBank.balance(player);
			}

			@Override
			public void add(ServerPlayer player, long amount) {
				CobbleDollarsBank.add(player, amount);
			}

			@Override
			public boolean take(ServerPlayer player, long amount) {
				return CobbleDollarsBank.take(player, amount);
			}
		});
	}

	private CobbleDollarsCompat() {
	}
}
//?}
