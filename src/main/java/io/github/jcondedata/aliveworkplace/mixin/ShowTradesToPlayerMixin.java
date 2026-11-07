package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.work.TradeDisplay;
import net.minecraft.world.entity.ai.behavior.ShowTradesToPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla's trade display treats a villager's main hand as its own: whenever a player stands by, it shows wares there
 * and empties the hand when it stops. Our workers keep their tools in that hand (a hired fisher's rod, a conscript's
 * militia sword (B324), a Farmer's hoe, a Shepherd's shears (B766), a Miner's pickaxe, …), so the first player to stand
 * by threw the tool away. The display now touches only an empty hand or a ware it showed itself ({@link TradeDisplay}).
 */
@Mixin(ShowTradesToPlayer.class)
abstract class ShowTradesToPlayerMixin {
	@Inject(method = "clearHeldItem", at = @At("HEAD"), cancellable = true)
	private static void aliveworkplace$keepToolOnClear(Villager villager, CallbackInfo ci) {
		if (!TradeDisplay.mayUseHand(villager)) {
			ci.cancel();
		}
	}

	@Inject(method = "displayAsHeldItem", at = @At("HEAD"), cancellable = true)
	private static void aliveworkplace$keepToolOnDisplay(Villager villager, ItemStack stack, CallbackInfo ci) {
		if (!TradeDisplay.mayUseHand(villager)) {
			ci.cancel();
			return;
		}
		TradeDisplay.mark(stack);
	}
}
