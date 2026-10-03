package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.travel.FerryRides;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A player leaving the game, on the server thread and before vanilla saves them (see {@link FerryRides#leaving}). */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
	@Inject(method = "remove", at = @At("HEAD"))
	private void aliveworkplace$beforeLeaving(ServerPlayer player, CallbackInfo ci) {
		FerryRides.leaving(player);
	}
}
