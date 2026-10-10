package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.hall.CaravanSights;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A caravan's carter and pack llamas (33.7, {@code hall/CaravanSights}) are only a sight: a right-click does nothing to
 * them on the server, whatever the player holds (no trade, no ride, no look in the llama's chest, no hay, no name tag).
 * The loader's use-entity event can't do this alone: the client doesn't know which mobs they are, so it sends the plain
 * click too, which only this stops.
 */
@Mixin(Mob.class)
abstract class CaravanPartyMixin {
	@Inject(method = "interact", at = @At("HEAD"), cancellable = true)
	private void aliveworkplace$onlyASight(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		Mob self = (Mob) (Object) this;
		if (!self.level().isClientSide() && CaravanSights.isParty(self)) {
			cir.setReturnValue(InteractionResult.CONSUME);
		}
	}
}
