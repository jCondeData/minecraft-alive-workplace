package io.github.jcondedata.aliveworkplace.craft;

import io.github.jcondedata.aliveworkplace.mc.Recipes;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

/** Chefs and what they cook. */
public final class Chefs {
	/** A chef stops cooking a dish once the chests hold this many. */
	public static final int KEEP = 16;
	/** At most this many of a dish in one go. */
	public static final int BATCH = 8;

	/** Cobblemon dishes a chef cooks (by id, so nothing breaks without Cobblemon or if one is renamed). */
	static final List<String> POT_DISHES = List.of("poke_bait", "poke_snack", "poke_cake", "candied_apple", "aprijuice_red", "aprijuice_yellow",
		"aprijuice_green", "aprijuice_blue", "aprijuice_pink", "aprijuice_black", "aprijuice_white");

	public static boolean isChef(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == ModVillagers.CHEF;
	}

	/**
	 * What a chef cooks, in turn: everything the smoker cooks (meat, fish, potatoes), bread, cookies, pumpkin pie, cake and
	 * the stews, and with Cobblemon its Poké Bait, Poké Snacks, Poké Cakes, candied apples and Aprijuice.
	 */
	public static List<Item> menu(ServerLevel level) {
		return new ArrayList<>(menu(Recipes.manager(level), level.registryAccess()));
	}

	private static Set<Item> menu(net.minecraft.world.item.crafting.RecipeManager manager, net.minecraft.core.HolderLookup.Provider registries) {
		Set<Item> out = new LinkedHashSet<>();
		for (RecipeHolder<?> holder : Recipes.all(manager, RecipeType.SMOKING)) {
			ItemStack result = Recipes.result(holder.value(), registries);
			if (!result.isEmpty() && result.getComponentsPatch().isEmpty()) {
				out.add(result.getItem());
			}
		}
		out.addAll(List.of(Items.BREAD, Items.COOKIE, Items.PUMPKIN_PIE, Items.CAKE, Items.MUSHROOM_STEW, Items.BEETROOT_SOUP, Items.RABBIT_STEW));
		for (String dish : POT_DISHES) {
			BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("cobblemon", dish)).ifPresent(out::add);
		}
		return out;
	}

	/**
	 * Every dish a kitchen keeps a stock of: {@link #menu(ServerLevel)}'s and every Campfire Pot recipe's result, whether or
	 * not {@code cuisineDishes} is on. {@link Crafting}'s kitchen recipes leave out a recipe that only turns some of these
	 * into another of them (B435, B459: Cobblemon's EXP Candies, six XS into an S in the pot and an S back into six XS on
	 * the crafting table, all the way up to XL), so a chef cooks his dishes from their makings and never cooks one kept dish
	 * up or breaks it down into another (the store's 15 XS into an M, a player's XL into L, M, S and XS). A recipe with any
	 * other making still takes a dish (Mighty Candy, an EXP Candy XL with berries and honeycomb).
	 */
	static Set<Item> keptDishes(net.minecraft.world.item.crafting.RecipeManager manager, net.minecraft.core.HolderLookup.Provider registries) {
		Set<Item> out = menu(manager, registries);
		for (RecipeHolder<?> holder : Recipes.all(manager)) {
			if (Crafting.isCookingPot(holder.value().getType())) {
				ItemStack result = Recipes.result(holder.value(), registries);
				if (!result.isEmpty() && result.getComponentsPatch().isEmpty()) {
					out.add(result.getItem());
				}
			}
		}
		return out;
	}

	/**
	 * What {@code chef} cooks on his own: {@link #menu(ServerLevel)}, then every Campfire Pot recipe's dish
	 * ({@link #potDishes}, 38.11). The other mods' dishes ({@link CuisineDishes}, Cobblecuisine's) are not on it: since
	 * 38.8a (the owner, 2026-10-19) he cooks those only when a player asks for them at the hall's board
	 * ({@link DishRequests}), and only those his level allows. Without those mods, or with {@code cuisineDishes} off,
	 * it's the same menu.
	 */
	public static List<Item> menu(ServerLevel level, Villager chef) {
		Set<Item> out = new LinkedHashSet<>(menu(level));
		out.addAll(potDishes(level));
		out.removeIf(item -> CuisineDishes.of(item) != null); // 38.8a: a dish of the lists only on request
		return new ArrayList<>(out);
	}

	/**
	 * Every Campfire Pot recipe's dish (ROADMAP 38.11): the results of the recipes of Cobblemon's pot types
	 * ({@code cobblemon:cooking_pot} and {@code cobblemon:cooking_pot_shapeless}, found by type, so another mod's or a data
	 * pack's come too), such as the EXP Candies, the IV and EV candies, medicines and sweets. A result with its own
	 * components (a potion's contents) is left out: the chef cooks plain items. The chef cooks one only from makings the
	 * chests hold ({@link Crafting#plan}). Empty without Cobblemon, and while {@code cuisineDishes} (behind M38's gate)
	 * is off.
	 */
	public static List<Item> potDishes(ServerLevel level) {
		if (!CuisineDishes.ENABLED) {
			return List.of();
		}
		net.minecraft.world.item.crafting.RecipeManager manager = Recipes.manager(level);
		synchronized (Chefs.class) {
			if (manager == potManager) {
				return potCache; // read once per recipe load
			}
		}
		Set<Item> out = new LinkedHashSet<>();
		for (RecipeHolder<?> holder : Recipes.all(manager)) {
			if (!Crafting.isCookingPot(holder.value().getType())) {
				continue;
			}
			ItemStack result = Recipes.result(holder.value(), level.registryAccess());
			if (!result.isEmpty() && result.getComponentsPatch().isEmpty()) {
				out.add(result.getItem());
			}
		}
		List<Item> dishes = List.copyOf(out);
		synchronized (Chefs.class) {
			potManager = manager;
			potCache = dishes;
		}
		return dishes;
	}

	private static net.minecraft.world.item.crafting.RecipeManager potManager;
	private static List<Item> potCache = List.of();

	private Chefs() {
	}
}
