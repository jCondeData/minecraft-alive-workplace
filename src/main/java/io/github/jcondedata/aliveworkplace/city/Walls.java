package io.github.jcondedata.aliveworkplace.city;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * The village's wall (ROADMAP 27.18). Once the village was raided in the last {@link #RAID_DAYS} days, or a bandit camp
 * is near, the Steward proposes a wall along the plan's wall line — or, when none is drawn, a line of his own round the
 * zones, {@link #ZONE_MARGIN} blocks out — and the line goes on the plan so the player sees what he means before
 * approving it ({@link StewardDesk}).
 *
 * <p>A wall is made of a kit ({@link WallKits}): a segment, a corner tower and a gate, for a range of ranks. The line is
 * laid out ({@link #layout}) into pieces: a tower at every corner and at least every {@link #TOWER_EVERY} blocks, the
 * segments between moved along so each fits whole (the last one of a run sits flush against the next corner, over its
 * neighbour, rather than leaving a gap short of a segment), and a gate wherever one of the plan's roads crosses. Each
 * piece sits at its own ground height, so the foundation fills under it on a slope. At most {@link #MAX_OPEN} wall sites
 * are open at once, and a wall counts as one building for the village's rank, not one per segment
 * ({@link #countsAsBuildings}). A village that outgrows its kit (a Town with a palisade) replaces it with the next kit a
 * piece at a time. Config {@code stewardWalls}.
 */
public final class Walls {
	/** Config {@code stewardWalls}: off, the Steward never proposes a wall (a drawn wall line is still just a line). */
	public static boolean ENABLED = true;
	/** A raid this many days ago or fewer makes him want a wall. */
	public static final int RAID_DAYS = 7;
	/** His own line, when none is drawn: this far out round the zones. */
	public static final int ZONE_MARGIN = 4;
	/** A tower at least every this many blocks of wall. */
	public static final int TOWER_EVERY = 28;
	/** Wall sites open at once per hall. */
	public static final int MAX_OPEN = 3;
	/** The blueprint a wall proposal stands for (none: it is many pieces). */
	public static final ResourceLocation WALL = io.github.jcondedata.aliveworkplace.AliveWorkplace.id("steward/wall");
	/** The rule a wall proposal is filed under on the desk (declining it holds walls off for the usual days). */
	public static final ResourceLocation RULE = io.github.jcondedata.aliveworkplace.AliveWorkplace.id("steward/wall");
	/** How far a road node may be from the line to count as crossing it. */
	static final int CROSSING_REACH = 2;
	/** How far above and below the hall a piece's ground is looked for. */
	static final int GROUND_REACH = 24;

	/** What a piece of a wall is. */
	public enum Kind {
		SEGMENT, TOWER, GATE
	}

	/** One piece of the wall: where it is in the layout, what it is, its blueprint and where it goes. */
	public record Piece(int index, Kind kind, ResourceLocation blueprint, BlueprintData.Placement placement, BoundingBox box) {
	}

	// ---- the line ---------------------------------------------------------------------------------------------

	/**
	 * The wall line in the world: the plan's, if one is drawn, else one of the Steward's own round the plan's buildable
	 * zones, {@link #ZONE_MARGIN} blocks out (a rectangle). Empty when the village has neither.
	 */
	public static List<BlockPos> line(ServerLevel level, BlockPos hall, CityPlan plan) {
		if (plan.wall().isPresent() && plan.wall().get().points().size() >= 2) {
			List<BlockPos> out = new ArrayList<>();
			for (BlockPos p : plan.wall().get().points()) {
				out.add(new BlockPos(hall.getX() + p.getX(), hall.getY(), hall.getZ() + p.getZ()));
			}
			return List.copyOf(out);
		}
		return ownLine(hall, plan);
	}

	/** Whether the line in use is closed (a drawn open line stays open; the Steward's own is a closed rectangle). */
	public static boolean closed(CityPlan plan) {
		return plan.wall().map(CityPlan.Wall::closed).orElse(true);
	}

	/** The Steward's own line: the rectangle round every buildable zone's cells, {@link #ZONE_MARGIN} blocks out. */
	public static List<BlockPos> ownLine(BlockPos hall, CityPlan plan) {
		int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		int size = CityPlan.cellSize();
		for (CityPlan.Zone zone : plan.zones()) {
			if (!CityZones.get(zone.kind()).map(CityZones.Kind::buildable).orElse(false)) {
				continue;
			}
			BitSet cells = zone.cells();
			for (int cell = cells.nextSetBit(0); cell >= 0; cell = cells.nextSetBit(cell + 1)) {
				BlockPos centre = CityPlan.cellCentre(hall, cell);
				minX = Math.min(minX, centre.getX() - size / 2);
				maxX = Math.max(maxX, centre.getX() + size / 2);
				minZ = Math.min(minZ, centre.getZ() - size / 2);
				maxZ = Math.max(maxZ, centre.getZ() + size / 2);
			}
		}
		if (minX > maxX) {
			return List.of();
		}
		minX -= ZONE_MARGIN;
		maxX += ZONE_MARGIN;
		minZ -= ZONE_MARGIN;
		maxZ += ZONE_MARGIN;
		int y = hall.getY();
		return List.of(new BlockPos(minX, y, minZ), new BlockPos(maxX, y, minZ), new BlockPos(maxX, y, maxZ), new BlockPos(minX, y, maxZ));
	}

	/**
	 * The line as axis-aligned runs: a leg that is not along an axis is turned into an L (along x, then along z), so
	 * every run has an inside and an outside. A closed line comes back to its first point.
	 */
	static List<BlockPos> corners(List<BlockPos> line, boolean closed) {
		List<BlockPos> points = new ArrayList<>(line);
		if (closed && points.size() > 2 && !points.get(0).equals(points.get(points.size() - 1))) {
			points.add(points.get(0));
		}
		List<BlockPos> out = new ArrayList<>();
		for (int i = 0; i < points.size(); i++) {
			BlockPos p = points.get(i);
			if (out.isEmpty()) {
				out.add(p);
				continue;
			}
			BlockPos last = out.get(out.size() - 1);
			if (last.getX() != p.getX() && last.getZ() != p.getZ()) {
				out.add(new BlockPos(p.getX(), last.getY(), last.getZ())); // the elbow of the L
			}
			if (!out.get(out.size() - 1).equals(p)) {
				out.add(p);
			}
		}
		return List.copyOf(out);
	}

	// ---- the layout -------------------------------------------------------------------------------------------

	/**
	 * The pieces of the wall along {@code line} with {@code kit}: a tower at every corner (its inside corner on the
	 * line's corner), segments between them with their inside face on the line, one more tower wherever the segments
	 * would run further than {@link #TOWER_EVERY} blocks, and a gate in place of the segment wherever a road of the plan
	 * crosses. Empty when the kit's blueprints aren't loaded.
	 */
	public static List<Piece> layout(ServerLevel level, BlockPos hall, CityPlan plan, WallKits.Kit kit) {
		Optional<Blueprint> segment = BlueprintLibrary.get(level, kit.segment());
		Optional<Blueprint> tower = BlueprintLibrary.get(level, kit.cornerTower());
		Optional<Blueprint> gate = BlueprintLibrary.get(level, kit.gate());
		List<BlockPos> line = line(level, hall, plan);
		if (segment.isEmpty() || tower.isEmpty() || gate.isEmpty() || line.size() < 2) {
			return List.of();
		}
		List<BlockPos> points = corners(line, closed(plan));
		int segW = segment.get().size().getX();
		int gateW = gate.get().size().getX();
		int towerW = tower.get().size().getX();
		List<Piece> out = new ArrayList<>();
		for (int i = 0; i + 1 < points.size(); i++) {
			BlockPos a = points.get(i);
			BlockPos b = points.get(i + 1);
			Direction along = Direction.getNearest(b.getX() - a.getX(), 0, b.getZ() - a.getZ());
			Direction outward = outward(hall, a, b);
			List<BlockPos> crossings = crossingsOn(level, hall, plan, a, b); // the roads crossing this run
			// the corner's tower: behind the corner along this run, so it fills the corner outside both of its runs
			add(out, level, hall, Kind.TOWER, kit.cornerTower(), tower.get().size(), a.relative(along.getOpposite(), towerW - 1), along, outward, towerW);
			int to = (int) Math.sqrt(a.distSqr(b)); // the run's length: a piece from s covers s .. s + width - 1
			for (int[] piece : run(crossings, a, along, to, segW, towerW, gateW)) {
				Kind kind = Kind.values()[piece[2]];
				ResourceLocation blueprint = kind == Kind.GATE ? kit.gate() : kind == Kind.TOWER ? kit.cornerTower() : kit.segment();
				Vec3i size = (kind == Kind.GATE ? gate : kind == Kind.TOWER ? tower : segment).get().size();
				add(out, level, hall, kind, blueprint, size, a.relative(along, piece[0]), along, outward, piece[1]);
			}
		}
		if (!closed(plan) && points.size() >= 2) {
			BlockPos last = points.get(points.size() - 1);
			BlockPos before = points.get(points.size() - 2);
			Direction along = Direction.getNearest(last.getX() - before.getX(), 0, last.getZ() - before.getZ());
			add(out, level, hall, Kind.TOWER, kit.cornerTower(), tower.get().size(), last, along, outward(hall, before, last), towerW);
		}
		return List.copyOf(out);
	}

	/**
	 * One run's pieces as {@code {start along the run, width, kind}}: a gate on every crossing it can take, a tower in
	 * every stretch longer than {@link #TOWER_EVERY} (evenly spaced, never within a segment of the stretch's ends), and
	 * whole segments filling what is left — spread evenly, so a stretch that isn't a round number of segments has them
	 * sitting a little over one another instead of leaving a hole. Nothing but a segment ever overlaps a segment.
	 */
	static List<int[]> run(List<BlockPos> crossings, BlockPos a, Direction along, int to, int segW, int towerW, int gateW) {
		List<int[]> fixed = new ArrayList<>();
		for (BlockPos crossing : crossings) {
			int d = (crossing.getX() - a.getX()) * along.getStepX() + (crossing.getZ() - a.getZ()) * along.getStepZ();
			int start = Math.max(1, Math.min(to - gateW, d - gateW / 2));
			if (start < 1 || start + gateW > to) {
				continue; // the crossing is too near a corner for the gate to fit
			}
			if (fixed.stream().anyMatch(f -> start < f[0] + f[1] && f[0] < start + gateW)) {
				continue; // two roads crossing within one gate's width: one gate serves both
			}
			fixed.add(new int[] {start, gateW, Kind.GATE.ordinal()});
		}
		fixed.sort(java.util.Comparator.comparingInt(f -> f[0]));
		List<int[]> withTowers = new ArrayList<>(fixed);
		for (int[] stretch : between(fixed, to)) {
			int length = stretch[1] - stretch[0];
			int towers = Math.max(0, (int) Math.ceil(length / (double) TOWER_EVERY) - 1);
			for (int k = 1; k <= towers; k++) {
				int centre = stretch[0] + (int) Math.round(k * length / (double) (towers + 1));
				int start = Math.max(stretch[0] + segW, Math.min(stretch[1] - towerW - segW, centre - towerW / 2));
				if (start >= stretch[0] && start + towerW <= stretch[1]) {
					withTowers.add(new int[] {start, towerW, Kind.TOWER.ordinal()});
				}
			}
		}
		withTowers.sort(java.util.Comparator.comparingInt(f -> f[0]));
		List<int[]> out = new ArrayList<>(withTowers);
		for (int[] stretch : between(withTowers, to)) {
			int length = stretch[1] - stretch[0];
			if (length < segW) {
				continue; // shorter than a segment: the pieces either side of it stand together
			}
			int n = (length + segW - 1) / segW;
			for (int k = 0; k < n; k++) {
				int start = n == 1 ? stretch[0] : stretch[0] + (int) Math.round(k * (length - segW) / (double) (n - 1));
				out.add(new int[] {start, segW, Kind.SEGMENT.ordinal()});
			}
		}
		out.sort(java.util.Comparator.comparingInt(f -> f[0]));
		return List.copyOf(out);
	}

	/** The stretches of a run (from 1 to {@code to}) left free by {@code pieces} (sorted, not overlapping). */
	private static List<int[]> between(List<int[]> pieces, int to) {
		List<int[]> out = new ArrayList<>();
		int cursor = 1;
		for (int[] piece : pieces) {
			if (piece[0] > cursor) {
				out.add(new int[] {cursor, piece[0]});
			}
			cursor = Math.max(cursor, piece[0] + piece[1]);
		}
		if (cursor < to) {
			out.add(new int[] {cursor, to});
		}
		return out;
	}

	/** Adds one piece: it starts at {@code at} along the run, its inside face on the line, at its own ground height. */
	private static void add(List<Piece> out, ServerLevel level, BlockPos hall, Kind kind, ResourceLocation blueprint, Vec3i size,
							BlockPos at, Direction along, Direction outward, int width) {
		Rotation rotation = rotationFor(outward);
		// the footprint: `width` along the run from `at`, `depth` outward from the line
		BlockPos far = at.relative(along, width - 1).relative(outward, size.getZ() - 1);
		int minX = Math.min(at.getX(), far.getX());
		int minZ = Math.min(at.getZ(), far.getZ());
		int maxX = Math.max(at.getX(), far.getX());
		int maxZ = Math.max(at.getZ(), far.getZ());
		int y = groundY(level, (minX + maxX) / 2, (minZ + maxZ) / 2, hall.getY());
		BlockPos corner = net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.transform(
			new BlockPos(size.getX() - 1, 0, size.getZ() - 1), Mirror.NONE, rotation, BlockPos.ZERO);
		BlockPos origin = new BlockPos(minX - Math.min(0, corner.getX()), y, minZ - Math.min(0, corner.getZ()));
		BlueprintData.Placement placement = new BlueprintData.Placement(Ids.of(level.dimension()), origin, rotation, Mirror.NONE);
		out.add(new Piece(out.size(), kind, blueprint, placement, BlueprintOutline.bounds(placement, size)));
	}

	/** The turn that puts a piece's outside (its z = size - 1 side, the village being at z = 0) towards {@code outward}. */
	static Rotation rotationFor(Direction outward) {
		for (Rotation rotation : Rotation.values()) {
			if (rotation.rotate(Direction.SOUTH) == outward) {
				return rotation;
			}
		}
		return Rotation.NONE;
	}

	/** Which way is out of the village along the run from {@code a} to {@code b}: away from the hall. */
	static Direction outward(BlockPos hall, BlockPos a, BlockPos b) {
		Direction along = Direction.getNearest(b.getX() - a.getX(), 0, b.getZ() - a.getZ());
		Direction side = along.getClockWise();
		BlockPos middle = new BlockPos((a.getX() + b.getX()) / 2, hall.getY(), (a.getZ() + b.getZ()) / 2);
		return middle.relative(side, 4).distSqr(hall) > middle.relative(side.getOpposite(), 4).distSqr(hall) ? side : side.getOpposite();
	}

	/** The top of the ground at {@code x, z} near the hall's height: a piece's blocks start on the block above it. */
	static int groundY(ServerLevel level, int x, int z, int nearY) {
		int top = Math.min(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), nearY + GROUND_REACH);
		for (int y = top; y >= nearY - GROUND_REACH; y--) {
			BlockPos p = new BlockPos(x, y, z);
			BlockState state = level.getBlockState(p);
			if (!state.isAir() && level.getFluidState(p).isEmpty() && !state.canBeReplaced()) {
				return y + 1;
			}
		}
		return nearY;
	}

	/** Where the plan's roads cross the run {@code a}-{@code b} of the line (world positions on it, one per crossing). */
	static List<BlockPos> crossingsOn(ServerLevel level, BlockPos hall, CityPlan plan, BlockPos a, BlockPos b) {
		List<BlockPos> out = new ArrayList<>();
		for (CityPlan.Road road : plan.roads()) {
			if (road.lane()) {
				continue;
			}
			List<BlockPos> way = new ArrayList<>();
			for (BlockPos p : road.routed() ? road.route() : road.points()) {
				way.add(new BlockPos(hall.getX() + p.getX(), a.getY(), hall.getZ() + p.getZ()));
			}
			for (int j = 0; j < way.size(); j++) {
				BlockPos node = way.get(j);
				BlockPos on = nearestOn(a, b, node);
				if (on != null && Math.abs(on.getX() - node.getX()) + Math.abs(on.getZ() - node.getZ()) <= CROSSING_REACH
					&& out.stream().noneMatch(p -> p.distSqr(on) < 64)) {
					out.add(on);
				}
				if (!road.routed() && j + 1 < way.size()) {
					// an unrouted road: its drawn legs are straight lines, so look along them too
					BlockPos hit = cross(a, b, node, way.get(j + 1));
					if (hit != null && out.stream().noneMatch(p -> p.distSqr(hit) < 64)) {
						out.add(hit);
					}
				}
			}
		}
		return List.copyOf(out);
	}

	/** The point of the run {@code a}-{@code b} nearest {@code p}, or null when {@code p} is past either end. */
	@Nullable
	private static BlockPos nearestOn(BlockPos a, BlockPos b, BlockPos p) {
		if (a.getX() == b.getX()) {
			int z = Math.max(Math.min(a.getZ(), b.getZ()), Math.min(Math.max(a.getZ(), b.getZ()), p.getZ()));
			return new BlockPos(a.getX(), a.getY(), z);
		}
		if (a.getZ() == b.getZ()) {
			int x = Math.max(Math.min(a.getX(), b.getX()), Math.min(Math.max(a.getX(), b.getX()), p.getX()));
			return new BlockPos(x, a.getY(), a.getZ());
		}
		return null;
	}

	/** Where the straight leg {@code c}-{@code d} crosses the axis-aligned run {@code a}-{@code b} (null: nowhere). */
	@Nullable
	private static BlockPos cross(BlockPos a, BlockPos b, BlockPos c, BlockPos d) {
		int steps = Math.max(Math.abs(d.getX() - c.getX()), Math.abs(d.getZ() - c.getZ()));
		for (int i = 0; i <= steps; i++) {
			int x = c.getX() + Math.round((d.getX() - c.getX()) * i / (float) Math.max(1, steps));
			int z = c.getZ() + Math.round((d.getZ() - c.getZ()) * i / (float) Math.max(1, steps));
			BlockPos node = new BlockPos(x, a.getY(), z);
			BlockPos on = nearestOn(a, b, node);
			if (on != null && Math.abs(on.getX() - x) + Math.abs(on.getZ() - z) <= CROSSING_REACH) {
				return on;
			}
		}
		return null;
	}

	// ---- proposing and building -------------------------------------------------------------------------------

	/** Whether the village wants a wall now: raided within {@link #RAID_DAYS} days, or a bandit camp near. */
	public static boolean wanted(ServerLevel level, BlockPos hall) {
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return false;
		}
		long last = entity.lastRaidDay();
		long since = last <= StewardConditions.RaidedWithin.NEVER_RAIDED ? -1 : Chronicle.day(level) - last;
		return since >= 0 && since < RAID_DAYS || io.github.jcondedata.aliveworkplace.guard.BanditCamps.near(level, hall).isPresent();
	}

	/** The kit this village builds its wall with: the one for its rank. */
	public static Optional<WallKits.Kit> kitFor(ServerLevel level, BlockPos hall) {
		return WallKits.forRank(VillageRanks.of(level, hall));
	}

	/**
	 * Once a morning, from the Steward's desk: if the village wants a wall, has a kit for its rank and no wall of that
	 * kit yet, his line goes on the plan and the wall becomes a proposal. Nothing happens with {@code stewardWalls} off.
	 */
	public static void propose(ServerLevel level, BlockPos hall, Villager steward) {
		if (!ENABLED || !(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) || !wanted(level, hall)) {
			return;
		}
		CityPlan plan = entity.plan();
		Optional<WallKits.Kit> kit = kitFor(level, hall);
		if (kit.isEmpty() || plan.wall().map(w -> w.approved() && w.kit().equals(kit.get().name())).orElse(false)) {
			return;
		}
		boolean own = plan.wall().isEmpty();
		List<BlockPos> line = line(level, hall, plan);
		if (line.size() < 2) {
			return; // nothing drawn and no zones to go round
		}
		List<BlockPos> offsets = new ArrayList<>();
		for (BlockPos p : line) {
			BlockPos offset = new BlockPos(p.getX() - hall.getX(), 0, p.getZ() - hall.getZ());
			if (!CityPlan.onGrid(offset)) {
				return; // his own line would run off the plan's grid
			}
			offsets.add(offset);
		}
		CityPlan.Wall wall = own ? new CityPlan.Wall(offsets, true).proposed(kit.get().name(), true)
			: plan.wall().get().proposed(kit.get().name(), false);
		CityPlan next = plan.withWall(wall);
		if (next == null) {
			return;
		}
		entity.setPlan(next);
		StewardDesk.offerWall(level, hall, kit.get());
	}

	/** The wall approved (from the desk): it is built from now on, and the chronicle says so. */
	public static boolean approve(ServerLevel level, BlockPos hall, @Nullable Villager steward, WallKits.Kit kit) {
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) || entity.plan().wall().isEmpty()) {
			return false;
		}
		CityPlan next = entity.plan().withWall(entity.plan().wall().get().approvedWith(kit.name()));
		if (next == null) {
			return false;
		}
		entity.setPlan(next);
		Chronicle.record(level, hall, Chronicle.Kind.PLANS, Component.translatable("chronicle.aliveworkplace.wall_started",
			steward != null ? steward.getDisplayName() : Component.translatable("steward.aliveworkplace.jobs.someone"), kit.title()));
		return true;
	}

	/** The wall's pieces now, with the kit for the village's rank (empty: no wall approved, or no kit). */
	public static List<Piece> pieces(ServerLevel level, BlockPos hall) {
		if (!ENABLED || !(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return List.of();
		}
		CityPlan plan = entity.plan();
		if (plan.wall().isEmpty() || !plan.wall().get().approved()) {
			return List.of();
		}
		return kitFor(level, hall).map(kit -> layout(level, hall, plan, kit)).orElse(List.of());
	}

	/** Whether {@code piece} stands finished: its own blueprint built on its spot. */
	public static boolean stands(ServerLevel level, Piece piece) {
		return finishedOn(level, piece).filter(f -> BlueprintStyles.base(f.structure()).equals(piece.blueprint())).isPresent();
	}

	/** A wall piece of any kit finished on {@code piece}'s spot (an older kit's: this piece replaces it a piece at a time). */
	static Optional<BuildSiteManager.Finished> finishedOn(ServerLevel level, Piece piece) {
		return BuildSiteManager.get(level).finishedNear(level, piece.placement().origin(), 8).stream()
			.filter(f -> isPiece(f.structure()) && BlueprintLibrary.get(level, f.structure())
				.map(b -> BlueprintOutline.bounds(f.placement(), b.size()).intersects(piece.box())).orElse(false))
			.findFirst();
	}

	/** Whether this piece would replace an older kit's piece standing on its spot (a Town's stone over its palisade). */
	public static boolean replaces(ServerLevel level, Piece piece) {
		return !stands(level, piece) && finishedOn(level, piece).isPresent();
	}

	/** The wall's pieces still to build, in order. */
	public static List<Piece> unbuilt(ServerLevel level, BlockPos hall) {
		return pieces(level, hall).stream().filter(p -> !stands(level, p)).toList();
	}

	/** Whether the wall is finished: it has pieces and every one stands. */
	public static boolean finished(ServerLevel level, BlockPos hall) {
		List<Piece> pieces = pieces(level, hall);
		return !pieces.isEmpty() && pieces.stream().allMatch(p -> stands(level, p));
	}

	/** The wall sites of this hall open now. */
	public static List<BuildSite> openSites(ServerLevel level, BlockPos hall) {
		List<ResourceLocation> ours = WallKits.allBlueprints();
		return StewardDesk.openSites(level, hall).stream()
			.filter(s -> ours.contains(BlueprintStyles.base(s.structure()))).toList();
	}

	/**
	 * The hall's round: opens wall sites for the nearest idle builders while fewer than {@link #MAX_OPEN} are open, never
	 * two that overlap, and never where a building of the village is waiting for a builder.
	 */
	public static void round(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		if (!ENABLED || !Roads.working(level, hall, entity.plan())) {
			return;
		}
		List<BuildSite> open = openSites(level, hall);
		if (open.size() >= MAX_OPEN) {
			return;
		}
		List<BoundingBox> taken = new ArrayList<>();
		for (BuildSite site : BuildSiteManager.get(level).all()) {
			BlueprintLibrary.get(level, site.structure()).ifPresent(b -> taken.add(BlueprintOutline.bounds(site.placement(), b.size())));
		}
		int opened = open.size();
		boolean replacing = open.stream().anyMatch(s -> pieces(level, hall).stream()
			.anyMatch(p -> p.placement().equals(s.placement()) && replaces(level, p)));
		for (Piece piece : unbuilt(level, hall)) {
			if (taken.stream().anyMatch(box -> box.intersects(piece.box()))) {
				continue;
			}
			if (replaces(level, piece)) {
				if (replacing) {
					continue; // a Town replaces its palisade with stone one piece at a time
				}
				replacing = true;
			}
			Villager builder = Roads.builderFor(level, hall, piece.box());
			if (builder == null) {
				return; // nobody free: wait
			}
			UUID owner = entity.owner() != null ? entity.owner() : builder.getUUID();
			String ownerName = entity.owner() != null ? entity.ownerName() : "";
			BuildSite site = Builders.start(level, builder, owner, ownerName, piece.blueprint(), piece.placement());
			site.setStewardHall(hall);
			site.setLevelGround(false); // a wall follows the ground; its foundation fills under it
			taken.add(piece.box());
			if (++opened >= MAX_OPEN) {
				return;
			}
		}
	}

	/** Every tick, from the hall's tick: every second, a wall site may open. */
	public static void tick(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		if (!ENABLED || entity.plan().wall().isEmpty() || !entity.plan().wall().get().approved()) {
			return;
		}
		if (Math.floorMod(level.getGameTime() + hall.hashCode(), Roads.CHECK_EVERY) == 0) {
			round(level, hall, entity);
		}
	}

	// ---- what the rest of the mod asks ------------------------------------------------------------------------

	/** Whether {@code structure} is a piece of some wall kit (its gates shut at night, and it is not a building of its own). */
	public static boolean isPiece(ResourceLocation structure) {
		return WallKits.allBlueprints().contains(BlueprintStyles.base(structure));
	}

	/**
	 * How many buildings a village's finished builds count as for its rank: everything that isn't a wall piece, and the
	 * whole wall as one more ({@link VillageRanks}).
	 */
	public static int countsAsBuildings(List<BuildSiteManager.Finished> finished) {
		int buildings = 0;
		boolean wall = false;
		for (BuildSiteManager.Finished f : finished) {
			if (Roads.isSegment(f.structure())) {
				continue; // roads aren't buildings (27.15)
			}
			if (isPiece(f.structure())) {
				wall = true; // a wall counts once, not one per segment (27.18)
				continue;
			}
			buildings++;
		}
		return buildings + (wall ? 1 : 0);
	}

	/** The pieces a wall of {@code kit} would have, whether or not it is approved yet (the desk's proposal). */
	public static List<Piece> proposedPieces(ServerLevel level, BlockPos hall, WallKits.Kit kit) {
		return level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity ? layout(level, hall, entity.plan(), kit) : List.of();
	}

	/** How long the wall line is, in blocks (the desk says so). */
	public static int lineLength(ServerLevel level, BlockPos hall) {
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return 0;
		}
		CityPlan plan = entity.plan();
		List<BlockPos> points = corners(line(level, hall, plan), closed(plan));
		int length = 0;
		for (int i = 0; i + 1 < points.size(); i++) {
			length += (int) Math.sqrt(points.get(i).distSqr(points.get(i + 1)));
		}
		return length;
	}

	/** What a wall proposal is called on the desk: "Palisade wall". */
	public static Component title(WallKits.Kit kit) {
		return Component.translatable("steward.aliveworkplace.wall.title", kit.title());
	}

	/** How many pieces the proposed wall has, for the desk: towers, segments and gates. */
	public static int[] counts(List<Piece> pieces) {
		int[] out = new int[Kind.values().length];
		pieces.forEach(p -> out[p.kind().ordinal()]++);
		return out;
	}

	/** The wall's pieces round {@code hall} that have been built, for the desk and the chronicle. */
	public static int standing(ServerLevel level, BlockPos hall) {
		return (int) pieces(level, hall).stream().filter(p -> stands(level, p)).count();
	}

	/** A wall piece was finished: when it was the last one, the chronicle notes the wall. */
	public static void pieceBuilt(ServerLevel level, BuildSite site, @Nullable Villager builder) {
		if (!isPiece(site.structure())) {
			return;
		}
		Optional<BlockPos> hall = site.stewardHall() != null ? Optional.of(site.stewardHall()) : VillageHalls.nearest(level, site.placement().origin());
		if (hall.isEmpty() || !(level.getBlockEntity(hall.get()) instanceof VillageHallBlockEntity entity) || entity.plan().wall().isEmpty()) {
			return;
		}
		if (!finished(level, hall.get())) {
			return;
		}
		Component who = builder != null ? builder.getDisplayName() : Component.translatable("steward.aliveworkplace.jobs.someone");
		kitFor(level, hall.get()).ifPresent(kit -> Chronicle.record(level, hall.get(), Chronicle.Kind.PLANS,
			Component.translatable("chronicle.aliveworkplace.wall_built", who, kit.title())));
	}

	private Walls() {
	}
}
