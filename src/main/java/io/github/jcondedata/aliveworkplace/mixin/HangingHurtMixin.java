package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.threat.ThreatDamage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * B743: a stranger's arrow, trident or potion can't knock the item out of an item frame, or break a frame or painting,
 * inside a protected village ({@link VillageProtection#mayBeHurt}). Living things are checked through the damage event;
 * frames and paintings aren't living, so their own {@code hurt} (an item frame's, and the one paintings and empty frames
 * share) asks here. Melee hits are refused before they get this far. B1053: a threat's blast or shot never breaks one
 * hung on the builders' work ({@link ThreatDamage#sparesHanging}).
 */
@Mixin({ItemFrame.class, BlockAttachedEntity.class})
public abstract class HangingHurtMixin {
	@Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
	private void aliveworkplace$protectedVillage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		Entity self = (Entity) (Object) this;
		if (self.level().isClientSide()) {
			return;
		}
		if (!VillageProtection.mayBeHurt(self, source)
			|| self instanceof HangingEntity hanging && self.level() instanceof ServerLevel level && ThreatDamage.sparesHanging(level, hanging, source)) {
			cir.setReturnValue(false);
		}
	}
}
