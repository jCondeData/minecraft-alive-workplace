package io.github.jcondedata.aliveworkplace.craft;

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
 * A Leatherworker as the village's dyer (at their cauldron): when a builder nearby is waiting for something coloured —
 * wool, carpet, stained glass, terracotta, concrete, candles, beds, dyes themselves — they make it from what the
 * builder can get at with the game's recipes (dyes from flowers too), and harden concrete powder into concrete in the
 * cauldron's water. Everything else a builder waits for is left to the carpenter.
 */
public class DyerWork extends CrafterWork {
	private static final List<String> COLOURS = java.util.Arrays.stream(DyeColor.values()).map(DyeColor::getName).toList();

	public DyerWork() {
		super(Crafting.Kind.CRAFTING, "dyer", "crafter", false);
	}

	/** Something coloured (by the usual naming: {@code red_wool}, {@code lime_dye}, modded blocks too) or a dye. */
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

	@Override
	protected boolean wants(Item item) {
		return isColoured(item);
	}

	/** Concrete: from its powder (in stock, or mixed from sand, gravel and dye), hardened in the cauldron. */
	@Nullable
	@Override
	protected Crafting.Plan planFor(ServerLevel level, Item item, int count, Map<Item, Long> usable) {
		Item powder = powderFor(item);
		if (powder == null) {
			return super.planFor(level, item, count, usable);
		}
		int n = Math.min(count, Crafting.MAX_CRAFTS);
		long have = usable.getOrDefault(powder, 0L);
		Map<Item, Integer> takes = new LinkedHashMap<>();
		if (have >= n) {
			takes.put(powder, n);
		} else {
			// Mix the powder first (a recipe makes 8), then harden it.
			Crafting.Plan mix = Crafting.plan(level, kind, powder, n, usable);
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
			return new Crafting.Plan(item, n, mix.steps(), takes, makes);
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
		Item powder = BuiltInRegistries.ITEM.get(id.withSuffix("_powder"));
		return powder == Items.AIR ? null : powder;
	}
}
