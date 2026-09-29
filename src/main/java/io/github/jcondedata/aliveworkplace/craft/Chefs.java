package io.github.jcondedata.aliveworkplace.craft;

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
		Set<Item> out = new LinkedHashSet<>();
		for (RecipeHolder<?> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.SMOKING)) {
			ItemStack result = holder.value().getResultItem(level.registryAccess());
			if (!result.isEmpty() && result.getComponentsPatch().isEmpty()) {
				out.add(result.getItem());
			}
		}
		out.addAll(List.of(Items.BREAD, Items.COOKIE, Items.PUMPKIN_PIE, Items.CAKE, Items.MUSHROOM_STEW, Items.BEETROOT_SOUP, Items.RABBIT_STEW));
		for (String dish : POT_DISHES) {
			BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("cobblemon", dish)).ifPresent(out::add);
		}
		return new ArrayList<>(out);
	}

	private Chefs() {
	}
}
