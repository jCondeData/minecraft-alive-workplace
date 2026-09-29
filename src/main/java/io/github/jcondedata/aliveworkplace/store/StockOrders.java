package io.github.jcondedata.aliveworkplace.store;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;

/**
 * Stock orders at a Storehouse: "keep 64 stone bricks in the store". The village's crafters (carpenters, masons,
 * tinkerers, chefs — each with the recipes of their trade) make whatever is short from what's in the store, between the
 * builders' jobs, and bring it to the storehouse chests. Set on the Storehouse's board.
 */
public final class StockOrders {
	/** The amounts an order cycles through (the last click removes it). */
	public static final List<Integer> STEPS = List.of(16, 32, 64, 128, 256);
	public static final int MAX_ORDERS = 18;
	/** How far from a crafter's workstation the storehouses they fill orders for may be. */
	public static int RANGE = 48;

	/** The storehouse's orders, item to how many to keep (empty if it isn't a storehouse). */
	public static Map<Item, Integer> of(ServerLevel level, BlockPos storehouse) {
		return level.getBlockEntity(storehouse) instanceof StorehouseBlockEntity entity ? entity.orders() : Map.of();
	}

	/** Orders {@code item} (keep the first step), or, if it's ordered, the next step up — past the last, the order's dropped. */
	public static void cycle(ServerLevel level, BlockPos storehouse, Item item) {
		if (!(level.getBlockEntity(storehouse) instanceof StorehouseBlockEntity entity)) {
			return;
		}
		Map<Item, Integer> orders = new LinkedHashMap<>(entity.orders());
		Integer now = orders.get(item);
		if (now == null) {
			if (orders.size() >= MAX_ORDERS) {
				return;
			}
			orders.put(item, STEPS.get(0));
		} else {
			int i = STEPS.indexOf(now);
			if (i < 0 || i + 1 >= STEPS.size()) {
				orders.remove(item);
			} else {
				orders.put(item, STEPS.get(i + 1));
			}
		}
		entity.setOrders(orders);
	}

	private StockOrders() {
	}
}
