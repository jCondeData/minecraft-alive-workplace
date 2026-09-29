package io.github.jcondedata.aliveworkplace.craft;

import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

/**
 * The Chef's shift: cook the dishes on the menu ({@link Chefs#menu}), one after another, from what's in the chests by the
 * Kitchen Stove, in the village's storehouse (the porter's chests hold the farms' and fishermen's spare harvest) and at the
 * butcher's (milk and eggs), and
 * put them in the chests by the stove — until those hold {@link Chefs#KEEP} of each.
 */
public class ChefWork extends CrafterWork {
	private int next;

	public ChefWork() {
		super(Crafting.Kind.KITCHEN, "chef", "chef", true);
	}

	@Nullable
	@Override
	protected Job choose(ServerLevel level, Villager villager, BlockPos station) {
		List<BlockPos> own = SupplyContainers.find(level, station, null);
		List<BlockPos> sources = new ArrayList<>(own);
		for (Village.Stash stash : Village.stashes(level, villager, station, null)) {
			if (stash.job() == ModVillagers.PORTER || stash.job() == net.minecraft.world.entity.npc.VillagerProfession.BUTCHER) {
				sources.addAll(stash.chests());
			}
		}
		if (own.isEmpty() || sources.isEmpty()) {
			return null; // nowhere to put what's cooked
		}
		Map<Item, Long> stock = SupplyContainers.contents(level, sources);
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
		return null;
	}
}
