package io.github.jcondedata.aliveworkplace.build;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * The sound of a builder passing materials to a crewmate close by (23.1a). Only a sound: no item flies (owner,
 * 23.1b), and the whole server plays at most {@link #MAX} of them in any {@link #WINDOW} ticks, so a village of
 * busy crews can't flood the players' sound channels or the network. Sounds past the cap are skipped; the materials
 * still change hands.
 */
public final class TossSounds {
	/** The most toss sounds the whole server plays in one window. */
	public static final int MAX = 4;
	/** The window, in ticks (half a second). */
	public static final int WINDOW = 10;

	private static long windowStart = -WINDOW;
	private static int inWindow;
	/** Every toss sound played and skipped since the server started, for the tests. */
	private static long played;
	private static long skipped;

	private TossSounds() {
	}

	/** Plays the toss sound at {@code pos} unless the cap is reached; returns whether it played. */
	public static boolean play(ServerLevel level, BlockPos pos) {
		if (!allow(level.getGameTime())) {
			return false;
		}
		level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.3f, 1.4f);
		return true;
	}

	/** Counts one toss at {@code now} against the cap; false if it is to be skipped. */
	public static synchronized boolean allow(long now) {
		// Game time can go backwards (another world opened in the same run): start a new window then too.
		if (now < windowStart || now - windowStart >= WINDOW) {
			windowStart = now;
			inWindow = 0;
		}
		if (inWindow >= MAX) {
			skipped++;
			return false;
		}
		inWindow++;
		played++;
		return true;
	}

	public static synchronized long played() {
		return played;
	}

	public static synchronized long skipped() {
		return skipped;
	}
}
