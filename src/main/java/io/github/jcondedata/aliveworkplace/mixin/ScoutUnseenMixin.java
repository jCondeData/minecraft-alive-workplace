package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.guard.Scouts;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A lair's band doesn't go for a scout more than 8 blocks off (32.12, {@code guard/Scouts.unseen}). Target goals and
 * sensors all ask {@code canAttack}, so a scout there is never picked, and one already picked is let go.
 */
@Mixin(LivingEntity.class)
abstract class ScoutUnseenMixin {
	@Inject(method = "canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
	private void aliveworkplace$scoutUnseen(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!self.level().isClientSide() && Scouts.unseen(self, target)) {
			cir.setReturnValue(false);
		}
	}
}
