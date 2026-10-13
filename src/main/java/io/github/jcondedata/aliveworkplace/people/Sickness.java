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
 *
 * <p>A <b>hex</b> (ROADMAP 32.11) is an illness of another kind ({@code ModAttachments.ILL_KIND} {@link #HEX}; an
 * ordinary illness has none): a witch coven by the village lays it on a few villagers a night ({@code threat/Covens}).
 * The hexed are ill as the others are, with a swirl of witch particles in place of the sneeze, but the nurse's remedies
 * don't touch it and it doesn't pass by itself: it ends when the coven is broken, or when no coven is left by them.
 *
 * <p>The <b>Grey Cough</b> (ROADMAP 31.18, {@link #GREY}) is the illness of the Sickness story ({@code story/SicknessStory}):
 * it doesn't pass by itself, a nurse's remedy only takes its slowness away for a day ({@link #ease}), and it passes from
 * villager to villager at night. It ends when the healer's Three-Herb Tonic comes round, when it runs its course, or when
 * no Sickness story runs in the village any more.
 *
 * <p>The <b>plague</b> (ROADMAP 32.18, {@link #PLAGUE}) is another kind: caught from the ill while a plague is under
 * way ({@code threat/Plague}), not passing by itself, beyond the nurse; only the Physician's Remedy cures it (and any
 * other illness, hexes included), and keeps the villager well for ten days ({@code ModAttachments.WELL_UNTIL}).
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
	/** How long an illness lasts at most with Pharmacy (35.14): a day. */
	public static final long PHARMACY = VillageNeeds.DAY;

	public static boolean isIll(Villager villager) {
		return ModAttachments.ILL_SINCE.has(villager);
	}

	/** The kind of illness a hex is. */
	public static final String HEX = "hex";

	/** The kind of illness the plague is (32.18). */
	public static final String PLAGUE = "plague";

	/** Whether {@code villager} is ill with the plague (32.18): no nurse's remedy touches it, only a Physician's Remedy. */
	public static boolean isPlagueIll(Villager villager) {
		return isIll(villager) && PLAGUE.equals(ModAttachments.ILL_KIND.get(villager));
	}

	/** Whether a Remedy (32.18) still keeps {@code villager} well: they don't fall ill, nor are hexed, nor catch the plague. */
	public static boolean isImmune(Villager villager, long now) {
		Long until = ModAttachments.WELL_UNTIL.get(villager);
		return until != null && now < until;
	}

	/** {@code villager} catches the plague (32.18), unless they're Hardy, kept well by a Remedy, or ill already. */
	public static boolean catchPlague(ServerLevel level, Villager villager) {
		if (isIll(villager) || io.github.jcondedata.aliveworkplace.legend.Gifted.noIllness(villager) || isImmune(villager, level.getGameTime())) {
			return false;
		}
		ModAttachments.ILL_SINCE.set(villager, level.getGameTime());
		ModAttachments.ILL_KIND.set(villager, PLAGUE);
		villager.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 40, 0, false, false));
		sneeze(level, villager);
		return true;
	}

	/** Whether {@code villager} is ill with a coven's hex (32.11). */
	public static boolean isHexed(Villager villager) {
		return isIll(villager) && HEX.equals(ModAttachments.ILL_KIND.get(villager));
	}

	/** The kind of illness the Grey Cough is (31.18). */
	public static final String GREY = "grey_cough";

	/** Whether {@code villager} has the Grey Cough (31.18). */
	public static boolean isGrey(Villager villager) {
		return isIll(villager) && GREY.equals(ModAttachments.ILL_KIND.get(villager));
	}

	/**
	 * {@code villager} catches the Grey Cough (31.18), unless they're Hardy, ill already, or kept well by a Remedy (32.18:
	 * ten days well after one); true if they did.
	 */
	public static boolean greyCough(ServerLevel level, Villager villager) {
		if (io.github.jcondedata.aliveworkplace.legend.Gifted.noIllness(villager) || isIll(villager) || isImmune(villager, level.getGameTime())) {
			return false;
		}
		ModAttachments.ILL_SINCE.set(villager, level.getGameTime());
		ModAttachments.ILL_KIND.set(villager, GREY);
		villager.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 40, 0, false, false));
		cough(level, villager);
		return true;
	}

	/** The Grey Cough's cough: a puff of grey smoke and a low, rough sneeze. */
	public static void cough(ServerLevel level, Villager villager) {
		level.sendParticles(ParticleTypes.SMOKE, villager.getX(), villager.getEyeY(), villager.getZ(), 8, 0.15, 0.08, 0.15, 0.01);
		level.sendParticles(ParticleTypes.ASH, villager.getX(), villager.getEyeY() + 0.2, villager.getZ(), 10, 0.3, 0.2, 0.3, 0.02);
		level.playSound(null, villager.blockPosition(), SoundEvents.PANDA_SNEEZE, SoundSource.NEUTRAL, 0.5f, 0.6f);
	}

	/** A nurse's remedy eases the Grey Cough for a day (31.18): the slowness goes, the cough stays. */
	public static void ease(ServerLevel level, Villager villager) {
		ModAttachments.ILL_EASED_UNTIL.set(villager, level.getGameTime() + VillageNeeds.DAY);
		villager.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		level.sendParticles(ParticleTypes.HEART, villager.getX(), villager.getY() + villager.getBbHeight() + 0.3, villager.getZ(), 2, 0.3, 0.2, 0.3, 0);
	}

	/** Whether {@code villager}'s Grey Cough is eased now (a remedy within the day). */
	public static boolean eased(Villager villager, long now) {
		Long until = ModAttachments.ILL_EASED_UNTIL.get(villager);
		return until != null && until > now;
	}

	/** {@code villager} is hexed (32.11): ill with a curse no remedy touches, in a swirl of witch particles. */
	public static void hex(ServerLevel level, Villager villager) {
		if (isImmune(villager, level.getGameTime())) {
			return; // a Remedy keeps them well (32.18)
		}
		ModAttachments.ILL_SINCE.set(villager, level.getGameTime());
		ModAttachments.ILL_KIND.set(villager, HEX);
		villager.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 40, 0, false, false));
		swirl(level, villager);
		level.playSound(null, villager.blockPosition(), SoundEvents.WITCH_CELEBRATE, SoundSource.HOSTILE, 0.8f, 1f);
	}

	/** The witch particles over the hexed. */
	public static void swirl(ServerLevel level, Villager villager) {
		level.sendParticles(ParticleTypes.WITCH, villager.getX(), villager.getY() + villager.getBbHeight() + 0.2, villager.getZ(), 16, 0.35, 0.4, 0.35, 0.05);
	}

	/** How long {@code villager}'s illness lasts without a nurse: {@link #RECOVERY}, with its founded guilds' {@code recovery_days} (30.20) and the village's {@code illness} days (29.11). */
	public static long recovery(Villager villager) {
		long days = Math.max(VillageNeeds.DAY, RECOVERY + io.github.jcondedata.aliveworkplace.hall.Guilds.recoveryDays(villager) * VillageNeeds.DAY); // the Healers' Guild: two days (30.20)
		long lasts = io.github.jcondedata.aliveworkplace.research.TreeEffects.illnessLasts(villager, days);
		// Pharmacy (35.14): well in a day, while the Great Library stands.
		return io.github.jcondedata.aliveworkplace.research.Research.bonus(villager, io.github.jcondedata.aliveworkplace.research.Research.Topic.PHARMACY) > 0 ? Math.min(lasts, PHARMACY) : lasts;
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
			if (isHexed(villager)) {
				// A hex doesn't pass with time, nor for the Hardy; it lifts once no coven is left by them (32.11).
				if (!io.github.jcondedata.aliveworkplace.threat.Covens.cursed(level, villager)) {
					recover(level, villager);
				} else {
					villager.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 40, 0, false, false));
					swirl(level, villager);
				}
				return;
			}
			if (isGrey(villager)) {
				// The Grey Cough doesn't pass with time; it lifts once no Sickness story runs here (31.18).
				if (!io.github.jcondedata.aliveworkplace.story.SicknessStory.coughing(level, villager)) {
					recover(level, villager);
				} else {
					if (!eased(villager, now)) {
						villager.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 40, 0, false, false));
					}
					cough(level, villager);
				}
				return;
			}
			if (PLAGUE.equals(ModAttachments.ILL_KIND.get(villager))
					&& io.github.jcondedata.aliveworkplace.threat.Plague.underway(level, villager.blockPosition())) {
				// The plague's ill don't get well by themselves, and after two days they keep to their bed (32.18).
				villager.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 40, 0, false, false));
				sneeze(level, villager);
				io.github.jcondedata.aliveworkplace.threat.Plague.toBed(level, villager);
				return;
			}
			if (now - since >= recovery(villager) ||io.github.jcondedata.aliveworkplace.legend.Gifted.noIllness(villager)) {
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
		if (io.github.jcondedata.aliveworkplace.legend.Gifted.noIllness(villager)) {
			return 0f; // Hardy (29.7)
		}
		float chance = DAILY + (VillageNeeds.isHungry(villager, level.getGameTime()) ? HUNGRY : 0f) + (VillageNeeds.bed(level, villager) == null ? HOMELESS : 0f);
		chance *= Math.max(0f, 1f + io.github.jcondedata.aliveworkplace.hall.CivicEffects.of(villager).sickness(villager) / 100f);
		// The illness effects of the village's research trees (29.11): Herb Lore and the like.
		chance *= Math.max(0f, 1f + io.github.jcondedata.aliveworkplace.research.TreeEffects.illnessPercent(villager) / 100f);
		// Medicine: a third less likely a level.
		return chance * (1f - io.github.jcondedata.aliveworkplace.research.Research.level(villager, io.github.jcondedata.aliveworkplace.research.Research.Topic.MEDICINE) / 3f);
	}

	/** {@code villager} falls ill, unless they're Hardy (29.7), who never do. */
	public static void fallIll(ServerLevel level, Villager villager) {
		if (io.github.jcondedata.aliveworkplace.legend.Gifted.noIllness(villager) || isHexed(villager) || isGrey(villager) || isPlagueIll(villager)
				|| isImmune(villager, level.getGameTime())) {
			return; // (nor does an ordinary illness take the place of a hex, the Grey Cough or the plague; a Remedy keeps them well, 32.18)
		}
		ModAttachments.ILL_SINCE.set(villager, level.getGameTime());
		villager.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 40, 0, false, false));
		sneeze(level, villager);
	}

	/** Well again: the slowness goes, and a little happy puff. */
	public static void recover(ServerLevel level, Villager villager) {
		ModAttachments.ILL_SINCE.remove(villager);
		ModAttachments.PLAGUE_SEEN.remove(villager);
		ModAttachments.ILL_KIND.remove(villager);
		ModAttachments.ILL_EASED_UNTIL.remove(villager);
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
