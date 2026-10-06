package io.github.jcondedata.aliveworkplace.build;

import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * What the build sites near a shared store (the village's kitchens and Storehouses, a village-mate's chests) still need
 * from it, so the village's other uses of the store leave it there (B84: the village ate a Steward's Farmstead's
 * carrots and potatoes from the storehouse each day and farmers took its wheat seeds to sow, so the build waited for
 * materials for days and held one of the Steward's open-build slots).
 * <p>
 * A site keeps back, by material family, what the rest of its build takes beyond what its bench's own chests and its
 * builder's bag already hold; nothing is saved: it is worked out from the saved {@link BuildSite}s each time, so a site
 * that is finished, cancelled or taken down frees its share at once, and old worlds need nothing new.
 */
public final class BuildReserve {
	/** Nothing kept back. */
	public static final BuildReserve NONE = new BuildReserve(List.of(), Map.of());

	private final List<BlockPos> chests;
	private final Map<Item, Integer> reserved;

	private BuildReserve(List<BlockPos> chests, Map<Item, Integer> reserved) {
		this.chests = chests;
		this.reserved = reserved;
	}

	/** What the build sites within the village's reach of {@code chests} keep back in them. */
	public static BuildReserve of(ServerLevel level, List<BlockPos> chests) {
		return of(level, chests, BuildSiteManager.get(level).all());
	}

	/**
	 * {@link #of(ServerLevel, List)}, worked out at most once a second for the same chests (for a worker who asks every
	 * tick on the way to them); what it keeps back may be up to a second old.
	 */
	public static BuildReserve cached(ServerLevel level, List<BlockPos> chests) {
		Key key = new Key(level.dimension(), List.copyOf(chests));
		long now = level.getGameTime();
		synchronized (CACHE) {
			Cached hit = CACHE.get(key);
			if (hit != null && hit.until() > now && hit.from() <= now) {
				return hit.reserve();
			}
			if (CACHE.size() > 256) {
				CACHE.clear();
			}
		}
		BuildReserve reserve = of(level, chests);
		synchronized (CACHE) {
			CACHE.put(key, new Cached(now, now + 20, reserve));
		}
		return reserve;
	}

	private record Key(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, List<BlockPos> chests) {
	}

	private record Cached(long from, long until, BuildReserve reserve) {
	}

	private static final Map<Key, Cached> CACHE = new HashMap<>();

	/** What {@code sites} (those within the village's reach of {@code chests}) keep back in them. */
	public static BuildReserve of(ServerLevel level, List<BlockPos> chests, Collection<BuildSite> sites) {
		if (chests.isEmpty() || sites.isEmpty()) {
			return new BuildReserve(chests, Map.of());
		}
		double reach = (double) Village.RADIUS * Village.RADIUS;
		Set<BlockPos> store = new HashSet<>(chests);
		Map<BlockPos, Map<Item, Integer>> byBench = new HashMap<>();
		Map<BlockPos, Set<UUID>> buildersAt = new HashMap<>();
		for (BuildSite site : sites) {
			BlockPos bench = site.bench();
			if (bench == null || site.isDone() || site.isDeconstruction()
				|| !site.placement().dimension().equals(level.dimension().location())) {
				continue;
			}
			// Its builder takes from this store if the village shares it (23.5), or if the store's chests are its own.
			if (chests.stream().noneMatch(c -> c.distSqr(bench) <= reach)
				&& SupplyContainers.find(level, bench, null).stream().noneMatch(store::contains)) {
				continue;
			}
			BuildPlan plan = site.plan(level);
			if (plan == null) {
				continue;
			}
			Map<Item, Integer> need = byBench.computeIfAbsent(bench, b -> new HashMap<>());
			Builders.remainingNeed(level, site, plan).forEach((item, n) -> need.merge(item, n, Integer::sum));
			if (site.builder() != null) {
				buildersAt.computeIfAbsent(bench, b -> new HashSet<>()).add(site.builder());
			}
		}
		Map<Item, Integer> out = new HashMap<>();
		byBench.forEach((bench, need) -> {
			// What the bench's own chests (not this store's) and its builders' bags hold goes into those builds already.
			Map<Item, Long> have = new HashMap<>();
			List<BlockPos> own = SupplyContainers.find(level, bench, null).stream().filter(c -> !store.contains(c)).toList();
			SupplyContainers.contents(level, own).forEach((item, n) -> have.merge(MaterialFamilies.key(item), n, Long::sum));
			for (UUID id : buildersAt.getOrDefault(bench, Set.of())) {
				BuilderBag bag = level.getEntity(id) instanceof Villager v ? ModAttachments.BUILDER_BAG.get(v) : null;
				if (bag != null) {
					for (ItemStack stack : bag.stacks()) {
						if (!stack.isEmpty()) {
							have.merge(MaterialFamilies.key(stack.getItem()), (long) stack.getCount(), Long::sum);
						}
					}
				}
			}
			need.forEach((item, n) -> {
				long short_ = n - have.getOrDefault(item, 0L);
				if (short_ > 0) {
					out.merge(item, (int) Math.min(Integer.MAX_VALUE, short_), Integer::sum);
				}
			});
		});
		return new BuildReserve(chests, Map.copyOf(out));
	}

	/** The store's chests. */
	public List<BlockPos> chests() {
		return chests;
	}

	/** How many of {@code item}'s family are kept back, by family key. */
	public int reserved(Item item) {
		return reserved.getOrDefault(MaterialFamilies.key(item), 0);
	}

	/** Everything kept back, by family key. */
	public Map<Item, Integer> reserved() {
		return reserved;
	}

	/** How many of {@code item} the store holds beyond what is kept back (counted now). */
	public long spare(ServerLevel level, Item item) {
		int kept = reserved(item);
		if (kept <= 0) {
			return SupplyContainers.count(level, chests, item);
		}
		return Math.min(SupplyContainers.count(level, chests, item), Builders.spare(level, chests, item, reserved));
	}

	/** Takes one item matching {@code test} from the store, but never one that is kept back; empty if there is none. */
	public ItemStack takeOne(ServerLevel level, Predicate<ItemStack> test) {
		if (reserved.isEmpty()) {
			return SupplyContainers.takeOne(level, chests, test);
		}
		return SupplyContainers.takeOne(level, chests, unreserved(level, test));
	}

	/**
	 * {@code test}, but only for items the store has to spare (worked out now, before the store is read again, so nothing
	 * reads it while it is being taken from).
	 */
	public Predicate<ItemStack> unreserved(ServerLevel level, Predicate<ItemStack> test) {
		if (reserved.isEmpty()) {
			return test;
		}
		Set<Item> kept = new HashSet<>();
		for (Item item : SupplyContainers.contents(level, chests).keySet()) {
			if (reserved(item) > 0 && spare(level, item) <= 0) {
				kept.add(item);
			}
		}
		return s -> test.test(s) && !kept.contains(s.getItem());
	}

	/** Takes up to {@code max} of {@code item}, never what is kept back. Returns the amount taken. */
	public int extract(ServerLevel level, Item item, int max) {
		int n = (int) Math.min(max, spare(level, item));
		return n <= 0 ? 0 : SupplyContainers.extract(level, chests, item, n);
	}
}
