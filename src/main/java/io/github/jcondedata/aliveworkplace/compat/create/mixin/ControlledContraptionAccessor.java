package io.github.jcondedata.aliveworkplace.compat.create.mixin;

import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The block that controls a piston's or bearing's contraption (B993: protection judges the machine where it stands). */
@Mixin(ControlledContraptionEntity.class)
public interface ControlledContraptionAccessor {
	@Accessor(value = "controllerPos", remap = false)
	BlockPos aliveworkplace$controllerPos();
}
