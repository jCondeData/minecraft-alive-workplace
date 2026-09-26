package io.github.jcondedata.aliveworkplace.blueprint;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Server-side placement preview: while a player holds a placed blueprint, draw its outline with
 * particles only that player can see. The front edge is drawn in gold so you can tell which way
 * the building faces. (A proper see-through ghost of the whole build is on the roadmap.)
 */
public final class BlueprintOutline {
	private static final DustParticleOptions EDGE = new DustParticleOptions(new Vector3f(0.3f, 0.6f, 1.0f), 1.0f);
	private static final DustParticleOptions FRONT = new DustParticleOptions(new Vector3f(1.0f, 0.8f, 0.2f), 1.2f);
	private static final int INTERVAL = 10;

	public static void init() {
		ServerTickEvents.END_WORLD_TICK.register(level -> {
			if (level.getGameTime() % INTERVAL != 0) {
				return;
			}
			for (ServerPlayer player : level.players()) {
				for (InteractionHand hand : InteractionHand.values()) {
					ItemStack stack = player.getItemInHand(hand);
					BlueprintItem.data(stack).ifPresent(data -> data.placement().ifPresent(p -> {
						if (p.dimension().equals(level.dimension().location()) && data.size().isPresent()) {
							show(level, player, p, data.size().get());
						}
					}));
				}
			}
		});
	}

	public static BoundingBox bounds(BlueprintData.Placement placement, Vec3i size) {
		BlockPos far = StructureTemplate.transform(new BlockPos(size.getX() - 1, size.getY() - 1, size.getZ() - 1), placement.mirror(), placement.rotation(), BlockPos.ZERO);
		return BoundingBox.fromCorners(placement.origin(), placement.origin().offset(far));
	}

	public static void show(ServerLevel level, Player player, BlueprintData.Placement placement, Vec3i size) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		BoundingBox box = bounds(placement, size);
		double x0 = box.minX(), y0 = box.minY(), z0 = box.minZ();
		double x1 = box.maxX() + 1, y1 = box.maxY() + 1, z1 = box.maxZ() + 1;

		// 12 edges of the box
		line(level, serverPlayer, EDGE, x0, y0, z0, x1, y0, z0);
		line(level, serverPlayer, EDGE, x0, y0, z1, x1, y0, z1);
		line(level, serverPlayer, EDGE, x0, y1, z0, x1, y1, z0);
		line(level, serverPlayer, EDGE, x0, y1, z1, x1, y1, z1);
		line(level, serverPlayer, EDGE, x0, y0, z0, x0, y0, z1);
		line(level, serverPlayer, EDGE, x1, y0, z0, x1, y0, z1);
		line(level, serverPlayer, EDGE, x0, y1, z0, x0, y1, z1);
		line(level, serverPlayer, EDGE, x1, y1, z0, x1, y1, z1);
		line(level, serverPlayer, EDGE, x0, y0, z0, x0, y1, z0);
		line(level, serverPlayer, EDGE, x1, y0, z0, x1, y1, z0);
		line(level, serverPlayer, EDGE, x0, y0, z1, x0, y1, z1);
		line(level, serverPlayer, EDGE, x1, y0, z1, x1, y1, z1);

		// Gold front edge (bottom edge on the side the building faces)
		Direction front = placement.rotation().rotate(Direction.NORTH);
		double fy = y0 + 0.05;
		switch (front) {
			case NORTH -> line(level, serverPlayer, FRONT, x0, fy, z0, x1, fy, z0);
			case SOUTH -> line(level, serverPlayer, FRONT, x0, fy, z1, x1, fy, z1);
			case WEST -> line(level, serverPlayer, FRONT, x0, fy, z0, x0, fy, z1);
			default -> line(level, serverPlayer, FRONT, x1, fy, z0, x1, fy, z1);
		}
	}

	private static void line(ServerLevel level, ServerPlayer player, DustParticleOptions particle,
							 double ax, double ay, double az, double bx, double by, double bz) {
		Vec3 a = new Vec3(ax, ay, az);
		Vec3 b = new Vec3(bx, by, bz);
		double length = a.distanceTo(b);
		int steps = Math.max(1, (int) Math.min(length * 2, 64));
		for (int i = 0; i <= steps; i++) {
			Vec3 p = a.lerp(b, i / (double) steps);
			level.sendParticles(player, particle, true, p.x, p.y, p.z, 1, 0, 0, 0, 0);
		}
	}

	private BlueprintOutline() {
	}
}
