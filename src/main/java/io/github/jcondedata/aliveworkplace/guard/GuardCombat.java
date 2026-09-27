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
		return target != null && Guards.isFoe(target) && target.level() == level && inArea(villager, target)
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

	/** Nearest monster in the guard's area that they can see (or that is right next to them). */
	@Nullable
	private static LivingEntity findFoe(ServerLevel level, Villager villager) {
		BlockPos center = Builders.benchPos(villager).orElse(villager.blockPosition());
		AABB area = new AABB(center).inflate(Guards.RADIUS, 8, Guards.RADIUS);
		return level.getEntitiesOfClass(LivingEntity.class, area, Guards::isFoe).stream()
			.filter(e -> villager.hasLineOfSight(e) || villager.distanceToSqr(e) < 16)
			.min(Comparator.comparingDouble(villager::distanceToSqr))
			.orElse(null);
	}

	private static boolean inArea(Villager villager, LivingEntity foe) {
		BlockPos center = Builders.benchPos(villager).orElse(villager.blockPosition());
		return foe.blockPosition().closerThan(center, Guards.RADIUS + 8);
	}

	static Component title(Villager villager) {
		return Component.translatable("message.aliveworkplace.guard.title", villager.getAttachedOrElse(ModAttachments.GUARD_KILLS, 0));
	}
}
