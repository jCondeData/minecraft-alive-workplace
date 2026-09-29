package io.github.jcondedata.aliveworkplace.mine;

import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Ids;
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
		if (context.getLevel().isClientSide() || player == null) {
			return InteractionResult.SUCCESS;
		}
		QuarryData data = data(stack);
		BlockPos pos = context.getClickedPos();
		var dim = Ids.of(context.getLevel().dimension());
		if (player.isShiftKeyDown()) {
			stack.set(ModComponents.QUARRY, new QuarryData(Optional.empty(), Optional.empty(), Optional.empty(), data.depth(), data.stripLevel()));
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.quarry.reset"));
			return InteractionResult.SUCCESS;
		}
		if (data.first().isEmpty() || data.isComplete() || !data.dimension().map(dim::equals).orElse(false)) {
			stack.set(ModComponents.QUARRY, new QuarryData(Optional.of(dim), Optional.of(pos), Optional.empty(), data.depth(), data.stripLevel()));
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.quarry.first", pos.getX(), pos.getY(), pos.getZ()));
			return InteractionResult.SUCCESS;
		}
		BlockPos first = data.first().get();
		int w = Math.abs(first.getX() - pos.getX()) + 1;
		int d = Math.abs(first.getZ() - pos.getZ()) + 1;
		if (!QuarryData.fits(w, d, data.isStripMine())) {
			Chat.actionBar(player, tooBig(w, d, data.isStripMine()).withStyle(ChatFormatting.RED));
			return InteractionResult.FAIL;
		}
		QuarryData done = new QuarryData(Optional.of(dim), Optional.of(first), Optional.of(pos), data.depth(), data.stripLevel());
		stack.set(ModComponents.QUARRY, done);
		Component message;
		if (done.shaftTop(Integer.MIN_VALUE).isPresent()) {
			message = Component.translatable("message.aliveworkplace.quarry.marked_strip_level", w, d, done.stripLevel().get());
		} else if (data.isStripMine()) {
			message = Component.translatable("message.aliveworkplace.quarry.marked_strip", w, d);
		} else {
			message = Component.translatable("message.aliveworkplace.quarry.marked", w, d, data.depth());
		}
		Chat.chat(player, message);
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!player.isShiftKeyDown()) {
			return InteractionResultHolder.pass(stack);
		}
		if (!level.isClientSide()) {
			QuarryData data = data(stack);
			QuarryData next = data.next();
			stack.set(ModComponents.QUARRY, next);
			Component message;
			if (next.stripLevel().isPresent()) {
				message = Component.translatable("message.aliveworkplace.quarry.strip_level", next.stripLevel().get(), oresAt(next.stripLevel().get()));
			} else if (next.isStripMine()) {
				message = Component.translatable("message.aliveworkplace.quarry.strip_mine");
			} else {
				message = Component.translatable("message.aliveworkplace.quarry.depth", next.depth());
			}
			Chat.actionBar(player, message);
			if (next.isComplete()) {
				BoundingBox box = next.area().orElseThrow();
				if (!QuarryData.fits(box.getXSpan(), box.getZSpan(), next.isStripMine())) {
					Chat.chat(player, tooBig(box.getXSpan(), box.getZSpan(), next.isStripMine()).withStyle(ChatFormatting.RED));
				}
			}
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		QuarryData data = data(stack);
		Optional<BoundingBox> area = data.area();
		if (area.isPresent()) {
			BoundingBox box = area.get();
			java.util.OptionalInt shaft = data.shaftTop(Integer.MIN_VALUE);
			if (shaft.isPresent()) {
				tooltip.add(Component.translatable("tooltip.aliveworkplace.quarry.strip_shaft_area", box.getXSpan(), box.getZSpan(), box.minY(),
					box.minX(), shaft.getAsInt(), box.minZ()).withStyle(ChatFormatting.AQUA));
			} else {
				tooltip.add(Component.translatable(data.isStripMine() ? "tooltip.aliveworkplace.quarry.strip_area" : "tooltip.aliveworkplace.quarry.area",
					box.getXSpan(), box.getZSpan(), box.getYSpan(), box.minX(), box.maxY(), box.minZ()).withStyle(ChatFormatting.AQUA));
			}
			tooltip.add(Component.translatable("tooltip.aliveworkplace.quarry.hand_over").withStyle(ChatFormatting.GRAY));
		} else if (data.first().isPresent()) {
			tooltip.add(Component.translatable("tooltip.aliveworkplace.quarry.second").withStyle(ChatFormatting.GRAY));
		} else {
			tooltip.add(Component.translatable("tooltip.aliveworkplace.quarry.first").withStyle(ChatFormatting.GRAY));
		}
		Component mode;
		if (data.stripLevel().isPresent() && data.isStripMine()) {
			mode = Component.translatable("tooltip.aliveworkplace.quarry.strip_level", data.stripLevel().get(), oresAt(data.stripLevel().get()));
		} else if (data.isStripMine()) {
			mode = Component.translatable("tooltip.aliveworkplace.quarry.strip_mine");
		} else {
			mode = Component.translatable("tooltip.aliveworkplace.quarry.depth", data.depth());
		}
		tooltip.add(mode.copy().withStyle(ChatFormatting.DARK_GRAY));
	}

	/** What's worth digging for at that height ("diamonds"). */
	/** What's worth mining at height {@code level} in the Overworld (the set heights have their own words). */
	public static Component oresAt(int level) {
		if (QuarryData.STRIP_LEVELS.contains(level)) {
			return Component.translatable("message.aliveworkplace.quarry.ores." + (level < 0 ? "minus_" + -level : String.valueOf(level)));
		}
		String band = level >= 80 ? "coal" : level >= 32 ? "copper" : level >= 8 ? "iron" : level >= -8 ? "lapis" : level >= -32 ? "gold" : "diamonds";
		return Component.translatable("message.aliveworkplace.quarry.ores.band." + band);
	}

	static net.minecraft.network.chat.MutableComponent tooBig(int w, int d, boolean stripMine) {
		return stripMine ? Component.translatable("message.aliveworkplace.quarry.too_long", w, d, QuarryData.MAX_TUNNEL, QuarryData.MAX_SIDE)
			: Component.translatable("message.aliveworkplace.quarry.too_big", w, d, QuarryData.MAX_SIDE);
	}
}
