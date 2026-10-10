package io.github.jcondedata.aliveworkplace.trade;

import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Tithe;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Players;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;

/**
 * Trading at the price board (ROADMAP 33.5, design note M33): on the Prices tab a player sells the village a bundle of
 * a good, or buys one, at the board's prices.
 * <ul>
 * <li><b>Selling</b>: the bundle leaves the player's inventory for the Storehouses' chests and the treasury pays the
 * good's price. The village never pays more than its treasury holds, and never takes what its chests have no room
 * for.</li>
 * <li><b>Buying</b>: the bundle comes out of the Storehouses' chests, which keep {@link Caravans#KEEP} of every item
 * back as they do for caravans, and the player pays 10% over the price ({@link TradePage#selling}) into the
 * treasury (up to its cap).</li>
 * <li>Each bundle moves that day's price 2% ({@link Prices#sold}, {@link Prices#bought}); the dawn move goes on from
 * there.</li>
 * <li>Money goes through {@link Money}: CobbleDollars with the pack, to the cent. With emeralds, a trade settles in
 * whole emeralds against the treasury's cents: the player is paid down and charged up to the whole emerald, and the
 * difference stays with the village; a sale that wouldn't come to one emerald isn't made.</li>
 * </ul>
 * What counts as a good's item at the board is {@link #counts}: the good's items, but no worn tools, and of potions only
 * those that heal (so nobody sells the village water bottles as remedies).
 */
public final class Board {
	/** Why a trade stopped, or wasn't made. */
	public enum Stop {
		/** Nothing stopped it. */
		NONE,
		/** The village has no Storehouse with a chest. */
		NO_STOREHOUSE,
		/** The economy is off, or the hall hasn't put the village on the caravans' list yet. */
		NOT_OPEN,
		/** The player doesn't carry a bundle. */
		NO_GOODS,
		/** The treasury can't pay for another bundle. */
		TREASURY,
		/** With emeralds: what the village would pay doesn't come to a whole emerald. */
		SMALL,
		/** The Storehouses' chests have no room for another bundle. */
		ROOM,
		/** The chests hold no other bundle over what they keep back. */
		STOCK,
		/** The player can't pay for another bundle. */
		MONEY,
		/** The player's inventory is full. */
		PACK
	}

	/**
	 * What a click came to: {@code bundles} traded ({@code items} items) for {@code cents} that moved in or out of the
	 * treasury, and why it stopped there ({@link Stop#NONE}: it did all it was asked).
	 */
	public record Result(boolean sale, TradeGoods.Good good, int bundles, int items, int cents, Stop stop) {
		public boolean traded() {
			return bundles > 0;
		}
	}

	/** Whether the board can trade at {@code hall} at all; {@link Stop#NONE} if so. */
	public static Stop open(ServerLevel level, BlockPos hall, List<BlockPos> chests) {
		if (!Economy.ENABLED || Caravans.Data.get(level).village(hall) == null || !(level.getBlockEntity(hall) instanceof VillageHallBlockEntity)) {
			return Stop.NOT_OPEN;
		}
		return chests.isEmpty() ? Stop.NO_STOREHOUSE : Stop.NONE;
	}

	/** Whether {@code stack} is {@code good} as the board trades it: one of its items, not worn, and a potion only if it heals. */
	public static boolean counts(TradeGoods.Good good, ItemStack stack) {
		if (!good.matches(stack) || stack.isDamaged()) {
			return false;
		}
		PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
		if (potion == null) {
			return true;
		}
		for (MobEffectInstance effect : potion.getAllEffects()) {
			if (effect.is(MobEffects.HEAL) || effect.is(MobEffects.REGENERATION)) {
				return true;
			}
		}
		return false;
	}

	/** How many items of {@code good} the player carries (their inventory's 36 slots; not what they wear or hold in the off hand). */
	public static int carried(ServerPlayer player, TradeGoods.Good good) {
		int n = 0;
		for (ItemStack stack : Players.mainItems(player)) {
			if (counts(good, stack)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	/** Copies of every stack in the Storehouses' chests. */
	public static List<ItemStack> stock(ServerLevel level, List<BlockPos> chests) {
		List<ItemStack> out = new ArrayList<>();
		for (BlockPos chest : chests) {
			out.addAll(SupplyContainers.peekMatching(level, chest, s -> true));
		}
		return out;
	}

	/**
	 * What the village can sell of {@code good} from {@code stock}, by item, the item it sells first first: for each of
	 * the good's items, what the chests hold over the {@link Caravans#KEEP} they keep back (and of that only what
	 * {@link #counts}).
	 */
	public static Map<Item, Integer> spare(TradeGoods.Good good, List<ItemStack> stock) {
		Map<Item, Integer> held = new LinkedHashMap<>();
		Map<Item, Integer> sellable = new LinkedHashMap<>();
		for (ItemStack stack : stock) {
			if (good.matches(stack)) {
				held.merge(stack.getItem(), stack.getCount(), Integer::sum);
				if (counts(good, stack)) {
					sellable.merge(stack.getItem(), stack.getCount(), Integer::sum);
				}
			}
		}
		List<Map.Entry<Item, Integer>> spare = new ArrayList<>();
		sellable.forEach((item, n) -> {
			int over = Math.min(n, held.get(item) - Caravans.KEEP);
			if (over > 0) {
				spare.add(Map.entry(item, over));
			}
		});
		ResourceLocation first = good.items().ids().isEmpty() ? null : good.items().ids().get(0);
		spare.sort(Comparator.<Map.Entry<Item, Integer>>comparingInt(e -> BuiltInRegistries.ITEM.getKey(e.getKey()).equals(first) ? 0
				: e.getKey() == good.icon() ? 1 : 2)
			.thenComparing(e -> -e.getValue())
			.thenComparing(e -> BuiltInRegistries.ITEM.getKey(e.getKey()).toString()));
		Map<Item, Integer> out = new LinkedHashMap<>();
		spare.forEach(e -> out.put(e.getKey(), e.getValue()));
		return out;
	}

	/** Bundles of {@code good} the village can sell from {@code stock}. */
	public static int spareBundles(TradeGoods.Good good, List<ItemStack> stock) {
		return spare(good, stock).values().stream().mapToInt(Integer::intValue).sum() / good.bundle();
	}

	/** What the village pays for a bundle of {@code good} now: its price, or its base before it has one. */
	public static int price(ServerLevel level, BlockPos hall, TradeGoods.Good good) {
		Market.Price p = Caravans.Data.get(level).market(hall).prices().get(good.id());
		return p == null ? good.basePrice() : p.cents();
	}

	/** {@code cents} in CobbleDollars. */
	public static long dollars(long cents) {
		return Math.round(cents * (double) Money.DOLLARS_PER_EMERALD / 100);
	}

	/**
	 * {@code player} sells the village a bundle of {@code good} ({@code all}: as many as the player carries, the
	 * treasury can pay for and the chests have room for).
	 */
	public static Result sell(ServerLevel level, BlockPos hall, ServerPlayer player, TradeGoods.Good good, boolean all) {
		List<BlockPos> chests = Caravans.storehouse(level, hall);
		Stop open = open(level, hall, chests);
		if (open != Stop.NONE) {
			return new Result(true, good, 0, 0, 0, open);
		}
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		int carried = carried(player, good);
		if (carried < good.bundle()) {
			return new Result(true, good, 0, 0, 0, Stop.NO_GOODS);
		}
		boolean inDollars = Money.cobbleDollars();
		int treasury = entity.treasury();
		int start = price(level, hall, good);
		// What the treasury can pay for, before anything moves
		int wanted = all ? carried / good.bundle() : 1;
		int planned = 0;
		long owed = 0;
		int price = start;
		Stop stop = Stop.NONE;
		while (planned < wanted) {
			if (owed + price > treasury) {
				stop = Stop.TREASURY;
				break;
			}
			owed += price;
			planned++;
			price = Prices.sold(good.basePrice(), price);
		}
		if (planned == 0) {
			return new Result(true, good, 0, 0, 0, Stop.TREASURY);
		}
		if (!inDollars && owed < 100) {
			return new Result(true, good, 0, 0, 0, stop == Stop.TREASURY ? Stop.TREASURY : Stop.SMALL);
		}
		// The goods, a bundle at a time
		List<ItemStack> delivered = new ArrayList<>();
		int done = 0;
		owed = 0;
		price = start;
		while (done < planned) {
			if (!deliver(level, chests, player, good, delivered)) {
				stop = Stop.ROOM;
				break;
			}
			owed += price;
			done++;
			price = Prices.sold(good.basePrice(), price);
		}
		if (done == 0 || (!inDollars && owed < 100)) {
			// no room for a bundle, or only for less than an emerald's worth: the player keeps the goods
			for (ItemStack stack : delivered) {
				int back = SupplyContainers.extractMatching(level, chests, stack, stack.getCount());
				give(player, stack.copyWithCount(back));
			}
			return new Result(true, good, 0, 0, 0, Stop.ROOM);
		}
		int emeralds = (int) (owed / 100);
		int paid = inDollars ? (int) owed : emeralds * 100;
		entity.setTreasury(treasury - paid);
		Money.pay(player, dollars(owed), emeralds);
		move(level, hall, good, price, -done);
		level.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_TRADE, SoundSource.PLAYERS, 0.8f, 1f);
		return new Result(true, good, done, done * good.bundle(), paid, stop);
	}

	/**
	 * {@code player} buys a bundle of {@code good} from the village ({@code all}: as many as the chests can spare, the
	 * player can pay for and carry).
	 */
	public static Result buy(ServerLevel level, BlockPos hall, ServerPlayer player, TradeGoods.Good good, boolean all) {
		List<BlockPos> chests = Caravans.storehouse(level, hall);
		Stop open = open(level, hall, chests);
		if (open != Stop.NONE) {
			return new Result(false, good, 0, 0, 0, open);
		}
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		int wanted = all ? Integer.MAX_VALUE : 1;
		boolean inDollars = Money.cobbleDollars();
		int price = price(level, hall, good);
		long total = 0;
		int done = 0;
		Stop stop = Stop.NONE;
		while (done < wanted) {
			if (spareBundles(good, stock(level, chests)) < 1) {
				stop = Stop.STOCK;
				break;
			}
			if (done > 0 && player.getInventory().getFreeSlot() < 0) {
				stop = Stop.PACK;
				break;
			}
			int cost = TradePage.selling(price);
			long dueDollars = dollars(total + cost) - dollars(total);
			int dueEmeralds = wholeUp(total + cost) - wholeUp(total);
			if (!Money.canAfford(player, dueDollars, dueEmeralds) || !Money.charge(player, dueDollars, dueEmeralds)) {
				stop = Stop.MONEY;
				break;
			}
			List<ItemStack> bundle = take(level, chests, good);
			if (bundle.stream().mapToInt(ItemStack::getCount).sum() < good.bundle()) {
				// the chests changed under us: everything back where it was
				bundle.forEach(stack -> SupplyContainers.insert(level, chests, stack));
				Money.pay(player, dueDollars, dueEmeralds);
				stop = Stop.STOCK;
				break;
			}
			bundle.forEach(stack -> give(player, stack));
			total += cost;
			done++;
			price = Prices.bought(good.basePrice(), price);
		}
		if (done == 0) {
			return new Result(false, good, 0, 0, 0, stop);
		}
		int paid = inDollars ? (int) total : wholeUp(total) * 100;
		Tithe.put(entity, paid);
		move(level, hall, good, price, done);
		level.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_TRADE, SoundSource.PLAYERS, 0.8f, 1.1f);
		return new Result(false, good, done, done * good.bundle(), paid, all && stop == Stop.STOCK ? Stop.NONE : stop);
	}

	/** {@code cents} in whole emeralds, rounded up (what a buyer is charged). */
	private static int wholeUp(long cents) {
		return (int) ((cents + 99) / 100);
	}

	/**
	 * Moves a bundle of {@code good} from the player's inventory into the chests, adding what went in to
	 * {@code delivered}; false, with nothing moved, if the chests have no room for all of it.
	 */
	private static boolean deliver(ServerLevel level, List<BlockPos> chests, ServerPlayer player, TradeGoods.Good good, List<ItemStack> delivered) {
		NonNullList<ItemStack> items = Players.mainItems(player);
		int need = good.bundle();
		List<int[]> plan = new ArrayList<>();
		for (int slot = 0; slot < items.size() && need > 0; slot++) {
			if (counts(good, items.get(slot))) {
				int n = Math.min(need, items.get(slot).getCount());
				plan.add(new int[]{slot, n});
				need -= n;
			}
		}
		if (need > 0) {
			return false;
		}
		List<ItemStack> put = new ArrayList<>();
		for (int[] part : plan) {
			ItemStack stack = items.get(part[0]).copyWithCount(part[1]);
			ItemStack rest = SupplyContainers.insert(level, chests, stack.copy());
			if (rest.getCount() < part[1]) {
				put.add(stack.copyWithCount(part[1] - rest.getCount()));
			}
			if (!rest.isEmpty()) {
				put.forEach(p -> SupplyContainers.extractMatching(level, chests, p, p.getCount()));
				return false;
			}
		}
		plan.forEach(part -> items.get(part[0]).shrink(part[1]));
		delivered.addAll(put);
		return true;
	}

	/** Takes a bundle of {@code good} out of the chests, the item the board sells first first; less if they can't spare one. */
	private static List<ItemStack> take(ServerLevel level, List<BlockPos> chests, TradeGoods.Good good) {
		List<ItemStack> out = new ArrayList<>();
		int need = good.bundle();
		for (Map.Entry<Item, Integer> e : spare(good, stock(level, chests)).entrySet()) {
			Item item = e.getKey();
			for (int i = Math.min(need, e.getValue()); i > 0; i--) {
				ItemStack one = SupplyContainers.takeOne(level, chests, s -> s.is(item) && counts(good, s));
				if (one.isEmpty()) {
					break;
				}
				need--;
				ItemStack same = out.stream().filter(s -> s.getCount() < s.getMaxStackSize() && ItemStack.isSameItemSameComponents(s, one)).findFirst().orElse(null);
				if (same != null) {
					same.grow(one.getCount());
				} else {
					out.add(one);
				}
			}
			if (need <= 0) {
				break;
			}
		}
		return out;
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!stack.isEmpty() && !player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
	}

	/** Writes {@code good}'s new price after {@code steps} bundles (sold to the village: negative). */
	private static void move(ServerLevel level, BlockPos hall, TradeGoods.Good good, int cents, int steps) {
		Caravans.Data data = Caravans.Data.get(level);
		Market market = data.market(hall);
		Map<ResourceLocation, Market.Price> prices = new LinkedHashMap<>(market.prices());
		Market.Price was = prices.get(good.id());
		prices.put(good.id(), new Market.Price(cents, was == null ? good.basePrice() : was.yesterday(), (was == null ? 0 : was.nudge()) + steps));
		data.setMarket(hall, new Market(market.knownFor(), market.shortOf(), prices, market.priceDay(), market.demand()));
	}

	/** What to tell the player about {@code result}: what was traded and for how much, and what stopped it. */
	public static Component message(ServerLevel level, BlockPos hall, Result result) {
		Component village = VillageHalls.name(level, hall);
		Component good = result.good().name();
		if (!result.traded()) {
			return (switch (result.stop()) {
				case NO_STOREHOUSE -> Component.translatable("message.aliveworkplace.board.no_storehouse", village);
				case NO_GOODS -> Component.translatable("message.aliveworkplace.board.no_goods", good, result.good().bundle());
				case TREASURY -> Component.translatable("message.aliveworkplace.board.treasury", village, good,
					TradePage.money(level.getBlockEntity(hall) instanceof VillageHallBlockEntity e ? e.treasury() : 0));
				case SMALL -> Component.translatable("message.aliveworkplace.board.small", good);
				case ROOM -> Component.translatable("message.aliveworkplace.board.room", village, good);
				case STOCK -> Component.translatable("message.aliveworkplace.board.stock", village, good, Caravans.KEEP);
				case MONEY -> Component.translatable("message.aliveworkplace.board.money", TradePage.money(TradePage.selling(price(level, hall, result.good()))), good);
				default -> Component.translatable("message.aliveworkplace.board.not_open");
			}).withStyle(ChatFormatting.YELLOW);
		}
		MutableComponent out = Component.translatable(result.sale() ? "message.aliveworkplace.board.sold" : "message.aliveworkplace.board.bought",
			result.items(), good, village, TradePage.money(result.cents()));
		String more = switch (result.stop()) {
			case TREASURY -> "message.aliveworkplace.board.more.treasury";
			case ROOM -> "message.aliveworkplace.board.more.room";
			case STOCK -> "message.aliveworkplace.board.more.stock";
			case MONEY -> "message.aliveworkplace.board.more.money";
			case PACK -> "message.aliveworkplace.board.more.pack";
			default -> null;
		};
		if (more != null) {
			out.append(" ").append(Component.translatable(more));
		}
		return out.withStyle(ChatFormatting.GREEN);
	}

	private Board() {
	}
}
