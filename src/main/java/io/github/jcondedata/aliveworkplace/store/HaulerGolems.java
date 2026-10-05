package io.github.jcondedata.aliveworkplace.store;

import com.mojang.serialization.Codec;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.legend.GolemSmith;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
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
 * a golem with a load heads to the Storehouse first. They never break blocks.
 */
public final class HaulerGolems {
	public static final int STACKS = PorterWork.BASE_STACKS;
	public static final int STOREHOUSE_RANGE = 64;
	private static final int EVERY = 10;
	private static final double REACH_SQR = 3.0 * 3.0;
	private static final double SPEED = 0.8;

	public static final Codec<Map<Item, Integer>> LOAD_CODEC = Codec.unboundedMap(BuiltInRegistries.ITEM.byNameCodec(), Codec.INT);

	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			for (IronGolem golem : level.getEntities(EntityType.IRON_GOLEM, g -> g.isAlive() && GolemSmith.role(g) == GolemSmith.Role.HAULER)) {
				work(level, golem);
			}
		}
	}

	/** One step of a hauler's round: store the load, or fetch goods from the nearest worker who has enough. */
	public static void work(ServerLevel level, IronGolem golem) {
		if (golem.getTarget() != null) {
			return; // a fight first
		}
		BlockPos storehouse = storehouse(level, golem.blockPosition()).orElse(null);
		if (storehouse == null) {
			return;
		}
		List<BlockPos> store = SupplyContainers.find(level, storehouse, null);
		if (store.isEmpty()) {
			return;
		}
		Map<Item, Integer> load = load(golem);
		if (!load.isEmpty()) {
			if (walkTo(golem, store.get(0))) {
				unload(level, golem, store, load);
			}
			return;
		}
		if (SupplyContainers.freeSlots(level, store) == 0) {
			return;
		}
		Village.Stash stash = choose(level, golem, storehouse);
		if (stash != null && walkTo(golem, stash.chests().get(0))) {
			collect(level, golem, stash, Math.min(STACKS, SupplyContainers.freeSlots(level, store)));
		}
	}

	/** The nearest Storehouse within {@link #STOREHOUSE_RANGE}. */
	public static Optional<BlockPos> storehouse(ServerLevel level, BlockPos near) {
		return level.getPoiManager().findClosest(h -> h.is(ModVillagers.STOREHOUSE_POI), near, STOREHOUSE_RANGE, PoiManager.Occupancy.ANY);
	}

	/** The saved load (empty when none). */
	public static Map<Item, Integer> load(IronGolem golem) {
		Map<Item, Integer> load = ModAttachments.HAULER_LOAD.get(golem);
		return load == null ? Map.of() : load;
	}

	/** The nearest stash with a trip's worth of goods, from a village worker's side (a Golem Smith's, or anyone's). */
	@Nullable
	static Village.Stash choose(ServerLevel level, IronGolem golem, BlockPos storehouse) {
		Village.Stash box = PorterWork.dropBox(level, storehouse);
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
			if (total(PorterWork.goods(level, stash)) >= PorterWork.MIN_LOAD) {
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

	/** Walks toward {@code pos}; true once within reach. */
	static boolean walkTo(IronGolem golem, BlockPos pos) {
		if (golem.blockPosition().distSqr(pos) <= REACH_SQR || golem.position().distanceToSqr(pos.getCenter()) <= REACH_SQR + 1) {
			golem.getNavigation().stop();
			return true;
		}
		if (golem.getNavigation().isDone()) {
			golem.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, SPEED);
		}
		return false;
	}

	private HaulerGolems() {
	}
}
