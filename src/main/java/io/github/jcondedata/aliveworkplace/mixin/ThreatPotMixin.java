package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.threat.ThreatDamage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A threat's arrow never breaks a builder's decorated pot (ROADMAP 32.16a, B1032). Vanilla cracks the pot first (a
 * {@code setBlock} with {@code cracked=true}) and only then calls {@code destroyBlock}; by then the pot no longer matches
 * the build as drawn, so {@link ThreatGriefMixin} let it go. Asked before the crack, the pot stays whole. A player's own
 * arrow, and anything shot at a player's own pot, follows the game's rules.
 */
@Mixin(DecoratedPotBlock.class)
public abstract class ThreatPotMixin {
	@Inject(method = "onProjectileHit", at = @At("HEAD"), cancellable = true)
	private void aliveworkplace$spareTheBuildersPot(Level level, BlockState state, BlockHitResult hit, Projectile projectile, CallbackInfo ci) {
		if (level instanceof ServerLevel server && ThreatDamage.spares(server, hit.getBlockPos(), projectile)) {
			ci.cancel();
		}
	}
}
