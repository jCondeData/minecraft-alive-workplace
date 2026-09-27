package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.guard.Guards;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.VillagerPanicTrigger;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Guards stand their ground: getting hurt or seeing a monster doesn't send them into a panic. */
@Mixin(VillagerPanicTrigger.class)
abstract class VillagerPanicTriggerMixin {
	@Inject(method = "start(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;J)V", at = @At("HEAD"), cancellable = true)
	private void aliveworkplace$guardsDontPanic(ServerLevel level, Villager villager, long gameTime, CallbackInfo ci) {
		if (Guards.isGuard(villager)) {
			ci.cancel();
		}
	}
}
