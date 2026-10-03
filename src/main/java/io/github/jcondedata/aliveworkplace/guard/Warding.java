package io.github.jcondedata.aliveworkplace.guard;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.research.Research;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Warding (research): explosions — creepers, TNT, fireballs — don't break blocks in a village whose scholars have
 * researched it (the blast still hurts). {@code ExplosionMixin} asks before the blocks go.
 */
public final class Warding {
	/**
	 * How far past a village's edge an explosion's centre can be and still reach blocks inside it. A charged creeper
	 * (power 6) breaks blocks up to about 11 blocks out; this leaves room for stronger modded blasts.
	 */
	static final int BLAST_REACH = 16;

	/**
	 * The areas of the warded villages an explosion at {@code center} could reach. A village is the square box
	 * {@link VillageHalls#area} round its hall, so its corners lie farther from the hall than {@link VillageHalls#RADIUS};
	 * the halls are looked up in a square, not a circle.
	 */
	public static List<AABB> wardedAreas(ServerLevel level, Vec3 center) {
		BlockPos at = BlockPos.containing(center);
		AABB reach = new AABB(at).inflate(BLAST_REACH);
		List<AABB> areas = new ArrayList<>();
		level.getPoiManager().getInSquare(h -> h.is(ModVillagers.VILLAGE_HALL_POI), at, VillageHalls.RADIUS + BLAST_REACH,
				PoiManager.Occupancy.ANY)
			.map(PoiRecord::getPos)
			.forEach(hall -> {
				AABB area = VillageHalls.area(hall);
				if (area.intersects(reach) && level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity
					&& entity.research().level(Research.Topic.WARDING) >= 1) {
					areas.add(area);
				}
			});
		return areas;
	}

	/** Takes out of {@code toBlow} the blocks inside any warded village. */
	public static void spare(ServerLevel level, Vec3 center, List<BlockPos> toBlow) {
		if (toBlow.isEmpty()) {
			return;
		}
		List<AABB> areas = wardedAreas(level, center);
		if (!areas.isEmpty()) {
			toBlow.removeIf(pos -> {
				Vec3 middle = Vec3.atCenterOf(pos);
				return areas.stream().anyMatch(area -> area.contains(middle));
			});
		}
	}

	private Warding() {
	}
}
