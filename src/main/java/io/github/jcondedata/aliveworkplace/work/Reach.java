package io.github.jcondedata.aliveworkplace.work;

import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.Comparator;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;

/**
 * How far a worker's job may be from the block they work at (36.20, the owner's "village-wide"): a spot is in reach when
 * it lies in the village the worker's block stands in (its claimed area: within the hall's radius, {@link VillageHalls#contains}
 * across, or in a cell painted into one of the City Plan's zones), wherever in the village the block stands; or, outside
 * it, within the job's long reach of the block (a config key each: {@code maxSiteDistance} for builds, {@code quarryReach},
 * {@code fieldReach}, {@code treeFarmReach}, {@code orchardAreaReach}, {@code berryPlotReach}). A block in no village
 * (no hall near) has only the long reach. Up and down never count, against the village or the long reach: a quarry's pit
 * goes below it and a tower above it, so every distance is measured across the ground ({@link #across}).
 */
public final class Reach {
	private Reach() {
	}

	/**
	 * The hall whose village {@code workBlock} stands in ({@link #claims}), if any; the nearest across if two claim it.
	 * B550/B557: the City Plan's grid is a square, so a zone painted in its corner lies past the hall's radius, where
	 * {@link VillageHalls#nearest} (a search within the radius) doesn't find the hall; the halls are looked up in a square
	 * column as wide as the grid, at any height.
	 */
	public static Optional<BlockPos> village(ServerLevel level, BlockPos workBlock) {
		Optional<BlockPos> near = VillageHalls.nearest(level, workBlock);
		if (near.isPresent() && claims(level, near.get(), workBlock)) {
			return near;
		}
		int reach = Math.max(VillageHalls.RADIUS, CityPlan.half()) + 1;
		return level.getPoiManager().getInSquare(h -> h.is(ModVillagers.VILLAGE_HALL_POI), workBlock, reach, PoiManager.Occupancy.ANY)
			.map(PoiRecord::getPos)
			.filter(hall -> claims(level, hall, workBlock))
			.min(Comparator.comparingLong(hall -> VillageHalls.across(hall, workBlock)));
	}

	/** True if {@code pos} is in the claimed area of the village round {@code hall} (any height). */
	public static boolean claims(ServerLevel level, BlockPos hall, BlockPos pos) {
		long dx = pos.getX() - hall.getX();
		long dz = pos.getZ() - hall.getZ();
		if (dx * dx + dz * dz <= (long) VillageHalls.RADIUS * VillageHalls.RADIUS) {
			return true;
		}
		return level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity
			&& entity.plan().zoneAt(hall, new BlockPos(pos.getX(), hall.getY(), pos.getZ())).isPresent();
	}

	/** True if {@code spot} is in reach of the worker's block at {@code workBlock}, {@code outside} blocks being the long reach. */
	public static boolean inReach(ServerLevel level, BlockPos workBlock, BlockPos spot, int outside) {
		return within(workBlock, spot, outside) || inSameVillage(level, workBlock, spot);
	}

	/** True if {@code spot} lies in the village {@code workBlock} stands in. */
	public static boolean inSameVillage(ServerLevel level, BlockPos workBlock, BlockPos spot) {
		Optional<BlockPos> hall = village(level, workBlock);
		return hall.isPresent() && claims(level, hall.get(), spot);
	}

	/**
	 * True if {@code spot} is within {@code reach} blocks of {@code from} across the ground ({@link #across}). B665/B668:
	 * up and down never count, so a deep pit or a tall build is in reach wherever its middle is in height.
	 */
	public static boolean within(BlockPos from, BlockPos spot, int reach) {
		return across(from, spot) <= reach;
	}

	/** How far {@code spot} is from {@code from} across the ground (x and z only), the distance a refusal names. */
	public static double across(BlockPos from, BlockPos spot) {
		double dx = spot.getX() - from.getX();
		double dz = spot.getZ() - from.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	/**
	 * The refusal for a spot out of reach: {@code key} ("That spot is 140 blocks from ... — keep it within 128.") for a
	 * worker in no village, else {@code key + "_village"}, which names the village first ("That spot is outside Mapleridge
	 * and 140 blocks from ..."). {@code args} are the key's own, the distance first; the village name goes before them.
	 */
	public static Component refusal(ServerLevel level, BlockPos workBlock, String key, Object... args) {
		Optional<BlockPos> hall = village(level, workBlock);
		if (hall.isEmpty()) {
			return Component.translatable(key, args);
		}
		Object[] withVillage = new Object[args.length + 1];
		withVillage[0] = VillageHalls.name(level, hall.get());
		System.arraycopy(args, 0, withVillage, 1, args.length);
		return Component.translatable(key + "_village", withVillage);
	}
}
