package io.github.jcondedata.aliveworkplace.nurse;

import com.google.common.collect.ImmutableMap;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Requests;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.Comparator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/**
 * While at work, a nurse tends to hurt villagers and iron golems nearby (guards included), and cures the village's ill
 * (see {@link Sickness}) with a remedy from the chest by her station — a honey bottle, a bucket of milk or a potion of
 * healing or regeneration — asking for one on the requests board when there's none.
 */
public class NurseWork extends Behavior<Villager> {
	private static final int EVERY = 100;
	private static final int RANGE = 16;
	/** How far the nurse looks for the ill. */
	public static final int CURE_RANGE = 32;
	private static final float HEAL = 4f;

	private int timer;
	private Component state = Component.translatable("message.aliveworkplace.nurse.state.open").withStyle(ChatFormatting.GRAY);

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
		WorkerStatus.set(villager, Component.translatable("entity.minecraft.villager.nurse"), -1f, state);
		if (++timer % EVERY != 0) {
			return;
		}
		AABB area = villager.getBoundingBox().inflate(RANGE, 4, RANGE);
		for (LivingEntity patient : level.getEntitiesOfClass(LivingEntity.class, area,
				e -> (e instanceof Villager || e instanceof IronGolem) && e != villager && e.isAlive() && e.getHealth() < e.getMaxHealth())) {
			patient.heal(HEAL);
			level.sendParticles(ParticleTypes.HEART, patient.getX(), patient.getY() + patient.getBbHeight() + 0.3, patient.getZ(), 2, 0.2, 0.1, 0.2, 0);
		}
		state = cure(level, villager);
	}

	/** Cures the nearest ill villager if there's a remedy by the station; what she's doing, for her status. */
	static Component cure(ServerLevel level, Villager nurse) {
		List<Villager> ill = level.getEntitiesOfClass(Villager.class, nurse.getBoundingBox().inflate(CURE_RANGE, 8, CURE_RANGE),
				v -> v.isAlive() && Sickness.isIll(v)).stream()
			.sorted(Comparator.comparingDouble(v -> v.distanceToSqr(nurse))).toList();
		if (ill.isEmpty()) {
			Requests.clear(nurse);
			return Component.translatable("message.aliveworkplace.nurse.state.open").withStyle(ChatFormatting.GRAY);
		}
		GlobalPos site = nurse.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
		List<BlockPos> chests = site == null ? List.of() : SupplyContainers.find(level, site.pos(), null);
		ItemStack remedy = SupplyContainers.takeOne(level, chests, Sickness::isRemedy);
		if (remedy.isEmpty()) {
			Requests.post(nurse, new ItemStack(Items.HONEY_BOTTLE), 1, Component.translatable("request.aliveworkplace.remedy"), Sickness::isRemedy);
			return Component.translatable("message.aliveworkplace.nurse.state.no_remedy", ill.size()).withStyle(ChatFormatting.YELLOW);
		}
		Requests.clear(nurse);
		Villager patient = ill.get(0);
		ItemStack left = Sickness.leftover(remedy);
		if (!left.isEmpty()) {
			ItemStack rest = SupplyContainers.insert(level, chests, left);
			if (!rest.isEmpty()) {
				nurse.spawnAtLocation(rest);
			}
		}
		Sickness.recover(level, patient);
		level.sendParticles(ParticleTypes.HEART, patient.getX(), patient.getY() + patient.getBbHeight() + 0.3, patient.getZ(), 4, 0.3, 0.2, 0.3, 0);
		level.playSound(null, patient.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.NEUTRAL, 0.6f, 1.1f);
		ModAttachments.VILLAGERS_CURED.set(nurse, ModAttachments.VILLAGERS_CURED.getOrElse(nurse, 0) + 1);
		BuilderLevels.addXp(level, nurse, 2, null);
		return Component.translatable("message.aliveworkplace.nurse.state.cured", patient.getDisplayName()).withStyle(ChatFormatting.GREEN);
	}
}
