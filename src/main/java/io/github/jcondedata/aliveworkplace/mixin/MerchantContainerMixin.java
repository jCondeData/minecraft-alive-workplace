package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.shop.Shops;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A shopkeeper's trade screen offers a lot only while the shop's chests hold it (B348): whenever the screen fills its
 * result slot (payment put in, or a sale just taken), a lot the chests no longer hold is taken back out of the slot and
 * the offer shows as sold out, so goods never come from nothing.
 */
@Mixin(MerchantContainer.class)
abstract class MerchantContainerMixin {
	@Shadow
	@Final
	private Merchant merchant;

	@Shadow
	@Final
	private NonNullList<ItemStack> itemStacks;

	@Shadow
	public abstract MerchantOffer getActiveOffer();

	@Inject(method = "updateSellItem", at = @At("RETURN"))
	private void aliveworkplace$shopStock(CallbackInfo ci) {
		ItemStack result = itemStacks.get(2);
		if (!result.isEmpty() && merchant instanceof Villager villager && villager.level() instanceof ServerLevel level
				&& Shops.isShopkeeper(villager) && !Shops.inStock(level, villager, result)) {
			itemStacks.set(2, ItemStack.EMPTY);
			Shops.soldOut(villager, getActiveOffer());
		}
	}
}
