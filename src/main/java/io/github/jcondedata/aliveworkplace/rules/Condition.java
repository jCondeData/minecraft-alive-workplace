package io.github.jcondedata.aliveworkplace.rules;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * Something a village must have, read from a data file ({@link Conditions#parse}): a Legend's conditions (M29), and later
 * Wonders (M35). Each kind is one small class; {@link #progress} tells how far the village has got, for the hall's hints.
 */
public interface Condition {
	/** The condition's type, as written in the file. */
	String type();

	/** How far the village round the hall at {@code hall} has got. */
	Progress progress(ServerLevel level, BlockPos hall);

	default boolean met(ServerLevel level, BlockPos hall) {
		return progress(level, hall).met();
	}

	/** {@code have} of {@code need}, and the line the player reads ("kinds of meal in the store: 5 of 8"). */
	record Progress(int have, int need, Component line) {
		public boolean met() {
			return have >= need;
		}

		/** A progress line {@code rule.aliveworkplace.<key>} with the numbers and any extra arguments. */
		public static Progress of(String key, int have, int need, Object... extra) {
			Object[] args = new Object[extra.length + 2];
			args[0] = have;
			args[1] = need;
			System.arraycopy(extra, 0, args, 2, extra.length);
			return new Progress(have, need, Component.translatable("rule.aliveworkplace." + key, args));
		}
	}
}
