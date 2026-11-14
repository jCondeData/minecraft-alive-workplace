package io.github.jcondedata.aliveworkplace.compat;

import java.util.List;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * Waiting for a chunk a test has just loaded far from its area (B1036). Forcing a chunk, or setting a block in it, loads
 * its blocks on the server thread, but the chunk only starts running later: its neighbours are generated on the
 * background executor, and its entities are read off the IO thread. A block entity there doesn't tick until then
 * ({@code LevelChunk.isTicking} needs {@code ServerLevel.areEntitiesLoaded}), and an entity added before then isn't
 * found. That's real time, but the headless test server ticks flat out (a few hundred ticks a second), so a budget
 * counted in ticks covers only a second or two, and on a busy CI runner (the background executor shared with every
 * other test's chunks) it ran out: builds 3457 and 3486. So the wait here is measured on the clock: it gives up only
 * when both {@link #MIN_TICKS} ticks and {@link #SECONDS} seconds have passed. A test using it needs a timeout that
 * covers that many seconds of ticks ({@link #TIMEOUT_TICKS} more than it needs otherwise).
 */
final class ChunkWaits {
	/** At least this many ticks before giving up. */
	static final int MIN_TICKS = 200;
	/** And at least this many seconds. */
	static final long SECONDS = 30;
	/** What a test's timeout adds for one such wait: {@link #SECONDS} at the fastest the suite ticks (seen up to about 600 a second). */
	static final int TIMEOUT_TICKS = 20_000;

	private ChunkWaits() {
	}

	/** The chunk at {@code pos} is loaded and running: entity-ticking, with its entities loaded (its block entities tick). */
	static boolean running(ServerLevel level, BlockPos pos) {
		return level.isLoaded(pos) && level.isPositionEntityTicking(pos) && level.areEntitiesLoaded(ChunkPos.asLong(pos));
	}

	/** How far the chunk at {@code pos} has got, for a failure message. */
	static String state(ServerLevel level, BlockPos pos) {
		return "loaded " + level.isLoaded(pos) + ", entity-ticking " + level.isPositionEntityTicking(pos) + ", entities loaded "
			+ level.areEntitiesLoaded(ChunkPos.asLong(pos)) + ", forced " + level.getForcedChunks().contains(ChunkPos.asLong(pos));
	}

	/**
	 * Every one of {@code chunks} (x and z each, as the far-away tests keep the chunks they force) is loaded and running
	 * (B1125, B1150): a village a test has force-loaded is only then what a village with a player standing in it is, its
	 * block entities ticking and its entities moving. {@code y} is any height inside the world.
	 */
	static boolean running(ServerLevel level, List<long[]> chunks, int y) {
		for (long[] c : chunks) {
			if (!running(level, middle(c, y))) {
				return false;
			}
		}
		return true;
	}

	/** Which of {@code chunks} aren't running yet and how far each has got, for a failure message. */
	static String state(ServerLevel level, List<long[]> chunks, int y) {
		StringBuilder out = new StringBuilder();
		int waiting = 0;
		for (long[] c : chunks) {
			BlockPos pos = middle(c, y);
			if (!running(level, pos)) {
				if (waiting++ < 4) {
					out.append(out.length() == 0 ? "" : "; ").append("chunk ").append(c[0]).append(' ').append(c[1]).append(": ").append(state(level, pos));
				}
			}
		}
		return waiting + " of " + chunks.size() + " chunks not running" + (waiting == 0 ? "" : " (" + out + (waiting > 4 ? "; ..." : "") + ")");
	}

	private static BlockPos middle(long[] chunk, int y) {
		return new BlockPos(((int) chunk[0] << 4) + 8, y, ((int) chunk[1] << 4) + 8);
	}

	/** A clock started now: {@link Clock#overdue} once both the ticks and the seconds have run out. */
	static Clock start(ServerLevel level) {
		return new Clock(level, level.getGameTime(), System.nanoTime());
	}

	record Clock(ServerLevel level, long startTick, long startNanos) {
		long ticks() {
			return level.getGameTime() - startTick;
		}

		long millis() {
			return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
		}

		boolean overdue() {
			return overdue(MIN_TICKS);
		}

		/** Overdue once both {@code minTicks} ticks and {@link #SECONDS} seconds have passed. */
		boolean overdue(int minTicks) {
			return ticks() >= minTicks && millis() >= TimeUnit.SECONDS.toMillis(SECONDS);
		}

		@Override
		public String toString() {
			return ticks() + " ticks, " + millis() + " ms";
		}
	}
}
