package io.github.jcondedata.aliveworkplace.guard;

import com.google.common.collect.ImmutableMap;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * A guard's fighting, in every activity (it's in the CORE package): spot a monster near the Guard Post,
 * wake up if need be, close in and hit it with the best weapon they have. Out of a fight they slowly
 * heal. Guards never panic (see the panic mixin).
 */
public class GuardCombat extends Behavior<Villager> {
	private static final int SEARCH_EVERY = 10;
	private static final int COOLDOWN = 14;
	private static final double REACH_SQR = 2.6 * 2.6;
	private static final float CHASE_SPEED = 0.75f;
	private static final int XP_PER_KILL = 2;
	/** Guards with a bow shoot foes up to this far away, and keep creepers at least {@link #CREEPER_DISTANCE} away. */
	static final double BOW_RANGE = 16;
	private static final double CREEPER_DISTANCE = 7;
	private static final double MELEE_FROM = 4.5;
	private static final int SHOT_COOLDOWN = 22;

	/** Guards in a fight right now (their patrol waits). */
	private static final Set<Villager> FIGHTING = Collections.newSetFromMap(new WeakHashMap<>());

	@Nullable
	private LivingEntity target;
	private int searchTimer;
	private int cooldown;
	private int calm;

	public GuardCombat() {
		super(ImmutableMap.of(
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 1200);
	}

	public static boolean isFighting(Villager villager) {
		return FIGHTING.contains(villager);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		if (!Guards.isGuard(villager)) {
			return false;
		}
		// Between fights: heal slowly and keep the guard's extra health.
		if (++calm % 40 == 0 && villager.getHealth() < villager.getMaxHealth()) {
			villager.heal(1f);
		}
		if (calm % 200 == 0) {
			Guards.updateHealth(villager);
		}
		if (--searchTimer > 0) {
			return false;
		}
		searchTimer = SEARCH_EVERY;
		target = findFoe(level, villager);
		return target != null;
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return target != null && Guards.isFoe(target, villager) && target.level() == level && inArea(villager, target)
			&& villager.distanceToSqr(target) < 40 * 40;
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		if (villager.isSleeping()) {
			villager.stopSleeping();
		}
		FIGHTING.add(villager);
		cooldown = 0;
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		FIGHTING.remove(villager);
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		target = null;
		searchTimer = 0;
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		LivingEntity foe = target;
		if (foe == null) {
			return;
		}
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(foe, true));
		villager.getLookControl().setLookAt(foe, 30f, 30f);
		WorkerStatus.set(villager, title(villager), -1f,
			Component.translatable("message.aliveworkplace.guard.state.fighting", foe.getDisplayName()).withStyle(ChatFormatting.RED));
		if (cooldown > 0) {
			cooldown--;
		}
		// With a bow: shoot creepers, fliers and anything still a few steps off; back away from creepers.
		double distance = Math.sqrt(villager.distanceToSqr(foe));
		boolean creeper = foe instanceof net.minecraft.world.entity.monster.Creeper;
		if (Guards.hasBow(villager) && villager.hasLineOfSight(foe) && distance <= BOW_RANGE
			&& (creeper || foe instanceof net.minecraft.world.entity.FlyingMob || distance > MELEE_FROM)) {
			if (creeper && distance < CREEPER_DISTANCE) {
				backAway(villager, foe);
			} else {
				villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
				villager.getNavigation().stop();
			}
			if (cooldown == 0 && clearShot(level, villager, foe)) {
				shoot(level, villager, foe);
				cooldown = SHOT_COOLDOWN;
			}
			return;
		}
		if (creeper) {
			// No bow any more (it broke): leave the creeper be.
			target = null;
			return;
		}
		if (villager.distanceToSqr(foe) > REACH_SQR || !villager.hasLineOfSight(foe)) {
			villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(foe, false), CHASE_SPEED, 1));
			return;
		}
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		villager.getNavigation().stop();
		if (cooldown == 0) {
			strike(level, villager, foe);
			cooldown = COOLDOWN;
		}
	}

	private static void strike(ServerLevel level, Villager villager, LivingEntity foe) {
		ItemStack weapon = villager.getItemBySlot(EquipmentSlot.MAINHAND);
		DamageSource source = level.damageSources().mobAttack(villager);
		float damage = Guards.baseDamage(weapon) * Guards.levelBonus(villager);
		damage = EnchantmentHelper.modifyDamage(level, weapon, foe, source, damage);
		villager.swing(InteractionHand.MAIN_HAND);
		if (!foe.hurt(source, damage)) {
			return;
		}
		foe.knockback(0.4, villager.getX() - foe.getX(), villager.getZ() - foe.getZ());
		EnchantmentHelper.doPostAttackEffectsWithItemSource(level, foe, source, weapon);
		if (!weapon.isEmpty() && weapon.isDamageableItem()) {
			weapon.hurtAndBreak(1, villager, EquipmentSlot.MAINHAND);
		}
		if (!foe.isAlive()) {
			villager.setAttached(ModAttachments.GUARD_KILLS, villager.getAttachedOrElse(ModAttachments.GUARD_KILLS, 0) + 1);
			BuilderLevels.addXp(level, villager, XP_PER_KILL, null);
		}
	}

	/** Steps away from a creeper (it would blow up next to the guard). */
	private static void backAway(Villager villager, LivingEntity foe) {
		net.minecraft.world.phys.Vec3 away = villager.position().subtract(foe.position()).multiply(1, 0, 1);
		if (away.lengthSqr() < 1.0E-4) {
			away = new net.minecraft.world.phys.Vec3(1, 0, 0);
		}
		net.minecraft.world.phys.Vec3 to = villager.position().add(away.normalize().scale(CREEPER_DISTANCE));
		villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(BlockPos.containing(to), CHASE_SPEED + 0.1f, 0));
	}

	/** Nobody but the foe near the line of fire (the arrow could hit a villager, a player or a pet). */
	private static boolean clearShot(ServerLevel level, Villager villager, LivingEntity foe) {
		net.minecraft.world.phys.Vec3 from = villager.getEyePosition();
		net.minecraft.world.phys.Vec3 to = foe.getBoundingBox().getCenter();
		return level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(1.0),
			e -> e != villager && e != foe && !Guards.isFoe(e, villager) && e.getBoundingBox().inflate(0.6).clip(from, to).isPresent()).isEmpty();
	}

	/** Looses an arrow at the foe, like a skeleton; the arrow can't be picked up. */
	private static void shoot(ServerLevel level, Villager villager, LivingEntity foe) {
		ItemStack bow = villager.getItemBySlot(EquipmentSlot.OFFHAND);
		net.minecraft.world.entity.projectile.AbstractArrow arrow = net.minecraft.world.entity.projectile.ProjectileUtil.getMobArrow(
			villager, new ItemStack(net.minecraft.world.item.Items.ARROW), 1.0f, bow);
		double dx = foe.getX() - villager.getX();
		double dy = foe.getY(0.3333) - arrow.getY();
		double dz = foe.getZ() - villager.getZ();
		double flat = Math.sqrt(dx * dx + dz * dz);
		arrow.shoot(dx, dy + flat * 0.2, dz, 1.6f, 4f);
		arrow.setBaseDamage(arrow.getBaseDamage() * Guards.levelBonus(villager));
		arrow.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED;
		level.addFreshEntity(arrow);
		level.playSound(null, villager.getX(), villager.getY(), villager.getZ(), net.minecraft.sounds.SoundEvents.SKELETON_SHOOT,
			net.minecraft.sounds.SoundSource.NEUTRAL, 1f, 1f / (villager.getRandom().nextFloat() * 0.4f + 0.8f));
		villager.swing(InteractionHand.OFF_HAND);
		bow.hurtAndBreak(1, villager, EquipmentSlot.OFFHAND);
	}

	/** Counts a kill made with an arrow (called when a foe dies). */
	public static void onFoeKilled(ServerLevel level, LivingEntity foe, DamageSource source) {
		if (source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow
			&& source.getEntity() instanceof Villager guard && Guards.isGuard(guard)) {
			guard.setAttached(ModAttachments.GUARD_KILLS, guard.getAttachedOrElse(ModAttachments.GUARD_KILLS, 0) + 1);
			BuilderLevels.addXp(level, guard, XP_PER_KILL, null);
		}
	}

	/** Nearest monster in the guard's area that they can see (or that is right next to them). */
	@Nullable
	private static LivingEntity findFoe(ServerLevel level, Villager villager) {
		BlockPos center = center(villager);
		AABB area = new AABB(center).inflate(Guards.RADIUS, 8, Guards.RADIUS);
		return level.getEntitiesOfClass(LivingEntity.class, area, e -> Guards.isFoe(e, villager)).stream()
			.filter(e -> villager.hasLineOfSight(e) || villager.distanceToSqr(e) < 16)
			.min(Comparator.comparingDouble(villager::distanceToSqr))
			.orElse(null);
	}

	private static boolean inArea(Villager villager, LivingEntity foe) {
		return foe.blockPosition().closerThan(center(villager), Guards.RADIUS + 8);
	}

	/** Where the guard keeps watch: the bell while answering it, else their Guard Post. */
	private static BlockPos center(Villager villager) {
		return GuardRally.rallyPoint(villager).orElseGet(() -> Builders.benchPos(villager).orElse(villager.blockPosition()));
	}

	static Component title(Villager villager) {
		return Component.translatable("message.aliveworkplace.guard.title", villager.getAttachedOrElse(ModAttachments.GUARD_KILLS, 0));
	}
}
