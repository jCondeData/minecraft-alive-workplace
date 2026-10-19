package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.trainer.RaidHelpers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A custom statistic awarded to a player: Raid Dens counts every raid joined, hosted and won this way (unlike its
 * advancements, which come once), so the raid helpers hear of each raid ({@link RaidHelpers#statAwarded}, 38.1a).
 */
@Mixin(ServerPlayer.class)
public abstract class RaidStatMixin {
	@Inject(method = "awardStat(Lnet/minecraft/stats/Stat;I)V", at = @At("HEAD"))
	private void aliveworkplace$statAwarded(Stat<?> stat, int amount, CallbackInfo ci) {
		if (amount > 0 && stat.getType() == Stats.CUSTOM && stat.getValue() instanceof ResourceLocation id) {
			RaidHelpers.statAwarded((ServerPlayer) (Object) this, id);
		}
	}
}
