package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.threat.ThreatDamage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * B1080: a threat's blast or shot never breaks an armor stand on the builders' work ({@link ThreatDamage#sparesStand}),
 * as {@link HangingHurtMixin} spares their item frames and paintings (B1053). An armor stand's own {@code hurt} breaks it
 * on any explosion or arrow, and it isn't covered by the living-damage event, so it asks here; a threat's explosion
 * passes it by altogether, so it isn't pushed off its spot either.
 */
@Mixin(ArmorStand.class)
public abstract class ArmorStandThreatMixin {
	@Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
	private void aliveworkplace$spareFromThreats(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		ArmorStand self = (ArmorStand) (Object) this;
		if (self.level() instanceof ServerLevel level && ThreatDamage.sparesStand(level, self, source)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "ignoreExplosion", at = @At("HEAD"), cancellable = true)
	private void aliveworkplace$threatBlastPasses(Explosion explosion, CallbackInfoReturnable<Boolean> cir) {
		ArmorStand self = (ArmorStand) (Object) this;
		if (self.level() instanceof ServerLevel level && ThreatDamage.sparesStand(level, self, explosion)) {
			cir.setReturnValue(true);
		}
	}
}
