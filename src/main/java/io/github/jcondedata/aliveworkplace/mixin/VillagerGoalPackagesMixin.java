package io.github.jcondedata.aliveworkplace.mixin;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import io.github.jcondedata.aliveworkplace.build.BuilderPackages;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.VillagerGoalPackages;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gives the Builder profession its own WORK activity (see {@link BuilderPackages}). */
@Mixin(VillagerGoalPackages.class)
abstract class VillagerGoalPackagesMixin {
	@Inject(method = "getWorkPackage", at = @At("HEAD"), cancellable = true)
	private static void aliveworkplace$builderWorkPackage(VillagerProfession profession, float speed,
			CallbackInfoReturnable<ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>>> cir) {
		if (profession == ModVillagers.BUILDER) {
			cir.setReturnValue(BuilderPackages.work(speed));
		} else if (profession == ModVillagers.MINER) {
			cir.setReturnValue(io.github.jcondedata.aliveworkplace.mine.MinerPackages.work(speed));
		}
	}
}
