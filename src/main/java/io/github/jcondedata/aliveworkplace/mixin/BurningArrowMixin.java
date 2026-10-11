package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.threat.VillageFire;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A raider's burning arrow may light the builder's wooden block it hits (ROADMAP 32.16, the pirate gunners of 32.8). */
@Mixin(AbstractArrow.class)
public abstract class BurningArrowMixin extends Projectile {
	protected BurningArrowMixin(EntityType<? extends Projectile> type, Level level) {
		super(type, level);
	}

	@Inject(method = "onHitBlock", at = @At("HEAD"))
	private void aliveworkplace$lightsTheRoof(BlockHitResult hit, CallbackInfo ci) {
		if (isOnFire() && level() instanceof ServerLevel level) {
			VillageFire.arrow(level, getOwner(), hit, level.random);
		}
	}
}
