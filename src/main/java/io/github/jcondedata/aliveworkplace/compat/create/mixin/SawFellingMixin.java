package io.github.jcondedata.aliveworkplace.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.simibubi.create.content.kinetics.saw.TreeCutter;
import io.github.jcondedata.aliveworkplace.compat.create.CreateMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * B1518: a Mechanical Saw outside a protected village that cuts a tree's trunk fells only the part of the tree it may
 * change ({@link CreateMachines#fellable}): Create finds the whole tree from the cut block and breaks every log and leaf
 * of it, while {@link BlockBreakerMixin} judged only the cut block. A branch reaching into the village stays.
 */
@Mixin(SawBlockEntity.class)
public abstract class SawFellingMixin {
	@WrapOperation(method = "onBlockBroken", remap = false, at = @At(value = "INVOKE", remap = true,
		target = "Lcom/simibubi/create/content/kinetics/saw/TreeCutter;findTree(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Lcom/simibubi/create/content/kinetics/saw/TreeCutter$Tree;"))
	private TreeCutter.Tree aliveworkplace$keepVillagesWhole(BlockGetter reader, BlockPos pos, BlockState state, Operation<TreeCutter.Tree> original) {
		BlockEntity saw = (BlockEntity) (Object) this;
		return CreateMachines.fellable(saw.getLevel(), saw.getBlockPos(), original.call(reader, pos, state));
	}
}
