package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Requests;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Caravans: villages with a Village Hall send each other what they need. Every hall in a dimension is on a list (with
 * its name and what its workers are waiting for, kept up to date by its own round, so it's known even where the world
 * isn't loaded). A trade route (set up on the hall's Trade Routes page) sends, once a day, whatever the other village is
 * waiting for that this one has plenty of in its Storehouses' chests — up to {@link #CARGO_STACKS} stacks — and the
 * caravan arrives after a trip as long as the road ({@link #travelTicks}), into the other village's Storehouse chests.
 * Both chronicles note it. 27.17: each village builds its half of a road to the other ({@code CaravanRoads}); the list
 * keeps where each half ends and whether it's built ({@link Half}), and on a finished road caravans take three quarters
 * of the time. 33.6: with the village economy on a caravan also carries up to {@link #TRADE_STACKS} stacks of goods to
 * sell ({@link Lot}), which the other village's treasury pays for when they arrive
 * ({@link io.github.jcondedata.aliveworkplace.trade.CaravanTrade}); what it can't pay for travels home again. 33.7: a
 * caravan leaving or arriving where a player is near is also seen ({@link CaravanSights}: a carter and two llamas);
 * the goods travel as before either way. 41.3: between two villages whose waystones both stand
 * ({@link VillageWaystones#linked}) a caravan walks to its stone and steps out at the other's, the trip as short as the
 * shortest ({@link #waystoneTrip}); a stone broken when it steps out, it walks the road instead.
 */
public final class Caravans {
	/** How far apart villages can trade. */
	public static int RANGE = 2048;
	/** Most stacks one caravan carries. */
	public static final int CARGO_STACKS = 4;
	/** Most stacks of goods for sale a caravan carries besides (33.6). */
	public static final int TRADE_STACKS = 2;
	/** A village keeps this many of anything back for itself. */
	public static final int KEEP = 16;
	/** Most trade routes out of one village. */
	public static final int MAX_ROUTES = 3;

	/** What a village is waiting for: {@code count} of {@code item}. */
	public record Want(Item item, int count) {
	}

	/** A village on the list: its hall, name and wants, and when it last sent a caravan. */
	public record Village(BlockPos hall, Component name, List<Want> wants, long lastCaravanDay) {
	}

	/**
	 * A village's half of the road to another village (27.17): where it ends (feet), whether it's all built, whether it
	 * stops short at a milestone, and whether the village's chronicle has noted it.
	 */
	public record Half(BlockPos end, boolean finished, boolean milestone, boolean noted) {
	}

	/**
	 * A village's Trainer Leader (28.17), as its hall's last round saw it: id, name ("" without one), tier, experience, and
	 * whether it's a Leader or (the village having none) its best Trainer. Kept so an unloaded village can still send them
	 * to a Festival Cup.
	 */
	public record Leader(java.util.UUID id, String name, int tier, int xp, boolean leader) {
	}

	/** Two halves within this many blocks of each other's ends have met: the road is one. */
	public static final int HALVES_MEET = 4;

	/** Goods of one trade good a caravan carries to sell (33.6): whole bundles of it. */
	public record Lot(ResourceLocation good, List<ItemStack> stacks) {
		public Lot {
			stacks = List.copyOf(stacks);
		}

		/** How many items the lot holds. */
		public int count() {
			return stacks.stream().mapToInt(ItemStack::getCount).sum();
		}
	}

	/**
	 * Goods on the road: {@code goods} are for the village at {@code to} to keep; {@code sale} (33.6) it pays for at its
	 * board's price when they arrive. {@code back}: a caravan bringing home what {@code from} couldn't pay for, or
	 * couldn't buy. {@code byTrain} (39.7): it rides the train between two villages with stations
	 * ({@link io.github.jcondedata.aliveworkplace.city.CaravanTrains}); saved, false (it walks) in older saves.
	 * {@code byWaystone} (41.3): it walked to its village's waystone and steps out at the other's when it {@code arrives}
	 * ({@link VillageWaystones#linked}); saved, false in older saves.
	 */
	public record Shipment(BlockPos from, BlockPos to, List<ItemStack> goods, long arrives, List<Lot> sale, boolean back, boolean byTrain, boolean byWaystone) {
		/** A caravan that doesn't go by waystone (41.3: {@code byWaystone} false). */
		public Shipment(BlockPos from, BlockPos to, List<ItemStack> goods, long arrives, List<Lot> sale, boolean back, boolean byTrain) {
			this(from, to, goods, arrives, sale, back, byTrain, false);
		}

		public Shipment(BlockPos from, BlockPos to, List<ItemStack> goods, long arrives) {
			this(from, to, goods, arrives, List.of(), false);
		}

		/** A caravan that walks (39.7: {@code byTrain} false). */
		public Shipment(BlockPos from, BlockPos to, List<ItemStack> goods, long arrives, List<Lot> sale, boolean back) {
			this(from, to, goods, arrives, sale, back, false);
		}
	}

	/**
	 * A caravan's sale the selling village hasn't booked yet (33.6): {@code items} of {@code good} sold to {@code buyer}
	 * for {@code cents} on {@code day}. Booked (the treasury paid, the chronicle written) in the seller's next round, at
	 * once when its hall is loaded. {@code buyerHall} (B430; null in older saves) gets back what the seller's full
	 * treasury can't take.
	 */
	public record Sale(BlockPos seller, Component buyer, ResourceLocation good, int items, int cents, long day, @Nullable BlockPos buyerHall) {
		public Sale(BlockPos seller, Component buyer, ResourceLocation good, int items, int cents, long day) {
			this(seller, buyer, good, items, cents, day, null);
		}
	}

	/** The list of villages, routes and caravans on the road in one dimension. */
	public static final class Data extends SavedData {
		private static final String NAME = "aliveworkplace_caravans";
		final Map<BlockPos, Village> villages = new LinkedHashMap<>();
		final Map<BlockPos, Set<BlockPos>> routes = new LinkedHashMap<>();
		final List<Shipment> onTheRoad = new ArrayList<>();
		/** Caravan sales their sellers haven't booked yet (33.6). */
		final List<Sale> sales = new ArrayList<>();
		/** Hundredths of an emerald owed back to buying villages whose money a full treasury couldn't take (B430). */
		final Map<BlockPos, Integer> refunds = new LinkedHashMap<>();
		/** The halves of roads between villages (27.17), from one hall towards another. */
		final Map<BlockPos, Map<BlockPos, Half>> halves = new LinkedHashMap<>();
		/** Each village's Trainer Leader (28.17); none until its hall's round writes one. */
		final Map<BlockPos, Leader> leaders = new LinkedHashMap<>();
		/** Trainer XP a village's entrants earned at a Festival Cup while away (28.18), by village and trainer, until the village next loads. */
		final Map<BlockPos, Map<java.util.UUID, Integer>> banked = new LinkedHashMap<>();
		/** Each village's trade goods (33.2): known for, short of, prices; none until its hall's daily count. */
		final Map<BlockPos, io.github.jcondedata.aliveworkplace.trade.Market> markets = new LinkedHashMap<>();
		/** Each village's Village Banner base colour as its hall's last round saw it (33.7: its caravans' carpets); none without a banner. */
		final Map<BlockPos, net.minecraft.world.item.DyeColor> colours = new LinkedHashMap<>();
		/**
		 * Sister routes (33.10): a mother village and the colony it founded, a route both ways that is always on, carries
		 * only what the other waits for (free, nothing for sale) and doesn't count toward the cap on trade routes.
		 */
		final Map<BlockPos, Set<BlockPos>> sisters = new LinkedHashMap<>();
		/** Each village's Create stations (39.7) as its hall's last round found them; none in older saves. */
		final Map<BlockPos, List<BlockPos>> stations = new LinkedHashMap<>();
		/** The villages each village has laid track towards along its half of the road (39.7); none in older saves. */
		final Map<BlockPos, Set<BlockPos>> tracks = new LinkedHashMap<>();

		public static Data get(ServerLevel level) {
			return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
		}

		public List<Village> villages() {
			return List.copyOf(villages.values());
		}

		@Nullable
		public Village village(BlockPos hall) {
			return villages.get(hall);
		}

		/** The villages {@code hall} sends caravans to: its trade routes and its sister villages (33.10). */
		public Set<BlockPos> routesFrom(BlockPos hall) {
			Set<BlockPos> out = new LinkedHashSet<>(routes.getOrDefault(hall, Set.of()));
			out.addAll(sisters.getOrDefault(hall, Set.of()));
			return Set.copyOf(out);
		}

		/** Links a mother village and its colony (33.10): a sister route both ways, outside the cap. */
		public void addSisters(BlockPos a, BlockPos b) {
			sisters.computeIfAbsent(a.immutable(), k -> new LinkedHashSet<>()).add(b.immutable());
			sisters.computeIfAbsent(b.immutable(), k -> new LinkedHashSet<>()).add(a.immutable());
			setDirty();
		}

		/** Whether {@code a} and {@code b} are mother and colony, with a sister route between them. */
		public boolean isSister(BlockPos a, BlockPos b) {
			return sisters.getOrDefault(a, Set.of()).contains(b);
		}

		/** The villages {@code hall} has a sister route with. */
		public Set<BlockPos> sistersOf(BlockPos hall) {
			return Set.copyOf(sisters.getOrDefault(hall, Set.of()));
		}

		/** The trade routes out of {@code hall} that count toward its cap: not the sister routes. */
		public int tradeRoutes(BlockPos hall) {
			return routes.getOrDefault(hall, Set.of()).size();
		}

		public List<Shipment> onTheRoad() {
			return List.copyOf(onTheRoad);
		}

		void update(BlockPos hall, Component name, List<Want> wants) {
			Village old = villages.get(hall);
			villages.put(hall.immutable(), new Village(hall.immutable(), name, List.copyOf(wants), old == null ? -1 : old.lastCaravanDay()));
			setDirty();
		}

		/** Notes what a village wants (tests, and the hall's round). */
		public void setWants(BlockPos hall, Component name, List<Want> wants) {
			update(hall, name, wants);
		}

		/** Marks {@code hall}'s caravans sent on {@code day} (tests set it back to send again the same day). */
		public void sent(BlockPos hall, long day) {
			Village v = villages.get(hall);
			if (v != null) {
				villages.put(hall, new Village(v.hall(), v.name(), v.wants(), day));
				setDirty();
			}
		}

		/**
		 * Takes the village at {@code hall} off the list (tests; a hall broken in the world: {@link Caravans#hallGone}).
		 * Without the level, a caravan stopped at a station for the village's Create train comes off it at once, and goods
		 * in the train's chest stay there.
		 */
		public void remove(BlockPos hall) {
			remove(hall, 0, s -> List.of(), s -> 0);
		}

		/**
		 * Takes the village at {@code hall} off the list. B214: a caravan stopped at a station for the village's Create
		 * train ({@link io.github.jcondedata.aliveworkplace.city.CaravanTrains#waiting}) to or from it has no train left
		 * to wait for: it gets off and walks, arriving {@code walk} ticks from {@code now} ({@code now} itself when it turns
		 * home from its own station, never having boarded), with its goods taken back out of the train's chest by {@code offTrain} when they rode there (only what
		 * is still there, once; nothing left: it's dropped).
		 */
		void remove(BlockPos hall, long now, java.util.function.Function<Shipment, List<ItemStack>> offTrain, java.util.function.ToLongFunction<Shipment> walk) {
			villages.remove(hall);
			routes.remove(hall);
			routes.values().forEach(to -> to.remove(hall));
			sales.removeIf(s -> s.seller().equals(hall));
			refunds.remove(hall); // B430: a village gone is owed nothing
			// 33.6, B212: a caravan on its way here turns round for home with everything it carried, the goods it brought
			// for nothing and those to sell (if home is still on the list); one coming home here, or whose home is gone
			// too, has nowhere to go and is dropped. A caravan that set out from here still arrives where it was going.
			// B214: one stopped at a station for the train gets off (its goods out of the train's chest, once) and walks.
			List<Shipment> home = new ArrayList<>();
			List<Shipment> next = new ArrayList<>();
			for (Shipment s : onTheRoad) {
				boolean stationed = s.byTrain() && io.github.jcondedata.aliveworkplace.city.CaravanTrains.waiting(s.arrives());
				if (!s.to().equals(hall) && !(stationed && s.from().equals(hall))) {
					next.add(s);
					continue;
				}
				boolean turn = s.to().equals(hall);
				if (turn && (s.back() || !villages.containsKey(s.from()))) {
					// nowhere to go: dropped; goods in the train's chest stay in it (never a second time with the caravan)
					continue;
				}
				List<ItemStack> goods = new ArrayList<>(stationed && s.arrives() == io.github.jcondedata.aliveworkplace.city.CaravanTrains.IN_CHEST
					? offTrain.apply(s) : s.goods());
				List<Lot> sale = s.sale();
				if (turn) {
					sale.forEach(lot -> goods.addAll(lot.stacks()));
					sale = List.of();
				}
				goods.removeIf(ItemStack::isEmpty);
				if (goods.isEmpty() && sale.isEmpty()) {
					continue;
				}
				// one that never boarded and turns round is still at its own station: home at once
				long arrives = !stationed ? s.arrives()
					: turn && s.arrives() == io.github.jcondedata.aliveworkplace.city.CaravanTrains.BOARDING ? now : now + walk.applyAsLong(s);
				Shipment moved = turn ? new Shipment(hall, s.from(), List.copyOf(goods), arrives, List.of(), true, !stationed && s.byTrain())
					: new Shipment(s.from(), s.to(), List.copyOf(goods), arrives, sale, s.back(), false);
				(turn ? home : next).add(moved);
			}
			onTheRoad.clear();
			onTheRoad.addAll(next);
			onTheRoad.addAll(home);
			halves.remove(hall);
			halves.values().forEach(to -> to.remove(hall));
			sisters.remove(hall);
			sisters.values().forEach(to -> to.remove(hall));
			leaders.remove(hall);
			markets.remove(hall);
			colours.remove(hall);
			stations.remove(hall);
			tracks.remove(hall);
			tracks.values().forEach(to -> to.remove(hall));
			setDirty();
		}

		/** The village's Create stations (39.7) as its hall's last round found them; empty without. */
		public List<BlockPos> stations(BlockPos hall) {
			return stations.getOrDefault(hall, List.of());
		}

		/** Notes the village's stations (39.7); a village not on the list keeps none. */
		public void setStations(BlockPos hall, List<BlockPos> found) {
			if (!villages.containsKey(hall)) {
				return;
			}
			List<BlockPos> old = found.isEmpty() ? stations.remove(hall) : stations.put(hall.immutable(), List.copyOf(found));
			if (!found.equals(old == null ? List.of() : old)) {
				setDirty();
			}
		}

		/** Whether {@code hall} has laid track along its half of the road to {@code other} (39.7). */
		public boolean tracked(BlockPos hall, BlockPos other) {
			return tracks.getOrDefault(hall, Set.of()).contains(other);
		}

		/** Notes (or forgets) the track along {@code hall}'s half of the road to {@code other} (39.7). */
		public void setTracked(BlockPos hall, BlockPos other, boolean laid) {
			boolean changed = laid ? tracks.computeIfAbsent(hall.immutable(), k -> new LinkedHashSet<>()).add(other.immutable())
				: tracks.getOrDefault(hall, new LinkedHashSet<>()).remove(other);
			if (changed) {
				setDirty();
			}
		}

		/** The village's Village Banner base colour as its hall's last round wrote it (33.7), or null without one. */
		@Nullable
		public net.minecraft.world.item.DyeColor colour(BlockPos hall) {
			return colours.get(hall);
		}

		/** Writes the village's Village Banner base colour (null: it has none); a village not on the list keeps none. */
		public void setColour(BlockPos hall, @Nullable net.minecraft.world.item.DyeColor colour) {
			if (!villages.containsKey(hall)) {
				return;
			}
			net.minecraft.world.item.DyeColor old = colour == null ? colours.remove(hall) : colours.put(hall.immutable(), colour);
			if (old != colour) {
				setDirty();
			}
		}

		/** The village's trade goods as its hall's last daily count left them (33.2); empty before the first. */
		public io.github.jcondedata.aliveworkplace.trade.Market market(BlockPos hall) {
			return markets.getOrDefault(hall, io.github.jcondedata.aliveworkplace.trade.Market.EMPTY);
		}

		/** Writes the village's trade goods (33.2); a village not on the list keeps none. */
		public void setMarket(BlockPos hall, io.github.jcondedata.aliveworkplace.trade.Market market) {
			if (!villages.containsKey(hall)) {
				return;
			}
			io.github.jcondedata.aliveworkplace.trade.Market old = market.isEmpty() ? markets.remove(hall) : markets.put(hall.immutable(), market);
			if (!market.equals(old == null ? io.github.jcondedata.aliveworkplace.trade.Market.EMPTY : old)) {
				setDirty();
			}
		}

		/** The villages {@code hall} has a trade route with, either way (27.17: each builds its half of the road). */
		public Set<BlockPos> partners(BlockPos hall) {
			Set<BlockPos> out = new LinkedHashSet<>(routes.getOrDefault(hall, Set.of()));
			routes.forEach((from, to) -> {
				if (to.contains(hall)) {
					out.add(from);
				}
			});
			out.addAll(sisters.getOrDefault(hall, Set.of()));
			out.remove(hall);
			return out;
		}

		/** The village's Trainer Leader as its hall's last round wrote it, or null. */
		@Nullable
		public Leader leader(BlockPos hall) {
			return leaders.get(hall);
		}

		/** Writes the village's Trainer Leader (null: it has none). */
		public void setLeader(BlockPos hall, @Nullable Leader leader) {
			Leader old = leader == null ? leaders.remove(hall) : leaders.put(hall.immutable(), leader);
			if (!java.util.Objects.equals(old, leader)) {
				setDirty();
			}
		}

		/** Banks {@code xp} trainer XP on {@code hall}'s entry for its trainer {@code trainer} (28.18), paid when the village next loads. */
		public void bankXp(BlockPos hall, java.util.UUID trainer, int xp) {
			if (xp > 0) {
				banked.computeIfAbsent(hall.immutable(), k -> new LinkedHashMap<>()).merge(trainer, xp, Integer::sum);
				setDirty();
			}
		}

		/** The trainer XP banked on {@code hall}'s entry, by trainer. */
		public Map<java.util.UUID, Integer> banked(BlockPos hall) {
			return Map.copyOf(banked.getOrDefault(hall, Map.of()));
		}

		/** Takes {@code trainer}'s banked XP off {@code hall}'s entry; returns how much there was. */
		public int takeBanked(BlockPos hall, java.util.UUID trainer) {
			Map<java.util.UUID, Integer> m = banked.get(hall);
			Integer xp = m == null ? null : m.remove(trainer);
			if (m != null && m.isEmpty()) {
				banked.remove(hall);
			}
			if (xp != null) {
				setDirty();
			}
			return xp == null ? 0 : xp;
		}

		/** {@code hall}'s half of the road towards {@code other}, or null if it has none with a way found yet. */
		@Nullable
		public Half half(BlockPos hall, BlockPos other) {
			return halves.getOrDefault(hall, Map.of()).get(other);
		}

		/** Notes {@code hall}'s half of the road towards {@code other}. */
		public void setHalf(BlockPos hall, BlockPos other, Half half) {
			Map<BlockPos, Half> to = halves.computeIfAbsent(hall.immutable(), k -> new LinkedHashMap<>());
			if (!half.equals(to.get(other))) {
				to.put(other.immutable(), half);
				setDirty();
			}
		}

		/** Whether the road between {@code a} and {@code b} is finished: both halves built, and they meet. */
		public boolean roadFinished(BlockPos a, BlockPos b) {
			Half ab = half(a, b);
			Half ba = half(b, a);
			return ab != null && ba != null && ab.finished() && ba.finished()
				&& Math.abs(ab.end().getX() - ba.end().getX()) + Math.abs(ab.end().getZ() - ba.end().getZ()) <= HALVES_MEET;
		}

		/** Starts or stops the route from {@code from} to {@code to} (at most {@link #MAX_ROUTES}); true if it's on now. */
		public boolean toggleRoute(BlockPos from, BlockPos to) {
			return toggleRoute(from, to, MAX_ROUTES);
		}

		/** Starts or stops the route from {@code from} to {@code to}, with at most {@code max} routes out; true if it's on now. */
		public boolean toggleRoute(BlockPos from, BlockPos to, int max) {
			if (isSister(from, to)) {
				return true; // a sister route stays open
			}
			Set<BlockPos> out = routes.computeIfAbsent(from.immutable(), k -> new LinkedHashSet<>());
			boolean on;
			if (out.remove(to)) {
				on = false;
			} else if (out.size() < max) {
				out.add(to.immutable());
				on = true;
			} else {
				return false;
			}
			setDirty();
			return on;
		}

		void ship(Shipment shipment) {
			onTheRoad.add(shipment);
			setDirty();
		}

		/** Brings the caravans on the road to {@code hall} in now (tests: one waiting for room tries again at once). */
		public void hurry(BlockPos hall) {
			onTheRoad.replaceAll(s -> s.to().equals(hall) ? new Shipment(s.from(), s.to(), s.goods(), 0, s.sale(), s.back(), s.byTrain(), s.byWaystone()) : s);
			setDirty();
		}

		/**
		 * Trains coming in to {@code hall} with no station there now (39.7: broken while they were on their way): each
		 * caravan gets off where the track ends and walks the rest, arriving no sooner than {@code walk} ticks from
		 * {@code now} (nor sooner than the train would have). Returns where they came from.
		 */
		List<BlockPos> walkTheRest(BlockPos hall, long now, java.util.function.ToLongFunction<Shipment> walk) {
			List<BlockPos> from = new ArrayList<>();
			onTheRoad.replaceAll(s -> {
				if (!s.to().equals(hall) || !s.byTrain() || s.arrives() == io.github.jcondedata.aliveworkplace.city.CaravanTrains.IN_CHEST) {
					return s; // 39.7b: goods in the train's chest come back out of it first (CaravanTrains.serve), never twice
				}
				from.add(s.from());
				long arrives = io.github.jcondedata.aliveworkplace.city.CaravanTrains.waiting(s.arrives()) ? now + walk.applyAsLong(s) // 39.7a: never boarded
					: Math.max(s.arrives(), now + walk.applyAsLong(s));
				return new Shipment(s.from(), s.to(), s.goods(), arrives, s.sale(), s.back(), false);
			});
			if (!from.isEmpty()) {
				setDirty();
			}
			return from;
		}

		/**
		 * 41.3: the caravans by waystone come to {@code hall} ({@code arrives} up) when the stone at either end isn't standing
		 * ({@code linked} false) can't step out: they walk the whole road instead, arriving {@code walk} ticks from {@code now}.
		 * Returns the villages they came from.
		 */
		List<BlockPos> offTheWaystones(BlockPos hall, long now, java.util.function.Predicate<Shipment> linked, java.util.function.ToLongFunction<Shipment> walk) {
			List<BlockPos> from = new ArrayList<>();
			onTheRoad.replaceAll(s -> {
				if (!s.to().equals(hall) || !s.byWaystone() || s.arrives() > now || linked.test(s)) {
					return s;
				}
				from.add(s.from());
				return new Shipment(s.from(), s.to(), s.goods(), now + walk.applyAsLong(s), s.sale(), s.back(), false, false);
			});
			if (!from.isEmpty()) {
				setDirty();
			}
			return from;
		}

		/**
		 * 39.7a: the caravans from {@code from} to {@code to} whose arrival is {@code was} (a stage of the trip by the
		 * village's Create train, {@link io.github.jcondedata.aliveworkplace.city.CaravanTrains#BOARDING}) get {@code arrives}
		 * instead. Returns how many.
		 */
		public int retime(BlockPos from, BlockPos to, long was, long arrives) {
			int[] n = {0};
			onTheRoad.replaceAll(s -> {
				if (!s.from().equals(from) || !s.to().equals(to) || s.arrives() != was) {
					return s;
				}
				n[0]++;
				return new Shipment(s.from(), s.to(), s.goods(), arrives, s.sale(), s.back(), s.byTrain());
			});
			if (n[0] > 0) {
				setDirty();
			}
			return n[0];
		}

		/**
		 * 39.7b: the caravans from {@code from} to {@code to} waiting at the station board the train standing there: with
		 * {@code load} putting their goods in its chest they ride {@link io.github.jcondedata.aliveworkplace.city.CaravanTrains#IN_CHEST},
		 * else (no room) as the caravan's record ({@code ABOARD}). Returns how many boarded.
		 */
		public int board(BlockPos from, BlockPos to, java.util.function.Predicate<List<ItemStack>> load) {
			int[] n = {0};
			onTheRoad.replaceAll(s -> {
				if (!s.from().equals(from) || !s.to().equals(to) || s.arrives() != io.github.jcondedata.aliveworkplace.city.CaravanTrains.BOARDING) {
					return s;
				}
				n[0]++;
				long stage = load.test(s.goods()) ? io.github.jcondedata.aliveworkplace.city.CaravanTrains.IN_CHEST
					: io.github.jcondedata.aliveworkplace.city.CaravanTrains.ABOARD;
				return new Shipment(s.from(), s.to(), s.goods(), stage, s.sale(), s.back(), s.byTrain());
			});
			if (n[0] > 0) {
				setDirty();
			}
			return n[0];
		}

		/**
		 * 39.7b: the train brought the caravans from {@code from} in at {@code to}'s station: each one's goods come out of
		 * its chest ({@code unload} gives what was still there) and it arrives {@code now} with those. Returns how many.
		 */
		public int unload(BlockPos from, BlockPos to, long now, java.util.function.UnaryOperator<List<ItemStack>> unload) {
			int[] n = {0};
			onTheRoad.replaceAll(s -> {
				if (!s.from().equals(from) || !s.to().equals(to) || s.arrives() != io.github.jcondedata.aliveworkplace.city.CaravanTrains.IN_CHEST) {
					return s;
				}
				n[0]++;
				return new Shipment(s.from(), s.to(), List.copyOf(unload.apply(s.goods())), now, s.sale(), s.back(), s.byTrain());
			});
			if (n[0] > 0) {
				setDirty();
			}
			return n[0];
		}

		/**
		 * 39.7b: the train with the goods of the caravans from {@code from} to {@code to} in its chest is gone or can't come
		 * in: what {@code unload} still finds in the chest (a derailed train keeps it) goes on with the caravan, arriving
		 * {@code arrives}; a caravan with nothing left there (the train broken up: its chest has them) is taken off the
		 * road. Returns how many were taken off.
		 */
		public int recover(BlockPos from, BlockPos to, long arrives, java.util.function.UnaryOperator<List<ItemStack>> unload) {
			int[] gone = {0};
			boolean[] changed = {false};
			List<Shipment> next = new ArrayList<>();
			for (Shipment s : onTheRoad) {
				if (!s.from().equals(from) || !s.to().equals(to) || s.arrives() != io.github.jcondedata.aliveworkplace.city.CaravanTrains.IN_CHEST) {
					next.add(s);
					continue;
				}
				changed[0] = true;
				List<ItemStack> back = unload.apply(s.goods());
				if (back.isEmpty()) {
					gone[0]++;
				} else {
					next.add(new Shipment(s.from(), s.to(), List.copyOf(back), arrives, s.sale(), s.back(), s.byTrain()));
				}
			}
			if (changed[0]) {
				onTheRoad.clear();
				onTheRoad.addAll(next);
				setDirty();
			}
			return gone[0];
		}

		/**
		 * B359: trains no longer run (Create taken out of the pack, or config {@code villageTrains} off). Every caravan to
		 * or from {@code hall} at a stage of the trip by the village's Create train (waiting at the station, aboard as its
		 * record, or with its goods in the train's chest) takes the timed trip instead, arriving {@code arrives}. Goods in
		 * the chest come back out of it by {@code offTrain} (with Create gone, the caravan's own record of them, the train
		 * being gone with Create); a caravan with nothing left there is taken off the road. Returns the other village of
		 * each one taken off (the chronicle's line).
		 */
		public List<BlockPos> offTheTrains(BlockPos hall, java.util.function.ToLongFunction<Shipment> arrives,
			java.util.function.Function<Shipment, List<ItemStack>> offTrain) {
			List<BlockPos> gone = new ArrayList<>();
			boolean changed = false;
			List<Shipment> next = new ArrayList<>();
			for (Shipment s : onTheRoad) {
				if (!(s.from().equals(hall) || s.to().equals(hall)) || !io.github.jcondedata.aliveworkplace.city.CaravanTrains.waiting(s.arrives())) {
					next.add(s);
					continue;
				}
				changed = true;
				List<ItemStack> goods = s.arrives() == io.github.jcondedata.aliveworkplace.city.CaravanTrains.IN_CHEST
					? new ArrayList<>(offTrain.apply(s)) : new ArrayList<>(s.goods());
				goods.removeIf(ItemStack::isEmpty);
				if (goods.isEmpty() && s.sale().isEmpty()) {
					gone.add(s.from().equals(hall) ? s.to() : s.from());
					continue;
				}
				next.add(new Shipment(s.from(), s.to(), List.copyOf(goods), arrives.applyAsLong(s), s.sale(), s.back(), s.byTrain()));
			}
			if (changed) {
				onTheRoad.clear();
				onTheRoad.addAll(next);
				setDirty();
			}
			return gone;
		}

		/** Takes every caravan to or from {@code hall} off the road (tests clean up after themselves). */
		public void clearRoad(BlockPos hall) {
			if (onTheRoad.removeIf(s -> s.to().equals(hall) || s.from().equals(hall))) {
				setDirty();
			}
		}

		/** Notes a caravan's sale for its seller to book (33.6). */
		public void sold(Sale sale) {
			sales.add(sale);
			setDirty();
		}

		/** The sales {@code seller} hasn't booked yet, taken off the list. */
		public List<Sale> takeSales(BlockPos seller) {
			List<Sale> out = new ArrayList<>();
			sales.removeIf(s -> {
				if (s.seller().equals(seller)) {
					out.add(s);
					return true;
				}
				return false;
			});
			if (!out.isEmpty()) {
				setDirty();
			}
			return out;
		}

		/** The sales waiting for their sellers (tests). */
		public List<Sale> sales() {
			return List.copyOf(sales);
		}

		/** Hundredths of an emerald {@code seller}'s caravans have sold that it hasn't booked yet. */
		public int unbooked(BlockPos seller) {
			return sales.stream().filter(s -> s.seller().equals(seller)).mapToInt(Sale::cents).sum();
		}

		/** Owes {@code cents} back to {@code buyer}, whose money a full treasury couldn't take (B430). */
		public void refund(BlockPos buyer, int cents) {
			if (cents > 0) {
				refunds.merge(buyer.immutable(), cents, Integer::sum);
				setDirty();
			}
		}

		/** What is owed back to {@code buyer}. */
		public int refunds(BlockPos buyer) {
			return refunds.getOrDefault(buyer, 0);
		}

		/** Sets what is still owed back to {@code buyer} after paying some of it. */
		public void setRefund(BlockPos buyer, int cents) {
			Integer old = cents > 0 ? refunds.put(buyer.immutable(), cents) : refunds.remove(buyer);
			if (old == null || old != cents) {
				setDirty();
			}
		}

		/** The caravans for {@code hall} that have arrived by {@code now}, taken off the road. */
		List<Shipment> arrived(BlockPos hall, long now) {
			List<Shipment> out = new ArrayList<>();
			onTheRoad.removeIf(s -> {
				if (s.to().equals(hall) && s.arrives() <= now) {
					out.add(s);
					return true;
				}
				return false;
			});
			if (!out.isEmpty()) {
				setDirty();
			}
			return out;
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			ListTag list = new ListTag();
			for (Village v : villages.values()) {
				CompoundTag t = new CompoundTag();
				t.putLong("hall", v.hall().asLong());
				t.putString("name", Component.Serializer.toJson(v.name(), registries));
				t.putLong("lastCaravanDay", v.lastCaravanDay());
				ListTag wants = new ListTag();
				for (Want w : v.wants()) {
					CompoundTag wt = new CompoundTag();
					wt.putString("item", BuiltInRegistries.ITEM.getKey(w.item()).toString());
					wt.putInt("count", w.count());
					wants.add(wt);
				}
				t.put("wants", wants);
				ListTag to = new ListTag();
				for (BlockPos p : routes.getOrDefault(v.hall(), Set.of())) {
					CompoundTag pt = new CompoundTag();
					pt.putLong("to", p.asLong());
					to.add(pt);
				}
				t.put("routes", to);
				ListTag halfList = new ListTag();
				halves.getOrDefault(v.hall(), Map.of()).forEach((other, h) -> {
					CompoundTag ht = new CompoundTag();
					ht.putLong("to", other.asLong());
					ht.putLong("end", h.end().asLong());
					ht.putBoolean("finished", h.finished());
					ht.putBoolean("milestone", h.milestone());
					ht.putBoolean("noted", h.noted());
					halfList.add(ht);
				});
				t.put("halves", halfList);
				Leader leader = leaders.get(v.hall());
				if (leader != null) {
					CompoundTag lt = new CompoundTag();
					lt.putUUID("id", leader.id());
					lt.putString("name", leader.name());
					lt.putInt("tier", leader.tier());
					lt.putInt("xp", leader.xp());
					lt.putBoolean("leader", leader.leader());
					t.put("leader", lt);
				}
				market(v.hall()).save(t); // 33.2
				net.minecraft.world.item.DyeColor colour = colours.get(v.hall());
				if (colour != null) { // 33.7
					t.putString("colour", colour.getName());
				}
				List<BlockPos> stationsHere = stations.getOrDefault(v.hall(), List.of());
				if (!stationsHere.isEmpty()) { // 39.7
					t.putLongArray("stations", stationsHere.stream().mapToLong(BlockPos::asLong).toArray());
				}
				Set<BlockPos> tracked = tracks.getOrDefault(v.hall(), Set.of());
				if (!tracked.isEmpty()) { // 39.7
					t.putLongArray("tracks", tracked.stream().mapToLong(BlockPos::asLong).toArray());
				}
				list.add(t);
			}
			tag.put("villages", list);
			ListTag road = new ListTag();
			for (Shipment s : onTheRoad) {
				CompoundTag t = new CompoundTag();
				t.putLong("from", s.from().asLong());
				t.putLong("to", s.to().asLong());
				t.putLong("arrives", s.arrives());
				ListTag goods = new ListTag();
				for (ItemStack stack : s.goods()) {
					if (!stack.isEmpty()) {
						goods.add(stack.save(registries));
					}
				}
				t.put("goods", goods);
				if (!s.sale().isEmpty()) { // 33.6
					ListTag sale = new ListTag();
					for (Lot lot : s.sale()) {
						CompoundTag lt = new CompoundTag();
						lt.putString("good", lot.good().toString());
						ListTag stacks = new ListTag();
						for (ItemStack stack : lot.stacks()) {
							if (!stack.isEmpty()) {
								stacks.add(stack.save(registries));
							}
						}
						lt.put("stacks", stacks);
						sale.add(lt);
					}
					t.put("sale", sale);
				}
				if (s.back()) {
					t.putBoolean("back", true);
				}
				if (s.byTrain()) { // 39.7
					t.putBoolean("byTrain", true);
				}
				if (s.byWaystone()) { // 41.3
					t.putBoolean("byWaystone", true);
				}
				road.add(t);
			}
			tag.put("road", road);
			if (!sales.isEmpty()) { // 33.6
				ListTag sold = new ListTag();
				for (Sale s : sales) {
					CompoundTag t = new CompoundTag();
					t.putLong("seller", s.seller().asLong());
					t.putString("buyer", Component.Serializer.toJson(s.buyer(), registries));
					t.putString("good", s.good().toString());
					t.putInt("items", s.items());
					t.putInt("cents", s.cents());
					t.putLong("day", s.day());
					if (s.buyerHall() != null) { // B430
						t.putLong("buyerHall", s.buyerHall().asLong());
					}
					sold.add(t);
				}
				tag.put("sales", sold);
			}
			if (!refunds.isEmpty()) { // B430
				ListTag owed = new ListTag();
				refunds.forEach((hall, cents) -> {
					CompoundTag t = new CompoundTag();
					t.putLong("hall", hall.asLong());
					t.putInt("cents", cents);
					owed.add(t);
				});
				tag.put("refunds", owed);
			}
			ListTag bank = new ListTag();
			banked.forEach((hall, m) -> m.forEach((id, xp) -> {
				CompoundTag t = new CompoundTag();
				t.putLong("hall", hall.asLong());
				Nbt.putUuid(t, "id", id);
				t.putInt("xp", xp);
				bank.add(t);
			}));
			tag.put("bankedXp", bank);
			if (!sisters.isEmpty()) { // 33.10
				ListTag sister = new ListTag();
				sisters.forEach((a, to) -> to.forEach(b -> {
					CompoundTag t = new CompoundTag();
					t.putLong("a", a.asLong());
					t.putLong("b", b.asLong());
					sister.add(t);
				}));
				tag.put("sisters", sister);
			}
			return tag;
		}

		public static Data load(CompoundTag tag, HolderLookup.Provider registries) {
			Data data = new Data();
			ListTag list = Nbt.getList(tag, "villages", Tag.TAG_COMPOUND);
			for (int i = 0; i < list.size(); i++) {
				CompoundTag t = Nbt.compoundAt(list, i);
				BlockPos hall = BlockPos.of(Nbt.getLong(t, "hall"));
				Component name = Component.Serializer.fromJson(Nbt.getString(t, "name"), registries);
				List<Want> wants = new ArrayList<>();
				ListTag wl = Nbt.getList(t, "wants", Tag.TAG_COMPOUND);
				for (int j = 0; j < wl.size(); j++) {
					ResourceLocation id = ResourceLocation.tryParse(Nbt.getString(Nbt.compoundAt(wl, j), "item"));
					if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
						wants.add(new Want(Lookup.value(BuiltInRegistries.ITEM, id), Nbt.getInt(Nbt.compoundAt(wl, j), "count")));
					}
				}
				data.villages.put(hall, new Village(hall, name == null ? Component.empty() : name, List.copyOf(wants), Nbt.getLong(t, "lastCaravanDay")));
				ListTag rl = Nbt.getList(t, "routes", Tag.TAG_COMPOUND);
				for (int j = 0; j < rl.size(); j++) {
					data.routes.computeIfAbsent(hall, k -> new LinkedHashSet<>()).add(BlockPos.of(Nbt.getLong(Nbt.compoundAt(rl, j), "to")));
				}
				ListTag hl = Nbt.getList(t, "halves", Tag.TAG_COMPOUND); // 27.17; older saves have none
				for (int j = 0; j < hl.size(); j++) {
					CompoundTag ht = Nbt.compoundAt(hl, j);
					data.halves.computeIfAbsent(hall, k -> new LinkedHashMap<>()).put(BlockPos.of(Nbt.getLong(ht, "to")),
						new Half(BlockPos.of(Nbt.getLong(ht, "end")), Nbt.getBoolean(ht, "finished"), Nbt.getBoolean(ht, "milestone"), Nbt.getBoolean(ht, "noted")));
				}
				CompoundTag lt = Nbt.getCompound(t, "leader"); // 28.17; older saves have none
				if (lt.hasUUID("id")) {
					data.leaders.put(hall, new Leader(lt.getUUID("id"), Nbt.getString(lt, "name"), Nbt.getInt(lt, "tier"), Nbt.getInt(lt, "xp"),
						Nbt.getBoolean(lt, "leader")));
				}
				io.github.jcondedata.aliveworkplace.trade.Market market = io.github.jcondedata.aliveworkplace.trade.Market.load(t); // 33.2; older saves have none
				if (!market.isEmpty()) {
					data.markets.put(hall, market);
				}
				net.minecraft.world.item.DyeColor colour = net.minecraft.world.item.DyeColor.byName(Nbt.getString(t, "colour"), null); // 33.7; older saves have none
				if (colour != null) {
					data.colours.put(hall, colour);
				}
				long[] stationsHere = t.getLongArray("stations"); // 39.7; older saves have none
				if (stationsHere.length > 0) {
					data.stations.put(hall, java.util.Arrays.stream(stationsHere).mapToObj(BlockPos::of).toList());
				}
				for (long other : t.getLongArray("tracks")) { // 39.7; older saves have none
					data.tracks.computeIfAbsent(hall, k -> new LinkedHashSet<>()).add(BlockPos.of(other));
				}
			}
			ListTag road = Nbt.getList(tag, "road", Tag.TAG_COMPOUND);
			for (int i = 0; i < road.size(); i++) {
				CompoundTag t = Nbt.compoundAt(road, i);
				List<ItemStack> goods = new ArrayList<>();
				ListTag gl = Nbt.getList(t, "goods", Tag.TAG_COMPOUND);
				for (int j = 0; j < gl.size(); j++) {
					ItemStack.parse(registries, Nbt.compoundAt(gl, j)).ifPresent(goods::add);
				}
				List<Lot> sale = new ArrayList<>();
				ListTag sl = Nbt.getList(t, "sale", Tag.TAG_COMPOUND); // 33.6; older saves have none
				for (int j = 0; j < sl.size(); j++) {
					CompoundTag lt = Nbt.compoundAt(sl, j);
					ResourceLocation good = ResourceLocation.tryParse(Nbt.getString(lt, "good"));
					List<ItemStack> stacks = new ArrayList<>();
					ListTag stl = Nbt.getList(lt, "stacks", Tag.TAG_COMPOUND);
					for (int k = 0; k < stl.size(); k++) {
						ItemStack.parse(registries, Nbt.compoundAt(stl, k)).ifPresent(stacks::add);
					}
					if (good != null && !stacks.isEmpty()) {
						sale.add(new Lot(good, stacks));
					}
				}
				data.onTheRoad.add(new Shipment(BlockPos.of(Nbt.getLong(t, "from")), BlockPos.of(Nbt.getLong(t, "to")), goods, Nbt.getLong(t, "arrives"),
					sale, Nbt.getBoolean(t, "back"), Nbt.getBoolean(t, "byTrain"), Nbt.getBoolean(t, "byWaystone"))); // byTrain: 39.7, byWaystone: 41.3, false in older saves
			}
			ListTag sold = Nbt.getList(tag, "sales", Tag.TAG_COMPOUND); // 33.6; older saves have none
			for (int i = 0; i < sold.size(); i++) {
				CompoundTag t = Nbt.compoundAt(sold, i);
				ResourceLocation good = ResourceLocation.tryParse(Nbt.getString(t, "good"));
				Component buyer = Component.Serializer.fromJson(Nbt.getString(t, "buyer"), registries);
				if (good != null) {
					data.sales.add(new Sale(BlockPos.of(Nbt.getLong(t, "seller")), buyer == null ? Component.empty() : buyer, good, Nbt.getInt(t, "items"),
						Nbt.getInt(t, "cents"), Nbt.getLong(t, "day"), Nbt.has(t, "buyerHall", Tag.TAG_LONG) ? BlockPos.of(Nbt.getLong(t, "buyerHall")) : null)); // buyerHall: B430
				}
			}
			ListTag owed = Nbt.getList(tag, "refunds", Tag.TAG_COMPOUND); // B430; older saves have none
			for (int i = 0; i < owed.size(); i++) {
				CompoundTag t = Nbt.compoundAt(owed, i);
				data.refunds.merge(BlockPos.of(Nbt.getLong(t, "hall")), Nbt.getInt(t, "cents"), Integer::sum);
			}
			ListTag bank = Nbt.getList(tag, "bankedXp", Tag.TAG_COMPOUND); // 28.18; older saves have none
			for (int i = 0; i < bank.size(); i++) {
				CompoundTag t = Nbt.compoundAt(bank, i);
				if (Nbt.hasUuid(t, "id")) {
					data.banked.computeIfAbsent(BlockPos.of(Nbt.getLong(t, "hall")), k -> new LinkedHashMap<>()).merge(Nbt.getUuid(t, "id"), Nbt.getInt(t, "xp"), Integer::sum);
				}
			}
			ListTag sister = Nbt.getList(tag, "sisters", Tag.TAG_COMPOUND); // 33.10; older saves have none
			for (int i = 0; i < sister.size(); i++) {
				CompoundTag t = Nbt.compoundAt(sister, i);
				data.sisters.computeIfAbsent(BlockPos.of(Nbt.getLong(t, "a")), k -> new LinkedHashSet<>()).add(BlockPos.of(Nbt.getLong(t, "b")));
			}
			return data;
		}
	}

	/** The shortest trip, in ticks. */
	public static long MIN_TRAVEL = 1200;

	/** How long a caravan takes over {@code blocks} of road: a minute at least, a tick for every two blocks beyond. */
	public static long travelTicks(double blocks) {
		return Math.max(MIN_TRAVEL, (long) (blocks / 2));
	}

	/** How long a caravan takes over {@code blocks}, three quarters of it on a finished road between the villages (27.17). */
	public static long travelTicks(double blocks, boolean road) {
		long ticks = travelTicks(blocks);
		return road ? ticks * 3 / 4 : ticks;
	}

	/**
	 * How long a caravan from {@code from} to {@code to} takes: {@link #travelTicks(double, boolean)}, and (33.16) Royal
	 * Roads' percent sooner when both villages are of one realm, with the Royal Highway edict's (33.22), 90% at most.
	 */
	public static long trip(ServerLevel level, BlockPos from, BlockPos to, Data data) {
		long ticks = travelTicks(Math.sqrt(from.distSqr(to)), data.roadFinished(from, to));
		int sooner = Math.min(90, io.github.jcondedata.aliveworkplace.realm.RealmResearch.caravanSpeed(level, from, to)
			+ io.github.jcondedata.aliveworkplace.realm.RealmEdicts.caravanSpeed(level, from, to)); // and the Royal Highway (33.22)
		ticks = sooner <= 0 ? ticks : Math.max(1, ticks * (100 - sooner) / 100);
		return io.github.jcondedata.aliveworkplace.wonder.LighthousePowers.caravanTrip(level, from, ticks); // half the time with the Lighthouse (35.12)
	}

	/**
	 * How long a caravan by waystone takes (41.3): the walk to its village's stone and from the other's, the shortest trip
	 * ({@link #MIN_TRAVEL}), never longer than the road.
	 */
	public static long waystoneTrip(ServerLevel level, BlockPos from, BlockPos to, Data data) {
		return Math.min(MIN_TRAVEL / 2, trip(level, from, to, data));
	}

	/**
	 * Granaries (33.16): a caravan of meals from {@code from} to the realm member {@code to}, sent at dawn outside the
	 * route cap (it needs no route and doesn't count as the day's caravan). Returns it.
	 */
	public static Shipment sendGranary(ServerLevel level, BlockPos from, BlockPos to, List<ItemStack> meals) {
		Data data = Data.get(level);
		Shipment shipment = new Shipment(from.immutable(), to.immutable(), List.copyOf(meals), level.getGameTime() + trip(level, from, to, data));
		data.ship(shipment);
		level.playSound(null, from, SoundEvents.LLAMA_CHEST, SoundSource.NEUTRAL, 1f, 1f);
		return shipment;
	}

	/** The villages {@code hall} can trade with, nearest first: within {@link #RANGE}, or further with a Lighthouse (35.12). */
	public static List<Village> neighbours(ServerLevel level, BlockPos hall) {
		long range = io.github.jcondedata.aliveworkplace.wonder.LighthousePowers.caravanRange(level, hall); // twice as far with the Lighthouse (35.12)
		return Data.get(level).villages().stream()
			.filter(v -> !v.hall().equals(hall) && v.hall().distSqr(hall) <= (double) range * range)
			.sorted(Comparator.comparingDouble(v -> v.hall().distSqr(hall)))
			.toList();
	}

	/** What the village round {@code hall} is waiting for: its workers' requests, and food when the store runs low. */
	static List<Want> wants(ServerLevel level, BlockPos hall, VillageHalls.Census census) {
		Map<Item, Integer> out = new LinkedHashMap<>();
		for (Requests.Request r : census.requests()) {
			Item item = r.item();
			if (item != null && item != Items.AIR) {
				out.merge(item, r.count(), Math::max);
			}
		}
		// 33.9: the supplies the colony's settlers still wait for
		for (Map.Entry<Item, Integer> e : io.github.jcondedata.aliveworkplace.colony.Settlers.wants(level, hall).entrySet()) {
			out.merge(e.getKey(), e.getValue(), Math::max);
		}
		// 27.19: what the Steward's waiting builds miss
		for (Map.Entry<Item, Integer> e : io.github.jcondedata.aliveworkplace.city.StewardSafety.shoppingList(level, hall)) {
			out.merge(e.getKey(), e.getValue(), Math::max);
		}
		int needed = VillageGrowth.foodNeeded(level, hall);
		if (census.food() < needed) {
			out.merge(Items.BREAD, needed, Math::max);
		}
		return out.entrySet().stream().limit(12).map(e -> new Want(e.getKey(), e.getValue())).toList();
	}

	/** The chests by the village's Storehouses (where caravans load and unload). */
	public static List<BlockPos> storehouse(ServerLevel level, BlockPos hall) {
		Set<BlockPos> chests = new LinkedHashSet<>();
		level.getPoiManager().findAll(h -> h.is(ModVillagers.STOREHOUSE_POI), p -> true, hall, VillageHalls.RADIUS, PoiManager.Occupancy.ANY)
			.forEach(s -> chests.addAll(SupplyContainers.find(level, s.immutable(), null)));
		return new ArrayList<>(chests);
	}

	/** The hall's round: its entry on the list brought up to date, caravans that have come unloaded, today's sent. */
	public static void round(ServerLevel level, BlockPos hall, @Nullable VillageHalls.Census census) {
		Data data = Data.get(level);
		if (census != null) {
			data.update(hall, VillageHalls.name(level, hall), wants(level, hall, census));
			VillageBanners.Colours colours = VillageBanners.of(level, hall); // 33.7: its caravans' carpets, known where it isn't loaded
			data.setColour(hall, colours == null ? null : colours.base());
		}
		io.github.jcondedata.aliveworkplace.city.CaravanTrains.noteStations(level, hall, data); // 39.7
		io.github.jcondedata.aliveworkplace.city.CaravanTrains.offline(level, hall, data); // B359: no trains, the timed trip
		if (data.stations(hall).isEmpty()) {
			// 39.7: trains coming in to a village whose station is gone: the caravans get off and walk the rest
			for (BlockPos from : data.walkTheRest(hall, level.getGameTime(), s -> trip(level, s.from(), s.to(), data) / 4)) {
				Village them = data.village(from);
				Chronicle.record(level, hall, Chronicle.Kind.CARAVAN, Component.translatable("chronicle.aliveworkplace.train_walked",
					Chronicle.otherVillage(them == null ? null : them.name())), true);
			}
		}
		// 41.3: a caravan by waystone that finds a stone broken when it steps out walks the road instead
		for (BlockPos from : data.offTheWaystones(hall, level.getGameTime(), s -> VillageWaystones.linked(level, s.from(), s.to()),
			s -> trip(level, s.from(), s.to(), data))) {
			Village them = data.village(from);
			Chronicle.record(level, hall, Chronicle.Kind.CARAVAN, Component.translatable("chronicle.aliveworkplace.waystone_walked",
				Chronicle.otherVillage(them == null ? null : them.name())), true);
		}
		unload(level, hall, data);
		io.github.jcondedata.aliveworkplace.trade.CaravanTrade.book(level, hall, data); // 33.6: what our caravans sold
		long day = Chronicle.day(level);
		Village us = data.village(hall);
		if (us == null || us.lastCaravanDay() == day || data.routesFrom(hall).isEmpty()) {
			return;
		}
		data.sent(hall, day);
		for (BlockPos to : data.routesFrom(hall)) {
			send(level, hall, to, data);
		}
	}

	/**
	 * Loads a caravan for the village at {@code to} with what it wants and we have plenty of, then (33.6) with up to
	 * {@link #TRADE_STACKS} stacks of goods to sell there; null if there's nothing to send.
	 */
	/** The stacks a caravan from the village round {@code hall} carries: {@link #CARGO_STACKS}, 2 more a level of Trade Law (35.14). */
	public static int cargo(ServerLevel level, BlockPos hall) {
		return CARGO_STACKS + (level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity
			? 2 * io.github.jcondedata.aliveworkplace.research.Research.bonus(level, hall, entity.research(), io.github.jcondedata.aliveworkplace.research.Research.Topic.TRADE_LAW) : 0);
	}

	@Nullable
	static Shipment send(ServerLevel level, BlockPos hall, BlockPos to, Data data) {
		Village them = data.village(to);
		if (them == null) {
			return null;
		}
		List<BlockPos> ours = storehouse(level, hall);
		List<ItemStack> goods = new ArrayList<>();
		int cargo = cargo(level, hall) + io.github.jcondedata.aliveworkplace.realm.RealmEdicts.extraStacks(level, hall, to); // the Royal Highway (33.22)
		for (Want want : them.wants()) {
			if (goods.size() >= cargo) {
				break;
			}
			long have = SupplyContainers.count(level, ours, want.item());
			int spare = (int) Math.min(want.count(), Math.min(want.item().getDefaultMaxStackSize(), have - KEEP));
			if (spare <= 0) {
				continue;
			}
			int taken = SupplyContainers.extract(level, ours, want.item(), spare);
			if (taken > 0) {
				goods.add(new ItemStack(want.item(), taken));
			}
		}
		// 33.10: a sister route carries what the other waits for, free, and nothing for sale
		List<Lot> sale = data.isSister(hall, to) ? List.of() : io.github.jcondedata.aliveworkplace.trade.CaravanTrade.load(level, hall, to, data, ours);
		if (goods.isEmpty() && sale.isEmpty()) {
			return null;
		}
		// 39.7: between two villages with stations and track along the road, the caravan rides the train
		boolean train = io.github.jcondedata.aliveworkplace.city.CaravanTrains.rides(data, hall, to);
		// 41.3: else, between two villages whose waystones both stand, it walks to its stone and steps out at theirs
		boolean waystone = !train && VillageWaystones.linked(level, hall, to);
		long ticks = waystone ? waystoneTrip(level, hall, to, data) : trip(level, hall, to, data);
		// 39.7a: with the village's own Create train on the line, the goods wait at the station to be loaded onto it
		long arrives = train && io.github.jcondedata.aliveworkplace.city.CaravanTrains.realTrain(level, hall, to)
			? io.github.jcondedata.aliveworkplace.city.CaravanTrains.BOARDING
			: level.getGameTime() + (train ? io.github.jcondedata.aliveworkplace.city.CaravanTrains.trainTrip(ticks) : ticks);
		Shipment shipment = new Shipment(hall.immutable(), to.immutable(), List.copyOf(goods), arrives, sale, false, train, waystone);
		List<ItemStack> all = new ArrayList<>(goods);
		sale.forEach(lot -> all.addAll(lot.stacks()));
		// 31.20: on a long road, one caravan in lostCaravanChance goes missing; the Lost Caravan's arc holds its goods (never a train's)
		boolean lost = !train && !waystone && io.github.jcondedata.aliveworkplace.story.LostCaravanStory.lose(level, hall, to, all, level.random);
		if (!lost) {
			data.ship(shipment);
			io.github.jcondedata.aliveworkplace.realm.RealmEdicts.caravanSent(level, hall, to); // a realm reform's count (33.22)
		}
		level.playSound(null, hall, SoundEvents.LLAMA_CHEST, SoundSource.NEUTRAL, 1f, 1f);
		Chronicle.record(level, hall, Chronicle.Kind.CARAVAN, Component.translatable(train ? "chronicle.aliveworkplace.train_left"
			: waystone ? "chronicle.aliveworkplace.waystone_left" : "chronicle.aliveworkplace.caravan_left", them.name(), describe(all)), true);
		VillageMoments.award(level, hall, VillageMoments.CARAVAN, null); // the questbook's first caravan
		if (train) {
			io.github.jcondedata.aliveworkplace.city.CaravanTrains.whistle(level, hall, data); // 39.7: steam and a bell at the station
		} else if (!lost) {
			CaravanSights.leave(level, hall, to, data, waystone); // 33.7: seen setting out, when someone is there to see it (41.3: to the stone)
		}
		return shipment;
	}

	/**
	 * A Village Hall taken away (its block broken): its village leaves the list ({@link Data#remove}). B214: a caravan stopped
	 * at a station for the village's Create train to or from it gets its goods back out of the train's chest (once) and
	 * walks the rest, home when it was going there.
	 */
	public static void hallGone(ServerLevel level, BlockPos hall) {
		Data data = Data.get(level);
		data.remove(hall, level.getGameTime(),
			s -> io.github.jcondedata.aliveworkplace.city.CaravanTrains.RAILWAY.call(
				r -> r.unload(level, io.github.jcondedata.aliveworkplace.city.CaravanTrains.railwayId(s.from(), s.to()), s.goods()), List.<ItemStack>of()),
			s -> Math.max(1, trip(level, s.from(), s.to(), data) / 4));
	}

	/** 39.7a: the village's Create train came in at {@code hall}'s station: the caravans it brought are unloaded now. */
	public static void trainIn(ServerLevel level, BlockPos hall) {
		unload(level, hall, Data.get(level));
	}

	/** Unloads the caravans that have come to {@code hall} into its Storehouses' chests (what doesn't fit stays on the road). */
	static void unload(ServerLevel level, BlockPos hall, Data data) {
		List<Shipment> arrived = data.arrived(hall, level.getGameTime());
		if (arrived.isEmpty()) {
			return;
		}
		List<BlockPos> chests = storehouse(level, hall);
		if (chests.isEmpty() && !data.sistersOf(hall).isEmpty()) {
			chests = SupplyContainers.find(level, hall, null); // 33.10: a colony before its Storehouse stands unloads by the hall (the camp's chest)
		}
		Village us = data.village(hall);
		for (Shipment s : arrived) {
			List<ItemStack> left = new ArrayList<>();
			int paid = 0;
			List<ItemStack> delivered = new ArrayList<>();
			for (ItemStack stack : s.goods()) {
				ItemStack rest = chests.isEmpty() ? stack.copy() : SupplyContainers.insert(level, chests, stack.copy());
				if (!rest.isEmpty()) {
					left.add(rest);
				}
				if (rest.getCount() < stack.getCount()) {
					delivered.add(stack.copyWithCount(stack.getCount() - rest.getCount()));
				}
				if (rest.getCount() < stack.getCount() && us != null && us.wants().stream().anyMatch(w -> w.item() == stack.getItem())) {
					paid++;
				}
			}
			pay(level, s.from(), s.back() ? 0 : paid);
			if (!s.back()) {
				payPact(level, hall, s.from(), delivered, data); // 33.18
			}
			// 33.6: the goods it brought to sell; what there's no room for waits on the road with the rest, what isn't paid for goes home
			io.github.jcondedata.aliveworkplace.trade.CaravanTrade.Outcome sold = s.sale().isEmpty()
				? io.github.jcondedata.aliveworkplace.trade.CaravanTrade.Outcome.NONE
				: io.github.jcondedata.aliveworkplace.trade.CaravanTrade.sell(level, hall, s, chests, data);
			if (!left.isEmpty() || !sold.waiting().isEmpty()) {
				data.ship(new Shipment(s.from(), s.to(), left, level.getGameTime() + VillageNeeds.CHECK_EVERY, sold.waiting(), s.back()));
			}
			// 33.7: a caravan that came in (not one still waiting on the road for room) is seen coming, when someone is there
			if (count(left) + sold.waiting().stream().mapToInt(Lot::count).sum() < count(s.goods()) + s.sale().stream().mapToInt(Lot::count).sum()) {
				if (s.byTrain()) {
					io.github.jcondedata.aliveworkplace.city.CaravanTrains.whistle(level, hall, data); // 39.7: the train comes in
				} else {
					if (s.byWaystone()) {
						VillageWaystones.warp(level, hall); // 41.3: out of the stone, seen or not
					}
					CaravanSights.come(level, hall, s, data);
				}
			}
			if (!sold.home().isEmpty()) {
				List<ItemStack> home = new ArrayList<>();
				sold.home().forEach(lot -> home.addAll(lot.stacks()));
				data.ship(new Shipment(s.to(), s.from(), home,
					level.getGameTime() + trip(level, s.from(), s.to(), data), List.of(), true));
			}
			if (s.goods().isEmpty()) {
				continue; // only goods to sell: the sale's own lines tell it
			}
			if (s.back()) {
				// our own caravan, home with what the other village didn't buy
				if (left.size() < s.goods().size() || left.stream().mapToInt(ItemStack::getCount).sum() < s.goods().stream().mapToInt(ItemStack::getCount).sum()) {
					Village from = data.village(s.from());
					Chronicle.record(level, hall, Chronicle.Kind.CARAVAN, Component.translatable("chronicle.aliveworkplace.caravan_unsold",
						Chronicle.otherVillage(from == null ? null : from.name()), describe(s.goods())), true);
					level.playSound(null, hall, SoundEvents.LLAMA_CHEST, SoundSource.NEUTRAL, 1f, 1.2f);
				}
				continue;
			}
			if (left.size() < s.goods().size() || left.stream().mapToInt(ItemStack::getCount).sum() < s.goods().stream().mapToInt(ItemStack::getCount).sum()) {
				Village from = data.village(s.from());
				Chronicle.record(level, hall, Chronicle.Kind.CARAVAN, Component.translatable(s.byTrain() ? "chronicle.aliveworkplace.train_came"
					: s.byWaystone() ? "chronicle.aliveworkplace.waystone_came" : "chronicle.aliveworkplace.caravan_came",
					Chronicle.otherVillage(from == null ? null : from.name()), describe(s.goods())), true);
				level.playSound(null, hall, SoundEvents.LLAMA_CHEST, SoundSource.NEUTRAL, 1f, 1.2f);
			}
		}
	}

	/**
	 * The Merchant Prince's caravan pay (29.17): {@code stacks} stacks the caravan from {@code from} brought to a village
	 * waiting for them earn {@code from}'s treasury its power's emeralds a stack, up to the treasury's cap.
	 */
	static void pay(ServerLevel level, BlockPos from, int stacks) {
		if (stacks <= 0 || !(level.getBlockEntity(from) instanceof VillageHallBlockEntity entity)) {
			return;
		}
		io.github.jcondedata.aliveworkplace.legend.CaravanPayPower.of(level, from)
			.ifPresent(p -> Tithe.put(entity, stacks * p.emeralds() * 100));
	}

	/**
	 * A pact caravan (33.18) from {@code from} delivered {@code delivered} of what {@code hall} waited for: {@code hall}
	 * pays for each trade good among them at its board's price a bundle (part bundles in proportion), as far as its
	 * treasury goes; the seller books it as a sale. Nothing when the sender gives, between friends, or with pacts off.
	 */
	public static int payPact(ServerLevel level, BlockPos hall, BlockPos from, List<ItemStack> delivered, Data data) {
		if (delivered.isEmpty() || io.github.jcondedata.aliveworkplace.realm.Pacts.pact(level, from, hall) == null
			|| io.github.jcondedata.aliveworkplace.realm.Pacts.gives(level, from, hall)
			|| !(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return 0;
		}
		Village buyer = data.village(hall);
		Component buyerName = buyer == null ? VillageHalls.name(level, hall) : buyer.name();
		int total = 0;
		for (ItemStack stack : delivered) {
			for (io.github.jcondedata.aliveworkplace.trade.TradeGoods.Good good : io.github.jcondedata.aliveworkplace.trade.TradeGoods.all()) {
				if (!good.matches(stack)) {
					continue;
				}
				int cents = Math.min(entity.treasury(), io.github.jcondedata.aliveworkplace.trade.Board.price(level, hall, good) * stack.getCount() / Math.max(1, good.bundle()));
				if (cents > 0) {
					entity.setTreasury(entity.treasury() - cents);
					data.sold(new Sale(from, buyerName, good.id(), stack.getCount(), cents, Chronicle.day(level), hall));
					total += cents;
				}
				break;
			}
		}
		if (total > 0) {
			Village seller = data.village(from);
			Chronicle.atHall(level, hall, Chronicle.Kind.CARAVAN, Component.translatable("chronicle.aliveworkplace.pact.paid",
				Chronicle.otherVillage(seller == null ? null : seller.name()), io.github.jcondedata.aliveworkplace.trade.TradePage.money(total)));
		}
		return total;
	}

	private static int count(List<ItemStack> stacks) {
		return stacks.stream().mapToInt(ItemStack::getCount).sum();
	}

	/** "32 × Oak Log, 16 × Bread". */
	public static Component describe(List<ItemStack> goods) {
		net.minecraft.network.chat.MutableComponent out = Component.empty();
		for (int i = 0; i < goods.size(); i++) {
			if (i > 0) {
				out.append(", ");
			}
			out.append(Component.translatable("chronicle.aliveworkplace.goods", goods.get(i).getCount(), goods.get(i).getHoverName()));
		}
		return out;
	}

	private Caravans() {
	}
}
