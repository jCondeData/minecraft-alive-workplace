package io.github.jcondedata.aliveworkplace.test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * Makes a far chunk a test has just forced (someone is back there) entity-ticking before the test goes on (B1242).
 *
 * <p>Why it can't be waited for by counting ticks: a chunk is entity-ticking only once the 5×5 chunks around it are
 * FULL. Far test spots are fresh terrain, so that means generating them, on the world-generation thread, in real time,
 * while the GameTest server runs its ticks back to back with no 50 ms pause ({@code GameTestServer.waitUntilNextTick}
 * only runs tasks). Counted in ticks (400 to 1000), the wait ran out in 5 of 200 local copies (3 of 10 in a night's
 * repeats): each time the far chunk had its forced ticket (level 31, ENTITY_TICKING, in entity-ticking range), but its
 * entity-ticking future was pending on neighbours whose FULL generation was still in flight. Slow, not stuck, and
 * nothing the mod does: waited for by the clock it took 19 to 472 ms in 230 copies.
 *
 * <p>So this waits by the clock, the way vanilla's own {@code ServerChunkCache.getChunk} waits for a chunk: on the
 * server thread, running the chunk system's main-thread tasks (ticket updates, chunk promotions) until every position
 * is entity-ticking, for at most {@link #LIMIT_SECONDS}. No game tick passes meanwhile, so nothing the test checks
 * about the mod changes; the test then asserts the positions are entity-ticking as before.
 */
final class FarChunks {
	/** A real server generates a 5×5 area in well under this, even on a busy CI machine. */
	static final long LIMIT_SECONDS = 60;

	private FarChunks() {
	}

	/** Waits (by the clock, at most {@link #LIMIT_SECONDS}) until every one of {@code positions} is entity-ticking; fails the test if one isn't. */
	static void awaitEntityTicking(GameTestHelper helper, BlockPos... positions) {
		ServerLevel level = helper.getLevel();
		long start = System.nanoTime();
		long deadline = start + TimeUnit.SECONDS.toNanos(LIMIT_SECONDS);
		while (!notTicking(level, positions).isEmpty() && System.nanoTime() < deadline) {
			if (!level.getChunkSource().pollTask()) {
				LockSupport.parkNanos(1_000_000L);
			}
		}
		List<BlockPos> missing = notTicking(level, positions);
		if (!missing.isEmpty()) {
			StringBuilder states = new StringBuilder();
			for (BlockPos pos : missing) {
				ChunkPos chunk = new ChunkPos(pos);
				states.append(' ').append(pos.toShortString())
					.append(": in entity-ticking range ").append(level.getChunkSource().chunkMap.getDistanceManager().inEntityTickingRange(chunk.toLong()))
					.append(", loaded ").append(level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null);
			}
			helper.fail("setup: the forced chunk isn't entity-ticking after " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) + " ms:" + states);
		}
	}

	private static List<BlockPos> notTicking(ServerLevel level, BlockPos... positions) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos pos : positions) {
			if (!level.isPositionEntityTicking(pos)) {
				out.add(pos);
			}
		}
		return out;
	}
}
