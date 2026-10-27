package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.work.PathObstacles;
import net.minecraft.world.entity.Mob;
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
 * block a mod calls passable, such as Carved Wood's lantern on a desk (B339, {@link PathObstacles}).
 */
@Mixin(WalkNodeEvaluator.class)
abstract class WalkNodeEvaluatorMixin {
	@Inject(method = "getPathTypeOfMob(Lnet/minecraft/world/level/pathfinder/PathfindingContext;IIILnet/minecraft/world/entity/Mob;)Lnet/minecraft/world/level/pathfinder/PathType;",
		at = @At("RETURN"), cancellable = true)
	private void aliveworkplace$shutTrapdoors(PathfindingContext context, int x, int y, int z, Mob mob, CallbackInfoReturnable<PathType> cir) {
		if (mob.getPathfindingMalus(cir.getReturnValue()) >= 0.0F && PathObstacles.blocksVillager(context.level(), mob, x, y, z)) {
			cir.setReturnValue(PathType.BLOCKED);
		}
	}
}
