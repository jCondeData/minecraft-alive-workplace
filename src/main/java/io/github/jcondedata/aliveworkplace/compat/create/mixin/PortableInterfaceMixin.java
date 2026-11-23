package io.github.jcondedata.aliveworkplace.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.contraptions.actors.psi.PortableStorageInterfaceBlockEntity;
import com.simibubi.create.content.contraptions.actors.psi.PortableStorageInterfaceMovement;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import io.github.jcondedata.aliveworkplace.compat.create.CreateMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * B1488, B1495, B1519 (B994's rule for the Portable Storage and Fluid Interfaces): a contraption whose machine stands
 * outside a protected village (a piston's, a cart's, a train's) can't dock its moving interface with a stationary one
 * inside it, so no items or fluid pass either way ({@link CreateMachines#may}, judged from the contraption's machine to
 * the stationary interface). Create looks the stationary interface up when the moving one passes it
 * ({@code findInterface}) and again every tick while docked ({@code tick}, which also reconnects after a save and load);
 * a village interface the contraption may not touch reads as no interface there, so the contraption doesn't stop and
 * nothing is wired up. Both kinds of interface share this movement behaviour.
 */
@Mixin(PortableStorageInterfaceMovement.class)
public abstract class PortableInterfaceMixin {
	@WrapOperation(method = "findInterface", remap = false, at = @At(value = "INVOKE", remap = true,
		target = "Lcom/simibubi/create/content/contraptions/actors/psi/PortableStorageInterfaceMovement;findStationaryInterface(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Lcom/simibubi/create/content/contraptions/actors/psi/PortableStorageInterfaceBlockEntity;"))
	private PortableStorageInterfaceBlockEntity aliveworkplace$notWithOthersVillages(PortableStorageInterfaceMovement self, Level level, BlockPos pos,
		BlockState state, Direction facing, Operation<PortableStorageInterfaceBlockEntity> original, @Local(argsOnly = true) MovementContext context) {
		return aliveworkplace$allowed(original.call(self, level, pos, state, facing), level, context);
	}

	@WrapOperation(method = "tick", remap = false, at = @At(value = "INVOKE", remap = true,
		target = "Lcom/simibubi/create/content/contraptions/actors/psi/PortableStorageInterfaceMovement;getStationaryInterfaceAt(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Lcom/simibubi/create/content/contraptions/actors/psi/PortableStorageInterfaceBlockEntity;"))
	private PortableStorageInterfaceBlockEntity aliveworkplace$stillNotWithOthersVillages(PortableStorageInterfaceMovement self, Level level, BlockPos pos,
		BlockState state, Direction facing, Operation<PortableStorageInterfaceBlockEntity> original, @Local(argsOnly = true) MovementContext context) {
		return aliveworkplace$allowed(original.call(self, level, pos, state, facing), level, context);
	}

	private static PortableStorageInterfaceBlockEntity aliveworkplace$allowed(PortableStorageInterfaceBlockEntity found, Level level, MovementContext context) {
		if (found == null || level == null || level.isClientSide() || context.contraption == null
			|| CreateMachines.may(level, context.contraption, found.getBlockPos())) {
			return found;
		}
		return null;
	}
}
