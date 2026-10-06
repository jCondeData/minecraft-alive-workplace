package io.github.jcondedata.aliveworkplace.craft;

import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Village;
import io.github.jcondedata.aliveworkplace.people.Tonics;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The Chef's shift: cook the dishes on the menu ({@link Chefs#menu}), one after another, from what's in the chests by the
 * Kitchen Stove, in the village's storehouse (the porter's chests hold the farms' and fishermen's spare harvest) and at the
 * butcher's (milk and eggs), and
 * put them in the chests by the stove — until those hold {@link Chefs#KEEP} of each. After the menu, the chef's tonics
 * ({@link Tonics}, ROADMAP 30.15) from the same chests, until the stove's chests hold each one's keep.
 */
public class ChefWork extends CrafterWork {
	private int next;

	public ChefWork() {
		super(Crafting.Kind.KITCHEN, "chef", "chef", true);
	}

	@Nullable
	@Override
	protected Job choose(ServerLevel level, Villager villager, BlockPos station) {
		Job order = chooseOrder(level, villager, station);
		if (order != null) {
			return order; // the store's orders first
		}
		List<BlockPos> own = SupplyContainers.find(level, station, null);
		List<BlockPos> sources = new ArrayList<>(own);
		if (own.isEmpty()) {
			return null; // nowhere to put what's cooked
		}
		Map<Item, Long> stock = new java.util.HashMap<>(SupplyContainers.contents(level, own));
		for (Village.Stash stash : Village.stashes(level, villager, station, null)) {
			if (stash.job() == ModVillagers.PORTER || stash.job() == net.minecraft.world.entity.npc.VillagerProfession.BUTCHER) {
				sources.addAll(stash.chests());
				// Only what the builds near them don't still need (B84: a Farmstead's potatoes went into the pot).
				io.github.jcondedata.aliveworkplace.build.BuildReserve reserve = io.github.jcondedata.aliveworkplace.build.BuildReserve.cached(level, stash.chests());
				for (Item item : SupplyContainers.contents(level, stash.chests()).keySet()) {
					long spare = reserve.spare(level, item);
					if (spare > 0) {
						stock.merge(item, spare, Long::sum);
					}
				}
			}
		}
		List<Item> menu = Chefs.menu(level);
		for (int i = 0; i < menu.size(); i++) {
			Item dish = menu.get((next + i) % menu.size());
			long have = stock.getOrDefault(dish, 0L);
			if (have >= Chefs.KEEP) {
				continue;
			}
			// As many as there are makings for, up to a batch.
			for (int count = (int) Math.min(Chefs.BATCH, Chefs.KEEP - have); count >= 1; count /= 2) {
				Crafting.Plan plan = Crafting.plan(level, kind, dish, count, stock);
				if (fits(plan)) {
					next = (next + i + 1) % menu.size();
					return new Job(station, null, plan, "", sources);
				}
			}
		}
		// After the menu, the chef's tonics (ROADMAP 30.15): one short of its keep in the chests by the stove.
		Tonics.Order tonic = Tonics.next(level, Tonics.Maker.CHEF, own, sources, Chefs.BATCH);
		if (tonic != null) {
			Crafting.Plan plan = plan(tonic);
			if (fits(plan)) {
				return new Job(station, null, plan, "", sources);
			}
		}
		return null;
	}

	/** A tonic's making as a plan: its makings in, the tonic out, no recipe (players can't make it). */
	static Crafting.Plan plan(Tonics.Order order) {
		Item item = order.tonic().item();
		Map<Item, Integer> each = new LinkedHashMap<>();
		order.takes().forEach((in, n) -> each.put(in, Math.max(1, n / order.count())));
		Crafting.Step step = new Crafting.Step(each, new ItemStack(item), order.count());
		return new Crafting.Plan(item, order.count(), List.of(step), new LinkedHashMap<>(order.takes()), Map.of(item, order.count()));
	}

	/** Partners at work (ROADMAP 28.5): the smoker's flames fanned. */
	@Override
	protected String partnerCue() {
		return "cook";
	}
}
