package io.github.jcondedata.aliveworkplace.build;

import io.github.jcondedata.aliveworkplace.mc.Rules;
import io.github.jcondedata.aliveworkplace.mixin.VillagerAccessor;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import org.jetbrains.annotations.Nullable;

/**
 * Builders get better by building. They earn villager XP for placed blocks and finished builds and
 * level up like a villager you trade with (Novice → Master), which unlocks their next trades. Each
 * level also makes them faster.
 */
public final class BuilderLevels {
	/** One XP for every this many blocks placed. */
	public static final int BLOCKS_PER_XP = 5;
	/** Bonus XP for finishing a build. */
	public static final int XP_PER_BUILD = 10;
	/** Time per block as a percentage of {@code workplaceBuildDelay}, by villager level (index 1–5). */
	private static final int[] DELAY_PERCENT = {100, 100, 85, 70, 55, 40};

	public static int level(Villager villager) {
		return Math.max(VillagerData.MIN_VILLAGER_LEVEL, Math.min(VillagerData.MAX_VILLAGER_LEVEL, villager.getVillagerData().getLevel()));
	}

	/** Ticks between blocks for this builder. */
	public static int delay(ServerLevel level, Villager villager) {
		return delay(Rules.number(level, ModGameRules.BUILD_DELAY), villager);
	}

	/**
	 * {@code baseDelay} for this villager: shorter with each level, with Pokémon partners helping and in a well-kept village, and
	 * near a Legend with a {@code pace} power (never more than twice as fast from those: {@link io.github.jcondedata.aliveworkplace.legend.LegendPowers#PACE_CAP}).
	 */
	public static int delay(int baseDelay, Villager villager) {
		return Math.round(delay(baseDelay, level(villager)) * io.github.jcondedata.aliveworkplace.work.Partners.factor(villager)
			* io.github.jcondedata.aliveworkplace.hall.VillageNeeds.factor(villager) * io.github.jcondedata.aliveworkplace.people.Traits.pace(villager)
			* io.github.jcondedata.aliveworkplace.people.Sickness.pace(villager) * io.github.jcondedata.aliveworkplace.people.Moods.pace(villager)
			/ io.github.jcondedata.aliveworkplace.legend.LegendPowers.pace(villager));
	}

	public static int delay(int baseDelay, int villagerLevel) {
		int level = Math.max(1, Math.min(5, villagerLevel));
		return Math.round(Math.max(0, baseDelay) * DELAY_PERCENT[level] / 100f);
	}

	/** How much faster than a novice, in percent (0 for novices). */
	public static int speedBonus(int villagerLevel) {
		return Math.round(100f / DELAY_PERCENT[Math.max(1, Math.min(5, villagerLevel))] * 100) - 100;
	}

	/** Called after every block the builder places. */
	static void onPlaced(ServerLevel level, Villager villager, BuildSite site) {
		if (site.placedBy(villager.getUUID(), true) % BLOCKS_PER_XP == 0) {
			addXp(level, villager, 1, site.owner());
		}
	}

	static void onFinished(ServerLevel level, Villager villager, BuildSite site) {
		addXp(level, villager, XP_PER_BUILD, site.owner());
	}

	/** Adds XP and levels the villager up (possibly several times) when it crosses a threshold. */
	public static void addXp(ServerLevel level, Villager villager, int xp, @Nullable UUID owner) {
		villager.setVillagerXp(villager.getVillagerXp() + io.github.jcondedata.aliveworkplace.people.Traits.xp(villager, xp, level.random));
		boolean leveled = false;
		while (VillagerData.canLevelUp(villager.getVillagerData().getLevel())
			&& villager.getVillagerXp() >= VillagerData.getMaxXpPerLevel(villager.getVillagerData().getLevel())) {
			((VillagerAccessor) villager).aliveworkplace$increaseMerchantCareer();
			leveled = true;
		}
		if (!leveled) {
			return;
		}
		int now = villager.getVillagerData().getLevel();
		if (now == VillagerData.MAX_VILLAGER_LEVEL) {
			io.github.jcondedata.aliveworkplace.hall.Chronicle.record(level, villager.blockPosition(), io.github.jcondedata.aliveworkplace.hall.Chronicle.Kind.MASTER, Component.translatable("chronicle.aliveworkplace.master",
				villager.getDisplayName(), Component.translatable("entity.minecraft.villager." + villager.getVillagerData().getProfession().name())));
		}
		villager.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getY() + 1.2, villager.getZ(), 20, 0.5, 0.8, 0.5, 0.0);
		level.playSound(null, villager, SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.6f, 1.2f);
		ServerPlayer player = owner != null ? level.getServer().getPlayerList().getPlayer(owner) : null;
		if (player != null) {
			Builders.tell(player, Component.translatable("message.aliveworkplace.level_up", villager.getDisplayName(),
				levelName(now), speedBonus(now)), ChatFormatting.GREEN);
		}
	}

	public static Component levelName(int level) {
		return Component.translatable("merchant.level." + Math.max(1, Math.min(5, level)));
	}

	/** "Apprentice · 35/70 XP · 18% faster" for the status message (plus any Pokémon helping). */
	public static Component describe(Villager villager) {
		int lvl = level(villager);
		net.minecraft.network.chat.MutableComponent out = !VillagerData.canLevelUp(lvl)
			? Component.translatable("message.aliveworkplace.status.level_max", levelName(lvl), speedBonus(lvl))
			: Component.translatable("message.aliveworkplace.status.level", levelName(lvl), villager.getVillagerXp(),
				VillagerData.getMaxXpPerLevel(lvl), speedBonus(lvl));
		java.util.List<Component> partners = io.github.jcondedata.aliveworkplace.work.Partners.helpers(villager);
		if (!partners.isEmpty()) {
			int faster = Math.round(100f / io.github.jcondedata.aliveworkplace.work.Partners.factor(villager)) - 100;
			out.append(Component.translatable("message.aliveworkplace.partners.status",
				io.github.jcondedata.aliveworkplace.work.Partners.names(partners), faster).withStyle(ChatFormatting.GREEN));
		}
		return out;
	}

	private BuilderLevels() {
	}
}
