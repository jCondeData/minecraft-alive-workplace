package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.threat.VillageFire;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A lightning strike in a village in a thunderstorm may set a wooden build's roof alight (ROADMAP 32.16). */
@Mixin(LightningBolt.class)
public abstract class LightningFireMixin extends Entity {
	@Shadow
	private boolean visualOnly;

	protected LightningFireMixin(EntityType<?> type, Level level) {
		super(type, level);
	}

	@Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LightningBolt;powerLightningRod()V"))
	private void aliveworkplace$villageRoof(CallbackInfo ci) {
		if (!visualOnly && level() instanceof ServerLevel level) {
			VillageFire.lightning(level, blockPosition(), level.random);
		}
	}
}
