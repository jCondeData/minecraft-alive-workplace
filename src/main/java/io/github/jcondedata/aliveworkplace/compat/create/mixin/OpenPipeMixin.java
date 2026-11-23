package io.github.jcondedata.aliveworkplace.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.fluids.OpenEndedPipe;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * B1517 (B808's rule for open pipes): the open end of a Create Fluid Pipe standing outside a protected village pours no
 * fluid into it and drains none out of it ({@link VillageProtection#mayMachineChange}, judged from the pipe to the cell in
 * front of its open end). Create pours and drains there only after asking whether that cell is loaded; a village cell the
 * pipe may not change counts as not loaded, so the pipe neither takes nor gives fluid there (nothing is lost or made).
 */
@Mixin(OpenEndedPipe.class)
public abstract class OpenPipeMixin {
	@Shadow(remap = false)
	private BlockPos pos;

	@WrapOperation(method = {"removeFluidFromSpace", "provideFluidToSpace"}, remap = false, at = @At(value = "INVOKE", remap = true,
		target = "Lnet/minecraft/world/level/Level;isLoaded(Lnet/minecraft/core/BlockPos;)Z"))
	private boolean aliveworkplace$noVillageFluid(Level level, BlockPos output, Operation<Boolean> original) {
		return original.call(level, output) && (pos == null || VillageProtection.mayMachineChange(level, pos, output));
	}
}
