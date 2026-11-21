package io.github.jcondedata.aliveworkplace.mixin;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * A path's pending rework. Vanilla's {@code stop()} drops the path but not a rework a changed block asked for less than
 * a second earlier, and that rework then sets the mob walking again to the stopped path's target: a raider let go of a
 * siege ladder walked on to it and climbed it (B907).
 *
 * <p>And a path search with a range of its own: vanilla's public searches all stop at the mob's follow range (B735,
 * {@code BedLadders.walkTo}).
 */
@Mixin(PathNavigation.class)
public interface PathNavigationAccessor {
	@Accessor("hasDelayedRecomputation")
	void aliveworkplace$setDelayedRecomputation(boolean delayed);

	/**
	 * Vanilla's path search to the nearest of {@code targets}: reading blocks {@code regionOffset} past the range, from the
	 * block above the mob with {@code offsetUpward}, there when within {@code reach} blocks, over walks up to {@code range} long.
	 */
	@Invoker("createPath")
	@Nullable
	Path aliveworkplace$createPath(Set<BlockPos> targets, int regionOffset, boolean offsetUpward, int reach, float range);
}
