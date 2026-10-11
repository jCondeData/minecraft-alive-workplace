package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.work.PathBudget;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Villagers' path searches go through the level's {@link PathBudget} (25.3). Vanilla's walking behaviour searches when
 * it starts on a walk target ({@code checkExtraStartConditions}) and again when the target moves while it walks
 * ({@code tick}). Held back at the start, the behaviour just doesn't start this tick: the walk target stays and it asks
 * again the next (vanilla would erase the target if the search itself failed). Held back while walking, it keeps
 * following its old path a tick longer. One behaviour per villager, so the time it has waited is kept here.
 */
@Mixin(MoveToTargetSink.class)
abstract class MoveToTargetSinkMixin {
	@Shadow
	private int remainingCooldown;

	@Unique
	private long aliveworkplace$waitingSince = -1;
	@Unique
	private boolean aliveworkplace$granted;

	@Inject(method = "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Mob;)Z",
		at = @At("HEAD"), cancellable = true)
	private void aliveworkplace$budgetStart(ServerLevel level, Mob mob, CallbackInfoReturnable<Boolean> cir) {
		aliveworkplace$granted = false;
		if (!(mob instanceof Villager) || remainingCooldown > 0) {
			return;
		}
		WalkTarget target = mob.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
		// Vanilla's reachedTarget: there already, no search.
		if (target == null || target.getTarget().currentBlockPosition().distManhattan(mob.blockPosition()) <= target.getCloseEnoughDist()) {
			return;
		}
		if (!aliveworkplace$take(level)) {
			cir.setReturnValue(false);
			return;
		}
		aliveworkplace$granted = true;
	}

	@Inject(method = "tryComputePath(Lnet/minecraft/world/entity/Mob;Lnet/minecraft/world/entity/ai/memory/WalkTarget;J)Z",
		at = @At("HEAD"), cancellable = true)
	private void aliveworkplace$budgetSearch(Mob mob, WalkTarget target, long time, CallbackInfoReturnable<Boolean> cir) {
		if (aliveworkplace$granted) {
			// Counted when it started.
			aliveworkplace$granted = false;
			return;
		}
		if (mob instanceof Villager && mob.level() instanceof ServerLevel level && !aliveworkplace$take(level)) {
			cir.setReturnValue(false);
		}
	}

	@Unique
	private boolean aliveworkplace$take(ServerLevel level) {
		if (PathBudget.take(level, aliveworkplace$waitingSince)) {
			aliveworkplace$waitingSince = -1;
			return true;
		}
		if (aliveworkplace$waitingSince < 0) {
			aliveworkplace$waitingSince = level.getGameTime();
		}
		return false;
	}
}
