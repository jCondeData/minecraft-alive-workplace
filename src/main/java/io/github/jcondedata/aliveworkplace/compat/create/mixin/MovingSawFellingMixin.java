package io.github.jcondedata.aliveworkplace.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.kinetics.saw.SawMovementBehaviour;
import com.simibubi.create.content.kinetics.saw.TreeCutter;
import io.github.jcondedata.aliveworkplace.compat.create.CreateMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * B1518: a saw carried on a contraption (a piston's head, a bearing's arm) whose machine stands outside a protected
 * village fells only the part of a tree it may change ({@link CreateMachines#fellable}, judged from the contraption's
 * machine), as {@link SawFellingMixin} does for a saw standing still.
 */
@Mixin(SawMovementBehaviour.class)
public abstract class MovingSawFellingMixin {
	@WrapOperation(method = "onBlockBroken", remap = false, at = @At(value = "INVOKE", remap = true,
		target = "Lcom/simibubi/create/content/kinetics/saw/TreeCutter;findTree(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Lcom/simibubi/create/content/kinetics/saw/TreeCutter$Tree;"))
	private TreeCutter.Tree aliveworkplace$keepVillagesWhole(BlockGetter reader, BlockPos pos, BlockState state, Operation<TreeCutter.Tree> original,
		@Local(argsOnly = true) MovementContext context) {
		TreeCutter.Tree tree = original.call(reader, pos, state);
		return context.contraption == null ? tree : CreateMachines.fellable(context.world, CreateMachines.machine(context.contraption), tree);
	}
}
