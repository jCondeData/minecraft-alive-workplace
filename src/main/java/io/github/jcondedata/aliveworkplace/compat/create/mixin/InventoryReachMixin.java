package io.github.jcondedata.aliveworkplace.compat.create.mixin;

import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.CapManipulationBehaviourBase;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * B994: a Create machine that takes from or puts into the container beside it (a funnel, a chute, through Create's
 * inventory behaviour) can't reach a container inside a protected village from outside it
 * ({@link VillageProtection#mayMachineChange}, judged where the container stands): it sees no inventory there, as when
 * nothing is attached. Asked each time, so turning protection on or off takes effect at once.
 */
@Mixin(CapManipulationBehaviourBase.class)
public abstract class InventoryReachMixin {
	/** The block whose storage this behaviour last looked up. */
	@Unique
	private BlockPos aliveworkplace$target;

	@ModifyArg(method = "findNewCapability", remap = false, index = 0, at = @At(value = "INVOKE", remap = true,
		target = "Lcom/simibubi/create/foundation/blockEntity/behaviour/inventory/CapManipulationBehaviourBase;getProvider(Lnet/minecraft/core/BlockPos;Z)Lio/github/fabricators_of_create/porting_lib/util/StorageProvider;"))
	private BlockPos aliveworkplace$remember(BlockPos target) {
		aliveworkplace$target = target.immutable();
		return target;
	}

	@Inject(method = "getInventory", remap = false, at = @At("HEAD"), cancellable = true)
	private void aliveworkplace$notIntoOthersVillages(CallbackInfoReturnable<Storage<?>> cir) {
		BlockPos target = aliveworkplace$target;
		if (target == null || !VillageProtection.guarding()) {
			return;
		}
		BlockEntityBehaviour self = (BlockEntityBehaviour) (Object) this;
		if (self.getWorld() != null && !VillageProtection.mayMachineChange(self.getWorld(), self.getPos(), target)) {
			cir.setReturnValue(null);
		}
	}
}
