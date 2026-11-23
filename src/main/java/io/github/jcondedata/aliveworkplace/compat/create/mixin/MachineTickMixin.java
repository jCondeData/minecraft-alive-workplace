package io.github.jcondedata.aliveworkplace.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntityTicker;
import io.github.jcondedata.aliveworkplace.compat.create.CreateMachines;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * B1532: remembers which Create machine is ticking ({@link CreateMachines#ticking}), so the hand-over guard on Create's
 * direct belt input ({@link HandOverMixin}) knows where the item comes from. Every Create machine (Funnel, Brass Tunnel,
 * Crushing Wheel, Saw, Drill, Mechanical Crafter, Item Drain, Weighted Ejector, ...) ticks through this one ticker.
 * Only while a village is protected; otherwise the tick runs untouched. ({@code tick} is {@code BlockEntityTicker}'s
 * method, which keeps its name in every mapping.)
 */
@Mixin(SmartBlockEntityTicker.class)
public abstract class MachineTickMixin {
	@WrapOperation(method = "tick", remap = false, at = @At(value = "INVOKE", remap = false,
		target = "Lcom/simibubi/create/foundation/blockEntity/SmartBlockEntity;tick()V"))
	private void aliveworkplace$rememberTheMachine(SmartBlockEntity machine, Operation<Void> original) {
		if (!VillageProtection.guarding()) {
			original.call(machine);
			return;
		}
		CreateMachines.ticking(machine, () -> original.call(machine));
	}
}
