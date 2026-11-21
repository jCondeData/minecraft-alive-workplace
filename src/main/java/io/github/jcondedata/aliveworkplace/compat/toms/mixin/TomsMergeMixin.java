package io.github.jcondedata.aliveworkplace.compat.toms.mixin;

import io.github.jcondedata.aliveworkplace.compat.toms.TomsMerge;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * B1025: a Tom's Simple Storage network merges each inventory once ({@link TomsMerge}): right after Tom's has listed a
 * network's inventories and before it sets up its item tracking over them, an inventory reached a second way (a chest
 * touching the inventory connector that a cable connector also faces) leaves the list.
 */
@Pseudo
@Mixin(targets = "com.tom.storagemod.inventory.MultiInventoryAccess", remap = false)
public abstract class TomsMergeMixin {
	@Shadow
	protected List<Object> connected;

	@Inject(method = "build", require = 0, at = @At(value = "INVOKE", target = "Lcom/tom/storagemod/inventory/MultiInventoryAccess;refresh()V"))
	private void aliveworkplace$eachInventoryOnce(CallbackInfo ci) {
		TomsMerge.once(connected);
	}
}
