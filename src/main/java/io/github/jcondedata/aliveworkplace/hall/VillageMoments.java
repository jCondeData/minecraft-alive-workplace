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
	// 36.2: the questbook's village quests that used to ask for a checkmark.
	/** A baby born in a village with a hall (the hall's births and the couples' own). */
	public static final String FIRST_BABY = "first_baby";
	/** The hall's research reaching 3 levels done in all. */
	public static final String RESEARCH_3 = "research_3";
	/** The hall's research reaching 7 levels done in all. */
	public static final String RESEARCH_7 = "research_7";
	/** An edict proclaimed (a realm edict for its capital's village). */
	public static final String EDICT = "edict";
	/** A household rising to Burgher. */
	public static final String BURGHERS = "burghers";
	/** A household rising to Noble. */
	public static final String NOBLES = "nobles";
	/** A realm founded, for its capital's village. */
	public static final String REALM = "realm";
	/** The treasury's takings collected (at least one emerald). */
	public static final String TREASURY = "treasury";
	public static final net.minecraft.resources.ResourceLocation BURGHER_CLASS = AliveWorkplace.id("burgher");
	public static final net.minecraft.resources.ResourceLocation NOBLE_CLASS = AliveWorkplace.id("noble");

	/** The day each hall's catch-up last ran (not saved: after a load the first round looks again, which is harmless). */
	private static final java.util.Map<VillageHallBlockEntity, Long> CAUGHT_UP = new java.util.WeakHashMap<>();

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

	/** The hall's research went from {@code before} to {@code after} levels done: 3 and 7 crossed are moments. */
	public static void researched(ServerLevel level, BlockPos hall, int before, int after) {
		if (before < 3 && after >= 3) {
			award(level, hall, RESEARCH_3, null);
		}
		if (before < 7 && after >= 7) {
			award(level, hall, RESEARCH_7, null);
		}
	}

	/** A household of the village round {@code hall} rose to the class {@code now}: Burgher and Noble are moments. */
	public static void rose(ServerLevel level, BlockPos hall, net.minecraft.resources.ResourceLocation now) {
		if (now.equals(BURGHER_CLASS)) {
			award(level, hall, BURGHERS, null);
		} else if (now.equals(NOBLE_CLASS)) {
			award(level, hall, NOBLES, null);
		}
	}

	/** A baby born by {@code parent}: the moment for the village whose hall is nearest, if there is one. */
	public static void born(ServerLevel level, BlockPos parent) {
		VillageHalls.nearest(level, parent).ifPresent(hall -> award(level, hall, FIRST_BABY, null));
	}

	/**
	 * The day's first hall round: a village that passed a moment before it was one (an update, 36.2) gets it now, so no
	 * quest is left that can't complete. Returns the moments it found passed (each awarded; a player who has one already
	 * isn't counted twice), or an empty list when today's catch-up already ran.
	 */
	public static java.util.List<String> catchUp(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity,
			java.util.List<net.minecraft.world.entity.npc.Villager> villagers) {
		long today = Chronicle.day(level);
		synchronized (CAUGHT_UP) {
			Long last = CAUGHT_UP.get(entity);
			if (last != null && last == today) {
				return java.util.List.of();
			}
			CAUGHT_UP.put(entity, today);
		}
		java.util.List<String> passed = new java.util.ArrayList<>();
		if (entity.births() > 0) {
			passed.add(FIRST_BABY);
		}
		int levels = entity.research().totalLevels();
		if (levels >= 3) {
			passed.add(RESEARCH_3);
		}
		if (levels >= 7) {
			passed.add(RESEARCH_7);
		}
		if (!entity.edicts().isEmpty()) {
			passed.add(EDICT);
		}
		io.github.jcondedata.aliveworkplace.people.SocialClasses.SocialClass burgher = io.github.jcondedata.aliveworkplace.people.SocialClasses.get(BURGHER_CLASS);
		io.github.jcondedata.aliveworkplace.people.SocialClasses.SocialClass noble = io.github.jcondedata.aliveworkplace.people.SocialClasses.get(NOBLE_CLASS);
		int top = -1;
		for (net.minecraft.world.entity.npc.Villager villager : villagers) {
			io.github.jcondedata.aliveworkplace.people.SocialClasses.SocialClass c = io.github.jcondedata.aliveworkplace.people.SocialClasses.of(villager);
			if (c != null && villager.isAlive()) {
				top = Math.max(top, c.tier());
			}
		}
		if (burgher != null && top >= burgher.tier()) {
			passed.add(BURGHERS);
		}
		if (noble != null && top >= noble.tier()) {
			passed.add(NOBLES);
		}
		io.github.jcondedata.aliveworkplace.realm.RealmData.Realm realm = io.github.jcondedata.aliveworkplace.realm.Realms.realmOf(level, hall);
		if (realm != null && realm.capital().equals(net.minecraft.core.GlobalPos.of(level.dimension(), hall))) {
			passed.add(REALM);
			if (!passed.contains(EDICT) && !io.github.jcondedata.aliveworkplace.realm.RealmData.get(level.getServer()).laws(realm.id()).edicts().isEmpty()) {
				passed.add(EDICT); // a realm edict counts for the capital's village
			}
		}
		for (String moment : passed) {
			award(level, hall, moment, null);
		}
		return passed;
	}

	/** Forgets which halls caught up today (tests run several "dawns" on one day). */
	public static void forgetCatchUp() {
		synchronized (CAUGHT_UP) {
			CAUGHT_UP.clear();
		}
	}

	private VillageMoments() {
	}
}
