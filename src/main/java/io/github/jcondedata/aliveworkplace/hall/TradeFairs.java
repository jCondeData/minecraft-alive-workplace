package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.legend.TradeFairPower;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;

/**
 * Trade fairs, the Merchant Prince's {@code trade_fair} (29.17): every {@link TradeFairPower#days} days, in the morning,
 * a fair at the village's Market Square (round the hall without one): {@link TradeFairPower#traders} travelling traders,
 * plus a stall for each village this one trades with (a trader named after it, selling what its Storehouse has spare),
 * bunting round the square (crimson and gold banners, put up on free ground only and taken down after the day,
 * {@link #putUpBunting}), fireworks over the square, every trade at the fair and with the village's villagers {@link TradeFairPower#discount}% cheaper
 * for the day ({@link #discount}); the chronicle notes it.
 */
public final class TradeFairs {
	/** What a village's stall sells at most: this many kinds of goods. */
	static final int STALL_GOODS = 4;

	/** The hall's round: holds the fair in the morning when its day comes (the first the first morning the Prince is here). */
	public static List<WanderingTrader> round(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		if (!entity.fairBunting().isEmpty() && entity.fairDay() != Chronicle.day(level)) {
			takeDownBunting(level, entity); // the fair's day is over (also when the Prince has left since)
		}
		var power = TradeFairPower.of(level, hall);
		if (power.isEmpty()) {
			return List.of();
		}
		long day = Chronicle.day(level);
		long time = level.getDayTime() % VillageNeeds.DAY;
		if ((entity.fairDay() >= 0 && day - entity.fairDay() < power.get().days()) || time < 1000 || time >= 6000) {
			return List.of();
		}
		entity.setFairDay(day);
		return hold(level, hall, power.get());
	}

	/** The fair: the traders and the stalls come to the square (or the hall), and the village celebrates. */
	public static List<WanderingTrader> hold(ServerLevel level, BlockPos hall, TradeFairPower power) {
		BlockPos square = MarketDays.square(level, hall).orElse(hall);
		if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) {
			entity.setFairDay(Chronicle.day(level)); // today's prices are the fair's
		}
		List<WanderingTrader> traders = new ArrayList<>();
		int n = 0;
		for (int i = 0; i < power.traders(); i++) {
			WanderingTrader t = trader(level, hall, square, n++);
			if (t != null) {
				MarketDays.addBlueprintOffer(level, t);
				traders.add(t);
			}
		}
		Caravans.Data data = Caravans.Data.get(level);
		for (Caravans.Village other : data.villages()) {
			if (other.hall().equals(hall) || !(data.routesFrom(hall).contains(other.hall()) || data.routesFrom(other.hall()).contains(hall))) {
				continue;
			}
			WanderingTrader t = trader(level, hall, square, n++);
			if (t == null) {
				continue;
			}
			t.setCustomName(Component.translatable("entity.aliveworkplace.fair_stall", other.name()));
			t.setCustomNameVisible(true);
			t.getOffers().clear();
			for (Map.Entry<Item, Integer> e : spare(level, other.hall()).entrySet()) {
				int count = Math.min(e.getValue(), e.getKey().getDefaultMaxStackSize());
				t.getOffers().add(new MerchantOffer(new ItemCost(Items.EMERALD, Math.max(1, count / 8)), new ItemStack(e.getKey(), count), 2, 1, 0.05f));
			}
			traders.add(t);
		}
		for (WanderingTrader t : traders) {
			for (MerchantOffer offer : t.getOffers()) {
				offer.addToSpecialPriceDiff(-Math.max(1, offer.getBaseCostA().getCount() * power.discount() / 100));
			}
		}
		if (traders.isEmpty()) {
			return traders;
		}
		if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) {
			putUpBunting(level, square, entity);
		}
		for (int i = 0; i < 3; i++) {
			ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
			rocket.set(net.minecraft.core.component.DataComponents.FIREWORKS, new net.minecraft.world.item.component.Fireworks(1,
				List.of(new net.minecraft.world.item.component.FireworkExplosion(net.minecraft.world.item.component.FireworkExplosion.Shape.LARGE_BALL,
					it.unimi.dsi.fastutil.ints.IntList.of(0xE6B53A, 0x9A1F2A), it.unimi.dsi.fastutil.ints.IntList.of(0xF4D670), false, true))));
			level.addFreshEntity(new net.minecraft.world.entity.projectile.FireworkRocketEntity(level, square.getX() + 0.5 + (i - 1) * 3, square.getY() + 1,
				square.getZ() + 0.5, rocket));
		}
		level.playSound(null, square, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.5f, 1.0f);
		Component name = VillageHalls.name(level, hall);
		for (ServerPlayer player : level.getPlayers(p -> p.blockPosition().distSqr(hall) <= (double) VillageHalls.RADIUS * VillageHalls.RADIUS)) {
			Chat.chat(player, Component.translatable("message.aliveworkplace.trade_fair", name, power.discount()).withStyle(ChatFormatting.GOLD));
		}
		Chronicle.record(level, hall, Chronicle.Kind.MARKET, Component.translatable("chronicle.aliveworkplace.trade_fair", traders.size()), true);
		return traders;
	}

	/**
	 * On a fair day every trade with the village's villagers is {@link TradeFairPower#discount}% cheaper too (after
	 * vanilla set the player's special prices; at least 1 emerald off).
	 */
	public static void discount(net.minecraft.world.entity.npc.Villager villager) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return;
		}
		VillageHallBlockEntity hall = CivicEffects.hallOf(villager);
		if (hall == null || hall.fairDay() != Chronicle.day(level)) {
			return;
		}
		TradeFairPower.of(level, hall.getBlockPos()).ifPresent(p -> {
			for (MerchantOffer offer : villager.getOffers()) {
				offer.addToSpecialPriceDiff(-Math.max(1, offer.getBaseCostA().getCount() * p.discount() / 100));
			}
		});
	}

	/** How far from the square's centre the bunting stands (outside the traders' 9 by 9). */
	public static final int BUNTING_RING = 6;
	/** A banner every this many blocks along the ring. */
	public static final int BUNTING_EVERY = 3;
	/** The bunting's two colours, alternating: the Prince's crimson and gold. */
	private static final Block[] BUNTING = {Blocks.RED_BANNER, Blocks.YELLOW_BANNER};

	/**
	 * Puts the fair's bunting up round {@code square}: a banner every {@link #BUNTING_EVERY} blocks on a ring
	 * {@link #BUNTING_RING} blocks out, red and yellow by turns, each facing the square. Only on air over solid ground
	 * (it never replaces a block); the banners are saved in the hall so they come down after the day, even after a reload.
	 */
	public static List<BlockPos> putUpBunting(ServerLevel level, BlockPos square, VillageHallBlockEntity entity) {
		takeDownBunting(level, entity);
		List<BlockPos> placed = new ArrayList<>();
		List<BlockPos> unloaded = new ArrayList<>(entity.fairBunting()); // an earlier fair's, still to come down
		List<BlockPos> ring = new ArrayList<>();
		int r = BUNTING_RING;
		for (int i = -r; i < r; i++) { // round the ring's edge, one side after another
			ring.add(square.offset(i, 0, -r));
		}
		for (int i = -r; i < r; i++) {
			ring.add(square.offset(r, 0, i));
		}
		for (int i = r; i > -r; i--) {
			ring.add(square.offset(i, 0, r));
		}
		for (int i = r; i > -r; i--) {
			ring.add(square.offset(-r, 0, i));
		}
		for (int i = 0; i < ring.size(); i += BUNTING_EVERY) {
			BlockPos column = ring.get(i);
			Block banner = BUNTING[placed.size() % BUNTING.length];
			double dx = column.getX() - square.getX();
			double dz = column.getZ() - square.getZ();
			float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f; // as if placed by someone at the square's centre
			BlockState state = banner.defaultBlockState().setValue(BannerBlock.ROTATION, RotationSegment.convertToSegment(yaw + 180.0f));
			for (int dy = 3; dy >= -2; dy--) {
				BlockPos p = column.above(dy);
				if (level.isLoaded(p) && level.getBlockState(p).isAir() && state.canSurvive(level, p)) {
					level.setBlock(p, state, Block.UPDATE_ALL);
					placed.add(p.immutable());
					break;
				}
			}
		}
		List<BlockPos> all = new ArrayList<>(unloaded);
		all.addAll(placed);
		entity.setFairBunting(all);
		return placed;
	}

	/**
	 * Takes the fair's bunting down: each saved banner that is still the bunting's (a player's block in its place is left
	 * alone), without drops. A banner in a chunk that isn't loaded stays on the list for the next round.
	 */
	public static void takeDownBunting(ServerLevel level, VillageHallBlockEntity entity) {
		List<BlockPos> left = new ArrayList<>();
		for (BlockPos p : entity.fairBunting()) {
			if (!level.isLoaded(p)) {
				left.add(p);
				continue;
			}
			Block block = level.getBlockState(p).getBlock();
			if (block == BUNTING[0] || block == BUNTING[1]) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
		entity.setFairBunting(left);
	}

	private static WanderingTrader trader(ServerLevel level, BlockPos hall, BlockPos square, int n) {
		BlockPos spot = MarketDays.spot(level, square, n);
		if (spot == null) {
			return null;
		}
		WanderingTrader trader = EntityType.WANDERING_TRADER.spawn(level, spot, MobSpawnType.EVENT);
		if (trader != null) {
			trader.setDespawnDelay(Curfew.marketStay(level, hall, MarketDays.STAY));
			trader.setWanderTarget(square);
		}
		return trader;
	}

	/** What the Storehouse of the village at {@code hall} has spare: kinds it holds more than {@link Caravans#KEEP} of. */
	static Map<Item, Integer> spare(ServerLevel level, BlockPos hall) {
		Map<Item, Integer> counts = new LinkedHashMap<>();
		for (BlockPos pos : Caravans.storehouse(level, hall)) {
			if (level.getBlockEntity(pos) instanceof Container c) {
				for (int i = 0; i < c.getContainerSize(); i++) {
					ItemStack s = c.getItem(i);
					if (!s.isEmpty()) {
						counts.merge(s.getItem(), s.getCount(), Integer::sum);
					}
				}
			}
		}
		Map<Item, Integer> out = new LinkedHashMap<>();
		for (Map.Entry<Item, Integer> e : counts.entrySet()) {
			if (out.size() < STALL_GOODS && e.getValue() > Caravans.KEEP) {
				out.put(e.getKey(), e.getValue() - Caravans.KEEP);
			}
		}
		return out;
	}

	private TradeFairs() {
	}
}
