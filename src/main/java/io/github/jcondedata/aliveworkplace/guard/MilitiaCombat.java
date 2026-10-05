package io.github.jcondedata.aliveworkplace.guard;

import com.google.common.collect.ImmutableMap;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.Conscription;
import io.github.jcondedata.aliveworkplace.mc.Damage;
import io.github.jcondedata.aliveworkplace.people.Traits;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import org.jetbrains.annotations.Nullable;

/**
 * A conscript's fighting under Conscription (ROADMAP 30.10, {@link Conscription}), in every grown villager's CORE
 * activity but the guards' (they have {@link GuardCombat}): while the village is raided they hold the militia's stone
 * sword, and when a raider (a foe by the guards' rules, {@link Guards#isFoe}) is within the {@code militia} effect's
 * range they wake, come out of hiding, close in and strike: its {@code damage} a blow, 15% more if Strong
 * ({@link Traits#strength}). Out of a raid the sword is put away.
 */
public class MilitiaCombat extends Behavior<Villager> {
	private static final int SEARCH_EVERY = 10;
	private static final int COOLDOWN = 14;
	private static final double REACH_SQR = 2.6 * 2.6;
	private static final float CHASE_SPEED = 0.75f;

	/** Conscripts in a fight right now (their schedule waits, {@code mixin/BrainMixin}). */
	private static final Set<Villager> FIGHTING = Collections.newSetFromMap(new WeakHashMap<>());

	@Nullable
	private LivingEntity target;
	private float damage;
	private int range;
	private int searchTimer;
	private int cooldown;

	public MilitiaCombat() {
		super(ImmutableMap.of(
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 1200);
	}

	public static boolean isFighting(Villager villager) {
		synchronized (FIGHTING) {
			return FIGHTING.contains(villager);
		}
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		if (--searchTimer > 0) {
			return false;
		}
		searchTimer = SEARCH_EVERY;
		CivicEffects.Militia militia = Conscription.militia(villager);
		if (militia == null) {
			Conscription.disarm(villager);
			return false;
		}
		Conscription.arm(villager);
		damage = militia.damage();
		range = militia.range();
		target = findFoe(level, villager, range);
		return target != null;
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return target != null && Guards.isFoe(target) && target.level() == level
			&& villager.distanceToSqr(target) < (double) (range + 8) * (range + 8) && Conscription.militia(villager) != null;
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		if (villager.isSleeping()) {
			villager.stopSleeping();
		}
		synchronized (FIGHTING) {
			FIGHTING.add(villager);
		}
		cooldown = 0;
		turnOut(villager);
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		synchronized (FIGHTING) {
			FIGHTING.remove(villager);
		}
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		target = null;
		searchTimer = 0;
	}

	/** Out of bed, out of hiding, no panic: the conscript's brain idles while they fight (nothing else steers them). */
	private static void turnOut(Villager villager) {
		var brain = villager.getBrain();
		brain.eraseMemory(MemoryModuleType.HEARD_BELL_TIME);
		brain.eraseMemory(MemoryModuleType.HIDING_PLACE);
		if (!brain.isActive(Activity.IDLE)) {
			brain.setActiveActivityIfPossible(Activity.IDLE);
		}
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		LivingEntity foe = target;
		if (foe == null) {
			return;
		}
		if (villager.isSleeping()) {
			villager.stopSleeping();
		}
		turnOut(villager);
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(foe, true));
		villager.getLookControl().setLookAt(foe, 30f, 30f);
		if (cooldown > 0) {
			cooldown--;
		}
		if (villager.distanceToSqr(foe) > REACH_SQR || !villager.hasLineOfSight(foe)) {
			villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(foe, false), CHASE_SPEED, 1));
			return;
		}
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		villager.getNavigation().stop();
		if (cooldown == 0) {
			strike(level, villager, foe, damage);
			cooldown = COOLDOWN;
		}
	}

	/** One blow: {@code damage}, 15% more if Strong. */
	static void strike(ServerLevel level, Villager villager, LivingEntity foe, float damage) {
		DamageSource source = level.damageSources().mobAttack(villager);
		villager.swing(InteractionHand.MAIN_HAND);
		if (Damage.hurt(foe, source, damage * Traits.strength(villager))) {
			foe.knockback(0.4, villager.getX() - foe.getX(), villager.getZ() - foe.getZ());
		}
	}

	/** The nearest raider within {@code range} blocks that the conscript can see (or that is right next to them). */
	@Nullable
	private static LivingEntity findFoe(ServerLevel level, Villager villager, int range) {
		double r = (double) range * range;
		return level.getEntitiesOfClass(LivingEntity.class, villager.getBoundingBox().inflate(range, 8, range),
				e -> Guards.isFoe(e) && e.distanceToSqr(villager) <= r).stream()
			.filter(e -> villager.hasLineOfSight(e) || villager.distanceToSqr(e) < 16)
			.min(Comparator.comparingDouble(villager::distanceToSqr))
			.orElse(null);
	}
}
