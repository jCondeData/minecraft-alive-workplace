package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.legend.GolemPaths;
import io.github.jcondedata.aliveworkplace.work.PathObstacles;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A villager's path doesn't run under a shut trapdoor it can't stand under, such as a window's flower box at head height
 * (B156, {@link ShutTrapdoors}): vanilla counts every trapdoor as open, and the villager stuck there. Nor through a small
 * block a mod calls passable, such as Carved Wood's lantern on a desk (B339, {@link PathObstacles}). A working golem's
 * path keeps clear of lava sunk below the ground and never drops into water too low to climb out of (B813, B814,
 * {@link GolemPaths}).
 */
@Mixin(WalkNodeEvaluator.class)
abstract class WalkNodeEvaluatorMixin extends NodeEvaluator {
	@Inject(method = "getPathTypeOfMob(Lnet/minecraft/world/level/pathfinder/PathfindingContext;IIILnet/minecraft/world/entity/Mob;)Lnet/minecraft/world/level/pathfinder/PathType;",
		at = @At("RETURN"), cancellable = true)
	private void aliveworkplace$shutTrapdoors(PathfindingContext context, int x, int y, int z, Mob mob, CallbackInfoReturnable<PathType> cir) {
		if (mob.getPathfindingMalus(cir.getReturnValue()) < 0.0F) {
			return;
		}
		if (PathObstacles.blocksVillager(context.level(), mob, x, y, z)) {
			cir.setReturnValue(PathType.BLOCKED);
		} else if (GolemPaths.atWork(mob) && GolemPaths.besideSunkenFire(context.level(), mob, x, y, z)) {
			cir.setReturnValue(PathType.DANGER_FIRE); // which a working golem's paths keep clear of (B814)
		}
	}

	/** {@code x, y, z}: the column a path steps into from height {@code y}, open there, so the path drops to the ground below. */
	@Inject(method = "tryFindFirstGroundNodeBelow(III)Lnet/minecraft/world/level/pathfinder/Node;", at = @At("RETURN"), cancellable = true)
	private void aliveworkplace$noDropIntoLowWater(int x, int y, int z, CallbackInfoReturnable<Node> cir) {
		Node landing = cir.getReturnValue();
		if (landing != null && landing.type == PathType.WATER && GolemPaths.atWork(this.mob) && GolemPaths.tooLowToLeave(y, landing.y)) {
			// a fresh node, not the cached one: the same water may still be reached by swimming (B813)
			Node blocked = new Node(landing.x, landing.y, landing.z);
			blocked.type = PathType.BLOCKED;
			blocked.costMalus = -1.0F;
			cir.setReturnValue(blocked);
		}
	}
}
