package io.github.jcondedata.aliveworkplace.compat.create.mixin;

import com.simibubi.create.content.contraptions.ContraptionCollider;
import com.simibubi.create.content.contraptions.TranslatingContraption;
import io.github.jcondedata.aliveworkplace.compat.create.CreateMachines;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * B993, B808 (B745's rule for Create): a Mechanical Piston, Gantry or Rope Pulley outside a protected village stops at
 * its edge. Create asks this before its contraption is put together and before every step it moves: here a step that
 * would take a block into the village, or one out of it, counts as hitting a wall. Pistons inside the village (and any
 * with protection off) move as before.
 */
@Mixin(ContraptionCollider.class)
public abstract class ContraptionColliderMixin {
	@Inject(method = "isCollidingWithWorld", remap = false, cancellable = true, at = @At("HEAD"))
	private static void aliveworkplace$villageEdge(Level level, TranslatingContraption contraption, BlockPos anchor, Direction movement,
		CallbackInfoReturnable<Boolean> cir) {
		if (level.isClientSide() || !VillageProtection.guarding() || contraption.getBlocks().isEmpty()) {
			return;
		}
		List<BlockPos> touched = new ArrayList<>(contraption.getBlocks().size() * 2);
		for (BlockPos local : contraption.getBlocks().keySet()) {
			BlockPos next = local.offset(anchor);
			touched.add(next); // where it goes
			touched.add(next.relative(movement.getOpposite())); // where it comes from
		}
		if (!VillageProtection.mayMachineChange(level, CreateMachines.machine(contraption), touched)) {
			cir.setReturnValue(true);
		}
	}
}
