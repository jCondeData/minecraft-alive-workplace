package io.github.jcondedata.aliveworkplace.cup;

import io.github.jcondedata.aliveworkplace.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * What a villager sits on in the stands of a Cup's Arena (ROADMAP 28.19), as villagers sit in saddles and boats: an
 * unseen entity on a bench. It's saved (a passenger is saved only with its vehicle), and lets the villager go once the
 * stands empty after the final, at the end of the Cup's day, or with the Cup off.
 */
public class StandSeat extends Entity {
	/** Where the seat sits above the bench's block: the villager's hips on the stair. */
	public static final double HEIGHT = 0.3;

	public StandSeat(EntityType<? extends StandSeat> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	/** A seat on the bench at {@code bench} (not yet added to the level). */
	public static StandSeat at(ServerLevel level, BlockPos bench) {
		StandSeat seat = new StandSeat(ModEntities.STAND_SEAT, level);
		seat.setPos(bench.getX() + 0.5, bench.getY() + HEIGHT, bench.getZ() + 0.5);
		return seat;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel server && (tickCount % 20 == 0 || getPassengers().isEmpty())
			&& (getPassengers().isEmpty() || !CupDays.standsOpen(server, blockPosition()))) {
			ejectPassengers();
			discard();
		}
	}

	/**
	 * Where a villager stands up to: a free spot in front of the bench (the aisle, or the row below), else on top of it,
	 * else beside it. Not on the bench itself: the seat sits on a stair, whose back step would hold the villager inside
	 * it (B94: Cup spectators flagged stuck in the stands after the final).
	 */
	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		BlockPos bench = blockPosition();
		BlockState state = level().getBlockState(bench);
		Direction front = state.hasProperty(StairBlock.FACING) ? state.getValue(StairBlock.FACING).getOpposite() : passenger.getDirection();
		java.util.List<BlockPos> spots = new java.util.ArrayList<>();
		spots.add(bench.relative(front));
		spots.add(bench.relative(front).below());
		spots.add(bench.above());
		spots.add(bench.relative(front.getClockWise()));
		spots.add(bench.relative(front.getCounterClockWise()));
		spots.add(bench.relative(front.getOpposite()).above());
		for (BlockPos spot : spots) {
			Vec3 at = DismountHelper.findSafeDismountLocation(passenger.getType(), level(), spot, true);
			if (at != null) {
				return at;
			}
		}
		return super.getDismountLocationForPassenger(passenger);
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}
}
