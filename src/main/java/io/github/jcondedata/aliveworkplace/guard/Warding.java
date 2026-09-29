package io.github.jcondedata.aliveworkplace.guard;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.research.Research;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Warding (research): explosions — creepers, TNT, fireballs — don't break blocks in a village whose scholars have
 * researched it (the blast still hurts). {@code ExplosionMixin} asks before the blocks go.
 */
public final class Warding {
	/** The warded village's area round an explosion at {@code center}, if there is one. */
	public static Optional<AABB> wardedArea(ServerLevel level, Vec3 center) {
		Optional<BlockPos> hall = VillageHalls.nearest(level, BlockPos.containing(center));
		if (hall.isEmpty() || !(level.getBlockEntity(hall.get()) instanceof VillageHallBlockEntity entity)
			|| entity.research().level(Research.Topic.WARDING) < 1) {
			return Optional.empty();
		}
		return Optional.of(VillageHalls.area(hall.get()));
	}

	/** Takes out of {@code toBlow} the blocks in a warded village. */
	public static void spare(ServerLevel level, Vec3 center, List<BlockPos> toBlow) {
		if (toBlow.isEmpty()) {
			return;
		}
		wardedArea(level, center).ifPresent(area -> toBlow.removeIf(pos -> area.contains(Vec3.atCenterOf(pos))));
	}

	private Warding() {
	}
}
