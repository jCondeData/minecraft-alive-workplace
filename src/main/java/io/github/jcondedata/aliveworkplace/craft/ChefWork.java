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
import net.minecraft.resources.ResourceLocation;
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
 * ({@link Tonics}, ROADMAP 30.15) from the same chests, until the stove's chests hold each one's keep. Before his menu,
 * the dishes asked for at his village's hall board ({@link DishRequests}, 38.8a), which are never on the menu itself.
 */
public class ChefWork extends CrafterWork {
	/** Not one of the jobs that took over the Crafter's chains (37.14a): a cook cooks for the kitchen's menu, not a builder's list. */
	@Override
	protected boolean chainsForBuilders() {
		return false;
	}

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
		Job asked = chooseRequest(level, villager, station, stock, sources);
		if (asked != null) {
			return asked; // 38.8a: the dishes asked for at the hall's board, before his own menu
		}
		List<Item> menu = Chefs.menu(level, villager); // Cobblecuisine's dishes only on request since 38.8a
		for (int i = 0; i < menu.size(); i++) {
			Item dish = menu.get((next + i) % menu.size());
			long have = stock.getOrDefault(dish, 0L);
			if (have >= Chefs.KEEP) {
				continue;
			}
			if (Crafting.planningSpent(level)) {
				// A long menu (38.11: every Campfire Pot recipe) used up this tick's planning: carry on from this dish next
				// time, or the dishes after it would never get their turn.
				next = (next + i) % menu.size();
				return null;
			}
			// As many as there are makings for, up to a batch.
			for (int count = (int) Math.min(Chefs.BATCH, Chefs.KEEP - have); count >= 1; count /= 2) {
				Crafting.Plan plan = Crafting.plan(level, kind, dish, count, stock);
				if (fits(plan)) {
					next = (next + i + 1) % menu.size();
					return new Job(station, null, plan, "", sources);
				}
			}
			if (Crafting.planningSpent(level)) {
				// This dish used up the rest (it had its go): the next one goes first next time, so none is skipped for good.
				next = (next + i + 1) % menu.size();
				return null;
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

	/**
	 * A dish asked for at the board of the village our stove is in ({@link DishRequests}, 38.8a), the oldest first: one
	 * our level allows, as many as are waiting (up to a batch, fewer if the makings run short), for the village's
	 * Storehouse (our own chests if it has none). Null when nothing waiting can be cooked.
	 */
	@Nullable
	private Job chooseRequest(ServerLevel level, Villager villager, BlockPos station, Map<Item, Long> stock, List<BlockPos> sources) {
		if (!DishRequests.shown()) {
			return null;
		}
		BlockPos hall = io.github.jcondedata.aliveworkplace.hall.VillageHalls.nearest(level, station).orElse(null);
		if (hall == null) {
			return null;
		}
		Map<ResourceLocation, Integer> waiting = DishRequests.get(level).of(hall);
		if (waiting.isEmpty()) {
			return null;
		}
		int chefLevel = io.github.jcondedata.aliveworkplace.build.BuilderLevels.level(villager);
		long now = level.getGameTime();
		BlockPos deliverTo = level.getPoiManager().findClosest(h -> h.is(ModVillagers.STOREHOUSE_POI), hall,
				io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS, net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY)
			.map(BlockPos::immutable).filter(p -> !SupplyContainers.find(level, p, null).isEmpty()).orElse(station);
		for (Map.Entry<ResourceLocation, Integer> e : waiting.entrySet()) {
			CuisineDishes.Dish dish = CuisineDishes.byId(e.getKey());
			if (dish == null || dish.level() > chefLevel) {
				continue; // a dish no longer listed, or above our level (another Chef's, or asked before he left)
			}
			String claim = REQUEST + hall.asLong() + "|" + dish.id();
			if (claimed(claim, now)) {
				continue;
			}
			for (int count = Math.min(Chefs.BATCH, e.getValue()); count >= 1; count /= 2) {
				Crafting.Plan plan = Crafting.plan(level, kind, dish.item(), count, stock);
				if (fits(plan)) {
					claim(claim, now);
					return new Job(deliverTo, null, plan, claim, sources);
				}
			}
		}
		return null;
	}

	/** A request's claim: {@code dish|<hall>|<dish id>}. */
	static final String REQUEST = "dish|";

	/** A requested dish delivered: that many fewer wait at the board (38.8a). */
	@Override
	protected void delivered(ServerLevel level, Villager villager, Job job, int made) {
		if (made <= 0 || !job.claim().startsWith(REQUEST)) {
			return;
		}
		String[] parts = job.claim().substring(REQUEST.length()).split("\\|", 2);
		ResourceLocation dish = parts.length == 2 ? ResourceLocation.tryParse(parts[1]) : null;
		if (dish != null) {
			DishRequests.get(level).done(BlockPos.of(Long.parseLong(parts[0])), dish, made);
		}
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
