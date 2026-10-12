package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.threat.ThreatDamage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A threat's mob never breaks a builder's block (ROADMAP 32.16a): a ravager trampling a roof's leaves, any raider's
 * {@code destroyBlock}. Only a siege's ram on its gate block gets through ({@link ThreatDamage}).
 */
@Mixin(Level.class)
public abstract class ThreatGriefMixin {
	@Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z", at = @At("HEAD"), cancellable = true)
	private void aliveworkplace$spareTheBuilders(BlockPos pos, boolean drop, Entity by, int depth, CallbackInfoReturnable<Boolean> cir) {
		if (by != null && (Object) this instanceof ServerLevel level && ThreatDamage.spares(level, pos, by)) {
			cir.setReturnValue(false);
		}
	}
}
