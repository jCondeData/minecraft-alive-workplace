package io.github.jcondedata.aliveworkplace.tutor;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

/**
 * Move Tutors (with Cobblemon installed): villagers at a Tutor's Desk who teach your Pokémon moves they
 * can learn but won't pick up by levelling (tutor, TM and egg moves), for emeralds. A tutor's level
 * decides how strong a move they can teach; they level up from every lesson.
 */
public final class Tutors {
	public static final boolean COBBLEMON = FabricLoader.getInstance().isModLoaded("cobblemon");
	/** Emeralds for a lesson, by grade (1..5). */
	private static final int[] PRICE = {3, 6, 10, 16, 24};
	/** How far a player can walk from the tutor before the lesson screen closes. */
	public static final double REACH = 8;

	public static boolean isTutor(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == ModVillagers.TUTOR;
	}

	/** 1 (Novice) to 5 (Master): the hardest lesson this tutor can give. */
	public static int tier(Villager villager) {
		return BuilderLevels.level(villager);
	}

	/**
	 * How hard a move is to teach, 1..5: by base power (≤50, ≤70, ≤85, ≤100, more), status and
	 * variable-power moves in the middle (3), egg moves one step harder.
	 */
	public static int grade(double power, boolean egg) {
		int grade = power <= 0 ? 3 : power <= 50 ? 1 : power <= 70 ? 2 : power <= 85 ? 3 : power <= 100 ? 4 : 5;
		return Math.min(5, grade + (egg ? 1 : 0));
	}

	public static int price(int grade) {
		return PRICE[Math.max(1, Math.min(5, grade)) - 1];
	}

	/** "Journeyman Move Tutor", or "Journeyman Move Tutor Ada" for a named one. */
	public static Component title(Villager tutor) {
		Component rank = BuilderLevels.levelName(tier(tutor));
		return tutor.hasCustomName()
			? Component.translatable("message.aliveworkplace.tutor.title_named", rank, tutor.getCustomName())
			: Component.translatable("message.aliveworkplace.tutor.title", rank);
	}

	/** Right-click on a tutor: the lesson screen. */
	public static void open(ServerPlayer player, Villager tutor) {
		if (!COBBLEMON) {
			player.displayClientMessage(Component.translatable("message.aliveworkplace.tutor.no_cobblemon").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		if (tutor.isSleeping()) {
			player.displayClientMessage(Component.translatable("message.aliveworkplace.tutor.asleep", tutor.getDisplayName()).withStyle(ChatFormatting.GRAY), true);
			return;
		}
		io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTutors.open(player, tutor);
	}

	/** A lesson was given: count it, and the tutor learns from teaching. */
	public static void taught(ServerPlayer player, Villager tutor, int grade) {
		int tierBefore = tier(tutor);
		tutor.setAttached(ModAttachments.TUTOR_LESSONS, tutor.getAttachedOrElse(ModAttachments.TUTOR_LESSONS, 0) + 1);
		BuilderLevels.addXp((ServerLevel) tutor.level(), tutor, 2 + grade, null);
		if (tier(tutor) > tierBefore) {
			player.sendSystemMessage(Component.translatable("message.aliveworkplace.tutor.ranked_up", tutor.getDisplayName(),
				BuilderLevels.levelName(tier(tutor))).withStyle(ChatFormatting.GOLD));
		}
	}

	private Tutors() {
	}
}
