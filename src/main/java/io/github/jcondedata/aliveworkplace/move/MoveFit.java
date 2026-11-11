package io.github.jcondedata.aliveworkplace.move;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.city.RoadStyles;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * Whether a building fits a new spot (ROADMAP 45.2): the Hall's Move page asks this for the spot picked on its map and
 * says plainly why one is refused. In order: the whole footprint inside the village, clear of the village's other
 * buildings (finished or going up), the hall and the new spots of other buildings on the move (B1174), no road or path under it, no water on it, and ground no steeper than
 * {@link #MAX_STEP} blocks from its lowest to its highest column, and no Mover's Crate in its box (B1129). The building's own old spot counts as its ground (the
 * Mover takes it down first), so a building can shift a little or turn where it stands.
 */
public final class MoveFit {
	/** The most the ground under a new spot may rise from its lowest column to its highest. */
	public static final int MAX_STEP = 3;

	public enum Reason {
		FITS, SAME_SPOT, OUTSIDE, BUILDING, ROAD, STEEP, WATER, CRATE;

		public String key() {
			return name().toLowerCase();
		}
	}

	/**
	 * A spot checked: {@code reason} (FITS when it fits), the box the building would take there ({@code min} its lowest
	 * corner on the ground), and for a refusal what's in the way ({@code what}, a building's name) and where ({@code at}).
	 */
	public record Fit(Reason reason, BlockPos min, BoundingBox box, @Nullable Component what, @Nullable BlockPos at) {
		public boolean fits() {
			return reason == Reason.FITS;
		}

		/** The line the page shows: "This spot fits." or why it's refused, plainly. */
		public Component line() {
			String key = "screen.aliveworkplace.move.fit." + reason.key();
			if (reason == Reason.BUILDING && what != null) {
				return Component.translatable(key, what);
			}
			if (at != null && (reason == Reason.ROAD || reason == Reason.WATER || reason == Reason.CRATE)) {
				return Component.translatable(key + ".at", at.getX(), at.getY(), at.getZ());
			}
			return Component.translatable(key);
		}
	}

	/**
	 * Checks the spot whose middle is ({@code centreX}, {@code centreZ}) for a building of turned size {@code size} now
	 * standing in {@code old} (null: none), in the village round {@code hall}; {@code same} says the spot and turn are the
	 * ones it has now. {@code from} is the building's origin, left out of the other buildings.
	 */
	public static Fit check(ServerLevel level, BlockPos hall, @Nullable BlockPos from, @Nullable BoundingBox old, Vec3i size, int centreX, int centreZ,
							boolean same) {
		int minX = centreX - size.getX() / 2;
		int minZ = centreZ - size.getZ() / 2;
		int maxX = minX + size.getX() - 1;
		int maxZ = minZ + size.getZ() - 1;
		// the ground: each column's top solid block (its old spot's floor under the building itself)
		int[] heights = new int[size.getX() * size.getZ()];
		BlockPos water = null;
		BlockPos road = null;
		int i = 0;
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				int top;
				if (old != null && x >= old.minX() && x <= old.maxX() && z >= old.minZ() && z <= old.maxZ()) {
					top = old.minY() - 1;
				} else {
					top = groundY(level, x, z);
					BlockPos pos = new BlockPos(x, top, z);
					BlockState state = level.getBlockState(pos);
					if (water == null && state.getFluidState().is(FluidTags.WATER)) {
						water = pos;
					}
					if (road == null && RoadStyles.isPaving(state)) {
						road = pos;
					}
				}
				heights[i++] = top;
			}
		}
		int[] sorted = heights.clone();
		Arrays.sort(sorted);
		int ground = sorted.length == 0 ? hall.getY() - 1 : sorted[sorted.length / 2];
		BlockPos min = new BlockPos(minX, ground + 1, minZ);
		BoundingBox box = new BoundingBox(minX, ground + 1, minZ, maxX, ground + Math.max(1, size.getY()), maxZ);
		if (same) {
			return new Fit(Reason.SAME_SPOT, min, box, null, null);
		}
		for (int[] corner : new int[][]{{minX, minZ}, {maxX, minZ}, {minX, maxZ}, {maxX, maxZ}}) {
			if (!VillageHalls.contains(hall, new BlockPos(corner[0], hall.getY(), corner[1]))) {
				return new Fit(Reason.OUTSIDE, min, box, null, null);
			}
		}
		if (hall.getX() >= minX && hall.getX() <= maxX && hall.getZ() >= minZ && hall.getZ() <= maxZ) {
			return new Fit(Reason.BUILDING, min, box, Component.translatable("screen.aliveworkplace.move.the_hall"), hall);
		}
		for (Other other : others(level, hall, from)) {
			if (other.box().minX() <= maxX && other.box().maxX() >= minX && other.box().minZ() <= maxZ && other.box().maxZ() >= minZ) {
				return new Fit(Reason.BUILDING, min, box, other.name(), new BlockPos(other.box().minX(), other.box().minY(), other.box().minZ()));
			}
		}
		for (Other other : moving(level, from)) {
			// B1174: another building's planned or under-way move will stand there, so two Movers would rebuild into each other
			if (other.box().minX() <= maxX && other.box().maxX() >= minX && other.box().minZ() <= maxZ && other.box().maxZ() >= minZ) {
				return new Fit(Reason.BUILDING, min, box, other.name(), new BlockPos(other.box().minX(), other.box().minY(), other.box().minZ()));
			}
		}
		BlockPos crate = crateIn(level, box);
		if (crate != null) {
			// B1129: a Mover's Crate stays where it stands (B1120), so the building going up there would break it
			return new Fit(Reason.CRATE, min, box, null, crate);
		}
		if (road != null) {
			return new Fit(Reason.ROAD, min, box, null, road);
		}
		if (water != null) {
			return new Fit(Reason.WATER, min, box, null, water);
		}
		if (sorted.length > 0 && sorted[sorted.length - 1] - sorted[0] > MAX_STEP) {
			return new Fit(Reason.STEEP, min, box, null, null);
		}
		return new Fit(Reason.FITS, min, box, null, null);
	}

	/**
	 * A Mover's Crate standing in {@code box} (null: none). A Mover's Crate never moves with a building (B1120), so a
	 * new spot over one would break it and leave its Mover without his workstation (B1129).
	 */
	@Nullable
	static BlockPos crateIn(ServerLevel level, BoundingBox box) {
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (MoveSite.stays(level.getBlockState(pos))) {
				return pos.immutable();
			}
		}
		return null;
	}

	/**
	 * The top of the ground at ({@code x}, {@code z}): the highest block that stops a walker or holds water, leaves and
	 * barriers left out (a barrier is no ground: GameTest areas and maps roof themselves with them).
	 */
	public static int groundY(ServerLevel level, int x, int z) {
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		int bottom = level.getMinBuildHeight();
		while (y > bottom) {
			BlockState state = level.getBlockState(new BlockPos(x, y, z));
			if (!state.is(Blocks.BARRIER) && !state.is(BlockTags.LEAVES) && (state.blocksMotion() || !state.getFluidState().isEmpty())) {
				break;
			}
			y--;
		}
		return y;
	}

	/** Another building of the village, with its box and name. */
	public record Other(BoundingBox box, Component name) {
	}

	/** The village's buildings, finished or going up, but the one at {@code from}. */
	public static List<Other> others(ServerLevel level, BlockPos hall, @Nullable BlockPos from) {
		List<Other> out = new ArrayList<>();
		BuildSiteManager sites = BuildSiteManager.get(level);
		for (BuildSiteManager.Finished f : sites.finishedInVillage(level, hall)) {
			if (!f.placement().origin().equals(from)) {
				BlueprintLibrary.get(level, f.structure()).ifPresent(b -> out.add(new Other(BlueprintOutline.bounds(f.placement(), b.size()),
					Blueprints.displayName(f.structure()))));
			}
		}
		for (BuildSite site : sites.all()) {
			if (!site.isDone() && site.placement().dimension().equals(Ids.of(level.dimension())) && !site.placement().origin().equals(from)
				&& VillageHalls.contains(hall, site.placement().origin())) {
				BlueprintLibrary.get(level, site.structure()).ifPresent(b -> out.add(new Other(BlueprintOutline.bounds(site.placement(), b.size()),
					Blueprints.displayName(site.structure()))));
			}
		}
		return out;
	}

	/**
	 * The new spots of the other buildings on the move in this dimension (B1174): each move under way by its site's new
	 * box, each move still waiting for a Mover by its new lowest corner and its blueprint's turned size. The building at
	 * {@code from} is left out, and so is a move being called off (it goes back where it was).
	 */
	public static List<Other> moving(ServerLevel level, @Nullable BlockPos from) {
		List<Other> out = new ArrayList<>();
		List<BlockPos> started = new ArrayList<>();
		for (MoveSite site : MoveSites.get(level).all()) {
			started.add(site.from());
			if (site.from().equals(from) || site.canceled() || site.stage() == MoveSite.Stage.DONE) {
				continue;
			}
			Component name = site.plan().structure() == null ? Component.translatable("screen.aliveworkplace.move.another_building")
				: Blueprints.displayName(site.plan().structure());
			out.add(new Other(site.newBox(), name));
		}
		for (PendingMoves.Move move : PendingMoves.get(level).all()) {
			if (move.from().equals(from) || started.contains(move.from())) {
				continue;
			}
			BlueprintLibrary.get(level, move.structure()).ifPresent(b -> {
				boolean quarter = move.rotation() == Rotation.CLOCKWISE_90 || move.rotation() == Rotation.COUNTERCLOCKWISE_90;
				int sx = quarter ? b.size().getZ() : b.size().getX();
				int sz = quarter ? b.size().getX() : b.size().getZ();
				BlockPos to = move.to();
				out.add(new Other(new BoundingBox(to.getX(), to.getY(), to.getZ(), to.getX() + sx - 1, to.getY() + Math.max(1, b.size().getY()) - 1,
					to.getZ() + sz - 1), Blueprints.displayName(move.structure())));
			});
		}
		return out;
	}

	private MoveFit() {
	}
}
