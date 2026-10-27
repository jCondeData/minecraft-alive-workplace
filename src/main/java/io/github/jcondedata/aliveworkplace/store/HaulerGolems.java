package io.github.jcondedata.aliveworkplace.store;

import com.mojang.serialization.Codec;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.legend.GolemRoleGoal;
import io.github.jcondedata.aliveworkplace.legend.GolemSmith;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Hauler Golems (ROADMAP 29.15): a Golem Smith's golem with the role {@code hauler} carries what the village's workers
 * make to the chests by the nearest Storehouse, {@link #STACKS} stacks a trip, by {@link PorterWork}'s rules (what a
 * worker's job makes, never what it needs: {@link PorterWork#goods}, at least {@link PorterWork#MIN_LOAD} items to be
 * worth a trip). The load is saved on the golem ({@link ModAttachments#HAULER_LOAD}), so a trip survives a reload:
 * a golem with a load heads to the Storehouse first. They never break blocks. The walking is the golem's own goal,
 * {@link GolemRoleGoal}. A Storehouse the golem can't get to (walled in, across water) is left alone for a while
 * after {@link #GIVE_UP} ticks without headway, and the next nearest one takes the load (B450); a Drop Box or worker's
 * chest it can't get to is skipped the same way, and the next one with goods is fetched from (B456). With no
 * Storehouse left to try, the load goes back into the nearest Drop Box and the golem is free (B463); with no Drop Box,
 * it goes back into the worker's chest it came from (or, after a reload, the nearest worker's chest with room) (B593).
 * Nothing of that is saved, so after a reload the golem simply tries the nearest again.
 */
public final class HaulerGolems {
	public static final int STACKS = PorterWork.BASE_STACKS;
	public static final int STOREHOUSE_RANGE = 64;
	private static final double REACH = 3.0;
	/** Ticks a golem may stand without headway on its way to a Storehouse or pickup before it gives that one up. */
	public static final int GIVE_UP = 200;
	/** Ticks a given-up Storehouse or pickup is left alone before the golem tries it again (the way may have been opened). */
	public static final int SKIP_FOR = 2400;
	/** Storehouses and pickup chests each golem has given up on, with the game time it tries them again (in memory only). */
	private static final Map<IronGolem, Map<BlockPos, Long>> UNREACHABLE = Collections.synchronizedMap(new WeakHashMap<>());
	/** The worker's chests each golem's load was last fetched from (in memory only; B593). */
	private static final Map<IronGolem, List<BlockPos>> FETCHED_FROM = Collections.synchronizedMap(new WeakHashMap<>());

	public static final Codec<Map<Item, Integer>> LOAD_CODEC = Codec.unboundedMap(BuiltInRegistries.ITEM.byNameCodec(), Codec.INT);

	/** One step of a hauler's round: store the load, or fetch goods from the nearest worker who has enough. */
	public static void work(ServerLevel level, IronGolem golem) {
		BlockPos storehouse = storehouse(level, golem).orElse(null);
		if (storehouse == null) {
			Map<Item, Integer> carried = load(golem);
			if (!carried.isEmpty()) {
				putBack(level, golem, carried);
			}
			return;
		}
		List<BlockPos> store = SupplyContainers.find(level, storehouse, null);
		if (store.isEmpty()) {
			return;
		}
		Map<Item, Integer> load = load(golem);
		if (!load.isEmpty()) {
			if (GolemRoleGoal.walkTo(golem, store.get(0), REACH)) {
				unload(level, golem, store, load);
			} else {
				giveUpIfStill(level, golem, storehouse);
			}
			return;
		}
		if (SupplyContainers.freeSlots(level, store) == 0) {
			return;
		}
		Village.Stash stash = choose(level, golem, storehouse);
		if (stash == null) {
			return;
		}
		BlockPos pickup = stash.chests().get(0);
		if (GolemRoleGoal.walkTo(golem, pickup, REACH)) {
			if (collect(level, golem, stash, Math.min(STACKS, SupplyContainers.freeSlots(level, store))) > 0) {
				FETCHED_FROM.put(golem, List.copyOf(stash.chests()));
			}
		} else {
			giveUpIfStill(level, golem, pickup);
		}
	}

	/**
	 * With a load and no Storehouse it can get to (B463), the golem puts the load back into the nearest Drop Box with
	 * room, so the goods aren't stuck on it and it is free to haul again; a Drop Box it can't get to is skipped the same
	 * way as a Storehouse. With no Drop Box it can use (B593), the load goes back into the worker's chest it came from,
	 * or, when that isn't known (after a reload) or has no room, the nearest village worker's chest with room.
	 */
	private static void putBack(ServerLevel level, IronGolem golem, Map<Item, Integer> load) {
		Map<BlockPos, Long> skip = skipped(level, golem);
		BlockPos box = level.getPoiManager().findClosest(h -> h.is(ModVillagers.DROP_BOX_POI),
			p -> !skip.containsKey(p) && SupplyContainers.freeSlots(level, List.of(p)) > 0, golem.blockPosition(), STOREHOUSE_RANGE,
			PoiManager.Occupancy.ANY).orElse(null);
		if (box == null) {
			box = workerChest(level, golem, skip);
		}
		if (box == null) {
			return;
		}
		if (GolemRoleGoal.walkTo(golem, box, REACH)) {
			unload(level, golem, List.of(box), load);
		} else {
			giveUpIfStill(level, golem, box);
		}
	}

	/**
	 * Where a load goes back to with no Drop Box (B593): the chest it was fetched from, else the nearest village worker's
	 * chest with room (never a Storehouse's), skipping ones the golem has lately given up on; null when none.
	 */
	@Nullable
	private static BlockPos workerChest(ServerLevel level, IronGolem golem, Map<BlockPos, Long> skip) {
		for (BlockPos from : FETCHED_FROM.getOrDefault(golem, List.of())) {
			if (!skip.containsKey(from) && SupplyContainers.freeSlots(level, List.of(from)) > 0) {
				return from;
			}
		}
		BlockPos center = storehouse(level, golem.blockPosition()).orElse(golem.blockPosition());
		java.util.Set<BlockPos> stores = new java.util.HashSet<>();
		level.getPoiManager().findAll(h -> h.is(ModVillagers.STOREHOUSE_POI), p -> true, golem.blockPosition(), STOREHOUSE_RANGE,
			PoiManager.Occupancy.ANY).forEach(sh -> stores.addAll(SupplyContainers.find(level, sh, null)));
		Villager context = level.getEntitiesOfClass(Villager.class, new AABB(center).inflate(Village.RADIUS), v -> v.isAlive() && Village.takesPart(v))
			.stream().findFirst().orElse(null);
		if (context == null) {
			return null;
		}
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (Village.Stash stash : Village.stashes(level, context, center, null)) {
			for (BlockPos c : stash.chests()) {
				if (stores.contains(c) || skip.containsKey(c) || SupplyContainers.freeSlots(level, List.of(c)) == 0) {
					continue;
				}
				double d = c.distSqr(golem.blockPosition());
				if (d < bestDist) {
					bestDist = d;
					best = c;
				}
			}
		}
		return best;
	}

	/** After {@link #GIVE_UP} ticks without headway, leaves {@code target} alone for {@link #SKIP_FOR} ticks. */
	private static void giveUpIfStill(ServerLevel level, IronGolem golem, BlockPos target) {
		if (GolemRoleGoal.stillFor(golem) >= GIVE_UP) {
			UNREACHABLE.computeIfAbsent(golem, g -> new HashMap<>()).put(target, level.getGameTime() + SKIP_FOR);
			GolemRoleGoal.giveUp(golem);
		}
	}

	/** What {@code golem} has lately given up on (expired entries dropped); empty when nothing. */
	private static Map<BlockPos, Long> skipped(ServerLevel level, IronGolem golem) {
		Map<BlockPos, Long> skip = UNREACHABLE.get(golem);
		if (skip == null) {
			return Map.of();
		}
		long now = level.getGameTime();
		skip.values().removeIf(until -> until <= now);
		return skip;
	}

	/** The nearest Storehouse within {@link #STOREHOUSE_RANGE}. */
	public static Optional<BlockPos> storehouse(ServerLevel level, BlockPos near) {
		return level.getPoiManager().findClosest(h -> h.is(ModVillagers.STOREHOUSE_POI), near, STOREHOUSE_RANGE, PoiManager.Occupancy.ANY);
	}

	/** The nearest Storehouse within {@link #STOREHOUSE_RANGE} of {@code golem} that it hasn't lately given up on. */
	public static Optional<BlockPos> storehouse(ServerLevel level, IronGolem golem) {
		Map<BlockPos, Long> skip = skipped(level, golem);
		if (skip.isEmpty()) {
			return storehouse(level, golem.blockPosition());
		}
		return level.getPoiManager().findClosest(h -> h.is(ModVillagers.STOREHOUSE_POI), p -> !skip.containsKey(p), golem.blockPosition(),
			STOREHOUSE_RANGE, PoiManager.Occupancy.ANY);
	}

	/** The saved load (empty when none). */
	public static Map<Item, Integer> load(IronGolem golem) {
		Map<Item, Integer> load = ModAttachments.HAULER_LOAD.get(golem);
		return load == null ? Map.of() : load;
	}

	/**
	 * The nearest stash with a trip's worth of goods, from a village worker's side (a Golem Smith's, or anyone's), but
	 * none whose chest the golem has lately given up on.
	 */
	@Nullable
	static Village.Stash choose(ServerLevel level, IronGolem golem, BlockPos storehouse) {
		Map<BlockPos, Long> skip = skipped(level, golem);
		Village.Stash box = PorterWork.dropBox(level, storehouse, p -> !skip.containsKey(p));
		if (box != null && total(PorterWork.goods(level, box)) >= PorterWork.MIN_LOAD) {
			return box;
		}
		Villager context = level.getEntitiesOfClass(Villager.class, new AABB(storehouse).inflate(Village.RADIUS), v -> v.isAlive() && Village.takesPart(v))
			.stream().filter(GolemSmith::isSmith).findFirst()
			.orElseGet(() -> level.getEntitiesOfClass(Villager.class, new AABB(storehouse).inflate(Village.RADIUS), v -> v.isAlive() && Village.takesPart(v))
				.stream().findFirst().orElse(null));
		if (context == null) {
			return null;
		}
		for (Village.Stash stash : Village.stashes(level, context, storehouse, null)) {
			if (!skip.containsKey(stash.chests().get(0)) && total(PorterWork.goods(level, stash)) >= PorterWork.MIN_LOAD) {
				return stash;
			}
		}
		return null;
	}

	private static long total(Map<Item, Long> goods) {
		return goods.values().stream().mapToLong(Long::longValue).sum();
	}

	/** Takes up to {@code stacks} stacks of goods from {@code stash} into the golem's load; returns how many items. */
	public static int collect(ServerLevel level, IronGolem golem, Village.Stash stash, int stacks) {
		Map<Item, Integer> load = new LinkedHashMap<>(load(golem));
		int taken = 0;
		int left = stacks;
		for (Map.Entry<Item, Long> e : PorterWork.goods(level, stash).entrySet()) {
			if (left <= 0) {
				break;
			}
			Item item = e.getKey();
			int perStack = Math.max(1, item.getDefaultMaxStackSize());
			int want = (int) Math.min(e.getValue(), (long) left * perStack);
			int got = SupplyContainers.extract(level, stash.chests(), item, want);
			if (got > 0) {
				load.merge(item, got, Integer::sum);
				taken += got;
				left -= (got + perStack - 1) / perStack;
			}
		}
		ModAttachments.HAULER_LOAD.set(golem, load.isEmpty() ? null : Map.copyOf(load));
		return taken;
	}

	/** Puts the load into the Storehouse's chests; what doesn't fit stays in the load. Returns how many items went in. */
	public static int unload(ServerLevel level, IronGolem golem, List<BlockPos> store, Map<Item, Integer> load) {
		Map<Item, Integer> rest = new LinkedHashMap<>();
		int stored = 0;
		for (Map.Entry<Item, Integer> e : load.entrySet()) {
			int count = e.getValue();
			while (count > 0) {
				int n = Math.min(count, Math.max(1, e.getKey().getDefaultMaxStackSize()));
				ItemStack left = SupplyContainers.insert(level, store, new ItemStack(e.getKey(), n));
				stored += n - left.getCount();
				count -= n;
				if (!left.isEmpty()) {
					rest.merge(e.getKey(), left.getCount() + count, Integer::sum);
					break;
				}
			}
		}
		ModAttachments.HAULER_LOAD.set(golem, rest.isEmpty() ? null : Map.copyOf(rest));
		return stored;
	}

	private HaulerGolems() {
	}
}
