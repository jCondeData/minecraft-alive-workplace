package io.github.jcondedata.aliveworkplace.threat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The drought (ROADMAP 32.17): {@code data/aliveworkplace/disasters/drought.json} on the disaster engine
 * ({@link Disasters}), whose effects (crops, stems and berry bushes at a quarter pace, farmland drying, cauldrons losing
 * a level a day, the villagers' "the wells are low" unless a Well or Fountain is finished) the engine applies. What is
 * the drought's own: the farmland a Well Keeper wets ({@link #wet}), 9 blocks to a bucket at moisture 7, keeps its
 * moisture that day and the crops on it grow at the full pace that day.
 *
 * <p>The watered farmland is kept in memory by dimension and day (a restart forgets it, and the keeper waters it again);
 * a day's list is dropped when the next day's first bucket is poured.
 */
public final class Drought {
	public static final ResourceLocation ID = AliveWorkplace.id("drought");
	/** How far a bucket reaches round the farmland it's poured on: 1, a 3 x 3 square of 9 blocks. */
	public static final int SPLASH = 1;

	/** Watered farmland, by dimension: position → the day it was watered. */
	private static final Map<String, Map<Long, Long>> WATERED = new ConcurrentHashMap<>();

	/** Forgets the watered farmland (a new server; tests). */
	public static void forget() {
		WATERED.clear();
	}

	/** The drought, if it's loaded and switched on. */
	public static Optional<Disaster> get() {
		return Disasters.on(ID);
	}

	/** Whether {@code pos} lies in a village under a drought (any disaster of the drought kind that dries farmland). */
	public static boolean in(ServerLevel level, BlockPos pos) {
		return Disasters.afflicted(level, pos, Disaster.Effect.Type.FARMLAND_DRIES)
			.filter(a -> a.disaster().kind() == Chronicle.Kind.DROUGHT).isPresent();
	}

	/** Whether the farmland at {@code farmland} was watered by a Well Keeper today. */
	public static boolean watered(ServerLevel level, BlockPos farmland) {
		if (WATERED.isEmpty()) {
			return false;
		}
		Map<Long, Long> dim = WATERED.get(level.dimension().location().toString());
		if (dim == null) {
			return false;
		}
		Long day = dim.get(farmland.asLong());
		return day != null && day == Chronicle.day(level);
	}

	/** Whether a Well Keeper's water keeps this farmland from drying on this random tick (watered today, still wet). */
	public static boolean keepsWet(ServerLevel level, BlockPos pos, BlockState state) {
		return state.getValue(FarmBlock.MOISTURE) > 0 && watered(level, pos);
	}

	/**
	 * A bucket poured on {@code center}: the farmland in the 3 x 3 round it is wet through (moisture 7) and counted as
	 * watered today. Returns how many farmland blocks it wet.
	 */
	public static int wet(ServerLevel level, BlockPos center) {
		long day = Chronicle.day(level);
		Map<Long, Long> dim = WATERED.computeIfAbsent(level.dimension().location().toString(), k -> new ConcurrentHashMap<>());
		dim.values().removeIf(d -> d != day);
		int wet = 0;
		for (BlockPos p : BlockPos.betweenClosed(center.offset(-SPLASH, 0, -SPLASH), center.offset(SPLASH, 0, SPLASH))) {
			BlockState state = level.getBlockState(p);
			if (!state.is(Blocks.FARMLAND)) {
				continue;
			}
			if (state.getValue(FarmBlock.MOISTURE) < FarmBlock.MAX_MOISTURE) {
				level.setBlock(p, state.setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE), 2);
			}
			dim.put(p.asLong(), day);
			wet++;
		}
		return wet;
	}

	private Drought() {
	}
}
