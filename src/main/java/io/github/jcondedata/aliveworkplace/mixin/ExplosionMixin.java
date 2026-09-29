package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.guard.Warding;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Blocks in a warded village are spared by explosions (see {@link Warding}). */
@Mixin(Explosion.class)
public abstract class ExplosionMixin {
	@Shadow
	@Final
	private Level level;

	@Shadow
	@Final
	private ObjectArrayList<BlockPos> toBlow;

	@Inject(method = "finalizeExplosion", at = @At("HEAD"))
	private void aliveworkplace$spareWardedVillages(boolean spawnParticles, CallbackInfo ci) {
		if (level instanceof ServerLevel server) {
			Warding.spare(server, ((Explosion) (Object) this).center(), toBlow);
		}
	}
}
