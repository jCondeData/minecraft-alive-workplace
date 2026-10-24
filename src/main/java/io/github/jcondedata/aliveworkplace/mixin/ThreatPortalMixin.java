package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.threat.Threats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A threat's mob, and anything carrying one, never goes through a portal (B373, B375): a piglin outpost's band
 * wandering into its own lit portal left for the Nether, and a raider's untagged mount carried it through. Both
 * {@link Entity} (a boat or minecart carrying a raider) and {@link LivingEntity}'s override are guarded. See
 * {@link Threats#staysOffPortals}.
 */
@Mixin({Entity.class, LivingEntity.class})
public abstract class ThreatPortalMixin {
	@Inject(method = "canUsePortal", at = @At("HEAD"), cancellable = true)
	private void aliveworkplace$threatsStay(boolean allowPassengers, CallbackInfoReturnable<Boolean> cir) {
		if (Threats.staysOffPortals((Entity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
