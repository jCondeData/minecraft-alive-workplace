package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.craft.LuxuryRecipes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.item.trading.Merchant;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Goods that age (a Berry Wine) bought from a villager are made the day they're sold (B443): the trade screen's result is
 * stamped with today when it's filled, so the bought wine says "Pressed on day N" and waits its days like the Vintner's own.
 */
@Mixin(MerchantContainer.class)
abstract class MerchantSaleDayMixin {
	@Shadow
	@Final
	private Merchant merchant;

	@Inject(method = "updateSellItem", at = @At("RETURN"))
	private void aliveworkplace$saleDay(CallbackInfo ci) {
		if (merchant instanceof Entity seller && !seller.level().isClientSide) {
			LuxuryRecipes.stampSale(((MerchantContainer) (Object) this).getItem(2), seller.level());
		}
	}
}
