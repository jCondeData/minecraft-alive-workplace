package io.github.jcondedata.aliveworkplace.hall;

import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * The Tithe edict's two effects (ROADMAP 30.8), read when a player trades with one of the village's villagers:
 * {@code trade_prices} makes their emerald prices that much higher when the trade screen opens (rounded to whole
 * emeralds, so a 10% rise leaves trades under 5 emeralds alone), and {@code tithe} puts its share of the emeralds a
 * player pays into the treasury, in hundredths, up to its cap. The Fair Ledger keeps the tithe and drops the price.
 */
public final class Tithe {
	/** The extra emeralds {@code percent} more makes of a price of {@code emeralds}: 20 at 10% is 2, 4 is 0, 5 is 1. */
	public static int rise(int emeralds, int percent) {
		return (int) Math.round(emeralds * percent / 100.0);
	}

	/**
	 * When {@code villager} opens the trade screen for a player, after vanilla's reputation and Hero discounts: each
	 * emerald price goes up by the village's {@code trade_prices} (vanilla resets it when the trading stops).
	 */
	public static void prices(Villager villager) {
		int percent = CivicEffects.of(villager).tradePrices(villager);
		if (percent == 0) {
			return;
		}
		for (MerchantOffer offer : villager.getOffers()) {
			ItemStack cost = offer.getCostA();
			if (cost.is(Items.EMERALD)) {
				int rise = rise(cost.getCount(), percent);
				if (rise != 0) {
					offer.addToSpecialPriceDiff(rise);
				}
			}
		}
	}

	/** A player bought {@code offer} from {@code villager}: the village's tithe of the emeralds paid goes to the treasury. */
	public static void paid(Villager villager, MerchantOffer offer) {
		if (villager.getTradingPlayer() == null) {
			return;
		}
		int percent = CivicEffects.of(villager).tithe(villager);
		if (percent <= 0) {
			return;
		}
		int emeralds = (offer.getCostA().is(Items.EMERALD) ? offer.getCostA().getCount() : 0)
			+ (offer.getCostB().is(Items.EMERALD) ? offer.getCostB().getCount() : 0);
		VillageHallBlockEntity hall = CivicEffects.hallOf(villager);
		if (emeralds > 0 && hall != null) {
			put(hall, emeralds * percent);
		}
	}

	/** {@code cents} hundredths of an emerald into {@code hall}'s treasury, up to its cap; returns what went in. */
	public static int put(VillageHallBlockEntity hall, int cents) {
		int before = hall.treasury();
		hall.setTreasury((int) Math.max(before, Math.min(Treasury.cap(hall.rank()) * 100L, (long) before + Math.max(0, cents))));
		int added = hall.treasury() - before;
		hall.addTreasuryTotal(added);
		return added;
	}

	private Tithe() {
	}
}
