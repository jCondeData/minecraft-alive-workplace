package io.github.jcondedata.aliveworkplace.people;

import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

/**
 * Sickness, in a village with a Village Hall: now and then a grown villager falls ill — more often when they're hungry or
 * have no bed of their own. The ill work at half pace and walk slowly, and sneeze now and then; they get well by
 * themselves after {@link #RECOVERY}, or at once when a Nurse gives them a remedy from her chest (a honey bottle, a
 * bucket of milk, or a potion of healing or regeneration — see {@code NurseWork}). {@code villagerSickness} in the
 * config turns it off.
 */
public final class Sickness {
	/** Whether villagers fall ill at all. */
	public static boolean ENABLED = true;
	/** The chance a day that a well-kept villager falls ill; hungry and homeless villagers are likelier to. */
	public static float DAILY = 0.02f;
	static final float HUNGRY = 0.08f;
	static final float HOMELESS = 0.04f;
	/** How long an illness lasts without a nurse. */
	public static final long RECOVERY = 3 * VillageNeeds.DAY;
	/** The ill work this much slower. */
	public static final float PACE = 2f;

	public static boolean isIll(Villager villager) {
		return ModAttachments.ILL_SINCE.has(villager);
	}

	/** Work delay multiplier: the ill work at half pace. */
	public static float pace(Villager villager) {
		return isIll(villager) ? PACE : 1f;
	}

	/**
	 * The hall's round for one grown villager ({@code rounds}: how many rounds make a day): the ill get better once their
	 * time is up (and sneeze meanwhile), the well may fall ill.
	 */
	public static void round(ServerLevel level, Villager villager, int rounds) {
		Long since = ModAttachments.ILL_SINCE.get(villager);
		long now = level.getGameTime();
		if (since != null) {
			if (now - since >= RECOVERY) {
				recover(level, villager);
			} else {
				villager.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 40, 0, false, false));
				sneeze(level, villager);
			}
			return;
		}
		if (!ENABLED) {
			return;
		}
		if (level.random.nextFloat() < dailyChance(level, villager) / rounds) {
			fallIll(level, villager);
		}
	}

	/**
	 * The chance a day that {@code villager} falls ill: higher when hungry, higher again without a bed, and as much more
	 * as their village's {@code sickness} effects say (Large Families, 30.6: half again).
	 */
	public static float dailyChance(ServerLevel level, Villager villager) {
		float chance = DAILY + (VillageNeeds.isHungry(villager, level.getGameTime()) ? HUNGRY : 0f) + (VillageNeeds.bed(level, villager) == null ? HOMELESS : 0f);
		chance *= Math.max(0f, 1f + io.github.jcondedata.aliveworkplace.hall.CivicEffects.of(villager).sickness(villager) / 100f);
		// Medicine: a third less likely a level.
		return chance * (1f - io.github.jcondedata.aliveworkplace.research.Research.level(villager, io.github.jcondedata.aliveworkplace.research.Research.Topic.MEDICINE) / 3f);
	}

	public static void fallIll(ServerLevel level, Villager villager) {
		ModAttachments.ILL_SINCE.set(villager, level.getGameTime());
		villager.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 40, 0, false, false));
		sneeze(level, villager);
	}

	/** Well again: the slowness goes, and a little happy puff. */
	public static void recover(ServerLevel level, Villager villager) {
		ModAttachments.ILL_SINCE.remove(villager);
		villager.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getY() + 1.2, villager.getZ(), 8, 0.4, 0.5, 0.4, 0);
	}

	static void sneeze(ServerLevel level, Villager villager) {
		level.sendParticles(ParticleTypes.SNEEZE, villager.getX(), villager.getEyeY(), villager.getZ(), 6, 0.2, 0.1, 0.2, 0.02);
		level.playSound(null, villager.blockPosition(), SoundEvents.PANDA_SNEEZE, SoundSource.NEUTRAL, 0.4f, 1.4f);
	}

	/** What a nurse cures an illness with. */
	public static boolean isRemedy(ItemStack stack) {
		if (stack.is(Items.HONEY_BOTTLE) || stack.is(Items.MILK_BUCKET)) {
			return true;
		}
		PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
		return potion != null && (stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION))
			&& java.util.stream.StreamSupport.stream(potion.getAllEffects().spliterator(), false)
				.anyMatch(e -> e.is(MobEffects.HEAL) || e.is(MobEffects.REGENERATION));
	}

	/** What's left of a remedy once it's used: the empty bottle or bucket. */
	public static ItemStack leftover(ItemStack remedy) {
		if (remedy.is(Items.MILK_BUCKET)) {
			return new ItemStack(Items.BUCKET);
		}
		return remedy.is(Items.HONEY_BOTTLE) || remedy.is(Items.POTION) ? new ItemStack(Items.GLASS_BOTTLE) : ItemStack.EMPTY;
	}

	private Sickness() {
	}
}
