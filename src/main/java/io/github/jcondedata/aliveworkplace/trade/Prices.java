package io.github.jcondedata.aliveworkplace.trade;

import io.github.jcondedata.aliveworkplace.hall.Caravans;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/**
 * The price engine (ROADMAP 33.2): every good has a price in each village, in hundredths of an emerald a bundle.
 * <pre>
 * supply = min(5, bundles in the Storehouses) + (known for ? 3 : 0)
 * demand = (short of ? 3 : 0) + min(3, bundles workers wait for) + (food and store under 16 meals ? 2 : 0) + events
 * target = clamp(base × (1 + 0.2 × (demand − supply)), base / 2, base × 2)
 * at dawn: price += (target − price) / 3, rounded to the cent; a good without a price starts at its target
 * </pre>
 */
public final class Prices {
	/** Meals in the store below which food goods are in demand. */
	public static final int MEALS = 16;

	/** The supply of a good in a village. */
	public static int supply(long bundlesInStore, boolean knownFor) {
		return (int) Math.min(5, Math.max(0, bundlesInStore)) + (knownFor ? 3 : 0);
	}

	/** The demand for a good in a village. */
	public static int demand(boolean shortOf, int bundlesWaiting, boolean food, long meals, int events) {
		return (shortOf ? 3 : 0) + Math.min(3, Math.max(0, bundlesWaiting)) + (food && meals < MEALS ? 2 : 0) + events;
	}

	/** The lowest price of a good of base price {@code base}. */
	public static int floor(int base) {
		return Math.max(1, base / 2);
	}

	/** The highest. */
	public static int ceiling(int base) {
		return base * 2;
	}

	/** Where the price is heading: base × (1 + 0.2 × (demand − supply)), between half and twice the base. */
	public static int target(int base, int demand, int supply) {
		long cents = Math.round(base * (1 + 0.2 * (demand - supply)));
		return (int) Math.max(floor(base), Math.min(ceiling(base), cents));
	}

	/** A dawn's move: a third of the way from {@code price} to {@code target}, rounded to the cent, kept between half and twice the base. */
	public static int move(int base, int price, int target) {
		int moved = price + (int) Math.round((target - price) / 3.0);
		return Math.max(floor(base), Math.min(ceiling(base), moved));
	}

	/** The price of {@code good} at the village whose hall is at {@code hall}: its last price, or its base if it has none yet. */
	public static int at(ServerLevel level, BlockPos hall, ResourceLocation good) {
		Market.Price p = Caravans.Data.get(level).market(hall).prices().get(good);
		if (p != null) {
			return p.cents();
		}
		TradeGoods.Good g = TradeGoods.get(good);
		return g == null ? 0 : g.basePrice();
	}

	private Prices() {
	}
}
