package io.github.jcondedata.aliveworkplace.table;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * B1020: drawing another mod's block in one of our previews must never crash the game. A tint lookup that throws
 * falls back to no tint, and a block whose drawing throws is left out of the picture; each block is logged once, not
 * every frame.
 */
public final class SafeRender {
	/** "No tint": white, as Minecraft's {@code BlockColors} answers for an untinted block. */
	public static final int NO_TINT = -1;

	private static final Set<Block> WARNED = ConcurrentHashMap.newKeySet();

	private SafeRender() {
	}

	/** The tint the lookup gives, or {@link #NO_TINT} when another mod's color provider throws. */
	public static int tint(BlockState state, IntSupplier lookup) {
		try {
			return lookup.getAsInt();
		} catch (RuntimeException | LinkageError e) {
			warnOnce(state, "the color of", e);
			return NO_TINT;
		}
	}

	/** Runs the drawing of one block; false (and the block skipped) when it throws. */
	public static boolean draw(BlockState state, Runnable drawing) {
		try {
			drawing.run();
			return true;
		} catch (RuntimeException | LinkageError e) {
			warnOnce(state, "drawing", e);
			return false;
		}
	}

	/** Whether this block has already been logged as failing (for tests). */
	public static boolean warned(Block block) {
		return WARNED.contains(block);
	}

	private static void warnOnce(BlockState state, String what, Throwable e) {
		if (WARNED.add(state.getBlock())) {
			AliveWorkplace.LOG.warn("Preview: {} {} failed in another mod's renderer; drawn without it from now on ({})",
				what, BuiltInRegistries.BLOCK.getKey(state.getBlock()), e.toString());
		}
	}
}
