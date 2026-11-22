package io.github.jcondedata.aliveworkplace.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.contraptions.actors.roller.RollerMovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import io.github.jcondedata.aliveworkplace.compat.create.CreateMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * B1503 (B808's rule for the roller): a Mechanical Roller carried on a contraption (a train, a cart, a piston's head)
 * whose machine stands outside a protected village lays no filler inside it. Create fills a cell in {@code tryFill}, which
 * first asks whether the cell is loaded and gives up (taking no item) when it isn't; here a village cell the machine may
 * not change counts as not loaded. Its clearing is {@link MovingBreakerMixin}'s.
 */
@Mixin(RollerMovementBehaviour.class)
public abstract class RollerFillMixin {
	@WrapOperation(method = "tryFill", remap = false, at = @At(value = "INVOKE", remap = true,
		target = "Lnet/minecraft/world/level/Level;isLoaded(Lnet/minecraft/core/BlockPos;)Z"))
	private boolean aliveworkplace$fillNoVillageHole(Level level, BlockPos pos, Operation<Boolean> original,
		@Local(argsOnly = true) MovementContext context) {
		return original.call(level, pos) && (context.contraption == null || CreateMachines.may(level, context.contraption, pos));
	}
}
