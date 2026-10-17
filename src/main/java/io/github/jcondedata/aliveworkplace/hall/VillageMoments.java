package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * A hidden advancement per key moment in a village's story, {@code aliveworkplace:village/<moment>}: the questbook's
 * story quests tick off on them, as the build quests do on {@link io.github.jcondedata.aliveworkplace.build.BuiltAdvancements}.
 * The files are written by {@code tools/questbook/generate.py} (its MOMENTS), each with a single "impossible" criterion
 * only this class grants, under a root with no display: no tab, no toast, no chat line.
 */
public final class VillageMoments {
	public static final String PREFIX = "village/";
	/** The criterion every moment has. */
	public static final String CRITERION = "done";

	public static final String CARAVAN = "caravan";
	public static final String FESTIVAL = "festival";
	public static final String RAID = "raid";
	public static final String HALL_QUEST = "hall_quest";
	public static final String STEWARD = "steward";
	public static final String COLONY = "colony";
	public static final String WONDER = "wonder";
	/** A raiders' camp broken by a war party (36.3): only to the players who broke it, through {@link #grant}. */
	public static final String CAMP_BROKEN = "camp_broken";

	/** The moment of reaching {@code rank} ({@code rank_village}, {@code rank_town}, {@code rank_city}); none for a Hamlet. */
	public static String rank(VillageRanks.Rank rank) {
		return "rank_" + rank.name().toLowerCase(Locale.ROOT);
	}

	/**
	 * The village round {@code hall} lived through {@code moment}: its advancement goes to the hall's owner (when online),
	 * to {@code also} (the player who did it, if any) and to every player within the village's reach. Returns how many
	 * players got it now.
	 */
	public static int award(ServerLevel level, BlockPos hall, String moment, @Nullable ServerPlayer also) {
		AdvancementHolder advancement = level.getServer().getAdvancements().get(AliveWorkplace.id(PREFIX + moment));
		if (advancement == null) {
			return 0;
		}
		Set<ServerPlayer> players = new LinkedHashSet<>();
		if (also != null) {
			players.add(also);
		}
		if (level.isLoaded(hall) && level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity && entity.owner() != null) {
			ServerPlayer owner = level.getServer().getPlayerList().getPlayer(entity.owner());
			if (owner != null) {
				players.add(owner);
			}
		}
		double r2 = (double) VillageHalls.RADIUS * VillageHalls.RADIUS;
		players.addAll(level.getPlayers(p -> p.blockPosition().distSqr(hall) <= r2));
		int awarded = 0;
		for (ServerPlayer player : players) {
			if (player.getAdvancements().award(advancement, CRITERION)) {
				awarded++;
			}
		}
		return awarded;
	}

	/** {@code moment}'s advancement to exactly {@code players}, no one else. Returns how many got it now. */
	public static int grant(ServerLevel level, String moment, Iterable<ServerPlayer> players) {
		AdvancementHolder advancement = level.getServer().getAdvancements().get(AliveWorkplace.id(PREFIX + moment));
		if (advancement == null) {
			return 0;
		}
		int awarded = 0;
		for (ServerPlayer player : players) {
			if (player.getAdvancements().award(advancement, CRITERION)) {
				awarded++;
			}
		}
		return awarded;
	}

	private VillageMoments() {
	}
}
