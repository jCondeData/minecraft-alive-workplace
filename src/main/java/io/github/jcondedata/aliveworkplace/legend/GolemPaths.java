package io.github.jcondedata.aliveworkplace.legend;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.NodeEvaluator;

/**
 * What a working golem's paths keep clear of beyond vanilla's rules (asked by {@code WalkNodeEvaluatorMixin} while
 * {@link GolemRoleGoal} holds the golem at work).
 *
 * <p>Sunken lava (B814). Vanilla marks a place to stand dangerous when lava or fire is beside it within a block up or
 * down, so a trench whose lava lies a block or more below the ground looked like a plain drop: the golem's path ran
 * along its edge, overhanging it, and turning round the trench's end the golem cut the corner and fell in. Now a place
 * whose footing, or a footing beside it, is open air above lava or fire within {@link #SUNKEN} blocks counts as next to
 * the lava, so the path keeps a block clear of it, as it does of lava level with the ground (B784).
 *
 * <p>Water too low to leave (B813). Vanilla lets a path drop up to three blocks into water. A floating golem's feet are
 * half a block above the top water block, and it climbs out onto a bank at most {@link GolemRoleGoal#CLIMB} above them,
 * so water whose surface is two blocks below the bank is water it can't leave there: it dropped in after a Storehouse on
 * an island, floated by the banks and gave up every Storehouse with its load. Now its path never drops into water it
 * couldn't climb back out of at that spot; it goes in where the bank is low enough, or not at all.
 */
public final class GolemPaths {
	/** How far below a golem's footing (blocks) lava still counts as beside it: as far as a path drops, and one more. */
	static final int SUNKEN = 4;
	/** Golems at work (whose paths follow these rules). */
	private static final Set<IronGolem> AT_WORK = Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<>()));

	private GolemPaths() {
	}

	static void atWork(IronGolem golem, boolean working) {
		if (working) {
			AT_WORK.add(golem);
		} else {
			AT_WORK.remove(golem);
		}
	}

	/** Whether {@code mob} is a golem at work, whose paths keep clear of sunken lava and of water too low to leave. */
	public static boolean atWork(Mob mob) {
		return mob instanceof IronGolem golem && AT_WORK.contains(golem);
	}

	/**
	 * Whether the place ({@code x}, {@code y}, {@code z}) for {@code mob} (its path cell, the corner of a footprint as wide as
	 * the path finder makes it) has open air under its footprint, or beside it, with lava or fire below within {@link #SUNKEN}
	 * blocks: lava a golem standing there could slip or fall into.
	 */
	public static boolean besideSunkenFire(BlockGetter level, Mob mob, int x, int y, int z) {
		int width = Mth.floor(mob.getBbWidth() + 1.0F); // the footprint the path finder checks (NodeEvaluator.prepare)
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int cx = x - 1; cx <= x + width; cx++) {
			for (int cz = z - 1; cz <= z + width; cz++) {
				if (!open(level, pos.set(cx, y - 1, cz))) {
					continue; // ground (vanilla already looks at lava level with it)
				}
				for (int cy = y - 2; cy >= y - 1 - SUNKEN; cy--) {
					BlockState state = level.getBlockState(pos.set(cx, cy, cz));
					if (state.getFluidState().is(FluidTags.LAVA) || NodeEvaluator.isBurningBlock(state)) {
						return true;
					}
					if (!open(level, pos)) {
						break;
					}
				}
			}
		}
		return false;
	}

	/**
	 * Whether a path dropping from a place at height {@code from} onto the top water block at {@code waterY} goes into water
	 * the golem couldn't climb back out of there: the bank more than {@link GolemRoleGoal#CLIMB} above a floating golem's
	 * feet, half a block above that water block.
	 */
	public static boolean tooLowToLeave(int from, int waterY) {
		return from - (waterY + 0.5) > GolemRoleGoal.CLIMB;
	}

	/** Air or another block nothing stands on, without a liquid: a place one falls through. */
	private static boolean open(BlockGetter level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty();
	}
}
