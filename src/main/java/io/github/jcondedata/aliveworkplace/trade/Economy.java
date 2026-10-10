package io.github.jcondedata.aliveworkplace.trade;

import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * A village's economy, once a day in its hall's round (ROADMAP 33.2): the goods it's known for and short of
 * ({@link Specialties}) and every good's price ({@link Prices}), kept on its {@code Caravans.Data} entry as a
 * {@link Market}. The daily count runs in the first round after dawn, one village per game tick per dimension (ten
 * villages whose rounds fall together spread over the next rounds), from the round's census and one Storehouse lookup.
 * Off ({@code villageEconomy}): nothing is worked out, and what was stays saved.
 */
public final class Economy {
	/** The {@code villageEconomy} switch (with M33's gate). */
	public static boolean ENABLED = true;

	/** The game tick a village in each dimension last had its daily count. */
	private static final Map<ResourceKey<Level>, Long> LAST = new HashMap<>();
	private static int countings;

	/** The hall's round: today's count, if the village hasn't had it and no other village in this dimension had one this tick. */
	public static void round(ServerLevel level, BlockPos hall, VillageHalls.Census census) {
		if (!ENABLED || TradeGoods.all().isEmpty()) {
			return;
		}
		Caravans.Data data = Caravans.Data.get(level);
		if (level.getRaidAt(hall) != null) {
			// A vanilla raid on the village: its raid demand, as our night and bandit raids give it when they end
			// (raising it again in a later round only keeps the same demand running to the later end).
			TradeGoods.event(level, hall, TradeGoods.RAID);
		}
		long today = Chronicle.day(level);
		if (data.village(hall) == null || data.market(hall).priceDay() == today) {
			return;
		}
		synchronized (LAST) {
			Long last = LAST.get(level.dimension());
			if (last != null && last == level.getGameTime()) {
				return;
			}
			LAST.put(level.dimension(), level.getGameTime());
		}
		count(level, hall, census, today);
	}

	/**
	 * The village's count for {@code today}: its lists worked out again, and, on a day its prices haven't moved yet, each
	 * price moved a third of the way to its target. Null with the economy off or a village not on the caravans' list.
	 */
	@org.jetbrains.annotations.Nullable
	public static Market count(ServerLevel level, BlockPos hall, VillageHalls.Census census, long today) {
		Caravans.Data data = Caravans.Data.get(level);
		if (!ENABLED || data.village(hall) == null) {
			return null;
		}
		countings++;
		List<ResourceLocation> jobs = new ArrayList<>();
		for (Villager v : census.workers()) {
			jobs.add(BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().getProfession()));
		}
		Map<Item, Long> stock = SupplyContainers.contents(level, Caravans.storehouse(level, hall));
		Specialties.Village village = new Specialties.Village(jobs, level.getBiome(hall), stock, census.requests());
		List<TradeGoods.Good> goods = TradeGoods.all();
		Specialties.Lists lists = Specialties.lists(goods, village);
		boolean ill = census.workers().stream().anyMatch(Sickness::isIll) || census.jobless().stream().anyMatch(Sickness::isIll);
		Market old = data.market(hall);
		List<Market.Demand> demand = old.demand().stream().filter(d -> d.until() >= today).toList();
		boolean dawn = old.priceDay() != today;
		Map<ResourceLocation, Market.Price> prices = new LinkedHashMap<>();
		for (TradeGoods.Good g : goods) {
			Market.Price was = old.prices().get(g.id());
			if (!dawn && was != null) {
				prices.put(g.id(), was);
				continue;
			}
			int target = target(g, village, lists, census.food(), demand, ill, today);
			int cents = was == null ? target : Prices.move(g.basePrice(), was.cents(), target);
			prices.put(g.id(), new Market.Price(cents, was == null ? cents : was.cents(), 0));
		}
		Market market = new Market(lists.knownFor(), lists.shortOf(), prices, today, demand);
		data.setMarket(hall, market);
		return market;
	}

	/** Where {@code good}'s price is heading in the village today. */
	static int target(TradeGoods.Good good, Specialties.Village village, Specialties.Lists lists, long meals, List<Market.Demand> demand, boolean ill,
					  long today) {
		int events = 0;
		for (Market.Demand d : demand) {
			if (d.good().equals(good.id()) && d.on(today)) {
				events += d.amount();
			}
		}
		if (ill) {
			for (TradeGoods.Event e : good.events()) {
				if (e.event().equals(TradeGoods.ILLNESS)) {
					events += e.demand();
				}
			}
		}
		int supply = Prices.supply(Specialties.bundles(good, village.stock()), lists.knownFor().contains(good.id()));
		int wanted = Prices.demand(lists.shortOf().contains(good.id()), Specialties.bundlesWaiting(good, village.requests()), good.food(), meals, events);
		return Prices.target(good.basePrice(), wanted, supply);
	}

	/** How many daily counts have run since the game started (tests: nothing is worked out with the switch off). */
	public static int countings() {
		return countings;
	}

	private Economy() {
	}
}
