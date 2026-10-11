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
 *
 * <p>A second budget (ROADMAP 25.4) caps the blocks builders and their neighbours read to recount what <em>other</em>
 * sites still need ({@link Builders#remainingNeedOfOther}): a builder fetching from a village store, or a crafter
 * keeping a bench's materials back, could recount every other build of the village, block by block, in one call. Past
 * it, the last count is reused (or, for a site never counted, its whole plan is kept back) until a later tick.
 */
public final class BuildBudget {
	/** Block changes by builders a level allows a tick. At the default delay a village of 25 sites averages about 4. */
	public static final int CHANGES_PER_TICK = 12;
	/** Blocks a level's recounts of other sites' needs may read a tick (25.4); a recount once started always finishes. */
	public static final int RECOUNT_READS_PER_TICK = 4096;
	/** Per level (or any key, for tests): {game time, changes taken}. */
	private static final Map<Object, long[]> USED = new WeakHashMap<>();
	/** Per level (or any key, for tests): {game time, blocks read by recounts}. */
	private static final Map<Object, long[]> READS = new WeakHashMap<>();

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

	/**
	 * Whether a recount may start on {@code key} at {@code gameTime}: true while the tick's reads are under
	 * {@link #RECOUNT_READS_PER_TICK}. The caller then reports what it read with {@link #read}. One recount may overrun
	 * the budget (a site's count is never cut in half), so a tick reads at most the budget plus one site.
	 */
	public static boolean mayRecount(Object key, long gameTime) {
		return readsUsed(key, gameTime) < RECOUNT_READS_PER_TICK;
	}

	/** Adds {@code reads} block reads to {@code key}'s budget for {@code gameTime}. */
	public static void read(Object key, long gameTime, int reads) {
		long[] used = READS.computeIfAbsent(key, k -> new long[] {gameTime, 0L});
		if (used[0] != gameTime) {
			used[0] = gameTime;
			used[1] = 0L;
		}
		used[1] += reads;
	}

	/** Blocks read by recounts on {@code key} at {@code gameTime} so far. */
	public static long readsUsed(Object key, long gameTime) {
		long[] used = READS.get(key);
		return used == null || used[0] != gameTime ? 0L : used[1];
	}
}
