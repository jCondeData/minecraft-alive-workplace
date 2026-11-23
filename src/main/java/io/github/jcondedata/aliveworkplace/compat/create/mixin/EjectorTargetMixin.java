package io.github.jcondedata.aliveworkplace.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.logistics.depot.EjectorBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * B1520: a Weighted Ejector standing outside a protected village puts nothing onto the depot, belt or other item
 * acceptor it aims at inside it ({@link VillageProtection#mayMachineChange}, judged where the target stands). Create
 * hands the item over through the target's direct belt input, which no other guard sees; there the target reads as
 * full, so the ejector keeps its item and doesn't launch, as at a full depot. Targets inside, or an ejector inside the
 * village, work as before.
 */
@Mixin(EjectorBlockEntity.class)
public abstract class EjectorTargetMixin {
	@WrapOperation(method = {"ejectIfTriggered", "placeItemAtTarget"}, remap = false, at = @At(value = "INVOKE", remap = true,
		target = "Lcom/simibubi/create/content/kinetics/belt/behaviour/DirectBeltInputBehaviour;handleInsertion(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/Direction;Z)Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack aliveworkplace$notIntoOthersVillages(DirectBeltInputBehaviour target, ItemStack stack, Direction side, boolean simulate,
		Operation<ItemStack> original) {
		BlockEntity ejector = (BlockEntity) (Object) this;
		if (VillageProtection.guarding() && ejector.getLevel() != null
			&& !VillageProtection.mayMachineChange(ejector.getLevel(), ejector.getBlockPos(), target.getPos())) {
			return stack;
		}
		return original.call(target, stack, side, simulate);
	}
}
