package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.threat.Plague;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The plague's ill keep to their bed after two days (ROADMAP 32.18): a villager's brain wakes it every morning
 * ({@code WakeUp}); while {@link Plague#keepsToBed} holds (alive, bedridden, their bed still standing) it stays asleep.
 */
@Mixin(Villager.class)
public abstract class PlagueBedMixin {
	@Inject(method = "stopSleeping", at = @At("HEAD"), cancellable = true)
	private void aliveworkplace$keepToBed(CallbackInfo ci) {
		if (Plague.keepsToBed((Villager) (Object) this)) {
			ci.cancel();
		}
	}
}
