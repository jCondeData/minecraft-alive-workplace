//? if cobblemon {
package io.github.jcondedata.aliveworkplace.compat.cobblemon;

import com.cobblemon.mod.common.CobblemonRecipeTypes;
import com.cobblemon.mod.common.block.campfirepot.CampfireBlock;
import com.cobblemon.mod.common.block.entity.CampfireBlockEntity;
import com.cobblemon.mod.common.item.crafting.CookingPotRecipe;
import com.cobblemon.mod.common.item.crafting.CookingPotShapelessRecipe;
import io.github.jcondedata.aliveworkplace.camp.CampCooks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.block.state.BlockState;

// Cobblemon's Campfire Pot for the Camp Cook (ROADMAP 28.8): its lid (the block state redstone drives) and its
// cooking recipes, shaped and shapeless, read from the loaded recipes so a data pack's dishes come too. Only touched
// when Cobblemon is installed.
public final class CobblemonCampPot implements CampCooks.Pot {
	@Override
	public boolean isPot(BlockState state) {
		return state.getBlock() instanceof CampfireBlock;
	}

	@Override
	public boolean lidShut(BlockState state) {
		return state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.Companion.getLID());
	}

	@Override
	public void setLid(ServerLevel level, BlockPos pos, boolean shut) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof CampfireBlock) || state.getValue(CampfireBlock.Companion.getLID()) == shut) {
			return;
		}
		if (level.getBlockEntity(pos) instanceof CampfireBlockEntity pot) {
			pot.toggleLid(!shut); // the pot's own sound and game event
		}
		BlockState now = level.getBlockState(pos);
		if (now.getBlock() instanceof CampfireBlock && now.getValue(CampfireBlock.Companion.getLID()) != shut) {
			level.setBlock(pos, now.setValue(CampfireBlock.Companion.getLID(), shut), 3);
		}
	}

	@Override
	public boolean cooks(ServerLevel level, BlockPos pos) {
		if (!(level.getBlockEntity(pos) instanceof CampfireBlockEntity pot)) {
			return false;
		}
		List<net.minecraft.world.item.ItemStack> grid = new ArrayList<>();
		for (int slot = 1; slot <= 9; slot++) {
			grid.add(pot.getItem(slot));
		}
		net.minecraft.world.item.crafting.CraftingInput input = net.minecraft.world.item.crafting.CraftingInput.of(3, 3, grid);
		return level.getRecipeManager().getRecipeFor(CobblemonRecipeTypes.INSTANCE.getCOOKING_POT_COOKING(), input, level).isPresent()
			|| level.getRecipeManager().getRecipeFor(CobblemonRecipeTypes.INSTANCE.getCOOKING_POT_SHAPELESS(), input, level).isPresent();
	}

	@Override
	public List<CampCooks.PotRecipe> recipes(ServerLevel level, Item dish) {
		List<CampCooks.PotRecipe> out = new ArrayList<>();
		for (RecipeHolder<CookingPotRecipe> holder : level.getRecipeManager().getAllRecipesFor(CobblemonRecipeTypes.INSTANCE.getCOOKING_POT_COOKING())) {
			CookingPotRecipe recipe = holder.value();
			if (!recipe.getResult().is(dish)) {
				continue;
			}
			ShapedRecipePattern pattern = recipe.getPattern();
			List<Ingredient> grid = new ArrayList<>();
			for (int i = 0; i < 9; i++) {
				grid.add(Ingredient.EMPTY);
			}
			for (int y = 0; y < pattern.height(); y++) {
				for (int x = 0; x < pattern.width(); x++) {
					grid.set(y * 3 + x, pattern.ingredients().get(y * pattern.width() + x));
				}
			}
			out.add(new CampCooks.PotRecipe(grid, recipe.getSeasoningTag()));
		}
		for (RecipeHolder<CookingPotShapelessRecipe> holder : level.getRecipeManager().getAllRecipesFor(CobblemonRecipeTypes.INSTANCE.getCOOKING_POT_SHAPELESS())) {
			CookingPotShapelessRecipe recipe = holder.value();
			if (recipe.getResult().is(dish)) {
				out.add(new CampCooks.PotRecipe(List.copyOf(recipe.getIngredients()), recipe.getSeasoningTag()));
			}
		}
		return out;
	}
}
//?}
