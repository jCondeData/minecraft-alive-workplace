package io.github.jcondedata.aliveworkplace.furnish;

import io.github.jcondedata.aliveworkplace.registry.ModEntities;
import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * What a villager sits on when they sit on a chair, stool, bench, couch or sofa of a furniture mod (ROADMAP 37.6), as in
 * the Stadium's stands ({@link io.github.jcondedata.aliveworkplace.cup.StandSeat}): an unseen entity on the seat. It's
 * saved, because a passenger is saved only with its vehicle, but a seat that was loaded from a save lets its villager go
 * on its first tick (a villager's seat isn't kept over a save, M37 design note), as does a seat whose block is gone.
 */
public class FurnitureSeat extends Entity {
	/** Where the seat sits above the furniture's block: a villager's hips on the cushion. */
	public static final double HEIGHT = 0.3;

	/** Loaded from a save: stand the villager up. */
	private boolean loaded;

	public FurnitureSeat(EntityType<? extends FurnitureSeat> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	/** A seat on the furniture at {@code pos} (not yet added to the level). */
	public static FurnitureSeat at(ServerLevel level, BlockPos pos) {
		FurnitureSeat seat = new FurnitureSeat(ModEntities.FURNITURE_SEAT, level);
		seat.setPos(pos.getX() + 0.5, pos.getY() + HEIGHT, pos.getZ() + 0.5);
		return seat;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel server && (loaded || getPassengers().isEmpty()
			|| tickCount % 20 == 0 && !server.getBlockState(blockPosition()).is(FurnitureSeats.SEATS))) {
			ejectPassengers();
			discard();
		}
	}

	/** Where a villager stands up to: beside the seat, its front first (chairs face their sitter's way), else on top of it. */
	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		BlockPos pos = blockPosition();
		BlockState state = level().getBlockState(pos);
		Direction front = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? state.getValue(BlockStateProperties.HORIZONTAL_FACING)
			: passenger.getDirection();
		List<BlockPos> spots = new ArrayList<>();
		spots.add(pos.relative(front));
		spots.add(pos.relative(front.getOpposite()));
		spots.add(pos.relative(front.getClockWise()));
		spots.add(pos.relative(front.getCounterClockWise()));
		spots.add(pos.above());
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
		loaded = true;
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}
}
