package io.github.jcondedata.aliveworkplace.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Villagers never trample farmland: a farmer working his field (or anyone walking through it) used to turn it to dirt
 * when he hopped down onto it, knocking the crop off. Only the trampling is skipped ({@code turnToDirt} with an entity
 * comes only from {@code fallOn}); farmland still dries back to dirt by itself.
 */
@Mixin(FarmBlock.class)
public abstract class FarmlandTrampleMixin {
	@Inject(method = "turnToDirt", at = @At("HEAD"), cancellable = true)
	private static void aliveworkplace$villagersDontTrample(Entity entity, BlockState state, Level level, BlockPos pos, CallbackInfo ci) {
		if (entity instanceof Villager) {
			ci.cancel();
		}
	}
}
