package io.github.jcondedata.aliveworkplace.work;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Paying and charging players: in CobbleDollars when that mod is installed (the Cobbleverse pack's money),
 * otherwise in emeralds. Amounts are given in both, and only the one in use counts. If CobbleDollars ever
 * changes under us, we quietly fall back to emeralds instead of crashing (see {@link Bank}).
 */
public final class Money {
	/** What an emerald price comes to in CobbleDollars (lessons, shop prices, fares). */
	public static int DOLLARS_PER_EMERALD = 100;

	/** Whether prices and prizes are in CobbleDollars right now (a {@link Bank} is filled in and still works). */
	public static boolean cobbleDollars() {
		return Bank.EXTENSION.present();
	}

	/** "600 CobbleDollars" or "6 emeralds". */
	public static Component describe(long dollars, int emeralds) {
		return cobbleDollars()
			? Component.translatable("message.aliveworkplace.money.dollars", dollars)
			: Component.translatable("message.aliveworkplace.money.emeralds", emeralds);
	}

	/** What the player has: "1,250 CobbleDollars" or "12 emeralds". */
	public static Component balance(ServerPlayer player) {
		Long dollars = Bank.EXTENSION.call(bank -> bank.balance(player), null);
		if (dollars != null) {
			return describe(dollars, 0);
		}
		return describe(0, player.getInventory().countItem(Items.EMERALD));
	}

	public static boolean canAfford(ServerPlayer player, long dollars, int emeralds) {
		if (player.getAbilities().instabuild) {
			return true;
		}
		Boolean enough = Bank.EXTENSION.call(bank -> bank.balance(player) >= dollars, null);
		if (enough != null) {
			return enough;
		}
		return player.getInventory().countItem(Items.EMERALD) >= emeralds;
	}

	/** Takes the price; false (and nothing taken) if the player can't pay. Creative players pay nothing. */
	public static boolean charge(ServerPlayer player, long dollars, int emeralds) {
		if (player.getAbilities().instabuild) {
			return true;
		}
		Boolean taken = Bank.EXTENSION.call(bank -> bank.take(player, dollars), null);
		if (taken != null) {
			return taken;
		}
		if (player.getInventory().countItem(Items.EMERALD) < emeralds) {
			return false;
		}
		player.getInventory().clearOrCountMatchingItems(s -> s.is(Items.EMERALD), emeralds, player.inventoryMenu.getCraftSlots());
		return true;
	}

	/** Pays a prize. */
	public static void pay(ServerPlayer player, long dollars, int emeralds) {
		boolean paid = Bank.EXTENSION.call(bank -> {
			bank.add(player, dollars);
			return true;
		}, false);
		if (paid) {
			return;
		}
		ItemStack prize = new ItemStack(Items.EMERALD, emeralds);
		if (!player.getInventory().add(prize)) {
			player.drop(prize, false);
		}
	}

	private Money() {
	}
}
