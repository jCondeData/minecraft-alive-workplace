package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.threat.ThreatDamage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A threat's trident never breaks the pointed dripstone on a builder's build (ROADMAP 32.16a, B1052). Vanilla's
 * {@code onProjectileHit} breaks the spike with {@code destroyBlock(pos, true)}, which names no entity, so
 * {@link ThreatGriefMixin} was never asked. Asked here, as {@link ThreatPotMixin} asks for pots, the spike stays. A wild
 * drowned's or a player's trident follows the game's rules.
 */
@Mixin(PointedDripstoneBlock.class)
public abstract class ThreatDripstoneMixin {
	@Inject(method = "onProjectileHit", at = @At("HEAD"), cancellable = true)
	private void aliveworkplace$spareTheBuildersDripstone(Level level, BlockState state, BlockHitResult hit, Projectile projectile, CallbackInfo ci) {
		if (level instanceof ServerLevel server && ThreatDamage.spares(server, hit.getBlockPos(), projectile)) {
			ci.cancel();
		}
	}
}
