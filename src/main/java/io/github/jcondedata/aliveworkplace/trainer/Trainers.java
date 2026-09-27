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

	/** Leaders pay this many times a trainer's prize. */
	private static final int LEADER_PRIZE_FACTOR = 3;
	/** Only one leader per village: another leader this close with more experience takes the challenges. */
	private static final int VILLAGE = 64;

	public static boolean isTrainer(Villager villager) {
		return !villager.isBaby() && (villager.getVillagerData().getProfession() == ModVillagers.TRAINER || isLeader(villager));
	}

	public static boolean isLeader(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == ModVillagers.TRAINER_LEADER;
	}

	/** 1 (Novice) to 5 (Master). Leaders start at Expert. */
	public static int tier(Villager villager) {
		int level = BuilderLevels.level(villager);
		return isLeader(villager) ? Math.max(4, level) : level;
	}

	public static Component title(Villager villager) {
		return isLeader(villager)
			? Component.translatable("message.aliveworkplace.trainer.leader_title", villager.getDisplayName())
			: Component.translatable("message.aliveworkplace.trainer.title", BuilderLevels.levelName(tier(villager)), villager.getDisplayName());
	}

	/** The leader who takes challenges in this village (the most experienced one within {@link #VILLAGE} blocks). */
	static Villager seniorLeader(Villager leader) {
		Villager best = leader;
		for (Villager other : leader.level().getEntitiesOfClass(Villager.class, leader.getBoundingBox().inflate(VILLAGE), Trainers::isLeader)) {
			if (other.getVillagerXp() > best.getVillagerXp()
				|| other.getVillagerXp() == best.getVillagerXp() && other.getUUID().compareTo(best.getUUID()) < 0) {
				best = other;
			}
		}
		return best;
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
		if (isLeader(trainer)) {
			Villager senior = seniorLeader(trainer);
			if (senior != trainer) {
				player.displayClientMessage(Component.translatable("message.aliveworkplace.trainer.not_the_leader", senior.getDisplayName())
					.withStyle(ChatFormatting.YELLOW), true);
				return;
			}
			long day = trainer.level().getDayTime() / 24000L;
			Long last = trainer.getAttachedOrElse(ModAttachments.LEADER_CHALLENGES, Map.<UUID, Long>of()).get(player.getUUID());
			if (last != null && last == day) {
				player.displayClientMessage(Component.translatable("message.aliveworkplace.trainer.leader_tomorrow", trainer.getDisplayName())
					.withStyle(ChatFormatting.YELLOW), true);
				return;
			}
			Map<UUID, Long> seen = new HashMap<>(trainer.getAttachedOrElse(ModAttachments.LEADER_CHALLENGES, Map.of()));
			seen.put(player.getUUID(), day);
			trainer.setAttached(ModAttachments.LEADER_CHALLENGES, Map.copyOf(seen));
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
		int factor = isLeader(trainer) ? LEADER_PRIZE_FACTOR : 1;
		if (COBBLEDOLLARS) {
			int prize = PRIZE[i] * factor;
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
				"cobbledollars give " + player.getGameProfile().getName() + " " + prize);
			player.sendSystemMessage(Component.translatable("message.aliveworkplace.trainer.won_dollars", trainer.getDisplayName(), prize).withStyle(ChatFormatting.GREEN));
		} else {
			ItemStack prize = new ItemStack(Items.EMERALD, EMERALDS[i] * factor);
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
