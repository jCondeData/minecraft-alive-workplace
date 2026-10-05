package io.github.jcondedata.aliveworkplace.mixin;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A mob's drop chance for one slot, so a conscript's held item keeps its own when it moves hands (30.10). */
@Mixin(Mob.class)
public interface MobAccessor {
	@Invoker("getEquipmentDropChance")
	float aliveworkplace$dropChance(EquipmentSlot slot);
}
