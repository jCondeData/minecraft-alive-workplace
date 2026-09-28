package io.github.jcondedata.aliveworkplace.work;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Paying and charging players: in CobbleDollars when that mod is installed (the Cobbleverse pack's money),
 * otherwise in emeralds. Amounts are given in both, and only the one in use counts. If CobbleDollars ever
 * changes under us, we quietly fall back to emeralds instead of crashing.
 */
public final class Money {
	private static boolean cobbleDollars = FabricLoader.getInstance().isModLoaded("cobbledollars");

	/** Whether prices and prizes are in CobbleDollars right now. */
	public static boolean cobbleDollars() {
		return cobbleDollars;
	}

	/** "600 CobbleDollars" or "6 emeralds". */
	public static Component describe(long dollars, int emeralds) {
		return cobbleDollars
			? Component.translatable("message.aliveworkplace.money.dollars", dollars)
			: Component.translatable("message.aliveworkplace.money.emeralds", emeralds);
	}

	/** What the player has: "1,250 CobbleDollars" or "12 emeralds". */
	public static Component balance(ServerPlayer player) {
		if (cobbleDollars) {
			try {
				return describe(io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank.balance(player), 0);
			} catch (LinkageError e) {
				disable(e);
			}
		}
		return describe(0, player.getInventory().countItem(Items.EMERALD));
	}

	public static boolean canAfford(ServerPlayer player, long dollars, int emeralds) {
		if (player.getAbilities().instabuild) {
			return true;
		}
		if (cobbleDollars) {
			try {
				return io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank.balance(player) >= dollars;
			} catch (LinkageError e) {
				disable(e);
			}
		}
		return player.getInventory().countItem(Items.EMERALD) >= emeralds;
	}

	/** Takes the price; false (and nothing taken) if the player can't pay. Creative players pay nothing. */
	public static boolean charge(ServerPlayer player, long dollars, int emeralds) {
		if (player.getAbilities().instabuild) {
			return true;
		}
		if (cobbleDollars) {
			try {
				return io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank.take(player, dollars);
			} catch (LinkageError e) {
				disable(e);
			}
		}
		if (player.getInventory().countItem(Items.EMERALD) < emeralds) {
			return false;
		}
		player.getInventory().clearOrCountMatchingItems(s -> s.is(Items.EMERALD), emeralds, player.inventoryMenu.getCraftSlots());
		return true;
	}

	/** Pays a prize. */
	public static void pay(ServerPlayer player, long dollars, int emeralds) {
		if (cobbleDollars) {
			try {
				io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank.add(player, dollars);
				return;
			} catch (LinkageError e) {
				disable(e);
			}
		}
		ItemStack prize = new ItemStack(Items.EMERALD, emeralds);
		if (!player.getInventory().add(prize)) {
			player.drop(prize, false);
		}
	}

	private static void disable(LinkageError e) {
		AliveWorkplace.LOG.warn("CobbleDollars changed in a way Alive Workplace doesn't understand; using emeralds instead", e);
		cobbleDollars = false;
	}

	private Money() {
	}
}
