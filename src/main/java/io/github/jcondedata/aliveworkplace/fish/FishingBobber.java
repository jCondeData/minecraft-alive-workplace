package io.github.jcondedata.aliveworkplace.fish;

import io.github.jcondedata.aliveworkplace.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The bobber a fisherman villager casts: it floats where they fish, a line runs to their rod (drawn by the client), and
 * it dips when a fish bites. Only for show — the catch comes from {@link FisherWork}. Vanilla's hook can't be used: it
 * belongs to a player and removes itself without one. Never saved; it goes when the fisherman stops fishing.
 */
public class FishingBobber extends Entity {
	private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(FishingBobber.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> BITING = SynchedEntityData.defineId(FishingBobber.class, EntityDataSerializers.BOOLEAN);
	/** Longest a bobber stays out, whatever happens (a cast never lasts this long). */
	private static final int MAX_AGE = 20 * 120;

	public FishingBobber(EntityType<? extends FishingBobber> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	/** Casts a bobber for {@code villager} onto the water at {@code at}. */
	public static FishingBobber cast(ServerLevel level, Villager villager, Vec3 at) {
		FishingBobber bobber = new FishingBobber(ModEntities.FISHING_BOBBER, level);
		bobber.setPos(at);
		bobber.entityData.set(OWNER, villager.getId());
		level.addFreshEntity(bobber);
		return bobber;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(OWNER, 0);
		builder.define(BITING, false);
	}

	/** The fisherman at the other end of the line (null once they're gone). */
	@Nullable
	public Villager owner() {
		return level().getEntity(entityData.get(OWNER)) instanceof Villager villager ? villager : null;
	}

	public boolean biting() {
		return entityData.get(BITING);
	}

	public void setBiting(boolean biting) {
		entityData.set(BITING, biting);
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide()) {
			Villager owner = owner();
			if (owner == null || !owner.isAlive() || owner.distanceToSqr(this) > 16 * 16 || tickCount > MAX_AGE) {
				discard();
			}
		}
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 64 * 64;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}
}
