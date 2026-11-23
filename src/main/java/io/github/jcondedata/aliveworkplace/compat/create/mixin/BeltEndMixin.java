package io.github.jcondedata.aliveworkplace.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.BeltInventory;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * B1520: a Mechanical Belt whose end stands outside a protected village carries nothing onto the depot, belt or other
 * item acceptor just past that end inside it ({@link VillageProtection#mayMachineChange}, judged from the belt's last
 * segment to that block). Create hands items over there through the acceptor's direct belt input, which no other guard
 * sees; the acceptor reads as full, so the items wait at the belt's end. A belt ending inside the village works as before.
 */
@Mixin(BeltInventory.class)
public abstract class BeltEndMixin {
	@Shadow(remap = false)
	@Final
	BeltBlockEntity belt;

	@Shadow(remap = false)
	boolean beltMovementPositive;

	@WrapOperation(method = "tick", remap = false, at = @At(value = "INVOKE", remap = true,
		target = "Lcom/simibubi/create/content/kinetics/belt/behaviour/DirectBeltInputBehaviour;handleInsertion(Lcom/simibubi/create/content/kinetics/belt/transport/TransportedItemStack;Lnet/minecraft/core/Direction;Z)Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack aliveworkplace$notIntoOthersVillages(DirectBeltInputBehaviour target, TransportedItemStack item, Direction side, boolean simulate,
		Operation<ItemStack> original) {
		if (VillageProtection.guarding() && belt.getLevel() != null) {
			BlockPos end = BeltHelper.getPositionForOffset(belt, beltMovementPositive ? belt.beltLength - 1 : 0);
			if (!VillageProtection.mayMachineChange(belt.getLevel(), end, target.getPos())) {
				return item.stack;
			}
		}
		return original.call(target, item, side, simulate);
	}
}
