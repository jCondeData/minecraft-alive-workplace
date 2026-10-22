package io.github.jcondedata.aliveworkplace.devclient;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Where a scene puts a Village Hall in a placed plains village (B293): a grass or path block at ground level whose
 * {@link #FRONT} blocks to the south are open ground too, so the player, set down {@link #FRONT} blocks in front of
 * the hall, stands beside it. The showcase's world has a new seed every run, so the village's layout changes; looking
 * at the hall's own column alone once put the player on a house roof out of the hall screen's reach, and the screen
 * closed as soon as it opened.
 */
final class VillageGround {
	/** How far in front of the hall (south) the player stands. */
	static final int FRONT = 3;
	/** The flat world's ground: its surface sits at y = -60, so open ground has its height at -59 or below. */
	private static final int GROUND = -59;

	/** The hall's spot near the origin; null when no spot within 16 blocks will do. */
	static BlockPos hallSpot(ServerLevel level) {
		for (int r = 2; r <= 16; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (open(level, dx, dz) && clearInFront(level, dx, dz)) {
						return new BlockPos(dx, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, dx, dz), dz);
					}
				}
			}
		}
		return null;
	}

	/** Whether the column at {@code x}, {@code z} is open grass or path at ground level. */
	private static boolean open(ServerLevel level, int x, int z) {
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos below = new BlockPos(x, top - 1, z);
		return top <= GROUND && (level.getBlockState(below).is(Blocks.GRASS_BLOCK) || level.getBlockState(below).is(Blocks.DIRT_PATH));
	}

	/** Whether the columns between the hall and where the player stands are all at ground level. */
	private static boolean clearInFront(ServerLevel level, int x, int z) {
		for (int i = 1; i <= FRONT; i++) {
			if (level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z + i) > GROUND) {
				return false;
			}
		}
		return true;
	}

	private VillageGround() {
	}
}
