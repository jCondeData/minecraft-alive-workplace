package io.github.jcondedata.aliveworkplace.mixin;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * A mob's drop chance for one slot, so a conscript's held item keeps its own when it moves hands (30.10), and its
 * goals, so a siege's ram can be held at the gate (32.4).
 */
@Mixin(Mob.class)
public interface MobAccessor {
	@Invoker("getEquipmentDropChance")
	float aliveworkplace$dropChance(EquipmentSlot slot);

	/** A mob's goals: a siege's ram is held at the gate (32.4), a caravan's pack llama is left with none but to follow its lead (33.7). */
	@Accessor("goalSelector")
	GoalSelector aliveworkplace$goals();

	@Accessor("targetSelector")
	GoalSelector aliveworkplace$targets();
}
