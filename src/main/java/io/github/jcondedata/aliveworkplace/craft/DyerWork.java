package io.github.jcondedata.aliveworkplace.craft;

import io.github.jcondedata.aliveworkplace.mc.Lookup;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * A Leatherworker's crafting for builders (at their cauldron): leather goods, item frames and leads (46.1, the
 * {@code aliveworkplace:crafts/leatherworker} tag), and until the Shepherd, Florist and Beekeeper craft (46.2, 46.3,
 * 46.5) their lists too, as the village's dyer who made them before: wool, carpets, beds, banners, dyes, flower pots and
 * candles, with the game's recipes. Stained glass, terracotta and concrete are the Mason's now ({@link CraftJobs}).
 */
public class DyerWork extends CrafterWork {
	private static final List<String> COLOURS = java.util.Arrays.stream(DyeColor.values()).map(DyeColor::getName).toList();

	public DyerWork() {
		super(Crafting.Kind.CRAFTING, "dyer", "crafter", false);
	}

	/**
	 * Something coloured (by the usual naming: {@code red_wool}, {@code lime_dye}, modded blocks too) or a dye. No longer
	 * what the dyer takes (46.1: each job's own list, {@link CraftJobs}); kept for the tests and tools that ask.
	 */
	static boolean isColoured(Item item) {
		ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
		String path = id.getPath();
		if (path.endsWith("_dye")) {
			return true;
		}
		for (String colour : COLOURS) {
			if (path.startsWith(colour + "_")) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 46.1: the Leatherworker's own list ({@code aliveworkplace:crafts/leatherworker}), and meanwhile, as the village's
	 * dyer who made them before, the Shepherd's, Florist's and Beekeeper's (until 46.2, 46.3 and 46.5 teach those jobs).
	 */
	@Override
	protected boolean wants(ServerLevel level, Item item) {
		return CraftJobs.makes(level, CraftJobs.Job.LEATHERWORKER, item);
	}

	/** Concrete: from its powder (in stock, or mixed from sand, gravel and dye), hardened in the cauldron. */
	@Nullable
	@Override
	protected Crafting.Plan planFor(ServerLevel level, Item item, int count, Map<Item, Long> usable) {
		if (powderFor(item) == null) {
			return super.planFor(level, item, count, usable);
		}
		return concrete(level, item, count, usable);
	}

	/**
	 * {@code count} concrete ({@code item}) from its powder, in stock or mixed first (a recipe makes 8), hardened in water
	 * as the plan's own last step; null if {@code item} isn't concrete or there's too little. The Mason's too since 46.1.
	 */
	@Nullable
	static Crafting.Plan concrete(ServerLevel level, Item item, int count, Map<Item, Long> usable) {
		Item powder = powderFor(item);
		if (powder == null) {
			return null;
		}
		int n = Math.min(count, Crafting.MAX_CRAFTS);
		long have = usable.getOrDefault(powder, 0L);
		Map<Item, Integer> takes = new LinkedHashMap<>();
		if (have >= n) {
			takes.put(powder, n);
		} else {
			// Mix the powder first (a recipe makes 8), then harden it.
			Crafting.Plan mix = Crafting.plan(level, Crafting.Kind.CRAFTING, powder, n, usable);
			if (mix == null || mix.count() < n) {
				return null;
			}
			takes.putAll(mix.takes());
			Map<Item, Integer> makes = new LinkedHashMap<>(mix.makes());
			int extra = makes.getOrDefault(powder, 0) - n;
			makes.remove(powder);
			if (extra > 0) {
				makes.put(powder, extra);
			}
			makes.merge(item, n, Integer::sum);
			// the hardening is the plan's own last step, so the chain is made step by step and saved after each (37.14a):
			// without it the plan was made all at once, and one loaded mid-job lost the spare powder
			List<Crafting.Step> steps = new java.util.ArrayList<>(mix.steps());
			steps.add(new Crafting.Step(Map.of(powder, 1), new net.minecraft.world.item.ItemStack(item), n));
			return new Crafting.Plan(item, n, steps, takes, makes);
		}
		return new Crafting.Plan(item, n, List.of(new Crafting.Step(Map.of(powder, 1), new net.minecraft.world.item.ItemStack(item), n)),
			takes, Map.of(item, n));
	}

	/** The concrete powder {@code item} hardens from, if it's concrete ({@code x_concrete} → {@code x_concrete_powder}). */
	@Nullable
	static Item powderFor(Item item) {
		ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
		if (!id.getPath().endsWith("_concrete")) {
			return null;
		}
		Item powder = Lookup.value(BuiltInRegistries.ITEM, id.withSuffix("_powder"));
		return powder == Items.AIR ? null : powder;
	}
}
