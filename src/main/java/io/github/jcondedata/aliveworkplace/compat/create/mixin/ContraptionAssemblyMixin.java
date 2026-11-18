package io.github.jcondedata.aliveworkplace.compat.create.mixin;

import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.bearing.ClockworkContraption;
import com.simibubi.create.content.contraptions.bearing.StabilizedContraption;
import com.simibubi.create.content.contraptions.elevator.ElevatorContraption;
import com.simibubi.create.content.contraptions.gantry.GantryContraption;
import com.simibubi.create.content.contraptions.mounted.MountedContraption;
import com.simibubi.create.content.contraptions.piston.PistonContraption;
import com.simibubi.create.content.contraptions.pulley.PulleyContraption;
import io.github.jcondedata.aliveworkplace.compat.create.CreateMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Remembers which machine puts a contraption together (the block each controller hands to {@code assemble}), so village
 * protection can judge a piston's first push before Create gives the contraption its entity ({@link CreateMachines}).
 */
@Mixin({PistonContraption.class, BearingContraption.class, ClockworkContraption.class, StabilizedContraption.class,
	GantryContraption.class, PulleyContraption.class, MountedContraption.class, ElevatorContraption.class})
public abstract class ContraptionAssemblyMixin {
	@Inject(method = "assemble", remap = false, at = @At("HEAD"))
	private void aliveworkplace$byWhom(Level level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		CreateMachines.assembling((Contraption) (Object) this, pos);
	}
}
