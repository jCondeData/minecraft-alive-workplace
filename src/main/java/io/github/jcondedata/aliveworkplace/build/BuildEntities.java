package io.github.jcondedata.aliveworkplace.build;

import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.mc.Rules;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;

/**
 * Putting up a blueprint's item frames, paintings and armor stands once the building is finished,
 * each paid for with its item from the supply chests (always empty: see {@code BlueprintEntities}).
 */
public final class BuildEntities {
	/** Puts up whatever isn't there yet; returns how many were left out for lack of materials or a wall. */
	public static int placeAll(ServerLevel level, BuildPlan plan, List<BlockPos> supplies) {
		boolean free = Rules.on(level, ModGameRules.FREE_MATERIALS);
		int left = 0;
		for (BuildPlan.EntityStep step : plan.entities()) {
			if (isPresent(level, step)) {
				continue;
			}
			if (!free && SupplyContainers.count(level, supplies, step.cost()) <= 0) {
				left++;
				continue;
			}
			Entity entity = create(level, step).orElse(null);
			if (entity == null) {
				continue;
			}
			if (entity instanceof HangingEntity hanging && !hanging.survives()) {
				left++; // nothing to hang it on
				continue;
			}
			if (!free) {
				// B132: built in like a block, or the soak's count had an item frame missing
				MaterialLedger.used(step.cost(), SupplyContainers.extract(level, supplies, step.cost(), 1));
			}
			level.addFreshEntity(entity);
			level.playSound(null, entity.blockPosition(), entity instanceof HangingEntity ? SoundEvents.ITEM_FRAME_PLACE : SoundEvents.ARMOR_STAND_PLACE,
				SoundSource.BLOCKS, 1f, 1f);
		}
		return left;
	}

	/** The same kind of entity is already standing (or hanging) where this one goes. */
	public static boolean isPresent(ServerLevel level, BuildPlan.EntityStep step) {
		Optional<EntityType<?>> type = EntityType.byString(Nbt.getString(step.nbt(), "id"));
		if (type.isEmpty()) {
			return true; // nothing we could put up
		}
		AABB around = new AABB(step.pos(), step.pos()).inflate(0.6);
		return !level.getEntities((Entity) null, around, e -> e.getType() == type.get() && e.isAlive()).isEmpty();
	}

	/** The entity, turned to match the placement, at its spot (not yet added to the world). */
	static Optional<Entity> create(ServerLevel level, BuildPlan.EntityStep step) {
		CompoundTag tag = step.nbt().copy();
		ListTag pos = new ListTag();
		pos.add(DoubleTag.valueOf(step.pos().x));
		pos.add(DoubleTag.valueOf(step.pos().y));
		pos.add(DoubleTag.valueOf(step.pos().z));
		tag.put("Pos", pos);
		BlockPos block = BlockPos.containing(step.pos());
		tag.putInt("TileX", block.getX());
		tag.putInt("TileY", block.getY());
		tag.putInt("TileZ", block.getZ());
		Optional<Entity> made;
		try {
			made = EntityType.create(tag, level);
		} catch (RuntimeException e) {
			return Optional.empty();
		}
		made.ifPresent(entity -> {
			// B439: mirror it, then turn it, the order the blocks and the positions go (MaterialRules.transformed,
			// StructureTemplate.transform). A hanging entity's mirror depends on the way it faces, so mirroring
			// after the turn hung a quarter-turned, mirrored frame facing away from its wall.
			float yaw = entity.mirror(step.mirror());
			yaw += entity.rotate(step.rotation()) - entity.getYRot();
			entity.moveTo(step.pos().x, step.pos().y, step.pos().z, yaw, entity.getXRot());
			if (entity instanceof HangingEntity hanging) {
				hangAt(hanging, step.pos());
			}
		});
		return made;
	}

	/**
	 * B439: a hanging entity sits off the block it hangs from (a frame against its wall; a two-wide painting half a
	 * block to one side), so the block holding its centre isn't always that block. This finds the block that puts its
	 * centre, facing the way it now faces, at {@code centre}: the turned, mirrored spot the blueprint hangs it.
	 */
	private static void hangAt(HangingEntity hanging, net.minecraft.world.phys.Vec3 centre) {
		BlockPos probe = hanging.getPos();
		hanging.setPos(net.minecraft.world.phys.Vec3.atCenterOf(probe));
		net.minecraft.world.phys.Vec3 offset = hanging.position().subtract(net.minecraft.world.phys.Vec3.atCenterOf(probe));
		hanging.setPos(net.minecraft.world.phys.Vec3.atCenterOf(BlockPos.containing(centre.subtract(offset))));
	}

	private BuildEntities() {
	}
}
