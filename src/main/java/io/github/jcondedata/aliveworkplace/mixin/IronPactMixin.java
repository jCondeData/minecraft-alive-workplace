package io.github.jcondedata.aliveworkplace.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Under the Ancient Lore's Iron Pact (29.14), a village's iron golems and guards take a quarter less damage: see {@code OldSage.damage}. */
@Mixin(LivingEntity.class)
abstract class IronPactMixin {
	@ModifyVariable(method = "actuallyHurt", at = @At("HEAD"), argsOnly = true)
	private float aliveworkplace$ironPact(float amount) {
		return io.github.jcondedata.aliveworkplace.legend.OldSage.damage((LivingEntity) (Object) this, amount);
	}
}
