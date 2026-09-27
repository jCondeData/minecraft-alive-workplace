package io.github.jcondedata.aliveworkplace.trainer;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Pokémon Trainers (with Cobblemon installed): villagers at a Training Post who battle players. A
 * trainer's tier is their villager level, Novice to Master, and they level up from every battle anyone
 * fights with them, so a village's trainers get tougher as the server plays. Beating one pays
 * CobbleDollars (emeralds without that mod) once per in-game day per player. No badges, no gyms.
 */
public final class Trainers {
	public static final boolean COBBLEMON = FabricLoader.getInstance().isModLoaded("cobblemon");
	private static final boolean COBBLEDOLLARS = FabricLoader.getInstance().isModLoaded("cobbledollars");
	/** CobbleDollars for a win, by tier (Novice..Master). */
	private static final int[] PRIZE = {100, 250, 500, 1000, 2500};
	/** Without CobbleDollars: emeralds instead. */
	private static final int[] EMERALDS = {1, 2, 4, 8, 16};
	/** Trainer XP for every battle, and a bonus when the trainer wins. */
	public static final int XP_PER_BATTLE = 5;
	public static final int XP_FOR_WIN = 3;

	public static boolean isTrainer(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == ModVillagers.TRAINER;
	}

	/** 1 (Novice) to 5 (Master). */
	public static int tier(Villager villager) {
		return BuilderLevels.level(villager);
	}

	public static Component title(Villager villager) {
		return Component.translatable("message.aliveworkplace.trainer.title", BuilderLevels.levelName(tier(villager)), villager.getDisplayName());
	}

	/** Right-click on a trainer: challenge them. */
	public static void challenge(ServerPlayer player, Villager trainer) {
		if (!COBBLEMON) {
			player.displayClientMessage(Component.translatable("message.aliveworkplace.trainer.no_cobblemon").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		if (trainer.isSleeping()) {
			player.displayClientMessage(Component.translatable("message.aliveworkplace.trainer.asleep", trainer.getDisplayName()).withStyle(ChatFormatting.GRAY), true);
			return;
		}
		io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers.challenge(player, trainer);
	}

	/** A battle with a trainer ended (won by the player or not). Levels the trainer and pays the prize. */
	public static void battleOver(MinecraftServer server, UUID trainerId, UUID playerId, boolean playerWon) {
		Villager trainer = find(server, trainerId);
		ServerPlayer player = server.getPlayerList().getPlayer(playerId);
		if (trainer == null) {
			return;
		}
		int tierBefore = tier(trainer);
		trainer.setAttached(ModAttachments.TRAINER_BATTLES, trainer.getAttachedOrElse(ModAttachments.TRAINER_BATTLES, 0) + 1);
		BuilderLevels.addXp((ServerLevel) trainer.level(), trainer, XP_PER_BATTLE + (playerWon ? 0 : XP_FOR_WIN), null);
		if (player == null) {
			return;
		}
		if (!playerWon) {
			player.sendSystemMessage(Component.translatable("message.aliveworkplace.trainer.lost", trainer.getDisplayName()).withStyle(ChatFormatting.GRAY));
		} else {
			long day = trainer.level().getDayTime() / 24000L;
			Map<UUID, Long> paid = new HashMap<>(trainer.getAttachedOrElse(ModAttachments.TRAINER_REWARDS, Map.of()));
			Long last = paid.get(playerId);
			if (last != null && last == day) {
				player.sendSystemMessage(Component.translatable("message.aliveworkplace.trainer.won_again", trainer.getDisplayName()).withStyle(ChatFormatting.GREEN));
			} else {
				paid.put(playerId, day);
				trainer.setAttached(ModAttachments.TRAINER_REWARDS, Map.copyOf(paid));
				pay(server, player, tierBefore, trainer);
			}
		}
		if (tier(trainer) > tierBefore) {
			player.sendSystemMessage(Component.translatable("message.aliveworkplace.trainer.ranked_up", trainer.getDisplayName(),
				io.github.jcondedata.aliveworkplace.build.BuilderLevels.levelName(tier(trainer))).withStyle(ChatFormatting.GOLD));
		}
	}

	private static void pay(MinecraftServer server, ServerPlayer player, int tier, Villager trainer) {
		int i = Math.max(0, Math.min(4, tier - 1));
		if (COBBLEDOLLARS) {
			int prize = PRIZE[i];
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
				"cobbledollars give " + player.getGameProfile().getName() + " " + prize);
			player.sendSystemMessage(Component.translatable("message.aliveworkplace.trainer.won_dollars", trainer.getDisplayName(), prize).withStyle(ChatFormatting.GREEN));
		} else {
			ItemStack prize = new ItemStack(Items.EMERALD, EMERALDS[i]);
			player.sendSystemMessage(Component.translatable("message.aliveworkplace.trainer.won_emeralds", trainer.getDisplayName(), prize.getCount())
				.withStyle(ChatFormatting.GREEN));
			if (!player.getInventory().add(prize)) {
				player.drop(prize, false);
			}
		}
	}

	private static Villager find(MinecraftServer server, UUID id) {
		for (ServerLevel level : server.getAllLevels()) {
			if (level.getEntity(id) instanceof Villager v) {
				return v;
			}
		}
		return null;
	}

	private Trainers() {
	}
}
