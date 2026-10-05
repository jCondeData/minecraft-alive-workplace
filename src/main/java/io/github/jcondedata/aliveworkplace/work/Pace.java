package io.github.jcondedata.aliveworkplace.work;

import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.people.Traits;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.research.Research;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

/**
 * A worker's pace (ROADMAP 30.2, docs/design/M30.md "The pace rule"): the one place a job's work time is worked out.
 * Each bonus and penalty is a named {@link Source}. Bonuses (factors under 1) multiply and stop at
 * {@code 100 / maxWorkPace} of the usual time (half at the default 200); penalties multiply after that cap, so an ill
 * worker still works at half the capped pace. A worker's level is not part of the pace ({@code BuilderLevels} applies
 * it before, and outside, the cap).
 *
 * <pre>time = base × levelShare × max(100 / maxWorkPace, Π bonuses) × Π penalties</pre>
 *
 * Nothing is cached here: each source reads what its own system already keeps (the hall's needs and research for 200
 * ticks, the partners for 100, the mood for its own while), so a lookup costs what the five factors cost before.
 */
public final class Pace {
	/** {@code maxWorkPace} in the config: bonuses together make work at most this many percent of the usual pace. */
	public static int MAX_PERCENT = 200;
	public static final int MIN_PERCENT = 100;
	public static final int MAX_MAX_PERCENT = 400;

	public enum Kind {
		/** Makes work quicker (a factor of 1 or under), up to the cap. */
		BONUS,
		/** Makes work slower (a factor of 1 or over), after the cap. */
		PENALTY
	}

	/** What one source does to a villager's work time (1: nothing). */
	@FunctionalInterface
	public interface Factor {
		float of(Villager villager);
	}

	/** How a source reads in the status line (its lang key by default; the partners name themselves). */
	@FunctionalInterface
	public interface Label {
		Component of(Villager villager);
	}

	/** A named source of pace. Its factor is clamped to its kind's side of 1. */
	public record Source(String id, Kind kind, Factor factor, Label label) {
		public Source(String id, Kind kind, Factor factor) {
			this(id, kind, factor, v -> Component.translatable("pace.aliveworkplace.source." + id));
		}

		float of(Villager villager) {
			float f = factor.of(villager);
			if (Float.isNaN(f) || f <= 0f) {
				return 1f;
			}
			return kind == Kind.BONUS ? Math.min(1f, f) : Math.max(1f, f);
		}
	}

	/** A source and what it does to this villager right now. */
	public record Part(Source source, float factor) {
	}

	/**
	 * A villager's pace: {@code factor} times the usual work time; {@code bonuses} the bonuses' product before the cap,
	 * {@code capped} when they reached it; the active sources, bonuses first.
	 */
	public record Breakdown(float factor, float bonuses, boolean capped, List<Part> faster, List<Part> slower) {
		/** How much faster (positive) or slower (negative) than the usual pace, in percent: {@code round((1/f - 1) × 100)}. */
		public int percent() {
			return Math.round((1f / factor - 1f) * 100f);
		}

		public boolean isEmpty() {
			return faster.isEmpty() && slower.isEmpty();
		}
	}

	private static final Map<String, Source> SOURCES = new LinkedHashMap<>();

	public static final Source PARTNERS = register(new Source("partners", Kind.BONUS, Partners::factor,
		v -> Component.translatable("pace.aliveworkplace.source.partners", Partners.names(Partners.helpers(v)))));
	public static final Source WELL_KEPT = register(new Source("well_kept", Kind.BONUS, VillageNeeds::kept));
	public static final Source SWIFT_HANDS = register(new Source("swift_hands", Kind.BONUS, VillageNeeds::swiftHands));
	public static final Source DILIGENT = register(new Source("diligent", Kind.BONUS, Traits::pace));
	public static final Source HAPPY = register(new Source("happy", Kind.BONUS, Moods::pace));
	public static final Source CRAFTSMANSHIP = register(new Source("craftsmanship", Kind.BONUS,
		v -> isCrafter(v.getVillagerData().getProfession()) ? craftsmanship(Research.level(v, Research.Topic.CRAFTSMANSHIP)) : 1f));
	public static final Source EXPEDITIONS = register(new Source("expeditions", Kind.BONUS,
		v -> v.getVillagerData().getProfession() == VillagerProfession.CARTOGRAPHER || v.getVillagerData().getProfession() == ModVillagers.NETHERWORKER
			? expeditions(Research.level(v, Research.Topic.EXPEDITIONS)) : 1f));
	/** The edicts in force in the villager's village ({@code work_pace} effects, 30.3): "the Long Shifts edict". */
	public static final Source EDICTS = register(new Source("edicts", Kind.BONUS,
		io.github.jcondedata.aliveworkplace.hall.CivicEffects::pace, io.github.jcondedata.aliveworkplace.hall.CivicEffects::paceLabel));
	/**
	 * Legends near the worker with a {@code pace} power (29.2): their speed-up, already held to the Legends' own
	 * {@link io.github.jcondedata.aliveworkplace.legend.LegendPowers#PACE_CAP}, as a time factor, then under this cap
	 * with every other bonus.
	 */
	public static final Source LEGEND = register(new Source("legend", Kind.BONUS,
		v -> 1f / io.github.jcondedata.aliveworkplace.legend.LegendPowers.pace(v)));
	/** Born Leaders of the worker's own trade near them (29.7, Gifted): their speed-up as a time factor, under this cap. */
	public static final Source BORN_LEADER = register(new Source("born_leader", Kind.BONUS,
		v -> 1f / io.github.jcondedata.aliveworkplace.legend.GiftedAuras.pace(v)));
	public static final Source ILL = register(new Source("ill", Kind.PENALTY, Sickness::pace));
	public static final Source UNHAPPY = register(new Source("unhappy", Kind.PENALTY, Moods::pace));
	public static final Source LAZY = register(new Source("lazy", Kind.PENALTY, Traits::pace));
	public static final Source BADLY_KEPT = register(new Source("badly_kept", Kind.PENALTY, VillageNeeds::kept));

	/**
	 * Adds a named source (or replaces the one with its id). This milestone's edicts, the Work Horn, tonics and guilds
	 * register theirs the same way, each with a {@code pace.aliveworkplace.source.<id>} lang key.
	 */
	public static Source register(Source source) {
		synchronized (SOURCES) {
			SOURCES.put(source.id(), source);
		}
		return source;
	}

	/** Every source, in the order they were registered. */
	public static Collection<Source> sources() {
		synchronized (SOURCES) {
			return List.copyOf(SOURCES.values());
		}
	}

	/** The jobs Craftsmanship speeds up: the ones that work at a crafting station (CrafterWork and its kinds). */
	public static boolean isCrafter(VillagerProfession job) {
		return job == VillagerProfession.MASON || job == VillagerProfession.TOOLSMITH || job == VillagerProfession.WEAPONSMITH
			|| job == VillagerProfession.FLETCHER || job == VillagerProfession.LEATHERWORKER || job == VillagerProfession.LIBRARIAN
			|| job == ModVillagers.CARPENTER || job == ModVillagers.CHEF || job == ModVillagers.TINKERER;
	}

	/** Craftsmanship: crafting 15% quicker a level. */
	public static float craftsmanship(int level) {
		return Math.max(0.1f, 1f - 0.15f * level);
	}

	/** Expeditions: trips and rests 20% shorter a level. */
	public static float expeditions(int level) {
		return Math.max(0.1f, 1f - 0.2f * level);
	}

	/** The least share of the usual time the bonuses can bring work down to: {@code 100 / maxWorkPace}. */
	public static float cap() {
		return 100f / Math.max(MIN_PERCENT, Math.min(MAX_MAX_PERCENT, MAX_PERCENT));
	}

	/** The rule itself: the bonuses' product, no lower than {@code cap}, times the penalties' product. */
	public static float combine(float bonuses, float penalties, float cap) {
		return Math.max(cap, bonuses) * penalties;
	}

	/** How many times the usual work time {@code villager}'s work takes (1: the usual pace; the level left out). */
	public static float factor(Villager villager) {
		return of(villager).factor();
	}

	/** {@code villager}'s pace with the sources behind it. */
	public static Breakdown of(Villager villager) {
		float bonuses = 1f;
		float penalties = 1f;
		List<Part> faster = new ArrayList<>();
		List<Part> slower = new ArrayList<>();
		for (Source source : sources()) {
			float f = source.of(villager);
			if (f == 1f) {
				continue;
			}
			if (source.kind() == Kind.BONUS) {
				bonuses *= f;
				faster.add(new Part(source, f));
			} else {
				penalties *= f;
				slower.add(new Part(source, f));
			}
		}
		float cap = cap();
		boolean capped = !faster.isEmpty() && bonuses <= cap;
		return new Breakdown(combine(bonuses, penalties, cap), bonuses, capped, List.copyOf(faster), List.copyOf(slower));
	}

	/** {@code ticks} at {@code villager}'s pace, rounded (for work timed without a level: rests, trips). */
	public static int ticks(int ticks, Villager villager) {
		return Math.round(Math.max(0, ticks) * factor(villager));
	}

	/** {@code amount} of progress a step at {@code villager}'s pace makes (lessons, research, calming a horse). */
	public static int progress(int amount, Villager villager) {
		return Math.round(amount / factor(villager));
	}

	/**
	 * "62% faster (Machop from the pasture, a happy mood)", "100% faster: at the cap (…; held back by being ill)", or
	 * null when nothing changes this villager's pace.
	 */
	@org.jetbrains.annotations.Nullable
	public static Component describe(Villager villager) {
		Breakdown pace = of(villager);
		if (pace.isEmpty()) {
			return null;
		}
		int percent = pace.percent();
		Component total = percent > 0 ? Component.translatable("message.aliveworkplace.pace.faster", percent)
			: percent < 0 ? Component.translatable("message.aliveworkplace.pace.slower", -percent)
			: Component.translatable("message.aliveworkplace.pace.usual");
		if (pace.capped()) {
			total = Component.translatable("message.aliveworkplace.pace.capped", total);
		}
		Component why;
		if (pace.slower().isEmpty()) {
			why = list(villager, pace.faster());
		} else if (pace.faster().isEmpty()) {
			why = Component.translatable("message.aliveworkplace.pace.held_back", list(villager, pace.slower()));
		} else {
			why = Component.translatable("message.aliveworkplace.pace.and_held_back", list(villager, pace.faster()), list(villager, pace.slower()));
		}
		return Component.translatable("message.aliveworkplace.pace.line", total, why)
			.withStyle(percent > 0 ? ChatFormatting.GREEN : percent < 0 ? ChatFormatting.RED : ChatFormatting.GRAY);
	}

	private static Component list(Villager villager, List<Part> parts) {
		MutableComponent out = Component.empty();
		for (int i = 0; i < parts.size(); i++) {
			if (i > 0) {
				out.append(Component.literal(", "));
			}
			out.append(parts.get(i).source().label().of(villager));
		}
		return out;
	}

	private Pace() {
	}
}
