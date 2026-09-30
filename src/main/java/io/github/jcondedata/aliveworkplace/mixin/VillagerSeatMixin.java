package io.github.jcondedata.aliveworkplace.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Villagers sit in a saddle or a boat like players instead of standing on it (a guard on horseback, a ferryman at the
 * oars, a fisher in a boat): the game gives villagers no riding height, so their feet went where a player's hips go.
 * The model's sitting pose is in the client's {@code VillagerModelMixin}.
 *
 * <p>The mixin extends the villager's parent class so that {@link #getVehicleAttachmentPoint} is a real override of
 * {@code Entity}'s: the remapper then renames it with the game's method in production, where a plain method with the
 * development name would override nothing.
 */
@Mixin(Villager.class)
abstract class VillagerSeatMixin extends AbstractVillager {
	/** Where a grown villager's hips are, from their feet (a player's riding offset). */
	private static final Vec3 SEATED = new Vec3(0, 0.6, 0);

	private VillagerSeatMixin(EntityType<? extends AbstractVillager> type, Level level) {
		super(type, level);
	}

	@Override
	public Vec3 getVehicleAttachmentPoint(Entity vehicle) {
		return isBaby() ? SEATED.scale(0.5) : SEATED;
	}
}
