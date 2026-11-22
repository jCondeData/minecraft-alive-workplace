package io.github.jcondedata.aliveworkplace.compat.create.mixin;

import com.simibubi.create.content.contraptions.actors.harvester.HarvesterMovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import io.github.jcondedata.aliveworkplace.compat.create.CreateMachines;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * B1498 (B808's rule for the harvester): a Mechanical Harvester carried on a contraption (a train, a cart, a piston's
 * head, a bearing's arm) whose machine stands outside a protected village reaps nothing inside it. Create reaps through
 * the harvester's movement behaviour, which {@link MovingBreakerMixin} (the drill's and saw's) doesn't cover: here the
 * harvester passes the village's crops by, as the drill passes its blocks.
 */
@Mixin(HarvesterMovementBehaviour.class)
public abstract class HarvesterMixin {
	@Inject(method = "visitNewPosition", remap = false, cancellable = true, at = @At("HEAD"))
	private void aliveworkplace$keepVillageCrops(MovementContext context, BlockPos pos, CallbackInfo ci) {
		if (context.world != null && !context.world.isClientSide() && context.contraption != null
			&& !CreateMachines.may(context.world, context.contraption, pos)) {
			ci.cancel();
		}
	}
}
