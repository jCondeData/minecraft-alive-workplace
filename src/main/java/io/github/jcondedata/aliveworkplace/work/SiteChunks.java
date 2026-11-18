package io.github.jcondedata.aliveworkplace.work;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * Whether a worker's site (a quarry, a field, a tree farm, a build) is in loaded chunks (B666, B667). A site can lie far
 * from the worker's block (36.20), in chunks nobody has loaded; reading one of its blocks there makes the server load
 * (or generate) the chunk on the spot, in the middle of a tick. Every job asks here before it looks at its site, and
 * waits while the answer is no: the site, its cursor and its plan stay as they are until the chunks are back.
 */
public final class SiteChunks {
	/** Blocks round a site that count with it: the jobs read the blocks beside their box and stand next to it. */
	public static final int MARGIN = 4;

	/** True when every chunk under {@code box} and {@link #MARGIN} blocks round it is loaded. */
	public static boolean loaded(ServerLevel level, BoundingBox box) {
		return loaded(level, box, MARGIN);
	}

	/** True when every chunk under {@code box} and {@code margin} blocks round it is loaded. */
	public static boolean loaded(ServerLevel level, BoundingBox box, int margin) {
		for (int cx = (box.minX() - margin) >> 4; cx <= (box.maxX() + margin) >> 4; cx++) {
			for (int cz = (box.minZ() - margin) >> 4; cz <= (box.maxZ() + margin) >> 4; cz++) {
				if (!level.hasChunk(cx, cz)) {
					return false;
				}
			}
		}
		return true;
	}

	/** True when {@code pos}'s chunk is loaded (whatever its height); false for no spot at all. */
	public static boolean loaded(ServerLevel level, @Nullable BlockPos pos) {
		return pos != null && level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
	}

	private SiteChunks() {
	}
}
