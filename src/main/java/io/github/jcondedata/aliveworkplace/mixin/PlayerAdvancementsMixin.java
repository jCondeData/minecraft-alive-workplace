package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.guard.DenWardens;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** An advancement completed: Raid Dens' raid advancements earn a watched den's cheer ({@link DenWardens#advancementDone}, 38.3). */
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {
	@Shadow
	private ServerPlayer player;

	@Shadow
	public abstract net.minecraft.advancements.AdvancementProgress getOrStartProgress(AdvancementHolder advancement);

	@Inject(method = "award", at = @At("RETURN"))
	private void aliveworkplace$awarded(AdvancementHolder advancement, String criterion, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && getOrStartProgress(advancement).isDone()) {
			DenWardens.advancementDone(player, advancement.id());
		}
	}
}
