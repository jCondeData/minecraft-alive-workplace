package io.github.jcondedata.aliveworkplace.trade;

import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Tithe;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Caravans that trade (ROADMAP 33.6, design note M33). Besides what the other village is waiting for, which travels
 * free as it always did, a caravan carries up to {@link Caravans#TRADE_STACKS} stacks of goods to sell:
 * <ul>
 * <li><b>What goes</b> ({@link #plan}): goods its village is known for that the other village is short of, or pays
 * at least 10% more for ({@link #DEARER_PERCENT}); whole bundles of one item a stack, out of the Storehouses' chests,
 * which keep {@link Caravans#KEEP} of every item back. Items the other village is waiting for aren't sold: those go
 * free.</li>
 * <li><b>On arrival</b> ({@link #sell}): the receiving village's treasury pays the sender's, bundle by bundle, at the
 * receiver's board price, and both boards move as if the goods had been traded there (2% a bundle: down where they
 * were sold, up where they came from). Bundles the treasury can't pay for go home in a caravan of their own; bundles
 * the Storehouse has no room for wait on the road, like any cargo.</li>
 * <li><b>The seller's side</b> ({@link #book}): the money reaches its treasury (up to its cap) and its chronicle says
 * "Sold 32 Timber to Ashford for 2.6 emeralds", at once if its hall is loaded, else in its next round.</li>
 * </ul>
 * Treasuries are kept in hundredths of an emerald, so village to village nothing is rounded; players read the sums
 * through {@link TradePage#money} (CobbleDollars with the pack). Off ({@code villageEconomy}): nothing is loaded to
 * sell, and goods for sale already on the road go home unsold.
 */
public final class CaravanTrade {
	/** The other village pays "more" for a good from this many percent of our price on. */
	public static final int DEARER_PERCENT = 110;

	/** One stack a caravan would carry to sell: {@code bundles} of {@code good} as {@code item}, earning {@code cents} at the other board's price now. */
	public record Offer(TradeGoods.Good good, Item item, int bundles, int cents) {
		public int items() {
			return bundles * good.bundle();
		}
	}

	/** What came of a caravan's goods for sale: {@code waiting} found no room yet and stay on the road; {@code home} weren't bought and go back. */
	public record Outcome(List<Caravans.Lot> waiting, List<Caravans.Lot> home) {
		public static final Outcome NONE = new Outcome(List.of(), List.of());
	}

	/**
	 * What a caravan from {@code from} would carry to sell at {@code to} out of {@code stock} (copies of what
	 * {@code from}'s Storehouses hold), best first: goods the other village is short of before those it only pays more
	 * for, then by how much more it pays. Empty with the economy off or either village off the caravans' list.
	 */
	public static List<Offer> plan(ServerLevel level, BlockPos from, BlockPos to, Caravans.Data data, List<ItemStack> stock) {
		Caravans.Village them = data.village(to);
		if (!Economy.ENABLED || them == null || data.village(from) == null || stock.isEmpty()) {
			return List.of();
		}
		Set<Item> free = new HashSet<>();
		them.wants().forEach(w -> free.add(w.item()));
		Market theirs = data.market(to);
		record Candidate(Offer offer, boolean shortThere, double ratio, int order) {
		}
		List<Candidate> candidates = new ArrayList<>();
		List<ResourceLocation> known = data.market(from).knownFor();
		for (int i = 0; i < known.size(); i++) {
			TradeGoods.Good good = TradeGoods.get(known.get(i));
			if (good == null) {
				continue;
			}
			int here = Board.price(level, from, good);
			int there = Board.price(level, to, good);
			boolean shortThere = theirs.shortOf().contains(good.id());
			if (!shortThere && (long) there * 100 < (long) here * DEARER_PERCENT) {
				continue;
			}
			for (Map.Entry<Item, Integer> spare : Board.spare(good, stock).entrySet()) {
				Item item = spare.getKey();
				int bundles = Math.min(spare.getValue(), Math.max(good.bundle(), item.getDefaultMaxStackSize())) / good.bundle();
				if (bundles < 1 || free.contains(item)) {
					continue;
				}
				candidates.add(new Candidate(new Offer(good, item, bundles, earnings(good, there, bundles)), shortThere, there / (double) Math.max(1, here), i));
				break;
			}
		}
		candidates.sort(Comparator.<Candidate>comparingInt(c -> c.shortThere() ? 0 : 1).thenComparingDouble(c -> -c.ratio()).thenComparingInt(Candidate::order));
		return candidates.stream().limit(Caravans.TRADE_STACKS).map(Candidate::offer).toList();
	}

	/** What {@code bundles} of {@code good} earn at a board paying {@code price} for the first: each bundle moves the price 2% down. */
	public static int earnings(TradeGoods.Good good, int price, int bundles) {
		int cents = 0;
		for (int i = 0; i < bundles; i++) {
			cents += price;
			price = Prices.sold(good.basePrice(), price);
		}
		return cents;
	}

	/** Takes today's goods for sale at {@code to} out of {@code chests} ({@code from}'s Storehouses'); empty if nothing goes. */
	public static List<Caravans.Lot> load(ServerLevel level, BlockPos from, BlockPos to, Caravans.Data data, List<BlockPos> chests) {
		if (!Economy.ENABLED || chests.isEmpty()) {
			return List.of();
		}
		List<Caravans.Lot> out = new ArrayList<>();
		for (Offer offer : plan(level, from, to, data, Board.stock(level, chests))) {
			List<ItemStack> stacks = new ArrayList<>();
			for (int i = offer.items(); i > 0; i--) {
				ItemStack one = SupplyContainers.takeOne(level, chests, s -> s.is(offer.item()) && Board.counts(offer.good(), s));
				if (one.isEmpty()) {
					break;
				}
				add(stacks, one);
			}
			int taken = stacks.stream().mapToInt(ItemStack::getCount).sum();
			int odd = taken % offer.good().bundle();
			if (odd > 0) {
				// the chests changed under us: only whole bundles travel
				for (ItemStack back : split(stacks, odd)) {
					SupplyContainers.insert(level, chests, back);
				}
			}
			if (!stacks.isEmpty()) {
				out.add(new Caravans.Lot(offer.good().id(), stacks));
			}
		}
		return out;
	}

	/**
	 * The caravan {@code shipment} has come to {@code hall} with goods to sell: its treasury pays for each bundle its
	 * Storehouses' {@code chests} take, at its board's price, and both boards move. Bundles it can't pay for (all of
	 * them with the economy off, or of a good that is none any more) go home; bundles there's no room for wait. A
	 * sender that has left the caravans' list can't be paid or sent anything: its goods are simply unloaded.
	 */
	public static Outcome sell(ServerLevel level, BlockPos hall, Caravans.Shipment shipment, List<BlockPos> chests, Caravans.Data data) {
		List<Caravans.Lot> waiting = new ArrayList<>();
		List<Caravans.Lot> home = new ArrayList<>();
		Caravans.Village seller = data.village(shipment.from());
		Caravans.Village buyer = data.village(hall);
		if (seller == null) {
			for (Caravans.Lot lot : shipment.sale()) {
				List<ItemStack> rest = new ArrayList<>();
				for (ItemStack stack : lot.stacks()) {
					ItemStack left = chests.isEmpty() ? stack.copy() : SupplyContainers.insert(level, chests, stack.copy());
					if (!left.isEmpty()) {
						rest.add(left);
					}
				}
				if (!rest.isEmpty()) {
					waiting.add(new Caravans.Lot(lot.good(), rest));
				}
			}
			return new Outcome(waiting, home);
		}
		VillageHallBlockEntity entity = level.getBlockEntity(hall) instanceof VillageHallBlockEntity e ? e : null;
		List<ItemStack> unpaid = new ArrayList<>();
		for (Caravans.Lot lot : shipment.sale()) {
			TradeGoods.Good good = TradeGoods.get(lot.good());
			if (!Economy.ENABLED || entity == null || buyer == null || good == null) {
				home.add(lot);
				continue;
			}
			List<ItemStack> left = new ArrayList<>();
			lot.stacks().forEach(s -> left.add(s.copy()));
			int price = Board.price(level, hall, good);
			int cents = 0;
			int done = 0;
			boolean room = true;
			while (left.stream().mapToInt(ItemStack::getCount).sum() >= good.bundle()) {
				if (entity.treasury() - cents < price) {
					break;
				}
				List<ItemStack> bundle = split(left, good.bundle());
				if (!put(level, chests, bundle)) {
					bundle.forEach(s -> add(left, s));
					room = false;
					break;
				}
				cents += price;
				done++;
				price = Prices.sold(good.basePrice(), price);
			}
			if (done > 0) {
				entity.setTreasury(entity.treasury() - cents);
				Board.move(level, hall, good, price, -done);
				int theirs = Board.price(level, shipment.from(), good);
				for (int i = 0; i < done; i++) {
					theirs = Prices.bought(good.basePrice(), theirs);
				}
				Board.move(level, shipment.from(), good, theirs, done);
				int items = done * good.bundle();
				data.sold(new Caravans.Sale(shipment.from(), buyer.name(), good.id(), items, cents, Chronicle.day(level)));
				Chronicle.atHall(level, hall, Chronicle.Kind.CARAVAN,
					Component.translatable("chronicle.aliveworkplace.caravan_bought", items, good.name(), seller.name(), TradePage.money(cents)));
			}
			if (!left.isEmpty()) {
				if (room) {
					home.add(new Caravans.Lot(lot.good(), left));
					unpaid.addAll(left);
				} else {
					waiting.add(new Caravans.Lot(lot.good(), left));
				}
			}
		}
		if (!unpaid.isEmpty()) {
			Chronicle.atHall(level, hall, Chronicle.Kind.CARAVAN,
				Component.translatable("chronicle.aliveworkplace.caravan_unpaid", Caravans.describe(unpaid), seller.name()));
		}
		if (level.isLoaded(shipment.from())) {
			book(level, shipment.from(), data);
		}
		return new Outcome(waiting, home);
	}

	/**
	 * Books what {@code hall}'s caravans have sold since it last did: the money into its treasury (up to its cap, as
	 * all its earnings) and a line in its chronicle for each sale, dated the day of the sale. Its hall must be loaded.
	 */
	public static void book(ServerLevel level, BlockPos hall, Caravans.Data data) {
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return;
		}
		for (Caravans.Sale sale : data.takeSales(hall)) {
			Tithe.put(entity, sale.cents());
			TradeGoods.Good good = TradeGoods.get(sale.good());
			Chronicle.atHall(level, hall, Chronicle.Kind.CARAVAN, Component.translatable("chronicle.aliveworkplace.caravan_sold", sale.items(),
				good == null ? Component.literal(sale.good().getPath()) : good.name(), sale.buyer(), TradePage.money(sale.cents())), sale.day());
		}
	}

	/** The Routes tab's line for a stack that would go: "Timber ×2 for 2.6 emeralds". */
	public static Component line(Offer offer) {
		return Component.translatable("screen.aliveworkplace.hall.route_offer", offer.good().name(), offer.bundles(), TradePage.money(offer.cents()));
	}

	/** Puts every stack of {@code bundle} into {@code chests}; false, with nothing left there, if they have no room for all of it. */
	private static boolean put(ServerLevel level, List<BlockPos> chests, List<ItemStack> bundle) {
		if (chests.isEmpty()) {
			return false;
		}
		List<ItemStack> in = new ArrayList<>();
		for (ItemStack stack : bundle) {
			ItemStack rest = SupplyContainers.insert(level, chests, stack.copy());
			if (rest.getCount() < stack.getCount()) {
				in.add(stack.copyWithCount(stack.getCount() - rest.getCount()));
			}
			if (!rest.isEmpty()) {
				in.forEach(s -> SupplyContainers.extractMatching(level, chests, s, s.getCount()));
				return false;
			}
		}
		return true;
	}

	/** Takes {@code count} items off the end of {@code stacks}. */
	private static List<ItemStack> split(List<ItemStack> stacks, int count) {
		List<ItemStack> out = new ArrayList<>();
		for (int i = stacks.size() - 1; i >= 0 && count > 0; i--) {
			ItemStack stack = stacks.get(i);
			int n = Math.min(count, stack.getCount());
			out.add(stack.split(n));
			count -= n;
			if (stack.isEmpty()) {
				stacks.remove(i);
			}
		}
		return out;
	}

	/** Adds {@code stack} to {@code stacks}, onto a stack of the same kind with room if there is one. */
	private static void add(List<ItemStack> stacks, ItemStack stack) {
		for (ItemStack held : stacks) {
			if (held.getCount() < held.getMaxStackSize() && ItemStack.isSameItemSameComponents(held, stack)) {
				int n = Math.min(stack.getCount(), held.getMaxStackSize() - held.getCount());
				held.grow(n);
				stack.shrink(n);
				if (stack.isEmpty()) {
					return;
				}
			}
		}
		if (!stack.isEmpty()) {
			stacks.add(stack);
		}
	}

	private CaravanTrade() {
	}
}
