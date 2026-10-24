package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.shop.Shops;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.trading.Merchant;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * The moment of the trade (B348): a shopkeeper's lot can't be taken from the trade screen when the shop's chests no
 * longer hold it (emptied while the screen was open). The slot is refreshed instead, which empties it and shows the
 * offer as sold out ({@link MerchantContainerMixin}).
 */
@Mixin(MerchantResultSlot.class)
abstract class MerchantResultSlotMixin extends Slot {
	@Shadow
	@Final
	private MerchantContainer slots;

	@Shadow
	@Final
	private Merchant merchant;

	private MerchantResultSlotMixin(Container container, int slot, int x, int y) {
		super(container, slot, x, y);
	}

	@Override
	public boolean mayPickup(Player player) {
		if (merchant instanceof Villager villager && villager.level() instanceof ServerLevel level && Shops.isShopkeeper(villager)
				&& !Shops.inStock(level, villager, getItem())) {
			slots.updateSellItem();
			return false;
		}
		return super.mayPickup(player);
	}
}
