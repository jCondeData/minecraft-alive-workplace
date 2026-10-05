package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * What a child sits in while asleep in a cradle at night (30.12), as villagers sit in saddles and boats: an unseen
 * entity on the cradle. It's saved (a passenger is saved only with its vehicle, so an unsaved seat would lose the
 * child), and lets the child go at dawn, when the cradle is broken, or when the switch is off.
 */
public class CradleSeat extends Entity {
	/** Where the seat sits above the cradle's block: the child's legs under the blanket. */
	public static final double HEIGHT = 0.45;

	public CradleSeat(EntityType<? extends CradleSeat> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	/** A seat on the cradle at {@code cradle} (not yet added to the level). */
	public static CradleSeat at(ServerLevel level, BlockPos cradle) {
		CradleSeat seat = new CradleSeat(ModEntities.CRADLE_SEAT, level);
		seat.setPos(cradle.getX() + 0.5, cradle.getY() + HEIGHT, cradle.getZ() + 0.5);
		return seat;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide() && (tickCount % 20 == 0 || getPassengers().isEmpty()) && !keep()) {
			ejectPassengers();
			discard();
		}
	}

	/** Whether the seat still has a child in a standing cradle at night. */
	boolean keep() {
		return Cradles.ENABLED && !getPassengers().isEmpty() && level().getBlockState(blockPosition()).is(ModBlocks.CRADLE) && Curfew.isNight(level());
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
