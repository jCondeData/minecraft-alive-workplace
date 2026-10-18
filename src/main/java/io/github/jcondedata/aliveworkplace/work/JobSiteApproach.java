package io.github.jcondedata.aliveworkplace.work;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The walk to a workstation a villager has picked, when the workstation is built in under solid blocks (B168): a smoker
 * as the mouth of a brick oven, a lectern under a shelf. Vanilla walks the villager to its pick with a walk target on the
 * block itself, and ground navigation lifts a target on a solid block to the first open block above it: here up through
 * the bricks into the roof. The villager then walks to whatever spot is nearest that, often the top of a chest or a
 * worktop beside it, a step more than the two blocks from which it takes the job, and stands there until its pick runs
 * out. Instead it is walked to the open floor beside the workstation (the side it faces first), from where it takes it.
 */
public final class JobSiteApproach {
	private JobSiteApproach() {
	}

	/** After vanilla's GoToPotentialJobSite set its walk target: aimed at the floor beside a built-in workstation. */
	public static void retarget(Villager villager, float speed) {
		Optional<GlobalPos> site = villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
		if (site.isEmpty() || site.get().dimension() != villager.level().dimension()) {
			return;
		}
		BlockPos pos = site.get().pos();
		approach(villager.level(), pos, villager.blockPosition()).ifPresent(spot ->
			villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new BlockPosTracker(spot), speed, 0)));
	}

	/**
	 * Where to stand to reach the workstation at {@code pos}, when vanilla's path to it would be lifted away (the block and
	 * the one above it solid): the open floor beside it, the side it faces first, then the nearest to {@code from}. Empty
	 * when vanilla's walk is right (nothing solid over it) or there's no such floor (vanilla's walk is kept).
	 */
	public static Optional<BlockPos> approach(Level level, BlockPos pos, BlockPos from) {
		BlockState state = level.getBlockState(pos);
		if (!state.isSolid() || !level.getBlockState(pos.above()).isSolid()) {
			return Optional.empty();
		}
		List<Direction> sides = new ArrayList<>(Direction.Plane.HORIZONTAL.stream().toList());
		sides.sort(java.util.Comparator.comparingDouble(d -> pos.relative(d).distSqr(from)));
		if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
			Direction front = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
			sides.remove(front);
			sides.add(0, front);
		}
		for (Direction side : sides) {
			BlockPos spot = pos.relative(side);
			if (open(level, spot) && open(level, spot.above()) && !level.getBlockState(spot.below()).getCollisionShape(level, spot.below()).isEmpty()) {
				return Optional.of(spot);
			}
		}
		return Optional.empty();
	}

	private static boolean open(Level level, BlockPos pos) {
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getFluidState(pos).isEmpty();
	}
}
