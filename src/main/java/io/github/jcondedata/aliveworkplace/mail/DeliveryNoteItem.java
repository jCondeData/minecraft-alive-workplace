package io.github.jcondedata.aliveworkplace.mail;

import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Sets up a courier route: right-click the container to take from, then the one to bring to; hold an item
 * in the other hand and right-click the air to add it to (or take it off) the list of what to carry. Give it
 * to a Postman. A blank note given to a postman ends all their routes.
 */
public class DeliveryNoteItem extends Item {
	public DeliveryNoteItem(Properties properties) {
		super(properties.component(ModComponents.ROUTE, RouteData.EMPTY));
	}

	public static RouteData data(ItemStack stack) {
		return stack.getOrDefault(ModComponents.ROUTE, RouteData.EMPTY);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (context.getLevel().isClientSide || player == null) {
			return InteractionResult.SUCCESS;
		}
		ItemStack stack = context.getItemInHand();
		RouteData data = data(stack);
		BlockPos pos = context.getClickedPos();
		if (player.isShiftKeyDown()) {
			stack.set(ModComponents.ROUTE, new RouteData(Optional.empty(), Optional.empty(), data.filter()));
			player.displayClientMessage(Component.translatable("message.aliveworkplace.route.reset"), true);
			return InteractionResult.SUCCESS;
		}
		boolean pasture = io.github.jcondedata.aliveworkplace.work.Pastures.isPasture(context.getLevel().getBlockState(pos));
		if (pasture && context.getLevel().getBlockState(pos.below()).is(context.getLevel().getBlockState(pos).getBlock())) {
			pos = pos.below(); // the top half: use the pasture's base
		}
		if (!pasture && (ItemStorage.SIDED.find(context.getLevel(), pos, null) == null
			|| context.getLevel().getBlockEntity(pos) instanceof io.github.jcondedata.aliveworkplace.work.PrivateContainer)) {
			player.displayClientMessage(Component.translatable("message.aliveworkplace.route.not_container").withStyle(ChatFormatting.YELLOW), true);
			return InteractionResult.FAIL;
		}
		if (data.from().isEmpty() || data.isComplete()) {
			stack.set(ModComponents.ROUTE, new RouteData(Optional.of(pos), Optional.empty(), data.filter()));
			player.displayClientMessage(Component.translatable("message.aliveworkplace.route.from", pos.getX(), pos.getY(), pos.getZ()), true);
		} else {
			stack.set(ModComponents.ROUTE, new RouteData(data.from(), Optional.of(pos), data.filter()));
			player.displayClientMessage(Component.translatable("message.aliveworkplace.route.to", pos.getX(), pos.getY(), pos.getZ()), false);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		ItemStack other = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
		if (other.isEmpty()) {
			return InteractionResultHolder.pass(stack);
		}
		if (level instanceof ServerLevel) {
			RouteData next = data(stack).toggle(other.getItem());
			stack.set(ModComponents.ROUTE, next);
			boolean added = next.filter().contains(other.getItem());
			player.displayClientMessage(Component.translatable(added ? "message.aliveworkplace.route.filter_add" : "message.aliveworkplace.route.filter_remove",
				other.getHoverName()), true);
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		RouteData data = data(stack);
		if (data.from().isPresent()) {
			BlockPos f = data.from().get();
			tooltip.add(Component.translatable("tooltip.aliveworkplace.route.from", f.getX(), f.getY(), f.getZ()).withStyle(ChatFormatting.AQUA));
		}
		if (data.to().isPresent()) {
			BlockPos t = data.to().get();
			tooltip.add(Component.translatable("tooltip.aliveworkplace.route.to", t.getX(), t.getY(), t.getZ()).withStyle(ChatFormatting.AQUA));
		}
		tooltip.add(data.filter().isEmpty()
			? Component.translatable("tooltip.aliveworkplace.route.everything").withStyle(ChatFormatting.GRAY)
			: Component.translatable("tooltip.aliveworkplace.route.only",
				data.filter().stream().map(i -> i.getDescription().getString()).collect(Collectors.joining(", "))).withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable(data.isComplete() ? "tooltip.aliveworkplace.route.hand_over" : "tooltip.aliveworkplace.route.how")
			.withStyle(ChatFormatting.DARK_GRAY));
	}
}
