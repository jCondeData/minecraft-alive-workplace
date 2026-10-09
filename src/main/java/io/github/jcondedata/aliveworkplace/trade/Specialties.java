package io.github.jcondedata.aliveworkplace.trade;

import io.github.jcondedata.aliveworkplace.work.Requests;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;

/**
 * What a village is known for and short of (ROADMAP 33.2), worked out once a day from what its hall's round already
 * counted: its workers' jobs, the biome at the hall, its Storehouses' stock and what its workers wait for. Points, as the
 * roadmap gives them (design note M33, "Prices, exactly as the roadmap writes them"):
 * <pre>
 * known for: 2 a worker of a making job (max 6) + 3 hall in a making biome + 1 Storehouses hold two bundles or more
 * short of:  3 biome wants it + 1 a worker of a job that uses it (max 3) + 2 a worker waits for it + 1 under a bundle
 * </pre>
 * 4 points count; the top 3 of each by points, then id. A good is never both: the higher score wins, a tie is neither.
 */
public final class Specialties {
	/** Points a good needs to be known for, or short of. */
	public static final int POINTS = 4;
	/** Most goods a village is known for, and most it's short of. */
	public static final int MOST = 3;

	/** What the daily count reads about a village: one profession id per worker, the hall's biome, its Storehouses' stock, the waits. */
	public record Village(List<ResourceLocation> jobs, @Nullable Holder<Biome> biome, Map<Item, Long> stock, List<Requests.Request> requests) {
	}

	/** A good's two scores in one village. */
	public record Score(int known, int shortOf) {
	}

	/** The village's known-for and short-of lists. */
	public record Lists(List<ResourceLocation> knownFor, List<ResourceLocation> shortOf) {
	}

	/** How many of {@code good}'s items the stock holds. */
	public static long stock(TradeGoods.Good good, Map<Item, Long> stock) {
		long n = 0;
		for (Map.Entry<Item, Long> e : stock.entrySet()) {
			if (good.matches(e.getKey())) {
				n += e.getValue();
			}
		}
		return n;
	}

	/** Whole bundles of {@code good} in the stock. */
	public static long bundles(TradeGoods.Good good, Map<Item, Long> stock) {
		return stock(good, stock) / good.bundle();
	}

	/** How many of {@code good}'s items the village's workers are waiting for (on the Storehouse's requests board). */
	public static int waiting(TradeGoods.Good good, List<Requests.Request> requests) {
		int n = 0;
		for (Requests.Request r : requests) {
			if (good.matches(r.icon())) {
				n += Math.max(1, r.count());
			}
		}
		return n;
	}

	/** Bundles of {@code good} its workers are waiting for, a part bundle counting as one. */
	public static int bundlesWaiting(TradeGoods.Good good, List<Requests.Request> requests) {
		int items = waiting(good, requests);
		return (items + good.bundle() - 1) / good.bundle();
	}

	public static Score score(TradeGoods.Good good, Village village) {
		long makers = village.jobs().stream().filter(good.makers()::contains).count();
		long users = village.jobs().stream().filter(good.users()::contains).count();
		long bundles = bundles(good, village.stock());
		int known = (int) Math.min(6, 2 * makers)
			+ (village.biome() != null && good.madeIn(village.biome()) ? 3 : 0)
			+ (bundles >= 2 ? 1 : 0);
		int shortOf = (village.biome() != null && good.wantedIn(village.biome()) ? 3 : 0)
			+ (int) Math.min(3, users)
			+ (waiting(good, village.requests()) > 0 ? 2 : 0)
			+ (bundles < 1 ? 1 : 0);
		return new Score(known, shortOf);
	}

	/** The village's lists from every good's score. */
	public static Lists lists(List<TradeGoods.Good> goods, Village village) {
		Map<ResourceLocation, Score> scores = new LinkedHashMap<>();
		for (TradeGoods.Good g : goods) {
			scores.put(g.id(), score(g, village));
		}
		return lists(scores);
	}

	/** The lists from scores: 4 points count, a good counted for both goes to the higher score (a tie: neither), top 3 each. */
	public static Lists lists(Map<ResourceLocation, Score> scores) {
		List<Map.Entry<ResourceLocation, Integer>> known = new ArrayList<>();
		List<Map.Entry<ResourceLocation, Integer>> shortOf = new ArrayList<>();
		scores.forEach((id, s) -> {
			boolean k = s.known() >= POINTS;
			boolean sh = s.shortOf() >= POINTS;
			if (k && sh) {
				k = s.known() > s.shortOf();
				sh = s.shortOf() > s.known();
			}
			if (k) {
				known.add(Map.entry(id, s.known()));
			}
			if (sh) {
				shortOf.add(Map.entry(id, s.shortOf()));
			}
		});
		return new Lists(top(known), top(shortOf));
	}

	private static List<ResourceLocation> top(List<Map.Entry<ResourceLocation, Integer>> list) {
		return list.stream()
			.sorted(Comparator.<Map.Entry<ResourceLocation, Integer>>comparingInt(Map.Entry::getValue).reversed()
				.thenComparing(e -> e.getKey().toString()))
			.limit(MOST).map(Map.Entry::getKey).toList();
	}

	private Specialties() {
	}
}
