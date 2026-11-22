package io.github.jcondedata.aliveworkplace.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.contraptions.actors.plough.PloughMovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import io.github.jcondedata.aliveworkplace.compat.create.CreateMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * B1502 (B808's rule for the plough): a Mechanical Plough carried on a contraption (a train, a cart, a piston's head)
 * whose machine stands outside a protected village tills nothing inside it. Create tills by having the plough's fake
 * player use a hoe on the block under each cell it passes, after asking whether that block is loaded; here a village
 * block the machine may not change counts as not loaded, so the plough passes it by. Its breaking (torches, flowers)
 * is {@link MovingBreakerMixin}'s.
 */
@Mixin(PloughMovementBehaviour.class)
public abstract class PloughMixin {
	@WrapOperation(method = "visitNewPosition", remap = false, at = @At(value = "INVOKE", remap = true,
		target = "Lnet/minecraft/world/level/Level;isLoaded(Lnet/minecraft/core/BlockPos;)Z"))
	private boolean aliveworkplace$tillNoVillageGround(Level level, BlockPos pos, Operation<Boolean> original,
		@Local(argsOnly = true) MovementContext context) {
		return original.call(level, pos) && (context.contraption == null || CreateMachines.may(level, context.contraption, pos));
	}
}
