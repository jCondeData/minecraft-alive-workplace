package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.threat.VillageFire;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fires in a village are noticed where they're placed (ROADMAP 32.16), never by scanning: {@link VillageFire#noticed}. */
@Mixin(BaseFireBlock.class)
public abstract class FirePlacedMixin {
	@Inject(method = "onPlace", at = @At("HEAD"))
	private void aliveworkplace$noticed(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved, CallbackInfo ci) {
		if (level instanceof ServerLevel server && !old.is(state.getBlock())) {
			VillageFire.noticed(server, pos);
		}
	}
}
