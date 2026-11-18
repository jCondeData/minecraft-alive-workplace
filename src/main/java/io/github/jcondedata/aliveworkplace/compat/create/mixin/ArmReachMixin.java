package io.github.jcondedata.aliveworkplace.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * B1015 (2): a Mechanical Arm standing outside a protected village neither takes from nor puts onto what it reaches
 * inside it (a depot, a belt, a basin, a funnel; {@link VillageProtection#mayMachineChange}, judged where that point
 * stands): there it finds nothing to take and no room. Points inside, or an arm inside the village, work as before.
 */
@Mixin(ArmBlockEntity.class)
public abstract class ArmReachMixin {
	@Unique
	private boolean aliveworkplace$mayReach(ArmInteractionPoint point) {
		BlockEntity arm = (BlockEntity) (Object) this;
		return !VillageProtection.guarding() || arm.getLevel() == null
			|| VillageProtection.mayMachineChange(arm.getLevel(), arm.getBlockPos(), point.getPos());
	}

	@WrapOperation(method = "collectItem", remap = false, at = @At(value = "INVOKE", remap = true,
		target = "Lcom/simibubi/create/content/kinetics/mechanicalArm/ArmInteractionPoint;extract(ILnet/fabricmc/fabric/api/transfer/v1/transaction/TransactionContext;)Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack aliveworkplace$takeNothing(ArmInteractionPoint point, int amount, TransactionContext ctx, Operation<ItemStack> original) {
		return aliveworkplace$mayReach(point) ? original.call(point, amount, ctx) : ItemStack.EMPTY;
	}

	@WrapOperation(method = "getDistributableAmount(Lcom/simibubi/create/content/kinetics/mechanicalArm/ArmInteractionPoint;Lnet/fabricmc/fabric/api/transfer/v1/transaction/TransactionContext;)I", remap = false, at = @At(value = "INVOKE", remap = true,
		target = "Lcom/simibubi/create/content/kinetics/mechanicalArm/ArmInteractionPoint;extract(Lnet/fabricmc/fabric/api/transfer/v1/transaction/TransactionContext;)Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack aliveworkplace$seeNothing(ArmInteractionPoint point, TransactionContext ctx, Operation<ItemStack> original) {
		return aliveworkplace$mayReach(point) ? original.call(point, ctx) : ItemStack.EMPTY;
	}

	@WrapOperation(method = {"searchForDestination", "simulateInsertion", "depositItem"}, remap = false, at = @At(value = "INVOKE", remap = true,
		target = "Lcom/simibubi/create/content/kinetics/mechanicalArm/ArmInteractionPoint;insert(Lnet/minecraft/world/item/ItemStack;Lnet/fabricmc/fabric/api/transfer/v1/transaction/TransactionContext;)Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack aliveworkplace$putNothing(ArmInteractionPoint point, ItemStack stack, TransactionContext ctx, Operation<ItemStack> original) {
		return aliveworkplace$mayReach(point) ? original.call(point, stack, ctx) : stack;
	}
}
