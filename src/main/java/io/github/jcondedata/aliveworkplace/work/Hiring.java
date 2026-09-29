package io.github.jcondedata.aliveworkplace.work;

import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Players;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;

/**
 * Hiring a vanilla villager whose job we upgraded (an Armorer as a smelter, a Toolsmith...): unhired, they work for
 * their village; hired, they work with the player's own workers (see {@link Village#sameSide}). Players sneak-right-click
 * with the item of the trade in hand, so a plain right-click still opens the trades.
 */
public final class Hiring {
	/** Hires {@code villager} for {@code player} and says so with {@code message}. */
	public static InteractionResult hire(ServerPlayer player, Villager villager, Component message) {
		if (!Friends.mayCommand(player, villager)) {
			Employer employer = ModAttachments.BUILDER_EMPLOYER.get(villager);
			Chat.chat(player, Component.translatable("message.aliveworkplace.not_your_builder", villager.getDisplayName(),
				employer != null ? employer.name() : "?", player.getGameProfile().getName()).withStyle(ChatFormatting.RED));
			return InteractionResult.CONSUME;
		}
		Friends.hire(player, villager);
		Village.forget(villager);
		Players.level(player).playSound(null, villager, SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
		Chat.chat(player, message.copy().withStyle(ChatFormatting.GREEN));
		return InteractionResult.SUCCESS;
	}

	private Hiring() {
	}
}
