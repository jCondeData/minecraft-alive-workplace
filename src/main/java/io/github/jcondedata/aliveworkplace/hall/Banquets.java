package io.github.jcondedata.aliveworkplace.hall;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.build.BuildReserve;
import io.github.jcondedata.aliveworkplace.legend.BanquetPower;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Banquets, the Grand Chef's {@code banquet} (29.18): every {@link BanquetPower#days} days, after work
 * ({@link Festivals#START}), the village gathers at its Market Square (round the hall without one) with
 * {@link Festivals}' gathering, and at supper ({@link #SUPPER}) each grown-up eats {@link BanquetPower#meals} meals from
 * the store, of as many kinds as it has (a guest's meals are of different kinds, and kinds nobody has had yet come
 * first). Everyone who came is {@link BanquetPower#mood} happier for {@link BanquetPower#moodDays} days ({@link #mood});
 * for {@link BanquetPower#growthDays} days the village's wait between babies is halved ({@link #growing},
 * {@code VillageGrowth.every}); the chronicle notes it. No banquet on a festival's day: it waits for the next.
 *
 * <p>The day and whether its feast was eaten are saved on the hall ({@code banquetDay}, {@code banquetEaten}), so a
 * banquet that was interrupted by a reload still feasts that evening, and never twice.
 */
public final class Banquets {
	/** The time of day the feast is eaten (the gathering starts at {@link Festivals#START}). */
	public static final long SUPPER = 10500;
	/** A banquet starts no later than this (villagers go to bed at 12000). */
	public static final long LAST_START = 12000;
	static final int TICK_EVERY = 40;

	/** A villager came to a banquet: the day, and how much happier it made them for how many days. */
	public record Feasted(long day, int mood, int days) {
		public static final Codec<Feasted> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("day").forGetter(Feasted::day),
			Codec.INT.optionalFieldOf("mood", 20).forGetter(Feasted::mood),
			Codec.INT.optionalFieldOf("days", 3).forGetter(Feasted::days)
		).apply(i, Feasted::new));
	}

	/** What a feast came to: who came, the grown-ups who ate, the meals eaten and of how many kinds. */
	public record Feast(int came, int fed, int meals, int kinds) {
	}

	/** Halls whose banquet is on now, by dimension (their rounds keep it up to date). */
	private static final Map<ResourceKey<Level>, Set<BlockPos>> ON = new ConcurrentHashMap<>();

	public static void init() {
		Platform.get().onLevelTick(level -> {
			if (level.getGameTime() % TICK_EVERY == 0) {
				tick(level);
			}
		});
	}

	/** True while the banquet of the village round {@code hall} is on: its day, from the gathering till dusk. */
	public static boolean isOn(ServerLevel level, BlockPos hall) {
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return false;
		}
		long t = Festivals.timeOfDay(level);
		return entity.banquetDay() >= 0 && entity.banquetDay() == Chronicle.day(level) && t >= Festivals.START && t < Festivals.END;
	}

	/** Where the banquet is: the village's Market Square, else the hall. */
	public static BlockPos square(ServerLevel level, BlockPos hall) {
		return MarketDays.square(level, hall).orElse(hall);
	}

	/**
	 * The hall's round: with a Grand Chef in the village, a banquet when its day comes (the first the first evening they
	 * are here), and its feast at supper.
	 */
	public static void round(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		long today = Chronicle.day(level);
		long t = Festivals.timeOfDay(level);
		var power = BanquetPower.of(level, hall);
		if (power.isPresent() && (entity.banquetDay() < 0 || today - entity.banquetDay() >= power.get().days())
			&& t >= Festivals.START && t < LAST_START && entity.festivalDay() != today) {
			call(level, hall, entity);
		}
		if (entity.banquetDay() == today && !entity.banquetEaten() && t >= SUPPER) {
			feast(level, hall, entity, power.orElse(BanquetPower.DEFAULT));
		}
		Set<BlockPos> on = ON.computeIfAbsent(level.dimension(), k -> ConcurrentHashMap.newKeySet());
		if (isOn(level, hall)) {
			on.add(hall.immutable());
		} else {
			on.remove(hall);
		}
	}

	/** The banquet is today: the village is called to the square. */
	public static void call(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		entity.setBanquet(Chronicle.day(level), false);
		BlockPos square = square(level, hall);
		level.playSound(null, square, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.5f, 1.2f);
		Component name = VillageHalls.name(level, hall);
		for (ServerPlayer player : nearby(level, hall)) {
			Chat.chat(player, Component.translatable("message.aliveworkplace.banquet.call", name).withStyle(ChatFormatting.GOLD));
		}
	}

	/**
	 * The feast: everyone in the village (not asleep) came; each grown-up eats {@link BanquetPower#meals} meals from the
	 * store, each of a different kind while the store has them, kinds nobody has had tonight first; everyone who came is
	 * remembered for the mood. The chronicle notes it. Returns what it came to.
	 */
	public static Feast feast(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, BanquetPower power) {
		long today = Chronicle.day(level);
		entity.setBanquet(today, true);
		BuildReserve store = BuildReserve.of(level,
			VillageNeeds.store(level, hall)); // B84: never what a build near the store still needs
		Set<Item> served = new HashSet<>();
		int came = 0;
		int fed = 0;
		int meals = 0;
		for (Villager villager : level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), v -> v.isAlive() && !v.isSleeping())) {
			came++;
			ModAttachments.BANQUET.set(villager, new Feasted(today, power.mood(), power.moodDays()));
			io.github.jcondedata.aliveworkplace.people.Moods.forget(villager);
			if (villager.isBaby()) {
				continue;
			}
			Set<Item> mine = new HashSet<>();
			for (int i = 0; i < power.meals(); i++) {
				ItemStack meal = serve(level, store, served, mine);
				if (meal.isEmpty()) {
					break;
				}
				meals++;
				mine.add(meal.getItem());
				served.add(meal.getItem());
				io.github.jcondedata.aliveworkplace.people.Diet.ate(villager, meal);
				ModAttachments.LAST_MEAL.set(villager, level.getGameTime());
				villager.heal(4f);
				level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, meal), villager.getX(), villager.getEyeY() - 0.2, villager.getZ(),
					8, 0.15, 0.1, 0.15, 0.05);
			}
			if (!mine.isEmpty()) {
				fed++;
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getEyeY() + 0.4, villager.getZ(), 5, 0.3, 0.2, 0.3, 0.0);
			}
		}
		BlockPos square = square(level, hall);
		level.playSound(null, square, SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.5f, 1.0f);
		level.playSound(null, square, SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 1.0f, 1.0f);
		Component name = VillageHalls.name(level, hall);
		Chronicle.record(level, hall, Chronicle.Kind.FESTIVAL, meals > 0
			? io.github.jcondedata.aliveworkplace.work.Words.counted("chronicle.aliveworkplace.banquet", served.size(), came, served.size())
			: Component.translatable("chronicle.aliveworkplace.banquet_bare", came), true);
		for (ServerPlayer player : nearby(level, hall)) {
			Chat.chat(player, (meals > 0 ? io.github.jcondedata.aliveworkplace.work.Words.counted("message.aliveworkplace.banquet.feast", served.size(), name, served.size())
				: Component.translatable("message.aliveworkplace.banquet.bare", name)).withStyle(meals > 0 ? ChatFormatting.GOLD : ChatFormatting.YELLOW));
		}
		return new Feast(came, fed, meals, served.size());
	}

	/** One meal from the store: a kind this guest hasn't had tonight and nobody has, else one they haven't, else any. */
	private static ItemStack serve(ServerLevel level, BuildReserve store, Set<Item> served, Set<Item> mine) {
		ItemStack meal = store.takeOne(level, s -> VillageNeeds.isMeal(s) && !mine.contains(s.getItem()) && !served.contains(s.getItem()));
		if (meal.isEmpty()) {
			meal = store.takeOne(level, s -> VillageNeeds.isMeal(s) && !mine.contains(s.getItem()));
		}
		if (meal.isEmpty()) {
			meal = store.takeOne(level, VillageNeeds::isMeal);
		}
		io.github.jcondedata.aliveworkplace.build.MaterialLedger.eaten(meal); // a soak's count (27.22); nothing when empty
		return meal;
	}

	/** The mood of the last banquet {@code villager} came to, while it lasts; null after. */
	@Nullable
	public static LegendPowers.MoodReason mood(ServerLevel level, Villager villager) {
		Feasted f = ModAttachments.BANQUET.get(villager);
		if (f == null || Chronicle.day(level) - f.day() >= f.days()) {
			return null;
		}
		return new LegendPowers.MoodReason(Component.translatable("mood.aliveworkplace.reason.banquet"), f.mood());
	}

	/** True for the {@link BanquetPower#growthDays} days from a banquet's feast: the village's wait between babies is halved. */
	public static boolean growing(ServerLevel level, BlockPos hall) {
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) || entity.banquetDay() < 0 || !entity.banquetEaten()) {
			return false;
		}
		int days = BanquetPower.of(level, hall).orElse(BanquetPower.DEFAULT).growthDays();
		return Chronicle.day(level) - entity.banquetDay() < days;
	}

	/** Every {@link #TICK_EVERY} ticks: the villagers off work gather at the banquet's square; the tables smoke. */
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
				Festivals.gatherAt(level, villager, square);
			}
			level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, square.getX() + 0.5, square.getY() + 1.2, square.getZ() + 0.5, 1, 0.6, 0.1, 0.6, 0.01);
		}
	}

	private static List<ServerPlayer> nearby(ServerLevel level, BlockPos hall) {
		double r = VillageHalls.RADIUS + 32;
		return new ArrayList<>(level.getPlayers(p -> p.blockPosition().distSqr(hall) <= r * r));
	}

	/** Forgets which banquets are on (tests). */
	public static void forget() {
		ON.clear();
	}

	private Banquets() {
	}
}
