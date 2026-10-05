package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.city.PlayerBuilt;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A player placed a block: the ledger of what players built marks its section (27.19, {@link PlayerBuilt}). */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
	@Inject(method = "place", at = @At("RETURN"))
	private void aliveworkplace$placed(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
		if (context.getPlayer() != null && context.getLevel() instanceof ServerLevel level && cir.getReturnValue().consumesAction()) {
			PlayerBuilt.changed(level, context.getClickedPos());
		}
	}
}
