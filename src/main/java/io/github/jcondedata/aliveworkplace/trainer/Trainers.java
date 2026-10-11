package io.github.jcondedata.aliveworkplace.trainer;

import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

/**
 * Pokémon Trainers (with Cobblemon installed): villagers at a Training Post who battle players. A
 * trainer's tier is their villager level, Novice to Master, and they level up from every battle anyone
 * fights with them, so a village's trainers get tougher as the server plays. Beating one pays
 * CobbleDollars (emeralds without that mod) once per in-game day per player. No badges, no gyms.
 */
public final class Trainers {
	public static final boolean COBBLEMON = Platform.get().isModLoaded("cobblemon");
	/** CobbleDollars for a win, by tier (Novice..Master). */
	private static final int[] PRIZE = {100, 250, 500, 1000, 2500};
	/** Without CobbleDollars: emeralds instead. */
	private static final int[] EMERALDS = {1, 2, 4, 8, 16};
	/** Trainer XP for every battle, and a bonus when the trainer wins. */
	public static final int XP_PER_BATTLE = 5;
	public static final int XP_FOR_WIN = 3;

	/**
	 * With Radical Cobblemon Trainers, the highest level a trainer's Pokémon have against a player, relative
	 * to that player's level cap, by tier: a Novice stays well under it, a Master goes a little over.
	 */
	private static final int[] CAP_OFFSET = {-6, -3, 0, 3, 5};

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

	/** "Novice Trainer" / "Trainer Leader", with the villager's name after it when they have one. */
	/** The highest level a tier's Pokémon have against a player with this RCT level cap. */
	public static int capCeiling(int tier, int levelCap) {
		return Math.max(1, levelCap + CAP_OFFSET[Math.max(1, Math.min(5, tier)) - 1]);
	}

	public static Component title(Villager villager) {
		boolean named = villager.hasCustomName();
		if (isLeader(villager)) {
			return named
				? Component.translatable("message.aliveworkplace.trainer.leader_title_named", villager.getCustomName())
				: Component.translatable("message.aliveworkplace.trainer.leader_title");
		}
		Component rank = BuilderLevels.levelName(tier(villager));
		return named
			? Component.translatable("message.aliveworkplace.trainer.title_named", rank, villager.getCustomName())
			: Component.translatable("message.aliveworkplace.trainer.title", rank);
	}

	/** The leader who takes challenges in this village (the most experienced one within {@link #VILLAGE} blocks). */
	static Villager seniorLeader(Villager leader) {
		Villager best = leader;
		for (Villager other : leader.level().getEntitiesOfClass(Villager.class, leader.getBoundingBox().inflate(VILLAGE),
				v -> isLeader(v) && !io.github.jcondedata.aliveworkplace.cup.CupDays.isDelegate(v))) {
			if (other.getVillagerXp() > best.getVillagerXp()
				|| other.getVillagerXp() == best.getVillagerXp() && other.getUUID().compareTo(best.getUUID()) < 0) {
				best = other;
			}
		}
		return best;
	}

	/** Right-click on a trainer: challenge them. */
	public static void challenge(ServerPlayer player, Villager trainer) {
		Component away = io.github.jcondedata.aliveworkplace.cup.CupDays.noChallenge((ServerLevel) trainer.level(), trainer);
		if (away != null) { // 28.19: a Leader away at the Cup, or a delegate there for it
			Chat.actionBar(player, away.copy().withStyle(ChatFormatting.YELLOW));
			return;
		}
		if (!COBBLEMON) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.trainer.no_cobblemon").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (trainer.isSleeping()) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.trainer.asleep", trainer.getDisplayName()).withStyle(ChatFormatting.GRAY));
			return;
		}
		if (isLeader(trainer)) {
			Villager senior = seniorLeader(trainer);
			if (senior != trainer) {
				Chat.actionBar(player, Component.translatable("message.aliveworkplace.trainer.not_the_leader", senior.getDisplayName())
					.withStyle(ChatFormatting.YELLOW));
				return;
			}
			long day = trainer.level().getDayTime() / 24000L;
			Long last = ModAttachments.LEADER_CHALLENGES.getOrElse(trainer, Map.<UUID, Long>of()).get(player.getUUID());
			io.github.jcondedata.aliveworkplace.hall.VillageQuests.onTrainerBeaten((ServerLevel) trainer.level(), trainer, player);
			if (last != null && last == day) {
				Chat.actionBar(player, Component.translatable("message.aliveworkplace.trainer.leader_tomorrow", trainer.getDisplayName())
					.withStyle(ChatFormatting.YELLOW));
				return;
			}
			Map<UUID, Long> seen = new HashMap<>(ModAttachments.LEADER_CHALLENGES.getOrElse(trainer, Map.of()));
			seen.put(player.getUUID(), day);
			ModAttachments.LEADER_CHALLENGES.set(trainer, Map.copyOf(seen));
		}
		TrainerBattles.EXTENSION.run(battles -> battles.challenge(player, trainer));
	}

	/** The experience a battle earns {@code trainer}: more for a win, a quarter more with the Trainers' Guild founded (30.20). */
	public static int battleXp(Villager trainer, boolean playerWon) {
		return io.github.jcondedata.aliveworkplace.hall.Guilds.trainerXp(trainer, XP_PER_BATTLE + (playerWon ? 0 : XP_FOR_WIN));
	}

	/** A battle with a trainer ended (won by the player or not). Levels the trainer and pays the prize. */
	public static void battleOver(MinecraftServer server, UUID trainerId, UUID playerId, boolean playerWon) {
		Villager trainer = find(server, trainerId);
		ServerPlayer player = server.getPlayerList().getPlayer(playerId);
		if (trainer == null) {
			return;
		}
		int tierBefore = tier(trainer);
		ModAttachments.TRAINER_BATTLES.set(trainer, ModAttachments.TRAINER_BATTLES.getOrElse(trainer, 0) + 1);
		BuilderLevels.addXp((ServerLevel) trainer.level(), trainer, battleXp(trainer, playerWon), null);
		if (player == null) {
			return;
		}
		if (!playerWon) {
			player.sendSystemMessage(Component.translatable("message.aliveworkplace.trainer.lost", trainer.getDisplayName()).withStyle(ChatFormatting.GRAY));
		} else {
			io.github.jcondedata.aliveworkplace.story.PersonalRequests.onBattleWon(trainer, player); // "Help me train" (31.9)
			long day = trainer.level().getDayTime() / 24000L;
			Map<UUID, Long> paid = new HashMap<>(ModAttachments.TRAINER_REWARDS.getOrElse(trainer, Map.of()));
			Long last = paid.get(playerId);
			if (last != null && last == day) {
				player.sendSystemMessage(Component.translatable("message.aliveworkplace.trainer.won_again", trainer.getDisplayName()).withStyle(ChatFormatting.GREEN));
			} else {
				paid.put(playerId, day);
				ModAttachments.TRAINER_REWARDS.set(trainer, Map.copyOf(paid));
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
		int dollars = PRIZE[i] * factor;
		int emeralds = EMERALDS[i] * factor;
		io.github.jcondedata.aliveworkplace.work.Money.pay(player, dollars, emeralds);
		player.sendSystemMessage(io.github.jcondedata.aliveworkplace.work.Money.cobbleDollars()
			? Component.translatable("message.aliveworkplace.trainer.won_dollars", trainer.getDisplayName(), dollars).withStyle(ChatFormatting.GREEN)
			: Component.translatable("message.aliveworkplace.trainer.won_emeralds", trainer.getDisplayName(), emeralds).withStyle(ChatFormatting.GREEN));
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
