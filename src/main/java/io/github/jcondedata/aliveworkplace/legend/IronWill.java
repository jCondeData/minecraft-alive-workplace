package io.github.jcondedata.aliveworkplace.legend;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.npc.Villager;

/**
 * Iron Will (29.6): vanilla's "hide at the bell" and "the raid's on" triggers pass over a villager with
 * {@code no_panic}, so they keep to their schedule (and their work) through raids and the bell.
 */
public record IronWill(BehaviorControl<LivingEntity> inner) implements BehaviorControl<LivingEntity> {
	public static BehaviorControl<LivingEntity> wrap(BehaviorControl<LivingEntity> inner) {
		return new IronWill(inner);
	}

	@Override
	public Behavior.Status getStatus() {
		return inner.getStatus();
	}

	@Override
	public boolean tryStart(ServerLevel level, LivingEntity entity, long time) {
		if (entity instanceof Villager villager && Gifted.noPanic(villager)) {
			return false;
		}
		return inner.tryStart(level, entity, time);
	}

	@Override
	public void tickOrStop(ServerLevel level, LivingEntity entity, long time) {
		inner.tickOrStop(level, entity, time);
	}

	@Override
	public void doStop(ServerLevel level, LivingEntity entity, long time) {
		inner.doStop(level, entity, time);
	}

	@Override
	public String debugString() {
		return inner.debugString();
	}
}
