package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.work.NewWorkstations;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** When a chunk section loads, blocks that became workstations in 21.1a get their record (see {@link NewWorkstations}). */
@Mixin(PoiManager.class)
public abstract class PoiManagerMixin {
	@Inject(method = "checkConsistencyWithBlocks", at = @At("TAIL"))
	private void aliveworkplace$fileNewWorkstations(SectionPos section, LevelChunkSection blocks, CallbackInfo ci) {
		NewWorkstations.fillMissing((PoiManager) (Object) this, section, blocks);
	}

	/** A jobless villager looking for a workstation doesn't see the free ones in a full village (25.5). */
	@Inject(method = "findAllClosestFirstWithType", at = @At("RETURN"), cancellable = true)
	private void aliveworkplace$workerCap(java.util.function.Predicate<net.minecraft.core.Holder<net.minecraft.world.entity.ai.village.poi.PoiType>> type,
			java.util.function.Predicate<net.minecraft.core.BlockPos> pos, net.minecraft.core.BlockPos from, int distance, PoiManager.Occupancy occupancy,
			org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<java.util.stream.Stream<com.mojang.datafixers.util.Pair<
				net.minecraft.core.Holder<net.minecraft.world.entity.ai.village.poi.PoiType>, net.minecraft.core.BlockPos>>> cir) {
		cir.setReturnValue(io.github.jcondedata.aliveworkplace.work.WorkerLimits.hideFull(cir.getReturnValue()));
	}
}
