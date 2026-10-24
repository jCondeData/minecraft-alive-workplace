package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.threat.Threats;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A threat's mob (a lair's band, a raider) never goes through a portal (B373): a piglin outpost's band wandering into
 * its own lit portal left for the Nether. See {@link Threats#staysOffPortals}.
 */
@Mixin(LivingEntity.class)
public abstract class ThreatPortalMixin {
	@Inject(method = "canUsePortal", at = @At("HEAD"), cancellable = true)
	private void aliveworkplace$threatsStay(boolean allowPassengers, CallbackInfoReturnable<Boolean> cir) {
		if (Threats.staysOffPortals((LivingEntity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
