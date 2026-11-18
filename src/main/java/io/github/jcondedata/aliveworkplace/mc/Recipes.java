package io.github.jcondedata.aliveworkplace.mc;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
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
	/** The data pack folder recipe files are in ({@code data/<namespace>/recipe/}; {@code recipes} before 1.21). */
	public static final String FOLDER = "recipe";

	public static RecipeManager manager(ServerLevel level) {
		return level.getRecipeManager();
	}

	public static RecipeManager manager(MinecraftServer server) {
		return server.getRecipeManager();
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

	/** The recipe loaded with id {@code id}, if there is one. */
	public static Optional<RecipeHolder<?>> byId(RecipeManager manager, ResourceLocation id) {
		return manager.byKey(id);
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

	/** The id of {@code recipe}'s serializer (a recipe file's {@code type}), or null if it isn't registered. */
	@org.jetbrains.annotations.Nullable
	public static ResourceLocation serializerId(Recipe<?> recipe) {
		return net.minecraft.core.registries.BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer());
	}

	/**
	 * What the game's own crafting table makes of {@code grid} (up to nine things, laid out row by row in a 2x2 grid when
	 * there are four or fewer, else a 3x3), or empty when no loaded recipe takes it (B1378: a special recipe's stand-in is
	 * checked against the real recipe).
	 */
	public static ItemStack crafted(ServerLevel level, List<ItemStack> grid) {
		int side = grid.size() <= 4 ? 2 : 3;
		if (grid.size() > side * side) {
			return ItemStack.EMPTY;
		}
		List<ItemStack> cells = new java.util.ArrayList<>(grid);
		while (cells.size() < side * side) {
			cells.add(ItemStack.EMPTY);
		}
		net.minecraft.world.item.crafting.CraftingInput input = net.minecraft.world.item.crafting.CraftingInput.of(side, side, cells);
		return find(manager(level), RecipeType.CRAFTING, input, level)
			.map(holder -> holder.value().assemble(input, level.registryAccess()))
			.orElse(ItemStack.EMPTY);
	}

	/** A shapeless crafting recipe, in memory only, making {@code result} from any one of {@code options} (B1302's stand-ins). */
	public static RecipeHolder<?> shapeless(ResourceLocation id, ItemStack result, List<net.minecraft.world.item.Item> options) {
		return shapeless(id, result, options, 1);
	}

	/** As {@link #shapeless(ResourceLocation, ItemStack, List)}, from {@code count} of them (B1290: a crafting table from four planks). */
	public static RecipeHolder<?> shapeless(ResourceLocation id, ItemStack result, List<net.minecraft.world.item.Item> options, int count) {
		Ingredient ingredient = Ingredient.of(options.stream().map(ItemStack::new));
		net.minecraft.core.NonNullList<Ingredient> slots = net.minecraft.core.NonNullList.withSize(count, Ingredient.EMPTY);
		for (int i = 0; i < count; i++) {
			slots.set(i, ingredient);
		}
		return new RecipeHolder<>(id, new net.minecraft.world.item.crafting.ShapelessRecipe("",
			net.minecraft.world.item.crafting.CraftingBookCategory.MISC, result, slots));
	}

	private Recipes() {
	}
}
