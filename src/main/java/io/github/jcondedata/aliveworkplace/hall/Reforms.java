package io.github.jcondedata.aliveworkplace.hall;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.trainer.Trainers;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;

/**
 * Reforms (ROADMAP 30.5, docs/design/M30.md): every edict file may carry a {@code reform}, a few quest steps that drop
 * the edict's cost for good. While an edict is in force and not reformed, the hall keeps its next step on the quest
 * page (a {@link VillageQuests.Quest} whose {@code reform} names the edict and the step; it never expires). The next
 * step goes up the morning after the last was done, so a three-step reform takes three days at least. Progress is kept
 * per edict on the hall ({@link Progress}), also while the edict is lifted, and a step handed in halfway stays half
 * done. The last step reforms the edict for that village: its boost stays, its cost is replaced by the reform's
 * {@code effects} (none: the cost just goes); fireworks over the hall, the village told, the chronicle (kind REFORM).
 *
 * <p>Steps are today's quest kinds: {@code bring} (an item and a count), {@code slay} (a count of monsters) and
 * {@code battle} (beat one of the village's trainers), which uses its {@code fallback} step when Cobblemon or a trainer
 * is missing (no fallback written: clear out {@link VillageQuests#MONSTERS} monsters for the same pay). A reform and
 * each step may carry an {@code arc} id for M31's story arcs. With {@code storyArcs} on, a reform whose {@code arc} names a
 * loaded arc is told as that story instead (31.22, {@link io.github.jcondedata.aliveworkplace.story.ReformStories}): the
 * arc runs the same steps as its quests and its {@code reform_edict} reward reforms the edict; its M30 step stays off the
 * quest page while the story runs. When the story can't start (nobody of the asking trade), the steps go up as before.
 */
public final class Reforms {
	/** Kinds as a file writes them: "bring", "slay", "battle". */
	static final Codec<VillageQuests.Kind> KIND = Codec.STRING.comapFlatMap(s -> {
		try {
			return DataResult.success(VillageQuests.Kind.valueOf(s.toUpperCase(Locale.ROOT)));
		} catch (IllegalArgumentException e) {
			return DataResult.error(() -> "unknown step kind: " + s);
		}
	}, k -> k.name().toLowerCase(Locale.ROOT));

	/** An item id a step asks for; one that isn't registered is refused (the file is skipped, naming it). */
	private static final Codec<String> ITEM = ResourceLocation.CODEC.comapFlatMap(id -> BuiltInRegistries.ITEM.containsKey(id)
		? DataResult.success(id.toString()) : DataResult.error(() -> "unknown item: " + id), ResourceLocation::parse);

	/**
	 * One step: its kind, the item (BRING) and count, the emeralds it pays before the rank's quarter, the step used
	 * instead of a battle that can't be fought, and an arc id kept for M31.
	 */
	public record Step(VillageQuests.Kind kind, String item, int count, int reward, Optional<Step> fallback, Optional<String> arc) {
		/** The step as it can be done here: a battle without Cobblemon or a trainer becomes its fallback. */
		public Step resolve(boolean battles) {
			if (kind != VillageQuests.Kind.BATTLE || battles) {
				return this;
			}
			return fallback.orElse(new Step(VillageQuests.Kind.SLAY, "minecraft:air", VillageQuests.MONSTERS, reward, Optional.empty(), arc));
		}

		/**
		 * The step as it can be done here (B301): a battle that can't be fought becomes its fallback, and clearing out
		 * monsters where nothing can be fought ({@code slayable} false, {@link Foes}) becomes bringing bread, same pay.
		 */
		public Step resolve(boolean battles, boolean slayable) {
			Step s = resolve(battles);
			return s.kind() != VillageQuests.Kind.SLAY || slayable ? s
				: new Step(VillageQuests.Kind.BRING, Foes.SUBSTITUTE, Foes.substituteCount(s.count()), s.reward(), Optional.empty(), s.arc());
		}

		/** The step as it can be done in the village round {@code hall}: its battles and its foes. */
		public Step resolve(ServerLevel level, BlockPos hall) {
			return resolve(battles(level, hall), Foes.slayable(level));
		}
	}

	/** A fallback step: kind, item, count, reward and arc (a battle's stand-in has no fallback of its own, nor is it a battle). */
	private static final Codec<Step> FALLBACK = RecordCodecBuilder.<Step>create(i -> i.group(
		KIND.fieldOf("kind").forGetter(Step::kind),
		ITEM.optionalFieldOf("item", "minecraft:air").forGetter(Step::item),
		Codec.intRange(1, 4096).optionalFieldOf("count", 1).forGetter(Step::count),
		Codec.intRange(0, 1000).optionalFieldOf("reward", 0).forGetter(Step::reward),
		Codec.STRING.optionalFieldOf("arc").forGetter(Step::arc)
	).apply(i, (kind, item, count, reward, arc) -> new Step(kind, item, count, reward, Optional.empty(), arc)))
		.validate(Reforms::checkStep)
		.validate(s -> s.kind() == VillageQuests.Kind.BATTLE ? DataResult.error(() -> "a fallback can't be a battle") : DataResult.success(s));

	/** A step as a file writes it. */
	public static final Codec<Step> STEP = RecordCodecBuilder.<Step>create(i -> i.group(
		KIND.fieldOf("kind").forGetter(Step::kind),
		ITEM.optionalFieldOf("item", "minecraft:air").forGetter(Step::item),
		Codec.intRange(1, 4096).optionalFieldOf("count", 1).forGetter(Step::count),
		Codec.intRange(0, 1000).optionalFieldOf("reward", 0).forGetter(Step::reward),
		FALLBACK.optionalFieldOf("fallback").forGetter(Step::fallback),
		Codec.STRING.optionalFieldOf("arc").forGetter(Step::arc)
	).apply(i, Step::new)).validate(Reforms::checkStep);

	private static DataResult<Step> checkStep(Step s) {
		if (s.kind() == VillageQuests.Kind.BRING && s.item().equals("minecraft:air")) {
			return DataResult.error(() -> "a bring step needs an item");
		}
		return DataResult.success(s);
	}

	/**
	 * A realm reform's count (33.22): {@code times} of {@code what} across the realm's villages while the edict is in
	 * force ({@code realm/RealmEdicts} counts them: {@code caravans_between_members}, {@code board_trades},
	 * {@code raids_beaten_with_relief}, {@code realm_festivals}, {@code days_without_raid}, {@code colonies_at_village_rank}).
	 */
	public record Count(ResourceLocation what, int times) {
		public static final Codec<Count> CODEC = RecordCodecBuilder.create(i -> i.group(
			ResourceLocation.CODEC.fieldOf("what").forGetter(Count::what),
			Codec.intRange(1, 100000).fieldOf("times").forGetter(Count::times)
		).apply(i, Count::new));
	}

	/**
	 * A reform as an edict file writes it: its name, its line, its steps, what replaces the cost, an arc id for M31, and
	 * (a realm edict's, 33.22) a count across the realm that must be reached too.
	 */
	public record Reform(Component name, Component line, List<Step> steps, List<CivicEffects.Effect> effects, Optional<String> arc, Optional<Count> count) {
		public static final Codec<Reform> CODEC = RecordCodecBuilder.create(i -> i.group(
			ComponentSerialization.CODEC.fieldOf("name").forGetter(Reform::name),
			ComponentSerialization.CODEC.optionalFieldOf("line", Component.empty()).forGetter(Reform::line),
			STEP.listOf().validate(l -> l.isEmpty() ? DataResult.error(() -> "a reform needs steps") : DataResult.success(l))
				.fieldOf("steps").forGetter(Reform::steps),
			CivicEffects.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(Reform::effects),
			Codec.STRING.optionalFieldOf("arc").forGetter(Reform::arc),
			Count.CODEC.optionalFieldOf("count").forGetter(Reform::count)
		).apply(i, Reform::new));

		/** A reform without a count (M30's). */
		public Reform(Component name, Component line, List<Step> steps, List<CivicEffects.Effect> effects, Optional<String> arc) {
			this(name, line, steps, effects, arc, Optional.empty());
		}
	}

	/**
	 * A village's progress on one edict's reform, saved on the hall: the edict's id, the steps done, whether it is
	 * reformed, and the day ({@link Chronicle#day}) its next step may go up.
	 */
	public record Progress(String id, int step, boolean reformed, long nextDay) {
		public static final Codec<Progress> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("id").forGetter(Progress::id),
			Codec.INT.optionalFieldOf("step", 0).forGetter(Progress::step),
			Codec.BOOL.optionalFieldOf("reformed", false).forGetter(Progress::reformed),
			Codec.LONG.optionalFieldOf("nextDay", 0L).forGetter(Progress::nextDay)
		).apply(i, Progress::new));
	}

	/** Fireworks over the hall when an edict is reformed. */
	static final int FIREWORKS = 5;

	/** {@code entity}'s progress on the reform of the edict {@code id} (nothing done yet if it never started). */
	public static Progress progress(VillageHallBlockEntity entity, String id) {
		return entity.reforms().stream().filter(p -> p.id().equals(id)).findFirst().orElse(new Progress(id, 0, false, 0));
	}

	/** Whether the edict {@code id} is reformed in {@code entity}'s village. */
	public static boolean reformed(VillageHallBlockEntity entity, String id) {
		return progress(entity, id).reformed();
	}

	private static void save(VillageHallBlockEntity entity, Progress progress) {
		List<Progress> all = new ArrayList<>(entity.reforms());
		all.removeIf(p -> p.id().equals(progress.id()));
		all.add(progress);
		entity.setReforms(all);
	}

	/** The reform of the edict {@code id}, if its file has one. */
	public static Optional<Reform> of(String id) {
		ResourceLocation rl = ResourceLocation.tryParse(id);
		return rl == null ? Optional.empty() : Edicts.get(rl).flatMap(Edicts.Edict::reform);
	}

	/**
	 * The reform steps the quest page shows, in the order of the edicts in force: one per edict in force that has a
	 * reform and isn't reformed, once its step is up; then (at a realm's capital, 33.22) the realm edicts' steps, which
	 * {@code realm/RealmEdicts} puts up and takes down. None while edicts are switched off.
	 */
	public static List<VillageQuests.Quest> shown(VillageHallBlockEntity entity) {
		List<VillageQuests.Quest> out = new ArrayList<>();
		if (!Edicts.ENABLED) {
			return out;
		}
		for (Edicts.InForce f : entity.edicts()) {
			if (reformed(entity, f.id()) || of(f.id()).isEmpty()) {
				continue;
			}
			entity.quests().stream().filter(q -> q.reform().map(r -> r.edict().equals(f.id())).orElse(false)).findFirst().ifPresent(out::add);
		}
		if (io.github.jcondedata.aliveworkplace.realm.RealmEdicts.on()) {
			entity.quests().stream().filter(q -> q.reform().map(r -> realm(r.edict())).orElse(false)).forEach(out::add);
		}
		return out;
	}

	/** Whether the edict {@code id} is a realm edict (33.22). */
	public static boolean realm(String id) {
		ResourceLocation rl = ResourceLocation.tryParse(id);
		return rl != null && Edicts.get(rl).map(Edicts.Edict::realm).orElse(false);
	}

	/** Whether the village round {@code hall} can fight a battle step: Cobblemon is there and so is a trainer. */
	public static boolean battles(ServerLevel level, BlockPos hall) {
		return Trainers.COBBLEMON && VillageHalls.census(level, hall).workers().stream().anyMatch(Trainers::isTrainer);
	}

	/**
	 * Puts up the next reform step of every edict in force that is due (its step not up yet, its day come); a step
	 * whose edict's file changed under it (fewer steps, or another one) is taken down first. Run with each hall round
	 * and when an edict is proclaimed.
	 */
	public static void round(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		io.github.jcondedata.aliveworkplace.realm.RealmEdicts.round(level, hall, entity); // 33.22: the realm's steps at its capital
		swapUnfinishable(level, entity);
		if (!Edicts.ENABLED) {
			return;
		}
		long today = Chronicle.day(level);
		List<VillageQuests.Quest> quests = new ArrayList<>(entity.quests());
		boolean changed = false;
		Boolean battles = null;
		for (Edicts.InForce f : entity.edicts()) {
			Optional<Reform> reform = of(f.id());
			if (reform.isEmpty()) {
				continue;
			}
			Progress progress = progress(entity, f.id());
			if (progress.reformed() || progress.step() >= reform.get().steps().size()) {
				continue;
			}
			int step = progress.step();
			if (io.github.jcondedata.aliveworkplace.story.ReformStories.told(level, hall, f.id(), reform.get())) { // 31.22: the story runs the steps
				changed |= quests.removeIf(q -> q.reform().map(r -> r.edict().equals(f.id())).orElse(false));
				continue;
			}
			changed |= quests.removeIf(q -> q.reform().map(r -> r.edict().equals(f.id()) && r.step() != step).orElse(false));
			if (quests.stream().anyMatch(q -> q.reform().map(r -> r.edict().equals(f.id())).orElse(false)) || today < progress.nextDay()) {
				continue;
			}
			if (battles == null) {
				battles = battles(level, hall);
			}
			Step s = reform.get().steps().get(step).resolve(battles, Foes.slayable(level));
			int reward = Math.round(s.reward() * VillageRanks.questRewardFactor(entity.rank()));
			VillageQuests.Quest quest = new VillageQuests.Quest(UUID.randomUUID(), s.kind(), s.item(), s.count(), 0, reward, level.getGameTime(), "",
				Optional.empty(), Optional.of(new VillageQuests.ReformStep(f.id(), step)));
			quests.add(quest);
			changed = true;
			announce(level, hall, Component.translatable("message.aliveworkplace.reform.posted", VillageHalls.name(level, hall),
				VillageQuests.describe(quest)).withStyle(ChatFormatting.GOLD));
		}
		if (changed) {
			entity.setQuests(quests);
		}
	}

	/**
	 * B301: a clearing-out reform step already up where nothing can be fought (a save from before, or the pack's
	 * blocklist added since) becomes bringing bread, same step and pay, started afresh. Run with each hall round.
	 */
	static void swapUnfinishable(ServerLevel level, VillageHallBlockEntity entity) {
		if (entity.quests().stream().noneMatch(q -> q.kind() == VillageQuests.Kind.SLAY) || Foes.slayable(level)) {
			return;
		}
		entity.setQuests(entity.quests().stream().map(q -> q.kind() != VillageQuests.Kind.SLAY ? q
			: new VillageQuests.Quest(q.id(), VillageQuests.Kind.BRING, Foes.SUBSTITUTE, Foes.substituteCount(q.count()), 0, q.reward(), q.posted(),
				q.poster(), Optional.empty(), q.reform())).toList());
	}

	/** "The Shift Bell, step 1 of 3" for a reform step (its edict's id if the file is gone). */
	public static Component title(VillageQuests.ReformStep step) {
		Optional<Reform> reform = of(step.edict());
		return Component.translatable("quest.aliveworkplace.reform", reform.map(Reform::name).orElse(Edicts.name(step.edict())),
			step.step() + 1, reform.map(r -> r.steps().size()).orElse(step.step() + 1));
	}

	/**
	 * A reform step was finished (and paid by {@link VillageQuests}): the next goes up tomorrow morning; after the last
	 * the edict is reformed for good.
	 */
	static void stepDone(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, VillageQuests.ReformStep done, @Nullable ServerPlayer player) {
		if (realm(done.edict())) { // 33.22: the realm keeps a realm edict's reform
			io.github.jcondedata.aliveworkplace.realm.RealmEdicts.stepDone(level, hall, done, player);
			return;
		}
		Progress progress = progress(entity, done.edict());
		if (progress.reformed() || done.step() != progress.step()) {
			return;
		}
		Optional<Reform> reform = of(done.edict());
		int steps = reform.map(r -> r.steps().size()).orElse(done.step() + 1);
		int next = done.step() + 1;
		if (next < steps) {
			save(entity, new Progress(done.edict(), next, false, Chronicle.day(level) + 1));
			if (player != null) {
				Chat.chat(player, Component.translatable("message.aliveworkplace.reform.next", reform.map(Reform::name).orElse(Edicts.name(done.edict())))
					.withStyle(ChatFormatting.GRAY));
			}
			return;
		}
		reform(level, hall, entity, done.edict());
	}

	/** {@code steps} steps of the edict {@code id}'s reform are done (a reform story's quest, 31.22); never goes back. */
	public static void stepsDone(ServerLevel level, VillageHallBlockEntity entity, String id, int steps) {
		Progress progress = progress(entity, id);
		if (!progress.reformed() && steps > progress.step()) {
			save(entity, new Progress(id, steps, false, Chronicle.day(level) + 1));
		}
	}

	/**
	 * The edict {@code id} is reformed for good in the village round {@code hall} (its last step, or a reform story's
	 * {@code reform_edict} reward): its boost stays, its cost goes; fireworks, the village told, the chronicle. False if
	 * it already was.
	 */
	public static boolean reform(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, String id) {
		if (reformed(entity, id)) {
			return false;
		}
		Optional<Reform> reform = of(id);
		int steps = reform.map(r -> r.steps().size()).orElse(1);
		VillageQuests.ReformStep done = new VillageQuests.ReformStep(id, steps - 1);
		save(entity, new Progress(done.edict(), steps, true, Chronicle.day(level)));
		entity.setQuests(entity.quests().stream().filter(q -> q.reform().map(r -> !r.edict().equals(id)).orElse(true)).toList());
		Component edict = Edicts.name(done.edict());
		Component name = reform.map(Reform::name).orElse(edict);
		Component line = reform.map(Reform::line).orElse(Component.empty());
		Chronicle.record(level, hall, Chronicle.Kind.REFORM, Component.translatable("chronicle.aliveworkplace.reform", edict, name), true);
		announce(level, hall, Component.translatable("message.aliveworkplace.reform.done", VillageHalls.name(level, hall), edict, name, line)
			.withStyle(ChatFormatting.GOLD));
		for (int i = 0; i < FIREWORKS; i++) {
			Festivals.launch(level, hall.offset(level.random.nextInt(5) - 2, 2, level.random.nextInt(5) - 2));
		}
		level.playSound(null, hall, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1f, 1.2f);
		level.playSound(null, hall, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1f);
		io.github.jcondedata.aliveworkplace.people.Moods.forget();
		return true;
	}

	/** Tells the players in the village. */
	private static void announce(ServerLevel level, BlockPos hall, Component message) {
		double r = (double) VillageHalls.RADIUS * VillageHalls.RADIUS;
		for (ServerPlayer player : level.getPlayers(p -> p.blockPosition().distSqr(hall) <= r)) {
			Chat.chat(player, message);
		}
	}

	private Reforms() {
	}
}
