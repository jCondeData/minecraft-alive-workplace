package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tells {@link SupplyContainers} when the storage it listed near a workstation may have changed (B455, B412): a block
 * entity placed or removed, a block with one changing state (a chest joining its other half), a chunk loading or
 * unloading. Until then a job's "which chests are near my station" is answered from the last scan.
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {
	@Inject(method = "setBlockEntity", at = @At("HEAD"))
	private void aliveworkplace$blockEntitySet(BlockEntity blockEntity, CallbackInfo ci) {
		SupplyContainers.blockEntitiesChanged();
	}

	@Inject(method = "removeBlockEntity", at = @At("HEAD"))
	private void aliveworkplace$blockEntityRemoved(BlockPos pos, CallbackInfo ci) {
		SupplyContainers.blockEntitiesChanged();
	}

	@Inject(method = "setBlockState", at = @At("RETURN"))
	private void aliveworkplace$blockEntityStateSet(BlockPos pos, BlockState state, boolean moved, CallbackInfoReturnable<BlockState> cir) {
		BlockState old = cir.getReturnValue();
		if (old != null && (old.hasBlockEntity() || state.hasBlockEntity())) {
			SupplyContainers.blockEntitiesChanged();
		}
	}

	@Inject(method = "setLoaded", at = @At("HEAD"))
	private void aliveworkplace$loadedSet(boolean loaded, CallbackInfo ci) {
		SupplyContainers.blockEntitiesChanged();
	}

	@Inject(method = "clearAllBlockEntities", at = @At("HEAD"))
	private void aliveworkplace$blockEntitiesCleared(CallbackInfo ci) {
		SupplyContainers.blockEntitiesChanged();
	}
}
