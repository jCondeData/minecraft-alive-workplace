package io.github.jcondedata.aliveworkplace.blueprint;

import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The Shape Planner: right-click to pick a shape (box, cylinder, dome, sphere, cone, pyramid or arch), its size, solid or
 * hollow, and one of the blocks you carry; "Draw the blueprint" turns a Blank Blueprint into a blueprint of it for a
 * builder (see {@link Shapes}).
 */
public class ShapePlannerItem extends Item {
	public ShapePlannerItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player instanceof ServerPlayer serverPlayer) {
			Shapes.open(serverPlayer, hand);
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		Shapes.Settings s = stack.getOrDefault(ModComponents.SHAPE, Shapes.Settings.DEFAULT);
		tooltip.add(Component.translatable("tooltip.aliveworkplace.shape_planner.settings",
			Component.translatable("shape.aliveworkplace." + s.shape().id()), s.width(), s.height(), s.depth(),
			s.material().isPresent() ? s.block().getBlock().getName() : Component.translatable("tooltip.aliveworkplace.shape_planner.no_block"))
			.withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("tooltip.aliveworkplace.shape_planner.use").withStyle(ChatFormatting.DARK_GRAY));
	}
}
