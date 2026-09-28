package io.github.jcondedata.aliveworkplace.smith;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;

/**
 * The Poké Ball recipes a Ball Smith knows: every crafting recipe (Cobblemon's own, or a datapack's) whose
 * result is in {@code #cobblemon:poke_balls} and that uses one of Cobblemon's ball metals, which also gives
 * its tier — copper 1 (Poké Ball, Premier Ball…), iron 2 (Great Ball…), gold 3 (Ultra Ball…), diamond 4.
 * A smith makes balls up to their own level. Balls without a ball metal (the Master Ball's nether star)
 * are never made. Everything goes by tag and recipe ids, so no Cobblemon classes are needed.
 */
public final class BallRecipes {
	public static final TagKey<Item> POKE_BALLS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("cobblemon", "poke_balls"));
	public static final int MAX_TIER = 4;

	public record BallRecipe(ResourceLocation id, ItemStack result, List<Ingredient> ingredients, int tier) {
	}

	@Nullable
	private static RecipeManager cachedFor;
	private static List<BallRecipe> cached = List.of();

	static TagKey<Item> metal(int tier) {
		return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("cobblemon", "tier_" + tier + "_poke_ball_materials"));
	}

	/** Every ball recipe a smith can use, lowest tier first. */
	public static synchronized List<BallRecipe> all(ServerLevel level) {
		RecipeManager manager = level.getRecipeManager();
		if (manager == cachedFor) {
			return cached;
		}
		List<BallRecipe> found = new ArrayList<>();
		for (RecipeHolder<CraftingRecipe> holder : manager.getAllRecipesFor(RecipeType.CRAFTING)) {
			ItemStack result = holder.value().getResultItem(level.registryAccess());
			if (result.isEmpty() || !result.is(POKE_BALLS)) {
				continue;
			}
			List<Ingredient> ingredients = holder.value().getIngredients().stream().filter(i -> !i.isEmpty()).toList();
			int tier = tier(ingredients);
			if (tier > 0 && !ingredients.isEmpty()) {
				found.add(new BallRecipe(holder.id(), result.copy(), ingredients, tier));
			}
		}
		found.sort(Comparator.comparingInt(BallRecipe::tier).thenComparing(r -> r.id().toString()));
		cached = List.copyOf(found);
		cachedFor = manager;
		return cached;
	}

	/** The highest ball-metal tier among the ingredients (0 if there's no ball metal). */
	static int tier(List<Ingredient> ingredients) {
		int tier = 0;
		for (Ingredient ingredient : ingredients) {
			for (ItemStack option : ingredient.getItems()) {
				for (int t = MAX_TIER; t > tier; t--) {
					if (option.is(metal(t))) {
						tier = t;
						break;
					}
				}
			}
		}
		return tier;
	}

	/**
	 * What to take out of {@code stock} (plain item counts) to craft {@code recipe} once, or null if the stock
	 * doesn't cover it. Doesn't change {@code stock}.
	 */
	@Nullable
	public static Map<Item, Integer> plan(Map<Item, Long> stock, BallRecipe recipe) {
		Map<Item, Long> left = new HashMap<>(stock);
		Map<Item, Integer> take = new HashMap<>();
		for (Ingredient ingredient : recipe.ingredients()) {
			Item chosen = null;
			for (ItemStack option : ingredient.getItems()) {
				if (left.getOrDefault(option.getItem(), 0L) > 0) {
					chosen = option.getItem();
					break;
				}
			}
			if (chosen == null) {
				return null;
			}
			left.merge(chosen, -1L, Long::sum);
			take.merge(chosen, 1, Integer::sum);
		}
		return take;
	}

	private BallRecipes() {
	}
}
