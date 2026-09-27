package io.github.jcondedata.aliveworkplace.shop;

import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
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
			offers.add(new MerchantOffer(new ItemCost(price.getItem(), price.getCount()), goods.copy(),
				(int) Math.min(available, MAX_USES), 1, 0f));
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
		villager.setAttached(ModAttachments.SHOP_SALES, villager.getAttachedOrElse(ModAttachments.SHOP_SALES, 0) + 1);
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
