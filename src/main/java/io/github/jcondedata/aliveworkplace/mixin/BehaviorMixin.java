package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.command.OurTickCost;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Times our job behaviours' start, tick and stop for the benchmark (ROADMAP 25.3, {@link OurTickCost}). Off unless a
 * benchmark records: then each call is one field read. Vanilla's behaviours are never counted.
 */
@Mixin(Behavior.class)
abstract class BehaviorMixin {
	@Inject(method = "tryStart", at = @At("HEAD"))
	private void aliveworkplace$costStartIn(ServerLevel level, LivingEntity owner, long gameTime, CallbackInfoReturnable<Boolean> cir) {
		OurTickCost.enter(this);
	}

	@Inject(method = "tryStart", at = @At("RETURN"))
	private void aliveworkplace$costStartOut(ServerLevel level, LivingEntity owner, long gameTime, CallbackInfoReturnable<Boolean> cir) {
		OurTickCost.exit(this);
	}

	@Inject(method = "tickOrStop", at = @At("HEAD"))
	private void aliveworkplace$costTickIn(ServerLevel level, LivingEntity owner, long gameTime, CallbackInfo ci) {
		OurTickCost.enter(this);
	}

	@Inject(method = "tickOrStop", at = @At("RETURN"))
	private void aliveworkplace$costTickOut(ServerLevel level, LivingEntity owner, long gameTime, CallbackInfo ci) {
		OurTickCost.exit(this);
	}

	@Inject(method = "doStop", at = @At("HEAD"))
	private void aliveworkplace$costStopIn(ServerLevel level, LivingEntity owner, long gameTime, CallbackInfo ci) {
		OurTickCost.enter(this);
	}

	@Inject(method = "doStop", at = @At("RETURN"))
	private void aliveworkplace$costStopOut(ServerLevel level, LivingEntity owner, long gameTime, CallbackInfo ci) {
		OurTickCost.exit(this);
	}
}
