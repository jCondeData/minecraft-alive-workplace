package io.github.jcondedata.aliveworkplace.client.mixin;

import io.github.jcondedata.aliveworkplace.client.LegendLookLayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A Legend's name shows over their head whenever they're within {@link #RANGE} blocks, not only when looked at (29.4),
 * as long as names show at all (F1 hides them) and the player can see them.
 */
@Mixin(MobRenderer.class)
abstract class LegendNameShownMixin {
	private static final double RANGE = 16;

	@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/Mob;)Z", at = @At("RETURN"), cancellable = true)
	private void aliveworkplace$legendName(Mob mob, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() || !LegendLookLayer.isLegend(mob) || !Minecraft.renderNames()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && mob != mc.getCameraEntity() && !mob.isInvisibleTo(mc.player) && mob.distanceToSqr(mc.player) < RANGE * RANGE) {
			cir.setReturnValue(true);
		}
	}
}
