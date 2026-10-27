package io.github.jcondedata.aliveworkplace.build;

import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

/** Player ↔ builder interactions and lifecycle hooks. */
public final class BuilderEvents {
	/**
	 * Whether a villager takes what {@code player} holds in the off hand when the main hand is empty (B341): the charter
	 * and the charm on a sneak-right-click, a tonic on a plain one, a Gift, a Remedy, a letter or an heirloom on either.
	 */
	private static boolean takenFromTheOffHand(net.minecraft.world.entity.player.Player player) {
		ItemStack stack = player.getOffhandItem();
		if (stack.is(ModItems.GUILD_CHARTER) || stack.is(ModItems.EVERGREEN_CHARM)) {
			return player.isShiftKeyDown();
		}
		if (stack.getItem() instanceof io.github.jcondedata.aliveworkplace.people.TonicItem) {
			return !player.isShiftKeyDown();
		}
		return stack.is(ModItems.GIFT) || io.github.jcondedata.aliveworkplace.people.RemedyItem.isRemedy(stack)
			|| io.github.jcondedata.aliveworkplace.story.RequestErrands.isCarried(stack);
	}

	public static void init() {
		// Right-click a builder with a blueprint: hand it over; with a Book and Quill: the material list (B279).
		// Sneak-right-click with an empty hand: status and the whole list.
		Platform.get().onUseEntity((player, level, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || player.isSpectator() || !(entity instanceof Villager villager)) {
				return InteractionResult.PASS;
			}
			// A Legend found at their camp (29.9): the traveller talks, the castaway takes a meal, the prisoner's bars hold.
			if (io.github.jcondedata.aliveworkplace.legend.LegendSites.isCaptive(villager)) {
				return level.isClientSide() ? InteractionResult.SUCCESS
					: io.github.jcondedata.aliveworkplace.legend.LegendSites.use((ServerPlayer) player, villager, hand);
			}
			// A Legend visiting as a guest (29.8): their terms. They take no job, so nothing else is done with them.
			if (io.github.jcondedata.aliveworkplace.legend.LegendGuests.isGuest(villager)) {
				if (!level.isClientSide()) {
					io.github.jcondedata.aliveworkplace.legend.LegendGuests.openTerms((ServerPlayer) player, villager);
				}
				return InteractionResult.SUCCESS;
			}
			// B341: with the main hand empty, a villager item in the off hand is given on the main hand's turn (Guilds,
			// LifeStages, Tonics, Gifts, RemedyItem, RequestErrands), so an empty-hand page here (the builder's status line,
			// a scholar's research, a nurse's treatment, ...) mustn't take the click.
			if (player.getMainHandItem().isEmpty() && takenFromTheOffHand(player)) {
				return InteractionResult.PASS;
			}
			// B342: the same for this listener's own items (a City Plan, a job's item, a hiring item such as lapis or a compass,
			// a marker, a blueprint, ...): with the main hand empty, the off hand's item is used on the main hand's turn, before
			// the empty-hand pages. Only when nothing here takes it do those pages answer as before, so a torch or a shield in the
			// off hand still leaves the builder's status line and the rest to an empty-handed click. The off hand's own turn
			// passes (above), so a click counts once. A shield is where players keep it, in the off hand: it isn't handed to a
			// guard by accident (a sneak-click with only a shield there is an empty-handed one); from the main hand it still is.
			ItemStack off = player.getOffhandItem();
			if (player.getMainHandItem().isEmpty() && !off.isEmpty() && !io.github.jcondedata.aliveworkplace.guard.Guards.isShield(off)) {
				InteractionResult fromOffHand = answer(player, level, villager, off, InteractionHand.OFF_HAND);
				if (fromOffHand != InteractionResult.PASS) {
					return fromOffHand;
				}
			}
			return answer(player, level, villager, player.getMainHandItem(), hand);
		});

		// B120 and 35.5: a builder at work (lead or helper, any build) takes no fall damage. He hops onto the build's own
		// upper rows (a palisade's stake tops, a tower's platform before its parapet, a Wonder's dome) and from there
		// vanilla's walking, a jump or a crewmate's shove takes him over the edge: in GameTests a crew of three fell 8 to 12
		// blocks off a tower several times each, 8 to 9 health a fall, and eight on the Wonder folly lost one or two to falls
		// (a lead's death once ended its part). Other damage still hurts him; a villager with no build falls as usual.
		Platform.get().allowDamage((entity, source, amount) ->
			!(entity instanceof Villager villager && !entity.level().isClientSide() && source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)
				&& io.github.jcondedata.aliveworkplace.registry.ModAttachments.BUILDER_JOB.has(villager)));
		// Knights block blows from in front with their shield.
		Platform.get().allowDamage((entity, source, amount) ->
			!(entity instanceof Villager villager && !entity.level().isClientSide() && (io.github.jcondedata.aliveworkplace.nether.Netherworkers.shields(villager, source)
				|| io.github.jcondedata.aliveworkplace.guard.Guards.block(villager, source, amount))));
		Platform.get().afterDeath((entity, source) -> {
			if (entity instanceof Villager villager && entity.level() instanceof ServerLevel level) {
				Builders.onBuilderDeath(level, villager);
				io.github.jcondedata.aliveworkplace.mine.Miners.onMinerDeath(level, villager);
				io.github.jcondedata.aliveworkplace.farm.Fields.onDeath(level, villager);
				io.github.jcondedata.aliveworkplace.wood.TreeFarms.onDeath(level, villager);
				io.github.jcondedata.aliveworkplace.orchard.Orchards.onDeath(level, villager);
				io.github.jcondedata.aliveworkplace.berry.BerryBreeders.onDeath(level, villager);
				io.github.jcondedata.aliveworkplace.people.FamilyBook.onDeath(level, villager); // the Book of Families dates them (34.21)
				io.github.jcondedata.aliveworkplace.people.Couples.onDeath(level, villager);
				io.github.jcondedata.aliveworkplace.hall.Guilds.onDeath(level, villager);
				io.github.jcondedata.aliveworkplace.ranch.Daycare.onDeath(level, villager); // (before the grave keeps the villager)
				io.github.jcondedata.aliveworkplace.daycare.DaycareKeepers.onDeath(level, villager);
				net.minecraft.core.BlockPos grave = io.github.jcondedata.aliveworkplace.grave.Graves.onDeath(level, villager);
				io.github.jcondedata.aliveworkplace.legend.LegendSlots.onDeath(level, villager, grave);
				// The village mourns (36.16): told who it was, their family grieving, the grave waiting for its funeral.
				net.minecraft.network.chat.Component died = io.github.jcondedata.aliveworkplace.people.Mourning.onDeath(level, villager, source.getLocalizedDeathMessage(villager), grave);
				io.github.jcondedata.aliveworkplace.hall.Chronicle.record(level, villager.blockPosition(), io.github.jcondedata.aliveworkplace.hall.Chronicle.Kind.DEATH, died);
				io.github.jcondedata.aliveworkplace.story.SicknessStory.onDeath(level, villager); // a killed Doctor Wren isn't put back: another is sent for (B618)
			} else if (entity.level() instanceof ServerLevel level) {
				io.github.jcondedata.aliveworkplace.legend.LegendSlots.onDeath(level, entity, null); // a Legend as a zombie villager
				io.github.jcondedata.aliveworkplace.guard.GuardCombat.onFoeKilled(level, entity, source);
				io.github.jcondedata.aliveworkplace.threat.SiegeReport.onDeath(level, entity, source); // a siege counts its dead (32.6)
				io.github.jcondedata.aliveworkplace.guard.BanditCamps.onDeath(level, entity, source);
				io.github.jcondedata.aliveworkplace.bounty.Bounties.onDeath(level, entity, source); // a bounty captain drops his insignia (31.13)
				io.github.jcondedata.aliveworkplace.story.Arcs.onDeath(level, entity); // an arc's mob isn't put back (31.4)
				io.github.jcondedata.aliveworkplace.hall.VillageQuests.onKill(level, entity, source);
			}
		});
	}

	/**
	 * What a right-click on {@code villager} holding {@code held} (in {@code hand}) does, with an empty {@code held} for an
	 * empty hand; PASS leaves it to the other listeners and vanilla.
	 */
	private static InteractionResult answer(net.minecraft.world.entity.player.Player player, net.minecraft.world.level.Level level,
			Villager villager, ItemStack held, InteractionHand hand) {
		// B354: a personal request's letter, reply or heirloom is paper, a compass, a clock...: never a job pick or a hiring
		// item. It's left to RequestErrands (the right villager takes it) and vanilla, sneaking or not.
		if (io.github.jcondedata.aliveworkplace.story.RequestErrands.isCarried(held)) {
			return InteractionResult.PASS;
		}
		// Sneak-right-click with a City Plan: the villager becomes the Steward of its hall, if he stands by it (ROADMAP 27.5).
		if (player.isShiftKeyDown() && held.is(ModItems.CITY_PLAN)) {
			return level.isClientSide() ? InteractionResult.SUCCESS
				: io.github.jcondedata.aliveworkplace.city.Stewards.appoint((ServerPlayer) player, villager, held);
		}
		// Sneak-right-click with a job's item: the villager takes that job at the block they stand by (ROADMAP 21.1a,
		// work/Stations). Passes when they already have it, so the item's other uses (hiring) still happen.
		if (player.isShiftKeyDown() && io.github.jcondedata.aliveworkplace.work.Stations.picksAny(held)) {
			if (level.isClientSide()) {
				return InteractionResult.SUCCESS;
			}
			InteractionResult chosen = io.github.jcondedata.aliveworkplace.work.Stations.choose((ServerPlayer) player, villager,
				held);
			if (chosen != InteractionResult.PASS) {
				return chosen;
			}
		}
		// The Pathfinder (29.13): a sneak-right-click with an empty hand offers an expedition; a right-click there asks for Home.
		if (!level.isClientSide() && io.github.jcondedata.aliveworkplace.legend.Pathfinder.isPathfinder(villager)) {
			if (player.isShiftKeyDown() && held.isEmpty()) {
				io.github.jcondedata.aliveworkplace.legend.Pathfinder.offer((ServerPlayer) player, villager);
				return InteractionResult.SUCCESS;
			}
			if (!player.isShiftKeyDown() && io.github.jcondedata.aliveworkplace.legend.Pathfinder.askHome((ServerPlayer) player, villager)) {
				return InteractionResult.SUCCESS;
			}
		}
		// The Golem Smith (29.15), sneak-right-clicked with an empty hand: chooses which golem comes next.
		if (player.isShiftKeyDown() && held.isEmpty() && io.github.jcondedata.aliveworkplace.legend.GolemSmith.isSmith(villager)) {
			if (!level.isClientSide()) {
				io.github.jcondedata.aliveworkplace.legend.GolemSmith.choose((ServerPlayer) player, villager);
			}
			return InteractionResult.SUCCESS;
		}
		// The Master Architect (29.12), sneak-right-clicked with an empty hand: pauses grander buildings, or carries on.
		if (player.isShiftKeyDown() && held.isEmpty() && io.github.jcondedata.aliveworkplace.legend.GrandRebuild.isArchitect(villager)) {
			if (!level.isClientSide()) {
				io.github.jcondedata.aliveworkplace.legend.GrandRebuild.togglePause((ServerPlayer) player, villager);
			}
			return InteractionResult.SUCCESS;
		}
		// A scholar, sneak-right-clicked with an empty hand: the village's research.
		if (player.isShiftKeyDown() && held.isEmpty()
			&& villager.getVillagerData().getProfession() == io.github.jcondedata.aliveworkplace.registry.ModVillagers.SCHOLAR) {
			if (!level.isClientSide()) {
				io.github.jcondedata.aliveworkplace.research.ResearchScreen.open((ServerPlayer) player, villager);
			}
			return InteractionResult.SUCCESS;
		}
		// The Pokémon Professor (29.21), right-clicked with an empty hand: hints about the player's party.
		if (!player.isShiftKeyDown() && held.isEmpty() && io.github.jcondedata.aliveworkplace.legend.PokemonProfessor.isProfessor(villager)) {
			if (!level.isClientSide()) {
				io.github.jcondedata.aliveworkplace.legend.PokemonProfessor.open((ServerPlayer) player, villager);
			}
			return InteractionResult.SUCCESS;
		}
		// A Legend without a trade of their own, sneak-right-clicked with an empty hand: their research tree's tab (29.11).
		if (player.isShiftKeyDown() && held.isEmpty()
			&& villager.getVillagerData().getProfession() == io.github.jcondedata.aliveworkplace.registry.ModVillagers.LEGEND) {
			if (!level.isClientSide()) {
				io.github.jcondedata.aliveworkplace.research.ResearchScreen.openForLegend((ServerPlayer) player, villager);
			}
			return InteractionResult.SUCCESS;
		}
		// A guard, sneak-right-clicked with a bow, crossbow, shield, healing potion or spyglass: they hold it, and that makes their kind.
		if (player.isShiftKeyDown() && io.github.jcondedata.aliveworkplace.guard.Guards.isGuard(villager)) {
			if (held.is(ModItems.RALLY_BANNER)) {
				return level.isClientSide() ? InteractionResult.SUCCESS
					: io.github.jcondedata.aliveworkplace.guard.RallyBannerItem.enlist((ServerPlayer) player, villager, held);
			}
			if (held.is(ModItems.PATROL_MAP)) {
				return level.isClientSide() ? InteractionResult.SUCCESS
					: io.github.jcondedata.aliveworkplace.guard.PatrolMapItem.giveTo((ServerPlayer) player, villager, held);
			}
			if (io.github.jcondedata.aliveworkplace.guard.Guards.makesKind(held)) {
				if (!level.isClientSide()) {
					io.github.jcondedata.aliveworkplace.guard.Guards.hand((ServerPlayer) player, villager, held);
				}
				return InteractionResult.SUCCESS;
			}
		}
		// A traveller staying at an inn: the screen to hire them.
		if (!level.isClientSide() && io.github.jcondedata.aliveworkplace.inn.Innkeepers.isTraveller(villager)) {
			io.github.jcondedata.aliveworkplace.inn.Innkeepers.openHire((ServerPlayer) player, villager);
			return InteractionResult.SUCCESS;
		}
		if (io.github.jcondedata.aliveworkplace.mine.Miners.isMiner(villager)) {
			if (held.is(ModItems.QUARRY_MARKER)) {
				return level.isClientSide() ? InteractionResult.SUCCESS
					: io.github.jcondedata.aliveworkplace.mine.Miners.assign((ServerPlayer) player, villager, held);
			}
			if (held.isEmpty() && player.isShiftKeyDown()) {
				if (!level.isClientSide()) {
					io.github.jcondedata.aliveworkplace.mine.Miners.sendStatus(player, villager);
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		}
		if (io.github.jcondedata.aliveworkplace.smith.BallSmiths.isSmith(villager)) {
			// Sneak-right-click with an empty hand: which balls to make. Otherwise the usual trades.
			if (held.isEmpty() && player.isShiftKeyDown()) {
				if (!level.isClientSide()) {
					io.github.jcondedata.aliveworkplace.smith.BallSmiths.openOrders((ServerPlayer) player, villager);
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		}
		if (io.github.jcondedata.aliveworkplace.gem.GemGrowers.isGrower(villager)) {
			// Sneak-right-click with an empty hand: which gem beds she keeps. Otherwise the usual trades.
			if (held.isEmpty() && player.isShiftKeyDown()) {
				if (!level.isClientSide()) {
					io.github.jcondedata.aliveworkplace.gem.GemGrowers.openOrders((ServerPlayer) player, villager);
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		}
		if (io.github.jcondedata.aliveworkplace.daycare.DaycareKeepers.isKeeper(villager)) {
			// Right-click with an empty hand: the daycare. Sneak for the trades.
			if (held.isEmpty() && !player.isShiftKeyDown()) {
				if (!level.isClientSide()) {
					io.github.jcondedata.aliveworkplace.daycare.DaycareKeepers.open((ServerPlayer) player, villager);
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		}
		if (io.github.jcondedata.aliveworkplace.habitat.HabitatKeepers.isKeeper(villager)) {
			// A Field Marker: a lure spot. Sneak-right-click with an empty hand: the lure picker. Otherwise the usual trades.
			if (held.is(ModItems.FIELD_MARKER)) {
				return level.isClientSide() ? InteractionResult.SUCCESS
					: io.github.jcondedata.aliveworkplace.habitat.HabitatKeepers.markSpot((ServerPlayer) player, villager, held);
			}
			if (held.isEmpty() && player.isShiftKeyDown()) {
				if (!level.isClientSide()) {
					io.github.jcondedata.aliveworkplace.habitat.HabitatKeepers.openLures((ServerPlayer) player, villager);
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		}
		if (io.github.jcondedata.aliveworkplace.berry.BerryBreeders.isBreeder(villager)) {
			// A Field Marker: her plot. Sneak-right-click with an empty hand: the berry book. Otherwise the usual trades.
			if (held.is(ModItems.FIELD_MARKER)) {
				return level.isClientSide() ? InteractionResult.SUCCESS
					: io.github.jcondedata.aliveworkplace.berry.BerryBreeders.assign((ServerPlayer) player, villager, held);
			}
			if (held.isEmpty() && player.isShiftKeyDown()) {
				if (!level.isClientSide()) {
					io.github.jcondedata.aliveworkplace.berry.BerryBreeders.open((ServerPlayer) player, villager);
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		}
		if (io.github.jcondedata.aliveworkplace.orchard.Orchards.isKeeper(villager)) {
			if (held.is(ModItems.FIELD_MARKER)) {
				return level.isClientSide() ? InteractionResult.SUCCESS
					: io.github.jcondedata.aliveworkplace.orchard.Orchards.assign((ServerPlayer) player, villager, held);
			}
			if (held.isEmpty() && player.isShiftKeyDown() && io.github.jcondedata.aliveworkplace.orchard.Orchards.orchard(villager) != null) {
				if (!level.isClientSide()) {
					io.github.jcondedata.aliveworkplace.orchard.Orchards.sendStatus(player, villager);
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		}
		if (io.github.jcondedata.aliveworkplace.wood.TreeFarms.isLumberjack(villager)) {
			if (held.is(ModItems.FIELD_MARKER)) {
				return level.isClientSide() ? InteractionResult.SUCCESS
					: io.github.jcondedata.aliveworkplace.wood.TreeFarms.assign((ServerPlayer) player, villager, held);
			}
			if (held.isEmpty() && player.isShiftKeyDown() && io.github.jcondedata.aliveworkplace.wood.TreeFarms.farm(villager) != null) {
				if (!level.isClientSide()) {
					io.github.jcondedata.aliveworkplace.wood.TreeFarms.sendStatus(player, villager);
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		}
		if (io.github.jcondedata.aliveworkplace.farm.Fields.isFarmer(villager)) {
			if (held.is(ModItems.FIELD_MARKER)) {
				return level.isClientSide() ? InteractionResult.SUCCESS
					: io.github.jcondedata.aliveworkplace.farm.Fields.assign((ServerPlayer) player, villager, held);
			}
			if (held.isEmpty() && player.isShiftKeyDown() && io.github.jcondedata.aliveworkplace.farm.Fields.hasField(villager)) {
				if (!level.isClientSide()) {
					io.github.jcondedata.aliveworkplace.farm.Fields.sendStatus(player, villager);
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		}
		if (io.github.jcondedata.aliveworkplace.mail.Postmen.isPostman(villager)) {
			if (held.is(ModItems.DELIVERY_NOTE)) {
				return level.isClientSide() ? InteractionResult.SUCCESS
					: io.github.jcondedata.aliveworkplace.mail.Postmen.assign((ServerPlayer) player, villager, held);
			}
			if (held.isEmpty() && player.isShiftKeyDown()) {
				if (!level.isClientSide()) {
					io.github.jcondedata.aliveworkplace.mail.Postmen.sendStatus(player, villager);
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		}
		if (io.github.jcondedata.aliveworkplace.trainer.Trainers.isTrainer(villager)) {
			// Right-click with an empty hand: battle. Sneak to trade instead.
			if (held.isEmpty() && !player.isShiftKeyDown()) {
				if (!level.isClientSide()) {
					io.github.jcondedata.aliveworkplace.trainer.Trainers.challenge((ServerPlayer) player, villager);
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		}
		if (io.github.jcondedata.aliveworkplace.fossil.FossilScientists.isScientist(villager)) {
			// Right-click holding a fossil: hand it over. With an empty hand: collect what's ready. Sneak to trade instead.
			if (io.github.jcondedata.aliveworkplace.fossil.FossilScientists.isFossil(held)) {
				if (!level.isClientSide()) {
					io.github.jcondedata.aliveworkplace.fossil.FossilScientists.handOver((ServerPlayer) player, villager);
				}
				return InteractionResult.SUCCESS;
			}
			if (held.isEmpty() && !player.isShiftKeyDown()) {
				if (!level.isClientSide()) {
					io.github.jcondedata.aliveworkplace.fossil.FossilScientists.check((ServerPlayer) player, villager);
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		}
		if (io.github.jcondedata.aliveworkplace.ranch.Daycare.keepsDaycare(villager) && held.isEmpty() && !player.isShiftKeyDown()) {
			// A rancher (with Cobblemon): the daycare. Sneak for the trades.
			if (!level.isClientSide()) {
				io.github.jcondedata.aliveworkplace.ranch.Daycare.open((ServerPlayer) player, villager);
			}
			return InteractionResult.SUCCESS;
		}
		if (io.github.jcondedata.aliveworkplace.trader.PokemonTraders.isTrader(villager)) {
			// Right-click with an empty hand: the day's Pokémon trades. Sneak to trade items instead.
			if (held.isEmpty() && !player.isShiftKeyDown()) {
				if (!level.isClientSide()) {
					io.github.jcondedata.aliveworkplace.trader.PokemonTraders.open((ServerPlayer) player, villager);
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		}
		if (io.github.jcondedata.aliveworkplace.shop.Shops.isShopkeeper(villager) && io.github.jcondedata.aliveworkplace.work.Money.cobbleDollars()) {
			// With CobbleDollars: right-click with an empty hand opens the shop (pay in CobbleDollars). Sneak for the trade screen.
			if (held.isEmpty() && !player.isShiftKeyDown()) {
				if (!level.isClientSide()) {
					io.github.jcondedata.aliveworkplace.shop.Shops.openMenu((ServerPlayer) player, villager);
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		}
		if (io.github.jcondedata.aliveworkplace.travel.Ferrymen.isFerryman(villager) && io.github.jcondedata.aliveworkplace.work.Money.cobbleDollars()) {
			// With CobbleDollars: right-click with an empty hand for tickets paid in CobbleDollars. Sneak for the trade screen.
			if (held.isEmpty() && !player.isShiftKeyDown()) {
				if (!level.isClientSide()) {
					io.github.jcondedata.aliveworkplace.travel.Ferrymen.openMenu((ServerPlayer) player, villager);
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		}
		if (io.github.jcondedata.aliveworkplace.tutor.Tutors.isTutor(villager)) {
			// Right-click with an empty hand: lessons. Sneak to trade instead.
			if (held.isEmpty() && !player.isShiftKeyDown()) {
				if (!level.isClientSide()) {
					io.github.jcondedata.aliveworkplace.tutor.Tutors.open((ServerPlayer) player, villager);
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		}
		if (io.github.jcondedata.aliveworkplace.nurse.Nurses.isNurse(villager)) {
			// Right-click with an empty hand: get treated. Sneak to trade instead.
			if (held.isEmpty() && !player.isShiftKeyDown()) {
				if (!level.isClientSide()) {
					io.github.jcondedata.aliveworkplace.nurse.Nurses.treat((ServerPlayer) player, villager);
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		}
		if (player.isShiftKeyDown() && io.github.jcondedata.aliveworkplace.smelt.Smelters.isSmelter(villager)
				&& io.github.jcondedata.aliveworkplace.smelt.Smelters.isFuel(held.getItem())) {
			return level.isClientSide() ? InteractionResult.SUCCESS
				: io.github.jcondedata.aliveworkplace.smelt.Smelters.hire((ServerPlayer) player, villager);
		}
		if (player.isShiftKeyDown() && held.is(net.minecraft.world.item.Items.LAPIS_LAZULI) && !villager.isBaby()
				&& villager.getVillagerData().getProfession() == net.minecraft.world.entity.npc.VillagerProfession.LIBRARIAN) {
			return level.isClientSide() ? InteractionResult.SUCCESS
				: io.github.jcondedata.aliveworkplace.work.Hiring.hire((ServerPlayer) player, villager,
					net.minecraft.network.chat.Component.translatable("message.aliveworkplace.scribe.hired", villager.getDisplayName()));
		}
		if (player.isShiftKeyDown() && held.is(net.minecraft.world.item.Items.COMPASS)
				&& io.github.jcondedata.aliveworkplace.explore.Explorers.isExplorer(villager)) {
			return level.isClientSide() ? InteractionResult.SUCCESS
				: io.github.jcondedata.aliveworkplace.explore.Explorers.hire((ServerPlayer) player, villager);
		}
		if (player.isShiftKeyDown() && held.is(net.minecraft.world.item.Items.GLASS_BOTTLE)
				&& io.github.jcondedata.aliveworkplace.brew.AlchemistWork.isAlchemist(villager)) {
			return level.isClientSide() ? InteractionResult.SUCCESS
				: io.github.jcondedata.aliveworkplace.work.Hiring.hire((ServerPlayer) player, villager,
					net.minecraft.network.chat.Component.translatable("message.aliveworkplace.alchemist.hired", villager.getDisplayName()));
		}
		if (player.isShiftKeyDown() && held.is(net.minecraft.world.item.Items.LEAD)
				&& io.github.jcondedata.aliveworkplace.ranch.HerderWork.isHerder(villager)) {
			return level.isClientSide() ? InteractionResult.SUCCESS
				: io.github.jcondedata.aliveworkplace.work.Hiring.hire((ServerPlayer) player, villager,
					net.minecraft.network.chat.Component.translatable("message.aliveworkplace.herder.hired", villager.getDisplayName(),
						io.github.jcondedata.aliveworkplace.ranch.RanchWork.RADIUS, io.github.jcondedata.aliveworkplace.ranch.RanchWork.cap(villager)));
		}
		if (player.isShiftKeyDown() && held.is(net.minecraft.world.item.Items.SHEARS)
				&& io.github.jcondedata.aliveworkplace.ranch.ShepherdWork.isShepherd(villager)) {
			return level.isClientSide() ? InteractionResult.SUCCESS
				: io.github.jcondedata.aliveworkplace.work.Hiring.hire((ServerPlayer) player, villager,
					net.minecraft.network.chat.Component.translatable("message.aliveworkplace.shepherd.hired", villager.getDisplayName(),
						io.github.jcondedata.aliveworkplace.ranch.RanchWork.RADIUS));
		}
		if (player.isShiftKeyDown() && !villager.isBaby() && held.is(net.minecraft.world.item.Items.FLINT)
				&& villager.getVillagerData().getProfession() == net.minecraft.world.entity.npc.VillagerProfession.FLETCHER) {
			return level.isClientSide() ? InteractionResult.SUCCESS
				: io.github.jcondedata.aliveworkplace.work.Hiring.hire((ServerPlayer) player, villager,
					net.minecraft.network.chat.Component.translatable("message.aliveworkplace.fletcher.hired", villager.getDisplayName()));
		}
		if (player.isShiftKeyDown() && !villager.isBaby() && held.is(net.minecraft.world.item.Items.IRON_INGOT)
				&& (villager.getVillagerData().getProfession() == net.minecraft.world.entity.npc.VillagerProfession.TOOLSMITH
					|| villager.getVillagerData().getProfession() == net.minecraft.world.entity.npc.VillagerProfession.WEAPONSMITH)) {
			String key = villager.getVillagerData().getProfession() == net.minecraft.world.entity.npc.VillagerProfession.TOOLSMITH
				? "message.aliveworkplace.toolsmith.hired" : "message.aliveworkplace.weaponsmith.hired";
			return level.isClientSide() ? InteractionResult.SUCCESS
				: io.github.jcondedata.aliveworkplace.work.Hiring.hire((ServerPlayer) player, villager,
					net.minecraft.network.chat.Component.translatable(key, villager.getDisplayName()));
		}
		if (io.github.jcondedata.aliveworkplace.fish.Fishers.isFisherman(villager)) {
			if (held.is(net.minecraft.world.item.Items.FISHING_ROD)) {
				return level.isClientSide() ? InteractionResult.SUCCESS
					: io.github.jcondedata.aliveworkplace.fish.Fishers.assign((ServerPlayer) player, villager, held);
			}
			if (held.isEmpty() && player.isShiftKeyDown() && io.github.jcondedata.aliveworkplace.fish.Fishers.isHired(villager)) {
				if (!level.isClientSide()) {
					io.github.jcondedata.aliveworkplace.fish.Fishers.sendStatus(player, villager);
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		}
		if (!Builders.isBuilder(villager)) {
			return InteractionResult.PASS;
		}
		if (held.is(ModItems.WONDER_PLAN)) { // a placed Wonder Plan: the Foundation Stone goes down, stage I begins (35.4)
			return level.isClientSide() ? InteractionResult.SUCCESS
				: io.github.jcondedata.aliveworkplace.wonder.WonderStages.handOver((ServerPlayer) player, villager, held);
		}
		if (held.is(net.minecraft.world.item.Items.WRITABLE_BOOK)) { // B279: the material list of the build under way
			if (!level.isClientSide()) {
				SiteList.write((ServerLevel) level, player, Builders.activeSite((ServerLevel) level, villager), villager, hand);
			}
			return InteractionResult.SUCCESS;
		}
		if (held.is(ModItems.BLUEPRINT)) {
			if (level.isClientSide()) {
				return InteractionResult.SUCCESS;
			}
			return Builders.assign((ServerPlayer) player, villager, held, player.isShiftKeyDown());
		}
		if (held.isEmpty() && player.isShiftKeyDown()) {
			if (!level.isClientSide()) {
				Builders.sendStatus(player, villager);
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	private BuilderEvents() {
	}
}
