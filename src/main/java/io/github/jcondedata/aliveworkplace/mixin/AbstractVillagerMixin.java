package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.shop.Shops;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A shopkeeper's sale moves real items: the goods out of the shop's chests, the payment into them. Any villager's sale
 * to a player pays the village's tithe (30.8, {@code hall/Tithe}) and counts as a favour (31.5, {@code story/Friendship}).
 */
@Mixin(AbstractVillager.class)
abstract class AbstractVillagerMixin {
	@Inject(method = "notifyTrade", at = @At("HEAD"))
	private void aliveworkplace$shopSale(MerchantOffer offer, CallbackInfo ci) {
		if ((Object) this instanceof Villager villager && villager.level() instanceof ServerLevel level && Shops.isShopkeeper(villager)) {
			Shops.onSale(level, villager, offer);
		}
	}

	@Inject(method = "notifyTrade", at = @At("HEAD"))
	private void aliveworkplace$tithe(MerchantOffer offer, CallbackInfo ci) {
		if ((Object) this instanceof Villager villager && villager.level() instanceof ServerLevel) {
			io.github.jcondedata.aliveworkplace.hall.Tithe.paid(villager, offer);
		}
	}

	/** A trade with a named villager is a favour to their friendship (31.5). */
	@Inject(method = "notifyTrade", at = @At("HEAD"))
	private void aliveworkplace$friendship(MerchantOffer offer, CallbackInfo ci) {
		if ((Object) this instanceof Villager villager && villager.level() instanceof ServerLevel) {
			io.github.jcondedata.aliveworkplace.story.Friendship.onTrade(villager);
		}
	}
}
