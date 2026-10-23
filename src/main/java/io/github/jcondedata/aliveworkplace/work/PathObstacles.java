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
	 * Whether {@code mob} (a villager), standing in cell ({@code x}, {@code y}, {@code z}), would have inside its body a
	 * block the game calls passable for a land path.
	 */
	public static boolean inTheWay(BlockGetter level, Mob mob, int x, int y, int z) {
		if (!(mob instanceof Villager)) {
			return false;
		}
		float height = mob.getBbHeight();
		double half = Math.min(mob.getBbWidth() / 2.0, 0.5);
		double step = Math.max(mob.maxUpStep(), 0.0);
		if (step >= height || half <= 0) {
			return false; // a body (a sleeping or very small one) wholly within what it steps over
		}
		VoxelShape body = Shapes.box(0.5 - half, step, 0.5 - half, 0.5 + half, height, 0.5 + half);
		int cells = Mth.floor(height + 1.0F); // the cells the path finder checks for this mob (NodeEvaluator.prepare)
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int j = 0; j < cells; j++) {
			BlockState state = level.getBlockState(pos.set(x, y + j, z));
			if (state.isAir() || state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock
				|| state.getBlock() instanceof FenceGateBlock || !state.isPathfindable(PathComputationType.LAND)) {
				continue;
			}
			VoxelShape shape = state.getCollisionShape(level, pos);
			if (!shape.isEmpty() && Shapes.joinIsNotEmpty(shape.move(0, j, 0), body, BooleanOp.AND)) {
				return true;
			}
		}
		return false;
	}
}
