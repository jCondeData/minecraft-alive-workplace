package io.github.jcondedata.aliveworkplace.work;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Small blocks a villager can't walk through (B339). The game asks each block whether a land path may go through it, and
 * a block that doesn't answer says yes whenever it isn't a full cube. Vanilla's own lantern, brewing stand or lectern
 * answers no, but a mod's may not: Carved Wood's lanterns don't, so a lantern standing on a desk (the furnished spruce
 * Library) was walkable air to the path finder. A jobless villager's path to the lectern went up onto the desk and
 * through the lantern; the villager jumped onto the desk, couldn't walk through the lantern, and stood on the desk's edge
 * walking on the spot for the rest of the day. Now a cell holding such a block, one that would be inside the villager's
 * body above the height it can step up, is blocked to a villager's path, as vanilla's lantern is, and the path goes
 * round. Carpets, beds, cakes and other blocks low enough to step onto stay walkable; a ladder or an open door (against
 * a side, clear of the body) stays open; doors, trapdoors and fence gates keep vanilla's rules.
 */
public final class PathObstacles {
	private PathObstacles() {
	}

	/**
	 * Whether a villager's path must not go through cell ({@code x}, {@code y}, {@code z}): {@link #inTheWay} or
	 * {@link ShutTrapdoors#inTheWay}, with each of the body's cells read once for both (B412: the path finder asks for
	 * every cell it looks at, and reading them twice made these checks a share of every villager's pathfinding).
	 */
	public static boolean blocksVillager(BlockGetter level, Mob mob, int x, int y, int z) {
		if (!(mob instanceof Villager)) {
			return false;
		}
		float height = mob.getBbHeight();
		int cells = Mth.floor(height + 1.0F); // the cells the path finder checks for this mob (NodeEvaluator.prepare)
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		VoxelShape body = null;
		boolean bodyFits = true;
		for (int j = 0; j < cells; j++) {
			BlockState state = level.getBlockState(pos.set(x, y + j, z));
			if (state.isAir()) {
				continue;
			}
			if (ShutTrapdoors.shutInTheWay(level, pos, state, j, height)) {
				return true;
			}
			if (!bodyFits || !obstacle(state)) {
				continue;
			}
			VoxelShape shape = state.getCollisionShape(level, pos);
			if (shape.isEmpty()) {
				continue;
			}
			if (body == null) {
				body = body(mob, height);
				if (body == null) {
					bodyFits = false; // nothing is in the way of such a body, but a shut trapdoor still is
					continue;
				}
			}
			if (Shapes.joinIsNotEmpty(shape.move(0, j, 0), body, BooleanOp.AND)) {
				return true;
			}
		}
		return false;
	}

	/** A block this check judges: not air, not one with vanilla's own rules, and passable by the game's word. */
	private static boolean obstacle(BlockState state) {
		return !state.isAir() && !(state.getBlock() instanceof DoorBlock) && !(state.getBlock() instanceof TrapDoorBlock)
			&& !(state.getBlock() instanceof FenceGateBlock) && state.isPathfindable(PathComputationType.LAND);
	}

	/** The part of the body above what it steps over, in its cell; null for a body wholly within that. */
	private static VoxelShape body(Mob mob, float height) {
		double half = Math.min(mob.getBbWidth() / 2.0, 0.5);
		double step = Math.max(mob.maxUpStep(), 0.0);
		if (step >= height || half <= 0) {
			return null; // a body (a sleeping or very small one) wholly within what it steps over
		}
		return Shapes.box(0.5 - half, step, 0.5 - half, 0.5 + half, height, 0.5 + half);
	}

	/**
	 * Whether {@code mob} (a villager), standing in cell ({@code x}, {@code y}, {@code z}), would have inside its body a
	 * block the game calls passable for a land path.
	 */
	public static boolean inTheWay(BlockGetter level, Mob mob, int x, int y, int z) {
		if (!(mob instanceof Villager)) {
			return false;
		}
		float height = mob.getBbHeight();
		int cells = Mth.floor(height + 1.0F); // the cells the path finder checks for this mob (NodeEvaluator.prepare)
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		VoxelShape body = null; // B412: made only once a cell holds a block that could be in the way
		for (int j = 0; j < cells; j++) {
			BlockState state = level.getBlockState(pos.set(x, y + j, z));
			if (!obstacle(state)) {
				continue;
			}
			VoxelShape shape = state.getCollisionShape(level, pos);
			if (shape.isEmpty()) {
				continue;
			}
			if (body == null && (body = body(mob, height)) == null) {
				return false;
			}
			if (Shapes.joinIsNotEmpty(shape.move(0, j, 0), body, BooleanOp.AND)) {
				return true;
			}
		}
		return false;
	}
}
