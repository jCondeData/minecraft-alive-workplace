package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.fish.Fishers;
import net.minecraft.world.entity.ai.behavior.ShowTradesToPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla's trade display treats a villager's main hand as its own: whenever a player stands by, it shows wares there
 * and empties the hand when it stops. A hired fisher's rod lives in that hand, so the first player to stand by (the
 * owner, just after hiring) threw the rod away. With a rod in a hired fisher's hand, the display leaves the hand alone.
 */
@Mixin(ShowTradesToPlayer.class)
abstract class ShowTradesToPlayerMixin {
	@Inject(method = "clearHeldItem", at = @At("HEAD"), cancellable = true)
	private static void aliveworkplace$keepRodOnClear(Villager villager, CallbackInfo ci) {
		if (Fishers.holdsTheirRod(villager)) {
			ci.cancel();
		}
	}

	@Inject(method = "displayAsHeldItem", at = @At("HEAD"), cancellable = true)
	private static void aliveworkplace$keepRodOnDisplay(Villager villager, ItemStack stack, CallbackInfo ci) {
		if (Fishers.holdsTheirRod(villager)) {
			ci.cancel();
		}
	}
}
