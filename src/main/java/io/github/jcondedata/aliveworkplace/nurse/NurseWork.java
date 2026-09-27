package io.github.jcondedata.aliveworkplace.nurse;

import com.google.common.collect.ImmutableMap;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;

/** While at work, a nurse tends to hurt villagers and iron golems nearby (guards included). */
public class NurseWork extends Behavior<Villager> {
	private static final int EVERY = 100;
	private static final int RANGE = 16;
	private static final float HEAL = 4f;

	private int timer;

	public NurseWork() {
		super(ImmutableMap.of(MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT), 1200);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return !villager.isSleeping();
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return !villager.isSleeping();
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		WorkerStatus.set(villager, Component.translatable("entity.minecraft.villager.nurse"), -1f,
			Component.translatable("message.aliveworkplace.nurse.state.open").withStyle(ChatFormatting.GRAY));
		if (++timer % EVERY != 0) {
			return;
		}
		AABB area = villager.getBoundingBox().inflate(RANGE, 4, RANGE);
		for (LivingEntity patient : level.getEntitiesOfClass(LivingEntity.class, area,
				e -> (e instanceof Villager || e instanceof IronGolem) && e != villager && e.isAlive() && e.getHealth() < e.getMaxHealth())) {
			patient.heal(HEAL);
			level.sendParticles(ParticleTypes.HEART, patient.getX(), patient.getY() + patient.getBbHeight() + 0.3, patient.getZ(), 2, 0.2, 0.1, 0.2, 0);
		}
	}
}
