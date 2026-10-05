package io.github.jcondedata.aliveworkplace.mixin;

import java.util.Map;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The villagers' schedule, overruled:
 * <ul>
 *   <li>under Curfew a villager kept in rests from dusk to dawn whatever their schedule says (30.9, {@code hall/Curfew});</li>
 *   <li>under Conscription (30.10, {@code hall/Conscription}) a conscript in a fight stays out of the schedule's hands,
 *   and the one work gate: a villager whose work a raid has stopped leaves the WORK activity, every behaviour of it
 *   stops (our jobs' and vanilla's alike), and the schedule can't send them back until the gate opens.</li>
 * </ul>
 * The schedule check is of the villager whose brain runs now ({@code WorkerLimits.thinker}).
 */
@Mixin(Brain.class)
abstract class BrainMixin {
	@Shadow
	@Final
	private Map<Integer, Map<Activity, Set<BehaviorControl<?>>>> availableBehaviorsByPriority;

	@Inject(method = "updateActivityFromSchedule", at = @At("HEAD"), cancellable = true)
	private void aliveworkplace$curfew(long dayTime, long gameTime, CallbackInfo ci) {
		Villager villager = io.github.jcondedata.aliveworkplace.work.WorkerLimits.thinker();
		if (villager == null || villager.getBrain() != (Object) this) {
			return;
		}
		if (io.github.jcondedata.aliveworkplace.hall.Curfew.rest(villager)
			|| io.github.jcondedata.aliveworkplace.guard.MilitiaCombat.isFighting(villager)) {
			ci.cancel();
			return;
		}
		Brain<?> self = (Brain<?>) (Object) this;
		if (self.getSchedule().getActivityAt((int) (dayTime % 24000L)) == Activity.WORK
			&& io.github.jcondedata.aliveworkplace.hall.Conscription.workStopped(villager)) {
			if (!self.isActive(Activity.IDLE)) {
				self.setActiveActivityIfPossible(Activity.IDLE);
			}
			ci.cancel();
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	@Inject(method = "tick", at = @At("HEAD"))
	private void aliveworkplace$raidWorkGate(ServerLevel level, LivingEntity entity, CallbackInfo ci) {
		if (!(entity instanceof Villager villager) || !io.github.jcondedata.aliveworkplace.hall.Conscription.workStopped(villager)) {
			return;
		}
		Brain<?> self = (Brain<?>) (Object) this;
		if (self.isActive(Activity.WORK)) {
			self.setActiveActivityIfPossible(Activity.IDLE);
		}
		for (Map<Activity, Set<BehaviorControl<?>>> byActivity : availableBehaviorsByPriority.values()) {
			Set<BehaviorControl<?>> work = byActivity.get(Activity.WORK);
			if (work == null) {
				continue;
			}
			for (BehaviorControl behavior : work) {
				if (behavior.getStatus() == Behavior.Status.RUNNING) {
					behavior.doStop(level, entity, level.getGameTime());
				}
			}
		}
	}
}
