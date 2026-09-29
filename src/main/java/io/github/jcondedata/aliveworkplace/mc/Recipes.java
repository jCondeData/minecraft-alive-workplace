package io.github.jcondedata.aliveworkplace.mc;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/**
 * The game's recipes, as the crafting jobs read them. Changes (porting.md): recipe ids become {@code ResourceKey}s and
 * {@code getResultItem} goes in 1.21.2; {@code assemble} loses its registries argument in 26.1. The 1.21.2 recipe
 * rework (ingredients, the recipe manager on the server only) lands here too.
 */
public final class Recipes {
	public static RecipeManager manager(ServerLevel level) {
		return level.getRecipeManager();
	}

	public static <I extends RecipeInput, T extends Recipe<I>> List<RecipeHolder<T>> all(RecipeManager manager, RecipeType<T> type) {
		return manager.getAllRecipesFor(type);
	}

	public static Collection<RecipeHolder<?>> all(RecipeManager manager) {
		return manager.getRecipes();
	}

	public static <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>> find(RecipeManager manager, RecipeType<T> type, I input,
			Level level) {
		return manager.getRecipeFor(type, input, level);
	}

	public static ResourceLocation id(RecipeHolder<?> holder) {
		return holder.id();
	}

	/** What the recipe makes (a template: don't change it). */
	public static ItemStack result(Recipe<?> recipe, HolderLookup.Provider registries) {
		return recipe.getResultItem(registries);
	}

	public static List<Ingredient> ingredients(Recipe<?> recipe) {
		return recipe.getIngredients();
	}

	/** Every item an ingredient accepts. */
	public static ItemStack[] options(Ingredient ingredient) {
		return ingredient.getItems();
	}

	public static ItemStack assemble(AbstractCookingRecipe recipe, SingleRecipeInput input, HolderLookup.Provider registries) {
		return recipe.assemble(input, registries);
	}

	private Recipes() {
	}
}
