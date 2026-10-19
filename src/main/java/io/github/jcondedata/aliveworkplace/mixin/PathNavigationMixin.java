package io.github.jcondedata.aliveworkplace.mixin;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A path worked out again is worked out to where the mob is walking (B203). Vanilla works a path out again when a block
 * on it changes shape, such as a door the villager has just opened or shut, and it aims the new path at the last target
 * any path was worked out for. That isn't always the walk: a villager also works out paths only to see whether it could
 * reach a bed, a bell or a workstation (vanilla's acquire-a-place checks), and follows none of them. So a villager
 * walking in by a shut door opened it, and the new path led to some bed across the village it had only looked at, or
 * came out as "can't get there" and led away round the building; it shut the door behind it and never got in. Now the
 * target (and how close counts as there) of the path the mob follows is kept, and a path worked out again goes there.
 */
@Mixin(PathNavigation.class)
abstract class PathNavigationMixin {
	@Shadow
	@Nullable
	protected Path path;
	@Shadow
	@Nullable
	private BlockPos targetPos;
	@Shadow
	private int reachRange;

	/** The last path worked out, and how close to its target counted as there. */
	@Unique
	@Nullable
	private Path aliveworkplace$worked;
	@Unique
	private int aliveworkplace$workedReach;
	/** Where the path the mob follows goes, and how close counts as there. */
	@Unique
	@Nullable
	private BlockPos aliveworkplace$followedTarget;
	@Unique
	private int aliveworkplace$followedReach;

	@Inject(method = "createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("RETURN"))
	private void aliveworkplace$rememberWorked(Set<BlockPos> targets, int regionOffset, boolean offsetUpward, int reach, float range,
			CallbackInfoReturnable<Path> cir) {
		Path worked = cir.getReturnValue();
		if (worked != null && worked != this.path) {
			aliveworkplace$worked = worked;
			aliveworkplace$workedReach = reach;
		}
	}

	@Inject(method = "moveTo(Lnet/minecraft/world/level/pathfinder/Path;D)Z", at = @At("RETURN"))
	private void aliveworkplace$rememberFollowed(@Nullable Path given, double speed, CallbackInfoReturnable<Boolean> cir) {
		if (given != null && given == this.path && given.getTarget() != null) {
			aliveworkplace$followedTarget = given.getTarget();
			aliveworkplace$followedReach = given == aliveworkplace$worked ? aliveworkplace$workedReach : reachRange;
		}
	}

	@Inject(method = "recomputePath()V", at = @At("HEAD"))
	private void aliveworkplace$recomputeTheFollowedPath(CallbackInfo ci) {
		// only while the mob is on that path: a path worked out again keeps its target, so this holds through repeats
		if (this.path != null && aliveworkplace$followedTarget != null && aliveworkplace$followedTarget.equals(this.path.getTarget())) {
			targetPos = aliveworkplace$followedTarget;
			reachRange = aliveworkplace$followedReach;
		}
	}
}
