package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.hall.CaravanSights;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A caravan's carter and pack llamas (33.7, {@code hall/CaravanSights}) are only a sight, so nothing hurts them; but
 * damage that goes through that ({@code /kill}, the void) still kills them, and a killed member leaves nothing behind
 * (B431): no leather, no chest, no carpet, no experience. The goods travel the saved way and are never on them.
 */
@Mixin(LivingEntity.class)
abstract class CaravanPartyLootMixin {
	@Inject(method = "dropAllDeathLoot", at = @At("HEAD"), cancellable = true)
	private void aliveworkplace$nothingToPickUp(ServerLevel level, DamageSource source, CallbackInfo ci) {
		if (CaravanSights.isParty((LivingEntity) (Object) this)) {
			ci.cancel();
		}
	}
}
