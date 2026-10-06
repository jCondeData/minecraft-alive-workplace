package io.github.jcondedata.aliveworkplace.people;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.people.SocialClasses.SocialClass;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * What each class gives (ROADMAP 34.7, {@code docs/design/M34.md}), read from the {@code effects} in the class files:
 * <ul>
 * <li>taxes: each worker pays the treasury's share times their class's {@code tax}, 10% more for each want they had at
 * their last dawn; a jobless villager of the top tier (a Noble, a Legend) pays like a worker. A Peasant pays exactly the
 * old share;</li>
 * <li>{@code work_pace} and {@code research_pace}: the class's members, and every class above, of the listed jobs work
 * that much faster, through {@link io.github.jcondedata.aliveworkplace.work.Pace}'s shared cap;</li>
 * <li>{@code caravan_routes} and {@code market_traders}: once the village has {@code households} households of the class or
 * higher, {@code extra} more routes or traders (the trader sells one of the {@code sells} blueprints for {@code price});</li>
 * <li>{@code wellbeing}: {@code percent} village wellbeing for each household of the class or higher, {@code max} at most;</li>
 * <li>{@code festival}: with one household of the class or higher, one festival in every {@code replace_every} is a
 * Noble's Ball ({@link io.github.jcondedata.aliveworkplace.hall.NobleBalls}).</li>
 * </ul>
 * A Legend (M29) lives among the top class: held to its needs and counted with it. With {@code villageClasses} off none
 * of this does anything. The village's sums (households by class) are worked out whenever the hall counts its people
 * ({@code VillageNeeds.count}) and kept in memory, not saved: they come back at the hall's next count after a load.
 */
public final class ClassPerks {
	public static final ResourceLocation WORK_PACE = AliveWorkplace.id("work_pace");
	public static final ResourceLocation RESEARCH_PACE = AliveWorkplace.id("research_pace");
	public static final ResourceLocation CARAVAN_ROUTES = AliveWorkplace.id("caravan_routes");
	public static final ResourceLocation MARKET_TRADERS = AliveWorkplace.id("market_traders");
	public static final ResourceLocation WELLBEING = AliveWorkplace.id("wellbeing");
	public static final ResourceLocation FESTIVAL = AliveWorkplace.id("festival");
	/** Each want had adds this share to a worker's tax. */
	public static final float WANT_TAX = 0.1f;

	/** A village's households by class (the higher class of a couple; a Legend's as the top class), and when counted. */
	public record Sums(Map<ResourceLocation, Integer> households) {
		public static final Sums NONE = new Sums(Map.of());

		/** Households of class {@code c} or higher. */
		public int atLeast(SocialClass c) {
			int n = 0;
			for (Map.Entry<ResourceLocation, Integer> e : households.entrySet()) {
				SocialClass other = SocialClasses.get(e.getKey());
				if (other != null && other.tier() >= c.tier()) {
					n += e.getValue();
				}
			}
			return n;
		}
	}

	/** What a day's taxes come to: worker shares in all (a Peasant worker is 1), and by class (lowest first). */
	public record Tax(float shares, Map<ResourceLocation, Float> byClass, Map<ResourceLocation, Integer> payers) {
	}

	private record Key(ResourceKey<Level> dimension, BlockPos hall) {
	}

	private static final Map<Key, Sums> SUMS = new ConcurrentHashMap<>();

	/** True when {@code villager} is a Legend (M29; none while Legends are off or before one settles). */
	public static boolean isLegend(Villager villager) {
		return ModAttachments.LEGEND.has(villager) && io.github.jcondedata.aliveworkplace.legend.Legends.of(villager).isPresent();
	}

	/** {@code villager}'s class as the perks read it: null with classes off or none yet; a Legend's is the top class. */
	@Nullable
	public static SocialClass classOf(Villager villager) {
		if (!SocialClasses.ENABLED) {
			return null;
		}
		List<SocialClass> ladder = SocialClasses.ladder();
		if (!ladder.isEmpty() && isLegend(villager)) {
			return ladder.get(ladder.size() - 1);
		}
		return SocialClasses.of(villager);
	}

	/** The effects of type {@code type} in {@code c}'s file. */
	public static List<JsonObject> effects(SocialClass c, ResourceLocation type) {
		List<JsonObject> out = new ArrayList<>();
		for (JsonElement e : c.effects()) {
			if (e.isJsonObject() && type.toString().equals(ResourceLocation.parse(GsonHelper.getAsString(e.getAsJsonObject(), "type", "")).toString())) {
				out.add(e.getAsJsonObject());
			}
		}
		return out;
	}

	private static boolean forJob(JsonObject effect, ResourceLocation job) {
		if (!effect.has("jobs")) {
			return true;
		}
		for (JsonElement j : GsonHelper.getAsJsonArray(effect, "jobs")) {
			if (job.toString().equals(ResourceLocation.parse(j.getAsString()).toString())) {
				return true;
			}
		}
		return false;
	}

	// --- pace ---

	/**
	 * The class bonus on {@code villager}'s work time (1: none): each {@code work_pace} and {@code research_pace} of their
	 * class or a class below, for their job, as {@code 1 / (1 + percent / 100)}. The pace's cap applies on top.
	 */
	public static float pace(Villager villager) {
		SocialClass c = classOf(villager);
		if (c == null) {
			return 1f;
		}
		ResourceLocation job = BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession());
		int percent = 0;
		for (SocialClass k : SocialClasses.ladder()) {
			if (k.tier() > c.tier()) {
				continue;
			}
			for (ResourceLocation type : List.of(WORK_PACE, RESEARCH_PACE)) {
				for (JsonObject e : effects(k, type)) {
					if (forJob(e, job)) {
						percent += GsonHelper.getAsInt(e, "percent", 0);
					}
				}
			}
		}
		return percent <= 0 ? 1f : 1f / (1f + percent / 100f);
	}

	/** The pace line's label: "their class (Artisan)". */
	public static Component paceLabel(Villager villager) {
		SocialClass c = classOf(villager);
		return Component.translatable("pace.aliveworkplace.source.class", c == null ? Component.empty() : c.name());
	}

	// --- taxes ---

	/**
	 * A day's tax shares from {@code workers} and the {@code jobless}: each worker {@code tax × (1 + 0.1 × wants)} (1 with
	 * classes off or no class yet); a jobless grown villager of the top tier pays like a worker.
	 */
	public static Tax tax(List<Villager> workers, List<Villager> jobless) {
		float shares = 0;
		Map<ResourceLocation, Float> byClass = new LinkedHashMap<>();
		Map<ResourceLocation, Integer> payers = new LinkedHashMap<>();
		SocialClasses.ladder().forEach(c -> {
			byClass.put(c.id(), 0f);
			payers.put(c.id(), 0);
		});
		List<SocialClass> ladder = SocialClasses.ladder();
		int top = ladder.isEmpty() ? Integer.MAX_VALUE : ladder.get(ladder.size() - 1).tier();
		List<Villager> payersList = new ArrayList<>(workers);
		for (Villager v : jobless) {
			SocialClass c = classOf(v);
			if (c != null && c.tier() >= top && ladder.size() > 1 && !v.isBaby()) {
				payersList.add(v);
			}
		}
		for (Villager v : payersList) {
			SocialClass c = classOf(v);
			float share = share(v, c);
			shares += share;
			if (c != null) {
				byClass.merge(c.id(), share, Float::sum);
				payers.merge(c.id(), 1, Integer::sum);
			}
		}
		return new Tax(shares, byClass, payers);
	}

	/** One payer's share: their class's {@code tax}, 10% more a want had at their last dawn; 1 without a class. */
	public static float share(Villager villager, @Nullable SocialClass c) {
		if (c == null) {
			return 1f;
		}
		SocialClasses.Standing s = ModAttachments.CLASS_STANDING.get(villager);
		int wants = s == null ? 0 : Math.min(s.wants(), c.wants().size());
		return c.tax() * (1f + WANT_TAX * wants);
	}

	// --- the village's sums ---

	/** Counts the households of {@code villagers} by class and keeps it for the hall at {@code hall}. */
	public static Sums tally(ServerLevel level, BlockPos hall, List<Villager> villagers) {
		if (!SocialClasses.ENABLED || SocialClasses.ladder().isEmpty()) {
			SUMS.remove(new Key(level.dimension(), hall.immutable()));
			return Sums.NONE;
		}
		Map<ResourceLocation, Integer> by = new LinkedHashMap<>();
		for (Households.Household h : Households.of(villagers)) {
			SocialClass best = null;
			for (Villager v : h.members()) {
				SocialClass c = classOf(v);
				if (c != null && (best == null || c.tier() > best.tier())) {
					best = c;
				}
			}
			if (best != null) {
				by.merge(best.id(), 1, Integer::sum);
			}
		}
		Sums sums = new Sums(Map.copyOf(by));
		SUMS.put(new Key(level.dimension(), hall.immutable()), sums);
		return sums;
	}

	/** The hall's sums from its last count (none before it, or with classes off). */
	public static Sums sums(ServerLevel level, BlockPos hall) {
		return SocialClasses.ENABLED ? SUMS.getOrDefault(new Key(level.dimension(), hall), Sums.NONE) : Sums.NONE;
	}

	/** The {@code extra} of every effect of {@code type} whose {@code households} count the village has reached. */
	private static int extra(Sums sums, ResourceLocation type) {
		int n = 0;
		for (SocialClass c : SocialClasses.ladder()) {
			for (JsonObject e : effects(c, type)) {
				if (sums.atLeast(c) >= Math.max(1, GsonHelper.getAsInt(e, "households", 1))) {
					n += GsonHelper.getAsInt(e, "extra", 1);
				}
			}
		}
		return n;
	}

	/** More caravan routes the village may send ({@code caravan_routes}: Burghers, one with 3 households). */
	public static int caravanRoutes(ServerLevel level, BlockPos hall) {
		return extra(sums(level, hall), CARAVAN_ROUTES);
	}

	/** More traders on market day ({@code market_traders}: Burghers, one with 3 households). */
	public static int marketTraders(ServerLevel level, BlockPos hall) {
		return extra(sums(level, hall), MARKET_TRADERS);
	}

	/** A blueprint a class's market trader sells, and its price in emeralds. */
	public record Offer(ResourceLocation blueprint, int price) {
	}

	/** The blueprints the class traders in force sell ({@code market_traders} {@code sells}, at {@code price}). */
	public static List<Offer> marketOffers(ServerLevel level, BlockPos hall) {
		Sums sums = sums(level, hall);
		List<Offer> out = new ArrayList<>();
		for (SocialClass c : SocialClasses.ladder()) {
			for (JsonObject e : effects(c, MARKET_TRADERS)) {
				if (sums.atLeast(c) >= Math.max(1, GsonHelper.getAsInt(e, "households", 1)) && e.has("sells")) {
					int price = GsonHelper.getAsInt(e, "price", 12);
					for (JsonElement b : GsonHelper.getAsJsonArray(e, "sells")) {
						out.add(new Offer(ResourceLocation.parse(b.getAsString()), price));
					}
				}
			}
		}
		return out;
	}

	/** The wellbeing the village's classes add ({@code wellbeing}: Nobles, 3% a household, 9% at most), 0 to 1. */
	public static float wellbeing(Sums sums) {
		float out = 0;
		for (SocialClass c : SocialClasses.ladder()) {
			for (JsonObject e : effects(c, WELLBEING)) {
				int percent = GsonHelper.getAsInt(e, "percent", 0) * sums.atLeast(c);
				out += Math.min(GsonHelper.getAsInt(e, "max", Integer.MAX_VALUE), percent) / 100f;
			}
		}
		return Math.max(0f, out);
	}

	/** One festival in every this many is a Noble's Ball ({@code festival}; 0: none, with no household of its class). */
	public static int ballEvery(ServerLevel level, BlockPos hall) {
		Sums sums = sums(level, hall);
		for (SocialClass c : SocialClasses.ladder()) {
			for (JsonObject e : effects(c, FESTIVAL)) {
				if (sums.atLeast(c) >= Math.max(1, GsonHelper.getAsInt(e, "households", 1))) {
					return Math.max(1, GsonHelper.getAsInt(e, "replace_every", 2));
				}
			}
		}
		return 0;
	}

	/** Forgets every hall's sums (tests). */
	public static void forget() {
		SUMS.clear();
	}

	private ClassPerks() {
	}
}
