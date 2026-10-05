package io.github.jcondedata.aliveworkplace.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.ReactToBell;
import net.minecraft.world.entity.ai.behavior.SetRaidStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Iron-Willed (29.6) neither hide at the bell nor drop their work for a raid: see {@code IronWill}. */
@Mixin({ReactToBell.class, SetRaidStatus.class})
abstract class IronWillMixin {
	@Inject(method = "create", at = @At("RETURN"), cancellable = true)
	private static void aliveworkplace$ironWill(CallbackInfoReturnable<BehaviorControl<LivingEntity>> cir) {
		cir.setReturnValue(io.github.jcondedata.aliveworkplace.legend.IronWill.wrap(cir.getReturnValue()));
	}
}
