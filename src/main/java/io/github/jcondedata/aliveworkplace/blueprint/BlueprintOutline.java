package io.github.jcondedata.aliveworkplace.blueprint;

import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.platform.Platform;
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
	private static final DustParticleOptions QUARRY = new DustParticleOptions(new Vector3f(0.9f, 0.3f, 0.2f), 1.0f);
	private static final DustParticleOptions FIELD = new DustParticleOptions(new Vector3f(0.4f, 0.85f, 0.25f), 1.0f);
	private static final DustParticleOptions SCAN = new DustParticleOptions(new Vector3f(0.75f, 0.35f, 1.0f), 1.0f);
	private static final DustParticleOptions PATROL = new DustParticleOptions(new Vector3f(0.95f, 0.2f, 0.2f), 1.0f);
	private static final int INTERVAL = 10;

	/** A plain box outline (quarry markers). */
	public static void box(ServerLevel level, ServerPlayer player, BoundingBox box, DustParticleOptions particle) {
		double x0 = box.minX(), y0 = box.minY(), z0 = box.minZ();
		double x1 = box.maxX() + 1, y1 = box.maxY() + 1, z1 = box.maxZ() + 1;
		line(level, player, particle, x0, y0, z0, x1, y0, z0);
		line(level, player, particle, x0, y0, z1, x1, y0, z1);
		line(level, player, particle, x0, y1, z0, x1, y1, z0);
		line(level, player, particle, x0, y1, z1, x1, y1, z1);
		line(level, player, particle, x0, y0, z0, x0, y0, z1);
		line(level, player, particle, x1, y0, z0, x1, y0, z1);
		line(level, player, particle, x0, y1, z0, x0, y1, z1);
		line(level, player, particle, x1, y1, z0, x1, y1, z1);
		line(level, player, particle, x0, y0, z0, x0, y1, z0);
		line(level, player, particle, x1, y0, z0, x1, y1, z0);
		line(level, player, particle, x0, y0, z1, x0, y1, z1);
		line(level, player, particle, x1, y0, z1, x1, y1, z1);
	}

	/** An outline shown to a player for a while without a blueprint in hand ("Show me" on the Steward's desk, 27.8). */
	private record Glow(BlueprintData.Placement placement, Vec3i size, long until) {
	}

	private static final java.util.Map<java.util.UUID, Glow> GLOWS = new java.util.concurrent.ConcurrentHashMap<>();

	/** Shows {@code placement}'s outline to {@code player} for {@code ticks} ticks, in place of any outline shown so before. */
	public static void glow(ServerPlayer player, BlueprintData.Placement placement, Vec3i size, int ticks) {
		GLOWS.put(player.getUUID(), new Glow(placement, size, player.serverLevel().getGameTime() + ticks));
	}

	/** Whether a timed outline is showing to {@code player} (tests). */
	public static boolean glowing(ServerPlayer player) {
		Glow glow = GLOWS.get(player.getUUID());
		return glow != null && player.serverLevel().getGameTime() < glow.until();
	}

	public static void init() {
		Platform.get().onLevelTick(level -> {
			if (level.getGameTime() % INTERVAL != 0) {
				return;
			}
			for (ServerPlayer player : level.players()) {
				Glow glow = GLOWS.get(player.getUUID());
				if (glow != null) {
					if (level.getGameTime() >= glow.until()) {
						GLOWS.remove(player.getUUID());
					} else if (glow.placement().dimension().equals(Ids.of(level.dimension()))) {
						show(level, player, glow.placement(), glow.size());
					}
				}
				for (InteractionHand hand : InteractionHand.values()) {
					ItemStack stack = player.getItemInHand(hand);
					var quarry = stack.get(io.github.jcondedata.aliveworkplace.registry.ModComponents.QUARRY);
					if (quarry != null && quarry.dimension().map(Ids.of(level.dimension())::equals).orElse(false)) {
						quarry.area().ifPresentOrElse(box -> box(level, player, box, QUARRY),
							() -> quarry.first().ifPresent(p -> box(level, player, new BoundingBox(p), QUARRY)));
					}
					var field = stack.get(io.github.jcondedata.aliveworkplace.registry.ModComponents.FIELD);
					if (field != null && field.dimension().map(Ids.of(level.dimension())::equals).orElse(false)) {
						field.area().ifPresentOrElse(box -> box(level, player, box, FIELD),
							() -> field.first().ifPresent(p -> box(level, player, new BoundingBox(p), FIELD)));
					}
					var patrol = stack.get(io.github.jcondedata.aliveworkplace.registry.ModComponents.PATROL);
					if (patrol != null && patrol.dimension().map(Ids.of(level.dimension())::equals).orElse(false)) {
						BlockPos last = null;
						for (BlockPos p : patrol.points()) {
							box(level, player, new BoundingBox(p), PATROL);
							if (last != null) {
								line(level, player, PATROL, last.getX() + 0.5, last.getY() + 0.1, last.getZ() + 0.5, p.getX() + 0.5, p.getY() + 0.1, p.getZ() + 0.5);
							}
							last = p;
						}
					}
					var scan = stack.get(io.github.jcondedata.aliveworkplace.registry.ModComponents.SCAN);
					if (scan != null && scan.dimension().map(Ids.of(level.dimension())::equals).orElse(false)) {
						scan.area().ifPresentOrElse(box -> box(level, player, box, SCAN),
							() -> scan.first().ifPresent(p -> box(level, player, new BoundingBox(p), SCAN)));
					}
					BlueprintItem.data(stack).ifPresent(data -> data.placement().ifPresent(p -> {
						if (p.dimension().equals(Ids.of(level.dimension())) && data.size().isPresent()) {
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
