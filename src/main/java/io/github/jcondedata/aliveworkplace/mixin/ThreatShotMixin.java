package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.threat.ThreatDamage;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks a raider's shot when it is shot (B1066, B1064): every arrow, trident, fireball or rocket gets its owner through
 * {@code setOwner}, from its constructor or a deflection, so {@link ThreatDamage#shot} tags it then. The tag is saved
 * with the projectile, so it still counts as a threat's after its raider is discarded, killed or unloaded, and after a
 * save and reload.
 */
@Mixin(Projectile.class)
public abstract class ThreatShotMixin {
	@Inject(method = "setOwner", at = @At("TAIL"))
	private void aliveworkplace$markARaidersShot(Entity owner, CallbackInfo ci) {
		ThreatDamage.shot((Projectile) (Object) this, owner);
	}
}
