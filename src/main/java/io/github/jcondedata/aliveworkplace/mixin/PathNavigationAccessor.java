package io.github.jcondedata.aliveworkplace.mixin;

import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * A path's pending rework. Vanilla's {@code stop()} drops the path but not a rework a changed block asked for less than
 * a second earlier, and that rework then sets the mob walking again to the stopped path's target: a raider let go of a
 * siege ladder walked on to it and climbed it (B907).
 */
@Mixin(PathNavigation.class)
public interface PathNavigationAccessor {
	@Accessor("hasDelayedRecomputation")
	void aliveworkplace$setDelayedRecomputation(boolean delayed);
}
