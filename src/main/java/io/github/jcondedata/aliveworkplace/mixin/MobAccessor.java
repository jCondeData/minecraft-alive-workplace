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

	/** A mob's goals, so a caravan's pack llama can be left with none but to follow its lead (33.7). */
	@org.spongepowered.asm.mixin.gen.Accessor("goalSelector")
	net.minecraft.world.entity.ai.goal.GoalSelector aliveworkplace$goals();

	@org.spongepowered.asm.mixin.gen.Accessor("targetSelector")
	net.minecraft.world.entity.ai.goal.GoalSelector aliveworkplace$targets();
}
