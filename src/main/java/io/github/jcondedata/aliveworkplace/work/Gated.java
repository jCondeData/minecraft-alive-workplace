package io.github.jcondedata.aliveworkplace.work;

import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;

/**
 * Wraps a vanilla behaviour so it only runs while {@code allowed} holds, and stops it as soon as it
 * doesn't. Used to pause a vanilla job's own routine while one of our jobs has the villager busy.
 */
public final class Gated<E extends LivingEntity> implements BehaviorControl<E> {
	private final Predicate<E> allowed;
	private final BehaviorControl<? super E> inner;

	public Gated(Predicate<E> allowed, BehaviorControl<? super E> inner) {
		this.allowed = allowed;
		this.inner = inner;
	}

	@Override
	public Behavior.Status getStatus() {
		return inner.getStatus();
	}

	@Override
	public boolean tryStart(ServerLevel level, E entity, long gameTime) {
		return allowed.test(entity) && inner.tryStart(level, entity, gameTime);
	}

	@Override
	public void tickOrStop(ServerLevel level, E entity, long gameTime) {
		if (allowed.test(entity)) {
			inner.tickOrStop(level, entity, gameTime);
		} else {
			inner.doStop(level, entity, gameTime);
		}
	}

	@Override
	public void doStop(ServerLevel level, E entity, long gameTime) {
		inner.doStop(level, entity, gameTime);
	}

	@Override
	public String debugString() {
		return inner.debugString();
	}
}
