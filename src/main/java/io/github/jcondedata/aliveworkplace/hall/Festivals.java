package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Festivals: every {@link #EVERY_DAYS} days a village with a Village Hall and at least {@link #MIN_VILLAGERS} villagers
 * holds a festival, and a player can call one from the hall with a cake (one every {@link #CALL_REST} days at most).
 * After work ({@link #START}) the villagers gather round the village's bell (or the hall) for a feast from the store,
 * music and dancing; at dusk ({@link #FIREWORKS}) fireworks go up (under Curfew it ends at dusk without them, 30.9). Everyone who came is in a better mood for
 * {@link #MOOD_DAYS} days, and players in the village are Heroes of the Village for the evening: cheaper trades, and
 * the villagers throw them gifts. {@code festivals} in the config turns the regular ones off (called ones still happen).
 *
 * <p>Festival Season (30.8): {@code festival_every} shortens the days between a village's regular festivals (counted
 * per hall from its own day), and {@code festival_cost} has the treasury pay for each one on its morning; a treasury
 * that can't pay means no festival, a line in the chronicle and a village {@link #DISAPPOINTED} less happy that day. A
 * festival called with a cake stays free.
 */
public final class Festivals {
	public static boolean ENABLED = true;
	public static final int EVERY_DAYS = 8;
	public static final int MIN_VILLAGERS = 6;
	public static final int CALL_REST = 3;
	/** Time of day the gathering begins (after work), the fireworks go up, and it's over. */
	public static final long START = 9000;
	public static final long FIREWORKS = 11500;
	/** How near the square a villager counts in the fireworks' crowd. */
	public static final int CROWD_RANGE = 16;
	public static final long END = 13000;
	/** Days a festival lifts the moods of everyone who came, and by how much. */
	public static final int MOOD_DAYS = 2;
	public static final int MOOD = 15;
	/** How much less happy a village is the day its festival fell through for want of money. */
	public static final int DISAPPOINTED = 5;
	/** How close to the square counts as being at the festival. */
	static final int SQUARE = 7;
	static final int TICK_EVERY = 40;
	private static final int[] COLORS = {0xF5C542, 0xE83F3F, 0x3FA7F5, 0x5FD35F, 0xC35FE8, 0xFFFFFF, 0xF58A3F};

	/** Halls whose festival is on now, by dimension (their rounds keep it up to date). */
	private static final Map<ResourceKey<Level>, Set<BlockPos>> ON = new ConcurrentHashMap<>();

	public static void init() {
		Platform.get().onLevelTick(level -> {
			if (level.getGameTime() % TICK_EVERY == 0) {
				tick(level);
			}
		});
	}

	static long timeOfDay(ServerLevel level) {
		return level.getDayTime() % VillageNeeds.DAY;
	}

	/** Which of the {@link #EVERY_DAYS} days is the festival day of the village round {@code hall}. */
	static long offset(BlockPos hall) {
		return Math.floorMod(hall.asLong() * 0x9E3779B97F4A7C15L >>> 20, EVERY_DAYS);
	}

	/** Days between the regular festivals of {@code entity}'s village: {@link #EVERY_DAYS}, or fewer under Festival Season. */
	public static int every(VillageHallBlockEntity entity) {
		return Math.max(1, CivicEffects.of(entity).festivalEvery(EVERY_DAYS));
	}

	/** Emeralds a regular festival costs the treasury of {@code entity}'s village of {@code villagers} (0: free). */
	public static int cost(VillageHallBlockEntity entity, int villagers) {
		return CivicEffects.of(entity).festivalCost(villagers);
	}

	/** True while the festival of the village round {@code hall} is on. */
	public static boolean isOn(ServerLevel level, BlockPos hall) {
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return false;
		}
		long t = timeOfDay(level);
		return entity.festivalDay() == Chronicle.day(level) && t >= START && t < Curfew.festivalEnd(entity); // Curfew: over at dusk
	}

	/** The day of the village's next festival (today if it's still to come or on). */
	public static long nextDay(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		long today = Chronicle.day(level);
		boolean over = timeOfDay(level) >= Curfew.festivalEnd(entity);
		if (entity.festivalDay() > today || entity.festivalDay() == today && !over) {
			return entity.festivalDay();
		}
		int every = every(entity);
		long day = today + Math.floorMod(offset(hall) - today, every);
		return day == today && over ? day + every : day;
	}

	/** The hall's round: a festival due today is planned; while one is on, the feast and the heroes. */
	public static void round(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, int villagers) {
		long today = Chronicle.day(level);
		if (ENABLED && entity.festivalDay() < today && entity.festivalMissed() < today && villagers >= MIN_VILLAGERS
			&& Math.floorMod(today - offset(hall), every(entity)) == 0 && timeOfDay(level) < START) {
			int cost = cost(entity, villagers);
			if (cost <= 0) {
				plan(level, hall, entity, today);
			} else if (entity.treasury() >= cost * 100) {
				entity.setTreasury(entity.treasury() - cost * 100);
				plan(level, hall, entity, today, cost);
			} else {
				noMoney(level, hall, entity, today, cost);
			}
		}
		Set<BlockPos> on = ON.computeIfAbsent(level.dimension(), k -> ConcurrentHashMap.newKeySet());
		if (!isOn(level, hall)) {
			on.remove(hall);
			return;
		}
		on.add(hall.immutable());
		if (entity.feastDay() < today) {
			entity.setFeastDay(today);
			feast(level, hall);
		}
		int left = (int) (Curfew.festivalEnd(entity) - timeOfDay(level));
		for (ServerPlayer player : level.getPlayers(p -> VillageHalls.area(hall).contains(p.position()))) {
			MobEffectInstance hero = player.getEffect(MobEffects.HERO_OF_THE_VILLAGE);
			if (hero == null || hero.getAmplifier() == 0 && hero.getDuration() < left) {
				player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, left, 0, true, true));
			}
		}
	}

	/** Sets the festival for {@code day} and tells the players about. */
	static void plan(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, long day) {
		plan(level, hall, entity, day, 0);
	}

	/** Sets the festival for {@code day} and tells the players about, with what the treasury {@code paid} for it (emeralds). */
	static void plan(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, long day, int paid) {
		entity.setFestivalDay(day);
		boolean today = day == Chronicle.day(level);
		Component name = VillageHalls.name(level, hall);
		Component message = paid > 0 ? io.github.jcondedata.aliveworkplace.work.Words.counted("message.aliveworkplace.festival.today_paid", paid, name, paid)
			: Component.translatable(today ? "message.aliveworkplace.festival.today" : "message.aliveworkplace.festival.tomorrow", name);
		for (ServerPlayer player : nearby(level, hall)) {
			Chat.chat(player, message.copy().withStyle(ChatFormatting.GOLD));
		}
	}

	/** The treasury can't pay for the festival due {@code today}: none, the chronicle says why, the village is disappointed. */
	static void noMoney(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, long today, int cost) {
		entity.setFestivalMissed(today);
		Component name = VillageHalls.name(level, hall);
		Chronicle.record(level, hall, Chronicle.Kind.FESTIVAL,
			io.github.jcondedata.aliveworkplace.work.Words.counted("chronicle.aliveworkplace.festival_no_money", cost, cost));
		for (ServerPlayer player : nearby(level, hall)) {
			Chat.chat(player, io.github.jcondedata.aliveworkplace.work.Words.counted("message.aliveworkplace.festival.no_money", cost, name, cost)
				.withStyle(ChatFormatting.YELLOW));
		}
	}

	private static List<ServerPlayer> nearby(ServerLevel level, BlockPos hall) {
		return level.getPlayers(p -> p.blockPosition().distSqr(hall) <= (double) (VillageHalls.RADIUS + 32) * (VillageHalls.RADIUS + 32));
	}

	/**
	 * True if the festival of {@code villager}'s village fell through today for want of money (and none was called
	 * instead): they are {@link #DISAPPOINTED} less happy.
	 */
	public static boolean disappointed(ServerLevel level, Villager villager) {
		VillageHallBlockEntity entity = CivicEffects.hallOf(villager);
		long today = Chronicle.day(level);
		return entity != null && entity.festivalMissed() == today && entity.festivalDay() != today;
	}

	/** {@code player} calls a festival from the hall with a cake; returns what to tell them. */
	public static Component call(ServerLevel level, BlockPos hall, ServerPlayer player) {
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return Component.empty();
		}
		long today = Chronicle.day(level);
		long t = timeOfDay(level);
		if (entity.festivalDay() > today || entity.festivalDay() == today && t < Curfew.festivalEnd(entity)) {
			return Component.translatable("message.aliveworkplace.festival.planned").withStyle(ChatFormatting.YELLOW);
		}
		if (entity.festivalCalled() >= 0 && today - entity.festivalCalled() < CALL_REST) {
			return io.github.jcondedata.aliveworkplace.work.Words.counted("message.aliveworkplace.festival.too_soon", CALL_REST - (today - entity.festivalCalled()),
				CALL_REST - (today - entity.festivalCalled()))
				.withStyle(ChatFormatting.YELLOW);
		}
		if (!player.getAbilities().instabuild) {
			int slot = -1;
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				if (player.getInventory().getItem(i).is(Items.CAKE)) {
					slot = i;
					break;
				}
			}
			if (slot < 0) {
				return Component.translatable("message.aliveworkplace.festival.cake").withStyle(ChatFormatting.YELLOW);
			}
			player.getInventory().getItem(slot).shrink(1);
		}
		entity.setFestivalCalled(today);
		plan(level, hall, entity, t < START ? today : today + 1);
		level.playSound(null, hall, SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1f, 1f);
		return Component.translatable(t < START ? "message.aliveworkplace.festival.called_today" : "message.aliveworkplace.festival.called_tomorrow")
			.withStyle(ChatFormatting.GREEN);
	}

	/** The feast: every grown villager eats from the store, everyone is remembered as having come. */
	static void feast(ServerLevel level, BlockPos hall) {
		List<BlockPos> store = VillageNeeds.store(level, hall);
		long today = Chronicle.day(level);
		int villagers = 0;
		int fed = 0;
		for (Villager villager : level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), Villager::isAlive)) {
			villagers++;
			ModAttachments.FESTIVAL_DAY.set(villager, today);
			if (!villager.isBaby() && VillageNeeds.eat(level, villager, store)) {
				fed++;
			}
		}
		Chronicle.record(level, hall, Chronicle.Kind.FESTIVAL, fed > 0
			? Component.translatable("chronicle.aliveworkplace.festival", villagers, fed)
			: Component.translatable("chronicle.aliveworkplace.festival_hungry", villagers));
		level.playSound(null, hall, SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.5f, 1.1f);
	}

	/** True if {@code villager} came to a festival in the last {@link #MOOD_DAYS} days. */
	public static boolean enjoyedLately(ServerLevel level, Villager villager) {
		Long day = ModAttachments.FESTIVAL_DAY.get(villager);
		return day != null && Chronicle.day(level) - day <= MOOD_DAYS;
	}

	/** Where the festival is: the village's bell nearest the hall, else the hall. */
	public static BlockPos square(ServerLevel level, BlockPos hall) {
		return level.getPoiManager().findClosest(h -> h.is(PoiTypes.MEETING), hall, VillageHalls.RADIUS, PoiManager.Occupancy.ANY).orElse(hall);
	}

	/** Every {@link #TICK_EVERY} ticks: the villagers off work gather at the square, dance; at dusk, fireworks. */
	static void tick(ServerLevel level) {
		Set<BlockPos> on = ON.get(level.dimension());
		if (on == null || on.isEmpty()) {
			return;
		}
		for (BlockPos hall : List.copyOf(on)) {
			if (!isOn(level, hall)) {
				on.remove(hall);
				continue;
			}
			BlockPos square = square(level, hall);
			for (Villager villager : level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), v -> v.isAlive() && !v.isSleeping())) {
				gather(level, villager, square);
			}
			if (timeOfDay(level) >= FIREWORKS && level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) {
				fireworks(level, hall, entity, square, level.random);
			}
			if (timeOfDay(level) >= FIREWORKS && level.getBlockEntity(hall) instanceof VillageHallBlockEntity held && Curfew.fireworks(held)
				&& level.random.nextFloat() < 0.4f) {
				BlockPos column = square.offset(level.random.nextInt(13) - 6, 0, level.random.nextInt(13) - 6);
				launch(level, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column));
			}
		}
	}

	/**
	 * While the fireworks are up: the crowd at the square is counted and kept on the hall ({@code festivalCrowd}, for 29.2's
	 * {@code festival_crowd}), and a Legend who comes to a festival that big may arrive (29.8, once a day). Returns the guest.
	 */
	@org.jetbrains.annotations.Nullable
	public static Villager fireworks(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, BlockPos square, net.minecraft.util.RandomSource random) {
		entity.setFestivalCrowd(level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(square).inflate(CROWD_RANGE), Villager::isAlive).size());
		return io.github.jcondedata.aliveworkplace.legend.LegendGuests.visit(level, hall, "festival", square, random);
	}

	/** A villager off work (idle, meeting or playing) comes to the square; one there dances now and then. */
	static void gather(ServerLevel level, Villager villager, BlockPos square) {
		Activity activity = villager.getBrain().getActiveNonCoreActivity().orElse(null);
		if (activity != Activity.IDLE && activity != Activity.MEET && activity != Activity.PLAY) {
			return;
		}
		if (!square.closerToCenterThan(villager.position(), SQUARE)) {
			BlockPos spot = square.offset(level.random.nextInt(9) - 4, 0, level.random.nextInt(9) - 4);
			villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(spot, 0.55f, 1));
			return;
		}
		if (level.random.nextFloat() < 0.25f && villager.onGround()) {
			villager.getJumpControl().jump();
			level.sendParticles(ParticleTypes.NOTE, villager.getX(), villager.getY() + 2.2, villager.getZ(), 1, 0.2, 0.1, 0.2, level.random.nextDouble());
		}
		ModAttachments.FESTIVAL_DAY.set(villager, Chronicle.day(level));
	}

	/** A firework from {@code at}, in two or three of the festival's colours. */
	public static FireworkRocketEntity launch(ServerLevel level, BlockPos at) {
		ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
		FireworkExplosion.Shape[] shapes = {FireworkExplosion.Shape.LARGE_BALL, FireworkExplosion.Shape.SMALL_BALL, FireworkExplosion.Shape.STAR,
			FireworkExplosion.Shape.BURST};
		int a = COLORS[level.random.nextInt(COLORS.length)];
		int b = COLORS[level.random.nextInt(COLORS.length)];
		rocket.set(DataComponents.FIREWORKS, new Fireworks(1 + level.random.nextInt(2), List.of(new FireworkExplosion(
			shapes[level.random.nextInt(shapes.length)], IntList.of(a, b), IntList.of(COLORS[level.random.nextInt(COLORS.length)]),
			level.random.nextBoolean(), level.random.nextBoolean()))));
		FireworkRocketEntity firework = new FireworkRocketEntity(level, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, rocket);
		level.addFreshEntity(firework);
		return firework;
	}

	/** Forgets which festivals are on (tests). */
	public static void forget() {
		ON.clear();
	}

	private Festivals() {
	}
}
