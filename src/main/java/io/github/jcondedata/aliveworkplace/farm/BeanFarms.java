package io.github.jcondedata.aliveworkplace.farm;

import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.craft.CuisineDishes;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Bean farms (ROADMAP 38.9, Cobblecuisine): a farmer sows {@code cobblecuisine:bean_seeds} only while the store holds
 * fewer than {@link #LOW} of a bean colour that a dish on the menu ({@link CuisineDishes}) needs, the beans being what
 * those dishes' own recipes ask for from {@code #cobblecuisine:beans}. The crop is cut when its {@code age} property
 * (read by name) is at its highest value; its colour is the crop's own loot, so a short colour brings a field of beans
 * of every colour. Everything is read by id: no Cobblecuisine class. Config {@code beanFarms} (behind M38's gate).
 */
public final class BeanFarms {
	/** Config switch {@code beanFarms}. */
	public static boolean ENABLED = true;
	/** Fewer than this many of a wanted colour in the store, and the farmer sows beans. */
	public static final int LOW = 16;

	static final ResourceLocation SEEDS = ResourceLocation.fromNamespaceAndPath("cobblecuisine", "bean_seeds");
	static final ResourceLocation CROP = ResourceLocation.fromNamespaceAndPath("cobblecuisine", "bean_crop");
	static final TagKey<Item> BEANS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("cobblecuisine", "beans"));

	/** The beans the dish recipes ask for, per recipe manager (a datapack reload makes a new one). */
	private static final Map<RecipeManager, Map<Item, Set<Item>>> NEEDS = new WeakHashMap<>();

	public static boolean isSeed(ItemStack stack) {
		return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(SEEDS);
	}

	public static boolean isCrop(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(CROP);
	}

	/** A bean crop at the highest value of its {@code age} property. */
	public static boolean isRipe(BlockState state) {
		if (!isCrop(state)) {
			return false;
		}
		for (Property<?> property : state.getProperties()) {
			if (property.getName().equals("age") && property instanceof IntegerProperty age) {
				int max = age.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);
				return state.getValue(age) >= max;
			}
		}
		return false;
	}

	/** Every bean colour a dish on the menu needs (none while the switch or the dishes are off). */
	public static Set<Item> wanted(ServerLevel level) {
		Set<Item> out = new LinkedHashSet<>();
		if (!ENABLED) {
			return out;
		}
		RecipeManager manager = level.getServer().getRecipeManager();
		Map<Item, Set<Item>> needs;
		synchronized (NEEDS) {
			needs = NEEDS.computeIfAbsent(manager, m -> new java.util.HashMap<>());
		}
		for (CuisineDishes.Dish dish : CuisineDishes.dishes()) {
			out.addAll(needs.computeIfAbsent(dish.item(), item -> beansIn(level, manager, item)));
		}
		return out;
	}

	private static Set<Item> beansIn(ServerLevel level, RecipeManager manager, Item dish) {
		Set<Item> beans = new LinkedHashSet<>();
		for (RecipeHolder<?> holder : Crafting.recipesFor(manager, level.registryAccess(), Crafting.Kind.KITCHEN, dish)) {
			for (Ingredient ingredient : holder.value().getIngredients()) {
				for (Item option : Crafting.options(level, ingredient)) {
					if (new ItemStack(option).is(BEANS)) {
						beans.add(option);
					}
				}
			}
		}
		return beans;
	}

	/** Whether a wanted colour is short in {@code store} (item counts of the farmer's chests and the village's stashes). */
	public static boolean anyShort(ServerLevel level, Map<Item, Long> store) {
		for (Item bean : wanted(level)) {
			if (store.getOrDefault(bean, 0L) < LOW) {
				return true;
			}
		}
		return false;
	}

	private BeanFarms() {
	}
}
