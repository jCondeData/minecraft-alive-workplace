package io.github.jcondedata.aliveworkplace.wonder;

import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.VillageGrowth;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.wonder.powers.DeclaredPower;
import io.github.jcondedata.aliveworkplace.wonder.powers.WonderPower;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The Hanging Gardens' powers (ROADMAP 35.20), read where the work they change hands out its numbers. Each answers only
 * while a finished, standing Wonder grants the type ({@link Wonders#power}: a damaged one, or Wonders off, turns them
 * off), so any Wonder file naming the type has the power, not only the Gardens':
 * <ul>
 * <li>{@code harvest} ({@code extra_every}, 4; {@code scope: "realm"}): every farmer working a field
 * ({@code farm/FieldWork}) and every Orchard Keeper ({@code orchard/OrchardWork}) in the Wonder's village and across its
 * realm ({@link Wonders#linked}: its realm's villages, M33, or with realms off its caravan partners) brings in one more
 * item for every {@code extra_every} picked. It is counted per worker and per kind of item (40 wheat cut by one farmer
 * are 10 more wheat, whatever seeds came with them); nothing grows any faster, the crops get no extra ticks;</li>
 * <li>{@code births} ({@code meals}, 12; {@code per_day}, 2): in the Wonder's own village a baby is on the way once the
 * store holds {@code meals} meals instead of {@link VillageGrowth#FOOD_NEEDED} (an edict naming its own figure still
 * has it), and up to {@code per_day} are born a day ({@link VillageGrowth#every}).</li>
 * </ul>
 * The terraces' four farm corners (a composter and a chest by each terrace's fields) are village farms: a farmer who
 * takes a terrace's composter takes on its fields by themselves ({@code farm/Fields}), as at any composter.
 *
 * <p>Nothing here is saved: a worker's count of what they picked since the last extra item lives while the server
 * runs (a restart loses at most {@code extra_every - 1} of each kind towards the next one).
 */
public final class GardensPowers {
	public static final String HARVEST = "harvest";
	public static final String BIRTHS = "births";
	/** The file's numbers when it leaves them out (docs/design/M35.md). */
	public static final int EXTRA_EVERY = 4;
	public static final int MEALS = 12;
	public static final int PER_DAY = 2;

	/** Each worker's items picked under the power towards the next extra one, by kind. */
	private static final Map<UUID, Object2IntOpenHashMap<Item>> PICKED = new ConcurrentHashMap<>();
	/** Each worker's extra items so far (the scene's and the tests' count; not saved). */
	private static final Map<UUID, Integer> EXTRAS = new ConcurrentHashMap<>();

	private static double number(WonderPower power, String name, double fallback) {
		return power instanceof DeclaredPower d ? d.number(name, fallback) : fallback;
	}

	/**
	 * Every how many items picked the worker at {@code at} (their job site, else the plant) brings in one more: the
	 * {@code harvest} power's {@code extra_every} reaching the village there (kept between 1 and 64), or 0 without it.
	 */
	public static int extraEvery(ServerLevel level, BlockPos at) {
		Optional<BlockPos> hall = VillageHalls.nearest(level, at);
		if (hall.isEmpty()) {
			return 0;
		}
		Optional<WonderPower> power = Wonders.power(level, hall.get(), HARVEST);
		return power.map(p -> (int) Math.max(1, Math.min(64, Math.round(number(p, "extra_every", EXTRA_EVERY))))).orElse(0);
	}

	/**
	 * A farmer's or Orchard Keeper's {@code picked} items from the plant at {@code at}: the extra items the
	 * {@code harvest} power adds to them (empty without it), to go wherever the picked ones go. Counted per worker and kind.
	 */
	public static List<ItemStack> harvest(ServerLevel level, Villager worker, BlockPos at, List<ItemStack> picked) {
		if (picked.isEmpty() || !Wonders.ENABLED) {
			return List.of();
		}
		int every = extraEvery(level, Builders.benchPos(worker).orElse(at));
		if (every <= 0) {
			return List.of();
		}
		Object2IntOpenHashMap<Item> counts = PICKED.computeIfAbsent(worker.getUUID(), id -> new Object2IntOpenHashMap<>());
		List<ItemStack> extras = new ArrayList<>();
		int given = 0;
		for (ItemStack stack : picked) {
			if (stack.isEmpty()) {
				continue;
			}
			int count = counts.getInt(stack.getItem()) + stack.getCount();
			int more = count / every;
			counts.put(stack.getItem(), count % every);
			if (more > 0) {
				extras.add(stack.copyWithCount(more));
				given += more;
			}
		}
		if (given > 0) {
			EXTRAS.merge(worker.getUUID(), given, Integer::sum);
		}
		return extras;
	}

	/** The extra items {@code worker} has brought in with the {@code harvest} power since the server started. */
	public static int extras(Villager worker) {
		return EXTRAS.getOrDefault(worker.getUUID(), 0);
	}

	/** Forgets {@code worker}'s counts (tests). */
	public static void forget(Villager worker) {
		PICKED.remove(worker.getUUID());
		EXTRAS.remove(worker.getUUID());
	}

	/** Meals the store of the village round {@code hall} must hold for a baby: {@code births}' {@code meals}, else {@code usual}. */
	public static int mealsNeeded(ServerLevel level, BlockPos hall, int usual) {
		return Wonders.power(level, hall, BIRTHS).map(p -> (int) Math.max(1, Math.min(4096, Math.round(number(p, "meals", MEALS))))).orElse(usual);
	}

	/** Babies a day in the village round {@code hall}: {@code births}' {@code per_day} when that's more than {@code usual}. */
	public static int birthsPerDay(ServerLevel level, BlockPos hall, int usual) {
		return Wonders.power(level, hall, BIRTHS).map(p -> Math.max(usual, (int) Math.max(1, Math.min(8, Math.round(number(p, "per_day", PER_DAY))))))
			.orElse(usual);
	}

	private GardensPowers() {
	}
}
