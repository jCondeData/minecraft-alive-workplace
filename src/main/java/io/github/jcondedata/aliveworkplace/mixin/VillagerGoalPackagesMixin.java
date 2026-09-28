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
		} else if (profession == ModVillagers.LUMBERJACK) {
			cir.setReturnValue(io.github.jcondedata.aliveworkplace.wood.LumberjackPackages.work(speed));
		} else if (profession == ModVillagers.POSTMAN) {
			cir.setReturnValue(io.github.jcondedata.aliveworkplace.mail.PostmanPackages.work(speed));
		} else if (profession == ModVillagers.GUARD) {
			cir.setReturnValue(io.github.jcondedata.aliveworkplace.guard.GuardPackages.work(speed));
		} else if (profession == ModVillagers.NURSE) {
			cir.setReturnValue(io.github.jcondedata.aliveworkplace.nurse.NursePackages.work(speed));
		} else if (profession == ModVillagers.SHOPKEEPER) {
			cir.setReturnValue(io.github.jcondedata.aliveworkplace.shop.ShopkeeperPackages.work(speed));
		} else if (profession == ModVillagers.FERRYMAN) {
			cir.setReturnValue(io.github.jcondedata.aliveworkplace.travel.FerrymanPackages.work(speed));
		} else if (profession == ModVillagers.BARD) {
			cir.setReturnValue(io.github.jcondedata.aliveworkplace.bard.BardPackages.work(speed));
		} else if (profession == ModVillagers.TRAINER || profession == ModVillagers.TRAINER_LEADER) {
			cir.setReturnValue(io.github.jcondedata.aliveworkplace.trainer.TrainerPackages.work(speed));
		} else if (profession == ModVillagers.TUTOR) {
			cir.setReturnValue(io.github.jcondedata.aliveworkplace.tutor.TutorPackages.work(speed));
		}
	}

	/** Guards fight in every activity, so their combat goes in CORE. */
	@Inject(method = "getCorePackage", at = @At("RETURN"), cancellable = true)
	private static void aliveworkplace$guardCorePackage(VillagerProfession profession, float speed,
			CallbackInfoReturnable<ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>>> cir) {
		if (profession == ModVillagers.GUARD) {
			cir.setReturnValue(io.github.jcondedata.aliveworkplace.guard.GuardPackages.core(cir.getReturnValue()));
		}
	}

	/** Farmers keep their vanilla routine, paused while they have a field that needs work (see {@code Fields}). */
	@Inject(method = "getWorkPackage", at = @At("RETURN"), cancellable = true)
	private static void aliveworkplace$farmerWorkPackage(VillagerProfession profession, float speed,
			CallbackInfoReturnable<ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>>> cir) {
		if (profession == VillagerProfession.FARMER) {
			cir.setReturnValue(io.github.jcondedata.aliveworkplace.farm.FarmerPackages.work(cir.getReturnValue()));
		} else if (profession == VillagerProfession.FISHERMAN) {
			cir.setReturnValue(io.github.jcondedata.aliveworkplace.work.UpgradedJob.work(cir.getReturnValue(),
				new io.github.jcondedata.aliveworkplace.fish.FisherWork(), io.github.jcondedata.aliveworkplace.fish.Fishers::vanillaMayRun));
		}
	}
}
