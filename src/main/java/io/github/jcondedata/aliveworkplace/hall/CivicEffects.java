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
 * it changes: {@code work_pace} by {@code work/Pace}, {@code mood} by {@code people/Moods.work}. Later items add their
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
