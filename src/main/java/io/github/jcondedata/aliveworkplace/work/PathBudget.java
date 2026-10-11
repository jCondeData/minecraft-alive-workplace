package io.github.jcondedata.aliveworkplace.work;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;

/**
 * A level's budget of villager path searches a tick (25.3). On the benchmark load (150 workers on the pack) the
 * villagers' path searches, made by vanilla's walking behaviour for the walk targets our jobs and vanilla's set, were
 * the biggest single cost of the server thread (19% of its samples) and came in bursts: every worker starting a job
 * at once asked for one in the same tick. Past {@link #PER_TICK} searches in a tick a villager's walk waits for the
 * next tick (the walk target is kept, nothing is lost), and one that has waited {@link #MOST_WAIT} ticks gets its
 * search whatever the budget, so every walk still starts within half a second. Server thread only, like the brains
 * that ask.
 */
public final class PathBudget {
	/** Path searches a level's villagers may start in one tick. */
	public static final int PER_TICK = 12;
	/** Ticks a villager's walk can be held back before it gets its search over the budget. */
	public static final int MOST_WAIT = 10;

	private static final class Use {
		long tick = Long.MIN_VALUE;
		int used;
		long deferred;
		long fullUntil = Long.MIN_VALUE;
	}

	private static final Map<ServerLevel, Use> USE = new WeakHashMap<>();

	private PathBudget() {
	}

	private static Use use(ServerLevel level) {
		Use use = USE.computeIfAbsent(level, l -> new Use());
		long now = level.getGameTime();
		if (use.tick != now) {
			use.tick = now;
			use.used = now < use.fullUntil ? PER_TICK : 0;
		}
		return use;
	}

	/**
	 * True if a villager may search for a path now (and counts it). {@code waitingSince} is the game time this walk was
	 * first held back, or -1 if it wasn't: past {@link #MOST_WAIT} ticks of waiting it goes ahead regardless.
	 */
	public static boolean take(ServerLevel level, long waitingSince) {
		Use use = use(level);
		if (use.used < PER_TICK || waitingSince >= 0 && level.getGameTime() - waitingSince >= MOST_WAIT) {
			use.used++;
			return true;
		}
		use.deferred++;
		return false;
	}

	/** Searches the level's villagers have started this tick. */
	public static int used(ServerLevel level) {
		return use(level).used;
	}

	/** Walks held back for a later tick since the server started. */
	public static long deferred(ServerLevel level) {
		return use(level).deferred;
	}

	/** For tests: the budget is spent (by others) every tick until game time {@code until}, as when a crowd asks first. */
	public static void spentUntil(ServerLevel level, long until) {
		Use use = use(level);
		use.fullUntil = until;
		use.used = Math.max(use.used, level.getGameTime() < until ? PER_TICK : 0);
	}
}
