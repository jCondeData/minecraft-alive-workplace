package io.github.jcondedata.aliveworkplace.mine;

import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import java.util.List;
import java.util.Map;
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
 * Miners tend the furnaces near their bench whenever they drop off a haul: what's smelted comes out into the chests,
 * raw ores and ore blocks go in (a stack at a time), and coal or charcoal from the chests keeps them burning. Only
 * ores are touched: whatever a player put in a furnace themselves is left alone.
 */
public final class Smelting {
	/** Coal kept in a furnace's fuel slot while it has ore to smelt. */
	static final int FUEL = 16;

	/** Tends every furnace near {@code bench}; returns how many items were moved in or out. */
	public static int tend(ServerLevel level, BlockPos bench, List<BlockPos> supplies) {
		int moved = 0;
		for (BlockPos pos : SupplyContainers.furnaces(level, bench)) {
			if (level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace) {
				moved += tend(level, furnace, supplies);
			}
		}
		return moved;
	}

	private static int tend(ServerLevel level, AbstractFurnaceBlockEntity furnace, List<BlockPos> supplies) {
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
				if (isOre(e.getKey()) && smelts(level, furnace, e.getKey())) {
					int got = SupplyContainers.extract(level, supplies, e.getKey(), e.getKey().getDefaultMaxStackSize());
					if (got > 0) {
						furnace.setItem(0, new ItemStack(e.getKey(), got));
						moved += got;
						break;
					}
				}
			}
		} else if (isOre(in.getItem()) && in.getCount() < in.getMaxStackSize() && smelts(level, furnace, in.getItem())) {
			int got = SupplyContainers.extract(level, supplies, in.getItem(), in.getMaxStackSize() - in.getCount());
			in.grow(got);
			moved += got;
		}
		// Coal to burn it with, only while there's ore in.
		ItemStack fuel = furnace.getItem(1);
		ItemStack smelting = furnace.getItem(0);
		if (!smelting.isEmpty() && isOre(smelting.getItem()) && (fuel.isEmpty() || fuel.is(ItemTags.COALS)) && fuel.getCount() < FUEL) {
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

	/** Raw ores and ore blocks (by the common {@code c:} tags, so modded ores count too). */
	static boolean isOre(Item item) {
		ItemStack stack = new ItemStack(item);
		return stack.is(ConventionalItemTags.RAW_MATERIALS) || stack.is(ConventionalItemTags.ORES);
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

	private Smelting() {
	}
}
