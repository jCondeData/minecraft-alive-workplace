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
 * by M29's inn visitors once they exist (until then nothing reads it, see {@link #LEGEND_VISITS_READ}), {@code festival_every} and
 * {@code festival_cost} by {@code Festivals}, {@code tithe} and {@code trade_prices} by {@code Tithe}, {@code curfew} by
 * {@code hall/Curfew} and the systems it names there, {@code militia} by {@code guard/MilitiaCombat} and
 * {@code work_stops_in_raids} by {@code hall/Conscription}'s one work gate. Later items add their
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
	public static final ResourceLocation FESTIVAL_EVERY = AliveWorkplace.id("festival_every");
	public static final ResourceLocation FESTIVAL_COST = AliveWorkplace.id("festival_cost");
	public static final ResourceLocation TITHE = AliveWorkplace.id("tithe");
	public static final ResourceLocation TRADE_PRICES = AliveWorkplace.id("trade_prices");
	public static final ResourceLocation CURFEW = AliveWorkplace.id("curfew");
	public static final ResourceLocation MILITIA = AliveWorkplace.id("militia");
	public static final ResourceLocation WORK_STOPS_IN_RAIDS = AliveWorkplace.id("work_stops_in_raids");

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
		register(FESTIVAL_EVERY, RecordCodecBuilder.<FestivalEvery>mapCodec(i -> i.group(
			Codec.intRange(1, 64).fieldOf("days").forGetter(FestivalEvery::days),
			JOBS.optionalFieldOf("jobs", List.of()).forGetter(FestivalEvery::jobs)
		).apply(i, FestivalEvery::new)));
		register(FESTIVAL_COST, RecordCodecBuilder.<FestivalCost>mapCodec(i -> i.group(
			Codec.intRange(0, 4096).fieldOf("emeralds").forGetter(FestivalCost::emeralds),
			Codec.intRange(0, 4096).optionalFieldOf("per_villagers", 0).forGetter(FestivalCost::perVillagers),
			JOBS.optionalFieldOf("jobs", List.of()).forGetter(FestivalCost::jobs)
		).apply(i, FestivalCost::new)));
		register(TITHE, RecordCodecBuilder.<TitheShare>mapCodec(i -> i.group(
			Codec.intRange(0, 100).fieldOf("percent").forGetter(TitheShare::percent),
			JOBS.optionalFieldOf("jobs", List.of()).forGetter(TitheShare::jobs)
		).apply(i, TitheShare::new)));
		register(TRADE_PRICES, RecordCodecBuilder.<TradePrices>mapCodec(i -> i.group(
			Codec.intRange(-90, 1000).fieldOf("percent").forGetter(TradePrices::percent),
			JOBS.optionalFieldOf("jobs", List.of()).forGetter(TradePrices::jobs)
		).apply(i, TradePrices::new)));
		register(CURFEW, RecordCodecBuilder.<CurfewRules>mapCodec(i -> i.group(
			Codec.floatRange(0f, 100f).optionalFieldOf("raids", 1f).forGetter(CurfewRules::raids),
			Codec.BOOL.optionalFieldOf("safe_nights", false).forGetter(CurfewRules::safeNights),
			Codec.BOOL.optionalFieldOf("stay_in", false).forGetter(CurfewRules::stayIn),
			JOBS.optionalFieldOf("jobs", List.of()).forGetter(CurfewRules::jobs)
		).apply(i, CurfewRules::new)));
		register(MILITIA, RecordCodecBuilder.<Militia>mapCodec(i -> i.group(
			Codec.floatRange(0f, 100f).optionalFieldOf("damage", 3f).forGetter(Militia::damage),
			Codec.intRange(1, 64).optionalFieldOf("range", 24).forGetter(Militia::range),
			JOBS.optionalFieldOf("jobs", List.of()).forGetter(Militia::jobs)
		).apply(i, Militia::new)));
		register(WORK_STOPS_IN_RAIDS, RecordCodecBuilder.<WorkStops>mapCodec(i -> i.group(
			Codec.BOOL.optionalFieldOf("until_noon", false).forGetter(WorkStops::untilNoon),
			Codec.intRange(1, 64).optionalFieldOf("near").forGetter(WorkStops::near),
			JOBS.optionalFieldOf("jobs", List.of()).forGetter(WorkStops::jobs)
		).apply(i, WorkStops::new)));
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

	/**
	 * {@code festival_every}: the village holds a festival every {@code days} days, not {@code Festivals.EVERY_DAYS}; the
	 * fewest named counts (Festival Season, 30.8). Village-wide: {@code jobs} doesn't narrow it.
	 */
	public record FestivalEvery(int days, List<ResourceLocation> jobs) implements Effect {
		@Override
		public ResourceLocation type() {
			return FESTIVAL_EVERY;
		}
	}

	/**
	 * {@code festival_cost}: each regular festival costs the treasury {@code emeralds}, and one more for every
	 * {@code per_villagers} villagers (0: none more), taken on its morning; several add (Festival Season, 30.8). A
	 * festival called with a cake is free. Village-wide: {@code jobs} doesn't narrow it.
	 */
	public record FestivalCost(int emeralds, int perVillagers, List<ResourceLocation> jobs) implements Effect {
		@Override
		public ResourceLocation type() {
			return FESTIVAL_COST;
		}

		/** What it costs a village of {@code villagers}, in emeralds. */
		public int cost(int villagers) {
			return emeralds + (perVillagers > 0 ? villagers / perVillagers : 0);
		}
	}

	/**
	 * {@code tithe}: {@code percent} of the emeralds players pay the villagers it reaches in trades goes into the village's
	 * treasury, in hundredths, up to its cap ({@code Tithe}); several add (Tithe, 30.8).
	 */
	public record TitheShare(int percent, List<ResourceLocation> jobs) implements Effect {
		@Override
		public ResourceLocation type() {
			return TITHE;
		}
	}

	/**
	 * {@code trade_prices}: the emerald prices of the villagers it reaches are {@code percent} higher, rounded to whole
	 * emeralds ({@code Tithe}); several add (Tithe, 30.8).
	 */
	public record TradePrices(int percent, List<ResourceLocation> jobs) implements Effect {
		@Override
		public ResourceLocation type() {
			return TRADE_PRICES;
		}
	}

	/**
	 * {@code curfew} (Curfew, 30.9; {@code hall/Curfew}): monster raids and bandit camps {@code raids} times as likely
	 * (several multiply); {@code safe_nights}: from dusk to dawn the village's safety counts as full in its wellbeing and a
	 * monster can't hurt a villager asleep in their bed; {@code stay_in}: from dusk to dawn every grown villager but the
	 * guards and mercenaries goes to bed and nobody trades with players, a festival ends at dusk without fireworks, market
	 * traders leave at dusk, and netherworkers and explorers don't set out after midday. Village-wide: {@code jobs} doesn't
	 * narrow it.
	 */
	public record CurfewRules(float raids, boolean safeNights, boolean stayIn, List<ResourceLocation> jobs) implements Effect {
		@Override
		public ResourceLocation type() {
			return CURFEW;
		}
	}

	/**
	 * {@code militia} (Conscription, 30.10; {@code guard/MilitiaCombat}): while the village is raided every grown villager
	 * who isn't ill fights, with a stone sword, {@code damage} a blow (15% more if Strong), going for the nearest raider
	 * within {@code range} blocks. Several: the highest of each. Village-wide: {@code jobs} doesn't narrow it.
	 */
	public record Militia(float damage, int range, List<ResourceLocation> jobs) implements Effect {
		@Override
		public ResourceLocation type() {
			return MILITIA;
		}
	}

	/**
	 * {@code work_stops_in_raids} (Conscription, 30.10; {@code hall/Conscription}): while the village is raided its work
	 * stops, for everyone, or with {@code near} only for villagers with a raider within that many blocks; with
	 * {@code until_noon} it stays stopped until noon the next day. Several: the strictest (one without {@code near}
	 * stops everyone, any {@code until_noon} counts). Village-wide: {@code jobs} doesn't narrow it.
	 */
	public record WorkStops(boolean untilNoon, Optional<Integer> near, List<ResourceLocation> jobs) implements Effect {
		@Override
		public ResourceLocation type() {
			return WORK_STOPS_IN_RAIDS;
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

		/** Days between the village's festivals: the fewest a {@code festival_every} effect names, else {@code usual}. */
		public int festivalEvery(int usual) {
			int out = usual;
			for (Active a : all) {
				if (a.effect() instanceof FestivalEvery f) {
					out = Math.min(out, f.days());
				}
			}
			return out;
		}

		/** Emeralds a regular festival costs a village of {@code villagers} ({@code festival_cost} effects, added; 0 without). */
		public int festivalCost(int villagers) {
			int out = 0;
			for (Active a : all) {
				if (a.effect() instanceof FestivalCost c) {
					out += c.cost(villagers);
				}
			}
			return out;
		}

		/** Percent of what players pay {@code villager} in emeralds that goes to the treasury ({@code tithe} effects, added). */
		public int tithe(Villager villager) {
			int percent = 0;
			for (TitheShare e : of(TitheShare.class, villager)) {
				percent += e.percent();
			}
			return percent;
		}

		/** How much higher {@code villager}'s emerald prices are, in percent ({@code trade_prices} effects, added). */
		public int tradePrices(Villager villager) {
			int percent = 0;
			for (TradePrices e : of(TradePrices.class, villager)) {
				percent += e.percent();
			}
			return percent;
		}

		/** How many times as likely raids and bandit camps are ({@code curfew} effects' {@code raids}, multiplied; 1 without). */
		public float raids() {
			float f = 1f;
			for (Active a : all) {
				if (a.effect() instanceof CurfewRules c) {
					f *= c.raids();
				}
			}
			return f;
		}

		/** Whether the village's nights count as safe ({@code curfew} with {@code safe_nights}). */
		public boolean safeNights() {
			return all.stream().anyMatch(a -> a.effect() instanceof CurfewRules c && c.safeNights());
		}

		/** Whether the village stays in from dusk to dawn ({@code curfew} with {@code stay_in}). */
		public boolean stayIn() {
			return all.stream().anyMatch(a -> a.effect() instanceof CurfewRules c && c.stayIn());
		}

		/** The village's militia: the highest damage and range its {@code militia} effects name, or null without one. */
		@Nullable
		public Militia militia() {
			Militia out = null;
			for (Active a : all) {
				if (a.effect() instanceof Militia m) {
					out = out == null ? m : new Militia(Math.max(out.damage(), m.damage()), Math.max(out.range(), m.range()), List.of());
				}
			}
			return out;
		}

		/**
		 * How the village's work stops in raids: the strictest of its {@code work_stops_in_raids} effects (any without
		 * {@code near}: everyone; else the widest {@code near}; any {@code until_noon}), or null without one.
		 */
		@Nullable
		public WorkStops workStops() {
			WorkStops out = null;
			for (Active a : all) {
				if (a.effect() instanceof WorkStops w) {
					if (out == null) {
						out = w;
					} else {
						Optional<Integer> near = out.near().isEmpty() || w.near().isEmpty() ? Optional.empty()
							: Optional.of(Math.max(out.near().get(), w.near().get()));
						out = new WorkStops(out.untilNoon() || w.untilNoon(), near, List.of());
					}
				}
			}
			return out;
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

	/** The hall of the village {@code villager} lives in (remembered a while), or null; with edicts on or off. */
	@Nullable
	public static VillageHallBlockEntity hallOf(Villager villager) {
		return villager.level() instanceof ServerLevel level ? hall(level, villager) : null;
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
