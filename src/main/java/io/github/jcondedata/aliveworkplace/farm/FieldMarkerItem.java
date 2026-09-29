package io.github.jcondedata.aliveworkplace.farm;

import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Marks out a field for a Farmer. Right-click one corner block of the field, then the opposite corner;
 * sneak-right-click a block to start over. Then give it to a farmer.
 */
public class FieldMarkerItem extends Item {
	public FieldMarkerItem(Properties properties) {
		super(properties.component(ModComponents.FIELD, FieldData.EMPTY));
	}

	public static FieldData data(ItemStack stack) {
		return stack.getOrDefault(ModComponents.FIELD, FieldData.EMPTY);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		ItemStack stack = context.getItemInHand();
		if (context.getLevel().isClientSide() || player == null) {
			return InteractionResult.SUCCESS;
		}
		FieldData data = data(stack);
		BlockPos pos = context.getClickedPos();
		var dim = Ids.of(context.getLevel().dimension());
		if (player.isShiftKeyDown()) {
			stack.set(ModComponents.FIELD, FieldData.EMPTY);
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.field.reset"));
			return InteractionResult.SUCCESS;
		}
		if (data.first().isEmpty() || data.isComplete() || !data.dimension().map(dim::equals).orElse(false)) {
			stack.set(ModComponents.FIELD, new FieldData(Optional.of(dim), Optional.of(pos), Optional.empty()));
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.field.first", pos.getX(), pos.getY(), pos.getZ()));
			return InteractionResult.SUCCESS;
		}
		BlockPos first = data.first().get();
		int w = Math.abs(first.getX() - pos.getX()) + 1;
		int d = Math.abs(first.getZ() - pos.getZ()) + 1;
		if (w > FieldData.MAX_SIDE || d > FieldData.MAX_SIDE) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.field.too_big", w, d, FieldData.MAX_SIDE)
				.withStyle(ChatFormatting.RED));
			return InteractionResult.FAIL;
		}
		stack.set(ModComponents.FIELD, new FieldData(Optional.of(dim), Optional.of(first), Optional.of(pos)));
		Chat.chat(player, Component.translatable("message.aliveworkplace.field.marked", w, d));
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		FieldData data = data(stack);
		Optional<BoundingBox> area = data.area();
		if (area.isPresent()) {
			BoundingBox box = area.get();
			tooltip.add(Component.translatable("tooltip.aliveworkplace.field.area", box.getXSpan(), box.getZSpan(),
				box.minX(), box.minY(), box.minZ()).withStyle(ChatFormatting.GREEN));
			tooltip.add(Component.translatable("tooltip.aliveworkplace.field.hand_over").withStyle(ChatFormatting.GRAY));
		} else if (data.first().isPresent()) {
			tooltip.add(Component.translatable("tooltip.aliveworkplace.field.second").withStyle(ChatFormatting.GRAY));
		} else {
			tooltip.add(Component.translatable("tooltip.aliveworkplace.field.first").withStyle(ChatFormatting.GRAY));
		}
		tooltip.add(Component.translatable("tooltip.aliveworkplace.field.reset").withStyle(ChatFormatting.DARK_GRAY));
	}
}
