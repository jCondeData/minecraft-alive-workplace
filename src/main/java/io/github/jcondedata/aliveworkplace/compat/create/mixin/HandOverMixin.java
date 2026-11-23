package io.github.jcondedata.aliveworkplace.compat.create.mixin;

import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import io.github.jcondedata.aliveworkplace.compat.create.CreateMachines;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * B1532 (B1520's kin): a Create machine outside a protected village hands nothing to an item acceptor inside it. Create
 * machines put items onto the block next to them (a depot, a belt, a drain, a funnel) through that block's direct belt
 * input, which no other guard sees: a Funnel, a Brass Tunnel, a Crushing Wheel, a Saw's or Drill's output, a Mechanical
 * Crafter and an Item Drain all do. Here, the one place they all pass, the acceptor reads as full when the machine
 * ticking now ({@link MachineTickMixin}) may not change its block, so the machine keeps its item, as at a full depot.
 * Machines inside the village, and items not handed over by a machine, work as before.
 */
@Mixin(DirectBeltInputBehaviour.class)
public abstract class HandOverMixin {
	@Inject(method = "handleInsertion(Lcom/simibubi/create/content/kinetics/belt/transport/TransportedItemStack;Lnet/minecraft/core/Direction;Z)Lnet/minecraft/world/item/ItemStack;",
		remap = false, at = @At("HEAD"), cancellable = true)
	private void aliveworkplace$notIntoOthersVillages(TransportedItemStack item, Direction side, boolean simulate, CallbackInfoReturnable<ItemStack> cir) {
		if (!VillageProtection.guarding()) {
			return;
		}
		DirectBeltInputBehaviour self = (DirectBeltInputBehaviour) (Object) this;
		if (!CreateMachines.mayHandOver(self.getWorld(), self.getPos())) {
			cir.setReturnValue(item.stack);
		}
	}
}
