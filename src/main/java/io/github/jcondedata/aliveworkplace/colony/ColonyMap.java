package io.github.jcondedata.aliveworkplace.colony;

import java.util.Collection;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * The sums of the Colony Charter's map (ROADMAP 33.8), shared by the server, the client's screen and the tests: the
 * map shows the {@link #SPAN} blocks round the mother village's hall on {@link #PIXELS} pixels a side, north up, the
 * hall in the middle; a colony may go {@link #MIN} to {@link #MAX} blocks from the hall and not within {@link #CLEAR}
 * blocks of another hall (all measured flat, east-west and north-south together).
 */
public final class ColonyMap {
	/** Blocks across the map. */
	public static final int SPAN = 2048;
	public static final int HALF = SPAN / 2;
	/** Map pixels along a side, as a vanilla map has. */
	public static final int PIXELS = 128;
	/** Blocks a map pixel: vanilla's scale 4. */
	public static final int STEP = SPAN / PIXELS;
	/** The ring: a colony goes at least this far from its mother village's hall... */
	public static final int MIN = 256;
	/** ...and at most this far. */
	public static final int MAX = 1024;
	/** No colony within this many blocks of another hall. */
	public static final int CLEAR = 128;

	/** Whether a colony may go on a spot, and why not. */
	public enum Verdict {
		OK, TOO_NEAR, TOO_FAR, NEAR_HALL
	}

	/**
	 * The world coordinate (x or z) under a pointer at {@code mouse} on a map drawn from {@code origin}, {@code size}
	 * screen pixels across, round a hall at {@code hall} on that axis. Left of the map and right of it give coordinates
	 * off the map ({@link #onMap} says no).
	 */
	public static int toWorld(int hall, double mouse, double origin, double size) {
		return hall - HALF + (int) Math.floor((mouse - origin) * SPAN / size);
	}

	/** Where the world coordinate {@code world} is drawn on that map: the middle of its block, in screen pixels. */
	public static double toScreen(int hall, int world, double origin, double size) {
		return origin + (world - hall + HALF + 0.5) * size / SPAN;
	}

	/** Whether the block column at {@code x}, {@code z} is on the map round {@code hall}. */
	public static boolean onMap(BlockPos hall, int x, int z) {
		return x >= hall.getX() - HALF && x < hall.getX() + HALF && z >= hall.getZ() - HALF && z < hall.getZ() + HALF;
	}

	/** Blocks from {@code hall} to the column at {@code x}, {@code z}, measured flat. */
	public static double distance(BlockPos hall, int x, int z) {
		double dx = x - hall.getX();
		double dz = z - hall.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** The hall of {@code others} nearest the column within {@link #CLEAR} blocks of it, or null. */
	@Nullable
	public static BlockPos hallTooNear(int x, int z, Collection<BlockPos> others) {
		BlockPos nearest = null;
		double best = CLEAR;
		for (BlockPos other : others) {
			double d = distance(other, x, z);
			if (d < best) {
				best = d;
				nearest = other;
			}
		}
		return nearest;
	}

	/** Whether a colony of the village at {@code hall} may go at {@code x}, {@code z}, with the other villages' halls at {@code others}. */
	public static Verdict verdict(BlockPos hall, int x, int z, Collection<BlockPos> others) {
		double d = distance(hall, x, z);
		if (d < MIN) {
			return Verdict.TOO_NEAR;
		}
		if (d > MAX) {
			return Verdict.TOO_FAR;
		}
		return hallTooNear(x, z, others) != null ? Verdict.NEAR_HALL : Verdict.OK;
	}

	private ColonyMap() {
	}
}
