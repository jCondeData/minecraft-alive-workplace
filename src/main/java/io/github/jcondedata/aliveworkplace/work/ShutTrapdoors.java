package io.github.jcondedata.aliveworkplace.work;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Shut trapdoors a villager can't walk under (B156). Vanilla's path finder counts every trapdoor as open, shut or not,
 * so it walks a villager into the cell under a window's flower box (a shut top-half trapdoor at head height, with pots
 * on it) or into a cell with a shut top-half trapdoor at its hips (a shelf, a counter). Its head hits the trapdoor, and
 * it stands pressed against it, its path still "on its way", until its walk runs out: a Printer coming up to the Print
 * Shop's door stuck under the flower box beside it for the rest of the day, about 1 time in 100. Now such a cell is
 * blocked to a villager's path, as a wall is, and the path goes round. A shut trapdoor under the feet (a floor, a step)
 * stays walkable, and open ones (shutters, flat against a side) stay open.
 */
public final class ShutTrapdoors {
	private ShutTrapdoors() {
	}

	/**
	 * Whether {@code mob} (a villager), standing in cell ({@code x}, {@code y}, {@code z}), would have a shut trapdoor in
	 * its body: one whose underside is above the cell's floor and below the top of its head.
	 */
	public static boolean inTheWay(BlockGetter level, Mob mob, int x, int y, int z) {
		if (!(mob instanceof Villager)) {
			return false;
		}
		float height = mob.getBbHeight();
		int cells = Mth.floor(height + 1.0F); // the cells the path finder checks for this mob (NodeEvaluator.prepare)
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int j = 0; j < cells; j++) {
			if (shutInTheWay(level, pos.set(x, y + j, z), level.getBlockState(pos), j, height)) {
				return true;
			}
		}
		return false;
	}

	/** Whether {@code state}, in the body's {@code j}-th cell at {@code pos}, is a shut trapdoor in a body {@code height} tall. */
	static boolean shutInTheWay(BlockGetter level, BlockPos pos, BlockState state, int j, float height) {
		if (!(state.getBlock() instanceof TrapDoorBlock) || state.getValue(TrapDoorBlock.OPEN)) {
			return false;
		}
		VoxelShape shape = state.getCollisionShape(level, pos);
		if (shape.isEmpty()) {
			return false;
		}
		double underside = j + shape.min(Direction.Axis.Y);
		return underside > 0 && underside < height;
	}
}
