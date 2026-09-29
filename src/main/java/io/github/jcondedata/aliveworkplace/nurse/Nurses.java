package io.github.jcondedata.aliveworkplace.nurse;

import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Players;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.npc.Villager;

/**
 * Nurses patch players up: right-click one with an empty hand to get your health back and harmful
 * effects cleared, and (with Cobblemon) your whole party healed. Once a minute per player, less as the
 * nurse levels up.
 */
public final class Nurses {
	/** Ticks between treatments for one player, at Novice level. */
	public static final int COOLDOWN = 1200;
	private static final boolean COBBLEMON = Platform.get().isModLoaded("cobblemon");
	private static final Map<UUID, Long> LAST_TREATED = new HashMap<>();

	public static boolean isNurse(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == ModVillagers.NURSE;
	}

	public static void treat(ServerPlayer player, Villager nurse) {
		ServerLevel level = Players.level(player);
		if (nurse.isSleeping()) {
			tell(player, Component.translatable("message.aliveworkplace.nurse.asleep", nurse.getDisplayName()), ChatFormatting.GRAY);
			return;
		}
		if (COBBLEMON && PokemonHealing.EXTENSION.call(h -> h.inBattle(player), false)) {
			tell(player, Component.translatable("message.aliveworkplace.nurse.in_battle"), ChatFormatting.YELLOW);
			return;
		}
		long now = level.getGameTime();
		int cooldown = BuilderLevels.delay(COOLDOWN, nurse);
		Long last = LAST_TREATED.get(player.getUUID());
		if (last != null && now - last < cooldown) {
			tell(player, Component.translatable("message.aliveworkplace.nurse.wait", nurse.getDisplayName(), (cooldown - (now - last)) / 20 + 1),
				ChatFormatting.YELLOW);
			return;
		}
		LAST_TREATED.put(player.getUUID(), now);
		boolean hurt = player.getHealth() < player.getMaxHealth();
		player.setHealth(player.getMaxHealth());
		for (MobEffectInstance effect : player.getActiveEffects().stream().toList()) {
			if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
				player.removeEffect(effect.getEffect());
				hurt = true;
			}
		}
		int pokemon = COBBLEMON ? PokemonHealing.EXTENSION.call(h -> h.healParty(player), 0) : 0;
		MutableComponent message = Component.translatable(pokemon > 0 ? "message.aliveworkplace.nurse.healed_party" : "message.aliveworkplace.nurse.healed",
			nurse.getDisplayName(), pokemon);
		tell(player, message, ChatFormatting.LIGHT_PURPLE);
		nurse.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
		level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.2, player.getZ(), 6, 0.4, 0.4, 0.4, 0);
		level.playSound(null, player, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1f, 1.2f);
		if (hurt || pokemon > 0) {
			BuilderLevels.addXp(level, nurse, 1, player.getUUID());
		}
	}

	/** Forget cooldowns (tests). */
	public static void resetCooldowns() {
		LAST_TREATED.clear();
	}

	private static void tell(ServerPlayer player, Component message, ChatFormatting color) {
		Chat.actionBar(player, message.copy().withStyle(color));
	}

	private Nurses() {
	}
}
