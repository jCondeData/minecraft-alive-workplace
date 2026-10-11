package io.github.jcondedata.aliveworkplace.mixin;

import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The village fire (32.16) asks the fire itself which blocks it can take hold of. */
@Mixin(FireBlock.class)
public interface FireBlockAccessor {
	/** Vanilla: whether fire can burn {@code state} (its ignite odds are above 0, and it isn't waterlogged). */
	@Invoker("canBurn")
	boolean aliveworkplace$canBurn(BlockState state);
}
