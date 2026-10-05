package io.github.jcondedata.aliveworkplace.city;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.hall.VillageAdvice;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.research.Research;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.VillagerProfession;

/**
 * The conditions of the Steward's rules (ROADMAP 27.6): small classes, each reading the village round a hall from one
 * {@link Facts}, counted once for all the rules. They read the same numbers the hall's "What next?" tips do
 * ({@link VillageAdvice}'s {@code bedsShort}, {@code jobless}, {@code foodWanted}, {@code homes}, {@code upgradable},
 * {@code poiCount}), so the Steward and the tips always agree. Each gives whether it held, one number (what a rule's
 * {@code why} is filled with) and a line for {@code /workplace steward explain}. {@code {"not": {...}}} turns any
 * condition round. 27.12 adds more through {@link #register}.
 */
public final class StewardConditions {
	/** The village round a hall, counted lazily and once: every rule's conditions read the same numbers. */
	public static final class Facts {
		public final ServerLevel level;
		public final BlockPos hall;
		private VillageHalls.Census census;
		private VillageAdvice.HomeCount homes;
		private List<BuildSiteManager.Finished> finished;
		private List<BuildSiteManager.Finished> upgradable;
		private int[] store;
		private List<VillagerProfession> wanted;

		private Facts(ServerLevel level, BlockPos hall) {
			this.level = level;
			this.hall = hall;
		}

		public static Facts of(ServerLevel level, BlockPos hall) {
			return new Facts(level, hall);
		}

		public VillageHalls.Census census() {
			if (census == null) {
				census = VillageHalls.census(level, hall);
			}
			return census;
		}

		public VillageAdvice.HomeCount homes() {
			if (homes == null) {
				homes = VillageAdvice.homes(level, hall);
			}
			return homes;
		}

		public List<BuildSiteManager.Finished> finished() {
			if (finished == null) {
				finished = BuildSiteManager.get(level).finishedNear(level, hall, VillageHalls.RADIUS);
			}
			return finished;
		}

		public List<BuildSiteManager.Finished> upgradable() {
			if (upgradable == null) {
				upgradable = VillageAdvice.upgradable(level, hall);
			}
			return upgradable;
		}

		/**
		 * The jobs the village wants that have no free block left (27.11): the same gaps the Steward's morning jobs
		 * ({@link StewardJobs#plan}) hand to {@link StewardJobs#WORKPLACE_WANTED}; none while nobody waits for a job.
		 */
		public List<VillagerProfession> wanted() {
			if (wanted == null) {
				wanted = census().jobless().stream().anyMatch(StewardJobs::wantsJob) ? StewardJobs.plan(level, hall).wanted() : List.of();
			}
			return wanted;
		}

		public VillageRanks.Rank rank() {
			return VillageRanks.of(level, hall);
		}

		public Research.State research() {
			return level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity ? entity.research() : Research.State.EMPTY;
		}

		/** The Storehouses' chests: all their slots and the empty ones. */
		public int[] store() {
			if (store == null) {
				Set<BlockPos> chests = new LinkedHashSet<>();
				level.getPoiManager().findAll(h -> h.is(ModVillagers.STOREHOUSE_POI), p -> true, hall, VillageHalls.RADIUS, PoiManager.Occupancy.ANY)
					.forEach(storehouse -> chests.addAll(SupplyContainers.find(level, storehouse.immutable(), null)));
				List<BlockPos> list = new ArrayList<>(chests);
				store = new int[] {SupplyContainers.slots(level, list), SupplyContainers.freeSlots(level, list)};
			}
			return store;
		}
	}

	/** What a condition found: whether it held, its number (for the rule's {@code why}) and its explain line. */
	public record Check(boolean held, long value, Component shown) {
	}

	/** One condition of a rule. */
	public interface Condition {
		String type();

		Check test(Facts facts);
	}

	/** Reads a condition's fields; throws {@link StewardRules.BadRule} on a bad one. */
	@FunctionalInterface
	public interface Parser {
		Condition parse(StewardRules.Fields fields);
	}

	private static final Map<String, Parser> PARSERS = new LinkedHashMap<>();

	/** Adds a kind of condition ({@code type} in a rule's {@code when}). */
	public static void register(String type, Parser parser) {
		PARSERS.put(type, parser);
	}

	public static Set<String> types() {
		return Collections.unmodifiableSet(PARSERS.keySet());
	}

	/**
	 * Reads one condition of a rule's {@code when}: one name and its fields, {@code {"beds_short": {"at_least": 1}}}, or
	 * {@code {"not": {...}}} round another.
	 */
	static Condition parse(com.google.gson.JsonElement json, String at) {
		if (json == null || !json.isJsonObject() || json.getAsJsonObject().size() != 1) {
			throw new StewardRules.BadRule(at, "a condition is one name and its fields, as {\"beds_short\": {\"at_least\": 1}}");
		}
		Map.Entry<String, com.google.gson.JsonElement> only = json.getAsJsonObject().entrySet().iterator().next();
		String type = only.getKey();
		String path = at + "." + type;
		if (type.equals("not")) {
			return new Not(parse(only.getValue(), path));
		}
		Parser parser = PARSERS.get(type);
		if (parser == null) {
			throw new StewardRules.BadRule(path, "unknown condition (known: " + String.join(", ", PARSERS.keySet()) + ")");
		}
		if (!only.getValue().isJsonObject()) {
			throw new StewardRules.BadRule(path, "its fields must be an object ({} for none), not " + only.getValue());
		}
		StewardRules.Fields fields = new StewardRules.Fields(only.getValue().getAsJsonObject(), path);
		Condition condition = parser.parse(fields);
		fields.done();
		return condition;
	}

	static Component key(String type, Object... args) {
		return Component.translatable("steward.aliveworkplace.condition." + type, args);
	}

	/** Any condition turned round: {@code {"not": {"research_at_least": {"topic": "hearth"}}}}. */
	public record Not(Condition inner) implements Condition {
		@Override
		public String type() {
			return inner.type();
		}

		@Override
		public Check test(Facts facts) {
			Check check = inner.test(facts);
			return new Check(!check.held(), check.value(), key("not", check.shown()));
		}
	}

	/** {@code beds_short {at_least}}: villagers (children too) more than the beds, the "beds" tip's number. */
	public record BedsShort(int atLeast) implements Condition {
		@Override
		public String type() {
			return "beds_short";
		}

		@Override
		public Check test(Facts facts) {
			int shortBy = VillageAdvice.bedsShort(facts.census());
			return new Check(shortBy >= atLeast, shortBy, key(type(), shortBy, atLeast));
		}
	}

	/** {@code food_short {meals_per_adult}}: the store holds less food than that for each grown-up; the number is how much less. */
	public record FoodShort(int mealsPerAdult) implements Condition {
		@Override
		public String type() {
			return "food_short";
		}

		@Override
		public Check test(Facts facts) {
			long food = facts.census().food();
			long wanted = VillageAdvice.foodWanted(facts.census(), mealsPerAdult);
			boolean held = VillageAdvice.adults(facts.census()) > 0 && food < wanted;
			return new Check(held, Math.max(0, wanted - food), key(type(), food, wanted, mealsPerAdult));
		}
	}

	/** {@code missing_poi {poi}}: the village has no point of interest of that type (a Storehouse, say). */
	public record MissingPoi(ResourceLocation poi) implements Condition {
		@Override
		public String type() {
			return "missing_poi";
		}

		@Override
		public Check test(Facts facts) {
			long count = VillageAdvice.poiCount(facts.level, facts.hall, ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, poi));
			return new Check(count == 0, count, key(type(), poi.toString(), count));
		}
	}

	/**
	 * {@code worker_without_workstation {professions, wanted}}: grown-ups with a job (of those, or any) but no
	 * workstation; with {@code "wanted": true} (27.11's workplace rules) also each of those jobs the village wants with
	 * no free block left ({@link Facts#wanted}), once the village has a builder to build it.
	 */
	public record WorkerWithoutWorkstation(Set<ResourceLocation> professions, boolean wanted) implements Condition {
		@Override
		public String type() {
			return "worker_without_workstation";
		}

		@Override
		public Check test(Facts facts) {
			long count = facts.census().jobless().stream().filter(v -> {
				VillagerProfession job = v.getVillagerData().getProfession();
				return job != VillagerProfession.NONE && job != VillagerProfession.NITWIT
					&& (professions.isEmpty() || professions.contains(BuiltInRegistries.VILLAGER_PROFESSION.getKey(job)));
			}).count();
			if (!wanted) {
				return new Check(count > 0, count, key(type(), which(), count));
			}
			// A wanted job's building waits for someone to build it: with no builder, no_builder asks the player first.
			long jobs = VillageAdvice.workers(facts.census(), ModVillagers.BUILDER) == 0 ? 0 : facts.wanted().stream()
				.filter(p -> professions.isEmpty() || professions.contains(BuiltInRegistries.VILLAGER_PROFESSION.getKey(p))).count();
			return new Check(count + jobs > 0, count + jobs, key(type() + "_or_wanted", which(), count, jobs));
		}

		private Component which() {
			return professions.isEmpty() ? Component.translatable("steward.aliveworkplace.condition.any_job")
				: Component.literal(professions.stream().map(ResourceLocation::toString).sorted().collect(Collectors.joining(", ")));
		}
	}

	/** {@code jobless {at_least}}: grown-ups without work (nitwits aside), the "jobless" tip's number. */
	public record Jobless(int atLeast) implements Condition {
		@Override
		public String type() {
			return "jobless";
		}

		@Override
		public Check test(Facts facts) {
			long count = VillageAdvice.jobless(facts.census());
			return new Check(count >= atLeast, count, key(type(), count, atLeast));
		}
	}

	/** {@code no_builder}: nobody works as a Builder (the "builder" tip). */
	public record NoBuilder() implements Condition {
		@Override
		public String type() {
			return "no_builder";
		}

		@Override
		public Check test(Facts facts) {
			long builders = VillageAdvice.workers(facts.census(), ModVillagers.BUILDER);
			return new Check(builders == 0, builders, key(type(), builders));
		}
	}

	/** {@code rank_at_least {rank}}: the village is of that rank or higher; the number is its rank (Hamlet 0 to City 3). */
	public record RankAtLeast(VillageRanks.Rank rank) implements Condition {
		@Override
		public String type() {
			return "rank_at_least";
		}

		@Override
		public Check test(Facts facts) {
			VillageRanks.Rank now = facts.rank();
			return new Check(now.ordinal() >= rank.ordinal(), now.ordinal(), key(type(), now.title(), rank.title()));
		}
	}

	/** {@code villagers_at_least {n}}: at least that many villagers, children too. */
	public record VillagersAtLeast(int n) implements Condition {
		@Override
		public String type() {
			return "villagers_at_least";
		}

		@Override
		public Check test(Facts facts) {
			int villagers = facts.census().villagers();
			return new Check(villagers >= n, villagers, key(type(), villagers, n));
		}
	}

	/** {@code built_count_below {blueprint, n}}: fewer than {@code n} of that building finished, in any style or tier. */
	public record BuiltCountBelow(ResourceLocation blueprint, int n) implements Condition {
		@Override
		public String type() {
			return "built_count_below";
		}

		@Override
		public Check test(Facts facts) {
			ResourceLocation family = family(blueprint);
			long count = facts.finished().stream().filter(f -> family(f.structure()).equals(family)).count();
			return new Check(count < n, count, key(type(), Blueprints.displayName(blueprint), count, n));
		}
	}

	/** {@code upgrade_available {blueprint}}: a finished building (of that one, any style or tier; or any) has a next tier. */
	public record UpgradeAvailable(Optional<ResourceLocation> blueprint) implements Condition {
		@Override
		public String type() {
			return "upgrade_available";
		}

		@Override
		public Check test(Facts facts) {
			long count = facts.upgradable().stream()
				.filter(f -> blueprint.isEmpty() || family(f.structure()).equals(family(blueprint.get()))).count();
			Component which = blueprint.<Component>map(Blueprints::displayName)
				.orElse(Component.translatable("steward.aliveworkplace.condition.any_building"));
			return new Check(count > 0, count, key(type(), which, count));
		}
	}

	/**
	 * {@code upgrade_adds_beds {}}: finished buildings in the village whose next tier has more beds (homes_upgrade, 27.10).
	 * The beds are counted in the two blueprints, never written down.
	 */
	public record UpgradeAddsBeds() implements Condition {
		@Override
		public String type() {
			return "upgrade_adds_beds";
		}

		@Override
		public Check test(Facts facts) {
			long count = facts.upgradable().stream().filter(f -> addsBeds(facts.level, f.structure())).count();
			return new Check(count > 0, count, key(type(), count));
		}
	}

	/** The beds (head halves) in a blueprint, 0 if the server doesn't have it. */
	public static int beds(net.minecraft.server.level.ServerLevel level, ResourceLocation blueprint) {
		return io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary.get(level, blueprint)
			.map(b -> (int) b.blocks().stream().filter(e -> e.state().getBlock() instanceof net.minecraft.world.level.block.BedBlock
				&& e.state().getValue(net.minecraft.world.level.block.BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.HEAD).count())
			.orElse(0);
	}

	/** Whether the next tier of a building has more beds than it (counted from both blueprints). */
	public static boolean addsBeds(net.minecraft.server.level.ServerLevel level, ResourceLocation structure) {
		ResourceLocation up = BlueprintUpgrades.upgradeOf(structure);
		return !up.equals(structure) && beds(level, up) > beds(level, structure);
	}

	/** {@code homes_tier_low {share}}: more than that share of grown-ups live in tier I homes or none (the "homes" tip at 0.5). */
	public record HomesTierLow(double share) implements Condition {
		@Override
		public String type() {
			return "homes_tier_low";
		}

		@Override
		public Check test(Facts facts) {
			VillageAdvice.HomeCount homes = facts.homes();
			boolean held = homes.grown() >= VillageAdvice.MIN_FOR_HOMES && homes.plain() > share * homes.grown();
			return new Check(held, homes.plain(), key(type(), homes.plain(), homes.grown(), percent(share)));
		}
	}

	/** {@code store_full {share}}: the Storehouses' chests have at least that share of their slots taken; the number is the percent. */
	public record StoreFull(double share) implements Condition {
		@Override
		public String type() {
			return "store_full";
		}

		@Override
		public Check test(Facts facts) {
			int[] store = facts.store();
			int slots = store[0];
			int used = slots - store[1];
			int pct = slots == 0 ? 0 : (int) Math.floor(100.0 * used / slots);
			boolean held = slots > 0 && used >= share * slots;
			return new Check(held, pct, key(type(), pct, percent(share), slots));
		}
	}

	/** {@code research_idle}: nothing is being researched and something is left to research; the number is the topics open. */
	public record ResearchIdle() implements Condition {
		@Override
		public String type() {
			return "research_idle";
		}

		@Override
		public Check test(Facts facts) {
			Research.State state = facts.research();
			long open = java.util.Arrays.stream(Research.Topic.values()).filter(state::available).count();
			Research.Topic current = state.currentTopic();
			Component now = current != null ? current.title() : Component.translatable("steward.aliveworkplace.condition.research_idle.nothing");
			return new Check(current == null && open > 0, open, key(type(), now, open));
		}
	}

	/** {@code research_at_least {topic, level}}: the village has researched that topic to that level. */
	public record ResearchAtLeast(Research.Topic topic, int level) implements Condition {
		@Override
		public String type() {
			return "research_at_least";
		}

		@Override
		public Check test(Facts facts) {
			int now = facts.research().level(topic);
			return new Check(now >= level, now, key(type(), topic.title(), now, level));
		}
	}

	/** {@code mod_loaded {mod}}: that mod is installed. */
	public record ModLoaded(String mod) implements Condition {
		@Override
		public String type() {
			return "mod_loaded";
		}

		@Override
		public Check test(Facts facts) {
			boolean loaded = Platform.get().isModLoaded(mod);
			return new Check(loaded, loaded ? 1 : 0, key(type(), mod));
		}
	}

	/** A blueprint's family: the base of its style and of its tiers ({@code styled/cherry/.../stone_house_3} is {@code stone_house}). */
	public static ResourceLocation family(ResourceLocation id) {
		ResourceLocation base = BlueprintStyles.base(id);
		for (int i = 0; i < 100; i++) {
			Optional<ResourceLocation> lower = BlueprintUpgrades.baseOf(base);
			if (lower.isEmpty()) {
				break;
			}
			base = lower.get();
		}
		return base;
	}

	static int percent(double share) {
		return (int) Math.round(share * 100);
	}

	static {
		register("beds_short", f -> new BedsShort(f.integer("at_least", 1, 1, 10000)));
		register("food_short", f -> new FoodShort(f.integer("meals_per_adult", VillageAdvice.MEALS_PER_ADULT, 1, 1000)));
		register("missing_poi", f -> {
			ResourceLocation poi = f.id("poi");
			if (!BuiltInRegistries.POINT_OF_INTEREST_TYPE.containsKey(poi)) {
				throw new StewardRules.BadRule(f.path("poi"), "unknown point of interest type " + poi);
			}
			return new MissingPoi(poi);
		});
		register("worker_without_workstation", f -> {
			Set<ResourceLocation> jobs = new LinkedHashSet<>();
			for (String s : f.strings("professions")) {
				ResourceLocation job = ResourceLocation.tryParse(s);
				if (job == null || !BuiltInRegistries.VILLAGER_PROFESSION.containsKey(job)) {
					throw new StewardRules.BadRule(f.path("professions"), "unknown profession " + s);
				}
				jobs.add(job);
			}
			return new WorkerWithoutWorkstation(Set.copyOf(jobs), f.bool("wanted", false));
		});
		register("jobless", f -> new Jobless(f.integer("at_least", 1, 1, 10000)));
		register("no_builder", f -> new NoBuilder());
		register("rank_at_least", f -> new RankAtLeast(StewardRules.rank(f, "rank")));
		register("villagers_at_least", f -> new VillagersAtLeast(f.integer("n", null, 0, 10000)));
		register("built_count_below", f -> new BuiltCountBelow(f.id("blueprint"), f.integer("n", null, 1, 10000)));
		register("upgrade_available", f -> new UpgradeAvailable(f.has("blueprint") ? Optional.of(f.id("blueprint")) : Optional.empty()));
		register("upgrade_adds_beds", f -> new UpgradeAddsBeds());
		register("homes_tier_low", f -> new HomesTierLow(f.share("share", 0.5)));
		register("store_full", f -> new StoreFull(f.share("share", 0.9)));
		register("research_idle", f -> new ResearchIdle());
		register("research_at_least", f -> {
			Research.Topic topic = StewardRules.topic(f, "topic");
			return new ResearchAtLeast(topic, f.integer("level", 1, 1, topic.maxLevel));
		});
		register("mod_loaded", f -> new ModLoaded(f.string("mod", null)));
	}

	private StewardConditions() {
	}
}
