package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;

/**
 * A village's rank — Hamlet, Village, Town, City — from how many live there, how many buildings its builders have
 * finished and how much its scholars have found out. Each rank up pays: quests pay a quarter more a rank, a village can
 * send caravans to one more village a rank, markets bring more traders, and the village can grow ten villagers bigger
 * a rank. A rank up is celebrated (fireworks over the hall, everyone nearby told) and goes in the chronicle.
 */
public final class VillageRanks {
	public enum Rank {
		HAMLET(0, 0, 0), VILLAGE(10, 5, 0), TOWN(20, 12, 3), CITY(35, 25, 7);

		public final int villagers;
		public final int buildings;
		public final int research;

		Rank(int villagers, int buildings, int research) {
			this.villagers = villagers;
			this.buildings = buildings;
			this.research = research;
		}

		public Component title() {
			return Component.translatable("rank.aliveworkplace." + name().toLowerCase(Locale.ROOT));
		}

		@Nullable
		public Rank next() {
			return ordinal() + 1 < values().length ? values()[ordinal() + 1] : null;
		}
	}

	/** What the rank goes by: villagers, finished buildings (decorations too), research levels. */
	public record Score(int villagers, int buildings, int research) {
		public boolean reaches(Rank rank) {
			return villagers >= rank.villagers && buildings >= rank.buildings && research >= rank.research;
		}
	}

	public static Score score(ServerLevel level, BlockPos hall, int villagers) {
		int buildings = BuildSiteManager.get(level).finishedNear(level, hall, VillageHalls.RADIUS).size();
		int research = level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity
			? entity.research().levels().values().stream().mapToInt(Integer::intValue).sum() : 0;
		return new Score(villagers, buildings, research);
	}

	public static Rank rank(Score score) {
		Rank best = Rank.HAMLET;
		for (Rank r : Rank.values()) {
			if (score.reaches(r)) {
				best = r;
			}
		}
		return best;
	}

	/** The rank the hall at {@code hall} last counted (a Hamlet before its first round). */
	public static Rank of(ServerLevel level, BlockPos hall) {
		return level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity ? entity.rank() : Rank.HAMLET;
	}

	/** Quest rewards: a quarter more a rank. */
	public static float questRewardFactor(Rank rank) {
		return 1f + 0.25f * rank.ordinal();
	}

	/** Villages a village can send caravans to: three, and one more a rank. */
	public static int caravanRoutes(Rank rank) {
		return 3 + rank.ordinal();
	}

	/** Traders on market day: two, three from a Town. */
	public static int marketTraders(Rank rank) {
		return rank.ordinal() >= Rank.TOWN.ordinal() ? 3 : 2;
	}

	/** How big the village may grow: the configured cap, ten more a rank. */
	public static int growthCap(Rank rank) {
		return VillageGrowth.CAP <= 0 ? 0 : VillageGrowth.CAP + 10 * rank.ordinal();
	}

	/** The hall's round: counts the rank again; a rank up is celebrated. Returns the rank now. */
	public static Rank round(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, int villagers) {
		Rank now = rank(score(level, hall, villagers));
		Rank before = entity.rank();
		if (now != before) {
			entity.setRank(now);
			if (now.ordinal() > before.ordinal()) {
				celebrate(level, hall, now);
			}
		}
		return now;
	}

	static void celebrate(ServerLevel level, BlockPos hall, Rank rank) {
		Component name = VillageHalls.name(level, hall);
		for (ServerPlayer player : level.getPlayers(p -> p.blockPosition().distSqr(hall) <= (double) VillageHalls.RADIUS * VillageHalls.RADIUS)) {
			player.displayClientMessage(Component.translatable("message.aliveworkplace.rank.up", name, rank.title()).withStyle(ChatFormatting.GOLD), false);
		}
		Chronicle.record(level, hall, Chronicle.Kind.RANK, Component.translatable("chronicle.aliveworkplace.rank", rank.title()), true);
		level.playSound(null, hall, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 1f, 1f);
		for (int i = 0; i < 3; i++) {
			net.minecraft.world.item.ItemStack rocket = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FIREWORK_ROCKET);
			rocket.set(net.minecraft.core.component.DataComponents.FIREWORKS, new net.minecraft.world.item.component.Fireworks(1, java.util.List.of(
				new net.minecraft.world.item.component.FireworkExplosion(net.minecraft.world.item.component.FireworkExplosion.Shape.LARGE_BALL,
					it.unimi.dsi.fastutil.ints.IntList.of(0xF5C542, 0xFFFFFF), it.unimi.dsi.fastutil.ints.IntList.of(0x3FA7F5), false, true))));
			level.addFreshEntity(new net.minecraft.world.entity.projectile.FireworkRocketEntity(level, hall.getX() + 0.5 + (i - 1) * 2,
				hall.getY() + 1.5, hall.getZ() + 0.5, rocket));
		}
	}

	private VillageRanks() {
	}
}
