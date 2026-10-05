package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The Village Banner (ROADMAP 30.13): crafted from any banner and a gold ingot, it keeps that banner's design (base
 * colour in {@link ModComponents#VILLAGE_BANNER}, patterns in vanilla's {@code banner_patterns}). Right-click a Village
 * Hall to make the design the village's colours (the banner isn't used up, as a Name Tag isn't); used on any other
 * block it places a vanilla banner of the design, as a banner does.
 */
public class VillageBannerItem extends Item {
	public static final RecipeSerializer<Recipe> RECIPE = new SimpleCraftingRecipeSerializer<>(Recipe::new);

	public VillageBannerItem(Properties properties) {
		super(properties);
	}

	/** A Village Banner of {@code base} with {@code patterns}. */
	public static ItemStack of(DyeColor base, BannerPatternLayers patterns) {
		ItemStack stack = new ItemStack(ModItems.VILLAGE_BANNER);
		stack.set(ModComponents.VILLAGE_BANNER, base);
		stack.set(DataComponents.BANNER_PATTERNS, patterns);
		return stack;
	}

	public static DyeColor base(ItemStack stack) {
		DyeColor base = stack.get(ModComponents.VILLAGE_BANNER);
		return base == null ? DyeColor.WHITE : base;
	}

	public static BannerPatternLayers patterns(ItemStack stack) {
		BannerPatternLayers patterns = stack.get(DataComponents.BANNER_PATTERNS);
		return patterns == null ? BannerPatternLayers.EMPTY : patterns;
	}

	/** The vanilla banner this one hangs as. */
	public static ItemStack asVanilla(DyeColor base, BannerPatternLayers patterns) {
		ItemStack banner = new ItemStack(BannerBlock.byColor(base).asItem());
		if (!patterns.layers().isEmpty()) {
			banner.set(DataComponents.BANNER_PATTERNS, patterns);
		}
		return banner;
	}

	/** Makes {@code stack}'s design the colours of the village whose hall is at {@code pos} (called by the hall block). */
	public static void setColours(ServerLevel level, ServerPlayer player, ItemStack stack, BlockPos pos) {
		if (!VillageBanners.ENABLED || !(level.getBlockEntity(pos) instanceof VillageHallBlockEntity hall)) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.village_banner.off").withStyle(ChatFormatting.YELLOW));
			return;
		}
		Component village = VillageHalls.name(level, pos);
		hall.setColours(new VillageBanners.Colours(base(stack), patterns(stack)));
		Chronicle.record(level, pos, Chronicle.Kind.BANNER,
			Component.translatable("chronicle.aliveworkplace.banner", player.getName(), village), true);
		level.playSound(null, pos, SoundEvents.VILLAGER_CELEBRATE, SoundSource.BLOCKS, 0.8f, 1.1f);
		Chat.actionBar(player, Component.translatable("message.aliveworkplace.village_banner.set", village).withStyle(ChatFormatting.GREEN));
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		ItemStack stack = context.getItemInHand();
		ItemStack banner = asVanilla(base(stack), patterns(stack));
		BlockHitResult hit = new BlockHitResult(context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), context.isInside());
		InteractionResult result = ((BlockItem) banner.getItem()).place(
			new BlockPlaceContext(context.getLevel(), context.getPlayer(), context.getHand(), banner, hit));
		if (result.consumesAction() && (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild)) {
			stack.shrink(1);
		}
		return result;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("tooltip.aliveworkplace.village_banner.base",
			Component.translatable("color.minecraft." + base(stack).getName())).withStyle(ChatFormatting.GRAY));
		BannerItem.appendHoverTextFromBannerBlockEntityTag(stack, tooltip);
		tooltip.add(Component.translatable("tooltip.aliveworkplace.village_banner.hint").withStyle(ChatFormatting.DARK_GRAY));
	}

	/** Any banner and a gold ingot, shapeless: a Village Banner of that banner's design. */
	public static class Recipe extends CustomRecipe {
		public Recipe(CraftingBookCategory category) {
			super(category);
		}

		@Nullable
		private static ItemStack banner(CraftingInput input) {
			ItemStack banner = null;
			int gold = 0;
			for (int i = 0; i < input.size(); i++) {
				ItemStack s = input.getItem(i);
				if (s.isEmpty()) {
					continue;
				}
				if (s.getItem() instanceof BannerItem && banner == null) {
					banner = s;
				} else if (s.is(Items.GOLD_INGOT)) {
					gold++;
				} else {
					return null;
				}
			}
			return gold == 1 ? banner : null;
		}

		@Override
		public boolean matches(CraftingInput input, Level level) {
			return VillageBanners.ENABLED && banner(input) != null;
		}

		@Override
		public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
			ItemStack banner = banner(input);
			if (banner == null) {
				return ItemStack.EMPTY;
			}
			return of(((BannerItem) banner.getItem()).getColor(), patterns(banner));
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
