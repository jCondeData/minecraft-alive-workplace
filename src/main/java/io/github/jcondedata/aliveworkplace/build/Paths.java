package io.github.jcondedata.aliveworkplace.build;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * Paths: when a builder finishes a building, they lay a dirt path from its door to the heart of the village — the
 * meeting bell or the Village Hall (their bench if there's neither) within {@link #REACH} blocks. The route is found over
 * the ground (steps of at most a block up or down, round water, buildings and trees) and only natural ground on it is
 * turned into path; stone, gravel, sand and roads already there are walked over as they are.
 */
public final class Paths {
	/** Whether builders lay paths at all ({@code builderPaths} in the config). */
	public static boolean ENABLED = true;
	/** How far from the building the path may lead. */
	public static int REACH = 48;
	/** Most blocks of path from one building. */
	public static final int MAX_LENGTH = 96;
	private static final int MAX_NODES = 6000;

	/** Plans the path from a finished building and hands it to the builder (see {@link PathWork}). */
	public static void afterBuild(ServerLevel level, Villager builder, BuildSite site) {
		if (site.isDeconstruction()) {
			return;
		}
		BoundingBox box = BlueprintLibrary.get(level, site.structure())
			.map(b -> BlueprintOutline.bounds(site.placement(), b.size())).orElse(null);
		if (box == null) {
			return;
		}
		BlockPos start = doorstep(level, box);
		BlockPos goal = destination(level, builder, box);
		if (start == null || goal == null) {
			return;
		}
		List<BlockPos> route = route(level, start, goal, box);
		List<BlockPos> path = new ArrayList<>();
		for (BlockPos p : route) {
			if (convertible(level.getBlockState(p.below()))) {
				path.add(p.below());
			}
			if (path.size() >= MAX_LENGTH) {
				break;
			}
		}
		if (!path.isEmpty()) {
			List<BlockPos> all = new ArrayList<>(ModAttachments.PATH.getOrElse(builder, List.of()));
			all.addAll(path);
			ModAttachments.PATH.set(builder, List.copyOf(all));
		}
	}

	/**
	 * Where the path starts: out of the building's door (the one nearest an edge of the building, on its lowest floor),
	 * at the first spot outside the building's bounds; without a door, in front of the middle of the front edge.
	 */
	@Nullable
	public static BlockPos doorstep(ServerLevel level, BoundingBox box) {
		BlockPos best = null;
		int bestSteps = Integer.MAX_VALUE;
		int lowest = Integer.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), Math.min(box.maxY(), box.minY() + 3), box.maxZ())) {
			BlockState state = level.getBlockState(p);
			if (!(state.getBlock() instanceof DoorBlock) || state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER || p.getY() > lowest) {
				continue;
			}
			for (Direction side : Direction.Plane.HORIZONTAL) {
				int steps = 1;
				BlockPos out = p.relative(side);
				while (box.isInside(out) && steps < 6) {
					out = out.relative(side);
					steps++;
				}
				BlockPos feet = box.isInside(out) ? null : walkable(level, out);
				if (feet != null && (p.getY() < lowest || steps < bestSteps)) {
					lowest = p.getY();
					bestSteps = steps;
					best = feet;
				}
			}
		}
		if (best != null) {
			return best;
		}
		return walkable(level, new BlockPos(box.getCenter().getX(), box.minY(), box.minZ() - 1));
	}

	/** Where the path goes: the nearest meeting bell or Village Hall within reach, else the builder's bench. */
	@Nullable
	static BlockPos destination(ServerLevel level, Villager builder, BoundingBox box) {
		BlockPos centre = box.getCenter();
		Optional<BlockPos> bell = level.getPoiManager().findClosest(h -> h.is(PoiTypes.MEETING), centre, REACH, PoiManager.Occupancy.ANY);
		Optional<BlockPos> hall = VillageHalls.nearest(level, centre).filter(h -> h.distSqr(centre) <= (double) REACH * REACH);
		BlockPos target = bell.isPresent() && (hall.isEmpty() || bell.get().distSqr(centre) <= hall.get().distSqr(centre)) ? bell.orElse(null)
			: hall.orElse(null);
		if (target == null) {
			target = Builders.benchPos(builder).orElse(null);
		}
		if (target == null || box.isInside(target)) {
			return null;
		}
		for (int r = 1; r <= 3; r++) {
			for (BlockPos p : BlockPos.betweenClosed(target.offset(-r, -2, -r), target.offset(r, 2, r))) {
				BlockPos feet = walkable(level, p);
				if (feet != null && !box.isInside(feet)) {
					return feet;
				}
			}
		}
		return null;
	}

	/** The feet position of a spot a villager can stand at in {@code pos}'s column (within a block up or down), or null. */
	@Nullable
	public static BlockPos walkable(ServerLevel level, BlockPos pos) {
		for (int dy : new int[] {0, 1, -1}) {
			BlockPos feet = pos.above(dy);
			BlockState ground = level.getBlockState(feet.below());
			if (ground.isFaceSturdy(level, feet.below(), Direction.UP) && open(level, feet) && open(level, feet.above())
				&& !ground.is(BlockTags.LEAVES) && level.getFluidState(feet.below()).isEmpty()) {
				return feet.immutable();
			}
		}
		return null;
	}

	private static boolean open(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return (state.isAir() || state.canBeReplaced() && !state.is(Blocks.WATER) && !state.is(Blocks.LAVA)) && level.getFluidState(pos).isEmpty();
	}

	/** Ground that turns into dirt path. */
	public static boolean convertible(BlockState state) {
		return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.PODZOL)
			|| state.is(Blocks.MYCELIUM) || state.is(Blocks.ROOTED_DIRT);
	}

	private record Node(BlockPos pos, int cost, int estimate) {
	}

	/**
	 * What a route keeps to (27.15 widens the builders' paths for roads): {@code halfWidth} blocks either side of each
	 * step kept clear ({@link #ok}), out of every box in {@code avoid}, within {@code reach} of the start; {@code clear}
	 * decides which spots a road may take (null: any spot a villager can stand at, as paths do).
	 */
	public record Options(int halfWidth, List<BoundingBox> avoid, int reach, @Nullable Clearance clear) {
		public Options {
			avoid = List.copyOf(avoid);
		}

		static Options path(@Nullable BoundingBox avoid) {
			return new Options(0, avoid == null ? List.of() : List.of(avoid), REACH, null);
		}
	}

	/** Whether a road may take the column at {@code x, z} with its feet at {@code feetY} (27.15's roads: natural ground only). */
	@FunctionalInterface
	public interface Clearance {
		boolean clear(ServerLevel level, int x, int feetY, int z);
	}

	/**
	 * The shortest walk from {@code start} to {@code goal} (feet positions), keeping out of {@code avoid} (the building)
	 * and within {@link #REACH} of the start; steps onto natural ground are cheaper than onto anything else, so the path
	 * keeps to the fields. Empty if there's no way.
	 */
	public static List<BlockPos> route(ServerLevel level, BlockPos start, BlockPos goal, @Nullable BoundingBox avoid) {
		Search search = new Search(level, start, goal, Options.path(avoid));
		search.step(MAX_NODES);
		return search.result();
	}

	/**
	 * A route being found a few nodes at a time ({@link #step}), so a long road's way can be looked for over several
	 * ticks (27.15: 600 nodes a tick per hall). Steps of at most one block up or down, round water.
	 */
	public static final class Search {
		private final ServerLevel level;
		private final BlockPos start;
		private final BlockPos goal;
		private final Options options;
		private final Map<Long, Integer> best = new HashMap<>();
		private final Map<Long, BlockPos> from = new HashMap<>();
		private final PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingInt(n -> n.cost() + n.estimate()));
		private int visited;
		private boolean done;
		private List<BlockPos> result = List.of();

		public Search(ServerLevel level, BlockPos start, BlockPos goal, Options options) {
			this.level = level;
			this.start = start.immutable();
			this.goal = goal.immutable();
			this.options = options;
			open.add(new Node(this.start, 0, estimate(this.start, this.goal)));
			best.put(this.start.asLong(), 0);
		}

		public boolean done() {
			return done;
		}

		/** The way found (start to goal), or empty if there's none (or not yet). */
		public List<BlockPos> result() {
			return result;
		}

		/** Nodes looked at so far. */
		public int visited() {
			return visited;
		}

		/** Looks at up to {@code nodes} more spots; true once finished (found or not). */
		public boolean step(int nodes) {
			int n = 0;
			while (!done && n++ < nodes) {
				if (open.isEmpty()) {
					done = true;
					break;
				}
				visited++;
				Node node = open.poll();
				BlockPos p = node.pos();
				if (node.cost() > best.getOrDefault(p.asLong(), Integer.MAX_VALUE)) {
					continue;
				}
				if (Math.abs(p.getX() - goal.getX()) + Math.abs(p.getZ() - goal.getZ()) <= 1 && Math.abs(p.getY() - goal.getY()) <= 1) {
					List<BlockPos> out = new ArrayList<>();
					for (BlockPos c = p; c != null; c = from.get(c.asLong())) {
						out.add(c);
					}
					Collections.reverse(out);
					result = List.copyOf(out);
					done = true;
					break;
				}
				for (Direction side : Direction.Plane.HORIZONTAL) {
					BlockPos next = walkable(level, p.relative(side));
					if (next == null || !ok(next) || next.distSqr(start) > (double) options.reach() * options.reach()) {
						continue;
					}
					int step = convertible(level.getBlockState(next.below())) || level.getBlockState(next.below()).is(Blocks.DIRT_PATH) ? 2 : 3;
					if (next.getY() != p.getY()) {
						step += 2;
					}
					int cost = node.cost() + step;
					if (cost < best.getOrDefault(next.asLong(), Integer.MAX_VALUE)) {
						best.put(next.asLong(), cost);
						from.put(next.asLong(), p);
						open.add(new Node(next, cost, estimate(next, goal)));
					}
				}
			}
			return done;
		}

		/** The spot and the road's width round it: out of every box to avoid, and clear for a road. */
		private boolean ok(BlockPos feet) {
			int r = options.halfWidth();
			for (BoundingBox box : options.avoid()) {
				if (options.clear() == null) {
					if (box.isInside(feet)) {
						return false; // a builder's path: only the building itself is kept out of
					}
					continue;
				}
				if (box.intersects(feet.getX() - r, feet.getZ() - r, feet.getX() + r, feet.getZ() + r)
					&& feet.getY() >= box.minY() - 2 && feet.getY() <= box.maxY() + 1) {
					return false;
				}
			}
			if (options.clear() == null) {
				return true;
			}
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (!options.clear().clear(level, feet.getX() + dx, feet.getY(), feet.getZ() + dz)) {
						return false;
					}
				}
			}
			return true;
		}
	}

	private static int estimate(BlockPos a, BlockPos b) {
		return 2 * (Math.abs(a.getX() - b.getX()) + Math.abs(a.getZ() - b.getZ()));
	}

	private Paths() {
	}
}
