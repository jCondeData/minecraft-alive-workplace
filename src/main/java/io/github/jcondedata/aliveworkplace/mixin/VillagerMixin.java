package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Builders keep longer hours than other villagers (see {@link ModVillagers#BUILDER_SCHEDULE}). */
@Mixin(Villager.class)
abstract class VillagerMixin {
	/** A shopkeeper's offers are whatever the shop has in stock right now. */
	@Inject(method = "mobInteract", at = @At("HEAD"))
	private void aliveworkplace$shopStock(net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
			org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.world.InteractionResult> cir) {
		Villager self = (Villager) (Object) this;
		if (self.level() instanceof net.minecraft.server.level.ServerLevel level && !self.isTrading() && !self.isSleeping()) {
			if (io.github.jcondedata.aliveworkplace.shop.Shops.isShopkeeper(self)) {
				io.github.jcondedata.aliveworkplace.shop.Shops.refreshOffers(level, self);
			} else if (io.github.jcondedata.aliveworkplace.travel.Ferrymen.isFerryman(self) && player instanceof net.minecraft.server.level.ServerPlayer sp) {
				io.github.jcondedata.aliveworkplace.travel.Ferrymen.refreshOffers(level, self, sp);
			}
		}
	}

	@Inject(method = "registerBrainGoals", at = @At("TAIL"))
	private void aliveworkplace$builderSchedule(Brain<Villager> brain, CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		if (!self.isBaby() && (ModVillagers.isWorker(self.getVillagerData().getProfession())
			|| io.github.jcondedata.aliveworkplace.farm.Fields.isFarmer(self) && io.github.jcondedata.aliveworkplace.farm.Fields.hasField(self)
			|| io.github.jcondedata.aliveworkplace.fish.Fishers.isFisherman(self) && io.github.jcondedata.aliveworkplace.fish.Fishers.isHired(self))) {
			brain.setSchedule(ModVillagers.BUILDER_SCHEDULE);
			brain.updateActivityFromSchedule(self.level().getDayTime(), self.level().getGameTime());
		} else if (!self.isBaby() && self.getVillagerData().getProfession() == ModVillagers.GUARD) {
			brain.setSchedule(ModVillagers.GUARD_SCHEDULE);
			brain.updateActivityFromSchedule(self.level().getDayTime(), self.level().getGameTime());
		} else if (!self.isBaby() && self.getVillagerData().getProfession() == ModVillagers.BARD) {
			brain.setSchedule(ModVillagers.BARD_SCHEDULE);
			brain.updateActivityFromSchedule(self.level().getDayTime(), self.level().getGameTime());
		}
		io.github.jcondedata.aliveworkplace.guard.Guards.updateHealth(self);
	}
}
