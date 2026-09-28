package io.github.jcondedata.aliveworkplace.mine;

import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Marks out a quarry for a Miner. Right-click one corner block, then the opposite corner; sneak-right-click
 * the air to change how deep they dig; sneak-right-click a block to start over. Then give it to a miner.
 */
public class QuarryMarkerItem extends Item {
	public QuarryMarkerItem(Properties properties) {
		super(properties.component(ModComponents.QUARRY, QuarryData.EMPTY));
	}

	public static QuarryData data(ItemStack stack) {
		return stack.getOrDefault(ModComponents.QUARRY, QuarryData.EMPTY);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		ItemStack stack = context.getItemInHand();
		if (context.getLevel().isClientSide || player == null) {
			return InteractionResult.SUCCESS;
		}
		QuarryData data = data(stack);
		BlockPos pos = context.getClickedPos();
		var dim = context.getLevel().dimension().location();
		if (player.isShiftKeyDown()) {
			stack.set(ModComponents.QUARRY, new QuarryData(Optional.empty(), Optional.empty(), Optional.empty(), data.depth()));
			player.displayClientMessage(Component.translatable("message.aliveworkplace.quarry.reset"), true);
			return InteractionResult.SUCCESS;
		}
		if (data.first().isEmpty() || data.isComplete() || !data.dimension().map(dim::equals).orElse(false)) {
			stack.set(ModComponents.QUARRY, new QuarryData(Optional.of(dim), Optional.of(pos), Optional.empty(), data.depth()));
			player.displayClientMessage(Component.translatable("message.aliveworkplace.quarry.first", pos.getX(), pos.getY(), pos.getZ()), true);
			return InteractionResult.SUCCESS;
		}
		BlockPos first = data.first().get();
		int w = Math.abs(first.getX() - pos.getX()) + 1;
		int d = Math.abs(first.getZ() - pos.getZ()) + 1;
		if (w > QuarryData.MAX_SIDE || d > QuarryData.MAX_SIDE) {
			player.displayClientMessage(Component.translatable("message.aliveworkplace.quarry.too_big", w, d, QuarryData.MAX_SIDE)
				.withStyle(ChatFormatting.RED), true);
			return InteractionResult.FAIL;
		}
		QuarryData done = new QuarryData(Optional.of(dim), Optional.of(first), Optional.of(pos), data.depth());
		stack.set(ModComponents.QUARRY, done);
		player.displayClientMessage(data.isStripMine() ? Component.translatable("message.aliveworkplace.quarry.marked_strip", w, d)
			: Component.translatable("message.aliveworkplace.quarry.marked", w, d, data.depth()), false);
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!player.isShiftKeyDown()) {
			return InteractionResultHolder.pass(stack);
		}
		if (!level.isClientSide) {
			QuarryData data = data(stack);
			QuarryData deeper = data.withDepth(data.nextDepth());
			stack.set(ModComponents.QUARRY, deeper);
			player.displayClientMessage(deeper.isStripMine() ? Component.translatable("message.aliveworkplace.quarry.strip_mine")
				: Component.translatable("message.aliveworkplace.quarry.depth", deeper.depth()), true);
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		QuarryData data = data(stack);
		Optional<BoundingBox> area = data.area();
		if (area.isPresent()) {
			BoundingBox box = area.get();
			tooltip.add(Component.translatable(data.isStripMine() ? "tooltip.aliveworkplace.quarry.strip_area" : "tooltip.aliveworkplace.quarry.area",
				box.getXSpan(), box.getZSpan(), box.getYSpan(), box.minX(), box.maxY(), box.minZ()).withStyle(ChatFormatting.AQUA));
			tooltip.add(Component.translatable("tooltip.aliveworkplace.quarry.hand_over").withStyle(ChatFormatting.GRAY));
		} else if (data.first().isPresent()) {
			tooltip.add(Component.translatable("tooltip.aliveworkplace.quarry.second").withStyle(ChatFormatting.GRAY));
		} else {
			tooltip.add(Component.translatable("tooltip.aliveworkplace.quarry.first").withStyle(ChatFormatting.GRAY));
		}
		tooltip.add(data.isStripMine() ? Component.translatable("tooltip.aliveworkplace.quarry.strip_mine").withStyle(ChatFormatting.DARK_GRAY)
			: Component.translatable("tooltip.aliveworkplace.quarry.depth", data.depth()).withStyle(ChatFormatting.DARK_GRAY));
	}
}
