package io.github.jcondedata.aliveworkplace.city;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

/**
 * The Steward's wishes (ROADMAP 27.6): once a morning, back at the hall from his rounds, he ranks the rules that hold
 * ({@link StewardRules}) into the day's wishes, highest priority first, at most {@link #MAX_WISHES}. They are saved on
 * the hall with the day they were ranked, so a restart the same day keeps them and they are ranked again only the next
 * morning. Carrying them out is 27.7–27.9, which call {@link #carriedOut}: that counts towards the rule's {@code max} and
 * starts its {@code cooldown_days}. {@code /workplace steward explain} shows every rule with its conditions
 * ({@link #explain}).
 */
public final class StewardWishes {
	/** Wishes kept a day at most. */
	public static final int MAX_WISHES = 8;

	/** One wish: the rule, what to do, how pressing, and why (the rule's {@code why} key and its conditions' numbers). */
	public record Wish(ResourceLocation rule, StewardRules.Effect effect, int priority, String why, List<Long> numbers) {
		public static final Codec<Wish> CODEC = RecordCodecBuilder.create(i -> i.group(
			ResourceLocation.CODEC.fieldOf("rule").forGetter(Wish::rule),
			StewardRules.Effect.CODEC.fieldOf("do").forGetter(Wish::effect),
			Codec.INT.fieldOf("priority").forGetter(Wish::priority),
			Codec.STRING.fieldOf("why").forGetter(Wish::why),
			Codec.LONG.listOf().optionalFieldOf("numbers", List.of()).forGetter(Wish::numbers)
		).apply(i, Wish::new));

		/** "3 villagers have no bed". */
		public Component reason() {
			return Component.translatable(why, numbers.toArray());
		}
	}

	/** How often a rule has been carried out in the village, and the day it last was. */
	public record Used(int times, long lastDay) {
		public static final Codec<Used> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("times").forGetter(Used::times),
			Codec.LONG.fieldOf("last_day").forGetter(Used::lastDay)
		).apply(i, Used::new));
	}

	/** A hall's Steward record: the day the wishes were ranked (-1: never), the wishes, and what each rule has done. */
	public record State(long day, List<Wish> wishes, Map<String, Used> used) {
		public static final State EMPTY = new State(-1, List.of(), Map.of());
		public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.optionalFieldOf("day", -1L).forGetter(State::day),
			Wish.CODEC.listOf().optionalFieldOf("wishes", List.of()).forGetter(State::wishes),
			Codec.unboundedMap(Codec.STRING, Used.CODEC).optionalFieldOf("used", Map.of()).forGetter(State::used)
		).apply(i, State::new));

		public State {
			wishes = List.copyOf(wishes);
			used = Map.copyOf(used);
		}

		public Used used(ResourceLocation rule) {
			return used.getOrDefault(rule.toString(), new Used(0, -1));
		}
	}

	/** Whether a rule may be wished for today, and if not why. */
	public enum Status {
		HELD, NOT_HELD, RANK, MOD_MISSING, COOLING, USED_UP
	}

	/** A rule judged against the village: its status and every condition's check. */
	public record Verdict(StewardRules.Rule rule, Status status, List<StewardConditions.Check> checks) {
		public List<Long> numbers() {
			return checks.stream().map(StewardConditions.Check::value).toList();
		}
	}

	public static void init() {
		StewardWork.PLANNER = StewardWishes::plan;
		Plots.init();
		StewardDesk.init();
	}

	/** The Steward's day: the world's day count (as his rounds go by). */
	public static long day(ServerLevel level) {
		return level.getDayTime() / 24000L;
	}

	public static State of(ServerLevel level, BlockPos hall) {
		return level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity ? entity.stewardWishes() : State.EMPTY;
	}

	/** Judges {@code rule}: rank, mods, cooldown and max first, then its conditions (all of them, for explain). */
	public static Verdict judge(StewardRules.Rule rule, StewardConditions.Facts facts, State state, long day) {
		List<StewardConditions.Check> checks = new ArrayList<>();
		boolean all = true;
		for (StewardConditions.Condition condition : rule.when()) {
			StewardConditions.Check check = condition.test(facts);
			checks.add(check);
			all &= check.held();
		}
		Used used = state.used(rule.id());
		Status status;
		if (facts.rank().ordinal() < rule.minRank().ordinal()) {
			status = Status.RANK;
		} else if (rule.requires().stream().anyMatch(mod -> !Platform.get().isModLoaded(mod))) {
			status = Status.MOD_MISSING;
		} else if (used.times() >= rule.max()) {
			status = Status.USED_UP;
		} else if (used.lastDay() >= 0 && day - used.lastDay() < rule.cooldownDays()) {
			status = Status.COOLING;
		} else {
			status = all ? Status.HELD : Status.NOT_HELD;
		}
		return new Verdict(rule, status, List.copyOf(checks));
	}

	/** The rules that hold, ranked into wishes: highest priority first, at most {@link #MAX_WISHES}. */
	public static List<Wish> rank(List<StewardRules.Rule> rules, StewardConditions.Facts facts, State state, long day) {
		List<Wish> out = new ArrayList<>();
		for (StewardRules.Rule rule : rules) { // already highest priority first
			Verdict verdict = judge(rule, facts, state, day);
			if (verdict.status() == Status.HELD) {
				out.add(new Wish(rule.id(), rule.effect(), rule.priority(), rule.why(), verdict.numbers()));
				if (out.size() >= MAX_WISHES) {
					break;
				}
			}
		}
		out.sort(java.util.Comparator.comparingInt(Wish::priority).reversed().thenComparing(w -> w.rule().toString()));
		return out;
	}

	/** Ranks today's wishes for the hall if they weren't yet today; true if it ranked. */
	public static boolean rankIfDue(ServerLevel level, BlockPos hall) {
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return false;
		}
		long day = day(level);
		State state = entity.stewardWishes();
		if (state.day() == day) {
			return false;
		}
		List<Wish> wishes = rank(StewardRules.all(), StewardConditions.Facts.of(level, hall), state, day);
		entity.setStewardWishes(new State(day, wishes, state.used()));
		return true;
	}

	/**
	 * A wish of {@code rule} was carried out (27.7–27.9): it counts towards the rule's {@code max}, its cooldown starts
	 * today, and it leaves today's wishes.
	 */
	public static void carriedOut(ServerLevel level, BlockPos hall, ResourceLocation rule) {
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return;
		}
		State state = entity.stewardWishes();
		Map<String, Used> used = new HashMap<>(state.used());
		used.put(rule.toString(), new Used(state.used(rule).times() + 1, day(level)));
		entity.setStewardWishes(new State(state.day(), state.wishes().stream().filter(w -> !w.rule().equals(rule)).toList(), used));
	}

	/** {@link StewardWork#PLANNER}: at the hall, he ranks the day's wishes once and says the first. */
	static Component plan(ServerLevel level, Villager steward, BlockPos hall) {
		if (StewardDesk.mode(level, hall) == CityPlan.Mode.REST) {
			return Component.translatable("message.aliveworkplace.steward.state.resting"); // Rest (27.8): he plans nothing
		}
		rankIfDue(level, hall);
		List<Wish> wishes = of(level, hall).wishes();
		for (Wish wish : wishes) {
			plotFor(wish).ifPresent(request -> Plots.request(level, hall, request)); // the morning's plots (27.7), kept till the plan changes
		}
		StewardDesk.plan(level, steward, hall); // proposals on his desk, or builds started in Run the village (27.8)
		wishes = of(level, hall).wishes();
		if (wishes.isEmpty()) {
			return Component.translatable("message.aliveworkplace.steward.state.reading");
		}
		return Component.translatable("message.aliveworkplace.steward.state.wish", wishes.get(0).effect().describe(), wishes.get(0).reason());
	}

	/** What a build wish needs a plot for (27.7): its blueprint in a zone of its kind. */
	public static java.util.Optional<Plots.Request> plotFor(Wish wish) {
		StewardRules.Effect effect = wish.effect();
		if (effect.kind() != StewardRules.Kind.BUILD || effect.blueprint().isEmpty() || effect.zone().isEmpty()) {
			return java.util.Optional.empty();
		}
		return java.util.Optional.of(new Plots.Request(List.of(effect.blueprint().get()), effect.zone().get()));
	}

	/** {@code /workplace steward explain}: every rule for the hall, each condition's number and whether it held, and today's wishes. */
	public static List<Component> explain(ServerLevel level, BlockPos hall, List<StewardRules.Rule> rules) {
		StewardConditions.Facts facts = StewardConditions.Facts.of(level, hall);
		State state = of(level, hall);
		long day = day(level);
		List<Verdict> verdicts = rules.stream().map(r -> judge(r, facts, state, day)).toList();
		List<Component> out = new ArrayList<>();
		long held = verdicts.stream().filter(v -> v.status() == Status.HELD).count();
		out.add(Component.translatable("command.aliveworkplace.steward.explain.header", VillageHalls.name(level, hall), held, verdicts.size())
			.withStyle(ChatFormatting.GOLD));
		if (verdicts.isEmpty()) {
			out.add(Component.translatable("command.aliveworkplace.steward.explain.no_rules").withStyle(ChatFormatting.GRAY));
		}
		for (Verdict v : verdicts) {
			StewardRules.Rule rule = v.rule();
			boolean ok = v.status() == Status.HELD;
			out.add(Component.translatable("command.aliveworkplace.steward.explain.rule",
					Component.translatable(ok ? "command.aliveworkplace.steward.explain.held" : "command.aliveworkplace.steward.explain.not_held")
						.withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED),
					rule.id().toString(), rule.priority(), rule.effect().describe())
				.withStyle(ok ? ChatFormatting.WHITE : ChatFormatting.GRAY));
			Component skipped = switch (v.status()) {
				case RANK -> Component.translatable("command.aliveworkplace.steward.explain.rank", rule.minRank().title());
				case MOD_MISSING -> Component.translatable("command.aliveworkplace.steward.explain.mod", String.join(", ", rule.requires()));
				case USED_UP -> Component.translatable("command.aliveworkplace.steward.explain.used_up", state.used(rule.id()).times(), rule.max());
				case COOLING -> Component.translatable("command.aliveworkplace.steward.explain.cooling",
					rule.cooldownDays() - (day - state.used(rule.id()).lastDay()));
				default -> null;
			};
			if (skipped != null) {
				out.add(Component.literal("    ").append(skipped).withStyle(ChatFormatting.YELLOW));
			}
			for (StewardConditions.Check check : v.checks()) {
				MutableComponent line = Component.literal("    ").append(Component.literal(check.held() ? "✔ " : "✘ ")
					.withStyle(check.held() ? ChatFormatting.GREEN : ChatFormatting.RED));
				out.add(line.append(check.shown().copy().withStyle(ChatFormatting.GRAY)));
			}
			if (ok) {
				out.add(Component.literal("    ").append(Component.translatable("command.aliveworkplace.steward.explain.why",
					Component.translatable(rule.why(), v.numbers().toArray()))).withStyle(ChatFormatting.AQUA));
			}
		}
		if (state.day() == day && !state.wishes().isEmpty()) {
			out.add(Component.translatable("command.aliveworkplace.steward.explain.wishes", state.wishes().size()).withStyle(ChatFormatting.GOLD));
			for (int i = 0; i < state.wishes().size(); i++) {
				Wish w = state.wishes().get(i);
				out.add(Component.translatable("command.aliveworkplace.steward.explain.wish", i + 1, w.effect().describe(), w.reason())
					.withStyle(ChatFormatting.WHITE));
			}
		} else {
			out.add(Component.translatable("command.aliveworkplace.steward.explain.no_wishes").withStyle(ChatFormatting.GRAY));
		}
		return out;
	}

	private StewardWishes() {
	}
}
