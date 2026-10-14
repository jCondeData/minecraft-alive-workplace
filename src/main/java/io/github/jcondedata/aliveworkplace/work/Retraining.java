package io.github.jcondedata.aliveworkplace.work;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderJob;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Rules;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Retraining a villager who already has a job (ROADMAP 26.6, the owner's playtest: in Cobblemon villages nearly every
 * villager works, so there was nobody to make a Builder). It extends picking a job ({@link Stations#choose}): a grown
 * villager with a job (vanilla, ours or another mod's; never a child or a nitwit) sneak-right-clicked with a Blueprint or
 * a Blank Blueprint by a free Blueprint Table is offered the Builder's job for a price by their level, and a second click
 * within {@link #CONFIRM_TICKS} pays and retrains them: the old job's trades and level go, the new job starts at Novice.
 * The other jobs' items keep switching a working villager for free, as they have since 21.1a (ROADMAP Notes, 26.6). A
 * builder given another job hands their builds back first (the blueprint returns, still placed, so another builder
 * carries on where they stopped). Game rule {@code workplaceRetraining} off: the old rule, no Builder by item.
 */
public final class Retraining {
	/** The price in emeralds by the villager's level, Novice to Master (config {@code retrainPrices}). */
	public static int[] PRICES = {5, 10, 20, 35, 50};
	/** How long an offer waits for the second click (5 seconds). */
	public static final int CONFIRM_TICKS = 100;

	/** An offer waiting for its second click. */
	private record Offer(UUID villager, VillagerProfession job, BlockPos station, long until) {
	}

	/** By player: the offer they were shown (only the latest counts; none is saved, an offer lasts seconds). */
	private static final Map<UUID, Offer> OFFERS = new HashMap<>();

	/** Whether retraining is on in {@code level} (game rule {@code workplaceRetraining}). */
	public static boolean on(ServerLevel level) {
		return Rules.on(level, ModGameRules.RETRAINING);
	}

	/** Whether {@code stack} picks the Builder: a Blueprint or a Blank Blueprint. */
	public static boolean picksBuilder(ItemStack stack) {
		return stack.is(ModItems.BLUEPRINT) || stack.is(ModItems.BLANK_BLUEPRINT);
	}

	/**
	 * The Blueprint Table (or an old Builder's Bench) a villager by it would take as a Builder: the free one nearest them
	 * within {@link Stations#REACH}.
	 */
	static Optional<BlockPos> freeTable(ServerLevel level, Villager villager) {
		BlockPos near = villager.blockPosition();
		return level.getPoiManager().findClosest(ModVillagers.BUILDER.acquirableJobSite(), near, (int) Math.ceil(Stations.REACH),
				PoiManager.Occupancy.HAS_SPACE)
			.filter(pos -> pos.distToCenterSqr(villager.position()) <= Stations.REACH * Stations.REACH);
	}

	/**
	 * Whether giving {@code villager} the job {@code job} at {@code station} is a paid retraining: the rule is on, they
	 * have a job, and it's the Builder at a block they don't already work at. Switching between the other jobs stays the
	 * free pick it has been since 21.1a (its tests and the guide rely on it; ROADMAP Notes, 26.6): the Builder had no item
	 * at all before, so a working villager could never become one.
	 */
	static boolean applies(ServerLevel level, Villager villager, BlockPos station, VillagerProfession job) {
		VillagerProfession now = villager.getVillagerData().getProfession();
		if (!on(level) || job != ModVillagers.BUILDER || now == VillagerProfession.NONE || now == VillagerProfession.NITWIT) {
			return false;
		}
		return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
			.map(g -> !g.equals(GlobalPos.of(level.dimension(), station))).orElse(true);
	}

	/** The price in emeralds for retraining {@code villager}: more for a higher level. */
	public static int price(Villager villager) {
		int level = Math.max(1, Math.min(PRICES.length, villager.getVillagerData().getLevel()));
		return PRICES[level - 1];
	}

	/** "Novice", "Journeyman"... (vanilla's names for a villager's level). */
	private static Component levelName(Villager villager) {
		return Component.translatable("merchant.level." + Math.max(1, Math.min(5, villager.getVillagerData().getLevel())));
	}

	/**
	 * The player sneak-right-clicked {@code villager} to make them a {@code job} at {@code station}, and it's a
	 * retraining: the first click shows the offer and its price, a second one soon after pays and retrains them.
	 */
	static InteractionResult offerOrRetrain(ServerPlayer player, Villager villager, BlockPos station, VillagerProfession job) {
		ServerLevel level = (ServerLevel) villager.level();
		int emeralds = price(villager);
		long dollars = (long) emeralds * Money.DOLLARS_PER_EMERALD;
		Component price = Money.describe(dollars, emeralds);
		Component who = villager.getDisplayName();
		Offer offer = OFFERS.get(player.getUUID());
		long now = level.getGameTime();
		if (offer == null || now > offer.until() || !offer.villager().equals(villager.getUUID()) || offer.job() != job
			|| !offer.station().equals(station)) {
			OFFERS.put(player.getUUID(), new Offer(villager.getUUID(), job, station.immutable(), now + CONFIRM_TICKS));
			Chat.chat(player, Component.translatable("message.aliveworkplace.retrain.offer", who,
				Stations.name(villager.getVillagerData().getProfession()), levelName(villager), Stations.name(job), price)
				.withStyle(ChatFormatting.GOLD));
			Chat.chat(player, Component.translatable("message.aliveworkplace.retrain.confirm", CONFIRM_TICKS / 20).withStyle(ChatFormatting.GRAY));
			return InteractionResult.CONSUME;
		}
		OFFERS.remove(player.getUUID());
		if (!Money.canAfford(player, dollars, emeralds) || !Money.charge(player, dollars, emeralds)) {
			level.playSound(null, villager, SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1f, 1f);
			Chat.chat(player, Component.translatable("message.aliveworkplace.retrain.short", who, price, shortBy(player, dollars, emeralds))
				.withStyle(ChatFormatting.YELLOW));
			return InteractionResult.CONSUME;
		}
		dropWork(level, player, villager);
		VillagerProfession old = villager.getVillagerData().getProfession();
		villager.setVillagerData(villager.getVillagerData().setLevel(1)); // the old job's level goes (its trades go with the job)
		villager.setVillagerXp(0);
		Stations.assign(level, villager, station, job);
		level.playSound(null, villager, SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
		Chat.chat(player, Component.translatable("message.aliveworkplace.retrain.done", who, Stations.name(old), Stations.name(job),
			level.getBlockState(station).getBlock().getName(), price).withStyle(ChatFormatting.GREEN));
		return InteractionResult.SUCCESS;
	}

	/** How much more the player needs: "7 emeralds" or "650 CobbleDollars". */
	private static Component shortBy(ServerPlayer player, long dollars, int emeralds) {
		Long balance = Bank.EXTENSION.call(bank -> bank.balance(player), null);
		if (balance != null) {
			return Money.describe(Math.max(0, dollars - balance), 0);
		}
		return Money.describe(0, Math.max(0, emeralds - player.getInventory().countItem(Items.EMERALD)));
	}

	/**
	 * A builder being retrained lets go of what they're building first: a Wonder's part goes on to the rest of its crew,
	 * a crew member stops helping (handing back what they fetched), and their own builds, the one under way and any
	 * waiting, are handed back (Builders.cancel: what's built stays, the blueprint returns still placed, so another builder
	 * carries on from there, the steps already done skipped).
	 */
	static void dropWork(ServerLevel level, ServerPlayer player, Villager villager) {
		for (BuildSite queued : Builders.queue(level, villager)) {
			if (queued.wonder() != null) {
				io.github.jcondedata.aliveworkplace.wonder.WonderCrews.handOn(level, queued, villager);
			} else {
				Builders.cancel(level, queued);
			}
		}
		BuilderJob job = ModAttachments.BUILDER_JOB.get(villager);
		if (job == null) {
			return;
		}
		BuildSite site = BuildSiteManager.get(level).get(job.siteId());
		if (job.helper() || site == null) {
			Builders.stopHelping(level, villager);
			return;
		}
		Component what = io.github.jcondedata.aliveworkplace.blueprint.Blueprints.displayName(site.structure());
		if (site.wonder() != null) {
			Builders.stopHelping(level, villager); // what they carry goes to the Wonder's stockpile
			io.github.jcondedata.aliveworkplace.wonder.WonderCrews.handOn(level, site, villager);
		} else {
			Builders.cancel(level, site);
		}
		Chat.chat(player, Component.translatable("message.aliveworkplace.retrain.build_dropped", villager.getDisplayName(), what)
			.withStyle(ChatFormatting.YELLOW));
	}

	/** Sets the prices from the config ({@code retrainPrices}, Novice to Master). */
	public static void prices(List<Integer> prices) {
		int[] out = new int[5];
		for (int i = 0; i < out.length; i++) {
			out[i] = i < prices.size() && prices.get(i) != null ? prices.get(i) : DEFAULT_PRICES.get(i);
		}
		PRICES = out;
	}

	/** The default prices, Novice to Master. */
	public static final List<Integer> DEFAULT_PRICES = List.of(5, 10, 20, 35, 50);

	private Retraining() {
	}
}
