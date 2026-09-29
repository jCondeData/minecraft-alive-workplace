package io.github.jcondedata.aliveworkplace.travel;

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

/** A ferry ticket to one Travel Post. Use it at any travel post to go there; it's used up on the way. */
public class TravelTicketItem extends Item {
	public TravelTicketItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
			if (Ferrymen.travel(serverPlayer, stack) && !player.getAbilities().instabuild) {
				stack.shrink(1);
			}
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}

	@Override
	public Component getName(ItemStack stack) {
		TicketData data = stack.get(ModComponents.TICKET);
		return data == null ? super.getName(stack) : Component.translatable("item.aliveworkplace.travel_ticket.to", data.name());
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		TicketData data = stack.get(ModComponents.TICKET);
		if (data != null) {
			tooltip.add(Component.translatable("tooltip.aliveworkplace.ticket.where", data.destination().pos().getX(), data.destination().pos().getZ())
				.withStyle(ChatFormatting.GRAY));
		}
		tooltip.add(Component.translatable("tooltip.aliveworkplace.ticket.how").withStyle(ChatFormatting.DARK_GRAY));
	}
}
