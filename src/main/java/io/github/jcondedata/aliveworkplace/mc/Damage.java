package io.github.jcondedata.aliveworkplace.mc;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Hurting things. Change: {@code hurt(source, amount)} becomes {@code hurtServer(level, source, amount)} by 1.21.4. */
public final class Damage {
	/** Hurts {@code target} on the server; false if it took no damage. */
	public static boolean hurt(LivingEntity target, DamageSource source, float amount) {
		return target.hurt(source, amount);
	}

	private Damage() {
	}
}
