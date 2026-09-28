package io.github.jcondedata.aliveworkplace.work;

import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;

/**
 * Workers tend the furnaces (and smokers) near their workstation whenever they drop off: what's done comes out into
 * the chests, their own kind of goods go in (a stack at a time) — miners' ores, fishers' fish — and coal or charcoal
 * from the chests keeps them burning. Anything else in a furnace was put there by a player and is left alone.
 */
public final class Furnaces {
	/** Coal kept in a furnace's fuel slot while it has ore to smelt. */
	static final int FUEL = 16;

	/**
	 * Tends every furnace near {@code station}, loading items that pass {@code goods} (and that furnace can cook);
	 * returns how many items were moved in or out.
	 */
	public static int tend(ServerLevel level, BlockPos station, List<BlockPos> supplies, Predicate<Item> goods) {
		int moved = 0;
		for (BlockPos pos : SupplyContainers.furnaces(level, station)) {
			if (level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace) {
				moved += tend(level, furnace, supplies, goods);
			}
		}
		return moved;
	}

	private static int tend(ServerLevel level, AbstractFurnaceBlockEntity furnace, List<BlockPos> supplies, Predicate<Item> goods) {
		int moved = 0;
		// What's done comes out into the chests.
		ItemStack out = furnace.getItem(2);
		if (!out.isEmpty()) {
			ItemStack rest = SupplyContainers.insert(level, supplies, out.copy());
			moved += out.getCount() - rest.getCount();
			furnace.setItem(2, rest);
		}
		// Ore goes in: a new stack, or more of what's already smelting.
		ItemStack in = furnace.getItem(0);
		if (in.isEmpty()) {
			for (Map.Entry<Item, Long> e : SupplyContainers.contents(level, supplies).entrySet()) {
				if (goods.test(e.getKey()) && smelts(level, furnace, e.getKey())) {
					int got = SupplyContainers.extract(level, supplies, e.getKey(), e.getKey().getDefaultMaxStackSize());
					if (got > 0) {
						furnace.setItem(0, new ItemStack(e.getKey(), got));
						moved += got;
						break;
					}
				}
			}
		} else if (goods.test(in.getItem()) && in.getCount() < in.getMaxStackSize() && smelts(level, furnace, in.getItem())) {
			int got = SupplyContainers.extract(level, supplies, in.getItem(), in.getMaxStackSize() - in.getCount());
			in.grow(got);
			moved += got;
		}
		// Coal to burn it with, only while there's ore in.
		ItemStack fuel = furnace.getItem(1);
		ItemStack smelting = furnace.getItem(0);
		if (!smelting.isEmpty() && goods.test(smelting.getItem()) && (fuel.isEmpty() || fuel.is(ItemTags.COALS)) && fuel.getCount() < FUEL) {
			Item coal = fuel.isEmpty() ? null : fuel.getItem();
			for (Item option : coal != null ? List.of(coal) : List.of(Items.COAL, Items.CHARCOAL)) {
				int got = SupplyContainers.extract(level, supplies, option, FUEL - fuel.getCount());
				if (got > 0) {
					furnace.setItem(1, new ItemStack(option, fuel.getCount() + got));
					moved += got;
					break;
				}
			}
		}
		if (moved > 0) {
			furnace.setChanged();
		}
		return moved;
	}

	/** Raw ores and ore blocks (by the common {@code c:} tags, so modded ores count too): what miners smelt. */
	public static boolean isOre(Item item) {
		ItemStack stack = new ItemStack(item);
		return stack.is(ConventionalItemTags.RAW_MATERIALS) || stack.is(ConventionalItemTags.ORES);
	}

	/** Raw fish: what fishers put in a smoker or furnace (cooked fish have no recipe, so they're never loaded). */
	public static boolean isFish(Item item) {
		return new ItemStack(item).is(ItemTags.FISHES);
	}

	/** Whether this furnace can smelt {@code item} (a blast furnace blasts, a smoker only cooks food). */
	static boolean smelts(ServerLevel level, AbstractFurnaceBlockEntity furnace, Item item) {
		RecipeType<?> type = furnace instanceof BlastFurnaceBlockEntity ? RecipeType.BLASTING
			: furnace instanceof SmokerBlockEntity ? RecipeType.SMOKING : RecipeType.SMELTING;
		SingleRecipeInput input = new SingleRecipeInput(new ItemStack(item));
		if (type == RecipeType.BLASTING) {
			return level.getRecipeManager().getRecipeFor(RecipeType.BLASTING, input, level).isPresent();
		}
		if (type == RecipeType.SMOKING) {
			return level.getRecipeManager().getRecipeFor(RecipeType.SMOKING, input, level).isPresent();
		}
		return level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, input, level).isPresent();
	}

	private Furnaces() {
	}
}
