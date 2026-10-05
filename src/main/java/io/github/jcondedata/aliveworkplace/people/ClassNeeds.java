package io.github.jcondedata.aliveworkplace.people;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.Decorations;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.Nullable;

/**
 * The toolbox of needs a social class is made of (ROADMAP 34.2, {@code docs/design/M34.md}): each need in a
 * {@code classes/} file names its {@code "type"}, and holds or doesn't for a villager. For a household (a couple) a need
 * holds when it holds for both. An unknown type is logged once when the file loads and never holds.
 *
 * <p>Two needs read what later items fill: {@code services} asks {@link #services} (the hall's daily service list,
 * 34.3; until then no service reaches anyone, so the need is unmet), and {@code luxury} reads the {@code luxuries_had}
 * record with each luxury's {@code every_days} from {@link #luxuryEvery} (34.4; until then no luxury is known, so the need
 * never holds).
 */
public final class ClassNeeds {
	/** How near home decorations count for {@code beauty} (as for moods). */
	public static final int BEAUTY_RANGE = 16;

	/** Which services reach a home: the ids (e.g. {@code aliveworkplace:school}) of those that do. */
	@FunctionalInterface
	public interface ServiceList {
		Set<ResourceLocation> reaching(ServerLevel level, BlockPos hall, @Nullable BlockPos home);
	}

	/** Which luxuries there are: a luxury's {@code every_days}, or 0 for one that isn't known. */
	@FunctionalInterface
	public interface LuxuryDays {
		int everyDays(ResourceLocation luxury);
	}

	/** The hall's service list; filled by 34.3. Until then: none reach anyone. */
	public static ServiceList services = (level, hall, home) -> Set.of();
	/** The luxuries' {@code every_days}; filled by 34.4. Until then: none known, so a luxury need never holds. */
	public static LuxuryDays luxuryEvery = luxury -> 0;

	/** One need of a class file. */
	public sealed interface Need permits Home, FedDays, DietNeed, Beauty, Building, VillageRank, Services, Luxury, Unknown {
		String type();
	}

	/** The building their bed is in is at least this grade. */
	public record Home(int grade) implements Need {
		public String type() {
			return "home";
		}
	}

	/** They ate from the store on each of the last {@code days} days. */
	public record FedDays(int days) implements Need {
		public String type() {
			return "fed_days";
		}
	}

	/** Their diet is this kind ({@code varied} or {@code plain}) or better. */
	public record DietNeed(Diet.Kind kind) implements Need {
		public String type() {
			return "diet";
		}
	}

	/** The decorations finished within {@link #BEAUTY_RANGE} of home are worth this many points. */
	public record Beauty(int points) implements Need {
		public String type() {
			return "beauty";
		}
	}

	/** A finished build of this blueprint (styled or upgraded counts), at least {@code tier}, stands in the village. */
	public record Building(ResourceLocation blueprint, int tier) implements Need {
		public String type() {
			return "building";
		}
	}

	/** The village is this rank or higher. */
	public record VillageRank(VillageRanks.Rank rank) implements Need {
		public String type() {
			return "village_rank";
		}
	}

	/** At least {@code count} of the {@code any} services, and every one of {@code all}, reach their home. */
	public record Services(int count, List<ResourceLocation> any, List<ResourceLocation> all) implements Need {
		public String type() {
			return "services";
		}
	}

	/** They had one of this luxury less than its {@code every_days} ago. */
	public record Luxury(ResourceLocation id) implements Need {
		public String type() {
			return "luxury";
		}
	}

	/** A type nobody knows: never holds. */
	public record Unknown(String type) implements Need {
	}

	/** Reads one need; throws naming the bad field. */
	public static Need read(JsonElement json) {
		JsonObject o = GsonHelper.convertToJsonObject(json, "need");
		String type = GsonHelper.getAsString(o, "type");
		return switch (type) {
			case "home" -> new Home(atLeast(o, "grade", 0));
			case "fed_days" -> new FedDays(atLeast(o, "days", 0));
			case "diet" -> new DietNeed(switch (GsonHelper.getAsString(o, "kind")) {
				case "varied" -> Diet.Kind.VARIED;
				case "plain" -> Diet.Kind.PLAIN;
				default -> throw new JsonSyntaxException("kind: expected varied or plain, found " + GsonHelper.getAsString(o, "kind"));
			});
			case "beauty" -> new Beauty(atLeast(o, "points", 0));
			case "building" -> new Building(id(GsonHelper.getAsString(o, "blueprint"), "blueprint"), o.has("tier") ? atLeast(o, "tier", 1) : 1);
			case "village_rank" -> new VillageRank(rank(GsonHelper.getAsString(o, "rank")));
			case "services" -> {
				List<ResourceLocation> any = ids(o, "any");
				List<ResourceLocation> all = ids(o, "all");
				if (any.isEmpty() && all.isEmpty()) {
					throw new JsonSyntaxException("services: expected \"any\" or \"all\"");
				}
				int count = o.has("count") ? atLeast(o, "count", 0) : any.isEmpty() ? 0 : 1;
				yield new Services(count, any, all);
			}
			case "luxury" -> new Luxury(id(GsonHelper.getAsString(o, "id"), "id"));
			default -> {
				AliveWorkplace.LOG.warn("Unknown class need type {}: it never holds", type);
				yield new Unknown(type);
			}
		};
	}

	private static int atLeast(JsonObject o, String field, int min) {
		int value = GsonHelper.getAsInt(o, field);
		if (value < min) {
			throw new JsonSyntaxException(field + ": " + value + " is below " + min);
		}
		return value;
	}

	private static VillageRanks.Rank rank(String name) {
		try {
			return VillageRanks.Rank.valueOf(name.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new JsonSyntaxException("rank: expected hamlet, village, town or city, found " + name);
		}
	}

	private static List<ResourceLocation> ids(JsonObject o, String field) {
		List<ResourceLocation> out = new ArrayList<>();
		if (o.has(field)) {
			for (JsonElement e : GsonHelper.getAsJsonArray(o, field)) {
				out.add(id(GsonHelper.convertToString(e, field), field));
			}
		}
		return List.copyOf(out);
	}

	/** An id; a bare name ({@code school}) is ours ({@code aliveworkplace:school}). */
	static ResourceLocation id(String text, String field) {
		ResourceLocation id = text.indexOf(':') >= 0 ? ResourceLocation.tryParse(text) : ResourceLocation.tryBuild(AliveWorkplace.MOD_ID, text);
		if (id == null) {
			throw new JsonSyntaxException(field + ": not an id: " + text);
		}
		return id;
	}

	/**
	 * What the needs read about the village at one dawn, worked out once and shared by every household checked in the
	 * same dawn: the day, the village's finished builds and the boxes of every builder's building.
	 */
	public static final class Village {
		final ServerLevel level;
		final BlockPos hall;
		final long today;
		@Nullable
		private List<BuildSiteManager.Finished> builds;
		@Nullable
		private List<Homes.Building> homes;
		/** Each villager's home grade and beauty, worked out once a dawn (a household's own class and the next both ask). */
		private final Map<Villager, Integer> grades = new java.util.IdentityHashMap<>();
		private final Map<Villager, Integer> beauty = new java.util.IdentityHashMap<>();

		public Village(ServerLevel level, BlockPos hall, long today) {
			this.level = level;
			this.hall = hall;
			this.today = today;
		}

		public long today() {
			return today;
		}

		List<BuildSiteManager.Finished> builds() {
			if (builds == null) {
				builds = BuildSiteManager.get(level).finishedNear(level, hall, VillageHalls.RADIUS);
			}
			return builds;
		}

		/** Reads what every household's needs read about the village (the finished builds and their boxes) now, not at the first need. */
		public void prepare() {
			builds();
			homes();
		}

		/** Every finished building's box, worked out once a dawn (each blueprint's size read once, not once a bed). */
		List<Homes.Building> homes() {
			if (homes == null) {
				homes = Homes.all(level);
			}
			return homes;
		}

		/** The building {@code bed} is in, if any. */
		public Optional<Homes.Building> buildingAt(@Nullable BlockPos bed) {
			return bed == null ? Optional.empty() : Homes.in(homes(), bed);
		}
	}

	/** Whether {@code need} holds for every one of {@code household}. */
	public static boolean holds(Need need, List<Villager> household, Village village) {
		for (Villager villager : household) {
			if (!holds(need, villager, village)) {
				return false;
			}
		}
		return true;
	}

	/** Whether every one of {@code needs} holds for every one of {@code household}. */
	public static boolean allHold(List<Need> needs, List<Villager> household, Village village) {
		for (Need need : needs) {
			if (!holds(need, household, village)) {
				return false;
			}
		}
		return true;
	}

	/** Whether {@code need} holds for {@code villager}. */
	public static boolean holds(Need need, Villager villager, Village village) {
		ServerLevel level = village.level;
		return switch (need) {
			case Home h -> village.grades.computeIfAbsent(villager, v -> village.buildingAt(VillageNeeds.bed(level, v)).map(b -> b.home().tier()).orElse(0))
				>= h.grade();
			case FedDays f -> SocialClasses.progress(villager).fed() >= f.days();
			case DietNeed d -> Diet.of(villager).ordinal() >= d.kind().ordinal();
			case Beauty b -> village.beauty.computeIfAbsent(villager, v -> beauty(level, v)) >= b.points();
			case Building b -> standing(village, b);
			case VillageRank r -> VillageRanks.of(level, village.hall).ordinal() >= r.rank().ordinal();
			case Services s -> served(s, services.reaching(level, village.hall, VillageNeeds.bed(level, villager)));
			case Luxury l -> hadLately(villager, l.id(), village.today);
			case Unknown u -> false;
		};
	}

	/** The grade of the building {@code villager}'s bed is in: its tier (a base blueprint I, {@code _2} II...), 0 for none. */
	public static int homeGrade(ServerLevel level, Villager villager) {
		return Homes.of(level, villager).map(Homes.Home::tier).orElse(0);
	}

	/** The beauty near {@code villager}'s home: the points of the decorations finished within {@link #BEAUTY_RANGE} of their bed. */
	public static int beauty(ServerLevel level, Villager villager) {
		BlockPos bed = VillageNeeds.bed(level, villager);
		if (bed == null) {
			return 0;
		}
		int points = 0;
		for (BuildSiteManager.Finished f : BuildSiteManager.get(level).finishedNear(level, bed, BEAUTY_RANGE)) {
			points += Decorations.points(f.structure());
		}
		return points;
	}

	private static boolean standing(Village village, Building need) {
		ResourceLocation wanted = root(need.blueprint());
		for (BuildSiteManager.Finished f : village.builds()) {
			ResourceLocation built = BlueprintStyles.base(f.structure());
			if (root(built).equals(wanted) && BlueprintUpgrades.tier(built) >= need.tier()) {
				return true;
			}
		}
		return false;
	}

	/** A blueprint without its style and its upgrades ({@code house_3} is {@code house}). */
	private static ResourceLocation root(ResourceLocation id) {
		ResourceLocation at = BlueprintStyles.base(id);
		for (int i = 0; i < 100; i++) {
			ResourceLocation base = BlueprintUpgrades.baseOf(at).orElse(null);
			if (base == null) {
				break;
			}
			at = base;
		}
		return at;
	}

	static boolean served(Services need, Set<ResourceLocation> reaching) {
		if (!reaching.containsAll(need.all())) {
			return false;
		}
		int found = 0;
		for (ResourceLocation id : need.any()) {
			if (reaching.contains(id)) {
				found++;
			}
		}
		return found >= need.count();
	}

	private static boolean hadLately(Villager villager, ResourceLocation luxury, long today) {
		int every = luxuryEvery.everyDays(luxury);
		if (every <= 0) {
			return false;
		}
		Map<ResourceLocation, Long> had = ModAttachments.LUXURIES_HAD.getOrElse(villager, Map.of());
		Long day = had.get(luxury);
		return day != null && today - day < every;
	}

	private ClassNeeds() {
	}
}
