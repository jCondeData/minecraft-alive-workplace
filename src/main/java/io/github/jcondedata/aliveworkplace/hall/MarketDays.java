package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.work.Walker;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import org.jetbrains.annotations.Nullable;

/**
 * Market days: once a week, in the morning, a village with a Village Hall and a finished Market Square holds a market —
 * {@link #TRADERS} travelling traders come to the square with their wares, and a blueprint or two to sell (a starter
 * building in another style, a decoration), and leave again by nightfall. Players in the village are told, and the
 * chronicle notes it. {@code marketDays} in the config turns it off.
 */
public final class MarketDays {
	public static boolean ENABLED = true;
	/** Market day comes every this many days. */
	public static final int EVERY_DAYS = 7;
	/** How many traders come. */
	public static final int TRADERS = 2;
	/** How long they stay (until about nightfall). */
	public static final int STAY = 10000;
	/** Blueprints cost this many emeralds at the market. */
	static final int BLUEPRINT_PRICE = 6;

	/** Whether it's market day and market time (the morning) for this hall now. */
	public static boolean isMarketMorning(ServerLevel level, BlockPos hall, long lastMarketDay) {
		long day = Chronicle.day(level);
		long time = level.getDayTime() % VillageNeeds.DAY;
		return day != lastMarketDay && isMarketDay(hall, day) && time >= 1000 && time < 6000;
	}

	/** Whether {@code day} (a {@link Chronicle#day}) is market day for the village round {@code hall}. */
	public static boolean isMarketDay(BlockPos hall, long day) {
		return Math.floorMod(day + hall.hashCode(), EVERY_DAYS) == 0;
	}

	/**
	 * The village's next market day (a {@link Chronicle#day}): today while this morning's market is still to come, else the
	 * next market day after it (the Seer's foretelling, 29.16).
	 */
	public static long nextDay(ServerLevel level, BlockPos hall, long lastMarketDay) {
		long today = Chronicle.day(level);
		long day = today + Math.floorMod(-(today + hall.hashCode()), EVERY_DAYS);
		return day == today && (lastMarketDay == today || level.getDayTime() % VillageNeeds.DAY >= 6000) ? day + EVERY_DAYS : day;
	}

	/** The village's Market Square (a finished one within the hall's reach), or empty. */
	public static Optional<BlockPos> square(ServerLevel level, BlockPos hall) {
		return BuildSiteManager.get(level).finishedNear(level, hall, VillageHalls.RADIUS).stream()
			.filter(f -> BlueprintStyles.base(f.structure()).equals(StarterBlueprints.MARKET_SQUARE.id()))
			.map(f -> io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline.bounds(f.placement(), StarterBlueprints.MARKET_SQUARE.size()))
			.map(box -> new BlockPos(box.getCenter().getX(), box.minY() + 1, box.getCenter().getZ()))
			.findFirst();
	}

	/** The hall's round: holds the market if it's market day (returns true if one began). */
	public static boolean tick(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		if (!ENABLED || !isMarketMorning(level, hall, entity.lastMarketDay())) {
			return false;
		}
		Optional<BlockPos> square = square(level, hall);
		if (square.isEmpty()) {
			return false;
		}
		entity.setLastMarketDay(Chronicle.day(level));
		return !hold(level, hall, square.get()).isEmpty();
	}

	/**
	 * How many traders come to the village's market: its rank's, one more a level of Commerce research, and the
	 * {@code market_traders} effects in force (Open Gates, 30.7: one more); never fewer than none.
	 */
	public static int traders(ServerLevel level, BlockPos hall) {
		return Math.max(0, VillageRanks.marketTraders(VillageRanks.of(level, hall))
			+ io.github.jcondedata.aliveworkplace.research.Research.at(level, hall).level(io.github.jcondedata.aliveworkplace.research.Research.Topic.COMMERCE)
			+ CivicEffects.of(level, hall).marketTraders());
	}

	/** The traders come to the square at {@code square}. */
	public static List<WanderingTrader> hold(ServerLevel level, BlockPos hall, BlockPos square) {
		List<WanderingTrader> traders = new ArrayList<>();
		int count = traders(level, hall);
		for (int i = 0; i < count; i++) {
			BlockPos spot = spot(level, square, i);
			if (spot == null) {
				break;
			}
			WanderingTrader trader = EntityType.WANDERING_TRADER.spawn(level, spot, MobSpawnType.EVENT);
			if (trader == null) {
				continue;
			}
			trader.setDespawnDelay(Curfew.marketStay(level, hall, STAY)); // Curfew: gone by dusk
			trader.setWanderTarget(square);
			addBlueprintOffer(level, trader);
			traders.add(trader);
		}
		if (traders.isEmpty()) {
			return traders;
		}
		level.playSound(null, square, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.5f, 1.2f);
		Component name = VillageHalls.name(level, hall);
		for (ServerPlayer player : level.getPlayers(p -> p.blockPosition().distSqr(hall) <= (double) VillageHalls.RADIUS * VillageHalls.RADIUS)) {
			Chat.chat(player, Component.translatable("message.aliveworkplace.market.day", name).withStyle(ChatFormatting.GOLD));
		}
		Chronicle.record(level, hall, Chronicle.Kind.MARKET, Component.translatable("chronicle.aliveworkplace.market", traders.size()), true);
		io.github.jcondedata.aliveworkplace.legend.LegendGuests.visit(level, hall, "market", square, level.random); // a Legend may come with them (29.8)
		return traders;
	}

	/** One offer on top of the trader's own: a starter blueprint in a style, or a decoration. */
	static void addBlueprintOffer(ServerLevel level, WanderingTrader trader) {
		List<StarterBlueprints.Entry> pool = new ArrayList<>();
		for (StarterBlueprints.Entry e : StarterBlueprints.ALL) {
			if (io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades.baseOf(e.id()).isEmpty()) {
				pool.add(e);
			}
		}
		pool.addAll(StarterBlueprints.DECORATIONS);
		StarterBlueprints.Entry entry = pool.get(level.random.nextInt(pool.size()));
		List<BlueprintStyles.Style> styles = BlueprintStyles.all();
		ResourceLocation id = styles.isEmpty() || level.random.nextBoolean() ? entry.id()
			: BlueprintStyles.styled(entry.id(), styles.get(level.random.nextInt(styles.size())).name());
		if (BlueprintLibrary.get(level, id).isEmpty()) {
			return;
		}
		trader.getOffers().add(new MerchantOffer(new ItemCost(Items.EMERALD, BLUEPRINT_PRICE), BlueprintItem.create(id, entry.size()), 1, 1, 0.05f));
	}

	@Nullable
	private static BlockPos spot(ServerLevel level, BlockPos square, int n) {
		List<BlockPos> spots = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(square.offset(-4, -2, -4), square.offset(4, 3, 4))) {
			if (Walker.canStand(level, p)) {
				spots.add(p.immutable());
			}
		}
		if (spots.isEmpty()) {
			return null;
		}
		spots.sort(java.util.Comparator.comparingDouble(p -> p.distSqr(square)));
		return spots.get(Math.min(spots.size() - 1, n * 5));
	}

	private MarketDays() {
	}
}
