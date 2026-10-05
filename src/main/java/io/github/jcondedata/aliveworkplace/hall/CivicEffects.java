package io.github.jcondedata.aliveworkplace.hall;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.Nullable;

/**
 * The effect toolbox (ROADMAP 30.3, docs/design/M30.md "The effect toolbox"): typed effects with codecs, shared by
 * edicts (village-wide), and later tonics (the drinker) and guilds (the members). {@code "type"} picks the codec; an
 * optional {@code jobs} list (profession ids) narrows any effect to those jobs. Each type is read by exactly the system
 * it changes: {@code work_pace} by {@code work/Pace}, {@code mood} by {@code people/Moods.work}, {@code food_use} by
 * {@code VillageNeeds}, {@code births} by {@code VillageGrowth}, {@code sickness} by {@code people/Sickness}, {@code inn} by {@code inn/Innkeepers},
 * {@code market_traders} by {@code MarketDays}, {@code bandit_camps} by {@code guard/BanditCamps} and {@code legend_visits}
 * by M29's inn visitors once they exist (until then nothing reads it, see {@link #LEGEND_VISITS_READ}). Later items add their
 * own types with {@link #register}.
 *
 * <p>The effects in force are summed per hall ({@link Sum}) whenever its edicts change, a data pack reloads or the
 * config switch flips, and kept on the hall; a villager finds their hall as {@code VillageNeeds.factor} does (remembered
 * for {@link #HALL_TICKS}) and reads that sum, so nothing is worked out per tick.
 */
public final class CivicEffects {
	/** How long a villager's hall is remembered before it is looked up again (as {@code VillageNeeds}' pace). */
	private static final long HALL_TICKS = 200;

	/** One effect. {@link #jobs} empty: everyone it reaches. */
	public interface Effect {
		ResourceLocation type();

		List<ResourceLocation> jobs();
	}

	private static final Map<ResourceLocation, MapCodec<? extends Effect>> TYPES = new LinkedHashMap<>();

	/** Every effect, by its {@code "type"}. An unknown type fails to parse (the file is skipped, with its name). */
	public static final Codec<Effect> CODEC = ResourceLocation.CODEC.dispatch("type", Effect::type, CivicEffects::codecOf);

	private static MapCodec<? extends Effect> codecOf(ResourceLocation type) {
		synchronized (TYPES) {
			MapCodec<? extends Effect> codec = TYPES.get(type);
			if (codec == null) {
				throw new IllegalArgumentException("unknown effect type " + type);
			}
			return codec;
		}
	}

	/** Adds an effect type (later items: food_use, births, curfew...). */
	public static <E extends Effect> MapCodec<E> register(ResourceLocation type, MapCodec<E> codec) {
		synchronized (TYPES) {
			TYPES.put(type, codec);
		}
		return codec;
	}

	private static final Codec<List<ResourceLocation>> JOBS = ResourceLocation.CODEC.listOf();

	public static final ResourceLocation WORK_PACE = AliveWorkplace.id("work_pace");
	public static final ResourceLocation MOOD = AliveWorkplace.id("mood");
	public static final ResourceLocation FOOD_USE = AliveWorkplace.id("food_use");
	public static final ResourceLocation BIRTHS = AliveWorkplace.id("births");
	public static final ResourceLocation SICKNESS = AliveWorkplace.id("sickness");
	public static final ResourceLocation INN = AliveWorkplace.id("inn");
	public static final ResourceLocation MARKET_TRADERS = AliveWorkplace.id("market_traders");
	public static final ResourceLocation LEGEND_VISITS = AliveWorkplace.id("legend_visits");
	public static final ResourceLocation BANDIT_CAMPS = AliveWorkplace.id("bandit_camps");

	/**
	 * Whether anything reads {@code legend_visits} yet: M29's inn visitors (29.8) set it when they land, and until then the
	 * Book of Edicts leaves the effect out rather than promise Legends nobody sends.
	 */
	public static boolean LEGEND_VISITS_READ = false;

	static {
		register(WORK_PACE, RecordCodecBuilder.<WorkPace>mapCodec(i -> i.group(
			Codec.intRange(-90, 1000).fieldOf("percent").forGetter(WorkPace::percent),
			JOBS.optionalFieldOf("jobs", List.of()).forGetter(WorkPace::jobs)
		).apply(i, WorkPace::new)));
		register(MOOD, RecordCodecBuilder.<Mood>mapCodec(i -> i.group(
			Codec.intRange(-100, 100).fieldOf("points").forGetter(Mood::points),
			ComponentSerialization.CODEC.fieldOf("reason").forGetter(Mood::reason),
			When.CODEC.optionalFieldOf("when", When.ALWAYS).forGetter(Mood::when),
			JOBS.optionalFieldOf("jobs", List.of()).forGetter(Mood::jobs)
		).apply(i, Mood::new)));
		register(FOOD_USE, RecordCodecBuilder.<FoodUse>mapCodec(i -> i.group(
			Codec.intRange(-100, 1000).fieldOf("percent").forGetter(FoodUse::percent),
			JOBS.optionalFieldOf("jobs", List.of()).forGetter(FoodUse::jobs)
		).apply(i, FoodUse::new)));
		register(BIRTHS, RecordCodecBuilder.<Births>mapCodec(i -> i.group(
			Codec.intRange(1, 24).optionalFieldOf("per_day", 1).forGetter(Births::perDay),
			Codec.intRange(0, 4096).optionalFieldOf("food_needed").forGetter(Births::foodNeeded),
			Codec.intRange(0, 4096).optionalFieldOf("family_meals").forGetter(Births::familyMeals),
			JOBS.optionalFieldOf("jobs", List.of()).forGetter(Births::jobs)
		).apply(i, Births::new)));
		register(SICKNESS, RecordCodecBuilder.<Illness>mapCodec(i -> i.group(
			Codec.intRange(-100, 1000).fieldOf("percent").forGetter(Illness::percent),
			JOBS.optionalFieldOf("jobs", List.of()).forGetter(Illness::jobs)
		).apply(i, Illness::new)));
		register(INN, RecordCodecBuilder.<Inn>mapCodec(i -> i.group(
			Codec.intRange(1, 64).optionalFieldOf("guests").forGetter(Inn::guests),
			Codec.intRange(1, 16).optionalFieldOf("arrivals").forGetter(Inn::arrivals),
			JOBS.optionalFieldOf("jobs", List.of()).forGetter(Inn::jobs)
		).apply(i, Inn::new)));
		register(MARKET_TRADERS, RecordCodecBuilder.<MarketTraders>mapCodec(i -> i.group(
			Codec.intRange(-16, 16).fieldOf("extra").forGetter(MarketTraders::extra),
			JOBS.optionalFieldOf("jobs", List.of()).forGetter(MarketTraders::jobs)
		).apply(i, MarketTraders::new)));
		register(LEGEND_VISITS, RecordCodecBuilder.<LegendVisits>mapCodec(i -> i.group(
			Codec.floatRange(0f, 100f).fieldOf("factor").forGetter(LegendVisits::factor),
			JOBS.optionalFieldOf("jobs", List.of()).forGetter(LegendVisits::jobs)
		).apply(i, LegendVisits::new)));
		register(BANDIT_CAMPS, RecordCodecBuilder.<BanditCampChance>mapCodec(i -> i.group(
			Codec.floatRange(0f, 100f).fieldOf("factor").forGetter(BanditCampChance::factor),
			JOBS.optionalFieldOf("jobs", List.of()).forGetter(BanditCampChance::jobs)
		).apply(i, BanditCampChance::new)));
	}

	/** {@code work_pace}: work {@code percent} faster (a bonus of {@code work/Pace}, up to its cap). */
	public record WorkPace(int percent, List<ResourceLocation> jobs) implements Effect {
		@Override
		public ResourceLocation type() {
			return WORK_PACE;
		}

		/** The work time this makes (20%: 1/1.2). */
		public float factor() {
			return 1f / (1f + percent / 100f);
		}
	}

	/** When a {@code mood} effect counts: always, or only for villagers fed in the last day (Free Bread, 30.6). */
	public enum When {
		ALWAYS, FED_TODAY;

		public static final Codec<When> CODEC = Codec.STRING.comapFlatMap(s -> {
			try {
				return DataResult.success(valueOf(s.toUpperCase(Locale.ROOT)));
			} catch (IllegalArgumentException e) {
				return DataResult.error(() -> "unknown when: " + s);
			}
		}, w -> w.name().toLowerCase(Locale.ROOT));
	}

	/** {@code mood}: {@code points} on a grown villager's mood, with the {@code reason} the hall's list shows. */
	public record Mood(int points, Component reason, When when, List<ResourceLocation> jobs) implements Effect {
		@Override
		public ResourceLocation type() {
			return MOOD;
		}
	}

	/**
	 * {@code food_use}: the village eats {@code percent} more (Free Bread, 30.6): for every meal a villager it reaches eats
	 * from the store, the hall counts that share of another and takes a whole one each time the count reaches 1
	 * ({@code VillageNeeds}).
	 */
	public record FoodUse(int percent, List<ResourceLocation> jobs) implements Effect {
		@Override
		public ResourceLocation type() {
			return FOOD_USE;
		}
	}

	/**
	 * {@code births}: up to {@code per_day} babies a day ({@code VillageGrowth.EVERY} divided by it; several add what each
	 * has over 1), and optionally the meals a baby needs in the store and the meals the family eats for it (the highest
	 * named counts). Village-wide: {@code jobs} doesn't narrow it.
	 */
	public record Births(int perDay, Optional<Integer> foodNeeded, Optional<Integer> familyMeals, List<ResourceLocation> jobs) implements Effect {
		@Override
		public ResourceLocation type() {
			return BIRTHS;
		}
	}

	/** {@code sickness}: villagers it reaches fall ill {@code percent} more often ({@code people/Sickness.dailyChance}). */
	public record Illness(int percent, List<ResourceLocation> jobs) implements Effect {
		@Override
		public ResourceLocation type() {
			return SICKNESS;
		}
	}

	/**
	 * {@code inn}: the village's inns take up to {@code guests} travellers at once (not {@code inn/Innkeepers.MAX_GUESTS})
	 * and up to {@code arrivals} of them arrive a morning (not 1); the highest named counts (Open Gates, 30.7).
	 * Village-wide: {@code jobs} doesn't narrow it.
	 */
	public record Inn(Optional<Integer> guests, Optional<Integer> arrivals, List<ResourceLocation> jobs) implements Effect {
		@Override
		public ResourceLocation type() {
			return INN;
		}
	}

	/** {@code market_traders}: {@code extra} more traders come on market days ({@code MarketDays}); several add. */
	public record MarketTraders(int extra, List<ResourceLocation> jobs) implements Effect {
		@Override
		public ResourceLocation type() {
			return MARKET_TRADERS;
		}
	}

	/** {@code legend_visits}: Legends visit the inn {@code factor} times as often (M29's inn visitors); several multiply. */
	public record LegendVisits(float factor, List<ResourceLocation> jobs) implements Effect {
		@Override
		public ResourceLocation type() {
			return LEGEND_VISITS;
		}
	}

	/** {@code bandit_camps}: bandits make camp near the village {@code factor} times as often ({@code guard/BanditCamps}); several multiply. */
	public record BanditCampChance(float factor, List<ResourceLocation> jobs) implements Effect {
		@Override
		public ResourceLocation type() {
			return BANDIT_CAMPS;
		}
	}

	/** An effect in force and where it comes from (an edict's name, for the status line). */
	public record Active(Effect effect, Component source) {
	}

	/** The effects in force in one village, by type. Built once when they change, read by every lookup. */
	public static final class Sum {
		public static final Sum EMPTY = new Sum(List.of());

		private final List<Active> all;
		private final List<Active> paces = new ArrayList<>();
		private final List<Active> moods = new ArrayList<>();

		public Sum(List<Active> effects) {
			this.all = List.copyOf(effects);
			for (Active a : all) {
				if (a.effect() instanceof WorkPace) {
					paces.add(a);
				} else if (a.effect() instanceof Mood) {
					moods.add(a);
				}
			}
		}

		public List<Active> all() {
			return all;
		}

		public boolean isEmpty() {
			return all.isEmpty();
		}

		/** Every effect of {@code type} that reaches {@code villager} (by job). */
		public <E extends Effect> List<E> of(Class<E> type, Villager villager) {
			List<E> out = new ArrayList<>();
			for (Active a : all) {
				if (type.isInstance(a.effect()) && reaches(a.effect(), villager)) {
					out.add(type.cast(a.effect()));
				}
			}
			return out;
		}

		/** The work time the {@code work_pace} effects make for {@code villager} (each its own bonus, multiplied). */
		public float pace(Villager villager) {
			float f = 1f;
			for (Active a : paces) {
				if (reaches(a.effect(), villager)) {
					f *= ((WorkPace) a.effect()).factor();
				}
			}
			return f;
		}

		/** Where {@code villager}'s {@code work_pace} comes from ("the Long Shifts edict"). */
		public List<Component> paceSources(Villager villager) {
			List<Component> out = new ArrayList<>();
			for (Active a : paces) {
				if (reaches(a.effect(), villager) && ((WorkPace) a.effect()).percent() > 0 && !out.contains(a.source())) {
					out.add(a.source());
				}
			}
			return out;
		}

		/** How much more {@code villager} eats, in percent ({@code food_use} effects that reach them, added). */
		public int foodUse(Villager villager) {
			int percent = 0;
			for (FoodUse e : of(FoodUse.class, villager)) {
				percent += e.percent();
			}
			return percent;
		}

		/** How much more often {@code villager} falls ill, in percent ({@code sickness} effects that reach them, added). */
		public int sickness(Villager villager) {
			int percent = 0;
			for (Illness e : of(Illness.class, villager)) {
				percent += e.percent();
			}
			return percent;
		}

		/** Babies the village may have a day: 1, and what each {@code births} effect has over 1. */
		public int birthsPerDay() {
			int perDay = 1;
			for (Active a : all) {
				if (a.effect() instanceof Births b) {
					perDay += b.perDay() - 1;
				}
			}
			return perDay;
		}

		/** Meals a baby needs in the store: the highest a {@code births} effect names, else {@code usual}. */
		public int foodNeeded(int usual) {
			return highest(Births::foodNeeded, usual);
		}

		/** Meals the family eats for a baby: the highest a {@code births} effect names, else {@code usual}. */
		public int familyMeals(int usual) {
			return highest(Births::familyMeals, usual);
		}

		private int highest(java.util.function.Function<Births, Optional<Integer>> field, int usual) {
			int out = -1;
			for (Active a : all) {
				if (a.effect() instanceof Births b && field.apply(b).isPresent()) {
					out = Math.max(out, field.apply(b).get());
				}
			}
			return out < 0 ? usual : out;
		}

		/** Travellers an inn takes at once: the highest an {@code inn} effect names, else {@code usual}. */
		public int innGuests(int usual) {
			int out = -1;
			for (Active a : all) {
				if (a.effect() instanceof Inn inn && inn.guests().isPresent()) {
					out = Math.max(out, inn.guests().get());
				}
			}
			return out < 0 ? usual : out;
		}

		/** Travellers who may arrive at an inn a morning: the highest an {@code inn} effect names, else 1. */
		public int innArrivals() {
			int out = 1;
			for (Active a : all) {
				if (a.effect() instanceof Inn inn && inn.arrivals().isPresent()) {
					out = Math.max(out, inn.arrivals().get());
				}
			}
			return out;
		}

		/** Extra traders on market days ({@code market_traders} effects, added; may be negative). */
		public int marketTraders() {
			int extra = 0;
			for (Active a : all) {
				if (a.effect() instanceof MarketTraders m) {
					extra += m.extra();
				}
			}
			return extra;
		}

		/** How many times as often Legends visit the inn ({@code legend_visits} effects, multiplied; 1 without). */
		public float legendVisits() {
			float f = 1f;
			for (Active a : all) {
				if (a.effect() instanceof LegendVisits l) {
					f *= l.factor();
				}
			}
			return f;
		}

		/** How many times as often bandits make camp near the village ({@code bandit_camps} effects, multiplied; 1 without). */
		public float banditCamps() {
			float f = 1f;
			for (Active a : all) {
				if (a.effect() instanceof BanditCampChance b) {
					f *= b.factor();
				}
			}
			return f;
		}

		/** The {@code mood} effects that count for {@code villager} now. */
		public List<Mood> moods(Villager villager, long now) {
			List<Mood> out = new ArrayList<>();
			for (Active a : moods) {
				Mood mood = (Mood) a.effect();
				if (reaches(mood, villager) && (mood.when() == When.ALWAYS || !VillageNeeds.isHungry(villager, now) && ModAttachments.LAST_MEAL.has(villager))) {
					out.add(mood);
				}
			}
			return out;
		}
	}

	/** Whether {@code effect} reaches {@code villager}: no jobs listed, or theirs is one. */
	public static boolean reaches(Effect effect, Villager villager) {
		if (effect.jobs().isEmpty()) {
			return true;
		}
		ResourceLocation job = BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession());
		return effect.jobs().contains(job);
	}

	private record Kept(@Nullable VillageHallBlockEntity hall, long until) {
	}

	private static final Map<Villager, Kept> HALLS = new WeakHashMap<>();

	/** The effects in force where {@code villager} lives (none outside a village with a hall, or with edicts off). */
	public static Sum of(Villager villager) {
		if (!Edicts.ENABLED || !(villager.level() instanceof ServerLevel level)) {
			return Sum.EMPTY;
		}
		VillageHallBlockEntity hall = hall(level, villager);
		return hall == null ? Sum.EMPTY : hall.civicEffects();
	}

	/** The effects in force in {@code hall}'s village (none with edicts off). */
	public static Sum of(@Nullable VillageHallBlockEntity hall) {
		return !Edicts.ENABLED || hall == null ? Sum.EMPTY : hall.civicEffects();
	}

	/** The effects in force in the village of the hall at {@code pos} (none without a hall there, or with edicts off). */
	public static Sum of(ServerLevel level, net.minecraft.core.BlockPos pos) {
		return of(level.getBlockEntity(pos) instanceof VillageHallBlockEntity hall ? hall : null);
	}

	@Nullable
	private static VillageHallBlockEntity hall(ServerLevel level, Villager villager) {
		long now = level.getGameTime();
		Kept kept;
		synchronized (HALLS) {
			kept = HALLS.get(villager);
		}
		if (kept != null && now < kept.until() && (kept.hall() == null || !kept.hall().isRemoved())) {
			return kept.hall();
		}
		VillageHallBlockEntity hall = VillageHalls.nearest(level, villager.blockPosition())
			.map(pos -> level.getBlockEntity(pos) instanceof VillageHallBlockEntity entity ? entity : null).orElse(null);
		synchronized (HALLS) {
			HALLS.put(villager, new Kept(hall, now + HALL_TICKS));
		}
		return hall;
	}

	/** The work time {@code villager}'s village's {@code work_pace} effects make (a source of {@code work/Pace}). */
	public static float pace(Villager villager) {
		return of(villager).pace(villager);
	}

	/** How the edicts' pace reads in the status line: "the Long Shifts edict". */
	public static Component paceLabel(Villager villager) {
		List<Component> sources = of(villager).paceSources(villager);
		MutableComponent out = Component.empty();
		for (int i = 0; i < sources.size(); i++) {
			if (i > 0) {
				out.append(Component.literal(", "));
			}
			out.append(Component.translatable("pace.aliveworkplace.source.edict", sources.get(i)));
		}
		return out;
	}

	/** Forget the villagers' halls (tests). */
	public static void forget() {
		synchronized (HALLS) {
			HALLS.clear();
		}
	}

	private CivicEffects() {
	}
}
