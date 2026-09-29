package io.github.jcondedata.aliveworkplace.work;

import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.mc.Recipes;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
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
	/** The common {@code c:raw_materials} and {@code c:ores} item tags (shared by mods; Fabric API fills them in). */
	private static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> RAW_MATERIALS = net.minecraft.tags.TagKey.create(
		net.minecraft.core.registries.Registries.ITEM, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "raw_materials"));
	private static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> ORE_ITEMS = net.minecraft.tags.TagKey.create(
		net.minecraft.core.registries.Registries.ITEM, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "ores"));
	/** Coal kept in a furnace's fuel slot while it has ore to smelt. */
	static final int FUEL = 16;

	/**
	 * Tends every furnace near {@code station}, loading items that pass {@code goods} (and that furnace can cook);
	 * returns how many items were moved in or out.
	 */
	public static int tend(ServerLevel level, BlockPos station, List<BlockPos> supplies, Predicate<Item> goods) {
		int moved = 0;
		int fire = -1;
		for (BlockPos pos : SupplyContainers.furnaces(level, station)) {
			if (level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace) {
				moved += tend(level, furnace, supplies, goods);
				if (fire < 0) {
					fire = firePartners(level, station);
				}
				if (fire > 0) {
					moved += blaze(level, furnace, pos, supplies, goods, fire * PER_FIRE_PARTNER);
				}
			}
		}
		return moved;
	}

	/** Items a Fire-type partner smelts on the spot, per furnace, each time a worker tends it. */
	public static final int PER_FIRE_PARTNER = 8;

	/** Fire-type Pokémon pastured near the workstation (with Cobblemon; at most {@link Partners#MAX}). */
	static int firePartners(ServerLevel level, BlockPos station) {
		return PokemonPartners.EXTENSION.call(p -> p.helpers(level, station, Partners.RADIUS, java.util.Set.of("fire"), Partners.MAX).size(), 0);
	}

	/**
	 * A Fire-type partner's help: up to {@code count} of what's waiting in the furnace is smelted at once, no fuel
	 * used, and goes straight into the chests (only as much as there's room for). Returns how many were smelted.
	 */
	static int blaze(ServerLevel level, AbstractFurnaceBlockEntity furnace, BlockPos pos, List<BlockPos> supplies, Predicate<Item> goods, int count) {
		ItemStack in = furnace.getItem(0);
		if (in.isEmpty() || !goods.test(in.getItem())) {
			return 0;
		}
		ItemStack each = result(level, furnace, in.getItem());
		if (each.isEmpty()) {
			return 0;
		}
		int done = 0;
		for (int i = 0; i < Math.min(count, in.getCount()); i++) {
			ItemStack rest = SupplyContainers.insert(level, supplies, each.copy());
			if (!rest.isEmpty()) {
				break; // the chests are full: the furnace carries on as usual
			}
			done++;
		}
		if (done > 0) {
			in.shrink(done);
			furnace.setChanged();
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 8, 0.25, 0.1, 0.25, 0.02);
			level.playSound(null, pos, net.minecraft.sounds.SoundEvents.FIRECHARGE_USE, net.minecraft.sounds.SoundSource.BLOCKS, 0.4f, 1.2f);
		}
		return done;
	}

	/** What one {@code item} smelts into in this furnace, or empty. */
	static ItemStack result(ServerLevel level, AbstractFurnaceBlockEntity furnace, Item item) {
		SingleRecipeInput input = new SingleRecipeInput(new ItemStack(item));
		RecipeType<? extends net.minecraft.world.item.crafting.AbstractCookingRecipe> type = furnace instanceof BlastFurnaceBlockEntity ? RecipeType.BLASTING
			: furnace instanceof SmokerBlockEntity ? RecipeType.SMOKING : RecipeType.SMELTING;
		return Recipes.find(Recipes.manager(level), type, input, level)
			.map(r -> Recipes.assemble(r.value(), input, level.registryAccess()))
			.orElse(ItemStack.EMPTY);
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
		return stack.is(RAW_MATERIALS) || stack.is(ORE_ITEMS);
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
			return Recipes.find(Recipes.manager(level), RecipeType.BLASTING, input, level).isPresent();
		}
		if (type == RecipeType.SMOKING) {
			return Recipes.find(Recipes.manager(level), RecipeType.SMOKING, input, level).isPresent();
		}
		return Recipes.find(Recipes.manager(level), RecipeType.SMELTING, input, level).isPresent();
	}

	private Furnaces() {
	}
}
