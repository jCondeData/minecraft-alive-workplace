package io.github.jcondedata.aliveworkplace.story;

import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The Gift (ROADMAP 31.6): any one item in Gift Wrap, kept in the {@code gift} component ({@link Gifts.Wrapped}). Its
 * tooltip says who it's from ("From Jesse": whoever took it off the crafting grid) and never what's inside. Giving it
 * to a villager is {@link Gifts#give}.
 */
public class GiftItem extends Item {
	public static final RecipeSerializer<Recipe> RECIPE = new SimpleCraftingRecipeSerializer<>(Recipe::new);

	public GiftItem(Properties properties) {
		super(properties);
	}

	/** Taken off the crafting grid by {@code player}: the Gift is from them. */
	@Override
	public void onCraftedBy(ItemStack stack, Level level, Player player) {
		Gifts.Wrapped wrapped = stack.get(ModComponents.GIFT);
		if (wrapped != null && wrapped.from().isEmpty()) {
			stack.set(ModComponents.GIFT, wrapped.withFrom(player.getGameProfile().getName()));
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		String from = Gifts.from(stack);
		if (!from.isEmpty()) {
			tooltip.add(Component.translatable("tooltip.aliveworkplace.gift.from", from).withStyle(ChatFormatting.GRAY));
		}
		tooltip.add(Component.translatable("tooltip.aliveworkplace.gift.hint").withStyle(ChatFormatting.DARK_GRAY));
	}

	/**
	 * Gift Wrap and any one other item, shapeless: a Gift holding that item (a special recipe, as vanilla's map
	 * cloning is). One item is taken from the grid and nothing is left behind (a wrapped Milk Bucket keeps its bucket).
	 * Gift Wrap and Gifts themselves can't be wrapped.
	 */
	public static class Recipe extends CustomRecipe {
		public Recipe(CraftingBookCategory category) {
			super(category);
		}

		/** The item to wrap: the grid's one item beside exactly one Gift Wrap, or null. */
		@Nullable
		private static ItemStack wrapped(CraftingInput input) {
			ItemStack item = null;
			int wrap = 0;
			for (int i = 0; i < input.size(); i++) {
				ItemStack s = input.getItem(i);
				if (s.isEmpty()) {
					continue;
				}
				if (s.is(ModItems.GIFT_WRAP)) {
					wrap++;
				} else if (item == null && !s.is(ModItems.GIFT)) {
					item = s;
				} else {
					return null;
				}
			}
			return wrap == 1 ? item : null;
		}

		@Override
		public boolean matches(CraftingInput input, Level level) {
			return wrapped(input) != null;
		}

		@Override
		public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
			ItemStack item = wrapped(input);
			return item == null ? ItemStack.EMPTY : Gifts.wrap(item, "");
		}

		/** Nothing stays on the grid: what a wrapped item would leave behind (a bucket, a bottle) is inside the Gift with it. */
		@Override
		public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
			return NonNullList.withSize(input.size(), ItemStack.EMPTY);
		}

		@Override
		public boolean canCraftInDimensions(int width, int height) {
			return width * height >= 2;
		}

		@Override
		public RecipeSerializer<?> getSerializer() {
			return RECIPE;
		}
	}
}
