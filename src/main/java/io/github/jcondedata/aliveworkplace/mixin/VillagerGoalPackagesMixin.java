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
		} else if (profession == ModVillagers.BALL_SMITH) {
			cir.setReturnValue(io.github.jcondedata.aliveworkplace.smith.BallSmithPackages.work(speed));
		} else if (profession == ModVillagers.RANCHER) {
			cir.setReturnValue(io.github.jcondedata.aliveworkplace.ranch.RancherPackages.work(speed));
		} else if (profession == ModVillagers.FLORIST) {
			cir.setReturnValue(io.github.jcondedata.aliveworkplace.flower.FloristPackages.work(speed));
		} else if (profession == ModVillagers.BEEKEEPER) {
			cir.setReturnValue(io.github.jcondedata.aliveworkplace.bee.BeekeeperPackages.work(speed));
		} else if (profession == ModVillagers.ORCHARD_KEEPER) {
			cir.setReturnValue(io.github.jcondedata.aliveworkplace.orchard.OrchardPackages.work(speed));
		} else if (profession == ModVillagers.FOSSIL_SCIENTIST) {
			cir.setReturnValue(io.github.jcondedata.aliveworkplace.fossil.FossilPackages.work(speed));
		} else if (profession == ModVillagers.CHEF) {
			cir.setReturnValue(io.github.jcondedata.aliveworkplace.craft.CarpenterPackages.chef(speed));
		} else if (profession == ModVillagers.CARPENTER) {
			cir.setReturnValue(io.github.jcondedata.aliveworkplace.craft.CarpenterPackages.work(speed));
		} else if (profession == ModVillagers.PORTER) {
			cir.setReturnValue(io.github.jcondedata.aliveworkplace.store.PorterPackages.work(speed));
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
			cir.setReturnValue(io.github.jcondedata.aliveworkplace.work.DeskPackages.work(speed, io.github.jcondedata.aliveworkplace.tutor.Tutors::title,
				v -> net.minecraft.network.chat.Component.translatable("message.aliveworkplace.tutor.state",
					v.getAttachedOrElse(io.github.jcondedata.aliveworkplace.registry.ModAttachments.TUTOR_LESSONS, 0))));
		} else if (profession == ModVillagers.POKEMON_TRADER) {
			cir.setReturnValue(io.github.jcondedata.aliveworkplace.work.DeskPackages.work(speed, io.github.jcondedata.aliveworkplace.trader.PokemonTraders::title,
				v -> net.minecraft.network.chat.Component.translatable("message.aliveworkplace.pokemon_trader.state",
					v.getAttachedOrElse(io.github.jcondedata.aliveworkplace.registry.ModAttachments.POKEMON_TRADE_COUNT, 0))));
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
		} else if (profession == VillagerProfession.MASON) {
			// Masons cut stone for the builders nearby when they're waiting for it, and go about their day otherwise.
			cir.setReturnValue(io.github.jcondedata.aliveworkplace.work.UpgradedJob.work(cir.getReturnValue(),
				new io.github.jcondedata.aliveworkplace.craft.MasonWork(),
				io.github.jcondedata.aliveworkplace.craft.CrafterWork::vanillaMayRun));
		} else if (profession == VillagerProfession.ARMORER) {
			// Armorers smelt the village's ore at their blast furnace, and go about their day when there's none.
			cir.setReturnValue(io.github.jcondedata.aliveworkplace.work.UpgradedJob.work(cir.getReturnValue(),
				new io.github.jcondedata.aliveworkplace.smelt.SmelterWork(), io.github.jcondedata.aliveworkplace.smelt.Smelters::vanillaMayRun));
		} else if (profession == VillagerProfession.TOOLSMITH) {
			// Toolsmiths make the tools the village's workers are waiting for.
			cir.setReturnValue(io.github.jcondedata.aliveworkplace.work.UpgradedJob.work(cir.getReturnValue(),
				new io.github.jcondedata.aliveworkplace.craft.ToolsmithWork(), io.github.jcondedata.aliveworkplace.craft.CrafterWork::vanillaMayRun));
		} else if (profession == VillagerProfession.WEAPONSMITH) {
			// Weaponsmiths make swords for the guards and mend the village's worn gear.
			cir.setReturnValue(io.github.jcondedata.aliveworkplace.work.UpgradedJob.work(cir.getReturnValue(),
				new io.github.jcondedata.aliveworkplace.craft.WeaponsmithWork(), new io.github.jcondedata.aliveworkplace.mend.MendingWork(),
				v -> io.github.jcondedata.aliveworkplace.craft.CrafterWork.vanillaMayRun(v) && !io.github.jcondedata.aliveworkplace.mend.MendingWork.isBusy(v)));
		} else if (profession == VillagerProfession.FLETCHER) {
			// Fletchers make bows and spectral arrows for the guards.
			cir.setReturnValue(io.github.jcondedata.aliveworkplace.work.UpgradedJob.work(cir.getReturnValue(),
				new io.github.jcondedata.aliveworkplace.craft.FletcherWork(), io.github.jcondedata.aliveworkplace.craft.CrafterWork::vanillaMayRun));
		} else if (profession == VillagerProfession.SHEPHERD) {
			// Shepherds shear and breed the sheep around their loom.
			cir.setReturnValue(io.github.jcondedata.aliveworkplace.work.UpgradedJob.work(cir.getReturnValue(),
				new io.github.jcondedata.aliveworkplace.ranch.ShepherdWork(), v -> !io.github.jcondedata.aliveworkplace.ranch.RanchWork.isBusy(v)));
		} else if (profession == VillagerProfession.BUTCHER) {
			// Butchers look after the cows, pigs, chickens and rabbits around their smoker.
			cir.setReturnValue(io.github.jcondedata.aliveworkplace.work.UpgradedJob.work(cir.getReturnValue(),
				new io.github.jcondedata.aliveworkplace.ranch.HerderWork(), v -> !io.github.jcondedata.aliveworkplace.ranch.RanchWork.isBusy(v)));
		} else if (profession == VillagerProfession.LEATHERWORKER) {
			// Leatherworkers dye what the builders nearby are waiting for.
			cir.setReturnValue(io.github.jcondedata.aliveworkplace.work.UpgradedJob.work(cir.getReturnValue(),
				new io.github.jcondedata.aliveworkplace.craft.DyerWork(), io.github.jcondedata.aliveworkplace.craft.CrafterWork::vanillaMayRun));
		} else if (profession == VillagerProfession.CLERIC) {
			// Clerics brew potions for the guards at their brewing stand.
			cir.setReturnValue(io.github.jcondedata.aliveworkplace.work.UpgradedJob.work(cir.getReturnValue(),
				new io.github.jcondedata.aliveworkplace.brew.AlchemistWork(), v -> !io.github.jcondedata.aliveworkplace.brew.AlchemistWork.isBusy(v)));
		} else if (profession == VillagerProfession.LIBRARIAN) {
			// Librarians make books for the builders and enchant the village's gear.
			cir.setReturnValue(io.github.jcondedata.aliveworkplace.work.UpgradedJob.work(cir.getReturnValue(),
				new io.github.jcondedata.aliveworkplace.craft.ScribeWork(), new io.github.jcondedata.aliveworkplace.scribe.EnchantWork(),
				v -> io.github.jcondedata.aliveworkplace.craft.CrafterWork.vanillaMayRun(v) && !io.github.jcondedata.aliveworkplace.scribe.EnchantWork.isBusy(v)));
		} else if (profession == VillagerProfession.CARTOGRAPHER) {
			// Cartographers go out exploring from their cartography table and bring back what they find.
			cir.setReturnValue(io.github.jcondedata.aliveworkplace.work.UpgradedJob.work(cir.getReturnValue(),
				new io.github.jcondedata.aliveworkplace.explore.ExplorerWork(), v -> !io.github.jcondedata.aliveworkplace.explore.ExplorerWork.isBusy(v)));
		} else if (profession == VillagerProfession.FISHERMAN) {
			cir.setReturnValue(io.github.jcondedata.aliveworkplace.work.UpgradedJob.work(cir.getReturnValue(),
				new io.github.jcondedata.aliveworkplace.fish.FisherWork(), io.github.jcondedata.aliveworkplace.fish.Fishers::vanillaMayRun));
		}
	}
}
