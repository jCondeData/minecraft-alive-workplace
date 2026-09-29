package io.github.jcondedata.aliveworkplace.craft;

import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * A vanilla Mason's work for the builders nearby: the stonecutter's recipes, and the village's crusher, glassblower and
 * kiln besides — cobblestone crushed to gravel and gravel to sand at the stonecutter, and, when there's a furnace by the
 * stonecutter, sand fired into glass and the rest of the kiln's work ({@link Crafting.Kind#KILN}: stone, smooth stone,
 * terracotta, bricks from clay...), a coal or charcoal for every {@link #GLASS_PER_FUEL} things fired.
 */
public class MasonWork extends CrafterWork {
	/** Glass fired per coal or charcoal (a furnace's rate). */
	static final int GLASS_PER_FUEL = 8;
	/** What's crushed into what. */
	static final Map<Item, Item> CRUSHED = Map.of(Items.GRAVEL, Items.COBBLESTONE, Items.SAND, Items.GRAVEL);

	@Nullable
	private Villager self;

	public MasonWork() {
		super(Crafting.Kind.STONECUTTING, false);
	}

	@Nullable
	@Override
	protected Job choose(ServerLevel level, Villager villager, net.minecraft.core.BlockPos station) {
		self = villager;
		return super.choose(level, villager, station);
	}

	@Nullable
	@Override
	protected Crafting.Plan planFor(ServerLevel level, Item item, int count, Map<Item, Long> usable) {
		int n = Math.min(count, Crafting.MAX_CRAFTS);
		if (item == Items.GLASS) {
			if (!hasFurnace(level)) {
				return null;
			}
			int fuel = (n + GLASS_PER_FUEL - 1) / GLASS_PER_FUEL;
			Item coal = usable.getOrDefault(Items.COAL, 0L) >= fuel ? Items.COAL : usable.getOrDefault(Items.CHARCOAL, 0L) >= fuel ? Items.CHARCOAL : null;
			if (coal == null) {
				return null;
			}
			Crafting.Plan sand = convert(level, Items.SAND, n, usable, coal, fuel);
			return sand == null ? null : swap(sand, Items.SAND, Items.GLASS, n);
		}
		if (CRUSHED.containsKey(item)) {
			return convert(level, item, n, usable, null, 0);
		}
		Crafting.Plan cut = super.planFor(level, item, count, usable);
		if (fits(cut) || !hasFurnace(level)) {
			return cut;
		}
		// The kiln: stone, smooth stone, terracotta, bricks... fired in the furnace by the stonecutter (and cut or laid after).
		Crafting.Plan kiln = Crafting.plan(level, Crafting.Kind.KILN, item, count, usable);
		if (kiln == null || !fires(level, kiln, Crafting.Kind.KILN)) {
			return cut;
		}
		return withFuel(level, kiln, usable, Crafting.Kind.KILN, GLASS_PER_FUEL);
	}

	/**
	 * {@code n} of {@code item}: taken as it is if there's enough, else crushed from what it's crushed from (two steps down
	 * at most: sand from gravel from cobblestone); {@code fuel} of {@code fuelItem} taken besides.
	 */
	@Nullable
	private Crafting.Plan convert(ServerLevel level, Item item, int n, Map<Item, Long> usable, @Nullable Item fuelItem, int fuel) {
		Map<Item, Integer> takes = new LinkedHashMap<>();
		if (fuelItem != null) {
			takes.put(fuelItem, fuel);
		}
		Item from = item;
		for (int depth = 0; depth <= 2 && from != null; depth++) {
			if (usable.getOrDefault(from, 0L) >= n) {
				takes.merge(from, n, Integer::sum);
				return new Crafting.Plan(item, n, List.of(new Crafting.Step(Map.of(from, 1), new ItemStack(item), n)), takes, Map.of(item, n));
			}
			from = CRUSHED.get(from);
		}
		return null;
	}

	/** The same plan, with {@code n} of {@code made} turned into {@code into} at the end. */
	private static Crafting.Plan swap(Crafting.Plan plan, Item made, Item into, int n) {
		Map<Item, Integer> makes = new LinkedHashMap<>(plan.makes());
		makes.merge(made, -n, Integer::sum);
		if (makes.getOrDefault(made, 0) <= 0) {
			makes.remove(made);
		}
		makes.merge(into, n, Integer::sum);
		return new Crafting.Plan(into, n, plan.steps(), plan.takes(), makes);
	}

	/** A furnace by our stonecutter to fire glass in. */
	private boolean hasFurnace(ServerLevel level) {
		return self != null && Builders.benchPos(self).map(p -> !SupplyContainers.furnaces(level, p).isEmpty()).orElse(false);
	}

	@Override
	protected boolean wants(Item item) {
		return true;
	}
}
