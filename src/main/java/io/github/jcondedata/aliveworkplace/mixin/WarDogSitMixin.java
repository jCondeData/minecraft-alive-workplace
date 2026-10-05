package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.legend.Beastmaster;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.animal.Wolf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Beastmaster's war dogs (29.20) never sit: vanilla sits a tame pet down whenever it has no player owner loaded, and
 * a war dog's owner is its guard, so the dog would sit for good instead of following and fighting.
 */
@Mixin(SitWhenOrderedToGoal.class)
abstract class WarDogSitMixin {
	@Shadow
	@Final
	private TamableAnimal mob;

	@Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
	private void aliveworkplace$warDogsStand(CallbackInfoReturnable<Boolean> cir) {
		if (this.mob instanceof Wolf wolf && Beastmaster.isWarDog(wolf)) {
			cir.setReturnValue(false);
		}
	}
}
