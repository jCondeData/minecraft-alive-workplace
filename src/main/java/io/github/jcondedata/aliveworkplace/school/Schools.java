package io.github.jcondedata.aliveworkplace.school;

import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;

/**
 * School: a Teacher at a Teacher's Desk gathers the village's children for lessons; a child with {@link #LESSONS_NEEDED}
 * ticks of lessons has been to school, and when they grow up and take a job they start a level up (an Apprentice, with
 * the Novice and Apprentice trades).
 */
public final class Schools {
	/** Ticks of lessons a child needs to have been to school (a couple of minutes in class). */
	public static int LESSONS_NEEDED = 2400;

	public static boolean isTeacher(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == ModVillagers.TEACHER;
	}

	/** Whether a villager went to school (as a child). */
	public static boolean isSchooled(Villager villager) {
		return ModAttachments.SCHOOLED.getOrElse(villager, false);
	}

	/** Ticks of lessons a child has had so far. */
	public static int lessons(Villager villager) {
		return ModAttachments.LESSONS.getOrElse(villager, 0);
	}

	/** {@code ticks} more of lessons for a child; true when that finishes their schooling. */
	public static boolean teach(ServerLevel level, Villager child, int ticks) {
		if (isSchooled(child)) {
			return false;
		}
		int total = lessons(child) + ticks;
		ModAttachments.LESSONS.set(child, total);
		if (total < LESSONS_NEEDED) {
			return false;
		}
		ModAttachments.SCHOOLED.set(child, true);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, child.getX(), child.getY() + 1.0, child.getZ(), 10, 0.3, 0.4, 0.3, 0.0);
		level.playSound(null, child.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.4f, 1.6f);
		return true;
	}

	/**
	 * Called when a villager takes their first job (from {@code old} to {@code now}): true if they went to school and
	 * should start a level up (the caller does it once the job is set).
	 */
	public static boolean startsAhead(Villager villager, VillagerData old, VillagerData now) {
		return old.getProfession() == VillagerProfession.NONE && now.getProfession() != VillagerProfession.NONE
			&& now.getProfession() != VillagerProfession.NITWIT && now.getLevel() == 1 && villager.getVillagerXp() == 0
			&& !villager.isBaby() && headStartLevel(villager) > 1 && !ModAttachments.SCHOOL_BONUS.getOrElse(villager, false);
	}

	/** The level a villager starts their first job at: Apprentice after school, a hired traveller's own level, else Novice. */
	public static int headStartLevel(Villager villager) {
		int school = isSchooled(villager)
			? 2 + io.github.jcondedata.aliveworkplace.research.Research.level(villager, io.github.jcondedata.aliveworkplace.research.Research.Topic.LORE) : 1;
		return Math.max(school, Math.min(VillagerData.MAX_VILLAGER_LEVEL, ModAttachments.HEAD_START.getOrElse(villager, 1)));
	}

	/** A villager's head start at their first job: the trades of every level on the way, up to their start level. */
	public static void headStart(Villager villager) {
		ModAttachments.SCHOOL_BONUS.set(villager, true);
		villager.getOffers(); // the Novice trades first
		int target = headStartLevel(villager);
		while (villager.getVillagerData().getLevel() < target) {
			villager.setVillagerXp(VillagerData.getMinXpPerLevel(villager.getVillagerData().getLevel() + 1));
			((io.github.jcondedata.aliveworkplace.mixin.VillagerAccessor) villager).aliveworkplace$increaseMerchantCareer();
		}
	}

	private Schools() {
	}
}
