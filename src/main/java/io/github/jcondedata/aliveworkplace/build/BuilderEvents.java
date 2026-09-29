package io.github.jcondedata.aliveworkplace.build;

import io.github.jcondedata.aliveworkplace.registry.ModItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

/** Player ↔ builder interactions and lifecycle hooks. */
public final class BuilderEvents {
	public static void init() {
		// Right-click a builder with a blueprint: hand it over. Sneak-right-click with an empty hand: status.
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || player.isSpectator() || !(entity instanceof Villager villager)) {
				return InteractionResult.PASS;
			}
			// A scholar, sneak-right-clicked with an empty hand: the village's research.
			if (player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty()
				&& villager.getVillagerData().getProfession() == io.github.jcondedata.aliveworkplace.registry.ModVillagers.SCHOLAR) {
				if (!level.isClientSide) {
					io.github.jcondedata.aliveworkplace.research.ResearchScreen.open((ServerPlayer) player, villager);
				}
				return InteractionResult.SUCCESS;
			}
			// A guard, sneak-right-clicked with a bow, crossbow, shield or healing potion: they hold it, and that makes their kind.
			if (player.isShiftKeyDown() && io.github.jcondedata.aliveworkplace.guard.Guards.isGuard(villager)) {
				ItemStack held = player.getItemInHand(hand);
				if (held.is(ModItems.PATROL_MAP)) {
					return level.isClientSide ? InteractionResult.SUCCESS
						: io.github.jcondedata.aliveworkplace.guard.PatrolMapItem.giveTo((ServerPlayer) player, villager, held);
				}
				if (io.github.jcondedata.aliveworkplace.guard.Guards.isBow(held) || io.github.jcondedata.aliveworkplace.guard.Guards.isShield(held)
					|| io.github.jcondedata.aliveworkplace.guard.Guards.isMedicine(held)) {
					if (!level.isClientSide) {
						ItemStack old = villager.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND);
						villager.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, held.split(1));
						villager.setDropChance(net.minecraft.world.entity.EquipmentSlot.OFFHAND, 0f);
						if (!old.isEmpty() && !player.getInventory().add(old)) {
							player.drop(old, false);
						}
						player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.aliveworkplace.guard.kind",
							villager.getDisplayName(), io.github.jcondedata.aliveworkplace.guard.Guards.kind(villager).title())
							.withStyle(net.minecraft.ChatFormatting.GREEN), true);
						level.playSound(null, villager.blockPosition(), net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_GENERIC.value(),
							net.minecraft.sounds.SoundSource.NEUTRAL, 1f, 1f);
					}
					return InteractionResult.SUCCESS;
				}
			}
			// A traveller staying at an inn: the screen to hire them.
			if (!level.isClientSide && io.github.jcondedata.aliveworkplace.inn.Innkeepers.isTraveller(villager)) {
				io.github.jcondedata.aliveworkplace.inn.Innkeepers.openHire((ServerPlayer) player, villager);
				return InteractionResult.SUCCESS;
			}
			if (io.github.jcondedata.aliveworkplace.mine.Miners.isMiner(villager)) {
				ItemStack held = player.getItemInHand(hand);
				if (held.is(ModItems.QUARRY_MARKER)) {
					return level.isClientSide ? InteractionResult.SUCCESS
						: io.github.jcondedata.aliveworkplace.mine.Miners.assign((ServerPlayer) player, villager, held);
				}
				if (held.isEmpty() && player.isShiftKeyDown()) {
					if (!level.isClientSide) {
						io.github.jcondedata.aliveworkplace.mine.Miners.sendStatus(player, villager);
					}
					return InteractionResult.SUCCESS;
				}
				return InteractionResult.PASS;
			}
			if (io.github.jcondedata.aliveworkplace.smith.BallSmiths.isSmith(villager)) {
				// Sneak-right-click with an empty hand: which balls to make. Otherwise the usual trades.
				if (player.getItemInHand(hand).isEmpty() && player.isShiftKeyDown()) {
					if (!level.isClientSide) {
						io.github.jcondedata.aliveworkplace.smith.BallSmiths.openOrders((ServerPlayer) player, villager);
					}
					return InteractionResult.SUCCESS;
				}
				return InteractionResult.PASS;
			}
			if (io.github.jcondedata.aliveworkplace.orchard.Orchards.isKeeper(villager)) {
				ItemStack held = player.getItemInHand(hand);
				if (held.is(ModItems.FIELD_MARKER)) {
					return level.isClientSide ? InteractionResult.SUCCESS
						: io.github.jcondedata.aliveworkplace.orchard.Orchards.assign((ServerPlayer) player, villager, held);
				}
				if (held.isEmpty() && player.isShiftKeyDown() && io.github.jcondedata.aliveworkplace.orchard.Orchards.orchard(villager) != null) {
					if (!level.isClientSide) {
						io.github.jcondedata.aliveworkplace.orchard.Orchards.sendStatus(player, villager);
					}
					return InteractionResult.SUCCESS;
				}
				return InteractionResult.PASS;
			}
			if (io.github.jcondedata.aliveworkplace.wood.TreeFarms.isLumberjack(villager)) {
				ItemStack held = player.getItemInHand(hand);
				if (held.is(ModItems.FIELD_MARKER)) {
					return level.isClientSide ? InteractionResult.SUCCESS
						: io.github.jcondedata.aliveworkplace.wood.TreeFarms.assign((ServerPlayer) player, villager, held);
				}
				if (held.isEmpty() && player.isShiftKeyDown() && io.github.jcondedata.aliveworkplace.wood.TreeFarms.farm(villager) != null) {
					if (!level.isClientSide) {
						io.github.jcondedata.aliveworkplace.wood.TreeFarms.sendStatus(player, villager);
					}
					return InteractionResult.SUCCESS;
				}
				return InteractionResult.PASS;
			}
			if (io.github.jcondedata.aliveworkplace.farm.Fields.isFarmer(villager)) {
				ItemStack held = player.getItemInHand(hand);
				if (held.is(ModItems.FIELD_MARKER)) {
					return level.isClientSide ? InteractionResult.SUCCESS
						: io.github.jcondedata.aliveworkplace.farm.Fields.assign((ServerPlayer) player, villager, held);
				}
				if (held.isEmpty() && player.isShiftKeyDown() && io.github.jcondedata.aliveworkplace.farm.Fields.hasField(villager)) {
					if (!level.isClientSide) {
						io.github.jcondedata.aliveworkplace.farm.Fields.sendStatus(player, villager);
					}
					return InteractionResult.SUCCESS;
				}
				return InteractionResult.PASS;
			}
			if (io.github.jcondedata.aliveworkplace.mail.Postmen.isPostman(villager)) {
				ItemStack held = player.getItemInHand(hand);
				if (held.is(ModItems.DELIVERY_NOTE)) {
					return level.isClientSide ? InteractionResult.SUCCESS
						: io.github.jcondedata.aliveworkplace.mail.Postmen.assign((ServerPlayer) player, villager, held);
				}
				if (held.isEmpty() && player.isShiftKeyDown()) {
					if (!level.isClientSide) {
						io.github.jcondedata.aliveworkplace.mail.Postmen.sendStatus(player, villager);
					}
					return InteractionResult.SUCCESS;
				}
				return InteractionResult.PASS;
			}
			if (io.github.jcondedata.aliveworkplace.trainer.Trainers.isTrainer(villager)) {
				// Right-click with an empty hand: battle. Sneak to trade instead.
				if (player.getItemInHand(hand).isEmpty() && !player.isShiftKeyDown()) {
					if (!level.isClientSide) {
						io.github.jcondedata.aliveworkplace.trainer.Trainers.challenge((ServerPlayer) player, villager);
					}
					return InteractionResult.SUCCESS;
				}
				return InteractionResult.PASS;
			}
			if (io.github.jcondedata.aliveworkplace.fossil.FossilScientists.isScientist(villager)) {
				// Right-click holding a fossil: hand it over. With an empty hand: collect what's ready. Sneak to trade instead.
				ItemStack held = player.getItemInHand(hand);
				if (io.github.jcondedata.aliveworkplace.fossil.FossilScientists.isFossil(held)) {
					if (!level.isClientSide) {
						io.github.jcondedata.aliveworkplace.fossil.FossilScientists.handOver((ServerPlayer) player, villager);
					}
					return InteractionResult.SUCCESS;
				}
				if (held.isEmpty() && !player.isShiftKeyDown()) {
					if (!level.isClientSide) {
						io.github.jcondedata.aliveworkplace.fossil.FossilScientists.check((ServerPlayer) player, villager);
					}
					return InteractionResult.SUCCESS;
				}
				return InteractionResult.PASS;
			}
			if (io.github.jcondedata.aliveworkplace.trader.PokemonTraders.isTrader(villager)) {
				// Right-click with an empty hand: the day's Pokémon trades. Sneak to trade items instead.
				if (player.getItemInHand(hand).isEmpty() && !player.isShiftKeyDown()) {
					if (!level.isClientSide) {
						io.github.jcondedata.aliveworkplace.trader.PokemonTraders.open((ServerPlayer) player, villager);
					}
					return InteractionResult.SUCCESS;
				}
				return InteractionResult.PASS;
			}
			if (io.github.jcondedata.aliveworkplace.shop.Shops.isShopkeeper(villager) && io.github.jcondedata.aliveworkplace.work.Money.cobbleDollars()) {
				// With CobbleDollars: right-click with an empty hand opens the shop (pay in CobbleDollars). Sneak for the trade screen.
				if (player.getItemInHand(hand).isEmpty() && !player.isShiftKeyDown()) {
					if (!level.isClientSide) {
						io.github.jcondedata.aliveworkplace.shop.Shops.openMenu((ServerPlayer) player, villager);
					}
					return InteractionResult.SUCCESS;
				}
				return InteractionResult.PASS;
			}
			if (io.github.jcondedata.aliveworkplace.travel.Ferrymen.isFerryman(villager) && io.github.jcondedata.aliveworkplace.work.Money.cobbleDollars()) {
				// With CobbleDollars: right-click with an empty hand for tickets paid in CobbleDollars. Sneak for the trade screen.
				if (player.getItemInHand(hand).isEmpty() && !player.isShiftKeyDown()) {
					if (!level.isClientSide) {
						io.github.jcondedata.aliveworkplace.travel.Ferrymen.openMenu((ServerPlayer) player, villager);
					}
					return InteractionResult.SUCCESS;
				}
				return InteractionResult.PASS;
			}
			if (io.github.jcondedata.aliveworkplace.tutor.Tutors.isTutor(villager)) {
				// Right-click with an empty hand: lessons. Sneak to trade instead.
				if (player.getItemInHand(hand).isEmpty() && !player.isShiftKeyDown()) {
					if (!level.isClientSide) {
						io.github.jcondedata.aliveworkplace.tutor.Tutors.open((ServerPlayer) player, villager);
					}
					return InteractionResult.SUCCESS;
				}
				return InteractionResult.PASS;
			}
			if (io.github.jcondedata.aliveworkplace.nurse.Nurses.isNurse(villager)) {
				// Right-click with an empty hand: get treated. Sneak to trade instead.
				if (player.getItemInHand(hand).isEmpty() && !player.isShiftKeyDown()) {
					if (!level.isClientSide) {
						io.github.jcondedata.aliveworkplace.nurse.Nurses.treat((ServerPlayer) player, villager);
					}
					return InteractionResult.SUCCESS;
				}
				return InteractionResult.PASS;
			}
			if (player.isShiftKeyDown() && io.github.jcondedata.aliveworkplace.smelt.Smelters.isSmelter(villager)
					&& io.github.jcondedata.aliveworkplace.smelt.Smelters.isFuel(player.getItemInHand(hand).getItem())) {
				return level.isClientSide ? InteractionResult.SUCCESS
					: io.github.jcondedata.aliveworkplace.smelt.Smelters.hire((ServerPlayer) player, villager);
			}
			if (player.isShiftKeyDown() && player.getItemInHand(hand).is(net.minecraft.world.item.Items.LAPIS_LAZULI) && !villager.isBaby()
					&& villager.getVillagerData().getProfession() == net.minecraft.world.entity.npc.VillagerProfession.LIBRARIAN) {
				return level.isClientSide ? InteractionResult.SUCCESS
					: io.github.jcondedata.aliveworkplace.work.Hiring.hire((ServerPlayer) player, villager,
						net.minecraft.network.chat.Component.translatable("message.aliveworkplace.scribe.hired", villager.getDisplayName()));
			}
			if (player.isShiftKeyDown() && player.getItemInHand(hand).is(net.minecraft.world.item.Items.COMPASS)
					&& io.github.jcondedata.aliveworkplace.explore.Explorers.isExplorer(villager)) {
				return level.isClientSide ? InteractionResult.SUCCESS
					: io.github.jcondedata.aliveworkplace.explore.Explorers.hire((ServerPlayer) player, villager);
			}
			if (player.isShiftKeyDown() && player.getItemInHand(hand).is(net.minecraft.world.item.Items.GLASS_BOTTLE)
					&& io.github.jcondedata.aliveworkplace.brew.AlchemistWork.isAlchemist(villager)) {
				return level.isClientSide ? InteractionResult.SUCCESS
					: io.github.jcondedata.aliveworkplace.work.Hiring.hire((ServerPlayer) player, villager,
						net.minecraft.network.chat.Component.translatable("message.aliveworkplace.alchemist.hired", villager.getDisplayName()));
			}
			if (player.isShiftKeyDown() && player.getItemInHand(hand).is(net.minecraft.world.item.Items.LEAD)
					&& io.github.jcondedata.aliveworkplace.ranch.HerderWork.isHerder(villager)) {
				return level.isClientSide ? InteractionResult.SUCCESS
					: io.github.jcondedata.aliveworkplace.work.Hiring.hire((ServerPlayer) player, villager,
						net.minecraft.network.chat.Component.translatable("message.aliveworkplace.herder.hired", villager.getDisplayName(),
							io.github.jcondedata.aliveworkplace.ranch.RanchWork.RADIUS, io.github.jcondedata.aliveworkplace.ranch.RanchWork.CAP));
			}
			if (player.isShiftKeyDown() && player.getItemInHand(hand).is(net.minecraft.world.item.Items.SHEARS)
					&& io.github.jcondedata.aliveworkplace.ranch.ShepherdWork.isShepherd(villager)) {
				return level.isClientSide ? InteractionResult.SUCCESS
					: io.github.jcondedata.aliveworkplace.work.Hiring.hire((ServerPlayer) player, villager,
						net.minecraft.network.chat.Component.translatable("message.aliveworkplace.shepherd.hired", villager.getDisplayName(),
							io.github.jcondedata.aliveworkplace.ranch.RanchWork.RADIUS));
			}
			if (player.isShiftKeyDown() && !villager.isBaby() && player.getItemInHand(hand).is(net.minecraft.world.item.Items.FLINT)
					&& villager.getVillagerData().getProfession() == net.minecraft.world.entity.npc.VillagerProfession.FLETCHER) {
				return level.isClientSide ? InteractionResult.SUCCESS
					: io.github.jcondedata.aliveworkplace.work.Hiring.hire((ServerPlayer) player, villager,
						net.minecraft.network.chat.Component.translatable("message.aliveworkplace.fletcher.hired", villager.getDisplayName()));
			}
			if (player.isShiftKeyDown() && !villager.isBaby() && player.getItemInHand(hand).is(net.minecraft.world.item.Items.IRON_INGOT)
					&& (villager.getVillagerData().getProfession() == net.minecraft.world.entity.npc.VillagerProfession.TOOLSMITH
						|| villager.getVillagerData().getProfession() == net.minecraft.world.entity.npc.VillagerProfession.WEAPONSMITH)) {
				String key = villager.getVillagerData().getProfession() == net.minecraft.world.entity.npc.VillagerProfession.TOOLSMITH
					? "message.aliveworkplace.toolsmith.hired" : "message.aliveworkplace.weaponsmith.hired";
				return level.isClientSide ? InteractionResult.SUCCESS
					: io.github.jcondedata.aliveworkplace.work.Hiring.hire((ServerPlayer) player, villager,
						net.minecraft.network.chat.Component.translatable(key, villager.getDisplayName()));
			}
			if (io.github.jcondedata.aliveworkplace.fish.Fishers.isFisherman(villager)) {
				ItemStack held = player.getItemInHand(hand);
				if (held.is(net.minecraft.world.item.Items.FISHING_ROD)) {
					return level.isClientSide ? InteractionResult.SUCCESS
						: io.github.jcondedata.aliveworkplace.fish.Fishers.assign((ServerPlayer) player, villager, held);
				}
				if (held.isEmpty() && player.isShiftKeyDown() && io.github.jcondedata.aliveworkplace.fish.Fishers.isHired(villager)) {
					if (!level.isClientSide) {
						io.github.jcondedata.aliveworkplace.fish.Fishers.sendStatus(player, villager);
					}
					return InteractionResult.SUCCESS;
				}
				return InteractionResult.PASS;
			}
			if (!Builders.isBuilder(villager)) {
				return InteractionResult.PASS;
			}
			ItemStack held = player.getItemInHand(hand);
			if (held.is(ModItems.BLUEPRINT)) {
				if (level.isClientSide) {
					return InteractionResult.SUCCESS;
				}
				return Builders.assign((ServerPlayer) player, villager, held, player.isShiftKeyDown());
			}
			if (held.isEmpty() && player.isShiftKeyDown()) {
				if (!level.isClientSide) {
					Builders.sendStatus(player, villager);
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});

		// Knights block blows from in front with their shield.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			!(entity instanceof Villager villager && !entity.level().isClientSide && (io.github.jcondedata.aliveworkplace.nether.Netherworkers.shields(villager, source)
				|| io.github.jcondedata.aliveworkplace.guard.Guards.block(villager, source, amount))));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Villager villager && entity.level() instanceof ServerLevel level) {
				Builders.onBuilderDeath(level, villager);
				io.github.jcondedata.aliveworkplace.mine.Miners.onMinerDeath(level, villager);
				io.github.jcondedata.aliveworkplace.farm.Fields.onDeath(level, villager);
				io.github.jcondedata.aliveworkplace.wood.TreeFarms.onDeath(level, villager);
				io.github.jcondedata.aliveworkplace.orchard.Orchards.onDeath(level, villager);
				io.github.jcondedata.aliveworkplace.grave.Graves.onDeath(level, villager);
				io.github.jcondedata.aliveworkplace.hall.Chronicle.record(level, villager.blockPosition(), io.github.jcondedata.aliveworkplace.hall.Chronicle.Kind.DEATH, source.getLocalizedDeathMessage(villager));
			} else if (entity.level() instanceof ServerLevel level) {
				io.github.jcondedata.aliveworkplace.guard.GuardCombat.onFoeKilled(level, entity, source);
				io.github.jcondedata.aliveworkplace.hall.VillageQuests.onKill(level, entity, source);
			}
		});
	}

	private BuilderEvents() {
	}
}
