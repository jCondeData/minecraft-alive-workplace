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
}
