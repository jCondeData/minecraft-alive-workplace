package io.github.jcondedata.aliveworkplace.client.mixin;

import io.github.jcondedata.aliveworkplace.work.JobPlate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A working villager the player looks at shows a name plate with its job and level (B221), the way vanilla shows a named
 * mob's name when looked at; names hidden (F1) or an invisible villager still show nothing.
 */
@Mixin(MobRenderer.class)
abstract class JobPlateShownMixin {
	@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/Mob;)Z", at = @At("RETURN"), cancellable = true)
	private void aliveworkplace$jobPlate(Mob mob, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() || !Minecraft.renderNames() || !JobPlate.shows(mob)) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && mob == mc.crosshairPickEntity && !mob.isInvisibleTo(mc.player)) {
			cir.setReturnValue(true);
		}
	}
}
