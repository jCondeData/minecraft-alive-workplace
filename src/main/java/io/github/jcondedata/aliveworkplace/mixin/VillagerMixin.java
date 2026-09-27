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
	@Inject(method = "registerBrainGoals", at = @At("TAIL"))
	private void aliveworkplace$builderSchedule(Brain<Villager> brain, CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		if (!self.isBaby() && (ModVillagers.isWorker(self.getVillagerData().getProfession())
			|| io.github.jcondedata.aliveworkplace.farm.Fields.isFarmer(self) && io.github.jcondedata.aliveworkplace.farm.Fields.hasField(self)
			|| io.github.jcondedata.aliveworkplace.fish.Fishers.isFisherman(self) && io.github.jcondedata.aliveworkplace.fish.Fishers.isHired(self))) {
			brain.setSchedule(ModVillagers.BUILDER_SCHEDULE);
			brain.updateActivityFromSchedule(self.level().getDayTime(), self.level().getGameTime());
		}
	}
}
