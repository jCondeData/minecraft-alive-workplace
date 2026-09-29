package io.github.jcondedata.aliveworkplace.mixin;

import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A brewing stand's brew time left and fuel (for alchemists tending it). */
@Mixin(BrewingStandBlockEntity.class)
public interface BrewingStandAccessor {
	@Accessor("brewTime")
	int aliveworkplace$brewTime();

	@Accessor("fuel")
	int aliveworkplace$fuel();
}
