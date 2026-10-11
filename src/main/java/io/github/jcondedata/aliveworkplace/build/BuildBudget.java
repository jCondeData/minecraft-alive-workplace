package io.github.jcondedata.aliveworkplace.build;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;

/**
 * How many blocks builders may change in one level in one tick (ROADMAP 25.3). A block a builder places or breaks is
 * a world block update (neighbours, light, block entities), the most expensive single thing a builder does; with many
 * sites in one village their work timers can line up and put a dozen of them in the same tick. Past the budget a
 * builder simply waits a tick and tries again, so nothing is lost: the budget is never saved (all progress stays in
 * {@link BuildSite}), and a fresh one starts every tick.
 */
public final class BuildBudget {
	/** Block changes by builders a level allows a tick. At the default delay a village of 25 sites averages about 4. */
	public static final int CHANGES_PER_TICK = 12;
	/** Per level (or any key, for tests): {game time, changes taken}. */
	private static final Map<Object, long[]> USED = new WeakHashMap<>();

	private BuildBudget() {
	}

	/** Takes one block change from {@code level}'s budget this tick; false when it's spent (wait a tick). */
	public static boolean take(ServerLevel level) {
		return take(level, level.getGameTime());
	}

	/** {@link #take(ServerLevel)} for any key at a given time. */
	public static boolean take(Object key, long gameTime) {
		long[] used = USED.computeIfAbsent(key, k -> new long[] {gameTime, 0L});
		if (used[0] != gameTime) {
			used[0] = gameTime;
			used[1] = 0L;
		}
		if (used[1] >= CHANGES_PER_TICK) {
			return false;
		}
		used[1]++;
		return true;
	}
}
