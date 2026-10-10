package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Builders keep longer hours than other villagers (see {@link ModVillagers#BUILDER_SCHEDULE}). */
@Mixin(Villager.class)
abstract class VillagerMixin {
	/** A shopkeeper's offers are whatever the shop has in stock right now. */
	@Inject(method = "mobInteract", at = @At("HEAD"))
	private void aliveworkplace$shopStock(net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
			org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.world.InteractionResult> cir) {
		Villager self = (Villager) (Object) this;
		if (self.level() instanceof net.minecraft.server.level.ServerLevel level && !self.isTrading() && !self.isSleeping()) {
			if (io.github.jcondedata.aliveworkplace.shop.Shops.isShopkeeper(self)) {
				io.github.jcondedata.aliveworkplace.shop.Shops.refreshOffers(level, self);
			} else if (io.github.jcondedata.aliveworkplace.travel.Ferrymen.isFerryman(self) && player instanceof net.minecraft.server.level.ServerPlayer sp) {
				io.github.jcondedata.aliveworkplace.travel.Ferrymen.refreshOffers(level, self, sp);
			}
		}
	}

	/** The Tithe edict's higher emerald prices, after vanilla's reputation and Hero discounts (30.8, {@code hall/Tithe}). */
	@Inject(method = "updateSpecialPrices", at = @At("TAIL"))
	private void aliveworkplace$tithePrices(net.minecraft.world.entity.player.Player player, CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		if (self.level() instanceof net.minecraft.server.level.ServerLevel) {
			io.github.jcondedata.aliveworkplace.hall.Tithe.prices(self);
		}
	}

	/** A netherworker away in the Nether: vanilla's portals leave them be, and the brain waits till they're back. */
	@Inject(method = "customServerAiStep", at = @At("HEAD"), cancellable = true)
	private void aliveworkplace$nether(CallbackInfo ci) {
		io.github.jcondedata.aliveworkplace.guard.Mercenaries.tick((Villager) (Object) this);
		io.github.jcondedata.aliveworkplace.work.Stations.retakeHive((Villager) (Object) this);
		io.github.jcondedata.aliveworkplace.work.Stations.takeHouseBlock((Villager) (Object) this);
		io.github.jcondedata.aliveworkplace.work.JobSiteTickets.tick((Villager) (Object) this);
		io.github.jcondedata.aliveworkplace.city.Stewards.tick((Villager) (Object) this);
		io.github.jcondedata.aliveworkplace.work.WorkerLimits.tick((Villager) (Object) this);
		io.github.jcondedata.aliveworkplace.legend.Legends.tick((Villager) (Object) this);
		io.github.jcondedata.aliveworkplace.legend.Gifted.tick((Villager) (Object) this);
		if (((Villager) (Object) this).isRemoved()) {
			ci.cancel();
			return;
		}
		if (io.github.jcondedata.aliveworkplace.nether.Netherworkers.tick((Villager) (Object) this)) {
			ci.cancel();
		}
	}

	/** While a villager's brain runs, its search for a free workstation skips full villages (25.5, see {@code WorkerLimits}). */
	@Inject(method = "customServerAiStep", at = @At("HEAD"))
	private void aliveworkplace$thinking(CallbackInfo ci) {
		io.github.jcondedata.aliveworkplace.work.WorkerLimits.thinking((Villager) (Object) this);
	}

	@Inject(method = "customServerAiStep", at = @At("RETURN"))
	private void aliveworkplace$thought(CallbackInfo ci) {
		io.github.jcondedata.aliveworkplace.work.WorkerLimits.thinking(null);
		io.github.jcondedata.aliveworkplace.work.BedLadders.tick((Villager) (Object) this); // up a ladder to a bed upstairs (B79)
		io.github.jcondedata.aliveworkplace.legend.Pathfinder.hold((Villager) (Object) this); // after the brain: waiting stays put
	}

	/** A villager placed by a structure template gets room to stand (B6). */
	@Inject(method = "finalizeSpawn", at = @At("HEAD"))
	private void aliveworkplace$structureSpot(net.minecraft.world.level.ServerLevelAccessor level, net.minecraft.world.DifficultyInstance difficulty,
			net.minecraft.world.entity.MobSpawnType type, net.minecraft.world.entity.SpawnGroupData data,
			org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.world.entity.SpawnGroupData> cir) {
		io.github.jcondedata.aliveworkplace.world.StructureVillagers.settle((Villager) (Object) this, level, type);
	}

	/** Under Curfew nobody trades with players from dusk to dawn (30.9, {@code hall/Curfew}). */
	@Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
	private void aliveworkplace$curfew(net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
			org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.world.InteractionResult> cir) {
		Villager self = (Villager) (Object) this;
		if (!player.getItemInHand(hand).is(net.minecraft.world.item.Items.VILLAGER_SPAWN_EGG) && io.github.jcondedata.aliveworkplace.hall.Curfew.refuseTrade(self, player)) {
			cir.setReturnValue(net.minecraft.world.InteractionResult.CONSUME);
		}
	}

	/** Nobody trades with a netherworker who's away. */
	@Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
	private void aliveworkplace$away(net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
			org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.world.InteractionResult> cir) {
		if (io.github.jcondedata.aliveworkplace.nether.Netherworkers.isAway((Villager) (Object) this)) {
			cir.setReturnValue(net.minecraft.world.InteractionResult.PASS);
		}
	}

	/** A baby had the vanilla way remembers its parents. */
	@Inject(method = "getBreedOffspring(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/AgeableMob;)Lnet/minecraft/world/entity/npc/Villager;",
		at = @At("RETURN"))
	private void aliveworkplace$parents(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.AgeableMob partner,
			org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Villager> cir) {
		if (cir.getReturnValue() != null && partner instanceof Villager other) {
			io.github.jcondedata.aliveworkplace.people.Families.born(cir.getReturnValue(), (Villager) (Object) this, other);
		}
	}

	/** Set when a villager who went to school is taking their first job: they start a level up once it's set. */
	@org.spongepowered.asm.mixin.Unique
	private boolean aliveworkplace$headStart;

	/** A jobless villager doesn't take a free workstation in a village that has its workers (25.5, {@code maxWorkersPerVillage}). */
	@Inject(method = "setVillagerData", at = @At("HEAD"), cancellable = true)
	private void aliveworkplace$workerCap(net.minecraft.world.entity.npc.VillagerData data, CallbackInfo ci) {
		if (io.github.jcondedata.aliveworkplace.work.WorkerLimits.refuse((Villager) (Object) this, data)) {
			ci.cancel();
		}
	}

	/** A jobless villager at a Healing Machine takes Cobblemon's nurse job, not ours (28.7: ours only by a honey bottle). */
	@Inject(method = "setVillagerData", at = @At("HEAD"), cancellable = true)
	private void aliveworkplace$onlyByItem(net.minecraft.world.entity.npc.VillagerData data, CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		net.minecraft.world.entity.npc.VillagerProfession instead = io.github.jcondedata.aliveworkplace.work.Stations.insteadByItself(self, data);
		if (instead != null) {
			ci.cancel();
			self.setVillagerData(data.setProfession(instead));
		}
	}

	@Inject(method = "setVillagerData", at = @At("HEAD"))
	private void aliveworkplace$schoolBefore(net.minecraft.world.entity.npc.VillagerData data, CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		aliveworkplace$headStart = !self.level().isClientSide() && self.tickCount > 0
			&& io.github.jcondedata.aliveworkplace.school.Schools.startsAhead(self, self.getVillagerData(), data);
	}

	@Inject(method = "setVillagerData", at = @At("TAIL"))
	private void aliveworkplace$schoolAfter(net.minecraft.world.entity.npc.VillagerData data, CallbackInfo ci) {
		if (aliveworkplace$headStart) {
			aliveworkplace$headStart = false;
			io.github.jcondedata.aliveworkplace.school.Schools.headStart((Villager) (Object) this);
		}
	}

	/** Every trade's WORK package (the first activity added with conditions) starts with a Legend's picket (29.5). */
	@org.spongepowered.asm.mixin.injection.ModifyArg(method = "registerBrainGoals", at = @At(value = "INVOKE", ordinal = 0,
		target = "Lnet/minecraft/world/entity/ai/Brain;addActivityWithConditions(Lnet/minecraft/world/entity/schedule/Activity;Lcom/google/common/collect/ImmutableList;Ljava/util/Set;)V"),
		index = 1)
	private com.google.common.collect.ImmutableList<com.mojang.datafixers.util.Pair<Integer, ? extends net.minecraft.world.entity.ai.behavior.BehaviorControl<? super Villager>>> aliveworkplace$picketAtWork(
			com.google.common.collect.ImmutableList<com.mojang.datafixers.util.Pair<Integer, ? extends net.minecraft.world.entity.ai.behavior.BehaviorControl<? super Villager>>> work) {
		return io.github.jcondedata.aliveworkplace.legend.Picket.work(work);
	}

	/** And the IDLE package (the fourth activity added plainly: after PLAY, CORE and REST), for a Legend with no workstation. */
	@org.spongepowered.asm.mixin.injection.ModifyArg(method = "registerBrainGoals", at = @At(value = "INVOKE", ordinal = 3,
		target = "Lnet/minecraft/world/entity/ai/Brain;addActivity(Lnet/minecraft/world/entity/schedule/Activity;Lcom/google/common/collect/ImmutableList;)V"),
		index = 1)
	private com.google.common.collect.ImmutableList<com.mojang.datafixers.util.Pair<Integer, ? extends net.minecraft.world.entity.ai.behavior.BehaviorControl<? super Villager>>> aliveworkplace$picketIdle(
			com.google.common.collect.ImmutableList<com.mojang.datafixers.util.Pair<Integer, ? extends net.minecraft.world.entity.ai.behavior.BehaviorControl<? super Villager>>> idle) {
		return io.github.jcondedata.aliveworkplace.legend.Picket.idle(idle);
	}

	@Inject(method = "registerBrainGoals", at = @At("TAIL"))
	private void aliveworkplace$builderSchedule(Brain<Villager> brain, CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		if (!self.isBaby() && (ModVillagers.isWorker(self.getVillagerData().getProfession())
			|| io.github.jcondedata.aliveworkplace.farm.Fields.isFarmer(self) && io.github.jcondedata.aliveworkplace.farm.Fields.hasField(self)
			|| io.github.jcondedata.aliveworkplace.fish.Fishers.isFisherman(self) && io.github.jcondedata.aliveworkplace.fish.Fishers.isHired(self))) {
			brain.setSchedule(ModVillagers.BUILDER_SCHEDULE);
			brain.updateActivityFromSchedule(self.level().getDayTime(), self.level().getGameTime());
		} else if (!self.isBaby() && self.getVillagerData().getProfession() == ModVillagers.GUARD) {
			brain.setSchedule(ModVillagers.GUARD_SCHEDULE);
			brain.updateActivityFromSchedule(self.level().getDayTime(), self.level().getGameTime());
		} else if (!self.isBaby() && self.getVillagerData().getProfession() == ModVillagers.BARD) {
			brain.setSchedule(ModVillagers.BARD_SCHEDULE);
			brain.updateActivityFromSchedule(self.level().getDayTime(), self.level().getGameTime());
		}
		if (io.github.jcondedata.aliveworkplace.legend.Gifted.nightOwl(self)) {
			// A Night Owl (29.6) works the night, whatever their trade.
			brain.setSchedule(ModVillagers.NIGHT_OWL_SCHEDULE);
			brain.updateActivityFromSchedule(self.level().getDayTime(), self.level().getGameTime());
		}
		io.github.jcondedata.aliveworkplace.guard.Guards.updateHealth(self);
	}

	/** A Gifted villager sparkles when they level up (29.6). */
	@Inject(method = "increaseMerchantCareer", at = @At("TAIL"))
	private void aliveworkplace$giftedSparkle(CallbackInfo ci) {
		io.github.jcondedata.aliveworkplace.legend.Gifted.onLevelUp((Villager) (Object) this);
	}

	/** A Vintner or a Tailor who reaches Journeyman sells their building's blueprint (34.13). */
	@Inject(method = "increaseMerchantCareer", at = @At("TAIL"))
	private void aliveworkplace$journeymanBlueprint(CallbackInfo ci) {
		io.github.jcondedata.aliveworkplace.registry.ModTrades.journeymanBlueprint((Villager) (Object) this);
	}

	/** Silver Tongue (29.6): every trade with a player is cheaper, on top of vanilla's special prices. */
	@Inject(method = "updateSpecialPrices", at = @At("TAIL"))
	private void aliveworkplace$silverTongue(net.minecraft.world.entity.player.Player player, CallbackInfo ci) {
		io.github.jcondedata.aliveworkplace.legend.Gifted.discount((Villager) (Object) this);
		io.github.jcondedata.aliveworkplace.hall.TradeFairs.discount((Villager) (Object) this); // a trade fair's day (29.17)
	}
}
