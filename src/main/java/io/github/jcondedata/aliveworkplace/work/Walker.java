package io.github.jcondedata.aliveworkplace.work;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * How workers (miners, lumberjacks...) get around: walk with the villager's own pathfinding, and hop
 * (a short teleport with a puff) when they make no progress, so they never stand stuck for long.
 * One instance per worker behaviour; it remembers where it is heading and how long it has been trying.
 */
public final class Walker {
	private static final int STUCK_TICKS = 80;
	private static final int MAX_WALK_TICKS = 240;

	private final float speed;
	/** How far below a target a worker may stand to reach it (fruit pickers reach up into trees). */
	private int below = 2;
	@Nullable
	private BlockPos walkingTo;
	private int stuckTicks;
	private int walkTicks;
	private double bestDistance;
	@Nullable
	private BlockPos standSpot;
	private int nudgeTicks;

	public Walker(float speed) {
		this.speed = speed;
	}

	/** Lets the worker reach targets up to {@code blocks} above where it stands (default 2). */
	public Walker reachingUp(int blocks) {
		this.below = blocks;
		return this;
	}

	/** Forget where we were going (a new job, a restart). */
	public void reset() {
		walkingTo = null;
		standSpot = null;
		nudgeTicks = 0;
	}

	/** Walks until the eyes are within {@code reach} of {@code pos} (a chest, a bench). True once there. */
	public boolean walkTo(ServerLevel level, Villager villager, BlockPos pos, double reach) {
		return walk(level, villager, pos, reach, false);
	}

	/**
	 * Gets within {@code reach} of {@code target} to work on it: picks a place to stand (solid ground, room
	 * for a villager), walks there and shuffles to the middle, hopping when stuck. Returns true once in reach;
	 * false while on the way; throws nothing when there is nowhere to stand — {@link #noSpot()} says so.
	 */
	public boolean reach(ServerLevel level, Villager villager, BlockPos target, double reach) {
		if (villager.getEyePosition().distanceTo(Vec3.atCenterOf(target)) <= reach) {
			villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			return true;
		}
		if (standSpot == null || !canStand(level, standSpot) || eyeFrom(standSpot).distanceTo(Vec3.atCenterOf(target)) > reach - 0.3) {
			standSpot = standingSpot(level, target, villager.blockPosition(), reach, below);
			walkingTo = null;
			if (standSpot == null) {
				noSpot = true;
				return false;
			}
		}
		noSpot = false;
		if (walk(level, villager, standSpot, 0.35, true) || villager.blockPosition().equals(standSpot)) {
			// On the spot but leaning out of reach: shuffle to the middle of the block, or hop there.
			villager.getMoveControl().setWantedPosition(standSpot.getX() + 0.5, standSpot.getY(), standSpot.getZ() + 0.5, speed);
			if (++nudgeTicks > 20) {
				hop(level, villager, standSpot);
				nudgeTicks = 0;
			}
		} else {
			nudgeTicks = 0;
		}
		return false;
	}

	private boolean noSpot;

	/** After {@link #reach} returned false: true if there was no place to stand within reach of the target. */
	public boolean noSpot() {
		return noSpot;
	}

	private boolean walk(ServerLevel level, Villager villager, BlockPos pos, double reach, boolean standOn) {
		double distance = standOn ? villager.position().distanceTo(Vec3.atBottomCenterOf(pos)) : villager.getEyePosition().distanceTo(Vec3.atCenterOf(pos));
		if (distance <= reach) {
			villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			walkingTo = null;
			return true;
		}
		if (!pos.equals(walkingTo)) {
			walkingTo = pos;
			stuckTicks = 0;
			walkTicks = 0;
			bestDistance = distance;
		}
		villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(pos, speed, standOn ? 0 : 1));
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(pos));
		if (distance < bestDistance - 0.3) {
			bestDistance = distance;
			stuckTicks = 0;
		}
		if (++stuckTicks > STUCK_TICKS || ++walkTicks > MAX_WALK_TICKS) {
			BlockPos spot = standOn ? pos : standingSpot(level, pos, villager.blockPosition(), reach);
			if (spot != null) {
				hop(level, villager, spot);
			}
			stuckTicks = 0;
			walkTicks = 0;
		}
		return false;
	}

	public static Vec3 eyeFrom(BlockPos feet) {
		return new Vec3(feet.getX() + 0.5, feet.getY() + 1.62, feet.getZ() + 0.5);
	}

	/** Nearest place (to {@code from}) with solid ground and room to stand, from which {@code target} is in reach. */
	@Nullable
	public static BlockPos standingSpot(ServerLevel level, BlockPos target, BlockPos from, double reach) {
		return standingSpot(level, target, from, reach, 2);
	}

	/** {@link #standingSpot(ServerLevel, BlockPos, BlockPos, double)}, looking up to {@code below} blocks under the target. */
	@Nullable
	public static BlockPos standingSpot(ServerLevel level, BlockPos target, BlockPos from, double reach, int below) {
		BlockPos best = null;
		double bestScore = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(target.offset(-4, -below, -4), target.offset(4, 3, 4))) {
			if (p.equals(target) || p.equals(target.below()) || !canStand(level, p)) {
				continue;
			}
			if (eyeFrom(p).distanceTo(Vec3.atCenterOf(target)) > reach - 0.3) {
				continue;
			}
			double score = p.distSqr(from);
			if (score < bestScore) {
				bestScore = score;
				best = p.immutable();
			}
		}
		return best;
	}

	public static boolean canStand(ServerLevel level, BlockPos feet) {
		if (hurts(level.getBlockState(feet))) {
			return false;
		}
		BlockState below = level.getBlockState(feet.below());
		boolean ground = below.isFaceSturdy(level, feet.below(), Direction.UP)
			|| below.getBlock() instanceof net.minecraft.world.level.block.FarmBlock || below.is(net.minecraft.world.level.block.Blocks.DIRT_PATH);
		return ground && below.getFluidState().isEmpty()
			&& level.getBlockState(feet).getCollisionShape(level, feet).isEmpty() && level.getFluidState(feet).isEmpty()
			&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty() && level.getFluidState(feet.above()).isEmpty();
	}

	/** Blocks with no collision that still hurt or trap whoever stands in them. */
	private static boolean hurts(BlockState state) {
		return state.getBlock() instanceof net.minecraft.world.level.block.SweetBerryBushBlock
			|| state.getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock
			|| state.getBlock() instanceof net.minecraft.world.level.block.PowderSnowBlock
			|| state.getBlock() instanceof net.minecraft.world.level.block.WitherRoseBlock
			|| state.getBlock() instanceof net.minecraft.world.level.block.WebBlock;
	}

	public static void hop(ServerLevel level, Villager villager, BlockPos spot) {
		level.sendParticles(ParticleTypes.POOF, villager.getX(), villager.getY() + 0.5, villager.getZ(), 6, 0.2, 0.3, 0.2, 0.01);
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		villager.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
		villager.resetFallDistance();
	}
}
