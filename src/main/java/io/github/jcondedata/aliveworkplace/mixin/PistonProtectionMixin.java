package io.github.jcondedata.aliveworkplace.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * B745: a piston outside a protected village can't push, pull or break its blocks, nor put its head inside. The move
 * vanilla resolved in {@code PistonBaseBlock.moveBlocks} (after it clears a retracting piston's head) is refused when
 * {@link VillageProtection#mayPistonMove} says so: an extending piston then stays put, a sticky one retracts without
 * pulling.
 */
@Mixin(PistonBaseBlock.class)
public abstract class PistonProtectionMixin {
	@WrapOperation(method = "moveBlocks", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/block/piston/PistonStructureResolver;resolve()Z"))
	private boolean aliveworkplace$keepVillagesOut(PistonStructureResolver resolver, Operation<Boolean> original,
		Level level, BlockPos pos, Direction facing, boolean extending) {
		return original.call(resolver) && VillageProtection.mayPistonMove(level, pos, resolver, extending);
	}
}
