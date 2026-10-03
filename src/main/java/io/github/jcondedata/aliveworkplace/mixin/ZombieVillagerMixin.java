package io.github.jcondedata.aliveworkplace.mixin;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A zombie villager placed by a structure template (an abandoned village's house) gets room to stand, as villagers do (B10). */
@Mixin(ZombieVillager.class)
public abstract class ZombieVillagerMixin {
	@Inject(method = "finalizeSpawn", at = @At("HEAD"))
	private void aliveworkplace$structureSpot(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType type, SpawnGroupData data,
			CallbackInfoReturnable<SpawnGroupData> cir) {
		io.github.jcondedata.aliveworkplace.world.StructureVillagers.settle((ZombieVillager) (Object) this, level, type);
	}
}
