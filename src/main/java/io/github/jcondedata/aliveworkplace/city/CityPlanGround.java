package io.github.jcondedata.aliveworkplace.city;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageLedgerItem;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Vector3f;

/**
 * The plan on the ground (ROADMAP 27.4): while a player holds a City Plan bound to a hall, the zones' edges, the roads
 * and the wall line show round them as coloured dust on the ground, within {@link #REACH} blocks, seen only by that
 * player (sent to them alone, as the Scan Tool's box is). Nothing is placed in the world.
 */
public final class CityPlanGround {
	/** How far round the player the plan shows. */
	public static final int REACH = 24;
	/** The most dust one player is sent each time, so a busy plan can't flood their game. */
	public static final int MAX_DOTS = 900;
	/** A road's colour on the ground: the colour of a dirt path. */
	public static final int ROAD_COLOR = 0xC9A26B;
	/** The wall line's colour on the ground: stone grey. */
	public static final int WALL_COLOR = 0x8A8A8A;
	private static final int INTERVAL = 10;

	/** One dot of dust: where, and its colour (0xRRGGBB). */
	public record Dot(double x, double y, double z, int color) {
	}

	public static void init() {
		Platform.get().onLevelTick(level -> {
			if (level.getGameTime() % INTERVAL != 0) {
				return;
			}
			for (ServerPlayer player : level.players()) {
				held(level, player).ifPresent(hall -> show(level, player, hall));
			}
		});
	}

	/** The hall the City Plan in {@code player}'s hand is bound to, if it's in this level and loaded. */
	static java.util.Optional<VillageHallBlockEntity> held(ServerLevel level, ServerPlayer player) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack stack = player.getItemInHand(hand);
			if (!stack.is(ModItems.CITY_PLAN)) {
				continue;
			}
			VillageLedgerItem.Ledger bound = stack.get(ModComponents.CITY_PLAN_HALL);
			if (bound == null || !bound.hall().dimension().equals(level.dimension())) {
				continue;
			}
			BlockPos hall = bound.hall().pos();
			int far = CityPlan.half() + REACH;
			if (Math.abs(player.getBlockX() - hall.getX()) > far || Math.abs(player.getBlockZ() - hall.getZ()) > far || !level.isLoaded(hall)) {
				continue;
			}
			if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) {
				return java.util.Optional.of(entity);
			}
		}
		return java.util.Optional.empty();
	}

	private static void show(ServerLevel level, ServerPlayer player, VillageHallBlockEntity entity) {
		List<Dot> dots = dots(level, entity.getBlockPos(), entity.plan(), player.blockPosition());
		java.util.Map<Integer, DustParticleOptions> dust = new java.util.HashMap<>();
		for (Dot d : dots) {
			DustParticleOptions particle = dust.computeIfAbsent(d.color(), c -> new DustParticleOptions(
				new Vector3f(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f), 1.0f));
			level.sendParticles(player, particle, true, d.x(), d.y(), d.z(), 1, 0, 0, 0, 0);
		}
	}

	/**
	 * The dust the plan of the hall at {@code hall} shows round {@code centre}: a dot a block along every zone edge (in
	 * the zone's colour), both sides of every road (one line for a lane) and the wall line, on the ground, within
	 * {@link #REACH} blocks; at most {@link #MAX_DOTS}.
	 */
	public static List<Dot> dots(ServerLevel level, BlockPos hall, CityPlan plan, BlockPos centre) {
		List<Dot> out = new ArrayList<>();
		zoneEdges(level, hall, plan, centre, out);
		for (CityPlan.Road road : plan.roads()) {
			double side = road.width() <= CityPlan.Road.LANE ? 0 : road.width() / 2.0;
			polyline(level, hall, road.points(), false, side, ROAD_COLOR, centre, out);
		}
		plan.wall().ifPresent(w -> polyline(level, hall, w.points(), w.closed(), 0, WALL_COLOR, centre, out));
		return out.size() > MAX_DOTS ? out.subList(0, MAX_DOTS) : out;
	}

	private static void zoneEdges(ServerLevel level, BlockPos hall, CityPlan plan, BlockPos centre, List<Dot> out) {
		int size = CityPlan.cellSize();
		int half = CityPlan.half();
		int[] of = new int[CityPlan.GRID * CityPlan.GRID];
		java.util.Arrays.fill(of, -1);
		for (int i = plan.zones().size() - 1; i >= 0; i--) {
			java.util.BitSet cells = plan.zones().get(i).cells();
			for (int c = cells.nextSetBit(0); c >= 0 && c < of.length; c = cells.nextSetBit(c + 1)) {
				of[c] = i;
			}
		}
		for (int c = 0; c < of.length; c++) {
			if (of[c] < 0) {
				continue;
			}
			int cx = c % CityPlan.GRID;
			int cz = c / CityPlan.GRID;
			int x0 = hall.getX() - half + cx * size;
			int z0 = hall.getZ() - half + cz * size;
			if (x0 + size < centre.getX() - REACH || x0 > centre.getX() + REACH || z0 + size < centre.getZ() - REACH || z0 > centre.getZ() + REACH) {
				continue;
			}
			int color = CityZones.get(plan.zones().get(of[c]).kind()).map(k -> k.color().getTextureDiffuseColor() & 0xFFFFFF).orElse(0x808080);
			// each edge a little inside the cell, so two zones side by side both show
			double in = 0.25;
			if (cz == 0 || of[c - CityPlan.GRID] != of[c]) {
				edge(level, x0 + in, z0 + in, x0 + size - in, z0 + in, color, centre, out);
			}
			if (cz == CityPlan.GRID - 1 || of[c + CityPlan.GRID] != of[c]) {
				edge(level, x0 + in, z0 + size - in, x0 + size - in, z0 + size - in, color, centre, out);
			}
			if (cx == 0 || of[c - 1] != of[c]) {
				edge(level, x0 + in, z0 + in, x0 + in, z0 + size - in, color, centre, out);
			}
			if (cx == CityPlan.GRID - 1 || of[c + 1] != of[c]) {
				edge(level, x0 + size - in, z0 + in, x0 + size - in, z0 + size - in, color, centre, out);
			}
		}
	}

	/** {@code points} (offsets from the hall) as a line, or two lines {@code side} blocks either side of it. */
	private static void polyline(ServerLevel level, BlockPos hall, List<BlockPos> points, boolean closed, double side, int color,
								 BlockPos centre, List<Dot> out) {
		int n = points.size();
		for (int i = 0; i + 1 < n || (closed && n > 2 && i < n); i++) {
			BlockPos a = points.get(i);
			BlockPos b = points.get((i + 1) % n);
			double ax = hall.getX() + a.getX() + 0.5, az = hall.getZ() + a.getZ() + 0.5;
			double bx = hall.getX() + b.getX() + 0.5, bz = hall.getZ() + b.getZ() + 0.5;
			if (side == 0) {
				edge(level, ax, az, bx, bz, color, centre, out);
				continue;
			}
			double len = Math.hypot(bx - ax, bz - az);
			if (len < 1e-6) {
				continue;
			}
			double px = -(bz - az) / len * side, pz = (bx - ax) / len * side;
			edge(level, ax + px, az + pz, bx + px, bz + pz, color, centre, out);
			edge(level, ax - px, az - pz, bx - px, bz - pz, color, centre, out);
		}
	}

	/** A dot a block from (ax, az) to (bx, bz), each on the ground, those within reach of {@code centre}. */
	private static void edge(ServerLevel level, double ax, double az, double bx, double bz, int color, BlockPos centre, List<Dot> out) {
		double len = Math.hypot(bx - ax, bz - az);
		int steps = Math.max(1, (int) Math.ceil(len));
		double cx = centre.getX() + 0.5, cz = centre.getZ() + 0.5;
		for (int i = 0; i <= steps && out.size() < MAX_DOTS; i++) {
			double x = ax + (bx - ax) * i / steps;
			double z = az + (bz - az) * i / steps;
			if ((x - cx) * (x - cx) + (z - cz) * (z - cz) > REACH * REACH) {
				continue;
			}
			int bxi = (int) Math.floor(x), bzi = (int) Math.floor(z);
			if (!level.hasChunk(bxi >> 4, bzi >> 4)) {
				continue;
			}
			out.add(new Dot(x, ground(level, bxi, bzi, centre.getY()) + 0.15, z, color));
		}
	}

	/**
	 * The ground at {@code x}, {@code z} near the player's height {@code y}: the first solid top at or below a few blocks
	 * over them (so the dust lies on the street, not on the roofs over it), else the top of the column.
	 */
	static int ground(ServerLevel level, int x, int z, int y) {
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, Math.min(top, y + 4) - 1, z);
		for (int i = 0; i < 16 && pos.getY() > level.getMinBuildHeight(); i++) {
			if (level.getBlockState(pos).blocksMotion()) {
				return pos.getY() + 1;
			}
			pos.move(0, -1, 0);
		}
		return top;
	}

	private CityPlanGround() {
	}
}
