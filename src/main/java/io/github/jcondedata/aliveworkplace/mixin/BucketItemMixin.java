package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.hall.StandIns;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * B1501 (B719's rule for machines): a machine's stand-in player (Create's Deployer) may not pour a bucket into, or scoop
 * one out of, a protected village it may not change, judged where the bucket acts ({@link VillageProtection#mayUseBucket}).
 * A player's bucket is judged by the use-item event; a deployer aiming at air or water calls the bucket's {@code use}
 * directly, so that event never fires for it. A stranger's machine is a stranger.
 */
@Mixin(BucketItem.class)
public abstract class BucketItemMixin {
	@Inject(method = "use", at = @At("HEAD"), cancellable = true)
	private void aliveworkplace$noMachineBucketInOthersVillages(Level level, Player player, InteractionHand hand,
		CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
		if (!level.isClientSide() && VillageProtection.guarding() && StandIns.machine(player)
			&& !VillageProtection.mayUseBucket(level, player, player.getItemInHand(hand))) {
			cir.setReturnValue(InteractionResultHolder.fail(player.getItemInHand(hand)));
		}
	}
}
