package io.github.jcondedata.aliveworkplace.legend;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.MarketDays;
import io.github.jcondedata.aliveworkplace.hall.VillageAdvice;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;

/**
 * The Seer (ROADMAP 29.16), a Rare Legend {@code legends/seer.json}: a guest at the village's finished Chapel at midnight
 * under a full moon, 1 time in 2 (29.8), or born to a Cleric (29.7). By day they keep to the Chapel; at night end-rod
 * motes drift round them. Their powers:
 * <ul>
 *   <li>{@code foretell}: each dawn (the hall's first round before {@link #DAWN_TO}) the village's players hear, in chat
 *   and on the hall's "What next?", whether raiders come that night and from which side, the next festival and market
 *   day, and the next day's guest. The night's raid is rolled then ({@link VillageRaids#rollNight}) and the night goes as
 *   rolled; the next day's guest is rolled then too ({@link LegendGuests#rollAhead}), so the Seer is never wrong. Through
 *   {@link Legends#foretold} the Seer is M32's warning of attacks, {@link ForetellPower#warningDays} days ahead;</li>
 *   <li>{@code bless_weddings}: a wedding at the Chapel with the Seer within {@link #CHAPEL_REACH} blocks is blessed: the
 *   couple is {@link BlessWeddingsPower#mood} happier for {@link BlessWeddingsPower#days} days, and their first baby
 *   comes within {@link BlessWeddingsPower#babyDays} days when a bed is free, past {@code VillageGrowth}'s daily wait
 *   (once).</li>
 * </ul>
 * The foretelling is saved on the hall ({@link State}, tag {@code seer}); a blessing on each of the couple
 * ({@link ModAttachments#SEER_BLESSING}).
 */
public final class Seer {
	public static final ResourceLocation ID = AliveWorkplace.id("seer");
	/** The dawn: the foretelling comes at the hall's first round of the day before this time (day time). */
	public static final long DAWN_TO = LegendGuests.MORNING_TO;
	/** How near the Chapel the Seer must be for a wedding there to be blessed. */
	public static final int CHAPEL_REACH = 32;
	/** By day the Seer walks back to the Chapel when further than this from it. */
	static final int KEEP_NEAR = 6;

	/** {@code foretell}: the dawn foretelling, and {@code warning_days} days' warning of attacks (M32) of {@code kinds} (none named: all). */
	public record ForetellPower(int warningDays, List<String> kinds) implements Power {
		static ForetellPower read(JsonObject json) {
			int days = json.has("warning_days") ? json.get("warning_days").getAsInt() : 2;
			if (days < 0) {
				throw new IllegalArgumentException("'warning_days' below 0");
			}
			List<String> kinds = new ArrayList<>();
			if (json.has("kinds")) {
				for (JsonElement e : json.getAsJsonArray("kinds")) {
					kinds.add(e.getAsString());
				}
			}
			return new ForetellPower(days, List.copyOf(kinds));
		}

		@Override
		public String type() {
			return "foretell";
		}

		public boolean covers(String kind) {
			return kinds.isEmpty() || kinds.contains(kind);
		}

		@Override
		public Component describe() {
			return Component.translatable("legend.aliveworkplace.power.foretell", warningDays);
		}
	}

	/** {@code bless_weddings}: a Chapel wedding's couple is {@code mood} happier for {@code days} days, their first baby within {@code baby_days} days. */
	public record BlessWeddingsPower(int mood, int days, int babyDays) implements Power {
		static BlessWeddingsPower read(JsonObject json) {
			int mood = json.has("mood") ? json.get("mood").getAsInt() : 10;
			int days = json.has("days") ? json.get("days").getAsInt() : 7;
			int babyDays = json.has("baby_days") ? json.get("baby_days").getAsInt() : 2;
			if (days < 0 || babyDays < 0) {
				throw new IllegalArgumentException("'days' or 'baby_days' below 0");
			}
			return new BlessWeddingsPower(mood, days, babyDays);
		}

		@Override
		public String type() {
			return "bless_weddings";
		}

		@Override
		public Component describe() {
			return Component.translatable("legend.aliveworkplace.power.bless_weddings", mood, days, babyDays);
		}
	}

	static void register() {
		Powers.register("foretell", ForetellPower::read);
		Powers.register("bless_weddings", BlessWeddingsPower::read);
	}

	/** A place's guest on {@code day} as rolled ahead: the Legend who comes, or nobody. */
	public record Told(long day, Optional<ResourceLocation> legend) {
		public static final Codec<Told> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("day").forGetter(Told::day),
			ResourceLocation.CODEC.optionalFieldOf("legend").forGetter(Told::legend)
		).apply(i, Told::new));
	}

	/**
	 * The hall's last dawn foretelling: the day it was told ({@code -1}: never), tonight's raid (whether, the side as an angle,
	 * the hour, and whether it has begun), the next festival and market day ({@code -1}: none) and the guests rolled ahead
	 * (today's and tomorrow's) by {@code <place>@<day>}. Every field has a default.
	 */
	public record State(long toldDay, boolean raid, double angle, long raidAt, boolean raidStarted, long festival, long market,
						Map<String, Told> guests) {
		public static final State EMPTY = new State(-1, false, 0, 0, false, -1, -1, Map.of());
		public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.optionalFieldOf("told_day", -1L).forGetter(State::toldDay),
			Codec.BOOL.optionalFieldOf("raid", false).forGetter(State::raid),
			Codec.DOUBLE.optionalFieldOf("angle", 0.0).forGetter(State::angle),
			Codec.LONG.optionalFieldOf("raid_at", 0L).forGetter(State::raidAt),
			Codec.BOOL.optionalFieldOf("raid_started", false).forGetter(State::raidStarted),
			Codec.LONG.optionalFieldOf("festival", -1L).forGetter(State::festival),
			Codec.LONG.optionalFieldOf("market", -1L).forGetter(State::market),
			Codec.unboundedMap(Codec.STRING, Told.CODEC).optionalFieldOf("guests", Map.of()).forGetter(State::guests)
		).apply(i, State::new));

		public static State load(CompoundTag tag) {
			return tag.contains("seer") ? CODEC.parse(NbtOps.INSTANCE, tag.get("seer")).result().orElse(EMPTY) : EMPTY;
		}

		public void save(CompoundTag tag) {
			if (toldDay >= 0) {
				CODEC.encodeStart(NbtOps.INSTANCE, this).result().ifPresent(t -> tag.put("seer", t));
			}
		}

		/** Tonight as foretold. */
		public VillageRaids.Night night() {
			return new VillageRaids.Night(raid, angle, raidAt);
		}

		State started() {
			return new State(toldDay, raid, angle, raidAt, true, festival, market, guests);
		}

		/** The Legend told to come on {@code day}, and where. */
		public Optional<Map.Entry<String, ResourceLocation>> guestOn(long day) {
			for (Map.Entry<String, Told> e : guests.entrySet()) {
				if (e.getValue().day() == day && e.getValue().legend().isPresent()) {
					String key = e.getKey();
					return Optional.of(Map.entry(key.contains("@") ? key.substring(0, key.indexOf('@')) : key, e.getValue().legend().get()));
				}
			}
			return Optional.empty();
		}
	}

	/** A blessed wedding, on each of the couple: the day, the mood and how long it lasts, the days the baby may take, whether it's still to come. */
	public record Blessing(long day, int mood, int days, int babyDays, boolean babyDue) {
		public static final Codec<Blessing> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("day").forGetter(Blessing::day),
			Codec.INT.optionalFieldOf("mood", 10).forGetter(Blessing::mood),
			Codec.INT.optionalFieldOf("days", 7).forGetter(Blessing::days),
			Codec.INT.optionalFieldOf("baby_days", 2).forGetter(Blessing::babyDays),
			Codec.BOOL.optionalFieldOf("baby_due", false).forGetter(Blessing::babyDue)
		).apply(i, Blessing::new));
	}

	// Who.

	private static boolean inVillage(ServerLevel level, LegendPowers.Active a, BlockPos hall) {
		return a.data().hall().map(hall::equals).orElseGet(() -> VillageHalls.nearest(level, a.villager().blockPosition()).map(hall::equals).orElse(false));
	}

	/** A Legend settled in the village round {@code hall} with a power of {@code kind} working (not on strike). */
	static <P extends Power> Optional<LegendPowers.Active> withPower(ServerLevel level, BlockPos hall, Class<P> kind) {
		return LegendPowers.settled(level).stream()
			.filter(a -> !a.legend().powers(kind).isEmpty() && a.villager().isAlive() && inVillage(level, a, hall))
			.findFirst();
	}

	/** The Seer foretelling for the village round {@code hall}, if there is one. */
	public static Optional<LegendPowers.Active> foreteller(ServerLevel level, BlockPos hall) {
		return withPower(level, hall, ForetellPower.class);
	}

	/** Days of warning the village round {@code hall} gets of attacks of {@code kind} (M32: {@link Legends#foretold}). */
	public static int warningDays(ServerLevel level, BlockPos hall, String kind) {
		int days = 0;
		for (LegendPowers.Active a : LegendPowers.settled(level)) {
			if (a.villager().isAlive() && inVillage(level, a, hall)) {
				for (ForetellPower p : a.legend().powers(ForetellPower.class)) {
					if (p.covers(kind)) {
						days = Math.max(days, p.warningDays());
					}
				}
			}
		}
		return days;
	}

	// The dawn foretelling.

	/** The hall's round: at dawn, once a day, a Seer in the village foretells. */
	public static void round(ServerLevel level, BlockPos hall, RandomSource random) {
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return;
		}
		if (level.getDayTime() % VillageNeeds.DAY < DAWN_TO && entity.seer().toldDay() != Chronicle.day(level)) {
			foreteller(level, hall).ifPresent(seer -> foretell(level, hall, seer.villager(), random));
		}
	}

	/**
	 * {@code seer} foretells for the village round {@code hall} now: tonight's raid and tomorrow's guest are rolled with
	 * {@code random} and kept on the hall, and the village's players hear it. Returns what was foretold.
	 */
	public static State foretell(ServerLevel level, BlockPos hall, Villager seer, RandomSource random) {
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		long today = Chronicle.day(level);
		int villagers = VillageHalls.census(level, hall).villagers();
		VillageRaids.Night night = VillageRaids.rollNight(level, hall, villagers, entity.lastRaidDay(), random);
		Map<String, Told> guests = new HashMap<>();
		entity.seer().guests().forEach((key, told) -> {
			if (told.day() == today) {
				guests.put(key, told); // today's, rolled yesterday, still hold
			}
		});
		if (guests.isEmpty()) {
			// The first foretelling (or the first after a missed dawn): today is rolled now too, unannounced, so that
			// tomorrow's roll knows whether today's guest will still be staying.
			LegendGuests.rollAhead(level, hall, today, false, random).forEach((place, told) -> guests.put(key(place, told.day()), told));
		}
		boolean guestToday = guests.values().stream().anyMatch(t -> t.day() == today && t.legend().isPresent());
		LegendGuests.rollAhead(level, hall, today + 1, guestToday, random).forEach((place, told) -> guests.put(key(place, told.day()), told));
		long festival = Festivals.nextDay(level, hall, entity);
		boolean festivalOn = entity.festivalDay() >= festival || Festivals.ENABLED && villagers >= Festivals.MIN_VILLAGERS;
		long market = MarketDays.ENABLED && MarketDays.square(level, hall).isPresent() ? MarketDays.nextDay(level, hall, entity.lastMarketDay()) : -1;
		State state = new State(today, night.raid(), night.angle(), night.at(), false, festivalOn ? festival : -1, market, Map.copyOf(guests));
		entity.setSeer(state);
		List<Component> lines = lines(level, hall, state);
		double r = VillageHalls.RADIUS + 32;
		for (ServerPlayer player : level.getPlayers(p -> p.blockPosition().distSqr(hall) <= r * r)) {
			Chat.chat(player, Component.translatable("message.aliveworkplace.seer.dawn", seer.getDisplayName(), VillageHalls.name(level, hall))
				.withStyle(ChatFormatting.LIGHT_PURPLE));
			for (Component line : lines) {
				Chat.chat(player, line);
			}
		}
		level.sendParticles(ParticleTypes.END_ROD, seer.getX(), seer.getY() + 1.6, seer.getZ(), 12, 0.4, 0.4, 0.4, 0.02);
		level.playSound(null, seer.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1.2f, 0.8f);
		return state;
	}

	/** The guest told for {@code place} on {@code day}, if the Seer rolled that day ahead. */
	public static Optional<Told> toldGuest(VillageHallBlockEntity entity, String place, long day) {
		return Optional.ofNullable(entity.seer().guests().get(key(place, day)));
	}

	/** How a guest rolled ahead is kept: {@code <place>@<day>}. */
	static String key(String place, long day) {
		return place + "@" + day;
	}

	/** Tonight as the Seer foretold it this dawn, if they did. */
	public static Optional<State> tonight(ServerLevel level, BlockPos hall) {
		return level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity && entity.seer().toldDay() == Chronicle.day(level)
			? Optional.of(entity.seer()) : Optional.empty();
	}

	/** The foretold raid has begun. */
	public static void raidStarted(ServerLevel level, BlockPos hall) {
		if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) {
			entity.setSeer(entity.seer().started());
		}
	}

	/** "today", "tomorrow", "in 3 days". */
	public static Component when(long day, long today) {
		long in = day - today;
		return in <= 0 ? Component.translatable("message.aliveworkplace.seer.when.today")
			: in == 1 ? Component.translatable("message.aliveworkplace.seer.when.tomorrow")
			: Component.translatable("message.aliveworkplace.seer.when.days", in);
	}

	/** The guest told for tomorrow: the Legend and the place, as words. */
	private static Optional<Component[]> guestWords(State state) {
		return state.guestOn(state.toldDay() + 1).flatMap(e -> Legends.get(e.getValue()).map(legend -> new Component[]{
			legend.titleText(), Component.translatable("message.aliveworkplace.seer.place." + e.getKey())}));
	}

	/** What {@code state} foretells, a line each: tonight, the next festival, the next market, tomorrow's guest. */
	public static List<Component> lines(ServerLevel level, BlockPos hall, State state) {
		long today = state.toldDay();
		List<Component> out = new ArrayList<>();
		out.add(state.raid() ? Component.translatable("message.aliveworkplace.seer.raid", state.night().side(hall)).withStyle(ChatFormatting.RED)
			: Component.translatable("message.aliveworkplace.seer.calm").withStyle(ChatFormatting.GREEN));
		out.add((state.festival() >= 0 ? Component.translatable("message.aliveworkplace.seer.festival", when(state.festival(), today), state.festival())
			: Component.translatable("message.aliveworkplace.seer.no_festival")).withStyle(ChatFormatting.GOLD));
		out.add((state.market() >= 0 ? Component.translatable("message.aliveworkplace.seer.market", when(state.market(), today), state.market())
			: Component.translatable("message.aliveworkplace.seer.no_market")).withStyle(ChatFormatting.GOLD));
		out.add(guestWords(state).map(w -> Component.translatable("message.aliveworkplace.seer.guest", w[0], w[1]))
			.orElseGet(() -> Component.translatable("message.aliveworkplace.seer.no_guest")).withStyle(ChatFormatting.AQUA));
		return out;
	}

	/** The hall's "What next?" with a Seer in the village who foretold today: tonight, the next days, tomorrow's guest (first). */
	public static List<VillageAdvice.Tip> tips(ServerLevel level, BlockPos hall) {
		Optional<State> told = tonight(level, hall);
		if (told.isEmpty() || foreteller(level, hall).isEmpty()) {
			return List.of();
		}
		State state = told.get();
		long today = state.toldDay();
		List<VillageAdvice.Tip> out = new ArrayList<>();
		out.add(state.raid() ? new VillageAdvice.Tip("seer_raid", Items.CROSSBOW, state.night().side(hall))
			: new VillageAdvice.Tip("seer_calm", Items.ENDER_EYE));
		Component festival = state.festival() >= 0 ? when(state.festival(), today) : Component.translatable("message.aliveworkplace.seer.none");
		Component market = state.market() >= 0 ? when(state.market(), today) : Component.translatable("message.aliveworkplace.seer.none");
		out.add(new VillageAdvice.Tip("seer_days", Items.CLOCK, festival, market));
		Optional<Component[]> guest = guestWords(state);
		out.add(guest.map(w -> new VillageAdvice.Tip("seer_guest", Items.NETHER_STAR, w[0], w[1]))
			.orElseGet(() -> new VillageAdvice.Tip("seer_no_guest", Items.NETHER_STAR)));
		return out;
	}

	// Blessed weddings.

	/**
	 * {@code a} and {@code b} were wed at {@code venue}: blessed if it's the village's Chapel and a Legend with
	 * {@code bless_weddings} is settled in the village within {@link #CHAPEL_REACH} of it. Returns whether it was.
	 */
	public static boolean bless(ServerLevel level, BlockPos hall, Villager a, Villager b, BlockPos venue) {
		Optional<LegendPowers.Active> seer = withPower(level, hall, BlessWeddingsPower.class);
		Optional<BlockPos> chapel = LegendGuests.chapel(level, hall);
		if (seer.isEmpty() || chapel.isEmpty() || !chapel.get().equals(venue)
			|| seer.get().villager().distanceToSqr(venue.getCenter()) > (double) CHAPEL_REACH * CHAPEL_REACH) {
			return false;
		}
		BlessWeddingsPower power = seer.get().legend().powers(BlessWeddingsPower.class).get(0);
		Blessing blessing = new Blessing(Chronicle.day(level), power.mood(), power.days(), power.babyDays(), true);
		for (Villager v : List.of(a, b)) {
			ModAttachments.SEER_BLESSING.set(v, blessing);
			io.github.jcondedata.aliveworkplace.people.Moods.forget(v);
			level.sendParticles(ParticleTypes.END_ROD, v.getX(), v.getY() + 1.2, v.getZ(), 15, 0.4, 0.6, 0.4, 0.03);
		}
		Villager who = seer.get().villager();
		level.sendParticles(ParticleTypes.ENCHANT, who.getX(), who.getY() + 1.5, who.getZ(), 30, 0.5, 0.5, 0.5, 0.5);
		level.playSound(null, venue, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 2f, 1.2f);
		double r = VillageHalls.RADIUS + 32;
		for (ServerPlayer player : level.getPlayers(p -> p.blockPosition().distSqr(hall) <= r * r)) {
			Chat.chat(player, Component.translatable("message.aliveworkplace.seer.blessed", who.getDisplayName(), a.getDisplayName(), b.getDisplayName())
				.withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		Chronicle.record(level, hall, Chronicle.Kind.WEDDING, Component.translatable("chronicle.aliveworkplace.blessed_wedding", who.getDisplayName(),
			a.getDisplayName(), b.getDisplayName()));
		return true;
	}

	/** The mood of a blessed wedding, while it lasts. */
	public static LegendPowers.MoodReason blessedMood(ServerLevel level, Villager villager) {
		Blessing b = ModAttachments.SEER_BLESSING.get(villager);
		if (b == null || Chronicle.day(level) - b.day() >= b.days()) {
			return null;
		}
		return new LegendPowers.MoodReason(Component.translatable("mood.aliveworkplace.reason.blessed"), b.mood());
	}

	/** A blessed couple of the village round {@code hall} whose first baby is due (within its days, both there), or empty. */
	public static List<Villager> blessedCouple(ServerLevel level, BlockPos hall) {
		long today = Chronicle.day(level);
		List<Villager> village = level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), v -> v.isAlive() && !v.isBaby());
		for (Villager v : village) {
			Blessing b = ModAttachments.SEER_BLESSING.get(v);
			Couples.Partner p = Couples.partner(v);
			if (b == null || !b.babyDue() || today - b.day() > b.babyDays() || p == null || !p.married()) {
				continue;
			}
			if (level.getEntity(p.id()) instanceof Villager other && village.contains(other)) {
				return List.of(v, other);
			}
		}
		return List.of();
	}

	/** The blessed couple's baby came: no other comes early. */
	public static void babyCame(List<Villager> parents) {
		for (Villager v : parents) {
			Blessing b = ModAttachments.SEER_BLESSING.get(v);
			if (b != null) {
				ModAttachments.SEER_BLESSING.set(v, new Blessing(b.day(), b.mood(), b.days(), b.babyDays(), false));
			}
		}
	}

	// The Seer themselves.

	/**
	 * Every second of a Seer's life (from {@link Legends#tick}): at night end-rod motes drift round them; by day a settled
	 * Seer walks back to the village's Chapel when they've strayed from it (every 5 seconds).
	 */
	static void tick(Villager villager) {
		LegendData data = ModAttachments.LEGEND.get(villager);
		if (data == null || !ID.equals(data.id()) || !(villager.level() instanceof ServerLevel level)) {
			return;
		}
		long time = level.getDayTime() % VillageNeeds.DAY;
		if (time >= 12500 && time < 23500 && !villager.isInvisible()) {
			level.sendParticles(ParticleTypes.END_ROD, villager.getX(), villager.getY() + villager.getBbHeight() * 0.7, villager.getZ(), 3,
				0.6, 0.6, 0.6, 0.004);
		}
		if (villager.tickCount % 100 == 0 && data.settled() && !data.onStrike() && time >= 1000 && time < 11000 && !villager.isSleeping()) {
			Optional<BlockPos> hall = data.hall().isPresent() ? data.hall() : VillageHalls.nearest(level, villager.blockPosition());
			Optional<BlockPos> chapel = hall.flatMap(h -> LegendGuests.chapel(level, h));
			if (chapel.isPresent() && villager.blockPosition().distSqr(chapel.get()) > KEEP_NEAR * KEEP_NEAR) {
				villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(chapel.get(), 0.5f, 2));
			}
		}
	}

	private Seer() {
	}
}
