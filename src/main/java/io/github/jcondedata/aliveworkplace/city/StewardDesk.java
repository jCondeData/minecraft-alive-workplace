package io.github.jcondedata.aliveworkplace.city;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.MaterialFamilies;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageAdvice;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.research.ScholarWork;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * The Steward's desk (ROADMAP 27.8): each morning his build wishes (27.6) with a plot found (27.7), and his upgrade
 * wishes on the finished building's own spot, become proposals on the hall's "What next?" page, at most
 * {@link #MAX_PROPOSALS}. A player who may change the plan approves one (a {@link BuildSite} for the nearest builder,
 * owned by the hall's owner and noted in the chronicle as {@link Chronicle.Kind#PLANS}), declines it (not proposed again
 * for {@link #DECLINE_DAYS} days), asks for another spot or another style. In {@link CityPlan.Mode#RUN} he approves them
 * himself each morning and tells the owner in one line; {@link CityPlan.Mode#REST} plans nothing. Proposals nobody
 * answered lapse after {@link #LAPSE_DAYS} days. Everything is saved on the hall ({@link State}).
 */
public final class StewardDesk {
	public static final int MAX_PROPOSALS = 9;
	public static final int DECLINE_DAYS = 3;
	public static final int LAPSE_DAYS = 3;
	/** "Show me": the outline glows this long (30 seconds). */
	public static final int SHOW_TICKS = 600;
	/** Materials listed on a proposal. */
	public static final int MATERIALS = 5;
	/** The blueprint a jobs proposal and a research proposal stand for: none (27.9). */
	public static final ResourceLocation JOBS = AliveWorkplace.id("steward/jobs");
	public static final ResourceLocation RESEARCH = AliveWorkplace.id("steward/research");
	/** Config {@code stewardSelfRun}: off, every village asks first whatever its mode. */
	public static boolean SELF_RUN = true;

	/**
	 * A proposal: a wish with where it goes. {@code searching}: "Another spot" asked, the next plot not found yet. A
	 * proposal with {@code jobs} gives jobless villagers work (27.9), one with a {@code topic} picks the next research
	 * (27.9); both stand at the hall with {@link #JOBS} or {@link #RESEARCH} for a blueprint.
	 */
	public record Proposal(int id, ResourceLocation rule, boolean upgrade, ResourceLocation blueprint, BlueprintData.Placement placement,
						   String zone, String kind, String why, List<Long> numbers, long day, int skip, boolean searching,
						   List<StewardJobs.Job> jobs, String topic, String wallKit) {
		public static final Codec<Proposal> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("id").forGetter(Proposal::id),
			ResourceLocation.CODEC.fieldOf("rule").forGetter(Proposal::rule),
			Codec.BOOL.optionalFieldOf("upgrade", false).forGetter(Proposal::upgrade),
			ResourceLocation.CODEC.fieldOf("blueprint").forGetter(Proposal::blueprint),
			BlueprintData.Placement.CODEC.fieldOf("placement").forGetter(Proposal::placement),
			Codec.STRING.optionalFieldOf("zone", "").forGetter(Proposal::zone),
			Codec.STRING.optionalFieldOf("kind", "").forGetter(Proposal::kind),
			Codec.STRING.optionalFieldOf("why", "").forGetter(Proposal::why),
			Codec.LONG.listOf().optionalFieldOf("numbers", List.of()).forGetter(Proposal::numbers),
			Codec.LONG.optionalFieldOf("day", 0L).forGetter(Proposal::day),
			Codec.INT.optionalFieldOf("skip", 0).forGetter(Proposal::skip),
			Codec.BOOL.optionalFieldOf("searching", false).forGetter(Proposal::searching),
			StewardJobs.Job.CODEC.listOf().optionalFieldOf("jobs", List.of()).forGetter(Proposal::jobs),
			Codec.STRING.optionalFieldOf("topic", "").forGetter(Proposal::topic),
			Codec.STRING.optionalFieldOf("wall_kit", "").forGetter(Proposal::wallKit)
		).apply(i, Proposal::new));

		public Proposal {
			numbers = List.copyOf(numbers);
			jobs = List.copyOf(jobs);
		}

		public Proposal(int id, ResourceLocation rule, boolean upgrade, ResourceLocation blueprint, BlueprintData.Placement placement,
						String zone, String kind, String why, List<Long> numbers, long day, int skip, boolean searching,
						List<StewardJobs.Job> jobs, String topic) {
			this(id, rule, upgrade, blueprint, placement, zone, kind, why, numbers, day, skip, searching, jobs, topic, "");
		}

		/** A build or an upgrade (not jobs, research or the wall). */
		public boolean isBuild() {
			return jobs.isEmpty() && topic.isEmpty() && wallKit.isEmpty();
		}

		/** The wall (27.18): its kit, if this proposal is one. */
		public Optional<WallKits.Kit> wall() {
			return wallKit.isEmpty() ? Optional.empty() : WallKits.get(wallKit);
		}

		public boolean isJobs() {
			return !jobs.isEmpty();
		}

		public Optional<Research.Topic> researchTopic() {
			return topic.isEmpty() ? Optional.empty() : StewardRules.topicOf(topic);
		}

		/** "3 villagers have no bed". */
		public Component reason() {
			return why.isEmpty() ? Component.empty() : Component.translatable(why, numbers.toArray());
		}

		/** "Stone House (Cherry)", "Give 3 villagers jobs", "Research Fortification", "A Palisade wall". */
		public Component name() {
			if (!wallKit.isEmpty()) {
				return wall().map(Walls::title).orElse(Component.literal(wallKit));
			}
			if (isJobs()) {
				return StewardJobs.title(jobs.size());
			}
			if (!topic.isEmpty()) {
				return Component.translatable("steward.aliveworkplace.research.title",
					researchTopic().map(Research.Topic::title).orElse(Component.literal(topic)));
			}
			return Blueprints.displayName(blueprint);
		}

		Proposal with(ResourceLocation blueprint, BlueprintData.Placement placement, int skip, boolean searching) {
			return new Proposal(id, rule, upgrade, blueprint, placement, zone, kind, why, numbers, day, skip, searching, jobs, topic, wallKit);
		}
	}

	/**
	 * The desk saved on the hall: proposals, rules declined until a day, the last day told and run, the next id, and the
	 * last day the jobs were planned and a research topic picked (27.9).
	 */
	public record State(List<Proposal> proposals, Map<String, Long> declined, long told, long ran, int next, long jobsDay, long researchDay) {
		public static final State EMPTY = new State(List.of(), Map.of(), -1, -1, 1, -1, -1);
		public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
			Proposal.CODEC.listOf().optionalFieldOf("proposals", List.of()).forGetter(State::proposals),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("declined", Map.of()).forGetter(State::declined),
			Codec.LONG.optionalFieldOf("told", -1L).forGetter(State::told),
			Codec.LONG.optionalFieldOf("ran", -1L).forGetter(State::ran),
			Codec.INT.optionalFieldOf("next", 1).forGetter(State::next),
			Codec.LONG.optionalFieldOf("jobs_day", -1L).forGetter(State::jobsDay),
			Codec.LONG.optionalFieldOf("research_day", -1L).forGetter(State::researchDay)
		).apply(i, State::new));

		public State {
			proposals = List.copyOf(proposals);
			declined = Map.copyOf(declined);
		}

		public Optional<Proposal> get(int id) {
			return proposals.stream().filter(p -> p.id() == id).findFirst();
		}

		State withProposals(List<Proposal> list) {
			return new State(list, declined, told, ran, next, jobsDay, researchDay);
		}

		State withDeclined(Map<String, Long> map) {
			return new State(proposals, map, told, ran, next, jobsDay, researchDay);
		}

		State withTold(long day) {
			return new State(proposals, declined, day, ran, next, jobsDay, researchDay);
		}

		State withRan(long day) {
			return new State(proposals, declined, told, day, next, jobsDay, researchDay);
		}

		State withJobsDay(long day) {
			return new State(proposals, declined, told, ran, next, day, researchDay);
		}

		State withResearchDay(long day) {
			return new State(proposals, declined, told, ran, next, jobsDay, day);
		}

		/** {@code list} with one more proposal, and the next id after it. */
		State adding(Proposal proposal) {
			List<Proposal> list = new ArrayList<>(proposals);
			list.add(proposal);
			return new State(list, declined, told, ran, next + 1, jobsDay, researchDay);
		}
	}

	/** What happened to an approval. */
	public enum Outcome {
		STARTED, QUEUED, NOT_ALLOWED, GONE, NO_STEWARD, NO_BUILDER, FULL, OVERLAPS, SEARCHING, JOBS_TAKEN, RESEARCHING;

		public boolean ok() {
			return this == STARTED || this == QUEUED;
		}
	}

	/** The builder who would build a proposal, and the build he is busy with first (null when idle). */
	public record Builder(Villager villager, @Nullable BuildSite after) {
	}

	// ---- state -------------------------------------------------------------------------------------------------

	public static State of(ServerLevel level, BlockPos hall) {
		return level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity ? entity.stewardDesk() : State.EMPTY;
	}

	private static void save(ServerLevel level, BlockPos hall, State state) {
		if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) {
			entity.setStewardDesk(state);
		}
	}

	public static CityPlan.Mode mode(ServerLevel level, BlockPos hall) {
		return level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity ? entity.plan().mode() : CityPlan.Mode.ASK;
	}

	/** Whether the Steward approves his own proposals here: Run the village, and the config lets him. */
	public static boolean runsItself(ServerLevel level, BlockPos hall) {
		return SELF_RUN && mode(level, hall) == CityPlan.Mode.RUN;
	}

	/** Who may use the desk: as for the plan (27.2), the hall's owner, their friends and operators; anyone while nobody owns it. */
	public static boolean mayUse(ServerLevel level, BlockPos hall, ServerPlayer player) {
		return level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity && VillageProtection.mayBuild(level, entity, player);
	}

	/** Sets the Steward's mode, if {@code player} may. */
	public static boolean setMode(ServerLevel level, BlockPos hall, ServerPlayer player, CityPlan.Mode mode) {
		if (!mayUse(level, hall, player) || !(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)
			|| mode == CityPlan.Mode.RUN && !SELF_RUN) {
			return false;
		}
		entity.setPlan(entity.plan().withMode(mode));
		if (mode == CityPlan.Mode.REST) {
			save(level, hall, of(level, hall).withProposals(List.of()));
		}
		return true;
	}

	/** His builds still open: the sites the Steward of this hall started. */
	public static List<BuildSite> openSites(ServerLevel level, BlockPos hall) {
		return BuildSiteManager.get(level).all().stream().filter(s -> hall.equals(s.stewardHall()))
			.sorted(Comparator.comparing(s -> s.id().toString())).toList();
	}

	// ---- proposals -----------------------------------------------------------------------------------------------

	/** Adds a proposal for {@code wish} at {@code plot} unless the rule is declined or already proposed; empty if not added. */
	public static Optional<Proposal> offer(ServerLevel level, BlockPos hall, StewardWishes.Wish wish, ResourceLocation blueprint,
										   BlueprintData.Placement placement, String zone, boolean upgrade) {
		State state = of(level, hall);
		long day = StewardWishes.day(level);
		if (declined(state, wish.rule(), day) || state.proposals().size() >= MAX_PROPOSALS
			|| state.proposals().stream().anyMatch(p -> p.rule().equals(wish.rule()))) {
			return Optional.empty();
		}
		Proposal proposal = new Proposal(state.next(), wish.rule(), upgrade, blueprint, placement, zone, wish.effect().zone().orElse(""),
			wish.why(), wish.numbers(), day, 0, false, List.of(), "");
		save(level, hall, state.adding(proposal));
		return Optional.of(proposal);
	}

	/** {@link #offer} for jobs or research: no blueprint, at the hall. */
	static Optional<Proposal> offerOther(ServerLevel level, BlockPos hall, ResourceLocation rule, String why, List<Long> numbers,
										 List<StewardJobs.Job> jobs, String topic) {
		State state = of(level, hall);
		long day = StewardWishes.day(level);
		if (declined(state, rule, day) || state.proposals().size() >= MAX_PROPOSALS
			|| state.proposals().stream().anyMatch(p -> p.rule().equals(rule))) {
			return Optional.empty();
		}
		Proposal proposal = new Proposal(state.next(), rule, false, jobs.isEmpty() ? RESEARCH : JOBS,
			new BlueprintData.Placement(Ids.of(level.dimension()), hall, Rotation.NONE, Mirror.NONE), "", "", why, numbers, day, 0, false, jobs, topic);
		save(level, hall, state.adding(proposal));
		return Optional.of(proposal);
	}

	/**
	 * {@link #offer} for the wall (27.18): no plot of its own (it is many pieces along the plan's wall line), at the
	 * hall. Nothing is offered while one is on the desk already, or while the rule is declined.
	 */
	public static Optional<Proposal> offerWall(ServerLevel level, BlockPos hall, WallKits.Kit kit) {
		State state = of(level, hall);
		long day = StewardWishes.day(level);
		if (declined(state, Walls.RULE, day) || state.proposals().size() >= MAX_PROPOSALS
			|| state.proposals().stream().anyMatch(p -> p.rule().equals(Walls.RULE))) {
			return Optional.empty();
		}
		Proposal proposal = new Proposal(state.next(), Walls.RULE, false, Walls.WALL,
			new BlueprintData.Placement(Ids.of(level.dimension()), hall, Rotation.NONE, Mirror.NONE), "", "",
			"steward.aliveworkplace.why.wall", List.of((long) Walls.RAID_DAYS), day, 0, false, List.of(), "", kit.name());
		save(level, hall, state.adding(proposal));
		Villager steward = Stewards.stewardOf(level, hall);
		if (steward != null) {
			tell(level, hall, steward);
		}
		return Optional.of(proposal);
	}

	static boolean declined(State state, ResourceLocation rule, long day) {
		Long until = state.declined().get(rule.toString());
		return until != null && day < until;
	}

	/** Proposals older than {@link #LAPSE_DAYS} days go, and declines that ran out are forgotten. */
	public static void lapse(ServerLevel level, BlockPos hall) {
		State state = of(level, hall);
		long day = StewardWishes.day(level);
		List<Proposal> kept = state.proposals().stream().filter(p -> day - p.day() < LAPSE_DAYS).toList();
		Map<String, Long> declined = new HashMap<>();
		state.declined().forEach((rule, until) -> {
			if (day < until) {
				declined.put(rule, until);
			}
		});
		if (kept.size() != state.proposals().size() || declined.size() != state.declined().size()) {
			save(level, hall, state.withProposals(kept).withDeclined(declined));
		}
	}

	/** Declines a proposal: gone, and its rule not proposed again for {@link #DECLINE_DAYS} days. */
	public static boolean decline(ServerLevel level, BlockPos hall, @Nullable ServerPlayer player, int id) {
		if (player != null && !mayUse(level, hall, player)) {
			return false;
		}
		State state = of(level, hall);
		Optional<Proposal> proposal = state.get(id);
		if (proposal.isEmpty()) {
			return false;
		}
		Map<String, Long> declined = new HashMap<>(state.declined());
		declined.put(proposal.get().rule().toString(), StewardWishes.day(level) + DECLINE_DAYS);
		save(level, hall, state.withProposals(state.proposals().stream().filter(p -> p.id() != id).toList()).withDeclined(declined));
		return true;
	}

	/** "Show me": the proposal's outline glows for {@code player} for {@link #SHOW_TICKS}. */
	public static boolean show(ServerLevel level, BlockPos hall, ServerPlayer player, int id) {
		Optional<Proposal> proposal = of(level, hall).get(id);
		Optional<Blueprint> blueprint = proposal.flatMap(p -> BlueprintLibrary.get(level, p.blueprint()));
		if (blueprint.isEmpty()) {
			return false;
		}
		BlueprintOutline.glow(player, proposal.get().placement(), blueprint.get().size(), SHOW_TICKS);
		return true;
	}

	/** "Another spot": the search goes on past the spots found so far; the proposal waits for it. False for an upgrade. */
	public static boolean anotherSpot(ServerLevel level, BlockPos hall, @Nullable ServerPlayer player, int id) {
		if (player != null && !mayUse(level, hall, player)) {
			return false;
		}
		State state = of(level, hall);
		Optional<Proposal> proposal = state.get(id);
		if (proposal.isEmpty() || proposal.get().upgrade() || !proposal.get().isBuild()) {
			return false;
		}
		Proposal p = proposal.get();
		Proposal next = p.with(p.blueprint(), p.placement(), p.skip() + 1, true);
		replace(level, hall, next);
		Plots.request(level, hall, request(next));
		return true;
	}

	private static Plots.Request request(Proposal p) {
		return new Plots.Request(List.of(p.blueprint()), p.kind(), p.skip());
	}

	/** Resolves the proposals waiting for another spot whose search has finished (none left: back to the spot it had). */
	public static void resolve(ServerLevel level, BlockPos hall) {
		for (Proposal p : of(level, hall).proposals()) {
			if (!p.searching()) {
				continue;
			}
			Plots.Request request = request(p);
			Optional<Optional<Plots.Plot>> result = Plots.result(level, hall, request);
			if (result.isEmpty()) {
				Plots.request(level, hall, request); // keeps it going
				continue;
			}
			replace(level, hall, result.get()
				.map(plot -> p.with(plot.blueprint(), plot.placement(), p.skip(), false))
				.orElseGet(() -> p.with(p.blueprint(), p.placement(), 0, false)));
		}
	}

	/** "Another style": the same blueprint in the next style there is (as drawn after the last), on the same spot. */
	public static boolean anotherStyle(ServerLevel level, BlockPos hall, @Nullable ServerPlayer player, int id) {
		if (player != null && !mayUse(level, hall, player)) {
			return false;
		}
		Optional<Proposal> proposal = of(level, hall).get(id);
		if (proposal.isEmpty() || !proposal.get().isBuild()) {
			return false;
		}
		Proposal p = proposal.get();
		Optional<ResourceLocation> next = nextStyle(level, p.blueprint());
		if (next.isEmpty()) {
			return false;
		}
		replace(level, hall, p.with(next.get(), p.placement(), p.skip(), false));
		return true;
	}

	/** The next style of {@code blueprint} with the same size (so it fits the same spot), going round; empty if none. */
	static Optional<ResourceLocation> nextStyle(ServerLevel level, ResourceLocation blueprint) {
		ResourceLocation base = BlueprintStyles.base(blueprint);
		Optional<Vec3i> size = BlueprintLibrary.get(level, blueprint).map(Blueprint::size);
		if (size.isEmpty()) {
			return Optional.empty();
		}
		List<String> names = new ArrayList<>();
		names.add("");
		BlueprintStyles.all().forEach(s -> names.add(s.name()));
		int at = names.indexOf(BlueprintStyles.styleOf(blueprint).orElse(""));
		for (int i = 1; i < names.size(); i++) {
			ResourceLocation id = BlueprintStyles.styled(base, names.get((at + i) % names.size()));
			if (BlueprintLibrary.get(level, id).map(b -> b.size().equals(size.get())).orElse(false)) {
				return Optional.of(id);
			}
		}
		return Optional.empty();
	}

	private static void replace(ServerLevel level, BlockPos hall, Proposal next) {
		State state = of(level, hall);
		save(level, hall, state.withProposals(state.proposals().stream().map(p -> p.id() == next.id() ? next : p).toList()));
	}

	// ---- approving ---------------------------------------------------------------------------------------------

	/** The builders of the village: villagers with the job and a bench, by the hall. */
	static List<Villager> builders(ServerLevel level, BlockPos hall) {
		return level.getEntities(EntityType.VILLAGER, new AABB(hall).inflate(VillageHalls.RADIUS),
				v -> v.isAlive() && Builders.isBuilder(v) && Builders.benchPos(v).isPresent())
			.stream().sorted(Comparator.comparingDouble(v -> v.distanceToSqr(hall.getX(), hall.getY(), hall.getZ()))).toList();
	}

	/** Which builder would build {@code proposal}: the nearest idle one whose bench reaches it, else the one with the shortest queue. */
	public static Optional<Builder> builderFor(ServerLevel level, BlockPos hall, Proposal proposal) {
		Optional<Blueprint> blueprint = BlueprintLibrary.get(level, proposal.blueprint());
		if (blueprint.isEmpty()) {
			return Optional.empty();
		}
		BlockPos centre = BlueprintOutline.bounds(proposal.placement(), blueprint.get().size()).getCenter();
		Builder best = null;
		int bestQueue = Integer.MAX_VALUE;
		for (Villager v : builders(level, hall)) {
			BlockPos bench = Builders.benchPos(v).orElseThrow();
			if (Math.sqrt(centre.distSqr(bench)) > Builders.MAX_SITE_DISTANCE) {
				continue;
			}
			BuildSite active = Builders.activeSite(level, v);
			int queue = active == null ? 0 : 1 + Builders.queue(level, v).size();
			if (queue <= Builders.MAX_QUEUE && queue < bestQueue) {
				List<BuildSite> waiting = Builders.queue(level, v);
				best = new Builder(v, active == null ? null : waiting.isEmpty() ? active : waiting.get(waiting.size() - 1));
				bestQueue = queue;
			}
		}
		return Optional.ofNullable(best);
	}

	/**
	 * Approves a proposal: a build site for its builder (started, or queued after what he's building), owned by the hall's
	 * owner, marked as the Steward's, noted in the chronicle; the wish counts as carried out. {@code player} null: the
	 * Steward approves it himself (Run the village).
	 */
	public static Outcome approve(ServerLevel level, BlockPos hall, @Nullable ServerPlayer player, int id) {
		if (player != null && !mayUse(level, hall, player)) {
			return Outcome.NOT_ALLOWED;
		}
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return Outcome.GONE;
		}
		Optional<Proposal> found = of(level, hall).get(id);
		if (found.isEmpty()) {
			return Outcome.GONE;
		}
		Proposal proposal = found.get();
		if (proposal.searching()) {
			return Outcome.SEARCHING;
		}
		Villager steward = Stewards.stewardOf(level, hall);
		if (steward == null) {
			return Outcome.NO_STEWARD;
		}
		if (proposal.isJobs()) {
			return approveJobs(level, hall, steward, proposal);
		}
		if (!proposal.topic().isEmpty()) {
			return approveResearch(level, hall, entity, steward, proposal);
		}
		if (proposal.wall().isPresent()) {
			return approveWall(level, hall, steward, proposal, proposal.wall().get());
		}
		if (openSites(level, hall).size() >= Stewards.maxOpenBuilds(level, steward)) {
			return Outcome.FULL;
		}
		Optional<Blueprint> blueprint = BlueprintLibrary.get(level, proposal.blueprint());
		if (blueprint.isEmpty()) {
			return Outcome.GONE;
		}
		BoundingBox box = BlueprintOutline.bounds(proposal.placement(), blueprint.get().size());
		for (BuildSite other : BuildSiteManager.get(level).all()) {
			if (other.placement().dimension().equals(proposal.placement().dimension()) && BlueprintLibrary.get(level, other.structure())
				.map(b -> BlueprintOutline.bounds(other.placement(), b.size()).intersects(box)).orElse(false)) {
				return Outcome.OVERLAPS;
			}
		}
		Optional<Builder> builder = builderFor(level, hall, proposal);
		if (builder.isEmpty()) {
			return Outcome.NO_BUILDER;
		}
		Villager villager = builder.get().villager();
		UUID owner = entity.owner() != null ? entity.owner() : player != null ? player.getUUID() : villager.getUUID();
		String ownerName = entity.owner() != null ? entity.ownerName() : player != null ? player.getGameProfile().getName() : "";
		boolean busy = Builders.activeSite(level, villager) != null;
		BuildSite site = busy
			? Builders.enqueue(level, villager, owner, ownerName, proposal.blueprint(), proposal.placement())
			: Builders.start(level, villager, owner, ownerName, proposal.blueprint(), proposal.placement());
		site.setStewardHall(hall);
		State state = of(level, hall);
		save(level, hall, state.withProposals(state.proposals().stream().filter(p -> p.id() != id).toList()));
		StewardWishes.carriedOut(level, hall, proposal.rule());
		Chronicle.record(level, hall, Chronicle.Kind.PLANS, Component.translatable(
			proposal.upgrade() ? "chronicle.aliveworkplace.plans_upgrade" : "chronicle.aliveworkplace.plans",
			steward.getDisplayName(), proposal.name(), villager.getDisplayName()));
		return busy ? Outcome.QUEUED : Outcome.STARTED;
	}

	/** The jobs that can still be given: the proposal leaves the desk, and with any job given the wish is carried out. */
	private static Outcome approveJobs(ServerLevel level, BlockPos hall, Villager steward, Proposal proposal) {
		List<StewardJobs.Job> given = StewardJobs.give(level, steward, proposal.jobs());
		State state = of(level, hall);
		if (given.isEmpty()) {
			save(level, hall, state.withProposals(state.proposals().stream().filter(p -> p.id() != proposal.id()).toList()));
			return Outcome.JOBS_TAKEN;
		}
		save(level, hall, state.withProposals(state.proposals().stream().filter(p -> p.id() != proposal.id()).toList()));
		StewardWishes.carriedOut(level, hall, proposal.rule());
		Chronicle.record(level, hall, Chronicle.Kind.PLANS, Component.translatable("chronicle.aliveworkplace.plans_jobs",
			steward.getDisplayName(), StewardJobs.list(level, hall, given)));
		return Outcome.STARTED;
	}

	/** The wall approved (27.18): the line on the plan is marked for building, and its pieces open from the hall's round. */
	private static Outcome approveWall(ServerLevel level, BlockPos hall, Villager steward, Proposal proposal, WallKits.Kit kit) {
		State state = of(level, hall);
		save(level, hall, state.withProposals(state.proposals().stream().filter(p -> p.id() != proposal.id()).toList()));
		if (!Walls.approve(level, hall, steward, kit)) {
			return Outcome.GONE;
		}
		StewardWishes.carriedOut(level, hall, proposal.rule());
		return Outcome.STARTED;
	}

	/** The topic chosen for the scholars, if nothing else is being researched and it can still be taken up. */
	private static Outcome approveResearch(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, Villager steward, Proposal proposal) {
		Research.State research = entity.research();
		Optional<Research.Topic> topic = proposal.researchTopic();
		State state = of(level, hall);
		if (research.currentTopic() != null) {
			return Outcome.RESEARCHING;
		}
		save(level, hall, state.withProposals(state.proposals().stream().filter(p -> p.id() != proposal.id()).toList()));
		if (topic.isEmpty() || !research.available(topic.get())) {
			return Outcome.GONE;
		}
		entity.setResearch(research.choose(topic.get()));
		Research.forget();
		StewardWishes.carriedOut(level, hall, proposal.rule());
		Chronicle.record(level, hall, Chronicle.Kind.PLANS, Component.translatable("chronicle.aliveworkplace.plans_research",
			steward.getDisplayName(), topic.get().title()));
		return Outcome.STARTED;
	}

	/** What a proposal is called in the morning's line: a build's name, or the jobs with who goes where. */
	static Component told(ServerLevel level, BlockPos hall, Proposal p) {
		return p.isJobs() ? StewardJobs.line(level, hall, p.jobs()) : p.name();
	}

	/** Approves every proposal it can, in order; the names of those started or queued. */
	public static List<Component> approveAll(ServerLevel level, BlockPos hall, @Nullable ServerPlayer player) {
		List<Component> started = new ArrayList<>();
		for (Proposal p : of(level, hall).proposals()) {
			Component name = told(level, hall, p);
			if (approve(level, hall, player, p.id()).ok()) {
				started.add(name);
			}
		}
		return started;
	}

	/** Cancels one of his open builds (the builder empties his bag into the store, as for any cancel). */
	public static boolean cancel(ServerLevel level, BlockPos hall, ServerPlayer player, UUID site) {
		if (!mayUse(level, hall, player)) {
			return false;
		}
		BuildSite found = BuildSiteManager.get(level).get(site);
		if (found == null || !hall.equals(found.stewardHall())) {
			return false;
		}
		Builders.cancel(level, found);
		return true;
	}

	// ---- the morning ---------------------------------------------------------------------------------------------

	/**
	 * At the hall, every second (from {@link StewardWishes#plan}): lapses old proposals, turns today's wishes with a plot into
	 * proposals, tells the owner once a morning that there are new ones, and in Run the village approves them himself once
	 * the morning's searches are done, with one line to the owner.
	 */
	public static void plan(ServerLevel level, Villager steward, BlockPos hall) {
		if (mode(level, hall) == CityPlan.Mode.REST) {
			return;
		}
		lapse(level, hall);
		resolve(level, hall);
		long day = StewardWishes.day(level);
		int before = of(level, hall).next();
		boolean searching = false;
		Map<ResourceLocation, Boolean> upgrading = new HashMap<>();
		for (StewardWishes.Wish wish : StewardWishes.of(level, hall).wishes()) {
			StewardRules.Effect effect = wish.effect();
			if (effect.kind() == StewardRules.Kind.BUILD) {
				Optional<Plots.Request> request = StewardWishes.plotFor(level, hall, wish);
				if (request.isEmpty()) {
					continue;
				}
				Optional<Optional<Plots.Plot>> result = Plots.result(level, hall, request.get());
				if (result.isEmpty()) {
					searching = true;
					continue;
				}
				result.get().ifPresent(plot -> offer(level, hall, wish, plot.blueprint(), plot.placement(), plot.zone(), false));
			} else if (effect.kind() == StewardRules.Kind.ASSIGN_JOBS) {
				offerJobs(level, hall, wish, day);
			} else if (effect.kind() == StewardRules.Kind.UPGRADE) {
				upgradeFor(level, hall, effect.blueprint(), effect.addsBeds()).ifPresent(f -> {
					if (upgrading.putIfAbsent(f.structure(), true) == null) {
						offer(level, hall, wish, BlueprintUpgrades.upgradeOf(f.structure()), f.placement(), "", true);
					}
				});
			}
		}
		Walls.propose(level, hall, steward); // 27.18: a raided village's wall
		State state = of(level, hall);
		if (state.next() != before) {
			tell(level, hall, steward);
		}
		if (runsItself(level, hall) && !searching && of(level, hall).ran() != day) {
			List<Component> started = approveAll(level, hall, null);
			state = of(level, hall);
			save(level, hall, state.withRan(day));
			ServerPlayer owner = owner(level, hall);
			if (owner != null && !started.isEmpty()) {
				Chat.chat(owner, Component.translatable("message.aliveworkplace.steward.desk.ran", steward.getDisplayName(),
					VillageHalls.name(level, hall), list(started)).withStyle(ChatFormatting.GOLD));
			}
		}
	}

	/** Once a morning, unless he runs the village himself: tells the owner there are new proposals on the desk. */
	static void tell(ServerLevel level, BlockPos hall, Villager steward) {
		State state = of(level, hall);
		long day = StewardWishes.day(level);
		if (state.told() == day || runsItself(level, hall)) {
			return;
		}
		ServerPlayer owner = owner(level, hall);
		if (owner != null) {
			Chat.chat(owner, Component.translatable("message.aliveworkplace.steward.desk.new", steward.getDisplayName(),
				VillageHalls.name(level, hall), state.proposals().size()).withStyle(ChatFormatting.GOLD));
		}
		save(level, hall, of(level, hall).withTold(day));
	}

	/**
	 * {@code assign_jobs} (27.9), once a morning: the morning's jobs ({@link StewardJobs#plan}) as one proposal, which Run
	 * the village approves with the rest; jobs with no free block go to 27.11 ({@link StewardJobs#WORKPLACE_WANTED}).
	 */
	static void offerJobs(ServerLevel level, BlockPos hall, StewardWishes.Wish wish, long day) {
		State state = of(level, hall);
		if (state.jobsDay() == day || declined(state, wish.rule(), day) || state.proposals().stream().anyMatch(p -> p.rule().equals(wish.rule()))) {
			return;
		}
		save(level, hall, state.withJobsDay(day));
		StewardJobs.Plan plan = StewardJobs.plan(level, hall);
		plan.wanted().forEach(job -> StewardJobs.WORKPLACE_WANTED.want(level, hall, job));
		if (!plan.jobs().isEmpty()) {
			offerOther(level, hall, wish.rule(), wish.why(), wish.numbers(), plan.jobs(), "");
		}
	}

	/**
	 * {@link ScholarWork#IDLE}: a scholar works and nothing is being researched. With a {@code research} wish today, the
	 * Steward picks the next topic ({@link StewardResearch#pick}): himself in Run the village, else as a proposal.
	 */
	public static void scholarIdle(ServerLevel level, Villager scholar, BlockPos hall) {
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) || mode(level, hall) == CityPlan.Mode.REST
			|| entity.research().currentTopic() != null) {
			return;
		}
		long day = StewardWishes.day(level);
		State state = of(level, hall);
		StewardWishes.State wishes = StewardWishes.of(level, hall);
		if (state.researchDay() == day || wishes.day() != day) {
			return;
		}
		Optional<StewardWishes.Wish> wish = wishes.wishes().stream()
			.filter(w -> w.effect().kind() == StewardRules.Kind.RESEARCH && !declined(state, w.rule(), day))
			.findFirst(); // highest priority first
		if (wish.isEmpty() || state.proposals().stream().anyMatch(p -> !p.topic().isEmpty())) {
			return;
		}
		Villager steward = Stewards.stewardOf(level, hall);
		if (steward == null) {
			return;
		}
		save(level, hall, state.withResearchDay(day));
		Optional<StewardResearch.Pick> pick = StewardResearch.pick(entity.research(), StewardResearch.village(level, hall),
			wish.get().effect().topic().flatMap(StewardRules::topicOf));
		if (pick.isEmpty()) {
			return;
		}
		Optional<Proposal> proposal = offerOther(level, hall, wish.get().rule(), pick.get().why(), List.of(pick.get().number()), List.of(),
			pick.get().topic().key());
		if (proposal.isEmpty()) {
			return;
		}
		if (runsItself(level, hall)) {
			if (approve(level, hall, null, proposal.get().id()).ok()) {
				ServerPlayer owner = owner(level, hall);
				if (owner != null) {
					Chat.chat(owner, Component.translatable("message.aliveworkplace.steward.research.chosen", steward.getDisplayName(),
						VillageHalls.name(level, hall), pick.get().topic().title()).withStyle(ChatFormatting.GOLD));
				}
			}
		} else {
			tell(level, hall, steward);
		}
	}

	/** A finished building by the hall (of {@code blueprint}'s family, if given) with a next tier, nobody building there yet. */
	static Optional<BuildSiteManager.Finished> upgradeFor(ServerLevel level, BlockPos hall, Optional<ResourceLocation> blueprint, boolean addsBeds) {
		List<BuildSite> sites = new ArrayList<>(BuildSiteManager.get(level).all());
		List<Proposal> proposed = of(level, hall).proposals();
		return VillageAdvice.upgradable(level, hall).stream()
			.filter(f -> blueprint.isEmpty() || StewardConditions.family(f.structure()).equals(StewardConditions.family(blueprint.get())))
			.filter(f -> !addsBeds || StewardConditions.addsBeds(level, f.structure()))
			.filter(f -> sites.stream().noneMatch(s -> s.placement().equals(f.placement())))
			.filter(f -> proposed.stream().noneMatch(p -> p.placement().equals(f.placement())))
			.findFirst();
	}

	@Nullable
	static ServerPlayer owner(ServerLevel level, BlockPos hall) {
		return level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity && entity.owner() != null
			? level.getServer().getPlayerList().getPlayer(entity.owner()) : null;
	}

	/** "A, B and C". */
	public static Component list(List<Component> names) {
		MutableComponent out = Component.empty();
		for (int i = 0; i < names.size(); i++) {
			if (i > 0) {
				out.append(i == names.size() - 1 ? Component.translatable("message.aliveworkplace.steward.desk.and") : Component.literal(", "));
			}
			out.append(names.get(i));
		}
		return out;
	}

	// ---- what a proposal needs -----------------------------------------------------------------------------------

	/** One material: how many the build needs and how many are spare in the builder's store. */
	public record Need(Item item, int count, long inStore) {
	}

	/** The {@link #MATERIALS} materials {@code proposal} needs most, with how many of each are in store (by its builder). */
	public static List<Need> needs(ServerLevel level, BlockPos hall, Proposal proposal) {
		Optional<Blueprint> blueprint = BlueprintLibrary.get(level, proposal.blueprint());
		if (blueprint.isEmpty()) {
			return List.of();
		}
		BuildPlan plan = BuildPlan.create(blueprint.get(), new BlueprintData.Placement(Ids.of(level.dimension()), BlockPos.ZERO, Rotation.NONE, Mirror.NONE));
		Map<Item, Integer> grouped = new LinkedHashMap<>();
		plan.materials().forEach((item, n) -> grouped.merge(MaterialFamilies.key(item), n, Integer::sum));
		List<BlockPos> chests = builderFor(level, hall, proposal)
			.map(b -> {
				BlockPos bench = Builders.benchPos(b.villager()).orElseThrow();
				List<BlockPos> out = new ArrayList<>(io.github.jcondedata.aliveworkplace.build.SupplyContainers.find(level, bench, null));
				Builders.storehouseChests(level, b.villager(), bench, null).forEach(c -> {
					if (!out.contains(c)) {
						out.add(c);
					}
				});
				return out;
			}).orElse(List.of());
		return grouped.entrySet().stream()
			.sorted(Map.Entry.<Item, Integer>comparingByValue().reversed())
			.limit(MATERIALS)
			.map(e -> new Need(e.getKey(), e.getValue(), chests.isEmpty() ? 0 : Builders.spare(level, chests, e.getKey(), Map.of())))
			.toList();
	}

	public static void init() {
		ScholarWork.IDLE = StewardDesk::scholarIdle;
		StewardWork.OPEN_SITES = (level, steward) -> Stewards.hallOf(level, steward)
			.map(hall -> openSites(level, hall).stream().map(s -> s.placement().origin()).toList()).orElse(List.of());
	}

	private StewardDesk() {
	}
}
