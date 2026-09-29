package io.github.jcondedata.aliveworkplace.shop;

import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Players;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * Player-run shops: a Shopkeeper sells what the chests near their Shop Counter hold, at the prices on the
 * counter's price list, and puts the payment in those chests. Offers are rebuilt from the stock every time
 * someone talks to the shopkeeper, so what's for sale is always what's there.
 */
public final class Shops {
	/** Most sales one offer allows before the shop needs restocking (vanilla shows it as sold out). */
	private static final int MAX_USES = 999;

	public static boolean isShopkeeper(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == ModVillagers.SHOPKEEPER;
	}

	/** The shopkeeper's counter, if it is still there. */
	public static Optional<ShopCounterBlockEntity> counter(ServerLevel level, Villager villager) {
		return Builders.benchPos(villager).map(level::getBlockEntity)
			.filter(ShopCounterBlockEntity.class::isInstance).map(ShopCounterBlockEntity.class::cast);
	}

	/** Replaces the shopkeeper's offers with one per priced column that has stock. */
	public static void refreshOffers(ServerLevel level, Villager villager) {
		MerchantOffers offers = villager.getOffers();
		offers.clear();
		ShopCounterBlockEntity counter = counter(level, villager).orElse(null);
		if (counter == null) {
			return;
		}
		List<BlockPos> stock = SupplyContainers.find(level, counter.getBlockPos(), null);
		for (int column = 0; column < ShopCounterBlockEntity.COLUMNS; column++) {
			ItemStack goods = counter.goods(column);
			ItemStack price = counter.price(column);
			if (goods.isEmpty() || price.isEmpty()) {
				continue;
			}
			long available = SupplyContainers.countMatching(level, stock, goods) / goods.getCount();
			if (available < 1) {
				continue;
			}
			// A Price Tag can't be handed over: the vanilla trade screen asks for the same price in emeralds.
			ItemCost cost = PriceTagItem.dollars(price) > 0 ? new ItemCost(net.minecraft.world.item.Items.EMERALD, emeraldPrice(price))
				: new ItemCost(price.getItem(), price.getCount());
			offers.add(new MerchantOffer(cost, goods.copy(), (int) Math.min(available, MAX_USES), 1, 0f));
		}
	}

	/** A sale went through: take the goods out of stock and put the payment in the chests. */
	public static void onSale(ServerLevel level, Villager villager, MerchantOffer offer) {
		ShopCounterBlockEntity counter = counter(level, villager).orElse(null);
		if (counter == null) {
			return;
		}
		BlockPos at = counter.getBlockPos();
		List<BlockPos> chests = SupplyContainers.find(level, at, null);
		ItemStack goods = offer.getResult();
		SupplyContainers.extractMatching(level, chests, goods, goods.getCount());
		store(level, chests, at, offer.getCostA().copy());
		store(level, chests, at, offer.getCostB().copy());
		ModAttachments.SHOP_SALES.set(villager, ModAttachments.SHOP_SALES.getOrElse(villager, 0) + 1);
		net.minecraft.world.entity.player.Player buyer = villager.getTradingPlayer();
		counter.logSale(new ShopCounterBlockEntity.Sale(level.getDayTime() / 24000L, buyer == null ? "?" : buyer.getGameProfile().getName(),
			goods.copy(), 0, offer.getCostA().copy()));
	}

	// --- paying in CobbleDollars ------------------------------------------------------------------

	/** First slot of the row of goods in the shop menu. */
	public static final int FIRST_GOODS_SLOT = 18;
	private static final int INFO_SLOT = 4;

	/** The price of a column in CobbleDollars (emeralds, or a Price Tag), or -1 if it can only be paid in items. */
	public static long dollarPrice(ItemStack price) {
		long tag = PriceTagItem.dollars(price);
		if (tag > 0) {
			return tag;
		}
		return price.is(net.minecraft.world.item.Items.EMERALD) && price.getComponentsPatch().isEmpty()
			? (long) price.getCount() * io.github.jcondedata.aliveworkplace.work.Money.DOLLARS_PER_EMERALD : -1;
	}

	/** The same price in emeralds (for a Price Tag: converted, rounded up), or -1 for an item price. */
	public static int emeraldPrice(ItemStack price) {
		long tag = PriceTagItem.dollars(price);
		if (tag > 0) {
			return PriceTagItem.emeralds(tag);
		}
		return dollarPrice(price) >= 0 ? price.getCount() : -1;
	}

	/** Opens the shop's own screen (with CobbleDollars installed): the goods in stock, bought with two clicks. */
	public static void openMenu(net.minecraft.server.level.ServerPlayer player, Villager villager) {
		if (!(villager.level() instanceof ServerLevel level) || counter(level, villager).isEmpty()) {
			Chat.actionBar(player, net.minecraft.network.chat.Component.translatable("message.aliveworkplace.shop.closed")
				.withStyle(net.minecraft.ChatFormatting.YELLOW));
			return;
		}
		int[] pending = {-1};
		io.github.jcondedata.aliveworkplace.work.ChoiceMenu.open(player, villager.getDisplayName(),
			p -> villager.isAlive() && !villager.isSleeping() && p.isAlive() && p.distanceTo(villager) <= 8,
			menu -> renderMenu(menu, player, villager, pending));
	}

	/** The shop screen without showing it (tests). */
	public static io.github.jcondedata.aliveworkplace.work.ChoiceMenu menuForTest(net.minecraft.server.level.ServerPlayer player, Villager villager) {
		int[] pending = {-1};
		return io.github.jcondedata.aliveworkplace.work.ChoiceMenu.detached(player, menu -> renderMenu(menu, player, villager, pending));
	}

	private static void renderMenu(io.github.jcondedata.aliveworkplace.work.ChoiceMenu menu, net.minecraft.server.level.ServerPlayer player,
			Villager villager, int[] pending) {
		menu.clearButtons();
		ServerLevel level = (ServerLevel) villager.level();
		ShopCounterBlockEntity counter = counter(level, villager).orElse(null);
		if (counter == null) {
			return;
		}
		List<BlockPos> stock = SupplyContainers.find(level, counter.getBlockPos(), null);
		int shown = 0;
		for (int column = 0; column < ShopCounterBlockEntity.COLUMNS; column++) {
			ItemStack goods = counter.goods(column);
			ItemStack price = counter.price(column);
			if (goods.isEmpty() || price.isEmpty()) {
				continue;
			}
			long available = SupplyContainers.countMatching(level, stock, goods) / goods.getCount();
			if (available < 1) {
				continue;
			}
			shown++;
			int col = column;
			boolean chosen = pending[0] == column;
			ItemStack icon = goods.copy();
			List<net.minecraft.network.chat.Component> lines = new java.util.ArrayList<>();
			lines.add(net.minecraft.network.chat.Component.translatable("message.aliveworkplace.shop.menu.price", priceText(price))
				.withStyle(canPay(player, price) ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED));
			lines.add(net.minecraft.network.chat.Component.translatable("message.aliveworkplace.shop.menu.stock", available)
				.withStyle(net.minecraft.ChatFormatting.GRAY));
			lines.add(chosen
				? net.minecraft.network.chat.Component.translatable("message.aliveworkplace.shop.menu.confirm", goods.getCount(), goods.getHoverName())
					.withStyle(net.minecraft.ChatFormatting.YELLOW)
				: net.minecraft.network.chat.Component.translatable("message.aliveworkplace.shop.menu.click").withStyle(net.minecraft.ChatFormatting.GRAY));
			icon.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(
				lines.stream().map(l -> (net.minecraft.network.chat.Component) l.copy().withStyle(st -> st.withItalic(false))).toList()));
			if (chosen) {
				icon.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			}
			menu.button(FIRST_GOODS_SLOT + column, icon, p -> {
				if (pending[0] != col) {
					pending[0] = col;
				} else {
					pending[0] = -1;
					buy(p, villager, col);
				}
				renderMenu(menu, player, villager, pending);
			});
		}
		menu.divider(1);
		ItemStack info = new ItemStack(net.minecraft.world.item.Items.EMERALD);
		info.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.translatable(
			"message.aliveworkplace.shop.menu.title", counter.ownerName()).withStyle(st -> st.withItalic(false).withColor(net.minecraft.ChatFormatting.WHITE)));
		info.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(java.util.stream.Stream.of(
				net.minecraft.network.chat.Component.translatable("message.aliveworkplace.tutor.info_money",
					io.github.jcondedata.aliveworkplace.work.Money.balance(player)).withStyle(net.minecraft.ChatFormatting.GREEN),
				shown == 0
					? net.minecraft.network.chat.Component.translatable("message.aliveworkplace.shop.menu.empty").withStyle(net.minecraft.ChatFormatting.YELLOW)
					: net.minecraft.network.chat.Component.translatable("message.aliveworkplace.shop.menu.help").withStyle(net.minecraft.ChatFormatting.GRAY),
				net.minecraft.network.chat.Component.translatable("message.aliveworkplace.shop.menu.items").withStyle(net.minecraft.ChatFormatting.DARK_GRAY))
			.map(l -> (net.minecraft.network.chat.Component) l.copy().withStyle(st -> st.withItalic(false))).toList()));
		menu.button(INFO_SLOT, info, null);
	}

	/** "300 CobbleDollars" for an emerald price, otherwise the items ("2 Diamond"). */
	public static net.minecraft.network.chat.Component priceText(ItemStack price) {
		long dollars = dollarPrice(price);
		if (dollars >= 0 && (io.github.jcondedata.aliveworkplace.work.Money.cobbleDollars() || PriceTagItem.dollars(price) > 0)) {
			return io.github.jcondedata.aliveworkplace.work.Money.describe(dollars, emeraldPrice(price));
		}
		return net.minecraft.network.chat.Component.translatable("message.aliveworkplace.shop.items", price.getCount(), price.getHoverName());
	}

	private static boolean canPay(net.minecraft.server.level.ServerPlayer player, ItemStack price) {
		long dollars = dollarPrice(price);
		if (dollars >= 0) {
			return io.github.jcondedata.aliveworkplace.work.Money.canAfford(player, dollars, emeraldPrice(price));
		}
		return player.getAbilities().instabuild || countLike(player, price) >= price.getCount();
	}

	private static int countLike(net.minecraft.server.level.ServerPlayer player, ItemStack price) {
		int n = 0;
		for (ItemStack s : Players.mainItems(player)) {
			if (ItemStack.isSameItemSameComponents(s, price)) {
				n += s.getCount();
			}
		}
		return n;
	}

	/**
	 * Buys one lot of column {@code column} for {@code player}: pays (CobbleDollars for emerald prices, else the
	 * price items from their inventory), takes the goods out of the shop's chests into the player's inventory,
	 * pays the owner (CobbleDollars straight to them, items into the chests) and logs the sale.
	 */
	public static boolean buy(net.minecraft.server.level.ServerPlayer player, Villager villager, int column) {
		ServerLevel level = (ServerLevel) villager.level();
		ShopCounterBlockEntity counter = counter(level, villager).orElse(null);
		if (counter == null || column < 0 || column >= ShopCounterBlockEntity.COLUMNS) {
			return false;
		}
		ItemStack goods = counter.goods(column).copy();
		ItemStack price = counter.price(column).copy();
		if (goods.isEmpty() || price.isEmpty()) {
			return false;
		}
		BlockPos at = counter.getBlockPos();
		List<BlockPos> chests = SupplyContainers.find(level, at, null);
		if (SupplyContainers.countMatching(level, chests, goods) < goods.getCount()) {
			Chat.actionBar(player, net.minecraft.network.chat.Component.translatable("message.aliveworkplace.shop.sold_out", goods.getHoverName())
				.withStyle(net.minecraft.ChatFormatting.YELLOW));
			return false;
		}
		long dollars = dollarPrice(price);
		boolean inDollars = dollars >= 0 && io.github.jcondedata.aliveworkplace.work.Money.cobbleDollars();
		// A Price Tag without CobbleDollars: paid in emeralds, which go into the shop's chests.
		boolean tagInEmeralds = !inDollars && PriceTagItem.dollars(price) > 0;
		if (inDollars || tagInEmeralds) {
			if (!io.github.jcondedata.aliveworkplace.work.Money.charge(player, dollars, emeraldPrice(price))) {
				Chat.actionBar(player, net.minecraft.network.chat.Component.translatable("message.aliveworkplace.shop.too_poor", priceText(price))
					.withStyle(net.minecraft.ChatFormatting.RED));
				return false;
			}
		} else if (!player.getAbilities().instabuild) {
			if (countLike(player, price) < price.getCount()) {
				Chat.actionBar(player, net.minecraft.network.chat.Component.translatable("message.aliveworkplace.shop.too_poor", priceText(price))
					.withStyle(net.minecraft.ChatFormatting.RED));
				return false;
			}
			player.getInventory().clearOrCountMatchingItems(s -> ItemStack.isSameItemSameComponents(s, price), price.getCount(),
				player.inventoryMenu.getCraftSlots());
		}
		int taken = SupplyContainers.extractMatching(level, chests, goods, goods.getCount());
		ItemStack bought = goods.copyWithCount(Math.max(1, taken));
		if (!player.getInventory().add(bought)) {
			player.drop(bought, false);
		}
		if (inDollars) {
			if (counter.owner() != null) {
				ShopLedger.payOwner(level.getServer(), counter.owner(), dollars);
				tellOwner(level, counter, player, goods, priceText(price));
			}
		} else if (tagInEmeralds) {
			store(level, chests, at, new ItemStack(net.minecraft.world.item.Items.EMERALD, emeraldPrice(price)));
		} else {
			store(level, chests, at, price.copy());
		}
		ModAttachments.SHOP_SALES.set(villager, ModAttachments.SHOP_SALES.getOrElse(villager, 0) + 1);
		counter.logSale(new ShopCounterBlockEntity.Sale(level.getDayTime() / 24000L, player.getGameProfile().getName(), goods,
			inDollars ? dollars : 0, inDollars ? ItemStack.EMPTY : tagInEmeralds ? new ItemStack(net.minecraft.world.item.Items.EMERALD, emeraldPrice(price)) : price));
		level.playSound(null, villager, net.minecraft.sounds.SoundEvents.VILLAGER_YES, net.minecraft.sounds.SoundSource.NEUTRAL, 0.6f, 1f);
		Chat.actionBar(player, net.minecraft.network.chat.Component.translatable("message.aliveworkplace.shop.bought",
			goods.getCount(), goods.getHoverName(), priceText(price)).withStyle(net.minecraft.ChatFormatting.GREEN));
		return true;
	}

	private static void tellOwner(ServerLevel level, ShopCounterBlockEntity counter, net.minecraft.server.level.ServerPlayer buyer, ItemStack goods,
			net.minecraft.network.chat.Component price) {
		net.minecraft.server.level.ServerPlayer owner = counter.owner() == null ? null : level.getServer().getPlayerList().getPlayer(counter.owner());
		if (owner != null && owner != buyer) {
			Chat.actionBar(owner, net.minecraft.network.chat.Component.translatable("message.aliveworkplace.shop.owner_sale",
				buyer.getGameProfile().getName(), goods.getCount(), goods.getHoverName(), price).withStyle(net.minecraft.ChatFormatting.GREEN));
		}
	}

	/** The sales log in chat (the owner sneak-right-clicks the counter). */
	public static void sendLog(net.minecraft.server.level.ServerPlayer player, ShopCounterBlockEntity counter) {
		player.sendSystemMessage((io.github.jcondedata.aliveworkplace.work.Money.cobbleDollars()
			? net.minecraft.network.chat.Component.translatable("message.aliveworkplace.shop.log.header", counter.totalSales(),
				io.github.jcondedata.aliveworkplace.work.Money.describe(counter.totalDollars(), 0))
			: net.minecraft.network.chat.Component.translatable("message.aliveworkplace.shop.log.header_items", counter.totalSales()))
			.withStyle(net.minecraft.ChatFormatting.GOLD));
		List<ShopCounterBlockEntity.Sale> sales = counter.sales();
		if (sales.isEmpty()) {
			player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.aliveworkplace.shop.log.none")
				.withStyle(net.minecraft.ChatFormatting.GRAY));
			return;
		}
		for (int i = 0; i < Math.min(10, sales.size()); i++) {
			ShopCounterBlockEntity.Sale sale = sales.get(i);
			net.minecraft.network.chat.Component paid = sale.dollars() > 0
				? io.github.jcondedata.aliveworkplace.work.Money.describe(sale.dollars(), 0)
				: net.minecraft.network.chat.Component.translatable("message.aliveworkplace.shop.items", sale.paid().getCount(), sale.paid().getHoverName());
			player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.aliveworkplace.shop.log.line", sale.day(), sale.buyer(),
				sale.goods().getCount(), sale.goods().getHoverName(), paid).withStyle(net.minecraft.ChatFormatting.GRAY));
		}
	}

	private static void store(ServerLevel level, List<BlockPos> chests, BlockPos near, ItemStack stack) {
		ItemStack rest = SupplyContainers.insert(level, chests, stack);
		if (!rest.isEmpty()) {
			ItemEntity item = new ItemEntity(level, near.getX() + 0.5, near.getY() + 1.1, near.getZ() + 0.5, rest);
			item.setDefaultPickUpDelay();
			level.addFreshEntity(item);
		}
	}

	private Shops() {
	}
}
